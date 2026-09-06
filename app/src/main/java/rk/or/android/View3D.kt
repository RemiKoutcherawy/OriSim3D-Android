package rk.or.android

import android.content.Context
import android.opengl.GLES30
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.widget.Toast
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.nio.IntBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import rk.or.core.Command
import rk.or.core.Model
import rk.or.core.Point

/**
 * Port of js/View3d.js's WebGL2 renderer to GLES 3.0 (the shaders are used verbatim: GLSL ES
 * 3.00 is shared between WebGL2 and OpenGL ES 3.0). Kept view-only: no point/segment/face
 * picking or crease editing (js/Helper.js's editing gestures are out of scope for this app) --
 * touch only rotates/zooms the camera, resets it on double-tap, and toggles play/pause on tap.
 */
class View3D @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) :
    GLSurfaceView(context, attrs), GLSurfaceView.Renderer {

    // Set by the activity right after construction
    lateinit var model: Model
    lateinit var command: Command
    var needRebuild: Boolean = true

    // Camera: one-finger drag rotates, pinch zooms, double-tap resets (js/Helper.js semantics)
    private var angleX = 0f
    private var angleY = 0f
    private var angleZ = 0f
    private var translationX = 0f
    private var translationY = 0f
    private var scale = 1f

    private val projection = FloatArray(16)
    private val modelView = FloatArray(16)

    private var program = 0
    private var vao = IntArray(1)
    private var vertexBuffer = 0
    private var normalBuffer = 0
    private var frontTexBuffer = 0
    private var backTexBuffer = 0
    private var lineBuffer = 0
    private var texFront = 0
    private var texBack = 0

    private var nbVertices = 0
    private var nbLineIndices = 0
    private var surfaceWidth = 1
    private var surfaceHeight = 1

    // Touch
    private var lastX = 0f
    private var lastY = 0f
    private var activePointerId = MotionEvent.INVALID_POINTER_ID
    private val scaleDetector = ScaleGestureDetector(context, ScaleListener())
    private val tapDetector = GestureDetector(context, TapListener())
    // Gated here rather than sent through the command language: Command.command("pause") only
    // enqueues a token at the *end* of tokenTodo, so with a whole multi-line script already
    // queued (as a menu selection loads it) it would only take effect once every remaining
    // line finished running on its own -- not a real pause. Freezing the render loop's call to
    // command.anim() is instant and exact; tStart is shifted by the paused wall-clock gap on
    // resume so the in-flight 't' line continues instead of jumping forward by the pause length.
    private var playing = true
    private var pausedAt = 0.0

    private fun now(): Double = System.nanoTime() / 1_000_000.0

    init {
        setEGLContextClientVersion(3)
        setRenderer(this)
        renderMode = RENDERMODE_WHEN_DIRTY
    }

    // ------ Touch ------

    private inner class ScaleListener : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            scale = max(0.2f, min(3f, scale * detector.scaleFactor))
            requestRender()
            return true
        }
    }

    private inner class TapListener : GestureDetector.SimpleOnGestureListener() {
        override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
            playing = !playing
            if (!playing) {
                pausedAt = now()
            } else {
                command.tStart += now() - pausedAt
            }
            Toast.makeText(context, if (playing) "Play" else "Pause", Toast.LENGTH_SHORT).show()
            requestRender()
            return true
        }

        override fun onDoubleTap(e: MotionEvent): Boolean {
            angleX = 0f; angleY = 0f; angleZ = 0f
            translationX = 0f; translationY = 0f
            scale = 1f
            command.command("fit")
            requestRender()
            return true
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        scaleDetector.onTouchEvent(event)
        tapDetector.onTouchEvent(event)

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                lastX = event.x
                lastY = event.y
                activePointerId = event.getPointerId(0)
            }
            MotionEvent.ACTION_MOVE -> {
                val pointerIndex = event.findPointerIndex(activePointerId)
                if (pointerIndex >= 0 && !scaleDetector.isInProgress) {
                    val x = event.getX(pointerIndex)
                    val y = event.getY(pointerIndex)
                    // js/Helper.js's move3d(): factor keeps drag sensitivity independent of view height
                    val factor = 600f / max(height, 1)
                    angleX += factor * (y - lastY)
                    angleY += factor * (x - lastX)
                    lastX = x
                    lastY = y
                    requestRender()
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                activePointerId = MotionEvent.INVALID_POINTER_ID
            }
            MotionEvent.ACTION_POINTER_UP -> {
                val pointerIndex = event.actionIndex
                if (event.getPointerId(pointerIndex) == activePointerId) {
                    val newIndex = if (pointerIndex == 0) 1 else 0
                    lastX = event.getX(newIndex)
                    lastY = event.getY(newIndex)
                    activePointerId = event.getPointerId(newIndex)
                }
                performClick()
            }
        }
        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    // ------ GLES30 ------

    override fun onSurfaceCreated(unused: GL10?, config: EGLConfig?) {
        GLES30.glEnable(GLES30.GL_DEPTH_TEST)
        GLES30.glDepthFunc(GLES30.GL_LEQUAL)
        GLES30.glClearColor(0xCC / 255f, 0xE4 / 255f, 1f, 1f)
        initShaders()
        initTextures()
    }

    private fun initShaders() {
        val vertexShader = """
            #version 300 es
            precision highp float;

            in vec4 aVertexPosition;
            in vec3 aVertexNormal;
            in vec2 aTexCoordsFront;
            in vec2 aTexCoordsBack;

            uniform mat4 uModelViewMatrix;
            uniform mat4 uProjectionMatrix;

            out highp vec2 vTexCoordsFront;
            out highp vec2 vTexCoordsBack;
            out highp vec3 vLighting;
            void main(void) {
                gl_Position = uProjectionMatrix * uModelViewMatrix * aVertexPosition;
                vTexCoordsFront = aTexCoordsFront;
                vTexCoordsBack  = aTexCoordsBack;
                highp vec3 directionalVector = normalize(vec3(0.1, 0.1, 0.75));
                highp vec4 normal = normalize(uModelViewMatrix * vec4(aVertexNormal, 0.0));
                float directional = dot(normal.xyz, directionalVector);
                vLighting = vec3(0.1) + directional;
            }
        """.trimIndent()

        val fragmentShader = """
            #version 300 es
            precision highp float;

            in highp vec2 vTexCoordsFront;
            in highp vec2 vTexCoordsBack;
            in highp vec3 vLighting;

            uniform sampler2D uSamplerFront;
            uniform sampler2D uSamplerBack;
            uniform bool uLine;
            out vec4 outColor;
            void main(void) {
                highp vec4 texelColor;
                highp vec3 lighting = vLighting;
                if (uLine) {
                    texelColor = vec4(0.0, 0.0, 0.0, 1.0);
                } else if (gl_FrontFacing) {
                    texelColor = texture(uSamplerFront, vTexCoordsFront);
                } else {
                    texelColor = texture(uSamplerBack, vTexCoordsBack);
                    lighting = vec3(0.1) - vLighting;
                }
                outColor = vec4(texelColor.rgb * lighting, texelColor.a);
            }
        """.trimIndent()

        val vxShader = compileShader(GLES30.GL_VERTEX_SHADER, vertexShader)
        val fgShader = compileShader(GLES30.GL_FRAGMENT_SHADER, fragmentShader)
        program = GLES30.glCreateProgram()
        GLES30.glAttachShader(program, vxShader)
        GLES30.glAttachShader(program, fgShader)
        GLES30.glLinkProgram(program)
    }

    private fun compileShader(type: Int, source: String): Int {
        val shader = GLES30.glCreateShader(type)
        GLES30.glShaderSource(shader, source)
        GLES30.glCompileShader(shader)
        return shader
    }

    // Solid-color placeholder textures (js/View3d.js's default front/back colors before any
    // real image loads) -- this app has no crease-pattern texture images, only the fold shape.
    private fun initTextures() {
        val ids = IntArray(2)
        GLES30.glGenTextures(2, ids, 0)
        texFront = ids[0]
        texBack = ids[1]
        uploadSolidColorTexture(texFront, 0x70, 0xAC, 0xF3, 0xFF)
        uploadSolidColorTexture(texBack, 0xFF, 0xFF, 0x00, 0xA8)
    }

    private fun uploadSolidColorTexture(id: Int, r: Int, g: Int, b: Int, a: Int) {
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, id)
        val pixel = ByteBuffer.allocateDirect(4).order(ByteOrder.nativeOrder())
        pixel.put(r.toByte()); pixel.put(g.toByte()); pixel.put(b.toByte()); pixel.put(a.toByte())
        pixel.position(0)
        GLES30.glTexImage2D(GLES30.GL_TEXTURE_2D, 0, GLES30.GL_RGBA, 1, 1, 0, GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE, pixel)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_NEAREST)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_NEAREST)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE)
    }

    override fun onSurfaceChanged(unused: GL10?, width: Int, height: Int) {
        surfaceWidth = width
        surfaceHeight = height
        GLES30.glViewport(0, 0, width, height)
        val ratio = width.toFloat() / height.toFloat()
        Matrix.perspectiveM(projection, 0, 40f, ratio, 50f, 1200f)
    }

    // Face normal: first non-degenerate consecutive triple, matching js/View3d.js's own
    // normal() (distinct from Model.normal's Newell-sum, which favors robustness over speed).
    private fun faceNormal(pts: List<Point>): DoubleArray {
        var nx = 0.0; var ny = 0.0; var nz = 0.0
        for (i in 0..pts.size - 3) {
            val p1 = pts[i]; val p2 = pts[i + 1]; val p3 = pts[i + 2]
            val ux = p2.x - p1.x; val uy = p2.y - p1.y; val uz = p2.z - p1.z
            val vx = p3.x - p1.x; val vy = p3.y - p1.y; val vz = p3.z - p1.z
            nx = uy * vz - uz * vy
            ny = uz * vx - ux * vz
            nz = ux * vy - uy * vx
            if (abs(nx) + abs(ny) + abs(nz) > 0.1) break
        }
        val len = hypot(hypot(nx, ny), nz)
        if (len < 1e-6) return doubleArrayOf(0.0, 0.0, 1.0)
        return doubleArrayOf(nx / len, ny / len, nz / len)
    }

    private fun initBuffers() {
        if (vao[0] == 0) GLES30.glGenVertexArrays(1, vao, 0)
        GLES30.glBindVertexArray(vao[0])

        val vtx = ArrayList<Float>()
        val fnr = ArrayList<Float>()
        val ftx = ArrayList<Float>()
        val btx = ArrayList<Float>()
        val lin = ArrayList<Int>()

        for (f in model.faces) {
            val pts = f.points
            if (pts.size < 3) continue
            val n = faceNormal(pts)
            val faceIndex = HashMap<Point, Int>()
            var index = vtx.size / 3

            fun addVertex(p: Point) {
                vtx.add((p.x + f.offset * n[0]).toFloat())
                vtx.add((p.y + f.offset * n[1]).toFloat())
                vtx.add((p.z + f.offset * n[2]).toFloat())
                fnr.add(n[0].toFloat()); fnr.add(n[1].toFloat()); fnr.add(n[2].toFloat())
                ftx.add(((200 + p.xf) / 400.0).toFloat()); ftx.add(((200 + p.yf) / 400.0).toFloat())
                btx.add(((200 + p.xf) / 400.0).toFloat()); btx.add(((200 + p.yf) / 400.0).toFloat())
                if (!faceIndex.containsKey(p)) faceIndex[p] = index
                index++
            }

            for (i in 1 until pts.size - 1) {
                addVertex(pts[0])
                addVertex(pts[i])
                addVertex(pts[i + 1])
            }
            for (i in pts.indices) {
                val a = faceIndex[pts[i]]
                val b = faceIndex[pts[(i + 1) % pts.size]]
                if (a != null && b != null) { lin.add(a); lin.add(b) }
            }
        }

        nbVertices = vtx.size / 3
        nbLineIndices = lin.size

        fun floatBufferOf(list: List<Float>): FloatBuffer =
            ByteBuffer.allocateDirect(list.size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer().apply {
                list.forEach { put(it) }
                position(0)
            }

        val vtxBuffer = floatBufferOf(vtx)
        val fnrBuffer = floatBufferOf(fnr)
        val ftxBuffer = floatBufferOf(ftx)
        val btxBuffer = floatBufferOf(btx)
        val linIntBuffer: IntBuffer =
            ByteBuffer.allocateDirect(lin.size * 4).order(ByteOrder.nativeOrder()).asIntBuffer().apply {
                lin.forEach { put(it) }
                position(0)
            }

        if (vertexBuffer == 0) vertexBuffer = genBuffer()
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, vertexBuffer)
        GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, vtxBuffer.capacity() * 4, vtxBuffer, GLES30.GL_STATIC_DRAW)
        val aVertexPosition = GLES30.glGetAttribLocation(program, "aVertexPosition")
        GLES30.glVertexAttribPointer(aVertexPosition, 3, GLES30.GL_FLOAT, false, 0, 0)
        GLES30.glEnableVertexAttribArray(aVertexPosition)

        if (normalBuffer == 0) normalBuffer = genBuffer()
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, normalBuffer)
        GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, fnrBuffer.capacity() * 4, fnrBuffer, GLES30.GL_STATIC_DRAW)
        val aVertexNormal = GLES30.glGetAttribLocation(program, "aVertexNormal")
        GLES30.glVertexAttribPointer(aVertexNormal, 3, GLES30.GL_FLOAT, false, 0, 0)
        GLES30.glEnableVertexAttribArray(aVertexNormal)

        if (frontTexBuffer == 0) frontTexBuffer = genBuffer()
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, frontTexBuffer)
        GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, ftxBuffer.capacity() * 4, ftxBuffer, GLES30.GL_STATIC_DRAW)
        val aTexCoordsFront = GLES30.glGetAttribLocation(program, "aTexCoordsFront")
        GLES30.glVertexAttribPointer(aTexCoordsFront, 2, GLES30.GL_FLOAT, false, 0, 0)
        GLES30.glEnableVertexAttribArray(aTexCoordsFront)

        if (backTexBuffer == 0) backTexBuffer = genBuffer()
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, backTexBuffer)
        GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, btxBuffer.capacity() * 4, btxBuffer, GLES30.GL_STATIC_DRAW)
        val aTexCoordsBack = GLES30.glGetAttribLocation(program, "aTexCoordsBack")
        GLES30.glVertexAttribPointer(aTexCoordsBack, 2, GLES30.GL_FLOAT, false, 0, 0)
        GLES30.glEnableVertexAttribArray(aTexCoordsBack)

        if (lineBuffer == 0) lineBuffer = genBuffer()
        GLES30.glBindBuffer(GLES30.GL_ELEMENT_ARRAY_BUFFER, lineBuffer)
        GLES30.glBufferData(GLES30.GL_ELEMENT_ARRAY_BUFFER, linIntBuffer.capacity() * 4, linIntBuffer, GLES30.GL_STATIC_DRAW)

        GLES30.glBindVertexArray(0)
        needRebuild = false
    }

    private fun genBuffer(): Int {
        val ids = IntArray(1)
        GLES30.glGenBuffers(1, ids, 0)
        return ids[0]
    }

    private fun updateModelView() {
        Matrix.setIdentityM(modelView, 0)
        Matrix.translateM(modelView, 0, translationX, translationY, -700f)
        Matrix.rotateM(modelView, 0, angleX, 1f, 0f, 0f)
        Matrix.rotateM(modelView, 0, angleY, 0f, 1f, 0f)
        Matrix.rotateM(modelView, 0, angleZ, 0f, 0f, 1f)
        Matrix.scaleM(modelView, 0, scale, scale, scale)
    }

    override fun onDrawFrame(unused: GL10?) {
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT or GLES30.GL_DEPTH_BUFFER_BIT)
        GLES30.glUseProgram(program)

        if (needRebuild) {
            initBuffers()
        }

        GLES30.glUniformMatrix4fv(GLES30.glGetUniformLocation(program, "uProjectionMatrix"), 1, false, projection, 0)
        updateModelView()
        GLES30.glUniformMatrix4fv(GLES30.glGetUniformLocation(program, "uModelViewMatrix"), 1, false, modelView, 0)

        GLES30.glBindVertexArray(vao[0])

        GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, texFront)
        GLES30.glUniform1i(GLES30.glGetUniformLocation(program, "uSamplerFront"), 0)
        GLES30.glActiveTexture(GLES30.GL_TEXTURE1)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, texBack)
        GLES30.glUniform1i(GLES30.glGetUniformLocation(program, "uSamplerBack"), 1)

        val uLine = GLES30.glGetUniformLocation(program, "uLine")
        GLES30.glUniform1i(uLine, 0)
        GLES30.glDrawArrays(GLES30.GL_TRIANGLES, 0, nbVertices)

        if (model.lines) {
            GLES30.glUniform1i(uLine, 1)
            GLES30.glDisable(GLES30.GL_DEPTH_TEST)
            GLES30.glBindBuffer(GLES30.GL_ELEMENT_ARRAY_BUFFER, lineBuffer)
            GLES30.glDrawElements(GLES30.GL_LINES, nbLineIndices, GLES30.GL_UNSIGNED_INT, 0)
            GLES30.glEnable(GLES30.GL_DEPTH_TEST)
            GLES30.glUniform1i(uLine, 0)
        }

        GLES30.glBindVertexArray(0)

        if (playing && command.anim()) {
            needRebuild = true
            requestRender()
        }
    }
}

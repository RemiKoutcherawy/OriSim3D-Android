package rk.or.android;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.opengl.GLUtils;
import android.opengl.Matrix;
import android.util.Log;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.widget.Toast;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.List;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

import rk.or.Face;
import rk.or.Model;
import rk.or.Point;
import rk.or.Segment;

// View 3D with Touch handler to rotate zoom
public class View3D extends GLSurfaceView implements GLSurfaceView.Renderer {

    // Mouse Rotation
    private float mAngleX = 0, mAngleY = 0, mAngleZ = 0;
    private float scale = 1.0f;

    // Model and Projection matrix
    private final float[] mvm = new float[16]; // uModelViewMatrix
    private final float[] mvp = new float[16]; // uProjectionMatrix

    // program
    private int program;

    // Touch
    private float mLastX, mLastY;
    private static final int INVALID_POINTER_ID = -1;
    private int activePointerId = INVALID_POINTER_ID;
    private final ScaleGestureDetector mScaleDetector;
    private final GestureDetector mDoubleTapDetector;
    private boolean running;

    // Flag to rebuild buffers
    public static boolean needRebuild = true;

    // Needed to access model
    private final ModelView mMainPane;

    // Texture size, set in initTextures, used in initBuffers
    private float wTexFront = 0, hTexFront = 0;
    private float wTexBack = 0, hTexBack = 0;
    private int[] textures;

    // Number of Points, and buffers
    private int nbPts;
    private FloatBuffer frontVertex;
    private FloatBuffer backVertex;
    private FloatBuffer frontNormal;
    private FloatBuffer backNormal;
    private FloatBuffer frontTex;
    private FloatBuffer backTex;

    // Todo show lines
    int nbPtsLines;
    private FloatBuffer lineVertex;

    // View3D shows and does Rendering
    public View3D(Context context) {
        super(context);
        mMainPane = (ModelView) context;

        // Create an OpenGL ES 2.0 context
        setEGLContextClientVersion(2);
        setRenderer(this);

        // RENDERMODE_WHEN_DIRTY or RENDERMODE_CONTINUOUSLY ?
        setRenderMode(GLSurfaceView.RENDERMODE_WHEN_DIRTY);

        // ScaleListener
        mScaleDetector = new ScaleGestureDetector(context,  new ScaleListener());
        // TapListener needed to build a GestureDetector with setOnDoubleTapListener()
        TapListener tapListener = new TapListener();
        mDoubleTapDetector = new GestureDetector( context, tapListener);
        // DoubleTapListener
        mDoubleTapDetector.setOnDoubleTapListener(new DoubleTapListener());
    }

    // ------ Touch event handling ------
    private class ScaleListener extends ScaleGestureDetector.SimpleOnScaleGestureListener {

        @Override
        public boolean onScale(ScaleGestureDetector detector) {

            scale *= detector.getScaleFactor();
            scale = Math.max(0.1f, Math.min(scale, 5.0f));

            return true;
        }
    }
    // Not used, except to create a GestureDetector
    private class TapListener implements GestureDetector.OnGestureListener {
        @Override
        public boolean onDown(MotionEvent e) { return false; }
        @Override
        public void onShowPress(MotionEvent e) { }
        @Override
        public boolean onSingleTapUp(MotionEvent e) { return false; }
        @Override
        public boolean onScroll(MotionEvent e1, MotionEvent e2, float dx, float dy) { return false; }
        @Override
        public void onLongPress(MotionEvent e) {}
        @Override
        public boolean onFling(MotionEvent e1, MotionEvent e2, float vx, float vy) { return false; }
    }

    private class DoubleTapListener implements GestureDetector.OnDoubleTapListener{
        @Override
        public boolean onSingleTapConfirmed(MotionEvent e) {
            Log.d("ORISIM","onSingleTapConfirmed");
            if (running) {
                // Simple tap, switch to pause, if running
                mMainPane.commands.command("pa"); // Pause
                Toast toast = Toast.makeText(mMainPane, "Paused. Tap to continue.", Toast.LENGTH_SHORT);
                toast.show();
            } else {
                // Simple tap continue, if paused
                mMainPane.commands.command("co"); // Continue
                Toast toast = Toast.makeText(mMainPane, "Continue. Tap to pause.", Toast.LENGTH_SHORT);
                toast.show();
            }
            running = !running;
            return true;
        }
        @Override
        public boolean onDoubleTap(MotionEvent e) {
            Log.d("ORISIM","onDoubleTap");
            // Reset view angle and scale
            mAngleX = mAngleY = mAngleZ = 0;
            scale = 1.0f;
            return true;
        }
        @Override
        public boolean onDoubleTapEvent(MotionEvent e) { return false; }
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        // mScaleDetector manages Zoom
        mScaleDetector.onTouchEvent(ev);
        // mDoubleTapDetector manages Tap, DoubleTap
        mDoubleTapDetector.onTouchEvent(ev);

        // Manage Move
        final int action = ev.getAction() & MotionEvent.ACTION_MASK;
        switch (action ) {
            case MotionEvent.ACTION_DOWN: {
                mLastX =  ev.getX();
                mLastY = ev.getY();
                activePointerId = ev.getPointerId(0);
                break;
            }

            case MotionEvent.ACTION_MOVE: {
                final int pointerIndex = ev.findPointerIndex(activePointerId);
                final float x = ev.getX(pointerIndex);
                final float y = ev.getY(pointerIndex);

                // Only rotate if the ScaleGestureDetector isn't processing a gesture.
                if (!mScaleDetector.isInProgress()) {
                    final float dx = (x - mLastX);
                    final float dy = (y - mLastY);

                    // Dividing by 4 enougth to flip 180°
                    mAngleX += dx / 4.0f;
                    mAngleY += dy / 4.0f;
                }
                mLastX = x;
                mLastY = y;
                break;
            }

            case MotionEvent.ACTION_UP: {
                activePointerId = INVALID_POINTER_ID;
                break;
            }

            case MotionEvent.ACTION_CANCEL: {
                activePointerId = INVALID_POINTER_ID;
                break;
            }

            case MotionEvent.ACTION_POINTER_UP: {
                // New API
                final int pointerIndex = (ev.getAction() & MotionEvent.ACTION_POINTER_INDEX_MASK)
                        >> MotionEvent.ACTION_POINTER_INDEX_SHIFT;

                final int pointerId = ev.getPointerId(pointerIndex);
                if (pointerId == activePointerId) {
                    final int newPointerIndex = pointerIndex == 0 ? 1 : 0;
                    mLastX = ev.getX(newPointerIndex);
                    mLastY = ev.getY(newPointerIndex);
                    activePointerId = ev.getPointerId(newPointerIndex);
                }
                break;
            }
        }

        // Don't forget to redraw
        requestRender();

        return true;
    }

    // ------ GLES20 part ------
    // Called by system
    public void onSurfaceCreated(GL10 unused, EGLConfig config) {
        GLES20.glEnable(GLES20.GL_CULL_FACE);
        GLES20.glEnable(GLES20.GL_DEPTH_TEST);
        GLES20.glClearColor(0.68f, 0.8f, 1f, 1); // blue

        initShaders();
        initTextures();
        // setPerspective(width, height); // Will be called by onSurfaceChanged
        // initBuffers(); // Will be called by onDrawFrame, when needed
    }

    // Shaders
    private void initShaders() {
        // Initialize Shaders
        String vertexShader =
        "    attribute vec4 aVertexPosition;\n" +
        "    attribute vec3 aVertexNormal;\n" +
        "    attribute vec2 aTexCoord;\n" +
        "    uniform mat4 uModelViewMatrix;\n" +
        "    uniform mat4 uProjectionMatrix;\n" +
        "    varying vec2 vTexCoord;\n" +
        "    varying vec3 vLight;\n" +
        "    void main(void) {\n" +
        "      gl_Position = uProjectionMatrix * (uModelViewMatrix * aVertexPosition);\n" +
        "      vTexCoord = aTexCoord;\n" +
        "      vec3 lightColor = vec3(0.8, 0.8, 0.8); \n" +
        "      vec3 direction = vec3(0.0, 0.0, 1.0);  \n" +
        "      vec4 normal = normalize(uModelViewMatrix * vec4(aVertexNormal, 1.0));\n" +
        "      vLight = lightColor * dot(normal.xyz, direction);\n" +
        "    }";
        int vxShader = GLES20.glCreateShader(GLES20.GL_VERTEX_SHADER);
        GLES20.glShaderSource(vxShader, vertexShader);
        GLES20.glCompileShader(vxShader);

        String fragmentShader =
        "    precision highp float;\n" +
        "    uniform sampler2D uSampler;\n" +
        "    varying highp vec2 vTexCoord;\n" +
        "    varying highp vec3 vLight;\n" +
        "    void main(void) {\n" +
        "      highp vec4 texelColor = texture2D(uSampler, vTexCoord);\n" +
        "      vec3 normal = texelColor.rgb * vLight;\n" +
        "      vec3 ambiant = texelColor.rgb * 0.5;\n" +
        "      gl_FragColor = vec4(ambiant + normal, 1.0);\n" +
        "    }";
        int fgShader = GLES20.glCreateShader(GLES20.GL_FRAGMENT_SHADER);
        GLES20.glShaderSource(fgShader, fragmentShader);
        GLES20.glCompileShader(fgShader);

        // Create OpenGL ES Program
        program = GLES20.glCreateProgram();
        GLES20.glAttachShader(program, vxShader);
        GLES20.glAttachShader(program, fgShader);

        // Create OpenGL ES program executables
        GLES20.glLinkProgram(program);
    }

    // Textures
    private void initTextures () {
        textures = new int[2];
        GLES20.glGenTextures(2, textures, 0);

        // Create Front texture
        BitmapFactory.Options opts = new BitmapFactory.Options();
        opts.inScaled = false; // Tricky, images are resized on real phone
        Bitmap frontTex = BitmapFactory.decodeResource(mMainPane.getResources(), R.drawable.hulk400x566, opts); // R.drawable.front
        wTexFront = frontTex.getWidth();
        hTexFront = frontTex.getHeight();
        // Bind to textureFront [0]
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textures[0]);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_NEAREST);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);
        GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, frontTex, 0);
        frontTex.recycle();

        // Create Back texture
        Bitmap backTex = BitmapFactory.decodeResource(mMainPane.getResources(), R.drawable.ville822x679, opts); // R.drawable.back
        wTexBack = backTex.getWidth();
        hTexBack = backTex.getHeight();
        // Bind to textureBack [1]
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textures[1]);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_NEAREST);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);
        GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, backTex, 0);
        backTex.recycle();
    }

    // Perspective
    private void setPerspective(int width, int height) {
        // Choose portrait or landscape
        float ratio = (float) width / (float) height;
        float fov = 40;
        float near = 50, far = 1200, top, bottom, left, right;
        if (ratio >= 1.0) {
            top = near * (float) Math.tan(fov * (Math.PI / 360.0));
            bottom = -top;
            left = bottom * ratio;
            right = top * ratio;
        } else {
            right = near * (float) Math.tan(fov * (Math.PI / 360.0));
            left = -right;
            top = right / ratio;
            bottom = left / ratio;
        }

        // Basic frustum at a distance of 700
        Matrix.frustumM(mvp, 0, left, right, bottom, top, near, far);

        // Step back
        mvp[15] += 700;
    }

    // Model view, rotation, and scale
    private void setModelView() {

        // One finger rotates the object
        Matrix.setIdentityM(mvm, 0);
        Matrix.rotateM(mvm, 0, mAngleX, 0, 1, 0); // Yes there is an inversion between X and Y
        Matrix.rotateM(mvm, 0, mAngleY, 1, 0, 0);

        // Two fingers rotate (z) unused
        Matrix.rotateM(mvm, 0, mAngleZ, 0, 0, 1);

        // Handle zoom
        Matrix.scaleM(mvm, 0, scale, scale, scale);
    }

    // Initialize from model
    public void initBuffers(Model model) {
        nbPts = 0;
        for (Face f : model.faces) {
            for (int i = 2; i < f.points.size(); i++)
                nbPts += 3;
        }
        nbPtsLines = 0;
        for (Segment s : model.segments) {
            if (s.select)
                nbPtsLines += 2;
        }
        // Did't manage how to use indexed buffer with GLES20, so plain buffers
        // Front Vertex  "vec3 aVertexPosition" n * 3 coords * 4 bytes
        frontVertex = ByteBuffer.allocateDirect((nbPts) * 3 * 4).order(ByteOrder.nativeOrder()).asFloatBuffer();
        // Back Vertex  "vec3 aVertexPosition" n * 3 coords * 4 bytes
        backVertex = ByteBuffer.allocateDirect((nbPts) * 3 * 4).order(ByteOrder.nativeOrder()).asFloatBuffer();
        // Front normals coords "vec3 aVertexNormal" n * 3 coords * 4 bytes
        frontNormal = ByteBuffer.allocateDirect(nbPts * 3 * 4).order(ByteOrder.nativeOrder()).asFloatBuffer();
        // Back normals coords "vec3 aVertexNormal" n * 3 coords * 4 bytes
        backNormal = ByteBuffer.allocateDirect(nbPts * 3 * 4).order(ByteOrder.nativeOrder()).asFloatBuffer();
        // Front texture coords  "vec2 aTexCoord" n * 2 coords * 2 bytes
        frontTex = ByteBuffer.allocateDirect(nbPts * 2 * 4).order(ByteOrder.nativeOrder()).asFloatBuffer();
        // Back texture coords "vec2 aTexCoord"
        backTex = ByteBuffer.allocateDirect(nbPts * 2 * 4).order(ByteOrder.nativeOrder()).asFloatBuffer();
        // Vertex Line   "vec3 aVertexPosition" n * 3 coords * 4 bytes
        lineVertex = ByteBuffer.allocateDirect((nbPtsLines) * 3 * 4).order(ByteOrder.nativeOrder()).asFloatBuffer();

        // Put Faces
        for (Face f : model.faces) {
            List<Point> pts = f.points;
            f.computeFaceNormal();
            float[] n = f.normal;
            // Triangle FAN can be used only because of convex CCW face
            Point c = pts.get(0); // center don't move
            Point p = pts.get(1); // previous
            for (int i = 2; i < pts.size(); i++) {
                Point s = f.points.get(i); // second point (i starts at 2)
                // front c,p,s back c,s,p
                // First point
                frontVertex.put(c.x + f.offset * n[0]);
                frontVertex.put(c.y + f.offset * n[1]);
                frontVertex.put(c.z + f.offset * n[2]);
                backVertex.put(c.x + f.offset * n[0]);
                backVertex.put(c.y + f.offset * n[1]);
                backVertex.put(c.z + f.offset * n[2]);
                frontNormal.put(n[0]);
                frontNormal.put(n[1]);
                frontNormal.put(n[2]);
                backNormal.put(-n[0]);
                backNormal.put(-n[1]);
                backNormal.put(-n[2]);
                // texture front
                frontTex.put( (200 + c.xf) / wTexFront);
                frontTex.put( 1.0f - (200 + c.yf) / hTexFront);
                // texture back
                backTex.put((200 + c.xf) / wTexBack);
                backTex.put((hTexBack - 200 - c.yf) / hTexBack);

                // Second point
                frontVertex.put(p.x + f.offset * n[0]);
                frontVertex.put(p.y + f.offset * n[1]);
                frontVertex.put(p.z + f.offset * n[2]);
                // Back face put s
                backVertex.put(s.x + f.offset * n[0]);
                backVertex.put(s.y + f.offset * n[1]);
                backVertex.put(s.z + f.offset * n[2]);
                frontNormal.put(n[0]);
                frontNormal.put(n[1]);
                frontNormal.put(n[2]);
                backNormal.put(-n[0]);
                backNormal.put(-n[1]);
                backNormal.put(-n[2]);
                // texture front put p
                frontTex.put((200 + p.xf) / wTexFront);
                frontTex.put( 1.0f - (200 + p.yf) / hTexFront);
                // Back face put s
                backTex.put((200 + s.xf) / wTexBack);
                backTex.put((hTexBack - 200 - s.yf) / hTexBack);

                // Third point
                frontVertex.put(s.x + f.offset * n[0]);
                frontVertex.put(s.y + f.offset * n[1]);
                frontVertex.put(s.z + f.offset * n[2]);
                // Back face put p
                backVertex.put(p.x + f.offset * n[0]);
                backVertex.put(p.y + f.offset * n[1]);
                backVertex.put(p.z + f.offset * n[2]);
                frontNormal.put(n[0]);
                frontNormal.put(n[1]);
                frontNormal.put(n[2]);
                backNormal.put(-n[0]);
                backNormal.put(-n[1]);
                backNormal.put(-n[2]);
                // texture front put s
                frontTex.put((200 + s.xf) / wTexFront);
                frontTex.put( 1.0f - (200 + s.yf) / hTexFront);
                // Back face put p
                backTex.put((200 + p.xf) / wTexBack);
                backTex.put((hTexBack - 200 - p.yf) / hTexBack);

                p = s; // next triangle
            }
        }

        // Put segments in the same vertex buffer, only index is different
        // TODO draw lines
        for (Segment s : model.segments) {
            if (s.select) {
                lineVertex.put(s.p1.x);
                lineVertex.put(s.p1.y);
                lineVertex.put(s.p1.z);

                lineVertex.put(s.p2.x);
                lineVertex.put(s.p2.y);
                lineVertex.put(s.p2.z);
            }
        }

        frontVertex.rewind();
        backVertex.rewind();
        frontNormal.rewind();
        backNormal.rewind();
        frontTex.rewind();
        backTex.rewind();
        lineVertex.rewind();

        needRebuild = false;
    }

    // Called by system
    public void onSurfaceChanged(GL10 unused, int width, int height) {

        // ViewPort
        GLES20.glViewport(0, 0, width, height);

        // Perspective, will not change, stored in mvp
        setPerspective(width, height);
    }

    // Called by system
    public void onDrawFrame(GL10 unused) {
        // Clear and use shader program
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);
        GLES20.glUseProgram(program);
        GLES20.glEnable(GLES20.GL_TEXTURE_2D);
        GLES20.glCullFace(GLES20.GL_BACK);

        // Initialize from model, if needed
        if (needRebuild) {
            initBuffers(mMainPane.model);
        }

        // Set ModelViewMatrix
        setModelView();
        int hmv = GLES20.glGetUniformLocation(program, "uModelViewMatrix");
        GLES20.glUniformMatrix4fv(hmv, 1, false, mvm, 0);

        // Set projection matrix
        int hpm = GLES20.glGetUniformLocation(program, "uProjectionMatrix");
        GLES20.glUniformMatrix4fv(hpm, 1, false, mvp, 0);

        // Front face
        int hfv = GLES20.glGetAttribLocation(program, "aVertexPosition");
        GLES20.glEnableVertexAttribArray(hfv);
        GLES20.glVertexAttribPointer(hfv, 3, GLES20.GL_FLOAT, false, 0, frontVertex); // 3 points, 4 bytes per vertex
        int hfn = GLES20.glGetAttribLocation(program, "aVertexNormal");
        GLES20.glEnableVertexAttribArray(hfn);
        GLES20.glVertexAttribPointer(hfn, 3, GLES20.GL_FLOAT, false, 0, frontNormal); // 3 points, 4 bytes per vertex
        // Front texture
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textures[0]);
        int hSampler = GLES20.glGetUniformLocation(program, "uSampler");
        GLES20.glUniform1i(hSampler, 0);
        int hft = GLES20.glGetAttribLocation(program, "aTexCoord");
        GLES20.glEnableVertexAttribArray(hft);
        GLES20.glVertexAttribPointer(hft, 2, GLES20.GL_FLOAT, false, 0, frontTex); // 2 uv, 4 bytes per uv

        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, nbPts);

        // Back face
        // Same handle with Back Vertex
        int hbv = GLES20.glGetAttribLocation(program, "aVertexPosition");
        GLES20.glEnableVertexAttribArray(hbv);
        GLES20.glVertexAttribPointer(hbv, 3, GLES20.GL_FLOAT, false, 0, backVertex); // 3 points, 4 bytes per vertex
        int hbn = GLES20.glGetAttribLocation(program, "aVertexNormal");
        GLES20.glEnableVertexAttribArray(hbn);
        GLES20.glVertexAttribPointer(hbn, 3, GLES20.GL_FLOAT, false, 0, backNormal); // 3 points, 4 bytes per vertex
        // Back texture, same Sampler, back textcoord
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textures[1]);
        int hbt = GLES20.glGetAttribLocation(program, "aTexCoord");
        GLES20.glEnableVertexAttribArray(hbt);
        GLES20.glVertexAttribPointer(hbt, 2, GLES20.GL_FLOAT, false, 0, backTex); // 2 uv, 4 bytes per uv

        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, nbPts);

        // Should we call glDisableVertexAttribArray ?

        // Calls commands.animationInProgress() to know if anim should continue
        if (mMainPane.commands.anim()){
            needRebuild = true;
            requestRender();
        }

    }
}

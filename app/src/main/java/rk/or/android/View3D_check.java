package rk.or.android;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.opengl.GLUtils;
import android.opengl.Matrix;
import android.util.AttributeSet;
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

import rk.or.Commands;
import rk.or.Face;
import rk.or.Model;
import rk.or.Point;
import rk.or.Segment;

import static rk.or.android.Check.checkCompile;
import static rk.or.android.Check.checkError;
import static rk.or.android.Check.checkLink;

// View 3D with Touch handler to rotate zoom
public class View3D_check extends GLSurfaceView implements GLSurfaceView.Renderer {

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
    private  ScaleGestureDetector mScaleDetector;
    private  GestureDetector mDoubleTapDetector;
    private boolean running = true;

    // Flag to rebuild buffers
    public boolean needRebuild = false;

    // Needed to access model
    public Model model;
    public Commands commands;

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
    private FloatBuffer backgroundVertex, backgroundNormal, backgroundTex;
//    private ShortBuffer backgroundIndex;
//    final int[] buffers = new int[4];


    private int nbPtsLines;
    private FloatBuffer lineVertex;

    // View3D shows and does Rendering
    public View3D_check(Context context) {
        super(context);
        Log.d("ORISIM", "View3D");
        init(context);
    }
    public View3D_check(Context context, AttributeSet attrs) {
        super(context, attrs);
        Log.d("ORISIM", "View3D attrs:"+attrs);
        init(context);
    }
    private void init(Context context) {
        // Create an OpenGL ES 2.0 context
        setEGLContextClientVersion(2);
        setRenderer(this);

        // RENDERMODE_WHEN_DIRTY or RENDERMODE_CONTINUOUSLY ?
        setRenderMode(GLSurfaceView.RENDERMODE_WHEN_DIRTY);

        // ScaleListener
        mScaleDetector = new ScaleGestureDetector(context,  new ScaleListener());
        // SimpleOnGestureListener needed to build a GestureDetector with setOnDoubleTapListener()
        GestureDetector.SimpleOnGestureListener dummy = new GestureDetector.SimpleOnGestureListener();
        mDoubleTapDetector = new GestureDetector( context, dummy);
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

    private class DoubleTapListener implements GestureDetector.OnDoubleTapListener{
        @Override
        public boolean onSingleTapConfirmed(MotionEvent e) {
            if (running) {
                // Simple tap, switch to pause, if running
                View3D_check.this.commands.command("pa"); // Pause
                Toast toast = Toast.makeText(getContext(), "Pause", Toast.LENGTH_SHORT);
                toast.show();
            } else {
                // Simple tap continue, if paused
                View3D_check.this.commands.command("co"); // Continue
                Toast toast = Toast.makeText(getContext(), "Continue", Toast.LENGTH_SHORT);
                toast.show();
            }
            running = !running;
            return true;
        }
        @Override
        public boolean onDoubleTap(MotionEvent e) {
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

                    // Dividing by 4 enough to flip 180°
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
                // Get rid of inspection warning.
                super.performClick();
                break;
            }
        }

        // Don't forget to redraw
        requestRender();

        return true;
    }
    // Get rid of inspection warning.
    @Override
    public boolean performClick(){
        super.performClick();
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
    private void initShaders () {
        Log.e("ORISIM", "initShaders.");

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
        checkCompile(vxShader);

        String fragmentShader =
        "    precision highp float;\n" +
        "    uniform sampler2D uSampler;\n" +
        "    varying highp vec2 vTexCoord;\n" +
        "    varying highp vec3 vLight;\n" +
        "    uniform vec4 uColor;"+
        "    void main(void) { \n" +
        "      highp vec4 texelColor = texture2D(uSampler, vTexCoord);\n" +
        "      vec3 normal = texelColor.rgb * vLight;\n" +
        "      vec3 ambiant = texelColor.rgb * 0.5;\n" +
        "       if (uColor.w == 1.0) { " +
        "           gl_FragColor = uColor; " +
        "       } else { " +
        "           gl_FragColor = vec4(ambiant + normal, 1.0); " +
        "       };\n" +
        "    }";
        int fgShader = GLES20.glCreateShader(GLES20.GL_FRAGMENT_SHADER);
        GLES20.glShaderSource(fgShader, fragmentShader);
        GLES20.glCompileShader(fgShader);
        checkCompile(fgShader);

        // Create OpenGL ES Program
        program = GLES20.glCreateProgram();
        GLES20.glAttachShader(program, vxShader);
        GLES20.glAttachShader(program, fgShader);

        // Create OpenGL ES program executables
        GLES20.glLinkProgram(program);
        checkLink(program);
    }

    // Textures
    private void initTextures () {
        Log.e("ORISIM", "initTextures.");

        textures = new int[3];
        GLES20.glGenTextures(3, textures, 0);

        // Create Front texture
        BitmapFactory.Options opts = new BitmapFactory.Options();
        opts.inScaled = false; // Tricky, images are resized on real phone
        Bitmap frontTex = BitmapFactory.decodeResource(getContext().getResources(), R.drawable.hulk400x566, opts); // R.drawable.front
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
        checkError();

        // Create Back texture
        Bitmap backTex = BitmapFactory.decodeResource(getContext().getResources(), R.drawable.ville400x565, opts); // R.drawable.back
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
        checkError();

        // Create Background texture
        Bitmap backgroundTex = BitmapFactory.decodeResource(getContext().getResources(), R.drawable.background256x256, opts);
        // Bind to texture [2]
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textures[2]);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_NEAREST);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_REPEAT);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_REPEAT);
        GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, backgroundTex, 0);
        backgroundTex.recycle();
        checkError();
    }

    // Perspective
    private void setPerspective(int width, int height) {
        Log.e("ORISIM", "setPerspective.");

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
        Log.e("ORISIM", "setModelView.");

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
    public void initBuffers() {
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

    // Initialize background
    private void initBackground(){
        // Background  (does not depend of number of points, no need to be done in init() )
        // We have only 2 triangles => 6 points, for vertex, normal, texture
        backgroundVertex = ByteBuffer.allocateDirect(6 * 3 * 4).order(ByteOrder.nativeOrder()).asFloatBuffer();
        backgroundNormal = ByteBuffer.allocateDirect(6 * 3 * 4).order(ByteOrder.nativeOrder()).asFloatBuffer();
        backgroundTex = ByteBuffer.allocateDirect(6 * 2 * 4).order(ByteOrder.nativeOrder()).asFloatBuffer();
        // Index Byte Buffer Background
//        backgroundIndex = ByteBuffer.allocateDirect(6 * 2).order(ByteOrder.nativeOrder()).asShortBuffer();

        // Background 2 triangles =  6 Vertex(3), 6 Normal(3)  6 Texture(2)
        float[] vertices = {
                -2000.0f, -2000.0f, -2000.0f,
                2000.0f, 2000.0f, -2000.0f,
                -2000.0f, 2000.0f, -2000.0f,

                -2000.0f, -2000.0f, -2000.0f,
                2000.0f, -2000.0f, -2000.0f,
                2000.0f, 2000.0f, -2000.0f
        };
        float[] texCoords = {
                0, 0,    5, 5,    0, 5,
                0, 0,    5,  0,   5, 5
        };
        float[] normals = {
                0, 0, 1,    0, 0, 1,    0, 0, 1,
                0, 0, 1,    0, 0, 1,    0, 0, 1
        };
//        short[] index = {
//                0,1,2,   3,4,5
//        };
        backgroundVertex.put(vertices).rewind();
        backgroundNormal.put(normals).rewind();
        backgroundTex.put(texCoords).rewind();
//        backgroundIndex.put(index).rewind();

        // Next time, use indexed buffers
//        GLES20.glGenBuffers(4, buffers, 0);
//        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, buffers[0]);
//        GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, backgroundVertex.capacity() * 4, backgroundVertex, GLES20.GL_STATIC_DRAW);
//        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, buffers[1]);
//        GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, backgroundNormal.capacity() * 4, backgroundNormal, GLES20.GL_STATIC_DRAW);
//        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, buffers[2]);
//        GLES20.glBufferData(GLES20.GL_ELEMENT_ARRAY_BUFFER, backgroundTex.capacity() * 4, backgroundTex, GLES20.GL_STATIC_DRAW);
//        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, buffers[3]);
//        GLES20.glBufferData(GLES20.GL_ELEMENT_ARRAY_BUFFER, backgroundIndex.capacity() * 2, backgroundIndex, GLES20.GL_STATIC_DRAW);
//        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0);
//        GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, 0);
    }

    // Called by system
    public void onSurfaceChanged(GL10 unused, int width, int height) {
        Log.e("ORISIM", "onSurfaceChanged.");

        // ViewPort
        GLES20.glViewport(0, 0, width, height);

        // Perspective, will not change, stored in mvp
        setPerspective(width, height);

        // Background, will not change
        initBackground();
    }

    // Called by system
    public void onDrawFrame(GL10 unused) {
        Log.d("ORISIM", "onDrawFrame Model:"+(model !=null)+" needRebuild:"+(needRebuild)+" commands:"+commands);

        // Clear and use shader program
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);
        GLES20.glUseProgram(program);
        GLES20.glEnable(GLES20.GL_TEXTURE_2D);
        GLES20.glCullFace(GLES20.GL_BACK);

        // Initialize from model, if needed
        if (needRebuild) {
            initBuffers();
        }

        // Set projection matrix
        int hpm = GLES20.glGetUniformLocation(program, "uProjectionMatrix");
        GLES20.glUniformMatrix4fv(hpm, 1, false, mvp, 0);

        // Set ModelViewMatrix to identity for background
        Matrix.setIdentityM(mvm, 0);
        int himv = GLES20.glGetUniformLocation(program, "uModelViewMatrix");
        GLES20.glUniformMatrix4fv(himv, 1, false, mvm, 0);

        // Background
        int hbgv = GLES20.glGetAttribLocation(program, "aVertexPosition");
        GLES20.glEnableVertexAttribArray(hbgv);
        GLES20.glVertexAttribPointer(hbgv, 3, GLES20.GL_FLOAT, false, 0, backgroundVertex);
        int hbgn = GLES20.glGetAttribLocation(program, "aVertexNormal");
        GLES20.glEnableVertexAttribArray(hbgn);
        GLES20.glVertexAttribPointer(hbgn, 3, GLES20.GL_FLOAT, false, 0, backgroundNormal);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textures[2]);
        int hBgSampler = GLES20.glGetUniformLocation(program, "uSampler");
        GLES20.glUniform1i(hBgSampler, 0);
        int hbgt = GLES20.glGetAttribLocation(program, "aTexCoord");
        GLES20.glEnableVertexAttribArray(hbgt);
        GLES20.glVertexAttribPointer(hbgt, 2, GLES20.GL_FLOAT, false, 0, backgroundTex); // 2 uv, 4 bytes per uv

        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, 2 * 3);
//        GLES20.glDrawElements(GLES20.GL_TRIANGLE_STRIP, 2 * 3, GLES20.GL_UNSIGNED_SHORT, 0); Next Time use indexed buffers

        // Set ModelViewMatrix with rotation and scale
        setModelView();
        int hmv = GLES20.glGetUniformLocation(program, "uModelViewMatrix");
        GLES20.glUniformMatrix4fv(hmv, 1, false, mvm, 0);

        // Front face
        int hfv = GLES20.glGetAttribLocation(program, "aVertexPosition");
        GLES20.glEnableVertexAttribArray(hfv);
        GLES20.glVertexAttribPointer(hfv, 3, GLES20.GL_FLOAT, false, 0, frontVertex); // 3 coords, 4 bytes per vertex
        checkError();
        int hfn = GLES20.glGetAttribLocation(program, "aVertexNormal");
        GLES20.glEnableVertexAttribArray(hfn);
        GLES20.glVertexAttribPointer(hfn, 3, GLES20.GL_FLOAT, false, 0, frontNormal); // 3 coords, 4 bytes per vertex
        checkError();
        // Front texture
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textures[0]);
        int hSampler = GLES20.glGetUniformLocation(program, "uSampler");
        GLES20.glUniform1i(hSampler, 0);
        int hft = GLES20.glGetAttribLocation(program, "aTexCoord");
        GLES20.glEnableVertexAttribArray(hft);
        GLES20.glVertexAttribPointer(hft, 2, GLES20.GL_FLOAT, false, 0, frontTex); // 2 uv, 4 bytes per uv
        checkError();

        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, nbPts);

        // Back face
        // Same handle with Back Vertex
        int hbv = GLES20.glGetAttribLocation(program, "aVertexPosition");
        GLES20.glEnableVertexAttribArray(hbv);
        GLES20.glVertexAttribPointer(hbv, 3, GLES20.GL_FLOAT, false, 0, backVertex); // 3 coords, 4 bytes per vertex
        checkError();
        int hbn = GLES20.glGetAttribLocation(program, "aVertexNormal");
        GLES20.glEnableVertexAttribArray(hbn);
        GLES20.glVertexAttribPointer(hbn, 3, GLES20.GL_FLOAT, false, 0, backNormal); // 3 coords, 4 bytes per vertex
        checkError();
        // Back texture, same Sampler, back textcoord
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textures[1]);
        int hbt = GLES20.glGetAttribLocation(program, "aTexCoord");
        GLES20.glEnableVertexAttribArray(hbt);
        GLES20.glVertexAttribPointer(hbt, 2, GLES20.GL_FLOAT, false, 0, backTex); // 2 uv, 4 bytes per uv
        checkError();

        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, nbPts);

        // Lines
        // Same Shader GLES20.GL_LINES instead of GLES20.GL_TRIANGLES
        GLES20.glLineWidth(10.0f);
        int hlv = GLES20.glGetAttribLocation(program, "aVertexPosition");
        GLES20.glEnableVertexAttribArray(hlv);
        GLES20.glVertexAttribPointer(hlv, 3, GLES20.GL_FLOAT, true, 0, lineVertex); // 3 coords, 4 bytes per vertex
        // Color Hack to tell the fragment shader to use uColor (w = 1)
        int hlc = GLES20.glGetUniformLocation(program, "uColor");
        GLES20.glUniform4f(hlc, 0.0f, 0.0f, 0.0f, 1.0f);

        GLES20.glDrawArrays(GLES20.GL_LINES, 0, nbPtsLines);

        // Hack to tell the fragment shader not to use uColor (w = 0)
        GLES20.glUniform4f(hlc, 0.0f, 0.0f, 0.0f, 0.0f);

        // Should I call glDisableVertexAttribArray ?
        GLES20.glDisableVertexAttribArray(hfv);
        GLES20.glDisableVertexAttribArray(hbv);
        GLES20.glDisableVertexAttribArray(hfn);
        GLES20.glDisableVertexAttribArray(hbn);
        GLES20.glDisableVertexAttribArray(hft);
        GLES20.glDisableVertexAttribArray(hbt);

//        // Triangle
//        float[] triangleCoords = new float[]{
//                -200.0f,  200.0f, 0.0f, // top
//                -200.0f, -200.0f, 0.0f, // bottom left
//                200.0f, -200.0f, 0.0f   // bottom right
//        };
//        float[] triangleNormal = new float[]{
//                0.0f, 0.0f, 1.0f,
//                0.0f, 0.0f, 1.0f,
//                0.0f, 0.0f, 1.0f
//        };
//        float[] triangleTexture = new float[]{
//                0, 1,
//                0, 0,
//                1, 0
//        };
//        // Vertex
//        FloatBuffer verPos = ByteBuffer.allocateDirect(triangleCoords.length * 4).order(ByteOrder.nativeOrder()).asFloatBuffer();
//        verPos.put(triangleCoords); verPos.rewind();
//        int hVerPos = GLES20.glGetAttribLocation(program, "aVertexPosition");
//        GLES20.glEnableVertexAttribArray(hVerPos);
//        GLES20.glVertexAttribPointer(hVerPos, 3, GLES20.GL_FLOAT, false, 0, verPos); // 3 points, 4 bytes per vertex
//        // Normal
//        FloatBuffer verNorm = ByteBuffer.allocateDirect(triangleCoords.length * 4).order(ByteOrder.nativeOrder()).asFloatBuffer();
//        verNorm.put(triangleNormal); verNorm.rewind();
//        int hVerNorm = GLES20.glGetAttribLocation(program, "aVertexNormal");
//        GLES20.glEnableVertexAttribArray(hVerNorm);
//        GLES20.glVertexAttribPointer(hVerNorm, 3, GLES20.GL_FLOAT, false, 0, verNorm); // 3 points, 4 bytes per vertex
//        // Texture
//        FloatBuffer frontTex = ByteBuffer.allocateDirect(triangleCoords.length * 2 * 4).order(ByteOrder.nativeOrder()).asFloatBuffer();
//        frontTex.put(triangleTexture); frontTex.rewind();
//        int hFrontTexture = GLES20.glGetAttribLocation(program, "aTexCoord");
//        GLES20.glEnableVertexAttribArray(hFrontTexture);
//        GLES20.glVertexAttribPointer(hFrontTexture, 2, GLES20.GL_FLOAT, false, 0, frontTex); // 2 uv, 4 bytes per uv
//        // Draw triangle
//        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, 3);
//        // Disable
//        GLES20.glDisableVertexAttribArray(hVerPos);
//        GLES20.glDisableVertexAttribArray(hVerNorm);
//        GLES20.glDisableVertexAttribArray(hFrontTexture);
        // Call commands.animationInProgress() to know if anim should continue
        if (commands.anim()) {
            needRebuild = true;
            requestRender();
        }
    }

//        CharSequence text = "Hello :" + e.getAction();
//        Toast toast = Toast.makeText(mMainPane, text, Toast.LENGTH_SHORT);
//        toast.show();
}
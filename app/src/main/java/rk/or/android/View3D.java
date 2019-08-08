package rk.or.android;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.ShortBuffer;
import java.util.List;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

import rk.or.Commands;
import rk.or.Face;
import rk.or.Model;
import rk.or.Point;
import rk.or.Segment;

import android.content.Context;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;

import android.view.MotionEvent;

/**
 * View 3D with Touch handler to pause undo rotate zoom
 * Handles double touch with rk.or.TouchHandler
 * This class is also a GLSurfaceView.Renderer
 * See
 * https://developer.android.com/training/graphics/opengl/environment.html
 * https://developer.android.com/training/graphics/opengl/draw
 */
public class View3D extends GLSurfaceView implements GLSurfaceView.Renderer {

    // Mouse Rotation
    private float mAngleX = 0, mAngleY = 0, mAngleZ = 0, mdx = 0, mdy = 0, mdz = 0;

    // Animation in progress and callback
    private boolean animated = false;
    private Commands commands;
    private final TouchHandler touchHandler;

    // View3D shows and does Rendering
    public View3D( ModelView context ) {
        super(context);

        // Create an OpenGL ES 2.0 context
        setEGLContextClientVersion(2);

        // Set the Renderer for drawing on the GLSurfaceView
        setRenderer(this);
        setRenderMode(GLSurfaceView.RENDERMODE_WHEN_DIRTY);

        // Keep reference
        mMainPane = context;

        // Add a touch
        touchHandler = new TouchHandler(context);

    }
    /** Constructor for Android Layout editor */
    public View3D(Context context){
        super(context);
        mMainPane = (ModelView) context;
        touchHandler = new TouchHandler(mMainPane);
    }

    // Rotation called from onTouchEvent
    public void rotateXY(float angleX, float angleY) {
        mAngleX += angleX;
        mAngleY += angleY;
    }

    // Rotation and zoom called from onTouchEvent
    public void rotateZoom(float angle, float dx, float dy, float dd) {
        mAngleZ += angle;
        mdx += dx;
        mdy += dy;
        mdz += dd;
    }

    // Restore rotation to identity
    public void rotateRestore() {
        mAngleX = mAngleY = mAngleZ = mdx = mdy = mdz = 0;
    }

    @Override
    // Forward MotionEvent to a specific class
    public boolean onTouchEvent(MotionEvent e) {
        touchHandler.onTouchEvent(e);
        requestRender();
        performClick();
        return true;
    }

    @Override
    public boolean performClick() {
        return super.performClick();
    }

    // Called by Commands
    public void animate(Commands commands) {
        this.commands = commands;
        animated = true;
        requestRender();
    }

    // ------------------- Rendering ----------------
    // ViewPort dimension
    public int wViewport = 0, hViewPort = 0;
    private final float[] triangleCoords = new float[]{   // in counterclockwise order:
            0.0f, 0.622008459f, 0.0f, // top
            -0.5f, -0.311004243f, 0.0f, // bottom left
            0.5f, -0.311004243f, 0.0f  // bottom right
    };
    private final float[] squareCoords = {
            -0.5f, 0.5f, 0.0f,   // top left
            -0.5f, -0.5f, 0.0f,   // bottom left
            0.5f, -0.5f, 0.0f,   // bottom right
            0.5f, 0.5f, 0.0f};
    private FloatBuffer vertexBufferTriangle;
    private FloatBuffer vertexBufferSquare;

    private final ModelView mMainPane;
    private int mProgram;

    /*
     * Initialize from model
     * Call from onDrawFrame each time the model need to be drawn
     * The buffers are allocated only if the number of points changed
     * The points are copied to the buffers
     *
     * See https://developer.android.com/training/graphics/opengl/shapes
     */
    public void init(Model model, GL10 unused) {

        // Origami part
        int nbPts = 0;
        for (Face f : model.faces) {
            for (int i = 2; i < f.points.size(); i++)
                nbPts += 3;
        }
        int nbPtsLines = 0;
        for (Segment s : model.segments) {
            if (s.select )
                nbPtsLines += 2;
        }

        // Vertex for faces and lines  x 3 coordinates x 4 bytes for float
        ByteBuffer vbb = ByteBuffer.allocateDirect((nbPts + nbPtsLines) * 3 * 4);
        vbb.order(ByteOrder.nativeOrder());
        FloatBuffer mFVertexBuffer = vbb.asFloatBuffer();

        // Index for each point of faces x 2 bytes for short
        ByteBuffer ibbf = ByteBuffer.allocateDirect(nbPts * 2);
        ibbf.order(ByteOrder.nativeOrder());
        ShortBuffer mIndexBufferFront = ibbf.asShortBuffer();
        ByteBuffer ibbb = ByteBuffer.allocateDirect(nbPts * 2);
        ibbb.order(ByteOrder.nativeOrder());
        ShortBuffer mIndexBufferBack = ibbb.asShortBuffer();

        // Index for each point of line x 2 bytes for short
        ByteBuffer ibbl = ByteBuffer.allocateDirect(nbPtsLines * 2);
        ibbl.order(ByteOrder.nativeOrder());
        ShortBuffer mIndexBufferLines = ibbl.asShortBuffer();

        short index = 0;

        // Put Faces
        for (Face f : model.faces) {
            List<Point> pts = f.points;
            f.computeFaceNormal();
            float[] n = f.normal;
            // Triangle FAN can be used only because of convex CCW face
            Point c = pts.get(0); // center
            Point p = pts.get(1); // previous
            for (int i = 2; i < pts.size(); i++) {
                Point s = f.points.get(i); // second
                mFVertexBuffer.put(c.x + f.offset * n[0]);
                mFVertexBuffer.put(c.y + f.offset * n[1]);
                mFVertexBuffer.put(c.z + f.offset * n[2]);

                mIndexBufferFront.put(index);
                mIndexBufferBack.put(index);
                index++;
                mFVertexBuffer.put(p.x + f.offset * n[0]);
                mFVertexBuffer.put(p.y + f.offset * n[1]);
                mFVertexBuffer.put(p.z + f.offset * n[2]);

                mIndexBufferFront.put(index);
                mIndexBufferBack.put((short) (index + 1));
                index++;
                mFVertexBuffer.put(s.x + f.offset * n[0]);
                mFVertexBuffer.put(s.y + f.offset * n[1]);
                mFVertexBuffer.put(s.z + f.offset * n[2]);

                mIndexBufferFront.put(index);
                mIndexBufferBack.put((short) (index - 1));
                index++;
                p = s; // next triangle
            }
        }
        // Put segments in the same vertex buffer, only index is different
        for (Segment s : model.segments) {
            if (s.select ) {
                mFVertexBuffer.put(s.p1.x);
                mFVertexBuffer.put(s.p1.y);
                mFVertexBuffer.put(s.p1.z);
                mIndexBufferLines.put(index++);
                mFVertexBuffer.put(s.p2.x);
                mFVertexBuffer.put(s.p2.y);
                mFVertexBuffer.put(s.p2.z);
                mIndexBufferLines.put(index++);
            }
        }
        mFVertexBuffer.rewind();

        mIndexBufferFront.rewind();
        mIndexBufferBack.rewind();
        mIndexBufferLines.rewind();
    }

    // From GLSurfaceView.Renderer
    // Called once to set up the view's OpenGL ES environment.
    public void onSurfaceCreated(GL10 unused, EGLConfig config) {
        GLES20.glClearColor(0.68f, 0.8f, 1f, 1); // blue

        // Triangle
        // initialize vertex byte buffer for shape coordinates
        ByteBuffer bb = ByteBuffer.allocateDirect(triangleCoords.length * 4);
        // use the device hardware's native byte order
        bb.order(ByteOrder.nativeOrder());
        // create a floating point buffer from the ByteBuffer
        vertexBufferTriangle = bb.asFloatBuffer();
        // add the coordinates to the FloatBuffer
        vertexBufferTriangle.put(triangleCoords);
        // set the buffer to read the first coordinate
        vertexBufferTriangle.position(0);

        // Square
        ShortBuffer drawListBufferSquare;
        short[] drawOrder = {0, 1, 2, 0, 2, 3}; // order to draw vertices
        // initialize vertex byte buffer for shape coordinates
        ByteBuffer bbSquare = ByteBuffer.allocateDirect( squareCoords.length * 4);
        bbSquare.order(ByteOrder.nativeOrder());
        vertexBufferSquare = bbSquare.asFloatBuffer();
        vertexBufferSquare.put(squareCoords);
        vertexBufferSquare.position(0);
        // initialize byte buffer for the draw list
        ByteBuffer dlb = ByteBuffer.allocateDirect( drawOrder.length * 2);
        dlb.order(ByteOrder.nativeOrder());
        drawListBufferSquare = dlb.asShortBuffer();
        drawListBufferSquare.put(drawOrder);
        drawListBufferSquare.position(0);


        // Initialize Shaders
        // see https://developer.android.com/training/graphics/opengl/draw
        // Shaders given with no textures !
        String vertexShaderCode = "attribute vec4 vPosition;" +
                "void main() {" +
                "  gl_Position = vPosition;" +
                "}";
        int vertexShader = GLES20.glCreateShader(GLES20.GL_VERTEX_SHADER);
        GLES20.glShaderSource(vertexShader, vertexShaderCode);
        GLES20.glCompileShader(vertexShader);

        String fragmentShaderCode = "precision mediump float;" +
                "uniform vec4 vColor;" +
                "void main() {" +
                "  gl_FragColor = vColor;" +
                "}";
        int fragmentShader = GLES20.glCreateShader(GLES20.GL_FRAGMENT_SHADER);
        GLES20.glShaderSource(fragmentShader, fragmentShaderCode);
        GLES20.glCompileShader(fragmentShader);


        // Create OpenGL ES Program
        mProgram = GLES20.glCreateProgram();
        GLES20.glAttachShader(mProgram, vertexShader);
        GLES20.glAttachShader(mProgram, fragmentShader);
        // Create OpenGL ES program executables
        GLES20.glLinkProgram(mProgram);

    }

    // Called if the geometry of the view changes
    public void onSurfaceChanged(GL10 unused, int width, int height) {
        GLES20.glViewport(0, 0, width, height);
        float ratio = (float) width / height;
//        Matrix.frustumM(projMatrix, 0, -ratio, ratio, -1, 1, 3, 7);
    }

    // Called for each redraw of the view.
    public void onDrawFrame(GL10 unused) {
        // number of coordinates per vertex in this array
        int COORDS_PER_VERTEX = 3;
        int vertexCount = triangleCoords.length / COORDS_PER_VERTEX;
        int vertexStride = COORDS_PER_VERTEX * 4; // 4 bytes per vertex

        // See https://developer.android.com/training/graphics/opengl/draw
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);

        // Add program to OpenGL ES environment
        GLES20.glUseProgram(mProgram);
        int positionHandle = GLES20.glGetAttribLocation(mProgram, "vPosition");

        // Draw triangle
        GLES20.glEnableVertexAttribArray(positionHandle);
        GLES20.glVertexAttribPointer(positionHandle, COORDS_PER_VERTEX,
                GLES20.GL_FLOAT, false,
                vertexStride, vertexBufferTriangle);
        int colorHandle = GLES20.glGetUniformLocation(mProgram, "vColor");
        float[] yellow = {1.0f, 249.0f/255.0f, 145.0f/255.0f, 1.0f};
        GLES20.glUniform4fv(colorHandle, 1, yellow, 0);
        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, vertexCount);

        // Draw square
        GLES20.glVertexAttribPointer(positionHandle, COORDS_PER_VERTEX,
                GLES20.GL_FLOAT, false,
                vertexStride, vertexBufferSquare);
        float[] blue = {145.0f/255.0f, 199.0f/255.0f, 1.0f, 1.0f};
        GLES20.glUniform4fv(colorHandle, 1, blue, 0);
        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, vertexCount);

        GLES20.glDisableVertexAttribArray(positionHandle);

    }
}

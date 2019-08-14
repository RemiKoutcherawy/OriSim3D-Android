package rk.or.android;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.ShortBuffer;
import java.util.List;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

import static android.opengl.GLES10.*;

import rk.or.Commands;
import rk.or.Face;
import rk.or.Model;
import rk.or.Point;
import rk.or.Segment;
import rk.or.TouchHandler;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.opengl.GLSurfaceView;
import android.opengl.GLU;
import android.opengl.GLUtils;
import android.view.MotionEvent;

/**
 * View 3D with Touch handler to pause undo rotate zoom 
 * Handles double touch with rk.or.TouchHandler
 */
public class View3D extends GLSurfaceView implements GLSurfaceView.Renderer {
  // Mouse Rotation
  private float mAngleX=0, mAngleY=0, mAngleZ=0, mdx=0, mdy=0, mdz=0;
  // Animation in progress and callback
  private boolean animated = false;
  private Commands commands;
  private TouchHandler touchHandler;
  // ViewPort dimension
  public int wViewport = 0, hViewPort = 0;
  // Texture dimensions
  public int wTexFront, hTexFront, wTexBack, hTexBack;
  // Texture resources ID set by ModelSelection
  public static int front, back, background;
  public static boolean texturesON = false;

  /** View3D shows and does Rendering */
  public View3D(ModelView context) {
    super(context);
    mMainPane = context;
    touchHandler = new TouchHandler(context);
  }
  /** Constructor for Android Layout editor */
  public View3D(Context context){
  	super(context);
  }
  /** Rotation called from onTouchEvent */
  public void rotateXY(float angleX, float angleY) {
    mAngleX += angleX; 
    mAngleY += angleY; 
  }
  /** Rotation and zoom called from onTouchEvent */
  public void rotateZoom(float angle, float dx, float dy, float dd) {
    mAngleZ += angle;
    mdx += dx;
    mdy += dy;
    mdz += dd;
  }
  /** Restore rotation to identity */
  public void rotateRestore() {
    mAngleX = mAngleY = mAngleZ = mdx = mdy = mdz = 0;
  }
  @Override
  /** Forward MotionEvent to a specific class */
  public boolean onTouchEvent(MotionEvent e) {
    touchHandler.onTouchEvent(e);
    requestRender();
    return true;
  }
  /** Called by Commands */
  public void animate(Commands commands) {
    this.commands = commands;
    animated = true;
    requestRender();
  }
  // ------------------- Rendering ----------------
  private ModelView mMainPane;
  private int nbPts = 0, nbPtsLines = 0;
  private FloatBuffer mFVertexBuffer, mFNormalsFront, mFNormalsBack;
  private FloatBuffer mBackgroundVertexBuffer, mBackgroundNormals, mBackgroundTexBuffer;
  private FloatBuffer mTexBufferFront, mTexBufferBack; //, mLightAmbiantBuffer;
  private ShortBuffer mIndexBufferFront, mIndexBufferBack, mBackgroundIndexBuffer, mIndexBufferLines;
  private int[] mTexturesBuffer;
  
  /** 
   * Initialize from model
   * Call from onDrawFrame each time the model need to be drawn
   * The buffers are allocated only if the number of points changed
   * The points are copied to the buffers 
   */
  public  void init(Model model, GL10 gl){ 
    nbPts = 0;
    for (Face f : model.faces){
      for (int i = 2; i < f.points.size(); i++)
        nbPts += 3;
    }
    nbPtsLines = 0;
  	for (Segment s : model.segments) {
  		if (s.select || !texturesON)
  			nbPtsLines += 2;
  	}
  	// Vertex for faces and lines  x 3 coordinates x 4 bytes for float
  	ByteBuffer vbb = ByteBuffer.allocateDirect((nbPts + nbPtsLines) * 3 * 4);
  	vbb.order(ByteOrder.nativeOrder());
  	mFVertexBuffer = vbb.asFloatBuffer();

  	// Normals for each point of faces 
  	ByteBuffer nbbf = ByteBuffer.allocateDirect(nbPts * 3 * 4);
  	nbbf.order(ByteOrder.nativeOrder());
  	mFNormalsFront = nbbf.asFloatBuffer();   
  	ByteBuffer nbbb = ByteBuffer.allocateDirect(nbPts * 3 * 4);
  	nbbb.order(ByteOrder.nativeOrder());
  	mFNormalsBack = nbbb.asFloatBuffer();   
  	if (texturesON) {
  		// Texture coordinates for each point of faces x 2 coordinates x 4 bytes
  		ByteBuffer tbbf = ByteBuffer.allocateDirect(nbPts * 2 * 4);
  		tbbf.order(ByteOrder.nativeOrder());
  		mTexBufferFront = tbbf.asFloatBuffer();
  		ByteBuffer tbbb = ByteBuffer.allocateDirect(nbPts * 2 * 4);
  		tbbb.order(ByteOrder.nativeOrder());
  		mTexBufferBack = tbbb.asFloatBuffer();
  	}

  	// Index for each point of faces x 2 bytes for short
  	ByteBuffer ibbf = ByteBuffer.allocateDirect(nbPts * 2);
  	ibbf.order(ByteOrder.nativeOrder());
  	mIndexBufferFront = ibbf.asShortBuffer();
  	ByteBuffer ibbb = ByteBuffer.allocateDirect(nbPts * 2);
  	ibbb.order(ByteOrder.nativeOrder());
  	mIndexBufferBack = ibbb.asShortBuffer();

  	// Index for each point of line x 2 bytes for short
  	ByteBuffer ibbl = ByteBuffer.allocateDirect(nbPtsLines * 2);
  	ibbl.order(ByteOrder.nativeOrder());
  	mIndexBufferLines = ibbl.asShortBuffer();    

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
  			mFNormalsFront.put(n[0]); mFNormalsFront.put(n[1]); mFNormalsFront.put(n[2]);
  			mFNormalsBack.put(-n[0]); mFNormalsBack.put(-n[1]); mFNormalsBack.put(-n[2]);
  			if (texturesON) {
  				mTexBufferFront.put((200 + c.xf)/wTexFront);
  				mTexBufferFront.put((200 + c.yf)/hTexFront);
  				mTexBufferBack.put((200 + c.xf)/wTexBack);
  				mTexBufferBack.put((hTexBack -200 -c.yf)/hTexBack);
  			}
  			mIndexBufferFront.put(index);
  			mIndexBufferBack.put(index);
  			index++;
  			mFVertexBuffer.put(p.x + f.offset * n[0]);
  			mFVertexBuffer.put(p.y + f.offset * n[1]);
  			mFVertexBuffer.put(p.z + f.offset * n[2]);
  			mFNormalsFront.put(n[0]); mFNormalsFront.put(n[1]); mFNormalsFront.put(n[2]);
  			mFNormalsBack.put(-n[0]); mFNormalsBack.put(-n[1]); mFNormalsBack.put(-n[2]);
  			if (texturesON) {
  				mTexBufferFront.put((200 + p.xf)/wTexFront);
  				mTexBufferFront.put((200 + p.yf)/hTexFront);
  				mTexBufferBack.put((200 + p.xf)/wTexBack);
  				mTexBufferBack.put((hTexBack -200 -p.yf)/hTexBack);
  			}
  			mIndexBufferFront.put(index);
  			mIndexBufferBack.put((short) (index+1));
  			index++;
  			mFVertexBuffer.put(s.x + f.offset * n[0]);
  			mFVertexBuffer.put(s.y + f.offset * n[1]);
  			mFVertexBuffer.put(s.z + f.offset * n[2]);
  			mFNormalsFront.put(n[0]); mFNormalsFront.put(n[1]); mFNormalsFront.put(n[2]);
  			mFNormalsBack.put(-n[0]); mFNormalsBack.put(-n[1]); mFNormalsBack.put(-n[2]);
  			if (texturesON) {
  				mTexBufferFront.put((200 + s.xf)/wTexFront);
  				mTexBufferFront.put((200 + s.yf)/hTexFront);
  				mTexBufferBack.put((200 + s.xf)/wTexBack);
  				mTexBufferBack.put((hTexBack -200 -s.yf)/hTexBack);
  			}
  			mIndexBufferFront.put(index);
  			mIndexBufferBack.put((short) (index-1));
  			index++;
  			p = s; // next triangle
  		}
  	}
  	// Put segments in the same vertex buffer, only index is different  
  	for (Segment s : model.segments) {
  		if (s.select || !texturesON) {
  			mFVertexBuffer.put(s.p1.x);  mFVertexBuffer.put(s.p1.y); mFVertexBuffer.put(s.p1.z);
  			mIndexBufferLines.put(index++);
  			mFVertexBuffer.put(s.p2.x);  mFVertexBuffer.put(s.p2.y); mFVertexBuffer.put(s.p2.z);
  			mIndexBufferLines.put(index++);
  		}
  	}
  	mFVertexBuffer.rewind();
  	mFNormalsFront.rewind();
  	mFNormalsBack.rewind();
  	if (texturesON) {
  		mTexBufferFront.rewind();
  		mTexBufferBack.rewind();
  	}
  	mIndexBufferFront.rewind();
  	mIndexBufferBack.rewind();
  	mIndexBufferLines.rewind();
  }

  /** From GLSurfaceView.Renderer */
  public void onSurfaceCreated(GL10 gl, EGLConfig config) {    
//  	gl.glDisable(GL10.GL_DITHER);
  	gl.glEnable(GL10.GL_CULL_FACE);
  	gl.glHint(GL10.GL_PERSPECTIVE_CORRECTION_HINT, GL10.GL_NICEST);
  	gl.glClearColor(0.68f, 0.8f, 1f, 1); // blue
  	gl.glShadeModel(GL10.GL_SMOOTH);
  	loadTextures(gl);

  	// Diffuse by default 1
  	float[] light_diffuse = new float[] { 1.0f, 1.0f, 1.0f, 1.0f }; 
  	ByteBuffer lbbd = ByteBuffer.allocateDirect(light_diffuse.length * 4);
  	lbbd.order(ByteOrder.nativeOrder());
  	FloatBuffer mLightDiffuseBuffer = lbbd.asFloatBuffer();
  	for (int i = 0; i < light_diffuse.length; i++)
  		mLightDiffuseBuffer.put(light_diffuse[i]);
  	mLightDiffuseBuffer.rewind();
  	gl.glLightfv(GL10.GL_LIGHT0, GL10.GL_DIFFUSE, mLightDiffuseBuffer);
  	gl.glEnable(GL10.GL_LIGHT0);

  	// Ambient by default 0
  	float[] light_ambient = new float[] { 1.0f, 1.0f, 1.0f, 1.0f }; 
  	ByteBuffer lbba = ByteBuffer.allocateDirect(light_ambient.length * 4);
  	lbba.order(ByteOrder.nativeOrder());
  	FloatBuffer mLightAmbiantBuffer = lbba.asFloatBuffer();
  	for (int i = 0; i < light_ambient.length; i++)
  		mLightAmbiantBuffer.put(light_ambient[i]);
  	mLightAmbiantBuffer.rewind();
  	gl.glLightfv(GL10.GL_LIGHT1, GL10.GL_AMBIENT, mLightAmbiantBuffer);
  	gl.glEnable(GL10.GL_LIGHT1);

  	gl.glEnable(GL10.GL_LIGHTING);
    
    // Enable
  	gl.glEnable(GL10.GL_DEPTH_TEST);
  	gl.glDepthFunc(GL10.GL_LESS);
  	gl.glClearDepthf(1.0f);

  	// Background  (does not depend of number of points, no need to be done in init() )
  	// We have only 2 triangles => 6 points, for vertex, normal, texture
  	// Vertex Byte Buffer Background = vbbbg
  	ByteBuffer vbbbg = ByteBuffer.allocateDirect(6 * 3 * 4);
  	vbbbg.order(ByteOrder.nativeOrder());
  	mBackgroundVertexBuffer = vbbbg.asFloatBuffer();
  	// Normals Byte Buffer Background
  	ByteBuffer nbbbg = ByteBuffer.allocateDirect(6 * 3 * 4);
  	nbbbg.order(ByteOrder.nativeOrder());
  	mBackgroundNormals = nbbbg.asFloatBuffer(); 
  	// Texture Byte Buffer Background
  	ByteBuffer tbbbg = ByteBuffer.allocateDirect(6 * 2 * 4);
  	tbbbg.order(ByteOrder.nativeOrder());
  	mBackgroundTexBuffer = tbbbg.asFloatBuffer();
  	// Index Byte Buffer Background
  	ByteBuffer ibbbg = ByteBuffer.allocateDirect(6 * 2);
  	ibbbg.order(ByteOrder.nativeOrder());
  	mBackgroundIndexBuffer = ibbbg.asShortBuffer(); 
  	
  	// Background 2 triangles =  6 Vertex(3), 6 Normal(3)  6 Texture(2)
  	float vertices[] = {
  	    -2000.0f, -2000.0f, -2000.0f,
  	    2000.0f, 2000.0f, -2000.0f,
  	    -2000.0f, 2000.0f, -2000.0f,
  	    
  	    -2000.0f, -2000.0f, -2000.0f,
  	    2000.0f, -2000.0f, -2000.0f,
  	    2000.0f, 2000.0f, -2000.0f
  	};
  	float texCoords[] = {
  	    0, 0,    5, 5,    0, 5,
  	    0, 0,    5,  0,   5, 5
  	};
  	float normals[] = {
  	    0, 0, 1,    0, 0, 1,    0, 0, 1,
  	    0, 0, 1,    0, 0, 1,    0, 0, 1
  	};
  	short index[] = {
  			0,1,2,   3,4,5
  	};
    for (float v : vertices)
    	mBackgroundVertexBuffer.put(v);
    for (float t : texCoords)
    	mBackgroundTexBuffer.put(t);
    for (float n : normals)
    	mBackgroundNormals.put(n);
    for (short i : index)
    	mBackgroundIndexBuffer.put(i);

  	mBackgroundVertexBuffer.rewind();
  	mBackgroundTexBuffer.rewind();
  	mBackgroundNormals.rewind();
  	mBackgroundIndexBuffer.rewind();
  }
  
  /**
   * Return true if not a power of two 
   */
	private boolean isNotPower2(int n) {
	  return 2*n != (n ^ (n-1)) + 1;
  }
  /**
   * Find the smallest power of two >= the input value.
   */
  private int roundUpPower2(int x) {
      x = x - 1;
      x = x | (x >> 1);
      x = x | (x >> 2);
      x = x | (x >> 4);
      x = x | (x >> 8);
      x = x | (x >>16);
      return x + 1;
  }
  /** Load textures and making them power of two */
  private void loadTextures(GL10 gl) {
    // Create textures
    gl.glEnable(GL10.GL_TEXTURE_2D);
    gl.glEnableClientState(GL_TEXTURE_COORD_ARRAY);

    mTexturesBuffer = new int[3];
    gl.glGenTextures(3, mTexturesBuffer, 0);
    // To scale textures upside down
    Matrix m = new Matrix(); 
    m.setScale(1, -1);
    
    // Setup texture 0 FRONT
    BitmapFactory.Options opts = new BitmapFactory.Options();
    opts.inScaled = false; // Tricky, images are resized on real phone
    Bitmap texture = BitmapFactory.decodeResource(mMainPane.getResources(), front, opts); // R.drawable.front
    wTexFront = texture.getWidth();
    hTexFront = texture.getHeight();
    // Check if w or h is not a power of two, then round up to next POT
    if ( isNotPower2(wTexFront) || isNotPower2(hTexFront)){
    	// Extend dimensions to power of two
      int h = roundUpPower2(hTexFront);
      int w = roundUpPower2(wTexFront);
      // Create new bitmap 
      Bitmap tex = Bitmap.createBitmap(w, h, texture.getConfig());
      Canvas canvas = new Canvas(tex);
      tex.eraseColor(0xFFFFFFFF);
      // Draw at the bottom
      canvas.drawBitmap(texture, 0, h-hTexFront, null);
      texture = tex;
      wTexFront = w;
      hTexFront = h;
    }
    // Use m matrix to switch upside down, the image will be cropped at the top
    texture = Bitmap.createBitmap(texture, 0, 0, texture.getWidth(), texture.getHeight(), m, true); // m upside down, no dpi filter
    gl.glBindTexture(GL10.GL_TEXTURE_2D, mTexturesBuffer[0]);
    gl.glTexParameterx(GL10.GL_TEXTURE_2D, GL10.GL_TEXTURE_MAG_FILTER,GL10.GL_LINEAR);
    gl.glTexParameterx(GL10.GL_TEXTURE_2D, GL10.GL_TEXTURE_MIN_FILTER,GL10.GL_NEAREST);
    gl.glTexParameterx(GL10.GL_TEXTURE_2D, GL10.GL_TEXTURE_WRAP_S,GL10.GL_REPEAT);
    gl.glTexParameterx(GL10.GL_TEXTURE_2D, GL10.GL_TEXTURE_WRAP_T,GL10.GL_REPEAT);
    GLUtils.texImage2D(GL10.GL_TEXTURE_2D, 0, texture, 0);
    // free bitmap
    texture.recycle();
    
    // Setup texture 1 BACK
    texture = BitmapFactory.decodeResource(mMainPane.getResources(), back, opts); // R.drawable.back
    wTexBack = texture.getWidth();
    hTexBack = texture.getHeight();
    // Check if w is not a power of two 
    if ( 2*wTexBack != (wTexBack ^ (wTexBack-1)) + 1){
    	// Extend dimensions to power of two
      int h = roundUpPower2(hTexBack);
      int w = roundUpPower2(wTexBack);
      // Create new bitmap 
      Bitmap tex = Bitmap.createBitmap(w, h, texture.getConfig());
      Canvas canvas = new Canvas(tex);
      tex.eraseColor(0xFFFFFFFF);
      // Draw at the top
      canvas.drawBitmap(texture, 0, 0, null);
      texture = tex;
      wTexBack = w;
      hTexBack = h;
    }
    // Use m matrix to switch upside down, the image will be cropped at the bottom by U,V
    texture = Bitmap.createBitmap(texture, 0, 0, wTexBack, hTexBack, m, true);
    gl.glBindTexture(GL10.GL_TEXTURE_2D, mTexturesBuffer[1]);
    gl.glTexParameterx(GL10.GL_TEXTURE_2D, GL10.GL_TEXTURE_MAG_FILTER,GL10.GL_LINEAR);
    gl.glTexParameterx(GL10.GL_TEXTURE_2D, GL10.GL_TEXTURE_MIN_FILTER,GL10.GL_NEAREST);
    gl.glTexParameterx(GL10.GL_TEXTURE_2D, GL10.GL_TEXTURE_WRAP_S,GL10.GL_REPEAT);
    gl.glTexParameterx(GL10.GL_TEXTURE_2D, GL10.GL_TEXTURE_WRAP_T,GL10.GL_REPEAT);
    GLUtils.texImage2D(GL10.GL_TEXTURE_2D, 0, texture, 0);
    // free bitmap
    texture.recycle();
    
    // Setup texture 2 Background
    texture = BitmapFactory.decodeResource(mMainPane.getResources(), background, opts); // R.drawable.background
    texture = Bitmap.createBitmap(texture, 0, 0, texture.getWidth(), texture.getHeight(), m, true);
    gl.glBindTexture(GL10.GL_TEXTURE_2D, mTexturesBuffer[2]);
    gl.glTexParameterx(GL10.GL_TEXTURE_2D, GL10.GL_TEXTURE_MAG_FILTER,GL10.GL_LINEAR);
    gl.glTexParameterx(GL10.GL_TEXTURE_2D, GL10.GL_TEXTURE_MIN_FILTER,GL10.GL_NEAREST);
    gl.glTexParameterx(GL10.GL_TEXTURE_2D, GL10.GL_TEXTURE_WRAP_S,GL10.GL_REPEAT);
    gl.glTexParameterx(GL10.GL_TEXTURE_2D, GL10.GL_TEXTURE_WRAP_T,GL10.GL_REPEAT);
    GLUtils.texImage2D(GL10.GL_TEXTURE_2D, 0, texture, 0);
    // free bitmap
    texture.recycle();  
  }

  public void onSurfaceChanged(GL10 gl, int w, int h) {    
    wViewport = w; 
    hViewPort = h;

    // ViewPort
    gl.glViewport(0, 0, w, h);
    // Projection
    gl.glMatrixMode(GL10.GL_PROJECTION);
    gl.glLoadIdentity();
    //  GLU.gluPerspective(gl, 30, ratio, 1, 4000); Android source Bugged !! Should be :
    //  GLU.gluPerspective(gl, 30, (float) w /h, 1, 4000);
    //  600 1200 semble OK mais le zoom coupe l'image...
    float ratio = (float) w / h, fov = 30.0f, near = 60, far = 12000, top, bottom, left, right;
    if (ratio >= 1.0f){
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
    gl.glFrustumf(left, right, bottom, top, near, far);
    gl.glPushMatrix();
  }
  
  public void onDrawFrame(GL10 gl) {
  	// Initialize from model
    init(mMainPane.model, gl);
    if (nbPts == 0)
      return;
    
    // Textures 
    gl.glTexEnvx(GL_TEXTURE_ENV, GL_TEXTURE_ENV_MODE, GL_MODULATE);
    gl.glEnableClientState(GL10.GL_TEXTURE_COORD_ARRAY);
    gl.glEnable(GL10.GL_TEXTURE_2D);
    gl.glEnable(GL10.GL_LIGHTING);
  	gl.glEnableClientState(GL10.GL_VERTEX_ARRAY);
  	gl.glEnableClientState(GL10.GL_NORMAL_ARRAY);

    gl.glClear(GL10.GL_COLOR_BUFFER_BIT | GL10.GL_DEPTH_BUFFER_BIT);
    gl.glFrontFace(GL10.GL_CCW);
    
    gl.glMatrixMode(GL10.GL_PROJECTION);
    gl.glPopMatrix();
    gl.glPushMatrix();
    gl.glMatrixMode(GL10.GL_MODELVIEW);
    gl.glLoadIdentity();
    GLU.gluLookAt(gl, 0, 0, 900f, 0f, 0f, 0f, 0f, 1.0f, 0.0f);
        
    // Background always textured
    gl.glBindTexture(GL10.GL_TEXTURE_2D, mTexturesBuffer[2]);
    gl.glTexCoordPointer(2, GL10.GL_FLOAT, 0, mBackgroundTexBuffer);
    gl.glVertexPointer(3, GL10.GL_FLOAT, 0, mBackgroundVertexBuffer);
    gl.glNormalPointer(GL10.GL_FLOAT, 0, mBackgroundNormals);
    gl.glDrawElements(GL10.GL_TRIANGLES, 6, GL10.GL_UNSIGNED_SHORT, mBackgroundIndexBuffer);
    
    // Handle finger rotate on the object
    gl.glRotatef(mAngleX, 0, 1, 0); // Yes there is an inversion between X and Y
    gl.glRotatef(mAngleY, 1, 0, 0);
    // Handle finger zoom, move rotate on
    gl.glMatrixMode(GL10.GL_PROJECTION);
    gl.glPopMatrix();
    gl.glPushMatrix();
    gl.glRotatef(mAngleZ, 0, 0, 1);
    gl.glTranslatef(mdx, mdy, mdz);
    // Back to Modelview
    gl.glMatrixMode(GL10.GL_MODELVIEW);

    // Front face
    if (texturesON) {
      gl.glBindTexture(GL10.GL_TEXTURE_2D, mTexturesBuffer[0]);
      gl.glTexCoordPointer(2, GL10.GL_FLOAT, 0, mTexBufferFront);
    } else {
    	gl.glDisable(GL10.GL_LIGHTING);
      gl.glDisable(GL10.GL_TEXTURE_2D);
    	gl.glColor4f(145.0f/255.0f, 199.0f/255.0f, 1.0f, 1.0f); // rgba => blue
    }
    gl.glVertexPointer(3, GL10.GL_FLOAT, 0, mFVertexBuffer);
    gl.glNormalPointer(GL10.GL_FLOAT, 0, mFNormalsFront);
    gl.glDrawElements(GL10.GL_TRIANGLES, nbPts, GL10.GL_UNSIGNED_SHORT, mIndexBufferFront);
    
    // Back face
    if (texturesON) {
      gl.glBindTexture(GL10.GL_TEXTURE_2D, mTexturesBuffer[1]);
      gl.glTexCoordPointer(2, GL10.GL_FLOAT, 0, mTexBufferBack);
    } else {
    	gl.glColor4f(1.0f, 249.0f/255.0f, 145.0f/255.0f, 1.0f); // rgba => yellow
    }
    gl.glVertexPointer(3, GL10.GL_FLOAT, 0, mFVertexBuffer);
    gl.glNormalPointer(GL10.GL_FLOAT, 0, mFNormalsBack);
    gl.glDrawElements(GL10.GL_TRIANGLES, nbPts, GL10.GL_UNSIGNED_SHORT, mIndexBufferBack);

    // Lines - a mess to get black lines => no texture no light
    gl.glColor4f(0.0f, 0.0f, 0.0f, 1.0f); // rgba => black
  	gl.glDisable(GL10.GL_TEXTURE_2D);
  	gl.glDisable(GL10.GL_LIGHTING);
    gl.glLineWidth(3.0f);
    gl.glClear(GL10.GL_DEPTH_BUFFER_BIT); // See through faces
    gl.glVertexPointer(3, GL10.GL_FLOAT, 0, mFVertexBuffer);
    gl.glDrawElements(GL10.GL_LINES, nbPtsLines, GL10.GL_UNSIGNED_SHORT, mIndexBufferLines);
    
    if (animated == true) { 
      animated = commands.anim();
      requestRender();
    }
  }
}

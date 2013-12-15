package rk.or;
import android.view.MotionEvent;
import rk.or.android.ModelView;

/** 
 * Help View3D to deal with TouchEvents 
 */
public class TouchHandler {
  private ModelView mMainPane;
  private float[] lastXY ={0,0, 0,0};
  private long lastDownTime, lastUpTime, lastUpUpTime;
  boolean wasRunning = false;
  private float lastX, lastY;

  /** Constructor only need mMainPane */
  public TouchHandler(ModelView context) {
    super();
    this.mMainPane = context;
  }

  /** Deal with simple double touch, simple double triple tap */
  public boolean onTouchEvent(MotionEvent e) {
  	// We receive only Pointer Count 1 or 2 and
  	// ACTION_DOWN(0) ACTION_UP(1) ACTION_MOVE(2) 
  	// ACTION_POINTER_2_DOWN(261) ACTION_POINTER_2_UP(262);
  	// One finger Up or Down 
    if (e.getPointerCount() == 1){
  		// One Pointer up 
  		if (e.getAction() == MotionEvent.ACTION_UP){ 
  			// Triple tap undo - UpUpTime is set by double tap
  			if ((System.currentTimeMillis() - lastUpUpTime) < 500){
  				mMainPane.commands.command("u");
  			} 
  			// Double tap restore rotation and zoom fit
  			else if ((System.currentTimeMillis() - lastUpTime) < 500){	
  				lastUpUpTime = System.currentTimeMillis();
  				mMainPane.view3d.rotateRestore();
  				mMainPane.commands.command("zf");
  			} 
  			// Simple tap continue, if we we were not already running and paused by touch down
  			else	if ((System.currentTimeMillis() - lastDownTime) < 500){
  				lastUpTime = System.currentTimeMillis();
  				if (!wasRunning){
  					mMainPane.commands.command("co");
  				}
  			}
  		}  		
  		// One first touch, switch to pause
  		else if (e.getAction() == MotionEvent.ACTION_DOWN) {
  			lastDownTime = System.currentTimeMillis();
  			if (mMainPane.commands.state == Commands.State.run
  					|| mMainPane.commands.state == Commands.State.anim) {
  				wasRunning = true;
  				mMainPane.commands.command("pa");
  			} else 
  				// We were already in pause, touch up should continue
  				wasRunning = false;
  		}
  		// Rotate with one finger
  		else if ((e.getAction() & MotionEvent.ACTION_MOVE) == MotionEvent.ACTION_MOVE){
  			float dx = (e.getX() - lastX) * 180.0f / 320;
  			float dy = (e.getY() - lastY) * 180.0f / 320;
  			mMainPane.view3d.rotateXY(dx, dy); // dx, dy
  		}
			lastX = e.getX();
			lastY = e.getY();
  	}
  	// Two fingers Zoom, Translate, Rotates around Z axis
  	else if (e.getPointerCount() == 2){
  		// First second pointer down
  		if (e.getAction() == MotionEvent.ACTION_POINTER_2_DOWN) {
  			lastDownTime = System.currentTimeMillis();
  		}
  		// Touch and tap, undo, and restore view
  		// Second pointer up short after pointer down
  		else if (e.getAction() == MotionEvent.ACTION_POINTER_2_UP
  				&& (System.currentTimeMillis() - lastDownTime) < 500){
  			mMainPane.commands.command("u");
  			mMainPane.view3d.rotateRestore();
  		}
  		// Zoom rotate with two fingers
  		// Not the first second pointer down, not pointer up
  		else {
  			// Delta distance
  			float vx0 = lastXY[2] - lastXY[0];
  			float vy0 = lastXY[3] - lastXY[1];
  			float vx1 = e.getX(1) - e.getX(0);
  			float vy1 = e.getY(1) - e.getY(0);
  			float lastd = (float) Math.sqrt(vx0*vx0+vy0*vy0);
  			float d = (float) Math.sqrt(vx1*vx1+vy1*vy1);
  			float dd = (d - lastd)*2; // arbitraire
  			// Delta Center
  			float dx = ((e.getX(1) + e.getX(0))-(lastXY[2] + lastXY[0])) /2;
  			float dy = ((e.getY(1) + e.getY(0))-(lastXY[3] + lastXY[1])) /2;
  			// Delta angle
  			float cz = vx1*vy0-vy1*vx0; // Cross product = v0 v1 sin
  			float sp = vx0*vx1+vy0*vy1; // Scalar product = v0 v1 cos
  			float v0v1 = (float) (Math.sqrt(vx0*vx0+vy0*vy0) * Math.sqrt(vx1*vx1+vy1*vy1));
  			float sin = cz / v0v1;
  			float cos = sp / v0v1;
  			if (cos > 1.0f )
  				cos = 1.0f;
  			if (cos < -1.0f )
  				cos = -1.0f;
  			float angle = (float)(Math.acos(cos) * 180/Math.PI);
  			if (sin < 0)  
  				angle = -angle;

  			// Send to commands ? 
  			// Simple but fail because all zooming will be recorded for undo
  			//        String cde = "z "+dd+" "+dx+" "+(-dy)+" tz "+angle;          
  			//        ((MainPane) (view3d.getParent())).commands.command(cde);
  			//        view3d.mainPanel.console.println(cde);
  			// note -dy because Y axis is downward, origin is top left.
  			mMainPane.view3d.rotateZoom(angle, dx, -dy, dd);
  		} 
  		lastXY[0]=e.getX(0); 
  		lastXY[1]=e.getY(0);
  		lastXY[2]=e.getX(1);
  		lastXY[3]=e.getY(1);
  	} 
  	return true;
  }                                                                                             
}

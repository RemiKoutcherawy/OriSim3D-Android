package rk.or.android;

import rk.or.Commands;
import rk.or.Model;
import android.app.Activity;
import android.opengl.GLSurfaceView;
import android.os.Bundle;
import android.view.Window;

/**
 * Activity for model view in 3D 
 */
public class ModelView extends Activity {
  public Commands commands;
  public View3D view3d;
  public Model model;
  public boolean paused = false;
  
  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    requestWindowFeature(Window.FEATURE_NO_TITLE);
    // Model
    model = new Model();
    // Commands
    commands = new Commands(this);
    // View3D
    view3d = new View3D(this);
    view3d.setEGLConfigChooser(true);
    view3d.setRenderer(view3d);
    view3d.setRenderMode(GLSurfaceView.RENDERMODE_WHEN_DIRTY);

    setContentView(view3d);
    view3d.requestFocus();
    view3d.setFocusableInTouchMode(true);
    
    String modelName = getIntent().getStringExtra("model");
    if (modelName == null)
    	modelName ="cocotte.txt";
    commands.command("read "+modelName); 
  }
  @Override
  protected void onResume() {
      super.onResume();
      view3d.onResume();
  }

  @Override
  protected void onPause() {
      super.onPause();
      view3d.onPause();
  }
}

package rk.or.app.android;

import rk.or.app.Commands;
import rk.or.app.Model;
import rk.or.app.R;

import android.app.Activity;
import android.content.Context;
import android.os.Bundle;
import android.view.Window;

/**
 * Activity for model view in 3D
 * See https://developer.android.com/training/graphics/opengl/environment.html
 */
public class ModelView extends Activity {

  // GLSurfaceView
  public View3D view3d;
  public Model model;
  public Commands commands;
  public boolean paused = false;

  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    requestWindowFeature(Window.FEATURE_NO_TITLE);

    // Model
    model = new Model();

    // Commands
    commands = new Commands(this);

    // Create a GLSurfaceView (View3D) and set it as ContentView
    view3d = new View3D(this);

    setContentView(view3d);

    view3d.requestFocus();
    view3d.setFocusableInTouchMode(true);

    String modelName = getIntent().getStringExtra("model");
    if (modelName == null) {
      modelName = "cocotte.txt";
    }

    // Ne marche pas vu que le jar ne contient plus de ressources
//    commands.command("read "+modelName);
    String text = getText(R.string.cocotte).toString();
//    Context context = getApplicationContext();
//    CharSequence text = "Hello toast!";
//    Toast toast = Toast.makeText(context, text, Toast.LENGTH_SHORT);
//    toast.show();
    commands.command(text);
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

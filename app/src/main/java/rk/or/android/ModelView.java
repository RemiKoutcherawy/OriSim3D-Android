package rk.or.android;

import rk.or.Commands;
import rk.or.Model;

import android.app.Activity;
import android.content.Context;
import android.content.res.Resources;
import android.os.Bundle;
import android.view.Window;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

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
        View3D.needRebuild = true;
        setContentView(view3d);

        // Get the model and update view3d
        int modelCode = getIntent().getIntExtra("model", R.raw.cocotte);

        // Ne marche pas vu que le jar ne contient plus de ressources
        // commands.command("read "+modelName);
        // N'accepte pas les apostrophes
        // String text = getText(R.string.cocotte).toString();
        // Donc en raw
        String text = readRawText(getApplicationContext(), modelCode);
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

    public static String readRawText(Context ctx, int id) {
        InputStream inputStream = ctx.getResources().openRawResource(id);
        InputStreamReader inputreader = new InputStreamReader(inputStream);
        BufferedReader buffreader = new BufferedReader(inputreader);
        String line;
        StringBuilder sb = new StringBuilder();

        try {
            while ((line = buffreader.readLine()) != null) {
                sb.append(line);
                sb.append('\n');
            }
        } catch (IOException e) {
            return null;
        }
        return sb.toString();
    }
}

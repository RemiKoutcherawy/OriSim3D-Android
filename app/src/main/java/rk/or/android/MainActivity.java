package rk.or.android;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.app.Activity;
import android.content.Context;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageButton;
import android.widget.LinearLayout;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

import rk.or.Commands;
import rk.or.Model;

public class MainActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        View3D view3d = findViewById(R.id.view3d);

        final ImageButton up = findViewById(R.id.ArrowUp);
        final ImageButton down = findViewById(R.id.ArrowDown);
        final LinearLayout menu = findViewById(R.id.BottomMenu);

        // Default
        up.setVisibility(View.GONE);
        menu.setVisibility(View.VISIBLE);

        // Animation
        final long delay = 300;
        up.setOnClickListener(view1 -> {
            up.setVisibility(View.GONE);
            menu.setVisibility(View.VISIBLE);
            ObjectAnimator.ofFloat(menu, "translationY", 200.0f, 0.0f).setDuration(delay).start();
        });
        down.setOnClickListener(view2 ->{
            ObjectAnimator animMenu = ObjectAnimator.ofFloat(menu, "translationY", 0f, 200.0f).setDuration(delay);
            animMenu.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    menu.setVisibility(View.GONE);
                    up.setVisibility(View.VISIBLE);
                    animation.removeListener(this);
                }
            });
            animMenu.start();
        });

        // Model
        Model model = new Model();
        view3d.needRebuild = true;
        view3d.model = model;

        // Commands
        Commands commands = new Commands();
        commands.model = model;
        commands.view3d = view3d;
        view3d.commands = commands;

        // Models
        findViewById(R.id.Cocotte).setOnClickListener(v-> launch(R.raw.cocotte, commands));
        findViewById(R.id.Austria).setOnClickListener(v-> launch(R.raw.austria, commands));
        findViewById(R.id.Boat).setOnClickListener(v-> launch(R.raw.boat, commands));
        findViewById(R.id.Duck).setOnClickListener(v-> launch(R.raw.duck, commands));
    }

    // Launch a simulation via commands
    private void launch(int id, Commands commands) {
        // Hide menu
        findViewById(R.id.BottomMenu).setVisibility(View.GONE);
        findViewById(R.id.ArrowUp).setVisibility(View.VISIBLE);

        // Stop current
        commands.state = Commands.State.idle;

        // Read and start simulation
        String text = readRawText(getApplicationContext(), id);
        commands.command(text);
    }

    // Read raw text in res/raw
    private static String readRawText(Context ctx, int id) {
        InputStream inputStream = ctx.getResources().openRawResource(id);
        InputStreamReader inputReader = new InputStreamReader(inputStream);
        BufferedReader bufferedReader = new BufferedReader(inputReader);
        String line;
        StringBuilder sb = new StringBuilder();
        try {
            while ((line = bufferedReader.readLine()) != null) {
                sb.append(line);
                sb.append('\n');
            }
        } catch (IOException e) {
            return null;
        }
        return sb.toString();
    }
}

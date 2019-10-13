package rk.or.android;

import android.app.Activity;
import android.os.Looper;

import androidx.test.rule.ActivityTestRule;

import org.junit.Rule;
import org.junit.Test;

import rk.or.Model;

import static org.junit.Assert.assertNotNull;

public class View3DTest {
    @Rule
    public ActivityTestRule<MainActivity> activityRule =
            new ActivityTestRule<>(MainActivity.class);

    @Test
    public void init() {
        Activity activity = activityRule.getActivity();
        assertNotNull(activity);

        Looper.prepare();

        MainActivity mainActivity = (MainActivity) activity;
        View3D view3d = new View3D(mainActivity);
        assertNotNull(view3d);

        Model model = new Model();
        model.init(-200, -200, 200, -200, 200, 200, -200, 200);
        assertNotNull(model);

        view3d.model = model;
        view3d.initBuffers();
        assertNotNull(view3d);

        view3d.onSurfaceCreated(null, null);
        assertNotNull(view3d);
    }

}
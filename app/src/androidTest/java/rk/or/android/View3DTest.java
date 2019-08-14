package rk.or.android;

import android.app.Activity;
import android.util.Log;

import androidx.test.rule.ActivityTestRule;

import org.junit.Rule;
import org.junit.Test;

import rk.or.Commands;
import rk.or.Model;

import static org.junit.Assert.assertNotNull;

public class View3DTest {
    @Rule
    public ActivityTestRule<ModelView> activityRule =
            new ActivityTestRule<>(ModelView.class);

    @Test
    public void init() {
        Activity activity = activityRule.getActivity();
        assertNotNull(activity);

        ModelView modelView = (ModelView) activity;
        View3D view3d = new View3D(modelView);
        assertNotNull(view3d);

        Model model = new Model();
        model.init(-200, -200, 200, -200, 200, 200, -200, 200);
        assertNotNull(model);

        view3d.initBuffers(model);
        assertNotNull(view3d);

        view3d.onSurfaceCreated(null, null);
        assertNotNull(view3d.width);
    }

}
package rk.or;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;

import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.rule.ActivityTestRule;

import org.junit.Rule;
import org.junit.Test;

import rk.or.android.ModelView;

import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static junit.framework.Assert.assertEquals;
import static org.junit.Assert.*;

public class CommandTest {

    @Rule
    public ActivityTestRule<ModelView> activityRule =
            new ActivityTestRule<>(ModelView.class);

    @Test
    public void command() {
        ModelView modelView = (ModelView) activityRule.getActivity();
        assertNotNull(modelView);
        Commands c = new Commands(modelView);
        assertNotNull(c);
        assertEquals(Commands.State.idle, c.state);
        c.command("a b t 2000 f 4 180 0) mo 2 0");
        assertEquals(12,c.mainPane.model.points.size());
    }
}
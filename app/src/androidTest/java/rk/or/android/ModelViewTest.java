package rk.or.android;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import androidx.test.espresso.ViewInteraction;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.rule.ActivityTestRule;

import org.junit.Rule;
import org.junit.Test;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static junit.framework.Assert.assertEquals;
import static org.junit.Assert.*;

public class ModelViewTest {

    @Rule
    public ActivityTestRule<ModelView> activityRule =
            new ActivityTestRule<>(ModelView.class);

    @Test
    public void creation() {
        Context appContext = InstrumentationRegistry.getInstrumentation().getTargetContext();
        assertEquals("rk.or.android", appContext.getPackageName());

        Activity activity = activityRule.getActivity();
        assertNotNull(activity);
        Intent intent = activity.getIntent();
        assertEquals("intent:", "android.intent.action.MAIN", intent.getAction());
        // And now, how to test click ?
    }
}
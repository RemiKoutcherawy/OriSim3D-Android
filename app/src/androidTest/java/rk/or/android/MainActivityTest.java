package rk.or.android;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;

import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.rule.ActivityTestRule;

import org.junit.Rule;
import org.junit.Test;

import static junit.framework.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class MainActivityTest {

    @Rule
    public ActivityTestRule<MainActivity> activityRule =
            new ActivityTestRule<>(MainActivity.class);

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
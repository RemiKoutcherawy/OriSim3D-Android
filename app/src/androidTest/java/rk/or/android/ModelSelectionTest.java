package rk.or.android;

import android.content.Context;

import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Test;

import static org.junit.Assert.*;

public class ModelSelectionTest {

    @Test
    public void onCreate() {
        Context appContext = InstrumentationRegistry.getInstrumentation().getTargetContext();
        assertEquals("rk.or.android", appContext.getPackageName());

    }

    @Test
    public void onItemClick() {
    }
}
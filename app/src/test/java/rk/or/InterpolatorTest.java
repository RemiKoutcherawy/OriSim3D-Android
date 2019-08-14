package rk.or;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class InterpolatorTest {
    private static float delta = 0.001f;

    @Test
    public void LinearInterpolator() {
        Interpolator i = new LinearInterpolator();
        assertEquals(0, i.interpolate(0), delta);
        assertEquals(0.5f, i.interpolate(0.5f), delta);
        assertEquals(1, i.interpolate(1), delta);
    }

    @Test
    public void AccelerateDecelerateInterpolator() {
        Interpolator i = new AccelerateDecelerateInterpolator();
        assertEquals(0, i.interpolate(0), delta);
        assertEquals(0.5f, i.interpolate(0.5f), delta);
        assertEquals(1, i.interpolate(1), delta);
    }

    @Test
    public void SpringOvershootInterpolator() {
        Interpolator i = new SpringOvershootInterpolator();
        assertEquals(0, i.interpolate(0), delta);
        assertEquals(0.918f, i.interpolate(0.5f), delta);
        assertEquals(1, i.interpolate(1), delta);
    }

    @Test
    public void AnticipateOvershootInterpolator() {
        Interpolator i = new AnticipateOvershootInterpolator(2.0f);
        assertEquals(0, i.interpolate(0), delta);
        assertEquals(-0.044, i.interpolate(0.1f), delta);
        assertEquals(0.5f, i.interpolate(0.5f), delta);
        assertEquals(1, i.interpolate(1), delta);
        assertEquals(1.044, i.interpolate(0.9f), delta);
    }
}
package rk.or.core

import org.junit.Assert.assertEquals
import org.junit.Test

class InterpolatorTest {
    private val eps = 1e-9

    @Test
    fun linearIsIdentity() {
        assertEquals(0.0, Interpolator.LinearInterpolator(0.0), eps)
        assertEquals(0.5, Interpolator.LinearInterpolator(0.5), eps)
        assertEquals(1.0, Interpolator.LinearInterpolator(1.0), eps)
    }

    @Test
    fun accelerateDecelerateStartsAndEndsAtBounds() {
        assertEquals(0.0, Interpolator.AccelerateDecelerateInterpolator(0.0), eps)
        assertEquals(1.0, Interpolator.AccelerateDecelerateInterpolator(1.0), eps)
    }

    @Test
    fun anticipateStartsBelowZero() {
        // Anticipate pulls back before moving forward
        assertEquals(0.0, Interpolator.AnticipateInterpolator(0.0), eps)
        assertEquals(1.0, Interpolator.AnticipateInterpolator(1.0), eps)
        assert(Interpolator.AnticipateInterpolator(0.1) < 0.0)
    }

    @Test
    fun overshootEndsAboveOneThenSettles() {
        assertEquals(0.0, Interpolator.OvershootInterpolator(0.0), eps)
        assertEquals(1.0, Interpolator.OvershootInterpolator(1.0), eps)
        assert(Interpolator.OvershootInterpolator(0.9) > 1.0)
    }

    @Test
    fun anticipateOvershootStartsAndEndsAtBounds() {
        assertEquals(0.0, Interpolator.AnticipateOvershootInterpolator(0.0), eps)
        assertEquals(1.0, Interpolator.AnticipateOvershootInterpolator(1.0), eps)
    }

    @Test
    fun bounceEndsAtOne() {
        assertEquals(1.0, Interpolator.BounceInterpolator(1.0), 1e-3)
    }
}

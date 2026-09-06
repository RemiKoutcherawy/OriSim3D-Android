package rk.or.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class PointTest {
    private val eps = 1e-9

    @Test
    fun distance2d() {
        val a = Point(0.0, 0.0)
        val b = Point(3.0, 4.0)
        assertEquals(5.0, Point.distance2d(a, b), eps)
    }

    @Test
    fun normalizeUnitLength() {
        val n = Point.normalize(3.0, 4.0)
        assertEquals(0.6, n.xf, eps)
        assertEquals(0.8, n.yf, eps)
    }

    @Test
    fun normalizeZeroStaysZero() {
        val n = Point.normalize(0.0, 0.0)
        assertEquals(0.0, n.xf, eps)
        assertEquals(0.0, n.yf, eps)
    }

    @Test
    fun align3dFrom2dPlacesPointProportionally() {
        val a = Point(0.0, 0.0, 0.0, 0.0, 0.0)
        val b = Point(10.0, 0.0, 20.0, 0.0, 0.0)
        val mid = Point(5.0, 0.0) // halfway in 2d
        Point.align3dFrom2d(a, b, mid)
        assertEquals(10.0, mid.x, eps)
        assertEquals(0.0, mid.y, eps)
        assertEquals(0.0, mid.z, eps)
    }

    @Test
    fun align2dFrom3dPlacesPointProportionally() {
        val a = Point(0.0, 0.0, 0.0, 0.0, 0.0)
        val b = Point(20.0, 0.0, 10.0, 0.0, 0.0)
        val mid = Point(0.0, 0.0, 5.0, 0.0, 0.0) // halfway in 3d
        Point.align2dFrom3d(a, b, mid)
        assertEquals(10.0, mid.xf, eps)
        assertEquals(0.0, mid.yf, eps)
    }

    @Test
    fun defaultsAreFlat() {
        val p = Point(1.0, 2.0)
        assertEquals(0.0, p.x, eps)
        assertEquals(0.0, p.y, eps)
        assertEquals(0.0, p.z, eps)
        assertFalse(p.hover)
        assertFalse(p.select)
    }
}

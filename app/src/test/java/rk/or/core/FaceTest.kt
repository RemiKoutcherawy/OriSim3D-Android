package rk.or.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FaceTest {
    private val eps = 1e-9

    private fun square(): Face = Face(
        mutableListOf(Point(-10.0, -10.0), Point(10.0, -10.0), Point(10.0, 10.0), Point(-10.0, 10.0)),
    )

    @Test
    fun area2dFlatOfASquare() {
        // CCW square of side 20 -> signed area +400
        assertEquals(400.0, Face.area2dFlat(square().points), eps)
    }

    @Test
    fun isConvex2dTrueForASquare() {
        assertTrue(Face.isConvex2d(square()))
    }

    @Test
    fun isConvex2dFalseForANotch() {
        val notch = Face(
            mutableListOf(
                Point(0.0, 0.0), Point(10.0, 0.0), Point(10.0, 10.0),
                Point(5.0, 5.0), Point(0.0, 10.0),
            ),
        )
        assertFalse(Face.isConvex2d(notch))
    }

    @Test
    fun contains2dInsideAndOutside() {
        val f = square()
        assertTrue(Face.contains2d(f, 0.0, 0.0))
        assertFalse(Face.contains2d(f, 100.0, 100.0))
    }

    @Test
    fun intersectionPlaneSegmentAtMidpoint() {
        val plane = Plane(Vector3(0.0, 0.0, 0.0), Vector3(0.0, 0.0, 1.0))
        val a = Point(0.0, 0.0, 0.0, 0.0, -10.0)
        val b = Point(10.0, 0.0, 10.0, 0.0, 10.0)
        val inter = Face.intersectionPlaneSegment(plane, a, b)
        assertNotNull(inter)
        assertEquals(0.0, inter!!.z, eps)
        assertEquals(5.0, inter.x, eps)
    }

    @Test
    fun planeToPointSignedDistance() {
        val plane = Plane(Vector3(0.0, 0.0, 0.0), Vector3(0.0, 0.0, 1.0))
        val above = Point(0.0, 0.0, 0.0, 0.0, 5.0)
        assertEquals(5.0, Face.planeToPointSignedDistance(plane, above), eps)
    }
}

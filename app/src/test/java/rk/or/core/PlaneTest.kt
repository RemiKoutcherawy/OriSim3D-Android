package rk.or.core

import org.junit.Assert.assertEquals
import org.junit.Test

class PlaneTest {
    private val eps = 1e-9

    @Test
    fun acrossIsMidwayAndNormalToTheSegment() {
        val p1 = Vector3(10.0, 0.0, 0.0)
        val p2 = Vector3(30.0, 0.0, 0.0)
        val plane = Plane.across(p1, p2)
        assertEquals(20.0, plane.origin.x, eps)
        assertEquals(1.0, plane.normal.x, eps) // normalized (20,0,0) -> (1,0,0)
        assertEquals(0.0, plane.normal.y, eps)
        assertEquals(0.0, plane.normal.z, eps)
    }

    @Test
    fun byTurnsTheSegment90DegreesInXy() {
        val p1 = Vector3(0.0, 0.0, 0.0)
        val p2 = Vector3(1.0, 0.0, 0.0)
        val plane = Plane.by(p1, p2)
        // (x,y) -> (y,-x): direction (1,0) -> (0,-1)
        assertEquals(0.0, plane.normal.x, eps)
        assertEquals(-1.0, plane.normal.y, eps)
        assertEquals(0.0, plane.normal.z, eps)
    }

    @Test
    fun orthogonalUsesGivenPointAsOrigin() {
        val p1 = Vector3(0.0, 0.0, 0.0)
        val p2 = Vector3(0.0, 0.0, 5.0)
        val point = Vector3(7.0, 8.0, 9.0)
        val plane = Plane.orthogonal(p1, p2, point)
        assertEquals(7.0, plane.origin.x, eps)
        assertEquals(8.0, plane.origin.y, eps)
        assertEquals(9.0, plane.origin.z, eps)
        assertEquals(1.0, plane.normal.z, eps)
    }
}

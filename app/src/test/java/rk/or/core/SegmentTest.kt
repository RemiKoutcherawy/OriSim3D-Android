package rk.or.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SegmentTest {
    private val eps = 1e-6

    @Test
    fun distance2dToPointOnTheSegment() {
        assertEquals(0.0, Segment.distance2d(0.0, 0.0, 10.0, 0.0, 5.0, 0.0), eps)
    }

    @Test
    fun distance2dToPointBeyondEndpoint() {
        // Beyond p2, distance is to p2 itself, not the infinite line
        assertEquals(5.0, Segment.distance2d(0.0, 0.0, 10.0, 0.0, 15.0, 0.0), eps)
    }

    @Test
    fun intersectionFlatOfCrossingSegments() {
        val a = Point(-1.0, 0.0); val b = Point(1.0, 0.0)
        val c = Point(0.0, -1.0); val d = Point(0.0, 1.0)
        val inter = Segment.intersectionFlat(a, b, c, d)
        assertNotNull(inter)
        assertEquals(0.0, inter!!.xf, eps)
        assertEquals(0.0, inter.yf, eps)
    }

    @Test
    fun intersectionFlatOfParallelNonTouchingSegments() {
        val a = Point(0.0, 0.0); val b = Point(1.0, 0.0)
        val c = Point(0.0, 1.0); val d = Point(1.0, 1.0)
        assertNull(Segment.intersectionFlat(a, b, c, d))
    }

    @Test
    fun intersection2dLinesIgnoresSegmentBounds() {
        // Lines cross outside both segments' extent; intersection2dLines still reports it
        val a = Point(0.0, 0.0); val b = Point(1.0, 0.0)
        val c = Point(5.0, -1.0); val d = Point(5.0, 1.0)
        val inter = Segment.intersection2dLines(a, b, c, d)
        assertNotNull(inter)
        assertEquals(5.0, inter!!.xf, eps)
        assertEquals(0.0, inter.yf, eps)
    }

    @Test
    fun length2dAndLength3d() {
        val p1 = Point(0.0, 0.0, 0.0, 0.0, 0.0)
        val p2 = Point(3.0, 4.0, 0.0, 0.0, 5.0)
        val s = Segment(p1, p2)
        assertEquals(5.0, Segment.length2d(s), eps)
        assertEquals(5.0, Segment.length3d(s), eps)
    }

    @Test
    fun closestSegmentOfCrossingLines() {
        val a = Point(0.0, 0.0, -1.0, 0.0, 0.0)
        val b = Point(0.0, 0.0, 1.0, 0.0, 0.0)
        val c = Point(0.0, 0.0, 0.0, -1.0, 0.0)
        val d = Point(0.0, 0.0, 0.0, 1.0, 0.0)
        val closest = Segment.closestSegment(a, b, c, d)
        assertEquals(0.0, closest.p.x, eps)
        assertEquals(0.0, closest.q.y, eps)
        assertEquals(0.0, Segment.distanceToSegment(a, b, c, d), eps)
    }

    @Test
    fun isPointOnSegmentTrueForMidpoint() {
        val s = Segment(Point(0.0, 0.0), Point(10.0, 0.0))
        assertTrue(Segment.isPointOnSegment(s, Point(5.0, 0.0)))
    }

    @Test
    fun isPointOnSegmentFalseWhenOffTheLine() {
        val s = Segment(Point(0.0, 0.0), Point(10.0, 0.0))
        assertEquals(false, Segment.isPointOnSegment(s, Point(5.0, 1.0)))
    }

    @Test
    fun newSegmentDefaultsToUnassigned() {
        val s = Segment(Point(0.0, 0.0), Point(1.0, 1.0))
        assertEquals("U", s.assignment)
    }
}

package rk.or.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelTest {
    private val eps = 1e-6

    @Test
    fun initCreatesASquareWithOneFace() {
        val m = Model().init(100.0, 100.0)
        assertEquals(4, m.points.size)
        assertEquals(4, m.segments.size)
        assertEquals(1, m.faces.size)
        assertEquals(4, m.faces[0].points.size)
        assertEquals(State.RUN, m.state)
    }

    @Test
    fun addPointReturnsExistingPointWithinEpsilon() {
        val m = Model()
        val p1 = m.addPoint(1.0, 2.0)
        val p2 = m.addPoint(1.0, 2.0)
        assertTrue(p1 === p2)
        assertEquals(1, m.points.size)
    }

    @Test
    fun addSegmentAndAddFaceDeduplicate() {
        val m = Model()
        val a = m.addPoint(0.0, 0.0)
        val b = m.addPoint(10.0, 0.0)
        val s1 = m.addSegment(a, b)
        val s2 = m.addSegment(b, a) // reversed order still the same segment
        assertTrue(s1 === s2)
        assertEquals(1, m.segments.size)
    }

    @Test
    fun splitBy2dCutsSquareInTwoFaces() {
        val m = Model().init(100.0, 100.0)
        val p0 = m.points[0] // (-100,-100)
        val p2 = m.points[2] // (100,100)
        m.splitBy2d(p0, p2)
        assertEquals(2, m.faces.size)
    }

    @Test
    fun splitCross2dCreatesPerpendicularBisector() {
        val m = Model().init(100.0, 100.0)
        val p0 = m.points[0]
        val p1 = m.points[1]
        m.splitCross2d(p0, p1)
        assertEquals(2, m.faces.size)
    }

    @Test
    fun splitBy3dSplitsAFlatModel() {
        val m = Model().init(100.0, 100.0)
        val p0 = m.points[0]
        val p2 = m.points[2]
        m.splitBy3d(p0, p2)
        assertEquals(2, m.faces.size)
    }

    @Test
    fun rotateAroundZAxisByNinetyDegrees() {
        val m = Model()
        val p = m.addPoint(0.0, 0.0, 10.0, 0.0, 0.0)
        val axis = Segment(Point(0.0, 0.0, 0.0, 0.0, 0.0), Point(0.0, 0.0, 0.0, 0.0, 1.0))
        m.rotate(axis, 90.0, listOf(p))
        assertEquals(0.0, p.x, 1e-6)
        assertEquals(10.0, p.y, 1e-6)
    }

    @Test
    fun turnXYZDelegatesToRotate() {
        val m = Model()
        val p = m.addPoint(0.0, 0.0, 0.0, 0.0, 10.0)
        m.turn("x", 90.0)
        // Rotating (0,0,10) by 90 around x axis -> (0,-10,0) (right-hand rule)
        assertEquals(0.0, p.x, 1e-6)
        assertEquals(-10.0, p.y, 1e-6)
        assertEquals(0.0, p.z, 1e-6)
    }

    @Test
    fun adjustPullsPointToMatch2dLength() {
        val m = Model()
        val a = m.addPoint(0.0, 0.0, 0.0, 0.0, 0.0)
        val b = m.addPoint(10.0, 0.0, 20.0, 0.0, 0.0) // 3d length (20) != 2d length (10)
        m.addSegment(a, b)
        m.adjust(b)
        assertEquals(10.0, Segment.length3d(m.getSegment(a, b)!!), 1e-2)
    }

    @Test
    fun zoomScalesXyAndZ() {
        val m = Model()
        val p = m.addPoint(0.0, 0.0, 10.0, 20.0, 30.0)
        m.zoom(2.0)
        assertEquals(20.0, p.x, eps)
        assertEquals(40.0, p.y, eps)
        assertEquals(60.0, p.z, eps)
        assertEquals(2.0, m.scale, eps)
    }

    @Test
    fun offsetAppliesToGivenFacesOnly() {
        val m = Model().init(100.0, 100.0)
        val f = m.faces[0]
        m.offset(10.0, listOf(f))
        assertEquals(1.0, f.offset, eps)
        m.offset(0.0, emptyList()) // reset
        assertEquals(0.0, f.offset, eps)
    }

    @Test
    fun serializeDeserializeRoundTrips() {
        val m = Model().init(100.0, 100.0)
        m.points[0].select = true // UI-only, should NOT round trip
        m.segments[0].assignment = "M"
        val text = m.serialize()
        val restored = Model.deserialize(text)
        assertEquals(m.points.size, restored.points.size)
        assertEquals(m.segments.size, restored.segments.size)
        assertEquals(m.faces.size, restored.faces.size)
        assertEquals("M", restored.segments[0].assignment)
        assertEquals(m.points[0].xf, restored.points[0].xf, eps)
        assertEquals(false, restored.points[0].select) // hover/select excluded from snapshots
        // Segments/faces reference the restored model's own point instances
        assertTrue(restored.segments[0].p1 === restored.points[restored.points.indexOf(restored.segments[0].p1)])
    }

    @Test
    fun getSegmentIsOrderIndependent() {
        val m = Model()
        val a = m.addPoint(0.0, 0.0)
        val b = m.addPoint(1.0, 1.0)
        val s = m.addSegment(a, b)
        assertTrue(m.getSegment(a, b) === s)
        assertTrue(m.getSegment(b, a) === s)
        assertNull(m.getSegment(a, m.addPoint(5.0, 5.0)))
    }

    @Test
    fun incidentFacesFindsTheTwoFacesSharingAnEdge() {
        val m = Model().init(100.0, 100.0)
        val p0 = m.points[0]; val p2 = m.points[2]
        m.splitBy2d(p0, p2)
        val shared = m.getSegment(p0, p2)
        assertNotNull(shared)
        val faces = Model.incidentFaces(m, shared)
        assertEquals(2, faces.size)
    }

    @Test
    fun normalOfAFlatSquareFacesPositiveZ() {
        val m = Model().init(100.0, 100.0)
        val n = Model.normal(m.faces[0])
        assertEquals(0.0, n[0], eps)
        assertEquals(0.0, n[1], eps)
        assertEquals(1.0, kotlin.math.abs(n[2]), eps)
    }

    @Test
    fun dihedralAngleOfCoplanarFacesIsZero() {
        val m = Model().init(100.0, 100.0)
        val p0 = m.points[0]; val p2 = m.points[2]
        m.splitBy2d(p0, p2)
        val angle = Model.dihedralAngle(m.faces[0], m.faces[1])
        assertEquals(0, angle)
    }
}

package rk.or.core

import org.junit.Assert.assertEquals
import org.junit.Test

class Vector3Test {
    private val eps = 1e-9

    @Test
    fun dotAndCross() {
        val a = Vector3(1.0, 0.0, 0.0)
        val b = Vector3(0.0, 1.0, 0.0)
        assertEquals(0.0, Vector3.dot(a, b), eps)
        val c = Vector3.cross(a, b)
        assertEquals(0.0, c.x, eps)
        assertEquals(0.0, c.y, eps)
        assertEquals(1.0, c.z, eps)
    }

    @Test
    fun addSubScale() {
        val a = Vector3(1.0, 2.0, 3.0)
        val b = Vector3(4.0, 5.0, 6.0)
        val sum = Vector3.add(a, b)
        assertEquals(5.0, sum.x, eps); assertEquals(7.0, sum.y, eps); assertEquals(9.0, sum.z, eps)
        val diff = Vector3.sub(b, a)
        assertEquals(3.0, diff.x, eps); assertEquals(3.0, diff.y, eps); assertEquals(3.0, diff.z, eps)
        val scaled = Vector3.scale(a, 2.0)
        assertEquals(2.0, scaled.x, eps); assertEquals(4.0, scaled.y, eps); assertEquals(6.0, scaled.z, eps)
    }

    @Test
    fun length3dAndDistance() {
        assertEquals(5.0, Vector3.length3d(Vector3(3.0, 4.0, 0.0)), eps)
        assertEquals(5.0, Vector3.distance(Vector3(0.0, 0.0, 0.0), Vector3(3.0, 4.0, 0.0)), eps)
    }

    @Test
    fun normalizeZeroVectorStaysZero() {
        val n = Vector3.normalize(Vector3(0.0, 0.0, 0.0))
        assertEquals(0.0, n.x, eps); assertEquals(0.0, n.y, eps); assertEquals(0.0, n.z, eps)
    }

    @Test
    fun normalizeUnitLength() {
        val n = Vector3.normalize(Vector3(3.0, 4.0, 0.0))
        assertEquals(1.0, Vector3.length3d(n), eps)
        assertEquals(0.6, n.x, eps)
        assertEquals(0.8, n.y, eps)
    }

    @Test
    fun closestPointOnLine() {
        // Line along x axis, point above the midpoint
        val a = Vector3(0.0, 0.0, 0.0)
        val b = Vector3(10.0, 0.0, 0.0)
        val c = Vector3(5.0, 3.0, 0.0)
        val closest = Vector3.closestPoint(c, a, b)
        assertEquals(5.0, closest.x, eps)
        assertEquals(0.0, closest.y, eps)
        assertEquals(3.0, Vector3.pointLineDistance(c, a, b), eps)
    }

    @Test
    fun transformMat4Identity() {
        val identity = FloatArray(16)
        for (i in 0..3) identity[i * 4 + i] = 1f
        val v = Vector3(1.0, 2.0, 3.0)
        val t = Vector3.transformMat4(v, identity)
        assertEquals(1.0, t.x, eps); assertEquals(2.0, t.y, eps); assertEquals(3.0, t.z, eps)
    }
}

package rk.or.core

import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sign

/** Result of Segment.closestSegment: closest points on line AB and line CD. */
data class ClosestSegment(val p: Vector3, val q: Vector3)

/**
 * Port of js/Segment.js. A Segment is just a pair of Point references; nothing
 * is duplicated, segments always reference the shared Model.points array.
 */
class Segment(@JvmField var p1: Point, @JvmField var p2: Point, assignment: String = "U") : Selectable {
    override var hover: Boolean = false
    override var select: Boolean = false
    // FOLD crease assignment: M mountain, V valley, B boundary, F flat, U unassigned
    @JvmField var assignment: String = assignment
    // Reverse Inside Fold geometry cache, set/read by Model.reverseFold
    @JvmField var reverseFoldCache: ReverseFoldCache? = null

    companion object {
        // 2d distance from Segment to Point
        @JvmStatic
        fun distance2d(x1: Double, y1: Double, x2: Double, y2: Double, x: Double, y: Double): Double {
            val l2 = (x1 - x2) * (x1 - x2) + (y1 - y2) * (y1 - y2)
            if (l2 == 0.0) return hypot(x - x1, y - y1)
            val t = ((x - x1) * (x2 - x1) + (y - y1) * (y2 - y1)) / l2
            if (t < 0) return hypot(x - x1, y - y1)
            if (t > 1) return hypot(x - x2, y - y2)
            val projectionX = x1 + t * (x2 - x1)
            val projectionY = y1 + t * (y2 - y1)
            return hypot(x - projectionX, y - projectionY)
        }

        // Area counter clock wise, CCW, gives 2d signed distance between Point and Segment in 3d
        @JvmStatic
        fun ccw(a: Point, b: Point, c: Point): Double =
            (a.x - c.x) * (b.y - c.y) - (a.y - c.y) * (b.x - c.x)

        // Area CounterClockWise, CCW, gives 2d signed distance between Point and Segment in 2d Crease pattern
        @JvmStatic
        fun ccwFlat(p1: Point, p2: Point, p3: Point): Double =
            (p1.xf - p3.xf) * (p2.yf - p3.yf) - (p1.yf - p3.yf) * (p2.xf - p3.xf)

        // Collinear case for intersectionFlat, maybe too much
        private fun collinear(a: Point, b: Point, c: Point, d: Point): Point? {
            val ab = (b.xf - a.xf) * (b.xf - a.xf) + (b.yf - a.yf) * (b.yf - a.yf)
            val cd = (d.xf - c.xf) * (d.xf - c.xf) + (d.yf - c.yf) * (d.yf - c.yf)
            if (ab == 0.0) {
                if (cd == 0.0) return if (a.xf == c.xf) a else null
                val t = ((a.xf - c.xf) * (d.xf - c.xf) + (a.yf - c.yf) * (d.yf - c.yf)) / cd
                if (t < 0 || t > 1) return null
                return a
            }
            val tc = ((c.xf - a.xf) * (b.xf - a.xf) + (c.yf - a.yf) * (b.yf - a.yf)) / ab
            val td = ((d.xf - a.xf) * (b.xf - a.xf) + (d.yf - a.yf) * (b.yf - a.yf)) / ab
            if (tc < 0) {
                if (td < 0) return null
                if (td > 1) return a
                return d
            } else if (tc > 1) {
                if (td > 1) return null
                return d
            }
            return c
        }

        // 2d intersection between two segments ab and cd
        @JvmStatic
        fun intersectionFlat(a: Point, b: Point, c: Point, d: Point): Point? {
            val a1 = ccwFlat(a, b, d)
            val a2 = ccwFlat(a, b, c)

            // A cross product that should be exactly 0 (point exactly on the other line) can come
            // out as a tiny nonzero value from floating-point rounding. Treat near-zero as zero so
            // that noise doesn't flip these sign checks and spuriously reject a real intersection.
            val areaEpsilon = 1e-6
            fun s(v: Double): Double = if (abs(v) <= areaEpsilon) 0.0 else sign(v)

            if (s(a1) * s(a2) <= 0) {
                val a3 = ccwFlat(c, d, a)
                val a4 = a3 + a2 - a1
                if (s(a3) * s(a4) <= 0) {
                    if (a3 - a4 == 0.0) {
                        return collinear(a, b, c, d)
                    }
                    val t = a3 / (a3 - a4)
                    val u = a1 / (a1 - a2)
                    val ratioEpsilon = 1e-9
                    if (t >= -ratioEpsilon && t <= 1 + ratioEpsilon && u >= -ratioEpsilon && u <= 1 + ratioEpsilon) {
                        return Point(a.xf + t * (b.xf - a.xf), a.yf + t * (b.yf - a.yf))
                    }
                    return null
                }
            }
            return null
        }

        // Basic intersection used for tests, does not handle collinear, or superposed
        @JvmStatic
        fun intersection2dBasicFlat(a: Point, b: Point, c: Point, d: Point): Point? {
            val v1x = b.xf - a.xf
            val v1y = b.yf - a.yf
            val v2x = d.xf - c.xf
            val v2y = d.yf - c.yf
            val denom = -v2x * v1y + v1x * v2y
            val s = (-v1y * (a.xf - c.xf) + v1x * (a.yf - c.yf)) / denom
            val t = (v2x * (a.yf - c.yf) - v2y * (a.xf - c.xf)) / denom
            if (s in 0.0..1.0 && t in 0.0..1.0) {
                return Point(a.xf + (t * v1x), a.yf + (t * v1y))
            }
            return null
        }

        // Lines in 2D
        @JvmStatic
        fun intersection2dLines(a: Point, b: Point, c: Point, d: Point): Point? {
            val v1x = b.xf - a.xf
            val v1y = b.yf - a.yf
            val v2x = d.xf - c.xf
            val v2y = d.yf - c.yf
            val determinant = -v2x * v1y + v1x * v2y
            if (abs(determinant) < 1e-10) return null
            val t = (v2x * (a.yf - c.yf) - v2y * (a.xf - c.xf)) / determinant
            return Point(a.xf + (t * v1x), a.yf + (t * v1y))
        }

        // 3d distance from segment to segment
        @JvmStatic
        fun distanceToSegment(a: Point, b: Point, c: Point, d: Point): Double {
            val closest = closestSegment(a, b, c, d)
            return Vector3.distance(closest.p, closest.q)
        }

        // Closest points between line [A, B] and line [C, D]
        @JvmStatic
        fun closestSegment(a: Point, b: Point, c: Point, d: Point): ClosestSegment {
            var s: Double
            var t: Double
            val epsilon = 1e-6
            val ab = Vector3(b.x - a.x, b.y - a.y, b.z - a.z)
            val cd = Vector3(d.x - c.x, d.y - c.y, d.z - c.z)
            val ca = Vector3(a.x - c.x, a.y - c.y, a.z - c.z)
            val aDot = Vector3.dot(ab, ab)
            val e = Vector3.dot(cd, cd)
            val f = Vector3.dot(cd, ca)
            if (aDot < epsilon && e < epsilon) {
                return ClosestSegment(Vector3(a.x, a.y, a.z), Vector3(c.x, c.y, c.z))
            }
            if (aDot < epsilon) {
                s = 0.0
                t = f / e
                t = max(0.0, min(1.0, t))
            } else {
                val cDot = Vector3.dot(ab, ca)
                if (e < epsilon) {
                    t = 0.0
                    s = -cDot / aDot
                    s = max(0.0, min(1.0, s))
                } else {
                    val bDot = Vector3.dot(ab, cd)
                    val denominator = aDot * e - bDot * bDot
                    s = if (denominator != 0.0) {
                        max(0.0, min(1.0, (bDot * f - cDot * e) / denominator))
                    } else {
                        0.0
                    }
                    t = (bDot * s + f) / e
                    if (t < 0) {
                        t = 0.0
                        s = max(0.0, min(1.0, -cDot / aDot))
                    } else if (t > 1) {
                        t = 1.0
                        s = max(0.0, min(1.0, (bDot - cDot) / aDot))
                    }
                }
            }
            val p = Vector3.add(Vector3(a.x, a.y, a.z), Vector3.scale(ab, s))
            val q = Vector3.add(Vector3(c.x, c.y, c.z), Vector3.scale(cd, t))
            return ClosestSegment(p, q)
        }

        // 3d length (x, y, z)
        @JvmStatic
        fun length3d(s: Segment): Double =
            hypot(hypot(s.p1.x - s.p2.x, s.p1.y - s.p2.y), s.p1.z - s.p2.z)

        // 2d length (xf, yf)
        @JvmStatic
        fun length2d(s: Segment): Double = hypot(s.p1.xf - s.p2.xf, s.p1.yf - s.p2.yf)

        @JvmStatic
        fun project2d(s: Segment, p: Point): Point? {
            val l2 = (s.p2.xf - s.p1.xf) * (s.p2.xf - s.p1.xf) + (s.p2.yf - s.p1.yf) * (s.p2.yf - s.p1.yf)
            val t = ((p.xf - s.p1.xf) * (s.p2.xf - s.p1.xf) + (p.yf - s.p1.yf) * (s.p2.yf - s.p1.yf)) / l2
            if (t < 0 || t > 1) return null
            val pXf = s.p1.xf + t * (s.p2.xf - s.p1.xf)
            val pYf = s.p1.yf + t * (s.p2.yf - s.p1.yf)
            return Point(pXf, pYf)
        }

        // Whether point p is on segment s (in 2D)
        @JvmStatic
        fun isPointOnSegment(s: Segment, p: Point, epsilon: Double = 1e-6): Boolean {
            val dx = s.p2.xf - s.p1.xf
            val dy = s.p2.yf - s.p1.yf
            val lenSq = dx * dx + dy * dy
            if (lenSq == 0.0) return false
            val t = ((p.xf - s.p1.xf) * dx + (p.yf - s.p1.yf) * dy) / lenSq
            if (t < -epsilon || t > 1 + epsilon) return false
            val closestX = s.p1.xf + t * dx
            val closestY = s.p1.yf + t * dy
            val dist = hypot(p.xf - closestX, p.yf - closestY)
            return dist < epsilon
        }

        // Closest point on the segment (in 2D)
        @JvmStatic
        fun closestPointOnSegment(s: Segment, p: Point): Point {
            val dx = s.p2.xf - s.p1.xf
            val dy = s.p2.yf - s.p1.yf
            val lenSq = dx * dx + dy * dy
            if (lenSq == 0.0) return Point(s.p1.xf, s.p1.yf)
            val t = max(0.0, min(1.0, ((p.xf - s.p1.xf) * dx + (p.yf - s.p1.yf) * dy) / lenSq))
            return Point(s.p1.xf + t * dx, s.p1.yf + t * dy)
        }
    }
}

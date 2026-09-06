package rk.or.core

import kotlin.math.abs
import kotlin.math.sign

/**
 * Port of js/Face.js. A Face is an ordered list of Point references; nothing
 * is duplicated, faces always reference the shared Model.points array.
 */
class Face(@JvmField var points: MutableList<Point>) : Selectable {
    @JvmField var offset: Double = 0.0
    override var hover: Boolean = false
    override var select: Boolean = false

    companion object {
        // Area 2d for an array of points
        @JvmStatic
        fun area2dFlat(points: List<Point>): Double {
            var area = 0.0
            for (i in points.indices) {
                val a = points[i]
                val b = points[(i + 1) % points.size]
                area += a.xf * b.yf - a.yf * b.xf
            }
            return area / 2
        }

        // All turns the same way (colinear vertices ignored)
        @JvmStatic
        fun isConvex2d(face: Face): Boolean {
            val pts = face.points
            if (pts.size < 3) return true
            var sign = 0
            for (i in pts.indices) {
                val z = distance2dLineToPoint(pts[i], pts[(i + 1) % pts.size], pts[(i + 2) % pts.size])
                if (abs(z) < 1e-9) continue
                val s = sign(z).toInt()
                if (sign == 0) sign = s
                else if (s != sign) return false
            }
            return true
        }

        // Distance 2d from line AB to point C
        @JvmStatic
        fun distance2dLineToPoint(a: Point, b: Point, c: Point): Double =
            (c.xf - a.xf) * (b.yf - a.yf) - (c.yf - a.yf) * (b.xf - a.xf)

        // Intersection with a segment (a,b)
        @JvmStatic
        fun intersectionPlaneSegment(plane: Plane, a: Point, b: Point): Point? {
            val ab = Vector3(b.x - a.x, b.y - a.y, b.z - a.z)
            val abn = Vector3.dot(plane.normal, ab)
            if (abn == 0.0) return null
            val t = (Vector3.dot(plane.normal, plane.origin) - Vector3.dot(plane.normal, a.toVector3())) / abn
            if (t in 0.0..1.0) {
                val scaled = Vector3.scale(ab, t)
                val xf = a.xf + t * (b.xf - a.xf)
                val yf = a.yf + t * (b.yf - a.yf)
                return Point(xf, yf, a.x + scaled.x, a.y + scaled.y, a.z + scaled.z)
            }
            return null
        }

        // Signed distance in 3d
        @JvmStatic
        fun planeToPointSignedDistance(plane: Plane, point: Point): Double =
            Vector3.dot(plane.normal, point.toVector3()) - Vector3.dot(plane.normal, plane.origin)

        // Face contains 2d point (ray-casting, https://wrf.ecse.rpi.edu/Research/Short_Notes/pnpoly.html)
        @JvmStatic
        fun contains2d(face: Face, xf: Double, yf: Double): Boolean {
            var inside = false
            val vs = face.points
            var j = vs.size - 1
            for (i in vs.indices) {
                val xi = vs[i].xf; val yi = vs[i].yf
                val xj = vs[j].xf; val yj = vs[j].yf
                if (xi == xf && yi == yf) return true
                val intersect = (yi > yf) != (yj > yf) && (xf < (xj - xi) * (yf - yi) / (yj - yi) + xi)
                if (intersect) inside = !inside
                j = i
            }
            return inside
        }

        // Face contains a projected 3d point (ray-casting). `isRendered` reports whether a point
        // is part of the current renderer geometry (JS uses view3d.indexMap for that check).
        @JvmStatic
        fun contains3d(face: Face, xCanvas: Double, yCanvas: Double, isRendered: (Point) -> Boolean): Boolean {
            var inside = false
            val vs = face.points
            var j = vs.size - 1
            for (i in vs.indices) {
                val pi = vs[i]
                val pj = vs[j]
                if (isRendered(pi) && isRendered(pj)) {
                    val xi = pi.xCanvas ?: 0.0; val yi = pi.yCanvas ?: 0.0
                    val xj = pj.xCanvas ?: 0.0; val yj = pj.yCanvas ?: 0.0
                    if (xi == xCanvas && yi == yCanvas) return true
                    val intersect = (yi > yCanvas) != (yj > yCanvas) &&
                        (xCanvas < (xj - xi) * (yCanvas - yi) / (yj - yi) + xi)
                    if (intersect) inside = !inside
                }
                j = i
            }
            return inside
        }
    }
}

package rk.or.core

import kotlin.math.hypot

/** Lightweight 2D vector, returned by Point.normalize (JS returns a plain {xf, yf} object). */
data class Vec2(val xf: Double, val yf: Double)

/**
 * Port of js/Point.js. Every point exists simultaneously in the flat crease
 * pattern (xf, yf) and the folded 3D space (x, y, z) - see CLAUDE.md.
 */
class Point(
    @JvmField var xf: Double,
    @JvmField var yf: Double,
    @JvmField var x: Double = 0.0,
    @JvmField var y: Double = 0.0,
    @JvmField var z: Double = 0.0,
) : Selectable {
    // Helper (UI-only, excluded from Model.serialize)
    override var hover: Boolean = false
    override var select: Boolean = false
    // Projected point in the 3D view's overlay, set by the renderer each frame
    @JvmField var xCanvas: Double? = null
    @JvmField var yCanvas: Double? = null
    @JvmField var zEye: Double? = null
    // Hide labels
    @JvmField var hidden: Boolean = false

    companion object {
        // Adjust point i 2d coords on segment ab
        @JvmStatic
        fun align2dFrom3d(a: Point, b: Point, i: Point) {
            val ab = hypot(hypot(b.x - a.x, b.y - a.y), b.z - a.z)
            if (ab == 0.0) return
            val ai = hypot(hypot(i.x - a.x, i.y - a.y), i.z - a.z)
            val t = ai / ab
            i.xf = a.xf + t * (b.xf - a.xf)
            i.yf = a.yf + t * (b.yf - a.yf)
        }

        // Adjust point i 3d coords on segment ab
        @JvmStatic
        fun align3dFrom2d(a: Point, b: Point, i: Point) {
            val ab = hypot(b.xf - a.xf, b.yf - a.yf)
            if (ab == 0.0) return
            val ai = hypot(i.xf - a.xf, i.yf - a.yf)
            val t = ai / ab
            i.x = a.x + t * (b.x - a.x)
            i.y = a.y + t * (b.y - a.y)
            i.z = a.z + t * (b.z - a.z)
        }

        // Distance
        @JvmStatic
        fun distance2d(xf1: Double, yf1: Double, xf2: Double, yf2: Double): Double =
            hypot(xf2 - xf1, yf2 - yf1)

        @JvmStatic
        fun distance2d(a: Point, b: Point): Double = distance2d(a.xf, a.yf, b.xf, b.yf)

        // Normalize as if Vector 2d
        @JvmStatic
        fun normalize(xf: Double, yf: Double): Vec2 {
            val length = hypot(xf, yf)
            return if (length == 0.0) Vec2(0.0, 0.0) else Vec2(xf / length, yf / length)
        }

        // Alias for backward compatibility (British English spelling)
        @JvmStatic
        fun normalise(xf: Double, yf: Double): Vec2 = normalize(xf, yf)
    }
}

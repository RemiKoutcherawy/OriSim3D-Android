package rk.or.core

import kotlin.math.hypot

/**
 * Port of js/Vector3.js. Plain 3D vector/point value with the same static
 * helpers as the JS source (kept as companion functions to mirror call sites
 * like Vector3.dot(a, b) 1:1 with the JS source during review).
 */
class Vector3(@JvmField var x: Double = 0.0, @JvmField var y: Double = 0.0, @JvmField var z: Double = 0.0) {

    companion object {
        // The closest point to C on line (A, B) (unclamped: not restricted to the [A, B] segment)
        @JvmStatic
        fun closestPoint(c: Vector3, a: Vector3, b: Vector3): Vector3 {
            val ab = Vector3(b.x - a.x, b.y - a.y, b.z - a.z)
            val ac = Vector3(c.x - a.x, c.y - a.y, c.z - a.z)
            val abDot = dot(ab, ab)
            val t = if (abDot == 0.0) 0.0 else dot(ac, ab) / abDot
            return Vector3(a.x + ab.x * t, a.y + ab.y * t, a.z + ab.z * t)
        }

        // Distance between point C and line [A, B]
        @JvmStatic
        fun pointLineDistance(c: Vector3, a: Vector3, b: Vector3): Double =
            distance(c, closestPoint(c, a, b))

        // Euclidean distance between two points
        @JvmStatic
        fun distance(a: Vector3, b: Vector3): Double = length3d(sub(a, b))

        @JvmStatic
        fun dot(u: Vector3, v: Vector3): Double = u.x * v.x + u.y * v.y + u.z * v.z

        @JvmStatic
        fun scale(v: Vector3, scalar: Double): Vector3 = Vector3(v.x * scalar, v.y * scalar, v.z * scalar)

        @JvmStatic
        fun length3d(v: Vector3): Double = hypot(hypot(v.x, v.y), v.z)

        @JvmStatic
        fun normalize(v: Vector3): Vector3 {
            val length = length3d(v)
            return if (length == 0.0) Vector3(v.x, v.y, v.z) else scale(v, 1 / length)
        }

        @JvmStatic
        fun add(u: Vector3, v: Vector3): Vector3 = Vector3(u.x + v.x, u.y + v.y, u.z + v.z)

        @JvmStatic
        fun sub(a: Vector3, b: Vector3): Vector3 = Vector3(a.x - b.x, a.y - b.y, a.z - b.z)

        @JvmStatic
        fun cross(a: Vector3, b: Vector3): Vector3 {
            val ax = a.x; val ay = a.y; val az = a.z
            val bx = b.x; val by = b.y; val bz = b.z
            return Vector3(ay * bz - az * by, az * bx - ax * bz, ax * by - ay * bx)
        }

        // Transform by a 16-element column-major matrix (as produced by mat4/GLES)
        @JvmStatic
        fun transformMat4(v: Vector3, m: FloatArray): Vector3 {
            val x = v.x; val y = v.y; val z = v.z
            var w = m[3] * x + m[7] * y + m[11] * z + m[15]
            if (!w.isFinite() || w == 0.0) w = 1.0
            return Vector3(
                (m[0] * x + m[4] * y + m[8] * z + m[12]) / w,
                (m[1] * x + m[5] * y + m[9] * z + m[13]) / w,
                (m[2] * x + m[6] * y + m[10] * z + m[14]) / w,
            )
        }
    }
}

/** Read a Point's folded 3D coordinates as a Vector3 (Point/Vector3 are duck-typed in the JS source). */
fun Point.toVector3(): Vector3 = Vector3(x, y, z)

package rk.or.core

/**
 * Port of js/Plane.js. Plane is defined by an origin point R, and a normal
 * vector N; point P is on the plane iff RP.N = 0.
 */
class Plane(@JvmField val origin: Vector3, normal: Vector3) {
    @JvmField val normal: Vector3 = Vector3.normalize(normal)

    companion object {
        // Plane across 2 points
        @JvmStatic
        fun across(p1: Vector3, p2: Vector3): Plane {
            val normal = Vector3(p2.x - p1.x, p2.y - p1.y, p2.z - p1.z)
            val middle = Vector3((p1.x + p2.x) / 2, (p1.y + p2.y) / 2, (p1.z + p2.z) / 2)
            return Plane(middle, normal)
        }

        // Plane by 2 points on xy orthogonal to z
        @JvmStatic
        fun by(p1: Vector3, p2: Vector3): Plane {
            // Turn 90° on the right (x,y) to (y,-x)
            val normal = Vector3(p2.y - p1.y, -(p2.x - p1.x), 0.0)
            return Plane(p1, normal)
        }

        // Plane orthogonal to a segment [p1, p2] passing by point
        @JvmStatic
        fun orthogonal(p1: Vector3, p2: Vector3, point: Vector3): Plane {
            val normal = Vector3(p2.x - p1.x, p2.y - p1.y, p2.z - p1.z)
            return Plane(point, normal)
        }
    }
}

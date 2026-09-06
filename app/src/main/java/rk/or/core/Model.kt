package rk.or.core

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sign
import kotlin.math.sin
import kotlin.math.sqrt

/** Port of js/Model.js's `export const State = {run: 0, anim: 1, undo: 2, pause: 3}`. */
enum class State { RUN, ANIM, UNDO, PAUSE }

/** 2D bounds, either the flat crease pattern (xf, yf) or the folded model (x, y). */
data class Bounds(val xMin: Double, val xMax: Double, val yMin: Double, val yMax: Double)

/** Reverse Inside Fold geometry, computed once per fold and cached on the crease Segment. */
class GateData(
    val point: Point,
    val r: Double,
    val ix: Double,
    val iy: Double,
    val iz: Double,
    val gateNormalX: Double,
    val gateNormalY: Double,
    val gateNormalZ: Double,
    val peakZOff: Double,
)

class ReverseFoldCache(
    val center: Point,
    val pointsToRotate: List<Point>,
    val axisSegment: Segment,
    val sweepAngle: Double,
    var appliedAngle: Double,
    var lastTn: Double,
    val gates: List<GateData>,
)

// Newell: magnitude of the accumulated normal (0 if degenerated or < 3 points)
private fun polygonArea3d(points: List<Point>?): Double {
    if (points == null || points.size < 3) return 0.0
    var nx = 0.0; var ny = 0.0; var nz = 0.0
    for (i in points.indices) {
        val a = points[i]; val b = points[(i + 1) % points.size]
        nx += (a.y - b.y) * (a.z + b.z)
        ny += (a.z - b.z) * (a.x + b.x)
        nz += (a.x - b.x) * (a.y + b.y)
    }
    return hypot(hypot(nx, ny), nz) / 2
}

// -1 left, 0 on the line, +1 right
private fun side2d(d: Double, epsilon: Double): Int {
    if (d < -epsilon) return -1
    if (d > epsilon) return 1
    return 0
}

/**
 * Port of js/Model.js. Owns points/segments/faces plus mutation methods
 * (add/split/rotate/offset/adjust) and serialize/deserialize for undo snapshots.
 *
 * serialize()/deserialize() use a small custom line-based format rather than JSON:
 * the JS source uses JSON.stringify only because it's convenient in JS, but the
 * actual contract (an opaque string that deserialize() restores exactly, for undo
 * snapshots) doesn't require JSON, and a hand-rolled format sidesteps needing a
 * JSON dependency on a plain JVM unit-test classpath.
 */
class Model {
    var points: MutableList<Point> = mutableListOf()
    var segments: MutableList<Segment> = mutableListOf()
    var faces: MutableList<Face> = mutableListOf()

    var state: State = State.RUN
    var scale: Double = 1.0

    var labels: Boolean = false
    var textures: Boolean = false
    var lines: Boolean = true
    var snap: Boolean = true

    // Initialize with 2d coordinates
    fun init(width: Double = 200.0, height: Double = 200.0): Model {
        points = mutableListOf()
        segments = mutableListOf()
        faces = mutableListOf()
        val p0 = Point(-width, -height, -width, -height, 0.0)
        val p1 = Point(width, -height, width, -height, 0.0)
        val p2 = Point(width, height, width, height, 0.0)
        val p3 = Point(-width, height, -width, height, 0.0)
        points.add(p0); points.add(p1); points.add(p2); points.add(p3)
        segments.add(Segment(p0, p1)); segments.add(Segment(p1, p2))
        segments.add(Segment(p2, p3)); segments.add(Segment(p3, p0))
        faces.add(Face(mutableListOf(p0, p1, p2, p3)))
        state = State.RUN
        scale = 1.0
        labels = false
        textures = false
        lines = true
        snap = true
        return this
    }

    // First non-empty pick among points, then segments, then faces
    fun firstHit(points: List<Selectable>, segments: List<Selectable>, faces: List<Selectable>): List<Selectable> {
        if (points.isNotEmpty()) return points
        if (segments.isNotEmpty()) return segments
        return faces
    }

    // Update hover2d3d on points, segments, faces 2d and 3d
    fun hover2d3d(points: List<Selectable>, segments: List<Selectable>, faces: List<Selectable>) {
        this.points.forEach { it.hover = false }
        this.segments.forEach { it.hover = false }
        this.faces.forEach { it.hover = false }
        firstHit(points, segments, faces).forEach { it.hover = true }
    }

    // Handle click2d3d on points, segments, faces 2d and 3d
    fun click2d3d(points: List<Selectable>, segments: List<Selectable>, faces: List<Selectable>) {
        firstHit(points, segments, faces).forEach { it.select = !it.select }
    }

    // Index of Point or Segment or Face
    fun indexOf(obj: Any?): Int = when (obj) {
        is Point -> points.indexOfFirst { it === obj }
        is Segment -> segments.indexOfFirst { it === obj }
        is Face -> faces.indexOfFirst { it === obj }
        else -> -1
    }

    // Get point on the flat crease pattern
    fun getPoint(xf: Double, yf: Double, epsilon: Double = 0.01): Point? =
        points.find { abs(it.xf - xf) + abs(it.yf - yf) < epsilon }

    // Add a point or return an existing point
    fun addPoint(xf: Double, yf: Double, x: Double = 0.0, y: Double = 0.0, z: Double = 0.0): Point {
        getPoint(xf, yf)?.let { return it }
        val point = Point(xf, yf, x, y, z)
        points.add(point)
        return point
    }

    // Add a segment or return an existing segment
    fun addSegment(a: Point, b: Point): Segment {
        getSegment(a, b)?.let { return it }
        val segment = Segment(a, b)
        segments.add(segment)
        return segment
    }

    // Get the face containing these points in this order
    fun getFace(pts: List<Point>): Face? = faces.find { f ->
        f.points.size == pts.size && f.points.indices.all { i -> f.points[i] === pts[i] }
    }

    // Add a face or return an existing face
    fun addFace(pts: MutableList<Point>): Face {
        getFace(pts)?.let { return it }
        pts.forEachIndexed { i, p -> addSegment(p, pts[(i + 1) % pts.size]) }
        val face = Face(pts)
        faces.add(face)
        return face
    }

    // Replace face polygon by left, add right as a new face, keep offset
    fun commitSplit(face: Face, left: MutableList<Point>, right: MutableList<Point>) {
        face.points = left
        val created = addFace(right)
        created.offset = face.offset
    }

    // Reverse order to add new faces in the same pass
    fun forEachFaceReverse(fn: (Face) -> Unit) {
        for (i in faces.indices.reversed()) fn(faces[i])
    }

    // Origami: split face by plane 3d. Not exactly 0 for distance but epsilon = 10.
    fun splitFaceByPlane3d(face: Face, plane: Plane) {
        val left = mutableListOf<Point>(); val right = mutableListOf<Point>()
        var lastInter: Point? = null
        val epsilon = 10.0
        fun side(d: Double): Int = if (abs(d) <= epsilon) 0 else if (d < 0) -1 else 1

        var last = face.points[face.points.size - 1]
        var dLast = Face.planeToPointSignedDistance(plane, last)
        for (current in face.points) {
            val dCurrent = Face.planeToPointSignedDistance(plane, current)
            val lastSide = side(dLast)
            val currSide = side(dCurrent)
            if (lastSide != 0 && lastSide == currSide) {
                (if (currSide < 0) left else right).add(current)
            } else if (currSide == 0) {
                left.add(current); right.add(current); lastInter = current
            } else if (lastSide == 0) {
                (if (currSide < 0) left else right).add(current)
            } else {
                val inter = Face.intersectionPlaneSegment(plane, last, current)
                if (inter != null) {
                    Point.align2dFrom3d(last, current, inter)
                    lastInter = addIntersection3d(inter, left, right, dCurrent, current, last, lastInter)
                } else {
                    (if (currSide < 0) left else right).add(current)
                }
            }
            last = current
            dLast = dCurrent
        }
        if (polygonArea3d(left) != 0.0 && polygonArea3d(right) != 0.0) {
            commitSplit(face, left, right)
        }
    }

    fun addIntersection3d(
        interIn: Point, left: MutableList<Point>, right: MutableList<Point>,
        dCurrent: Double, current: Point, last: Point, lastInterIn: Point?,
    ): Point? {
        val inter = addPoint(interIn.xf, interIn.yf, interIn.x, interIn.y, interIn.z)
        left.add(inter); right.add(inter)
        (if (dCurrent < 0) left else right).add(current)

        // Set Segment [last,current] to [last,inter]
        val segment = getSegment(last, current)
        if (segment != null) splitSegment(segment, last, inter)
        // Add a new segment
        addSegment(inter, current)

        // Eventually, if last intersection was on plane, add a segment from it to inter
        if (lastInterIn != null && inter !== lastInterIn) {
            addSegment(lastInterIn, inter)
            return null
        }
        return inter
    }

    fun splitFaceBySegment2d(face: Face, a: Point, b: Point) {
        val left = mutableListOf<Point>(); val right = mutableListOf<Point>()
        val epsilon = 1.0
        var last = face.points[face.points.size - 1]
        var dLast = Face.distance2dLineToPoint(a, b, last)
        if (abs(dLast) < epsilon && Segment.intersectionFlat(a, b, last, face.points[0]) == null) {
            return
        }
        for (current in face.points) {
            val dCurrent = Face.distance2dLineToPoint(a, b, current)
            val lastSide = side2d(dLast, epsilon)
            val currSide = side2d(dCurrent, epsilon)
            if (lastSide == currSide) {
                if (currSide < 0) left.add(current)
                else if (currSide > 0) right.add(current)
                else {
                    if (Segment.intersectionFlat(a, b, last, current) == null) return
                    left.add(current); right.add(current)
                }
            } else if (currSide == 0) {
                if (Segment.intersectionFlat(a, b, last, current) == null) return
                left.add(current); right.add(current)
            } else if (lastSide == 0) {
                (if (currSide < 0) left else right).add(current)
            } else {
                val inter = Segment.intersectionFlat(a, b, last, current) ?: return
                Point.align3dFrom2d(last, current, inter)
                addIntersectionPoint(inter, left, right, last, current, face)
                (if (currSide < 0) left else right).add(current)
            }
            last = current
            dLast = dCurrent
        }
        if (abs(Face.area2dFlat(left)) > epsilon && abs(Face.area2dFlat(right)) > epsilon) {
            commitSplit(face, left, right)
        }
    }

    fun addIntersectionPoint(
        interIn: Point, left: MutableList<Point>, right: MutableList<Point>,
        last: Point, current: Point, face: Face,
    ): Point {
        val inter = addPoint(interIn.xf, interIn.yf, interIn.x, interIn.y, interIn.z)
        left.add(inter); right.add(inter)
        val segment = getSegment(last, current)
        if (segment != null) {
            for (sibling in incidentFaces(this, segment)) {
                if (sibling === face || sibling.points.any { it === inter }) continue
                val pts = sibling.points
                for (i in pts.indices) {
                    val next = pts[(i + 1) % pts.size]
                    if ((pts[i] === last && next === current) || (pts[i] === current && next === last)) {
                        pts.add(i + 1, inter)
                        break
                    }
                }
            }
            splitSegment(segment, last, inter)
            addSegment(inter, current)
        }
        return inter
    }

    fun splitSegmentByRatio2d(s: Segment, k: Double) {
        splitSegmentOnPoint2d(s, Point(s.p1.xf + k * (s.p2.xf - s.p1.xf), s.p1.yf + k * (s.p2.yf - s.p1.yf)))
    }

    fun splitSegmentOnPoint2d(s: Segment, pIn: Point): Segment {
        // Align Point p on segment s in 2D
        val a = s.p1; val b = s.p2
        val p = addPoint(pIn.xf, pIn.yf)
        Point.align3dFrom2d(a, b, p)
        // Add the point p to both faces, between a and b
        for (face in searchFacesWithAB(a, b)) {
            val pts = face.points
            if (pts.contains(p)) continue
            for (i in pts.indices) {
                val next = pts[(i + 1) % pts.size]
                if ((pts[i] === a && next === b) || (pts[i] === b && next === a)) {
                    pts.add(i + 1, p)
                    break
                }
            }
        }
        // Reduce segment s to [a, p]
        splitSegment(s, a, p)
        // And add a new segment p,b
        addSegment(p, b)
        return s
    }

    fun splitAllFacesByPlane3d(plane: Plane) = forEachFaceReverse { splitFaceByPlane3d(it, plane) }
    fun splitAllFacesBySegment2d(a: Point, b: Point) = forEachFaceReverse { splitFaceBySegment2d(it, a, b) }

    fun splitAllFacesByLine2d(a: Point, b: Point) {
        val dx = (b.xf - a.xf) * 1000; val dy = (b.yf - a.yf) * 1000
        splitAllFacesBySegment2d(Point(a.xf + dx, a.yf + dy), Point(b.xf - dx, b.yf - dy))
    }

    // Split a single face by the line through a,b, extended just far enough to clear that face.
    fun splitFaceByLine2d(face: Face, a: Point, b: Point) {
        val dx = b.xf - a.xf; val dy = b.yf - a.yf
        val len = hypot(dx, dy)
        if (len == 0.0) return
        val reach = face.points.fold(0.0) { m, p -> max(m, Point.distance2d(a, p)) } + 1
        val k = reach / len
        splitFaceBySegment2d(face, Point(a.xf - dx * k, a.yf - dy * k), Point(a.xf + dx * k, a.yf + dy * k))
    }

    // Find the face where b is a vertex adjacent to both a and c (edges [b,a] and [b,c])
    fun faceAtVertex(a: Point, b: Point, c: Point): Face? {
        val facesA = searchFacesWithAB(a, b)
        val facesC = searchFacesWithAB(b, c)
        return facesA.find { facesC.contains(it) }
    }

    fun splitCross3d(p1: Point, p2: Point) = splitAllFacesByPlane3d(Plane.across(p1.toVector3(), p2.toVector3()))

    fun splitCross2d(p1: Point, p2: Point) {
        val nx = p2.yf - p1.yf; val ny = -(p2.xf - p1.xf)
        val mx = (p1.xf + p2.xf) / 2; val my = (p1.yf + p2.yf) / 2
        splitAllFacesByLine2d(Point(mx + nx, my + ny), Point(mx - nx, my - ny))
    }

    fun splitBy3d(p1: Point, p2: Point) = splitAllFacesByPlane3d(Plane.by(p1.toVector3(), p2.toVector3()))
    fun splitBy2d(p1: Point, p2: Point) = splitAllFacesBySegment2d(p1, p2)

    fun splitPerpendicular2d(s: Segment, point: Point) =
        splitAllFacesBySegment2d(point, requireNotNull(Segment.project2d(s, point)))

    fun splitPerpendicular3d(s: Segment, point: Point) =
        splitAllFacesByPlane3d(Plane.orthogonal(s.p1.toVector3(), s.p2.toVector3(), point.toVector3()))

    // Crease between segment and point: fold that brings the line onto the point.
    fun splitParallel2d(s: Segment, point: Point) {
        val dx = s.p2.xf - s.p1.xf; val dy = s.p2.yf - s.p1.yf
        val l2 = dx * dx + dy * dy
        if (l2 == 0.0) return
        val t = ((point.xf - s.p1.xf) * dx + (point.yf - s.p1.yf) * dy) / l2
        val footXf = s.p1.xf + t * dx; val footYf = s.p1.yf + t * dy
        if (Point.distance2d(point.xf, point.yf, footXf, footYf) < 1e-9) return
        splitCross2d(point, Point(footXf, footYf))
    }

    fun splitParallel3d(s: Segment, point: Point) {
        val foot = Vector3.closestPoint(point.toVector3(), s.p1.toVector3(), s.p2.toVector3())
        if (Vector3.length3d(Vector3.sub(point.toVector3(), foot)) < 1e-9) return
        splitAllFacesByPlane3d(Plane.across(point.toVector3(), foot))
    }

    fun bisector3d(a: Point, b: Point, c: Point, d: Point) {
        val closest = Segment.closestSegment(a, b, c, d)
        val p = closest.p; val q = closest.q
        if (p.x == q.x && p.y == q.y && p.z == q.z) {
            fun farther(u: Point, v: Point): Point {
                val du = Vector3.length3d(Vector3.sub(u.toVector3(), p))
                val dv = Vector3.length3d(Vector3.sub(v.toVector3(), p))
                return if (du > dv) u else v
            }
            bisector3dPoints(farther(a, b).toVector3(), p, farther(c, d).toVector3())
        } else {
            splitAllFacesByPlane3d(Plane.across(p, q))
        }
    }

    fun bisector2d(s1: Segment, s2: Segment) {
        val inter = Segment.intersection2dLines(s1.p1, s1.p2, s2.p1, s2.p2)
        if (inter != null) {
            val a = if (Point.distance2d(inter, s1.p1) < Point.distance2d(inter, s1.p2)) s1.p2 else s1.p1
            val b = if (Point.distance2d(inter, s2.p1) < Point.distance2d(inter, s2.p2)) s2.p2 else s2.p1
            bisector2dPoints(a, inter, b)
        } else {
            val middleXf = (s1.p1.xf + s2.p1.xf) / 2; val middleYf = (s1.p1.yf + s2.p1.yf) / 2
            val p1p2Xf = s1.p2.xf - s1.p1.xf; val p1p2Yf = s1.p2.yf - s1.p1.yf
            splitAllFacesByLine2d(Point(middleXf, middleYf), Point(middleXf + p1p2Xf, middleYf + p1p2Yf))
        }
    }

    // Split faces by a plane between two segments [ap] [pc].
    fun bisector3dPoints(a: Vector3, p: Vector3, c: Vector3) {
        val denom = hypot(hypot(p.x - c.x, p.y - c.y), p.z - c.z)
        if (denom == 0.0) return
        val k = hypot(hypot(p.x - a.x, p.y - a.y), p.z - a.z) / denom
        val e = Vector3(p.x + k * (c.x - p.x), p.y + k * (c.y - p.y), p.z + k * (c.z - p.z))
        splitAllFacesByPlane3d(Plane.across(a, e))
    }

    // Split the face at vertex b (adjacent to a and c) by the bisector of angle a-b-c.
    fun bisector2dPoints(a: Point, b: Point, c: Point) {
        val v1n = Point.normalize(a.xf - b.xf, a.yf - b.yf)
        val v2n = Point.normalize(c.xf - b.xf, c.yf - b.yf)
        val dirXf = v1n.xf + v2n.xf; val dirYf = v1n.yf + v2n.yf
        val targetXf = b.xf + dirXf; val targetYf = b.yf + dirYf

        val vertex = getPoint(b.xf, b.yf)
        val face = vertex?.let { faceAtVertex(a, it, c) }
        if (face != null) {
            splitFaceByLine2d(face, vertex!!, Point(targetXf, targetYf))
        } else {
            splitAllFacesByLine2d(b, Point(targetXf, targetYf))
        }
    }

    // Rotate around axis Segment, by angle, the list of Points
    fun rotate(s: Segment?, angle: Double, list: List<Point> = points) {
        if (s == null) return
        val angleRd = angle * PI / 180
        val ax = s.p1.x; val ay = s.p1.y; val az = s.p1.z
        var nx = s.p2.x - ax; var ny = s.p2.y - ay; var nz = s.p2.z - az
        val len = hypot(hypot(nx, ny), nz)
        if (len == 0.0) return
        val n = 1 / len
        nx *= n; ny *= n; nz *= n
        val sinA = sin(angleRd); val cosA = cos(angleRd)
        val c1 = 1 - cosA
        val c11 = c1 * nx * nx + cosA; val c12 = c1 * nx * ny - nz * sinA; val c13 = c1 * nx * nz + ny * sinA
        val c21 = c1 * ny * nx + nz * sinA; val c22 = c1 * ny * ny + cosA; val c23 = c1 * ny * nz - nx * sinA
        val c31 = c1 * nz * nx - ny * sinA; val c32 = c1 * nz * ny + nx * sinA; val c33 = c1 * nz * nz + cosA
        list.forEach { p ->
            val ux = p.x - ax; val uy = p.y - ay; val uz = p.z - az
            p.x = ax + c11 * ux + c12 * uy + c13 * uz
            p.y = ay + c21 * ux + c22 * uy + c23 * uz
            p.z = az + c31 * ux + c32 * uy + c33 * uz
        }
    }

    // Adjust one point 3d with 2d length of segments
    fun adjust(point: Point): Double {
        val segs = searchSegmentsOnePoint(point)
        var max = 0.1
        var i = 0
        while (max > 0.001 && i < 200) {
            max = 0.0
            val pm = Vector3(0.0, 0.0, 0.0)
            for (s in segs) {
                val lg3d = Segment.length3d(s) / scale
                if (lg3d == 0.0) continue
                val lg2d = Segment.length2d(s)
                val d = abs(lg2d - lg3d)
                if (d > max) max = d
                val r = lg2d / lg3d
                val other = when {
                    s.p2 === point -> s.p1
                    s.p1 === point -> s.p2
                    else -> null
                } ?: continue
                pm.x += other.x + (point.x - other.x) * r
                pm.y += other.y + (point.y - other.y) * r
                pm.z += other.z + (point.z - other.z) * r
            }
            if (segs.isNotEmpty()) {
                point.x = pm.x / segs.size
                point.y = pm.y / segs.size
                point.z = pm.z / segs.size
            }
            i++
        }
        return max
    }

    fun adjustList(list: List<Point>): Double {
        var max = 0.1
        var i = 0
        while (max > 0.001 && i < 200) {
            max = 0.0
            for (point in list) {
                val d = abs(adjust(point))
                if (d > max) max = d
            }
            i++
        }
        return max
    }

    // Checks segments and selects segments with anormal length
    fun checkSegments() {
        for (s in segments) {
            val lg3d = Segment.length3d(s) / scale
            val lg2d = Segment.length2d(s)
            if (abs(lg2d - lg3d) > 1) s.select = true
        }
    }

    fun searchSegmentsOnePoint(a: Point): List<Segment> = segments.filter { it.p1 === a || it.p2 === a }

    fun searchFacesWithAB(a: Point, b: Point): List<Face> {
        val seg = getSegment(a, b)
        if (seg != null) {
            val list = incidentFaces(this, seg)
            if (list.isNotEmpty()) return list
        }
        return faces.filter { f -> f.points.any { it === a } && f.points.any { it === b } }
    }

    // Segments that border both faceA and faceB (their shared edges)
    fun sharedSegments(faceA: Face, faceB: Face): List<Segment> {
        val pts = faceA.points
        val segs = mutableListOf<Segment>()
        for (i in pts.indices) {
            val s = getSegment(pts[i], pts[(i + 1) % pts.size])
            if (s != null && incidentFaces(this, s).contains(faceB)) segs.add(s)
        }
        return segs
    }

    fun movePoints(dx: Double, dy: Double, dz: Double, pointsIn: List<Point>) {
        val list = if (pointsIn.isEmpty()) points else pointsIn
        list.forEach { it.x += dx; it.y += dy; it.z += dz }
    }

    fun moveOnPoint(p0: Point, pts: List<Point>) {
        pts.forEach { it.x = p0.x; it.y = p0.y; it.z = p0.z }
    }

    fun moveOnSegment(s: Segment, pts: List<Point>) {
        val a = s.p1; val b = s.p2
        val lengthAB = hypot(b.xf - a.xf, b.yf - a.yf)
        pts.forEach { p ->
            val t = if (lengthAB == 0.0) 0.0 else hypot(p.xf - a.xf, p.yf - a.yf) / lengthAB
            p.x = a.x + t * (b.x - a.x)
            p.y = a.y + t * (b.y - a.y)
            p.z = a.z + t * (b.z - a.z)
        }
    }

    // snap points near each other, and round coordinates
    fun snapPoints() {
        for (i in points.indices) {
            val p = points[i]
            if (abs(p.z) <= 6) p.z = 0.0
            p.x = Math.round(p.x * 100) / 100.0
            p.y = Math.round(p.y * 100) / 100.0
            p.z = Math.round(p.z * 100) / 100.0
            for (j in i + 1 until points.size) {
                val q = points[j]
                if (hypot(hypot(q.x - p.x, q.y - p.y), q.z - p.z) < 1) {
                    q.x = p.x; q.y = p.y; q.z = p.z
                }
            }
        }
    }

    private fun axisSegmentFor(name: String): Segment {
        val key = name.lowercase().replace("angle", "")
        val p2 = when (key) {
            "y" -> Point(0.0, 0.0, 0.0, 1.0, 0.0)
            "z" -> Point(0.0, 0.0, 0.0, 0.0, 1.0)
            else -> Point(0.0, 0.0, 1.0, 0.0, 0.0)
        }
        return Segment(Point(0.0, 0.0, 0.0, 0.0, 0.0), p2)
    }

    // Turn the model around axis ("x"/"y"/"z") by angle
    fun turn(axis: String, angle: Double) = rotate(axisSegmentFor(axis), angle, points)

    // Turn the model around an explicit axis Segment by angle
    fun turn(axis: Segment, angle: Double) = rotate(axis, angle, points)

    private fun fail(segment: Segment, center: Point, msg: String) {
        println("$msg for segment ${indexOf(segment)} and center ${indexOf(center)}")
    }

    // Reverse Inside Fold: pushes the tip of a crease between the two panels that meet at
    // `center` (e.g. a crane's neck). tn is the fold's absolute progress (0 start, 1 fully
    // reversed); may be called every frame of an animation with an increasing tn. The
    // movement's geometry (mobile point, gates, sweep angle) is frozen once per fold, on the
    // first call, and cached on the segment (recomputed if tn goes backwards: a new fold
    // starts over) -- see js/Model.js's reverseFold for the full rationale.
    fun reverseFold(segment: Segment, center: Point, tn: Double) {
        var mobilePoint: Point? = null
        if (segment.p1 === center) mobilePoint = segment.p2
        else if (segment.p2 === center) mobilePoint = segment.p1
        if (mobilePoint == null) { fail(segment, center, "Invalid mobile point"); return }
        val mobile = mobilePoint

        var cache = segment.reverseFoldCache
        if (cache == null || cache.center !== center || tn < cache.lastTn) {
            val excluded = HashSet<Point>()
            excluded.add(center)
            for (seg in searchSegmentsOnePoint(center)) {
                val other = if (seg.p1 === center) seg.p2 else seg.p1
                if (other !== mobile) excluded.add(other)
            }
            val pointsToRotate = mutableListOf<Point>()
            val toVisit = ArrayDeque<Point>()
            toVisit.addLast(mobile)
            while (toVisit.isNotEmpty()) {
                val current = toVisit.removeLast()
                if (excluded.contains(current)) continue
                excluded.add(current)
                pointsToRotate.add(current)
                for (seg in searchSegmentsOnePoint(current)) {
                    val other = if (seg.p1 === current) seg.p2 else seg.p1
                    if (!excluded.contains(other)) toVisit.addLast(other)
                }
            }
            if (pointsToRotate.isEmpty()) { fail(segment, center, "No points to rotate"); return }

            val faces = searchFacesWithAB(segment.p1, segment.p2)
            if (faces.size != 2) { fail(segment, center, "Expected 2 faces adjacent to segment"); return }
            fun gateOf(face: Face): Point {
                val pts = face.points
                val i = pts.indexOfFirst { it === center }
                val prev = pts[(i - 1 + pts.size) % pts.size]
                return if (prev === mobile) pts[(i + 1) % pts.size] else prev
            }
            val gate0 = gateOf(faces[0]); val gate1 = gateOf(faces[1])

            val normalVec = normal(faces[0])

            val gx = (gate0.x + gate1.x) / 2; val gy = (gate0.y + gate1.y) / 2; val gz = (gate0.z + gate1.z) / 2
            var ax = gx - center.x; var ay = gy - center.y; var az = gz - center.z
            val axisLen = hypot(hypot(ax, ay), az)
            if (axisLen < 1e-9) { fail(segment, center, "Invalid axis"); return }
            ax /= axisLen; ay /= axisLen; az /= axisLen

            val v1x = mobile.x - center.x; val v1y = mobile.y - center.y; val v1z = mobile.z - center.z
            val along = v1x * ax + v1y * ay + v1z * az
            val targetX = center.x + 2 * along * ax - v1x
            val targetY = center.y + 2 * along * ay - v1y
            val targetZ = center.z + 2 * along * az - v1z

            val v2x = targetX - center.x; val v2y = targetY - center.y; val v2z = targetZ - center.z
            val dotV = v1x * v2x + v1y * v2y + v1z * v2z
            val crossX = v1y * v2z - v1z * v2y; val crossY = v1z * v2x - v1x * v2z; val crossZ = v1x * v2y - v1y * v2x
            val sinPart = crossX * normalVec[0] + crossY * normalVec[1] + crossZ * normalVec[2]
            val sweepAngle = atan2(sinPart, dotV) * 180 / PI

            val axisSegment = Segment(
                Point(center.xf, center.yf, center.x, center.y, center.z),
                Point(
                    center.x + normalVec[0], center.y + normalVec[1],
                    center.x + normalVec[0], center.y + normalVec[1], center.z + normalVec[2],
                ),
            )

            val mobileRadius = hypot(hypot(v1x, v1y), v1z)
            val maxGateOpenFraction = 0.3

            fun gateData(p: Point, face: Face): GateData {
                val faceNormal = normal(face)
                val gn0 = -faceNormal[0]; val gn1 = -faceNormal[1]; val gn2 = -faceNormal[2]
                val hx = p.x - center.x; val hy = p.y - center.y; val hz = p.z - center.z
                val r = hypot(hypot(hx, hy), hz)
                val alongN = hx * gn0 + hy * gn1 + hz * gn2
                var ix = hx - alongN * gn0; var iy = hy - alongN * gn1; var iz = hz - alongN * gn2
                val iLen = hypot(hypot(ix, iy), iz).let { if (it == 0.0) 1.0 else it }
                ix /= iLen; iy /= iLen; iz /= iLen
                val edgeLength = hypot(hypot(p.x - mobile.x, p.y - mobile.y), p.z - mobile.z)
                val k = (r * r + mobileRadius * mobileRadius - edgeLength * edgeLength) / 2
                val peakCosPhi = max(-1.0, min(1.0, k / (r * mobileRadius)))
                val exactPeakZOff = r * sqrt(max(1 - peakCosPhi * peakCosPhi, 0.0))
                val peakZOff = min(exactPeakZOff, r * maxGateOpenFraction)
                return GateData(p, r, ix, iy, iz, gn0, gn1, gn2, peakZOff)
            }

            cache = ReverseFoldCache(
                center, pointsToRotate, axisSegment, sweepAngle, 0.0, 0.0,
                listOf(gateData(gate0, faces[0]), gateData(gate1, faces[1])),
            )
            segment.reverseFoldCache = cache
        }

        val targetAngle = cache.sweepAngle * tn
        rotate(cache.axisSegment, targetAngle - cache.appliedAngle, cache.pointsToRotate)
        cache.appliedAngle = targetAngle
        cache.lastTn = tn

        val h = sin(PI * tn)
        for (g in cache.gates) {
            val zOff = g.peakZOff * h
            val inPlaneLen = sqrt(max(g.r * g.r - zOff * zOff, 0.0))
            g.point.x = center.x + g.ix * inPlaneLen + g.gateNormalX * zOff
            g.point.y = center.y + g.iy * inPlaneLen + g.gateNormalY * zOff
            g.point.z = center.z + g.iz * inPlaneLen + g.gateNormalZ * zOff
        }

        segments.forEach { it.select = false }

        if (tn >= 1) {
            segment.assignment = when (segment.assignment) {
                "V" -> "M"
                "M" -> "V"
                else -> "U"
            }
        }
    }

    // Zoom model. Scales 3D distances by `scale`; adjust()/checkSegments() (which divide
    // length3d by this.scale) must stay in sync, the same way scaleModel() keeps them.
    fun zoom(scale: Double, x: Double = 0.0, y: Double = 0.0) {
        this.scale *= scale
        points.forEach { p ->
            p.x = x + (p.x - x) * scale
            p.y = y + (p.y - y) * scale
            p.z *= scale
        }
    }

    // Fit model in 400x400
    fun fit() {
        val b = get3DBounds()
        val span = max(b.xMax - b.xMin, b.yMax - b.yMin).let { if (it == 0.0) 1.0 else it }
        val s = 400 / span
        val cx = (b.xMin + b.xMax) / 2; val cy = (b.yMin + b.yMax) / 2
        points.forEach { p ->
            p.x = (p.x - cx) * s
            p.y = (p.y - cy) * s
            p.z *= s
        }
    }

    // Offset faces by dz
    fun offset(dz: Double, facesIn: List<Face>) {
        if (dz == 0.0 || facesIn.isEmpty()) {
            faces.forEach { it.offset = 0.0 }
        } else {
            facesIn.forEach { it.offset += dz / 10 }
        }
    }

    fun bounds(getX: (Point) -> Double, getY: (Point) -> Double): Bounds {
        var xMin = Double.POSITIVE_INFINITY; var xMax = Double.NEGATIVE_INFINITY
        var yMin = Double.POSITIVE_INFINITY; var yMax = Double.NEGATIVE_INFINITY
        points.forEach { p ->
            xMin = min(xMin, getX(p)); xMax = max(xMax, getX(p))
            yMin = min(yMin, getY(p)); yMax = max(yMax, getY(p))
        }
        return Bounds(xMin, xMax, yMin, yMax)
    }

    fun get2DBounds(): Bounds = bounds({ it.xf }, { it.yf })
    fun get3DBounds(): Bounds = bounds({ it.x }, { it.y })

    fun scaleModel(scale: Double) {
        this.scale *= scale
        points.forEach { it.x *= scale; it.y *= scale; it.z *= scale }
    }

    // Snapshot 3D point positions for animation frames (lightweight snapshot)
    fun snapshotPositions(): DoubleArray {
        val coords = DoubleArray(points.size * 3)
        for (i in points.indices) {
            val p = points[i]
            coords[i * 3] = p.x; coords[i * 3 + 1] = p.y; coords[i * 3 + 2] = p.z
        }
        return coords
    }

    // Restore 3D point positions from a lightweight snapshot
    fun restorePositions(coords: DoubleArray) {
        for (i in points.indices) {
            points[i].x = coords[i * 3]
            points[i].y = coords[i * 3 + 1]
            points[i].z = coords[i * 3 + 2]
        }
    }

    // Copy every field from `other` into this model (Kotlin has no Object.assign)
    fun restoreFrom(other: Model) {
        points = other.points
        segments = other.segments
        faces = other.faces
        state = other.state
        scale = other.scale
        labels = other.labels
        textures = other.textures
        lines = other.lines
        snap = other.snap
    }

    // Serialize the model to an opaque string snapshot (see class doc: not JSON)
    fun serialize(): String {
        val pointIndex = HashMap<Point, Int>()
        points.forEachIndexed { i, p -> pointIndex[p] = i }
        val sb = StringBuilder()
        sb.append(state.name).append(',').append(scale).append(',')
            .append(labels).append(',').append(textures).append(',')
            .append(lines).append(',').append(snap).append('\n')
        sb.append(points.size).append('\n')
        for (p in points) {
            sb.append(p.xf).append(',').append(p.yf).append(',')
                .append(p.x).append(',').append(p.y).append(',').append(p.z).append('\n')
        }
        sb.append(segments.size).append('\n')
        for (s in segments) {
            sb.append(pointIndex[s.p1]).append(',').append(pointIndex[s.p2]).append(',')
                .append(s.assignment).append('\n')
        }
        sb.append(faces.size).append('\n')
        for (f in faces) {
            sb.append(f.offset).append(',')
                .append(f.points.joinToString(" ") { pointIndex[it].toString() }).append('\n')
        }
        return sb.toString()
    }

    companion object {
        @JvmStatic
        fun splitSegment(segment: Segment?, last: Point, inter: Point) {
            if (segment == null) return
            if (segment.p1 === last) segment.p2 = inter
            else if (segment.p2 === last) segment.p1 = inter
        }

        // Deserialize a snapshot produced by Model.serialize() into a new Model
        @JvmStatic
        fun deserialize(text: String): Model {
            val lines = text.split('\n')
            var li = 0
            fun nextLine(): String = lines[li++]

            val model = Model()
            val header = nextLine().split(',')
            model.state = State.valueOf(header[0])
            model.scale = header[1].toDouble()
            model.labels = header[2].toBoolean()
            model.textures = header[3].toBoolean()
            model.lines = header[4].toBoolean()
            model.snap = header[5].toBoolean()

            val nPoints = nextLine().toInt()
            val points = ArrayList<Point>(nPoints)
            repeat(nPoints) {
                val f = nextLine().split(',')
                points.add(Point(f[0].toDouble(), f[1].toDouble(), f[2].toDouble(), f[3].toDouble(), f[4].toDouble()))
            }
            model.points = points

            val nSegments = nextLine().toInt()
            val segments = ArrayList<Segment>(nSegments)
            repeat(nSegments) {
                val f = nextLine().split(',')
                segments.add(Segment(points[f[0].toInt()], points[f[1].toInt()], f[2]))
            }
            model.segments = segments

            val nFaces = nextLine().toInt()
            val faces = ArrayList<Face>(nFaces)
            repeat(nFaces) {
                val comma = nextLine()
                val offsetEnd = comma.indexOf(',')
                val offset = comma.substring(0, offsetEnd).toDouble()
                val rest = comma.substring(offsetEnd + 1)
                val idx = if (rest.isBlank()) emptyList() else rest.split(' ').map { it.toInt() }
                val face = Face(idx.map { points[it] }.toMutableList())
                face.offset = offset
                faces.add(face)
            }
            model.faces = faces

            return model
        }

        // Get a segment from two points -- exposed as a companion overload used by tests
        // that don't need a full Model instance; Model.getSegment(a, b) is the normal entry point.

        private fun normalOf(pts: List<Point>): DoubleArray {
            if (pts.size < 3) return doubleArrayOf(0.0, 0.0, 1.0)
            var nx = 0.0; var ny = 0.0; var nz = 0.0
            for (i in pts.indices) {
                val a = pts[i]; val b = pts[(i + 1) % pts.size]
                nx += (a.y - b.y) * (a.z + b.z)
                ny += (a.z - b.z) * (a.x + b.x)
                nz += (a.x - b.x) * (a.y + b.y)
            }
            val len = hypot(hypot(nx, ny), nz)
            if (len < 1e-6) return doubleArrayOf(0.0, 0.0, 1.0)
            return doubleArrayOf(nx / len, ny / len, nz / len)
        }

        // Compute 3D unit normal vector [nx, ny, nz]
        @JvmStatic
        fun normal(face: Face): DoubleArray = normalOf(face.points)

        @JvmStatic
        fun normal(points: List<Point>): DoubleArray = normalOf(points)

        // Compute dihedral angle in degrees between two faces
        @JvmStatic
        fun dihedralAngle(face1: Face, face2: Face): Int {
            val n1 = normal(face1); val n2 = normal(face2)
            val dot = max(-1.0, min(1.0, n1[0] * n2[0] + n1[1] * n2[1] + n1[2] * n2[2]))
            return Math.round(acos(dot) * 180 / PI).toInt()
        }

        // Signed dihedral angle in degrees between two faces sharing edge [a, b], in the FOLD
        // edges_foldAngle convention: 0 flat, negative Mountain, positive Valley.
        @JvmStatic
        fun signedDihedralAngle(face1: Face, face2: Face, a: Point, b: Point): Int {
            val edgeDir = edgeDirection(face1, a, b) ?: return 0
            val n1 = normal(face1); val n2 = normal(face2)
            val crossX = n1[1] * n2[2] - n1[2] * n2[1]
            val crossY = n1[2] * n2[0] - n1[0] * n2[2]
            val crossZ = n1[0] * n2[1] - n1[1] * n2[0]
            val sinV = crossX * edgeDir.x + crossY * edgeDir.y + crossZ * edgeDir.z
            val cosV = n1[0] * n2[0] + n1[1] * n2[1] + n1[2] * n2[2]
            return Math.round(atan2(sinV, cosV) * 180 / PI).toInt()
        }

        private fun edgeDirection(face: Face?, a: Point, b: Point): Vector3? {
            val pts = face?.points ?: return null
            for (i in pts.indices) {
                val p0 = pts[i]; val p1 = pts[(i + 1) % pts.size]
                if ((p0 === a && p1 === b) || (p0 === b && p1 === a)) {
                    return Vector3.normalize(Vector3.sub(p1.toVector3(), p0.toVector3()))
                }
            }
            return null
        }

        // Up to two faces incident to the given segment
        @JvmStatic
        fun incidentFaces(model: Model?, segment: Segment?): List<Face> {
            if (model == null || segment == null) return emptyList()
            val result = mutableListOf<Face>()
            for (face in model.faces) {
                if (faceContainsSegment(face, segment)) {
                    if (!result.contains(face)) result.add(face)
                    if (result.size == 2) break
                }
            }
            return result
        }

        private fun faceContainsSegment(face: Face, segment: Segment): Boolean {
            val pts = face.points
            for (i in pts.indices) {
                val a = pts[i]; val b = pts[(i + 1) % pts.size]
                if ((a === segment.p1 && b === segment.p2) || (a === segment.p2 && b === segment.p1)) return true
            }
            return false
        }
    }

    // Get a segment from two points
    fun getSegment(p1: Point, p2: Point): Segment? =
        segments.find { (it.p1 === p1 && it.p2 === p2) || (it.p1 === p2 && it.p2 === p1) }
}

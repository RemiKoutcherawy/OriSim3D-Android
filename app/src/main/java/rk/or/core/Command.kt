package rk.or.core

import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sign

/** Port of js/CommandArea.js's minimal contract used by Command (an on-screen console). */
interface CommandArea {
    fun addLine(text: String)
}

/** Port of the `view3d` shape Command.js relies on (only used by the `writeSvg` command). */
interface CommandView3d {
    fun updateCanvasCoords()
}

/**
 * Port of js/ReadWrite.js's file-IO surface as used by Command. Android's actual
 * Storage-Access-Framework-backed implementation is Phase 2; these default to
 * no-ops so the command language is complete (and testable) without it.
 */
interface ReadWriteHooks {
    fun readFileAsText(filename: String?, onResult: (String?) -> Unit) = onResult(null)
    fun loadText(cmd: Command, text: String) {}
    fun writeFile(text: String, filename: String?) {}
    fun writeSVG(cmd: Command, filename: String?) {}
    fun writeFold(model: Model, filename: String?) {}
    fun writeDiagrams(steps: List<Model>, filename: String?) {}
}

private val noOpReadWrite = object : ReadWriteHooks {}

private fun now(): Double = System.nanoTime() / 1_000_000.0

private fun <T> MutableList<T>.popOrNull(): T? = if (isEmpty()) null else removeAt(size - 1)

/** One entry of Command.done: either a full model snapshot, or a lightweight anim-frame one. */
sealed class UndoSnapshot {
    class Full(val text: String) : UndoSnapshot()
    class AnimFrame(val coords: DoubleArray) : UndoSnapshot()
}

/**
 * Port of js/Command.js. Tokenizes and interprets a whitespace-separated text
 * script and applies it to a Model, driving animated transitions via Interpolator.
 */
class Command(val model: Model, var view3d: CommandView3d? = null) {
    val tokenTodo: MutableList<String> = mutableListOf()
    var iToken: Int = 0
    val done: MutableList<UndoSnapshot> = mutableListOf()
    val instructions: MutableList<String> = mutableListOf()

    // Time interpolated at instant 'p' preceding and instant 'n' now
    var tpi: Double = 0.0
    var tni: Double = 1.0
    var interpolator: Interp = Interpolator.LinearInterpolator

    // Animation
    var duration: Double = 0.0
    var tStart: Double = 0.0

    var commandArea: CommandArea? = null
    var readWrite: ReadWriteHooks = noOpReadWrite

    // When true, newly queued instructions pause instead of auto-running, so a step-by-step
    // debug control can advance them one line at a time.
    var stepMode: Boolean = false

    // Scratch state for the multi-frame 'fit' command
    var fitScale: Double = 1.0
    var fitCx: Double = 0.0
    var fitCy: Double = 0.0

    private var idxBefore: Int = 0

    // The main entry point: executes a string of commands
    fun command(cde: String): Command {
        commandArea?.addLine(cde)
        val tokens = tokenize(cde)
        val first = tokens.getOrNull(0)
        if (first == "d" || first == "define") {
            done.clear(); tokenTodo.clear(); iToken = 0; instructions.clear()
            // A fresh model replaces whatever script was running before.
            model.state = State.RUN
        } else if (first == "u" || first == "undo") {
            // Drop the snapshot of the live model; runUndo restores the previous one.
            if (done.isNotEmpty()) {
                done.popOrNull()
                instructions.popOrNull()
            }
            model.state = State.UNDO
            return this
        } else if (first == "run") {
            model.state = State.RUN
            stepMode = false
            return this
        } else if (first == "stop") {
            while (tokenTodo.size > iToken) tokenTodo.removeAt(tokenTodo.size - 1)
            return this
        }
        if (model.state == State.UNDO) {
            model.state = State.RUN
        }
        tokenTodo.addAll(tokens)
        if (stepMode) {
            model.state = State.PAUSE
        }
        return this
    }

    fun stepLine(): Boolean {
        if (model.state == State.PAUSE) {
            model.state = State.RUN
        }
        var advanced = false
        while (model.state == State.ANIM || iToken < tokenTodo.size) {
            if (model.state == State.ANIM) {
                tStart = now() - duration - 1 // force tn >= 1
                runAnim()
                advanced = true
                continue
            }
            val wasNewline = peek() == "\n"
            if (!runNext()) break
            advanced = true
            if (wasNewline) break
        }
        model.state = State.PAUSE
        return advanced
    }

    // Tokenize: split the input String into an Array of String
    fun tokenize(input: String): List<String> {
        var cleaned = Regex("(:?//|<!--)[^\r\n]*").replace(input, "") // Remove comments
        cleaned = Regex("(?m)^\\s*$").replace(cleaned, "") // Remove spaces-only lines
        cleaned = Regex("\n{2,}").replace(cleaned, "\n") // Remove empty lines
        cleaned = cleaned.trim()
        return Regex("\\S+|\n").findAll(cleaned).map { it.value }.toList()
    }

    fun isNumber(token: String?): Boolean = token != null && token != "\n" && token.toDoubleOrNull() != null

    fun peek(): String? = tokenTodo.getOrNull(iToken)

    fun next(): String? {
        val t = tokenTodo.getOrNull(iToken)
        iToken++
        return t
    }

    // Optional number with default
    fun num(fallback: Double): Double = if (isNumber(peek())) next()!!.toDouble() else fallback

    // Consume one token (always advances, like the previous idx++)
    fun token(prefix: String): Any? {
        val t = listTokens(tokenTodo, iToken, prefix).getOrNull(0)
        iToken++
        return t
    }

    // Consume a run of tokens with the same prefix (p0 p1 p2...)
    fun tokens(prefix: String): List<Any> {
        val list = listTokens(tokenTodo, iToken, prefix)
        iToken += list.size
        return list
    }

    // Animation delta between previous and current interpolated time
    val dt: Double get() = tni - tpi

    // State machine returns true if the model needs redrawing. Called every animation frame.
    fun anim(): Boolean = when (model.state) {
        State.PAUSE -> false
        State.RUN -> runNext()
        State.UNDO -> runUndo()
        State.ANIM -> runAnim()
    }

    fun runNext(): Boolean {
        if (iToken >= tokenTodo.size) {
            return false
        }
        idxBefore = iToken
        // Handle time command to start animation and switch to Anim
        if (peek() == "t" || peek() == "time") {
            // Snapshot before the animated line (state is still run). Without it, undo only
            // has anim-frame snapshots and never returns to State.RUN.
            pushUndo()
            iToken++
            duration = next()?.toDoubleOrNull() ?: 0.0
            tStart = now()
            tpi = 0.0
            model.state = State.ANIM
            return true
        }
        execute(iToken)
        doneInstructions(idxBefore, iToken)
        return true
    }

    fun runUndo(): Boolean {
        // Empty stack: stay in undo and nothing ever runs again.
        if (done.isEmpty()) {
            model.state = State.RUN
            return false
        }
        popUndo()
        // Continue undo through per-frame snapshots of an animated line
        if (model.state == State.ANIM) {
            model.state = State.UNDO
            return true
        }
        model.state = if (stepMode) State.PAUSE else State.RUN
        return true
    }

    fun runAnim(): Boolean {
        val tn = min((now() - tStart) / duration, 1.0)
        val targetTni = interpolator(tn)
        val iBeginAnim = iToken
        // Commands like 'a' (adjust) resolve a point's 3d position iteratively from its
        // position in the *previous* step, so replay this frame's motion as small fixed-size
        // substeps so the result only depends on the animated distance, not on how many real
        // frames covered it.
        val maxStep = 0.01
        val steps = max(1, ceil(abs(targetTni - tpi) / maxStep).toInt())
        for (i in 1..steps) {
            tni = tpi + (targetTni - tpi) * i / steps
            iToken = iBeginAnim
            val recordUndo = i == steps
            while (iToken < tokenTodo.size && peek() != "\n") {
                execute(iToken, recordUndo)
            }
            tpi = tni
        }
        if (tn >= 1) {
            tni = 1.0
            tpi = 0.0
            if (model.snap) {
                model.snapPoints()
            }
            doneInstructions(idxBefore, iToken)
            model.state = State.RUN
            return true
        }
        iToken = iBeginAnim
        return true
    }

    fun doneInstructions(idxBefore: Int, idxAfter: Int) {
        val doneCommands = tokenTodo.subList(idxBefore, idxAfter).joinToString(" ")
        if (doneCommands == "undo") {
            instructions.popOrNull()
        } else if (doneCommands.isNotEmpty() && doneCommands != "\n") {
            instructions.add(doneCommands)
        }
    }

    // Execute one instruction from tokenTodo starting at idx on the model.
    fun execute(idx: Int, pushUndoFlag: Boolean = true) {
        iToken = idx
        val token = next()
        val command = token?.let { COMMANDS[it] }
        if (command != null) {
            command(this)
        } else if (token != "\n") {
            skipUnexpected()
        }
        if (pushUndoFlag) pushUndo()
    }

    fun skipUnexpected() {
        val idx = iToken - 1
        println("Unexpected end of command " + tokenTodo.subList(idx, min(idx + 3, tokenTodo.size)).joinToString(" "))
        iToken = idx
        while (peek() != "\n" && iToken < tokenTodo.size) {
            iToken++
        }
        if (iToken < tokenTodo.size) {
            iToken++
        }
    }

    fun listTokens(tokenList: List<String>, iStartIn: Int, prefix: String): List<Any> {
        val list = mutableListOf<Any>()
        val p = prefix.lowercase()
        val collection: List<Any>? = when (p) {
            "p" -> model.points
            "s" -> model.segments
            "f" -> model.faces
            else -> null
        }
        var iStart = iStartIn
        while (iStart < tokenList.size) {
            val token = tokenList[iStart]
            if (token == "\n" || token.isEmpty() || token[0].lowercaseChar() != p[0]) break
            if (token.length < 2) break
            val n = token.substring(1).toIntOrNull()
            if (n == null || collection == null || n !in collection.indices) break
            list.add(collection[n])
            iStart++
        }
        return list
    }

    fun pushUndo() {
        if (model.state == State.ANIM) {
            done.add(UndoSnapshot.AnimFrame(model.snapshotPositions()))
        } else {
            done.add(UndoSnapshot.Full(model.serialize()))
        }
    }

    fun popUndo() {
        when (val snapshot = done.popOrNull() ?: return) {
            is UndoSnapshot.Full -> model.restoreFrom(Model.deserialize(snapshot.text))
            is UndoSnapshot.AnimFrame -> {
                model.state = State.ANIM
                model.restorePositions(snapshot.coords)
            }
        }
    }
}

private fun reportError(cmd: Command, message: String) {
    cmd.commandArea?.addLine("// $message")
}

private fun take(cmd: Command, prefix: String, n: Int, label: String, apply: (List<Any>) -> Unit) {
    val list = cmd.tokens(prefix)
    if (list.size != n) {
        val end = min(cmd.iToken + n + 1, cmd.tokenTodo.size)
        val got = cmd.tokenTodo.subList(cmd.iToken, end).joinToString(" ")
        reportError(cmd, "$label (got ${list.size}: $got)")
        return
    }
    apply(list)
}

// Consume one token per given prefix (e.g. one segment then one point), reporting a
// commandArea error if any of them is missing, wrong type, or out of order.
private fun takeOne(cmd: Command, prefixes: List<String>, label: String, apply: (List<Any>) -> Unit) {
    val start = cmd.iToken
    val list = prefixes.map { cmd.token(it) }
    if (list.any { it == null }) {
        val got = cmd.tokenTodo.subList(start, cmd.iToken).joinToString(" ")
        reportError(cmd, "$label (got: $got)")
        return
    }
    @Suppress("UNCHECKED_CAST")
    apply(list as List<Any>)
}

private fun select(cmd: Command, prefix: String, collection: List<Selectable>) {
    val selected = cmd.tokens(prefix)
    collection.forEach { o -> o.select = selected.contains(o) && !o.select }
}

private fun turnCmd(axis: String): (Command) -> Unit = { cmd ->
    val angle = (cmd.next()?.toDoubleOrNull() ?: 0.0) * cmd.dt
    cmd.model.turn(axis, angle)
}

private fun define(cmd: Command) {
    cmd.model.init(cmd.num(200.0), cmd.num(200.0))
}

// Replay a recorded instruction list (cmd.instructions) on a fresh headless Model, instantly
// settling any 't'-animated line, one Model snapshot returned per instruction.
fun replaySteps(instructions: List<String>): List<Model> {
    val replay = Command(Model())
    val steps = mutableListOf<Model>()
    for (line in instructions) {
        replay.command(line)
        while (replay.iToken < replay.tokenTodo.size || replay.model.state == State.ANIM) {
            if (replay.model.state == State.ANIM) {
                replay.tStart = now() - replay.duration - 1 // force tn >= 1
            }
            if (!replay.anim()) break
        }
        steps.add(Model.deserialize(replay.model.serialize()))
    }
    return steps
}

private fun splitSegmentCmd(cmd: Command) {
    val s = cmd.token("s") as? Segment ?: return
    val k = cmd.next()?.toDoubleOrNull() ?: return
    if (k in 0.0..1.0) cmd.model.splitSegmentByRatio2d(s, k)
}

private fun rotateCmd(cmd: Command) {
    val s = cmd.token("s") as? Segment
    val angle = (cmd.next()?.toDoubleOrNull() ?: 0.0) * cmd.dt
    cmd.model.rotate(s, angle, cmd.tokens("p").map { it as Point })
}

private fun moveCmd(cmd: Command) {
    val d = cmd.dt
    val dx = (cmd.next()?.toDoubleOrNull() ?: 0.0) * d
    val dy = (cmd.next()?.toDoubleOrNull() ?: 0.0) * d
    val dz = (cmd.next()?.toDoubleOrNull() ?: 0.0) * d
    cmd.model.movePoints(dx, dy, dz, cmd.tokens("p").map { it as Point })
    cmd.model.checkSegments()
}

private fun zoomCmd(cmd: Command) {
    val scale = cmd.next()?.toDoubleOrNull() ?: 1.0
    val a = (1 + cmd.tni * (scale - 1)) / (1 + cmd.tpi * (scale - 1))
    cmd.model.zoom(a, cmd.num(0.0), cmd.num(0.0))
}

private fun fitCmd(cmd: Command) {
    if (cmd.tpi == 0.0) {
        val b = cmd.model.get3DBounds()
        val span = max(b.xMax - b.xMin, b.yMax - b.yMin).let { if (it == 0.0) 1.0 else it }
        cmd.fitScale = 400 / span
        cmd.fitCx = (b.xMin + b.xMax) / 2
        cmd.fitCy = (b.yMin + b.yMax) / 2
    }
    val s = cmd.fitScale
    val a = (1 + cmd.tni * (s - 1)) / (1 + cmd.tpi * (s - 1))
    cmd.model.zoom(a, 0.0, 0.0)
    val k = s * (a * cmd.tpi - cmd.tni)
    cmd.model.movePoints(cmd.fitCx * k, cmd.fitCy * k, 0.0, cmd.model.points)
    if (cmd.tni == 1.0) {
        val roundedA = Math.round(a * 100) / 100.0
        cmd.commandArea?.addLine(
            "// Fit: zoom $roundedA 0 0 move ${Math.round(cmd.fitCx)} ${Math.round(cmd.fitCy)} 0",
        )
    }
}

// Order faces front-to-back: the first face is set nearest the viewer, each following one
// progressively behind it.
private fun orderCmd(cmd: Command) {
    val faces = cmd.tokens("f").map { it as Face }
    faces.forEachIndexed { i, face ->
        val rank = faces.size - 1 - i
        val nz = Model.normal(face)[2]
        face.offset = if (rank == 0) 0.0 else sign(nz) * rank
    }
}

private fun helpCmd(cmd: Command) {
    // Group names registered together (on('by by3d', fn)) as aliases of the same command, so
    // each line lists synonyms together.
    val byFn = LinkedHashMap<(Command) -> Unit, MutableList<String>>()
    for ((name, fn) in COMMANDS) {
        byFn.getOrPut(fn) { mutableListOf() }.add(name)
    }
    val groups = byFn.values.toList()
    cmd.commandArea?.addLine("Commandes (${groups.size}) :\n${groups.joinToString("\n") { it.joinToString("/") }}")
}

private val COMMANDS: MutableMap<String, (Command) -> Unit> = LinkedHashMap()

private fun on(names: String, command: (Command) -> Unit) {
    for (name in names.split(Regex("\\s+"))) {
        COMMANDS[name] = command
    }
}

private fun registerCommands() {
    on("d define", ::define)
    on("pause") { cmd -> cmd.model.state = State.PAUSE }
    // Breakpoint: discard everything still queued after this token.
    on("stop") { cmd -> while (cmd.tokenTodo.size > cmd.iToken) cmd.tokenTodo.removeAt(cmd.tokenTodo.size - 1) }

    on("by by3d") { cmd -> take(cmd, "p", 2, "by3d needs 2 points") { l -> cmd.model.splitBy3d(l[0] as Point, l[1] as Point) } }
    on("by2d") { cmd -> take(cmd, "p", 2, "by2d needs 2 points") { l -> cmd.model.splitBy2d(l[0] as Point, l[1] as Point) } }
    on("c3d across3d") { cmd -> take(cmd, "p", 2, "c3d needs 2 points") { l -> cmd.model.splitCross3d(l[0] as Point, l[1] as Point) } }
    on("c2d across2d") { cmd -> take(cmd, "p", 2, "c2d needs 2 points") { l -> cmd.model.splitCross2d(l[0] as Point, l[1] as Point) } }
    on("p2d perpendicular2d") { cmd -> takeOne(cmd, listOf("s", "p"), "p2d needs 1 segment and 1 point") { l -> cmd.model.splitPerpendicular2d(l[0] as Segment, l[1] as Point) } }
    on("p3d perpendicular3d") { cmd -> takeOne(cmd, listOf("s", "p"), "p3d needs 1 segment and 1 point") { l -> cmd.model.splitPerpendicular3d(l[0] as Segment, l[1] as Point) } }
    on("parallel2d") { cmd -> takeOne(cmd, listOf("s", "p"), "parallel2d needs 1 segment and 1 point") { l -> cmd.model.splitParallel2d(l[0] as Segment, l[1] as Point) } }
    on("parallel3d") { cmd -> takeOne(cmd, listOf("s", "p"), "parallel3d needs 1 segment and 1 point") { l -> cmd.model.splitParallel3d(l[0] as Segment, l[1] as Point) } }
    on("bisector2d b2d") { cmd -> take(cmd, "s", 2, "bisector2d needs 2 segments") { l -> cmd.model.bisector2d(l[0] as Segment, l[1] as Segment) } }
    on("bisector3d b3d") { cmd ->
        take(cmd, "s", 2, "bisector3d needs 2 segments") { l ->
            val s1 = l[0] as Segment; val s2 = l[1] as Segment
            cmd.model.bisector3d(s1.p1, s1.p2, s2.p1, s2.p2)
        }
    }
    on("bisector2dPoints") { cmd -> take(cmd, "p", 3, "bisector2dPoints needs 3 points") { l -> cmd.model.bisector2dPoints(l[0] as Point, l[1] as Point, l[2] as Point) } }
    on("bisector3dPoints") { cmd ->
        take(cmd, "p", 3, "bisector3dPoints needs 3 points") { l ->
            cmd.model.bisector3dPoints((l[0] as Point).toVector3(), (l[1] as Point).toVector3(), (l[2] as Point).toVector3())
        }
    }
    on("split splitSegment2d", ::splitSegmentCmd)

    // Reverse Inside Fold: inverts a crease by pushing its tip between the two panels that
    // meet at the center point (e.g. reverseInside s0 p1).
    on("reverseInside") { cmd ->
        val s = cmd.token("s") as? Segment
        val p = cmd.token("p") as? Point
        if (s != null && p != null) cmd.model.reverseFold(s, p, cmd.tni)
    }

    on("r rotate", ::rotateCmd)
    on("m move", ::moveCmd)
    on("mop moveOnPoint") { cmd -> val pts = cmd.tokens("p").map { it as Point }; if (pts.isNotEmpty()) cmd.model.moveOnPoint(pts[0], pts) }
    on("mos moveOnSegment") { cmd -> val s = cmd.token("s") as? Segment; if (s != null) cmd.model.moveOnSegment(s, cmd.tokens("p").map { it as Point }) }
    on("a adjust") { cmd ->
        val pts = cmd.tokens("p").map { it as Point }
        cmd.model.adjustList(pts.ifEmpty { cmd.model.points })
    }
    on("check") { cmd ->
        cmd.model.points.forEach { it.select = false }
        cmd.model.segments.forEach { it.select = false }
        cmd.model.checkSegments()
    }
    on("o offset") { cmd -> cmd.model.offset((cmd.next()?.toDoubleOrNull() ?: 0.0) / 10, cmd.tokens("f").map { it as Face }) }
    on("order", ::orderCmd)
    on("mountain mv") { cmd -> cmd.tokens("s").forEach { (it as Segment).assignment = "M" } }
    on("valley") { cmd -> cmd.tokens("s").forEach { (it as Segment).assignment = "V" } }

    on("tx", turnCmd("x"))
    on("ty", turnCmd("y"))
    on("tz", turnCmd("z"))
    on("z zoom", ::zoomCmd)
    on("fit", ::fitCmd)

    on("selectPoints sp") { cmd -> select(cmd, "p", cmd.model.points) }
    on("selectSegments ss") { cmd -> select(cmd, "s", cmd.model.segments) }
    on("selectFaces sf") { cmd -> select(cmd, "f", cmd.model.faces) }

    on("read") { cmd ->
        val token = cmd.peek()
        val filename = if (token != null && token != "\n" && COMMANDS[token] == null) cmd.next() else null
        cmd.readWrite.readFileAsText(filename) { text -> if (text != null) cmd.readWrite.loadText(cmd, text) }
    }
    on("write instructions") { cmd ->
        val token = cmd.peek()
        val filename = if (token != null && token != "\n" && COMMANDS[token] == null) cmd.next() else null
        cmd.readWrite.writeFile(cmd.instructions.joinToString("\n"), filename)
    }
    on("writeSvg svg") { cmd ->
        val token = cmd.peek()
        val filename = if (token != null && token != "\n" && COMMANDS[token] == null) cmd.next() else null
        cmd.view3d?.updateCanvasCoords()
        cmd.readWrite.writeSVG(cmd, filename)
    }
    on("writeFold fold") { cmd ->
        val token = cmd.peek()
        val filename = if (token != null && token != "\n" && COMMANDS[token] == null) cmd.next() else null
        cmd.readWrite.writeFold(cmd.model, filename)
    }
    on("writeDiagrams diagrams") { cmd ->
        val token = cmd.peek()
        val filename = if (token != null && token != "\n" && COMMANDS[token] == null) cmd.next() else null
        cmd.readWrite.writeDiagrams(replaySteps(cmd.instructions), filename)
    }

    on("labels") { cmd -> cmd.model.labels = !cmd.model.labels }
    on("textures") { cmd -> cmd.model.textures = !cmd.model.textures }
    on("lines") { cmd -> cmd.model.lines = !cmd.model.lines }
    on("snap") { cmd -> cmd.model.snap = !cmd.model.snap }

    on("il") { cmd -> cmd.interpolator = Interpolator.LinearInterpolator }
    on("ib") { cmd -> cmd.interpolator = Interpolator.BounceInterpolator }
    on("io") { cmd -> cmd.interpolator = Interpolator.OvershootInterpolator }
    on("ia") { cmd -> cmd.interpolator = Interpolator.AnticipateInterpolator }
    on("iao") { cmd -> cmd.interpolator = Interpolator.AnticipateOvershootInterpolator }
    on("iad") { cmd -> cmd.interpolator = Interpolator.AccelerateDecelerateInterpolator }
    on("iso") { cmd -> cmd.interpolator = Interpolator.SpringOvershootInterpolator }
    on("isb") { cmd -> cmd.interpolator = Interpolator.SpringBounceInterpolator }
    on("igb") { cmd -> cmd.interpolator = Interpolator.GravityBounceInterpolator }

    on("? help", ::helpCmd)
}

private val commandsRegistered: Boolean = run { registerCommands(); true }

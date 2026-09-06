package rk.or.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private fun Command.runToCompletion(maxSteps: Int = 100000) {
    var i = 0
    while (anim() && i++ < maxSteps) { /* drain */ }
}

class CommandTest {
    @Test
    fun tokenizeStripsCommentsAndBlankLines() {
        val cmd = Command(Model())
        val tokens = cmd.tokenize(
            """
            d 100 100 // a comment
            <!-- another comment -->

            by2d p0 p1
            """.trimIndent(),
        )
        assertTrue(tokens.contains("by2d"))
        assertTrue(tokens.none { it.contains("comment") })
    }

    @Test
    fun defineInitializesModel() {
        val cmd = Command(Model())
        cmd.command("d 100 100")
        cmd.runToCompletion()
        assertEquals(4, cmd.model.points.size)
        assertEquals(1, cmd.model.faces.size)
    }

    @Test
    fun by2dSplitsTheSquare() {
        val cmd = Command(Model())
        cmd.command("d 100 100")
        cmd.command("by2d p0 p2")
        cmd.runToCompletion()
        assertEquals(2, cmd.model.faces.size)
    }

    @Test
    fun undoRestoresThePreviousState() {
        val cmd = Command(Model())
        cmd.command("d 100 100")
        cmd.command("by2d p0 p2")
        cmd.runToCompletion()
        assertEquals(2, cmd.model.faces.size)

        cmd.command("undo")
        cmd.runToCompletion()
        assertEquals(1, cmd.model.faces.size)
    }

    @Test
    fun animatedTurnRunsToCompletionAndSettlesAtTargetAngle() {
        val cmd = Command(Model())
        cmd.command("d 100 100")
        cmd.command("t 10 ty 90")
        cmd.runToCompletion()
        // After a full ty 90 turn, state settles back to RUN
        assertEquals(State.RUN, cmd.model.state)
    }

    @Test
    fun stopCommandTruncatesQueuedTokens() {
        // 'stop' truncates tokenTodo back to the current execution cursor (iToken), so it only
        // discards what's queued but not yet drained -- it must run *between* draining passes,
        // like a render loop would between two separate user commands.
        val cmd = Command(Model())
        cmd.command("d 100 100"); cmd.runToCompletion()
        cmd.command("by2d p0 p2"); cmd.runToCompletion()
        assertEquals(2, cmd.model.faces.size)

        cmd.command("by2d p1 p3") // queued but not yet drained
        cmd.command("stop") // discards it before the loop gets to it
        cmd.runToCompletion()
        assertEquals(2, cmd.model.faces.size)
    }

    @Test
    fun mountainAndValleyAssignCreaseType() {
        val cmd = Command(Model())
        cmd.command("d 100 100")
        cmd.command("mountain s0")
        cmd.command("valley s1")
        cmd.runToCompletion()
        assertEquals("M", cmd.model.segments[0].assignment)
        assertEquals("V", cmd.model.segments[1].assignment)
    }

    @Test
    fun replayStepsReturnsOneModelSnapshotPerInstruction() {
        val cmd = Command(Model())
        cmd.command("d 100 100")
        cmd.command("by2d p0 p2")
        cmd.runToCompletion()
        val steps = replaySteps(cmd.instructions)
        assertEquals(cmd.instructions.size, steps.size)
        assertEquals(2, steps.last().faces.size)
    }

    @Test
    fun malformedCommandDoesNotCrash() {
        val cmd = Command(Model())
        cmd.command("d 100 100")
        cmd.command("by2d p0") // missing second point
        cmd.runToCompletion() // should not throw
        assertEquals(1, cmd.model.faces.size)
    }
}

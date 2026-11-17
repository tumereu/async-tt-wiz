package org.asyncttwiz.android

import org.asyncttwiz.protocol.PlayZone
import org.asyncttwiz.protocol.StackEffect
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * Stack animation (owner spec 2026-11-14; HIS-112, HIS-132, HIS-141, STK-058, HIS-230, TBL-125): a board's steps stop where a play shown went on the
 * Stack while it shows (nothing after it, its resolution, plays under it), and while an entry's arrows play a newer board
 * waits (never a take-back).
 */
class StackMotionTest {
    private val f = EngineGameFixture()

    private fun transition(serial: Long, steps: List<TableBoard>, undone: EngineGame.Undone? = null) =
        EngineGame.Transition(serial, "cursor-$serial", jump = false, changes = emptyList(), shuffled = emptySet(),
            forward = emptyList(), backward = emptyList(), steps = steps, undone = undone)

    /** [board] with [entry] on its Stack (top). */
    private fun withEntry(board: TableBoard, entry: String): TableBoard {
        val stack = board.board.containers.single { it.zone == PlayZone.STACK }
        return board.copy(board = board.board.copy(
            containers = board.board.containers.map { if (it.id == stack.id) it.copy(cards = listOf(entry) + it.cards) else it },
            stackEffects = board.board.stackEffects + StackEffect(entry, "source-card")))
    }

    // HIS-112, HIS-132: the steps stop at the first step with the play's entry on the Stack while it shows, and go on once it landed
    @Test fun theStepsStopWhereAPlayShownWentOnTheStack() {
        val game = EngineGame(f.game.id, f.a, f.base, f.snapshot(), worker = EngineWorker.Direct)
        val live = game.shown!!.board
        val cast = withEntry(live, "entry:spark")
        val queue = EngineMotionQueue()
        queue.board(1, null, live)
        val steps = transition(1, listOf(live.copy(nodeId = "before"), cast, live.copy(nodeId = "resolved")))
        queue.stopEntries = setOf("entry:spark")
        assertEquals("before", queue.board(2, steps, live).nodeId)
        assertTrue(!queue.reachedEntries(), "not there yet")
        queue.settled()
        assertSame(cast, queue.board(2, steps, live), "on to the cast")
        queue.settled()
        assertTrue(queue.reachedEntries(), "stopped at the cast while the play shows")
        queue.settled()
        assertSame(cast, queue.board(2, steps, live), "still there")
        // Landed: the play's end publishes the board again, the steps go on to its resolution
        queue.stopEntries = emptySet()
        assertSame(cast, queue.board(3, steps, live))
        queue.settled()
        assertEquals("resolved", queue.board(3, steps, live).nodeId)
    }

    // STK-058, HIS-230, TBL-125: while the top entry's arrows play a newer board waits; a take-back never does
    @Test fun aNewerBoardWaitsForTheArrowsButATakeBackDoesNot() {
        val game = EngineGame(f.game.id, f.a, f.base, f.snapshot(), worker = EngineWorker.Direct)
        val live = game.shown!!.board
        val cast = withEntry(live, "entry:spark")
        val queue = EngineMotionQueue()
        assertSame(cast, queue.board(1, null, cast))
        val resolved = live.copy(nodeId = "resolved")
        assertSame(cast, queue.board(2, transition(2, emptyList()), resolved, hold = true), "held while the arrows play")
        assertSame(resolved, queue.board(2, transition(2, emptyList()), resolved, hold = false), "then the newer board")
        val back = live.copy(nodeId = "back")
        assertSame(back, queue.board(3, transition(3, emptyList(), EngineGame.Undone("bob", null, null)), back, hold = true),
            "a take-back at once")
    }

    // HIS-112, HIS-132: the board drawn while a play shows has its entry off the Stack (none of it in the Stack or its count), the
    // same board object when it isn't there
    @Test fun theEntryOfAPlayNotLandedIsOffTheBoard() {
        val game = EngineGame(f.game.id, f.a, f.base, f.snapshot(), worker = EngineWorker.Direct)
        val live = game.shown!!.board
        val cast = withEntry(live, "entry:spark")
        val hidden = cast.withoutEntries(setOf("entry:spark"))
        assertTrue("entry:spark" !in hidden.board.containers.single { it.zone == PlayZone.STACK }.cards)
        assertTrue(hidden.board.stackEffects.none { it.id == "entry:spark" })
        assertTrue(hidden.board.cards.size == cast.board.cards.size, "an ability's source card stays where it is")
        assertSame(live, live.withoutEntries(setOf("entry:spark")))
        assertSame(cast, cast.withoutEntries(emptySet()))
    }
}

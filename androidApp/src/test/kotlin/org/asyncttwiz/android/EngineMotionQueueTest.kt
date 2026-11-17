package org.asyncttwiz.android

import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.snapshots.Snapshot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.yield
import org.asyncttwiz.protocol.game.GameDevice
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Problem report QVERHX (owner, 2026-11-09): the defender's phone showed the other player's attack with its combat hold
 * (HIS-113) and, while the attack's steps still played, the server's next board came (the local-head confirmation of
 * that very attack). A newer board ends the steps at once (TBL-040), but what waited on them outside composition, the
 * combat hold's wait for the steps to reach the attackers or end, never heard of it: the hold never ended, and playback
 * stayed held behind it ("Your move" with no main button) until the game was opened again.
 */
class EngineMotionQueueTest {
    private val f = EngineGameFixture()

    private fun transition(serial: Long, steps: List<TableBoard>) =
        EngineGame.Transition(serial, "cursor-$serial", jump = false, changes = emptyList(), shuffled = emptySet(),
            forward = emptyList(), backward = emptyList(), steps = steps)

    // HIS-113, TBL-040, problem report QVERHX: a newer board that ends the steps is heard by a wait on them made outside
    // composition (as the combat hold's snapshot wait is)
    @Test fun aNewerBoardEndingTheStepsEndsAWaitOnThem() = runBlocking {
        val game = EngineGame(f.game.id, f.a, f.base, f.snapshot(), worker = EngineWorker.Direct)
        val live = game.shown!!.board
        val queue = EngineMotionQueue()
        queue.board(1, null, live)
        // The attack's board, with steps in between
        val attack = transition(1, listOf(live, live, live))
        queue.board(2, attack, live)
        assertTrue(queue.playing, "the attack's steps play")
        val ended = launch(Dispatchers.Unconfined) { snapshotFlow { !queue.playing || queue.stoppedAt(GameDevice.CombatMoment.Attackers) }.first { it } }
        yield()
        assertTrue(ended.isActive, "waiting on the steps")
        // The next board (the same transition: only the local heads changed) ends the steps at once
        queue.board(3, attack, live)
        Snapshot.sendApplyNotifications()
        val heard = withTimeoutOrNull(2_000) { ended.join(); true }
        assertEquals(false, queue.playing, "the steps ended")
        assertEquals(true, heard, "the wait heard that the steps ended")
    }

    // HIS-113, HIS-147 (owner playtest 2026-11-09): while the damage holds, the steps stop at the board right after the damage is
    // dealt, the creatures it destroys still on it; a board published again on the way or meanwhile keeps the steps, and the
    // hold's end publishes the board again and the steps go on from there to the end (they die), instead of ending at once
    @Test fun theDamageHoldStopsTheStepsAtTheDamageDealtAndTheyGoOnAfterIt() {
        val game = EngineGame(f.game.id, f.a, f.base, f.snapshot(), worker = EngineWorker.Direct)
        val live = game.shown!!.board
        val dealt = live.copy(damageDealt = true)
        val queue = EngineMotionQueue()
        queue.board(1, null, live)
        val combat = transition(1, listOf(live, dealt, live))
        queue.stopAt = GameDevice.CombatMoment.Damage
        assertEquals(live, queue.board(2, combat, live))
        // A board published again on the way there (the server's confirmation) keeps the steps going
        assertEquals(live, queue.board(3, combat, live), "the steps still going")
        queue.settled()
        assertEquals(dealt, queue.board(3, combat, live), "on to the damage dealt")
        assertTrue(!queue.stoppedAt(GameDevice.CombatMoment.Damage), "not stopped before it settles")
        queue.settled()
        assertTrue(queue.stoppedAt(GameDevice.CombatMoment.Damage), "stopped at the damage dealt")
        queue.settled()
        assertEquals(dealt, queue.board(3, combat, live), "still there while the damage holds")
        // A board published again while it holds keeps it there
        assertEquals(dealt, queue.board(4, combat, live))
        queue.settled()
        assertTrue(queue.stoppedAt(GameDevice.CombatMoment.Damage))
        // The hold's end: published again, nothing holding
        queue.stopAt = null
        assertEquals(dealt, queue.board(5, combat, live), "goes on from the damage dealt")
        assertTrue(!queue.stoppedAt(GameDevice.CombatMoment.Damage))
        queue.settled()
        assertTrue(queue.playing, "the step after it")
        queue.settled()
        assertTrue(!queue.playing, "on to the board itself")
    }

    /** [board] with [entries] on its Stack, top first. */
    private fun stacked(board: TableBoard, vararg entries: String) = board.copy(board = board.board.copy(containers =
        board.board.containers.map { if (it.zone == org.asyncttwiz.protocol.PlayZone.STACK) it.copy(cards = entries.toList()) else it }))

    // HIS-112, HIS-132, TBL-125 (owner playtest 2026-11-15, report VSVLSK: the plays landed on a Stack their entries had already left,
    // with no arrows): the same board published again while its steps play toward a play shown not landed yet (its status,
    // the other player's presence) keeps the steps, instead of jumping to the end of the event under the play
    @Test fun aBoardPublishedAgainKeepsTheStepsTowardAPlayShown() {
        val game = EngineGame(f.game.id, f.a, f.base, f.snapshot(), worker = EngineWorker.Direct)
        val live = game.shown!!.board
        val cast = stacked(live, "entry:x")
        val queue = EngineMotionQueue()
        queue.board(1, null, live)
        val event = transition(1, listOf(live, cast, live))
        val plays = setOf("entry:x")
        queue.stopEntries = plays
        assertEquals(live, queue.board(2, event, live, plays = plays))
        assertTrue(queue.playing)
        // Published again on the way to the play: the steps go on
        queue.board(3, event, live, plays = plays)
        assertTrue(queue.playing, "the steps still going")
        queue.settled()
        assertEquals(cast, queue.board(3, event, live, plays = plays), "on to the play's entry on the Stack")
        queue.settled()
        assertTrue(queue.reachedEntries(), "stopped where the play shows")
        assertEquals(cast, queue.board(4, event, live, plays = plays), "still there while it shows")
    }

    // HIS-112, HIS-132, HIS-137, TBL-125 (owner playtest 2026-11-15, report VSVLSK: the caster's own trigger shown over a Stack its event had
    // already resolved): the board drawn before has the entry of a play shown not landed yet, and the event's first step
    // already takes it off the Stack: that board stays drawn until the play has landed, then the steps play
    @Test fun aFirstStepTakingAPlaysEntryOffTheStackWaitsForThePlay() {
        val game = EngineGame(f.game.id, f.a, f.base, f.snapshot(), worker = EngineWorker.Direct)
        val live = game.shown!!.board
        val early = stacked(live, "entry:t", "entry:s")
        val resolved = stacked(live, "entry:s")
        val queue = EngineMotionQueue()
        // The own action's early board, its trigger on the Stack (kept off the table's board until its play lands)
        queue.board(1, null, early)
        val plays = setOf("entry:t")
        queue.stopEntries = plays
        val whole = transition(1, listOf(resolved, live))
        assertEquals(early, queue.board(2, whole, live, plays = plays), "the board before the steps, the trigger still there")
        queue.settled()
        assertEquals(early, queue.board(2, whole, live, plays = plays), "held while the play shows")
        assertTrue(queue.reachedEntries(), "the play may show")
        // It landed
        queue.stopEntries = emptySet()
        assertEquals(resolved, queue.board(2, whole, live), "the first step plays: the trigger resolves")
        queue.settled()
        assertEquals(live, queue.board(2, whole, live), "then the next")
    }
}

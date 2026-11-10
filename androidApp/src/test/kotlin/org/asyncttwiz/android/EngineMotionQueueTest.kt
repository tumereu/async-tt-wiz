package org.asyncttwiz.android

import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.snapshots.Snapshot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.yield
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
        val ended = launch(Dispatchers.Unconfined) { snapshotFlow { !queue.playing || queue.atAttackers }.first { it } }
        yield()
        assertTrue(ended.isActive, "waiting on the steps")
        // The next board (the same transition: only the local heads changed) ends the steps at once
        queue.board(3, attack, live)
        Snapshot.sendApplyNotifications()
        val heard = withTimeoutOrNull(2_000) { ended.join(); true }
        assertEquals(false, queue.playing, "the steps ended")
        assertEquals(true, heard, "the wait heard that the steps ended")
    }
}

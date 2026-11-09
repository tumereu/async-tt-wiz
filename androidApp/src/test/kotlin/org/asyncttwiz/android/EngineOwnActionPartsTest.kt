package org.asyncttwiz.android

import arcanum.engine.input.Input
import arcanum.engine.state.PlayerId
import org.asyncttwiz.android.OwnActionTiming.Companion.AFTER
import org.asyncttwiz.android.OwnActionTiming.Companion.BOARD
import org.asyncttwiz.android.OwnActionTiming.Companion.COMPUTED
import org.asyncttwiz.android.OwnActionTiming.Companion.ENGINE
import org.asyncttwiz.android.OwnActionTiming.Companion.FRAME
import org.asyncttwiz.android.OwnActionTiming.Companion.INFO
import org.asyncttwiz.android.OwnActionTiming.Companion.LABELS
import org.asyncttwiz.android.OwnActionTiming.Companion.PLAYS
import org.asyncttwiz.android.OwnActionTiming.Companion.TOTAL
import org.asyncttwiz.android.OwnActionTiming.Companion.WAIT
import org.asyncttwiz.protocol.game.GameDevice
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * SYS-025, SYS-030 (engine-responsiveness R1): the probe of an own action splits its time into its parts and gives each
 * to the part it belongs to: up to the board the wait for the engine thread, the engine's work, the information rule and
 * building the board; then the next frame, the computed board and hand plays, and the engine's work after the board.
 * A fake clock that moves one millisecond on every read makes every part that was timed non-zero; nothing compares
 * elapsed times (no timing tests).
 */
class EngineOwnActionPartsTest {
    private val f = EngineGameFixture()
    private var now = 1_000_000_000L
    private val clock = { now += 1_000_000; now }

    /** The engine thread inline; what it posts to the main thread and the next frames wait for [drain] and [frames]. With
     * [early] an own action's board shows at its performed point, as on the app's engine thread. */
    private class QueuedWorker(private val early: Boolean) : EngineWorker {
        private val mains = ArrayDeque<() -> Unit>()
        private val nextFrames = ArrayDeque<() -> Unit>()
        override fun run(work: () -> Unit) = work()
        override fun <T> call(work: () -> T): T = work()
        override fun main(work: () -> Unit) { mains.addLast(work) }
        override fun frame(work: () -> Unit) { nextFrames.addLast(work) }
        override fun early(work: (release: (() -> Unit)?) -> Unit) { if (early) work {} else super.early(work) }
        fun drain() { while (mains.isNotEmpty()) mains.removeFirst()() }
        fun frames() { drain(); while (nextFrames.isNotEmpty()) nextFrames.removeFirst()(); drain() }
    }

    private fun game(worker: QueuedWorker): EngineGame {
        val game = EngineGame(f.game.id, f.a, f.base, f.snapshot(otherKept = true), worker = worker, clock = clock)
        worker.frames()
        game.tick(1_000); worker.frames()
        assertIs<GameDevice.Acted.Sent>(game.keep()); worker.frames()
        game.tick(2_000); worker.frames()
        assertEquals(GameDevice.Status.YourMove, game.shown!!.status)
        // The keep's own probe gets its computed board, so the next action is the only one measured
        computedAndPlays(game)
        return game
    }

    /** The table's computed board and hand plays arrive for the board shown now. */
    private fun computedAndPlays(game: EngineGame) {
        val shown = game.shown!!.board.board
        OwnActionProbes.computed(shown); OwnActionProbes.plays(shown)
    }

    /** Plays a land from Alice's hand (one the engine offers her: the only own action made). */
    private fun playLand(game: EngineGame): GameDevice.Acted {
        val land = game.read { d -> arcanum.engine.Engine.options(d.model.engine, PlayerId(f.a)).first { it is Input.PlayLand } }
        return game.act(land)
    }

    // SYS-030: the early path (the board shown at the action's performed point) times building that board as its own part,
    // not as the engine's work; the frame, the computed board and the hand plays are filled in as they arrive, and the
    // action is reported once every part is in
    @Test fun anEarlyBoardIsTimedAsTheBoardAndTheLaterPartsAreFilledInAsTheyArrive() {
        val worker = QueuedWorker(early = true)
        val game = game(worker)
        val before = game.shown!!.board.board
        val shownParts = mutableListOf<Pair<Long, Long>>()
        game.probe = { _, _, acted, board -> shownParts += acted to board }
        val reported = mutableListOf<OwnActionTiming>()
        game.measured = { reported += it }
        assertEquals(GameDevice.Acted.Made, playLand(game))
        worker.drain()
        val (acted, board) = shownParts.single()
        assertTrue(board > 0, "the board's building is its own part")
        assertTrue(acted > 0, "the engine's work is timed")
        assertTrue(reported.isEmpty(), "the next frame and the computed board haven't arrived yet")

        worker.frames()
        assertTrue(reported.isEmpty(), "the computed board hasn't arrived yet")
        // A computed board of the board shown before the action isn't this action's
        OwnActionProbes.computed(before); OwnActionProbes.plays(before)
        assertTrue(reported.isEmpty())
        // The hand plays came already: worked out on the engine thread right after the early board, before its follow-up
        // work (smoke 2026-11-05)
        val after = game.shown!!.board.board
        OwnActionProbes.computed(after)

        val timing = reported.single()
        assertTrue(timing.early)
        val parts = timing.parts()
        assertEquals(LABELS.toSet(), parts.keys, "every part is in: ${timing.line()}")
        for (label in listOf(TOTAL, ENGINE, BOARD, FRAME, COMPUTED, PLAYS, AFTER)) assertTrue(parts.getValue(label) > 0, "$label: ${timing.line()}")
        assertEquals(parts.getValue(TOTAL), parts.getValue(WAIT) + parts.getValue(ENGINE) + parts.getValue(INFO) + parts.getValue(BOARD), 1e-6,
            "the parts up to the board add up to its time: ${timing.line()}")
        assertTrue(timing.line().startsWith("what=act path=early totalMs="), timing.line())
    }

    // SYS-030: without the early release the whole action is worked out first; its board is still timed apart from the
    // engine's work
    @Test fun aWholeActionsBoardIsTimedApartFromItsWork() {
        val worker = QueuedWorker(early = false)
        val game = game(worker)
        val reported = mutableListOf<OwnActionTiming>()
        game.measured = { reported += it }
        assertIs<GameDevice.Acted.Sent>(playLand(game))
        worker.frames()
        computedAndPlays(game)
        val timing = reported.single()
        assertFalse(timing.early)
        val parts = timing.parts()
        assertEquals(LABELS.toSet(), parts.keys, timing.line())
        assertTrue(parts.getValue(BOARD) > 0 && parts.getValue(ENGINE) > 0, timing.line())
        assertTrue(timing.line().startsWith("what=act path=whole "), timing.line())
    }

    // SYS-030: on a tentative line (the device assumed the other player's answers, not yet confirmed) the information
    // rule's work is its own part
    @Test fun theInformationRuleOnATentativeLineIsItsOwnPart() {
        val worker = QueuedWorker(early = true)
        val game = game(worker)
        assertTrue(game.read { it.tentative() }, "Bob's answers are assumed and not confirmed")
        val reported = mutableListOf<OwnActionTiming>()
        game.measured = { reported += it }
        playLand(game)
        worker.frames()
        computedAndPlays(game)
        val parts = reported.single().parts()
        assertTrue(parts.getValue(INFO) > 0, reported.single().line())
    }

    // SYS-030: an action whose computed board never arrives is reported, with the parts it has, when the next own action's
    // board is shown; its missing parts read "-"
    @Test fun anActionStillWaitingIsReportedWhenTheNextOnesBoardShows() {
        val worker = QueuedWorker(early = true)
        val game = game(worker)
        val reported = mutableListOf<OwnActionTiming>()
        game.measured = { reported += it }
        playLand(game)
        worker.frames()
        assertTrue(reported.isEmpty())
        game.pass()
        worker.drain()
        val first = reported.single()
        assertEquals("act", first.what)
        assertNull(first.parts()[COMPUTED])
        assertTrue(" computedMs=- " in first.line(), first.line())
        assertTrue(first.parts().getValue(FRAME) > 0, first.line())
        // The second action waits for its own parts
        worker.frames()
        computedAndPlays(game)
        assertEquals(listOf("act", "pass"), reported.map { it.what })
    }
}

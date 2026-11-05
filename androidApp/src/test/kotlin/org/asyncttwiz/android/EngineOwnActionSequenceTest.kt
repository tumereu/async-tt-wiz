package org.asyncttwiz.android

import arcanum.engine.Engine
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.input.Input
import arcanum.engine.state.PlayerId
import org.asyncttwiz.android.OwnActionTiming.Companion.LABELS
import org.asyncttwiz.automatum.CardPlays
import org.asyncttwiz.automatum.HandPlays
import org.asyncttwiz.protocol.game.GameDevice
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * SYS-025, SYS-030 (smoke 2026-11-05): every own action is probed once, whatever path its board takes, and the player's
 * hand plays on an own action's board are worked out right after it is published, before the action's follow-up work.
 *
 * The engine thread and the main thread are queues run in the app's order ([Threads]): the engine thread's work after an
 * action waits for its turn, the main thread runs what was posted to it, the table drawing the board shown after each
 * ([drawnForProbe], as the table's composition does), then the next frame. Counted and ordered, never timed.
 */
class EngineOwnActionSequenceTest {
    private val f = EngineGameFixture()
    private var now = 1_000_000_000L
    private val clock = { now += 1_000_000; now }

    /**
     * The app's threads as queues: [engine] the engine thread's (an action [call]ed or made [early] runs after the work
     * queued before it), [mains] the main thread's. With [early] an own action's board shows at its performed point.
     */
    private class Threads(private val early: Boolean) : EngineWorker {
        val engine = ArrayDeque<() -> Unit>()
        val mains = ArrayDeque<() -> Unit>()
        private val nextFrames = ArrayDeque<() -> Unit>()
        private var onEngine = false
        /** Work handed to the engine thread by a caller waiting for it ([call]). */
        var calls = 0

        private fun <T> onEngine(work: () -> T): T {
            if (onEngine) return work()
            runEngine()
            onEngine = true
            try { return work() } finally { onEngine = false }
        }

        override fun run(work: () -> Unit) { engine.addLast(work) }
        override fun <T> call(work: () -> T): T { if (!onEngine) calls++; return onEngine(work) }
        override fun main(work: () -> Unit) { mains.addLast(work) }
        override fun frame(work: () -> Unit) { nextFrames.addLast(work) }
        override fun early(work: (release: (() -> Unit)?) -> Unit) {
            val nested = onEngine
            onEngine { if (early && !nested) work {} else work(null) }
        }

        fun runEngine() {
            while (engine.isNotEmpty()) { val work = engine.removeFirst(); onEngine = true; try { work() } finally { onEngine = false } }
        }

        /** Everything queued runs: the engine thread's work, the main thread's with the table drawing after each, and
         * the next frames, until nothing is left. */
        fun settle(draw: () -> Unit) {
            while (engine.isNotEmpty() || mains.isNotEmpty() || nextFrames.isNotEmpty()) {
                runEngine()
                while (mains.isNotEmpty()) { mains.removeFirst()(); draw() }
                val frames = nextFrames.toList()
                nextFrames.clear()
                frames.forEach { it() }
            }
        }
    }

    /** The early path: [EngineWorker.early] releases the caller at the performed point; the whole path: it doesn't. */
    private class WholeThreads(private val threads: Threads) : EngineWorker by threads {
        override fun early(work: (release: (() -> Unit)?) -> Unit) { call { work(null) } }
    }

    /** Alice at her first main phase after her keep (Bob not live: his answers assumed), everything settled. */
    private fun ready(threads: Threads, worker: EngineWorker = threads): EngineGame {
        val made = EngineGame(f.game.id, f.a, f.base, f.snapshot(), worker = worker, clock = clock)
        val draw: () -> Unit = { made.shown?.let { drawnForProbe(it.board, f.a) } }
        threads.settle(draw)
        made.tick(live = false, now = 1_000); threads.settle(draw)
        assertIs<GameDevice.Acted.Sent>(made.keep(emptyList())); threads.settle(draw)
        made.tick(live = false, now = 2_000); threads.settle(draw)
        assertEquals(GameDevice.Status.YourMove, made.shown!!.status)
        return made
    }

    private fun option(game: EngineGame, kind: (Input) -> Boolean): Input =
        game.read { d -> Engine.options(d.model.engine, PlayerId(f.a)).first(kind) }

    /** A Storm Spark cast with its answers (its target, its mana), as the cast panel makes them. */
    private fun cast(game: EngineGame): List<Input> = game.read { d ->
        val me = PlayerId(f.a)
        val copy = d.model.engine.deepCopy()
        var input = Engine.options(copy, me).first { it is Input.Cast }
        val inputs = mutableListOf(input)
        while (true) {
            Engine.perform(copy, input).changes.forEach(copy::apply)
            val decision = copy.pending?.decision ?: break
            if (decision is Decision.Random || Engine.decider(copy, decision.player) != me) break
            input = if (decision is Decision.ManaPayment) Input.Answer(decision.id, Answer.ManaPayment(decision.suggestion))
                else Engine.options(copy, me).filterIsInstance<Input.Answer>().first { it.decision == decision.id }
            inputs += input
        }
        inputs
    }

    /** A land, a cast with a choice, a pass and End turn, each settled; every report, in order. */
    private fun mixedActions(threads: Threads, worker: EngineWorker): List<OwnActionTiming> {
        val game = ready(threads, worker)
        val draw: () -> Unit = { game.shown?.let { drawnForProbe(it.board, f.a) } }
        val reported = mutableListOf<OwnActionTiming>()
        game.measured = { reported += it }
        game.act(option(game) { it is Input.PlayLand }); threads.settle(draw)
        val spark = cast(game)
        assertTrue(spark.size > 1, "the cast has a choice: $spark")
        game.send(spark); threads.settle(draw)
        assertEquals(GameDevice.Status.YourMove, game.shown!!.status, "Alice's main phase again, the Spark resolved")
        game.pass(); threads.settle(draw)
        game.endTurn(); threads.settle(draw)
        return reported
    }

    private fun assertEachReportedOnce(reported: List<OwnActionTiming>, early: Boolean) {
        assertEquals(listOf("act", "send", "pass", "endTurn"), reported.map { it.what }, "one probe per action")
        val log = AppLog.text()
        for (timing in reported) {
            assertEquals(LABELS.toSet(), timing.parts().keys, "every part arrived: ${timing.line()}")
            // End turn never shows early (the device passes on for the player first)
            assertEquals(early && timing.what != "endTurn", timing.early, timing.line())
            // SYS-030: test builds log every own action alike, slow or not
            assertTrue("Own action ${timing.line()}" in log, "logged: ${timing.line()}")
        }
    }

    // SYS-030 (smoke 2026-11-05): on the early path (each board shown at its action's performed point) a land, a cast
    // with a choice, a pass and End turn are each reported once, with every part, and logged as "Own action what=…"
    @Test fun everyOwnActionIsProbedOnceOnTheEarlyPath() {
        val threads = Threads(early = true)
        assertEachReportedOnce(mixedActions(threads, threads), early = true)
    }

    // SYS-030: the same on the whole path (each board shown once its action is all worked out)
    @Test fun everyOwnActionIsProbedOnceOnTheWholePath() {
        val threads = Threads(early = true)
        assertEachReportedOnce(mixedActions(threads, WholeThreads(threads)), early = false)
    }

    // SYS-025 (smoke 2026-11-05): the hand plays and card menus' plays of an own action's early board are worked out on
    // the engine thread right after it is published, before the action's follow-up work (the device's own steps and the
    // board after them): the table hears of them while that board is the one shown. Each board's plays are its own.
    @Test fun anEarlyBoardsPlaysComeBeforeItsFollowUpWork() {
        val threads = Threads(early = true)
        val game = ready(threads)
        game.act(option(game) { it is Input.PlayLand })
        threads.settle { game.shown?.let { drawnForProbe(it.board, f.a) } }
        val inputs = cast(game)
        // Any probe hearing of the early board's plays
        val heard = OwnActionTiming("heard", clock, null)
        OwnActionProbes.add(heard) {}
        try {
            assertEquals(GameDevice.Acted.Made, game.send(inputs))
            assertTrue(threads.engine.isNotEmpty(), "the follow-up work waits for the engine thread")
            // The main thread: the early board first
            threads.mains.removeFirst()()
            val early = game.shown!!
            heard.boards += early.board.board
            val values = assertNotNull(early.values)
            assertNull(heard.plays)
            // Then its plays, before anything else
            threads.mains.removeFirst()()
            assertSame(early, game.shown, "still the early board")
            assertNotNull(heard.plays, "its plays arrived while it is shown")
            assertEquals(HandPlays.of(early.engine, f.a, null).byCard, assertNotNull(values.handPlays).byCard)
            assertEquals(cardPlaysOf(early.board).byCard, assertNotNull(values.cardPlays).byCard)
            // Only then the follow-up: the board after the device's own steps, with its own plays
            threads.settle { game.shown?.let { drawnForProbe(it.board, f.a) } }
            val whole = game.shown!!
            assertTrue(whole.version > early.version)
            assertEquals(HandPlays.of(whole.engine, f.a, null).byCard, assertNotNull(whole.values?.handPlays).byCard)
        } finally { OwnActionProbes.remove(heard) }
    }

    // SYS-025: a reader on another thread (the table's glow and cost chips) waits for promised plays alone, never for the
    // engine thread's queued work
    @Test fun aReaderWaitsForPromisedPlaysNotForTheEngineThread() {
        val threads = Threads(early = true)
        val game = ready(threads)
        val values = assertNotNull(game.reviewPlay(game.shown!!.tip)?.values, "a board with nothing worked out yet")
        assertNull(values.cardPlays)
        val calls = threads.calls
        values.promisePlays()
        var hand: HandPlays? = null
        var cards: CardPlays? = null
        val reader = Thread { hand = values.handPlays(); cards = values.cardPlays() }
        reader.start()
        // The reader waits for the promise (without one it would hand its work to the engine thread and end)
        while (reader.isAlive && reader.state != Thread.State.WAITING) Thread.yield()
        values.workPlays()
        reader.join()
        assertSame(values.handPlays, hand)
        assertSame(values.cardPlays, cards)
        assertEquals(calls, threads.calls, "nothing was handed to the engine thread")
    }

    /** What CardPlays works out for [play] from its own state, as the table would. */
    private fun cardPlaysOf(play: TableBoard): CardPlays {
        val state = assertNotNull(play.engine).state
        val view = Engine.view(state)
        return CardPlays.of(state, f.a, view, Engine.visible(state, PlayerId(f.a), view))
    }
}

package org.asyncttwiz.android

import arcanum.engine.Engine
import arcanum.engine.input.Input
import arcanum.engine.state.PlayerId
import org.asyncttwiz.automatum.HandPlays
import org.asyncttwiz.automatum.boundary.EngineLog
import org.asyncttwiz.automatum.boundary.EngineMessages
import org.asyncttwiz.automatum.boundary.EnginePlanner
import org.asyncttwiz.automatum.boundary.EngineRequest
import org.asyncttwiz.automatum.boundary.MessageEngineChannel
import org.asyncttwiz.protocol.game.EnginePreferences
import org.asyncttwiz.protocol.game.GameDevice
import org.asyncttwiz.protocol.game.GameModel
import org.junit.After
import org.junit.Test
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Web client step 4c (docs/specs/web-client.md §1 item 3): the engine's results come back as callbacks on both
 * platforms. Android's engine thread is waited for as before, so the callback runs before the call returns (the same
 * frame, SYS-025); a page that can't wait gets the answers later, in the order of the calls, and a second tap made on a
 * board the device has moved on from is treated exactly as on Android. In the browser a published board goes to the
 * engine's worker as the events from the board it holds, which move it to exactly that board.
 */
class EngineCallbackFormsTest {
    private val f = EngineGameFixture(creatures = true)

    @After fun noWorker() = EngineBoundary.reset()

    /** A page that can't wait for its engine side (the browser's): the page's requests, the engine's work and what it
     * posts back run only when [drain]ed, in order. Each answer follows its work at once; what the engine side asks of
     * itself runs inline, and what it queues for itself runs right after the work that queued it, before the page's next
     * request, as on Android's engine thread when the next press comes after the answer. */
    private class LaterWorker : EngineWorker {
        private val queue = ArrayDeque<() -> Unit>()
        private val internal = ArrayDeque<() -> Unit>()
        private var onEngine = false
        private fun engine(work: () -> Unit) {
            onEngine = true
            try { work(); while (internal.isNotEmpty()) internal.removeFirst()() } finally { onEngine = false }
        }
        override fun run(work: () -> Unit) { if (onEngine) internal.addLast(work) else queue.addLast { engine(work) } }
        override fun <T> call(work: () -> T, done: (T) -> Unit) {
            if (onEngine) return done(work())
            queue.addLast { var result: Result<T>? = null; engine { result = runCatching(work) }; done(result!!.getOrThrow()) }
        }
        override fun main(work: () -> Unit) { queue.addLast(work) }
        override fun early(work: (release: (() -> Unit)?) -> Unit, released: () -> Unit) {
            if (onEngine) { work(null); released(); return }
            queue.addLast { engine { work(null) }; released() }
        }
        val idle: Boolean get() = queue.isEmpty()
        fun drain() { while (queue.isNotEmpty()) queue.removeFirst()() }
    }

    /** Android's order on one thread: the engine's work at once (its queue runs before the next call), what it posts to
     * the main thread (the boards) held until [release]: a second tap is made on the board still shown. */
    private class HeldMain : EngineWorker {
        private val waiting = ArrayDeque<() -> Unit>()
        var holding = false
        override fun run(work: () -> Unit) = work()
        override fun <T> call(work: () -> T, done: (T) -> Unit) = done(work())
        override fun main(work: () -> Unit) { if (holding) waiting.addLast(work) else work() }
        fun release() { holding = false; while (waiting.isNotEmpty()) waiting.removeFirst()() }
    }

    /** What an action answered, without its event's random ids. */
    private fun said(acted: GameDevice.Acted): String = when (acted) {
        is GameDevice.Acted.Refused -> "Refused(${acted.reason})"
        else -> acted::class.simpleName.orEmpty()
    }

    private fun options(game: EngineGame): List<Input> =
        game.device.model.engine.let { Engine.options(it, PlayerId(f.a), EnginePreferences.of(it)) }

    // ---- Android's engine thread: waited for, the callback in the same frame

    @Test fun androidsCallAnswersOnTheCallersThreadBeforeReturning() {
        var workedOn: Thread? = null
        var doneOn: Thread? = null
        var returned = false
        BackgroundEngineWorker.call({ workedOn = Thread.currentThread(); 42 }) { answer ->
            assertEquals(42, answer)
            assertTrue(!returned, "before the call returned")
            doneOn = Thread.currentThread()
        }
        returned = true
        assertSame(Thread.currentThread(), doneOn)
        assertEquals("atw-engine", workedOn?.name)
        // A failure is thrown to the caller, the callback never runs
        assertFailsWith<IllegalStateException> { BackgroundEngineWorker.call<Int>({ error("engine failed") }) { fail("no answer") } }
    }

    // SYS-025: an own action's caller waits only until its release; the callback runs then, before the call returns, while
    // the engine thread finishes the action's event
    @Test fun androidsEarlyReleasesTheCallerAtThePerformedPoint() {
        val order = Collections.synchronizedList(mutableListOf<String>())
        val rest = CountDownLatch(1)
        BackgroundEngineWorker.early({ release ->
            order += "made"
            assertNotNull(release, "the main thread waits for a release")()
            rest.await(5, TimeUnit.SECONDS)
            order += "rest of its event"
        }) { order += "released" }
        order += "returned"
        rest.countDown()
        BackgroundEngineWorker.call({}) {}
        assertEquals(listOf("made", "released", "returned", "rest of its event"), order)
    }

    @Test fun awaitWorksOnlyWhereTheAnswerComesBeforeReturning() {
        assertEquals(3, EngineWorker.Direct.await { 3 })
        assertEquals(4, BackgroundEngineWorker.await { 4 })
        val later = LaterWorker()
        assertFailsWith<IllegalStateException> { later.await { 3 } }
    }

    // ---- A page that can't wait

    /** Alice at her first main phase after her keep, on [worker] ([settle]: everything queued runs). */
    private fun ready(worker: EngineWorker, settle: () -> Unit): EngineGame {
        val game = EngineGame(f.game.id, f.a, f.base, f.snapshot(otherKept = true), worker = worker)
        settle()
        game.tick(1_000); settle()
        var kept: GameDevice.Acted? = null
        game.keep { kept = it }
        settle()
        assertIs<GameDevice.Acted.Sent>(kept)
        game.tick(2_000); settle()
        assertEquals(GameDevice.Status.YourMove, game.shown!!.status)
        return game
    }

    // SYS-011, SYS-012 with step 4c: the answers come later, in the order the actions were made; the second tap, made on
    // the board shown before the first action's board arrived, is answered as Android answers that tap
    @Test fun answersComeLaterInOrderAndASecondTapOnTheOldBoardIsTreatedAsOnAndroid() {
        val worker = LaterWorker()
        val game = ready(worker, worker::drain)
        val land = options(game).first { it is Input.PlayLand }
        val answers = mutableListOf<String>()
        game.act(land) { answers += "land: ${said(it)}" }
        game.pass { answers += "pass: ${said(it)}" }
        assertTrue(answers.isEmpty(), "nothing answered before the engine side ran")
        worker.drain()

        // Android: the land made, its board not on screen yet, then the pass tapped on the board still shown
        val held = HeldMain()
        val android = ready(held) {}
        held.holding = true
        val expected = listOf("land: ${said(android.act(options(android).first { it is Input.PlayLand }))}", "pass: ${said(android.pass())}")
        held.release()
        assertEquals(expected, answers)
        assertEquals(android.shown!!.status, game.shown!!.status)
        assertEquals(android.device.session.pending.size, game.device.session.pending.size, "the same line")
        assertEquals(android.shown!!.version, game.shown!!.version, "the same boards published")
        assertTrue(worker.idle)
    }

    // The routes' press on a page that can't wait: answered as sent now, its answer handled when it comes
    @Test fun aRoutePressIsAskedNowAndHandledWhenItsAnswerComes() {
        val worker = LaterWorker()
        val game = ready(worker, worker::drain)
        var pumped = 0
        val routes = EngineTableRoutes(f.a, { game }, { pumped++ }, { game.shown?.board })
        assertTrue(routes.pass(), "asked")
        assertEquals(0, pumped, "nothing sent before the answer")
        worker.drain()
        assertEquals(1, pumped, "the answer sends what the action made")
    }

    // ---- Boards to the worker as events (BoardTransfer.since)

    // In the browser each published board goes to the engine's worker as the events from the board it holds (a snapshot
    // only first): those events move the worker's copy to exactly the board published, so its plays are the in-process ones
    @Test fun publishedBoardsGoToTheWorkerAsEventsAndMoveItToExactlyThatBoard() {
        val planner = EnginePlanner()
        val posted = ArrayDeque<String>()
        val logged = mutableListOf<String>()
        val channel = MessageEngineChannel({ posted += it }, EngineLog { _, text -> logged += text })
        val sent = mutableListOf<EngineRequest>()
        fun deliver() {
            while (posted.isNotEmpty()) {
                val request = EngineMessages.decodeRequest(posted.removeFirst())
                sent += request
                channel.received(EngineMessages.encode(planner.handle(request)))
            }
        }
        EngineBoundary.useRemote(channel)
        val shown = mutableListOf<EngineGame.Shown>()
        var made: EngineGame? = null
        val game = EngineGame(f.game.id, f.a, f.base, f.snapshot(otherKept = true), onShown = { made?.shown?.let { shown += it } })
        made = game
        deliver()
        game.tick(1_000); deliver()
        assertIs<GameDevice.Acted.Sent>(game.keep()); deliver()
        game.tick(2_000); deliver()
        game.act(options(game).first { it is Input.PlayLand }); deliver()
        game.act(options(game).first { it is Input.Cast }); deliver()
        game.pass(); deliver()
        game.endTurn(); deliver()
        game.tick(3_000); deliver()

        val loads = sent.count { it is EngineRequest.Load }
        val advances = sent.filterIsInstance<EngineRequest.Advance>()
        assertTrue(advances.size >= 4, "${advances.size} boards sent as events, $loads as snapshots")
        assertTrue(loads <= 2, "$loads snapshots: ${logged.filter { "load" in it }}")
        assertTrue(logged.none { "failed" in it || "missing" in it }, logged.toString())
        var checked = 0
        for (s in shown.distinctBy { it.values?.number }) {
            val values = s.values ?: continue
            val hand = values.handPlays ?: continue
            assertEquals(HandPlays.of(s.engine, f.a, null).byCard, hand.byCard, "the worker's hand plays on ${s.board.nodeId}")
            checked++
        }
        assertTrue(checked >= 4, "$checked boards' plays from the worker")
        assertEquals(0, channel.requests.awaited)
    }

    @Suppress("unused") private fun GameModel.unused() = Unit
}

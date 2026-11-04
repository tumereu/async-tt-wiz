package org.asyncttwiz.android

import arcanum.api.TurnStep
import arcanum.engine.input.Input
import org.asyncttwiz.protocol.EngineSnapshot
import org.asyncttwiz.protocol.GameAction
import org.asyncttwiz.protocol.PlayZone
import org.asyncttwiz.protocol.PlayerStatus
import org.asyncttwiz.protocol.game.GameDevice
import org.asyncttwiz.protocol.game.GameEvent
import org.asyncttwiz.protocol.game.GameModel
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * I-D56 step C: the app's engine game ([EngineGame]) on the device core: the table's board from the engine's state, the
 * player's own inputs sent as submissions and shown at once, assumptions while the other player is away (the
 * indicator), the server's echo confirming them, and the other player's catch-up screen, playback and decision stop.
 */
class EngineGameTest {
    private val f = EngineGameFixture()

    private fun alice() = EngineGame(f.game.id, f.a, f.base, f.snapshot())

    // TRN-062, I-D54 §8: the opening is the engine's setup: both players mulligan at the same time
    @Test fun theOpeningShowsTheEnginesHandsToEachPlayer() {
        val game = alice()
        val board = game.play().board
        assertNotNull(board.engine)
        assertTrue(board.players.all { it.status == PlayerStatus.OPENING })
        val own = board.containers.single { it.zone == PlayZone.HAND && it.accountId == f.a }
        val other = board.containers.single { it.zone == PlayZone.HAND && it.accountId == f.b }
        assertEquals(7, own.cards.size)
        assertTrue(own.cards.all { id -> !board.cards.single { it.id == id }.covered }, "her own hand face up")
        assertTrue(other.cards.all { id -> board.cards.single { it.id == id }.covered }, "Bob's hand hidden from her (I-D27)")
        assertEquals(GameDevice.Status.YourMove, game.device.status)
    }

    // I-D56 §3, §4, §14: with Bob away the starting player keeps and plays on assumed answers: his keep and passes are
    // assumed (the indicator counts them), everything goes out as submissions in order and shows at once
    @Test fun keepingWhileTheOtherPlayerIsAwayPlaysOnAssumedAnswers() {
        val game = alice()
        game.tick(live = false, now = 1_000)
        assertIs<GameDevice.Acted.Sent>(game.keep(emptyList()))
        game.tick(live = false, now = 2_000)
        val sent = game.take(2_000)
        assertTrue(sent.isNotEmpty() && sent.all { it is GameAction.Submit || it is GameAction.EngineSeen })
        val state = game.device.model.engine
        assertEquals(f.a, state.turn.active.value)
        assertEquals(TurnStep.PrecombatMain, state.turn.step)
        assertEquals(GameDevice.Status.YourMove, game.device.status)
        val indicator = assertNotNull(game.device.indicator(), "her line is tentative until Bob confirms")
        assertTrue(indicator.assumed >= 1)
        val board = game.play().board
        assertTrue(board.players.none { it.status == PlayerStatus.OPENING })
        assertEquals(f.a, board.turn.accountId)
        // The server takes them in order: each echo confirms her own pending event
        val pending = game.device.session.pending
        pending.forEachIndexed { n, event -> game.revision(f.revision(event, n + 2, mapOf(f.a to event.id, f.b to f.start.id))) }
        assertTrue(game.device.session.pending.isEmpty())
        assertNotNull(game.device.indicator(), "still tentative: Bob hasn't seen them")
    }

    // I-D56 §7, §8, HIS-033 (§29.1): coming back in the opening, Bob isn't caught up with a paused playback: the opening
    // shows at once up to the keep assumed for him, where playback stops; his same answer confirms it and playback goes on
    @Test fun theOtherPlayerCatchesUpAndConfirmsTheAssumedKeep() {
        val alice = alice()
        alice.tick(false, 1_000)
        alice.keep(emptyList())
        alice.tick(false, 2_000)
        val events: List<GameEvent> = alice.device.session.pending
        val snapshot = EngineSnapshot(f.codec.encodeDefinitions(), f.snapshot().rootId, events.last().id,
            f.codec.encodeModel(alice.device.model), localHeads = mapOf(f.a to events.last().id, f.b to f.start.id),
            baseId = f.start.id, base = f.snapshot().state, events = events.map(f.codec::encodeEvent))
        val bob = EngineGame(f.game.id, f.b, f.base, snapshot)
        assertIs<GameDevice.Status.Playing>(bob.device.status)
        var guard = 0
        while (bob.device.status is GameDevice.Status.Playing && guard++ < 100) bob.step()
        val stop = assertIs<GameDevice.Status.Stopped>(bob.device.status)
        assertTrue(stop.assumed.let { it is Input.SetupAnswer || it is Input.Answer })
        // The table is live before it: his opening hand, still Mulliganing
        assertTrue(bob.play().board.players.single { it.accountId == f.b }.status == PlayerStatus.OPENING)
        assertEquals(GameDevice.Acted.Confirmed, bob.keep(emptyList()))
        assertNull((bob.device.status as? GameDevice.Status.Stopped)?.takeIf { it.event.id == stop.event.id })
        assertTrue(bob.take(3_000).any { it is GameAction.Submit })
    }

    // SYS-012 (abreview #1): the player's own action that the game moved on from before it reached the server is told
    // ("That wasn't taken…"), never dropped silently; the table goes back to the server's. TRN-027, SYS-011 (2026-11-04):
    // a declaration the server refused because the other player's event came first waits for that event and is made
    // again on it while it is still asked; here Bob conceded, so it isn't, and it is told
    @Test fun aRefusedOwnActionIsTold() {
        val game = alice()
        game.tick(live = true, now = 1_000)
        assertIs<GameDevice.Acted.Sent>(game.keep(emptyList()))
        val submit = game.take(1_000).filterIsInstance<GameAction.Submit>().first()
        game.refused(submit, "stale")
        assertTrue(game.device.session.pending.isEmpty() && game.device.model.sameAs(f.snapshot().let { s ->
            f.codec.decodeModel(s.state) }), "back to the server's state")
        assertTrue(game.take(1_000).none { it is GameAction.Submit }, "not sent again on the event the server refused it on")
        val concede = org.asyncttwiz.protocol.game.EventBuilder(f.codec.decodeModel(f.snapshot().state), "bob-concedes", f.start.id, f.b)
            .run { perform(Input.Concede(arcanum.engine.state.PlayerId(f.b))); build() }
        game.revision(f.revision(concede, 2, mapOf(f.a to f.start.id, f.b to concede.id)))
        assertTrue(game.notes.any { it == GameDevice.Note.Refused("stale") }, "told: ${game.notes}")
        assertEquals(NOT_TAKEN, refusedText("stale"))
        assertTrue(game.device.session.pending.isEmpty(), "her keep isn't made again")
    }

    // SYS-012 (problem report N7FU6Y): an own action the server refuses for any other reason (the engine's) is told too,
    // never a silent snap back, and the app log says once which board was taken back and why
    @Test fun anOwnActionTheEngineRefusesIsToldAndLogged() {
        val game = alice()
        game.tick(live = true, now = 1_000)
        assertIs<GameDevice.Acted.Sent>(game.keep(emptyList()))
        val submit = game.take(1_000).filterIsInstance<GameAction.Submit>().first()
        val shown = game.device.session.tip
        game.refused(submit, "refusedByEngine")
        assertTrue(game.notes.any { it == GameDevice.Note.Refused("refusedByEngine") })
        assertEquals(NOT_ACCEPTED, refusedText("refusedByEngine"))
        val lines = AppLog.text().lines().filter { "Shown board taken back" in it && submit.submission.id in it }
        assertEquals(1, lines.size, AppLog.text())
        assertTrue("refusedByEngine" in lines.single() && "tip $shown → ${game.device.session.tip}" in lines.single(), lines.single())
    }

    // SYS-025, I-D56 step D2 (engine-state-architecture §12 item 11): the device lives on one engine thread; the table
    // reads only the snapshots it publishes. The player's own action still has its local result when it returns; the
    // device's own steps, the server's events and the board follow on the engine thread, the same as inline
    @Test fun theEngineWorkRunsOnItsOwnThreadAndTheTableReadsItsSnapshots() {
        val worker = ThreadWorker()
        try {
            val threaded = EngineGame(f.game.id, f.a, f.base, f.snapshot(), worker = worker)
            worker.drain()
            assertNotNull(threaded.shown, "published once loaded on the engine thread")
            assertEquals("test-engine", threaded.read { Thread.currentThread().name })
            val inline = alice()
            for (game in listOf(threaded, inline)) {
                game.tick(live = false, now = 1_000)
                assertIs<GameDevice.Acted.Sent>(game.keep(emptyList()))
                game.tick(live = false, now = 2_000)
            }
            worker.drain()
            val shown = assertNotNull(threaded.shown)
            val expected = assertNotNull(inline.shown)
            assertEquals(expected.status, shown.status)
            assertEquals(expected.indicator, shown.indicator)
            assertEquals(expected.line.size, shown.line.size)
            assertEquals(expected.play.board.turn, shown.play.board.turn)
            assertEquals(expected.play.board.players.map { it.status }, shown.play.board.players.map { it.status })
            // The server's echoes are worked in on the engine thread too
            val pending = threaded.read { it.session.pending }
            pending.forEachIndexed { n, event -> threaded.revision(f.revision(event, n + 2, mapOf(f.a to event.id, f.b to f.start.id))) }
            worker.drain()
            assertTrue(threaded.read { it.session.pending.isEmpty() })
            assertTrue(threaded.shown!!.version > shown.version)
        } finally { worker.close() }
    }

    /** A real engine thread with the test's own thread as the main thread: what it posts runs on [drain]. */
    private class ThreadWorker : EngineWorker, AutoCloseable {
        @Volatile private var thread: Thread? = null
        private val executor = java.util.concurrent.Executors.newSingleThreadExecutor { r -> Thread(r, "test-engine").also { thread = it } }
        private val main = java.util.concurrent.LinkedBlockingQueue<() -> Unit>()
        override fun run(work: () -> Unit) { executor.execute(work) }
        override fun <T> call(work: () -> T): T = if (Thread.currentThread() === thread) work() else executor.submit<T> { work() }.get()
        override fun main(work: () -> Unit) { main.add(work) }
        /** Waits until the engine thread is idle and runs what it posted, until neither has anything left. */
        fun drain() {
            do {
                executor.submit {}.get()
                var ran = false
                while (true) { (main.poll() ?: break).invoke(); ran = true }
            } while (ran)
        }
        override fun close() { executor.shutdownNow() }
    }

    @Suppress("unused") private fun GameModel.unused() = Unit
}

package org.asyncttwiz.android

import arcanum.engine.input.Input
import arcanum.engine.state.PlayerId
import org.asyncttwiz.protocol.game.GameDevice
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * The player's own action on engine state (SYS-025, playtest 2026-11-04: "playing a land had a long delay before
 * anything happened"): its board is published as soon as it is made, and the probe (SYS-030) times the wait for the
 * engine thread, the action and its board. The measurements are printed, never asserted (no timing tests).
 */
class EngineOwnActionProbeTest {
    private val f = EngineGameFixture()

    private fun mainPhase(game: EngineGame, drain: () -> Unit = {}) {
        game.tick(live = false, now = 1_000); drain()
        assertIs<GameDevice.Acted.Sent>(game.keep(emptyList())); drain()
        game.tick(live = false, now = 2_000); drain()
        assertEquals(GameDevice.Status.YourMove, game.shown!!.status)
    }

    /** Plays a land from Alice's hand (the first card the engine takes as a land play). */
    private fun playLand(game: EngineGame): GameDevice.Acted {
        val hand = game.read { d -> d.model.engine.player(PlayerId(f.a)).hand.toList() }
        for (card in hand) {
            val acted = game.act(Input.PlayLand(PlayerId(f.a), card))
            if (acted is GameDevice.Acted.Sent) return acted
        }
        error("no land in hand")
    }

    private fun lands(game: EngineGame) = game.shown!!.engine.let { s ->
        s.objects.values.count { it.zone == arcanum.api.Zone.Battlefield && it.controller == PlayerId(f.a) } }

    // SYS-025: the land shows on the board that is published right after the action, before the device's own steps that
    // follow it; the probe hears of it once
    @Test fun aLandPlayShowsAtOnceAndIsTimed() {
        val game = EngineGame(f.game.id, f.a, f.base, f.snapshot())
        mainPhase(game)
        val timed = mutableListOf<String>()
        val boards = mutableListOf<Int>()
        game.probe = { what, waited, acted, board ->
            timed += what; boards += lands(game)
            println("probe (inline) $what: waited ${waited / 1e6} ms, acted ${acted / 1e6} ms, board ${board / 1e6} ms")
        }
        val before = lands(game)
        playLand(game)
        assertEquals(listOf("act"), timed)
        assertEquals(listOf(before + 1), boards, "the board told to the probe already shows the land")
    }

    // SYS-025, switchreview #11: measured on a real engine thread, with another game loading on it first (what every
    // list game did at the start before only the open game was loaded)
    @Test fun aLandPlayBehindAnotherGamesLoadIsMeasured() {
        val worker = ThreadWorker()
        try {
            val game = EngineGame(f.game.id, f.a, f.base, f.snapshot(), worker = worker)
            worker.drain()
            mainPhase(game, worker::drain)
            var measured: String? = null
            game.probe = { what, waited, acted, board ->
                measured = "probe (engine thread, another game loading) $what: waited ${waited / 1e6} ms, acted ${acted / 1e6} ms, board ${board / 1e6} ms"
            }
            // Another game's load is queued on the same thread just before the tap
            EngineGame(f.game.id, f.b, f.base, f.snapshot(), worker = worker)
            playLand(game)
            worker.drain()
            println(assertNotNull(measured))
            assertTrue(lands(game) >= 1)
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
        fun drain() {
            do {
                executor.submit {}.get()
                var ran = false
                while (true) { (main.poll() ?: break).invoke(); ran = true }
            } while (ran)
        }
        override fun close() { executor.shutdownNow() }
    }
}

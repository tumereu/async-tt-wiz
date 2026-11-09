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
        game.tick(1_000); drain()
        assertIs<GameDevice.Acted.Sent>(game.keep()); drain()
        game.tick(2_000); drain()
        assertEquals(GameDevice.Status.YourMove, game.shown!!.status)
    }

    /** Plays a land from Alice's hand (the first card the engine takes as a land play). */
    private fun playLand(game: EngineGame): GameDevice.Acted {
        val hand = game.read { d -> d.model.engine.player(PlayerId(f.a)).hand.toList() }
        for (card in hand) {
            val acted = game.act(Input.PlayLand(PlayerId(f.a), card))
            if (acted is GameDevice.Acted.Sent || acted == GameDevice.Acted.Made) return acted
        }
        error("no land in hand")
    }

    private fun lands(game: EngineGame) = game.shown!!.engine.let { s ->
        s.objects.values.count { it.zone == arcanum.api.Zone.Battlefield && it.controller == PlayerId(f.a) } }

    // SYS-025: the land shows on the board that is published right after the action, before the device's own steps that
    // follow it; the probe hears of it once
    @Test fun aLandPlayShowsAtOnceAndIsTimed() {
        val game = EngineGame(f.game.id, f.a, f.base, f.snapshot(otherKept = true))
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
            val game = EngineGame(f.game.id, f.a, f.base, f.snapshot(otherKept = true), worker = worker)
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

    // SYS-025 (playtest 2026-11-04): on the engine thread the tap waits only until the land is made; its board is already
    // posted to the main thread then, before the passes that follow it are worked out. The times before (waiting for the
    // whole action) and after are printed, warmed up over several games.
    @Test fun aLandPlayShowsBeforeItsEventIsFinished() {
        val rounds = 6
        for (early in listOf(false, true)) {
            val times = mutableListOf<Double>()
            repeat(rounds) {
                val worker = ThreadWorker(early)
                try {
                    val game = EngineGame(f.game.id, f.a, f.base, f.snapshot(otherKept = true), worker = worker)
                    worker.drain()
                    mainPhase(game, worker::drain)
                    val before = lands(game)
                    val hand = game.read { d -> d.model.engine.player(PlayerId(f.a)).hand.toList() }
                    var acted: GameDevice.Acted? = null
                    var took = 0L
                    for (card in hand) {
                        val start = System.nanoTime()
                        acted = game.act(Input.PlayLand(PlayerId(f.a), card))
                        took = System.nanoTime() - start
                        if (acted is GameDevice.Acted.Sent || acted == GameDevice.Acted.Made) break
                        worker.drain()
                    }
                    times += took / 1e6
                    if (early) {
                        assertEquals(GameDevice.Acted.Made, acted)
                        worker.drainMain()
                        assertEquals(before + 1, lands(game), "the land shows from what was posted when the tap returned")
                    }
                    worker.drain()
                    assertEquals(before + 1, lands(game))
                } finally { worker.close() }
            }
            println("land play, tap to return (${if (early) "board at the performed point" else "whole action"}): " +
                times.joinToString { "%.1f".format(it) } + " ms")
        }
    }

    /** A real engine thread with the test's own thread as the main thread: what it posts runs on [drain]. With [early],
     * an own action returns at its release, as [EngineWorker.Background] does. */
    private class ThreadWorker(private val early: Boolean = false) : EngineWorker, AutoCloseable {
        @Volatile private var thread: Thread? = null
        private val executor = java.util.concurrent.Executors.newSingleThreadExecutor { r -> Thread(r, "test-engine").also { thread = it } }
        private val main = java.util.concurrent.LinkedBlockingQueue<() -> Unit>()
        override fun run(work: () -> Unit) { executor.execute(work) }
        override fun <T> call(work: () -> T): T = if (Thread.currentThread() === thread) work() else executor.submit<T> { work() }.get()
        override fun main(work: () -> Unit) { main.add(work) }
        override fun early(work: (release: (() -> Unit)?) -> Unit) {
            if (!early || Thread.currentThread() === thread) return super.early(work)
            val gate = java.util.concurrent.CountDownLatch(1)
            executor.execute { try { work { gate.countDown() } } finally { gate.countDown() } }
            gate.await()
        }
        fun drainMain() { while (true) (main.poll() ?: break).invoke() }
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

package org.asyncttwiz.android

import arcanum.engine.Engine
import arcanum.engine.EngineProbes
import arcanum.engine.input.Input
import arcanum.engine.state.PlayerId
import org.asyncttwiz.protocol.game.GameDevice
import org.asyncttwiz.protocol.game.GameModel
import org.junit.After
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Engine-responsiveness R2: one own action works the layers out once per state. The game's model keeps the engine
 * state's view and visible sets until its next change, and the engine hands the view of the state after an outcome back
 * ([arcanum.engine.Outcome.view]), so the board's projection, the transition's P/T and the information rule read them
 * instead of working them out again. Counted with [EngineProbes.layersWorkedOut] on the engine thread (here the test's), with
 * [GameModel.keepViews] off (every reader works them out itself, as before R2) and on; nothing is timed.
 */
class EngineLayersOnceTest {
    private val f = EngineGameFixture()

    @After fun reset() {
        EngineProbes.layersWorkedOut = null
        GameModel.keepViews = true
    }

    /** The engine thread inline, the main thread's work and frames run at once. */
    private class Inline(private val early: Boolean) : EngineWorker {
        override fun run(work: () -> Unit) = work()
        override fun <T> call(work: () -> T, done: (T) -> Unit) = done(work())
        override fun main(work: () -> Unit) = work()
        override fun early(work: (release: (() -> Unit)?) -> Unit, released: () -> Unit) { if (early) { work {}; released() } else super.early(work, released) }
    }

    /** Alice at her first main phase after her keep (Bob's keep assumed: her line is tentative). */
    private fun game(early: Boolean): EngineGame {
        val game = EngineGame(f.game.id, f.a, f.base, f.snapshot(otherKept = true), worker = Inline(early))
        game.tick(1_000)
        assertIs<GameDevice.Acted.Sent>(game.keep())
        game.tick(2_000)
        assertEquals(GameDevice.Status.YourMove, game.shown!!.status)
        return game
    }

    /** The layer computations on this thread while [action] runs on a game made with [keep]ing views. */
    private fun count(keep: Boolean, early: Boolean, action: (EngineGame) -> Unit): Int {
        GameModel.keepViews = keep
        val game = game(early)
        val thread = Thread.currentThread()
        var count = 0
        EngineProbes.layersWorkedOut = { if (Thread.currentThread() === thread) count++ }
        try { action(game) } finally { EngineProbes.layersWorkedOut = null }
        return count
    }

    private fun playLand(game: EngineGame) {
        val land = game.read { d -> Engine.options(d.model.engine, PlayerId(f.a), org.asyncttwiz.protocol.game.EnginePreferences.of(d.model.engine)).first { it is Input.PlayLand } }
        game.act(land)
    }

    private fun compare(what: String, early: Boolean, action: (EngineGame) -> Unit) {
        val before = count(keep = false, early = early, action = action)
        val after = count(keep = true, early = early, action = action)
        println("R2 layer computations for one own action ($what): before $before, after $after")
        assertTrue(before - after >= 5, "$what: at least 5 fewer layer computations (before $before, after $after)")
    }

    // R2: playing a land on a tentative line, its board shown early (the app's path): the engine's work, the early board,
    // the information rule, the folded passes and the final board with its transition
    @Test fun aLandPlayedEarlyWorksTheLayersOutOncePerState() = compare("land, early board", early = true, ::playLand)

    // R2: the same action worked out whole before its board
    @Test fun aLandPlayedWholeWorksTheLayersOutOncePerState() = compare("land, whole", early = false, ::playLand)

    // R2: a pass at the main phase (the step button)
    @Test fun aPassWorksTheLayersOutOncePerState() = compare("pass", early = true) { it.pass() }
}

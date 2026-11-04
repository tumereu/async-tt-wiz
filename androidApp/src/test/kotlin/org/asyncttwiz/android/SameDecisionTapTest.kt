package org.asyncttwiz.android

import arcanum.api.TurnStep
import arcanum.engine.Engine
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.input.Input
import arcanum.engine.state.GameState
import arcanum.engine.state.ObjectId
import arcanum.engine.state.PlayerId
import org.asyncttwiz.protocol.game.GameDevice
import org.asyncttwiz.protocol.game.PlaySettings
import org.asyncttwiz.protocol.game.ResponseMode
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * SYS-011 (owner, 2026-11-04): an own action tapped on a board the device has already moved on from (the other
 * player's event arrived, not on screen yet) is made on the newer board when the same decision still waits for the
 * player there, and refused with SYS-012's note when the board changed in a way that matters.
 */
class SameDecisionTapTest {
    private val f = EngineGameFixture()
    private val alice = PlayerId(f.a)

    /** Engine work inline (as in tests), but what it posts to the main thread waits while [holding]: the table still
     * shows the board from before (the newer one is published but hasn't reached the screen). */
    private class HeldWorker : EngineWorker {
        var holding = false
        private val main = ArrayDeque<() -> Unit>()
        override fun run(work: () -> Unit) = work()
        override fun <T> call(work: () -> T): T = work()
        override fun main(work: () -> Unit) { if (holding) main.add(work) else work() }
        fun release() { holding = false; while (main.isNotEmpty()) main.removeAt(0)() }
    }

    private val held = HeldWorker()
    private val sim = EngineAppSim(f) { p -> if (p == f.a) held else EngineWorker.Direct }

    /** Alice keeps while Bob is away (his keep assumed) and stands in her first main phase with priority, after playing
     * a Mountain when [land]; nothing of hers sent yet. */
    private fun aliceInMain(land: Boolean = false): EngineGame {
        val game = sim.load(f.a)
        sim.load(f.b)
        game.tick(live = false, now = 1_000)
        assertIs<GameDevice.Acted.Sent>(game.keep(emptyList()))
        game.tick(live = false, now = 2_000)
        if (land) {
            val s = game.device.model.engine
            val mountain = assertNotNull(Engine.options(s, alice).filterIsInstance<Input.PlayLand>().firstOrNull(), "a Mountain in hand")
            assertIs<GameDevice.Acted.Sent>(game.send(listOf(mountain)))
        }
        val shown = assertNotNull(game.shown)
        assertEquals(TurnStep.PrecombatMain, shown.engine.turn.step)
        assertEquals(GameDevice.Status.YourMove, shown.status)
        return game
    }

    /** Bob comes back and confirms what Alice's device assumed for him (the same answers): the server takes his
     * confirmations, which reach Alice as revisions that change nothing in the game. */
    private fun bobConfirms() {
        sim.flush(f.a)
        sim.deliver(f.a)
        val bob = sim.load(f.b)
        if (bob.device.catchUp) bob.startPlayback()
        var confirmed = 0
        repeat(50) {
            when (val status = bob.device.status) {
                is GameDevice.Status.Playing -> bob.step()
                is GameDevice.Status.Stopped -> { bob.act(status.assumed); confirmed++ }
                else -> return@repeat
            }
        }
        assertTrue(confirmed > 0, "Bob confirmed his assumed keep")
        sim.flush(f.b)
        assertTrue(sim.pending(f.a) > 0, "his confirmations are on their way to Alice")
    }

    /** Spark's cast with Alice's own answers (its first target, the suggested payment), as the cast panel makes it. */
    private fun spark(s: GameState): List<Input> {
        val cast = assertNotNull(Engine.options(s, alice).filterIsInstance<Input.Cast>().firstOrNull(), "a Storm Spark castable")
        val copy = s.deepCopy()
        val inputs = mutableListOf<Input>(cast)
        var input: Input = cast
        repeat(10) {
            Engine.perform(copy, input).changes.forEach(copy::apply)
            val d = copy.pending?.decision ?: return inputs
            if (d is Decision.Random || Engine.decider(copy, d.player) != alice) return inputs
            input = if (d is Decision.ManaPayment) Input.Answer(d.id, Answer.ManaPayment(d.suggestion))
            else Engine.options(copy, alice).filterIsInstance<Input.Answer>().filter { it.decision == d.id }
                .first()
            inputs += input
        }
        return inputs
    }

    private fun sameDecisionLogged(what: String) = AppLog.text().lines().any { "Own $what made on board" in it && "the same decision" in it }

    // SYS-011: Alice taps Next in her main phase while Bob's confirmation of the pass (keep) assumed for him arrives
    // and rebuilds her board: the same decision still waits for her there, so her pass is made on the newer board
    @Test fun aPassTappedWhileTheOpponentsPassArrivesIsMade() {
        val game = aliceInMain()
        bobConfirms()
        val shown = assertNotNull(game.shown)
        held.holding = true
        sim.deliver(f.a)
        assertEquals(shown.version, game.shown!!.version, "the table still shows the board from before Bob's revision")
        val acted = game.pass()
        held.release()
        assertFalse(acted is GameDevice.Acted.Refused, "her pass is taken: $acted")
        assertTrue(game.notes.none { it is GameDevice.Note.Refused }, "no refusal note: ${game.notes}")
        assertTrue(sameDecisionLogged("pass"), "made on the newer board as the same decision")
        assertNotEquals(TurnStep.PrecombatMain to 1, game.device.model.engine.turn.let { it.step to it.number }, "her turn went on")
    }

    // SYS-011: Alice casts Storm Spark while his revision arrives that changes nothing in the game: the cast,
    // its target and its payment are the same decision on the newer board, so it is made there
    @Test fun aCastWhileTheOpponentsMoveChangesNothingRelevantIsMade() {
        val game = aliceInMain(land = true)
        bobConfirms()
        val inputs = spark(game.shown!!.engine)
        held.holding = true
        sim.deliver(f.a)
        val acted = game.send(inputs)
        held.release()
        assertFalse(acted is GameDevice.Acted.Refused, "her cast is taken: $acted")
        assertTrue(game.notes.none { it is GameDevice.Note.Refused }, "no refusal note: ${game.notes}")
        assertTrue(sameDecisionLogged("send"), "made on the newer board as the same decision")
        assertTrue(game.device.session.pending.any { e -> e.engine?.steps.orEmpty().any { it.input is Input.Cast } }, "Storm Spark was cast")
    }

    // SYS-011, SYS-012: Alice casts Storm Spark on her main phase while Bob's real mulligan arrives instead of the keep
    // her device assumed: the game is back in the opening, the board she tapped on is gone; refused with the note
    @Test fun aCastOnABoardThatChangedInAWayThatMattersIsRefusedWithTheNote() {
        val game = aliceInMain(land = true)
        val inputs = spark(game.shown!!.engine)
        val bob = sim.load(f.b)
        assertIs<GameDevice.Acted.Sent>(bob.mulligan())
        sim.flush(f.b)
        held.holding = true
        sim.deliver(f.a)
        val acted = game.send(inputs)
        held.release()
        assertEquals(GameDevice.Acted.Refused("stale"), acted)
        assertTrue(game.notes.any { it == GameDevice.Note.Refused("stale") }, "told with SYS-012's note: ${game.notes}")
        assertTrue(game.device.model.engine.stack.isEmpty(), "nothing cast")
    }

    // SYS-011 (switchreview #6 kept): a fast second tap of Next on the still-shown board would pass at a point Alice never
    // saw (Full control: her beginning of combat); a different decision, so it isn't taken
    @Test fun aFastSecondPassIsNotMadeAtAPointNeverShown() {
        val game = aliceInMain()
        game.settings(PlaySettings(mode = ResponseMode.FullControl))
        val shown = assertNotNull(game.shown)
        held.holding = true
        assertIs<GameDevice.Acted.Sent>(game.pass())
        assertEquals(shown.version, game.shown!!.version, "the table still shows main 1")
        val second = game.pass()
        held.release()
        assertEquals(GameDevice.Acted.Refused("stale"), second)
        assertTrue(game.notes.any { it == GameDevice.Note.Refused("stale") })
        assertEquals(TurnStep.BeginningOfCombat, game.device.model.engine.turn.step, "her first pass only")
    }

    // SYS-011: what makes a decision the same: nothing in the game differs but its record of what happened; a new Stack
    // object, another step or an object gone (a chosen target) make it another one
    @Test fun theSameDecisionIsTheSameGameApartFromItsRecord() {
        val game = aliceInMain(land = true)
        val s = game.device.model.engine
        assertTrue(GameDevice.sameDecision(s, s.deepCopy().also { it.log.clear(); it.timestampCounter += 5 }))
        assertFalse(GameDevice.sameDecision(s, s.deepCopy().also { it.turn = it.turn.copy(step = TurnStep.BeginningOfCombat) }), "another step")
        val land: ObjectId = s.battlefield.first()
        assertFalse(GameDevice.sameDecision(s, s.deepCopy().also { it.battlefield.remove(land) }), "the target gone")
        assertFalse(GameDevice.sameDecision(s, s.deepCopy().also { it.stack.add(land) }), "a new Stack object")
    }
}

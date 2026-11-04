package org.asyncttwiz.android

import arcanum.api.TurnStep
import arcanum.engine.Engine
import arcanum.engine.change.Change
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.input.Input
import arcanum.engine.state.GameState
import arcanum.engine.state.PlayerId
import org.asyncttwiz.protocol.game.GameDevice
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * An extra turn on the app's engine path: two app engine games on the simulated server, Alice playing alone while Bob is
 * away. She casts an Ember Stride ("Take an extra turn after this one.") in her first turn and ends it: the next turn is
 * hers again.
 */
class ExtraTurnScenarioTest {
    private val f = EngineGameFixture(extraTurns = true)
    private val a = PlayerId(f.a)
    private val names = mapOf(f.a to "Alice", f.b to "Bob")

    /** Alice's device on its own while Bob is away: what the server sent played back, her device's own steps sent. */
    private fun alone(sim: EngineAppSim, alice: EngineGame) {
        sim.deliver(f.a)
        alice.playToStop()
        (alice.device.status as? GameDevice.Status.Stopped)?.let { alice.act(it.assumed) }
        alice.tick(false, sim.now)
        sim.flush(f.a)
        sim.now += 1_000
    }

    private fun named(s: GameState, name: String) = s.player(a).hand.first { s.obj(it)?.definition?.faces?.first()?.name == name }

    /** [input] with the cast's own answers and its payment (Autotap: the engine's suggestion), as the cast panel sends it. */
    private fun cast(s: GameState, input: Input): List<Input> {
        val copy = s.deepCopy()
        val inputs = mutableListOf(input)
        var next = input
        while (true) {
            Engine.perform(copy, next).changes.forEach(copy::apply)
            val d = copy.pending?.decision ?: return inputs
            if (d is Decision.Random || Engine.decider(copy, d.player) != a) return inputs
            next = if (d is Decision.ManaPayment) Input.Answer(d.id, Answer.ManaPayment(d.suggestion))
                else Engine.options(copy, a).filterIsInstance<Input.Answer>().first { it.decision == d.id }
            inputs += next
        }
    }

    // TRN-052 (CR 500.7), HIS-091: the turn change Automatum makes names whose turn begins, so Alice's extra turn is hers
    // again: turn number up by one, at Untap; the event that begins it reads "Turn 2 → you" to her, "Turn 2 → Alice" to Bob
    @Test fun anExtraTurnGoesToTheSamePlayer() {
        val sim = EngineAppSim(f)
        val alice = sim.load(f.a)
        val bob = sim.load(f.b)
        alice.keep(emptyList())
        sim.flush(f.a)
        sim.deliver(f.b)
        bob.playToStop()
        bob.keep(emptyList())
        sim.flush(f.b)
        repeat(5) { alone(sim, alice) }
        assertEquals(GameDevice.Status.YourMove, alice.device.status)
        assertEquals(1, alice.device.model.engine.turn.number)
        assertIs<GameDevice.Acted.Sent>(alice.send(listOf(Input.PlayLand(a, named(alice.device.model.engine, "Mountain")))))
        sim.flush(f.a)
        val s = alice.device.model.engine
        val stride = assertNotNull(Engine.options(s, a).filterIsInstance<Input.Cast>().firstOrNull {
            s.obj(it.card)?.definition?.faces?.first()?.name == "Ember Stride" })
        assertIs<GameDevice.Acted.Sent>(alice.send(cast(s, stride)))
        sim.flush(f.a)
        alice.endTurn()
        repeat(5) { alone(sim, alice) }

        val turn = alice.device.model.engine.turn
        assertEquals(2, turn.number)
        assertEquals(a, turn.active, "the extra turn is Alice's")
        val board = alice.play().board.turn
        assertEquals(f.a to "2", board.accountId to board.number)
        val begins = sim.line().single { e ->
            e.engine?.changes.orEmpty().any { it is Change.Step && it.before.number == 1 && it.after.number == 2 }
        }
        val change = begins.engine!!.changes.filterIsInstance<Change.Step>().first { it.after.number == 2 }
        assertEquals(TurnStep.Cleanup to TurnStep.Untap, change.before.step to change.after.step)
        assertEquals(a, change.after.active)
        val state = alice.device.model.engine
        val own = EngineEventText.of(begins, state, f.a, names)
        assertTrue(own.endsWith("Turn 2 → you"), own)
        val other = EngineEventText.of(begins, state, f.b, names)
        assertTrue(other.endsWith("Turn 2 → Alice"), other)
    }
}

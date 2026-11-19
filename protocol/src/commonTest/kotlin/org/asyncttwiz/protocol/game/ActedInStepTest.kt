package org.asyncttwiz.protocol.game

import arcanum.api.TurnStep
import arcanum.engine.input.Input
import arcanum.engine.state.ObjectId
import arcanum.engine.state.PlayerId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * STK-213 (owner 2026-11-19, item dea2ae3ac4): after a player casts a spell or activates an ability in a step, their device
 * passes for them only over what they put on top of the Stack; once that has resolved, their next pass in that step is their
 * own press (a second trick after a pump), and the other player's device doesn't assume it by their mode either. A step
 * where they did nothing is passed by their mode as before.
 */
class ActedInStepTest {
    private val glimmer = arcanum.engine.cards.Cards.invent("Idle Glimmer", "{0}", "Instant", "Draw a card.")
    private val pup = arcanum.engine.cards.Cards.invent("Idle Pup", "{0}", "Creature — Dog", "", "1/1")

    private fun hand(model: GameModel, p: PlayerId, name: String): ObjectId =
        model.engine.player(p).hand.first { model.engine.obj(it)?.definition?.faces?.first()?.name == name }

    /**
     * Both players at the table in Normal; Bob starts, both keep, and Bob casts an Idle Pup in his main phase: Alice's pause
     * (his spell on top). With [respond] Alice casts an Idle Glimmer over it; else she passes. Both pass on until the Pup has
     * resolved, and Bob passes in his main phase with the Stack empty: Alice's priority there is no pause of hers.
     */
    private fun bobPassesAfterHisPup(respond: Boolean): DeviceSim {
        val sim = DeviceSim(EngineGames(3, (List(10) { "Forest" } + List(30) { glimmer }) to (List(20) { "Island" } + List(20) { pup })))
        val alice = sim.alice; val bob = sim.bob
        sim.start(bob, mode = ResponseMode.Normal, alwaysAssume = false)
        for (p in listOf(bob, alice)) { assertIs<GameDevice.Acted.Sent>(sim.device(p).act(sim.keep(p))); sim.settle() }
        val bobs = sim.device(bob); val alices = sim.device(alice)
        bobs.model.engine.let { assertTrue(it.turn.active == bob && it.turn.step == TurnStep.PrecombatMain && it.priority.holder == bob, "Bob's main") }
        assertIs<GameDevice.Acted.Sent>(bobs.act(Input.Cast(bob, hand(bobs.model, bob, "Idle Pup"))))
        sim.settle()
        assertEquals(GameDevice.Status.YourMove, alices.status, "Alice stops at Bob's spell")
        if (respond) assertIs<GameDevice.Acted.Sent>(alices.act(Input.Cast(alice, hand(alices.model, alice, "Idle Glimmer"))))
        else assertIs<GameDevice.Acted.Sent>(alices.act(Input.PassPriority(alice)))
        var guard = 0
        // Each stop's own press, until the Pup is on the battlefield and Bob stands at his main phase again
        while (guard++ < 20) {
            sim.settle()
            val s = bobs.model.engine
            if (s.stack.isEmpty() && s.priority.holder == bob && s.turn.step == TurnStep.PrecombatMain) break
            for (p in listOf(alice, bob)) if (sim.device(p).status == GameDevice.Status.YourMove &&
                sim.device(p).model.engine.priority.holder == p) sim.device(p).act(Input.PassPriority(p))
        }
        assertTrue(bobs.model.engine.battlefield.any { bobs.model.engine.obj(it)?.definition?.faces?.first()?.name == "Idle Pup" }, "the Pup resolved")
        assertIs<GameDevice.Acted.Sent>(bobs.act(Input.PassPriority(bob)))
        sim.settle()
        return sim
    }

    // STK-213, STK-171, HIS-176 (owner 2026-11-19): Alice cast an Idle Glimmer in Bob's main phase; when Bob passes there with the
    // Stack empty (no pause of hers: Normal would pass it), her device stops for her press, and Bob's device doesn't assume her
    // pass by her mode: it waits for her
    @Test
    fun afterYourOwnCastInAStepYourNextPassThereIsYourPress() {
        val sim = bobPassesAfterHisPup(respond = true)
        val alices = sim.device(sim.alice)
        alices.model.engine.let { s ->
            assertEquals(TurnStep.PrecombatMain, s.turn.step)
            assertEquals(sim.bob, s.turn.active)
            assertEquals(sim.alice, s.priority.holder, "Alice holds priority in Bob's main phase")
            assertTrue(s.stack.isEmpty())
        }
        assertEquals(GameDevice.Status.YourMove, alices.status, "her press")
        assertIs<GameDevice.Status.Waiting>(sim.device(sim.bob).status, "Bob's device waits for her")
        assertTrue(sim.server.line().none { it.assumed?.assumedBy == sim.bob && it.assumed?.input == Input.PassPriority(sim.alice) &&
            sim.server.state().engine.turn.step != TurnStep.PrecombatMain }, "nothing assumed for her")
        // Her press moves the game on to Bob's combat
        assertIs<GameDevice.Acted.Sent>(alices.act(Input.PassPriority(sim.alice)))
        sim.settle()
        assertTrue(sim.device(sim.bob).model.engine.turn.step != TurnStep.PrecombatMain, "on past his main phase")
    }

    // STK-213, STK-171: the same with Alice having done nothing in Bob's main phase (she passed his Pup): her device passes there
    // for her as before, and the game goes on to Bob's combat
    @Test
    fun aStepWhereYouDidNothingIsPassedAsBefore() {
        val sim = bobPassesAfterHisPup(respond = false)
        assertTrue(sim.device(sim.bob).model.engine.turn.step != TurnStep.PrecombatMain, "on past his main phase")
        assertTrue(sim.device(sim.alice).model.engine.turn.step != TurnStep.PrecombatMain, "on past his main phase")
    }
}

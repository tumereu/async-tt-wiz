package arcanum.engine.turn

import arcanum.api.TurnStep
import arcanum.engine.Engine
import arcanum.engine.cards.Cards
import arcanum.engine.change.Changes
import arcanum.engine.input.Input
import arcanum.engine.scenario.scenario
import arcanum.engine.state.PlayerControl
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Controlling another player (CR 723): who makes the controlled player's decisions and what they see. */
class ControlPlayerTest {
    private val gambit = Cards.invent("Puppeteer's Gambit", "{1}{U}", "Sorcery", "You control target opponent during their next turn.")

    // CR 723.1, 723.3, 723.5, 723.8: Bob's next turn is Alice's to play; Bob stays the active player
    @Test
    fun controlDuringTheirNextTurn() = scenario {
        alice.battlefield("Island", "Island")
        alice.hand(gambit)
        players.forEach { it.library("Island", "Island", "Island") }
        alice.casts(gambit, target = "bob")
        allPass()
        assertEquals(listOf(PlayerControl(bob.id, alice.id)), state.turn.controlNext)
        // Not yet: the effect waits for Bob's next turn
        assertEquals(bob.id, Engine.decider(state, bob.id))
        advanceTo(TurnStep.Upkeep, turn = 2)
        assertEquals(bob.id, state.turn.active)
        assertEquals(alice.id, Engine.decider(state, bob.id))
        assertEquals(alice.id, Engine.decider(state, alice.id))
        assertTrue(state.turn.controlNext.isEmpty())
        // Bob's options are still Bob's own inputs (his resources, CR 723.5a)
        assertTrue(Engine.options(state, bob.id).all { it !is Input.PassPriority || it.player == bob.id })
        // CR 723.1: until the next turn begins
        advanceTo(TurnStep.End, turn = 2)
        assertEquals(alice.id, Engine.decider(state, bob.id))
        advanceTo(TurnStep.Upkeep, turn = 3)
        assertEquals(bob.id, Engine.decider(state, bob.id))
        assertNull(state.turn.controlled)
    }

    // CR 723.4: the controller sees what the controlled player sees in the game
    @Test
    fun theControllerSeesTheirHand() = scenario {
        alice.battlefield("Island", "Island")
        alice.hand(gambit)
        bob.hand("Forest")
        players.forEach { it.library("Island", "Island", "Island") }
        assertFalse(card("Forest") in Engine.visible(state, alice.id))
        alice.casts(gambit, target = "bob")
        allPass()
        advanceTo(TurnStep.Upkeep, turn = 2)
        assertTrue(card("Forest") in Engine.visible(state, alice.id))
        // Bob still sees his own cards
        assertTrue(card("Forest") in Engine.visible(state, bob.id))
    }

    // CR 723.1b: a skipped turn isn't taken, so the control waits for the next turn the player actually takes
    @Test
    fun waitsThroughASkippedTurn() = scenario {
        alice.battlefield("Island", "Island")
        alice.hand(gambit)
        players.forEach { it.library("Island", "Island", "Island", "Island") }
        alice.casts(gambit, target = "bob")
        allPass()
        change(Changes.step(state, state.turn.copy(skippedTurns = listOf(bob.id))))
        advanceTo(TurnStep.Upkeep, turn = 2)
        assertEquals(alice.id, state.turn.active)
        assertEquals(bob.id, Engine.decider(state, bob.id))
        advanceTo(TurnStep.Upkeep, turn = 3)
        assertEquals(bob.id, state.turn.active)
        assertEquals(alice.id, Engine.decider(state, bob.id))
    }

    // CR 723.1a: the last control effect created is the one that works
    @Test
    fun theLastControlWins() = scenario(players = 3) {
        alice.battlefield("Island", "Island")
        alice.hand(gambit)
        players.forEach { it.library("Island", "Island", "Island") }
        alice.casts(gambit, target = "carol")
        allPass()
        change(Changes.step(state, state.turn.copy(controlNext = state.turn.controlNext + PlayerControl(carol.id, bob.id))))
        advanceTo(TurnStep.Upkeep, turn = 3)
        assertEquals(carol.id, state.turn.active)
        assertEquals(bob.id, Engine.decider(state, carol.id))
        assertEquals(alice.id, Engine.decider(state, alice.id))
    }

    // CR 723.5: while controlled, a player's "skip when I have no choice" follows the controller's setting
    @Test
    fun passingFollowsTheControllersSetting() = scenario {
        alice.battlefield("Island", "Island")
        alice.hand(gambit)
        bob.hand("Mountain")
        players.forEach { it.library("Island", "Island", "Island") }
        alice.casts(gambit, target = "bob")
        allPass()
        change(Changes.skipWhenNoChoice(state, alice.id, false))
        advanceTo(TurnStep.Upkeep, turn = 2)
        assertFalse(Engine.shouldPassFor(state, bob.id))
    }
}

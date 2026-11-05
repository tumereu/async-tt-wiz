package arcanum.engine.casting

import arcanum.engine.Engine
import arcanum.engine.decision.Decision
import arcanum.engine.input.Input
import arcanum.engine.scenario.scenario
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * A decision's id is part of the answer that names it, and two hosts that make the same game must make the same ids
 * (problem report N7FU6Y: a shrunk Android build named a class "Question$Pay" where the JVM says "Pay", so every answer
 * to a payment a phone made was refused elsewhere as answering no such decision). A cast's question is named in words.
 */
class DecisionIdTest {
    // CR 601.2g–h: the mana payment of a cast is "cast:Mana:Pay@<player>#<inputs before it>", whatever the platform
    // (owner, 2026-11-05: an id names what is asked, of whom, and how many inputs the game has had before it)
    @Test
    fun aCastsPaymentIsNamedInWords() = scenario {
        alice.battlefield("Forest")
        alice.hand("Elvish Seer")
        perform(Engine.options(state, alice.id).filterIsInstance<Input.Cast>().single { it.card == card("Elvish Seer") })
        val payment = pending as Decision.ManaPayment
        assertEquals("cast:Mana:Pay@${alice.id.value}#${state.inputCount}", payment.id.value)
    }

    /** Alice's Elvish Seer cast, with a Forest to pay for it: its payment is asked. */
    private fun arcanum.engine.scenario.Scenario.seerCast(): Input.Cast {
        alice.battlefield("Forest")
        alice.hand("Elvish Seer")
        alice.library("Forest", "Storm Spark", "Forest")
        return Engine.options(state, alice.id).filterIsInstance<Input.Cast>().single { it.card == card("Elvish Seer") }
    }

    // Owner, 2026-11-05 (stable decision identity): the same question asked again after an undo (the changes taken back;
    // the timestamp counter stays where it was) is the same decision
    @Test
    fun theSameQuestionAfterAnUndoHasTheSameId() = scenario {
        val cast = seerCast()
        val game = state.deepCopy()
        val first = Engine.perform(game, cast)
        first.changes.forEach(game::apply)
        val asked = assertNotNull(game.pending).decision.id
        val counter = game.timestampCounter
        first.changes.asReversed().forEach(game::revert)
        assertNull(game.pending)
        assertEquals(counter, game.timestampCounter, "the timestamp counter stays where it was (D34)")
        assertEquals(asked, assertNotNull(Engine.perform(game, cast).pending).decision.id)
    }

    // Owner, 2026-11-05: a host's reorder of a library's unknown part (a new line's reshuffle) changes no id
    @Test
    fun aLibrarysReorderChangesNoId() = scenario {
        val cast = seerCast()
        val plain = state.deepCopy()
        val parts = Engine.unknownOrder(state, alice.id)
        val reorder = assertNotNull(Engine.reorderUnknown(state, alice.id, parts.map { it.reversed() }))
        state.apply(reorder)
        perform(cast)
        assertEquals(assertNotNull(Engine.perform(plain, cast).pending).decision.id, assertNotNull(pending).id)
    }

    // Owner, 2026-11-05: any input in between makes the same question another decision
    @Test
    fun anInputInBetweenGivesTheQuestionAnotherId() = scenario {
        val cast = seerCast()
        perform(cast)
        val asked = assertNotNull(pending).id
        perform(Input.Cancel(alice.id))
        perform(cast)
        assertNotEquals(asked, assertNotNull(pending).id)
    }

    // Every question's name is its own, written out (a new question must be named, not left to its class)
    @Test
    fun everyQuestionNamesItself() {
        val names = listOf(
            Question.Pay(arcanum.api.ManaCost.parse("{G}")!!),
            Question.Announce("X", 0),
            Question.Assist(emptyList()),
        ).map { it.kind }
        assertEquals(listOf("Pay", "Announce", "Assist"), names)
    }
}

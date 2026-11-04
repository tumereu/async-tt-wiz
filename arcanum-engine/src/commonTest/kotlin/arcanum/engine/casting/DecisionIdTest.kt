package arcanum.engine.casting

import arcanum.engine.Engine
import arcanum.engine.decision.Decision
import arcanum.engine.input.Input
import arcanum.engine.scenario.scenario
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A decision's id is part of the answer that names it, and two hosts that make the same game must make the same ids
 * (problem report N7FU6Y: a shrunk Android build named a class "Question$Pay" where the JVM says "Pay", so every answer
 * to a payment a phone made was refused elsewhere as answering no such decision). A cast's question is named in words.
 */
class DecisionIdTest {
    // CR 601.2g–h: the mana payment of a cast is "cast:Mana:Pay@<timestamp>.<log size>", whatever the platform
    @Test
    fun aCastsPaymentIsNamedInWords() = scenario {
        alice.battlefield("Forest")
        alice.hand("Elvish Seer")
        perform(Engine.options(state, alice.id).filterIsInstance<Input.Cast>().single { it.card == card("Elvish Seer") })
        val payment = pending as Decision.ManaPayment
        assertTrue(Regex("""cast:Mana:Pay@\d+\.\d+""").matches(payment.id.value), payment.id.value)
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

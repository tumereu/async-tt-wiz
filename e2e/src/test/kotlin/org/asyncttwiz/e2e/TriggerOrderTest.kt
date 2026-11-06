package org.asyncttwiz.e2e

import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.performClick
import arcanum.engine.decision.Decision
import org.asyncttwiz.protocol.game.ResponseMode
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/**
 * Owner, 2026-11-06: with the default response mode (Auto-skip) Alice's device put her two different enters triggers on
 * the Stack in an order of its own. She casts an Ember Twinherald ("you gain 1 life" and "draw a card" as it enters):
 * she is asked their order as a drag-to-reorder list (owner gallery review, 2026-11-06), drags the life trigger to the
 * top (it resolves first), OK, and both resolve in that order.
 */
class TriggerOrderTest : E2e() {
    private val life = "Trigger: When this creature enters, you gain 1 life."
    private val draw = "Trigger: When this creature enters, draw a card."

    // STK-030, STK-036, SYS-063, CR 603.3b: the order of two different triggers is asked in Auto-skip too, as a list
    // dragged into the order they resolve
    @Test fun twoTriggersAreOrderedInAutoSkip() {
        startGame(TestDeck.thirds(TestCards.twinherald), deal = { a, _ -> "Mountain" in hand(a) && "Ember Twinherald" in hand(a) })
        bothOpen()
        bothKeep(bobAway = false)
        assertEquals(ResponseMode.AutoSkip, alice.game!!.shown!!.settings.mode)
        alice.playFromHand("Mountain", this)
        waitUntil("the Mountain on the battlefield", along = listOf(bob)) { alice.field("Mountain").isNotEmpty() && alice.inOwnMain() }
        val hand = alice.state.player(alice.me).hand.size
        alice.playFromHand("Ember Twinherald", this)
        waitUntil("Alice ordering the Twinherald's triggers", along = listOf(bob)) {
            alice.state.pending?.decision is Decision.OrderTriggers && alice.shows(hasText("Order triggers") and isHeading())
        }
        assertEquals(2, alice.nodes(hasContentDescription("Drag to reorder")).fetchSemanticsNodes().size, "a handle per trigger")
        // Life on top: it resolves first (the draw goes on the Stack first)
        if (alice.rowTop(life) > alice.rowTop(draw)) {
            inputClockCaughtUp()
            dragRowUp(alice, life)
        }
        waitUntil("the life trigger at the top") { alice.rowTop(life) < alice.rowTop(draw) }
        val order = alice.state.pending!!.decision as Decision.OrderTriggers
        fun index(words: String) = order.triggers.single { words in it.text.orEmpty() }.index
        alice.text("OK").performClick()
        waitUntil("both triggers resolved", along = listOf(bob)) {
            alice.state.stack.isEmpty() && alice.state.player(alice.me).life == 21 && alice.inOwnMain()
        }
        // The Twinherald left the hand, the draw put a card in it
        assertEquals(hand - 1 + 1, alice.state.player(alice.me).hand.size)
        // CR 603.3b: the draw went on the Stack first, the life on top of it
        val answered = alice.engine { (device.session.events + device.session.pending).flatMap { it.engine?.steps.orEmpty() } }
            .mapNotNull { ((it.input as? arcanum.engine.input.Input.Answer)?.answer as? arcanum.engine.decision.Answer.Order)?.order }
        assertEquals(listOf(listOf(index("draw a card"), index("gain 1 life"))), answered)
        assertFalse(alice.refusalSeen, "no refusal: ${alice.shownTexts}")
    }
}

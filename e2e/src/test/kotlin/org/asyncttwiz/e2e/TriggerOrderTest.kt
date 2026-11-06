package org.asyncttwiz.e2e

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
 * she is asked their order, orders them and both resolve.
 */
class TriggerOrderTest : E2e() {
    // STK-030, STK-036, SYS-063, CR 603.3b: the order of two different triggers is asked in Auto-skip too
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
        // Draw first on the Stack, life on top
        alice.text("When this creature enters, draw a card.").performClick()
        alice.text("When this creature enters, you gain 1 life.").performClick()
        alice.text("OK").performClick()
        waitUntil("both triggers resolved", along = listOf(bob)) {
            alice.state.stack.isEmpty() && alice.state.player(alice.me).life == 21 && alice.inOwnMain()
        }
        // The Twinherald left the hand, the draw put a card in it
        assertEquals(hand - 1 + 1, alice.state.player(alice.me).hand.size)
        assertFalse(alice.refusalSeen, "no refusal: ${alice.shownTexts}")
    }
}

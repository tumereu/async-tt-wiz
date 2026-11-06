package org.asyncttwiz.e2e

import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.performClick
import arcanum.engine.decision.Decision
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Owner gallery review, 2026-11-06 (bug): a cost to pay while something resolves had no way to decline it in its payment
 * panel. Alice has a Mountain and casts Ember Tollkeepers ("When this creature enters, you may pay {1}. If you do, draw a
 * card."): the trigger asks in one panel "Pay {1}" with Autotap's Mountain already picked and "Don't pay" beside Pay. She
 * declines the first (nothing drawn, the Mountain untapped) and pays for the second (a card drawn, the Mountain tapped).
 */
class PayWhileResolvingTest : E2e() {
    private fun payPanel() = alice.shows(hasText("Pay {1}") and isHeading())

    // STK-030, STK-049, CR 118.12: Pay or Don't pay, the sources Autotap picks already picked
    @Test fun aCostToPayOnResolutionCanBeDeclinedOrPaid() {
        startGame(TestDeck("Tolls", listOf(TestCards.mountain to 20, TestCards.tollkeeper to 20)),
            deal = { a, _ -> "Mountain" in hand(a) && hand(a).count { it == "Ember Tollkeeper" } >= 2 })
        bothOpen()
        bothKeep(bobAway = false)
        alice.playFromHand("Mountain", this)
        waitUntil("the Mountain on the battlefield", along = listOf(bob)) { alice.field("Mountain").isNotEmpty() && alice.inOwnMain() }

        val before = alice.state.player(alice.me).hand.size
        alice.playFromHand("Ember Tollkeeper", this)
        waitUntil("Alice asked to pay {1}", along = listOf(bob)) { alice.state.pending?.decision is Decision.May && payPanel() }
        assertTrue(alice.shows(hasContentDescription("Mana source Mountain", substring = true) and
            hasContentDescription(", tapped", substring = true)), "Autotap's Mountain already picked: ${alice.screen()}")
        assertTrue(alice.showsText("Don't pay"), "a decline: ${alice.screen()}")
        alice.text("Don't pay").performClick()
        waitUntil("the trigger declined", along = listOf(bob)) {
            alice.state.stack.isEmpty() && alice.state.pending == null && alice.inOwnMain() && alice.field("Ember Tollkeeper").size == 1
        }
        assertEquals(before - 1, alice.state.player(alice.me).hand.size, "nothing drawn")
        assertFalse(alice.state.obj(alice.field("Mountain").single())!!.tapped, "the Mountain stays untapped")

        alice.playFromHand("Ember Tollkeeper", this)
        waitUntil("Alice asked to pay {1} again", along = listOf(bob)) { alice.state.pending?.decision is Decision.May && payPanel() }
        alice.text("Pay").performClick()
        waitUntil("the cost paid and a card drawn", along = listOf(bob)) {
            alice.state.stack.isEmpty() && alice.state.pending == null && alice.field("Ember Tollkeeper").size == 2 &&
                alice.state.player(alice.me).hand.size == before - 1
        }
        assertTrue(alice.state.obj(alice.field("Mountain").single())!!.tapped, "the Mountain paid")
        assertFalse(alice.refusalSeen, "no refusal: ${alice.shownTexts}")
    }
}

package org.asyncttwiz.e2e

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.performClick
import org.junit.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Batch B 2026-11-06 (extending the owner's "every way shows above the card"): abilities used from the hand show as chips
 * above the raised hand card; before, cycling couldn't be used at all (the hand has no menu). Alice plays a Mountain and
 * cycles an Ember Musing ({3}{R}; cycling {R}).
 */
class HandCyclingTest : E2e() {
    private val greyed = SemanticsMatcher("greyed") { "can't be paid now" in it.config.getOrElse(SemanticsProperties.StateDescription) { "" } }

    // ZON-080 (owner 2026-11-07): the raised Musing shows Cast (greyed: one Mountain) and Cycling {R}; a tap on Cycling cycles
    // it at once: the Musing goes to the graveyard and Alice draws a card
    @Test fun aCardCyclesFromItsChip() {
        startGame(TestDeck.musings(), deal = { a, _ -> "Mountain" in hand(a) && "Ember Musing" in hand(a) })
        bothOpen()
        bothKeep(bobAway = false)
        alice.playFromHand("Mountain", this)
        waitUntil("the Mountain on the battlefield", along = listOf(bob)) { alice.field("Mountain").isNotEmpty() && alice.inOwnMain() }
        val hand = alice.state.player(alice.me).hand.size
        alice.handCard("Ember Musing").performClick()
        waitUntil("the Musing's chips") { alice.shows(hasContentDescription("Way Cycling {R}")) }
        assertTrue(alice.shows(hasContentDescription("Way Cast {3}{R}") and greyed), "Cast greyed: ${alice.screen()}")
        assertFalse(alice.shows(hasContentDescription("Way Cycling {R}") and greyed), "Cycling can be paid")
        alice.node(hasContentDescription("Way Cycling {R}")).performClick()
        waitUntil("the Musing cycled, on the server", along = listOf(bob)) {
            val me = alice.state.player(alice.me)
            me.graveyard.any { cardName(alice.state, it) == "Ember Musing" } && me.hand.size == hand && alice.inOwnMain() &&
                alice.engine { device.session.pending.isEmpty() }
        }
        assertFalse(alice.refusalSeen, "no refusal: ${alice.shownTexts}")
    }
}

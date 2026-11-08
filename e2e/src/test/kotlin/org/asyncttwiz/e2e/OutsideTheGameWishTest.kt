package org.asyncttwiz.e2e

import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.performClick
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Owner, 2026-11-08 (outside the game = Side + Maybe): in every game a player's cards outside the game, what wishes and the
 * like can fetch, are their deck's Side and Maybe. Alice's deck has a Cinder Hound in Side and an Ember Herald in Maybe;
 * she casts an Ember Wish ("You may put a card you own from outside the game into your hand").
 */
class OutsideTheGameWishTest : E2e() {
    private val inPanel = hasAnyAncestor(hasContentDescription("Automatum choice"))

    // LOB-141: the Wish's choice offers both cards outside the game, the Side's Hound and the Maybe's Herald; the Herald
    // she takes goes to her hand
    @Test fun aWishOffersTheSideAndMaybeCards() {
        startGame(TestDeck.wishes(), deal = { a, _ -> "Ember Wish" in hand(a) })
        bothOpen()
        assertEquals(listOf("Cinder Hound", "Ember Herald"),
            alice.state.player(alice.me).outside.map { cardName(alice.state, it) }, "Alice's cards outside the game")
        bothKeep(bobAway = false)
        alice.playFromHand("Ember Wish", this)
        waitUntil("Alice's choice among her cards outside the game", along = listOf(bob)) {
            // Alice passes her own spell on (STK-026) as the main button says
            if (alice.stepArrow() == "Resolve" || alice.stepArrow() == "Pass") alice.once { alice.tap(alice.stepArrow()!!) }
            alice.shows(hasContentDescription("Ember Herald") and inPanel)
        }
        assertTrue(alice.shows(hasContentDescription("Cinder Hound") and inPanel), "the Side's Hound offered too: ${alice.screen()}")
        alice.nodes(hasContentDescription("Ember Herald") and inPanel)[0].performClick()
        waitUntil("the Herald picked") { alice.shows(hasContentDescription("Ember Herald, chosen") and inPanel) }
        alice.text("OK").performClick()
        waitUntil("the Herald in Alice's hand, on the server", along = listOf(bob)) {
            alice.hand("Ember Herald").size == 1 && alice.engine { device.session.pending.isEmpty() }
        }
        assertEquals(listOf("Cinder Hound"), alice.state.player(alice.me).outside.map { cardName(alice.state, it) })
        assertFalse(alice.refusalSeen, "no refusal: ${alice.shownTexts}")
    }
}

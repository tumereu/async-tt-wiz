package org.asyncttwiz.e2e

import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.performClick
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/**
 * ZON-079 (owner, 2026-11-12) on two phones: a card its player may cast from another zone (flashback from the graveyard) shows
 * at the right end of their hand row with its zone's badge and is cast from there as a hand card is; the other player's
 * table shows no such card.
 */
class HandRowOffZoneTest : E2e() {
    private val inRow get() = "Ember Flare, owner ${alice.name}, untapped, from graveyard"

    // ZON-079, CR 702.34a: Alice casts her Ember Flare from her hand, then from her graveyard by its flashback through her hand
    // row: two taps, Autotap tapping her Mountain; it is exiled and she gained 2 life twice
    @Test fun aFlashbackCardIsCastFromTheHandRow() {
        startGame(TestDeck("Flashback", listOf(TestCards.mountain to 20, TestCards.flare to 20)), TestDeck.glimmers(), deal = { a, _ ->
            hand(a).count { it == "Ember Flare" } >= 1 && hand(a).count { it == "Mountain" } >= 1
        })
        bothOpen()
        bothKeep(bobAway = false)
        alice.playFromHand("Mountain", this)
        waitUntil("the Mountain on the battlefield", along = listOf(bob)) { alice.field("Mountain").size == 1 && alice.inOwnMain() }
        alice.playFromHand("Ember Flare", this)
        waitUntil("the Flare in her graveyard, on the server", along = listOf(bob)) {
            alice.named(alice.state.player(alice.me).graveyard, "Ember Flare").size == 1 && alice.inOwnMain() &&
                alice.engine { device.session.pending.isEmpty() }
        }
        waitUntil("the Flare in her hand row: ${alice.screen()}") { alice.shows(hasContentDescription(inRow)) }
        assertFalse(bob.shows(hasContentDescription("from graveyard", substring = true)), "nothing of it in Bob's hand row")
        waitUntil("Alice's game settled") { alice.settled(this) }
        alice.described(inRow).performClick()
        waitUntil("the Flare raised in her hand row") { alice.raisedInHand() == "Ember Flare" && alice.settled(this) }
        alice.described(inRow).performClick()
        waitUntil("the Flare exiled, on the server", along = listOf(bob)) {
            alice.named(alice.state.exile, "Ember Flare").size == 1 && alice.inOwnMain() && alice.engine { device.session.pending.isEmpty() }
        }
        assertEquals(24, alice.state.player(alice.me).life, "2 life from each cast")
        assertEquals(true, alice.state.obj(alice.field("Mountain").single())?.tapped, "Autotap tapped the Mountain")
        assertFalse(alice.shows(hasContentDescription(inRow)), "out of her hand row")
    }
}

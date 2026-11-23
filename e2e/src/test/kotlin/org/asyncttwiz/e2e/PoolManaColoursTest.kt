package org.asyncttwiz.e2e

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performClick
import org.asyncttwiz.protocol.game.PlaySettings
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/**
 * Owner problem report (prod 0.3.1, 2026-11-22): with Autotap off, after tapping mana sources on the table by hand, the cast panel
 * offered the pool's mana as chips that all read "Pool mana" beside the same generic tap icon, whatever their colour, so they
 * couldn't be told apart. Alice (Autotap off) taps her Mountain and Forest on the table and casts Two Pence ({2}): the panel shows the
 * red and the green mana of her pool by colour, never "Pool mana", and paying with both casts it.
 */
class PoolManaColoursTest : E2e() {
    private fun E2ePlayer.payment(): String? = nodes(hasContentDescription("Payment: ", substring = true)).fetchSemanticsNodes()
        .firstOrNull()?.config?.getOrElse(SemanticsProperties.ContentDescription) { emptyList() }?.joinToString()

    private fun E2ePlayer.pool() = state.player(me).manaPool.size

    // STK-228, STK-157, STK-024, STK-155 (owner problem report 2026-11-22): pool mana in the cast panel shows by its colour
    @Test fun poolManaIsShownByItsColour() {
        startGame(TestDeck("Pool colours", listOf(TestCards.mountain to 12, TestCards.forest to 12, TestCards.twoPence to 16)),
            aliceSettings = PlaySettings(autotap = false),
            deal = { a, _ -> "Mountain" in hand(a) && "Forest" in hand(a) && "Two Pence" in hand(a) })
        bothOpen()
        bothKeep(bobAway = false)
        alice.playFromHand("Mountain", this)
        waitUntil("the Mountain out", along = listOf(bob)) { alice.field("Mountain").size == 1 && alice.inOwnMain() && alice.engine { device.session.pending.isEmpty() } }
        waitUntil("Alice's turn over") {
            if (alice.state.turn.active == alice.me) alice.playAlong()
            alice.state.turn.active != alice.me
        }
        waitUntil("Alice's next Main 1", along = listOf(bob)) { alice.inOwnMain() && alice.state.turn.number >= 3 }
        alice.playFromHand("Forest", this)
        waitUntil("the Forest out", along = listOf(bob)) { alice.field("Forest").size == 1 && alice.inOwnMain() && alice.engine { device.session.pending.isEmpty() } }
        for ((n, land) in listOf("Mountain", "Forest").withIndex()) {
            waitUntil("Alice's game settled") { alice.settled(this) }
            alice.battlefieldCard(land).performClick()
            waitUntil("the $land's mana in Alice's pool", along = listOf(bob)) { alice.pool() == n + 1 && alice.inOwnMain() && alice.engine { device.session.pending.isEmpty() } }
        }
        alice.playFromHand("Two Pence", this)
        waitUntil("the panel showing the pool's red and green mana: ${alice.screen()}") {
            alice.shows(hasContentDescription("Red mana from your pool")) && alice.shows(hasContentDescription("Green mana from your pool"))
        }
        assertFalse(alice.shows(hasText("Pool mana", substring = true)), "no \"Pool mana\" text: ${alice.screen()}")
        assertFalse(alice.shows(hasContentDescription("Pool mana", substring = true)), "no \"Pool mana\" said: ${alice.screen()}")
        alice.described("Red mana from your pool").performClick()
        waitUntil("the red chosen") { alice.shows(hasContentDescription("Red mana from your pool, chosen")) }
        alice.described("Green mana from your pool").performClick()
        waitUntil("both chosen: ${alice.payment()}") { alice.payment()?.let { "Red mana from your pool" in it && "Green mana from your pool" in it } == true }
        val cast = alice.nodes(hasClickAction() and hasAnyDescendant(hasText("Cast"))).fetchSemanticsNodes().minBy { it.size.width * it.size.height }
        alice.node(SemanticsMatcher("Cast button") { it.id == cast.id }).performClick()
        waitUntil("Two Pence resolved, the pool spent", along = listOf(bob)) {
            alice.state.player(alice.me).graveyard.isNotEmpty() && alice.pool() == 0 && alice.inOwnMain() && alice.engine { device.session.pending.isEmpty() }
        }
        assertEquals(0, alice.pool())
        assertFalse(alice.refusalSeen, "no refusal: ${alice.shownTexts}")
    }
}

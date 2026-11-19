package org.asyncttwiz.e2e

import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.performClick
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/**
 * Owner playtest 2026-11-19 (N9, on 0.2.1): "Adventure chip cast from hand reads 'with permission'. These were supposed to be
 * fixed already." The 2026-11-12 fix named a way by the card's own permission ability ("Flashback"); the card an Adventure
 * exiled (CR 716.3d) is cast under a permission no ability of the card gives, which still fell back to "With permission".
 */
class AdventureChipTest : E2e() {
    // ZON-105, ZON-107, ZON-108: Alice casts the free Adventure of her Ember Roamer by its chip; once it resolved the card is in
    // her hand row from exile, and raised it shows its creature's way as "Cast {R}", never "With permission"; that chip casts it
    @Test fun theCardAnAdventureExiledIsCastByItsCastChip() {
        startGame(TestDeck("Roamers", listOf(TestCards.mountain to 20, TestCards.roamer to 20)), TestDeck.glimmers(), deal = { a, _ ->
            hand(a).any { it.startsWith("Ember Roamer") } && "Mountain" in hand(a)
        })
        bothOpen()
        bothKeep(bobAway = false)
        alice.playFromHand("Mountain", this)
        waitUntil("the Mountain on the battlefield", along = listOf(bob)) { alice.field("Mountain").size == 1 && alice.inOwnMain() }
        waitUntil("Alice's game settled") { alice.settled(this) }
        // The hand card by its card's name (as the table names an adventurer card)
        val roamer = listOf("Ember Roamer // Roaming Errand", "Ember Roamer").first { runCatching { alice.handCard(it) }.isSuccess }
        alice.handCard(roamer).performClick()
        waitUntil("the Roamer's chips: ${alice.screen()}") { alice.shows(hasContentDescription("Way Roaming Errand")) }
        alice.node(hasContentDescription("Way Roaming Errand")).performClick()
        val inRow = "from exile"
        waitUntil("the Roamer in her hand row from exile, on the server", along = listOf(bob)) {
            alice.state.exile.any { cardName(alice.state, it) == "Ember Roamer" } && alice.inOwnMain() &&
                alice.engine { device.session.pending.isEmpty() } && alice.shows(hasContentDescription(inRow, substring = true))
        }
        assertEquals(22, alice.state.player(alice.me).life, "the Errand resolved")
        waitUntil("Alice's game settled") { alice.settled(this) }
        alice.node(hasContentDescription(inRow, substring = true)).performClick()
        waitUntil("the exiled Roamer's chips: ${alice.screen()}") { alice.shows(hasContentDescription("Way Cast {R}")) }
        assertFalse(alice.shows(hasContentDescription("With permission", substring = true)), "no stand-in name: ${alice.screen()}")
        alice.node(hasContentDescription("Way Cast {R}")).performClick()
        waitUntil("the Roamer on the battlefield", along = listOf(bob)) { alice.field("Ember Roamer").size == 1 && alice.inOwnMain() }
    }
}

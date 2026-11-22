package org.asyncttwiz.e2e

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performClick
import org.junit.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Problem report 9HBW6Y (prod 0.3.0): casting a spell with "As an additional cost to cast this spell, sacrifice a creature or
 * pay {3}{B}." (Eaten Alive), the cast panel's option button read "Pay {3}{B}" with its braces as plain text instead of mana
 * symbols.
 */
class OptionCostSymbolsTest : E2e() {
    /** Whether a text [text] on this player's screen draws its symbols as symbols (TBL-159): its words hold inline content. */
    private fun E2ePlayer.drawsSymbols(text: String): Boolean = nodes(hasText(text)).fetchSemanticsNodes().any { node ->
        node.config.getOrElse(SemanticsProperties.Text) { emptyList() }.any { shown ->
            shown.getStringAnnotations(INLINE_CONTENT, 0, shown.length).isNotEmpty()
        }
    }

    // TBL-159, STK-030, CR 601.2b: Alice casts Ember Feast with an Ash Pup and an Ember Geode out: its additional cost's choice offers
    // "Pay {1}{R}" with the cost drawn as mana symbols; she pays it, the Geode tapped, the Pup kept
    @Test fun anOptionsCostIsDrawnInManaSymbols() {
        startGame(TestDeck("Feast", listOf(TestCards.geode to 13, TestCards.pup to 13, TestCards.feast to 14)), TestDeck.glimmers(),
            deal = { a, _ -> listOf("Ember Geode", "Ash Pup", "Ember Feast").all { it in hand(a) } })
        bothOpen()
        bothKeep(bobAway = false)
        for (card in listOf("Ember Geode", "Ash Pup")) {
            alice.playFromHand(card, this)
            waitUntil("$card on Alice's battlefield", along = listOf(bob)) {
                alice.field(card).size == 1 && alice.inOwnMain() && alice.engine { device.session.pending.isEmpty() }
            }
        }
        alice.playFromHand("Ember Feast", this)
        waitUntil("the Feast's additional cost asked: ${alice.screen()}") { alice.showsText("Pay {1}{R}") }
        assertTrue(alice.drawsSymbols("Pay {1}{R}"), "the option's cost as mana symbols: ${alice.screen()}")
        alice.text("Pay {1}{R}").performClick()
        waitUntil("the Feast resolved, paid by the Geode", along = listOf(bob)) {
            alice.state.stack.isEmpty() && alice.inOwnMain() && alice.field("Ash Pup").size == 1 &&
                alice.state.obj(alice.field("Ember Geode").single())?.tapped == true &&
                alice.named(alice.state.player(alice.me).graveyard, "Ember Feast").size == 1
        }
        assertFalse(alice.refusalSeen, "no refusal: ${alice.shownTexts}")
    }

    private companion object {
        /** Compose's tag of inline content (a drawn symbol) in a text. */
        const val INLINE_CONTENT = "androidx.compose.foundation.text.inlineContent"
    }
}

package org.asyncttwiz.e2e

import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.performClick
import arcanum.engine.decision.Decision
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/**
 * Owner gallery review, 2026-11-06 (bug): the legend rule panel's picture showed one card though two share the name. Alice
 * casts two Ember Sovereigns (a free legendary creature): the legend rule panel shows both to pick the one to keep; she
 * keeps the second and the first goes to her graveyard.
 */
class LegendRuleTest : E2e() {
    // STK-030, CR 704.5j: every permanent with the name shows; the one tapped is kept
    @Test fun theLegendRuleShowsEveryCardWithTheName() {
        startGame(TestDeck("Sovereigns", listOf(TestCards.mountain to 20, TestCards.sovereign to 20)),
            deal = { a, _ -> hand(a).count { it == "Ember Sovereign" } >= 2 })
        bothOpen()
        bothKeep(bobAway = false)
        alice.playFromHand("Ember Sovereign", this)
        waitUntil("the first Sovereign on the battlefield", along = listOf(bob)) { alice.field("Ember Sovereign").size == 1 && alice.inOwnMain() }
        alice.playFromHand("Ember Sovereign", this)
        waitUntil("Alice's legend rule panel", along = listOf(bob)) {
            alice.state.pending?.decision is Decision.LegendRule && alice.shows(hasText("Legend rule") and isHeading())
        }
        val rule = alice.state.pending!!.decision as Decision.LegendRule
        assertEquals(2, rule.among.size)
        val inPanel = hasContentDescription("Ember Sovereign") and hasAnyAncestor(hasContentDescription("Automatum choice"))
        assertEquals(2, alice.nodes(inPanel).fetchSemanticsNodes().size, "both Sovereigns in the panel: ${alice.screen()}")
        alice.nodes(inPanel)[1].performClick()
        alice.text("Keep").performClick()
        waitUntil("one Sovereign kept, one in the graveyard", along = listOf(bob)) {
            alice.state.pending == null && alice.field("Ember Sovereign").size == 1 &&
                alice.state.player(alice.me).graveyard.count { cardName(alice.state, it) == "Ember Sovereign" } == 1
        }
        assertFalse(alice.refusalSeen, "no refusal: ${alice.shownTexts}")
    }
}

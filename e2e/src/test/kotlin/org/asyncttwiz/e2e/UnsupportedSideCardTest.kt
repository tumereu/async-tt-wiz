package org.asyncttwiz.e2e

import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isNotEnabled
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import org.asyncttwiz.protocol.GameAction
import org.junit.Test
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Owner, 2026-11-17 catalog review: a deck is unplayable if it holds even one card Automatum can't play, in any section
 * ("block even side/maybe to not have to deal with things like 'play a card you own outside of the game'"). Alice's deck
 * is all playable in Main; its one Odd Relic (rules text Automatum can't do) sits in Side.
 */
class UnsupportedSideCardTest : E2e() {
    // LOB-115, LOB-118, LOB-141: the deck's warning names the Side card in Choose deck; highlighted, it greys Use selected deck with
    // the reason; the server refuses choosing it, so Alice can't get Ready with it
    @Test fun aDeckWhoseOnlyUnplayableCardIsInItsSideCantStartAGame() {
        val (a, b) = ServerSetup.accounts()
        val name = "E2E side relic ${UUID.randomUUID().toString().take(6)}"
        val deck = ServerSetup.deck(a, TestDeck("E2E side relic", listOf(TestCards.mountain to 20, TestCards.spark to 20),
            side = listOf(TestCards.relic to 1)))
        assertEquals(listOf("Odd Relic"), deck.deck.automatum?.byHand, "the server's result counts the Side card")
        val game = ServerSetup.invitation(a, b, name)
        launchPlayers()
        waitUntil("Alice's menu") { alice.shows(hasTestTag("menu-GAMES")) }
        alice.tagged("menu-GAMES").performClick()
        waitUntil("the game in Alice's list") { alice.showsText(name) }
        alice.text(name).performScrollTo().performClick()
        waitUntil("Choose deck in the game's lobby") { alice.showsText("Choose deck") }
        alice.text("Choose deck").performScrollTo().performClick()
        waitUntil("Alice's deck in Choose deck, its warning shown") {
            alice.showsText("E2E side relic") && alice.shows(hasContentDescription("Automatum can't play 1 card yet: Odd Relic"))
        }
        alice.text("E2E side relic").performScrollTo().performClick()
        waitUntil("the reason under the list") { alice.shows(hasTestTag("automatum-blocked")) }
        val use = alice.nodes(hasClickAction() and hasAnyDescendant(hasText("Use selected deck"))).fetchSemanticsNodes()
        assertEquals(1, use.size, alice.screen())
        assertTrue(isNotEnabled().matches(use.single()), "Use selected deck greyed: ${alice.screen()}")
        // The server refuses it too
        val (chosen, ready) = ServerSetup.attempt(a, game, GameAction.SelectDeck(deck.deck.id, deck.deck.revision), GameAction.Ready)
        assertEquals("automatumUnsupported", chosen.reason)
        assertEquals("rejected", ready.outcome, "Ready without a deck: ${ready.reason}")
    }
}

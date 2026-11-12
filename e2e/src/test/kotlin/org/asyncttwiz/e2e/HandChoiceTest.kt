package org.asyncttwiz.e2e

import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.performClick
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Owner question, 2026-11-12: choosing a card from the other player's hand, now that their hand is a fan in their row on
 * the table (ZON-001). Alice casts an Ember Extraction ("Target opponent reveals their hand. You choose a nonland card from
 * it. That player discards that card.") at Bob: her choice panel shows his hand's nonland cards as cards, the one she picks
 * is discarded into his graveyard on both tables, and Bob sees what happened.
 */
class HandChoiceTest : E2e() {
    private val inChoice = hasAnyAncestor(hasContentDescription("Automatum choice"))
    private fun hounds(player: E2ePlayer) =
        player.nodes(hasContentDescription("Cinder Hound", substring = true) and inChoice).fetchSemanticsNodes()
    private fun houndsInGraveyard(player: E2ePlayer) =
        player.state.player(bob.me).graveyard.count { cardName(player.state, it) == "Cinder Hound" }

    // ZON-001, STK-030, STK-044: the caster chooses from the revealed hand in her panel; the card picked is discarded
    @Test fun theCasterChoosesACardFromTheOtherPlayersHandToDiscard() {
        startGame(TestDeck("Extractions", listOf(TestCards.mountain to 20, TestCards.extraction to 20)),
            TestDeck("Hounds", listOf(TestCards.mountain to 20, TestCards.hound to 20)),
            deal = { a, b -> "Ember Extraction" in hand(a) && "Cinder Hound" in hand(b) && "Mountain" in hand(b) })
        bothOpen()
        bothKeep(bobAway = false)
        val hand = bob.state.player(bob.me).hand.size
        val houndsInHand = bob.state.player(bob.me).hand.count { cardName(bob.state, it) == "Cinder Hound" }
        alice.playFromHand("Ember Extraction", this)
        // The target (Bob, her only opponent) picked on the table if asked (STK-047); his reveal's panel answered with OK
        waitUntil("Bob's hand in Alice's choice panel", along = listOf(bob)) {
            if (alice.shows(hasText("Choose target opponent") and isHeading()))
                alice.once { alice.node(hasContentDescription("${bob.name}, life 20", substring = true)).performClick() }
            if (alice.stepArrow() == "Resolve" || alice.stepArrow() == "Pass") alice.once { alice.tap(alice.stepArrow()!!) }
            if (alice.showsRevealOk()) alice.once { alice.revealOk() }
            hounds(alice).isNotEmpty()
        }
        assertEquals(houndsInHand, hounds(alice).size, "each Hound of his hand as a card in her panel: ${alice.screen()}")
        alice.nodes(hasContentDescription("Cinder Hound", substring = true) and inChoice)[0].performClick()
        run(skip = java.time.Duration.ofMillis(300))
        if (alice.showsControl("OK")) alice.tap("OK")
        waitUntil("the Hound in Bob's graveyard on both tables", along = listOf(bob)) {
            if (bob.showsRevealOk()) bob.once { bob.revealOk() }
            houndsInGraveyard(alice) == 1 && houndsInGraveyard(bob) == 1 && alice.state.stack.isEmpty() && bob.state.stack.isEmpty()
        }
        assertEquals(hand - 1, bob.state.player(bob.me).hand.size, "one card fewer in his hand")
        for (player in listOf(alice, bob)) assertTrue(player.shows(hasContentDescription("${bob.name} graveyard, 1 cards")),
            "his graveyard pile on ${player.name}'s table: ${player.screen()}")
        // Bob sees what happened: Alice choosing, then her choice, in his event text (his own hand's reveal opens no panel of
        // his, HIS-110), and the Hound leaving his hand for his graveyard (above)
        assertTrue(bob.shownTexts.any { it.startsWith("${alice.name} is choosing · Ember Extraction") }, "Bob told she chooses: ${bob.shownTexts}")
        assertTrue("${alice.name} chose" in bob.shownTexts, "Bob told she chose: ${bob.shownTexts}")
        assertFalse(alice.refusalSeen || bob.refusalSeen, "no refusal")
    }
}

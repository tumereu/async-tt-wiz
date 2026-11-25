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
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Owner-reported 2026-11-25: the player who chooses one of two separated piles saw only "Choose one" with "First" and "Second": no
 * cards, no idea which pile was face up or how many cards lay face down. Alice casts her free Ember Destiny (Curator of Destinies'
 * wording) and puts the Ember Cub among her top three cards face down; Bob chooses between the piles.
 */
class PileChoiceTest : E2e() {
    private val inChoice = hasAnyAncestor(hasContentDescription("Automatum choice"))
    private fun area(label: String) = hasAnyAncestor(hasContentDescription("$label area"))

    // STK-229, STK-043: Bob's choice is "Choose a pile": the face-up pile shows Alice's two cards, the face-down pile one card back and its
    // count, never the Cub; his Choose on the face-down pile puts the Cub into Alice's hand and the face-up cards into her graveyard
    @Test fun theChooserSeesTheFaceUpCardsAndTheFaceDownCount() {
        startGame(TestDeck("Destinies", listOf(TestCards.mountain to 14, TestCards.destiny to 13, TestCards.cub to 13)),
            TestDeck("Sparks", listOf(TestCards.mountain to 20, TestCards.spark to 20)), deal = { a, _ ->
                TestCards.destiny.name in hand(a) && library(a).take(3).let { top -> top.count { it == "Ember Cub" } == 1 }
            })
        bothOpen()
        bothKeep(bobAway = false)
        waitUntil("Alice's Main 1", along = listOf(bob)) { alice.inOwnMain() }
        alice.playFromHand(TestCards.destiny.name, this)
        waitUntil("Alice separating her top three cards", along = listOf(bob)) {
            alice.state.pending?.decision is Decision.ChooseObjects && alice.shows(hasText("Face-down pile · 0", substring = true))
        }
        // STK-181, STK-043 (owner gallery review 2026-11-25: every choice shows its ability's whole text): the separator sees the Destiny's
        // whole text under "Separate into piles"
        assertTrue(alice.shows(hasContentDescription("Question: ${TestCards.destiny.faces.first().rulesText}") and inChoice), "the whole text: ${alice.screen()}")
        alice.nodes(hasContentDescription("Ember Cub") and inChoice)[0].performClick()
        waitUntil("the Cub in the face-down pile") { alice.shows(hasText("Face-down pile · 1") and inChoice) }
        alice.tap("OK")
        waitUntil("Bob's pile choice") {
            (bob.state.pending?.decision as? Decision.ChooseOption)?.piles != null && bob.shows(hasText("Choose a pile") and isHeading())
        }
        val piles = assertNotNull((bob.state.pending?.decision as Decision.ChooseOption).piles)
        assertTrue(piles.secondFaceDown, "the second pile lies face down: $piles")
        assertTrue(bob.shows(hasText("Face-up pile · 2") and inChoice), "the face-up pile's count: ${bob.screen()}")
        assertTrue(bob.shows(hasText("Face-down pile · 1") and inChoice), "the face-down pile's count: ${bob.screen()}")
        // STK-229, STK-181 (owner gallery review 2026-11-25): "Choose a pile" showed only "An opponent chooses one of the piles.": the
        // chooser sees the Destiny's whole text under the title
        assertTrue(bob.shows(hasContentDescription("Question: ${TestCards.destiny.faces.first().rulesText}") and inChoice), "the whole text: ${bob.screen()}")
        val faceUp = piles.first.map { assertNotNull(cardName(bob.state, it)) }
        for (name in faceUp) assertTrue(bob.shows(hasContentDescription(name) and area("Face-up pile")), "$name drawn face up: ${bob.screen()}")
        assertEquals(1, bob.nodes(hasContentDescription("Hidden card") and area("Face-down pile")).fetchSemanticsNodes().size,
            "one card back: ${bob.screen()}")
        assertFalse(bob.shows(hasContentDescription("Ember Cub") and inChoice), "the face-down Cub stays hidden: ${bob.screen()}")
        val hand = alice.state.player(alice.me).hand.size
        bob.tagged("pile-choose-second").performClick()
        waitUntil("the Cub in Alice's hand", along = listOf(bob)) {
            alice.state.player(alice.me).hand.size == hand + 1 && alice.hand("Ember Cub").isNotEmpty() && alice.state.stack.isEmpty()
        }
        val graveyard = alice.state.player(alice.me).graveyard.mapNotNull { cardName(alice.state, it) }
        assertTrue(faceUp.all { it in graveyard }, "the face-up pile in Alice's graveyard: $graveyard")
        assertFalse(bob.refusalSeen, "no refusal: ${bob.shownTexts}")
        assertTrue(alice.leaks.leaks.isEmpty() && bob.leaks.leaks.isEmpty(), "no leaks: ${alice.leaks.leaks + bob.leaks.leaks}")
    }
}

package org.asyncttwiz.automatum

import kotlin.test.Test
import kotlin.test.assertEquals

/** STK-220: another player's spell's own "you"/"your" read as its controller's name, cases from oracle templates. */
class ControllerWordsTest {
    private fun bob(text: String, before: String = "") = ControllerWords.text(text, "Bob", before)

    // STK-220: gaining and losing life, drawing, discarding, sacrificing agree with the name
    @Test fun aSubjectYouAgreesWithTheName() {
        assertEquals("Bob gains 2 life", bob("you gain 2 life"))
        assertEquals("Bob gains 3 life.", bob("You gain 3 life."))
        assertEquals("Bob loses 1 life and Bob draws a card", bob("you lose 1 life and you draw a card"))
        assertEquals("Bob draws 2 cards, then Bob discards a card", bob("you draw 2 cards, then you discard a card"))
        assertEquals("Bob sacrifices a creature", bob("you sacrifice a creature"))
        assertEquals("Bob searches Bob's library for a basic land card", bob("you search your library for a basic land card"))
        assertEquals("Bob copies it", bob("you copy it"))
        assertEquals("Bob pays 2 life", bob("you pay 2 life"))
    }

    // STK-220: modals stay, irregular verbs agree
    @Test fun modalsAndIrregularVerbs() {
        assertEquals("Bob may draw a card", bob("you may draw a card"))
        assertEquals("If Bob does, Bob gains 2 life", bob("If you do, you gain 2 life"))
        assertEquals("If Bob doesn't, Bob loses 3 life", bob("If you don't, you lose 3 life"))
        assertEquals("Bob can't lose the game this turn", bob("You can't lose the game this turn"))
        assertEquals("if Bob has 7 or more cards in hand", bob("if you have 7 or more cards in hand"))
        assertEquals("if Bob controlled an artifact", bob("if you controlled an artifact"))
        assertEquals("Bob also gains 1 life", bob("you also gain 1 life"))
        assertEquals("if Bob is the monarch", bob("if you're the monarch"))
        assertEquals("spells Bob has cast this turn", bob("spells you've cast this turn"))
    }

    // STK-220: "you control", "you own" as relative clauses
    @Test fun youControlAndYouOwn() {
        assertEquals("creatures Bob controls get +1/+1 until end of turn", bob("creatures you control get +1/+1 until end of turn"))
        assertEquals("return each creature card Bob owns", bob("return each creature card you own"))
        assertEquals("for each land Bob controls", bob("for each land you control"))
    }

    // STK-220: possessives and objects
    @Test fun yourAndAnObjectYou() {
        assertEquals("return a creature card from Bob's graveyard to Bob's hand", bob("return a creature card from your graveyard to your hand"))
        assertEquals("deals 2 damage to Bob", bob("deals 2 damage to you"))
        assertEquals("each player other than Bob", bob("each player other than you"))
        assertEquals("each creature attacking Bob", bob("each creature attacking you"))
        assertEquals("the card is Bob's", bob("the card is yours"))
        // A "you" opening the text whose words before it make it an object
        assertEquals("Bob", bob("you", before = "deals 3 damage to "))
    }

    // STK-220: opponents mean the viewer (or one of several players): "your opponents" names the controller, the rest stays
    @Test fun opponentPhrasesStay() {
        assertEquals("each opponent loses 2 life and Bob gains 2 life", bob("each opponent loses 2 life and you gain 2 life"))
        assertEquals("target opponent reveals their hand", bob("target opponent reveals their hand"))
        assertEquals("each of Bob's opponents mills 3 cards", bob("each of your opponents mills 3 cards"))
    }

    // STK-221: a sentence that can't be rewritten cleanly keeps "you" with the name after it
    @Test fun anUnknownVerbFallsBackToYouWithTheName() {
        assertEquals("you (Bob) frobnicate a card", bob("you frobnicate a card"))
        assertEquals("You (Bob) each draw a card", bob("You each draw a card"))
    }

    // STK-220: the name is its own part, for colouring it
    @Test fun theNameIsAPart() {
        assertEquals(listOf(ControllerWords.Part.Name("Bob"), ControllerWords.Part.Plain("'s graveyard")),
            ControllerWords.parts("your graveyard", "Bob"))
        assertEquals(listOf(ControllerWords.Part.Plain("each opponent mills 4 cards")), ControllerWords.parts("each opponent mills 4 cards", "Bob"))
    }
}

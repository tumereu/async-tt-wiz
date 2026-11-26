package org.asyncttwiz.android

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** STK-230 (owner 2026-11-25): the part of a spell's or ability's whole text a panel is about, highlighted only where it is known exactly. */
class AskedPartTest {
    private val forage = "When this creature enters, you may forage. If you do, put two +1/+1 counters on it."

    // STK-230: a question whose words are a run of whole clauses of the text is found there, its full stop with it
    @Test fun aQuestionsClausesAreFoundInTheText() {
        assertEquals("you may forage. If you do, put two +1/+1 counters on it.",
            AskedPart.clauses(forage, "You may forage. If you do, put two +1/+1 counters on it.")?.of(forage))
        assertEquals("you may forage.", AskedPart.clauses(forage, "You may forage?")?.of(forage))
        val musing = "You gain 2 life. You may draw a card. If you do, discard a card."
        assertEquals("You may draw a card.", AskedPart.clauses(musing, "You may draw a card.")?.of(musing))
        val search = "Search your library for a basic land card, reveal it, put it into your hand, then shuffle. You gain 2 life."
        assertEquals("Search your library for a basic land card, reveal it, put it into your hand, then shuffle.",
            AskedPart.clauses(search, "Search your library for a basic land card, reveal it, put it into your hand, then shuffle.")?.of(search))
    }

    // STK-230: a question across lines of a spell's text, and a symbol at its edge drawn whole
    @Test fun aClauseOnItsOwnLineAndSymbolsWhole() {
        val text = "Kicker {2}\nYou may pay {2}. If you do, draw a card."
        assertNull(AskedPart.clauses(text, "pay {2}"), "only part of a clause")
        assertEquals("You may pay {2}.", AskedPart.clauses(text, "You may pay {2}.")?.of(text))
        val cost = "{2}, {T}: Draw a card. Activate only as a sorcery."
        assertEquals("{2}", AskedPart.clauses(cost, "2")?.of(cost), "a symbol drawn whole")
    }

    // STK-230: never a guess: words found nowhere, only part of a clause, twice, or the whole text highlight nothing
    @Test fun nothingWhenNotKnownExactly() {
        assertNull(AskedPart.clauses(forage, "Put on the bottom"), "a scry's words aren't in the text")
        assertNull(AskedPart.clauses(forage, "Forage?"), "only part of a clause")
        assertNull(AskedPart.clauses("Draw a card. Draw a card.", "Draw a card."), "found twice")
        assertNull(AskedPart.clauses(forage, forage), "the whole text")
        assertNull(AskedPart.clauses(forage, null))
        assertNull(AskedPart.clauses(forage, ""))
    }

    // STK-230, HIS-110: a reveal's sentence is the one sentence of the text saying "reveal"; none when none or several do
    @Test fun aRevealsSentence() {
        val insight = "Reveal the top card of your library and put that card into your hand. You gain 1 life."
        assertEquals("Reveal the top card of your library and put that card into your hand.",
            AskedPart.sentence(insight, AskedPart.REVEAL, "reveal")?.of(insight))
        val glimpse = "Look at the top four cards of your library. You may reveal a creature card from among them and put it into your hand. " +
            "Put the rest on the bottom of your library in a random order."
        assertEquals("You may reveal a creature card from among them and put it into your hand.",
            AskedPart.sentence(glimpse, AskedPart.REVEAL, "reveal")?.of(glimpse))
        assertNull(AskedPart.sentence("Reveal the top card of your library. If you revealed a land card, reveal another.", AskedPart.REVEAL, "reveal"),
            "two sentences say reveal")
        assertNull(AskedPart.sentence("Draw a card. You gain 1 life.", AskedPart.REVEAL, "reveal"), "none says it")
        assertNull(AskedPart.sentence("Reveal the top card of your library.", AskedPart.REVEAL, "reveal"), "the whole text")
        val granted = "Draw a card. Target creature gains \"Whenever this creature attacks, reveal the top card of your library.\" Reveal your hand."
        assertEquals("Reveal your hand.", AskedPart.sentence(granted, AskedPart.REVEAL, "reveal")?.of(granted), "a quoted ability's reveal is its own")
    }

    // STK-230, HIS-114, HIS-233: a pile move's sentence names where the cards came from and went; a library exile's says "exile"
    @Test fun aPileMovesAndAnExilesSentence() {
        val recall = "Return target instant or sorcery card from your graveyard to your hand. You gain 2 life."
        assertEquals("Return target instant or sorcery card from your graveyard to your hand.",
            AskedPart.sentence(recall, AskedPart.leaving("graveyards?", "hands?"), "where")?.of(recall))
        val delve = "Exile the top two cards of your library. Until end of turn, you may play those cards.\nDraw a card."
        assertEquals("Exile the top two cards of your library.", AskedPart.sentence(delve, AskedPart.EXILE, "exile")?.of(delve))
        val bullets = "Choose one —\n• Reveal the top card of your library.\n• Draw a card."
        assertEquals("Reveal the top card of your library.", AskedPart.sentence(bullets, AskedPart.REVEAL, "reveal")?.of(bullets), "a mode's bullet left out")
    }

    // STK-229, STK-230 (owner 2026-11-26): a pile choice's part is the one sentence choosing a pile, never the one separating them
    @Test fun aPileChoicesSentence() {
        val destiny = "Look at the top three cards of your library and separate them into a face-down pile and a face-up pile. " +
            "An opponent chooses one of the piles. Put that pile into your hand and the other into your graveyard."
        assertEquals("An opponent chooses one of the piles.", AskedPart.sentence(destiny, AskedPart.PILE_CHOICE, "pile")?.of(destiny))
        val fact = "Reveal the top five cards of your library. An opponent separates those cards into two piles. Put one pile into your " +
            "hand and the other into your graveyard."
        assertEquals("Put one pile into your hand and the other into your graveyard.", AskedPart.sentence(fact, AskedPart.PILE_CHOICE, "pile")?.of(fact))
        assertNull(AskedPart.sentence("Separate those cards into two piles. An opponent chooses one of them.", AskedPart.PILE_CHOICE, "pile"),
            "no sentence names choosing a pile")
    }
}

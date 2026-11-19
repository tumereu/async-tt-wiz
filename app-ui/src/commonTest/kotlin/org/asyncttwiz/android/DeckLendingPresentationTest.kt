package org.asyncttwiz.android

import org.asyncttwiz.protocol.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** LOB-244..LOB-258: how Manage decks lists categories and how a lobby shows lent decks. */
class DeckLendingPresentationTest {
    private val lender = AccountIdentity("a", "Tuomas")
    private val borrower = AccountIdentity("b", "Riley")
    private val game = GameSummary("g", "Azure Spark", "2026-11-19T12:00:00Z", lender, borrower, "round", "invite", InvitationState.ACCEPTED)
    private val box = DeckCategory("c-box", "Battle box")
    private val red = DeckCategory("c-red", "Red", box.id)
    private val other = DeckCategory("c-other", "Aggro")
    private fun deck(id: String, category: String? = null, draft: Boolean = false) = DeckSummary(id, "Deck $id", "1", "2026-11-19T12:00:00Z",
        category = category, draft = if (draft) DraftDeckOrigin("pod", "Cube night") else null)

    // LOB-244: categories (siblings by name) before the decks without one; a shut category hides what it holds, an open one
    // shows its subcategories, then its decks; each counts the decks in it and below it
    @Test fun theDeckTreeListsCategoriesThenUncategorisedDecks() {
        val decks = listOf(deck("loose"), deck("burn", red.id), deck("elves", box.id), deck("rush", other.id))
        val shut = deckTreeRows(decks, listOf(box, red, other), emptySet())
        assertEquals(listOf("Aggro 1", "Battle box 2", "loose"), shut.map(::label))
        val open = deckTreeRows(decks, listOf(box, red, other), setOf(box.id, red.id))
        assertEquals(listOf("Aggro 1", "Battle box 2", "Red 1", "burn", "elves", "loose"), open.map(::label))
        assertEquals(listOf(0, 0, 1, 2, 1, 0), open.map { it.depth })
        // A deck in a category that isn't listed is uncategorised
        assertEquals(listOf("lost"), deckTreeRows(listOf(deck("lost", "gone")), emptyList(), emptySet()).map(::label))
    }

    private fun label(row: DeckTreeRow) = when (row) {
        is DeckTreeRow.Category -> "${row.category.name} ${row.decks}"
        is DeckTreeRow.Deck -> row.deck.id
    }

    // LOB-246: what deleting a category says it does
    @Test fun deletingACategorySaysWhereItsContentsGo() {
        assertEquals("Its decks and subcategories move up to “Battle box”. No deck is deleted.", deleteCategoryNote(listOf(box, red), red))
        assertEquals("Its decks and subcategories move up to the top level. No deck is deleted.", deleteCategoryNote(listOf(box, red), box))
    }

    // LOB-255, LOB-040: the other player's Choose deck: the lender's lent decks first under their categories, uncategorised last,
    // then their own decks and draft decks; with Only lent decks those are greyed
    @Test fun chooseDeckListsTheLentDecksFirst() {
        val lent = LentDecks(lender, listOf(deck("burn", red.id), deck("elves", box.id), deck("plain")), listOf(box, red))
        val own = listOf(deck("mine"), deck("drafted", draft = true))
        val lending = game.copy(deckSettings = DeckSettings(DeckLending.Categories(listOf(box.id))))
        val sections = deckPickerSections(lending, borrower.accountId, own, emptyList(), lent)
        assertEquals(listOf("Tuomas's decks" to emptyList(), "Battle box" to listOf("elves"), "Battle box › Red" to listOf("burn"),
            "Uncategorised" to listOf("plain"), "Your decks" to listOf("mine"), "Draft decks" to listOf("drafted")),
            sections.map { it.heading to it.decks.map { deck -> deck.id } })
        assertEquals(List(6) { true }, sections.map { it.usable })
        val box = deckPickerSections(lending.copy(deckSettings = lending.deckSettings.copy(onlyLent = true)), borrower.accountId, own, emptyList(), lent)
        assertEquals(listOf(true, true, true, true, false, false), box.map { it.usable })
        assertEquals("Only Tuomas's lent decks can be used in this game.", onlyLentNote(lender.username))
        // Nothing lent: the picker as it always was
        assertEquals(listOf(null to listOf("mine"), "Draft decks" to listOf("drafted")),
            deckPickerSections(game, borrower.accountId, own, emptyList(), null).map { it.heading to it.decks.map { deck -> deck.id } })
    }

    // LOB-249, LOB-255: the creator's own Choose deck in a battle box: the lent decks usable, the others greyed
    @Test fun theCreatorsDecksNotLentAreGreyedInABattleBox() {
        val own = listOf(deck("burn", red.id), deck("rush", other.id), deck("drafted", draft = true))
        val battleBox = game.copy(deckSettings = DeckSettings(DeckLending.Categories(listOf(box.id)), onlyLent = true))
        assertEquals(setOf("burn"), lentOwnDecks(battleBox.deckSettings, own, listOf(box, red, other)))
        val sections = deckPickerSections(battleBox, lender.accountId, own, listOf(box, red, other), null)
        assertEquals(listOf(Triple(null, listOf("burn"), true), Triple("Not lent", listOf("rush"), false), Triple("Draft decks", listOf("drafted"), false)),
            sections.map { Triple(it.heading, it.decks.map { deck -> deck.id }, it.usable) })
    }

    // LOB-247, LOB-248: what Lend my decks shows: the creator names their categories, the other player counts them
    @Test fun lendMyDecksShowsItsValue() {
        assertEquals("Off", GameSettingsText.lending(DeckLending.Off, null))
        assertEquals("All", GameSettingsText.lending(DeckLending.All, null))
        assertEquals("Battle box › Red, Aggro", GameSettingsText.lending(DeckLending.Categories(listOf(red.id, other.id)), listOf(box, red, other)))
        assertEquals("2 categories", GameSettingsText.lending(DeckLending.Categories(listOf(red.id, other.id)), null))
        assertEquals("1 category", GameSettingsText.lending(DeckLending.Categories(listOf(red.id)), null))
    }

    // LOB-250, LOB-036: with Random a player without a deck may get Ready, and the deck says it is dealt as the game starts
    @Test fun randomNeedsNoChosenDeck() {
        val preparation = LobbyPreparation(game.id, game.preparationRound, listOf(PlayerPreparation(lender.accountId), PlayerPreparation(borrower.accountId)))
        assertEquals("Select your deck to get ready.", readyReason(preparation, borrower.accountId, game))
        val random = game.copy(deckSettings = DeckSettings(DeckLending.All, random = true))
        assertNull(readyReason(preparation, borrower.accountId, random))
        assertEquals("Dealt at random when the game starts", DEALT_AT_RANDOM)
    }

    // LOB-252, LOB-256: an invitation that lends decks says so on its rows; a borrowed deck names its lender
    @Test fun invitationsAndBorrowedDecksNameTheLender() {
        val invitation = game.copy(invitationState = InvitationState.PENDING, deckSettings = DeckSettings(DeckLending.All))
        assertEquals("Tuomas lends you decks", lendsDecksLine(invitation, borrower.accountId))
        assertNull(lendsDecksLine(invitation, lender.accountId), "the creator's own row says nothing")
        assertNull(lendsDecksLine(invitation.copy(deckSettings = DeckSettings()), borrower.accountId))
        assertNull(lendsDecksLine(invitation.copy(invitationState = InvitationState.ACCEPTED), borrower.accountId))
        val borrowed = PreparedDeck("p", "burn", "1", "Burn", null, "00000000-0000-4000-8000-000000000001", emptyList(), lender = lender.accountId)
        assertEquals(" · from Tuomas", borrowedFrom(borrowed, game))
        assertNull(borrowedFrom(borrowed.copy(lender = null), game))
    }
}

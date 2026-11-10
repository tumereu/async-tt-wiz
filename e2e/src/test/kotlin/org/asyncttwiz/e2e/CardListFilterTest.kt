package org.asyncttwiz.e2e

import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import kotlinx.coroutines.runBlocking
import org.asyncttwiz.android.DeckApi
import org.asyncttwiz.protocol.SavedDeckSection
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Owner, beta9 notes: the deck editor's Cards tab filters by search's syntax and chips, and what you do while filtered keeps every card. */
class CardListFilterTest : E2e() {
    private val deck = TestDeck("E2E Filter", listOf(TestCards.mountain to 20, TestCards.cub to 10, TestCards.spark to 10))
    private fun shows(name: String, section: String) = alice.shows(hasContentDescription("$name, ", substring = true) and
        hasContentDescription(section, substring = true))
    private val summary get() = alice.screen()

    // LOB-142, LOB-105, LOB-120, LOB-108, LOB-119: typed text and chips narrow the sections; the counts stay the whole
    // list's; a swipe, Undo and the section's + work while filtered; the Text tab and the saved deck keep every card
    @Test fun filteringShowsOnlyMatchesAndEditingKeepsTheWholeList() {
        val saved = ServerSetup.deck(ServerSetup.accounts().first, deck)
        launchPlayers()
        alice.decksShown = true
        waitUntil("Alice's menu") { alice.shows(hasTestTag("menu-DECKS")) }
        alice.tagged("menu-DECKS").performClick()
        waitUntil("her deck in the list") { alice.showsText(deck.name) }
        alice.text(deck.name).performClick()
        // Edit is greyed until the deck has loaded
        waitUntil("the saved deck") { alice.showsText("Edit") && alice.showsText("Mainboard · 40") }
        alice.text("Edit").performClick()
        waitUntil("the Cards tab") { alice.shows(hasTestTag("card-list-filter-field")) && shows("Ember Cub", "Mainboard") }
        val field = alice.tagged("card-list-filter-field")

        // Search's syntax
        field.performTextInput("t:instant")
        waitUntil("only the Spark") { shows("Storm Spark", "Mainboard") && !shows("Ember Cub", "Mainboard") }
        assertTrue(alice.showsText("40 main"), "the counts stay the whole list's: $summary")
        assertTrue(alice.showsText("Mainboard · 10"), "the section counts what shows: $summary")
        assertTrue(alice.showsText("No matching cards"), "Side has none: $summary")

        // Text that can't be read says why and filters nothing
        field.performTextClearance()
        field.performTextInput("t:")
        waitUntil("the reason, every card back") { alice.shows(hasTestTag("card-list-filter-error")) && shows("Ember Cub", "Mainboard") }
        field.performTextClearance()

        // Chips: the colourless card alone, then with its row's other choice the red ones too
        alice.tagged("search-colour-C").performClick()
        waitUntil("the Mountain alone") { shows("Mountain", "Mainboard") && !shows("Ember Cub", "Mainboard") && !shows("Storm Spark", "Mainboard") }
        alice.tagged("search-colour-R").performClick()
        waitUntil("the Mountain and the red cards") { shows("Ember Cub", "Mainboard") && shows("Storm Spark", "Mainboard") }
        alice.tagged("search-colour-C").performClick()
        alice.tagged("search-colour-R").performClick()
        waitUntil("no chip, every card") { alice.showsText("Mainboard · 40") }

        // A swipe while filtered moves one copy on the whole list; the filter follows it to Side (a swipe left, SYS-083)
        field.performTextInput("t:instant")
        waitUntil("only the Spark again") { !shows("Ember Cub", "Mainboard") }
        alice.described("Storm Spark, 10 copies, Mainboard").performTouchInput { swipeLeft() }
        waitUntil("one Spark in Side") { alice.showsText("39 main · 1 side") && shows("Storm Spark", "Sideboard") }
        assertTrue(!shows("Ember Cub", "Mainboard"), "still filtered: $summary")

        // Undo takes it back, and the swipe again
        alice.tagged("card-list-undo").performClick()
        waitUntil("the Spark back in Main") { alice.showsText("40 main") && alice.shows(hasContentDescription("Storm Spark, 10 copies, Mainboard")) }
        alice.described("Storm Spark, 10 copies, Mainboard").performTouchInput { swipeLeft() }
        waitUntil("the swipe again") { alice.showsText("39 main · 1 side") }

        // The section's + opens search, and Done comes back to the same filter
        alice.tagged("add-cards-side").performClick()
        waitUntil("the search page") { alice.shows(hasTestTag("card-search")) }
        alice.tagged("card-search-done").performClick()
        waitUntil("the Cards tab, still filtered") { shows("Storm Spark", "Sideboard") && !shows("Ember Cub", "Mainboard") }

        // The Text tab holds every card, and so does what is saved
        alice.tagged("card-list-tab-text").performClick()
        waitUntil("the Text tab's list") { alice.shows(hasTestTag("deck-list")) }
        val text = alice.deckImport.state.text
        for (line in listOf("20 Mountain", "10 Ember Cub", "9 Storm Spark", "1 Storm Spark")) assertTrue(line in text, "$line in $text")
        alice.tagged("card-list-tab-cards").performClick()
        waitUntil("the filter still there") { alice.shows(hasTestTag("card-list-filter-field")) && !shows("Ember Cub", "Mainboard") }
        alice.tagged("save-deck").performClick()
        waitUntil("the saved deck") { alice.showsText("Edit") }
        val entries = runBlocking { DeckApi(E2eStack.origin).use { it.read(alice.account.token, saved.deck.id) } }.entries
        fun copies(card: TestCard, section: SavedDeckSection) =
            entries.filter { it.printingId == card.printingId && it.section == section }.sumOf { it.quantity }
        assertEquals(20, copies(TestCards.mountain, SavedDeckSection.MAINBOARD))
        assertEquals(10, copies(TestCards.cub, SavedDeckSection.MAINBOARD))
        assertEquals(9, copies(TestCards.spark, SavedDeckSection.MAINBOARD))
        assertEquals(1, copies(TestCards.spark, SavedDeckSection.SIDEBOARD))
    }
}

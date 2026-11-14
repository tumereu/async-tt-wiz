package org.asyncttwiz.e2e

import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import kotlinx.coroutines.runBlocking
import org.asyncttwiz.android.DeckApi
import org.asyncttwiz.protocol.SavedDeckSection
import org.junit.Test
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The desktop layout's deck editor (docs/specs/web-client.md §7.5, step 6, wireframe D8): Alice, on a computer at 1440 × 900,
 * builds her deck with the search beside the list, hides the search and opens it again, and saves with Ctrl+S.
 */
@Config(qualifiers = "w1440dp-h900dp-land-mdpi")
class DesktopDeckEditorTest : E2e() {
    private val deck = TestDeck("E2E Desktop", listOf(TestCards.mountain to 20, TestCards.cub to 10))
    private fun result(name: String) = hasTestTag("search-result-$name")

    // WEB-037, WEB-039, WEB-040, WEB-041, LOB-105, LOB-121: from the side menu to the saved deck and its editor; a click on a
    // search result adds a copy to the section chosen in Add to; Hide search folds the search into its rail and the rail
    // opens it again with the same search; Ctrl+S saves the deck with the added cards
    @Test fun buildADeckInTheTwoPaneEditor() {
        val saved = ServerSetup.deck(ServerSetup.accounts().first, deck)
        launchPlayers(aliceCatalog = true, aliceDesktop = true)
        waitUntil("Alice's card records installed") { alice.catalog!!.state.ready }
        alice.decksShown = true
        // WEB-037: the side menu is open beside the main menu
        waitUntil("Alice's side menu") { alice.shows(hasTestTag("navigation-Manage decks")) }
        alice.tagged("navigation-Manage decks").performClick()
        waitUntil("her deck in the list") { alice.showsText(deck.name) }
        alice.text(deck.name).performClick()
        waitUntil("the saved deck") { alice.showsText("Edit") && alice.showsText("Mainboard · 30") }
        alice.text("Edit").performClick()

        // WEB-039: the list and the search side by side
        waitUntil("the two-pane editor") { alice.shows(hasTestTag("card-search-pane")) && alice.shows(hasTestTag("card-list-cards")) }
        alice.tagged("card-search-field").performTextInput("spark")
        waitUntil("Storm Spark found") { alice.shows(result("Storm Spark")) }
        alice.node(result("Storm Spark")).performClick()
        waitUntil("a Spark in Main") { alice.showsText("31 main") }
        alice.node(result("Storm Spark")).performClick()
        waitUntil("two Sparks in Main") { alice.showsText("32 main") }
        alice.tagged("destination-side").performClick()
        alice.node(result("Storm Spark")).performClick()
        waitUntil("a Spark in Side") { alice.showsText("32 main · 1 side") }

        // WEB-040: hidden, the list takes the width and the rail stays; the rail opens the search as it was left
        alice.tagged("hide-search").performClick()
        waitUntil("the search folded into its rail") { !alice.shows(hasTestTag("card-search-pane")) && alice.shows(hasTestTag("search-rail")) }
        assertTrue(alice.shows(hasContentDescription("Storm Spark, 2 copies, Mainboard")), "the Sparks among the list's cards: ${alice.screen()}")
        alice.tagged("search-rail").performClick()
        waitUntil("the search again, as left") {
            alice.shows(hasTestTag("card-search-field") and hasText("spark")) && alice.shows(result("Storm Spark"))
        }
        alice.node(result("Storm Spark")).performClick()
        waitUntil("Add to still on Side") { alice.showsText("32 main · 2 side") }

        // WEB-041: Ctrl+S saves
        assertTrue(alice.key("s", true, ctrl = true), "Ctrl+S taken")
        alice.key("s", false, ctrl = true)
        waitUntil("the saved deck") { !alice.deckImport.state.active && alice.showsText("Edit") }
        val entries = runBlocking { DeckApi(E2eStack.origin).use { it.read(alice.account.token, saved.deck.id) } }.entries
        fun copies(card: TestCard, section: SavedDeckSection) =
            entries.filter { it.printingId == card.printingId && it.section == section }.sumOf { it.quantity }
        assertEquals(20, copies(TestCards.mountain, SavedDeckSection.MAINBOARD))
        assertEquals(10, copies(TestCards.cub, SavedDeckSection.MAINBOARD))
        assertEquals(2, copies(TestCards.spark, SavedDeckSection.MAINBOARD))
        assertEquals(2, copies(TestCards.spark, SavedDeckSection.SIDEBOARD))
    }
}

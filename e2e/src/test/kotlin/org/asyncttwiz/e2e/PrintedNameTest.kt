package org.asyncttwiz.e2e

import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.performClick
import kotlinx.coroutines.runBlocking
import org.asyncttwiz.android.DeckApi
import org.asyncttwiz.android.INSPECT_ORACLE_NAME_TAG
import org.asyncttwiz.protocol.SavedDeckEntry
import org.asyncttwiz.protocol.SavedDeckSection
import org.asyncttwiz.storage.CatalogCardSearch
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Printed alternate names (owner 2026-11-15; Secret Lair drops, Universes Within): a printing that gives its card another
 * name (MTGJSON's flavorName) is imported and found by that name, shows it on the table and in Inspect with its Oracle name
 * beside it, and Automatum plays it as its Oracle card. The test card Ember Pyreling is printed as "Sparkfang the Kitten".
 */
class PrintedNameTest : E2e() {
    private val kitten = TestCards.pyreling.flavorName!!

    // LOB-150, LOB-151, SYS-084, SYS-200: a list naming the printed name imports that printing and saves it by printing; the deck
    // view's card, built from Alice's card records, is the server's, showing the printed name; search finds it by it too
    @Test fun aListImportsAPrintingByItsPrintedName() {
        launchPlayers(aliceCatalog = true)
        waitUntil("Alice's card records installed") { alice.catalog!!.state.ready }
        alice.decksShown = true
        waitUntil("Alice's menu") { alice.shows(hasTestTag("menu-DECKS")) }
        alice.tagged("menu-DECKS").performClick()
        alice.deckImport.open()
        alice.deckImport.name("E2E printed names")
        alice.deckImport.edit("Deck\n20 Mountain\n20 $kitten")
        alice.deckImport.lookup()
        waitUntil("the list looked up in Alice's card records") { alice.deckImport.state.result?.resolved == true && alice.deckImport.canSave }
        val entry = alice.deckImport.state.result!!.entries.single { it.printing.id == TestCards.pyreling.printingId }
        assertEquals(20, entry.quantity)
        alice.tagged("save-deck").performClick()
        waitUntil("the saved deck") { !alice.deckImport.state.active && alice.decks.state.detail != null }
        val saved = alice.decks.state.detail!!.saved
        val read = runBlocking { DeckApi(E2eStack.origin).use { it.read(alice.account.token, saved.deck.id) } }
        // Kept by its printing under its Oracle name: the name a saved card is found again by
        assertEquals(SavedDeckEntry(SavedDeckSection.MAINBOARD, TestCards.pyreling.printingId, "Ember Pyreling", 20),
            read.entries.single { it.printingId == TestCards.pyreling.printingId })
        val card = alice.decks.state.detail!!.definitions.getValue(TestCards.pyreling.printingId)
        assertEquals(TestCards.pyreling.definition, card, "the card data the server builds")
        assertEquals("Ember Pyreling", card.name)
        assertEquals(kitten, card.shownName)
        // The saved deck view names it by its printed name
        waitUntil("the deck view naming the card") { alice.showsText(kitten, substring = true) }

        val found = alice.catalog!!.repository.searchCards("sparkfang") as CatalogCardSearch.Page
        assertEquals(listOf("Ember Pyreling" to TestCards.pyreling.printingId), found.cards.map { it.name to it.printing.id })
    }

    // TBL-138, TBL-197, TBL-104: on both tables the card is named by its printed name, Automatum plays it as Ember Pyreling, and
    // holding it shows Inspect with its Oracle name labelled on its picture
    @Test fun theTableShowsThePrintedNameAndInspectTheOracleName() {
        startGame(TestDeck("Kittens", listOf(TestCards.mountain to 20, TestCards.pyreling to 20)),
            deal = { a, _ -> "Ember Pyreling" in hand(a) })
        bothOpen()
        bothKeep(bobAway = false)
        assertTrue(runCatching { alice.handCard(kitten) }.isSuccess, "in hand by its printed name: ${alice.screen()}")
        alice.playFromHand(kitten, this)
        // Automatum's object is the Oracle card
        waitUntil("the Pyreling on the battlefield", along = listOf(bob)) { alice.field("Ember Pyreling").isNotEmpty() && alice.inOwnMain() }
        waitUntil("Alice's table naming it by its printed name") { alice.showsOnBattlefield(kitten) }
        assertTrue(!alice.showsOnBattlefield("Ember Pyreling"), "never by its Oracle name on the table")
        waitUntil("Bob's table naming Alice's card by its printed name", along = listOf(alice)) { bob.showsOnBattlefield(kitten, alice) }

        holding(alice.battlefieldCard(kitten)) {
            assertTrue(alice.shows(hasContentDescription("Card inspection content")), alice.screen())
            assertTrue(alice.shows(hasContentDescription("Card image: $kitten")), alice.screen())
            assertTrue(alice.shows(hasTestTag(INSPECT_ORACLE_NAME_TAG)), alice.screen())
            assertTrue(alice.showsText("Oracle name: Ember Pyreling"), alice.screen())
        }
        assertTrue(!alice.shows(hasContentDescription("Card inspection content")), "closed on release")
        holding(bob.battlefieldCard(kitten, alice)) {
            assertTrue(bob.showsText("Oracle name: Ember Pyreling"), bob.screen())
        }
    }
}

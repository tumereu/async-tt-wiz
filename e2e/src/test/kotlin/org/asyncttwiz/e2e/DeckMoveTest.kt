package org.asyncttwiz.e2e

import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.performClick
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Owner 2026-11-20: decks are sorted into categories from Manage decks' list, without opening each one. */
class DeckMoveTest : E2e() {
    private val deck = TestDeck("E2E Sorted", listOf(TestCards.mountain to 40))

    // LOB-262, LOB-244: Alice's deck at the top level moves into her Battle box category from its row's ⋮ (the server holds
    // it there and the shut category counts it), then from inside the opened category back to the top level
    @Test fun aDeckMovesIntoACategoryAndBackFromItsRow() {
        val account = ServerSetup.accounts().first
        val box = ServerSetup.category(account, "Battle box")
        ServerSetup.deck(account, deck)
        launchPlayers()
        alice.decksShown = true
        waitUntil("Alice's menu") { alice.shows(hasTestTag("menu-DECKS")) }
        alice.tagged("menu-DECKS").performClick()
        waitUntil("her deck and category in the list") { alice.showsText(deck.name) && alice.showsText("Battle box") }
        assertTrue(alice.showsText("0 decks"), "the category is empty: ${alice.screen()}")

        alice.described("${deck.name} options").performClick()
        waitUntil("the deck's menu") { alice.showsText("Move to…") }
        alice.text("Move to…").performClick()
        waitUntil("the Move dialog") { alice.showsText("Move ${deck.name}") && alice.showsText("Top level") }
        // The dialog's choice, not the category row behind it
        alice.node(hasText("Battle box") and hasAnyAncestor(isSelectable())).performClick()
        waitUntil("the deck in Battle box, on the server") { ServerSetup.decks(account).single { it.name == deck.name }.category == box.id }
        waitUntil("the dialog closed and the shut category counting it") {
            !alice.showsText("Move ${deck.name}") && alice.showsText("1 deck") && !alice.showsText(deck.name)
        }

        alice.text("Battle box").performClick()
        waitUntil("the deck inside the opened category") { alice.showsText(deck.name) }
        alice.described("${deck.name} options").performClick()
        waitUntil("the deck's menu again") { alice.showsText("Move to…") }
        alice.text("Move to…").performClick()
        waitUntil("the Move dialog again") { alice.showsText("Top level") }
        alice.text("Top level").performClick()
        waitUntil("the deck at the top level, on the server") { ServerSetup.decks(account).single { it.name == deck.name }.category == null }
        waitUntil("the category empty again") { alice.showsText("0 decks") && alice.showsText(deck.name) }
        assertNull(ServerSetup.decks(account).single { it.name == deck.name }.category)
        assertEquals(1, ServerSetup.decks(account).size, "no deck was copied or lost")
        assertTrue(!alice.refusalSeen, "no refusal")
    }
}

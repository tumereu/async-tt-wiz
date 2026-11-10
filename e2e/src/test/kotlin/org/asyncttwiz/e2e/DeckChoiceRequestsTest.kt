package org.asyncttwiz.e2e

import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import org.junit.Test
import java.util.UUID
import kotlin.test.assertTrue

/**
 * Owner playtest 2026-11-06 (bug): choosing a deck in a game's lobby once showed "The service is busy. Wait a moment,
 * then retry.": the server allows an account 600 deck-service requests a minute, and the app read a saved deck's card
 * data one card per request (here, when the deck is chosen; the background Automatum support and card data runs read
 * every saved deck so). Alice chooses a deck of 21 different cards for a game with Bob: the whole choice takes a handful
 * of deck-service requests, however many different cards the deck has.
 */
class DeckChoiceRequestsTest : E2e() {
    // LOB-040: Choose deck → the deck → Use selected deck; the deck service is asked a handful of times, not once a card
    @Test fun choosingADeckTakesAHandfulOfDeckServiceRequests() {
        val (a, b) = ServerSetup.accounts()
        val name = "E2E deck choice ${UUID.randomUUID().toString().take(6)}"
        val deck = ServerSetup.deck(a, TestDeck.everyCard())
        ServerSetup.invitation(a, b, name)
        launchPlayers()
        waitUntil("Alice's menu") { alice.shows(hasTestTag("menu-GAMES")) }
        alice.tagged("menu-GAMES").performClick()
        waitUntil("the game in Alice's list") { alice.showsText(name) }
        alice.text(name).performScrollTo().performClick()
        waitUntil("Choose deck in the game's lobby") { alice.showsText("Choose deck") }

        alice.link.requests.clear()
        alice.text("Choose deck").performScrollTo().performClick()
        waitUntil("Alice's deck in Choose deck") { alice.showsText("Every card") }
        alice.text("Every card").performScrollTo().performClick()
        alice.text("Use selected deck").performClick()
        waitUntil("Alice's deck chosen, on the server") {
            alice.lobby.state.acceptedPreparation?.players?.firstOrNull { it.accountId == alice.id }?.deck?.name == deck.deck.name
        }

        val deckService = synchronized(alice.link.requests) { alice.link.requests.filter { request ->
            request.substringAfter(' ').let { it.startsWith("/v1/decks") || it.startsWith("/v1/definitions") || it.startsWith("/v1/cubes") }
        } }
        // The list, the draft decks, the deck: one request each; no card data is read (owner 2026-11-10: the phone builds
        // the cards from its records)
        assertTrue(deckService.size <= 6, "${deckService.size} deck-service requests to choose a deck: $deckService")
    }
}

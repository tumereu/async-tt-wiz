package org.asyncttwiz.e2e

import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import kotlinx.coroutines.runBlocking
import org.asyncttwiz.protocol.PodDetail
import org.asyncttwiz.protocol.SavedDeckSection
import org.junit.Test
import java.util.UUID

/**
 * Owner playtest 2026-11-11: after submitting a pod deck you can still rename it and change its cover from Prepare deck in a
 * pod game's lobby. Alice drafted with Bob, submitted, challenged him and opens Prepare deck.
 */
class PreparePodDeckNameTest : E2e() {
    private fun pod(id: String): PodDetail = runBlocking {
        org.asyncttwiz.android.PodApi(E2eStack.origin).use { it.detail(alice.account.token, id) }
    }

    /** Alice's own prepared deck as the server accepted it. */
    private fun acceptedDeck() = alice.lobby.state.acceptedPreparation?.players?.firstOrNull { it.accountId == alice.id }?.deck

    // DRF-105, DRF-166, LOB-047: the pen in Prepare deck's header renames the deck (the header shows the new name, the pod deck
    // has it); the cover thumbnail beside it lists the deck's cards, and the card chosen becomes the cover of this game's deck
    // and of the pod deck
    @Test fun aSubmittedPodDeckIsRenamedAndGivenAnotherCoverFromPrepareDeck() {
        launchPlayers()
        val name = "E2E pod game ${UUID.randomUUID().toString().take(6)}"
        val game = ServerSetup.podGame(alice.account, bob.account, name)
        val podId = checkNotNull(game.podId)
        waitUntil("Alice's menu") { alice.shows(hasTestTag("menu-GAMES")) }
        alice.tagged("menu-GAMES").performClick()
        waitUntil("the game in Alice's list") { alice.showsText(name) }
        alice.text(name).performScrollTo().performClick()
        waitUntil("Alice's pod deck in the lobby") { acceptedDeck() != null && alice.showsText("Prepare deck") }
        alice.text("Prepare deck").performScrollTo().performClick()
        waitUntil("the deck's pen and cover in Prepare deck") {
            alice.shows(hasTestTag("preparation-rename")) && alice.shows(hasTestTag("preparation-cover"))
        }

        val renamed = "Renamed in the lobby"
        alice.tagged("preparation-rename").performClick()
        waitUntil("the name dialog") { alice.showsText("Deck name") }
        alice.node(hasSetTextAction()).performTextReplacement(renamed)
        alice.text("Save name").performClick()
        waitUntil("the new name in the header and in the pod") { alice.showsText(renamed) && pod(podId).deckName == renamed }

        val deck = checkNotNull(acceptedDeck())
        val choices = deck.entries.filter { it.suppliedBasic == null && it.section != SavedDeckSection.MAYBEBOARD }.map { it.definition }.distinct()
        val other = choices.first { it != deck.cover }
        alice.tagged("preparation-cover").performClick()
        waitUntil("the cover list") { alice.shows(hasTestTag("preparation-cover-$other")) }
        alice.tagged("preparation-cover-$other").performClick()
        waitUntil("the chosen cover on this game's deck and the pod deck") { acceptedDeck()?.cover == other && pod(podId).deckCover == other }
        kotlin.test.assertTrue(alice.showsText(renamed), "the name stays: ${alice.screen()}")
    }
}

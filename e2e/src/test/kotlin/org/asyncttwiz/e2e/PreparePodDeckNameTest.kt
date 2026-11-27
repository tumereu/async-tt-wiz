package org.asyncttwiz.e2e

import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import kotlinx.coroutines.runBlocking
import org.asyncttwiz.protocol.*
import org.junit.Test
import java.util.UUID

/**
 * Owner playtest 2026-11-11: after submitting a pod deck you can still rename it and change its cover from Prepare deck in a
 * pod game's lobby. Alice drafted with Bob, submitted, challenged him and opens Prepare deck.
 */
class PreparePodDeckNameTest : E2e() {
    private fun pod(id: String): PodDetail = runBlocking {
        org.asyncttwiz.android.PodApi(E2eStack.origin, E2eHttp.client()).use { it.detail(alice.account.token, id) }
    }

    /** Alice's own prepared deck as the server accepted it. */
    private fun acceptedDeck() = alice.lobby.state.acceptedPreparation?.players?.firstOrNull { it.accountId == alice.id }?.deck

    // DRF-105, DRF-166, DRF-229, LOB-047, LOB-226: the pen in Prepare deck's header renames the deck (the header shows the new name, the pod deck
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

    // DRF-039, DRF-105 (owner, 2026-11-23): Alice and Bob made their Arena picks and submitted while Carol still drafted, and Bob
    // accepted Alice's challenge; Alice cancels the draft, so the game is orphaned from the pod. Renaming her deck from
    // Prepare deck in its lobby renames that game's deck alone, not the pod's (its next draft's) deck
    @Test fun anOrphanedGamesDeckIsRenamedAloneFromPrepareDeck() {
        launchPlayers()
        val carol = ServerSetup.third()
        val name = "E2E orphaned deck ${UUID.randomUUID().toString().take(6)}"
        val podId = ServerSetup.setupPod(alice.account, name, cards = listOf(TestCards.spark, TestCards.cub, TestCards.herald,
            TestCards.hound, TestCards.stride, TestCards.glimmer, TestCards.glint, TestCards.drowsy),
            settings = DraftSettings.arena().copy(packsPerPlayer = 3, cardsPerPack = 2, keep = 1, ignoreRarity = true, reuseCards = true))
        ServerSetup.invitePod(alice.account, carol, podId)
        runBlocking { org.asyncttwiz.android.PodApi(E2eStack.origin, E2eHttp.client()).use { it.accept(carol.token, podId, PodRequest(UUID.randomUUID().toString())) } }
        ServerSetup.joinAndStart(alice.account, bob.account, podId)
        runBlocking {
            org.asyncttwiz.android.PodApi(E2eStack.origin, E2eHttp.client()).use { api ->
                for (player in listOf(alice.account, bob.account)) {
                    repeat(3) {
                        val detail = api.detail(player.token, podId); val head = checkNotNull(detail.draft?.head)
                        api.pick(player.token, podId, PickPod(UUID.randomUUID().toString(), detail.draftAttemptId!!, head.id,
                            head.cards.take(detail.settings.keepAt(head.pick - 1)).map { it.id }))
                    }
                    api.basics(player.token, podId, SetPodBasics(UUID.randomUUID().toString(), api.detail(player.token, podId).draftAttemptId!!,
                        PodDeckBasics(plains = 40)))
                }
            }
        }
        for (player in listOf(alice.account, bob.account)) ServerSetup.submitPodDeck(player, podId)
        val game = ServerSetup.challenge(alice.account, bob.account, podId, "$name game")
        ServerSetup.attempt(bob.account, game, GameAction.AcceptInvitation)
        runBlocking { org.asyncttwiz.android.PodApi(E2eStack.origin, E2eHttp.client()).use { api ->
            api.cancel(alice.account.token, podId, PodDraftRequest(UUID.randomUUID().toString(), api.detail(alice.account.token, podId).draftAttemptId!!))
        } }
        val podDeckName = pod(podId).deckName

        waitUntil("Alice's menu") { alice.shows(hasTestTag("menu-GAMES")) }
        alice.lobby.refresh()
        alice.tagged("menu-GAMES").performClick()
        waitUntil("the game in Alice's list") { alice.showsText("$name game") }
        alice.text("$name game").performScrollTo().performClick()
        waitUntil("Alice's orphaned game in its lobby") {
            alice.lobby.state.detail?.podOrphaned == true && acceptedDeck() != null && alice.showsText("Prepare deck")
        }
        alice.text("Prepare deck").performScrollTo().performClick()
        waitUntil("the deck's pen in Prepare deck") { alice.shows(hasTestTag("preparation-rename")) }
        val renamed = "The cancelled draft's deck"
        alice.tagged("preparation-rename").performClick()
        waitUntil("the name dialog") { alice.showsText("Deck name") }
        alice.node(hasSetTextAction()).performTextReplacement(renamed)
        alice.text("Save name").performClick()
        waitUntil("the new name on this game's deck") {
            alice.showsText(renamed) && ServerSetup.head(alice.account, game.id).ownPodDeckName == renamed
        }
        kotlin.test.assertEquals(podDeckName, pod(podId).deckName, "the pod's deck keeps its name")
    }
}

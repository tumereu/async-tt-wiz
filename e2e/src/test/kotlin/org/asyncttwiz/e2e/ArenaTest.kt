package org.asyncttwiz.e2e

import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isPopup
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import kotlinx.coroutines.runBlocking
import org.asyncttwiz.android.DeckApi
import org.asyncttwiz.android.ShellDestination
import org.asyncttwiz.protocol.*
import org.junit.Test
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Arena (owner, 2026-11-20): each player drafts alone from offers of their own, takes some of each offer and the rest is
 * gone; nothing passes between players.
 */
class ArenaTest : E2e() {
    // DRF-281, DRF-287, DRF-288 (owner, 2026-11-21): Alice sets "Initial rares" to 2 in her Arena pod's setup on her phone; the
    // server keeps it, the summary counts the picks, and in the draft her first two picks are rare picks, the third common
    @Test fun initialRaresSetInSetupReachTheDraft() {
        launchPlayers()
        val name = "E2E Arena rares ${UUID.randomUUID().toString().take(6)}"
        val pod = ServerSetup.setupPod(alice.account, name, cards = listOf(TestCards.spark, TestCards.cub, TestCards.herald, TestCards.hound,
            TestCards.stride, TestCards.glimmer, TestCards.glint, TestCards.drowsy),
            settings = DraftSettings.arena().copy(packsPerPlayer = 3, cardsPerPack = 2, keep = 1))
        alice.auth.navigate(ShellDestination.PODS)
        alice.pods.open(pod)
        waitUntil("Alice's Arena setup with Initial rares 1") {
            alice.shows(hasTestTag("pod-setting-initial_rares") and hasText("1")) && alice.showsText("Initial rares")
        }
        val field = hasSetTextAction() and hasAnyAncestor(isDialog())
        alice.tagged("pod-setting-initial_rares").performScrollTo().performClick()
        waitUntil("the Initial rares dialog") { alice.shows(field) && alice.showsText("0–120") }
        alice.node(field).performTextReplacement("2")
        alice.node(hasClickAction() and hasAnyDescendant(hasText("Save")) and hasAnyAncestor(isDialog())).performClick()
        waitUntil("the setting on the server") {
            alice.pods.state.detail?.settings?.initialRares == 2 && alice.pods.state.setup == null && alice.pods.state.pending == 0 &&
                ServerSetup.pod(alice.account, pod).settings.initialRares == 2
        }
        waitUntil("the summary counts two rare picks") { alice.showsText("2 rare, 1 common picks", substring = true) }

        ServerSetup.joinAndStart(alice.account, bob.account, pod)
        alice.pods.refresh()
        for ((pick, rarity) in listOf(1 to "Rare", 2 to "Rare", 3 to "Common")) {
            waitUntil("Alice's pick $pick line") {
                alice.pods.state.detail?.draft?.head != null &&
                    alice.shows(hasTestTag("pod-pick-orientation") and hasText("Pick $pick of 3 · $rarity · take 1"))
            }
            val card = alice.pods.state.detail!!.draft!!.head!!.cards.first()
            alice.tagged("pod-pick-card-${card.id}").performScrollTo().performClick()
            waitUntil("the card menu") { alice.shows(hasText("Keep") and hasAnyAncestor(isPopup())) }
            alice.node(hasText("Keep") and hasAnyAncestor(isPopup())).performClick()
            alice.tagged("pod-pick-confirm").performClick()
            waitUntil("pick $pick on the server") { ServerSetup.pod(alice.account, pod).draft!!.pool.size == pick }
        }
    }

    // DRF-280, DRF-284, DRF-287, DRF-288, DRF-289, DRF-290: a two-player Arena pod from a cube without Reuse cards, 3 picks of
    // offers of 3 taking 1. Bob's invitation reads the format; both pick on their phones, each from their own offers, with the
    // pick line; neither ever sees the other's offers; a card Alice took never comes back to her; her history lists her picks;
    // both reach Build
    @Test fun twoPlayersDraftTheirOwnOffersAndReachBuild() {
        launchPlayers()
        val name = "E2E Arena ${UUID.randomUUID().toString().take(6)}"
        val cards = listOf(TestCards.spark, TestCards.cub, TestCards.herald, TestCards.hound, TestCards.stride, TestCards.glimmer,
            TestCards.glint, TestCards.drowsy)
        val settings = DraftSettings.arena().copy(packsPerPlayer = 3, cardsPerPack = 3, keep = 1, ignoreRarity = true)
        val pod = runBlocking {
            fun id() = UUID.randomUUID().toString()
            val cube = DeckApi(E2eStack.origin, E2eHttp.client()).use { api ->
                api.create(alice.account.token, CreateCube(id(), "E2E Arena cube", cards.map { CardListEntry(SavedDeckSection.MAINBOARD, it.printingId, 1) }))
            }
            org.asyncttwiz.android.PodApi(E2eStack.origin, E2eHttp.client()).use { api ->
                api.create(alice.account.token, CreatePod(id(), name, cube.cube.id, DraftMode.ARENA, settings = settings)).id.also { pod ->
                    ServerSetup.contact(alice.account, bob.account)
                    api.invite(alice.account.token, pod, InviteToPod(id(), bob.account.id))
                }
            }
        }
        // Bob's invitation tells the format; he accepts on his phone
        bob.auth.navigate(ShellDestination.PODS)
        bob.pods.refresh()
        waitUntil("Bob's invitation with the format") {
            bob.shows(hasTestTag("pod-invitation-format") and hasText("Arena: 3 picks, 3 cards, take 1"))
        }
        bob.node(hasClickAction() and hasAnyDescendant(hasText("Accept"))).performClick()
        waitUntil("Bob a member") { ServerSetup.pod(alice.account, pod).members.size == 2 }
        runBlocking {
            org.asyncttwiz.android.PodApi(E2eStack.origin, E2eHttp.client()).use { api ->
                api.start(alice.account.token, pod, PodRequest(UUID.randomUUID().toString()))
                    .also { check(it.phase == PodPhase.DRAFTING) { "the draft didn't start: $it" } }
            }
        }

        val players = listOf(alice, bob)
        for (player in players) {
            player.auth.navigate(ShellDestination.PODS)
            player.pods.open(pod)
            waitUntil("${player.name}'s first offer") { player.pods.state.detail?.takeIf { it.id == pod }?.draft?.head?.cards?.size == 3 }
        }
        val offered = players.associateWith { mutableListOf<String>() }
        val taken = players.associateWith { mutableListOf<DraftCopy>() }
        for (pick in 1..3) for (player in players) {
            val other = players.first { it != player }
            waitUntil("${player.name}'s pick $pick line") {
                player.pods.state.detail?.draft?.head != null && player.shows(hasTestTag("pod-pick-orientation") and hasText("Pick $pick of 3 · take 1"))
            }
            val head = player.pods.state.detail!!.draft!!.head!!
            // The offer is the player's own: none of the other's offers' cards ever shows on this phone or reaches it
            assertTrue(head.cards.none { it.id in offered.getValue(other) }, "${player.name} got the other's cards")
            offered.getValue(player) += head.cards.map { it.id }
            // DRF-284: without Reuse cards a card the player took is never offered to them again
            assertTrue(head.cards.none { card -> taken.getValue(player).any { it.definition == card.definition } },
                "${player.name} was offered a card they took: ${head.cards}")
            val card = head.cards.first()
            player.tagged("pod-pick-card-${card.id}").performScrollTo().performClick()
            waitUntil("the card menu") { player.shows(hasText("Keep") and hasAnyAncestor(isPopup())) }
            player.node(hasText("Keep") and hasAnyAncestor(isPopup())).performClick()
            player.tagged("pod-pick-confirm").performClick()
            taken.getValue(player) += card
            waitUntil("${player.name}'s pick $pick on the server") {
                ServerSetup.pod(player.account, pod).draft!!.pool.map { it.id } == taken.getValue(player).map { it.id }
            }
        }
        for (player in players) {
            val other = players.first { it != player }
            val seen = ServerSetup.pod(player.account, pod)
            val shown = seen.draft!!.pool.map { it.id } + seen.draft!!.queuedPacks.flatMap { pack -> pack.cards.map { it.id } }
            assertTrue(shown.none { it in offered.getValue(other) }, "${player.name} can see the other's offers")
            assertEquals(3, taken.getValue(player).map { it.definition }.distinct().size)
        }
        waitUntil("the draft over") { ServerSetup.pod(alice.account, pod).phase == PodPhase.DRAFTED }

        // Alice's history: her three picks, each under its pick, her keep marked
        alice.pods.refresh()
        waitUntil("Alice's History button") { alice.shows(hasTestTag("pod-history")) }
        alice.tagged("pod-history").performClick()
        waitUntil("Alice's Arena history") {
            alice.pods.history?.picks?.map { it.kept.single() } == taken.getValue(alice).map { it.id } &&
                alice.showsText("Pick 1") && !alice.shows(hasTestTag("pod-history-returning"))
        }
        alice.described("Close draft history").performClick()

        // Both reach Build with their three cards
        for (player in players) {
            player.pods.refresh()
            waitUntil("${player.name}'s Build") {
                player.pods.state.detail?.phase == PodPhase.DRAFTED && player.shows(hasText("Build")) && player.pods.state.detail?.build?.copies?.size == 3
            }
            player.text("Build").performClick()
            waitUntil("${player.name}'s Build screen") { player.shows(hasTestTag("pod-build-basics")) }
        }
    }
}

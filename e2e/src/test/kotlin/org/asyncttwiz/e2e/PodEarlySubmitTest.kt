package org.asyncttwiz.e2e

import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasAnyChild
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.isEnabled
import androidx.compose.ui.test.isNotEnabled
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import kotlinx.coroutines.runBlocking
import org.asyncttwiz.android.PodTab
import org.asyncttwiz.android.ShellDestination
import org.asyncttwiz.protocol.*
import org.junit.Test
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Problem report MT74XQ (owner, 2026-11-21): in a two-member Arena pod a player finished all their picks and built a valid
 * deck, but Submit stayed greyed with no reason because the other member hadn't picked yet. Now a member submits as soon as
 * their own draft is done, in every mode, and can challenge any member, also one still drafting.
 */
class PodEarlySubmitTest : E2e() {
    private fun id() = UUID.randomUUID().toString()

    /** [player]'s next pick over the API: the first cards of their head pack. */
    private fun pick(player: E2ePlayer, pod: String) = runBlocking {
        org.asyncttwiz.android.PodApi(E2eStack.origin).use { api ->
            val detail = api.detail(player.account.token, pod)
            val head = checkNotNull(detail.draft?.head) { "${player.name} has no pack: $detail" }
            api.pick(player.account.token, pod, PickPod(id(), detail.draftAttemptId!!, head.id,
                head.cards.take(detail.settings.keepAt(head.pick - 1)).map { it.id }))
        }
    }

    /** [player]'s Main gets 40 Plains over the API, so their deck is valid. */
    private fun basics(player: E2ePlayer, pod: String) = runBlocking {
        org.asyncttwiz.android.PodApi(E2eStack.origin).use { api ->
            api.basics(player.account.token, pod, SetPodBasics(id(), api.detail(player.account.token, pod).draftAttemptId!!,
                PodDeckBasics(plains = 40)))
        }
    }

    /** [player] opens [pod], which opens on Build, and submits there: Submit enabled, confirmed. */
    private fun submitOnPhone(player: E2ePlayer, pod: String) {
        player.auth.navigate(ShellDestination.PODS)
        player.pods.open(pod)
        val count = { player.pods.state.detail?.takeIf { it.id == pod }?.build?.let { it.copies.size + it.basics.total } }
        waitUntil("${player.name}'s pod open on Build with Submit enabled") {
            val shown = count() ?: return@waitUntil false
            player.pods.state.tab == PodTab.BUILD && !player.shows(hasTestTag("pod-build-submit-wait")) &&
                player.shows(hasClickAction() and hasAnyDescendant(hasText("Submit deck ($shown)")) and isEnabled())
        }
        player.node(hasClickAction() and hasAnyDescendant(hasText("Submit deck (${count()})"))).performClick()
        val confirm = hasClickAction() and hasAnyDescendant(hasText("Submit")) and hasAnyAncestor(isDialog())
        waitUntil("the Submit deck? dialog") { player.shows(confirm) }
        player.node(confirm).performClick()
        waitUntil("${player.name}'s deck submitted on the server") { ServerSetup.pod(player.account, pod).submitted }
    }

    // MT74XQ, DRF-119, DRF-302, DRF-303, DRF-121, DRF-122: a two-member Arena pod; Alice makes all her picks while Bob has made
    // none. Bob's Build says why his Submit is locked; Alice submits on her phone while the pod still drafts, then challenges
    // Bob from the Games tab; Bob, still drafting, sees the challenge at once with Accept greyed, and accepts it once his own
    // picks are done and his deck submitted
    @Test fun anArenaMemberSubmitsAndChallengesWhileTheOtherStillDrafts() {
        launchPlayers()
        val name = "E2E early ${UUID.randomUUID().toString().take(6)}"
        val pod = ServerSetup.setupPod(alice.account, name, cards = listOf(TestCards.spark, TestCards.cub, TestCards.herald,
            TestCards.hound, TestCards.stride, TestCards.glimmer, TestCards.glint, TestCards.drowsy),
            settings = DraftSettings.arena().copy(packsPerPlayer = 3, cardsPerPack = 2, keep = 1, ignoreRarity = true))
        ServerSetup.joinAndStart(alice.account, bob.account, pod)
        repeat(3) { pick(alice, pod) }
        basics(alice, pod)
        val drafting = ServerSetup.pod(alice.account, pod)
        assertEquals(PodPhase.DRAFTING, drafting.phase)
        assertNull(drafting.draftWait)
        assertEquals(0, drafting.members.single { it.account.accountId == bob.id }.picksMade)

        // Bob's Build: his Submit is locked, and says why
        bob.auth.navigate(ShellDestination.PODS)
        bob.pods.open(pod, PodTab.BUILD)
        waitUntil("Bob's Build saying when Submit opens") {
            bob.pods.state.tab == PodTab.BUILD &&
                bob.shows(hasTestTag("pod-build-submit-wait") and hasText("Submit opens after your last pick")) &&
                bob.shows(hasClickAction() and hasAnyDescendant(hasText("Submit deck", substring = true)) and isNotEnabled())
        }

        submitOnPhone(alice, pod)
        assertEquals(PodPhase.DRAFTING, ServerSetup.pod(alice.account, pod).phase, "the pod still drafts")

        // Alice challenges Bob, still drafting, from the Games tab
        waitUntil("Alice's Games tab with Bob drafting and the Challenge beside him") {
            alice.pods.state.tab == PodTab.GAMES && alice.showsText("drafting") && alice.showsText("deck submitted") &&
                alice.shows(hasContentDescription("Challenge") and isEnabled())
        }
        alice.node(hasContentDescription("Challenge") and isEnabled()).performScrollTo().performClick()
        val podGames = { E2eStack.query("SELECT id FROM game WHERE pod_id = '$pod'") }
        waitUntil("Alice's challenge made") { podGames().size == 1 }
        val game = podGames().single()
        assertTrue(ServerSetup.head(alice.account, game).summary.invitationHeld)

        // Bob sees it at once in the pod's Games tab; he can't accept while he still drafts
        bob.pods.open(pod, PodTab.GAMES)
        bob.lobby.refresh()
        waitUntil("the challenge in Bob's Games tab, Accept greyed") {
            bob.pods.state.tab == PodTab.GAMES && bob.shows(hasTestTag("pod-game-$game")) &&
                bob.shows(hasText("Challenged you · submit your deck to accept")) &&
                bob.shows(hasAnyChild(hasContentDescription("Accept invitation")) and isNotEnabled())
        }

        // Bob's own picks; he submits, and the challenge can be accepted
        repeat(3) { pick(bob, pod) }
        basics(bob, pod)
        ServerSetup.submitPodDeck(bob.account, pod)
        bob.pods.refresh(); bob.lobby.refresh()
        bob.pods.open(pod, PodTab.GAMES)
        val accept = hasAnyChild(hasContentDescription("Accept invitation")) and isEnabled()
        waitUntil("the challenge acceptable on Bob's phone") { bob.pods.state.tab == PodTab.GAMES && bob.shows(accept) }
        bob.node(accept).performScrollTo().performClick()
        waitUntil("Bob accepted Alice's challenge") {
            ServerSetup.head(bob.account, game).summary.invitationState == InvitationState.ACCEPTED
        }
    }

    // DRF-119, DRF-302: a Booster pod whose last card of a pack is thrown away (discard at 1): Alice's two picks end her draft
    // while Bob still has one, and she submits on her phone at once
    @Test fun aBoosterMemberWhoseDraftIsDoneSubmitsWhileTheOtherStillPicks() {
        launchPlayers()
        val name = "E2E early booster ${UUID.randomUUID().toString().take(6)}"
        val pod = ServerSetup.draftingPod(alice.account, bob.account, name,
            settings = DraftSettings(packsPerPlayer = 1, cardsPerPack = 3, discardRemainingAt = 1))
        pick(alice, pod); pick(bob, pod); pick(alice, pod)
        basics(alice, pod)
        assertNull(ServerSetup.pod(alice.account, pod).draftWait)
        assertEquals(PodDraftWait.Picks, ServerSetup.pod(bob.account, pod).draftWait)

        submitOnPhone(alice, pod)
        val after = ServerSetup.pod(bob.account, pod)
        assertEquals(PodPhase.DRAFTING, after.phase)
        assertEquals(listOf(true, false), listOf(alice.id, bob.id).map { who -> after.members.single { it.account.accountId == who }.submitted })
    }
}

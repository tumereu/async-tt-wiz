package org.asyncttwiz.e2e

import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelected
import androidx.compose.ui.test.performClick
import kotlinx.coroutines.runBlocking
import org.asyncttwiz.android.ShellDestination
import org.asyncttwiz.protocol.*
import org.junit.Test
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Stats' Drafts tab (owner, 2026-11-26: "deleting a pod should not take away a player's opportunity to learn"). */
class StatsDraftsTest : E2e() {
    // LOB-279, LOB-280, LOB-281, LOB-182, DRF-315, DRF-209: Alice drafts Test Set with a bot to its end and deletes the pod; Stats'
    // Drafts tab still lists the draft, and tapping it opens its draft history with the card ratings, By rating and the packs chip
    @Test fun aDeletedPodsDraftOpensFromStats() {
        launchPlayers(aliceCatalog = true)
        waitUntil("Alice's card records installed") { alice.catalog!!.state.ready }
        val name = "E2E kept draft ${UUID.randomUUID().toString().take(6)}"
        val pod = runBlocking {
            org.asyncttwiz.android.PodApi(E2eStack.origin, E2eHttp.client()).use { api ->
                api.create(alice.account.token, CreatePod(UUID.randomUUID().toString(), name, setCode = "TST",
                    settings = DraftSettings(packsPerPlayer = 2, cardsPerPack = 4, bots = 1))).id
            }
        }
        ServerSetup.startPod(alice.account, pod)
        ServerSetup.pickUntil(listOf(alice.account), pod) { ServerSetup.pod(alice.account, pod).phase != PodPhase.DRAFTING }
        val picks = runBlocking {
            org.asyncttwiz.android.PodApi(E2eStack.origin, E2eHttp.client()).use { api -> api.history(alice.account.token, pod).picks }
        }
        runBlocking {
            org.asyncttwiz.android.PodApi(E2eStack.origin, E2eHttp.client()).use { api -> api.destroy(alice.account.token, pod, PodRequest(UUID.randomUUID().toString())) }
        }
        assertEquals(0, E2eStack.query("select count(*) from pod where id = '$pod'").single().toInt(), "the pod is gone")

        alice.auth.navigate(ShellDestination.MENU)
        waitUntil("Alice's menu") { alice.shows(hasTestTag("menu-STATS")) }
        alice.tagged("menu-STATS").performClick()
        waitUntil("Alice's Stats") { alice.shows(hasTestTag("stats-drafts-tab")) }
        alice.tagged("stats-drafts-tab").performClick()
        waitUntil("the deleted pod's draft in Drafts") { alice.showsText(name) && alice.showsText("Test Set · ", substring = true) }
        val kept = alice.lobby.history.drafts.value!!.single { it.podName == name }
        assertEquals(pod, kept.podId)
        alice.tagged("stats-draft-${kept.id}").performClick()
        waitUntil("its draft history with the ratings") {
            alice.shows(hasTestTag("pod-history-dialog")) && alice.lobby.history.draftRecords[kept.id]?.value != null &&
                alice.shows(hasTestTag("pod-ratings-citation")) && alice.shows(hasTestTag("pod-history-by-rating")) &&
                alice.showsText("Pack 1 · Pick 1")
        }
        assertEquals(picks, alice.lobby.history.draftRecords.getValue(kept.id).value!!.history.picks, "what the pod's history showed")
        // A rated card's chip, as in the pod's own dialog
        waitUntil("a rated card's win rate") { alice.shows(hasContentDescription("rated ", substring = true)) }
        // The packs chip goes round: All packs, then Returning
        assertTrue(alice.shows(hasTestTag("pod-history-returning") and hasAnyDescendant(hasText("All packs"))), alice.screen())
        alice.tagged("pod-history-returning").performClick()
        waitUntil("Returning") { alice.shows(hasTestTag("pod-history-returning") and hasAnyDescendant(hasText("Returning")) and isSelected()) }
        alice.tagged("pod-history-returning").performClick()
        waitUntil("New") { alice.shows(hasTestTag("pod-history-returning") and hasAnyDescendant(hasText("New")) and isSelected()) }
        alice.tagged("pod-history-returning").performClick()
        waitUntil("All packs again") { alice.shows(hasTestTag("pod-history-returning") and hasAnyDescendant(hasText("All packs"))) }
        alice.node(hasContentDescription("Close draft history")).performClick()
        waitUntil("back at the Drafts tab") { !alice.shows(hasTestTag("pod-history-dialog")) && alice.showsText(name) }
    }

    // LOB-279, LOB-281, DRF-160, DRF-315, DRF-316 (owner, 2026-11-27): Alice drafts a Test Set Rotisserie with a bot to its end and
    // deletes the pod; Stats' Drafts tab lists it too, and tapping it opens its Picks: the bot's chip and picks as the pod's Picks
    // showed them, with the card ratings and By rating
    @Test fun aDeletedRotisseriePodsPicksOpenFromStats() {
        launchPlayers(aliceCatalog = true)
        waitUntil("Alice's card records installed") { alice.catalog!!.state.ready }
        val name = "E2E kept rotisserie ${UUID.randomUUID().toString().take(6)}"
        val pod = runBlocking {
            org.asyncttwiz.android.PodApi(E2eStack.origin, E2eHttp.client()).use { api ->
                api.create(alice.account.token, CreatePod(UUID.randomUUID().toString(), name, mode = DraftMode.ROTISSERIE, setCode = "TST",
                    settings = DraftSettings.rotisserie().copy(cardsPerPlayer = 5, bots = 1))).id
            }
        }
        ServerSetup.startPod(alice.account, pod)
        val picks = runBlocking {
            org.asyncttwiz.android.PodApi(E2eStack.origin, E2eHttp.client()).use { api ->
                // Alice takes the first card left at each of her turns; the bot moves on the server at once (DRF-298)
                var detail = api.detail(alice.account.token, pod)
                while (detail.phase == PodPhase.DRAFTING) {
                    val limited = detail.limited!!
                    api.turn(alice.account.token, pod, PodTurn(UUID.randomUUID().toString(), detail.draftAttemptId!!, LimitedAction.TAKE_CARD,
                        limited.turn, card = limited.board.first { it.pickedBy == null }.copy.id))
                    detail = api.detail(alice.account.token, pod)
                }
                api.detail(alice.account.token, pod).limited!!.picks
            }
        }
        val bots = picks.filter { it.player == PodBots.id(1) }.asReversed().flatMap { it.cards }
        assertEquals(5, bots.size, "the bot's five picks")
        runBlocking {
            org.asyncttwiz.android.PodApi(E2eStack.origin, E2eHttp.client()).use { api -> api.destroy(alice.account.token, pod, PodRequest(UUID.randomUUID().toString())) }
        }

        alice.auth.navigate(ShellDestination.MENU)
        waitUntil("Alice's menu") { alice.shows(hasTestTag("menu-STATS")) }
        alice.tagged("menu-STATS").performClick()
        waitUntil("Alice's Stats") { alice.shows(hasTestTag("stats-drafts-tab")) }
        alice.tagged("stats-drafts-tab").performClick()
        waitUntil("the deleted Rotisserie in Drafts") { alice.showsText(name) }
        val kept = alice.lobby.history.drafts.value!!.single { it.podName == name }
        alice.tagged("stats-draft-${kept.id}").performClick()
        waitUntil("its Picks with the ratings") {
            alice.lobby.history.draftRecords[kept.id]?.value != null && alice.showsText("Picks") && alice.showsText("Bot 1 (5)") &&
                alice.shows(hasTestTag("pod-ratings-citation")) && alice.shows(hasTestTag("pod-history-by-rating")) &&
                alice.shows(hasTestTag("pod-picks-${bots.first()!!.id}"))
        }
        assertTrue(!alice.shows(hasTestTag("pod-history-dialog")), "its Picks, not a draft history")
        // A rated card's chip, as in the pod's own Picks ("58.0%")
        waitUntil("a rated card's win rate") { alice.shows(hasText("%", substring = true)) }
        alice.node(hasContentDescription("Close picks")).performClick()
        waitUntil("back at the Drafts tab") { !alice.showsText("Bot 1 (5)") && alice.showsText(name) }
    }
}

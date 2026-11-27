package org.asyncttwiz.e2e

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
            org.asyncttwiz.android.PodApi(E2eStack.origin).use { api ->
                api.create(alice.account.token, CreatePod(UUID.randomUUID().toString(), name, setCode = "TST",
                    settings = DraftSettings(packsPerPlayer = 2, cardsPerPack = 4, bots = 1))).id
            }
        }
        ServerSetup.startPod(alice.account, pod)
        ServerSetup.pickUntil(listOf(alice.account), pod) { ServerSetup.pod(alice.account, pod).phase != PodPhase.DRAFTING }
        val picks = runBlocking {
            org.asyncttwiz.android.PodApi(E2eStack.origin).use { api -> api.history(alice.account.token, pod).picks }
        }
        runBlocking {
            org.asyncttwiz.android.PodApi(E2eStack.origin).use { api -> api.destroy(alice.account.token, pod, PodRequest(UUID.randomUUID().toString())) }
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
        assertTrue(alice.shows(hasTestTag("pod-history-returning") and hasText("All packs")), alice.screen())
        alice.tagged("pod-history-returning").performClick()
        waitUntil("Returning") { alice.shows(hasTestTag("pod-history-returning") and hasText("Returning") and isSelected()) }
        alice.tagged("pod-history-returning").performClick()
        waitUntil("New") { alice.shows(hasTestTag("pod-history-returning") and hasText("New") and isSelected()) }
        alice.tagged("pod-history-returning").performClick()
        waitUntil("All packs again") { alice.shows(hasTestTag("pod-history-returning") and hasText("All packs")) }
        alice.node(hasContentDescription("Close draft history")).performClick()
        waitUntil("back at the Drafts tab") { !alice.shows(hasTestTag("pod-history-dialog")) && alice.showsText(name) }
    }
}

package org.asyncttwiz.e2e

import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import kotlinx.coroutines.runBlocking
import org.asyncttwiz.android.PodTab
import org.asyncttwiz.android.ShellDestination
import org.asyncttwiz.protocol.*
import org.junit.Test
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Owner, 2026-11-14 (DRF-152): Rotisserie has separate settings for the pool per player and the picks per player. */
class RotisseriePoolTest : E2e() {
    // DRF-152, DRF-022, DRF-023, DRF-194, DRF-153: in pod setup Alice makes a Rotisserie of 5 picks each from a pool of 6 each; the
    // server keeps both; the draft lays out 12 cards for the two of them, takes 10 turns and ends with 2 cards left untaken
    @Test fun thePoolAndThePicksPerPlayerAreSetApart() {
        launchPlayers()
        val name = "E2E Rotisserie pool ${UUID.randomUUID().toString().take(6)}"
        val pod = ServerSetup.setupPod(alice.account, name)
        alice.auth.navigate(ShellDestination.PODS)
        alice.pods.open(pod)
        waitUntil("Alice's pod setup") { alice.shows(hasTestTag("pod-mode")) }
        alice.tagged("pod-mode").performScrollTo().performClick()
        waitUntil("the modes") { alice.showsText("Rotisserie", substring = true) }
        alice.text("Rotisserie · 2–8 players").performClick()
        waitUntil("Rotisserie's tiles") { alice.showsText("Pool each") && alice.showsText("Picks each") }
        val field = hasSetTextAction() and hasAnyAncestor(isDialog())
        fun set(tile: String, value: Int) {
            alice.text(tile).performScrollTo().performClick()
            waitUntil("the $tile dialog") { alice.shows(field) }
            alice.node(field).performTextReplacement(value.toString())
            alice.node(hasClickAction() and hasAnyDescendant(hasText("Save")) and hasAnyAncestor(isDialog())).performClick()
            waitUntil("the $tile dialog closed") { !alice.shows(field) }
        }
        // The picks first: the pool can't be smaller than them (45 each by default)
        set("Picks each", 5)
        set("Pool each", 6)
        waitUntil("both saved") {
            alice.pods.state.setup == null && alice.pods.state.pending == 0 &&
                alice.pods.state.detail?.settings?.let { it.cardsPerPlayer == 5 && it.poolPerPlayer == 6 } == true
        }
        val saved = ServerSetup.pod(alice.account, pod).settings
        assertEquals(listOf(DraftMode.ROTISSERIE, 5, 6), listOf(saved.mode, saved.cardsPerPlayer, saved.poolPerPlayer), "the server keeps both")

        ServerSetup.joinAndStart(alice.account, bob.account, pod)
        alice.pods.refresh()
        waitUntil("Alice's Rotisserie pool of 12") {
            alice.pods.state.detail?.limited?.board?.size == 12 && alice.showsText("Pick 1 of 10", substring = true)
        }
        val attempt = alice.pods.state.detail!!.draftAttemptId!!
        runBlocking {
            org.asyncttwiz.android.PodApi(E2eStack.origin, E2eHttp.client()).use { api ->
                // Each turn's answer is the pod as it stands after it: no other reads (the server limits how often it is asked)
                var view = api.detail(alice.account.token, pod).limited!!
                repeat(10) {
                    val player = listOf(alice, bob).single { it.id == view.currentPlayer }
                    val card = view.board.first { it.pickedBy == null }.copy.id
                    view = api.turn(player.account.token, pod, PodTurn(UUID.randomUUID().toString(), attempt, LimitedAction.TAKE_CARD, view.turn, card)).limited!!
                }
            }
        }
        val done = ServerSetup.pod(alice.account, pod)
        assertEquals(PodPhase.DRAFTED, done.phase)
        assertEquals(2, done.limited!!.board.count { it.pickedBy == null }, "two cards nobody picked")
        assertTrue(done.limited!!.pool.size == 5)
        alice.pods.refresh()
        waitUntil("Alice building") { alice.pods.state.detail?.phase == PodPhase.DRAFTED }
    }
}

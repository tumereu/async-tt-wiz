package org.asyncttwiz.e2e

import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.isEnabled
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import kotlinx.coroutines.runBlocking
import org.asyncttwiz.protocol.*
import org.junit.Test
import java.util.UUID

/** Rotisserie pick queue (DRF-164): queued cards are taken when your turn comes; a queued card taken by another clears it. */
class RotisserieQueueTest : E2e() {
    // DRF-164, DRF-153: the waiting player queues four cards on the phone; when their two picks in a row come, the first
    // two queued cards are taken at once and the rest stays queued; the other player then takes a queued card, which
    // clears the queue with a note, and the next turn waits for the queue's owner
    @Test fun queuedCardsAreTakenWhenTheTurnComesAndATakenOneClearsTheQueue() {
        launchPlayers()
        val name = "E2E Rotisserie ${UUID.randomUUID().toString().take(6)}"
        val pod = ServerSetup.draftingPod(alice.account, bob.account, name,
            settings = DraftSettings.rotisserie().copy(cardsPerPlayer = 5))
        for (player in listOf(alice, bob)) {
            waitUntil("${player.name}'s menu") { player.shows(hasTestTag("menu-PODS")) }
            player.tagged("menu-PODS").performClick()
            waitUntil("the pod in ${player.name}'s list") { player.showsText(name) }
            player.text(name).performScrollTo().performClick()
            waitUntil("${player.name}'s Rotisserie pool") { player.pods.state.detail?.takeIf { it.id == pod }?.limited?.board?.size == 10 }
        }
        val seating = alice.pods.state.detail!!.limited!!.seating
        val x = listOf(alice, bob).single { it.id == seating[0] }
        val y = listOf(alice, bob).single { it.id == seating[1] }
        val board = x.pods.state.detail!!.limited!!.board.map { it.copy.id }
        val attempt = x.pods.state.detail!!.draftAttemptId!!
        fun take(player: E2ePlayer, card: String) = runBlocking {
            org.asyncttwiz.android.PodApi(E2eStack.origin).use { api ->
                val turn = api.detail(player.account.token, pod).limited!!.turn
                api.turn(player.account.token, pod, PodTurn(UUID.randomUUID().toString(), attempt, LimitedAction.TAKE_CARD, turn, card))
            }
        }
        // x takes its first pick (turn 1 of 10); then it's y's two picks in a row
        take(x, board[0])
        x.pods.refresh()
        waitUntil("${x.name} waits for ${y.name}") { x.showsText("Waiting for ${y.name} · Pick 2 of 10") }
        // x queues four cards on the phone: tap a card, then "Queue <card>"
        val queued = board.subList(1, 5)
        for (card in queued) {
            x.tagged("pod-rotisserie-pool").performScrollToNode(hasTestTag("pod-rotisserie-$card"))
            x.tagged("pod-rotisserie-$card").performClick()
            waitUntil("$card framed for the queue") { x.shows(hasTestTag("pod-limited-confirm") and isEnabled()) &&
                x.pods.state.detail!!.limited!!.board.any { it.copy.id == card } }
            x.tagged("pod-limited-confirm").performClick()
            waitUntil("$card in ${x.name}'s queue strip") { x.shows(hasTestTag("pod-rotisserie-queued-$card")) }
        }
        waitUntil("the queue kept on the server") { x.pods.state.pending == 0 && x.pods.state.detail!!.limited!!.pickQueue.map { it.id } == queued }
        // y picks twice: x's two picks in a row take the first two queued cards at once; the other two stay queued
        take(y, board[5])
        take(y, board[6])
        x.pods.refresh()
        waitUntil("${x.name}'s first two queued cards taken") {
            val view = x.pods.state.detail!!.limited!!
            view.pool.map { it.id } == listOf(board[0], board[1], board[2]) && view.pickQueue.map { it.id } == queued.drop(2) && view.turn == 5
        }
        waitUntil("the strip shows the two still queued") {
            x.shows(hasTestTag("pod-rotisserie-queued-${board[3]}")) && x.shows(hasTestTag("pod-rotisserie-queued-${board[4]}")) &&
                !x.shows(hasTestTag("pod-rotisserie-queued-${board[1]}"))
        }
        // y takes x's last queued card: x's whole queue clears with a note, and y's next pick still waits for y
        take(y, board[4])
        x.pods.refresh()
        waitUntil("${x.name}'s queue cleared") {
            x.pods.state.detail!!.limited!!.pickQueue.isEmpty() && x.shows(hasTestTag("pod-rotisserie-queue-cleared")) &&
                !x.shows(hasTestTag("pod-rotisserie-queue"))
        }
        kotlin.test.assertEquals(board[4], x.pods.state.detail!!.limited!!.queueCleared?.id)
        take(y, board[7])
        x.pods.refresh()
        waitUntil("${x.name}'s turn waits for them") { x.showsText("Your pick · Pick 8 of 10") }
        kotlin.test.assertEquals(3, x.pods.state.detail!!.limited!!.pool.size)
    }
}

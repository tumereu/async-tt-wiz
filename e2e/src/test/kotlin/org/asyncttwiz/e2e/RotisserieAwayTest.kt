package org.asyncttwiz.e2e

import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import kotlinx.coroutines.runBlocking
import org.asyncttwiz.protocol.*
import org.junit.Test
import java.util.UUID
import kotlin.test.assertEquals

/** "While you were away" in Rotisserie (DRF-239, DRF-240; owner 2026-11-19, playtest N12). */
class RotisserieAwayTest : E2e() {
    // DRF-239, DRF-240, DRF-218: the first player queues a card and leaves the pod for the list; the other player makes their two
    // picks, the second taking the queued card. Reopening the pod, the first player is told their queue was cleared and the two
    // picks since they last looked; OK closes it, the server then keeps them as seen, and opening the pod again tells nothing
    @Test fun reopeningARotisseriePodTellsWhatHappenedSinceYouLastLooked() {
        launchPlayers()
        val name = "E2E Rotisserie away ${UUID.randomUUID().toString().take(6)}"
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
        fun <T> api(call: suspend (org.asyncttwiz.android.PodApi) -> T): T = runBlocking {
            org.asyncttwiz.android.PodApi(E2eStack.origin, E2eHttp.client()).use { call(it) }
        }
        fun take(player: E2ePlayer, card: String) = api { api ->
            val turn = api.detail(player.account.token, pod).limited!!.turn
            api.turn(player.account.token, pod, PodTurn(UUID.randomUUID().toString(), attempt, LimitedAction.TAKE_CARD, turn, card))
        }
        // x picks and queues a card while looking at the pod (nothing to tell: the picks so far are seen)
        take(x, board[0])
        api { it.queue(x.account.token, pod, PodPickQueue(UUID.randomUUID().toString(), attempt, listOf(board[6]))) }
        x.pods.refresh()
        waitUntil("${x.name} sees the first pick") { x.pods.state.detail!!.limited!!.picks.size == 1 && x.pods.state.detail!!.limited!!.pickQueue.size == 1 }
        waitUntil("the first pick seen on the server") {
            api { it.detail(x.account.token, pod) }.limited!!.picksSeen == 1
        }
        x.pods.back()
        waitUntil("${x.name}'s Pods list") { x.pods.state.selected == null && x.showsText(name) }
        // y's two picks in a row while x is away: the second takes x's queued card
        take(y, board[5])
        take(y, board[6])
        x.text(name).performScrollTo().performClick()
        waitUntil("\"While you were away\" on ${x.name}'s screen") { x.shows(hasTestTag("pod-away")) }
        val cardName = { id: String -> x.pods.state.detail!!.definitions.single { d ->
            d.digest == x.pods.state.detail!!.limited!!.board.single { it.copy.id == id }.copy.definition }.definition.name }
        waitUntil("the queue cleared and the two picks told") {
            x.shows(hasTestTag("pod-away-queue-0") and hasText("Your queue was cleared: ${y.name} took ${cardName(board[6])}")) &&
                x.showsText("2 picks since you last looked") &&
                x.shows(hasTestTag("pod-away-pick-2")) && x.shows(hasTestTag("pod-away-pick-3")) && !x.shows(hasTestTag("pod-away-pick-1"))
        }
        waitUntil("the picks since seen on the server") { api { it.detail(x.account.token, pod) }.limited!!.picksSeen == 3 }
        x.tagged("pod-away-ok").performClick()
        waitUntil("the dialog closed") { !x.shows(hasTestTag("pod-away")) && x.pods.away == null }
        // Opening the pod again (as on another device: the server keeps what was seen) tells nothing new
        x.pods.back()
        waitUntil("${x.name}'s Pods list again") { x.pods.state.selected == null && x.showsText(name) }
        x.text(name).performScrollTo().performClick()
        waitUntil("the pod open again") { x.pods.state.detail?.id == pod && x.pods.state.detail!!.limited!!.picks.size == 3 }
        run()
        assertEquals(null, x.pods.away)
        assertEquals(false, x.shows(hasTestTag("pod-away")))
    }
}

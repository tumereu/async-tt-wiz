package org.asyncttwiz.e2e

import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import kotlinx.coroutines.runBlocking
import org.asyncttwiz.protocol.*
import org.junit.Test
import java.util.UUID

/** Winchester tables (DRF-158, DRF-159): a two-player, two-table Winchester draft where both pick at the same time. */
class WinchesterTablesTest : E2e() {
    // DRF-158, DRF-159: each phone opens on the table where it's its player's pick, both take a pile at once, each screen
    // moves on to the table that came round to them, and the draft plays to its end
    @Test fun bothPlayersPickAtTheirOwnTablesAtOnce() {
        launchPlayers()
        val name = "E2E Winchester ${UUID.randomUUID().toString().take(6)}"
        val pod = ServerSetup.draftingPod(alice.account, bob.account, name,
            settings = DraftSettings.winchester().copy(packsPerPlayer = 1, cardsPerPack = 6, piles = 2, tables = 2))
        for (player in listOf(alice, bob)) {
            waitUntil("${player.name}'s menu") { player.shows(hasTestTag("menu-PODS")) }
            player.tagged("menu-PODS").performClick()
            waitUntil("the pod in ${player.name}'s list") { player.showsText(name) }
            player.text(name).performScrollTo().performClick()
            waitUntil("${player.name}'s Winchester tables") { player.pods.state.detail?.takeIf { it.id == pod }?.limited?.tables?.size == 2 }
        }
        // Each sees the chip row, opened on their own pick: the first seat at table 1, the second at table 2
        fun table(player: E2ePlayer) = player.pods.state.detail!!.limited!!.tables.indexOfFirst { it.currentPlayer == player.id }
        val tables = listOf(alice, bob).associateWith(::table)
        kotlin.test.assertEquals(setOf(0, 1), tables.values.toSet())
        for (player in listOf(alice, bob)) {
            val other = if (player == alice) bob else alice
            waitUntil("${player.name}'s chips") {
                player.showsText("Table ${tables.getValue(player) + 1} · Your pick") &&
                    player.showsText("Table ${tables.getValue(other) + 1} · ${other.name} picks")
            }
        }
        // Both tap a card of their table's first pile and take it, without waiting for each other
        for (player in listOf(alice, bob)) {
            val copy = player.pods.state.detail!!.limited!!.tables[tables.getValue(player)].piles[0].first()!!
            player.tagged("pod-winchester-${copy.id}").performClick()
            waitUntil("${player.name}'s Take pile 1") { player.showsText("Take pile 1 (1 card)") }
            player.tagged("pod-limited-confirm").performClick()
        }
        waitUntil("both takes confirmed") {
            listOf(alice, bob).all { player -> !player.pods.state.picking && player.pods.state.detail!!.limited!!.pool.size == 1 }
        }
        // The pod's periodic refresh (every 10 s while it's open) brings each the other's take
        for (player in listOf(alice, bob)) player.pods.refresh()
        waitUntil("both takes on both phones") {
            listOf(alice, bob).all { player -> player.pods.state.detail!!.limited!!.tables.all { it.turn == 1 } }
        }
        // Each table came round to the other player: each screen moved on to the table where it's their pick now
        for (player in listOf(alice, bob)) {
            val other = if (player == alice) bob else alice
            waitUntil("${player.name} moved to their next pick") {
                player.showsText("Table ${tables.getValue(other) + 1} · Your pick") &&
                    player.showsText("Table ${tables.getValue(player) + 1} · ${other.name} picks") &&
                    player.showsText("Your pick · Stack: 2 cards")
            }
        }
        // The rest of the draft over the server: it ends when both tables are empty
        runBlocking {
            org.asyncttwiz.android.PodApi(E2eStack.origin).use { api ->
                var guard = 0
                while (api.detail(alice.account.token, pod).phase == PodPhase.DRAFTING) {
                    check(guard++ < 40) { "the draft didn't finish" }
                    for (player in listOf(alice, bob)) {
                        val detail = api.detail(player.account.token, pod)
                        val view = detail.limited ?: continue
                        val index = view.tables.indexOfFirst { it.currentPlayer == player.id }.takeIf { it >= 0 } ?: continue
                        val table = view.tables[index]
                        api.turn(player.account.token, pod, PodTurn(UUID.randomUUID().toString(), detail.draftAttemptId!!,
                            LimitedAction.TAKE_PILE, table.turn, pile = table.piles.indexOfFirst { it.isNotEmpty() }, table = index))
                    }
                }
            }
        }
        for (player in listOf(alice, bob)) player.pods.refresh()
        waitUntil("the draft finished on both phones") {
            listOf(alice, bob).all { it.pods.state.detail?.phase == PodPhase.DRAFTED }
        }
        kotlin.test.assertEquals(12, listOf(alice, bob).sumOf { it.pods.state.detail!!.limited!!.pool.size })
    }
}

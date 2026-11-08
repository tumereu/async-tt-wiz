package org.asyncttwiz.e2e

import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import kotlinx.coroutines.runBlocking
import org.asyncttwiz.protocol.*
import org.junit.Test
import java.util.UUID

/** Winston tables (DRF-156, DRF-157): a two-player, two-table Winston draft where both play at the same time. */
class WinstonTablesTest : E2e() {
    // DRF-156, DRF-157: each phone opens on the table where it's its player's turn, both take pile 1 at once, each screen
    // moves on to the table that came round to them, and the draft plays to its end
    @Test fun bothPlayersPlayAtTheirOwnTablesAtOnce() {
        launchPlayers()
        val name = "E2E Winston ${UUID.randomUUID().toString().take(6)}"
        val pod = ServerSetup.draftingPod(alice.account, bob.account, name,
            settings = DraftSettings.winston().copy(packsPerPlayer = 1, cardsPerPack = 6, piles = 2, tables = 2))
        for (player in listOf(alice, bob)) {
            waitUntil("${player.name}'s menu") { player.shows(hasTestTag("menu-PODS")) }
            player.tagged("menu-PODS").performClick()
            waitUntil("the pod in ${player.name}'s list") { player.showsText(name) }
            player.text(name).performScrollTo().performClick()
            waitUntil("${player.name}'s Winston tables") { player.pods.state.detail?.takeIf { it.id == pod }?.limited?.tables?.size == 2 }
        }
        // Each sees the chip row, opened on their own turn: the first seat at table 1, the second at table 2
        fun table(player: E2ePlayer) = player.pods.state.detail!!.limited!!.tables.indexOfFirst { it.currentPlayer == player.id }
        val tables = listOf(alice, bob).associateWith(::table)
        kotlin.test.assertEquals(setOf(0, 1), tables.values.toSet())
        for (player in listOf(alice, bob)) {
            val other = if (player == alice) bob else alice
            waitUntil("${player.name}'s chips") {
                player.showsText("Table ${tables.getValue(player) + 1} · Your pick") &&
                    player.showsText("Table ${tables.getValue(other) + 1} · ${other.name} looks") &&
                    player.showsText("Pile 1 · 1 card · you look at it")
            }
        }
        // Both take pile 1 at their table, without waiting for each other
        for (player in listOf(alice, bob)) {
            waitUntil("${player.name}'s Take pile 1") { player.showsText("Take pile 1") }
            player.tagged("pod-winston-take").performClick()
        }
        waitUntil("both takes confirmed") {
            listOf(alice, bob).all { player -> !player.pods.state.picking && player.pods.state.detail!!.limited!!.pool.size == 1 }
        }
        // The pod's periodic refresh (every 10 s while it's open) brings each the other's take
        for (player in listOf(alice, bob)) player.pods.refresh()
        waitUntil("both takes on both phones") {
            listOf(alice, bob).all { player -> player.pods.state.detail!!.limited!!.tables.all { it.turn == 1 } }
        }
        // Each table came round to the other player: each screen moved on to the table where it's their turn now
        for (player in listOf(alice, bob)) {
            val other = if (player == alice) bob else alice
            waitUntil("${player.name} moved to their next turn") {
                player.showsText("Table ${tables.getValue(other) + 1} · Your pick") &&
                    player.showsText("Table ${tables.getValue(player) + 1} · ${other.name} looks") &&
                    player.showsText("Your pick · Stack: 3 cards")
            }
        }
        // The rest of the draft over the server: it ends when both tables are empty
        runBlocking {
            org.asyncttwiz.android.PodApi(E2eStack.origin).use { api ->
                var guard = 0
                while (api.detail(alice.account.token, pod).phase == PodPhase.DRAFTING) {
                    check(guard++ < 60) { "the draft didn't finish" }
                    for (player in listOf(alice, bob)) {
                        val detail = api.detail(player.account.token, pod)
                        val view = detail.limited ?: continue
                        val index = view.tables.indexOfFirst { it.currentPlayer == player.id }.takeIf { it >= 0 } ?: continue
                        val table = view.tables[index]
                        api.turn(player.account.token, pod, PodTurn(UUID.randomUUID().toString(), detail.draftAttemptId!!,
                            LimitedAction.TAKE_PILE, table.turn, pile = table.pile, table = index))
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

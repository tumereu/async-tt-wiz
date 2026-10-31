package org.asyncttwiz.android

import androidx.compose.ui.graphics.Color
import org.asyncttwiz.protocol.*
import org.junit.Test
import kotlin.test.*

class TableChromeTest {
    // SYS-017, TRN-011
    @Test fun initialAdmissionAndCatchupAreLoadingNotUnavailable() {
        val game = TableFixture.game.id
        val connecting = LobbyState()
        assertTrue(tableIsLoading(connecting.tableView(), game, null))
        assertTrue(tableIsLoading(connecting.copy(connectedGames = setOf(game)).tableView(), game, null))
        assertFalse(tableIsLoading(connecting.copy(connectionMessage = "Connection failed").tableView(), game, null))
        assertFalse(tableIsLoading(connecting.copy(reconnectingGames = setOf(game)).tableView(), game, null))
        assertFalse(tableIsLoading(connecting.tableView(), game, "History unavailable"))
        assertFalse(tableIsLoading(connecting.tableView(), game, null, timedOut = true))
        // A failed different game must not interrupt admission to this table.
        assertTrue(tableIsLoading(connecting.copy(reconnectingGames = setOf("other")).tableView(), game, null))
    }

    // TRN-013, TRN-015, TRN-018
    @Test fun quickVisibilityAndAdvanceStyleUseTurnAndMoveIndependentlyForBothPlayers() {
        val play = TableFixture.play()
        for (viewer in listOf("a", "b")) for (turn in listOf("a", "b")) for (move in listOf("a", "b")) {
            val current = play.copy(board = play.board.copy(turn = play.board.turn.copy(accountId = turn), attentionHolder = move))
            assertEquals(viewer == turn || viewer == move || QuickActions.openingMulligan(current, viewer), QuickActions.visible(current, viewer))
            val style = advanceButtonStyle(current.board, viewer)
            when {
                viewer != move -> assertEquals(AdvanceButtonStyle(Color.White, Color.Gray, Color.Gray), style)
                viewer == turn -> assertEquals(AdvanceButtonStyle(OwnTableColor, Color.White, OwnTableColor), style)
                else -> assertEquals(AdvanceButtonStyle(Color.White, OpponentTableColor, OpponentTableColor), style)
            }
            assertEquals(battlefieldColor(move, viewer, move, false), tableBarColor(current.board, viewer))
        }
    }

    @Test fun liveTimelineCentersLatestAndProjectsOnlyTheVisibleWindow() {
        val count = 50_000
        for (width in listOf(280f, 412f, 892f)) {
            assertEquals(width / 2f, HistoryTimelineLayout.x(count - 1, count - 1, width, 24f))
            val visible = HistoryTimelineLayout.visible(count, count - 1, width, 24f)
            assertEquals(count - 1, visible.last)
            assertTrue(visible.count() <= width.toInt() / 48 + 2)
        }
    }
}

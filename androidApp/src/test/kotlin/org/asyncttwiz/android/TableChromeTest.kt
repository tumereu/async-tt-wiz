package org.asyncttwiz.android

import androidx.compose.ui.graphics.Color
import org.asyncttwiz.protocol.*
import org.junit.Test
import kotlin.test.*

class TableChromeTest {
    @Test fun initialAdmissionAndCatchupAreLoadingNotUnavailable() {
        val game = TableFixture.game.id
        val connecting = LobbyState()
        assertTrue(tableIsLoading(connecting, game, null))
        assertTrue(tableIsLoading(connecting.copy(connectedGames = setOf(game)), game, null))
        assertFalse(tableIsLoading(connecting.copy(connectionMessage = "Connection failed"), game, null))
        assertFalse(tableIsLoading(connecting.copy(reconnectingGames = setOf(game)), game, null))
        assertFalse(tableIsLoading(connecting, game, "History unavailable"))
        assertFalse(tableIsLoading(connecting, game, null, timedOut = true))
        // A failed different game must not interrupt admission to this table.
        assertTrue(tableIsLoading(connecting.copy(reconnectingGames = setOf("other")), game, null))
    }

    @Test fun quickVisibilityAndAdvanceStyleUseTurnAndMoveIndependentlyForBothPlayers() {
        val play = TableFixture.play()
        for (viewer in listOf("a", "b")) for (turn in listOf("a", "b")) for (move in listOf("a", "b")) {
            val current = play.copy(board = play.board.copy(turn = play.board.turn.copy(accountId = turn), attentionHolder = move))
            assertEquals(viewer == turn || viewer == move, QuickActions.visible(current, viewer))
            val style = advanceButtonStyle(current.board, viewer)
            when {
                viewer != move -> assertEquals(AdvanceButtonStyle(Color.White, Color.Gray, Color.Gray), style)
                viewer == turn -> assertEquals(AdvanceButtonStyle(OwnTableColor, Color.White, OwnTableColor), style)
                else -> assertEquals(AdvanceButtonStyle(Color.White, OpponentTableColor, OpponentTableColor), style)
            }
            assertEquals(battlefieldColor(turn, viewer, turn, false), tableBarColor(current.board, viewer))
        }
    }

    @Test fun liveTimelinePinsLatestAtRightAndProjectsOnlyTheVisibleWindow() {
        val count = 50_000
        for (width in listOf(280f, 412f, 892f)) {
            assertEquals(width - 12f, HistoryTimelineLayout.x(count - 1, count - 1, width, 24f, rightAligned = true))
            val visible = HistoryTimelineLayout.visible(count, count - 1, width, 24f, rightAligned = true)
            assertEquals(count - 1, visible.last)
            assertTrue(visible.count() <= width.toInt() / 24 + 2)
        }
    }
}

package org.asyncttwiz.android

import androidx.compose.ui.graphics.Color
import org.asyncttwiz.protocol.*
import org.junit.Test
import kotlin.test.*

class TableChromeTest {
    // SYS-017
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

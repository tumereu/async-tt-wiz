package org.asyncttwiz.android

import org.asyncttwiz.protocol.ActivityHint
import org.asyncttwiz.protocol.ActivityKind
import org.junit.Test
import kotlin.test.assertEquals

/** The cleanup's warnings (game history §4.5) as the app shows them. */
class RemovalNoticeTest {
    // LOB-134, LOB-077: a game in play: "<game> · vs <opponent>" / "Removed in 3 days if nobody plays", in the game's own
    // notification slot (it clears while the game is open); a pod: "<pod>: Removed in 3 days if nobody picks or plays"
    @Test fun removalWarningTexts() {
        val game = ActivityHint("5", ActivityKind.GAME_REMOVAL, "g1", "i1", resourceName = "Azure Spark", opponentName = "riley")
        assertEquals("Azure Spark · vs riley" to "Removed in 3 days if nobody plays", gameRemovalText(game))
        assertEquals("game:g1", game.notificationKey())
        assertEquals("game_activity", ActivityChannels.channel(ActivityKind.GAME_REMOVAL))
        assertEquals("Cube night: Removed in 3 days if nobody picks or plays", podActivityText(ActivityKind.POD_REMOVAL, "Cube night"))
        val pod = ActivityHint("6", ActivityKind.POD_REMOVAL, "p1", "i2", resourceName = "Cube night")
        assertEquals("pod:p1", pod.notificationKey())
        assertEquals("draft_activity", ActivityChannels.channel(ActivityKind.POD_REMOVAL))
    }
}

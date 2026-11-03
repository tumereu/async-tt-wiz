package org.asyncttwiz.android

import org.asyncttwiz.protocol.PlayZone
import org.junit.Test
import kotlin.test.*

class TimelineGesturesTest {
    // HIS-016, TBL-049
    @Test fun doubleTappingInTimelineIsSilentForBattlefieldHandStackAndCombat() {
        for (zone in listOf(PlayZone.BATTLEFIELD, PlayZone.HAND, PlayZone.STACK)) {
            for (viewport in listOf("table", "combat")) {
                val events = mutableListOf<TableIntent>()
                val diagnostics = mutableListOf<String>()
                val card = TableItem("card", TableBounds(10f, 10f, 30f, 40f), "Card", "", kind = TableItemKind.CARD,
                    cardId = "card", zone = zone, viewport = viewport)
                val scene = TableScene(listOf(card), emptyList())
                val gestures = TableGestureCoordinator(events::add, diagnostic = diagnostics::add)
                gestures.update(scene, false, reviewing = true)
                gestures.down(card.bounds.center, 0); gestures.up(card.bounds.center, 10)
                events.clear(); diagnostics.clear()
                gestures.down(card.bounds.center, 100); gestures.up(card.bounds.center, 110)
                assertTrue(events.all { it == TableIntent.DismissTapUi }, "$zone / $viewport: $events")
                assertTrue(diagnostics.isEmpty(), "$zone / $viewport: $diagnostics")
            }
        }
    }

}

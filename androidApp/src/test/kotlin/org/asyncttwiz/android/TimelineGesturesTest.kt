package org.asyncttwiz.android

import org.asyncttwiz.protocol.PlayZone
import org.junit.Test
import kotlin.test.*

class TimelineGesturesTest {
    @Test fun doubleTappingInTimelineIsSilentForBattlefieldHandStackAndCombat() {
        for (zone in listOf(PlayZone.BATTLEFIELD, PlayZone.HAND, PlayZone.STACK)) {
            for (viewport in listOf("table", "combat")) {
                val events = mutableListOf<TableIntent>()
                val diagnostics = mutableListOf<String>()
                val card = TableItem("card", TableBounds(10f, 10f, 30f, 40f), "Card", "", kind = TableItemKind.CARD,
                    cardId = "card", zone = zone, viewport = viewport)
                val scene = TableScene(listOf(card), emptyList())
                val gestures = TableGestureCoordinator(events::add, diagnostic = diagnostics::add)
                gestures.update(scene, TableSelection(), false, reviewing = true)
                gestures.down(card.bounds.center, 0); gestures.up(card.bounds.center, 10)
                events.clear(); diagnostics.clear()
                gestures.down(card.bounds.center, 100); gestures.up(card.bounds.center, 110)
                assertTrue(events.all { it == TableIntent.DismissTapUi }, "$zone / $viewport: $events")
                assertTrue(diagnostics.isEmpty(), "$zone / $viewport: $diagnostics")
            }
        }
    }

    @Test fun reviewCannotRectangleSelectOrToggleSelectionEvenWithStaleSelectMode() {
        val scene = TableFixture.scene()
        val events = mutableListOf<TableIntent>()
        val gestures = TableGestureCoordinator(events::add)
        val card = scene.items.first { it.kind == TableItemKind.CARD && it.zone == PlayZone.BATTLEFIELD }
        gestures.update(scene, TableSelection(ids = setOf(card.cardId!!), mode = TableMode.SELECT), false, reviewing = true)
        gestures.select(card)
        gestures.down(card.bounds.center, 0); gestures.up(card.bounds.center, 10)
        val start = scene.fields.first().bounds.center
        gestures.down(start, 1000)
        gestures.move(TablePoint(start.x + 100, start.y + 100), 1020)
        assertNull(gestures.preview.rectangle)
        gestures.up(TablePoint(start.x + 100, start.y + 100), 1040)
        assertTrue(events.filterIsInstance<TableIntent.Selection>().all { it.selection.ids.isEmpty() })
    }
}

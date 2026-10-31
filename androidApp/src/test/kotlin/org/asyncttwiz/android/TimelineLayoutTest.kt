package org.asyncttwiz.android

import org.asyncttwiz.protocol.*
import org.junit.Test
import kotlin.test.*

class TimelineLayoutTest {
    // HIS-007
    @Test fun playAndTimelineReserveIdenticalHudAndCameraSpaceInBothOrientationsAtEveryZoom() {
        for ((width, height, landscape) in listOf(Triple(360f, 640f, false), Triple(412f, 892f, false),
            Triple(640f, 360f, true), Triple(892f, 412f, true))) for (zoom in listOf(.65f, 1f, 2f)) {
            val play = TableFixture.play(hand = 4, board = 3).let { p -> p.copy(board = p.board.copy(players =
                p.board.players.map { it.copy(counters = mapOf(CounterColor.BLUE to "2")) })) }
            val layout = TableLayoutState(TableCamera(13f, -8f, zoom), scroll = mapOf("a/HAND" to 9f))
            val normal = TablePresentation.scene(TableFixture.game, play, "a", width, height, landscape, layout)
            val review = TablePresentation.scene(TableFixture.game, play, "a", width, height, landscape, layout, reviewing = true)
            assertEquals(normal.camera, review.camera)
            assertEquals(normal.world!!.viewport, review.world!!.viewport)
            assertEquals(normal.world.bounds, review.world.bounds)
            assertEquals(normal.world.visibleViewport, review.world.visibleViewport)
            for ((before, after) in normal.items.zip(review.items)) {
                assertEquals(before.id, after.id)
                assertEquals(before.bounds, after.bounds)
                assertEquals(before.footprint, after.footprint)
                if (before.zone == PlayZone.BATTLEFIELD) assertTrue(after.visible.all { it.bottom <= review.world.visibleViewport.bottom })
            }
            for ((before, after) in normal.fields.zip(review.fields)) assertEquals(before.gridBounds, after.gridBounds)
            val bottom = review.items.filter { it.viewport in (if (landscape) setOf("a/HAND") else setOf("a/HAND", "dock/a", "counters/a")) }.maxOf { it.bounds.bottom }
            val panelTop = height - tableBottomHeight(landscape)
            assertEquals(panelTop - if (landscape) 2f else 0f, bottom, .001f)
            assertFalse(review.worldInput(TablePoint(review.world.visibleViewport.center.x, panelTop + 1)))
            assertEquals(normal, TablePresentation.scene(TableFixture.game, play, "a", width, height, landscape, layout))
        }
    }
}

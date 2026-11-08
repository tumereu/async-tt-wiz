package org.asyncttwiz.android

import org.asyncttwiz.protocol.*
import org.junit.Test
import kotlin.test.*

class TimelineLayoutTest {
    // HIS-001, HIS-007: play mode's bottom bar is only the controls; timeline mode's panel adds its strip in portrait, the
    // HUD sitting right above either; in landscape both are one row, your hand in it (TBL-009), and nothing moves
    @Test fun theHudSitsAboveThePlayBarOrTheTimelinePanelInBothOrientationsAtEveryZoom() {
        for ((width, height, landscape) in listOf(Triple(360f, 640f, false), Triple(412f, 892f, false),
            Triple(640f, 360f, true), Triple(892f, 412f, true))) for (zoom in listOf(.65f, 1f, 2f)) {
            val play = TableFixture.play(hand = 4, board = 3).let { p -> p.copy(board = p.board.copy(players =
                p.board.players.map { it.copy(counters = mapOf(CounterColor.BLUE to "2")) })) }
            val layout = TableLayoutState(TableCamera(13f, -8f, zoom), scroll = mapOf("a/HAND" to 9f))
            val normal = TablePresentation.scene(TableFixture.game, tableBoard(play), "a", width, height, landscape, layout)
            val review = TablePresentation.scene(TableFixture.game, tableBoard(play), "a", width, height, landscape, layout,
                reviewing = true, timeline = true)
            assertEquals(normal.camera, review.camera)
            if (landscape) {
                assertEquals(normal.world!!.viewport, review.world!!.viewport)
                assertEquals(normal.world.bounds, review.world.bounds)
                assertEquals(normal.world.visibleViewport, review.world.visibleViewport)
                for ((before, after) in normal.items.zip(review.items)) {
                    assertEquals(before.id, after.id)
                    assertEquals(before.bounds, after.bounds)
                    assertEquals(before.footprint, after.footprint)
                }
                for ((before, after) in normal.fields.zip(review.fields)) assertEquals(before.gridBounds, after.gridBounds)
            }
            for ((scene, timeline) in listOf(normal to false, review to true)) {
                for (item in scene.items) if (item.zone == PlayZone.BATTLEFIELD)
                    assertTrue(item.visible.all { it.bottom <= scene.world!!.visibleViewport.bottom })
                val bottom = (scene.items.filter { it.viewport == "a/HAND" }.map { it.bounds.bottom } +
                    scene.viewports.filter { !landscape && it.id == "dock/a" }.map { it.bounds.bottom }).max()
                val panelTop = height - tableBottomHeight(landscape, timeline)
                assertEquals(if (landscape) height - 4f else panelTop, bottom, .001f)
                assertFalse(scene.worldInput(TablePoint(scene.world!!.visibleViewport.center.x, panelTop + 1)))
            }
            assertEquals(normal, TablePresentation.scene(TableFixture.game, tableBoard(play), "a", width, height, landscape, layout))
        }
    }
}

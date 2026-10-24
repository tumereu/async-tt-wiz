package org.asyncttwiz.android

import org.asyncttwiz.protocol.*
import org.junit.Test
import kotlin.test.*

class TableWorldTest {
    @Test fun worldScalesUniformlyWhileAllHudGeometryStaysFixed() {
        val f = TableStatesFixture
        for (landscape in listOf(false, true)) {
            fun scene(camera: TableCamera) = TablePresentation.scene(f.game, f.combat(), f.a,
                if (landscape) 892f else 412f, if (landscape) 412f else 892f, landscape, TableLayoutState(camera))
            val home = scene(TableCamera())
            for (zoom in listOf(.65f, 1.4f, 2f)) {
                val next = scene(TableCamera(18f, -12f, zoom))
                fun transformed(b: TableBounds): TableBounds {
                    val center = home.world!!.viewport.center
                    return TableBounds(center.x + (b.x - center.x - 18f) * zoom,
                        center.y + (b.y - center.y + 12f) * zoom, b.width * zoom, b.height * zoom)
                }
                for (field in home.fields) {
                    val other = next.fields.single { it.owner == field.owner }
                    val expected = transformed(field.gridBounds)
                    assertEquals(expected.x, other.gridBounds.x, .001f)
                    assertEquals(expected.y, other.gridBounds.y, .001f)
                    assertEquals(expected.height, other.gridBounds.height, .001f)
                    assertEquals(field.rowHeight * zoom, other.rowHeight, .001f)
                }
                val homeGap = home.fields.last().gridBounds.y - home.fields.first().gridBounds.bottom
                assertEquals(homeGap * zoom, next.fields.last().gridBounds.y - next.fields.first().gridBounds.bottom, .001f)
                for (item in home.items) {
                    val other = next.items.single { it.id == item.id }
                    if (item.zone == PlayZone.BATTLEFIELD) {
                        val expected = transformed(item.bounds)
                        assertEquals(expected.x, other.bounds.x, .001f, item.id)
                        assertEquals(expected.y, other.bounds.y, .001f, item.id)
                        assertEquals(expected.width, other.bounds.width, .001f, item.id)
                        assertEquals(expected.height, other.bounds.height, .001f, item.id)
                    } else assertEquals(item.bounds, other.bounds, item.id)
                }
                assertEquals(home.stackRegion, next.stackRegion)
                assertEquals(home.viewports.filter { it.id == "sidebar" || it.hand || it.id.startsWith("dock/") || it.id.startsWith("counters/") },
                    next.viewports.filter { it.id == "sidebar" || it.hand || it.id.startsWith("dock/") || it.id.startsWith("counters/") })
            }
        }
    }

    @Test fun combatBandAnimationMovesFieldsWithoutChangingTheirRowsOrHud() {
        val initial = TableFixture.play(board = 1)
        val play = initial.copy(board = initial.board.copy(combat = CombatArea("combat")))
        fun scene(height: Float) = TablePresentation.scene(TableFixture.game, play, "a", 412f, 892f, false, combatBandHeight = height)
        val closed = scene(12f)
        for (height in listOf(30f, 60f, 88.48f)) {
            val frame = scene(height)
            assertEquals((height - 12) / 2, closed.fields.first().gridBounds.y - frame.fields.first().gridBounds.y, .001f)
            assertEquals((height - 12) / 2, frame.fields.last().gridBounds.y - closed.fields.last().gridBounds.y, .001f)
            assertEquals(closed.fields.first().rowHeight, frame.fields.first().rowHeight)
            assertEquals(closed.stackRegion, frame.stackRegion)
            for (item in closed.items.filter { it.zone != PlayZone.BATTLEFIELD }) assertEquals(item.bounds, frame.items.single { it.id == item.id }.bounds)
        }
    }

    @Test fun fitIncludesDistantOccupiedCellsAndCombatAndPanNeverLosesEntireWorld() {
        val positions = mapOf("a/BATTLEFIELD/0" to TablePlacement(8, 4), "b/BATTLEFIELD/0" to TablePlacement(6, 3))
        val scene = TableFixture.scene(TableFixture.play(board = 1), placements = positions)
        val world = scene.world!!
        val camera = world.fit()
        assertTrue(camera.zoom in TablePresentation.MIN_ZOOM..TablePresentation.MAX_ZOOM)
        val fitted = world.screen(world.bounds, camera)
        assertEquals(world.viewport.center.x, fitted.center.x, .001f)
        assertEquals(world.viewport.center.y, fitted.center.y, .001f)
        assertTrue(fitted.x >= world.viewport.x - .001f && fitted.right <= world.viewport.right + .001f)
        assertTrue(fitted.y >= world.viewport.y - .001f && fitted.bottom <= world.viewport.bottom + .001f)
        for (x in listOf(-Float.MAX_VALUE, Float.MAX_VALUE)) for (y in listOf(-Float.MAX_VALUE, Float.MAX_VALUE)) {
            val clamped = world.clamp(TableCamera(x, y, 2f))
            assertNotNull(world.screen(world.bounds, clamped).intersection(world.viewport))
        }
    }

    @Test fun portraitStackOccludesWorldDrawingSelectionAndDropsAtEveryZoom() {
        for (zoom in listOf(.65f, 1f, 2f)) {
            val scene = TableFixture.scene(ui = TableLayoutState(TableCamera(zoom = zoom)))
            val region = scene.stackRegion!!
            assertEquals(412f, region.right); assertEquals(410f, region.center.y)
            for (item in scene.items.filter { it.zone == PlayZone.BATTLEFIELD }) {
                assertTrue(item.visible.none { it.intersection(region) != null })
            }
            assertFalse(scene.worldInput(region.center))
            val start = scene.items.single { it.cardId == "a/HAND/0" }.visible.first().center
            val events = mutableListOf<TableIntent>()
            val gesture = TableGestureCoordinator(events::add)
            gesture.update(scene, TableSelection(), true)
            gesture.down(start, 0); gesture.move(region.center, 80); gesture.up(region.center, 100)
            assertEquals(TableDestination.Container("stack", PlayZone.STACK), events.filterIsInstance<TableIntent.Move>().single().destination)
        }
    }
}

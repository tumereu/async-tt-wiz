package org.asyncttwiz.android

import org.asyncttwiz.protocol.*
import org.junit.Test
import kotlin.test.*

class TableWorldTest {
    @Test fun panExposesThreeEmptyColumnsBeyondBothBattlefieldsWithoutChangingFitBounds() {
        val f = TableStatesFixture
        val play = f.combat()
        val cards = play.board.cards.filter { c -> play.board.containers.any { it.id == c.containerId && it.zone == PlayZone.BATTLEFIELD } }
        val positions = cards.mapIndexed { i, c -> c.id to TablePlacement(if (i % 2 == 0) -4 else 12, 0) }.toMap()
        for (landscape in listOf(false, true)) {
            val scene = TablePresentation.scene(f.game, play, f.a, if (landscape) 892f else 412f,
                if (landscape) 412f else 892f, landscape, placements = positions)
            val world = scene.world!!
            assertTrue(world.panBounds.x <= -7 * TablePresentation.GRID)
            assertTrue(world.panBounds.right >= 16 * TablePresentation.GRID)
            for (zoom in listOf(.65f, 1f, 2f)) {
                val left = world.clamp(TableCamera(-10000f, zoom = zoom))
                val right = world.clamp(TableCamera(10000f, zoom = zoom))
                assertEquals(world.viewport.x, world.screen(world.panBounds, left).x, .001f)
                assertEquals(world.viewport.right, world.screen(world.panBounds, right).right, .001f)
            }
            assertTrue(world.bounds.width < world.panBounds.width)
        }
    }
    @Test fun boundsAlignContentEdgesForLargeSmallAndMixedAxesAtEveryZoom() {
        val viewport = TableBounds(20f, 30f, 200f, 100f)
        for (width in listOf(80f, 400f)) for (height in listOf(30f, 300f))
            for (zoom in listOf(.65f, 1f, 2f)) {
                val world = TableWorld(viewport, TableBounds(-30f, -40f, width, height))
                for (sign in listOf(-1f, 1f)) {
                    val camera = world.clamp(TableCamera(sign * 10000, sign * 10000, zoom))
                    val content = world.screen(world.bounds, camera)
                    if (width * zoom >= viewport.width) {
                        assertTrue(content.x <= viewport.x + .001f && content.right >= viewport.right - .001f)
                    } else assertTrue(content.x >= viewport.x - .001f && content.right <= viewport.right + .001f)
                    if (height * zoom >= viewport.height) {
                        assertTrue(content.y <= viewport.y + .001f && content.bottom >= viewport.bottom - .001f)
                    } else assertTrue(content.y >= viewport.y - .001f && content.bottom <= viewport.bottom + .001f)
                    val resisted = world.resist(camera.copy(x = camera.x + sign * 1000, y = camera.y + sign * 1000))
                    assertTrue(kotlin.math.abs(resisted.x - camera.x) * zoom in 0f..96f)
                    assertTrue(kotlin.math.abs(resisted.y - camera.y) * zoom in 0f..96f)
                }
            }
    }

    @Test fun backgroundBandsCoverViewportWidthEvenWhenAllocatedWorldIsOffscreen() {
        val scene = TableFixture.scene(ui = TableLayoutState(TableCamera(800f, 0f, .65f)))
        val world = scene.world!!
        assertTrue(scene.fields.all { it.bounds.width == 0f })
        val bands = scene.fields.map { it.gridBounds } + TableBounds(0f, scene.fields.first().gridBounds.bottom,
            0f, scene.fields.last().gridBounds.y - scene.fields.first().gridBounds.bottom)
        for (band in bands) {
            val background = world.horizontalBand(band)
            assertEquals(world.viewport.x, background.x)
            assertEquals(world.viewport.width, background.width)
            assertTrue(background.height > 0f)
        }
        assertEquals(.65f, scene.camera.zoom)
        assertEquals(800f, scene.camera.x, "Presentation must not discard gesture overscroll")
    }

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

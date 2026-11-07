package org.asyncttwiz.android

import org.asyncttwiz.protocol.*
import org.junit.Test
import kotlin.test.*

class TableWorldTest {
    // TBL-013: the pan area reaches 3 card slots past the outermost cards on the left and right; Fit table (the world's
    // bounds) doesn't count them
    @Test fun panExposesThreeEmptySlotsBeyondBothBattlefieldsWithoutChangingFitBounds() {
        val f = TableStatesFixture
        // Thirty more creatures of Alice's, each different: her creature row is wider than either screen at every zoom
        val combat = f.combat()
        val creature = f.source(f.a, "1/3")
        val extra = (0 until 30).map { n -> creature.copy(id = f.id("extra$n"), corner = "$n/$n", placement = null) }
        val play = combat.copy(board = combat.board.copy(cards = combat.board.cards + extra, containers = combat.board.containers.map {
            if (it.id == creature.containerId) it.copy(cards = it.cards + extra.map(PlayCard::id)) else it }))
        val pitch = TablePresentation.COLUMN_PITCH
        for (landscape in listOf(false, true)) {
            val scene = TablePresentation.scene(f.game, tableBoard(play), f.a, if (landscape) 892f else 412f,
                if (landscape) 412f else 892f, landscape)
            val world = scene.world!!
            assertEquals(world.bounds.x - 3 * pitch, world.panBounds.x, .001f)
            assertEquals(world.bounds.right + 3 * pitch, world.panBounds.right, .001f)
            assertTrue(scene.items.any { it.battlefieldCard })
            for (zoom in listOf(.65f, 1f, 2f)) {
                val left = world.clamp(TableCamera(-10000f, zoom = zoom))
                val right = world.clamp(TableCamera(10000f, zoom = zoom))
                assertEquals(world.viewport.x, world.screen(world.panBounds, left).x, .001f)
                assertEquals(world.viewport.right, world.screen(world.panBounds, right).right, .001f)
            }
            assertTrue(world.bounds.width < world.panBounds.width)
        }
    }
    // TBL-015
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

    // TBL-005
    @Test fun backgroundBandsCoverViewportWidthEvenWhenAllocatedWorldIsOffscreen() {
        val scene = TableFixture.scene(ui = TableLayoutState(TableCamera(800f, 0f, .65f)))
        val world = scene.world!!
        // The battlefield feels infinite: even overscrolled past the pan bounds, the fields fill the viewport width.
        assertTrue(scene.fields.all { it.bounds.x == world.viewport.x && it.bounds.width == world.viewport.width })
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

    // TBL-001
    @Test fun worldScalesUniformlyWhileAllHudGeometryStaysFixed() {
        val f = TableStatesFixture
        for (landscape in listOf(false, true)) {
            fun scene(camera: TableCamera) = TablePresentation.scene(f.game, tableBoard(f.combat()), f.a,
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
                    // Fields span the whole pan area; their column-0 origin carries the world transform.
                    assertEquals(transformed(TableBounds(field.originX, 0f, 0f, 0f)).x, other.originX, .001f)
                    assertEquals(expected.y, other.gridBounds.y, .001f)
                    assertEquals(expected.height, other.gridBounds.height, .001f)
                    assertEquals(field.rowHeight * zoom, other.rowHeight, .001f)
                }
                val homeGap = home.fields.last().gridBounds.y - home.fields.first().gridBounds.bottom
                assertEquals(homeGap * zoom, next.fields.last().gridBounds.y - next.fields.first().gridBounds.bottom, .001f)
                // CMB-007, CMB-042 (owner, 2026-11-05): the combat band is world content: its panel and cards zoom with
                // the battlefields
                val panel = transformed(home.combatPanel!!)
                val moved = assertNotNull(next.combatPanel)
                assertEquals(panel.x, moved.x, .001f); assertEquals(panel.y, moved.y, .001f)
                assertEquals(panel.width, moved.width, .001f); assertEquals(panel.height, moved.height, .001f)
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
                assertEquals(home.viewports.filter { it.hand || it.id.startsWith("dock/") || it.id.startsWith("counters/") },
                    next.viewports.filter { it.hand || it.id.startsWith("dock/") || it.id.startsWith("counters/") })
            }
        }
    }

    // CMB-007 (owner, 2026-11-05): opening the combat band pushes the battlefields apart by its height; no row height or
    // HUD changes
    @Test fun openingCombatPushesTheFieldsApartButMovesNoHud() {
        val initial = TableFixture.play(board = 1)
        val play = initial.copy(board = initial.board.copy(combat = CombatArea("combat", listOf(CombatPair("pair", attackers = listOf(CombatOccurrence("attacker", "a/BATTLEFIELD/0")))))))
        for (landscape in listOf(false, true)) {
            val (w, h) = if (landscape) 892f to 412f else 412f to 892f
            val closed = TablePresentation.scene(TableFixture.game, tableBoard(initial), "a", w, h, landscape)
            val open = TablePresentation.scene(TableFixture.game, tableBoard(play), "a", w, h, landscape)
            val panel = assertNotNull(open.combatPanel)
            assertTrue(open.fields.first().gridBounds.bottom <= panel.y && open.fields.last().gridBounds.y >= panel.bottom)
            for ((a, b) in closed.fields.zip(open.fields)) {
                assertEquals(a.gridBounds.height, b.gridBounds.height, .001f)
                assertEquals(a.rowHeight, b.rowHeight, .001f)
            }
            assertEquals(closed.stackRegion, open.stackRegion)
            for (item in closed.items.filter { it.zone != PlayZone.BATTLEFIELD })
                assertEquals(item.bounds, open.items.single { it.id == item.id }.bounds, item.id)
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
            gesture.update(scene, true)
            gesture.down(start, 0); gesture.move(region.center, 80); gesture.up(region.center, 100)
            assertEquals(TableDestination.Container("stack", PlayZone.STACK), events.filterIsInstance<TableIntent.Move>().single().destination)
        }
    }
}

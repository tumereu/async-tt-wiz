package org.asyncttwiz.android

import org.asyncttwiz.protocol.*
import org.junit.Test
import kotlin.test.*

class BattlefieldGeometryTest {
    @Test fun rowsFollowRenderedCardHeightWithMarginsAndOverflowIsReachable() {
        val initial = TableFixture.play(board = 15)
        val positions = initial.board.cards.filter { it.containerId.endsWith("BATTLEFIELD") }.associate { card ->
            val n = card.id.substringAfterLast('/').toInt()
            card.id to TablePlacement(n % 3, n / 3, n % 3)
        }
        for (landscape in listOf(false, true)) for (scale in listOf(.5f, 1f, 3f))
            for (zoom in listOf(.65f, 1f, 2f)) for (tapped in listOf(false, true)) {
            val play = initial.copy(board = initial.board.copy(cards = initial.board.cards.map { it.copy(tapped = tapped) }))
            val width = if (landscape) 892f else 412f
            val height = if (landscape) 412f else 892f
            fun scene(camera: TableCamera) = TablePresentation.scene(TableFixture.game, play, "a", width, height,
                landscape, TableLayoutState(camera), positions, appearance = CardAppearance(cardSize = scale))
            val home = scene(TableCamera(zoom = zoom))
            val world = home.world!!
            for (field in home.fields) {
                assertEquals(56f * scale * zoom, field.rowHeight, .001f)
                for (card in home.items.filter { it.viewport == field.containerId }) {
                    val row = positions.getValue(card.cardId!!).row
                    val rowY = if (field.owner == "a") field.gridBounds.bottom - (row + 1f) * field.rowHeight
                        else field.gridBounds.y + row * field.rowHeight
                    val margin = 40f * scale * zoom * .1f
                    assertTrue(card.bounds.y >= rowY + margin - .001f, "$scale/$zoom: ${card.bounds}")
                    assertTrue(card.bounds.bottom <= rowY + field.rowHeight - margin + .001f)
                    assertTrue(card.bounds.y >= field.gridBounds.y && card.bounds.bottom <= field.gridBounds.bottom)
                }
                // Both extreme edges of both halves can be panned fully into view.
                for (row in listOf(0, 4)) {
                    val id = "${field.owner}/BATTLEFIELD/${row * 3}"
                    val card = home.items.single { it.cardId == id }
                    val centered = world.clamp(TableCamera(y = (card.bounds.center.y - world.viewport.center.y) / zoom, zoom = zoom))
                    val reached = scene(centered).items.single { it.cardId == id }
                    assertTrue(reached.bounds.center.y >= world.viewport.y - .001f)
                    assertTrue(reached.bounds.center.y <= world.viewport.bottom + .001f)
                    if (reached.bounds.height <= world.viewport.height) {
                        assertTrue(reached.bounds.y >= world.viewport.y - .001f)
                        assertTrue(reached.bounds.bottom <= world.viewport.bottom + .001f)
                    }
                }
            }
            if (world.bounds.height * zoom > world.viewport.height) {
                assertTrue(world.clamp(TableCamera(y = -10000f, zoom = zoom)).y <
                    world.clamp(TableCamera(y = 10000f, zoom = zoom)).y)
            }
        }
    }

    @Test fun stackHasEqualPaddingOnEverySideInBothOrientations() {
        for (landscape in listOf(false, true)) for (scale in listOf(.5f, 1f, 3f)) {
            val scene = TablePresentation.scene(TableFixture.game, TableFixture.play(), "a",
                if (landscape) 892f else 412f, if (landscape) 412f else 892f, landscape,
                appearance = CardAppearance(cardSize = scale))
            val sidebar = scene.viewports.firstOrNull { it.id == "sidebar" }
            val visible = if (sidebar == null) scene else TablePresentation.scene(TableFixture.game, TableFixture.play(), "a",
                892f, 412f, true, TableLayoutState(scrollY = mapOf("sidebar" to sidebar.maxScrollY / 2)),
                appearance = CardAppearance(cardSize = scale))
            val stack = visible.items.single { it.zone == PlayZone.STACK }.bounds
            val region = visible.stackRegion!!
            val padding = 12f
            assertEquals(padding, stack.x - region.x, .001f)
            assertEquals(padding, region.right - stack.right, .001f)
            assertEquals(padding, stack.y - region.y, .001f)
            assertEquals(padding, region.bottom - stack.bottom, .001f)
        }
    }
}

package org.asyncttwiz.android

import org.asyncttwiz.protocol.*
import org.junit.Test
import kotlin.test.*

class StackBubbleGeometryTest {
    private fun assertPlacement(bubble: StackBubbleGeometry, width: Float, height: Float) {
        val board = TableBounds(0f, 0f, width, height)
        assertEquals(5, bubble.controls.size)
        for ((index, control) in bubble.controls.withIndex()) {
            val context = "${control.id}, anchor=${bubble.anchor}, board=${width}x$height"
            assertEquals(control.bounds, control.bounds.intersection(board), context)
            assertNull(control.bounds.intersection(bubble.anchor), context)
            for (other in bubble.controls.take(index))
                assertNull(control.bounds.intersection(other.bounds), "$context overlaps ${other.id}")
        }
    }

    @Test fun anchorsAcrossEntireBoardHaveNonOverlappingControlsAtAllZooms() {
        for ((width, height) in listOf(412f to 892f, 892f to 412f))
            for (zoom in listOf(.65f, 1f, 2f)) for (tapped in listOf(false, true)) {
                val cardWidth = (if (tapped) 40f else 28f) * zoom
                val cardHeight = (if (tapped) 28f else 40f) * zoom
                // Include each board edge, plus partially off-screen cards after camera panning.
                val xs = (0..width.toInt() step 8).map { it.toFloat() } + (width - cardWidth)
                val ys = (0..height.toInt() step 8).map { it.toFloat() } + (height - cardHeight)
                for (x in xs) for (y in ys) {
                    val anchor = TableBounds(x, y, cardWidth, cardHeight)
                    assertPlacement(StackBubbleGeometry.create("entry", anchor, width, height, null, 4), width, height)
                }
            }
    }

    @Test fun realStackTopAnchorsHaveNonOverlappingDeterministicControlsAtAllZooms() {
        val f = TableStatesFixture
        val play = f.combat()
        val top = CombatStackRules.stack(play.board).cards.first()
        for ((width, height) in listOf(412f to 892f, 892f to 412f)) for (zoom in listOf(.65f, 1f, 2f)) {
            val scene = TablePresentation.scene(f.game, play, f.a, width, height, width > height,
                TableLayoutState(TableCamera(zoom = zoom)))
            val bubble = assertNotNull(TablePresentation.stackBubbles(scene, play, top, width, height))
            assertPlacement(bubble, width, height)
            assertEquals(bubble, TablePresentation.stackBubbles(scene, play, top, width, height))
        }
    }
}

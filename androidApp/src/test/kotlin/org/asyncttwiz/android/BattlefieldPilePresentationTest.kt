package org.asyncttwiz.android

import org.asyncttwiz.protocol.*
import org.junit.Test
import kotlin.test.*

class BattlefieldPilePresentationTest {
    private fun play(): PlayState {
        val initial = TableFixture.play(board = 4)
        return initial.copy(board = initial.board.copy(cards = initial.board.cards.map { card ->
            if (card.containerId == "a/BATTLEFIELD") card.copy(placement = PlayPlacement(
                BoardPosition(if (card.id.endsWith("3")) 1 else 0, 0),
                if (card.id.endsWith("3")) "single" else "pile")) else card
        }))
    }

    private fun members(scene: TableScene) = (0..2).map { n -> scene.items.single { it.cardId == "a/BATTLEFIELD/$n" } }
    private fun point(scene: TableScene, item: TableItem): TablePoint = item.visible.flatMap { b ->
        listOf(b.center, TablePoint(b.x + .5f, b.y + .5f))
    }.first { scene.hit(it)?.id == item.id }

    // TBL-054
    @Test fun eachBattlefieldPileMemberKeepsItsDrawingOffsetAndHitRegion() {
        for (landscape in listOf(false, true)) for (zoom in listOf(.65f, 1f, 2f)) {
            val base = TableFixture.scene(play(), landscape)
            val center = members(base).first().footprint.center
            val viewport = base.world!!.viewport
            // Keep the tested pile on screen; zooming the default home view can hide row zero.
            val camera = TableCamera(center.x - viewport.center.x, center.y - viewport.center.y, zoom)
            val scene = TableFixture.scene(play(), landscape, TableLayoutState(camera))
            for ((index, item) in members(scene).withIndex()) {
                assertEquals(battlefieldCardBounds(item.footprint, 28f * zoom, 40f * zoom, index, zoom), item.bounds)
                assertEquals(1, item.copies)
                assertTrue(item.cards.isEmpty(), "Individual battlefield drawings are not zone piles")
                assertEquals(item.id, scene.hit(point(scene, item))?.id)
            }
        }
    }


    // TBL-053
    @Test fun fullBattlefieldPileRejectsDropWithoutSendingMove() {
        val scene = TableFixture.scene(play())
        val events = mutableListOf<TableIntent>()
        val gestures = TableGestureCoordinator(events::add)
        gestures.update(scene, true)
        val start = point(scene, scene.items.single { it.cardId == "a/BATTLEFIELD/3" })
        val target = point(scene, members(scene).first())
        gestures.down(start, 0); gestures.move(target, 100)
        assertNotNull(gestures.preview.scope)
        assertNull(gestures.preview.destination)
        gestures.up(target, 120)
        assertTrue(events.none { it is TableIntent.Move })
    }
}

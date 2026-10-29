package org.asyncttwiz.android

import org.asyncttwiz.protocol.*
import org.junit.Test
import kotlin.test.*

/** Rules v30 stacks new tokens onto piles of identical tokens, but a Battlefield pile still holds three cards. */
class BattlefieldTokenPileTest {
    /** Identical tokens: three in a full pile at (0,0), one alone at (1,0), one alone at (2,0). */
    private fun play(): PlayState {
        val initial = TableFixture.play(board = 5)
        return initial.copy(board = initial.board.copy(cards = initial.board.cards.map { card ->
            if (card.containerId != "a/BATTLEFIELD") card else {
                val n = card.id.substringAfterLast('/').toInt()
                val (column, pile) = when (n) { in 0..2 -> 0 to "full"; 3 -> 1 to "half"; else -> 2 to "single" }
                card.copy(token = true, placement = PlayPlacement(BoardPosition(column, 0), pile))
            }
        }))
    }
    private fun item(scene: TableScene, n: Int) = scene.items.single { it.cardId == "a/BATTLEFIELD/$n" }
    private fun point(scene: TableScene, item: TableItem): TablePoint = item.visible.flatMap { b ->
        listOf(b.center, TablePoint(b.x + .5f, b.y + .5f))
    }.first { scene.hit(it)?.id == item.id }
    private fun destination(scene: TableScene, from: Int, onto: Int): TableDestination? {
        val gestures = TableGestureCoordinator({ })
        gestures.update(scene, TableSelection(), true)
        gestures.down(point(scene, item(scene, from)), 0); gestures.move(point(scene, item(scene, onto)), 100)
        return gestures.preview.destination
    }

    @Test fun aFullTokenPileShowsNoCountAndEveryCardIsInSight() {
        val scene = TableFixture.scene(play())
        assertTrue((0..4).all { item(scene, it).copies == 1 })
        assertFalse(item(scene, 0).label.contains("pile of"))
        assertTrue((0..2).all { item(scene, it).visible.isNotEmpty() })
    }

    @Test fun anIdenticalTokenCannotBeDroppedOntoAFullPileButOntoOneWithRoom() {
        val scene = TableFixture.scene(play())
        assertNull(destination(scene, from = 4, onto = 0))
        assertIs<TableDestination.CardPile>(destination(scene, from = 4, onto = 3))
    }
}

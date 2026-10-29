package org.asyncttwiz.android

import org.asyncttwiz.protocol.*
import org.junit.Test
import kotlin.test.*

/** Rules v30: a token pile may hold more than three cards; its size shows on the top card. */
class BattlefieldTokenPileTest {
    /** Seven identical tokens in one pile at (0,0), one more token of the same kind alone at (1,0). */
    private fun play(): PlayState {
        val initial = TableFixture.play(board = 8)
        return initial.copy(board = initial.board.copy(cards = initial.board.cards.map { card ->
            if (card.containerId != "a/BATTLEFIELD") card else {
                val alone = card.id.endsWith("7")
                card.copy(token = true, placement = PlayPlacement(BoardPosition(if (alone) 1 else 0, 0), if (alone) "single" else "pile"))
            }
        }))
    }
    private fun item(scene: TableScene, n: Int) = scene.items.single { it.cardId == "a/BATTLEFIELD/$n" }
    private fun point(scene: TableScene, item: TableItem): TablePoint = item.visible.flatMap { b ->
        listOf(b.center, TablePoint(b.x + .5f, b.y + .5f))
    }.first { scene.hit(it)?.id == item.id }

    @Test fun theTopCardOfATallTokenPileShowsItsSize() {
        val scene = TableFixture.scene(play())
        val top = item(scene, 0)
        assertEquals(7, top.copies)
        assertTrue(top.label.endsWith(", pile of 7"))
        assertEquals(top.footprint.let { battlefieldCardBounds(it, top.bounds.width, top.bounds.height, 0, 1f) }, top.bounds)
        // Lower members and small piles show no count.
        assertTrue((1..6).all { item(scene, it).copies == 1 })
        assertEquals(1, item(scene, 7).copies)
        assertEquals(TableFixture.submitted.digest + "/face", top.tokenKind)
        // Members below the third are out of sight; the top one is still the hit target.
        assertTrue((3..6).all { item(scene, it).visible.isEmpty() })
        assertEquals(top.id, scene.hit(point(scene, top))?.id)
    }

    @Test fun aThreeCardTokenPileShowsNoCount() {
        val state = play().let { p -> p.copy(board = p.board.copy(cards = p.board.cards.filterNot { it.id in setOf(
            "a/BATTLEFIELD/3", "a/BATTLEFIELD/4", "a/BATTLEFIELD/5", "a/BATTLEFIELD/6") },
            containers = p.board.containers.map { c -> c.copy(cards = c.cards.filterNot { it in setOf(
                "a/BATTLEFIELD/3", "a/BATTLEFIELD/4", "a/BATTLEFIELD/5", "a/BATTLEFIELD/6") }) })) }
        assertEquals(1, item(TableFixture.scene(state), 0).copies)
    }

    @Test fun rectangleSelectionTakesTheBuriedMembersToo() {
        val scene = TableFixture.scene(play())
        val top = item(scene, 0)
        assertEquals((0..6).map { "a/BATTLEFIELD/$it" }.toSet(), scene.rectangle(top.bounds.copy(width = 4f, height = 4f,
            x = top.bounds.center.x, y = top.bounds.center.y)))
    }

    @Test fun aSameKindTokenDropsOntoTheTallPileButAnotherCardDoesNot() {
        val scene = TableFixture.scene(play())
        val events = mutableListOf<TableIntent>()
        val gestures = TableGestureCoordinator(events::add)
        gestures.update(scene, TableSelection(), true)
        val target = point(scene, item(scene, 0))
        gestures.down(point(scene, item(scene, 7)), 0); gestures.move(target, 100)
        assertIs<TableDestination.CardPile>(gestures.preview.destination)
        gestures.up(target, 120)
        assertNotNull(events.filterIsInstance<TableIntent.Move>().singleOrNull())
        // A non-token card is refused.
        val mixed = play().let { p -> p.copy(board = p.board.copy(cards = p.board.cards.map {
            if (it.id == "a/BATTLEFIELD/7") it.copy(token = false) else it })) }
        val other = TableFixture.scene(mixed)
        val refused = TableGestureCoordinator({ })
        refused.update(other, TableSelection(), true)
        refused.down(point(other, item(other, 7)), 0); refused.move(point(other, item(other, 0)), 100)
        assertNull(refused.preview.destination)
    }
}

package org.asyncttwiz.android

import org.asyncttwiz.protocol.*
import org.junit.Test
import kotlin.test.*

class CounterPresentationTest {
    @Test fun portraitGridTilesThreeColumnsAndTwoRowsForBothPlayersAtCompactWidths() {
        for (width in listOf(320f, 360f, 412f)) {
            val play = TableFixture.play()
            val scene = TablePresentation.scene(TableFixture.game, play, "a", width, 892f, false)
            for (owner in listOf("a", "b")) {
                val grid = scene.viewports.single { it.id == "counters/$owner" }.bounds
                val dock = scene.viewports.single { it.id == "dock/$owner" }.bounds
                val counters = scene.items.filter { it.kind == TableItemKind.COUNTER && it.owner == owner }
                assertEquals(6, counters.size)
                assertEquals(3, counters.map { it.bounds.x }.distinct().size)
                assertEquals(2, counters.map { it.bounds.y }.distinct().size)
                assertEquals(dock.y, grid.y)
                assertEquals(dock.bottom, grid.bottom)
                assertEquals(dock.right, grid.x)
                assertEquals(width, grid.right)
                assertTrue(counters.all { it.value.isEmpty() && it.visible == listOf(it.bounds) })
                for (x in grid.x.toInt() + 1 until grid.right.toInt())
                    for (y in grid.y.toInt() + 1 until grid.bottom.toInt())
                        assertEquals(owner, scene.hit(TablePoint(x.toFloat(), y.toFloat()))?.takeIf {
                            it.kind == TableItemKind.COUNTER
                        }?.owner)
                val piles = scene.items.filter { it.viewport == "dock/$owner" && it.kind == TableItemKind.PILE }
                assertEquals(3, piles.size)
                assertEquals(12f, piles[1].bounds.x - piles[0].bounds.right)
                assertTrue(piles.all { it.bounds.right < grid.x })
            }
        }
    }

    @Test fun zeroAndEmptyCardCountersAreHiddenWithoutChangingAuthoritativeValues() {
        val initial = TableFixture.play(board = 1)
        for (value in listOf("", " ", "0", "+0", "-000", "1", "-2", "X")) {
            val play = initial.copy(board = initial.board.copy(cards = initial.board.cards.map {
                it.copy(counters = mapOf(CounterColor.RED to value, CounterColor.BLUE to "2"))
            }))
            for (landscape in listOf(false, true)) {
                val card = TableFixture.scene(play, landscape).items.single { it.cardId == "a/BATTLEFIELD/0" }
                assertEquals(TablePresentation.nonzero(value), CounterColor.RED in card.counters)
                assertEquals("2", card.counters[CounterColor.BLUE])
                assertEquals(value, play.board.cards.first().counters[CounterColor.RED])
            }
        }
    }
}

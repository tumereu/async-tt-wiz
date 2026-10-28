package org.asyncttwiz.android

import org.asyncttwiz.protocol.*
import org.junit.Test
import kotlin.test.*

class StatusBubbleTest {
    private fun withStatus(own: String, other: String): PlayState {
        val play = TableFixture.play(board = 3, hand = 2)
        return play.copy(board = play.board.copy(players = play.board.players.map {
            it.copy(status = if (it.accountId == "a") own else other)
        }))
    }

    @Test fun bubbleSitsBelowTheTopLifeSquareAndAboveTheBottomOne() {
        val life = TableBounds(10f, 100f, 48f, 48f)
        val below = StatusBubble.bounds(life, below = true, textWidth = 40f)
        assertTrue(below.y > life.bottom); assertEquals(life.x, below.x)
        assertEquals(40f + 2 * StatusBubble.PADDING, below.width); assertEquals(StatusBubble.HEIGHT, below.height)
        val above = StatusBubble.bounds(life, below = false, textWidth = 40f)
        assertTrue(above.bottom < life.y)
        assertEquals(28f, StatusBubble.bounds(life, true, 2f).width) // never narrower than its tail
    }

    @Test fun longTextIsEllipsizedOnOneLine() {
        val measure = { text: String -> text.length * 6f }
        assertEquals("Wait", StatusBubble.fit("Wait", 60f, measure))
        assertEquals("Got a…", StatusBubble.fit("Got a response", 42f, measure))
        assertEquals("…", StatusBubble.fit("Thinking", 1f, measure))
        // Never splits a surrogate pair.
        assertEquals("ab…", StatusBubble.fit("ab😀cd", 18f, measure))
    }

    @Test fun lifeItemsCarryEachPlayersStatusInBothOrientationsAndOnlySetStatusesAreAnnounced() {
        for (landscape in listOf(false, true)) {
            val scene = TableFixture.scene(withStatus("Wait", PlayerStatus.OPENING), landscape)
            val own = scene.items.single { it.id == "life/a" }
            val other = scene.items.single { it.id == "life/b" }
            assertEquals("Wait", own.status); assertEquals(PlayerStatus.OPENING, other.status)
            assertEquals(ValueDragSide.BOTTOM, own.valueDragSide); assertEquals(ValueDragSide.TOP, other.valueDragSide)
            assertTrue(own.label.endsWith(", status Wait"))
            assertTrue(other.label.endsWith(", status Mulliganing"))
            val plain = TableFixture.scene(withStatus("", ""), landscape).items.single { it.id == "life/a" }
            assertEquals("", plain.status); assertFalse("status" in plain.label)
        }
    }
}

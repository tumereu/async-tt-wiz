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

    // TRN-039
    @Test fun bubbleSitsBelowTheTopLifeSquareAndAboveTheBottomOne() {
        val life = TableBounds(10f, 100f, 48f, 48f)
        val below = StatusBubble.bounds(life, below = true, textWidth = 40f)
        assertTrue(below.y > life.bottom); assertEquals(life.x, below.x)
        assertEquals(40f + 2 * StatusBubble.PADDING, below.width); assertEquals(StatusBubble.HEIGHT, below.height)
        val above = StatusBubble.bounds(life, below = false, textWidth = 40f)
        assertTrue(above.bottom < life.y)
        assertEquals(28f, StatusBubble.bounds(life, true, 2f).width) // never narrower than its tail
    }

    // [StatusBubble.lines] ellipsizes its last line with this.
    @Test fun fitEllipsizesTheLongestPrefixWithoutSplittingASurrogatePair() {
        val measure = { text: String -> text.length * 6f }
        assertEquals("Wait", StatusBubble.fit("Wait", 60f, measure))
        assertEquals("Got a…", StatusBubble.fit("Got a response", 42f, measure))
        assertEquals("…", StatusBubble.fit("Thinking", 1f, measure))
        // Never splits a surrogate pair.
        assertEquals("ab…", StatusBubble.fit("ab😀cd", 18f, measure))
    }

    // TRN-039
    @Test fun longTextWrapsAtSpacesOntoAtMostFourLinesAndOnlyThenEllipsizes() {
        val measure = { text: String -> text.length * 6f }
        assertEquals(listOf("Wait"), StatusBubble.lines("Wait", 60f, measure))
        assertEquals(listOf("Back in", "five min"), StatusBubble.lines("Back in five min", 60f, measure))
        assertEquals(listOf("Back in", "five", "minutes"), StatusBubble.lines("Back in five minutes", 60f, measure))
        // A single long word breaks inside the word.
        assertEquals(listOf("xxxxxxxxxx", "xxxxx"), StatusBubble.lines("x".repeat(15), 60f, measure))
        assertEquals(StatusBubble.MAX_LINES, 4)
        assertEquals(listOf("one two", "three four", "five six", "seven eig…"),
            StatusBubble.lines("one two three four five six seven eight nine ten", 60f, measure))
        val longest = StatusBubble.lines("x".repeat(PlayerStatus.MAX_LENGTH), 60f, measure)
        assertEquals(listOf("x".repeat(10), "x".repeat(10), "x".repeat(10), "x".repeat(9) + "…"), longest)
        assertTrue(longest.all { measure(it) <= 60f })
        val life = TableBounds(10f, 100f, 48f, 48f)
        val two = StatusBubble.bounds(life, below = false, textWidth = 40f, lines = 2)
        assertEquals(StatusBubble.HEIGHT + StatusBubble.LINE, two.height)
        assertTrue(two.bottom < life.y)
        assertEquals(StatusBubble.HEIGHT + 3 * StatusBubble.LINE, StatusBubble.bounds(life, true, 40f, lines = 9).height)
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

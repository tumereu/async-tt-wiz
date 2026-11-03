package org.asyncttwiz.android

import org.asyncttwiz.protocol.*
import org.junit.Test
import kotlin.test.*

class CardRingTest {





    // TBL-069
    @Test fun inputGuardUsesPressStartNotReleaseAndIncludesTheWholeDoubleTapWindow() {
        val guard = CardRingInputGuard(1000)
        assertFalse(guard.accepts(999))
        assertFalse(guard.accepts(1000))
        assertFalse(guard.accepts(1250))
        val startedDuringGuard = guard.accepts(1279)
        assertFalse(startedDuringGuard) // still false when this press eventually releases
        assertTrue(guard.accepts(1280))
    }


    // TBL-067
    @Test fun squareFitsWithoutCoveringTheCardAtEdgesAndCorners() {
        for ((width, height) in listOf(360f to 640f, 412f to 892f, 892f to 412f, 640f to 360f)) {
            for (x in listOf(0f, width / 2 - 24, width - 48))
                for (y in listOf(0f, height / 2 - 34, height - 68)) for (own in listOf(true, false)) {
                    val card = TableBounds(x, y, 48f, 68f)
                    val b = assertNotNull(CardRingGeometry.place(width, height, card, own)).square
                    assertEquals(CardRingGeometry.squareSide, b.width)
                    assertEquals(b.width, b.height)
                    assertTrue(b.x >= 12 && b.y >= 12 && b.right <= width - 12 && b.bottom <= height - 12, "$width×$height $card $b")
                    assertNull(b.intersection(card), "$card overlaps $b")
                }
        }
    }

    // TBL-067: a popup keeps off both hands where it fits elsewhere: your Library's menu opens just above your hand, the
    // opponent's just below theirs, a battlefield card's where it did; with no room off them it still opens
    @Test fun popupsKeepOffTheHands() {
        val hands = listOf(TableBounds(8f, 116f, 396f, 52f), TableBounds(8f, 668f, 396f, 52f))
        val library = TableBounds(76f, 740f, 28f, 40f)
        val row = assertNotNull(CardRingGeometry.place(412f, 892f, library, true, 264f, 40f, avoid = hands)).square
        assertTrue(hands.all { it.intersection(row) == null }, "$row")
        assertEquals(668f - 8f, row.bottom)
        val opponentLibrary = TableBounds(76f, 64f, 28f, 40f)
        val below = assertNotNull(CardRingGeometry.place(412f, 892f, opponentLibrary, false, 264f, 40f, avoid = hands)).square
        assertEquals(168f + 8f, below.y)
        val field = TableBounds(182f, 560f, 28f, 40f)
        assertEquals(CardRingGeometry.place(412f, 892f, field, true), CardRingGeometry.place(412f, 892f, field, true, avoid = hands))
        val tight = listOf(TableBounds(0f, 0f, 412f, 550f), TableBounds(0f, 610f, 412f, 282f))
        assertEquals(CardRingGeometry.place(412f, 892f, field, true), CardRingGeometry.place(412f, 892f, field, true, avoid = tight))
        // an area the card itself lies in is not avoided
        val around = listOf(TableBounds(0f, 540f, 412f, 80f))
        assertEquals(CardRingGeometry.place(412f, 892f, field, true), CardRingGeometry.place(412f, 892f, field, true, avoid = around))
    }

    // TBL-067
    @Test fun largestFittingSideWinsAndOwnershipBreaksAboveBelowTies() {
        val portrait = TableBounds(182f, 412f, 48f, 68f)
        assertEquals(CardRingSide.ABOVE, CardRingGeometry.place(412f, 892f, portrait, true)?.side)
        assertEquals(CardRingSide.BELOW, CardRingGeometry.place(412f, 892f, portrait, false)?.side)
        assertEquals(CardRingSide.ABOVE, CardRingGeometry.place(412f, 892f, portrait.copy(y = 650f), false)?.side)
        assertEquals(CardRingSide.BELOW, CardRingGeometry.place(412f, 892f, portrait.copy(y = 100f), true)?.side)
        assertEquals(CardRingSide.LEFT, CardRingGeometry.place(892f, 412f, TableBounds(800f, 172f, 48f, 68f), true)?.side)
        assertEquals(CardRingSide.RIGHT, CardRingGeometry.place(892f, 412f, TableBounds(44f, 172f, 48f, 68f), false)?.side)
    }

    @Test fun clampingKeepsSquareInsideTwelveDpMarginAndDoesNotShiftItAcrossPages() {
        val card = TableBounds(0f, 800f, 48f, 68f)
        val opening = assertNotNull(CardRingGeometry.place(412f, 892f, card, true))
        assertEquals(12f, opening.square.x)
        for (page in CardMenuLayout.entries.map { CardRingPages.page(it) }) {
            assertTrue(page.actions.size <= 25)
            assertEquals(opening, CardRingGeometry.place(412f, 892f, card, true))
        }
        val right = assertNotNull(CardRingGeometry.place(412f, 892f, card.copy(x = 364f), true))
        assertEquals(400f, right.square.right)
    }

    @Test fun impossibleViewportDoesNotPlaceASquareOverTheCard() {
        assertNull(CardRingGeometry.place(240f, 240f, TableBounds(96f, 86f, 48f, 68f), true))
    }

}

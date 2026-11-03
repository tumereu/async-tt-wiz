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

package org.asyncttwiz.android

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** TBL-104 (owner 2026-11-06): pinned Inspect's swipe, measured in dp from where the hold began. */
class InspectSwipeTest {
    // TBL-131, TBL-104: a drag to the left past half way pins it on release; the swipe takes the movement (nothing scrolls)
    @Test fun aDragLeftPastHalfWayPins() {
        val swipe = InspectSwipe()
        assertFalse(swipe.moved(-8f, 1f), "within the slop: the scroll's")
        assertTrue(swipe.moved(-40f, 3f))
        assertTrue(swipe.moved(-80f, 30f), "once swiping, vertical movement doesn't scroll")
        assertEquals((80f - InspectSwipe.SLOP) / InspectSwipe.DISTANCE, swipe.progress, .001f)
        assertTrue(swipe.released())
        assertTrue(swipe.pinned)
        assertEquals(1f, swipe.progress)
    }

    // TBL-131, TBL-104: released before half way it closes as a plain hold does
    @Test fun lessThanHalfWayCloses() {
        val swipe = InspectSwipe()
        swipe.moved(-40f, 0f)
        assertFalse(swipe.released())
        assertFalse(swipe.pinned)
        assertEquals(0f, swipe.progress)
    }

    // TBL-131, TBL-104, TBL-127 (owner 2026-11-06): once the finger has scrolled up or down, small sideways movement never starts
    // the swipe
    @Test fun scrollingFirstNeverSwipes() {
        val swipe = InspectSwipe()
        assertFalse(swipe.moved(-2f, 20f))
        assertFalse(swipe.moved(-60f, 24f))
        assertFalse(swipe.moved(-200f, 30f))
        assertEquals(0f, swipe.progress)
        assertFalse(swipe.released())
    }

    // TBL-131 (owner playtest 2026-11-06): a drag to the right pins it as well, for a card at the table's side
    @Test fun aDragRightPinsToo() {
        val right = InspectSwipe()
        assertTrue(right.moved(60f, 0f))
        assertEquals((60f - InspectSwipe.SLOP) / InspectSwipe.DISTANCE, right.progress, .001f)
        right.moved(200f, 4f)
        assertTrue(right.released())
        assertTrue(right.pinned)
    }

    // TBL-131, TBL-104: Close (or Back) closes a pinned Inspect; a new hold starts unpinned
    @Test fun closeAndANewHoldStartFresh() {
        var closed = 0
        val swipe = InspectSwipe().apply { onClose = { closed++ } }
        swipe.moved(-200f, 0f); swipe.released()
        swipe.close()
        assertEquals(1, closed)
        assertFalse(swipe.pinned)
        swipe.moved(-200f, 0f); swipe.released()
        swipe.begin()
        assertFalse(swipe.pinned)
        assertEquals(0f, swipe.progress)
    }
}

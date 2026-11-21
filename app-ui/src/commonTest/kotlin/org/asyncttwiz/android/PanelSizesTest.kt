package org.asyncttwiz.android

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** STK-222, STK-223: the rules of the large panels' one shared size. */
class PanelSizesTest {
    // STK-222 (owner 2026-11-21, E14): only a panel with something of its content cut off (it would scroll) has the handle, a tall
    // one whose content fits none; while it is dragged it keeps the handle, whatever is cut
    @Test fun onlyAPanelWithContentCutOffHasTheHandle() {
        assertFalse(PanelSizes.handle(content = 60f, shown = 60f, dragging = false))
        assertFalse(PanelSizes.handle(content = 400f, shown = 400f, dragging = false), "tall, but all shown")
        assertFalse(PanelSizes.handle(content = 400.3f, shown = 400f, dragging = false), "a part of a dp is rounding")
        assertTrue(PanelSizes.handle(content = 900f, shown = 440f, dragging = false), "cut off")
        assertTrue(PanelSizes.handle(content = 130f, shown = 120f, dragging = false), "a little cut off")
        assertTrue(PanelSizes.handle(content = 300f, shown = 300f, dragging = true), "dragged until all shows")
    }

    // STK-222: a panel whose content scrolls follows the drag both ways
    @Test fun aScrollingPanelFollowsTheDrag() {
        assertEquals(500f, PanelSizes.dragged(start = 440f, before = 440f, grow = 60f, content = 900f, room = 800f))
        assertEquals(300f, PanelSizes.dragged(start = 440f, before = 440f, grow = -140f, content = 900f, room = 800f))
    }

    // STK-223: never below the smallest size, never above three quarters of the room (unless it was already taller)
    @Test fun theSizeStaysWithinItsLimits() {
        assertEquals(PanelSizes.MIN, PanelSizes.dragged(start = 300f, before = 300f, grow = -1000f, content = 900f, room = 800f))
        assertEquals(600f, PanelSizes.dragged(start = 300f, before = 300f, grow = 1000f, content = 900f, room = 800f))
        assertEquals(650f, PanelSizes.dragged(start = 650f, before = 650f, grow = 100f, content = 900f, room = 800f))
    }

    // STK-223: a short panel shown whole doesn't lower the size the longer panels share when dragged outward, but grows the
    // size when dragged past it, and lowers it when dragged inward so its content scrolls
    @Test fun aShortPanelKeepsTheSharedSizeUnlessItIsCut() {
        assertEquals(440f, PanelSizes.dragged(start = 200f, before = 440f, grow = 80f, content = 200f, room = 800f))
        assertEquals(500f, PanelSizes.dragged(start = 200f, before = 440f, grow = 300f, content = 200f, room = 800f))
        assertEquals(150f, PanelSizes.dragged(start = 200f, before = 440f, grow = -50f, content = 200f, room = 800f))
    }

    // STK-223: a size read back from the device is a finite one within the limits
    @Test fun aKeptSizeIsReadWithinTheLimits() {
        assertNull(PanelSizes.kept(null))
        assertNull(PanelSizes.kept(Float.NaN))
        assertEquals(PanelSizes.MIN, PanelSizes.kept(10f))
        assertEquals(333f, PanelSizes.kept(333f))
    }
}

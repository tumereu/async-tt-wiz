package org.asyncttwiz.android

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** STK-222, STK-223: the rules of the large panels' one shared size. */
class PanelSizesTest {
    // STK-222: only a panel whose content is taller than the smallest size can be resized (it gets the handle)
    @Test fun onlyContentTallerThanTheSmallestSizeIsResizable() {
        assertFalse(PanelSizes.resizable(60f))
        assertFalse(PanelSizes.resizable(PanelSizes.MIN))
        assertTrue(PanelSizes.resizable(PanelSizes.MIN + 1f))
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

package org.asyncttwiz.android

import org.junit.Test
import kotlin.test.*

class HoldPreviewScrollTest {
    private val screen = 1000f

    // TBL-127, SYS-049
    @Test fun shortPreviewsScrollOneToOneAndLongOnesCoverTheirWholeRangeIn90PercentOfTheScreen() {
        assertEquals(1f, HoldPreviewScroll.gain(0f, screen))
        assertEquals(1f, HoldPreviewScroll.gain(500f, screen))
        assertEquals(1f, HoldPreviewScroll.gain(900f, screen))
        assertEquals(2f, HoldPreviewScroll.gain(1800f, screen), .0001f)
        // A drag over 90% of the screen height scrolls a long preview from top to bottom.
        for (range in listOf(300f, 900f, 2500f, 12_000f))
            assertEquals(range, HoldPreviewScroll.target(0f, range, .9f * screen, screen), .01f)
        // A short drag still moves the content at least as far as the finger.
        assertEquals(10f * 2f, HoldPreviewScroll.target(0f, 1800f, 10f, screen), .0001f)
        assertEquals(10f, HoldPreviewScroll.target(0f, 400f, 10f, screen), .0001f)
        assertEquals(1f, HoldPreviewScroll.gain(800f, 0f))
    }

    // TBL-127
    @Test fun movementPastEitherEndIsNotStoredSoTurningBackScrollsAtOnce() {
        val range = 1800f
        var value = HoldPreviewScroll.target(0f, range, 5_000f, screen)
        assertEquals(range, value)
        value = HoldPreviewScroll.target(value, range, -10f, screen)
        assertEquals(range - 20f, value, .0001f)
        value = HoldPreviewScroll.target(value, range, -5_000f, screen)
        assertEquals(0f, value)
        assertEquals(20f, HoldPreviewScroll.target(value, range, 10f, screen), .0001f)
    }
}

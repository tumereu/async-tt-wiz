package org.asyncttwiz.android

import androidx.compose.ui.unit.dp
import org.junit.Test
import kotlin.test.*

class PodPickTrayLayoutTest {
    @Test fun exactFitUsesOneRowAndOneDpLessUsesTwo() {
        val width = podPickTrayWidth(1, 40.dp) + PodPickTrayGap + podPickTrayWidth(2, 42.dp)
        assertTrue(podPickTraysFit(width, 1, 2, 40.dp, 42.dp))
        assertFalse(podPickTraysFit(width - 1.dp, 1, 2, 40.dp, 42.dp))
    }

    @Test fun requiredSlotsAndLabelWidthsDetermineFit() {
        assertTrue(podPickTraysFit(300.dp, 1, 2, 40.dp, 40.dp))
        assertFalse(podPickTraysFit(300.dp, 1, 4, 40.dp, 140.dp))
        assertFalse(podPickTraysFit(300.dp, 1, 8, 40.dp, 40.dp))
        assertTrue(podPickTraysFit(600.dp, 1, 8, 40.dp, 40.dp))
        assertTrue(podPickTraysFit(100.dp, 20, 0, 40.dp, 40.dp))
    }

    @Test fun trayReservesOnlyPaddingLabelAndRequiredCards() {
        assertEquals(48.dp + PodPickTrayCardWidth, podPickTrayWidth(1, 40.dp))
        assertEquals(48.dp + PodPickTrayCardWidth * 2 + PodPickTrayGap, podPickTrayWidth(2, 40.dp))
    }

    @Test fun spareWidthIsSplitInProportionToRequiredWidths() {
        val keep = podPickTrayWidth(1, 40.dp)
        val burn = podPickTrayWidth(4, 42.dp)
        val layout = podPickTrayLayout((keep + burn) * 1.5f + PodPickTrayGap, 1, 4, 40.dp, 42.dp)
        assertTrue(layout.singleRow)
        assertEquals(keep.value * 1.5f, layout.keepWidth.value, .001f)
        assertEquals(burn.value * 1.5f, layout.burnWidth.value, .001f)
        assertEquals(keep.value / burn.value, layout.keepWidth / layout.burnWidth, .001f)
    }

    @Test fun exactFitDoesNotExpandAndOverflowPreservesCompactSeparateRows() {
        val keep = podPickTrayWidth(1, 40.dp)
        val burn = podPickTrayWidth(8, 42.dp)
        val exact = keep + burn + PodPickTrayGap
        assertEquals(PodPickTrayLayout(keep, burn, true), podPickTrayLayout(exact, 1, 8, 40.dp, 42.dp))
        assertEquals(PodPickTrayLayout(keep, burn, false), podPickTrayLayout(exact - 1.dp, 1, 8, 40.dp, 42.dp))
        assertEquals(PodPickTrayLayout(keep, 200.dp, false), podPickTrayLayout(200.dp, 1, 8, 40.dp, 42.dp))
    }

    @Test fun keepOnlyTrayUsesAllAvailableWidthEvenWhenItMustPan() {
        assertEquals(PodPickTrayLayout(600.dp, 0.dp, true), podPickTrayLayout(600.dp, 1, 0, 40.dp, 40.dp))
        assertEquals(PodPickTrayLayout(100.dp, 0.dp, true), podPickTrayLayout(100.dp, 20, 0, 40.dp, 40.dp))
    }
}

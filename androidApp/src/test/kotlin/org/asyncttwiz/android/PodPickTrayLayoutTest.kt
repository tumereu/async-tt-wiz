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
}

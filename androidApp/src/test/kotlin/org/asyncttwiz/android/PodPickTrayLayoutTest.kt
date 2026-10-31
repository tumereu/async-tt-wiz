package org.asyncttwiz.android

import androidx.compose.ui.unit.dp
import org.junit.Test
import kotlin.test.*

class PodPickTrayLayoutTest {
    // DRF-065
    @Test fun exactFitUsesOneRowAndOneDpLessUsesTwo() {
        val width = podPickTrayWidth(1) + PodPickTrayGap + podPickTrayWidth(2)
        assertTrue(podPickTraysFit(width, 1, 2))
        assertFalse(podPickTraysFit(width - 1.dp, 1, 2))
    }

    @Test fun requiredSlotsDetermineFit() {
        assertTrue(podPickTraysFit(300.dp, 1, 2))
        assertFalse(podPickTraysFit(300.dp, 1, 8))
        assertTrue(podPickTraysFit(600.dp, 1, 8))
        assertTrue(podPickTraysFit(100.dp, 20, 0))
    }

    @Test fun trayReservesOnlyPaddingAndRequiredCardsNotTheCountChip() {
        assertEquals(8.dp + PodPickTrayCardWidth, podPickTrayWidth(1))
        assertEquals(8.dp + PodPickTrayCardWidth * 2 + PodPickTrayGap, podPickTrayWidth(2))
    }

    // DRF-054
    @Test fun librarianPodsKeepAtLeastTwoCardsWide() {
        assertEquals(1, podPickKeepSlots(1, librarians = false))
        assertEquals(2, podPickKeepSlots(1, librarians = true))
        assertEquals(3, podPickKeepSlots(3, librarians = true))
        val plain = podPickTrayLayout(300.dp, podPickKeepSlots(1, false), 2)
        val librarian = podPickTrayLayout(300.dp, podPickKeepSlots(1, true), 2)
        assertTrue(librarian.keepWidth > plain.keepWidth)
        val narrow = podPickTrayWidth(2) + PodPickTrayGap + podPickTrayWidth(8) - 1.dp
        assertEquals(podPickTrayWidth(2), podPickTrayLayout(narrow, podPickKeepSlots(1, true), 8).keepWidth)
    }

    // DRF-065
    @Test fun spareWidthIsSplitInProportionToRequiredWidths() {
        val keep = podPickTrayWidth(1)
        val burn = podPickTrayWidth(4)
        val layout = podPickTrayLayout((keep + burn) * 1.5f + PodPickTrayGap, 1, 4)
        assertTrue(layout.singleRow)
        assertEquals(keep.value * 1.5f, layout.keepWidth.value, .001f)
        assertEquals(burn.value * 1.5f, layout.burnWidth.value, .001f)
        assertEquals(keep.value / burn.value, layout.keepWidth / layout.burnWidth, .001f)
    }

    @Test fun exactFitDoesNotExpandAndOverflowPreservesCompactSeparateRows() {
        val keep = podPickTrayWidth(1)
        val burn = podPickTrayWidth(8)
        val exact = keep + burn + PodPickTrayGap
        assertEquals(PodPickTrayLayout(keep, burn, true), podPickTrayLayout(exact, 1, 8))
        assertEquals(PodPickTrayLayout(keep, burn, false), podPickTrayLayout(exact - 1.dp, 1, 8))
        assertEquals(PodPickTrayLayout(keep, 200.dp, false), podPickTrayLayout(200.dp, 1, 8))
    }

    @Test fun keepOnlyTrayUsesAllAvailableWidthEvenWhenItMustPan() {
        assertEquals(PodPickTrayLayout(600.dp, 0.dp, true), podPickTrayLayout(600.dp, 1, 0))
        assertEquals(PodPickTrayLayout(100.dp, 0.dp, true), podPickTrayLayout(100.dp, 20, 0))
    }
}

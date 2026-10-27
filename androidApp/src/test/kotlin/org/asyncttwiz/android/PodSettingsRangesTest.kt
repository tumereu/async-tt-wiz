package org.asyncttwiz.android

import org.asyncttwiz.protocol.*
import org.junit.Test
import kotlin.test.*

class PodSettingsRangesTest {
    @Test fun rangesDeriveFromPackAndKeepBurnAndParallelCounts() {
        val s = DraftSettings(cardsPerPack = 5, keep = 2, burn = 1, discardRemainingAt = 4,
            packsPerPlayer = 6, packsOpenAtOnce = 3, grids = 12, gridsOpenAtOnce = 2)
        assertEquals(1..4, PodSetting.KEEP.range(s))
        assertEquals(0..3, PodSetting.BURN.range(s))
        assertEquals(4..30, PodSetting.PACK_SIZE.range(s))
        assertEquals(0..5, PodSetting.DISCARD.range(s))
        assertEquals(3..30, PodSetting.PACKS.range(s))
        assertEquals(1..6, PodSetting.OPEN_PACKS.range(s))
        assertEquals(2..60, PodSetting.GRIDS.range(s))
        assertEquals(1..12, PodSetting.OPEN_GRIDS.range(s))
        assertEquals(0..Int.MAX_VALUE, PodSetting.SIDEBOARD.range(s))
    }

    @Test fun validationRejectsMissingOverflowAndOutOfRangeAndAcceptsBothBounds() {
        val s = DraftSettings()
        for (field in PodSetting.entries) {
            val range = field.range(s)
            assertTrue(field.valid(range.first.toString(), s))
            assertTrue(field.valid(range.last.toString(), s))
            for (input in listOf("", "-1", "1.5", "invalid", "2147483648")) assertFalse(field.valid(input, s))
            assertFalse(field.valid((range.first - 1).toString(), s))
            if (range.last < Int.MAX_VALUE) assertFalse(field.valid((range.last + 1).toString(), s))
        }
    }

    @Test fun everyOfferedValuePreservesServerValidationForItsMode() {
        for (settings in listOf(DraftSettings(), DraftSettings.glimpse(), DraftSettings.grid())) {
            val fields = if (settings.mode == DraftMode.GRID) listOf(PodSetting.GRIDS, PodSetting.OPEN_GRIDS, PodSetting.SIDEBOARD)
                else PodSetting.entries.filter { it !in listOf(PodSetting.GRIDS, PodSetting.OPEN_GRIDS) }
            for (field in fields) {
                val range = field.range(settings)
                for (number in listOf(range.first, range.last)) assertTrue(field.apply(settings, number).validate(2).isEmpty())
            }
        }
        assertNull(PodSetting.SIDEBOARD.apply(DraftSettings(sideboardLimit = 3), null).sideboardLimit)
    }
}

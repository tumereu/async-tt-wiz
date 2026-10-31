package org.asyncttwiz.android

import org.asyncttwiz.protocol.*
import org.junit.Test
import kotlin.test.*

class PodSettingsRangesTest {
    // DRF-023
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

    // DRF-022
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
        val sources = listOf(PodSource("Cube", 540), PodSource(kind = PodSourceKind.SET, boosterKind = SetBoosterKind.APPROXIMATE),
            PodSource(kind = PodSourceKind.SET, boosterKind = SetBoosterKind.RECIPE_BASED))
        for (mode in DraftMode.entries) for (source in sources) {
            val settings = DraftSettings.defaults(mode)
            for (field in podSettingFields(settings, source)) {
                val range = field.range(settings)
                for (number in listOf(range.first, range.last)) assertTrue(field.apply(settings, number).validate(2).isEmpty(), "$mode $field $number")
            }
        }
        assertNull(PodSetting.SIDEBOARD.apply(DraftSettings(sideboardLimit = 3), null).sideboardLimit)
        assertNull(PodSetting.TABLE_OPEN.apply(DraftSettings.rochester().copy(tablePacksOpen = 3), null).tablePacksOpen)
    }

    // DRF-022, DRF-024: each engine's tiles, the pack size only where the source doesn't fix it.
    @Test fun eachEngineShowsItsOwnTiles() {
        val cube = PodSource("Cube", 540)
        val recipe = PodSource(kind = PodSourceKind.SET, boosterKind = SetBoosterKind.RECIPE_BASED)
        fun fields(settings: DraftSettings, source: PodSource = cube) = podSettingFields(settings, source)
        assertEquals(listOf(PodSetting.PACKS, PodSetting.PACK_SIZE, PodSetting.TABLE_OPEN, PodSetting.SIDEBOARD), fields(DraftSettings.rochester()))
        assertEquals(listOf(PodSetting.PACKS, PodSetting.TABLE_OPEN, PodSetting.SIDEBOARD), fields(DraftSettings.rochester(), recipe))
        assertEquals(listOf(PodSetting.CARDS_EACH, PodSetting.SIDEBOARD), fields(DraftSettings.rotisserie(), recipe))
        assertEquals(listOf(PodSetting.PACKS, PodSetting.PACK_SIZE, PodSetting.PILES, PodSetting.SIDEBOARD), fields(DraftSettings.winston()))
        assertEquals(listOf(PodSetting.PACKS, PodSetting.PILES, PodSetting.SIDEBOARD), fields(DraftSettings.winchester(), recipe))
        assertEquals(listOf(PodSetting.CARDS_EACH, PodSetting.SIDEBOARD), fields(DraftSettings.sealed()))
        assertEquals(listOf(PodSetting.BOOSTERS, PodSetting.SIDEBOARD), fields(DraftSettings.sealed(), recipe))
        assertEquals(1..15, PodSetting.PACKS.range(DraftSettings.winston()))
        assertEquals(5..30, PodSetting.PACK_SIZE.range(DraftSettings.rochester()))
        assertEquals(2..6, PodSetting.PILES.range(DraftSettings.winston()))
        assertEquals(2..8, PodSetting.PILES.range(DraftSettings.winchester()))
        assertEquals(1..24, PodSetting.TABLE_OPEN.range(DraftSettings.rochester()))
        assertEquals(5..225, PodSetting.CARDS_EACH.range(DraftSettings.rotisserie()))
        assertEquals(1..30, PodSetting.BOOSTERS.range(DraftSettings.sealed()))
        assertEquals("1 each", PodSetting.TABLE_OPEN.emptyText)
    }

    // DRF-030, DRF-141
    @Test fun playerLimitTextsNameTheEngineAndCount() {
        fun account(n: Int) = AccountIdentity("id-$n", "P$n")
        fun detail(settings: DraftSettings, members: Int, invited: Int = 0, armed: Boolean = false) = PodDetail("pod", "Pod",
            PodPhase.SETUP, account(0), settings, PodSource("Cube", 540), DraftSupply(0, 540),
            List(members) { PodMember(account(it)) }, List(invited) { account(100 + it) }, updatedAt = "", autoStart = armed)
        assertNull(podPlayerLimitText(detail(DraftSettings.grid(), 4)))
        assertEquals("Grid takes at most 4 players; 5 have joined.", podPlayerLimitText(detail(DraftSettings.grid(), 5)))
        assertFalse(podCanStart(detail(DraftSettings.grid(), 5)))
        assertEquals("Winston takes at most 4 players; 5 are joined or invited.",
            podPlayerLimitText(detail(DraftSettings.winston(), 3, invited = 2)))
        assertNull(podPlayerLimitText(detail(DraftSettings.rotisserie(), 3, invited = 5)))
        assertEquals("Too many players for Winston (at most 4); auto-start is off",
            podAutoStartOffText(PodAutoStartOff("too_many_players"), DraftMode.WINSTON))
    }
}

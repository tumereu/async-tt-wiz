package org.asyncttwiz.android

import org.asyncttwiz.protocol.*
import kotlin.test.Test
import kotlin.test.*

class PodCustomBoosterTest {
    private fun pack(cards: Int, pick: Int) = DraftPrivatePack("pack", 1, (1..cards).map { DraftCopy("$it", "definition") }, pick)
    private val settings = DraftSettings.customBooster().copy(passes = listOf(DraftPass(2, 1), DraftPass(1, 1), DraftPass(1, 0)))

    // DRF-020, DRF-262, DRF-296: the mode list names Custom booster after Booster; its tiles leave keep, burn and discard to the passes
    @Test fun modeAndTiles() {
        assertEquals(listOf(DraftMode.BOOSTER, DraftMode.CUSTOM_BOOSTER), PodModes.take(2))
        assertEquals("Custom booster · 2–8 players", podModeOption(DraftMode.CUSTOM_BOOSTER))
        assertEquals(listOf(PodSetting.PACKS, PodSetting.PACK_SIZE, PodSetting.OPEN_PACKS, PodSetting.LIBRARIANS, PodSetting.SIDEBOARD),
            podSettingFields(DraftSettings.customBooster(), PodSource("Cube", 540)))
        assertEquals(1..30, PodSetting.PACK_SIZE.range(DraftSettings.customBooster()))
        // Switching to it loads its default passes, keeping the sideboard limit and Librarians (DRF-020)
        val switched = podModeSettings(DraftMode.CUSTOM_BOOSTER, DraftSettings.booster().copy(sideboardLimit = 5, librarians = 2))
        assertEquals(List(7) { DraftPass(1, 1) }, switched.passes)
        assertEquals(5, switched.sideboardLimit); assertEquals(2, switched.librarians)
        assertEquals(DraftPass(3, 1), settings.withPass(0, DraftPass(3, 1)).passes.first())
    }

    // DRF-263, DRF-264: passes in words
    @Test fun passTexts() {
        assertEquals("Pick 2 · Burn 1", podPassText(DraftPass(2, 1)))
        assertEquals("Pick 1", podPassText(DraftPass(1, 0)))
        assertEquals("Pick 2 · Burn 1, Pick 1 · Burn 1 ×6", podPassesSummary(listOf(DraftPass(2, 1)) + List(6) { DraftPass(1, 1) }))
        assertEquals("Pick 1 ×2, Pick 2, Pick 1", podPassesSummary(listOf(DraftPass(1), DraftPass(1), DraftPass(2), DraftPass(1))))
    }

    // DRF-260, DRF-263: the pick screen asks what the pack's pass says
    @Test fun pickRulesFollowThePass() {
        val first = PodPickRules(pack(11, 1), settings)
        assertEquals(listOf(2, 1, 1), listOf(first.requiredKeeps, first.manualBurns, first.requiredBurns))
        assertEquals("Pass 1/3 · Pick 2 of 11 · Burn 1", podPickPassText(first, settings))
        val second = PodPickRules(pack(8, 2), settings)
        assertEquals(listOf(1, 1), listOf(second.requiredKeeps, second.manualBurns))
        assertEquals("Pass 2/3 · Pick 1 of 8 · Burn 1", podPickPassText(second, settings))
        // DRF-261: the last pass throws the rest away
        val last = PodPickRules(pack(5, 3), settings)
        assertEquals(listOf(1, 0), listOf(last.requiredKeeps, last.manualBurns))
        assertEquals("Pass 3/3 · Pick 1 of 5 · the rest are thrown away", podPickPassText(last, settings))
        assertEquals("Pass 3/3 · Pick 1 of 1", podPickPassText(PodPickRules(pack(1, 3), settings), settings))
        // Near a pack's end only the picks are chosen; the rest burn by themselves (DRF-045)
        val small = PodPickRules(pack(3, 1), settings)
        assertTrue(small.automaticBurns); assertEquals(0, small.manualBurns); assertEquals(1, small.requiredBurns)
        // A spent Librarian adds a card and a pick
        assertEquals("Pass 1/3 · Pick 3 of 12 · Burn 1", podPickPassText(PodPickRules(pack(11, 1), settings, listOf("lib")), settings))
        // Booster has no pass line
        assertNull(podPickPassText(PodPickRules(pack(11, 1), DraftSettings.booster()), DraftSettings.booster()))
    }
}

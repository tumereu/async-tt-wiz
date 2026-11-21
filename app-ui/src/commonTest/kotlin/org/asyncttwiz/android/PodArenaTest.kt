package org.asyncttwiz.android

import org.asyncttwiz.protocol.*
import kotlin.test.Test
import kotlin.test.assertEquals

/** Arena's setup tiles and summary, its pick line and its history headers. */
class PodArenaTest {
    private val base = DraftSettings.arena()

    // DRF-281, DRF-282: Arena's tiles, their ranges and "Never"; "Initial rares" (owner 2026-11-21) before "Rare every", 0–120
    @Test fun tilesRangesAndNever() {
        assertEquals(listOf(PodSetting.PICKS, PodSetting.OFFER_CARDS, PodSetting.TAKE, PodSetting.INITIAL_RARES, PodSetting.RARE_EVERY,
            PodSetting.UNCOMMON_EVERY, PodSetting.MYTHIC, PodSetting.SIDEBOARD), podSettingFields(base, PodSource()))
        assertEquals(listOf("45", "5", "1", "1", "10", "4", "1/8"),
            listOf(PodSetting.PICKS, PodSetting.OFFER_CARDS, PodSetting.TAKE, PodSetting.INITIAL_RARES, PodSetting.RARE_EVERY,
                PodSetting.UNCOMMON_EVERY, PodSetting.MYTHIC).map { it.text(base) })
        assertEquals("Initial rares", PodSetting.INITIAL_RARES.label)
        assertEquals(0..120, PodSetting.INITIAL_RARES.range(base)); assertEquals("0–120", PodSetting.INITIAL_RARES.limit(base))
        assertEquals(null, PodSetting.INITIAL_RARES.emptyText)
        assertEquals(3, PodSetting.INITIAL_RARES.apply(base, 3).initialRares)
        assertEquals(0, PodSetting.INITIAL_RARES.apply(base, 0).initialRares)
        assertEquals(1..4, PodSetting.TAKE.range(base))
        assertEquals(2..15, PodSetting.OFFER_CARDS.range(base))
        assertEquals(4..15, PodSetting.OFFER_CARDS.range(base.copy(keep = 3)))
        assertEquals(1..120, PodSetting.PICKS.range(base))
        assertEquals(1..120, PodSetting.RARE_EVERY.range(base)); assertEquals(1..120, PodSetting.UNCOMMON_EVERY.range(base))
        val never = PodSetting.MYTHIC.apply(PodSetting.RARE_EVERY.apply(base, null), null)
        assertEquals(null, never.rareEvery); assertEquals("Never", PodSetting.MYTHIC.text(never))
        assertEquals(12, PodSetting.UNCOMMON_EVERY.apply(base, 12).uncommonEvery)
        assertEquals("Arena · 2–8 players", podModeOption(DraftMode.ARENA))
        assertEquals(DraftMode.ARENA, PodModes.last())
        assertEquals("Arena · My cube · 45 picks, 5 cards, take 1", podPresetSummary(base, "My cube", set = false))
    }

    // DRF-287, DRF-285, DRF-284: the summary's format, rarity counts, a cube's Reuse cards and each rarity too short to fill an offer
    @Test fun summaryTellsTheFormatAndShortRarities() {
        assertEquals(listOf("Arena: 45 picks, 5 cards, take 1", "5 rare, 9 uncommon, 31 common picks · each player ends with 45 cards",
            "Reuse cards off: a card you pick is never offered to you again; other players' picks never change your offers"),
            podArenaSummary(base, PodSource("My cube", 100)))
        assertEquals("Reuse cards on: a card you pick may be offered to you again", podArenaSummary(base.copy(reuseCards = true), PodSource("My cube", 100))[2])
        val short = PodSource("My cube", 100, rarities = ArenaCounts(common = 80, uncommon = 15, rare = 3, mythic = 0))
        assertEquals(listOf("No mythic rares: filled up from rares", "Only 3 rares for offers of 5: filled up from uncommons"),
            podArenaSummary(base, short).drop(3))
        // Ignore rarity: no rarity lines
        assertEquals(listOf("Arena: 1 pick, 3 cards, take 2", "Rarity ignored · each player ends with 2 cards"),
            podArenaSummary(base.copy(packsPerPlayer = 1, cardsPerPack = 3, keep = 2, ignoreRarity = true), short).take(2))
        assertEquals(3, podArenaSummary(base.copy(ignoreRarity = true), short).size)
        // A set's offers always go by rarity, and a set short of commons fills from above
        val set = PodSource(kind = PodSourceKind.SET, rarities = ArenaCounts(common = 0, uncommon = 40, rare = 20, mythic = 8))
        assertEquals(listOf("No commons: filled up from uncommons"), podArenaSummary(base.copy(ignoreRarity = true), set).drop(2))
        assertEquals("Arena: 45 picks, 5 cards, take 1", podArenaFormat(ArenaFormat(45, 5, 1)))
        // DRF-281: initial rares count among the rare picks; none with 0
        assertEquals("7 rare, 9 uncommon, 29 common picks · each player ends with 45 cards",
            podArenaSummary(base.copy(initialRares = 3), PodSource("My cube", 100))[1])
        assertEquals("4 rare, 9 uncommon, 32 common picks · each player ends with 45 cards",
            podArenaSummary(base.copy(initialRares = 0), PodSource("My cube", 100))[1])
    }

    // DRF-288, DRF-289: the pick line and the history header
    @Test fun pickLineAndHistoryHeader() {
        assertEquals("Pick 12 of 45 · Uncommon · take 1", podArenaPickLine(base, 12, PodSourceKind.CUBE, 5))
        assertEquals("Pick 1 of 45 · Rare · take 1", podArenaPickLine(base, 1, PodSourceKind.CUBE, 5))
        assertEquals("Pick 10 of 45 · Rare · take 1", podArenaPickLine(base, 10, PodSourceKind.SET, 5))
        assertEquals("Pick 1 of 45 · take 1", podArenaPickLine(base.copy(ignoreRarity = true), 1, PodSourceKind.CUBE, 5))
        assertEquals("Pick 3 of 45 · Common · take 1", podArenaPickLine(base.copy(keep = 3), 3, PodSourceKind.SET, 1))
        assertEquals("Pick 12 · Uncommon", podArenaHistoryHeader(base, 12, PodSourceKind.CUBE))
        assertEquals("Pick 12", podArenaHistoryHeader(base.copy(ignoreRarity = true), 12, PodSourceKind.CUBE))
        assertEquals("Pick 4", podPackRowLabel(base, 3))
    }
}

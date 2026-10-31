package org.asyncttwiz.android

import org.asyncttwiz.protocol.*
import org.junit.Test
import kotlin.test.*

class PodStatsModelsTest {
    // DRF-111
    @Test fun compactChartGroupsSevenPlusAndNormalizesDemandAndSourcesIndependently() {
        val stats = podStats(PodDeckBuild(), emptyMap(), emptyMap()).copy(curve = mapOf(
            0.0 to PodStatsCurveBucket(creatures = 1), 1.5 to PodStatsCurveBucket(noncreatures = 2),
            7.0 to PodStatsCurveBucket(2, 1), 9.0 to PodStatsCurveBucket(1, 3)),
            pips = DraftColor.entries.associateWith { if (it == DraftColor.W) 3.0 else if (it == DraftColor.U) 1.0 else 0.0 },
            sources = DraftColor.entries.associateWith { if (it == DraftColor.W || it == DraftColor.U) 2L else 0L })
        assertEquals(listOf("0", "1", "2", "3", "4", "5", "6", "7+"), stats.curveBuckets().keys.toList())
        assertEquals(PodStatsCurveBucket(3, 4), stats.curveBuckets()["7+"])
        assertEquals(PodStatsCurveBucket(noncreatures = 2), stats.curveBuckets()["1"])
        assertEquals(PodStatsCurveBucket(), stats.curveBuckets()["6"])
        assertEquals(.75, podStatsShares(stats.pips)[DraftColor.W])
        assertEquals(.25, podStatsShares(stats.pips)[DraftColor.U])
        assertEquals(.5, podStatsShares(stats.sources)[DraftColor.W])
        assertEquals(.5, podStatsShares(stats.sources)[DraftColor.U])
        assertEquals(1.0, podStatsShares(stats.pips).values.sum())
        assertEquals(1.0, podStatsShares(stats.sources).values.sum())
        val empty = podStats(PodDeckBuild(), emptyMap(), emptyMap())
        assertTrue(podStatsShares(empty.pips).values.all { it == 0.0 })
        assertTrue(podStatsShares(empty.sources).values.all { it == 0.0 })
        assertTrue(podStatsShares(empty.colors).values.all { it == 0.0 })
    }
    private fun card(name: String, type: String, cost: String = "", text: String = "") =
        CardDefinition(name, listOf(DefinitionFace("front", name, typeLine = type, manaCost = cost, rulesText = text)))

    // DRF-114
    @Test fun coloredPipsHandleHybridPhyrexianHalfPipsAndIgnoreGenericXAndBack() {
        val pips = podStatsPips("{3}{X}{W/U}{B/P}{2/R}{G}{G/U/P}{HW}{C}{S} // {R}{R}")
        assertEquals(1.0, pips[DraftColor.W])
        assertEquals(1.0, pips[DraftColor.U])
        assertEquals(1.0, pips[DraftColor.B])
        assertEquals(0.5, pips[DraftColor.R])
        assertEquals(1.5, pips[DraftColor.G])
    }

    // DRF-116
    @Test fun pipPatternsKeepColoredAndColorlessSymbolsInPrintedOrderAndDropGenericXAndSnow() {
        assertEquals("{U}", podStatsPipPattern("{3}{U}"))
        assertEquals("{R/W}{R/W}", podStatsPipPattern("{1}{r/w}{R/W}"))
        assertEquals("{U}{U}{B}", podStatsPipPattern("{X}{U}{U}{B}"))
        assertEquals("{C}{2/G}{W/P}", podStatsPipPattern("{10}{C}{2/G}{S}{W/P}{Y}{Z}"))
        assertEquals("{G}", podStatsPipPattern("{G} // {R}{R}"))
        assertEquals("", podStatsPipPattern("{4}"))
        assertEquals("", podStatsPipPattern(""))
        assertEquals(listOf("0", "4", "½", "4½", "1.3"), listOf(0.0, 4.0, .5, 4.5, 4.0 / 3).map(::podStatsPipText))
    }

    // DRF-116
    @Test fun mainCardsGroupByPipPatternMostCommonFirstAndLandsWithoutColorAreCounted() {
        val definitions = mapOf("u" to card("Bird", "Creature", "{1}{U}"), "u2" to card("Fish", "Instant", "{U}"),
            "rw" to card("Boros", "Creature", "{R/W}{R/W}"), "ub" to card("Grixis", "Sorcery", "{U}{U}{B}"),
            "art" to card("Artifact", "Artifact", "{3}"), "waste" to card("Waste", "Land", text = "{T}: Add {C}."),
            "dual" to card("Dual", "Land — Island Swamp"), "back" to card("Back", "Land"))
        val copies = listOf("u" to "u", "u-b" to "u", "u2" to "u2", "u2-b" to "u2", "rw" to "rw", "rw-b" to "rw", "ub" to "ub",
            "art" to "art", "waste" to "waste", "dual" to "dual", "side" to "back").associate { (id, digest) -> id to DraftCopy(id, digest) }
        val build = PodDeckBuild(copies.keys.filter { it != "side" }.map { PodDeckCopy(it) } +
            PodDeckCopy("side", PodDeckCategory.SIDE), PodDeckBasics(island = 2))
        val stats = podStats(build, copies, definitions)
        assertEquals(listOf("{U}" to 4, "{R/W}{R/W}" to 2, "{U}{U}{B}" to 1), stats.pipPatterns)
        assertEquals(1, stats.noPips)
        assertEquals(1, stats.colorlessLands) // Waste; Dual has colors, basics always do, Side is ignored
        assertEquals(3L, stats.sources[DraftColor.U]); assertEquals(1L, stats.sources[DraftColor.B])
        assertEquals(0L, stats.sources[DraftColor.R]) // Nonland costs never count as sources.
    }

    // DRF-112
    @Test fun typeCountsComeFromFrontTypeLineWordsOfMainCardsAndBasicsMostCommonFirst() {
        val definitions = mapOf(
            "golem" to card("Golem", "Legendary Artifact Creature — Golem"),
            "elf" to card("Elf", "Creature — Elf"),
            "adventure" to card("Giant", "Creature — Giant // Sorcery — Adventure"),
            "hyphen" to card("Dual", "Land - Island Swamp"),
            "ench" to card("Aura", "Enchantment — Aura"),
            "side" to card("Side", "Instant"),
        )
        val copies = listOf("g" to "golem", "e1" to "elf", "e2" to "elf", "a" to "adventure", "h" to "hyphen", "n" to "ench",
            "s" to "side", "t" to "side", "m" to "missing").associate { (id, digest) -> id to DraftCopy(id, digest) }
        val build = PodDeckBuild(copies.keys.map { PodDeckCopy(it, when (it) {
            "s" -> PodDeckCategory.SIDE
            "t" -> PodDeckCategory.TRASH
            else -> PodDeckCategory.MAIN
        }) }, PodDeckBasics(plains = 2, forest = 1), sideBasics = PodDeckBasics(island = 5))
        val stats = podStats(build, copies, definitions)
        assertEquals(listOf("Creature" to 4L, "Land" to 4L, "Basic" to 3L, "Artifact" to 1L, "Enchantment" to 1L, "Legendary" to 1L), stats.types)
        assertEquals(1, stats.missingDefinitions)
        assertTrue(stats.types.none { it.first in setOf("Instant", "Sorcery", "Golem", "Island", "-", "—") })
        assertEquals(listOf("Basic", "Snow", "Land"), podStatsTypeWords("Basic  Snow Land — Forest"))
        assertEquals(emptyList(), podStatsTypeWords(""))
        assertTrue(podStats(PodDeckBuild(), emptyMap(), emptyMap()).types.isEmpty())
    }

    // DRF-118
    @Test fun notesSanitizeAndCountersStayWithinTheirRange() {
        val notes = PodDeckNotes("a\r\nb\tc\u0000", listOf(1, 2, 3)).podNotesSanitized()
        assertEquals("a\nbc", notes.text)
        assertEquals(listOf(1, 2, 3, 0, 0, 0, 0, 0), notes.counters)
        assertTrue(notes.valid)
        assertEquals(PodDeckNotes.MAX_COUNTER, notes.podNotesAdjust(2, 500).counters[2])
        assertEquals(0, notes.podNotesAdjust(0, -3).counters[0])
        assertFalse(PodDeckNotes(counters = List(7) { 0 }).valid)
        assertFalse(PodDeckNotes("x".repeat(PodDeckNotes.MAX_TEXT + 1)).valid)
    }

    // DRF-111, DRF-115
    @Test fun mainIncludesBasicsAndDuplicateCopiesButNotSideTrashOrNonlandSources() {
        val definitions = mapOf(
            "creature" to card("Elf", "Creature — Elf", "{G}", "{T}: Add {G}."),
            "spell" to card("Spell", "Instant", "{X}{2}{W/U}{B/P}"),
            "dual" to card("Dual", "Land — Plains Island"),
            "textLand" to card("Other", "Land", text = "{B}, {T}: Add {R}{R} or {G}."),
            "creatureLand" to card("Dryad", "Land Creature — Forest Dryad"),
        )
        val copies = listOf(DraftCopy("c1", "creature"), DraftCopy("c2", "creature"), DraftCopy("s", "spell"),
            DraftCopy("d", "dual"), DraftCopy("t", "textLand"), DraftCopy("l", "creatureLand"), DraftCopy("side", "spell"), DraftCopy("trash", "dual")).associateBy { it.id }
        val build = PodDeckBuild(copies.keys.map { PodDeckCopy(it, when (it) {
            "side" -> PodDeckCategory.SIDE
            "trash" -> PodDeckCategory.TRASH
            else -> PodDeckCategory.MAIN
        }) }, PodDeckBasics(plains = 3, island = 2, swamp = 1, mountain = 4, forest = 5))
        val stats = podStats(build, copies, definitions)
        assertEquals(mapOf(1.0 to PodStatsCurveBucket(creatures = 2), 4.0 to PodStatsCurveBucket(noncreatures = 1)), stats.curve)
        assertEquals(2, stats.creatures)
        assertEquals(1, stats.noncreatures)
        assertEquals(18L, stats.lands)
        assertEquals(mapOf(DraftColor.W to 0.5, DraftColor.U to 0.5, DraftColor.B to 1.0, DraftColor.R to 0.0, DraftColor.G to 2.0), stats.pips)
        assertEquals(mapOf(DraftColor.W to 4L, DraftColor.U to 3L, DraftColor.B to 1L, DraftColor.R to 5L, DraftColor.G to 7L), stats.sources)
        assertEquals(0, stats.missingDefinitions)
    }

    @Test fun normalizedCostFallsBackToSourceDataAndOnlyFrontAffectsCurvePipsAndLandType() {
        val card = CardDefinition("Front", listOf(
            DefinitionFace("front", "Front", typeLine = "Sorcery", sourceData = """{"manaCost":"{X}{3}{U/P}{W/U}"}"""),
            DefinitionFace("back", "Back", typeLine = "Land — Mountain", manaCost = "{R}{R}{R}"),
        ))
        val stats = podStats(PodDeckBuild(listOf(PodDeckCopy("copy"))), mapOf("copy" to DraftCopy("copy", "digest")), mapOf("digest" to card))
        assertEquals(mapOf(5.0 to PodStatsCurveBucket(noncreatures = 1)), stats.curve)
        assertEquals(0L, stats.lands)
        assertEquals(1.5, stats.pips[DraftColor.U])
        assertEquals(0.5, stats.pips[DraftColor.W])
        assertEquals(0.0, stats.pips[DraftColor.R])
        assertTrue(stats.sources.values.all { it == 0L })
        val explicit = card.copy(faces = listOf(card.faces.first().copy(manaCost = "{G}")))
        assertEquals("{G}", podStatsFrontCost(explicit))
        assertEquals("", podStatsFrontCost(card.copy(faces = listOf(card.faces.first().copy(sourceData = "invalid")))))
    }

    // DRF-115
    @Test fun landSourcesCountAColorOnceAndExcludeActivationCostsAndNonlands() {
        assertEquals(setOf(DraftColor.U, DraftColor.B), podStatsLandColors(card("Land", "Land", text = "{R}, {T}: Add {U}{U} or {B}. Spend {G} only on creatures.")))
        assertEquals(DraftColor.entries.toSet(), podStatsLandColors(card("Rainbow", "Land", text = "{T}: Add one mana of any color.")))
        assertEquals(emptySet(), podStatsLandColors(card("Elf", "Creature", text = "{T}: Add {G}.")))
        assertEquals(setOf(DraftColor.W, DraftColor.G), podStatsLandColors(card("Typed", "Land — Plains Forest")))
    }

    @Test fun missingDefinitionsAreReportedAndEmptyPoolStillCountsBasics() {
        val stats = podStats(PodDeckBuild(listOf(PodDeckCopy("unknown")), PodDeckBasics(island = 40)), emptyMap(), emptyMap())
        assertEquals(40L, stats.lands)
        assertEquals(40L, stats.sources[DraftColor.U])
        assertEquals(1, stats.missingDefinitions)
        assertTrue(stats.curve.isEmpty())
        assertTrue(stats.pips.values.all { it == 0.0 })
        assertTrue(stats.colors.values.all { it == 0 })
    }

    // DRF-117
    @Test fun colorsCountEveryMainNonlandCopyByIdentityAndExcludeLandsBasicsSideAndTrash() {
        val definitions = DraftColor.entries.associate { color -> color.name to card(color.name, "Creature", "{${color.name}}") } + mapOf(
            "gold" to card("Gold", "Instant", "{W/U}"),
            "colorless" to card("Rock", "Artifact", "{3}"),
            "land" to card("Dryad", "Land Creature — Forest"),
        )
        val copies = (definitions.keys + listOf("duplicate", "side", "trash", "missing")).associateWith { id ->
            DraftCopy(id, if (id in listOf("duplicate", "side", "trash")) "gold" else id)
        }
        val build = PodDeckBuild(copies.keys.map { id -> PodDeckCopy(id, when (id) {
            "side" -> PodDeckCategory.SIDE
            "trash" -> PodDeckCategory.TRASH
            else -> PodDeckCategory.MAIN
        }) }, PodDeckBasics(plains = 20))
        val stats = podStats(build, copies, definitions)
        assertEquals(PodStatsColor.entries.associateWith { if (it == PodStatsColor.MULTICOLOR) 2 else 1 }, stats.colors)
        assertEquals(8, stats.colors.values.sum())
        assertEquals(.25, podStatsShares(stats.colors)[PodStatsColor.MULTICOLOR])
        assertEquals(.125, podStatsShares(stats.colors)[PodStatsColor.COLORLESS])
        assertEquals(1, stats.missingDefinitions)
    }

    // DRF-117
    @Test fun colorIdentityUsesCatalogIdentityIncludingExplicitColorlessInsteadOfOnlyManaCost() {
        val artifact = card("Artifact", "Artifact", "{2}", "{T}: Add {G}.")
        val recorded = artifact.copy(faces = listOf(artifact.faces.single().copy(sourceData = """{"colorIdentity":["U","R"]}""")))
        assertEquals(PodStatsColor.MULTICOLOR, podStatsColorIdentity(recorded, setOf(DraftColor.G)))
        val colorless = recorded.copy(faces = listOf(recorded.faces.single().copy(sourceData = """{"colorIdentity":[]}""")))
        assertEquals(PodStatsColor.COLORLESS, podStatsColorIdentity(colorless, setOf(DraftColor.G)))
        assertEquals(PodStatsColor.G, podStatsColorIdentity(artifact, emptySet()))
        assertEquals(PodStatsColor.B, podStatsColorIdentity(card("Indicator", "Creature"), setOf(DraftColor.B)))
        val twoFaces = card("Front", "Creature", "{U}").copy(faces = listOf(
            card("Front", "Creature", "{U}").faces.single(), card("Back", "Creature", "{R}").faces.single()))
        assertEquals(PodStatsColor.MULTICOLOR, podStatsColorIdentity(twoFaces, emptySet()))
        assertEquals(PodStatsColor.W, podStatsColorIdentity(card("Extort", "Creature", "{W}", "Extort (You may pay {W/B}.)"), emptySet()))
    }
}

package org.asyncttwiz.android

import org.asyncttwiz.protocol.*
import org.junit.Test
import kotlin.test.*

class PodStatsModelsTest {
    private fun card(name: String, type: String, cost: String = "", text: String = "") =
        CardDefinition(name, listOf(DefinitionFace("front", name, typeLine = type, manaCost = cost, rulesText = text)))

    @Test fun coloredPipsHandleHybridPhyrexianHalfPipsAndIgnoreGenericXAndBack() {
        val pips = podStatsPips("{3}{X}{W/U}{B/P}{2/R}{G}{G/U/P}{HW}{C}{S} // {R}{R}")
        assertEquals(1.0, pips[DraftColor.W])
        assertEquals(1.0, pips[DraftColor.U])
        assertEquals(1.0, pips[DraftColor.B])
        assertEquals(0.5, pips[DraftColor.R])
        assertEquals(1.5, pips[DraftColor.G])
    }

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
        assertEquals(mapOf(1.0 to 2, 4.0 to 1), stats.curve)
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
        assertEquals(mapOf(5.0 to 1), stats.curve)
        assertEquals(0L, stats.lands)
        assertEquals(1.5, stats.pips[DraftColor.U])
        assertEquals(0.5, stats.pips[DraftColor.W])
        assertEquals(0.0, stats.pips[DraftColor.R])
        assertTrue(stats.sources.values.all { it == 0L })
        val explicit = card.copy(faces = listOf(card.faces.first().copy(manaCost = "{G}")))
        assertEquals("{G}", podStatsFrontCost(explicit))
        assertEquals("", podStatsFrontCost(card.copy(faces = listOf(card.faces.first().copy(sourceData = "invalid")))))
    }

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
    }
}

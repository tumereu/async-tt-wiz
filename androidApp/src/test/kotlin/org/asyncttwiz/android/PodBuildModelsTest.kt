package org.asyncttwiz.android

import org.asyncttwiz.protocol.*
import org.junit.Test
import kotlin.test.*

class PodBuildModelsTest {
    @Test fun mainHeaderSplitsNonlandCardsFromAllMainLands() {
        val spell = CardDefinition("Spell", listOf(DefinitionFace("front", "Spell", typeLine = "Creature — Elf")))
        val land = CardDefinition("Dual", listOf(DefinitionFace("front", "Dual", typeLine = "Land — Island Swamp")))
        // A back-face land does not count; only the front face's type line.
        val mdfc = CardDefinition("Pathway", listOf(DefinitionFace("front", "Spell side", typeLine = "Sorcery"),
            DefinitionFace("back", "Land side", typeLine = "Land")))
        val copies = (0 until 25).associate { "$it" to DraftCopy("$it", "spell") } + mapOf("land" to DraftCopy("land", "land"),
            "land2" to DraftCopy("land2", "land"), "mdfc" to DraftCopy("mdfc", "mdfc"), "side" to DraftCopy("side", "land"),
            "trash" to DraftCopy("trash", "land"))
        val definitions = mapOf("spell" to spell, "land" to land, "mdfc" to mdfc)
        val build = PodDeckBuild((0 until 25).map { PodDeckCopy("$it") } + PodDeckCopy("land") + PodDeckCopy("land2") +
            PodDeckCopy("mdfc") + PodDeckCopy("missing") +
            PodDeckCopy("side", PodDeckCategory.SIDE) + PodDeckCopy("trash", PodDeckCategory.TRASH), PodDeckBasics(3, 3, 3, 2, 1))
        assertEquals("Main 27 + 14 = 41", podBuildMainTitle(build, copies, definitions))
        assertEquals("Main 0 + 0 = 0", podBuildMainTitle(PodDeckBuild(), emptyMap(), emptyMap()))
    }

    @Test fun tabsHideDraftAfterCompletionAndDefaultToSubmissionStatus() {
        assertEquals(listOf(PodTab.DRAFT, PodTab.BUILD, PodTab.STATS), podTabs(PodPhase.DRAFTING))
        assertEquals(listOf(PodTab.BUILD, PodTab.STATS, PodTab.GAMES), podTabs(PodPhase.DRAFTED))
        assertEquals(PodTab.DRAFT, podDefaultTab(PodPhase.DRAFTING, false))
        assertEquals(PodTab.BUILD, podDefaultTab(PodPhase.DRAFTED, false))
        assertEquals(PodTab.GAMES, podDefaultTab(PodPhase.DRAFTED, true))
    }
    private fun card(name: String, type: String = "Creature", cost: String = "{1}") =
        CardDefinition(name, listOf(DefinitionFace("front", name, typeLine = type, manaCost = cost)))

    @Test fun duplicatesCombineOnlyWithinTheirSectionAndKeepTheirCopyIds() {
        val copies = listOf(DraftCopy("a", "same"), DraftCopy("b", "same"), DraftCopy("c", "same"), DraftCopy("d", "same")).associateBy { it.id }
        val build = PodDeckBuild(listOf(PodDeckCopy("a"), PodDeckCopy("b"), PodDeckCopy("c", PodDeckCategory.SIDE), PodDeckCopy("d", PodDeckCategory.TRASH)))
        val definitions = mapOf("same" to card("Same"))
        val main = podBuildColumns(build, PodDeckCategory.MAIN, copies, definitions, 7)
        assertEquals(7, main.size)
        assertEquals(listOf("a", "b"), main[2].single().copyIds)
        assertEquals(2, podBuildCount(build, PodDeckCategory.MAIN))
        assertEquals(listOf("c"), podBuildColumns(build, PodDeckCategory.SIDE, copies, definitions, 7)[2].single().copyIds)
        assertEquals(listOf("d"), podBuildColumns(build, PodDeckCategory.TRASH, copies, definitions, 7)[2].single().copyIds)
    }

    @Test fun bucketsFitTheSliderAndKeepEmptyColumnsAndFrontFaceRules() {
        val definitions = mapOf(
            "land" to card("Land", "Land — Island", "{9}"),
            "zero" to card("Zero", cost = "{X}"),
            "one" to card("One"),
            "big" to card("Big", cost = "{9}"),
        )
        val copies = definitions.keys.associateWith { DraftCopy(it, it) }
        val build = PodDeckBuild(copies.keys.map { PodDeckCopy(it) })
        for (count in 2..10) {
            val columns = podBuildColumns(build, PodDeckCategory.MAIN, copies, definitions, count)
            assertEquals(count, columns.size)
            assertEquals("land", columns.first().single().digest)
            assertTrue(columns[1].any { it.digest == "zero" })
            assertTrue(columns.last().any { it.digest == "big" })
            assertEquals(4, columns.sumOf { it.size })
        }
        assertTrue(podBuildColumns(build, PodDeckCategory.MAIN, copies, definitions, 7)[4].isEmpty())
    }

    @Test fun sortUsesExplicitCopyColorsBeforeNameIncludingColorlessAndMulticolor() {
        val colors = listOf(setOf(DraftColor.G), emptySet(), setOf(DraftColor.W, DraftColor.U), setOf(DraftColor.U), setOf(DraftColor.W), setOf(DraftColor.B), setOf(DraftColor.R))
        val copies = colors.mapIndexed { index, color -> DraftCopy("$index", "$index", color) }.associateBy { it.id }
        // Every cost is white; explicit colors, even an empty set, must override that.
        val definitions = copies.keys.associateWith { card("Name $it", cost = "{W}") }
        val build = PodDeckBuild(copies.keys.map { PodDeckCopy(it) })
        assertEquals(listOf("4", "3", "5", "6", "0", "2", "1"),
            podBuildColumns(build, PodDeckCategory.MAIN, copies, definitions, 7)[2].map { it.digest })
        val alphaCopies = mapOf("z" to DraftCopy("z", "z"), "a" to DraftCopy("a", "a"))
        assertEquals(listOf("a", "z"), podBuildColumns(PodDeckBuild(alphaCopies.keys.map { PodDeckCopy(it) }), PodDeckCategory.MAIN,
            alphaCopies, mapOf("z" to card("Zulu"), "a" to card("alpha")), 7)[2].map { it.digest })
    }

    @Test fun submitRequiresLifecycleMinimumPoolIntegrityAndSideLimit() {
        val copies = mapOf("main" to DraftCopy("main", "m"), "side" to DraftCopy("side", "s"))
        val valid = PodDeckBuild(listOf(PodDeckCopy("main"), PodDeckCopy("side", PodDeckCategory.SIDE)), PodDeckBasics(plains = 39))
        assertTrue(podBuildSubmitEnabled(valid, copies, 1, editable = true, canSubmit = true))
        assertFalse(podBuildSubmitEnabled(valid, copies, null, editable = false, canSubmit = true))
        assertFalse(podBuildSubmitEnabled(valid, copies, null, editable = true, canSubmit = false))
        assertFalse(podBuildSubmitEnabled(valid, copies, 0, true, true))
        assertFalse(podBuildSubmitEnabled(valid.copy(basics = PodDeckBasics(plains = 38)), copies, null, true, true))
        assertFalse(podBuildSubmitEnabled(valid.copy(copies = valid.copies + PodDeckCopy("main")), copies, null, true, true))
        assertFalse(podBuildSubmitEnabled(valid.copy(copies = valid.copies + PodDeckCopy("unknown")), copies, null, true, true))
        assertFalse(podBuildSubmitEnabled(valid.copy(copies = valid.copies.take(1)), copies, null, true, true))
        assertTrue(podBuildSubmitEnabled(valid.copy(basics = PodDeckBasics(plains = 998)), copies, null, true, true))
        assertFalse(podBuildSubmitEnabled(valid.copy(basics = PodDeckBasics(plains = 999)), copies, null, true, true))
        assertEquals("Main needs 40 cards", podBuildIssueText(PodDeckIssue.MAIN_TOO_SMALL, null))
        assertEquals("Main + Side including basics exceeds 1000 cards", podBuildIssueText(PodDeckIssue.DECK_TOO_LARGE, null))
        assertEquals("Side exceeds its limit of 0 cards", podBuildIssueText(PodDeckIssue.SIDEBOARD_LIMIT, 0))
    }

    @Test fun basicsAdjustOneTypeWithoutGoingBelowZeroOrOverflowing() {
        val basics = PodDeckBasics(plains = 2, island = 3)
        assertEquals(PodDeckBasics(plains = 2, island = 4), basics.podBuildAdjust(1, 1))
        assertEquals(basics, basics.podBuildAdjust(2, -1))
        assertEquals(Int.MAX_VALUE, PodDeckBasics(forest = Int.MAX_VALUE).podBuildAdjust(4, 1).forest)
        assertEquals(5L, basics.total)
    }
}

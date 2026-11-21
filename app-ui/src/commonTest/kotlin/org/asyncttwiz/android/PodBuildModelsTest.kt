package org.asyncttwiz.android

import org.asyncttwiz.protocol.*
import kotlin.test.Test
import kotlin.test.*

class PodBuildModelsTest {
    // DRF-166: the chosen card while it is in Main or Side, else the first of them
    @Test fun theCoverIsTheChosenCardWhileItIsAmongTheChoices() {
        assertEquals("b", podBuildCover(listOf("a", "b"), "b"))
        assertEquals("a", podBuildCover(listOf("a", "b"), "gone"))
        assertEquals("a", podBuildCover(listOf("a", "b"), null))
        assertEquals(null, podBuildCover(emptyList(), "gone"))
    }

    // DRF-103: the basics dialog's line counts the section it was opened for, every land of it, nonbasics too
    @Test fun basicsLineCountsTheSectionsBasicsAndAllItsLands() {
        val spell = CardDefinition("Spell", listOf(DefinitionFace("front", "Spell", typeLine = "Creature — Elf")))
        val land = CardDefinition("Dual", listOf(DefinitionFace("front", "Dual", typeLine = "Land — Island Swamp")))
        val definitions = mapOf("spell" to spell, "land" to land)
        val copies = listOf("s1" to "spell", "l1" to "land", "l2" to "land", "l3" to "land", "l4" to "land")
            .associate { (id, digest) -> id to DraftCopy(id, digest) }
        val build = PodDeckBuild(listOf(PodDeckCopy("s1"), PodDeckCopy("l1"), PodDeckCopy("l2"), PodDeckCopy("l3", PodDeckCategory.SIDE),
            PodDeckCopy("l4", PodDeckCategory.MAYBE)), PodDeckBasics(plains = 7, forest = 8), sideBasics = PodDeckBasics(island = 17))
        assertEquals("Basics 15 · Lands 17", podBuildBasicsLine(build, PodDeckCategory.MAIN, copies, definitions))
        assertEquals("Basics 17 · Lands 18", podBuildBasicsLine(build, PodDeckCategory.SIDE, copies, definitions))
        assertEquals("Basics 0 · Lands 0", podBuildBasicsLine(PodDeckBuild(), PodDeckCategory.MAIN, emptyMap(), emptyMap()))
    }

    // DRF-094
    @Test fun mainHeaderSplitsNonlandCardsFromAllMainLands() {
        val spell = CardDefinition("Spell", listOf(DefinitionFace("front", "Spell", typeLine = "Creature — Elf")))
        val land = CardDefinition("Dual", listOf(DefinitionFace("front", "Dual", typeLine = "Land — Island Swamp")))
        // A back-face land does not count; only the front face's type line.
        val mdfc = CardDefinition("Pathway", listOf(DefinitionFace("front", "Spell side", typeLine = "Sorcery"),
            DefinitionFace("back", "Land side", typeLine = "Land")))
        val copies = (0 until 25).associate { "$it" to DraftCopy("$it", "spell") } + mapOf("land" to DraftCopy("land", "land"),
            "land2" to DraftCopy("land2", "land"), "mdfc" to DraftCopy("mdfc", "mdfc"), "side" to DraftCopy("side", "land"),
            "maybe" to DraftCopy("maybe", "land"))
        val definitions = mapOf("spell" to spell, "land" to land, "mdfc" to mdfc)
        val build = PodDeckBuild((0 until 25).map { PodDeckCopy("$it") } + PodDeckCopy("land") + PodDeckCopy("land2") +
            PodDeckCopy("mdfc") + PodDeckCopy("missing") +
            PodDeckCopy("side", PodDeckCategory.SIDE) + PodDeckCopy("maybe", PodDeckCategory.MAYBE), PodDeckBasics(3, 3, 3, 2, 1))
        assertEquals("Main 27 + 14 = 41", podBuildMainTitle(build, copies, definitions))
        assertEquals("Main 0 + 0 = 0", podBuildMainTitle(PodDeckBuild(), emptyMap(), emptyMap()))
        // Side uses the same split, with a drafted land joining the side basics; no lands keeps a plain count.
        assertEquals("Side 0 + 3 = 3", podBuildSideTitle(build.copy(sideBasics = PodDeckBasics(plains = 2)), copies, definitions))
        assertEquals("Side 1", podBuildSideTitle(build.copy(copies = listOf(PodDeckCopy("0", PodDeckCategory.SIDE))), copies, definitions))
    }

    // DRF-076, DRF-077 (owner, 2026-11-21: Games only once your own draft is done; the default follows your own draft, DRF-119)
    @Test fun tabsHideDraftAfterCompletionAndDefaultToSubmissionStatus() {
        assertEquals(listOf(PodTab.DRAFT, PodTab.BUILD, PodTab.STATS), podTabs(PodPhase.DRAFTING))
        assertEquals(listOf(PodTab.DRAFT, PodTab.BUILD, PodTab.STATS, PodTab.GAMES), podTabs(PodPhase.DRAFTING, ownDraftDone = true))
        assertEquals(listOf(PodTab.BUILD, PodTab.STATS, PodTab.GAMES), podTabs(PodPhase.DRAFTED))
        assertEquals(PodTab.DRAFT, podDefaultTab(PodPhase.DRAFTING, false))
        assertEquals(PodTab.BUILD, podDefaultTab(PodPhase.DRAFTED, false))
        assertEquals(PodTab.GAMES, podDefaultTab(PodPhase.DRAFTED, true))
        // Your own draft done while others still draft: Build until submitted, then Games
        assertEquals(PodTab.BUILD, podDefaultTab(PodPhase.DRAFTING, submitted = false, draftDone = true))
        assertEquals(PodTab.GAMES, podDefaultTab(PodPhase.DRAFTING, submitted = true, draftDone = true))
        val drafting = PodDetail("p", "Pod", PodPhase.DRAFTING, AccountIdentity("a", "A"), DraftSettings(), PodSource(), DraftSupply(0, 0),
            emptyList(), emptyList(), updatedAt = "", draftWait = PodDraftWait.Picks)
        assertFalse(podOwnDraftDone(drafting))
        assertEquals(PodTab.DRAFT, podDefaultTab(drafting))
        assertEquals(listOf(PodTab.DRAFT, PodTab.BUILD, PodTab.STATS), podTabs(drafting))
        assertEquals(listOf(PodTab.DRAFT, PodTab.BUILD, PodTab.STATS, PodTab.GAMES), podTabs(drafting.copy(draftWait = null)))
        assertTrue(podOwnDraftDone(drafting.copy(draftWait = null)))
        assertEquals(PodTab.BUILD, podDefaultTab(drafting.copy(draftWait = null)))
        assertTrue(podOwnDraftDone(drafting.copy(phase = PodPhase.DRAFTED, draftWait = null)))
    }

    // DRF-013, DRF-119 (owner, 2026-11-21): a member done early reads "Build your deck", and "Deck submitted" once submitted,
    // while the others still draft
    @Test fun podStatusFollowsYourOwnDraftWhileOthersDraft() {
        val ann = AccountIdentity("a", "Ann")
        val pod = PodSummary("p", "Pod", PodPhase.DRAFTING, ann, listOf(ann), PodAttention.NONE, 0, "", DraftMode.ARENA)
        assertEquals("Drafting", podStatus(pod))
        assertEquals("Build your deck", podStatus(pod.copy(attention = PodAttention.BUILD)))
        assertEquals("Deck submitted", podStatus(pod.copy(submitted = true)))
        assertEquals("Deck submitted", podStatus(pod.copy(phase = PodPhase.DRAFTED, submitted = true)))
        assertEquals("building", podMemberStatus(PodMember(ann, building = true), PodPhase.DRAFTING, DraftMode.ARENA))
        assertEquals("waiting", podMemberStatus(PodMember(ann), PodPhase.DRAFTING, DraftMode.ARENA))
    }

    // DRF-119, DRF-303 (owner, 2026-11-21, MT74XQ): Build says why Submit is locked while your own draft runs, and the log says what
    // Submit is and why
    @Test fun lockedSubmitSaysWhyInPlainWords() {
        assertEquals("Submit opens after your last pick", podSubmitWaitText(PodDraftWait.Picks))
        assertEquals("Submit opens when your last cards arrive", podSubmitWaitText(PodDraftWait.Cards))
        assertEquals("Submit opens once the last pile is taken", podSubmitWaitText(PodDraftWait.Piles))
        val copies = (0 until 3).map { DraftCopy("$it", "d$it") }.associateBy { it.id }
        val build = PodDeckBuild(copies.keys.map { PodDeckCopy(it) }, PodDeckBasics(plains = 37))
        fun state(canSubmit: Boolean = true, editable: Boolean = true, submitted: Boolean = false, wait: PodDraftWait? = null,
            deck: PodDeckBuild = build) = podBuildSubmitState(deck, copies, null, editable, canSubmit, submitted, wait)
        assertEquals("locked, your own draft isn't done: \"Submit opens after your last pick\"", state(canSubmit = false, wait = PodDraftWait.Picks))
        assertEquals("open", state())
        assertTrue(podBuildSubmitEnabled(build, copies, null, editable = true, canSubmit = true))
        assertEquals("locked by the deck rules: Main needs 40 cards", state(deck = build.copy(basics = PodDeckBasics(plains = 30))))
        assertEquals("locked while a change is unconfirmed", state(editable = false))
        assertEquals("submitted", state(submitted = true))
    }
    private fun card(name: String, type: String = "Creature", cost: String = "{1}") =
        CardDefinition(name, listOf(DefinitionFace("front", name, typeLine = type, manaCost = cost)))

    // DRF-098
    @Test fun duplicatesCombineOnlyWithinTheirSectionAndKeepTheirCopyIds() {
        val copies = listOf(DraftCopy("a", "same"), DraftCopy("b", "same"), DraftCopy("c", "same"), DraftCopy("d", "same")).associateBy { it.id }
        val build = PodDeckBuild(listOf(PodDeckCopy("a"), PodDeckCopy("b"), PodDeckCopy("c", PodDeckCategory.SIDE), PodDeckCopy("d", PodDeckCategory.MAYBE)))
        val definitions = mapOf("same" to card("Same"))
        val main = podBuildColumns(build, PodDeckCategory.MAIN, copies, definitions, 7)
        assertEquals(7, main.size)
        assertEquals(listOf("a", "b"), main[2].single().copyIds)
        assertEquals(2, podBuildCount(build, PodDeckCategory.MAIN))
        assertEquals(listOf("c"), podBuildColumns(build, PodDeckCategory.SIDE, copies, definitions, 7)[2].single().copyIds)
        assertEquals(listOf("d"), podBuildColumns(build, PodDeckCategory.MAYBE, copies, definitions, 7)[2].single().copyIds)
    }

    // DRF-095
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

    // DRF-098
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

    // DRF-094, DRF-119: a header warns exactly when its section's rule stops Submit, whatever the numbers
    @Test fun headerWarningsFollowSubmitsRules() {
        val copies = mapOf("main" to DraftCopy("main", "m"), "side" to DraftCopy("side", "s"))
        val valid = PodDeckBuild(listOf(PodDeckCopy("main"), PodDeckCopy("side", PodDeckCategory.SIDE)), PodDeckBasics(plains = 39))
        fun warning(category: PodDeckCategory, build: PodDeckBuild = valid, limit: Int? = 1) = podBuildHeaderWarning(category, build, copies, limit)
        for (category in PodDeckCategory.entries) assertNull(warning(category))
        assertTrue(podBuildSubmitEnabled(valid, copies, 1, editable = true, canSubmit = true))

        val small = valid.copy(basics = PodDeckBasics(plains = 38))
        assertEquals("Main needs 40 cards", warning(PodDeckCategory.MAIN, small))
        assertNull(warning(PodDeckCategory.SIDE, small))
        assertFalse(podBuildSubmitEnabled(small, copies, 1, editable = true, canSubmit = true))

        assertEquals("Side exceeds its limit of 0 cards", warning(PodDeckCategory.SIDE, limit = 0))
        assertNull(warning(PodDeckCategory.MAIN, limit = 0))
        assertFalse(podBuildSubmitEnabled(valid, copies, 0, editable = true, canSubmit = true))
        // Side's basics count toward its limit, as they do for Submit; no limit, no warning
        val sideBasics = valid.copy(sideBasics = PodDeckBasics(island = 1))
        assertEquals("Side exceeds its limit of 1 cards", warning(PodDeckCategory.SIDE, sideBasics))
        assertNull(warning(PodDeckCategory.SIDE, sideBasics, limit = null))
    }

    // DRF-119
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

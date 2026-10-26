package org.asyncttwiz.protocol

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.*

class PodDeckRulesTest {
    private fun card(name: String, cost: String = "", type: String = "Creature") =
        CardDefinition(name, listOf(DefinitionFace("front", name, typeLine = type, manaCost = cost)))

    @Test fun swipeTable() {
        val table = listOf(
            Triple(PodDeckCategory.MAIN, PodDeckCategory.SIDE, PodDeckCategory.TRASH),
            Triple(PodDeckCategory.SIDE, PodDeckCategory.MAIN, PodDeckCategory.TRASH),
            Triple(PodDeckCategory.TRASH, PodDeckCategory.MAIN, PodDeckCategory.SIDE),
        )
        for ((source, right, left) in table) {
            assertEquals(right, PodDeckRules.swipe(source, PodDeckSwipe.RIGHT))
            assertEquals(left, PodDeckRules.swipe(source, PodDeckSwipe.LEFT))
        }
    }

    @Test fun newAwardsGoToMainWithoutMovingExistingCopiesOrChangingBasics() {
        val build = PodDeckBuild(listOf(PodDeckCopy("one", PodDeckCategory.SIDE), PodDeckCopy("two", PodDeckCategory.TRASH)),
            PodDeckBasics(plains = 17))
        val updated = PodDeckRules.withAwards(build, listOf("one", "two", "three", "four", "three"))
        assertEquals(build.copies + listOf(PodDeckCopy("three"), PodDeckCopy("four")), updated.copies)
        assertEquals(build.basics, updated.basics)
        assertEquals(updated, PodDeckRules.withAwards(updated, listOf("one", "two", "three", "four")))
    }

    @Test fun basicsAreNonnegativeAndCountTowardFortyWithoutAnUpperLimit() {
        assertTrue(PodDeckBasics().valid)
        assertEquals(0L, PodDeckBasics().total)
        val basics = PodDeckBasics(2, 3, 5, 7, 23)
        assertTrue(basics.valid)
        assertEquals(40L, basics.total)
        assertTrue(PodDeckRules.validate(PodDeckBuild(basics = basics), emptyList()).isEmpty())
        assertContains(PodDeckRules.validate(PodDeckBuild(basics = basics.copy(forest = 22)), emptyList()), PodDeckIssue.MAIN_TOO_SMALL)
        for (bad in listOf(basics.copy(plains = -1), basics.copy(island = -1), basics.copy(swamp = -1),
            basics.copy(mountain = -1), basics.copy(forest = -1))) {
            assertFalse(bad.valid)
            assertContains(PodDeckRules.validate(PodDeckBuild(basics = bad), emptyList()), PodDeckIssue.NEGATIVE_BASICS)
        }
        val huge = PodDeckBasics(Int.MAX_VALUE, Int.MAX_VALUE)
        assertEquals(4294967294L, huge.total)
        assertTrue(PodDeckRules.validate(PodDeckBuild(basics = huge), emptyList()).isEmpty())
    }

    @Test fun submissionChecksOwnershipUniquenessCompletenessMainAndSideLimitOnly() {
        val pool = List(50) { "copy-$it" }
        val build = PodDeckBuild(pool.mapIndexed { index, id -> PodDeckCopy(id, when {
            index < 23 -> PodDeckCategory.MAIN
            index < 48 -> PodDeckCategory.SIDE
            else -> PodDeckCategory.TRASH
        }) }, PodDeckBasics(forest = 17))
        assertTrue(PodDeckRules.validate(build, pool).isEmpty())
        assertTrue(PodDeckRules.validate(build, pool, 25).isEmpty())
        assertEquals(listOf(PodDeckIssue.SIDEBOARD_LIMIT), PodDeckRules.validate(build, pool, 24))
        assertContains(PodDeckRules.validate(build, pool, 0), PodDeckIssue.SIDEBOARD_LIMIT)
        val allMain = PodDeckBuild(pool.map { PodDeckCopy(it) })
        assertTrue(PodDeckRules.validate(allMain, pool, 0).isEmpty())
        assertEquals(listOf(PodDeckIssue.MAIN_TOO_SMALL), PodDeckRules.validate(build.copy(basics = PodDeckBasics(forest = 16)), pool))
        assertEquals(listOf(PodDeckIssue.UNKNOWN_COPY), PodDeckRules.validate(build.copy(copies = build.copies + PodDeckCopy("foreign")), pool))
        assertContains(PodDeckRules.validate(build.copy(copies = build.copies + build.copies.first()), pool), PodDeckIssue.DUPLICATE_COPY)
        assertEquals(listOf(PodDeckIssue.MISSING_COPY), PodDeckRules.validate(build.copy(copies = build.copies.dropLast(1)), pool))
        val duplicateAcrossCategories = build.copy(copies = build.copies + PodDeckCopy(pool.first(), PodDeckCategory.TRASH))
        assertEquals(listOf(PodDeckIssue.DUPLICATE_COPY), PodDeckRules.validate(duplicateAcrossCategories, pool))
        assertFailsWith<IllegalArgumentException> { PodDeckRules.validate(build, pool, -1) }
        assertEquals(build, Json.decodeFromString<PodDeckBuild>(Json.encodeToString(build)))
    }

    @Test fun manaValueAndColorsCoverGenericVariableHybridPhyrexianAndColorless() {
        val cases = listOf(
            Triple("", 0.0, emptySet()),
            Triple("{X}{Y}{Z}{0}", 0.0, emptySet()),
            Triple("{10}{W}{U}{B}{R}{G}", 15.0, DraftColor.entries.toSet()),
            Triple("{2/W}{U/B}{R/P}{G/U/P}", 5.0, DraftColor.entries.toSet()),
            Triple("{C}{S}{3}", 5.0, emptySet()),
            Triple("{HW}", 0.5, setOf(DraftColor.W)),
            Triple("{x}{u}{u}", 2.0, setOf(DraftColor.U)),
            Triple("{1}{B} // {8}{G}", 2.0, setOf(DraftColor.B)),
        )
        for ((cost, value, colors) in cases) assertEquals(PodDeckMana(value, colors), PodDeckManaCost.parse(cost), cost)
    }

    @Test fun columnsHaveFixedLandZeroAndOverflowBucketsFromTwoToTen() {
        assertEquals(listOf("Lands", "0+"), PodDeckColumns.buckets(2).map { it.label })
        assertEquals(listOf("Lands", "0", "1+"), PodDeckColumns.buckets(3).map { it.label })
        assertEquals(listOf("Lands", "0", "1", "2", "3", "4", "5+"), PodDeckColumns.buckets(7).map { it.label })
        for (columns in 2..10) {
            val buckets = PodDeckColumns.buckets(columns)
            assertEquals(columns, buckets.size)
            assertNull(buckets.first().minimumManaValue)
            assertTrue(buckets.last().andAbove)
            assertTrue(buckets.dropLast(1).none { it.andAbove })
            for (value in 0..20) {
                assertEquals(1 + minOf(value, columns - 2), PodDeckColumns.bucket(columns, card("spell", "{$value}")))
            }
            assertEquals(1, PodDeckColumns.bucket(columns, card("X spell", "{X}")))
            assertEquals(0, PodDeckColumns.bucket(columns, card("Forest", type = "Basic Land — Forest")))
            assertEquals(0, PodDeckColumns.bucket(columns, card("Dryad Arbor", type = "Land Creature — Forest Dryad")))
            assertEquals(1, PodDeckColumns.bucket(columns, card("Landscaper", type = "Creature — Landshark")))
        }
        for (bad in listOf(0, 1, 11)) {
            assertFailsWith<IllegalArgumentException> { PodDeckColumns.buckets(bad) }
            assertFailsWith<IllegalArgumentException> { PodDeckColumns.bucket(bad, card("x")) }
        }
    }

    @Test fun frontFaceAloneDeterminesLandAndManaBucket() {
        val modal = card("spell // land", "{2}{R}").copy(faces = listOf(
            DefinitionFace("front", "spell", manaCost = "{2}{R}", typeLine = "Sorcery"),
            DefinitionFace("back", "land", typeLine = "Land"),
        ))
        assertEquals(4, PodDeckColumns.bucket(7, modal))
        assertEquals(DraftColor.R.ordinal, PodDeckColumns.colorRank(modal))
        assertEquals(0, PodDeckColumns.bucket(7, modal.copy(faces = modal.faces.reversed())))
    }

    @Test fun normalizedAdventureUsesFrontRecordCost() {
        val giant = CardDefinition("Bonecrusher Giant // Stomp", listOf(DefinitionFace("front", "Bonecrusher Giant // Stomp",
            typeLine = "Creature — Giant", sourceData = """{"manaCost":"{2}{R}","type":"Creature — Giant"}""")))
        assertEquals(4, PodDeckColumns.bucket(7, giant))
        assertEquals(DraftColor.R.ordinal, PodDeckColumns.colorRank(giant))
    }

    @Test fun inColumnSortIsWubrgThenMulticolorThenColorlessThenName() {
        val cards = listOf(card("Z white", "{W}"), card("a White", "{W}"), card("Blue", "{U}"),
            card("Black", "{B}"), card("Red", "{R}"), card("Green", "{G}"),
            card("Multi", "{W/U}"), card("Colorless", "{3}"))
        assertEquals(listOf("a White", "Z white", "Blue", "Black", "Red", "Green", "Multi", "Colorless"),
            cards.reversed().sortedWith(PodDeckColumns.comparator()).map { it.name })
        assertEquals(6, PodDeckColumns.colorRank(card("Devoid", "{B}"), emptySet()))
        assertEquals(1, PodDeckColumns.colorRank(card("Indicator"), setOf(DraftColor.U)))
        val indicator = card("Blue indicator")
        val white = card("White", "{W}")
        assertEquals(listOf(white, indicator), listOf(indicator, white).sortedWith(PodDeckColumns.comparator {
            if (it == indicator) setOf(DraftColor.U) else null
        }))
    }
}

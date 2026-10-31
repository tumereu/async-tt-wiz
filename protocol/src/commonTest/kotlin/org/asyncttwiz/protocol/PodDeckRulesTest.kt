package org.asyncttwiz.protocol

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.*

class PodDeckRulesTest {
    private fun card(name: String, cost: String = "", type: String = "Creature") =
        CardDefinition(name, listOf(DefinitionFace("front", name, typeLine = type, manaCost = cost)))

    // DRF-099
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

    // DRF-093
    @Test fun newAwardsGoToMainWithoutMovingExistingCopiesOrChangingBasics() {
        val build = PodDeckBuild(listOf(PodDeckCopy("one", PodDeckCategory.SIDE), PodDeckCopy("two", PodDeckCategory.TRASH)),
            PodDeckBasics(plains = 17))
        val updated = PodDeckRules.withAwards(build, listOf("one", "two", "three", "four", "three"))
        assertEquals(build.copies + listOf(PodDeckCopy("three"), PodDeckCopy("four")), updated.copies)
        assertEquals(build.basics, updated.basics)
        assertEquals(updated, PodDeckRules.withAwards(updated, listOf("one", "two", "three", "four")))
    }

    @Test fun basicsAreNonnegativeAndCountTowardFortyWithOverflowSafeTotals() {
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
        assertEquals(listOf(PodDeckIssue.DECK_TOO_LARGE), PodDeckRules.validate(PodDeckBuild(basics = huge), emptyList()))
    }

    // DRF-119
    @Test fun submissionCapacityCountsMainSideAndBasicsButNotTrash() {
        val pool = List(1100) { "copy-$it" }
        val build = PodDeckBuild(pool.mapIndexed { index, id -> PodDeckCopy(id, when {
            index < 45 -> PodDeckCategory.MAIN
            index < 50 -> PodDeckCategory.SIDE
            else -> PodDeckCategory.TRASH
        }) }, PodDeckBasics(plains = 950))
        assertTrue(PodDeckRules.validate(build, pool).isEmpty()) // Exactly 1000, ignoring Trash.
        assertEquals(listOf(PodDeckIssue.DECK_TOO_LARGE), PodDeckRules.validate(build.copy(basics = PodDeckBasics(plains = 951)), pool))
        val originalProbe = PodDeckBuild(pool.take(45).map { PodDeckCopy(it) }, PodDeckBasics(plains = 956))
        assertEquals(listOf(PodDeckIssue.DECK_TOO_LARGE), PodDeckRules.validate(originalProbe, pool.take(45)))
        val noBasics = build.copy(basics = PodDeckBasics(), copies = pool.map { PodDeckCopy(it) })
        assertEquals(listOf(PodDeckIssue.DECK_TOO_LARGE), PodDeckRules.validate(noBasics, pool))
    }

    // DRF-119
    @Test fun submissionChecksOwnershipUniquenessCompletenessMainAndSideLimit() {
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

    // DRF-104
    @Test fun sideBasicsCountTowardSideLimitAndCapacityButNotMain() {
        val pool = List(45) { "copy-$it" }
        val build = PodDeckBuild(pool.mapIndexed { index, id -> PodDeckCopy(id, if (index < 40) PodDeckCategory.MAIN else PodDeckCategory.SIDE) },
            sideBasics = PodDeckBasics(island = 10))
        assertTrue(PodDeckRules.validate(build, pool, 15).isEmpty())
        assertEquals(listOf(PodDeckIssue.SIDEBOARD_LIMIT), PodDeckRules.validate(build, pool, 14))
        assertTrue(PodDeckRules.validate(build, pool).isEmpty())
        assertEquals(listOf(PodDeckIssue.MAIN_TOO_SMALL), PodDeckRules.validate(build.copy(copies = build.copies.map {
            if (it.copyId == "copy-0") it.copy(category = PodDeckCategory.SIDE) else it }, sideBasics = PodDeckBasics(island = 40)), pool))
        assertContains(PodDeckRules.validate(build.copy(sideBasics = PodDeckBasics(island = -1)), pool), PodDeckIssue.NEGATIVE_BASICS)
        assertEquals(listOf(PodDeckIssue.DECK_TOO_LARGE), PodDeckRules.validate(build.copy(sideBasics = PodDeckBasics(island = 956)), pool))
        assertTrue(PodDeckRules.validate(build.copy(sideBasics = PodDeckBasics(island = 955)), pool).isEmpty())
        assertEquals(PodDeckBasics(17, 17, 17, 17, 17), PodDeckRules.initialBuild(null).sideBasics)
        assertEquals(PodDeckBuild(), PodDeckRules.initialBuild(15))
        assertEquals(PodDeckBuild(), DeckJson.format.decodeFromString<PodDeckBuild>("""{"copies":[],"basics":{}}"""))
        assertEquals(build, DeckJson.format.decodeFromString<PodDeckBuild>(DeckJson.format.encodeToString(build)))
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

    // DRF-095
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

    @Test fun normalizedSplitCardsCountBothHalvesAndCatalogColorsWin() {
        val fireIce = CardDefinition("Fire // Ice", listOf(DefinitionFace("front", "Fire // Ice", typeLine = "Instant",
            rulesText = "Fire\n{1}{R}\nInstant\nFire deals 2 damage.\n\n────────\n\nIce\n{1}{U}\nInstant\nTap target permanent.")),
            layout = "split")
        assertEquals(4.0, PodDeckManaCost.front(fireIce).value)
        assertEquals(5, PodDeckColumns.bucket(7, fireIce))
        assertEquals(setOf(DraftColor.U, DraftColor.R), PodDeckManaCost.front(fireIce).colors)
        val devoid = card("Wasteland Strangler", "{2}{B}").let { it.copy(faces = listOf(it.faces.single().copy(sourceData = """{"colors":[]}"""))) }
        val vision = card("Ancestral Vision", type = "Sorcery").let { it.copy(faces = listOf(it.faces.single().copy(sourceData = """{"colors":["U"]}"""))) }
        assertEquals(listOf(6, DraftColor.U.ordinal), listOf(devoid, vision).map { PodDeckColumns.colorRank(it) })
    }

    // DRF-098
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

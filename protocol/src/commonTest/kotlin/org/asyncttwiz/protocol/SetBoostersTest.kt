package org.asyncttwiz.protocol

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.*

class SetBoostersTest {
    private val seed = RandomV1.encodeSeed(ByteArray(32) { it.toByte() })
    private fun card(index: Int, rarity: SetCardRarity = SetCardRarity.UNKNOWN, colors: Set<DraftColor> = emptySet()) =
        SetSourceCard("sha256:" + index.toString(16).padStart(64, '0'),
            "00000000-0000-0000-0000-" + index.toString(16).padStart(12, '0'), rarity, colors)
    private fun source(cards: List<SetSourceCard> = listOf(card(1)), recipe: SetBoosterRecipe? = null) =
        SetSourceSnapshot("TST", "Test set", cards, recipe)
    private fun recipe(sheet: SetBoosterSheet, count: Int = 1) =
        SetBoosterRecipe(listOf(SetBoosterComposition(mapOf("main" to count), 1)), mapOf("main" to sheet))
    private fun sheet(cards: List<SetSourceCard>, weight: (Int) -> Long = { 1 }) =
        SetBoosterSheet(cards.mapIndexed { index, card -> card.uuid to weight(index) }.toMap())
    private fun generate(source: SetSourceSnapshot, count: Int = 1) = SetBoosters.generate(source, count, seed)

    @Test fun recipePreferenceDoesNotDependOnMapOrderAndDoesNotChooseOtherProducts() {
        val base = recipe(sheet(listOf(card(1))))
        val play = base.copy(name = "Play")
        val draft = base.copy(name = "Draft")
        val default = base.copy(name = "Default")
        val booster = linkedMapOf("collector" to base, "default" to default, "draft" to draft, "play" to play)
        assertEquals(play, selectSetBoosterRecipe(booster))
        assertEquals(draft, selectSetBoosterRecipe(booster - "play"))
        assertEquals(default, selectSetBoosterRecipe(booster - "play" - "draft"))
        assertNull(selectSetBoosterRecipe(mapOf("collector" to base)))
        assertNull(selectSetBoosterRecipe(emptyMap()))
    }

    @Test fun mtgjsonRecipeShapeAndSnapshotRoundTrip() {
        val card = card(1, SetCardRarity.COMMON, setOf(DraftColor.W))
        val json = """{
            "boosters": [{"contents": {"main": 2}, "weight": 3}],
            "boostersTotalWeight": 3,
            "name": "Play Booster",
            "sourceSetCodes": ["TST", "OTHER"],
            "sheets": {"main": {"cards": {"${card.uuid}": 10}, "foil": true,
                "totalWeight": 10, "allowDuplicates": true}}
        }"""
        val recipe = Json.decodeFromString<SetBoosterRecipe>(json)
        assertFalse(recipe.sheets.getValue("main").fixed)
        assertFalse(recipe.sheets.getValue("main").balanceColors)
        assertTrue(recipe.sheets.getValue("main").foil)
        val source = source(listOf(card), recipe)
        assertTrue(source.validate().isEmpty())
        val encoded = Json.encodeToString(source)
        assertContains(encoded, "\"common\"")
        val decoded = Json.decodeFromString<SetSourceSnapshot>(encoded)
        assertEquals(source, decoded)
        val packs = generate(decoded, 2)
        assertEquals(packs, Json.decodeFromString<List<SetBoosterPack>>(Json.encodeToString(packs)))
        assertTrue(packs.all { it.kind == SetBoosterKind.RECIPE_BASED && it.cards.size == 2 })
    }

    @Test fun weightedCompositionChoiceHasSeededDistributionAndVariableSizes() {
        val cards = listOf(card(1), card(2))
        val recipe = SetBoosterRecipe(
            listOf(SetBoosterComposition(mapOf("small" to 1), 1), SetBoosterComposition(mapOf("large" to 2), 3)),
            mapOf("small" to sheet(cards.take(1)), "large" to sheet(cards).copy(fixed = true)))
        val packs = generate(source(cards, recipe), 4000)
        assertTrue(packs.count { it.cards.size == 2 } in 2800..3200)
        assertTrue(packs.all { it.cards.size in 1..2 && it.kind == SetBoosterKind.RECIPE_BASED })
        assertTrue(packs.filter { it.cards.size == 1 }.all { it.cards.single().definition == cards[0].definition })
    }

    @Test fun weightedSheetChoiceHasSeededDistribution() {
        val cards = listOf(card(1), card(2))
        val source = source(cards, recipe(sheet(cards) { if (it == 0) 1 else 9 }))
        val packs = generate(source, 4000)
        assertTrue(packs.count { it.cards.single().definition == cards[1].definition } in 3440..3760)
    }

    @Test fun weightedDrawsWithoutReplacementRemoveSelectedUuidAndResetEachPack() {
        val cards = List(3) { card(it + 1) }
        val source = source(cards, recipe(sheet(cards) { if (it == 0) 1000 else 1 }, 3))
        val packs = generate(source, 200)
        for (pack in packs) assertEquals(cards.map { it.definition }.toSet(), pack.cards.map { it.definition }.toSet())
        assertTrue(packs.count { it.cards.first().definition == cards.first().definition } > 190)
        assertEquals(600, packs.flatMap { it.cards }.map { it.id }.distinct().size)
    }

    @Test fun duplicateSheetsAllowRepeatedUuidsButEveryDrawHasItsOwnCopy() {
        val card = card(1, colors = setOf(DraftColor.G))
        val source = source(listOf(card), recipe(sheet(listOf(card)).copy(allowDuplicates = true), 8))
        val packs = generate(source, 3)
        assertEquals(24, packs.flatMap { it.cards }.map { it.id }.distinct().size)
        assertTrue(packs.all { pack -> pack.cards.all { it.definition == card.definition && it.colors == card.colors } })
    }

    @Test fun duplicateRestrictionsArePerSheetNotAcrossDifferentSheets() {
        val cards = listOf(card(1))
        val recipe = SetBoosterRecipe(listOf(SetBoosterComposition(mapOf("normal" to 1, "foil" to 1), 1)),
            mapOf("normal" to sheet(cards), "foil" to sheet(cards).copy(foil = true)))
        val pack = generate(source(cards, recipe)).single()
        assertEquals(2, pack.cards.size)
        assertEquals(1, pack.cards.map { it.definition }.distinct().size)
        assertEquals(2, pack.cards.map { it.id }.distinct().size)
    }

    @Test fun fixedSheetsPreserveUploadedOrderAndMultiplicitiesRegardlessOfSeedOrColorBalancing() {
        val cards = listOf(card(3), card(1, colors = setOf(DraftColor.W)), card(2))
        val sheet = sheet(cards) { (it + 1).toLong() }.copy(fixed = true, balanceColors = true)
        val source = source(cards, recipe(sheet, 2))
        val expected = cards.take(2).map { it.definition }
        assertTrue(generate(source, 20).all { it.cards.map { copy -> copy.definition } == expected })
        val otherSeed = RandomV1.encodeSeed(ByteArray(32) { 9 })
        assertEquals(expected, SetBoosters.generate(source, 1, otherSeed).single().cards.map { it.definition })
        val expectedMultiset = listOf(cards[0], cards[1], cards[1], cards[2], cards[2], cards[2]).map { it.definition }
        for (duplicates in listOf(false, true)) {
            val full = source.copy(recipe = recipe(sheet.copy(allowDuplicates = duplicates), 6))
            assertTrue(full.validate().isEmpty())
            assertTrue(generate(full, 20).all { it.cards.map { copy -> copy.definition } == expectedMultiset })
            val excessive = full.copy(recipe = recipe(sheet.copy(allowDuplicates = duplicates), 7))
            assertContains(excessive.validate(), SetSourceIssue.SHEET_CAPACITY)
        }
    }

    @Test fun fixedSheetsWithOneOneFiveCountsDrawExactlyTheGuaranteedMultiset() {
        val cards = listOf(card(1), card(2), card(3))
        val fixed = sheet(cards) { if (it == 2) 5 else 1 }.copy(fixed = true)
        val source = source(cards, recipe(fixed, 7))
        assertTrue(source.validate().isEmpty())
        val expected = listOf(cards[0], cards[1]) + List(5) { cards[2] }
        assertTrue(generate(source, 10).all { it.cards.map { copy -> copy.definition } == expected.map { it.definition } })
        // Only the requested prefix is allocated, even if a fixed count is larger than Int.
        val huge = source(cards, recipe(sheet(cards) { Long.MAX_VALUE / 3 }.copy(fixed = true), 20))
        assertTrue(generate(huge).single().cards.all { it.definition == cards[0].definition })
    }

    @Test fun balancingCoversAvailableColorsThenFillsByWeightWithoutDuplicates() {
        val colors = DraftColor.entries.mapIndexed { index, color -> card(index + 1, colors = setOf(color)) }
        val cards = colors + card(6)
        val source = source(cards, recipe(sheet(cards) { if (it == 5) 1000000 else 1 }.copy(balanceColors = true), 6))
        for (pack in generate(source, 30)) {
            assertEquals(DraftColor.entries.toSet(), pack.cards.take(5).flatMap { it.colors }.toSet())
            assertEquals(cards.map { it.definition }.toSet(), pack.cards.map { it.definition }.toSet())
        }
        val small = source.copy(recipe = recipe(sheet(cards).copy(balanceColors = true), 2))
        assertTrue(generate(small, 20).all { it.cards.size == 2 && it.cards.map { it.definition }.distinct().size == 2 })
    }

    @Test fun balancingHandlesMulticolorMissingColorsAndReplacement() {
        val cards = listOf(card(1, colors = DraftColor.entries.toSet()), card(2))
        val source = source(cards, recipe(sheet(cards).copy(balanceColors = true), 1))
        assertEquals(DraftColor.entries.toSet(), generate(source).single().cards.single().colors)
        val scarce = listOf(card(3, colors = setOf(DraftColor.U)), card(4))
        val duplicates = source(scarce, recipe(sheet(scarce).copy(balanceColors = true, allowDuplicates = true), 7))
        for (pack in generate(duplicates, 10)) {
            assertEquals(7, pack.cards.size)
            assertTrue(pack.cards.any { DraftColor.U in it.colors })
        }
    }

    @Test fun fallbackUsesTenCommonsThreeUncommonsRareAndWildcard() {
        val cards = listOf(card(1, SetCardRarity.COMMON), card(2, SetCardRarity.UNCOMMON), card(3, SetCardRarity.RARE))
        for (pack in generate(source(cards), 100)) {
            assertEquals(SetBoosterKind.APPROXIMATE, pack.kind)
            assertEquals(15, pack.cards.size)
            assertTrue(pack.cards.take(10).all { it.definition == cards[0].definition })
            assertTrue(pack.cards.subList(10, 13).all { it.definition == cards[1].definition })
            assertEquals(cards[2].definition, pack.cards[13].definition)
            assertContains(cards.map { it.definition }, pack.cards[14].definition)
        }
        assertEquals(1500, generate(source(cards), 100).flatMap { it.cards }.map { it.id }.distinct().size)
    }

    @Test fun fallbackFillsEveryMissingRarityFromAllEligibleCards() {
        for (rarity in SetCardRarity.entries) {
            val card = card(1, rarity)
            val packs = generate(source(listOf(card)), 2)
            assertTrue(packs.all { it.cards.size == 15 && it.cards.all { copy -> copy.definition == card.definition } })
        }
        val cards = listOf(card(1, SetCardRarity.SPECIAL), card(2, SetCardRarity.BONUS), card(3))
        val packs = generate(source(cards), 100)
        assertEquals(cards.map { it.definition }.toSet(), packs.flatMap { it.cards }.map { it.definition }.toSet())
        assertTrue(packs.all { it.cards.size == 15 })
    }

    @Test fun approximatePacksScaleToConfiguredSizeWhileRecipeSizesStayFixed() {
        val cards = listOf(card(1, SetCardRarity.COMMON), card(2, SetCardRarity.UNCOMMON), card(3, SetCardRarity.RARE))
        for (size in listOf(1, 2, 3, 5, 7, 15, 20, 1000)) {
            val packs = SetBoosters.generate(source(cards), 2, seed, cardsPerPack = size)
            val uncommons = size / 5
            val commons = size - 1 - (if (size >= 2) 1 else 0) - uncommons
            for (pack in packs) {
                assertEquals(size, pack.cards.size)
                assertEquals(SetBoosterKind.APPROXIMATE, pack.kind)
                assertTrue(pack.cards.take(commons).all { it.definition == cards[0].definition })
                assertTrue(pack.cards.subList(commons, commons + uncommons).all { it.definition == cards[1].definition })
                assertEquals(cards[2].definition, pack.cards[commons + uncommons].definition)
            }
        }
        val fixedSize = source(cards, recipe(sheet(cards).copy(fixed = true), 3))
        assertTrue(SetBoosters.generate(fixedSize, 2, seed, cardsPerPack = 7).all { it.cards.size == 3 })
        for (size in listOf(0, -1, 1001, Int.MAX_VALUE)) {
            assertFailsWith<IllegalArgumentException> { SetBoosters.generate(source(cards), 1, seed, size) }
        }
        assertFailsWith<IllegalArgumentException> { SetBoosters.generate(source(cards), 1001, seed, 1000) }
    }

    @Test fun longCompositionAndCardWeightsValidateRoundTripAndSampleDeterministically() {
        val cards = listOf(card(1), card(2))
        val recipe = SetBoosterRecipe(
            listOf(SetBoosterComposition(mapOf("small" to 1), 6_491_647_008L),
                SetBoosterComposition(mapOf("large" to 2), 3 * 6_491_647_008L)),
            mapOf("small" to sheet(cards) { if (it == 0) 6_491_647_008L else 3 * 6_491_647_008L },
                "large" to sheet(cards).copy(fixed = true)))
        val source = source(cards, recipe)
        assertTrue(source.validate().isEmpty())
        val packs = generate(source, 4000)
        assertEquals(packs, generate(Json.decodeFromString<SetSourceSnapshot>(Json.encodeToString(source)), 4000))
        val singles = packs.filter { it.cards.size == 1 }
        assertTrue(singles.size in 840..1160)
        assertTrue(singles.count { it.cards.single().definition == cards[1].definition } in (singles.size * 0.68).toInt()..(singles.size * 0.82).toInt())
        val max = source(cards, recipe(sheet(cards.take(1)) { Long.MAX_VALUE }))
        assertTrue(max.validate().isEmpty())
        assertEquals(cards[0].definition, generate(max).single().cards.single().definition)
    }

    @Test fun fallbackMythicUpgradeIsRoughlyOneInEightNotIncludingTheWildcard() {
        val cards = listOf(card(1, SetCardRarity.COMMON), card(2, SetCardRarity.UNCOMMON),
            card(3, SetCardRarity.RARE), card(4, SetCardRarity.MYTHIC))
        val packs = generate(source(cards), 8000)
        val mythics = packs.count { it.cards[13].definition == cards[3].definition }
        assertTrue(mythics in 840..1160, "Mythic rare-slot count: $mythics")
        assertTrue(packs.all { it.cards[13].definition in cards.takeLast(2).map { card -> card.definition } })
    }

    @Test fun generationIsDeterministicAcrossSerializationAndUnorderedInputsWithoutMutatingSource() {
        val cards = List(12) { card(it + 1, SetCardRarity.entries[it % SetCardRarity.entries.size], setOf(DraftColor.entries[it % 5])) }
        val sheets = mapOf("a" to sheet(cards.take(8)).copy(balanceColors = true), "b" to sheet(cards.takeLast(4)))
        val recipe = SetBoosterRecipe(listOf(SetBoosterComposition(mapOf("a" to 5, "b" to 2), 1)), sheets)
        for (original in listOf(source(cards), source(cards, recipe))) {
            val before = Json.encodeToString(original)
            val packs = generate(original, 20)
            assertEquals(packs, generate(Json.decodeFromString<SetSourceSnapshot>(before), 20))
            val reordered = original.copy(cards = cards.reversed(), recipe = original.recipe?.copy(
                boosters = recipe.boosters.map { it.copy(contents = it.contents.entries.reversed().associate { it.toPair() }) },
                sheets = sheets.entries.reversed().associate { (name, sheet) ->
                    name to sheet.copy(cards = sheet.cards.entries.reversed().associate { it.toPair() })
                }))
            assertEquals(packs, generate(reordered, 20))
            assertEquals(before, Json.encodeToString(original))
            assertNotEquals(packs, SetBoosters.generate(original, 20, RandomV1.encodeSeed(ByteArray(32) { 9 })))
        }
    }

    @Test fun validationRejectsInvalidIdentityCardsAndConflictingColorMetadata() {
        val base = source()
        val invalid = listOf(
            base.copy(setCode = "") to SetSourceIssue.SET_CODE,
            base.copy(setCode = "s".repeat(33)) to SetSourceIssue.SET_CODE,
            base.copy(setName = " ") to SetSourceIssue.SET_NAME,
            base.copy(setName = "s".repeat(257)) to SetSourceIssue.SET_NAME,
            base.copy(setName = "bad\u0000name") to SetSourceIssue.SET_NAME,
            base.copy(cards = emptyList()) to SetSourceIssue.CARDS,
            base.copy(cards = List(5001) { card(it) }) to SetSourceIssue.CARDS,
            base.copy(cards = listOf(card(1).copy(definition = "bad"))) to SetSourceIssue.CARD,
            base.copy(cards = listOf(card(1).copy(uuid = "bad"))) to SetSourceIssue.CARD,
            base.copy(cards = listOf(card(1), card(1))) to SetSourceIssue.DUPLICATE_UUID,
            base.copy(cards = listOf(card(1), card(2).copy(definition = card(1).definition, colors = setOf(DraftColor.W))))
                to SetSourceIssue.DEFINITION_COLORS,
        )
        for ((source, issue) in invalid) {
            assertContains(source.validate(), issue)
            assertFailsWith<IllegalArgumentException> { generate(source) }
        }
        assertTrue(source(List(5000) { card(it) }).validate().isEmpty())
        // Different UUIDs may legitimately resolve to the same definition.
        assertTrue(base.copy(cards = listOf(card(1), card(2).copy(definition = card(1).definition))).validate().isEmpty())
    }

    @Test fun validationRejectsMalformedRecipesWeightsUnknownCardsAndImpossibleDraws() {
        val cards = listOf(card(1), card(2))
        val base = recipe(sheet(cards))
        val main = base.sheets.getValue("main")
        fun withSheet(sheet: SetBoosterSheet) = base.copy(sheets = mapOf("main" to sheet))
        val invalid = listOf(
            base.copy(boosters = emptyList()) to SetSourceIssue.RECIPE_SIZE,
            base.copy(boosters = List(1001) { base.boosters.single() }) to SetSourceIssue.RECIPE_SIZE,
            base.copy(sheets = emptyMap()) to SetSourceIssue.RECIPE_SIZE,
            base.copy(sheets = (0..100).associate { "s$it" to main }) to SetSourceIssue.RECIPE_SIZE,
            base.copy(sourceSetCodes = List(101) { "TST" }) to SetSourceIssue.RECIPE_SIZE,
            base.copy(sourceSetCodes = listOf("")) to SetSourceIssue.RECIPE_SIZE,
            base.copy(name = "") to SetSourceIssue.RECIPE_SIZE,
            base.copy(boostersTotalWeight = 2) to SetSourceIssue.WEIGHT,
            base.copy(boosters = listOf(SetBoosterComposition(mapOf("main" to 1), 0))) to SetSourceIssue.WEIGHT,
            base.copy(boosters = listOf(SetBoosterComposition(mapOf("main" to 1), -1))) to SetSourceIssue.WEIGHT,
            base.copy(boosters = List(2) { SetBoosterComposition(mapOf("main" to 1), Long.MAX_VALUE) }) to SetSourceIssue.WEIGHT,
            withSheet(main.copy(totalWeight = 0)) to SetSourceIssue.WEIGHT,
            withSheet(sheet(cards) { 0 }) to SetSourceIssue.WEIGHT,
            withSheet(sheet(cards) { -1 }) to SetSourceIssue.WEIGHT,
            withSheet(sheet(cards) { Long.MAX_VALUE }) to SetSourceIssue.WEIGHT,
            withSheet(SetBoosterSheet(emptyMap())) to SetSourceIssue.SHEET,
            withSheet(SetBoosterSheet((0..5000).associate { card(it).uuid to 1 })) to SetSourceIssue.SHEET,
            base.copy(sheets = mapOf("" to main)) to SetSourceIssue.SHEET,
            withSheet(sheet(listOf(card(3)))) to SetSourceIssue.UNKNOWN_CARD,
            base.copy(boosters = listOf(SetBoosterComposition(emptyMap(), 1))) to SetSourceIssue.COMPOSITION,
            base.copy(boosters = listOf(SetBoosterComposition(mapOf("main" to 0), 1))) to SetSourceIssue.COMPOSITION,
            base.copy(boosters = listOf(SetBoosterComposition(mapOf("main" to -1), 1))) to SetSourceIssue.COMPOSITION,
            base.copy(boosters = listOf(SetBoosterComposition(mapOf("main" to 1001), 1))) to SetSourceIssue.COMPOSITION,
            base.copy(boosters = listOf(SetBoosterComposition(mapOf("main" to Int.MAX_VALUE, "other" to Int.MAX_VALUE), 1)))
                to SetSourceIssue.COMPOSITION,
            base.copy(boosters = listOf(SetBoosterComposition(mapOf("missing" to 1), 1))) to SetSourceIssue.SHEET,
            recipe(main, 3) to SetSourceIssue.SHEET_CAPACITY,
            recipe(main.copy(fixed = true), 3) to SetSourceIssue.SHEET_CAPACITY,
        )
        for ((recipe, issue) in invalid) {
            val source = source(cards, recipe)
            assertContains(source.validate(), issue, recipe.toString())
            assertFailsWith<IllegalArgumentException> { generate(source) }
        }
        val unknownUnusedSheet = base.copy(sheets = base.sheets + ("unused" to sheet(listOf(card(3)))))
        assertContains(source(cards, unknownUnusedSheet).validate(), SetSourceIssue.UNKNOWN_CARD)
        val maxWeight = recipe(sheet(cards.take(1)) { Int.MAX_VALUE.toLong() })
        assertTrue(source(cards, maxWeight).validate().isEmpty())
        assertEquals(1, generate(source(cards, maxWeight)).single().cards.size)
    }

    @Test fun checkedWeightTotalsRejectOverflowEvenWhenItWrapsToAPositiveDeclaredTotal() {
        val cards = List(3) { card(it + 1) }
        val weights = sheet(cards) { if (it == 2) 3 else Long.MAX_VALUE }
        assertEquals(1L, weights.totalWeight)
        assertContains(source(cards, recipe(weights)).validate(), SetSourceIssue.WEIGHT)
        val compositions = listOf(Long.MAX_VALUE, Long.MAX_VALUE, 3L).map { SetBoosterComposition(mapOf("main" to 1), it) }
        val recipe = SetBoosterRecipe(compositions, mapOf("main" to sheet(cards)))
        assertEquals(1L, recipe.boostersTotalWeight)
        assertContains(source(cards, recipe).validate(), SetSourceIssue.WEIGHT)
    }

    @Test fun generationRejectsInvalidSeedsCountsAndExcessiveAllocation() {
        for (count in listOf(-1, 0, SetBoosters.MAX_PACKS + 1, Int.MAX_VALUE)) {
            assertFailsWith<IllegalArgumentException> { generate(source(), count) }
        }
        assertFailsWith<IllegalArgumentException> { SetBoosters.generate(source(), 1, "bad") }
        val cards = listOf(card(1))
        val large = source(cards, recipe(sheet(cards).copy(allowDuplicates = true), SetSourceValidation.MAX_PACK_SIZE))
        assertTrue(large.validate().isEmpty())
        assertFailsWith<IllegalArgumentException> { generate(large, SetBoosters.MAX_PACKS) }
    }
}

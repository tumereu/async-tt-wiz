package org.asyncttwiz.protocol

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.*

class DraftEnginePacksTest {
    private val seed = RandomV1.encodeSeed(ByteArray(32) { it.toByte() })
    private fun packs(sizes: List<Int>) = sizes.mapIndexed { pack, size ->
        List(size) { card -> DraftCopy("copy-$pack-$card", "definition-$card", setOf(DraftColor.G)) }
    }

    private fun finish(initial: DraftState): DraftState {
        var state = initial
        var picks = 0
        val originalIds = state.copies.map { it.id }.toSet()
        while (!state.finished) {
            assertTrue(picks++ < 10000, "Draft must make progress")
            val player = state.seating.first { state.queues.getValue(it).isNotEmpty() }
            val pack = assertNotNull(DraftEngine.privateView(state, player).head)
            val ids = pack.cards.map { it.id }
            val burn = if (ids.size <= state.settings.keep + state.settings.burn) emptyList()
                else ids.drop(state.settings.keep).take(state.settings.burn)
            val result = DraftEngine.pick(state, DraftPick(player, pack.id, ids.take(state.settings.keep), burn))
            assertNull(result.reason)
            state = assertNotNull(result.state)
            val accounted = state.packs.flatMap { it.cards } + state.log.flatMap { it.kept + it.burned + it.discarded }
            assertEquals(originalIds.size, accounted.size)
            assertEquals(originalIds, accounted.toSet())
        }
        assertTrue(state.packs.all { it.cards.isEmpty() })
        assertTrue(state.queues.values.all { it.isEmpty() })
        assertEquals(state.log.flatMap { it.kept }.toSet(), state.pools.values.flatten().toSet())
        return state
    }

    @Test fun varyingPackSizesCompleteForTwoAndThreePlayersWithAllEndOfPackRules() {
        for (players in listOf(2, 3)) {
            for (open in listOf(1, 3)) {
                val members = List(players) { "player-$it" }
                // Includes empty, automatic, near-end burns, ordinary passes, and packs larger than cardsPerPack.
                val packs = packs(List(players * 4) { listOf(0, 1, 2, 3, 4, 7, 9, 31)[it % 8] })
                for (discard in listOf(0, 2)) {
                    val settings = DraftSettings(packsPerPlayer = 4, cardsPerPack = 5, keep = 2, burn = 2,
                        discardRemainingAt = discard, packsOpenAtOnce = open)
                    val state = DraftEngine.startWithPacks(members, packs, settings, seed)
                    assertEquals(packs.flatten(), state.copies)
                    val final = finish(state)
                    assertEquals(settings.rounds, final.round)
                    assertTrue(final.log.any { it.automatic })
                    assertTrue(final.log.any { it.burned.isNotEmpty() })
                    if (discard > 0) assertTrue(final.log.any { it.discarded.isNotEmpty() })
                }
            }
        }
    }

    @Test fun packsAreAssignedInOpeningNumberThenSeededSeatOrderWithoutRedrawing() {
        val settings = DraftSettings(packsPerPlayer = 3, cardsPerPack = 1, packsOpenAtOnce = 2, colorBalance = true)
        val packs = packs(listOf(4, 5, 6, 7, 8, 9))
        val state = DraftEngine.startWithPacks(listOf("b", "a"), packs, settings, seed)
        for ((index, pack) in state.packs.withIndex()) {
            assertEquals(packs[index].map { it.id }, pack.cards)
            assertEquals(index / 2 + 1, pack.number)
            assertEquals((index / 2) / 2, pack.round)
            assertEquals(state.seating[index % 2], pack.initialPlayer)
        }
        val expectedSeating = DraftEngine.start(listOf("a", "b"), listOf(DraftCubeEntry("definition", 6)), settings, seed).seating
        assertEquals(expectedSeating, state.seating)
        assertEquals(state, DraftEngine.startWithPacks(listOf("a", "b"), packs, settings, seed))
        val before = Json.encodeToString(state)
        val final = finish(state)
        assertEquals(before, Json.encodeToString(state))
        assertEquals(final, finish(Json.decodeFromString<DraftState>(before)))
        assertEquals(packs.sumOf { it.size }, final.pools.values.sumOf { it.size })
    }

    @Test fun allEmptyOrSmallPacksAutomaticallyCompleteAcrossRoundBarriers() {
        for (players in listOf(2, 3)) {
            val settings = DraftSettings(packsPerPlayer = 4, keep = 2, packsOpenAtOnce = 3)
            val packs = packs(List(players * 4) { it % 3 })
            val state = DraftEngine.startWithPacks(List(players) { "p$it" }, packs, settings, seed)
            assertTrue(state.finished)
            assertTrue(state.log.all { it.automatic })
            assertEquals(packs.sumOf { it.size }, state.pools.values.sumOf { it.size })
        }
    }

    @Test fun generatedRecipeAndApproximatePacksCompleteFullDraftsForTwoAndThreePlayers() {
        val cards = List(8) { index ->
            SetSourceCard("sha256:" + index.toString(16).padStart(64, '0'),
                "00000000-0000-0000-0000-" + index.toString(16).padStart(12, '0'),
                SetCardRarity.entries[index % SetCardRarity.entries.size])
        }
        val recipe = SetBoosterRecipe(listOf(SetBoosterComposition(mapOf("main" to 3), 1),
            SetBoosterComposition(mapOf("main" to 7), 2)),
            mapOf("main" to SetBoosterSheet(cards.associate { it.uuid to 1 })))
        for (players in listOf(2, 3)) {
            for (source in listOf(SetSourceSnapshot("TST", "Test", cards), SetSourceSnapshot("TST", "Test", cards, recipe))) {
                val settings = DraftSettings(packsPerPlayer = 3, packsOpenAtOnce = 2)
                val packs = SetBoosters.generate(source, players * settings.packsPerPlayer, seed).map { it.cards }
                val state = DraftEngine.startWithPacks(List(players) { "p$it" }, packs, settings, seed)
                assertEquals(packs.sumOf { it.size }, finish(state).pools.values.sumOf { it.size })
            }
        }
    }

    @Test fun invalidPacksMembersSettingsAndSeedAreRejected() {
        val settings = DraftSettings(packsPerPlayer = 1)
        val members = listOf("a", "b")
        val valid = packs(listOf(3, 4))
        for (packs in listOf(emptyList(), valid.take(1), valid + valid.take(1), listOf(valid[0], valid[0]),
            listOf(valid[0], listOf(DraftCopy("", "definition"))),
            listOf(valid[0], listOf(DraftCopy("unique", ""))))) {
            assertFailsWith<IllegalArgumentException> { DraftEngine.startWithPacks(members, packs, settings, seed) }
        }
        for (members in listOf(listOf("a"), listOf("a", "a"), listOf("a", ""))) {
            assertFailsWith<IllegalArgumentException> { DraftEngine.startWithPacks(members, valid, settings, seed) }
        }
        assertFailsWith<IllegalArgumentException> { DraftEngine.startWithPacks(members, valid, settings.copy(keep = 0), seed) }
        assertFailsWith<IllegalArgumentException> { DraftEngine.startWithPacks(members, valid, settings, "bad") }
    }
}

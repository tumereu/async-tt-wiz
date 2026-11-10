package org.asyncttwiz.protocol

import kotlin.test.*

/** The pack grid of multiset drafting: its shape per mode, its shortcuts and the packs built from it. */
class PodPackGridTest {
    private val booster = DraftSettings.booster()
    private val seed = RandomV1.encodeSeed(ByteArray(32) { it.toByte() })

    // DRF-177: one row per round, one cell per pack of that round, sized by the rounds and packs-per-round settings
    @Test fun theGridHasARowPerRoundAndACellPerPackOfThatRound() {
        assertEquals(listOf(1, 1, 1), PodPackGrid.rows(booster))
        assertEquals(listOf(2, 2, 1), PodPackGrid.rows(booster.copy(packsPerPlayer = 5, packsOpenAtOnce = 2)))
        assertEquals(listOf(3, 3, 3), PodPackGrid.rows(DraftSettings.glimpse().copy(packsOpenAtOnce = 3)))
        assertEquals(List(18) { 1 }, PodPackGrid.rows(DraftSettings.grid()))
        assertEquals(listOf(6, 6, 6), PodPackGrid.rows(DraftSettings.grid().copy(gridsOpenAtOnce = 6)))
        assertEquals(listOf(1, 1, 1), PodPackGrid.rows(DraftSettings.rochester()))
        // Sealed is one row; so are Winston and Winchester, whose packs are all shuffled into one stack
        assertEquals(listOf(6), PodPackGrid.rows(DraftSettings.sealed()))
        assertEquals(listOf(3), PodPackGrid.rows(DraftSettings.winston()))
        assertEquals(listOf(3), PodPackGrid.rows(DraftSettings.winchester()))
        // Rotisserie opens boosters until the pool is full: one set for the whole pod
        assertNull(PodPackGrid.rows(DraftSettings.rotisserie()))
        assertEquals(listOf("A"), PodPackGrid.normalize(listOf("A", "B"), DraftSettings.rotisserie()))
    }

    // DRF-178: a cell, a row label ("Round 2") and a column label ("Pack 1")
    @Test fun aCellARowLabelAndAColumnLabelSetTheirPacks() {
        val settings = booster.copy(packsPerPlayer = 5, packsOpenAtOnce = 2) // rows: [0 1] [2 3] [4]
        val one = listOf("RTR")
        assertEquals(listOf("RTR", "RTR", "GTC", "GTC", "RTR"), PodPackGrid.setRow(one, settings, 1, "GTC"))
        // The last round only: trailing repeats are dropped, the cells read the same
        assertEquals(listOf("RTR", "RTR", "RTR", "RTR", "GTC"), PodPackGrid.cells(PodPackGrid.setRow(one, settings, 2, "GTC"), settings))
        assertEquals(listOf("RTR", "RTR", "RTR", "RTR", "GTC"), PodPackGrid.setRow(one, settings, 2, "GTC"))
        assertEquals(listOf("GTC", "RTR", "GTC", "RTR", "GTC"), PodPackGrid.setColumn(one, settings, 0, "GTC"))
        // A column past a short round's end leaves that round alone
        assertEquals(listOf("RTR", "GTC", "RTR", "GTC", "RTR"), PodPackGrid.setColumn(one, settings, 1, "GTC"))
        assertEquals(listOf("RTR", "RTR", "RTR", "DGM", "RTR"), PodPackGrid.setCell(one, settings, PodPackGrid.index(settings, 1, 1), "DGM"))
        assertEquals(3, PodPackGrid.index(settings, 1, 1))
        // Setting every pack back to one set is that set alone
        assertEquals(one, PodPackGrid.setRow(listOf("RTR", "GTC"), DraftSettings.sealed().copy(packsPerPlayer = 2), 0, "RTR"))
        // Original RTR block: one round each
        val block = PodPackGrid.setRow(PodPackGrid.setRow(one, booster, 1, "GTC"), booster, 2, "DGM")
        assertEquals(listOf("RTR", "GTC", "DGM"), block)
        assertTrue(PodPackGrid.mixed(block))
        assertFalse(PodPackGrid.mixed(one))
    }

    // DRF-179: settings changes resize the grid: a bigger one repeats the last set, a smaller one drops its last packs
    @Test fun aSettingsChangeResizesTheGrid() {
        val block = listOf("RTR", "GTC", "DGM")
        assertEquals(List(4) { listOf("RTR", "GTC", "DGM", "DGM")[it] }, PodPackGrid.cells(block, booster.copy(packsPerPlayer = 4)))
        assertEquals(listOf("RTR", "GTC"), PodPackGrid.normalize(block, booster.copy(packsPerPlayer = 2)))
        assertEquals(block, PodPackGrid.normalize(block + "DGM" + "DGM", booster))
        // The same packs, read row by row, whatever the rounds
        assertEquals(block, PodPackGrid.normalize(block, booster.copy(packsOpenAtOnce = 3)))
        assertEquals(listOf("RTR", "GTC", "DGM", "DGM", "DGM", "DGM"), PodPackGrid.cells(block, DraftSettings.sealed()))
    }

    // DRF-179: each engine's pack i takes its cell's set
    @Test fun eachEnginesPacksTakeTheirCellsSet() {
        val block = listOf("RTR", "GTC", "DGM")
        assertEquals(listOf("RTR", "RTR", "GTC", "GTC", "DGM", "DGM"), PodPackGrid.packSets(block, booster, 2))
        assertEquals(listOf("RTR", "RTR", "GTC", "GTC", "DGM", "DGM"), PodPackGrid.packSets(block, DraftSettings.winston(), 2))
        // Sealed: each player's boosters together
        val sealed = DraftSettings.sealed().copy(packsPerPlayer = 3)
        assertEquals(listOf("RTR", "GTC", "DGM", "RTR", "GTC", "DGM"), PodPackGrid.packSets(block, sealed, 2))
        assertEquals(listOf("RTR", "GTC", "DGM"), PodPackGrid.packSets(block, DraftSettings.grid().copy(grids = 3), 4))
        assertTrue(PodPackGrid.valid(block))
        assertFalse(PodPackGrid.valid(emptyList()))
        assertFalse(PodPackGrid.valid(listOf("R T R")))
        assertFalse(PodPackGrid.valid(List(61) { "RTR" }))
    }

    private fun card(index: Int) = SetSourceCard("sha256:" + index.toString(16).padStart(64, '0'),
        "00000000-0000-0000-0000-" + index.toString(16).padStart(12, '0'), SetCardRarity.COMMON)
    private fun set(code: String, first: Int) = SetSourceSnapshot(code, "Set $code", List(20) { card(first + it) })

    // DRF-179, DRF-169: every pack is built from its own cell's set; a pod of one set gets the packs one set always gave
    @Test fun packsComeFromTheirCellsSetAndOneSetDealsAsBefore() {
        val a = set("AAA", 0); val b = set("BBB", 100)
        val sources = SetPackSources(listOf("AAA", "BBB", "AAA"), listOf(a, b))
        val packs = SetBoosters.generate(sources, listOf("AAA", "BBB", "AAA"), seed, 5)
        val inA = a.cards.map { it.definition }.toSet(); val inB = b.cards.map { it.definition }.toSet()
        assertTrue(packs[0].cards.all { it.definition in inA }); assertTrue(packs[1].cards.all { it.definition in inB })
        assertTrue(packs[2].cards.all { it.definition in inA })
        assertEquals(packs.flatMap { it.cards }.map { it.id }.distinct().size, packs.sumOf { it.cards.size }, "no two copies share an id")
        assertEquals(SetBoosters.generate(a, 3, seed, 5), SetBoosters.generate(SetPackSources.of(a), listOf("AAA", "AAA", "AAA"), seed, 5))
        assertNotEquals(SetBoosters.setSeed(seed, 1), SetBoosters.setSeed(seed, 2))
        assertEquals(seed, SetBoosters.setSeed(seed, 0))
        // Grid with fresh boosters and without: grid i from its cell's set
        val grid = DraftSettings.grid().copy(grids = 3)
        val gridSources = SetPackSources(listOf("AAA", "BBB"), listOf(a, b))
        for (cards in listOf(GridEngine.freshBoosterCards(gridSources, grid, 2, seed), GridEngine.setCards(gridSources, grid, 2, seed))) {
            assertEquals(27, cards.size)
            assertTrue(cards.take(9).all { it.definition in inA }); assertTrue(cards.drop(9).all { it.definition in inB })
        }
        assertEquals(GridEngine.freshBoosterCards(a, grid, 2, seed), GridEngine.freshBoosterCards(SetPackSources.of(a), grid, 2, seed))
        // Sealed: every player's booster i from cell i
        val sealed = LimitedEngine.startFromSet(listOf("p1", "p2"), SetPackSources(listOf("AAA", "BBB"), listOf(a, b)),
            DraftSettings.sealed().copy(packsPerPlayer = 2, cardsPerPack = 5), seed)
        val definitions = sealed.copies.associate { it.id to it.definition }
        sealed.pools.values.forEach { pool ->
            assertEquals(5, pool.count { definitions.getValue(it) in inA }); assertEquals(5, pool.count { definitions.getValue(it) in inB })
        }
    }
}

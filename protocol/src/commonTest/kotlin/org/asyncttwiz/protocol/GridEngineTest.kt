package org.asyncttwiz.protocol

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.*

class GridEngineTest {
    private val seed = RandomV1.encodeSeed(ByteArray(32) { it.toByte() })
    private fun settings(grids: Int = 1, open: Int = 1, twoPicks: Boolean = false) =
        DraftSettings.grid().copy(grids = grids, gridsOpenAtOnce = open, twoPicks = twoPicks)
    private fun cube(count: Int) = List(count) { DraftCubeEntry("definition-$it", 1) }
    private fun start(players: Int = 2, settings: DraftSettings = settings(), extra: Int = 0): GridState {
        val count = settings.supply(players, emptyList()).needed.toInt()
        return GridEngine.start(List(players) { "player-$it" }, cube(count + extra), settings, seed)
    }
    private fun row(index: Int) = GridLine(GridLineType.ROW, index)
    private fun column(index: Int) = GridLine(GridLineType.COLUMN, index)

    /** The oldest grid that is some member's head. */
    private fun head(state: GridState) = state.grids.first { grid -> state.queues.values.any { it.firstOrNull() == grid.id } }

    private fun pick(state: GridState, grid: GridBoard = head(state), line: GridLine = row(0)): GridState {
        val result = GridEngine.pick(state, GridPick(assertNotNull(grid.currentPlayer), grid.id, line))
        assertNull(result.reason)
        return assertNotNull(result.state)
    }

    private fun next(state: GridState): GridState {
        val grid = head(state)
        val line = (0..2).first { row -> (0..2).any { grid.positions[row * 3 + it] != null } }
        return pick(state, grid, row(line))
    }

    private fun finish(initial: GridState, check: (GridState) -> Unit = {}): GridState {
        var state = initial
        var actions = 0
        while (!state.finished) {
            check(state)
            assertTrue(actions++ < 1000, "Draft must make progress")
            state = next(state)
        }
        check(state)
        return state
    }

    private fun assertConserved(state: GridState) {
        val accounted = state.supply + state.grids.flatMap { it.positions.filterNotNull() + it.reserve } +
            state.pools.values.flatten() + state.log.flatMap { it.discarded }
        assertEquals(state.copies.size, accounted.size)
        assertEquals(state.copies.map { it.id }.toSet(), accounted.toSet())
        assertEquals(accounted.size, accounted.distinct().size)
        for (player in state.seating) {
            assertEquals(state.log.filter { it.player == player }.flatMap { it.kept }, state.pools.getValue(player))
        }
        assertTrue(state.grids.all { it.positions.size == 9 })
        assertTrue(state.grids.filter { it.finished }.all { it.positions.all { card -> card == null } })
    }

    // DRF-021, DRF-023, DRF-028
    @Test fun defaultsBoundariesAndModeSpecificValidation() {
        val defaults = DraftSettings.grid()
        assertEquals(DraftMode.GRID, defaults.mode)
        assertEquals(18, defaults.grids)
        assertFalse(defaults.twoPicks)
        assertEquals(1, defaults.gridsOpenAtOnce)
        for (players in 2..4) assertTrue(defaults.validate(players).isEmpty())
        for (players in listOf(-1, 0, 1, 5, 8)) assertContains(defaults.validate(players), DraftSettingsIssue.PLAYERS)
        for (grids in listOf(0, 61)) assertContains(defaults.copy(grids = grids).validate(2), DraftSettingsIssue.GRIDS)
        for (open in listOf(0, 19)) {
            assertContains(defaults.copy(gridsOpenAtOnce = open).validate(2), DraftSettingsIssue.GRIDS_OPEN_AT_ONCE)
        }
        assertTrue(defaults.copy(grids = 1).validate(2).isEmpty())
        assertTrue(defaults.copy(grids = 60, gridsOpenAtOnce = 60, sideboardLimit = 0).validate(4).isEmpty())
        assertTrue(defaults.copy(twoPicks = true).validate(2).isEmpty())
        for (players in listOf(3, 4)) {
            assertContains(defaults.copy(twoPicks = true).validate(players), DraftSettingsIssue.TWO_PICKS)
        }
        assertContains(defaults.copy(sideboardLimit = -1).validate(2), DraftSettingsIssue.SIDEBOARD_LIMIT)
        // Pack settings have no effect on Grid, and Grid settings have no effect on packs.
        assertTrue(defaults.copy(packsPerPlayer = 0, cardsPerPack = 0, keep = 0, burn = -1,
            discardRemainingAt = -1, packsOpenAtOnce = 0).validate(2).isEmpty())
        assertTrue(DraftSettings.booster().copy(grids = 0, gridsOpenAtOnce = 0, twoPicks = true).validate(8).isEmpty())
        assertEquals(2, settings(grids = 5, open = 3).rounds)
    }

    @Test fun preGeneratedSetCardsKeepTheirDrawOrderThroughBoardsAndRefills() {
        val settings = settings(grids = 3, open = 2, twoPicks = true)
        val cards = List(36) { DraftCopy("set-copy-$it", "definition-$it") }
        val initial = GridEngine.startWithCards(listOf("a", "b"), cards, settings, seed)
        // Grid by grid, all dealt at the start: nine cards, then the grid's own refill cards.
        assertEquals(3, initial.grids.size)
        assertEquals(cards.take(9).map { it.id }, initial.grids[0].positions)
        assertEquals(cards.slice(9..11).map { it.id }, initial.grids[0].reserve)
        assertEquals(cards.slice(12..20).map { it.id }, initial.grids[1].positions)
        assertEquals(cards.slice(33..35).map { it.id }, initial.grids[2].reserve)
        val after = pick(initial)
        assertEquals(cards.slice(9..11).map { it.id }, after.grids[0].positions.take(3))
        assertConserved(finish(after))
        assertEquals(initial, GridEngine.startWithCards(listOf("b", "a"), cards, settings, seed))
        assertFailsWith<IllegalArgumentException> {
            GridEngine.startWithCards(listOf("a", "b"), cards.dropLast(1), settings, seed)
        }
        assertFailsWith<IllegalArgumentException> {
            GridEngine.startWithCards(listOf("a", "b"), cards + cards.first(), settings, seed)
        }
    }

    private fun setCard(index: Int) = SetSourceCard("sha256:" + index.toString(16).padStart(64, '0'),
        "00000000-0000-0000-0000-" + index.toString(16).padStart(12, '0'), SetCardRarity.COMMON)
    private fun setSource(boosterSize: Int? = null): SetSourceSnapshot {
        val cards = List(20) { setCard(it + 1) }
        val recipe = boosterSize?.let { size -> SetBoosterRecipe(listOf(SetBoosterComposition(mapOf("main" to size), 1)),
            mapOf("main" to SetBoosterSheet(cards.associate { it.uuid to 1L }))) }
        return SetSourceSnapshot("TST", "Test set", cards, recipe)
    }
    private fun booster(id: String) = id.substringBefore(":copy:")

    // DRF-091
    @Test fun freshBoostersGiveEachGridRandomCardsFromItsOwnBooster() {
        val settings = settings(grids = 3)
        val cards = GridEngine.freshBoosterCards(setSource(), settings, 2, seed)
        assertEquals(27, cards.size)
        assertEquals(cards, GridEngine.freshBoosterCards(setSource(), settings, 2, seed))
        cards.chunked(9).forEachIndexed { grid, chunk -> assertEquals(setOf("set-pack-$grid"), chunk.map { booster(it.id) }.toSet()) }
        // Not simply the booster's first nine cards in pack order.
        assertNotEquals(SetBoosters.generate(setSource(), 3, seed).flatMap { it.cards.take(9) }, cards)
        val state = GridEngine.startWithCards(listOf("a", "b"), cards, settings, seed)
        assertEquals(cards.take(9).map { it.id }, state.grids[0].positions)
        assertTrue(state.grids[0].reserve.isEmpty()) // two players without two picks never refill
        assertConserved(finish(state))
    }

    // DRF-091
    @Test fun freshBoosterRefillsComeFromTheGridsOwnBoosterWithSeveralGridsOpen() {
        val settings = settings(grids = 4, open = 2)
        val cards = GridEngine.freshBoosterCards(setSource(), settings, 3, seed)
        assertEquals(48, cards.size)
        cards.chunked(12).forEachIndexed { grid, chunk -> assertEquals(setOf("set-pack-$grid"), chunk.map { booster(it.id) }.toSet()) }
        val initial = GridEngine.startWithCards(listOf("a", "b", "c"), cards, settings, seed)
        assertEquals(4, initial.grids.size)
        assertEquals(2, GridEngine.publicView(initial).grids.size)
        // The second grid opened before the first refilled, yet each refill still comes from its own booster.
        val second = pick(initial, initial.grids[1])
        assertEquals(cards.slice(21..23).map { it.id }, second.grids[1].positions.take(3))
        val first = pick(second, second.grids[0])
        assertEquals(cards.slice(9..11).map { it.id }, first.grids[0].positions.take(3))
        assertTrue(first.grids.take(2).all { it.reserve.isEmpty() })
        assertConserved(finish(first) { assertConserved(it) })
    }

    @Test fun smallFreshBoostersAreToppedUpFromSpareBoosters() {
        val settings = settings(grids = 2)
        val cards = GridEngine.freshBoosterCards(setSource(boosterSize = 5), settings, 2, seed)
        assertEquals(18, cards.size)
        assertEquals(cards.size, cards.map { it.id }.distinct().size)
        cards.chunked(9).forEachIndexed { grid, chunk ->
            assertEquals(5, chunk.count { booster(it.id) == "set-pack-$grid" })
            assertTrue(chunk.filter { booster(it.id) != "set-pack-$grid" }.all { booster(it.id).removePrefix("set-pack-").toInt() >= 2 })
        }
        assertConserved(finish(GridEngine.startWithCards(listOf("a", "b"), cards, settings, seed)))
    }

    // DRF-027
    @Test fun freshBoostersDefaultOnAndOldSettingsDecodeWithIt() {
        assertTrue(DraftSettings.grid().freshBoosters)
        assertTrue(Json.decodeFromString<DraftSettings>("""{"mode":"GRID"}""").freshBoosters)
        val off = DraftSettings.grid().copy(freshBoosters = false)
        assertEquals(off, Json.decodeFromString<DraftSettings>(Json.encodeToString(off)))
        assertTrue(Json.decodeFromString<GridBoard>("""{"id":"g","number":1,"turns":["a"],"positions":[]}""").reserve.isEmpty())
    }

    @Test fun exactMaximumSupplyAndInsufficientCubes() {
        for (players in 2..4) {
            for (twoPicks in if (players == 2) listOf(false, true) else listOf(false)) {
                val settings = settings(grids = 5, open = 3, twoPicks = twoPicks)
                val needed = 5L * if (players == 2 && !twoPicks) 9 else 12
                assertEquals(DraftSupply(needed, needed), settings.supply(players, cube(needed.toInt())))
                assertFalse(settings.supply(players, cube(needed.toInt() - 1)).sufficient)
                assertFailsWith<IllegalArgumentException> {
                    GridEngine.start(List(players) { "p-$it" }, cube(needed.toInt() - 1), settings, seed)
                }
                val final = finish(start(players, settings), ::assertConserved)
                assertTrue(final.supply.isEmpty(), "Exact supply is fully consumed, including all refills")
                assertEquals(5, final.grids.size)
                val accounted = final.pools.values.flatten() + final.log.flatMap { it.discarded }
                assertEquals(needed.toInt(), accounted.size)
                assertEquals(needed.toInt(), accounted.distinct().size)
            }
        }
        assertEquals(2L * Int.MAX_VALUE, settings().supply(2,
            listOf(DraftCubeEntry("a", Int.MAX_VALUE), DraftCubeEntry("b", Int.MAX_VALUE))).available)
    }

    @Test fun invalidSetupIsRejected() {
        val members = listOf("a", "b")
        assertFailsWith<IllegalArgumentException> { GridEngine.start(members, cube(20), DraftSettings.booster(), seed) }
        for (players in listOf(listOf("a"), listOf("a", "a"), listOf("a", ""), List(5) { "p-$it" })) {
            assertFailsWith<IllegalArgumentException> { GridEngine.start(players, cube(20), settings(), seed) }
        }
        for (bad in listOf(DraftCubeEntry("", 1), DraftCubeEntry("bad", 0), DraftCubeEntry("bad", -1))) {
            assertFailsWith<IllegalArgumentException> { GridEngine.start(members, cube(20) + bad, settings(), seed) }
        }
        assertFailsWith<IllegalArgumentException> {
            GridEngine.start(members, listOf(DraftCubeEntry("same", 10, setOf(DraftColor.W)),
                DraftCubeEntry("same", 10, setOf(DraftColor.U))), settings(), seed)
        }
    }

    // DRF-079
    @Test fun dealingAndRefillHappenOnlyAfterFirstSelection() {
        for (players in 2..4) {
            for (twoPicks in if (players == 2) listOf(false, true) else listOf(false)) {
                var state = start(players, settings(twoPicks = twoPicks))
                val initial = state.grids.single()
                assertEquals(9, initial.positions.filterNotNull().size)
                assertEquals(9, initial.positions.distinct().size)
                val refills = players >= 3 || twoPicks
                assertTrue(state.supply.isEmpty())
                assertEquals(if (refills) 3 else 0, state.grids.single().reserve.size)
                state = pick(state, line = row(1))
                val grid = state.grids.single()
                assertEquals(initial.positions.slice(3..5), state.log.single().kept)
                assertEquals(if (refills) 9 else 6, grid.positions.filterNotNull().size)
                assertEquals(initial.positions.take(3), grid.positions.take(3))
                assertEquals(initial.positions.drop(6), grid.positions.drop(6))
                if (refills) {
                    assertTrue(grid.positions.slice(3..5).all { it in state.copies.map { copy -> copy.id } })
                    assertEquals(initial.reserve, grid.positions.slice(3..5))
                } else assertEquals(listOf(null, null, null), grid.positions.slice(3..5))
                assertTrue(grid.reserve.isEmpty())
                state = pick(state, line = column(0))
                assertEquals(if (refills) 3 else 2, state.log.last().kept.size)
                if (!state.finished) {
                    assertEquals(listOf(null, null, null), state.grids.single().positions.filterIndexed { i, _ -> i % 3 == 0 })
                }
                val final = finish(state, ::assertConserved)
                assertEquals(if (twoPicks) 4 else players, final.log.size)
                assertTrue(final.log.all { it.line != null })
                assertTrue(final.log.dropLast(1).all { it.discarded.isEmpty() })
            }
        }
    }

    // DRF-079
    @Test fun normalTurnsFollowDraftmancersOrders() {
        // Seat indices per grid: two players alternate the starter; three go through all six orders; four through the
        // four rotations and then the four reversed ones.
        val orders = mapOf(
            2 to listOf(listOf(0, 1), listOf(1, 0)),
            3 to listOf(listOf(0, 1, 2), listOf(1, 2, 0), listOf(2, 0, 1), listOf(0, 2, 1), listOf(2, 1, 0), listOf(1, 0, 2)),
            4 to listOf(listOf(0, 1, 2, 3), listOf(1, 2, 3, 0), listOf(2, 3, 0, 1), listOf(3, 0, 1, 2),
                listOf(0, 3, 2, 1), listOf(3, 2, 1, 0), listOf(2, 1, 0, 3), listOf(1, 0, 3, 2)))
        for (players in 2..4) {
            for (open in listOf(1, 3)) {
                val initial = start(players, settings(grids = 9, open = open))
                val final = finish(initial)
                for ((index, grid) in final.grids.withIndex()) {
                    val order = orders.getValue(players)
                    val expected = order[index % order.size].map { initial.seating[it] }
                    assertEquals(expected, grid.turns)
                    assertEquals(expected, final.log.filter { it.gridId == grid.id }.map { it.player })
                }
                assertEquals(List(players) { 9 }, final.seating.map { player -> final.log.count { it.player == player } })
            }
        }
    }

    @Test fun twoPicksUsesAbabThenBabaWithoutRefillingAgain() {
        var state = start(settings = settings(grids = 2, twoPicks = true))
        val a = state.seating[0]
        val b = state.seating[1]
        assertEquals(listOf(a, b, a, b), state.grids.first().turns)
        for (gridNumber in 1..2) {
            val expected = if (gridNumber == 1) listOf(a, b, a, b) else listOf(b, a, b, a)
            for ((turn, player) in expected.withIndex()) {
                val grid = state.grids.first { !it.finished }
                assertEquals(player, grid.currentPlayer)
                // First take row 0, then take it again after refill, then the other two rows.
                state = pick(state, grid, row(if (turn < 2) 0 else turn - 1))
                assertEquals(3, state.log.last().kept.size)
            }
        }
        assertTrue(state.finished)
        assertEquals(listOf(a, b, a, b, b, a, b, a), state.log.map { it.player })
        assertTrue(state.pools.values.all { it.size == 12 })
        assertTrue(state.log.all { it.discarded.isEmpty() })
        assertConserved(state)
    }

    // DRF-079
    @Test fun selectionsTakeAllRemainingCardsAcrossGapsAndEmptyLinesAreRejected() {
        var state = start()
        val original = state.grids.single().positions
        state = pick(state, line = row(1))
        val grid = state.grids.single()
        val snapshot = Json.encodeToString(state)
        val rejected = GridEngine.pick(state, GridPick(assertNotNull(grid.currentPlayer), grid.id, row(1)))
        assertEquals(GridPickRejection.EMPTY_LINE, rejected.reason)
        assertNull(rejected.state)
        assertEquals(snapshot, Json.encodeToString(state))
        state = pick(state, grid, column(2))
        assertEquals(listOf(original[2], original[8]), state.log.last().kept)
        assertEquals(listOf(original[0], original[1], original[6], original[7]), state.log.last().discarded)
        assertTrue(state.finished)
        assertConserved(state)

        // The symmetric case: a column gap inside a selected row.
        var other = pick(start(), line = column(1))
        other = pick(other, line = row(2))
        assertEquals(listOf(original[6], original[8]), other.log.last().kept)
        assertConserved(other)
    }

    private fun queue(state: GridState, player: String) = GridEngine.privateView(state, player).queue
    private fun visible(state: GridState, player: String) =
        GridEngine.publicView(state, player).let { view -> (view.grids + view.finishedGrids).map { it.number }.toSet() }

    @Test fun freshGridsComeFirstAndANewArrivalNeverReplacesTheHead() {
        var state = start(settings = settings(grids = 6, open = 3))
        val a = state.seating[0]
        val b = state.seating[1]
        assertEquals(listOf("grid-0", "grid-2"), queue(state, a))
        assertEquals(listOf("grid-1"), queue(state, b))
        assertEquals(GridPickRejection.NOT_HEAD_GRID, GridEngine.pick(state, GridPick(a, "grid-2", row(0))).reason)
        state = pick(state, state.grids[0])
        // B keeps looking at the head; the passed grid queues behind it.
        assertEquals(listOf("grid-1", "grid-0"), queue(state, b))
        state = pick(state, state.grids[1])
        // A's own fresh grid stays ahead of the grid B passed.
        assertEquals(listOf("grid-2", "grid-1"), queue(state, a))
        assertEquals(GridPickRejection.NOT_HEAD_GRID, GridEngine.pick(state, GridPick(a, "grid-1", row(1))).reason)
        val final = finish(state, ::assertConserved)
        assertTrue(GridEngine.publicView(final).grids.isEmpty())
        assertEquals(6, GridEngine.publicView(final).finishedGrids.size)
        assertTrue(final.seating.all { GridEngine.privateView(final, it).headGridId == null })
    }

    /** Owner's example: two players, three grids open; A opens round 2 alone once nothing of round 1 can come back. */
    // DRF-080
    @Test fun aMemberOpensTheirNextRoundOfGridsOnceNoOpenGridCanReachThem() {
        var state = start(settings = settings(grids = 18, open = 3))
        val a = state.seating[0]
        val b = state.seating[1]
        // Round 1: grid 1 (A first), grid 2 (B first), grid 3 (A first). Round 2: grid 4 (B), grid 5 (A), grid 6 (B).
        assertEquals(listOf(a, b, a, b, a, b), state.grids.take(6).map { it.turns.first() })
        state = pick(state, state.grids[0])
        state = pick(state, state.grids[2])
        assertEquals(1, GridEngine.privateView(state, a).round)
        state = pick(state, state.grids[1]) // B's first pick of grid 2
        state = pick(state, state.grids[1], row(1)) // A's last pick of round 1
        // Only B's last picks of grids 1 and 3 remain: A opens round 2, where A picks first in grid 5 only.
        assertEquals(listOf("grid-4"), queue(state, a))
        assertEquals(2, GridEngine.privateView(state, a).round)
        assertEquals(listOf("grid-0", "grid-2"), queue(state, b))
        assertEquals(1, GridEngine.privateView(state, b).round)
        assertEquals(0, state.round)
        assertEquals(setOf(1, 2, 3, 5), visible(state, a))
        // B still on round 1 sees none of round 2, and grids no one has opened stay hidden from everyone.
        assertEquals(setOf(1, 2, 3), visible(state, b))
        assertEquals(setOf(1, 2, 3, 5), GridEngine.publicView(state).let { (it.grids + it.finishedGrids).map { g -> g.number }.toSet() })
        state = pick(state, state.grids[4])
        // Grid 5 waits unseen for B; A waits for grids 4 and 6, which B has not opened.
        assertEquals(listOf("grid-4"), state.held[b])
        assertTrue(queue(state, a).isEmpty())
        assertEquals(listOf("grid-0", "grid-2"), queue(state, b))
        assertEquals(GridPickRejection.NOT_YOUR_TURN, GridEngine.pick(state, GridPick(b, "grid-4", row(1))).reason)
        assertEquals(GridPickRejection.NOT_YOUR_TURN, GridEngine.pick(state, GridPick(b, "grid-3", row(1))).reason)
        state = pick(state, state.grids[0], row(1))
        state = pick(state, state.grids[2], row(1))
        // B opens round 2: their own fresh grids 4 and 6 before grid 5 that A already picked from.
        assertEquals(listOf("grid-3", "grid-5", "grid-4"), queue(state, b))
        assertTrue(state.held.isEmpty())
        assertEquals(1, state.round)
        assertEquals(setOf(1, 2, 3, 4, 5, 6), visible(state, b))
        val final = finish(state, ::assertConserved)
        assertEquals(6, final.round)
        assertEquals(List(2) { 18 }, final.seating.map { player -> final.log.count { it.player == player } })
    }

    @Test fun singleOpenGridOpensTheNextOnceTheStarterIsDone() {
        var state = start(players = 3, settings = settings(grids = 2))
        val (first, second, third) = state.seating
        state = next(state)
        // The first player has no more turns in grid 1 and opens round 2, which the second player starts.
        assertEquals(2, GridEngine.privateView(state, first).round)
        assertEquals(listOf("grid-0"), queue(state, second))
        state = next(state)
        assertEquals(listOf("grid-1"), queue(state, second))
        assertEquals(listOf("grid-0"), queue(state, third))
        assertEquals(setOf(1, 2), visible(state, second))
        assertEquals(setOf(1), visible(state, third))
        assertEquals(9, state.grids.last().positions.filterNotNull().size)
        state = pick(state, state.grids[1])
        assertEquals(listOf(third), state.held.keys.toList())
        assertConserved(finish(state, ::assertConserved))
    }

    @Test fun rejectionReasonsAndAcceptedTransitionsNeverMutateInput() {
        val state = start(settings = settings(grids = 3, open = 3))
        val snapshot = Json.encodeToString(state)
        val grid = state.grids[0]
        val good = GridPick(assertNotNull(grid.currentPlayer), grid.id, row(0))
        fun rejected(request: GridPick, reason: GridPickRejection) {
            val result = GridEngine.pick(state, request)
            assertEquals(reason, result.reason)
            assertNull(result.state)
            assertEquals(snapshot, Json.encodeToString(state))
        }
        rejected(good.copy(player = "outsider"), GridPickRejection.NOT_YOUR_TURN)
        rejected(good.copy(player = state.seating[1]), GridPickRejection.NOT_YOUR_TURN)
        rejected(good.copy(gridId = "missing"), GridPickRejection.NOT_YOUR_TURN)
        rejected(good.copy(gridId = state.grids[2].id), GridPickRejection.NOT_HEAD_GRID)
        for (index in listOf(-1, 3, Int.MIN_VALUE, Int.MAX_VALUE)) {
            rejected(good.copy(line = row(index)), GridPickRejection.INVALID_LINE)
            rejected(good.copy(line = column(index)), GridPickRejection.INVALID_LINE)
        }
        pick(state)
        assertEquals(snapshot, Json.encodeToString(state))
        assertEquals(GridPickRejection.DRAFT_FINISHED, GridEngine.pick(finish(state), good).reason)
    }

    @Test fun emptyBoardGuardSkipsRemainingTurnsAndAdvancesRound() {
        val initial = start(players = 4)
        val grid = initial.grids.single()
        // Defensive fixture: just one nonempty line remains before the final player's turn.
        val removed = grid.positions.drop(3).filterNotNull() + grid.reserve
        val state = initial.copy(grids = listOf(grid.copy(positions = grid.positions.take(3) + List(6) { null }, selection = 1,
            reserve = emptyList())), queues = initial.seating.associateWith { if (it == initial.seating[1]) listOf(grid.id) else emptyList() },
            pools = initial.pools + (initial.seating[0] to removed),
            log = listOf(GridPickLog(initial.seating[0], grid.id, row(1), removed)))
        val final = pick(state)
        assertTrue(final.finished)
        assertEquals(listOf(initial.seating[1], initial.seating[2], initial.seating[3]), final.log.drop(1).map { it.player })
        assertTrue(final.log.takeLast(2).all { it.line == null && it.kept.isEmpty() })
        assertEquals(initial.supply, final.supply) // No refill after a later selection, even in the guard path.
        assertConserved(final)
    }

    // DRF-087
    @Test fun publicBoardsAndHistoryAreVisibleButUndealtCardsAndOtherPoolsStayPrivate() {
        var state = start(players = 3, settings = settings(grids = 4, open = 3), extra = 5)
        state = pick(state)
        val copies = state.copies.associateBy { it.id }
        val public = GridEngine.publicView(state)
        assertEquals(1, public.round)
        assertEquals(2, public.rounds)
        assertEquals(state.seating, public.seating)
        assertFalse(public.finished)
        for ((board, view) in state.grids.zip(public.grids)) {
            assertEquals(board.positions.map { it?.let(copies::getValue) }, view.positions)
            assertEquals(board.currentPlayer, view.currentPlayer)
        }
        val event = public.log.single()
        assertEquals(state.log.single().kept.map(copies::getValue), event.kept)
        assertEquals(row(0), event.line)
        val publicJson = Json.encodeToString(public)
        // Spare cards, refill cards and unopened grids stay hidden.
        assertEquals(3, public.grids.size)
        for (id in state.supply + state.grids.flatMap { it.reserve } + state.grids[3].positions.filterNotNull())
            assertFalse("\"$id\"" in publicJson)
        for (player in state.seating) {
            val view = GridEngine.privateView(state, player)
            assertEquals(state.pools.getValue(player).map(copies::getValue), view.pool)
            assertEquals(state.queues.getValue(player).firstOrNull(), view.headGridId)
            val privateJson = Json.encodeToString(view)
            for (id in state.supply + state.pools.filterKeys { it != player }.values.flatten()) {
                assertFalse("\"$id\"" in privateJson)
            }
        }
        assertFailsWith<IllegalArgumentException> { GridEngine.privateView(state, "outsider") }
        val final = finish(state)
        val finalView = GridEngine.publicView(final)
        assertEquals(final.log.size, finalView.log.size)
        assertEquals(final.pools.values.flatten().toSet(), finalView.log.flatMap { it.kept }.map { it.id }.toSet())
        assertEquals(final.log.flatMap { it.discarded }, finalView.log.flatMap { it.discarded }.map { it.id })
    }

    @Test fun seededDraftIsCanonicalAndIgnoresColorBalance() {
        val settings = settings(grids = 4, open = 3)
        val entries = listOf(DraftCubeEntry("a", 10, setOf(DraftColor.W, DraftColor.U)), DraftCubeEntry("a", 8,
            setOf(DraftColor.U, DraftColor.W)), DraftCubeEntry("b", 30, setOf(DraftColor.R)))
        val a = GridEngine.start(listOf("a", "b", "c"), entries, settings, seed)
        val b = GridEngine.start(listOf("c", "b", "a"), entries.reversed(), settings, seed)
        val combined = GridEngine.start(listOf("a", "b", "c"), listOf(
            DraftCubeEntry("a", 18, setOf(DraftColor.W, DraftColor.U)), DraftCubeEntry("b", 30, setOf(DraftColor.R))), settings, seed)
        assertEquals(a, b)
        assertEquals(a, combined)
        assertEquals(18, a.copies.count { it.definition == "a" })
        assertEquals(finish(a), finish(b))
        val unbalanced = GridEngine.start(listOf("a", "b", "c"), entries, settings.copy(colorBalance = false), seed)
        assertEquals(a.copy(settings = unbalanced.settings), unbalanced)
        assertNotEquals(a, GridEngine.start(listOf("a", "b", "c"), entries, settings,
            RandomV1.encodeSeed(ByteArray(32) { 9 })))
        val seatings = (0..15).map { byte -> GridEngine.start(listOf("a", "b", "c"), entries, settings,
            RandomV1.encodeSeed(ByteArray(32) { byte.toByte() })).seating }
        assertTrue(seatings.distinct().size > 1)
    }

    @Test fun serializationRoundTripResumesWithNoSeedOrCatalog() {
        for (players in 2..4) {
            var state = start(players, settings(grids = 5, open = 3, twoPicks = players == 2))
            repeat(5) { state = next(state) }
            val decoded = Json.decodeFromString<GridState>(Json.encodeToString(state))
            assertEquals(state, decoded)
            assertEquals(finish(state), finish(decoded))
            val final = finish(decoded)
            assertEquals(final, Json.decodeFromString<GridState>(Json.encodeToString(final)))
            val public = GridEngine.publicView(state)
            assertEquals(public, Json.decodeFromString<GridPublicView>(Json.encodeToString(public)))
            val privateView = GridEngine.privateView(state, state.seating.first())
            assertEquals(privateView, Json.decodeFromString<GridPrivateView>(Json.encodeToString(privateView)))
        }
        // Old Booster JSON remains readable with the new Grid defaults.
        assertEquals(DraftSettings.booster(), Json.decodeFromString<DraftSettings>("{}"))
    }

    @Test fun variedAsynchronousSchedulesConserveAllCopiesAndRespectHeads() {
        val scheduler = RandomV1.Words(seed)
        for (players in 2..4) {
            for (open in listOf(1, 3)) {
                for (twoPicks in if (players == 2) listOf(false, true) else listOf(false)) {
                    var state = start(players, settings(grids = 8, open = open, twoPicks = twoPicks), extra = 7)
                    var steps = 0
                    while (!state.finished) {
                        assertConserved(state)
                        assertTrue(steps++ < 100)
                        val heads = state.seating.mapNotNull { player ->
                            GridEngine.privateView(state, player).headGridId?.let { id -> state.grids.first { it.id == id } }
                        }
                        val grid = heads[RandomV1.sample(heads.size, scheduler::next)]
                        val lines = (0..2).flatMap { listOf(row(it), column(it)) }.filter { line ->
                            (0..2).any { offset -> grid.positions[if (line.type == GridLineType.ROW)
                                line.index * 3 + offset else offset * 3 + line.index] != null }
                        }
                        state = pick(state, grid, lines[RandomV1.sample(lines.size, scheduler::next)])
                    }
                    assertConserved(state)
                    assertEquals(7, state.supply.size)
                    assertEquals(8 * if (twoPicks) 4 else players, state.log.size)
                    assertTrue(state.log.all { it.line != null })
                }
            }
        }
    }

    /** First nonempty row, so every grid evolves the same way whatever order the members act in. */
    private fun play(initial: GridState, chooser: (List<String>) -> String): GridState {
        var state = initial
        var steps = 0
        while (!state.finished) {
            assertTrue(steps++ < 1000)
            val player = chooser(state.seating.filter { state.queues.getValue(it).isNotEmpty() })
            val grid = state.grids.first { it.id == state.queues.getValue(player).first() }
            val line = (0..2).first { row -> (0..2).any { grid.positions[row * 3 + it] != null } }
            state = pick(state, grid, row(line))
        }
        return state
    }

    // DRF-080
    @Test fun everyGridIsDealtUpFrontSoItsCardsNeverDependOnPickTiming() {
        for ((players, twoPicks) in listOf(3 to false, 4 to false, 2 to true, 2 to false)) {
            val initial = start(players, settings(grids = 7, open = 3, twoPicks = twoPicks), extra = 4)
            val perGrid = GridEngine.cardsPerGrid(initial.settings, players)
            assertEquals(7, initial.grids.size)
            assertTrue(initial.grids.all { it.positions.filterNotNull().size + it.reserve.size == perGrid })
            assertEquals(4, initial.supply.size)
            val first = play(initial) { it.first() }
            val last = play(initial) { it.last() }
            assertNotEquals(first.log, last.log, "The two schedules really act in different orders")
            for (grid in initial.grids) {
                val dealt = grid.positions.filterNotNull() + grid.reserve
                fun events(state: GridState) = state.log.filter { it.gridId == grid.id }
                assertEquals(events(first), events(last))
                assertEquals(dealt.toSet(), events(first).flatMap { it.kept + it.discarded }.toSet())
            }
            assertEquals(first.pools.mapValues { it.value.toSet() }, last.pools.mapValues { it.value.toSet() })
            assertEquals(initial.supply, first.supply)
            assertConserved(first)
        }
    }

    // DRF-081
    @Test fun publicSlotsKeepDealtPositionsWithPickersRefillsAndDiscards() {
        // Three players refill the first selection's line.
        var state = start(players = 3, settings = settings())
        val (a, b, c) = state.seating
        val dealt = state.grids.single().positions
        val reserve = state.grids.single().reserve
        state = pick(state, line = row(0))
        state = pick(state, line = column(1))
        var slots = GridEngine.publicView(state).grids.single().slots
        assertEquals(listOf(reserve[0], reserve[2]), listOf(slots[0].copy?.id, slots[2].copy?.id))
        assertEquals(listOf(true, true, true, false), listOf(slots[0].refill, slots[1].refill, slots[2].refill, slots[3].refill))
        assertEquals(reserve[1], slots[1].copy?.id)
        assertEquals(listOf(b, null, b, b, null), listOf(slots[1].pickedBy, slots[0].pickedBy, slots[4].pickedBy, slots[7].pickedBy, slots[3].pickedBy))
        assertEquals(dealt[4], slots[4].copy?.id)
        state = pick(state, line = row(2))
        assertTrue(state.finished)
        val view = GridEngine.publicView(state)
        assertTrue(view.grids.isEmpty())
        val finished = view.finishedGrids.single()
        assertTrue(finished.finished)
        slots = finished.slots
        // Discarded leftovers are the unpicked slots, at their positions.
        assertEquals(listOf(0, 2, 3, 5), slots.indices.filter { slots[it].pickedBy == null })
        assertEquals(listOf(reserve[0], reserve[2]), listOf(0, 2).map { slots[it].copy?.id })
        assertEquals(listOf(c, c), listOf(slots[6].pickedBy, slots[8].pickedBy))
        assertEquals(listOf(dealt[3], dealt[5]), listOf(3, 5).map { slots[it].copy?.id })
        assertEquals(listOf(null, null), listOf(3, 5).map { slots[it].pickedBy })
        assertEquals(setOf(dealt[3], dealt[5]) + setOf(reserve[0], reserve[2]), state.log.last().discarded.toSet())
        assertTrue(slots.none { it.pickedBy == a }) // A's original row was replaced by the refill
        assertEquals(1, finished.round)
    }

    /** As stored before per-member opening: only the current round's grids dealt, the rest still in the supply. */
    private fun oldSnapshot(state: GridState, refillsDrawnLater: Boolean): GridState {
        val dealt = state.grids.filter { state.roundOf(it) <= state.round }
        val later = state.grids.drop(dealt.size).flatMap { it.positions.filterNotNull() + it.reserve }
        val reserves = if (refillsDrawnLater) dealt.flatMap { it.reserve } else emptyList()
        val old = state.copy(supply = state.supply + later + reserves,
            grids = if (refillsDrawnLater) dealt.map { it.copy(reserve = emptyList()) } else dealt,
            queues = emptyMap(), held = emptyMap(), opened = emptyMap())
        // The old JSON has none of the scheduling keys.
        val encoded = Json.encodeToString(old)
        for (key in listOf("queues", "held", "opened")) assertFalse("\"$key\"" in encoded)
        return Json.decodeFromString<GridState>(encoded)
    }

    @Test fun oldSnapshotsDealTheirRemainingGridsAndContinueWithEarlyOpening() {
        var state = start(settings = settings(grids = 6, open = 3))
        val a = state.seating[0]
        val b = state.seating[1]
        state = pick(state, state.grids[0])
        state = pick(state, state.grids[2])
        state = pick(state, state.grids[1])
        val old = oldSnapshot(state, refillsDrawnLater = false)
        assertEquals(3, old.grids.size)
        val resumed = GridEngine.resume(old)
        assertEquals(6, resumed.grids.size)
        assertTrue(resumed.supply.isEmpty())
        // The old head rule: each member's turns in the open grids, oldest first.
        assertEquals(listOf("grid-1"), resumed.queues[a])
        assertEquals(listOf("grid-0", "grid-2"), resumed.queues[b])
        assertEquals(listOf("grid-1"), GridEngine.privateView(old, a).queue)
        assertEquals(GridPickRejection.NOT_HEAD_GRID, GridEngine.pick(old, GridPick(b, "grid-2", row(1))).reason)
        assertConserved(resumed)
        var next = assertNotNull(GridEngine.pick(old, GridPick(a, "grid-1", row(1))).state)
        assertEquals(listOf("grid-4"), next.queues[a]) // A opens round 2 early
        next = finish(next, ::assertConserved)
        assertTrue(next.finished)

        // Three players: an old grid still waiting for its first selection gets its refill cards from the supply.
        val three = start(players = 3, settings = settings(grids = 4, open = 2))
        val legacy = oldSnapshot(three, refillsDrawnLater = true)
        assertTrue(legacy.grids.all { it.reserve.isEmpty() })
        val continued = GridEngine.resume(legacy)
        assertEquals(4, continued.grids.size)
        assertTrue(continued.grids.all { it.reserve.size == 3 })
        // Grids dealt for an old pod keep its plain rotation; a new pod's fourth grid would go A, C, B.
        assertEquals(List(3) { three.seating[(3 + it) % 3] }, continued.grids[3].turns)
        assertTrue(continued.supply.isEmpty())
        assertConserved(finish(continued, ::assertConserved))
        // Finished old snapshots need nothing.
        val done = finish(three)
        val oldDone = done.copy(queues = emptyMap(), held = emptyMap(), opened = emptyMap())
        assertEquals(oldDone, GridEngine.resume(oldDone))
        assertEquals(4, GridEngine.publicView(oldDone).finishedGrids.size)
    }
}

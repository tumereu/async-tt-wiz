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

    private fun pick(state: GridState, grid: GridBoard = state.grids.first { !it.finished }, line: GridLine = row(0)): GridState {
        val result = GridEngine.pick(state, GridPick(assertNotNull(grid.currentPlayer), grid.id, line))
        assertNull(result.reason)
        return assertNotNull(result.state)
    }

    private fun next(state: GridState): GridState {
        val grid = state.grids.first { !it.finished }
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
        val accounted = state.supply + state.grids.flatMap { it.positions.filterNotNull() } +
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
        assertEquals(cards.take(9).map { it.id }, initial.grids[0].positions)
        assertEquals(cards.slice(9..17).map { it.id }, initial.grids[1].positions)
        val after = pick(initial)
        assertEquals(cards.slice(18..20).map { it.id }, after.grids[0].positions.take(3))
        assertConserved(finish(after))
        assertEquals(initial, GridEngine.startWithCards(listOf("b", "a"), cards, settings, seed))
        assertFailsWith<IllegalArgumentException> {
            GridEngine.startWithCards(listOf("a", "b"), cards.dropLast(1), settings, seed)
        }
        assertFailsWith<IllegalArgumentException> {
            GridEngine.startWithCards(listOf("a", "b"), cards + cards.first(), settings, seed)
        }
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

    @Test fun dealingAndRefillHappenOnlyAfterFirstSelection() {
        for (players in 2..4) {
            for (twoPicks in if (players == 2) listOf(false, true) else listOf(false)) {
                var state = start(players, settings(twoPicks = twoPicks))
                val initial = state.grids.single()
                assertEquals(9, initial.positions.filterNotNull().size)
                assertEquals(9, initial.positions.distinct().size)
                val refills = players >= 3 || twoPicks
                assertEquals(if (refills) 3 else 0, state.supply.size)
                state = pick(state, line = row(1))
                val grid = state.grids.single()
                assertEquals(initial.positions.slice(3..5), state.log.single().kept)
                assertEquals(if (refills) 9 else 6, grid.positions.filterNotNull().size)
                assertEquals(initial.positions.take(3), grid.positions.take(3))
                assertEquals(initial.positions.drop(6), grid.positions.drop(6))
                if (refills) {
                    assertTrue(grid.positions.slice(3..5).all { it in state.copies.map { copy -> copy.id } })
                    assertTrue(grid.positions.slice(3..5).none { it in initial.positions })
                } else assertEquals(listOf(null, null, null), grid.positions.slice(3..5))
                assertTrue(state.supply.isEmpty())
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

    @Test fun normalTurnsFollowSeatingAndStarterRotatesAcrossRounds() {
        for (players in 2..4) {
            for (open in listOf(1, 3)) {
                val initial = start(players, settings(grids = 7, open = open))
                val final = finish(initial)
                for ((index, grid) in final.grids.withIndex()) {
                    val expected = List(players) { initial.seating[(index + it) % players] }
                    assertEquals(expected, grid.turns)
                    assertEquals(expected, final.log.filter { it.gridId == grid.id }.map { it.player })
                }
                assertEquals(List(players) { 7 }, final.seating.map { player -> final.log.count { it.player == player } })
            }
        }
    }

    @Test fun twoPicksUsesAbabThenBabaWithoutRefillingAgain() {
        var state = start(settings = settings(grids = 2, twoPicks = true))
        val a = state.seating[0]
        val b = state.seating[1]
        assertEquals(listOf(a, b, a, b), state.grids.single().turns)
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

    @Test fun oldestReadyGridIsHeadAndRoundWaitsForEveryOpenGrid() {
        var state = start(settings = settings(grids = 5, open = 3))
        val a = state.seating[0]
        val b = state.seating[1]
        val ids = state.grids.map { it.id }
        assertEquals(3, ids.size)
        assertEquals(ids[0], GridEngine.privateView(state, a).headGridId)
        assertEquals(ids[1], GridEngine.privateView(state, b).headGridId)
        assertEquals(GridPickRejection.NOT_HEAD_GRID, GridEngine.pick(state, GridPick(a, ids[2], row(0))).reason)
        state = pick(state, state.grids[0])
        assertEquals(ids[2], GridEngine.privateView(state, a).headGridId)
        // B must now take the older grid, not the grid on which B started.
        assertEquals(ids[0], GridEngine.privateView(state, b).headGridId)
        assertEquals(GridPickRejection.NOT_HEAD_GRID, GridEngine.pick(state, GridPick(b, ids[1], row(0))).reason)
        state = pick(state, state.grids[0], row(1))
        // A can independently advance grid 2 while grid 1 waits for B.
        state = pick(state, state.grids[2])
        state = pick(state, state.grids[1])
        state = pick(state, state.grids[1], row(1))
        assertEquals(0, state.round)
        assertEquals(3, state.grids.size)
        assertNull(GridEngine.privateView(state, a).headGridId)
        assertEquals(ids[2], GridEngine.privateView(state, b).headGridId)
        state = pick(state, state.grids[2], row(1))
        assertEquals(1, state.round)
        assertEquals(5, state.grids.size)
        assertEquals(listOf(4, 5), GridEngine.publicView(state).grids.map { it.number })
        assertEquals(b, state.grids[3].currentPlayer)
        assertEquals(a, state.grids[4].currentPlayer)
        val final = finish(state, ::assertConserved)
        assertTrue(final.finished)
        assertEquals(2, final.round)
        assertTrue(GridEngine.publicView(final).grids.isEmpty())
        assertTrue(final.seating.all { GridEngine.privateView(final, it).headGridId == null })
    }

    @Test fun singleOpenGridNeverDealsNextUntilLastSelection() {
        var state = start(players = 3, settings = settings(grids = 2))
        repeat(2) {
            state = next(state)
            assertEquals(0, state.round)
            assertEquals(1, state.grids.size)
        }
        state = next(state)
        assertEquals(1, state.round)
        assertEquals(2, state.grids.size)
        assertEquals(9, state.grids.last().positions.filterNotNull().size)
        assertEquals(state.seating[1], state.grids.last().currentPlayer)
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
        val removed = grid.positions.drop(3).filterNotNull()
        val state = initial.copy(grids = listOf(grid.copy(positions = grid.positions.take(3) + List(6) { null }, selection = 1)),
            pools = initial.pools + (initial.seating[0] to removed),
            log = listOf(GridPickLog(initial.seating[0], grid.id, row(1), removed)))
        val final = pick(state)
        assertTrue(final.finished)
        assertEquals(listOf(initial.seating[1], initial.seating[2], initial.seating[3]), final.log.drop(1).map { it.player })
        assertTrue(final.log.takeLast(2).all { it.line == null && it.kept.isEmpty() })
        assertEquals(initial.supply, final.supply) // No refill after a later selection, even in the guard path.
        assertConserved(final)
    }

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
        for (id in state.supply) assertFalse("\"$id\"" in publicJson)
        for (player in state.seating) {
            val view = GridEngine.privateView(state, player)
            assertEquals(state.pools.getValue(player).map(copies::getValue), view.pool)
            assertEquals(state.grids.firstOrNull { it.currentPlayer == player }?.id, view.headGridId)
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
}

package org.asyncttwiz.protocol

import kotlin.random.Random
import kotlin.test.*

/** Grid: a member's count is the selections they can make in a row if nobody else acts, next-round grids included. */
class GridConsecutivePicksTest {
    private val seed = RandomV1.encodeSeed(ByteArray(32) { (it * 5).toByte() })
    private fun start(players: Int, grids: Int, open: Int = 1, twoPicks: Boolean = false): GridState {
        val settings = DraftSettings.grid().copy(grids = grids, gridsOpenAtOnce = open, twoPicks = twoPicks)
        val count = settings.supply(players, emptyList()).needed.toInt()
        return GridEngine.start(List(players) { "p$it" }, List(count) { DraftCubeEntry("definition-$it", 1) }, settings, seed)
    }
    private val lines = GridLineType.entries.flatMap { type -> List(3) { GridLine(type, it) } }
    private fun positions(line: GridLine) = if (line.type == GridLineType.ROW) List(3) { line.index * 3 + it } else List(3) { it * 3 + line.index }
    private fun queue(state: GridState, seat: Int) = state.queues.getValue(state.seating[seat])
    private fun count(state: GridState, seat: Int) = GridEngine.consecutivePicks(state, state.seating[seat])

    private fun pick(state: GridState, seat: Int, random: Random? = null): GridState {
        val id = queue(state, seat).first()
        val board = state.grids.first { it.id == id }.positions
        val open = lines.filter { line -> positions(line).any { board[it] != null } }
        val result = GridEngine.pick(state, GridPick(state.seating[seat], id, random?.let { open.random(it) } ?: open.first()))
        assertNull(result.reason)
        return assertNotNull(result.state)
    }

    private fun reference(initial: GridState, seat: Int): Int {
        var state = initial
        var picks = 0
        while (!state.finished && queue(state, seat).isNotEmpty()) { state = pick(state, seat); picks++ }
        return picks
    }

    // DRF-074
    @Test fun queuedGridsPlusOwnNextRoundGrids() {
        // Two players, two grids a round: grid 1 goes p0 then p1, grid 2 goes p1 then p0.
        var state = start(players = 2, grids = 4, open = 2)
        assertEquals(1, count(state, 0), "The other member's unpicked grid still comes to this member")
        state = pick(state, 1)
        assertEquals(listOf("grid-0", "grid-1"), queue(state, 0))
        assertEquals(3, count(state, 0))
        assertEquals(reference(state, 0), count(state, 0))
        assertEquals(2, count(pick(state, 0), 0))
    }

    @Test fun gridsReturningThroughOtherMembersDoNotCount() {
        val state = start(players = 2, grids = 2, twoPicks = true)
        assertEquals(1, queue(state, 0).size)
        assertEquals(1, count(state, 0))
        assertEquals(0, count(pick(state, 0), 0))
        assertEquals(0, GridEngine.consecutivePicks(state, "stranger"))
    }

    // DRF-074
    @Test fun countMatchesRealPicksIsPositiveExactlyWithGridsAndDropsByOnePerOwnSelection() {
        val configs = listOf(start(2, 6), start(2, 6, open = 3), start(2, 4, open = 2, twoPicks = true), start(3, 6, open = 2),
            start(4, 8, open = 4))
        for ((index, initial) in configs.withIndex()) {
            val random = Random(index)
            var state = initial
            var steps = 0
            while (!state.finished) {
                assertTrue(steps++ < 1000, "Draft must make progress")
                val counts = state.seating.indices.map { count(state, it) }
                for (seat in state.seating.indices) {
                    assertEquals(queue(state, seat).isNotEmpty(), counts[seat] > 0, "Config $index step $steps seat $seat")
                    assertEquals(reference(state, seat), counts[seat], "Config $index step $steps seat $seat")
                }
                val seat = state.seating.indices.filter { queue(state, it).isNotEmpty() }.random(random)
                // Any line: the count never depends on which line is taken.
                state = pick(state, seat, random)
                assertEquals(counts[seat] - 1, count(state, seat), "Config $index step $steps: an own selection lowers the count by one")
            }
            assertTrue(state.seating.indices.all { count(state, it) == 0 })
        }
    }

    @Test fun largeGridDraftCountsStayCheap() {
        var state = start(players = 4, grids = 60, open = 15)
        val random = Random(1)
        repeat(60) {
            val seats = state.seating.indices.filter { queue(state, it).isNotEmpty() }
            if (seats.isNotEmpty()) state = pick(state, seats.random(random))
        }
        repeat(3) { state.seating.indices.forEach { count(state, it) } }
        val started = kotlin.time.TimeSource.Monotonic.markNow()
        state.seating.indices.forEach { count(state, it) }
        val elapsed = started.elapsedNow().inWholeMilliseconds
        assertTrue(elapsed < 250, "Counting all four members took $elapsed ms")
    }
}

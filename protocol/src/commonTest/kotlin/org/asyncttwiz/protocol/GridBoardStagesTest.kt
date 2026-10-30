package org.asyncttwiz.protocol

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.*

/** Boards a grid showed before and after its refill, rebuilt from what a member's public view holds. */
class GridBoardStagesTest {
    private val seed = RandomV1.encodeSeed(ByteArray(32) { it.toByte() })
    private fun row(index: Int) = GridLine(GridLineType.ROW, index)
    private fun column(index: Int) = GridLine(GridLineType.COLUMN, index)

    private fun start(players: Int, twoPicks: Boolean = false): GridState {
        val settings = DraftSettings.grid().copy(grids = 1, gridsOpenAtOnce = 1, twoPicks = twoPicks)
        val count = settings.supply(players, emptyList()).needed.toInt()
        return GridEngine.start(List(players) { "player-$it" }, List(count) { DraftCubeEntry("definition-$it", 1) }, settings, seed)
    }

    private fun pick(state: GridState, line: GridLine): GridState {
        val grid = state.grids.single()
        return assertNotNull(GridEngine.pick(state, GridPick(assertNotNull(grid.currentPlayer), grid.id, line)).state)
    }

    /** The stages of the only grid as [viewer] receives it. */
    private fun stages(state: GridState, viewer: String = state.seating[0]): List<GridBoardStage> {
        val view = GridEngine.publicView(state, viewer)
        val board = (view.grids + view.finishedGrids).single()
        return gridBoardStages(board, view.log)
    }

    private fun ids(stage: GridBoardStage) = stage.slots.map { it.copy?.id }

    @Test fun twoPlayersWithoutRefillShowOneBoardWithEveryPick() {
        var state = start(2)
        val dealt = state.grids.single().positions
        val (a, b) = state.seating
        state = pick(state, row(0))
        state = pick(state, column(1))
        assertTrue(state.finished)
        val stage = stages(state).single()
        assertEquals(dealt, ids(stage)) // cards stay in place, discards included
        assertEquals(listOf(a to row(0), b to column(1)), stage.picks.map { it.player to it.line })
        assertEquals(listOf(a, a, a, null, b, null, null, b, null), stage.slots.map { it.pickedBy })
    }

    @Test fun threePlayersShowTheBoardBeforeTheRefillAndTheBoardAfterIt() {
        var state = start(3)
        val (a, b, c) = state.seating
        val dealt = state.grids.single().positions
        val reserve = state.grids.single().reserve
        // Before any selection: one board, and the refill cards are nowhere in the view.
        assertEquals(listOf(dealt), stages(state).map(::ids))
        assertTrue(reserve.none { "\"$it\"" in Json.encodeToString(GridEngine.publicView(state, a)) })

        state = pick(state, row(0))
        val live = stages(state, viewer = b)
        assertEquals(2, live.size)
        assertEquals(dealt, ids(live[0]))
        assertEquals(listOf(a to row(0)), live[0].picks.map { it.player to it.line })
        assertEquals(listOf(a, a, a) + List(6) { null }, live[0].slots.map { it.pickedBy })
        assertTrue(live[0].slots.none { it.refill })
        // The live board after the refill: the refill cards in row 0, nothing picked yet.
        assertEquals(state.grids.single().positions, ids(live[1]))
        assertEquals(reserve, ids(live[1]).take(3))
        assertEquals(listOf(true, true, true) + List(6) { false }, live[1].slots.map { it.refill })
        assertTrue(live[1].picks.isEmpty())

        state = pick(state, column(1))
        state = pick(state, row(2))
        assertTrue(state.finished)
        val done = stages(state, viewer = c)
        assertEquals(2, done.size)
        assertEquals(dealt, ids(done[0]))
        assertEquals(listOf(a), done[0].picks.map { it.player })
        assertEquals(reserve + dealt.drop(3), ids(done[1]))
        assertEquals(listOf(b to column(1), c to row(2)), done[1].picks.map { it.player to it.line })
        assertEquals(listOf(null, b, null, null, b, null, c, b, c), done[1].slots.map { it.pickedBy })
    }

    @Test fun twoPicksRefillOnceAndKeepTheLaterSelectionsOnTheRefilledBoard() {
        var state = start(2, twoPicks = true)
        val (a, b) = state.seating
        val dealt = state.grids.single().positions
        val reserve = state.grids.single().reserve
        for (line in listOf(column(2), column(2), row(0), row(1))) state = pick(state, line)
        assertTrue(state.finished)
        val (before, after) = stages(state, viewer = b).also { assertEquals(2, it.size) }
        assertEquals(dealt, ids(before))
        assertEquals(listOf(null, null, a, null, null, a, null, null, a), before.slots.map { it.pickedBy })
        assertEquals(listOf(a to column(2)), before.picks.map { it.player to it.line })
        assertEquals(listOf(b to column(2), a to row(0), b to row(1)), after.picks.map { it.player to it.line })
        assertEquals(reserve, listOf(2, 5, 8).map { ids(after)[it] })
        assertEquals(listOf(a, a, b, b, b, b, null, null, b), after.slots.map { it.pickedBy })
    }

    @Test fun boardsWithoutSlotsFromOlderServersStayOneBoard() {
        val board = GridPublicBoard("grid-0", 1, List(9) { null }, null)
        val log = listOf(GridPublicPickLog("a", "grid-0", row(0), emptyList(), emptyList()),
            GridPublicPickLog("b", "other", row(1), emptyList(), emptyList()))
        assertEquals(listOf(GridBoardStage(emptyList(), log.take(1))), gridBoardStages(board, log))
    }
}

package org.asyncttwiz.protocol

import kotlinx.serialization.Serializable

/** Server-only snapshot, including undealt supply. Send only the projections to members. */
@Serializable
data class GridState(
    val settings: DraftSettings,
    val seating: List<String>,
    val copies: List<DraftCopy>,
    val supply: List<String>,
    /** All dealt grids, in creation order; finished boards have nine empty positions. */
    val grids: List<GridBoard>,
    /** Zero-based; equals settings.rounds after completion. */
    val round: Int,
    val pools: Map<String, List<String>>,
    val log: List<GridPickLog> = emptyList(),
) {
    val finished: Boolean get() = round == settings.rounds
}

@Serializable data class GridBoard(
    val id: String,
    /** One-based grid number across the whole draft. */
    val number: Int,
    val turns: List<String>,
    /** Row-major order, always nine positions; null denotes a gap. */
    val positions: List<String?>,
    val selection: Int = 0,
) {
    val finished: Boolean get() = selection == turns.size
    val currentPlayer: String? get() = turns.getOrNull(selection)
}

@Serializable enum class GridLineType { ROW, COLUMN }
@Serializable data class GridLine(val type: GridLineType, val index: Int)
@Serializable data class GridPick(val player: String, val gridId: String, val line: GridLine)
@Serializable data class GridPickLog(
    val player: String,
    val gridId: String,
    /** Null only for an automatic skip when no nonempty line remains. */
    val line: GridLine?,
    val kept: List<String>,
    val discarded: List<String> = emptyList(),
)
@Serializable enum class GridPickRejection { NOT_YOUR_TURN, NOT_HEAD_GRID, EMPTY_LINE, INVALID_LINE, DRAFT_FINISHED }
data class GridPickResult(val state: GridState? = null, val reason: GridPickRejection? = null)

@Serializable data class GridPublicBoard(
    val id: String,
    val number: Int,
    val positions: List<DraftCopy?>,
    val currentPlayer: String?,
)
@Serializable data class GridPublicPickLog(
    val player: String,
    val gridId: String,
    val line: GridLine?,
    val kept: List<DraftCopy>,
    val discarded: List<DraftCopy>,
)
@Serializable data class GridPublicView(
    val round: Int,
    val rounds: Int,
    val finished: Boolean,
    val seating: List<String>,
    val grids: List<GridPublicBoard>,
    val log: List<GridPublicPickLog>,
)
@Serializable data class GridPrivateView(val player: String, val pool: List<DraftCopy>, val headGridId: String?)

object GridEngine {
    /** Canonical inputs and RandomV1 make seating and the entire draw order reproducible. */
    fun start(players: List<String>, cube: List<DraftCubeEntry>, settings: DraftSettings, seed: String): GridState {
        require(settings.mode == DraftMode.GRID && settings.validate(players.size).isEmpty()) { "Invalid grid settings" }
        require(players.all { it.isNotBlank() } && players.distinct().size == players.size) { "Invalid members" }
        require(cube.all { it.definition.isNotBlank() && it.quantity > 0 }) { "Invalid cube entries" }
        require(settings.supply(players.size, cube).sufficient) { "Insufficient cube supply" }
        val words = RandomV1.Words(seed)
        fun <T> shuffled(values: List<T>): List<T> {
            val result = values.toMutableList()
            for (i in result.lastIndex downTo 1) {
                val j = RandomV1.sample(i + 1, words::next)
                val value = result[i]; result[i] = result[j]; result[j] = value
            }
            return result
        }
        val seating = shuffled(players.sorted())
        val copies = cube.groupBy { it.definition }.entries.sortedBy { it.key }.flatMap { (definition, entries) ->
            require(entries.all { it.colors == entries.first().colors }) { "Conflicting cube colors" }
            val quantity = entries.sumOf { it.quantity.toLong() }
            require(quantity <= Int.MAX_VALUE) { "Too many copies of one definition" }
            List(quantity.toInt()) { index ->
                DraftCopy("$definition:copy:$index", definition, entries.first().colors.sortedBy { it.ordinal }.toSet())
            }
        }
        return Working(GridState(settings, seating, copies, shuffled(copies).map { it.id }, emptyList(), -1,
            seating.associateWith { emptyList() })).settle()
    }

    fun pick(state: GridState, pick: GridPick): GridPickResult {
        fun reject(reason: GridPickRejection) = GridPickResult(reason = reason)
        if (state.finished) return reject(GridPickRejection.DRAFT_FINISHED)
        val grid = state.grids.firstOrNull { it.id == pick.gridId }
        if (grid == null || grid.currentPlayer != pick.player) return reject(GridPickRejection.NOT_YOUR_TURN)
        if (head(state, pick.player)?.id != grid.id) return reject(GridPickRejection.NOT_HEAD_GRID)
        if (pick.line.index !in 0..2) return reject(GridPickRejection.INVALID_LINE)
        val positions = linePositions(pick.line)
        if (positions.all { grid.positions[it] == null }) return reject(GridPickRejection.EMPTY_LINE)
        return GridPickResult(state = Working(state).applyPick(pick, positions))
    }

    fun privateView(state: GridState, player: String): GridPrivateView {
        require(player in state.seating) { "Not a draft member" }
        val copies = state.copies.associateBy { it.id }
        return GridPrivateView(player, state.pools.getValue(player).map(copies::getValue), head(state, player)?.id)
    }

    fun publicView(state: GridState): GridPublicView {
        val copies = state.copies.associateBy { it.id }
        return GridPublicView(if (state.finished) state.settings.rounds else state.round + 1,
            state.settings.rounds, state.finished, state.seating,
            state.grids.filterNot { it.finished }.map { grid ->
                GridPublicBoard(grid.id, grid.number, grid.positions.map { it?.let(copies::getValue) }, grid.currentPlayer)
            }, state.log.map { event -> GridPublicPickLog(event.player, event.gridId, event.line,
                event.kept.map(copies::getValue), event.discarded.map(copies::getValue)) })
    }

    private fun head(state: GridState, player: String) = state.grids.firstOrNull { it.currentPlayer == player }

    private fun linePositions(line: GridLine): List<Int> = when (line.type) {
        GridLineType.ROW -> List(3) { line.index * 3 + it }
        GridLineType.COLUMN -> List(3) { it * 3 + line.index }
    }

    /** Only transition-local collections are mutated. Supply is consumed once, including refills. */
    private class Working(private val source: GridState) {
        private val grids = source.grids.toMutableList()
        private val supply = source.supply.toMutableList()
        private val pools = source.pools.mapValues { it.value.toMutableList() }
        private val log = source.log.toMutableList()
        private var round = source.round

        private fun draw() = supply.removeAt(supply.lastIndex)

        fun applyPick(pick: GridPick, positions: List<Int>): GridState {
            val index = grids.indexOfFirst { it.id == pick.gridId }
            advance(index, pick.line, positions)
            return settle()
        }

        private fun advance(index: Int, line: GridLine?, selected: List<Int>) {
            val grid = grids[index]
            val positions = grid.positions.toMutableList()
            val kept = selected.mapNotNull { positions[it] }
            selected.forEach { positions[it] = null }
            val selection = grid.selection + 1
            val finished = selection == grid.turns.size
            val discarded = if (finished) positions.filterNotNull() else emptyList()
            if (finished) positions.fill(null)
            else if (selection == 1 && line != null && source.settings.gridRefills(source.seating.size)) {
                for (position in positions.indices) if (positions[position] == null) positions[position] = draw()
            }
            val player = grid.turns[grid.selection]
            pools.getValue(player).addAll(kept)
            log.add(GridPickLog(player, grid.id, line, kept, discarded))
            grids[index] = grid.copy(positions = positions.toList(), selection = selection)
        }

        fun settle(): GridState {
            while (true) {
                // An empty board has no valid line: consume its remaining turns without asking for input.
                for (index in grids.indices) {
                    while (!grids[index].finished && grids[index].positions.all { it == null }) {
                        advance(index, null, emptyList())
                    }
                }
                if (grids.any { !it.finished } || round == source.settings.rounds) break
                round++
                if (round == source.settings.rounds) break
                val count = minOf(source.settings.gridsOpenAtOnce, source.settings.grids - grids.size)
                repeat(count) {
                    val number = grids.size
                    val turns = List(if (source.settings.twoPicks) 4 else source.seating.size) { turn ->
                        source.seating[(number + turn) % source.seating.size]
                    }
                    grids.add(GridBoard("grid-$number", number + 1, turns, List(9) { draw() }))
                }
            }
            return source.copy(supply = supply.toList(), grids = grids.toList(), round = round,
                pools = pools.mapValues { it.value.toList() }, log = log.toList())
        }
    }
}

package org.asyncttwiz.protocol

import kotlinx.serialization.Serializable

/**
 * Server-only snapshot, including unopened grids and refill cards. Send only the projections to members.
 *
 * Every grid is dealt at the start with its nine cards and, when grids refill, its own refill cards ([GridBoard.reserve]),
 * so no grid's cards depend on when anyone picks. Scheduling is the shared [DraftSchedule]: [queues], [held] and
 * [opened] work exactly as in [DraftState]. Snapshots from before per-member opening (no [opened]) dealt grids one round
 * at a time from [supply]; every engine call first deals their remaining grids (see [GridEngine.resume]).
 */
@Serializable
data class GridState(
    val settings: DraftSettings,
    val seating: List<String>,
    val copies: List<DraftCopy>,
    /** Undealt cards; empty unless the source had spare cards (old snapshots: the cards of grids not dealt yet). */
    val supply: List<String>,
    /** All grids, in creation order; finished boards have nine empty positions. */
    val grids: List<GridBoard>,
    /** Zero-based lowest round a member is still in (minimum of [openedRound]); equals settings.rounds after completion. */
    val round: Int,
    val pools: Map<String, List<String>>,
    val log: List<GridPickLog> = emptyList(),
    /** Old snapshots only: each grid drew its refill cards when it was dealt (fresh boosters). New grids always do. */
    val refillsDealt: Boolean = false,
    /** Grids each member can pick from, head first. */
    val queues: Map<String, List<String>> = emptyMap(),
    /** Grids passed to a member ahead of their [openedRound]; unseen and unpickable until they open that round. */
    val held: Map<String, List<String>> = emptyMap(),
    /** Highest round each member has opened; absent in snapshots from before per-member opening. */
    val opened: Map<String, Int> = emptyMap(),
) {
    val finished: Boolean get() = round == settings.rounds
    fun openedRound(player: String): Int = opened[player] ?: round
    /** Zero-based round of a grid: grids open [DraftSettings.gridsOpenAtOnce] per round, in number order. */
    fun roundOf(grid: GridBoard): Int = (grid.number - 1) / settings.gridsOpenAtOnce
}

@Serializable data class GridBoard(
    val id: String,
    /** One-based grid number across the whole draft. */
    val number: Int,
    val turns: List<String>,
    /** Row-major order, always nine positions; null denotes a gap. */
    val positions: List<String?>,
    val selection: Int = 0,
    /** The grid's own refill cards, dealt with the board; they fill the first selection's line in position order. */
    val reserve: List<String> = emptyList(),
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
    /** The live board; all empty once finished. */
    val positions: List<DraftCopy?>,
    val currentPlayer: String?,
    /** One-based round of the grid; 0 from older servers. */
    val round: Int = 0,
    val finished: Boolean = false,
    /**
     * Nine positions as dealt, cards staying in place: the latest card placed at each position (a refill card replaces
     * the first selection's card), who picked it, and whether it is a refill. Empty from older servers.
     */
    val slots: List<GridPublicSlot> = emptyList(),
)
/** A finished grid's unpicked slot cards were discarded. */
@Serializable data class GridPublicSlot(val copy: DraftCopy?, val pickedBy: String? = null, val refill: Boolean = false)
@Serializable data class GridPublicPickLog(
    val player: String,
    val gridId: String,
    val line: GridLine?,
    val kept: List<DraftCopy>,
    val discarded: List<DraftCopy>,
)
/** Grids and log entries of grids the viewer has seen: their round is open for both the viewer and the grid's opener. */
@Serializable data class GridPublicView(
    /** One-based lowest round a member is still in. */
    val round: Int,
    val rounds: Int,
    val finished: Boolean,
    val seating: List<String>,
    /** Open grids, in number order. */
    val grids: List<GridPublicBoard>,
    val log: List<GridPublicPickLog>,
    /** Finished grids, most recently finished first. */
    val finishedGrids: List<GridPublicBoard> = emptyList(),
)
@Serializable data class GridPrivateView(
    val player: String,
    val pool: List<DraftCopy>,
    val headGridId: String?,
    /** Grids the caller can pick from, head first. */
    val queue: List<String> = emptyList(),
    /** The caller's own one-based round (members open rounds early) and the round count; 0 from older servers. */
    val round: Int = 0,
    val rounds: Int = 0,
)

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
        return initial(settings, seating, copies, shuffled(copies).map { it.id })
    }

    /** Cards per grid: nine, plus three refill cards when grids refill. */
    fun cardsPerGrid(settings: DraftSettings, players: Int) = 9 + if (settings.gridRefills(players)) 3 else 0

    /**
     * Fresh boosters: one generated booster per grid, in grid order. Each grid takes [cardsPerGrid] random cards from its
     * own booster (the nine dealt first, then its refill cards); a booster smaller than that is topped up from spare
     * boosters generated after the grid boosters. Deterministic from the seed.
     */
    fun freshBoosterCards(source: SetSourceSnapshot, settings: DraftSettings, players: Int, seed: String): List<DraftCopy> {
        val perGrid = cardsPerGrid(settings, players)
        val grids = settings.grids
        var boosters = SetBoosters.generate(source, grids, seed).map { it.cards }
        val short = boosters.sumOf { (perGrid - it.size).coerceAtLeast(0) }
        if (short > 0) {
            // The same seed generates the same leading boosters, so spares only append.
            val smallest = boosters.minOf { it.size }.coerceAtLeast(1)
            boosters = SetBoosters.generate(source, grids + (short + smallest - 1) / smallest, seed).map { it.cards }
        }
        val words = RandomV1.Words(seed, "grid-booster-v1")
        fun shuffled(values: List<DraftCopy>): MutableList<DraftCopy> {
            val result = values.toMutableList()
            for (i in result.lastIndex downTo 1) {
                val j = RandomV1.sample(i + 1, words::next)
                val value = result[i]; result[i] = result[j]; result[j] = value
            }
            return result
        }
        val spares = shuffled(boosters.drop(grids).flatten())
        return boosters.take(grids).flatMap { booster ->
            val taken = shuffled(booster).take(perGrid)
            taken + List(perGrid - taken.size) { spares.removeAt(spares.lastIndex) }
        }
    }

    /**
     * Pre-generated set cards in draw order, including enough cards for every refill. Grids are dealt grid by grid:
     * each takes its nine cards, then its refill cards (see [freshBoosterCards]).
     */
    fun startWithCards(players: List<String>, cards: List<DraftCopy>, settings: DraftSettings, seed: String): GridState {
        require(settings.mode == DraftMode.GRID && settings.validate(players.size).isEmpty()) { "Invalid grid settings" }
        require(players.all { it.isNotBlank() } && players.distinct().size == players.size) { "Invalid members" }
        require(cards.size.toLong() >= settings.supply(players.size, emptyList()).needed) { "Insufficient grid cards" }
        require(cards.all { it.id.isNotBlank() && it.definition.isNotBlank() } && cards.map { it.id }.distinct().size == cards.size) {
            "Invalid copies"
        }
        val words = RandomV1.Words(seed)
        val seating = players.sorted().toMutableList()
        for (i in seating.lastIndex downTo 1) {
            val j = RandomV1.sample(i + 1, words::next)
            val player = seating[i]; seating[i] = seating[j]; seating[j] = player
        }
        return initial(settings, seating.toList(), cards, cards.asReversed().map { it.id })
    }

    /** [supply] is drawn from its end. Every grid is dealt now, so its cards never depend on pick timing. */
    private fun initial(settings: DraftSettings, seating: List<String>, copies: List<DraftCopy>, supply: List<String>): GridState {
        val remaining = supply.toMutableList()
        val grids = deal(settings, seating, remaining, 0)
        return Working(GridState(settings, seating, copies, remaining.toList(), grids, -1,
            seating.associateWith { emptyList() }, queues = seating.associateWith { emptyList() },
            opened = seating.associateWith { -1 })).settle()
    }

    /** Grids from index [from] on, each with nine cards and then its refill cards, drawn from the end of [supply]. */
    private fun deal(settings: DraftSettings, seating: List<String>, supply: MutableList<String>, from: Int): List<GridBoard> =
        (from until settings.grids).map { number ->
            fun draw() = supply.removeAt(supply.lastIndex)
            val turns = List(if (settings.twoPicks) 4 else seating.size) { turn -> seating[(number + turn) % seating.size] }
            val positions = List(9) { draw() }
            val reserve = if (settings.gridRefills(seating.size)) List(3) { draw() } else emptyList()
            GridBoard("grid-$number", number + 1, turns, positions, reserve = reserve)
        }

    /**
     * Brings a snapshot from before per-member opening to the current model (identity for current snapshots): grids
     * still waiting for their first selection get their refill cards, every remaining grid is dealt from the supply,
     * each member's queue is their turns in the open grids (oldest first, the old head rule), and members whose picks of
     * the round are done open the next round at once.
     */
    fun resume(state: GridState): GridState {
        if (state.opened.isNotEmpty() || state.finished) return state
        val supply = state.supply.toMutableList()
        val refills = state.settings.gridRefills(state.seating.size)
        val existing = state.grids.map { grid ->
            if (refills && !grid.finished && grid.selection == 0 && grid.reserve.isEmpty())
                grid.copy(reserve = List(3) { supply.removeAt(supply.lastIndex) }) else grid
        }
        val grids = existing + deal(state.settings, state.seating, supply, existing.size)
        return Working(state.copy(supply = supply.toList(), grids = grids,
            queues = state.seating.associateWith { player -> existing.filter { !it.finished && it.currentPlayer == player }.map { it.id } },
            held = emptyMap(), opened = state.seating.associateWith { state.round })).settle()
    }

    fun pick(state: GridState, pick: GridPick): GridPickResult {
        fun reject(reason: GridPickRejection) = GridPickResult(reason = reason)
        if (state.finished) return reject(GridPickRejection.DRAFT_FINISHED)
        val current = resume(state)
        val grid = current.grids.firstOrNull { it.id == pick.gridId }
        val queue = current.queues[pick.player]
        if (grid == null || queue == null || grid.id !in queue) return reject(GridPickRejection.NOT_YOUR_TURN)
        if (queue.first() != grid.id) return reject(GridPickRejection.NOT_HEAD_GRID)
        if (pick.line.index !in 0..2) return reject(GridPickRejection.INVALID_LINE)
        val positions = linePositions(pick.line)
        if (positions.all { grid.positions[it] == null }) return reject(GridPickRejection.EMPTY_LINE)
        return GridPickResult(state = Working(current).applyPick(pick, positions))
    }

    /** Grids [player] can pick from now, head first. */
    fun queue(state: GridState, player: String): List<String> = resume(state).queues[player].orEmpty()

    fun privateView(state: GridState, player: String): GridPrivateView {
        require(player in state.seating) { "Not a draft member" }
        val current = resume(state)
        val copies = current.copies.associateBy { it.id }
        val queue = current.queues[player].orEmpty()
        val rounds = current.settings.rounds
        return GridPrivateView(player, current.pools.getValue(player).map(copies::getValue), queue.firstOrNull(), queue,
            if (current.finished) rounds else (current.openedRound(player) + 1).coerceAtMost(rounds), rounds)
    }

    /**
     * Boards and selections are public, but only of grids the [viewer] has seen: grids of a round that both the viewer
     * and the grid's opener have opened. Without a viewer, every opened grid.
     */
    fun publicView(state: GridState, viewer: String? = null): GridPublicView {
        val current = resume(state)
        val copies = current.copies.associateBy { it.id }
        val refills = current.settings.gridRefills(current.seating.size)
        val visible = current.grids.filter { grid ->
            val round = current.roundOf(grid)
            round <= current.openedRound(grid.turns.first()) && (viewer == null || round <= current.openedRound(viewer))
        }
        val ids = visible.map { it.id }.toSet()
        val log = current.log.filter { it.gridId in ids }
        val entries = log.groupBy { it.gridId }
        fun board(grid: GridBoard) = GridPublicBoard(grid.id, grid.number, grid.positions.map { it?.let(copies::getValue) },
            grid.currentPlayer, current.roundOf(grid) + 1, grid.finished,
            slots(grid, entries[grid.id].orEmpty(), refills).map { (id, player, refill) ->
                GridPublicSlot(id?.let(copies::getValue), player, refill)
            })
        val lastPick = log.withIndex().associate { it.value.gridId to it.index }
        return GridPublicView(if (current.finished) current.settings.rounds else current.round + 1,
            current.settings.rounds, current.finished, current.seating,
            visible.filterNot { it.finished }.map(::board),
            log.map { event -> GridPublicPickLog(event.player, event.gridId, event.line,
                event.kept.map(copies::getValue), event.discarded.map(copies::getValue)) },
            visible.filter { it.finished }.sortedByDescending { lastPick[it.id] ?: -1 }.map(::board))
    }

    /**
     * Each position's latest card, its picker and whether it is a refill, replayed from the grid's selections: a
     * selection takes the occupied positions of its line in order, the first one is refilled in place when grids
     * refill, and a finished grid's discards are its remaining positions in order.
     */
    private fun slots(grid: GridBoard, entries: List<GridPickLog>, refills: Boolean): List<Triple<String?, String?, Boolean>> {
        val cards = arrayOfNulls<String>(9)
        val pickers = arrayOfNulls<String>(9)
        val refilled = BooleanArray(9)
        val occupied = BooleanArray(9) { true }
        entries.forEachIndexed { index, entry ->
            val line = entry.line ?: return@forEachIndexed
            val taken = linePositions(line).filter { occupied[it] }
            taken.zip(entry.kept).forEach { (position, card) -> cards[position] = card; pickers[position] = entry.player }
            taken.forEach { occupied[it] = false }
            if (index == 0 && refills && grid.turns.size > 1) taken.forEach {
                occupied[it] = true; refilled[it] = true; cards[it] = null; pickers[it] = null
            }
        }
        val remaining = (0 until 9).filter { occupied[it] }
        if (grid.finished) remaining.zip(entries.lastOrNull()?.discarded.orEmpty()).forEach { (position, card) -> cards[position] = card }
        else remaining.forEach { cards[it] = grid.positions.getOrNull(it) }
        return List(9) { Triple(cards[it], pickers[it], refilled[it]) }
    }

    private fun linePositions(line: GridLine): List<Int> = when (line.type) {
        GridLineType.ROW -> List(3) { line.index * 3 + it }
        GridLineType.COLUMN -> List(3) { it * 3 + line.index }
    }

    /** Grid pick rules on the shared [DraftSchedule]; only transition-local collections are mutated. */
    private class Working(private val source: GridState) : DraftSchedule(source.seating, source.settings.rounds,
        source.queues, source.held, source.seating.associateWith { source.openedRound(it) }) {
        private val grids = source.grids.toMutableList()
        private val indexes = grids.withIndex().associate { it.value.id to it.index }
        private val supply = source.supply.toMutableList()
        private val pools = source.pools.mapValues { it.value.toMutableList() }
        private val log = source.log.toMutableList()

        private fun grid(id: String) = grids[indexes.getValue(id)]
        override val items: List<String> = grids.map { it.id }
        override fun round(item: String) = source.roundOf(grid(item))
        override fun opener(item: String) = grid(item).turns.first()
        override fun live(item: String) = !grid(item).finished
        override fun picks(item: String) = grid(item).selection
        /** A grid comes to every member still in its remaining turns. */
        override fun reaches(item: String, holder: String?, player: String) = grid(item).let { player in it.turns.drop(it.selection) }

        /** An empty board has no valid line: the turn passes without asking for input. */
        override fun automatic(item: String, player: String): Boolean {
            val index = indexes.getValue(item)
            if (grids[index].positions.any { it != null }) return false
            advance(index, null, emptyList())
            grids[index].currentPlayer?.let { arrive(item, it) }
            return true
        }

        fun applyPick(pick: GridPick, positions: List<Int>): GridState {
            val index = indexes.getValue(pick.gridId)
            take(pick.player, pick.gridId)
            advance(index, pick.line, positions)
            grids[index].currentPlayer?.let { arrive(pick.gridId, it) }
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
            val reserve = grid.reserve.toMutableList()
            val refill = !finished && selection == 1 && line != null && source.settings.gridRefills(source.seating.size)
            if (refill) {
                // Old snapshots' boards dealt before refill cards were dealt up front still draw from the supply.
                for (position in positions.indices) if (positions[position] == null)
                    positions[position] = reserve.removeFirstOrNull() ?: supply.removeAt(supply.lastIndex)
            }
            val player = grid.turns[grid.selection]
            pools.getValue(player).addAll(kept)
            log.add(GridPickLog(player, grid.id, line, kept, discarded))
            // Unused reserve cards (the refill needed fewer, or the grid ended first) leave the draft with the board.
            grids[index] = grid.copy(positions = positions.toList(), selection = selection,
                reserve = if (finished || refill) emptyList() else reserve.toList())
        }

        fun settle(): GridState {
            open()
            return source.copy(supply = supply.toList(), grids = grids.toList(), round = round,
                pools = pools.mapValues { it.value.toList() }, log = log.toList(), queues = queueSnapshot(),
                held = heldSnapshot(), opened = openedRounds())
        }
    }
}

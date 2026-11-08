package org.asyncttwiz.protocol

import kotlinx.serialization.Serializable

/** Engines whose server snapshot is a [LimitedDraftState]: Rochester, Rotisserie, Winston, Winchester and Sealed. */
val DraftMode.limited: Boolean get() = this == DraftMode.ROCHESTER || this == DraftMode.ROTISSERIE ||
    this == DraftMode.WINSTON || this == DraftMode.WINCHESTER || this == DraftMode.SEALED
/** Booster pack passing ([DraftEngine]), also for pods created as Glimpse. */
val DraftMode.packPassing: Boolean get() = this == DraftMode.BOOSTER || this == DraftMode.GLIMPSE
/** How many members an engine allows. */
val DraftMode.players: IntRange get() = when (this) {
    DraftMode.GRID, DraftMode.WINSTON, DraftMode.WINCHESTER -> 2..4
    else -> 2..8
}

/** A Rochester pack: [cards] as opened (they stay in place), and the cards taken from it with who took them, in order. */
@Serializable data class LimitedPack(
    val id: String,
    /** One-based number across the whole draft. */
    val number: Int,
    /** Zero-based round: packs of a round are open together. */
    val round: Int,
    val opener: String,
    val cards: List<String>,
    val picked: List<String> = emptyList(),
    val pickers: List<String> = emptyList(),
) {
    val empty: Boolean get() = picked.size == cards.size
}

/** One take: a card (Rochester, Rotisserie, the top of Winston's stack) or a whole pile (Winston, Winchester). */
@Serializable data class LimitedTake(val player: String, val cards: List<String>, val packId: String? = null,
    val pile: Int? = null, val fromStack: Boolean = false)

/**
 * Server-only snapshot of the limited engines. Never send it to a member; use [LimitedEngine.view].
 * Fields not used by the pod's engine stay empty.
 */
@Serializable data class LimitedDraftState(
    val settings: DraftSettings,
    val seating: List<String>,
    val copies: List<DraftCopy>,
    val pools: Map<String, List<String>>,
    /**
     * Who can act now and on what: Rochester the packs where it is their pick, the turn engines "turn". Read by the
     * activity query exactly like the other drafts' queues ("Your pick" while non-empty).
     */
    val queues: Map<String, List<String>>,
    val log: List<LimitedTake> = emptyList(),
    val finished: Boolean = false,
    /** Rotisserie: the whole face-up pool, in dealt order. */
    val board: List<String> = emptyList(),
    /** Rochester: every pack, in number order. */
    val packs: List<LimitedPack> = emptyList(),
    /** Rochester: zero-based round whose packs are open. */
    val round: Int = 0,
    /** Winston and Winchester: the face-down stack, top card last. */
    val stack: List<String> = emptyList(),
    /** Winston and Winchester: the piles, each bottom first. */
    val piles: List<List<String>> = emptyList(),
    /** Completed turns (Rotisserie: picks taken). */
    val turn: Int = 0,
    /** Winston: the pile the current player is looking at. */
    val pile: Int = 0,
    /** Winston: every card each member has seen in a pile; it stays face up for them. */
    val seen: Map<String, List<String>> = emptyMap(),
    /** Winchester tables after the first (DRF-158); the first table is [stack], [piles] and [turn]. */
    val tables: List<LimitedTable> = emptyList(),
)

/** One Winchester table: its own face-down stack (top card last), face-up piles (each bottom first) and completed turns. */
@Serializable data class LimitedTable(val stack: List<String>, val piles: List<List<String>>, val turn: Int = 0)

@Serializable enum class LimitedAction { TAKE_CARD, TAKE_PILE, PASS_PILE }
/**
 * [turn] must be the move's turn: Rochester the pack's picks so far, Winchester its [table]'s completed turns, the others
 * [LimitedDraftState.turn]. [table]: Winchester's zero-based table.
 */
@Serializable data class LimitedMove(val player: String, val action: LimitedAction, val turn: Int, val card: String? = null,
    val packId: String? = null, val pile: Int? = null, val table: Int = 0)
@Serializable enum class LimitedRejection { DRAFT_FINISHED, WRONG_ACTION, NOT_YOUR_TURN, STALE_TURN, UNKNOWN_CARD, WRONG_PILE, EMPTY_PILE, CANNOT_PASS, WRONG_TABLE }
data class LimitedResult(val state: LimitedDraftState? = null, val reason: LimitedRejection? = null)

/** A face-up card on a board or in a pack, with who took it. */
@Serializable data class LimitedSlot(val copy: DraftCopy, val pickedBy: String? = null)
@Serializable data class LimitedPackView(val id: String, val number: Int, val opener: String, val slots: List<LimitedSlot>,
    /** Who picks next; null once empty. */
    val currentPlayer: String?,
    /** Picks taken so far (the [LimitedMove.turn] of the next pick). */
    val picks: Int)
/** One Winchester table as members see it (DRF-158): whose turn it is there, its completed turns, stack count and piles. */
@Serializable data class LimitedTableView(val currentPlayer: String?, val turn: Int, val stack: Int, val piles: List<List<DraftCopy>>)
/** One take as the viewer may see it: a card they have not seen is null (a card back). */
@Serializable data class LimitedPickView(val player: String, val cards: List<DraftCopy?>, val pile: Int? = null, val fromStack: Boolean = false)
/** What one member may see of a limited draft. */
@Serializable data class LimitedView(
    val mode: DraftMode,
    val player: String,
    val seating: List<String>,
    /** The viewer's own pool. */
    val pool: List<DraftCopy>,
    val finished: Boolean,
    /** Rotisserie, Winston, Winchester: whose turn it is. */
    val currentPlayer: String? = null,
    /** Completed turns; the [LimitedMove.turn] of the next move (Rochester: per pack). */
    val turn: Int = 0,
    /** Rotisserie: picks in the whole draft. */
    val turns: Int = 0,
    /** Rotisserie: the whole pool. */
    val board: List<LimitedSlot> = emptyList(),
    /** Rochester: the open round's packs, in number order. */
    val packs: List<LimitedPackView> = emptyList(),
    /** Rochester: one-based open round and the round count. */
    val round: Int = 0,
    val rounds: Int = 0,
    /** Winston, Winchester: cards left in the face-down stack. */
    val stack: Int = 0,
    /** Winston, Winchester: piles bottom first; a card the viewer has not seen is null. */
    val piles: List<List<DraftCopy?>> = emptyList(),
    /** Winston: the pile the current player is looking at. */
    val pile: Int = 0,
    /** Winston, Winchester: every take so far, oldest first, as the viewer may see it. */
    val picks: List<LimitedPickView> = emptyList(),
    /** Winchester: every table, in order; the first is also [currentPlayer], [turn], [stack] and [piles]. */
    val tables: List<LimitedTableView> = emptyList(),
)

/**
 * Rochester, Rotisserie, Winston, Winchester and Sealed after Draftmancer's rules. Seating is random; turns go around
 * the table in seat order (Rotisserie and each Rochester pack in snake order). All randomness comes from the seed.
 */
object LimitedEngine {
    private const val TURN = "turn"

    /** Cards the source must hold (a set has unlimited supply). */
    fun needed(settings: DraftSettings, players: Int): Long = when (settings.mode) {
        DraftMode.ROTISSERIE, DraftMode.SEALED -> players.toLong() * settings.cardsPerPlayer
        else -> players.toLong() * settings.packsPerPlayer * settings.cardsPerPack
    }

    private class Random(seed: String) {
        private val words = RandomV1.Words(seed)
        fun <T> shuffled(values: List<T>): List<T> {
            val result = values.toMutableList()
            for (i in result.lastIndex downTo 1) {
                val j = RandomV1.sample(i + 1, words::next)
                val value = result[i]; result[i] = result[j]; result[j] = value
            }
            return result
        }
    }

    private fun check(players: List<String>, settings: DraftSettings) {
        require(settings.mode.limited && settings.validate(players.size).isEmpty()) { "Invalid limited settings" }
        require(players.all { it.isNotBlank() } && players.distinct().size == players.size) { "Invalid members" }
    }

    /** Cube cards are drawn at random without replacement; Rochester, Winston and Winchester cut them into packs. */
    fun startFromCube(players: List<String>, cube: List<DraftCubeEntry>, settings: DraftSettings, seed: String): LimitedDraftState {
        check(players, settings)
        require(cube.all { it.definition.isNotBlank() && it.quantity > 0 }) { "Invalid cube entries" }
        require(cube.sumOf { it.quantity.toLong() } >= needed(settings, players.size)) { "Insufficient cube supply" }
        val random = Random(seed)
        val seating = random.shuffled(players.sorted())
        val copies = cube.groupBy { it.definition }.entries.sortedBy { it.key }.flatMap { (definition, entries) ->
            val quantity = entries.sumOf { it.quantity.toLong() }
            require(quantity <= Int.MAX_VALUE) { "Too many copies of one definition" }
            List(quantity.toInt()) { DraftCopy("$definition:copy:$it", definition, entries.first().colors.sortedBy { c -> c.ordinal }.toSet()) }
        }
        val drawn = random.shuffled(copies).take(needed(settings, players.size).toInt())
        val material = when (settings.mode) {
            DraftMode.ROTISSERIE, DraftMode.SEALED -> listOf(drawn)
            else -> drawn.chunked(settings.cardsPerPack)
        }
        return deal(seating, settings, material, random)
    }

    /**
     * Set boosters, generated from the seed: one per player and pack (Sealed: per player and booster); Rotisserie
     * opens boosters until the pool holds every player's cards.
     */
    fun startFromSet(players: List<String>, set: SetSourceSnapshot, settings: DraftSettings, seed: String): LimitedDraftState {
        check(players, settings)
        val random = Random(seed)
        val seating = random.shuffled(players.sorted())
        val material = if (settings.mode == DraftMode.ROTISSERIE) {
            val needed = needed(settings, players.size)
            var count = 1
            var boosters = SetBoosters.generate(set, count, seed, settings.cardsPerPack)
            while (boosters.sumOf { it.cards.size.toLong() } < needed) {
                val size = boosters.minOf { it.cards.size }.coerceAtLeast(1)
                count += ((needed - boosters.sumOf { it.cards.size.toLong() } + size - 1) / size).toInt()
                // The same seed generates the same leading boosters, so more boosters only append.
                boosters = SetBoosters.generate(set, count, seed, settings.cardsPerPack)
            }
            listOf(boosters.flatMap { it.cards })
        } else SetBoosters.generate(set, players.size * settings.packsPerPlayer, seed, settings.cardsPerPack).map { it.cards }
        return deal(seating, settings, material, random)
    }

    private fun deal(seating: List<String>, settings: DraftSettings, material: List<List<DraftCopy>>, random: Random): LimitedDraftState {
        val copies = material.flatten()
        require(copies.map { it.id }.distinct().size == copies.size) { "Duplicate copy IDs" }
        val empty = seating.associateWith { emptyList<String>() }
        val base = LimitedDraftState(settings, seating, copies, empty, empty)
        val n = seating.size
        return when (settings.mode) {
            DraftMode.SEALED -> {
                // Set: each player opens their own boosters; cube: consecutive shares of the drawn cards.
                val pools = if (material.size == 1) material.single().chunked(settings.cardsPerPlayer)
                    .mapIndexed { seat, cards -> seating[seat] to cards.map { it.id } }.toMap()
                    else material.chunked(settings.packsPerPlayer).mapIndexed { seat, packs -> seating[seat] to packs.flatten().map { it.id } }.toMap()
                base.copy(pools = seating.associateWith { pools[it].orEmpty() }, finished = true)
            }
            DraftMode.ROTISSERIE -> settle(base.copy(board = material.single().map { it.id }))
            DraftMode.ROCHESTER -> {
                val open = (settings.tablePacksOpen ?: n).coerceIn(1, material.size)
                settle(base.copy(packs = material.mapIndexed { index, cards ->
                    LimitedPack("pack-$index", index + 1, index / open, seating[index % n], cards.map { it.id })
                }))
            }
            DraftMode.WINSTON -> {
                val stack = random.shuffled(copies.map { it.id }).toMutableList()
                val piles = List(settings.piles) { mutableListOf<String>() }
                // Winston piles start with one card each.
                piles.forEach { pile -> stack.removeLastOrNull()?.let(pile::add) }
                settle(look(base.copy(stack = stack, piles = piles)))
            }
            DraftMode.WINCHESTER -> {
                // The shuffled cards split evenly into the tables (one per player at most); each deals at the start of every turn.
                val stack = random.shuffled(copies.map { it.id })
                val count = settings.tables.coerceIn(1, n)
                var start = 0
                val tables = List(count) { index ->
                    val size = stack.size / count + if (index < stack.size % count) 1 else 0
                    dealWinchester(LimitedTable(stack.subList(start, start + size).toList(), List(settings.piles) { emptyList() }))
                        .also { start += size }
                }
                settle(withTables(base, tables))
            }
            else -> error("Not a limited mode")
        }
    }

    /** Rotisserie: whose pick the [turn]-th is, in snake order. */
    private fun snake(seating: List<String>, turn: Int, start: Int = 0): String {
        val n = seating.size
        val lap = turn / n
        val position = turn % n
        val offset = if (lap % 2 == 0) position else n - 1 - position
        return seating[(start + offset) % n]
    }

    private fun rotisserieTurns(state: LimitedDraftState) = state.seating.size * state.settings.cardsPerPlayer
    private fun packPlayer(state: LimitedDraftState, pack: LimitedPack): String? =
        if (pack.empty) null else snake(state.seating, pack.picked.size, state.seating.indexOf(pack.opener))
    private fun turnPlayer(state: LimitedDraftState): String? = when {
        state.finished -> null
        state.settings.mode == DraftMode.ROTISSERIE -> snake(state.seating, state.turn)
        state.settings.mode == DraftMode.WINSTON -> state.seating[state.turn % state.seating.size]
        state.settings.mode == DraftMode.WINCHESTER -> tablePlayer(state, 0, tables(state).first())
        else -> null
    }

    /** Winchester: every table, the first from the state's own stack, piles and turn. */
    private fun tables(state: LimitedDraftState): List<LimitedTable> = listOf(LimitedTable(state.stack, state.piles, state.turn)) + state.tables
    private fun withTables(state: LimitedDraftState, tables: List<LimitedTable>): LimitedDraftState =
        state.copy(stack = tables.first().stack, piles = tables.first().piles, turn = tables.first().turn, tables = tables.drop(1))
    private val LimitedTable.empty: Boolean get() = stack.isEmpty() && piles.all { it.isEmpty() }
    /** Winchester: whose turn it is at the [index]-th table; each table's turn order starts one seat later (DRF-158). */
    private fun tablePlayer(state: LimitedDraftState, index: Int, table: LimitedTable): String? =
        if (state.finished || table.empty) null else state.seating[(table.turn + index) % state.seating.size]
    /** Winchester queue entries: "turn" for the first table, "turn:N" for the N-th after it. */
    private fun tableQueue(index: Int) = if (index == 0) TURN else "$TURN:$index"

    /** Recomputes [LimitedDraftState.queues], the Rochester round and whether the draft is over. */
    private fun settle(state: LimitedDraftState): LimitedDraftState {
        val settings = state.settings
        val finished = when (settings.mode) {
            DraftMode.ROTISSERIE -> state.turn >= rotisserieTurns(state) || state.log.size >= state.board.size
            DraftMode.ROCHESTER -> state.packs.all { it.empty }
            DraftMode.WINSTON -> state.stack.isEmpty() && state.piles.all { it.isEmpty() }
            DraftMode.WINCHESTER -> tables(state).all { it.empty }
            else -> true
        }
        val round = if (settings.mode == DraftMode.ROCHESTER)
            state.packs.filter { !it.empty }.minOfOrNull { it.round } ?: (state.packs.maxOfOrNull { it.round } ?: 0) else 0
        val next = state.copy(finished = finished, round = round)
        val queues = next.seating.associateWith { player ->
            when {
                finished -> emptyList()
                settings.mode == DraftMode.ROCHESTER ->
                    next.packs.filter { it.round == round && packPlayer(next, it) == player }.map { it.id }
                settings.mode == DraftMode.WINCHESTER -> tables(next).withIndex()
                    .filter { (index, table) -> tablePlayer(next, index, table) == player }.map { tableQueue(it.index) }
                turnPlayer(next) == player -> listOf(TURN)
                else -> emptyList()
            }
        }
        return next.copy(queues = queues)
    }

    /** Winston: the current player looks at their current pile; its cards stay face up for them. */
    private fun look(state: LimitedDraftState): LimitedDraftState {
        val player = state.seating[state.turn % state.seating.size]
        val cards = state.piles.getOrNull(state.pile).orEmpty()
        val known = state.seen[player].orEmpty()
        return state.copy(seen = state.seen + (player to known + cards.filter { it !in known }))
    }

    /** Winchester: every pile of a table gets one card from its stack at the start of a turn. */
    private fun dealWinchester(table: LimitedTable): LimitedTable {
        val stack = table.stack.toMutableList()
        val piles = table.piles.map { pile -> stack.removeLastOrNull()?.let { pile + it } ?: pile }
        return table.copy(stack = stack, piles = piles)
    }

    fun act(state: LimitedDraftState, move: LimitedMove): LimitedResult {
        fun reject(reason: LimitedRejection) = LimitedResult(reason = reason)
        if (state.finished) return reject(LimitedRejection.DRAFT_FINISHED)
        val settings = state.settings
        val pool = state.pools[move.player] ?: return reject(LimitedRejection.NOT_YOUR_TURN)
        fun take(next: LimitedDraftState, take: LimitedTake) = next.copy(log = next.log + take,
            pools = next.pools + (move.player to pool + take.cards))
        when (settings.mode) {
            DraftMode.ROTISSERIE -> {
                if (move.action != LimitedAction.TAKE_CARD) return reject(LimitedRejection.WRONG_ACTION)
                if (turnPlayer(state) != move.player) return reject(LimitedRejection.NOT_YOUR_TURN)
                if (move.turn != state.turn) return reject(LimitedRejection.STALE_TURN)
                val taken = state.log.flatMap { it.cards }.toSet()
                val card = move.card?.takeIf { it in state.board && it !in taken } ?: return reject(LimitedRejection.UNKNOWN_CARD)
                return LimitedResult(settle(take(state, LimitedTake(move.player, listOf(card))).copy(turn = state.turn + 1)))
            }
            DraftMode.ROCHESTER -> {
                if (move.action != LimitedAction.TAKE_CARD) return reject(LimitedRejection.WRONG_ACTION)
                val index = state.packs.indexOfFirst { it.id == move.packId }
                val pack = state.packs.getOrNull(index)
                if (pack == null || pack.round != state.round || packPlayer(state, pack) != move.player)
                    return reject(LimitedRejection.NOT_YOUR_TURN)
                if (move.turn != pack.picked.size) return reject(LimitedRejection.STALE_TURN)
                val card = move.card?.takeIf { it in pack.cards && it !in pack.picked } ?: return reject(LimitedRejection.UNKNOWN_CARD)
                val packs = state.packs.toMutableList()
                packs[index] = pack.copy(picked = pack.picked + card, pickers = pack.pickers + move.player)
                return LimitedResult(settle(take(state.copy(packs = packs), LimitedTake(move.player, listOf(card), pack.id))))
            }
            DraftMode.WINSTON -> {
                if (move.action != LimitedAction.TAKE_PILE && move.action != LimitedAction.PASS_PILE) return reject(LimitedRejection.WRONG_ACTION)
                if (turnPlayer(state) != move.player) return reject(LimitedRejection.NOT_YOUR_TURN)
                if (move.turn != state.turn) return reject(LimitedRejection.STALE_TURN)
                if (move.pile != state.pile) return reject(LimitedRejection.WRONG_PILE)
                val piles = state.piles.map { it.toMutableList() }
                val stack = state.stack.toMutableList()
                val last = state.pile == piles.lastIndex
                if (move.action == LimitedAction.TAKE_PILE) {
                    val cards = piles[state.pile].toList()
                    if (cards.isEmpty()) return reject(LimitedRejection.EMPTY_PILE)
                    // The taken pile is replaced with one card from the stack.
                    piles[state.pile].clear(); stack.removeLastOrNull()?.let(piles[state.pile]::add)
                    return LimitedResult(endWinston(take(state.copy(piles = piles, stack = stack), LimitedTake(move.player, cards, pile = state.pile))))
                }
                // Draftmancer: with an empty stack you must take unless a later pile still has cards.
                val later = (state.pile + 1..piles.lastIndex).firstOrNull { piles[it].isNotEmpty() }
                if (stack.isEmpty() && later == null) return reject(LimitedRejection.CANNOT_PASS)
                // The passed pile gets one more card, unless it is the last pile and that card is the stack's last.
                if (stack.size > 1 || !last && stack.isNotEmpty()) piles[state.pile].add(stack.removeAt(stack.lastIndex))
                if (later == null) {
                    // Passed every pile: take the top card of the stack.
                    val card = stack.removeAt(stack.lastIndex)
                    return LimitedResult(endWinston(take(state.copy(piles = piles, stack = stack),
                        LimitedTake(move.player, listOf(card), fromStack = true))))
                }
                return LimitedResult(settle(look(state.copy(piles = piles, stack = stack, pile = later))))
            }
            DraftMode.WINCHESTER -> {
                if (move.action != LimitedAction.TAKE_PILE) return reject(LimitedRejection.WRONG_ACTION)
                val tables = tables(state)
                val table = tables.getOrNull(move.table) ?: return reject(LimitedRejection.WRONG_TABLE)
                if (tablePlayer(state, move.table, table) != move.player) return reject(LimitedRejection.NOT_YOUR_TURN)
                if (move.turn != table.turn) return reject(LimitedRejection.STALE_TURN)
                val index = move.pile ?: return reject(LimitedRejection.WRONG_PILE)
                val cards = table.piles.getOrNull(index) ?: return reject(LimitedRejection.WRONG_PILE)
                if (cards.isEmpty()) return reject(LimitedRejection.EMPTY_PILE)
                val piles = table.piles.toMutableList().also { it[index] = emptyList() }
                val dealt = dealWinchester(table.copy(piles = piles, turn = table.turn + 1))
                val next = withTables(state, tables.toMutableList().also { it[move.table] = dealt })
                return LimitedResult(settle(take(next, LimitedTake(move.player, cards, pile = index))))
            }
            else -> return reject(LimitedRejection.WRONG_ACTION)
        }
    }

    /** The next player starts at the first pile that has cards. */
    private fun endWinston(state: LimitedDraftState): LimitedDraftState {
        val next = state.copy(turn = state.turn + 1, pile = state.piles.indexOfFirst { it.isNotEmpty() }.coerceAtLeast(0))
        return settle(look(next))
    }

    /** Picks [player] can make in a row if nobody else acts: their pick badge (DRF-074). 0 when they can't act. */
    fun consecutivePicks(state: LimitedDraftState, player: String): Int {
        if (state.finished || state.queues[player].isNullOrEmpty()) return 0
        return when (state.settings.mode) {
            DraftMode.ROTISSERIE -> {
                val total = minOf(rotisserieTurns(state), state.board.size)
                (state.turn until total).takeWhile { snake(state.seating, it) == player }.count()
            }
            DraftMode.ROCHESTER -> state.packs.filter { it.round == state.round && !it.empty }.sumOf { pack ->
                val start = state.seating.indexOf(pack.opener)
                (pack.picked.size until pack.cards.size).takeWhile { snake(state.seating, it, start) == player }.count()
            }
            // Winchester: one pick per table where it is their turn.
            else -> state.queues.getValue(player).size
        }
    }

    fun picksMade(state: LimitedDraftState, player: String): Int = state.log.count { it.player == player }

    /** What [viewer] may see: their pool, every open board, and pile cards only once they have seen them (Winston). */
    fun view(state: LimitedDraftState, viewer: String): LimitedView {
        require(viewer in state.seating) { "Not a draft member" }
        val copies = state.copies.associateBy { it.id }
        val mode = state.settings.mode
        val base = LimitedView(mode, viewer, state.seating, state.pools.getValue(viewer).map(copies::getValue), state.finished,
            turnPlayer(state), state.turn)
        return when (mode) {
            DraftMode.ROTISSERIE -> {
                val pickers = state.log.flatMap { take -> take.cards.map { it to take.player } }.toMap()
                base.copy(turns = minOf(rotisserieTurns(state), state.board.size),
                    board = state.board.map { LimitedSlot(copies.getValue(it), pickers[it]) })
            }
            DraftMode.ROCHESTER -> base.copy(round = if (state.finished) state.packs.maxOf { it.round } + 1 else state.round + 1,
                rounds = state.packs.maxOf { it.round } + 1,
                packs = state.packs.filter { it.round == state.round }.map { pack ->
                    val pickers = pack.picked.zip(pack.pickers).toMap()
                    LimitedPackView(pack.id, pack.number, pack.opener, pack.cards.map { LimitedSlot(copies.getValue(it), pickers[it]) },
                        packPlayer(state, pack), pack.picked.size)
                })
            DraftMode.WINSTON, DraftMode.WINCHESTER -> {
                val seen = if (mode == DraftMode.WINSTON) state.seen[viewer].orEmpty().toSet() else null
                fun shown(id: String) = copies.getValue(id).takeIf { seen == null || id in seen }
                base.copy(stack = state.stack.size, pile = state.pile,
                    piles = state.piles.map { pile -> pile.map(::shown) },
                    picks = state.log.map { take ->
                        LimitedPickView(take.player, take.cards.map { if (take.player == viewer) copies.getValue(it) else shown(it) },
                            take.pile, take.fromStack)
                    },
                    tables = if (mode != DraftMode.WINCHESTER) emptyList() else tables(state).mapIndexed { index, table ->
                        LimitedTableView(tablePlayer(state, index, table), table.turn, table.stack.size,
                            table.piles.map { pile -> pile.map(copies::getValue) })
                    })
            }
            else -> base
        }
    }
}

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
    /** Winston and Winchester tables after the first (DRF-156, DRF-158); the first table is [stack], [piles], [turn] and [pile]. */
    val tables: List<LimitedTable> = emptyList(),
    /**
     * Rotisserie: each member's private pick queue (DRF-164), untaken board cards in order; its first card is taken for
     * them as soon as their turn comes.
     */
    val pickQueues: Map<String, List<String>> = emptyMap(),
    /** Rotisserie: the card another member took that cleared a member's pick queue, until they queue or pick again. */
    val queueCleared: Map<String, String> = emptyMap(),
)

/**
 * One Winston or Winchester table: its own face-down stack (top card last), piles (each bottom first), completed turns
 * and (Winston) the pile its current player is looking at.
 */
@Serializable data class LimitedTable(val stack: List<String>, val piles: List<List<String>>, val turn: Int = 0, val pile: Int = 0)

@Serializable enum class LimitedAction { TAKE_CARD, TAKE_PILE, PASS_PILE }
/**
 * [turn] must be the move's turn: Rochester the pack's picks so far, Winston and Winchester its [table]'s completed turns,
 * the others [LimitedDraftState.turn]. [table]: Winston's and Winchester's zero-based table.
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
/**
 * One Winston or Winchester table as members see it (DRF-156, DRF-158): whose turn it is there, its completed turns, stack
 * count and piles (Winston: a card the viewer has not seen is null), and the pile Winston's current player looks at.
 */
@Serializable data class LimitedTableView(val currentPlayer: String?, val turn: Int, val stack: Int, val piles: List<List<DraftCopy?>>,
    val pile: Int = 0)
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
    /** Winston, Winchester: every table, in order; the first is also [currentPlayer], [turn], [stack], [piles] and [pile]. */
    val tables: List<LimitedTableView> = emptyList(),
    /** Rotisserie: the viewer's own pick queue, in order (DRF-164). */
    val pickQueue: List<DraftCopy> = emptyList(),
    /** Rotisserie: the card another member took that cleared the viewer's pick queue, until they queue or pick again. */
    val queueCleared: DraftCopy? = null,
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
    fun startFromSet(players: List<String>, set: SetSourceSnapshot, settings: DraftSettings, seed: String): LimitedDraftState =
        startFromSet(players, SetPackSources.of(set), settings, seed)

    /**
     * Set boosters from the pack grid (DRF-173): each pack from its cell's set ([PodPackGrid.packSets]); Rotisserie
     * opens boosters of the pod's one set.
     */
    fun startFromSet(players: List<String>, sources: SetPackSources, settings: DraftSettings, seed: String): LimitedDraftState {
        check(players, settings)
        val random = Random(seed)
        val seating = random.shuffled(players.sorted())
        val set = sources.first
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
        } else SetBoosters.generate(sources, PodPackGrid.packSets(sources.packs, settings, players.size), seed, settings.cardsPerPack)
            .map { it.cards }
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
            DraftMode.WINSTON, DraftMode.WINCHESTER -> {
                // The shuffled cards split evenly into the tables (one per player at most). Every pile gets one card: Winston
                // piles start so and each table's first player looks at its pile 1; Winchester deals so every turn.
                val stack = random.shuffled(copies.map { it.id })
                val count = settings.tables.coerceIn(1, n)
                var start = 0
                val tables = List(count) { index ->
                    val size = stack.size / count + if (index < stack.size % count) 1 else 0
                    dealOne(LimitedTable(stack.subList(start, start + size).toList(), List(settings.piles) { emptyList() }))
                        .also { start += size }
                }
                val state = withTables(base, tables)
                settle(if (settings.mode == DraftMode.WINSTON) tables.indices.fold(state, ::look) else state)
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
        state.settings.mode.piled -> tablePlayer(state, 0, tables(state).first())
        else -> null
    }

    /** Winston and Winchester: the pile engines, played at one or more tables (DRF-156, DRF-158). */
    private val DraftMode.piled: Boolean get() = this == DraftMode.WINSTON || this == DraftMode.WINCHESTER
    /** Pile engines: every table, the first from the state's own stack, piles, turn and pile. */
    private fun tables(state: LimitedDraftState): List<LimitedTable> =
        listOf(LimitedTable(state.stack, state.piles, state.turn, state.pile)) + state.tables
    private fun withTables(state: LimitedDraftState, tables: List<LimitedTable>): LimitedDraftState = tables.first().let { first ->
        state.copy(stack = first.stack, piles = first.piles, turn = first.turn, pile = first.pile, tables = tables.drop(1))
    }
    private fun withTable(state: LimitedDraftState, index: Int, table: LimitedTable): LimitedDraftState =
        withTables(state, tables(state).toMutableList().also { it[index] = table })
    private val LimitedTable.empty: Boolean get() = stack.isEmpty() && piles.all { it.isEmpty() }
    /** Whose turn the [index]-th table's is by its turn count; each table's turn order starts one seat later. */
    private fun tableSeat(state: LimitedDraftState, index: Int, table: LimitedTable): String =
        state.seating[(table.turn + index) % state.seating.size]
    /** Whose turn it is at the [index]-th table; null once it is empty or the draft is over (DRF-156, DRF-158). */
    private fun tablePlayer(state: LimitedDraftState, index: Int, table: LimitedTable): String? =
        if (state.finished || table.empty) null else tableSeat(state, index, table)
    /** Pile engines' queue entries: "turn" for the first table, "turn:N" for the N-th after it. */
    private fun tableQueue(index: Int) = if (index == 0) TURN else "$TURN:$index"

    /** Recomputes [LimitedDraftState.queues], the Rochester round and whether the draft is over. */
    private fun settle(state: LimitedDraftState): LimitedDraftState {
        val settings = state.settings
        val finished = when (settings.mode) {
            DraftMode.ROTISSERIE -> state.turn >= rotisserieTurns(state) || state.log.size >= state.board.size
            DraftMode.ROCHESTER -> state.packs.all { it.empty }
            DraftMode.WINSTON, DraftMode.WINCHESTER -> tables(state).all { it.empty }
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
                settings.mode.piled -> tables(next).withIndex()
                    .filter { (index, table) -> tablePlayer(next, index, table) == player }.map { tableQueue(it.index) }
                turnPlayer(next) == player -> listOf(TURN)
                else -> emptyList()
            }
        }
        return next.copy(queues = queues)
    }

    /** Winston: the [index]-th table's current player looks at its current pile; its cards stay face up for them. */
    private fun look(state: LimitedDraftState, index: Int): LimitedDraftState {
        val table = tables(state)[index]
        val player = tableSeat(state, index, table)
        val cards = table.piles.getOrNull(table.pile).orEmpty()
        val known = state.seen[player].orEmpty()
        return state.copy(seen = state.seen + (player to known + cards.filter { it !in known }))
    }

    /** Every pile of a table gets one card from its stack: Winston's at the start, Winchester's at the start of every turn. */
    private fun dealOne(table: LimitedTable): LimitedTable {
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
                return LimitedResult(rotisserieTake(state, move.player, card))
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
                val table = tables(state).getOrNull(move.table) ?: return reject(LimitedRejection.WRONG_TABLE)
                if (tablePlayer(state, move.table, table) != move.player) return reject(LimitedRejection.NOT_YOUR_TURN)
                if (move.turn != table.turn) return reject(LimitedRejection.STALE_TURN)
                if (move.pile != table.pile) return reject(LimitedRejection.WRONG_PILE)
                val piles = table.piles.map { it.toMutableList() }
                val stack = table.stack.toMutableList()
                val last = table.pile == piles.lastIndex
                fun at(next: LimitedTable) = withTable(state, move.table, next)
                if (move.action == LimitedAction.TAKE_PILE) {
                    val cards = piles[table.pile].toList()
                    if (cards.isEmpty()) return reject(LimitedRejection.EMPTY_PILE)
                    // The taken pile is replaced with one card from the stack.
                    piles[table.pile].clear(); stack.removeLastOrNull()?.let(piles[table.pile]::add)
                    return LimitedResult(endWinston(take(at(table.copy(piles = piles, stack = stack)),
                        LimitedTake(move.player, cards, pile = table.pile)), move.table))
                }
                // Draftmancer: with an empty stack you must take unless a later pile still has cards.
                val later = (table.pile + 1..piles.lastIndex).firstOrNull { piles[it].isNotEmpty() }
                if (stack.isEmpty() && later == null) return reject(LimitedRejection.CANNOT_PASS)
                // The passed pile gets one more card, unless it is the last pile and that card is the stack's last.
                if (stack.size > 1 || !last && stack.isNotEmpty()) piles[table.pile].add(stack.removeAt(stack.lastIndex))
                if (later == null) {
                    // Passed every pile: take the top card of the stack.
                    val card = stack.removeAt(stack.lastIndex)
                    return LimitedResult(endWinston(take(at(table.copy(piles = piles, stack = stack)),
                        LimitedTake(move.player, listOf(card), fromStack = true)), move.table))
                }
                return LimitedResult(settle(look(at(table.copy(piles = piles, stack = stack, pile = later)), move.table)))
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
                val next = withTable(state, move.table, dealOne(table.copy(piles = piles, turn = table.turn + 1)))
                return LimitedResult(settle(take(next, LimitedTake(move.player, cards, pile = index))))
            }
            else -> return reject(LimitedRejection.WRONG_ACTION)
        }
    }

    /**
     * Rotisserie: [player] takes [card] (DRF-152). It leaves their own pick queue and clears every other member's queue
     * holding it (DRF-164); then, while the next turn's player has a queue, its first card is taken for them at once.
     */
    private fun rotisserieTake(state: LimitedDraftState, player: String, card: String): LimitedDraftState {
        val cleared = state.pickQueues.filter { (who, cards) -> who != player && card in cards }.keys
        val queues = state.pickQueues.mapValues { (who, cards) -> if (who == player) cards - card else cards }
            .filter { (who, cards) -> who !in cleared && cards.isNotEmpty() }
        val next = settle(state.copy(log = state.log + LimitedTake(player, listOf(card)),
            pools = state.pools + (player to state.pools.getValue(player) + card), turn = state.turn + 1,
            pickQueues = queues, queueCleared = state.queueCleared - player + cleared.associateWith { card }))
        val following = turnPlayer(next) ?: return next
        val head = next.pickQueues[following]?.firstOrNull() ?: return next
        return rotisserieTake(next, following, head)
    }

    /**
     * Rotisserie: sets [player]'s private pick queue to [cards], untaken board cards in order (DRF-164); an empty list
     * empties it. Nothing is taken now, even on their turn: a queue's first card is taken when their turn comes.
     */
    fun queue(state: LimitedDraftState, player: String, cards: List<String>): LimitedResult {
        fun reject(reason: LimitedRejection) = LimitedResult(reason = reason)
        if (state.finished) return reject(LimitedRejection.DRAFT_FINISHED)
        if (state.settings.mode != DraftMode.ROTISSERIE) return reject(LimitedRejection.WRONG_ACTION)
        if (player !in state.seating) return reject(LimitedRejection.NOT_YOUR_TURN)
        val taken = state.log.flatMap { it.cards }.toSet()
        val board = state.board.toSet()
        if (cards.distinct().size != cards.size || cards.any { it !in board || it in taken }) return reject(LimitedRejection.UNKNOWN_CARD)
        val queues = if (cards.isEmpty()) state.pickQueues - player else state.pickQueues + (player to cards)
        return LimitedResult(state.copy(pickQueues = queues, queueCleared = state.queueCleared - player))
    }

    /** Winston: the [index]-th table's turn ends; its next player starts at the first pile that has cards. */
    private fun endWinston(state: LimitedDraftState, index: Int): LimitedDraftState {
        val table = tables(state)[index]
        val next = table.copy(turn = table.turn + 1, pile = table.piles.indexOfFirst { it.isNotEmpty() }.coerceAtLeast(0))
        return settle(look(withTable(state, index, next), index))
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
            // Winston and Winchester: one pick per table where it is their turn.
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
                    board = state.board.map { LimitedSlot(copies.getValue(it), pickers[it]) },
                    pickQueue = state.pickQueues[viewer].orEmpty().map(copies::getValue),
                    queueCleared = state.queueCleared[viewer]?.let(copies::getValue))
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
                    tables = tables(state).mapIndexed { index, table ->
                        LimitedTableView(tablePlayer(state, index, table), table.turn, table.stack.size,
                            table.piles.map { pile -> pile.map(::shown) }, table.pile)
                    })
            }
            else -> base
        }
    }
}

package org.asyncttwiz.protocol

/**
 * Bot seats' identities (owner 2026-11-21): bot seat n drafts as player "bot:n" and is named "Bot n". No account has
 * such an id (accounts are UUIDs).
 */
object PodBots {
    const val PREFIX = "bot:"
    fun id(number: Int): String = "$PREFIX$number"
    /** The player ids of [count] bot seats, in number order. */
    fun ids(count: Int): List<String> = (1..count).map(::id)
    fun isBot(player: String): Boolean = player.startsWith(PREFIX)
    /** A bot seat's number from its player id; null for anyone else's. */
    fun number(player: String): Int? = player.takeIf(::isBot)?.removePrefix(PREFIX)?.toIntOrNull()
    fun identity(number: Int): AccountIdentity = AccountIdentity(id(number), "Bot $number")
    /** A bot seat's identity from its player id; null for anyone else's. */
    fun identity(player: String): AccountIdentity? =
        number(player)?.let(::identity)
}

/**
 * Modes where bot seats draft (owner 2026-11-21): every mode with a draft that passes or shares cards. Sealed has no
 * draft, and in Arena each player drafts alone, so a bot there would touch nobody: neither has bot seats.
 */
val DraftMode.botSeats: Boolean get() = this != DraftMode.SEALED && this != DraftMode.ARENA
/** The most bot seats a pod of this mode takes: its engine's player limit less its creator (DRF-296, owner 2026-11-23: a
 * solo draft is its creator alone with bots). */
val DraftMode.maxBots: Int get() = if (botSeats) players.last - 1 else 0

/** A bot's move, with [text] saying what it was offered (each card's score) and what it chose, for the server's log. */
data class DraftBotMove<T>(val move: T, val text: String)

/**
 * Bot seats' moves (owner 2026-11-21): the server makes them the moment a pack, grid, pile or turn reaches a bot, so the
 * humans' draft never waits for one. A bot sees what its seat may see (its own pool, its packs, public boards, the
 * Winston piles it has looked at) and judges cards by [DraftBot]: one card at a time by its score, several taken
 * together (a Grid line, a pile) by [DraftBot.value]; it burns by [DraftBot.burns]. Pure and deterministic.
 */
object DraftBotSeats {
    /** Winston: a pile before the last one with cards is taken when worth at least this ([DraftBot.value]), about one
     * good card; otherwise the bot looks at the next. */
    const val WINSTON_TAKE = 60.0
    /** Winston: the last pile with cards is taken when worth at least a blind card from the stack, counted as an
     * ordinary card ([DraftBot.BASE]); otherwise the bot passes it and takes the stack's top card. */
    const val WINSTON_BLIND = DraftBot.BASE.toDouble()
    /** The most bot moves one run makes: far more than any draft has, a guard against a bot that can't stop. */
    private const val LIMIT = 100_000

    /** Whether a bot can act in a draft with these [queues] (the drafts' "who can act now"). */
    fun waiting(queues: Map<String, List<String>>): Boolean = queues.any { (player, queue) -> PodBots.isBot(player) && queue.isNotEmpty() }

    private fun name(card: DraftBotCard) = card.definition?.name ?: card.id
    private fun offered(cards: List<DraftBotCard>, scores: List<DraftBotScore>): String {
        val byId = cards.associateBy { it.id }
        return scores.joinToString { "${byId[it.card]?.let(::name) ?: it.card} ${it.total}" }
    }
    private fun names(cards: List<DraftBotCard>, ids: List<String>): String {
        val byId = cards.associateBy { it.id }
        return ids.joinToString { byId[it]?.let(::name) ?: it }.ifEmpty { "nothing" }
    }
    /** Each offered card's score total by its id (every card of a choice's offer has one). */
    private fun totals(choice: DraftBotChoice): (String) -> Int {
        val totals = choice.scores.associate { it.card to it.total }
        return { id -> totals[id] ?: DraftBot.NEUTRAL }
    }
    private fun colours(choice: DraftBotChoice) =
        if (choice.colors.isEmpty()) "open" else choice.colors.joinToString("") { it.name } + " at ${choice.commitment}%"

    /** Booster, Glimpse and Custom booster: [bot]'s pick from the head of its queue, or null when it has none. */
    fun booster(state: DraftState, bot: String, card: (String) -> DraftBotCard): DraftBotMove<DraftPick>? {
        if (state.finished || state.arena != null) return null
        val packId = state.queues[bot]?.firstOrNull() ?: return null
        val pack = state.packs.first { it.id == packId }
        val taken = state.log.count { !it.automatic && it.packId == pack.id }
        val offer = pack.cards.map(card)
        val keep = state.settings.keepAt(taken).coerceAtMost(offer.size)
        if (keep < 1) return null
        val pool = state.pools.getValue(bot).map(card)
        val choice = DraftBot.choose(offer, pool, pool.size / keep + 1, keep)
        val rest = offer.filter { it.id !in choice.picks }
        // DRF-045: near a pack's end the rest all go anyway (burned or thrown away), so only the keeps are chosen
        val burns = if (state.settings.keepsOnly(offer.size, keep.toLong(), taken)) emptyList()
            else DraftBot.burns(rest, state.settings.burnAt(taken))
        return DraftBotMove(DraftPick(bot, pack.id, choice.picks, burns),
            "pack ${pack.number} of ${pack.initialPlayer}, pick ${taken + 1} (${colours(choice)}): offered " +
                "${offered(offer, choice.scores)}; keeps ${names(offer, choice.picks)}" +
                if (burns.isEmpty()) "" else "; burns ${names(offer, burns)}")
    }

    /** Grid: [bot]'s line of the head of its queue, the one whose cards are worth most together; null when it has none. */
    fun grid(state: GridState, bot: String, card: (String) -> DraftBotCard): DraftBotMove<GridPick>? {
        if (state.finished) return null
        val gridId = state.queues[bot]?.firstOrNull() ?: return null
        val grid = state.grids.first { it.id == gridId }
        val pool = state.pools.getValue(bot).map(card)
        val onBoard = grid.positions.filterNotNull().map(card)
        val scores = DraftBot.choose(onBoard, pool, pool.size + 1, 0)
        val total = totals(scores)
        val lines = GridLineType.entries.flatMap { type -> (0..2).map { GridLine(type, it) } }.mapNotNull { line ->
            line.positions().mapNotNull { grid.positions[it] }.takeIf { it.isNotEmpty() }?.let { line to it }
        }
        // Rows before columns, lower index first, on a tie
        val (line, cards) = lines.maxByOrNull { (_, cards) -> DraftBot.value(cards.map(total)) } ?: return null
        return DraftBotMove(GridPick(bot, grid.id, line),
            "grid ${grid.number} (${colours(scores)}): " + lines.joinToString("; ") { (option, ids) ->
                "${option.type.name.lowercase()} ${option.index + 1} ${ids.joinToString { id -> "${name(card(id))} ${total(id)}" }} " +
                    "= ${DraftBot.value(ids.map(total)).toInt()}"
            } + "; takes ${line.type.name.lowercase()} ${line.index + 1} (${names(onBoard, cards)})")
    }

    /** Rochester, Rotisserie, Winston and Winchester: [bot]'s next move, or null when it has none. */
    fun limited(state: LimitedDraftState, bot: String, card: (String) -> DraftBotCard): DraftBotMove<LimitedMove>? {
        if (state.finished || state.queues[bot].isNullOrEmpty()) return null
        val view = LimitedEngine.view(state, bot)
        val pool = view.pool.map { card(it.id) }
        return when (state.settings.mode) {
            DraftMode.ROTISSERIE -> {
                if (view.currentPlayer != bot) return null
                val free = view.board.filter { it.pickedBy == null }.map { card(it.copy.id) }
                val choice = DraftBot.choose(free, pool, pool.size + 1)
                val pick = choice.picks.firstOrNull() ?: return null
                DraftBotMove(LimitedMove(bot, LimitedAction.TAKE_CARD, view.turn, card = pick),
                    "pick ${view.turn + 1} (${colours(choice)}): takes ${names(free, listOf(pick))} of ${free.size} free cards, the best " +
                        offered(free, choice.scores.sortedByDescending { it.total }.take(5)))
            }
            DraftMode.ROCHESTER -> {
                val pack = view.packs.firstOrNull { it.currentPlayer == bot } ?: return null
                val free = pack.slots.filter { it.pickedBy == null }.map { card(it.copy.id) }
                val choice = DraftBot.choose(free, pool, pool.size + 1)
                val pick = choice.picks.firstOrNull() ?: return null
                DraftBotMove(LimitedMove(bot, LimitedAction.TAKE_CARD, pack.picks, card = pick, packId = pack.id),
                    "pack ${pack.number}, pick ${pack.picks + 1} (${colours(choice)}): offered ${offered(free, choice.scores)}; " +
                        "takes ${names(free, listOf(pick))}")
            }
            DraftMode.WINSTON -> {
                val (index, table) = view.tables.withIndex().firstOrNull { it.value.currentPlayer == bot } ?: return null
                // The pile in play is one the bot is looking at: its cards are face up for it
                val pile = table.piles.getOrNull(table.pile).orEmpty().filterNotNull().map { card(it.id) }
                val scores = DraftBot.choose(pile, pool, pool.size + 1, 0)
                val value = DraftBot.value(scores.scores.map { it.total })
                val later = (table.pile + 1..table.piles.lastIndex).any { table.piles[it].isNotEmpty() }
                val canPass = table.stack > 0 || later
                val threshold = if (later) WINSTON_TAKE else WINSTON_BLIND
                val take = !canPass || value >= threshold
                DraftBotMove(LimitedMove(bot, if (take) LimitedAction.TAKE_PILE else LimitedAction.PASS_PILE, table.turn,
                    pile = table.pile, table = index),
                    "table ${index + 1}, pile ${table.pile + 1} (${colours(scores)}): ${offered(pile, scores.scores)} = ${value.toInt()}, " +
                        (if (!canPass) "can't pass: takes it" else if (take) "takes it (at least ${threshold.toInt()})"
                        else if (later) "passes (under ${threshold.toInt()})" else "passes and takes the top card (under ${threshold.toInt()})"))
            }
            DraftMode.WINCHESTER -> {
                val (index, table) = view.tables.withIndex().firstOrNull { it.value.currentPlayer == bot } ?: return null
                val piles = table.piles.map { pile -> pile.filterNotNull().map { card(it.id) } }
                val scores = DraftBot.choose(piles.flatten(), pool, pool.size + 1, 0)
                val total = totals(scores)
                val values = piles.map { pile -> DraftBot.value(pile.map { total(it.id) }) }
                // The lower pile first on a tie
                val best = piles.indices.filter { piles[it].isNotEmpty() }.maxByOrNull { values[it] } ?: return null
                DraftBotMove(LimitedMove(bot, LimitedAction.TAKE_PILE, table.turn, pile = best, table = index),
                    "table ${index + 1} (${colours(scores)}): " + piles.withIndex().filter { it.value.isNotEmpty() }.joinToString("; ") { (i, pile) ->
                        "pile ${i + 1} ${offered(pile, scores.scores.filter { score -> pile.any { it.id == score.card } })} = ${values[i].toInt()}"
                    } + "; takes pile ${best + 1}")
            }
            else -> null
        }
    }

    /** Every move the bots of [state] can make, one after another, until none can act: Booster's and Custom booster's
     * pack passing ([log] gets each move's [DraftBotMove.text]). */
    fun run(state: DraftState, cards: (DraftCopy) -> DraftBotCard, log: (String) -> Unit): DraftState =
        loop(state, state.seating, cards, { it.copies }, { it.queues }, log) { current, bot, card ->
            booster(current, bot, card)?.let { move -> move.text to DraftEngine.pick(current, move.move).let { it.state to it.reason?.name } }
        }

    /** As [run] for a Grid draft. */
    fun run(state: GridState, cards: (DraftCopy) -> DraftBotCard, log: (String) -> Unit): GridState =
        loop(state, state.seating, cards, { it.copies }, { it.queues }, log) { current, bot, card ->
            grid(current, bot, card)?.let { move -> move.text to GridEngine.pick(current, move.move).let { it.state to it.reason?.name } }
        }

    /** As [run] for Rochester, Rotisserie, Winston and Winchester. */
    fun run(state: LimitedDraftState, cards: (DraftCopy) -> DraftBotCard, log: (String) -> Unit): LimitedDraftState =
        loop(state, state.seating, cards, { it.copies }, { it.queues }, log) { current, bot, card ->
            limited(current, bot, card)?.let { move -> move.text to LimitedEngine.act(current, move.move).let { it.state to it.reason?.name } }
        }

    /**
     * Lets the first bot in seat order that can act make its move, again and again, until no bot can. A move the engine
     * refuses (it never should) or a bot without one stops the run, said in [log], so the draft waits rather than loops.
     */
    private fun <S> loop(state: S, seating: List<String>, cards: (DraftCopy) -> DraftBotCard, copies: (S) -> List<DraftCopy>,
        queues: (S) -> Map<String, List<String>>, log: (String) -> Unit,
        act: (S, String, (String) -> DraftBotCard) -> Pair<String, Pair<S?, String?>>?): S {
        val byId = copies(state).associateBy { it.id }
        val known = HashMap<String, DraftBotCard>()
        val card = { id: String -> known.getOrPut(id) { cards(byId.getValue(id)) } }
        var current = state
        repeat(LIMIT) {
            val waiting = queues(current)
            val bot = seating.firstOrNull { PodBots.isBot(it) && waiting[it].orEmpty().isNotEmpty() } ?: return current
            val seat = seating.indexOf(bot) + 1
            val (text, result) = act(current, bot, card) ?: run {
                log("$bot (seat $seat) has a turn but no move to make; the bots stop here")
                return current
            }
            val (next, refusal) = result
            if (next == null) {
                log("$bot (seat $seat) $text: refused ($refusal); the bots stop here")
                return current
            }
            log("$bot (seat $seat) $text")
            current = next
        }
        log("the bots stopped after $LIMIT moves")
        return current
    }
}

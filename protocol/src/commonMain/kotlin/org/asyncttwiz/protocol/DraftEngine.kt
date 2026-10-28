package org.asyncttwiz.protocol

import kotlinx.serialization.Serializable

@Serializable enum class DraftDirection { NEXT_SEAT, PREVIOUS_SEAT }

/** Server-only snapshot. Never send this object to a member; use the projections below. */
@Serializable
data class DraftState(
    val settings: DraftSettings,
    val seating: List<String>,
    val copies: List<DraftCopy>,
    val packs: List<DraftPack>,
    /** Zero-based lowest round a member is still in (minimum of [openedRound]); equals settings.rounds after completion. */
    val round: Int,
    val queues: Map<String, List<String>>,
    val pools: Map<String, List<String>>,
    val log: List<DraftPickLog> = emptyList(),
    /**
     * Highest round each member has opened their own packs for. A member opens the next round early once no pack of
     * their opened rounds can come back to them for a pick. Absent in snapshots from before per-member opening,
     * where every member had opened exactly [round].
     */
    val opened: Map<String, Int> = emptyMap(),
    /**
     * Packs passed to a member ahead of their [openedRound], in arrival order. They are live packs but stay out of
     * [queues] (and so out of every projection, pick check and your-pick hint) until that member opens their round.
     */
    val held: Map<String, List<String>> = emptyMap(),
) {
    val finished: Boolean get() = round == settings.rounds
    /** Direction of the lowest live round; every pack passes in its own round's direction. */
    val direction: DraftDirection get() = DraftEngine.direction(round)
    fun openedRound(player: String): Int = opened[player] ?: round
}

@Serializable data class DraftPack(
    val id: String,
    val round: Int,
    val initialPlayer: String,
    /** One-based pack number for the opening player. */
    val number: Int,
    val cards: List<String>,
)

@Serializable data class DraftPick(val player: String, val packId: String, val keep: List<String>, val burn: List<String> = emptyList(), val librarians: List<String> = emptyList())
@Serializable data class DraftPickLog(
    val player: String,
    val packId: String,
    val round: Int,
    val kept: List<String>,
    val burned: List<String> = emptyList(),
    val discarded: List<String> = emptyList(),
    val automatic: Boolean = false,
    val librarians: List<String> = emptyList(),
)
@Serializable enum class DraftPickRejection { NOT_YOUR_PACK, NOT_HEAD_OF_QUEUE, WRONG_COUNTS, UNKNOWN_CARD, DRAFT_FINISHED, INVALID_LIBRARIANS }
data class DraftPickResult(val state: DraftState? = null, val reason: DraftPickRejection? = null)

@Serializable data class DraftPrivatePack(
    val id: String, val number: Int, val cards: List<DraftCopy>,
    /** One-based pick number of this pack: manual picks already taken from it + 1. */
    val pick: Int = 1,
    /** Member whose manual pick passed the pack to the caller; null while it is the caller's own unpicked pack. */
    val passedBy: String? = null,
)
/** The caller's saved keep/burn/Librarian marks for their current head pack; local intent, never validated as a pick. */
@Serializable data class DraftMarks(
    val packId: String,
    val keeps: List<String> = emptyList(),
    val burns: List<String> = emptyList(),
    val librarians: List<String> = emptyList(),
)
@Serializable data class DraftPrivateView(
    val player: String,
    val head: DraftPrivatePack?,
    val packsBehind: Int,
    val pool: List<DraftCopy>,
    val queuedPacks: List<DraftPrivatePack> = emptyList(),
    /** The caller's own 1-based round (members may open rounds early) and the round count; 0 when unknown. */
    val round: Int = 0,
    val rounds: Int = 0,
    /** Live packs of the caller's opened rounds, outside their queue, that can still reach them for a manual pick. */
    val incoming: Int = 0,
    /** Present only when the saved marks belong to [head]. */
    val marks: DraftMarks? = null,
)
/** No card/pack identities or log. Picks include automatic awards; waiting is not finished until the draft ends. */
@Serializable data class DraftMemberStatus(val player: String, val packsWaiting: Int, val picksMade: Int, val finished: Boolean)
@Serializable data class DraftPublicStatus(val round: Int, val rounds: Int, val finished: Boolean, val members: List<DraftMemberStatus>)

object DraftEngine {
    fun direction(round: Int) = if (round % 2 == 0) DraftDirection.NEXT_SEAT else DraftDirection.PREVIOUS_SEAT

    /** Same canonical source/members and seed give identical seating and packs, independent of input order. */
    fun start(players: List<String>, cube: List<DraftCubeEntry>, settings: DraftSettings, seed: String): DraftState {
        require(settings.validate(players.size).isEmpty()) { "Invalid draft settings" }
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
                DraftCopy("$definition:copy:$index", definition, entries.first().colors.sortedBy { it.ordinal }.toSet(), entries.first().librarian)
            }
        }
        val remaining = shuffled(copies).toMutableList()
        val packs = buildList {
            for (number in 1..settings.packsPerPlayer) {
                for (player in seating) {
                    val cards = mutableListOf<DraftCopy>()
                    if (settings.colorBalance) {
                        // A multicolor copy can cover multiple colors. Never take a second copy for a color already covered.
                        for (color in DraftColor.entries) {
                            if (cards.size == settings.cardsPerPack) break
                            if (cards.any { color in it.colors }) continue
                            val index = remaining.indexOfFirst { color in it.colors }
                            if (index >= 0) cards.add(remaining.removeAt(index))
                        }
                    }
                    while (cards.size < settings.cardsPerPack) cards.add(remaining.removeAt(remaining.lastIndex))
                    add(DraftPack("pack-$size", (number - 1) / settings.packsOpenAtOnce, player, number, cards.map { it.id }))
                }
            }
        }
        return initial(settings, seating, copies, packs)
    }

    /**
     * Pre-generated packs, in opening-number then seat order. Card order and pack sizes are preserved;
     * cardsPerPack and colorBalance do not change them. Copy IDs must be unique across the whole draft.
     */
    fun startWithPacks(players: List<String>, packs: List<List<DraftCopy>>, settings: DraftSettings, seed: String): DraftState {
        require(settings.validate(players.size).isEmpty()) { "Invalid draft settings" }
        require(players.all { it.isNotBlank() } && players.distinct().size == players.size) { "Invalid members" }
        require(packs.size == settings.packsPerPlayer * players.size) { "Wrong pack count" }
        val copies = packs.flatten()
        require(copies.all { it.id.isNotBlank() && it.definition.isNotBlank() }) { "Invalid copies" }
        require(copies.map { it.id }.distinct().size == copies.size) { "Duplicate copy IDs" }
        val words = RandomV1.Words(seed)
        val seating = players.sorted().toMutableList()
        for (i in seating.lastIndex downTo 1) {
            val j = RandomV1.sample(i + 1, words::next)
            val player = seating[i]; seating[i] = seating[j]; seating[j] = player
        }
        val draftPacks = packs.mapIndexed { index, cards ->
            val number = index / seating.size + 1
            DraftPack("pack-$index", (number - 1) / settings.packsOpenAtOnce,
                seating[index % seating.size], number, cards.map { it.id })
        }
        return initial(settings, seating.toList(), copies, draftPacks)
    }

    private fun initial(settings: DraftSettings, seating: List<String>, copies: List<DraftCopy>, packs: List<DraftPack>): DraftState {
        val supplied = seating.associateWith { player -> List(settings.librarians) { index ->
            DraftCopy("librarian:$player:$index", requireNotNull(settings.librarianDefinition).digest, librarian = true)
        } }
        return Working(DraftState(settings, seating, copies + supplied.values.flatten(), packs, -1,
            seating.associateWith { emptyList() }, supplied.mapValues { it.value.map { copy -> copy.id } })).settle()
    }

    fun pick(state: DraftState, pick: DraftPick): DraftPickResult {
        fun reject(reason: DraftPickRejection) = DraftPickResult(reason = reason)
        if (state.finished) return reject(DraftPickRejection.DRAFT_FINISHED)
        val queue = state.queues[pick.player]
        if (queue == null || pick.packId !in queue) return reject(DraftPickRejection.NOT_YOUR_PACK)
        if (queue.first() != pick.packId) return reject(DraftPickRejection.NOT_HEAD_OF_QUEUE)
        val pack = state.packs.first { it.id == pick.packId }
        val owned = state.pools.getValue(pick.player).toSet()
        val librarians = state.copies.filter { it.librarian && it.id in owned }.map { it.id }.toSet()
        if (pick.librarians.distinct().size != pick.librarians.size || pick.librarians.any { it !in librarians })
            return reject(DraftPickRejection.INVALID_LIBRARIANS)
        val keep = state.settings.keep.toLong() + pick.librarians.size
        val selected = pick.keep + pick.burn
        if (selected.any { it !in pack.cards }) return reject(DraftPickRejection.UNKNOWN_CARD)
        if (selected.distinct().size != selected.size || pick.keep.size.toLong() != keep)
            return reject(DraftPickRejection.WRONG_COUNTS)
        val rest = pack.cards.filter { it !in pick.keep }
        val nearEnd = pack.cards.size <= keep + state.settings.burn
        if (nearEnd) {
            if (pick.burn.isNotEmpty() && pick.burn.toSet() != rest.toSet()) return reject(DraftPickRejection.WRONG_COUNTS)
        } else if (pick.burn.size != state.settings.burn) return reject(DraftPickRejection.WRONG_COUNTS)
        val burned = if (nearEnd) rest else pick.burn
        return DraftPickResult(state = Working(state).applyPick(pick, burned))
    }

    fun privateView(state: DraftState, player: String): DraftPrivateView {
        require(player in state.seating) { "Not a draft member" }
        val copies = state.copies.associateBy { it.id }
        val queue = state.queues.getValue(player)
        val packs = state.packs.associateBy { it.id }
        val picks = state.log.filter { !it.automatic && it.packId in queue }.groupBy { it.packId }
        val visible = queue.map { id -> packs.getValue(id).let { pack ->
            val taken = picks[id].orEmpty()
            DraftPrivatePack(pack.id, pack.number, pack.cards.map(copies::getValue), taken.size + 1, taken.lastOrNull()?.player)
        } }
        return DraftPrivateView(player, visible.firstOrNull(),
            (queue.size - 1).coerceAtLeast(0), state.pools.getValue(player).map(copies::getValue), visible.drop(1),
            if (state.finished) state.settings.rounds else (state.openedRound(player) + 1).coerceAtMost(state.settings.rounds),
            state.settings.rounds, if (state.finished) 0 else Working(state).incoming(player))
    }

    fun publicStatus(state: DraftState) = DraftPublicStatus(
        if (state.finished) state.settings.rounds else state.round + 1, state.settings.rounds, state.finished,
        state.seating.map { player -> DraftMemberStatus(player, state.queues.getValue(player).size,
            state.log.count { it.player == player }, state.finished) },
    )

    /** Mutable scratch space is private to one transition; no caller-owned collections are changed. */
    private class Working(private val source: DraftState) {
        private val packs = source.packs.toMutableList()
        private val packIndexes = packs.withIndex().associate { it.value.id to it.index }
        private val queues = source.queues.mapValues { it.value.toMutableList() }
        private val held = source.seating.associateWith { source.held[it].orEmpty().toMutableList() }
        private val pools = source.pools.mapValues { it.value.toMutableList() }
        private val log = source.log.toMutableList()
        private val opened = source.seating.associateWith { source.openedRound(it) }.toMutableMap()
        /** Manual picks taken from each pack; with round, orders a member's queue (fresh packs first). */
        private val picks = source.log.filter { !it.automatic }.groupingBy { it.packId }.eachCount().toMutableMap()

        fun applyPick(pick: DraftPick, burned: List<String>): DraftState {
            val index = packIndexes.getValue(pick.packId)
            val pack = packs[index]
            queues.getValue(pick.player).remove(pick.packId)
            val removed = (pick.keep + burned).toSet()
            val remaining = pack.cards.filter { it !in removed }
            val discard = remaining.isNotEmpty() && remaining.size <= source.settings.discardRemainingAt
            val discarded = if (discard) remaining else emptyList()
            // Spent Librarians re-enter the pack only when, without them, it still travels on for a manual pick. On a
            // pack's final pick (emptied, discarded or left for an automatic award) they burn with the rest instead.
            val returned = !discard && remaining.size > source.settings.keep
            packs[index] = pack.copy(cards = (if (discard) emptyList() else remaining) + (if (returned) pick.librarians else emptyList()))
            pools.getValue(pick.player).removeAll(pick.librarians.toSet())
            pools.getValue(pick.player).addAll(pick.keep)
            log.add(DraftPickLog(pick.player, pack.id, pack.round, pick.keep.toList(),
                burned.toList() + (if (returned) emptyList() else pick.librarians), discarded, librarians = pick.librarians.toList()))
            picks[pack.id] = (picks[pack.id] ?: 0) + 1
            if (packs[index].cards.isNotEmpty()) arrive(index, passTo(pick.player, pack.round))
            return settle()
        }

        private fun passTo(player: String, round: Int): String {
            val seat = source.seating.indexOf(player)
            val step = if (direction(round) == DraftDirection.NEXT_SEAT) 1 else source.seating.size - 1
            return source.seating[(seat + step) % source.seating.size]
        }

        /**
         * Whether a live or unopened pack of rounds up to [upTo] can still reach [player] for a manual pick, assuming
         * every pick removes keep + burn cards (all of them near the end) and applies the discard threshold. Automatic
         * awards are not picks. A Cogwork Librarian spend removes the same net keep + burn while the pack travels on and
         * burns the Librarians on a final pick, so it never lets a pack reach further than predicted.
         */
        private fun canReach(player: String, upTo: Int): Boolean = reaching(player, upTo).any()

        /** Packs outside [player]'s queue that can still come to them for a manual pick (see [canReach]). */
        fun incoming(player: String): Int {
            val queued = queues.getValue(player).toSet()
            return reaching(player, opened.getValue(player)).count { it.id !in queued }
        }

        private fun reaching(player: String, upTo: Int): Sequence<DraftPack> {
            val holders = (queues.entries + held.entries).flatMap { (owner, ids) -> ids.map { it to owner } }.toMap()
            return packs.asSequence().filter { it.cards.isNotEmpty() && it.round <= upTo && reaches(it, holders[it.id], player) }
        }

        private fun reaches(pack: DraftPack, queued: String?, player: String): Boolean {
            val keep = source.settings.keep
            val removed = keep + source.settings.burn
            // Unopened packs that are already small enough are awarded on arrival.
            if (queued == null && pack.cards.size <= keep) return false
            var holder = queued ?: pack.initialPlayer
            var size = pack.cards.size
            while (true) {
                if (holder == player) return true
                size = if (size <= removed) 0 else size - removed
                if (size <= source.settings.discardRemainingAt || size <= keep) return false
                holder = passTo(holder, pack.round)
            }
        }

        /**
         * A visible queue runs by round, then manual picks already taken (fresh packs first), then arrival. An arrival
         * never goes ahead of the head of a non-empty queue unless [protectHead] is false (a member opening a round
         * with an empty queue), since the member may be looking at or marking that head.
         */
        private fun enqueue(player: String, id: String, protectHead: Boolean) {
            val queue = queues.getValue(player)
            fun rank(pack: String) = packs[packIndexes.getValue(pack)].round.toLong() * 1_000_000 + (picks[pack] ?: 0)
            val rank = rank(id)
            val start = if (protectHead && queue.isNotEmpty()) 1 else 0
            val at = (start until queue.size).firstOrNull { rank(queue[it]) > rank } ?: queue.size
            queue.add(at, id)
        }

        private fun arrive(index: Int, player: String, protectHead: Boolean = true) {
            val pack = packs[index]
            // A later-round pack waits unseen until the receiver has no more packs of their own round to pick.
            if (pack.round > opened.getValue(player)) held.getValue(player).add(pack.id)
            // Automatic awards happen on arrival, even behind a pack that still needs a manual choice.
            else if (pack.cards.size <= source.settings.keep) {
                pools.getValue(player).addAll(pack.cards)
                log.add(DraftPickLog(player, pack.id, pack.round, pack.cards, automatic = true))
                packs[index] = pack.copy(cards = emptyList())
            } else enqueue(player, pack.id, protectHead)
        }

        fun settle(): DraftState {
            // Each member opens their own next-round packs once nothing they opened can come back to them for a pick.
            // An opening only adds packs that the other members' checks already counted as unopened, so seat order does
            // not change the result. Rounds emptied by automatic awards cascade.
            val last = source.settings.rounds - 1
            do {
                var changed = false
                for (player in source.seating) {
                    val current = opened.getValue(player)
                    if (current >= last || canReach(player, current)) continue
                    opened[player] = current + 1
                    // Released held packs arrived before the member's own packs open; the queue rule orders them all.
                    // An empty queue has no head to protect, so fresh own packs can come before half-picked held ones.
                    val released = held.getValue(player).filter { id -> packs[packIndexes.getValue(id)].round <= current + 1 }
                    held.getValue(player).removeAll(released.toSet())
                    val protectHead = queues.getValue(player).isNotEmpty()
                    for (id in released) arrive(packIndexes.getValue(id), player, protectHead)
                    for (index in packs.indices) {
                        if (packs[index].round == current + 1 && packs[index].initialPlayer == player) arrive(index, player, protectHead)
                    }
                    changed = true
                }
            } while (changed)
            val finished = opened.values.all { it >= last } && packs.all { it.cards.isEmpty() }
            return source.copy(packs = packs.toList(), round = if (finished) source.settings.rounds else opened.values.min(),
                queues = queues.mapValues { it.value.toList() }, pools = pools.mapValues { it.value.toList() }, log = log.toList(),
                opened = opened.toMap(), held = held.filterValues { it.isNotEmpty() }.mapValues { it.value.toList() })
        }
    }
}

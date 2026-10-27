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

@Serializable data class DraftPrivatePack(val id: String, val number: Int, val cards: List<DraftCopy>)
@Serializable data class DraftPrivateView(
    val player: String,
    val head: DraftPrivatePack?,
    val packsBehind: Int,
    val pool: List<DraftCopy>,
    val queuedPacks: List<DraftPrivatePack> = emptyList(),
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
        val visible = queue.map { id -> packs.getValue(id).let { DraftPrivatePack(it.id, it.number, it.cards.map(copies::getValue)) } }
        return DraftPrivateView(player, visible.firstOrNull(),
            (queue.size - 1).coerceAtLeast(0), state.pools.getValue(player).map(copies::getValue), visible.drop(1))
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
        private val pools = source.pools.mapValues { it.value.toMutableList() }
        private val log = source.log.toMutableList()
        private val opened = source.seating.associateWith { source.openedRound(it) }.toMutableMap()

        fun applyPick(pick: DraftPick, burned: List<String>): DraftState {
            val index = packIndexes.getValue(pick.packId)
            val pack = packs[index]
            queues.getValue(pick.player).removeAt(0)
            val removed = (pick.keep + burned).toSet()
            val remaining = pack.cards.filter { it !in removed }
            val discard = remaining.isNotEmpty() && remaining.size <= source.settings.discardRemainingAt
            val discarded = if (discard) remaining else emptyList()
            // Apply keeps, burns and the ordinary discard threshold before returning Librarians.
            packs[index] = pack.copy(cards = (if (discard) emptyList() else remaining) + pick.librarians)
            pools.getValue(pick.player).removeAll(pick.librarians.toSet())
            pools.getValue(pick.player).addAll(pick.keep)
            log.add(DraftPickLog(pick.player, pack.id, pack.round, pick.keep.toList(), burned.toList(), discarded, librarians = pick.librarians.toList()))
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
         * awards are not picks. A near-end Cogwork Librarian spend can return cards to a pack that would have emptied;
         * such a pack simply keeps passing and queues behind whatever the receiver already has.
         */
        private fun canReach(player: String, upTo: Int): Boolean {
            val keep = source.settings.keep
            val removed = keep + source.settings.burn
            val holders = queues.entries.flatMap { (owner, ids) -> ids.map { it to owner } }.toMap()
            for (pack in packs) {
                if (pack.cards.isEmpty() || pack.round > upTo) continue
                val queued = holders[pack.id]
                // Unopened packs that are already small enough are awarded on arrival.
                if (queued == null && pack.cards.size <= keep) continue
                var holder = queued ?: pack.initialPlayer
                var size = pack.cards.size
                while (true) {
                    if (holder == player) return true
                    size = if (size <= removed) 0 else size - removed
                    if (size <= source.settings.discardRemainingAt || size <= keep) break
                    holder = passTo(holder, pack.round)
                }
            }
            return false
        }

        private fun arrive(index: Int, player: String) {
            val pack = packs[index]
            // Automatic awards happen on arrival, even behind a pack that still needs a manual choice.
            if (pack.cards.size <= source.settings.keep) {
                pools.getValue(player).addAll(pack.cards)
                log.add(DraftPickLog(player, pack.id, pack.round, pack.cards, automatic = true))
                packs[index] = pack.copy(cards = emptyList())
            } else queues.getValue(player).add(pack.id)
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
                    for (index in packs.indices) {
                        if (packs[index].round == current + 1 && packs[index].initialPlayer == player) arrive(index, player)
                    }
                    changed = true
                }
            } while (changed)
            val finished = opened.values.all { it >= last } && packs.all { it.cards.isEmpty() }
            return source.copy(packs = packs.toList(), round = if (finished) source.settings.rounds else opened.values.min(),
                queues = queues.mapValues { it.value.toList() }, pools = pools.mapValues { it.value.toList() }, log = log.toList(),
                opened = opened.toMap())
        }
    }
}

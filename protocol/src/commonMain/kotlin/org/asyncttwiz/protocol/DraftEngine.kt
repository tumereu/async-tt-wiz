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
        // Spent Librarians join the pack for this pick as ordinary cards: each may be kept back, burned or passed on.
        val cards = pack.cards + pick.librarians
        val keep = state.settings.keep.toLong() + pick.librarians.size
        val selected = pick.keep + pick.burn
        if (selected.any { it !in cards }) return reject(DraftPickRejection.UNKNOWN_CARD)
        if (selected.distinct().size != selected.size || pick.keep.size.toLong() != keep)
            return reject(DraftPickRejection.WRONG_COUNTS)
        val rest = cards.filter { it !in pick.keep }
        // Equivalent to pack.cards.size <= settings.keep + burn: a spend never changes whether a pick is near the end.
        val nearEnd = cards.size <= keep + state.settings.burn
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

    /** Pack rules on the shared [DraftSchedule]; mutable scratch space is private to one transition. */
    private class Working(private val source: DraftState) : DraftSchedule(source.seating, source.settings.rounds,
        source.queues, source.held, source.seating.associateWith { source.openedRound(it) }) {
        private val packs = source.packs.toMutableList()
        private val packIndexes = packs.withIndex().associate { it.value.id to it.index }
        private val pools = source.pools.mapValues { it.value.toMutableList() }
        private val log = source.log.toMutableList()
        /** Manual picks taken from each pack; with round, orders a member's queue (fresh packs first). */
        private val picks = source.log.filter { !it.automatic }.groupingBy { it.packId }.eachCount().toMutableMap()

        private fun pack(id: String) = packs[packIndexes.getValue(id)]
        override val items: List<String> = packs.map { it.id }
        override fun round(item: String) = pack(item).round
        override fun opener(item: String) = pack(item).initialPlayer
        override fun live(item: String) = pack(item).cards.isNotEmpty()
        override fun picks(item: String) = picks[item] ?: 0

        fun applyPick(pick: DraftPick, burned: List<String>): DraftState {
            val index = packIndexes.getValue(pick.packId)
            val pack = packs[index]
            take(pick.player, pick.packId)
            val removed = (pick.keep + burned).toSet()
            // A spent Librarian that was neither kept back nor burned stays in the pack like any leftover card: it passes
            // on, is discarded at the threshold, or goes to the next member with an automatic award.
            val remaining = (pack.cards + pick.librarians).filter { it !in removed }
            val discard = remaining.isNotEmpty() && remaining.size <= source.settings.discardRemainingAt
            val discarded = if (discard) remaining else emptyList()
            packs[index] = pack.copy(cards = if (discard) emptyList() else remaining)
            pools.getValue(pick.player).removeAll(pick.librarians.toSet())
            pools.getValue(pick.player).addAll(pick.keep)
            log.add(DraftPickLog(pick.player, pack.id, pack.round, pick.keep.toList(), burned.toList(), discarded,
                librarians = pick.librarians.toList()))
            picks[pack.id] = (picks[pack.id] ?: 0) + 1
            if (packs[index].cards.isNotEmpty()) arrive(pack.id, passTo(pick.player, pack.round))
            return settle()
        }

        private fun passTo(player: String, round: Int): String {
            val seat = seating.indexOf(player)
            val step = if (direction(round) == DraftDirection.NEXT_SEAT) 1 else seating.size - 1
            return seating[(seat + step) % seating.size]
        }

        /**
         * Whether the pack can still reach [player] for a manual pick, assuming every pick removes keep + burn cards
         * (all of them near the end) and applies the discard threshold. Automatic awards are not picks. A Cogwork
         * Librarian spend adds L cards to the pack and L keep slots, so the pick removes the same net keep + burn and is
         * near the end exactly when it would be without the spend: a pack never reaches further than predicted.
         */
        override fun reaches(item: String, holder: String?, player: String): Boolean {
            val pack = pack(item)
            val keep = source.settings.keep
            val removed = keep + source.settings.burn
            // Unopened packs that are already small enough are awarded on arrival.
            if (holder == null && pack.cards.size <= keep) return false
            var current = holder ?: pack.initialPlayer
            var size = pack.cards.size
            while (true) {
                if (current == player) return true
                size = if (size <= removed) 0 else size - removed
                if (size <= source.settings.discardRemainingAt || size <= keep) return false
                current = passTo(current, pack.round)
            }
        }

        /** Automatic awards happen on arrival, even behind a pack that still needs a manual choice. */
        override fun automatic(item: String, player: String): Boolean {
            val index = packIndexes.getValue(item)
            val pack = packs[index]
            if (pack.cards.size > source.settings.keep) return false
            pools.getValue(player).addAll(pack.cards)
            log.add(DraftPickLog(player, pack.id, pack.round, pack.cards, automatic = true))
            packs[index] = pack.copy(cards = emptyList())
            return true
        }

        fun settle(): DraftState {
            open()
            return source.copy(packs = packs.toList(), round = round, queues = queueSnapshot(),
                pools = pools.mapValues { it.value.toList() }, log = log.toList(), opened = openedRounds(), held = heldSnapshot())
        }
    }
}

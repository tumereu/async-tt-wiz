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
    /** Zero-based; equals settings.rounds after completion. */
    val round: Int,
    val queues: Map<String, List<String>>,
    val pools: Map<String, List<String>>,
    val log: List<DraftPickLog> = emptyList(),
) {
    val finished: Boolean get() = round == settings.rounds
    val direction: DraftDirection get() = if (round % 2 == 0) DraftDirection.NEXT_SEAT else DraftDirection.PREVIOUS_SEAT
}

@Serializable data class DraftPack(
    val id: String,
    val round: Int,
    val initialPlayer: String,
    /** One-based pack number for the opening player. */
    val number: Int,
    val cards: List<String>,
)

@Serializable data class DraftPick(val player: String, val packId: String, val keep: List<String>, val burn: List<String> = emptyList())
@Serializable data class DraftPickLog(
    val player: String,
    val packId: String,
    val round: Int,
    val kept: List<String>,
    val burned: List<String> = emptyList(),
    val discarded: List<String> = emptyList(),
    val automatic: Boolean = false,
)
@Serializable enum class DraftPickRejection { NOT_YOUR_PACK, NOT_HEAD_OF_QUEUE, WRONG_COUNTS, UNKNOWN_CARD, DRAFT_FINISHED }
data class DraftPickResult(val state: DraftState? = null, val reason: DraftPickRejection? = null)

@Serializable data class DraftPrivatePack(val id: String, val number: Int, val cards: List<DraftCopy>)
@Serializable data class DraftPrivateView(
    val player: String,
    val head: DraftPrivatePack?,
    val packsBehind: Int,
    val pool: List<DraftCopy>,
)
/** No card/pack identities or log. Picks include automatic awards; waiting is not finished until the draft ends. */
@Serializable data class DraftMemberStatus(val player: String, val packsWaiting: Int, val picksMade: Int, val finished: Boolean)
@Serializable data class DraftPublicStatus(val round: Int, val rounds: Int, val finished: Boolean, val members: List<DraftMemberStatus>)

object DraftEngine {
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
                DraftCopy("$definition:copy:$index", definition, entries.first().colors.sortedBy { it.ordinal }.toSet())
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
        return Working(DraftState(settings, seating, copies, packs, -1,
            seating.associateWith { emptyList() }, seating.associateWith { emptyList() })).settle()
    }

    fun pick(state: DraftState, pick: DraftPick): DraftPickResult {
        fun reject(reason: DraftPickRejection) = DraftPickResult(reason = reason)
        if (state.finished) return reject(DraftPickRejection.DRAFT_FINISHED)
        val queue = state.queues[pick.player]
        if (queue == null || pick.packId !in queue) return reject(DraftPickRejection.NOT_YOUR_PACK)
        if (queue.first() != pick.packId) return reject(DraftPickRejection.NOT_HEAD_OF_QUEUE)
        val pack = state.packs.first { it.id == pick.packId }
        val selected = pick.keep + pick.burn
        if (selected.any { it !in pack.cards }) return reject(DraftPickRejection.UNKNOWN_CARD)
        if (selected.distinct().size != selected.size || pick.keep.size != state.settings.keep)
            return reject(DraftPickRejection.WRONG_COUNTS)
        val rest = pack.cards.filter { it !in pick.keep }
        val nearEnd = pack.cards.size <= state.settings.keep + state.settings.burn
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
        val head = queue.firstOrNull()?.let { id -> state.packs.first { it.id == id } }
        return DraftPrivateView(player, head?.let { DraftPrivatePack(it.id, it.number, it.cards.map(copies::getValue)) },
            (queue.size - 1).coerceAtLeast(0), state.pools.getValue(player).map(copies::getValue))
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
        private var round = source.round

        fun applyPick(pick: DraftPick, burned: List<String>): DraftState {
            val index = packIndexes.getValue(pick.packId)
            val pack = packs[index]
            queues.getValue(pick.player).removeAt(0)
            val removed = (pick.keep + burned).toSet()
            val remaining = pack.cards.filter { it !in removed }
            val discard = remaining.isNotEmpty() && remaining.size <= source.settings.discardRemainingAt
            val discarded = if (discard) remaining else emptyList()
            packs[index] = pack.copy(cards = if (discard) emptyList() else remaining)
            pools.getValue(pick.player).addAll(pick.keep)
            log.add(DraftPickLog(pick.player, pack.id, round, pick.keep.toList(), burned.toList(), discarded))
            if (packs[index].cards.isNotEmpty()) {
                val seat = source.seating.indexOf(pick.player)
                val step = if (round % 2 == 0) 1 else source.seating.size - 1
                arrive(index, source.seating[(seat + step) % source.seating.size])
            }
            return settle()
        }

        private fun arrive(index: Int, player: String) {
            val pack = packs[index]
            // Automatic awards happen on arrival, even behind a pack that still needs a manual choice.
            if (pack.cards.size <= source.settings.keep) {
                pools.getValue(player).addAll(pack.cards)
                log.add(DraftPickLog(player, pack.id, round, pack.cards, automatic = true))
                packs[index] = pack.copy(cards = emptyList())
            } else queues.getValue(player).add(pack.id)
        }

        fun settle(): DraftState {
            // Empty rounds (all automatic awards) can cascade, but never cross a live-round barrier.
            while (queues.values.all { it.isEmpty() } && round < source.settings.rounds) {
                round++
                if (round < source.settings.rounds) {
                    for (index in packs.indices) {
                        if (packs[index].round == round) arrive(index, packs[index].initialPlayer)
                    }
                }
            }
            return source.copy(packs = packs.toList(), round = round,
                queues = queues.mapValues { it.value.toList() }, pools = pools.mapValues { it.value.toList() }, log = log.toList())
        }
    }
}

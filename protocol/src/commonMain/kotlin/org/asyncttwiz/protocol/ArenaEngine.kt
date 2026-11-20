package org.asyncttwiz.protocol

import kotlinx.serialization.Serializable

/**
 * One card Arena offers are drawn from: a cube copy (one printing), or a set's card with every printing of it in the
 * set (one is chosen at random when it is offered). [rarity] is one of common, uncommon, rare and mythic ([ArenaEngine.rarity]);
 * [set] is the set's code ("" for a cube). [bonus]: a set's special or bonus card, in its [rarity]'s bonus bag (DRF-291).
 */
@Serializable data class ArenaCard(val id: String, val printings: List<String>, val colors: Set<DraftColor> = emptySet(),
    val rarity: SetCardRarity = SetCardRarity.COMMON, val set: String = "", val bonus: Boolean = false)

/** An offer that had too few cards of its rarity (DRF-285): [count] of its cards came from [used] in place of [wanted]. */
@Serializable data class ArenaFill(val player: String, val pick: Int, val wanted: SetCardRarity, val used: SetCardRarity, val count: Int)

/**
 * Arena's part of a [DraftState] (server-only): what each player's offers are drawn from. Each player's offers come
 * only from [seed], their own picks and the source: no other player's pick changes them (owner, 2026-11-20).
 */
@Serializable data class ArenaState(
    val seed: String,
    /** The source is a cube (Ignore rarity and Reuse cards apply, DRF-283, DRF-284), not sets. */
    val cube: Boolean,
    val cards: List<ArenaCard>,
    /** A set source's set of each pick, by its pack grid (DRF-286); empty for a cube. */
    val pickSets: List<String> = emptyList(),
    /** A cube without Reuse cards: the cards each player has picked, never offered to that player again (DRF-284). */
    val picked: Map<String, List<String>> = emptyMap(),
    /** The open offers' copies: copy id to its [ArenaCard.id]. */
    val drawn: Map<String, String> = emptyMap(),
    /** Every offer filled up from another rarity so far (DRF-285), for the server's log. */
    val fills: List<ArenaFill> = emptyList(),
)

/** The cards of each rarity an Arena source holds (DRF-285): a cube's copies, a set's cards (the fewest of the pod's sets). */
@Serializable data class ArenaCounts(val common: Int = 0, val uncommon: Int = 0, val rare: Int = 0, val mythic: Int = 0) {
    fun of(rarity: SetCardRarity): Int = when (rarity) {
        SetCardRarity.UNCOMMON -> uncommon; SetCardRarity.RARE -> rare; SetCardRarity.MYTHIC -> mythic; else -> common
    }
    val total: Int get() = common + uncommon + rare + mythic
}

/**
 * Arena (DRF-280): each player drafts alone, at their own pace. Every pick offers them [DraftSettings.cardsPerPack]
 * cards of the pick's rarity ([pickRarity]); they take [DraftSettings.keep] and the rest of the offer is gone. Nothing
 * passes between players. The state is a [DraftState]: each offer is a pack of its player's, its round the pick's index,
 * so the pick screen, marks and draft history of Booster work unchanged.
 */
object ArenaEngine {
    /** The most picks, and the longest rarity interval (DRF-281, owner 2026-11-20). */
    const val MAX_PICKS = 120
    const val MAX_OFFER = 15
    const val MAX_MYTHIC_ONE_IN = 100
    private val ORDER = listOf(SetCardRarity.COMMON, SetCardRarity.UNCOMMON, SetCardRarity.RARE, SetCardRarity.MYTHIC)
    private val BASICS = BasicLand.entries.map { it.cardName }.toSet() + "Wastes"
    /**
     * DRF-291 (owner, 2026-11-20): the most a card drawn for a rarity comes from that rarity's bonus bag, in percent; the
     * chance is this times the bag's cards over the normal cards of the rarity, at most once over.
     */
    const val BONUS_PERCENT = 20

    /** Whether [value] is a special or bonus rarity (bonus-sheet cards, DRF-291). */
    fun special(value: SetCardRarity) = value == SetCardRarity.SPECIAL || value == SetCardRarity.BONUS

    /**
     * Arena's four rarities: an unknown rarity counts as common; a special or bonus card (DRF-291) as [tier], the lowest
     * rarity of its other printings, or rare when it has none.
     */
    fun rarity(value: SetCardRarity, tier: SetCardRarity? = null): SetCardRarity = when {
        special(value) -> tier?.takeIf { !special(it) }?.let { rarity(it) } ?: SetCardRarity.RARE
        value == SetCardRarity.UNKNOWN -> SetCardRarity.COMMON
        else -> value
    }

    /** DRF-291: the lowest of common, uncommon, rare and mythic among [rarities] (a card's other printings), the tier its
     * special and bonus printings count as ([rarity]); null without any. */
    fun tier(rarities: Collection<SetCardRarity>): SetCardRarity? = rarities.filter { it in ORDER }.minByOrNull { ORDER.indexOf(it) }

    /** DRF-281: the rarity of pick [pick] (one-based): rare every [DraftSettings.rareEvery]th, uncommon every
     * [DraftSettings.uncommonEvery]th (rare wins), common otherwise. */
    fun pickRarity(settings: DraftSettings, pick: Int): SetCardRarity = when {
        settings.rareEvery?.let { pick % it == 0 } == true -> SetCardRarity.RARE
        settings.uncommonEvery?.let { pick % it == 0 } == true -> SetCardRarity.UNCOMMON
        else -> SetCardRarity.COMMON
    }

    /** How many of [settings]' picks are of each rarity. */
    fun schedule(settings: DraftSettings): Map<SetCardRarity, Int> =
        (1..settings.packsPerPlayer.coerceAtLeast(0)).groupingBy { pickRarity(settings, it) }.eachCount()

    /**
     * Cards a cube must hold for one player (DRF-284): with Reuse cards an offer's worth; without, every card the player
     * picks before the last offer plus that offer. Each player draws on their own, so the players don't add up.
     */
    fun needed(settings: DraftSettings): Long = if (settings.reuseCards) settings.cardsPerPack.toLong()
        else (settings.packsPerPlayer - 1).coerceAtLeast(0).toLong() * settings.keep + settings.cardsPerPack

    /** A cube's copies as Arena cards, each with its card's [rarity] (by its definition; a special or bonus printing's tier,
     * DRF-291, is the caller's to give: a cube has no bonus bag). */
    fun cubeCards(cube: List<DraftCubeEntry>, rarity: (String) -> SetCardRarity): List<ArenaCard> =
        cube.groupBy { it.definition }.entries.sortedBy { it.key }.flatMap { (definition, entries) ->
            val quantity = entries.sumOf { it.quantity }
            val colors = entries.first().colors.sortedBy { it.ordinal }.toSet()
            List(quantity) { ArenaCard("$definition:copy:$it", listOf(definition), colors, rarity(rarity(definition))) }
        }

    /**
     * Each set's cards as Arena cards (DRF-286): a card once however many printings it has, its rarity the lowest of
     * its printings'. Basic lands are left out, and so are printings [own] says are not the set's own (cards of other sets
     * its booster recipe names). A card all of whose printings in the set are special or bonus is a bonus card (DRF-291):
     * its rarity is [tier] (the lowest rarity of its printings elsewhere, by its name), or rare without one.
     */
    fun setCards(sets: List<SetSourceSnapshot>, own: (set: String, uuid: String) -> Boolean = { _, _ -> true },
        tier: (card: String) -> SetCardRarity? = { null }): List<ArenaCard> =
        sets.flatMap { set ->
            set.cards.filter { own(set.setCode, it.uuid) && !basic(it.card) }.sortedBy { it.uuid }
                .groupBy { it.card.ifEmpty { it.uuid } }.entries.sortedBy { it.key }.map { (card, printings) ->
                    val normal = printings.filter { !special(it.rarity) }
                    val bonus = normal.isEmpty()
                    val shown = if (bonus) printings else normal
                    ArenaCard("${set.setCode}:$card", shown.map { it.definition }.distinct(),
                        shown.flatMap { it.colors }.sortedBy { it.ordinal }.toSet(),
                        if (bonus) rarity(SetCardRarity.SPECIAL, tier(card)) else ArenaEngine.tier(normal.map { rarity(it.rarity) }) ?: SetCardRarity.COMMON,
                        set.setCode, bonus)
                }
        }

    private fun basic(name: String) = name in BASICS || name.removePrefix("Snow-Covered ") in BASICS

    /** The cards of each rarity in [cards] (DRF-285); several sets give each rarity's fewest of any one set. */
    fun counts(cards: List<ArenaCard>): ArenaCounts {
        val bySet = cards.groupBy { it.set }.values.map { set -> set.groupingBy { it.rarity }.eachCount() }
        fun count(rarity: SetCardRarity) = bySet.minOfOrNull { it[rarity] ?: 0 } ?: 0
        return ArenaCounts(count(SetCardRarity.COMMON), count(SetCardRarity.UNCOMMON), count(SetCardRarity.RARE), count(SetCardRarity.MYTHIC))
    }

    /**
     * The draft's start: random seating and every player's first offer. [pickSets]: a set source's set of each pick
     * ([PodPackGrid.packSets]), empty for a cube ([cube]).
     */
    fun start(players: List<String>, cards: List<ArenaCard>, settings: DraftSettings, seed: String, cube: Boolean,
        pickSets: List<String> = emptyList()): DraftState {
        require(settings.mode == DraftMode.ARENA && settings.validate(players.size).isEmpty()) { "Invalid Arena settings" }
        require(players.all { it.isNotBlank() } && players.distinct().size == players.size) { "Invalid members" }
        require(cards.isNotEmpty() && cards.all { it.printings.isNotEmpty() } && cards.map { it.id }.distinct().size == cards.size) { "Invalid cards" }
        require(!cube || cards.size >= needed(settings)) { "Insufficient cube supply" }
        require(cube || pickSets.size == settings.packsPerPlayer) { "A set for every pick" }
        val words = RandomV1.Words(seed)
        val seating = players.sorted().toMutableList()
        for (i in seating.lastIndex downTo 1) {
            val j = RandomV1.sample(i + 1, words::next)
            val player = seating[i]; seating[i] = seating[j]; seating[j] = player
        }
        val empty = seating.associateWith { emptyList<String>() }
        var state = DraftState(settings, seating.toList(), emptyList(), emptyList(), 0, empty, empty,
            opened = seating.associateWith { 0 }, arena = ArenaState(seed, cube, cards, if (cube) emptyList() else pickSets))
        for (player in seating) state = offer(state, player, 0)
        return state
    }

    /** [player] takes [DraftPick.keep] of their offer (DRF-280): exactly the pick's count, no burns, no Librarians. */
    fun pick(state: DraftState, pick: DraftPick): DraftPickResult {
        fun reject(reason: DraftPickRejection) = DraftPickResult(reason = reason)
        val arena = requireNotNull(state.arena) { "Not an Arena draft" }
        if (state.finished) return reject(DraftPickRejection.DRAFT_FINISHED)
        val queue = state.queues[pick.player]
        if (queue == null || pick.packId !in queue) return reject(DraftPickRejection.NOT_YOUR_PACK)
        if (pick.librarians.isNotEmpty()) return reject(DraftPickRejection.INVALID_LIBRARIANS)
        val pack = state.packs.first { it.id == pick.packId }
        if (pick.keep.any { it !in pack.cards } || pick.burn.any { it !in pack.cards }) return reject(DraftPickRejection.UNKNOWN_CARD)
        if (pick.burn.isNotEmpty() || pick.keep.distinct().size != pick.keep.size ||
            pick.keep.size != minOf(state.settings.keep, pack.cards.size)) return reject(DraftPickRejection.WRONG_COUNTS)
        val player = pick.player
        val rest = pack.cards.filter { it !in pick.keep }
        val reuse = !arena.cube || state.settings.reuseCards
        val picked = if (reuse) arena.picked
            else arena.picked + (player to arena.picked[player].orEmpty() + pick.keep.map { arena.drawn.getValue(it) })
        val next = state.copy(
            packs = state.packs.map { if (it.id == pack.id) it.copy(cards = emptyList()) else it },
            queues = state.queues + (player to emptyList()),
            pools = state.pools + (player to state.pools.getValue(player) + pick.keep),
            log = state.log + DraftPickLog(player, pack.id, pack.round, pick.keep, discarded = rest),
            opened = state.opened + (player to pack.round + 1),
            arena = arena.copy(picked = picked, drawn = arena.drawn - pack.cards.toSet()),
        )
        return DraftPickResult(state = settle(offer(next, player, pack.round + 1)))
    }

    /** The lowest pick any player is on: the draft is finished when everyone has made every pick. */
    private fun settle(state: DraftState): DraftState = state.copy(round = state.seating.minOf { state.openedRound(it) })

    /** Picks [player] has left (DRF-074): every one of them can be made without waiting for anybody. */
    fun consecutivePicks(state: DraftState, player: String): Int {
        if (state.finished || state.queues[player].isNullOrEmpty()) return 0
        return state.settings.packsPerPlayer - state.openedRound(player)
    }

    /** The random words of [player]'s offer at pick index [index], from the draft's seed alone. */
    private fun words(seed: String, player: String, index: Int): RandomV1.Words {
        val digest = sha256("async-tt-wiz/arena/v1\u0000".encodeToByteArray() + RandomV1.decodeSeed(seed) +
            "$player\u0000$index".encodeToByteArray())
        return RandomV1.Words(RandomV1.encodeSeed(ByteArray(32) { digest.substring(it * 2, it * 2 + 2).toInt(16).toByte() }))
    }

    /**
     * [player]'s offer for pick index [index] (none past the last pick): [DraftSettings.cardsPerPack] different cards of
     * the pick's rarity (DRF-281), each card of a rare pick a mythic rare with chance 1 in [DraftSettings.mythicOneIn]
     * (DRF-282); a rarity running out fills up from the nearest rarity below, then above (DRF-285). A cube with Ignore
     * rarity draws from all of it (DRF-283); without Reuse cards, never a card this player picked (DRF-284).
     */
    private fun offer(state: DraftState, player: String, index: Int): DraftState {
        val settings = state.settings
        if (index >= settings.packsPerPlayer) return settle(state)
        val arena = requireNotNull(state.arena) { "Not an Arena draft" }
        val words = words(arena.seed, player, index)
        val excluded = if (arena.cube && !settings.reuseCards) arena.picked[player].orEmpty().toSet() else emptySet()
        val set = arena.pickSets.getOrNull(index)
        val pool = arena.cards.filter { (set == null || it.set == set) && it.id !in excluded }
        val chosen = mutableListOf<ArenaCard>()
        val fills = mutableListOf<ArenaFill>()
        fun draw(from: List<ArenaCard>): ArenaCard? {
            val free = from.filter { card -> chosen.none { it.id == card.id } }
            return if (free.isEmpty()) null else free[RandomV1.sample(free.size, words::next)]
        }
        if (arena.cube && settings.ignoreRarity) repeat(settings.cardsPerPack) { draw(pool)?.let(chosen::add) }
        else {
            val base = pickRarity(settings, index + 1)
            val buckets = pool.filter { !it.bonus }.groupBy { it.rarity }
            val bags = pool.filter { it.bonus }.groupBy { it.rarity }
            // DRF-291: a card drawn for a rarity comes from its bonus bag with chance BONUS_PERCENT × min(1, bag / normal cards)
            fun drawTier(tier: SetCardRarity): ArenaCard? {
                val normal = buckets[tier].orEmpty().filter { card -> chosen.none { it.id == card.id } }
                val bag = bags[tier].orEmpty().filter { card -> chosen.none { it.id == card.id } }
                if (bag.isEmpty()) return draw(normal)
                if (normal.isEmpty()) return draw(bag)
                val bagSize = bags.getValue(tier).size.toLong(); val normalSize = buckets.getValue(tier).size.toLong()
                val fromBag = RandomV1.sample(100L * normalSize, words::next) < BONUS_PERCENT * minOf(bagSize, normalSize)
                return draw(if (fromBag) bag else normal)
            }
            val wanted = List(settings.cardsPerPack) {
                val mythic = base == SetCardRarity.RARE && settings.mythicOneIn?.let { n -> RandomV1.sample(n, words::next) == 0 } == true
                if (mythic) SetCardRarity.MYTHIC else base
            }
            for (rarity in wanted) {
                val at = ORDER.indexOf(rarity)
                // The rarity itself, then the nearest below, then above
                val tries = listOf(rarity) + ORDER.subList(0, at).reversed() + ORDER.subList(at + 1, ORDER.size)
                for (used in tries) {
                    val card = drawTier(used) ?: continue
                    chosen += card
                    if (used != rarity) {
                        val last = fills.lastOrNull()
                        if (last != null && last.wanted == rarity && last.used == used) fills[fills.lastIndex] = last.copy(count = last.count + 1)
                        else fills += ArenaFill(player, index + 1, rarity, used, 1)
                    }
                    break
                }
            }
        }
        if (chosen.isEmpty()) {
            // A source without a card left for this player ends their draft here; the server logs it.
            return settle(state.copy(opened = state.opened + (player to settings.packsPerPlayer), arena = arena.copy(
                fills = arena.fills + ArenaFill(player, index + 1, pickRarity(settings, index + 1), pickRarity(settings, index + 1), 0))))
        }
        val seat = state.seating.indexOf(player)
        val copies = chosen.mapIndexed { slot, card ->
            val printing = if (card.printings.size == 1) card.printings.single() else card.printings[RandomV1.sample(card.printings.size, words::next)]
            DraftCopy("offer-$seat-$index:copy:$slot", printing, card.colors)
        }
        val pack = DraftPack("offer-$seat-$index", index, player, index + 1, copies.map { it.id })
        return state.copy(copies = state.copies + copies, packs = state.packs + pack, queues = state.queues + (player to listOf(pack.id)),
            opened = state.opened + (player to index),
            arena = arena.copy(drawn = arena.drawn + copies.zip(chosen).associate { (copy, card) -> copy.id to card.id },
                fills = arena.fills + fills))
    }
}

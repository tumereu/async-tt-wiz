package arcanum.engine.casting

import arcanum.api.CardType
import arcanum.api.Cost
import arcanum.api.CostChange
import arcanum.api.CostObjects
import arcanum.api.CostPart
import arcanum.api.ManaCost
import arcanum.api.ManaSymbol
import arcanum.api.Mechanic
import arcanum.api.Zone
import arcanum.engine.query.Evaluator
import arcanum.engine.state.ObjectId
import arcanum.engine.state.PlayerId

/** One part of a total cost with a stable [key]: where its choices are kept while casting. */
internal data class KeyedPart(val key: String, val part: CostPart)

/**
 * The total cost of a spell or ability (CR 601.2f, 118): its mana, with X replaced and cost changes applied (CR
 * 118.7), and its other parts in order.
 */
internal data class TotalCost(
    val mana: ManaCost, val parts: List<KeyedPart>,
    // tla
    /** The generic mana of its waterbend costs, which untapped artifacts and creatures may pay (CR 701.67a, b). */
    val waterbend: Int = 0,
    /** Whether it has a waterbend cost: paying it is waterbending (CR 701.67c). */
    val waterbends: Boolean = false,
) {
    val manaValue: Int get() = mana.symbols.sumOf { (it as? ManaSymbol.Generic)?.amount ?: 1 }
    val hasMana: Boolean get() = mana.symbols.any { !(it is ManaSymbol.Generic && it.amount == 0) }
}

/** Builds total costs from their contributions and applies cost changes (CR 601.2f, 118.7). */
internal object Costs {
    /** Cost contributions in order: the base (mana cost, an alternative cost or none), additional costs, chosen optional
     * costs (each repeated its times), mode costs; each with a key prefix. */
    data class Contribution(val key: String, val cost: Cost, val times: Int = 1)

    /**
     * The total: mana parts merged ({X} replaced by [x], `times` and `x` values of mana parts evaluated, "its mana cost"
     * read from [itsManaCost]), [oneOf] choices resolved (the chosen option's parts replace the choice), then
     * [increases] added and [reductions] taken off (CR 601.2f: increases first, reductions last, 118.7).
     */
    fun total(
        contributions: List<Contribution>, eval: Evaluator, x: Map<Char, Int>, itsManaCost: ManaCost?,
        oneOf: (String) -> Int?, increases: List<ManaCost> = emptyList(), reductions: List<Pair<ManaCost, Int>> = emptyList(),
    ): TotalCost {
        val symbols = mutableListOf<ManaSymbol>()
        val parts = mutableListOf<KeyedPart>()
        var waterbend = 0
        var waterbends = false
        fun add(key: String, part: CostPart) {
            when (part) {
                // tla: CR 701.67a: its mana is part of the total; its generic mana may be paid by tapping (701.67b)
                is CostPart.Waterbend -> {
                    val partX = part.x?.let { mapOf('X' to eval.value(it)) } ?: x
                    val mana = withX(part.cost, partX).symbols
                    symbols += mana
                    waterbend += mana.sumOf { (it as? ManaSymbol.Generic)?.amount ?: 0 }
                    waterbends = true
                }
                is CostPart.Mana -> {
                    val times = part.times?.let { eval.value(it) } ?: 1
                    val partX = part.x?.let { mapOf('X' to eval.value(it)) } ?: x
                    repeat(times.coerceAtLeast(0)) { symbols += withX(part.cost, partX).symbols }
                }
                CostPart.ItsManaCost -> itsManaCost?.let { symbols += withX(it, x).symbols }
                is CostPart.OneOf -> {
                    val chosen = oneOf(key)
                    if (chosen == null) parts += KeyedPart(key, part)
                    else part.options.getOrNull(chosen)?.parts?.forEachIndexed { j, p -> add("$key.o$chosen.$j", p) }
                }
                // blb: forage is "A or B" (CR 701.61a); its parts' keys carry FORAGE so paying it can be told
                is CostPart.Action -> asCostPart(part).let { p -> if (p is CostPart.OneOf) add(key + FORAGE, p) else parts += KeyedPart(key, p) }
                else -> parts += KeyedPart(key, part)
            }
        }
        contributions.forEach { c -> repeat(c.times) { t -> c.cost.parts.forEachIndexed { j, p -> add("${c.key}.$t.$j", p) } } }
        increases.forEach { symbols += it.symbols }
        var mana = normalize(symbols)
        reductions.forEach { (r, minimum) -> mana = reduce(mana, r, minimum) }
        val generic = mana.symbols.sumOf { (it as? ManaSymbol.Generic)?.amount ?: 0 }
        return TotalCost(mana, parts, waterbend = minOf(waterbend, generic), waterbends = waterbends)
    }

    /** A keyword action as a cost, as the cost part it is: collecting evidence N is exiling any number of cards from
     * your graveyard with total mana value N or greater (CR 701.59a). Others stay as they are (not supported yet). */
    fun asCostPart(part: CostPart.Action): CostPart = when (val action = part.action) {
        is arcanum.api.KeywordAction.CollectEvidence -> CostPart.Exile(
            CostObjects.Choose(arcanum.api.ObjectFilter.Any, count = null, total = arcanum.api.Total(arcanum.api.NumericProperty.ManaValue, action.amount)),
            Zone.Graveyard, postBind = part.bind)
        // blb: CR 701.61a: "Exile three cards from your graveyard or sacrifice a Food."
        arcanum.api.KeywordAction.Forage -> forage
        else -> part
    }

    /** blb: what foraging is (CR 701.61a), as a cost. */
    val forage: CostPart.OneOf = CostPart.OneOf(listOf(
        Cost(listOf(CostPart.Exile(CostObjects.Choose(arcanum.api.ObjectFilter.Any, count = arcanum.api.Value.Fixed(3)), Zone.Graveyard))),
        Cost(listOf(CostPart.Sacrifice(CostObjects.Choose(arcanum.api.ObjectFilter.Sub(arcanum.api.Subtype("Food")), count = arcanum.api.Value.Fixed(1))))),
    ))

    /** blb: marks the keys of a forage's parts. */
    const val FORAGE = ".forage"

    /** {X} replaced by its value; X of 0 adds nothing (CR 107.3). */
    fun withX(cost: ManaCost, x: Map<Char, Int>): ManaCost = ManaCost(cost.symbols.flatMap { s ->
        if (s is ManaSymbol.Variable) {
            val v = x[s.name] ?: 0
            if (v > 0) listOf(ManaSymbol.Generic(v)) else emptyList()
        } else listOf(s)
    })

    /** Generic mana merged into one symbol, first; the rest in order. */
    fun normalize(symbols: List<ManaSymbol>): ManaCost {
        val generic = symbols.sumOf { (it as? ManaSymbol.Generic)?.amount ?: 0 }
        val rest = symbols.filterNot { it is ManaSymbol.Generic }
        return ManaCost((if (generic > 0) listOf(ManaSymbol.Generic(generic)) else emptyList()) + rest)
    }

    /**
     * CR 118.7: a reduction by generic mana reduces only generic mana (118.7a); by coloured or colourless mana, the
     * matching symbols, and generic mana for what the cost doesn't have (118.7b–d); Phyrexian by one mana of its colour
     * (118.7f); snow by generic (118.7g). Generic mana never goes below [minimum].
     */
    fun reduce(cost: ManaCost, reduction: ManaCost, minimum: Int = 0): ManaCost {
        val symbols = cost.symbols.toMutableList()
        var genericOff = 0
        reduction.symbols.forEach { r ->
            when (r) {
                is ManaSymbol.Generic -> genericOff += r.amount
                is ManaSymbol.Colored, ManaSymbol.Colorless -> {
                    val i = symbols.indexOf(r)
                    if (i >= 0) symbols.removeAt(i) else genericOff += 1
                }
                is ManaSymbol.Phyrexian -> {
                    val i = symbols.indexOfFirst { it == ManaSymbol.Colored(r.color) || it == r }
                    if (i >= 0) symbols.removeAt(i) else genericOff += 1
                }
                // msh: CR 118.7e: the payer chooses a half; the same hybrid symbol, then a symbol of either colour, then generic
                is ManaSymbol.Hybrid -> {
                    val i = symbols.indexOf(r).takeIf { it >= 0 }
                        ?: symbols.indexOfFirst { it == ManaSymbol.Colored(r.first) || it == ManaSymbol.Colored(r.second) }
                    if (i >= 0) symbols.removeAt(i) else genericOff += 1
                }
                else -> genericOff += 1
            }
        }
        val generic = symbols.sumOf { (it as? ManaSymbol.Generic)?.amount ?: 0 }
        val left = maxOf(minOf(generic, minimum), generic - genericOff)
        val rest = symbols.filterNot { it is ManaSymbol.Generic }
        return ManaCost((if (left > 0) listOf(ManaSymbol.Generic(left)) else emptyList()) + rest)
    }

    /** A [CostChange] as mana and a count (CR 601.2f). */
    fun expand(change: CostChange, eval: Evaluator): ManaCost {
        val times = eval.value(change.times).coerceAtLeast(0)
        return ManaCost(List(times) { change.mana.symbols }.flatten())
    }

    fun isIncrease(change: CostChange) = change is CostChange.More

    /** Whether the cost names {X} anywhere ([Instructions.announced] aside). */
    fun mentionsX(contributions: List<Contribution>): Boolean = contributions.any { c -> c.cost.parts.any { mentionsX(it) } }

    private fun mentionsX(part: CostPart): Boolean = when (part) {
        is CostPart.Mana -> part.x == null && part.cost.symbols.any { it is ManaSymbol.Variable }
        is CostPart.OneOf -> part.options.any { o -> o.parts.any { mentionsX(it) } }
        else -> false
    }

    // ---- Objects the parts use

    /** How many objects a part uses and from which candidates (CR 601.2h): sacrifice, tap, discard, exile, return,
     * reveal. Null when the part uses no chosen objects. */
    data class ObjectChoice(
        val options: List<ObjectId>, val min: Int, val max: Int?, val text: String, val total: arcanum.api.Total?,
        // rtr chunk 07: the chosen objects must all share this quality ("two nonland cards with the same name")
        val shared: arcanum.api.SharedQuality? = null,
    )

    fun objectChoice(snap: Snapshot, player: PlayerId, self: ObjectId?, part: CostPart, eval: Evaluator): ObjectChoice? {
        val what: CostObjects = when (part) {
            is CostPart.Sacrifice -> part.what
            is CostPart.TapObjects -> part.what
            is CostPart.Discard -> part.what
            is CostPart.Exile -> part.what
            is CostPart.ReturnToHand -> part.what
            is CostPart.Reveal -> part.what
            // CR 701.4a: beholding is revealing a card from the hand or choosing a permanent you control
            is CostPart.Action -> when (val action = part.action) {
                is arcanum.api.KeywordAction.Behold -> CostObjects.Choose(action.filter, count = action.count)
                // ecl: CR 701.68a: a creature you control gets the counters
                is arcanum.api.KeywordAction.Blight -> CostObjects.Choose(arcanum.api.ObjectFilter.Type(CardType.Creature), count = arcanum.api.Value.Fixed(1))
                else -> return null
            }
            is CostPart.RemoveCounters -> part.from
            is CostPart.PutCounters -> part.on
            else -> return null
        }
        val choose = what as? CostObjects.Choose ?: return null
        if (part is CostPart.Discard && part.random) return null
        val s = snap.state
        val candidates: List<ObjectId> = when (part) {
            is CostPart.Sacrifice, is CostPart.ReturnToHand, is CostPart.TapObjects, is CostPart.RemoveCounters, is CostPart.PutCounters ->
                s.battlefield.filter { snap.board.controller(it) == player && s.obj(it)?.phasedOut != true }
            is CostPart.Discard, is CostPart.Reveal -> s.player(player).hand
            is CostPart.Action -> (if (part.action is arcanum.api.KeywordAction.Blight) emptyList() else s.player(player).hand) +
                s.battlefield.filter { snap.board.controller(it) == player && s.obj(it)?.phasedOut != true }
            is CostPart.Exile -> when (part.from) {
                Zone.Battlefield -> s.battlefield.filter { snap.board.controller(it) == player }
                // rtr chunk 11: spells the payer controls ("Exile an instant or sorcery spell you control")
                Zone.Stack -> s.stack.filter { s.stackItems[it]?.controller == player }
                Zone.Exile, Zone.Command -> emptyList()
                else -> s.zone(part.from, player)
            }
            else -> emptyList()
        }
        val options = candidates.filter { id ->
            id != self && eval.matches(choose.filter, id) && when (part) {
                is CostPart.TapObjects -> s.obj(id)?.tapped == false
                is CostPart.Sacrifice -> snap.view[id]?.has { it is Mechanic.CantBeSacrificed } != true
                is CostPart.RemoveCounters -> (part.kind?.let { k -> s.obj(id)?.counters(k) ?: 0 } ?: s.obj(id)?.counters?.sumOf { it.count } ?: 0) > 0
                else -> true
            }
        }
        val count = choose.count?.let { eval.value(it) }
        val text = describe(part)
        // rtr chunk 07: only objects with enough others sharing the quality can be among the chosen
        val sharing = choose.shared?.let { q -> options.filter { a -> options.count { b -> b != a && eval.shares(q, a, b) } >= (count ?: 1) - 1 } } ?: options
        return if (choose.total != null) ObjectChoice(sharing, 1, null, text, choose.total, choose.shared)
            else ObjectChoice(sharing, count ?: 0, count, text, null, choose.shared)
    }

    /** A plain description of a cost part, for decisions ("Sacrifice a creature"). */
    fun describe(part: CostPart): String = when (part) {
        is CostPart.Sacrifice -> "Sacrifice"
        is CostPart.TapObjects -> "Tap"
        is CostPart.Discard -> "Discard"
        is CostPart.Exile -> "Exile from ${part.from}"
        is CostPart.ReturnToHand -> "Return to hand"
        is CostPart.Reveal -> "Reveal"
        is CostPart.Action -> when (part.action) {
            is arcanum.api.KeywordAction.Behold -> "Behold (reveal from your hand or choose one you control)"
            // ecl
            is arcanum.api.KeywordAction.Blight -> "Blight (put -1/-1 counters on a creature you control)"
            // blb
            arcanum.api.KeywordAction.Forage -> "Forage"
            else -> "Do"
        }
        is CostPart.RemoveCounters -> "Remove counters from"
        is CostPart.PutCounters -> "Put counters on"
        is CostPart.PayLife -> "Pay life"
        is CostPart.Mana -> "Pay ${part.cost}"
        is CostPart.OneOf -> part.options.joinToString(" or ") { o -> o.parts.joinToString(", ") { describe(it) } }
        // blb
        is CostPart.ChoosePlayer -> "Choose a player"
        // ecl
        is CostPart.Choose -> "Choose"
        else -> part::class.simpleName ?: "cost"
    }

    /** Whether a creature [id] can pay its own {T}: it isn't summoning sick (CR 302.6). */
    fun isCreature(snap: Snapshot, id: ObjectId) = snap.view[id]?.characteristics?.isType(CardType.Creature) == true
}

package arcanum.engine.mana

import arcanum.api.Ability
import arcanum.api.CardType
import arcanum.api.Color
import arcanum.api.Cost
import arcanum.api.CostPart
import arcanum.api.ManaCost
import arcanum.api.ManaSymbol
import arcanum.api.Mechanic
import arcanum.api.PlayKind
import arcanum.api.Zone
import arcanum.engine.casting.Legality
import arcanum.engine.casting.Snapshot
import arcanum.engine.decision.ManaUse
import arcanum.engine.state.ObjectId
import arcanum.engine.state.ObjectStatus
import arcanum.engine.state.PlayerId
import arcanum.engine.view.Functioning

/**
 * Autotap keeps options open (owner, 2026-11-22): among payments of the same tiers of Autotap's order ([Mana.suggest]), the one
 * after which the sources left can still pay the mana of the most of the payer's other spells and abilities ([Want]): the cards
 * in their hand, the cards they may use from other zones ([ObjectStatus.Playable]) and their permanents' activated abilities,
 * whatever their timing. Mana only: whether the leftover sources can produce each one's mana cost, no trial casts.
 */
internal object KeepOpen {
    /** One spell or ability to keep payable: any of [costs] pays it, and its cost taps [taps] ({T}), which must then stay unused. */
    class Want(val of: ObjectId, val costs: List<ManaCost>, val taps: ObjectId?)

    /** The spells and abilities of [player] a payment should keep payable (none costing nothing, which any payment keeps). */
    fun wants(snap: Snapshot, player: PlayerId): List<Want> {
        val result = mutableListOf<Want>()
        val state = snap.state
        // The cards in their hand: each face it can be cast as, its alternative costs, its abilities that work in the hand
        state.player(player).hand.forEach { id ->
            val o = state.obj(id) ?: return@forEach
            val faces = Legality.castableFaces(o).mapNotNull { o.definition.faces.getOrNull(it) }.filter { CardType.Land !in it.types }
            val instantOrSorcery = faces.firstOrNull()?.types.orEmpty().any { it == CardType.Instant || it == CardType.Sorcery }
            val costs = faces.mapNotNull { it.manaCost } +
                Legality.alternativeCosts(snap, id, player).filter { it.second.from == null || it.second.from == Zone.Hand }.mapNotNull { manaOf(it.second.cost) } +
                Legality.activatable(snap, id).filter { Zone.Hand in Functioning.zones(it.ability, instantOrSorcery) && !Legality.isManaAbility(it.ability) }
                    .mapNotNull { manaOf(costOf(it.ability)) }
            add(result, id, costs, null)
        }
        // Their cards they may use from other zones: a cast by a permission (its own cost, else the card's), their abilities there
        Legality.abilitySources(state, player).forEach { id ->
            val shown = snap.view[id] ?: return@forEach
            if (shown.zone == Zone.Hand || shown.zone == Zone.Battlefield) return@forEach
            val by = shown.statuses.filterIsInstance<ObjectStatus.Playable>().firstOrNull()?.by?.firstOrNull { it.player == player } ?: return@forEach
            val o = shown.obj ?: return@forEach
            val casts = if (by.how != PlayKind.Cast) emptyList() else {
                val permitted = shown.mechanics.mapNotNull { it.mechanic as? Mechanic.MayPlay }.filter { it.from == shown.zone && it.how == PlayKind.Cast }
                val own = permitted.mapNotNull { m -> if (m.withoutPaying) ManaCost(emptyList()) else m.cost?.let(::manaOf) }
                own.ifEmpty { Legality.castableFaces(o).mapNotNull { o.definition.faces.getOrNull(it)?.manaCost } }
            }
            val abilities = Legality.activatable(snap, id).filter { it.origin in by.abilities }.mapNotNull { manaOf(costOf(it.ability)) }
            add(result, id, casts + abilities, null)
        }
        // Their permanents' activated abilities other than mana abilities, each on its own
        state.battlefield.forEach { id ->
            if (snap.board.controller(id) != player) return@forEach
            Legality.activatable(snap, id).forEach { inst ->
                if (Legality.isManaAbility(inst.ability) || Zone.Battlefield !in Functioning.zones(inst.ability, false)) return@forEach
                val cost = costOf(inst.ability) ?: return@forEach
                val taps = id.takeIf { cost.parts.any { it == CostPart.TapSelf } }
                add(result, id, listOfNotNull(manaOf(cost) ?: ManaCost(emptyList()).takeIf { taps != null }), taps)
            }
        }
        return result
    }

    private fun add(to: MutableList<Want>, id: ObjectId, costs: List<ManaCost>, taps: ObjectId?) {
        val plain = costs.map { Mana.withX(it, emptyMap()) }.distinct()
        if (plain.isEmpty()) return
        // Payable whatever is tapped: it changes nothing between payments
        if (taps == null && plain.any { Mana.needs(it).isEmpty() }) return
        to += Want(id, plain, taps)
    }

    private fun costOf(ability: Ability): Cost? = when (ability) {
        is Ability.Activated -> ability.cost
        is Ability.SpecialAction -> ability.cost
        else -> null
    }

    /** The mana [cost] asks, its X as 0 (CR 107.3); null when it asks none. */
    private fun manaOf(cost: Cost?): ManaCost? {
        val symbols = cost?.parts.orEmpty().filterIsInstance<CostPart.Mana>().flatMap { it.cost.symbols }.filter { it !is ManaSymbol.Variable }
        return if (symbols.isEmpty()) null else ManaCost(symbols)
    }

    /**
     * The mana left after a payment of [uses] (its activations [picks]): the pool mana it doesn't spend, and one activation's mana
     * from each source object it leaves untapped and in place (what its best ability nets, any colour any of them makes).
     */
    fun leftover(sources: List<Mana.Source>, pool: List<Mana.Payer>, uses: List<ManaUse>, picks: List<Mana.Source>): Pair<List<Mana.Payer>, Set<ObjectId>> {
        val used = picks.filter { it.exclusive }.map { it.id }.toSet() + picks.flatMap { it.taps } + picks.flatMap { it.leaves }
        val left = pool.filter { it.use !in uses }.toMutableList()
        sources.filter { !it.unknown && it.id !in used }.groupBy { it.id }.forEach { (_, of) ->
            val net = of.maxOf { s -> s.amount - (s.ownCost?.let { Mana.leastMana(it) } ?: 0L).toInt() }
            if (net <= 0) return@forEach
            val colors: Set<Color?> = of.flatMap { s -> s.yields.flatMap { it.colors } + s.bonus.flatMap { it.colors } }.toSet()
            val snow = of.any { s -> s.yields.any { it.snow } }
            repeat(minOf(net, MAX_EACH)) { left += Mana.Payer(colors, snow, ManaUse.Life(-1)) }
        }
        return left to used
    }

    /** How many of [wants] the mana [left] can still pay, [used] objects no longer there to tap. */
    fun score(wants: List<Want>, left: List<Mana.Payer>, used: Set<ObjectId>): Int = wants.count { payable(it, left, used) }

    fun payable(want: Want, left: List<Mana.Payer>, used: Set<ObjectId>): Boolean {
        if (want.taps != null && want.taps in used) return false
        return want.costs.any { c -> Mana.needs(c).let { n -> n.isEmpty() || Mana.assign(n, left, budget = ASSIGN_STEPS) != null } }
    }

    /** The most mana one source object counts for. */
    private const val MAX_EACH = 4
    /** The steps one check of a cost against the mana left may take (a stopped check counts as not payable). */
    private const val ASSIGN_STEPS = 400
}

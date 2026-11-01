package arcanum.engine.mana

import arcanum.api.Ability
import arcanum.api.CardType
import arcanum.api.Color
import arcanum.api.CostPart
import arcanum.api.CostObjects
import arcanum.api.Effect
import arcanum.api.ManaCost
import arcanum.api.ManaKind
import arcanum.api.ManaPaymentWay
import arcanum.api.ManaProduction
import arcanum.api.ManaSymbol
import arcanum.api.Mechanic
import arcanum.api.Step
import arcanum.api.Supertype
import arcanum.api.Value
import arcanum.api.Zone
import arcanum.api.isManaAbility
import arcanum.engine.casting.Snapshot
import arcanum.engine.decision.ManaUse
import arcanum.engine.query.Evaluator
import arcanum.engine.state.Bindings
import arcanum.engine.state.ManaUnit
import arcanum.engine.state.NamedValue
import arcanum.engine.state.ObjectId
import arcanum.engine.state.PlayerId
import arcanum.engine.view.AbilityInstance
import arcanum.engine.view.AbilityOrigin
import arcanum.engine.view.Functioning

/**
 * Mana (CR 106, 107.4, 601.2g–h, 605): the mana abilities a player can use, what they add, paying a cost with pool
 * mana, mana abilities and other ways (convoke, delve, improvise, Phyrexian life), and the suggested payment (§7.8)
 * shown in [arcanum.engine.decision.Decision.ManaPayment].
 *
 * Mana abilities offered while paying are the ones that need no further choice: their costs are tapping or untapping
 * the source, sacrificing it or paying a fixed amount of life, and they only add mana. Others (sacrifice a creature,
 * X, "deals 1 damage to you") are activated on their own first ([arcanum.engine.input.Input.Activate]), which puts
 * their mana in the pool (CR 605.3a).
 */
internal object Mana {
    /** One mana ability of a permanent and what it adds: one [Yield] per mana-adding step. */
    data class Source(
        val id: ObjectId,
        val origin: AbilityOrigin,
        val ability: Ability.Activated,
        val yields: List<Yield>,
        /** How much using it costs in options kept open (§7.8): lower is used first. */
        val penalty: Int,
        /** Uses the source up (tap or sacrifice it): one ability of it at a time. */
        val exclusive: Boolean,
    ) {
        val colors: Set<Color?> get() = yields.flatMap { it.colors }.toSet()
    }

    /** [amount] mana, each one of [colors] (null: colourless); [choice]: the player picks one of [colors] for all of it. */
    data class Yield(val colors: List<Color?>, val amount: Int, val choice: Boolean, val production: ManaProduction, val snow: Boolean)

    /** Something that can pay one mana symbol (or one generic mana): pool mana, a convoking creature, a delved card. */
    data class Payer(val colors: Set<Color?>, val snow: Boolean, val use: ManaUse, val unit: ManaUnit? = null)

    // ---- Mana abilities

    /** The mana abilities of [id] in its characteristics (CR 605.1a), granted ones included. */
    fun manaAbilities(snap: Snapshot, id: ObjectId): List<AbilityInstance> =
        snap.view[id]?.characteristics?.abilities.orEmpty().filter { it.ability is Ability.Activated && it.ability.isManaAbility() }

    /** Whether [player] may activate [instance] of [id] now as far as its source and cost go (CR 602.2, 602.5, 605.3a). */
    fun canActivate(snap: Snapshot, player: PlayerId, id: ObjectId, instance: AbilityInstance): Boolean {
        val ability = instance.ability as? Ability.Activated ?: return false
        val o = snap.state.obj(id) ?: return false
        val ov = snap.view[id] ?: return false
        val instantOrSorcery = ov.characteristics?.let { it.isType(CardType.Instant) || it.isType(CardType.Sorcery) } == true
        if (o.zone !in Functioning.zones(ability, instantOrSorcery)) return false
        val controller = snap.board.controller(id) ?: o.owner
        val eval = snap.evaluator(controller, self = id)
        if (!eval.matches(ability.rules.activatedBy, player)) return false
        if (ability.rules.condition?.let { eval.holds(it) } == false) return false
        if (ov.has { it is Mechanic.CantBeActivated && (it.manaAbilities || !ability.isManaAbility()) }) return false
        return ability.cost.parts.all { part -> costPartPossible(snap, player, id, part) }
    }

    /** Parts of a mana ability's cost that need no choice: can they be paid now? Other parts are possible when there is
     * something to choose (checked by the casting flow). */
    private fun costPartPossible(snap: Snapshot, player: PlayerId, id: ObjectId, part: CostPart): Boolean {
        val o = snap.state.obj(id) ?: return false
        val ov = snap.view[id]
        return when (part) {
            // CR 302.6, 602.5a: a creature's {T} or {Q} ability needs it controlled since the turn began
            CostPart.TapSelf -> o.zone == Zone.Battlefield && !o.tapped && !sick(snap, id)
            CostPart.UntapSelf -> o.zone == Zone.Battlefield && o.tapped && !sick(snap, id)
            is CostPart.Sacrifice -> part.what != CostObjects.Self ||
                (o.zone == Zone.Battlefield && ov?.has { it is Mechanic.CantBeSacrificed } != true)
            is CostPart.PayLife -> snap.state.player(player).life >= snap.evaluator(player, self = id).value(part.amount)
            else -> true
        }
    }

    /** CR 302.6: a creature whose controller hasn't controlled it since their turn began, without haste. */
    fun sick(snap: Snapshot, id: ObjectId): Boolean {
        val ov = snap.view[id] ?: return false
        val creature = ov.characteristics?.isType(CardType.Creature) == true
        return creature && ov.summoningSick && !ov.has { it is Mechanic.IgnoresSummoningSickness }
    }

    /** A mana ability that pays without any choice but a colour: only tap/untap/sacrifice itself/fixed life, and it does
     * nothing but add mana. */
    fun isSimple(ability: Ability.Activated): Boolean =
        ability.instructions.announced.isEmpty() &&
            ability.instructions.steps.all { it is Step.Do && it.effect is Effect.AddMana } &&
            ability.cost.parts.all {
                it == CostPart.TapSelf || it == CostPart.UntapSelf ||
                    (it is CostPart.Sacrifice && it.what == CostObjects.Self) ||
                    (it is CostPart.PayLife && it.amount is Value.Fixed)
            }

    /** What [ability] of [id] adds when [player] activates it, per adding step (CR 106.1). [names] holds announced X. */
    fun yields(snap: Snapshot, player: PlayerId, id: ObjectId, ability: Ability.Activated, names: Bindings = Bindings.EMPTY): List<Yield> {
        val eval = snap.evaluator(player, self = id, names = names)
        val snow = snap.view[id]?.characteristics?.supertypes?.contains(Supertype.Snow) == true
        return ability.instructions.steps.mapNotNull { (it as? Step.Do)?.effect as? Effect.AddMana }.flatMap { add ->
            yieldsOf(snap, eval, add.mana, snow, depth = 0)
        }
    }

    private fun yieldsOf(snap: Snapshot, eval: Evaluator, production: ManaProduction, snow: Boolean, depth: Int): List<Yield> =
        when (val kind = production.kind) {
            is ManaKind.Fixed -> {
                val times = kind.times?.let { eval.value(it) } ?: 1
                kind.mana.symbols.groupBy { (it as? ManaSymbol.Colored)?.color }.mapNotNull { (color, symbols) ->
                    val amount = symbols.sumOf { (it as? ManaSymbol.Generic)?.amount ?: 1 } * times
                    if (amount <= 0) null else Yield(listOf(color), amount, choice = false, production, snow)
                }
            }
            is ManaKind.Choose -> {
                val amount = eval.value(kind.amount)
                val colors = kind.colors?.flatMap { eval.colors(it) }?.distinct() ?: Color.all
                if (amount <= 0 || colors.isEmpty()) emptyList()
                else listOf(Yield(colors, amount, choice = colors.size > 1, production, snow))
            }
            is ManaKind.AsCouldProduce -> {
                val amount = eval.value(kind.amount)
                val colors = if (depth > 0) emptyList() else eval.each(kind.filter).flatMap { land ->
                    manaAbilities(snap, land).flatMap { inst ->
                        val a = inst.ability as Ability.Activated
                        a.instructions.steps.mapNotNull { (it as? Step.Do)?.effect as? Effect.AddMana }
                            .flatMap { yieldsOf(snap, eval.with(eval.ctx.copy(self = land)), it.mana, snow, depth + 1) }
                            .flatMap { it.colors }
                    }
                }.distinct()
                if (amount <= 0 || colors.isEmpty()) emptyList()
                else listOf(Yield(colors, amount, choice = colors.size > 1, production, snow))
            }
            // Triggered mana abilities (CR 605.1b) aren't activated; nothing to pay with here.
            is ManaKind.SameAsProduced -> emptyList()
        }

    /** The mana units [yield] adds, all of [color] when it is a choice, from [source]. */
    fun units(yield: Yield, color: Color?, source: ObjectId): List<ManaUnit> {
        val c = if (yield.choice || yield.colors.size > 1) color ?: yield.colors.first() else yield.colors.first()
        return List(yield.amount) {
            ManaUnit(c, source = source, snow = yield.snow, restriction = yield.production.restriction,
                spellGets = yield.production.spellGets, keptUntil = yield.production.keptUntil, whenSpent = yield.production.whenSpent)
        }
    }

    /** The simple mana abilities [player] can use now to pay, cheapest first (§7.8). */
    fun sources(snap: Snapshot, player: PlayerId): List<Source> {
        val result = mutableListOf<Source>()
        snap.state.battlefield.forEach { id ->
            if (snap.board.controller(id) != player && manaAbilities(snap, id).none { activatableByOthers(it) }) return@forEach
            val usable = manaAbilities(snap, id).mapNotNull { inst ->
                val ability = inst.ability as Ability.Activated
                if (!isSimple(ability) || !canActivate(snap, player, id, inst)) return@mapNotNull null
                yields(snap, player, id, ability).takeIf { it.isNotEmpty() }?.let { Triple(inst, ability, it) }
            }
            // A source that could make more colours (with any of its abilities) keeps more options open
            val colors = usable.flatMap { (_, _, ys) -> ys.flatMap { it.colors } }.distinct().size
            usable.forEach { (inst, ability, ys) ->
                result += Source(id, inst.origin, ability, ys, penalty(snap, id, ability, ys, colors), exclusive = true)
            }
        }
        return result.sortedBy { it.penalty }
    }

    private fun activatableByOthers(inst: AbilityInstance): Boolean =
        (inst.ability as? Ability.Activated)?.rules?.activatedBy != arcanum.api.PlayerFilter.You

    /** Cheaper sources are used first: lands before other permanents, few colours before many, nothing given up. */
    private fun penalty(snap: Snapshot, id: ObjectId, ability: Ability.Activated, yields: List<Yield>, colors: Int): Int {
        val c = snap.view[id]?.characteristics
        var p = colors * 2
        if (c?.isType(CardType.Creature) == true) p += 10
        if (c?.isType(CardType.Land) != true) p += 3
        ability.cost.parts.forEach { part ->
            when (part) {
                is CostPart.Sacrifice -> p += 100
                is CostPart.PayLife -> p += 40
                else -> Unit
            }
        }
        if (yields.sumOf { it.amount } > 1) p += 1
        return p
    }

    // ---- Paying

    /** One requirement of a cost: one of [alternatives], each a list of single-mana needs, or 2 life when [life]. */
    data class Need(val symbolIndex: Int, val alternatives: List<List<(Payer) -> Boolean>>, val life: Boolean, val generic: Boolean)

    /** The needs of [cost]: X symbols must have been replaced already ([withX]). */
    fun needs(cost: ManaCost, lifeFor: Set<Int> = emptySet()): List<Need> = cost.symbols.flatMapIndexed { i, s ->
        fun color(c: Color): (Payer) -> Boolean = { c in it.colors }
        val colorless: (Payer) -> Boolean = { null in it.colors }
        val any: (Payer) -> Boolean = { true }
        when (s) {
            is ManaSymbol.Generic -> List(s.amount) { Need(i, listOf(listOf(any)), life = false, generic = true) }
            is ManaSymbol.Colored -> listOf(Need(i, listOf(listOf(color(s.color))), false, false))
            ManaSymbol.Colorless -> listOf(Need(i, listOf(listOf(colorless)), false, false))
            ManaSymbol.Snow -> listOf(Need(i, listOf(listOf { p: Payer -> p.snow }), false, false))
            is ManaSymbol.Variable -> emptyList()
            is ManaSymbol.Hybrid -> listOf(Need(i, listOf(listOf(color(s.first)), listOf(color(s.second))), false, false))
            is ManaSymbol.GenericHybrid -> listOf(Need(i, listOf(listOf(color(s.color)), List(s.amount) { any }), false, false))
            is ManaSymbol.ColorlessHybrid -> listOf(Need(i, listOf(listOf(colorless), listOf(color(s.color))), false, false))
            is ManaSymbol.Phyrexian -> listOf(Need(i, if (i in lifeFor) emptyList() else listOf(listOf(color(s.color))), life = i in lifeFor, generic = false))
            is ManaSymbol.PhyrexianHybrid -> listOf(Need(i, if (i in lifeFor) emptyList()
                else listOf(listOf(color(s.first)), listOf(color(s.second))), life = i in lifeFor, generic = false))
        }
    }

    /** Replaces {X} (and {Y}, {Z}) with their announced values (CR 107.3). */
    fun withX(cost: ManaCost, values: Map<Char, Int>): ManaCost = ManaCost(cost.symbols.flatMap { s ->
        if (s is ManaSymbol.Variable) {
            val v = values[s.name] ?: 0
            if (v > 0) listOf(ManaSymbol.Generic(v)) else emptyList()
        } else listOf(s)
    })

    /**
     * Assigns [payers] to the [needs] (CR 601.2h): each need gets one payer per part of one of its alternatives; generic
     * needs come last. Returns the payers used, or null when they can't pay it all.
     */
    fun assign(needs: List<Need>, payers: List<Payer>): List<Payer>? {
        val ordered = needs.filterNot { it.life }.sortedBy { if (it.generic) 2 else if (it.alternatives.size > 1 || it.alternatives.firstOrNull()?.size != 1) 1 else 0 }
        val used = BooleanArray(payers.size)
        var budget = 20_000
        fun go(i: Int): Boolean {
            if (budget-- <= 0) return false
            if (i == ordered.size) return true
            val need = ordered[i]
            for (alternative in need.alternatives) {
                val picked = mutableListOf<Int>()
                fun pick(k: Int): Boolean {
                    if (k == alternative.size) return go(i + 1)
                    var tried = mutableSetOf<Pair<Set<Color?>, Boolean>>()
                    for (j in payers.indices) {
                        if (used[j] || !alternative[k](payers[j])) continue
                        // Payers alike are interchangeable: try one of each kind
                        if (!tried.add(payers[j].colors to payers[j].snow) && need.generic) continue
                        used[j] = true; picked += j
                        if (pick(k + 1)) return true
                        used[j] = false; picked.removeAt(picked.lastIndex)
                    }
                    return false
                }
                if (pick(0)) return true
            }
            return false
        }
        return if (go(0)) payers.filterIndexed { j, _ -> used[j] } else null
    }

    /** Payers from mana in [player]'s pool that may be spent on [paysFor] (CR 106.6). */
    fun poolPayers(snap: Snapshot, player: PlayerId, paysFor: ObjectId?, indices: List<Int>? = null): List<Payer> {
        val pool = snap.state.player(player).manaPool
        val anyColor = spendsAsAnyColor(snap, player, paysFor)
        return pool.withIndex().filter { (i, u) -> (indices == null || i in indices) && allowed(snap, player, u, paysFor) }
            .map { (i, u) -> Payer(if (anyColor) (Color.all + u.color).toSet() else setOf(u.color), u.snow, ManaUse.Pool(i), u) }
    }

    /** "Spend mana as though it were mana of any colour" for [player] (CR 609.4b). */
    fun spendsAsAnyColor(snap: Snapshot, player: PlayerId, paysFor: ObjectId?): Boolean =
        snap.view.players[player]?.mechanics.orEmpty().any { applied ->
            val m = applied.mechanic as? Mechanic.SpendManaAsAnyColor ?: return@any false
            m.onlyFor == null || (paysFor != null && snap.evaluator(applied.controller, self = applied.source).matches(m.onlyFor!!, paysFor))
        }

    /** Whether [unit] may be spent on [paysFor]: its restriction ("spend this mana only to cast artifact spells"). */
    fun allowed(snap: Snapshot, player: PlayerId, unit: ManaUnit, paysFor: ObjectId?): Boolean {
        val restriction = unit.restriction ?: return true
        if (paysFor == null) return false
        return snap.evaluator(player, self = unit.source).matches(restriction, paysFor)
    }

    /** Payers for the other ways the spell may pay ([ManaPaymentWay], CR 702.51, 702.66, 702.126). */
    fun specialPayers(snap: Snapshot, player: PlayerId, ways: List<ManaPaymentWay>, exclude: Set<ObjectId> = emptySet()): List<Payer> =
        ways.distinct().flatMap { way ->
            when (way) {
                ManaPaymentWay.TapCreatures -> untapped(snap, player, CardType.Creature).filter { it !in exclude }.map { id ->
                    Payer((snap.view[id]?.characteristics?.colors.orEmpty() + listOf<Color?>()).toSet(), snow = false,
                        use = ManaUse.Special(way, listOf(id)))
                }
                ManaPaymentWay.TapArtifacts -> untapped(snap, player, CardType.Artifact).filter { it !in exclude }.map { id ->
                    Payer(emptySet(), snow = false, use = ManaUse.Special(way, listOf(id)))
                }
                ManaPaymentWay.ExileFromGraveyard -> snap.state.player(player).graveyard.filter { it !in exclude }.map { id ->
                    Payer(emptySet(), snow = false, use = ManaUse.Special(way, listOf(id)))
                }
            }
        }

    private fun untapped(snap: Snapshot, player: PlayerId, type: CardType): List<ObjectId> = snap.state.battlefield.filter { id ->
        val o = snap.state.obj(id)
        o != null && !o.tapped && !o.phasedOut && snap.board.controller(id) == player && snap.view[id]?.characteristics?.isType(type) == true
    }

    // ---- The decision's options and suggestion

    /** Ways [player] could pay [cost] now (CR 601.2g–h): pool mana, simple mana abilities with each colour they can
     * make, life for Phyrexian symbols, and the spell's other ways. */
    fun options(snap: Snapshot, player: PlayerId, cost: ManaCost, paysFor: ObjectId?, ways: List<ManaPaymentWay> = emptyList()): List<ManaUse> =
        buildList {
            poolPayers(snap, player, paysFor).forEach { add(it.use) }
            sources(snap, player).forEach { s ->
                val colors = s.yields.filter { it.choice }.flatMap { it.colors }.distinct()
                if (colors.isEmpty()) add(ManaUse.Ability(s.id, s.origin, s.colors.singleOrNull()))
                else colors.forEach { c -> add(ManaUse.Ability(s.id, s.origin, c)) }
            }
            cost.symbols.forEachIndexed { i, s -> if (s is ManaSymbol.Phyrexian || s is ManaSymbol.PhyrexianHybrid) add(ManaUse.Life(i)) }
            specialPayers(snap, player, ways).forEach { add(it.use) }
        }

    /**
     * A payment of [cost] that keeps the most options open, or empty when none is found (§7.8): pool mana first, then
     * the cheapest sources (lands before creatures, single colours before many, nothing sacrificed when avoidable);
     * Phyrexian symbols by mana when it can be found, else by life. Other ways (convoke, delve) are left to the player.
     */
    fun suggest(snap: Snapshot, player: PlayerId, cost: ManaCost, paysFor: ObjectId?): List<ManaUse> {
        if (cost.symbols.all { it is ManaSymbol.Generic && it.amount == 0 || it is ManaSymbol.Variable }) return emptyList()
        val pool = poolPayers(snap, player, paysFor)
        val sources = sources(snap, player)
        val phyrexian = cost.symbols.withIndex().filter { it.value is ManaSymbol.Phyrexian || it.value is ManaSymbol.PhyrexianHybrid }.map { it.index }
        // Try paying every Phyrexian symbol with mana first, then with life one by one from the last
        for (lifeCount in 0..phyrexian.size) {
            val lifeFor = phyrexian.takeLast(lifeCount).toSet()
            if (snap.state.player(player).life < 2 * lifeCount) break
            val found = search(snap, player, needs(cost, lifeFor), pool, sources, paysFor)
            if (found != null) return found + lifeFor.sorted().map { ManaUse.Life(it) }
        }
        return emptyList()
    }

    /** Depth-first search for sources that pay [needs] with [pool] mana first; cheapest sources tried first. */
    private fun search(snap: Snapshot, player: PlayerId, needs: List<Need>, pool: List<Payer>, sources: List<Source>, paysFor: ObjectId?): List<ManaUse>? {
        assign(needs, pool)?.let { used -> return used.map { it.use } }
        // Add sources one at a time, cheapest first, until the payers cover the needs
        val chosen = mutableListOf<Pair<Source, Color?>>()
        var budget = 3_000
        fun payersOf(list: List<Pair<Source, Color?>>): List<Payer> = list.flatMap { (s, c) ->
            s.yields.flatMap { y ->
                units(y, c, s.id).filter { allowed(snap, player, it, paysFor) }
                    .map { u -> Payer(setOf(u.color), u.snow, ManaUse.Ability(s.id, s.origin, if (y.choice) c else s.colors.singleOrNull()), u) }
            }
        }
        fun go(from: Int): List<ManaUse>? {
            if (budget-- <= 0) return null
            val payers = pool + payersOf(chosen)
            val used = assign(needs, payers)
            if (used != null) {
                val poolUses = used.mapNotNull { it.use as? ManaUse.Pool }.distinct()
                return poolUses + chosen.map { (s, c) -> ManaUse.Ability(s.id, s.origin, if (s.yields.any { it.choice }) c else s.colors.singleOrNull()) }
            }
            if (chosen.size >= needs.size) return null
            val missing = missingColors(needs, payers)
            for (k in from until sources.size) {
                val s = sources[k]
                if (s.exclusive && chosen.any { it.first.id == s.id }) continue
                val options = if (s.yields.any { it.choice }) {
                    val colors = s.yields.filter { it.choice }.flatMap { it.colors }.distinct()
                    colors.sortedBy { if (it in missing) 0 else 1 }
                } else listOf(null)
                for (c in options) {
                    chosen += s to c
                    go(k + 1)?.let { return it }
                    chosen.removeAt(chosen.lastIndex)
                }
            }
            return null
        }
        return go(0)
    }

    /** Colours some need asks for that no payer has yet: guides the colour a choosing source is tried with first. */
    private fun missingColors(needs: List<Need>, payers: List<Payer>): Set<Color?> {
        val have = payers.flatMap { it.colors }.toSet()
        val wanted = needs.flatMap { n -> n.alternatives.flatten() }.let { tests ->
            (Color.all + listOf<Color?>(null)).filter { c -> tests.any { t -> t(Payer(setOf(c), false, ManaUse.Life(-1))) } }
        }
        return wanted.filter { it !in have }.toSet()
    }

    /** Colours as named values, for a mana ability's colour choice. */
    fun colorChoice(color: Color): NamedValue = NamedValue.Colors(listOf(color))
}

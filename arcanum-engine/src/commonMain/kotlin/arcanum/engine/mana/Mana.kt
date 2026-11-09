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
import arcanum.engine.casting.Legality
import arcanum.engine.casting.Snapshot
import arcanum.engine.decision.Answer
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
 * Every mana ability may be used while paying (CR 605.3a): a [Source] says what one activation takes (its own mana, life,
 * objects, damage it deals you) and adds. Three questions are asked of them, never mixed up: [verdict], whether a payment
 * can exist at all (denied only with proof: any search stopped early, or a source that can't be read, answers "maybe");
 * [suggest], Autotap's convenience, which never decides whether something can be cast; [options], what a player may pick
 * by hand.
 */
internal object Mana {
    /**
     * One activation of a mana ability and what it takes (CR 605.3a): [yields] per mana-adding step, [bonus] what triggered
     * mana abilities add when it taps for mana (CR 605.1b).
     */
    data class Source(
        val id: ObjectId,
        val origin: AbilityOrigin,
        val ability: Ability.Activated,
        val yields: List<Yield>,
        /** How much using it costs in options kept open (§7.8): lower is used first. */
        val penalty: Int,
        /** Uses the source up (tap or sacrifice it): one ability of it at a time. */
        val exclusive: Boolean,
        /** The mana its own cost asks ("{1}, {T}: Add {W}{U}"), paid from the mana the payment has by then. */
        val ownCost: ManaCost? = null,
        /** Life its own cost pays (CR 119.4: only with that much life). */
        val life: Int = 0,
        /** Damage it deals its controller, or life it makes them lose, as it resolves (an effect, not a cost: CR 118.3, 119.4
         * limit only paid life, so it counts even when lethal). */
        val damage: Int = 0,
        /** Autotap's order (owner, castability §4): 0 nothing given up, 1 damage or life, 2 sacrifices and other things used up. */
        val tier: Int = 0,
        /** Other objects its cost taps. */
        val taps: Set<ObjectId> = emptySet(),
        /** Objects its cost moves away (itself or others): each only once in a payment; they may tap for mana first. */
        val leaves: Set<ObjectId> = emptySet(),
        /** The answers its activation asks, in order, beside a colour (what its cost uses, an amount). */
        val answers: List<Answer> = emptyList(),
        /** How many times it can be used in one payment (counters, energy, a limit each turn). */
        val limit: Int = 1,
        val bonus: List<Yield> = emptyList(),
        /** What it adds or takes can't be read from the state (an X it announces, mana inside a "may pay"): not searched, so a
         * search that doesn't find a way can't deny (it may be the way); a player can still pick it by hand. */
        val unknown: Boolean = false,
        /** Only some of the objects its cost could use were tried: a search that doesn't find a way can't deny. */
        val partial: Boolean = false,
    ) {
        val colors: Set<Color?> get() = yields.flatMap { it.colors }.toSet()
        val amount: Int get() = yields.sumOf { it.amount } + bonus.sumOf { it.amount }
        /** Interchangeable with [other] in a search: only one of two such needs trying first. */
        fun alike(other: Source) = exclusive && other.exclusive && ownCost == other.ownCost && life == other.life && damage == other.damage &&
            taps.isEmpty() && other.taps.isEmpty() && answers.isEmpty() && other.answers.isEmpty() && (leaves - id).isEmpty() && (other.leaves - other.id).isEmpty() &&
            leaves.isEmpty() == other.leaves.isEmpty() && penalty == other.penalty && limit == other.limit &&
            yields.map { it.colors to it.amount } == other.yields.map { it.colors to it.amount } && bonus.map { it.colors to it.amount } == other.bonus.map { it.colors to it.amount } &&
            yields.map { it.snow } == other.yields.map { it.snow } && yields.map { it.production } == other.yields.map { it.production }
    }

    /** [amount] mana, each one of [colors] (null: colourless); [choice]: the player picks one of [colors] for all of it, or with
     * [combination] one for each. */
    data class Yield(val colors: List<Color?>, val amount: Int, val choice: Boolean, val production: ManaProduction, val snow: Boolean, val combination: Boolean = false)

    /** What the other costs of the cast use (CR 601.2g comes before 601.2h): the targets and the cost are locked by then. */
    data class Reserved(
        /** Objects the cost taps: they can't also tap for mana. */
        val tapped: Set<ObjectId> = emptySet(),
        /** Objects the cost moves away: they may tap for mana first, but can't also be sacrificed for mana. */
        val leaving: Set<ObjectId> = emptySet(),
        /** Life the cost pays after the mana abilities have run (damage from a mana ability comes first, CR 601.2g, 601.2h). */
        val paidLife: Int = 0,
    ) {
        companion object { val NONE = Reserved() }
    }

    /** Whether a payment can be found: with the uses that make it, proven impossible, or not known (treated as possible). */
    sealed interface Verdict {
        data class Found(val uses: List<ManaUse>) : Verdict
        data object None : Verdict
        data object Maybe : Verdict
    }

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
        // fra: a player who can't activate such abilities (split second, CR 702.61a)
        if (snap.view.players[player]?.mechanics.orEmpty().any { applied ->
                val m = applied.mechanic as? Mechanic.CantActivate ?: return@any false
                abilityMatches(snap.evaluator(applied.controller, self = applied.source, names = applied.names), m.abilities, ability, id)
            }) return false
        return ability.cost.parts.all { part -> costPartPossible(snap, player, id, part) }
    }

    /** fra: whether [ability] of [source], being activated, matches [filter] (an ability filter: kind, source, label). */
    private fun abilityMatches(e: arcanum.engine.query.Evaluator, filter: arcanum.api.ObjectFilter, ability: Ability.Activated, source: ObjectId): Boolean =
        when (filter) {
            arcanum.api.ObjectFilter.Any -> true
            is arcanum.api.ObjectFilter.Not -> !abilityMatches(e, filter.filter, ability, source)
            is arcanum.api.ObjectFilter.And -> filter.all.all { abilityMatches(e, it, ability, source) }
            is arcanum.api.ObjectFilter.Or -> filter.any.any { abilityMatches(e, it, ability, source) }
            is arcanum.api.ObjectFilter.AbilityOf -> (filter.kind == null || when (filter.kind) {
                arcanum.api.AbilityKind.Mana -> ability.isManaAbility()
                arcanum.api.AbilityKind.Activated -> true
                arcanum.api.AbilityKind.Loyalty -> ability.cost.parts.any { it is CostPart.Loyalty }
                else -> false
            }) && (filter.source == null || e.matches(filter.source!!, source)) && (filter.label == null || ability.label == filter.label)
            else -> false
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
        return creature && ov.summoningSick && !ov.has { it is Mechanic.IgnoresSummoningSickness || it is Mechanic.AbilitiesIgnoreSummoningSickness }
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
    /**
     * ecl: [step] of [ability] with mana "of the colour chosen" by the ability's own colour choice ("{1}: Add one mana of any color.
     * This creature becomes that color", a [Step.Choose] of a colour bound and then named) read as mana of any of the colours that
     * choice offers: the colour is chosen as the mana is made (CR 605.3b), and the choice step then reuses it.
     */
    fun withChosenColors(ability: Ability.Activated, step: Step): Step {
        val add = (step as? Step.Do)?.effect as? Effect.AddMana ?: return step
        val kind = add.mana.kind as? ManaKind.Choose ?: return step
        val refs = kind.colors ?: return step
        val choices = ability.instructions.steps.filterIsInstance<Step.Choose>()
            .mapNotNull { c -> (c.choice as? arcanum.api.Choice.Color)?.let { c.bind to it } }.toMap()
        if (refs.none { it is arcanum.api.ColorRef.Chosen && it.name in choices }) return step
        val among = refs.flatMap { r ->
            val choice = (r as? arcanum.api.ColorRef.Chosen)?.let { choices[it.name] } ?: return@flatMap listOf(r)
            choice.among ?: return Step.Do(add.copy(mana = add.mana.copy(kind = kind.copy(colors = null))))
        }
        return Step.Do(add.copy(mana = add.mana.copy(kind = kind.copy(colors = among))))
    }

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
                else listOf(Yield(colors, amount, choice = colors.size > 1, production, snow, combination = kind.combination && amount > 1 && colors.size > 1))
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
            // Triggered mana abilities (CR 605.1b) aren't activated: counted with the source that taps ([triggeredMana]).
            is ManaKind.SameAsProduced -> emptyList()
            // ecl: one mana of each colour among the objects (CR 105.4, 106.1)
            is ManaKind.EachColorAmong -> eval.objects(kind.objects).flatMap { eval.characteristics(it)?.colors.orEmpty() }.distinct()
                .map { Yield(listOf(it), 1, choice = false, production, snow) }
        }

    /** The mana units [yield] adds, all of [color] when it is a choice, from [source]. */
    fun units(yield: Yield, color: Color?, source: ObjectId): List<ManaUnit> {
        val c = if (yield.choice || yield.colors.size > 1) color ?: yield.colors.first() else yield.colors.first()
        return List(yield.amount) {
            ManaUnit(c, source = source, snow = yield.snow, restriction = yield.production.restriction,
                spellGets = yield.production.spellGets, keptUntil = yield.production.keptUntil, whenSpent = yield.production.whenSpent,
                spellGetsIf = yield.production.spellGetsIf, spellGetsUntil = yield.production.spellGetsUntil)
        }
    }

    /** The mana abilities [player] can use now to pay, one [Source] per use of what its cost asks, cheapest first (§7.8): on the
     * battlefield and in their hand (CR 113.6: "exile this card from your hand: add {R}"). */
    fun sources(snap: Snapshot, player: PlayerId, reserved: Reserved = Reserved.NONE): List<Source> {
        val result = mutableListOf<Source>()
        val triggers = manaTriggers(snap, player)
        (snap.state.battlefield + snap.state.player(player).hand).forEach { id ->
            val abilities = manaAbilities(snap, id)
            if (abilities.isEmpty()) return@forEach
            if (snap.board.controller(id) != player && snap.state.obj(id)?.owner != player && abilities.none { activatableByOthers(it) }) return@forEach
            val usable = abilities.filter { canActivate(snap, player, id, it) && Legality.manaUsesLeft(snap, id, it) > 0 }.flatMap { sourcesOf(snap, player, id, it, triggers, reserved) }
            // A source that could make more colours (with any of its abilities) keeps more options open
            val colors = usable.flatMap { it.yields }.flatMap { it.colors }.distinct().size
            usable.forEach { s -> result += s.copy(penalty = penalty(snap, s, colors)) }
        }
        return result.sortedBy { it.penalty }
    }

    /** What activating [inst] of [id] takes and adds, one [Source] for each set of objects its cost could use. */
    private fun sourcesOf(snap: Snapshot, player: PlayerId, id: ObjectId, inst: AbilityInstance, triggers: List<ManaTrigger>, reserved: Reserved): List<Source> {
        val ability = inst.ability as Ability.Activated
        val eval = snap.evaluator(player, self = id)
        val you = snap.state.player(player)
        val parts = ability.cost.parts
        val tapsSelf = parts.any { it == CostPart.TapSelf }
        if (tapsSelf && id in reserved.tapped) return emptyList()
        var unknown = ability.instructions.announced.isNotEmpty() || ability.instructions.modes != null
        var own: ManaCost? = null
        var life = 0
        var limit = Legality.manaUsesLeft(snap, id, inst)
        var exclusive = false
        var tier = 0
        val leaves = mutableSetOf<ObjectId>()
        // The parts that choose objects: each with what it may choose (a single object each, the first ones otherwise)
        val chosen = mutableListOf<Triple<CostPart, List<List<ObjectId>>, Boolean>>()
        parts.forEach { part ->
            when (part) {
                CostPart.TapSelf, CostPart.UntapSelf -> exclusive = true
                is CostPart.Mana -> if (part.times != null || part.x != null || part.cost.symbols.any { it is ManaSymbol.Variable }) unknown = true
                    else own = ManaCost((own?.symbols ?: emptyList()) + part.cost.symbols)
                CostPart.ItsManaCost -> unknown = true
                is CostPart.PayLife -> { life += eval.value(part.amount); tier = maxOf(tier, 1) }
                is CostPart.PayEnergy -> eval.value(part.amount).let { n -> if (n > 0) limit = minOf(limit, you.counters(arcanum.api.CounterKind.Energy) / n); tier = maxOf(tier, 1) }
                is CostPart.PutCounters -> if (part.on != CostObjects.Self) unknown = true
                is CostPart.RemoveCounters -> if (part.from == CostObjects.Self) {
                    val n = eval.value(part.amount)
                    val o = snap.state.obj(id)
                    val kind = part.kind ?: o?.counters?.firstOrNull()?.kind
                    if (n > 0) limit = minOf(limit, if (kind == null) 0 else (o?.counters(kind) ?: 0) / n)
                    tier = 2
                } else unknown = true
                is CostPart.Sacrifice, is CostPart.Exile, is CostPart.Discard, is CostPart.ReturnToHand, is CostPart.TapObjects -> {
                    if (part !is CostPart.TapObjects) tier = 2
                    val what = when (part) {
                        is CostPart.Sacrifice -> part.what
                        is CostPart.Exile -> part.what
                        is CostPart.Discard -> part.what
                        is CostPart.ReturnToHand -> part.what
                        is CostPart.TapObjects -> part.what
                        else -> error("Not this part")
                    }
                    when {
                        what == CostObjects.Self -> { exclusive = true; leaves += id; if (part is CostPart.TapObjects) unknown = true }
                        what is CostObjects.Choose -> {
                            val choice = arcanum.engine.casting.Costs.objectChoice(snap, player, null, part, eval)
                            if (choice == null || (part is CostPart.Discard && part.random)) unknown = true
                            else {
                                val single = choice.min <= 1 && choice.total == null && choice.shared == null
                                // CR 601.2g before 601.2h: what the cost taps must still be there and untapped; what the cost moves away may tap
                                // for mana first but can't also go for mana
                                val free = choice.options.filter { o -> o !in reserved.tapped && (part is CostPart.TapObjects || o !in reserved.leaving) }
                                if (free.size < choice.min) return emptyList()
                                chosen += if (single) Triple(part, free.take(MAX_OBJECTS).map { listOf(it) }, free.size > MAX_OBJECTS)
                                    else Triple(part, listOf(free.take(choice.min)), true)
                            }
                        }
                        else -> unknown = true
                    }
                }
                else -> unknown = true
            }
        }
        if (id in leaves && (id in reserved.leaving || id in reserved.tapped)) return emptyList()
        if (limit <= 0) return emptyList()
        // Each combination of the objects its cost could use
        var combos: List<List<Pair<CostPart, List<ObjectId>>>> = listOf(emptyList())
        chosen.forEach { (part, options, _) -> combos = combos.flatMap { c -> options.map { o -> c + (part to o) } }.take(MAX_OBJECTS) }
        val partial = chosen.any { it.third } || (combos.size >= MAX_OBJECTS && chosen.size > 1)
        var damage = 0
        ability.instructions.steps.forEach { step ->
            when (val effect = (step as? Step.Do)?.effect) {
                is Effect.AddMana -> Unit
                // CR 120: to the player who activates it, whichever way the definition says "you"
                is Effect.DealDamage -> if (effect.to.players == arcanum.api.Players.You && effect.to.objects == null && effect.to.target == null) damage += eval.value(effect.amount) else unknown = true
                is Effect.LoseLife -> if (effect.players == arcanum.api.Players.You) damage += eval.value(effect.amount)
                else -> if (step !is Step.Choose) unknown = true
            }
        }
        if (damage > 0) tier = maxOf(tier, 1)
        return combos.mapNotNull { combo ->
            // An amount it announces: its colours are read with one mana (the search leaves it out: [Source.unknown])
            var names = ability.instructions.announced.fold(Bindings.EMPTY) { b, name -> b.with(name, NamedValue.Number(1)) }
            val taps = mutableSetOf<ObjectId>()
            val away = leaves.toMutableSet()
            combo.forEach { (part, ids) ->
                val value = NamedValue.Objects(ids)
                fun bind(vararg bound: String?) = bound.filterNotNull().forEach { names = names.with(it, value) }
                when (part) {
                    is CostPart.Sacrifice -> { away += ids; bind(part.preBind, part.postBind) }
                    is CostPart.Exile -> { away += ids; bind(part.preBind, part.postBind) }
                    is CostPart.Discard -> { away += ids; bind(part.preBind, part.postBind) }
                    is CostPart.ReturnToHand -> { away += ids; bind(part.preBind, part.postBind) }
                    is CostPart.TapObjects -> { taps += ids; bind(part.bind) }
                    else -> Unit
                }
            }
            val ys = ability.instructions.steps.flatMap { step ->
                yields(snap, player, id, ability.copy(instructions = ability.instructions.copy(steps = listOf(withChosenColors(ability, step)))), names)
            }
            // Mana only inside other steps ("you may pay {1}; if you do, add {G}{G}") can't be read here
            val cantRead = unknown || ability.instructions.steps.none { (it as? Step.Do)?.effect is Effect.AddMana }
            if (ys.isEmpty() && !cantRead) return@mapNotNull null
            Source(id, inst.origin, ability, ys, 0, exclusive, ownCost = own, life = life, damage = damage, tier = tier, taps = taps, leaves = away,
                answers = combo.map { (_, ids) -> Answer.Objects(ids) }, limit = if (exclusive) 1 else limit,
                bonus = if (tapsSelf) triggeredMana(snap, id, ys, triggers) else emptyList(), unknown = cantRead, partial = partial)
        }
    }

    /** "Whenever you tap a land for mana, add …" (CR 605.1b) as [player] would get it: what taps it, and what it adds. */
    private class ManaTrigger(val eval: Evaluator, val pattern: arcanum.api.EventPattern.Tap, val productions: List<ManaProduction>)

    private fun manaTriggers(snap: Snapshot, player: PlayerId): List<ManaTrigger> = snap.state.battlefield.flatMap { id ->
        snap.view[id]?.characteristics?.abilities.orEmpty().mapNotNull { inst ->
            val ability = inst.ability as? Ability.Triggered ?: return@mapNotNull null
            if (!arcanum.engine.trigger.Triggers.isManaTrigger(ability)) return@mapNotNull null
            val on = ability.trigger as arcanum.api.Trigger.On
            val controller = snap.board.controller(id) ?: return@mapNotNull null
            val productions = ability.instructions.steps.mapNotNull { ((it as? Step.Do)?.effect as? Effect.AddMana) }
                .filter { add -> add.players != arcanum.api.Players.You || controller == player }.map { it.mana }
            val pattern = on.events.firstOrNull() as? arcanum.api.EventPattern.Tap ?: return@mapNotNull null
            ManaTrigger(snap.evaluator(controller, self = id), pattern, productions)
        }
    }

    /** What the triggered mana abilities (CR 605.1b) add when [id] taps for the mana [yields]. */
    private fun triggeredMana(snap: Snapshot, id: ObjectId, yields: List<Yield>, triggers: List<ManaTrigger>): List<Yield> = triggers.flatMap { t ->
        if (!t.eval.matches(t.pattern.objects, id)) return@flatMap emptyList()
        t.pattern.producing?.let { wanted ->
            val want = wanted.symbols.map { (it as? ManaSymbol.Colored)?.color }
            if (yields.none { y -> y.colors.any { it in want } }) return@flatMap emptyList()
        }
        t.productions.flatMap { production ->
            when (val kind = production.kind) {
                is ManaKind.SameAsProduced -> {
                    val colors = yields.flatMap { it.colors }.distinct()
                    val amount = t.eval.value(kind.amount)
                    if (amount <= 0 || colors.isEmpty()) emptyList()
                    else listOf(Yield(colors, amount, choice = colors.size > 1, production, snow = false))
                }
                else -> yieldsOf(snap, t.eval, production, snow = false, depth = 0)
            }
        }
    }

    private fun activatableByOthers(inst: AbilityInstance): Boolean =
        (inst.ability as? Ability.Activated)?.rules?.activatedBy != arcanum.api.PlayerFilter.You

    /** Cheaper sources are used first: lands before other permanents, few colours before many, nothing given up. */
    private fun penalty(snap: Snapshot, source: Source, colors: Int): Int {
        val c = snap.view[source.id]?.characteristics
        var p = colors * 2
        if (c?.isType(CardType.Creature) == true) p += 10
        if (c?.isType(CardType.Land) != true) p += 3
        if (source.ownCost != null) p += 5
        if (source.damage > 0) p += 30
        if (source.life > 0) p += 40
        if (source.leaves.isNotEmpty()) p += 100
        if (source.amount > 1) p += 1
        return p
    }

    // ---- Paying

    /** One requirement of a cost: one of [alternatives], each a list of single-mana needs, or 2 life when [life]. */
    data class Need(val symbolIndex: Int, val alternatives: List<List<(Payer) -> Boolean>>, val life: Boolean, val generic: Boolean)

    /** The least mana that pays [cost], Phyrexian symbols paid with life (X symbols as replaced, [withX]). */
    fun leastMana(cost: ManaCost): Long = cost.symbols.sumOf { s ->
        when (s) {
            is ManaSymbol.Generic -> s.amount.toLong()
            is ManaSymbol.Variable, is ManaSymbol.Phyrexian, is ManaSymbol.PhyrexianHybrid -> 0L
            else -> 1L
        }
    }

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
    fun assign(needs: List<Need>, payers: List<Payer>, stopped: (() -> Unit)? = null): List<Payer>? {
        val ordered = needs.filterNot { it.life }.sortedBy { if (it.generic) 2 else if (it.alternatives.size > 1 || it.alternatives.firstOrNull()?.size != 1) 1 else 0 }
        val used = BooleanArray(payers.size)
        var budget = 20_000
        fun go(i: Int): Boolean {
            if (budget-- <= 0) { stopped?.invoke(); return false }
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

    /** Payers from mana in [player]'s pool that may be spent on [paysFor] (CR 106.6); [anyColor]: spent as though it were
     * mana of any colour for it anyway (ecl: a permission that says so, CR 609.4b). */
    fun poolPayers(snap: Snapshot, player: PlayerId, paysFor: ObjectId?, indices: List<Int>? = null, anyColor: Boolean = false): List<Payer> {
        val pool = snap.state.player(player).manaPool
        val anyColor = anyColor || spendsAsAnyColor(snap, player, paysFor)
        val anyType = spendsAsAnyType(snap, player, paysFor)
        return pool.withIndex().filter { (i, u) -> (indices == null || i in indices) && allowed(snap, player, u, paysFor) }
            .map { (i, u) -> Payer(colorsOf(u, anyColor, anyType), u.snow, ManaUse.Pool(i), u) }
    }

    /** fdn: "You can spend mana of any type" for [player]: as though it were colorless or any colour (CR 118.14). */
    fun spendsAsAnyType(snap: Snapshot, player: PlayerId, paysFor: ObjectId?): Boolean =
        snap.view.players[player]?.mechanics.orEmpty().any { applied ->
            val m = applied.mechanic as? Mechanic.SpendManaAsAnyColor ?: return@any false
            m.anyType && (m.onlyFor == null || (paysFor != null && snap.evaluator(applied.controller, self = applied.source).matches(m.onlyFor!!, paysFor)))
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

    /** Payers for the other ways the spell may pay ([ManaPaymentWay], CR 702.51, 702.66, 702.126); [ManaPaymentWay.Waterbend] is listed
     * once for each generic mana of the waterbend cost (tla, CR 701.67b), and [suggest] uses at most that many of its payers. */
    fun specialPayers(snap: Snapshot, player: PlayerId, ways: List<ManaPaymentWay>, exclude: Set<ObjectId> = emptySet()): List<Payer> =
        ways.distinct().flatMap { way ->
            when (way) {
                // tla: CR 701.67a: an untapped artifact or creature for one generic mana
                ManaPaymentWay.Waterbend -> snap.state.battlefield.filter { id ->
                    val o = snap.state.obj(id)
                    o != null && !o.tapped && !o.phasedOut && snap.board.controller(id) == player && id !in exclude &&
                        snap.view[id]?.characteristics?.let { it.isType(CardType.Artifact) || it.isType(CardType.Creature) } == true
                }.map { id -> Payer(emptySet(), snow = false, use = ManaUse.Special(way, listOf(id))) }
                ManaPaymentWay.TapCreatures -> untapped(snap, player, CardType.Creature).filter { it !in exclude }.map { id ->
                    Payer((snap.view[id]?.characteristics?.colors.orEmpty() + listOf<Color?>()).toSet(), snow = false,
                        use = ManaUse.Special(way, listOf(id)))
                }
                // blb gaps 2: any one mana of the cost, whatever the creature's colour
                ManaPaymentWay.TapCreaturesForAnyMana -> untapped(snap, player, CardType.Creature).filter { it !in exclude }.map { id ->
                    Payer((Color.all + listOf<Color?>(null)).toSet(), snow = false, use = ManaUse.Special(way, listOf(id)))
                }
                ManaPaymentWay.TapArtifacts -> untapped(snap, player, CardType.Artifact).filter { it !in exclude }.map { id ->
                    Payer(emptySet(), snow = false, use = ManaUse.Special(way, listOf(id)))
                }
                ManaPaymentWay.ExileFromGraveyard -> snap.state.player(player).graveyard.filter { it !in exclude }.map { id ->
                    Payer(emptySet(), snow = false, use = ManaUse.Special(way, listOf(id)))
                }
            }
        }

    /** tla: at most as many waterbend payers as the waterbend cost has generic mana (CR 701.67b). */
    private fun capped(payers: List<Payer>, ways: List<ManaPaymentWay>): List<Payer> {
        val cap = ways.count { it == ManaPaymentWay.Waterbend }
        val waterbend = payers.filter { (it.use as? ManaUse.Special)?.way == ManaPaymentWay.Waterbend }
        return payers.filter { it !in waterbend } + waterbend.take(cap)
    }

    private fun untapped(snap: Snapshot, player: PlayerId, type: CardType): List<ObjectId> = snap.state.battlefield.filter { id ->
        val o = snap.state.obj(id)
        o != null && !o.tapped && !o.phasedOut && snap.board.controller(id) == player && snap.view[id]?.characteristics?.isType(type) == true
    }

    // ---- The decision's options and suggestion

    /** Ways [player] could pay [cost] now (CR 601.2g–h, 605.3a): pool mana, every mana ability with each colour it can make and
     * each set of objects its cost could use, life for Phyrexian symbols, and the spell's other ways. */
    fun options(
        snap: Snapshot, player: PlayerId, cost: ManaCost, paysFor: ObjectId?, ways: List<ManaPaymentWay> = emptyList(), anyColor: Boolean = false,
        reserved: Reserved = Reserved.NONE,
    ): List<ManaUse> =
        buildList {
            poolPayers(snap, player, paysFor, anyColor = anyColor).forEach { add(it.use) }
            sources(snap, player, reserved).forEach { s ->
                if (s.ability.instructions.announced.isNotEmpty()) {
                    // An amount it announces: the amounts a payment can use (what its cost then asks is checked as it is paid)
                    (1..MAX_ANNOUNCED).forEach { n -> picks(s).forEach { p -> add(use(p).let { u -> u.copy(answers = listOf<Answer>(Answer.Number(n)) + u.answers) }) } }
                } else picks(s).forEach { add(use(it)) }
            }
            cost.symbols.forEachIndexed { i, s -> if (s is ManaSymbol.Phyrexian || s is ManaSymbol.PhyrexianHybrid) add(ManaUse.Life(i)) }
            specialPayers(snap, player, ways, reserved.tapped).forEach { add(it.use) }
        }

    /**
     * A payment of [cost] that keeps the most options open, or empty when none is found (§7.8, owner's order, castability §4): pool
     * mana first; then sources with no side effects, the cheapest first (lands before creatures, single colours before many);
     * then damage or life; then sacrifices; a source whose damage would be lethal only when it is the only way. Phyrexian symbols
     * by mana when it can be found, else by life you have. The spell's other ways to pay [ways] (convoke, delve, improvise, CR
     * 702.51, 702.66, 702.126) are used only when mana alone isn't found, or alone when mana can't be spent on it ([canSpendMana]
     * false); a creature tapped for mana isn't also tapped for convoke. [reserved]: what the rest of the cost uses. [pinned]: uses
     * the payment must hold (a player's picks), [removed]: uses it must not. This is a convenience: it never decides whether
     * something can be paid ([verdict] does).
     */
    fun suggest(
        snap: Snapshot, player: PlayerId, cost: ManaCost, paysFor: ObjectId?,
        ways: List<ManaPaymentWay> = emptyList(), canSpendMana: Boolean = true, anyColor: Boolean = false,
        reserved: Reserved = Reserved.NONE, pinned: List<ManaUse> = emptyList(), removed: List<ManaUse> = emptyList(),
    ): List<ManaUse> {
        if (cost.symbols.all { it is ManaSymbol.Generic && it.amount == 0 || it is ManaSymbol.Variable }) return emptyList()
        val payment = PaymentSearch(snap, player, cost, paysFor, ways, canSpendMana, anyColor, reserved, pinned, removed)
        if (payment.impossible()) return emptyList()
        // Without other ways, then with them; each from the fewest side effects up, never a lethal source; then lethal ones
        val wayRounds = if (!canSpendMana) listOf(true) else if (ways.isEmpty()) listOf(false) else listOf(false, true)
        for (keepAlive in listOf(true, false)) for (withWays in wayRounds) for (tier in if (keepAlive) 0..2 else 2..2) {
            payment.search(tier, withWays, keepAlive)?.let { return payment.withPinned(it) }
        }
        return emptyList()
    }

    /**
     * Whether a payment of [cost] can exist ([Verdict.Found], with one), is shown not to ([Verdict.None]), or the search couldn't
     * tell ([Verdict.Maybe]): stopped by a bound, or a source that can't be read ([Source.unknown], [Source.partial]). A cast is
     * denied only on [Verdict.None] (castability §1.1).
     */
    fun verdict(
        snap: Snapshot, player: PlayerId, cost: ManaCost, paysFor: ObjectId?,
        ways: List<ManaPaymentWay> = emptyList(), canSpendMana: Boolean = true, anyColor: Boolean = false, reserved: Reserved = Reserved.NONE,
    ): Verdict {
        if (cost.symbols.all { it is ManaSymbol.Generic && it.amount == 0 || it is ManaSymbol.Variable }) return Verdict.Found(emptyList())
        val payment = PaymentSearch(snap, player, cost, paysFor, ways, canSpendMana, anyColor, reserved, emptyList(), emptyList())
        if (payment.impossible()) return if (payment.cantTell) Verdict.Maybe else Verdict.None
        payment.search(2, withWays = ways.isNotEmpty() || !canSpendMana, keepAlive = false)?.let { return Verdict.Found(it) }
        return if (payment.cantTell) Verdict.Maybe else Verdict.None
    }

    /** [verdict] isn't [Verdict.None]. */
    fun mayPay(
        snap: Snapshot, player: PlayerId, cost: ManaCost, paysFor: ObjectId?,
        ways: List<ManaPaymentWay> = emptyList(), canSpendMana: Boolean = true, anyColor: Boolean = false, reserved: Reserved = Reserved.NONE,
    ): Boolean = verdict(snap, player, cost, paysFor, ways, canSpendMana, anyColor, reserved) != Verdict.None

    /** One activation of a source as a search tries it, with the colours it makes. */
    private class Pick(val source: Source, val color: Color?, val combination: List<Color> = emptyList(), val triggered: List<Color> = emptyList())

    /** The colours to try for [s]: none to choose, each colour of a choice, or each way of a combination (CR 106.1). */
    private fun picks(s: Source): List<Pick> {
        val own = ownPicks(s)
        // CR 605.4a: what its triggered mana abilities let be chosen: each colour too
        val free = freeBonus(s)
        if (free.isEmpty()) return own
        var ways: List<List<Color>> = listOf(emptyList())
        free.forEach { y -> ways = ways.flatMap { w -> y.colors.filterNotNull().map { w + it } } }
        return own.flatMap { p -> ways.map { Pick(s, p.color, p.combination, it) } }
    }

    private fun ownPicks(s: Source): List<Pick> {
        val choosing = s.yields.filter { it.choice }
        if (choosing.isEmpty()) return listOf(Pick(s, null))
        val colors = choosing.flatMap { it.colors }.distinct()
        val combination = choosing.firstOrNull { it.combination } ?: return colors.map { Pick(s, it) }
        val each = combination.colors.filterNotNull()
        var ways: List<List<Color>> = listOf(emptyList())
        repeat(minOf(combination.amount, MAX_COMBINED)) { ways = ways.flatMap { w -> each.filter { c -> w.isEmpty() || each.indexOf(c) >= each.indexOf(w.last()) }.map { w + it } } }
        return ways.map { Pick(s, it.first(), it) }
    }

    /** The mana a source's triggered mana abilities add whose colour is chosen as they resolve: not "any type that land produced" when the
     * source itself made just the one colour picked (CR 106.7). */
    private fun freeBonus(s: Source): List<Yield> = s.bonus.filter { y -> y.choice && !(y.production.kind is ManaKind.SameAsProduced && s.yields.any { it.choice }) }

    private fun use(p: Pick): ManaUse.Ability {
        val s = p.source
        val colors = p.combination.map { Answer.ColorChoice(it) }
        return ManaUse.Ability(s.id, s.origin, if (s.yields.any { it.choice }) p.color else s.colors.singleOrNull(), answers = s.answers + colors, triggered = p.triggered)
    }

    private fun colorsOf(unit: ManaUnit, any: Boolean, anyType: Boolean = false): Set<Color?> = when {
        anyType -> (Color.all + listOf<Color?>(null)).toSet()
        any -> (Color.all + unit.color).toSet()
        else -> setOf(unit.color)
    }

    /**
     * Searching one payment of a cost (CR 601.2g–h): which activations, with which colours, cover the cost and each other's own
     * mana in some order (a chain: a Plains into a Signet). The search is exhaustive unless [cantTell] says otherwise.
     */
    private class PaymentSearch(
        val snap: Snapshot, val player: PlayerId, val cost: ManaCost, val paysFor: ObjectId?, val ways: List<ManaPaymentWay>, val canSpendMana: Boolean,
        anyColor: Boolean, val reserved: Reserved, val pinned: List<ManaUse>, removed: List<ManaUse>,
    ) {
        val life = snap.state.player(player).life
        val any = anyColor || spendsAsAnyColor(snap, player, paysFor)
        val anyType = spendsAsAnyType(snap, player, paysFor)
        private val removedPool = removed.filterIsInstance<ManaUse.Pool>().map { it.index }.toSet()
        val pool: List<Payer> = if (!canSpendMana) emptyList() else poolPayers(snap, player, paysFor, anyColor = any).filter { (it.use as ManaUse.Pool).index !in removedPool }
        private val pinnedSources = pinned.filterIsInstance<ManaUse.Ability>()
        private val removedSources = removed.filterIsInstance<ManaUse.Ability>()
        private val all: List<Source> = if (!canSpendMana) emptyList() else sources(snap, player, reserved).filter { s -> removedSources.none { sameSource(it, s) } }
        private val searched = all.filter { !it.unknown }
        private val phyrexian = cost.symbols.withIndex().filter { it.value is ManaSymbol.Phyrexian || it.value is ManaSymbol.PhyrexianHybrid }.map { it.index }
        private val mustLife = pinned.filterIsInstance<ManaUse.Life>().map { it.symbol }.toSet()
        private val noLife = removed.filterIsInstance<ManaUse.Life>().map { it.symbol }.toSet()
        private val specials: List<Payer> = capped(specialPayers(snap, player, ways, reserved.tapped).filter { p -> removed.none { it == p.use } }, ways)
        /** Anything that may change what a source's damage does to its controller means its damage can't deny anything (CR 615, 616, 702.15,
         * 702.16e): a replacement of damage, prevention (static or an effect's, a shield too, CR 615.7), a player's protection, lifelink on a source. */
        val damageSure = snap.view.rules.none { it.mechanic is Mechanic.Prevention || (it.mechanic as? Mechanic.Replacement)?.event is arcanum.api.EventPattern.Damage } &&
            snap.state.effects.values.none { e -> e.mechanics.any { it is Mechanic.Prevention || (it as? Mechanic.Replacement)?.event is arcanum.api.EventPattern.Damage } } &&
            snap.view.players[player]?.mechanics.orEmpty().none { it.mechanic is Mechanic.Protection } &&
            all.none { s -> s.damage > 0 && snap.view[s.id]?.characteristics?.hasKeyword(arcanum.api.Keyword.Lifelink) == true }
        /** A bound stopped a search, or a source wasn't searched: "not found" proves nothing. */
        var cantTell = all.any { it.unknown || it.partial }

        private fun sameSource(use: ManaUse.Ability, s: Source): Boolean {
            val objects = use.answers.filterIsInstance<Answer.Objects>()
            return use.source == s.id && (objects.isEmpty() || objects == s.answers.filterIsInstance<Answer.Objects>())
        }

        /** The Phyrexian symbols that may be paid with life (CR 107.4f): the fewest first, the last symbols first, every subset, only
         * with that much life (CR 119.4). */
        private val lifeWays: List<Set<Int>> = run {
            val free = phyrexian.filter { it !in mustLife && it !in noLife }
            val found = mutableListOf<Set<Int>>()
            fun pick(from: Int, left: Int, have: Set<Int>) {
                if (left == 0) { found += have + mustLife; return }
                for (i in from downTo 0) pick(i - 1, left - 1, have + free[i])
            }
            for (n in 0..free.size) pick(free.lastIndex, n, emptySet())
            found.filter { 2 * it.size + reserved.paidLife <= life }
        }

        private fun allPayersOf(s: Source): List<Payer> {
            val colors = (s.yields.flatMap { it.colors } + s.bonus.flatMap { it.colors }).toSet() + (if (any || anyType) Color.all else emptyList()) +
                (if (anyType) listOf<Color?>(null) else emptyList())
            val snow = s.yields.any { it.snow }
            val n = (s.yields + s.bonus).sumOf { y -> units(y, y.colors.firstOrNull(), s.id).count { allowed(snap, player, it, paysFor) } }.toLong() * minOf(s.limit.toLong(), 8L)
            return List(minOf(n, MAX_UNITS.toLong()).toInt()) { Payer(colors, snow, ManaUse.Life(-1)) }
        }

        /** Shown cheaply that nothing can pay: more mana than everything nets, or a colour nothing makes. */
        fun impossible(): Boolean {
            if (lifeWays.isEmpty()) return true
            // More mana than everything nets isn't worked out symbol by symbol (a huge X)
            val least = leastMana(cost)
            val net = pool.size + specials.size + searched.sumOf { s ->
                val own = s.ownCost?.let { leastMana(it) } ?: 0L
                maxOf(0L, s.amount.toLong() - own) * minOf(s.limit.toLong(), least + 1)
            }
            if (least > net) return true
            val gross = pool + specials + searched.flatMap { allPayersOf(it) }
            var stopped = false
            val found = lifeWays.any { assign(needs(cost, it), gross) { stopped = true } != null }
            if (stopped) cantTell = true
            return !found && !stopped
        }

        fun withPinned(uses: List<ManaUse>): List<ManaUse> = uses + pinned.filter { it !is ManaUse.Ability && it !in uses }

        /** The first payment found from sources of tier up to [tier]: with [withWays] the spell's other ways count, with
         * [keepAlive] nothing that would take the payer to 0 life or less. Null when none is found (see [cantTell]). */
        fun search(tier: Int, withWays: Boolean, keepAlive: Boolean): List<ManaUse>? {
            val required = pinnedSources.map { p -> searched.filter { sameSource(p, it) } }
            if (required.any { it.isEmpty() }) return null
            val from = searched.filter { it.tier <= tier && required.none { r -> it in r } }
            for (lifeFor in lifeWays) {
                val found = Search(this, needs(cost, lifeFor), if (withWays) pool + specials else pool, from, required,
                    reserved.paidLife + 2 * lifeFor.size, keepAlive).run()
                if (found != null) return found + lifeFor.sorted().map { ManaUse.Life(it) }
            }
            return null
        }
    }

    /** One depth-first search of [PaymentSearch]: sources are added one at a time, cheapest first, until the mana covers it. */
    private class Search(val p: PaymentSearch, val needs: List<Need>, val base: List<Payer>, val sources: List<Source>, val required: List<List<Source>>,
        val paidLife: Int, val keepAlive: Boolean) {
        private var budget = SEARCH_STEPS
        private val chosen = mutableListOf<Pick>()
        private fun manaOf(needs: List<Need>) = needs.sumOf { n -> n.alternatives.maxOfOrNull { it.size } ?: 0 }
        private val mostMana = manaOf(needs)
        /** A least payment uses each activation's mana: no more activations than the mana it pays, the own costs it may take included. */
        private val mostActivations = required.size + mostMana + (sources + required.flatten()).sumOf { s -> s.ownCost?.let { manaOf(needs(it)) } ?: 0 }

        fun run(): List<ManaUse>? {
            val found = pinned(0)
            if (budget <= 0) p.cantTell = true
            if (found == null) return null
            // Nothing tapped for no reason: a source a chain was begun with that the later one made unneeded goes (never a player's own pick)
            chosen.drop(required.size).forEach { pick ->
                chosen.remove(pick)
                if (payment() == null) chosen.add(pick)
            }
            return payment() ?: found
        }

        private fun pinned(i: Int): List<ManaUse>? {
            if (i == required.size) return go(0, chosen.sumOf { it.source.life }, chosen.sumOf { it.source.damage })
            for (s in required[i]) {
                if (!fits(s, chosen.sumOf { it.source.life }, chosen.sumOf { it.source.damage })) continue
                for (pick in picks(s)) {
                    chosen += pick
                    pinned(i + 1)?.let { return it }
                    chosen.removeAt(chosen.lastIndex)
                }
            }
            return null
        }

        private fun fits(s: Source, life: Int, damage: Int): Boolean {
            if (s.exclusive && chosen.any { it.source.exclusive && it.source.id == s.id }) return false
            if (chosen.count { it.source.id == s.id && it.source.origin == s.origin } >= s.limit) return false
            if (s.leaves.isNotEmpty() && chosen.any { c -> c.source.leaves.any { it in s.leaves } }) return false
            if (s.taps.isNotEmpty() && chosen.any { c -> c.source.taps.any { it in s.taps } || (c.source.id in s.taps && c.source.exclusive) }) return false
            if (s.exclusive && chosen.any { s.id in it.source.taps }) return false
            val paid = life + s.life
            // CR 119.4: life is paid only when there is that much; life costs come first, damage only limits what the cast then pays
            if (paid > p.life) return false
            val left = p.life - paid - if (p.damageSure) damage + s.damage else 0
            if (paidLife > 0 && left < paidLife) return false
            if (keepAlive && left - paidLife <= 0 && (s.life > 0 || s.damage > 0)) return false
            return true
        }

        private fun go(from: Int, life: Int, damage: Int): List<ManaUse>? {
            if (budget-- <= 0) return null
            payment()?.let { return it }
            if (chosen.size >= mostActivations) return null
            for (k in from until sources.size) {
                val s = sources[k]
                if (k > from && s.alike(sources[k - 1])) continue
                if (!fits(s, life, damage)) continue
                for (pick in picks(s)) {
                    chosen += pick
                    go(if (s.exclusive) k + 1 else k, life + s.life, damage + s.damage)?.let { return it }
                    chosen.removeAt(chosen.lastIndex)
                    if (budget <= 0) return null
                }
            }
            return null
        }

        private fun payersOf(pick: Pick): List<Payer> {
            val s = pick.source
            val use = use(pick)
            val free = freeBonus(s)
            val said = pick.triggered.iterator()
            val made = s.yields.flatMap { y ->
                if (y.combination && pick.combination.isNotEmpty()) pick.combination.flatMap { c -> units(y.copy(amount = 1), c, s.id) } else units(y, pick.color, s.id)
            } + s.bonus.flatMap { y -> units(y, if (y in free && said.hasNext()) said.next() else pick.color, s.id) }
            return made.map { u -> Payer(colorsOf(u, p.any, p.anyType), u.snow, use, u) }
        }

        /** The payment [chosen] makes, or null: its mana (in an order in which each own cost can be paid) covers the needs. */
        private fun payment(): List<ManaUse>? {
            val tapped = chosen.filter { it.source.exclusive }.map { it.source.id }.toSet()
            val from = if (base.none { it.use is ManaUse.Special }) base else base.filter { b ->
                (b.use as? ManaUse.Special)?.objects?.none { it in tapped } != false
            }
            val free = chosen.filter { it.source.ownCost == null }.flatMap { payersOf(it) }
            val funded = chosen.filter { it.source.ownCost != null }
            if (funded.isEmpty()) {
                val used = assign(needs, from + usable(free)) { p.cantTell = true } ?: return null
                return used.map { it.use }.filter { it is ManaUse.Pool || it is ManaUse.Special }.distinct() + chosen.map { use(it) }
            }
            val spent = mutableListOf<ManaUse>()
            return if (fund(from + free, funded, spent)) spent.distinct() + chosen.map { use(it) } else null
        }

        private fun usable(payers: List<Payer>) = payers.filter { a -> a.unit == null || allowed(p.snap, p.player, a.unit, p.paysFor) }

        /** Whether [rest] can each be paid in some order from [payers] and what they add, the needs then covered. */
        private fun fund(payers: List<Payer>, rest: List<Pick>, spent: MutableList<ManaUse>): Boolean {
            if (rest.isEmpty()) {
                val used = assign(needs, usable(payers)) { p.cantTell = true } ?: return false
                spent += used.map { it.use }.filter { it is ManaUse.Pool || it is ManaUse.Special }
                return true
            }
            for (next in rest) {
                val own = needs(next.source.ownCost!!)
                for (paid in assignments(own, payers.filter { it.use !is ManaUse.Special }, stopped = { p.cantTell = true })) {
                    val mark = spent.size
                    spent += paid.map { it.use }.filter { it is ManaUse.Pool }
                    val left = payers.toMutableList().also { list -> paid.forEach { list.remove(it) } }
                    if (fund(left + payersOf(next), rest - next, spent)) return true
                    while (spent.size > mark) spent.removeAt(spent.lastIndex)
                    if (budget-- <= 0) return false
                }
            }
            return false
        }
    }

    /**
     * The different ways [payers] can cover [needs] by what they are (one of each kind for a need), at most [limit]: what a mana
     * ability's own cost could be paid with, for a search or a payment to choose what leaves the rest payable. [stopped]: told when the limit
     * left ways untried, so a search that found nothing proves nothing.
     */
    fun assignments(needs: List<Need>, payers: List<Payer>, limit: Int = 24, stopped: (() -> Unit)? = null): List<List<Payer>> {
        val found = LinkedHashMap<List<String>, List<Payer>>()
        val used = BooleanArray(payers.size)
        val picked = mutableListOf<Payer>()
        fun go(i: Int) {
            if (found.size >= limit) { stopped?.invoke(); return }
            if (i == needs.size) {
                found.getOrPut(picked.map { "${it.colors}/${it.snow}" }.sorted()) { picked.toList() }
                return
            }
            for (alternative in needs[i].alternatives) {
                fun pick(k: Int) {
                    if (k == alternative.size) return go(i + 1)
                    val tried = mutableSetOf<Pair<Set<Color?>, Boolean>>()
                    for (j in payers.indices) {
                        if (used[j] || !alternative[k](payers[j]) || !tried.add(payers[j].colors to payers[j].snow)) continue
                        used[j] = true; picked += payers[j]
                        pick(k + 1)
                        used[j] = false; picked.removeAt(picked.lastIndex)
                    }
                }
                pick(0)
            }
        }
        go(0)
        return found.values.toList()
    }

    /**
     * The pool mana (indices among [funds]) a mana ability's [own] cost spends: among the ways to cover it, the first that leaves
     * the rest coverable, [needs] and what [later] abilities ask, from the mana left and what they add. Refused when nothing
     * covers it; with nothing known of later abilities, the first way.
     */
    fun spendFor(snap: Snapshot, player: PlayerId, own: ManaCost, paysFor: ObjectId?, funds: List<Int>, later: List<ManaUse.Ability>, needs: List<Need>): List<Int> {
        val payers = poolPayers(snap, player, paysFor, funds)
        val ways = assignments(needs(own), payers)
        require(ways.isNotEmpty()) { "Nothing pays $own" }
        val all = sources(snap, player)
        val coming = later.map { use -> all.firstOrNull { it.id == use.source && it.origin == use.ability } }
        val best = if (coming.any { it == null }) null else ways.firstOrNull { way ->
            val left = payers.toMutableList().also { list -> way.forEach { list.remove(it) } }
            val made = coming.filterNotNull().flatMap { s -> List(s.amount) { Payer((s.yields + s.bonus).flatMap { it.colors }.toSet(), s.yields.any { it.snow }, ManaUse.Life(-1)) } }
            val asked = needs + coming.filterNotNull().flatMap { s -> s.ownCost?.let { needs(it) }.orEmpty() }
            assign(asked, left + made) != null
        }
        return (best ?: ways.first()).map { (it.use as ManaUse.Pool).index }
    }

    private const val SEARCH_STEPS = 3_000
    /** How many objects a cost's choice of one is tried with, in a search. */
    private const val MAX_OBJECTS = 8
    private const val MAX_UNITS = 64
    /** Amounts offered for an ability that announces how much it adds. */
    private const val MAX_ANNOUNCED = 8
    /** The most mana a combination's colours are worked out for one by one. */
    private const val MAX_COMBINED = 5

    /** Colours as named values, for a mana ability's colour choice. */
    fun colorChoice(color: Color): NamedValue = NamedValue.Colors(listOf(color))
}

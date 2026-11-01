package arcanum.engine.casting

import arcanum.api.Ability
import arcanum.api.CardType
import arcanum.api.Color
import arcanum.api.Cost
import arcanum.api.CostChange
import arcanum.api.CostPart
import arcanum.api.Effect
import arcanum.api.Instructions
import arcanum.api.Keyword
import arcanum.api.ManaCost
import arcanum.api.ManaKind
import arcanum.api.ManaPaymentWay
import arcanum.api.Mechanic
import arcanum.api.ObjectFilter
import arcanum.api.Step
import arcanum.api.TargetKind
import arcanum.api.TargetSlot
import arcanum.api.Value
import arcanum.api.Zone
import arcanum.engine.decision.AnnounceStage
import arcanum.engine.decision.Announced
import arcanum.engine.decision.Continuation
import arcanum.engine.decision.Decision
import arcanum.engine.decision.DecisionId
import arcanum.engine.decision.ManaUse
import arcanum.engine.decision.ModeOption
import arcanum.engine.input.CastWay
import arcanum.engine.mana.Mana
import arcanum.engine.query.Evaluator
import arcanum.engine.state.Bindings
import arcanum.engine.state.GameState
import arcanum.engine.state.NamedValue
import arcanum.engine.state.ObjectId
import arcanum.engine.state.PlayerId
import arcanum.engine.state.StackItem
import arcanum.engine.state.StackItemKind
import arcanum.engine.state.TargetRef
import arcanum.engine.view.AbilityInstance
import arcanum.engine.view.AbilityOrigin
import arcanum.engine.view.AppliedMechanic

/** What a cast or activation asks next (CR 601.2b–h), in rules order. */
internal sealed interface Question {
    val stage: AnnounceStage

    data class Modes(val modes: arcanum.api.Modes) : Question { override val stage get() = AnnounceStage.Modes }
    data class OptionalCost(val key: String, val mechanic: Mechanic.AdditionalCost) : Question { override val stage get() = AnnounceStage.Modes }
    data class Announce(val name: String, val min: Int) : Question { override val stage get() = AnnounceStage.Announce }
    data class Target(val slot: SlotOf, val forPlayer: PlayerId?, val options: List<TargetRef>, val min: Int, val max: Int?) : Question {
        override val stage get() = AnnounceStage.Targets
    }
    data class Divide(val key: String, val amount: Int, val among: List<TargetRef>) : Question { override val stage get() = AnnounceStage.Divide }
    data class OneOf(val key: String, val part: CostPart.OneOf, val labels: List<String>) : Question { override val stage get() = AnnounceStage.Costs }
    data class Objects(val key: String, val choice: Costs.ObjectChoice) : Question { override val stage get() = AnnounceStage.Costs }
    data class ManaColor(val key: String, val among: List<Color>) : Question { override val stage get() = AnnounceStage.Costs }
    data class Pay(val cost: ManaCost) : Question { override val stage get() = AnnounceStage.Mana }
}

/**
 * A cast or activation worked out from its continuation (CR 601.2, 602.2): the spell's or ability's instructions, its
 * Stack item as chosen so far (put on a copy of the state, so filters that ask about "this spell" see it), its total
 * cost, and the next question. Recomputed on every call, so answers are read against the same question that was asked.
 */
internal class Plan(base: GameState, val c: Continuation.Announcing) {
    val player: PlayerId = c.player
    val choices = c.choices
    val isSpell = c.what is Announced.Spell
    val way: CastWay = (c.what as? Announced.Spell)?.way ?: CastWay()

    /** The spell's card on the Stack, or the ability's Stack item id. */
    val itemId: ObjectId = when (val w = c.what) {
        is Announced.Spell -> w.card
        is Announced.Ability -> ObjectId.StackItem((choices.names[ITEM] as? NamedValue.Option)?.option ?: error("No item id"))
        is Announced.Trigger -> error("Triggers are put on the Stack by the triggers part")
    }

    /** The spell itself, or the object the ability is of. */
    val sourceId: ObjectId = (c.what as? Announced.Ability)?.source ?: itemId
    val ability: Ability? = (c.what as? Announced.Ability)?.ability
    val origin: AbilityOrigin? = (c.what as? Announced.Ability)?.origin
    val isManaAbility = ability != null && Legality.isManaAbility(ability)
    val isSpecialAction = ability is Ability.SpecialAction

    /** Before any provisional item: what the card and the board are now. */
    private val plain = Snapshot(base)

    val castFrom: Zone? = ((choices.names[FROM] as? NamedValue.Option)?.option)?.let { zoneByName(it) }

    /** The alternative cost used: its static ability (looked up on the card on the Stack) and mechanic. */
    val alternative: Pair<AbilityInstance, Mechanic.AlternativeCost>? = if (!isSpell || way.alternative == null) null
        else plain.view[itemId]?.characteristics?.abilities.orEmpty().firstOrNull { it.origin == way.alternative }?.let { inst ->
            (inst.ability as? Ability.Static)?.mechanics?.filterIsInstance<Mechanic.AlternativeCost>()?.firstOrNull()?.let { inst to it }
        }

    /** The permission used to cast it from elsewhere than the hand (D41): found by where it was granted. */
    val permission: Pair<Mechanic.MayPlay, AbilityOrigin?>? = way.permission?.let { p -> permissionMechanic(plain, p) }

    val overloaded = alternative?.second?.keyword == Keyword.Overload.Kind
    val bestowed = alternative?.second?.keyword == Keyword.Bestow.Kind

    /** What it does: the spell abilities of the face cast (overload's own when overloaded, CR 702.96b), or the
     * ability's; an Aura spell's enchant target (CR 303.4a, bestow 702.103b) is the slot [ENCHANT_SLOT]. */
    val instructions: Instructions = run {
        val base = if (isSpell) spellInstructions() else when (val a = ability) {
            is Ability.Activated -> a.instructions
            is Ability.SpecialAction -> a.instructions
            else -> Instructions()
        }
        val enchant = if (isSpell) enchantSlot() else null
        if (enchant != null) base.copy(targets = listOf(enchant) + base.targets) else base
    }

    /** The provisional Stack item: everything chosen so far. */
    val item: StackItem = StackItem(
        id = itemId,
        kind = if (isSpell) StackItemKind.Spell else StackItemKind.Activated,
        controller = player,
        source = sourceId,
        ability = if (isSpell) spellAbility() else ability,
        origin = if (isSpell) spellOrigin() else origin,
        targets = choices.targets,
        divided = choices.divided,
        modes = choices.modes,
        announced = choices.announced,
        names = publicNames(choices.names),
        snapshots = choices.snapshots,
        timestamp = base.nextTimestamp(),
    )

    /** The board with the provisional item on the Stack. */
    val snap: Snapshot = Snapshot(base.deepCopy().also { s ->
        s.stackItems[itemId] = item
        if (!isSpell && itemId !in s.stack) s.stack.add(itemId)
    })

    /** Names filters and values see: choices so far, announced values and targets by slot. */
    val names: Bindings = run {
        var b = choices.names
        choices.announced.forEach { (k, v) -> b = b.with(k, NamedValue.Number(v)) }
        choices.targets.forEach { (k, v) -> b = b.with(k, NamedValue.Targets(v)) }
        b
    }

    val eval: Evaluator = snap.evaluator(player, self = sourceId, names = names, stackItem = item)

    // ---- Instructions

    private fun spellAbilities(): List<AbilityInstance> =
        snap0Abilities().filter { it.ability is Ability.Spell }

    private fun snap0Abilities(): List<AbilityInstance> = plain.view[itemId]?.characteristics?.abilities.orEmpty()

    private fun spellInstructions(): Instructions {
        if (overloaded) {
            snap0Abilities().firstNotNullOfOrNull { (it.ability as? Keyword.Overload)?.instructions }?.let { return it }
        }
        val all = spellAbilities().map { (it.ability as Ability.Spell).instructions }
        return when (all.size) {
            0 -> Instructions()
            1 -> all.single()
            else -> Instructions(
                targets = all.flatMap { it.targets }, modes = all.firstNotNullOfOrNull { it.modes },
                steps = all.flatMap { it.steps }, announced = all.flatMap { it.announced }.distinct(),
                replacements = all.flatMap { it.replacements },
            )
        }
    }

    private fun spellAbility(): Ability? = when {
        overloaded -> Ability.Spell(spellInstructions())
        spellAbilities().size == 1 -> spellAbilities().single().ability
        spellAbilities().isEmpty() -> null
        else -> Ability.Spell(spellInstructions())
    }

    private fun spellOrigin(): AbilityOrigin? = if (overloaded) alternative?.first?.origin else spellAbilities().firstOrNull()?.origin

    private fun enchantSlot(): TargetSlot? {
        if (bestowed) {
            // CR 702.103b: an Aura spell with enchant creature
            return TargetSlot(ENCHANT_SLOT, TargetKind.Objects(ObjectFilter.Type(CardType.Creature)), Value.Fixed(1), Value.Fixed(1))
        }
        val c = plain.view[itemId]?.characteristics ?: return null
        if (!c.isType(CardType.Enchantment) || c.subtypes.none { it.name == "Aura" }) return null
        val enchant = c.keywords.filterIsInstance<Keyword.Enchant>().firstOrNull() ?: return null
        return TargetSlot(ENCHANT_SLOT, enchant.what, Value.Fixed(1), Value.Fixed(1))
    }

    // ---- Costs

    /** Static abilities of the spell with [Mechanic.AdditionalCost], by origin. */
    val additionalCosts: List<Pair<AbilityInstance, Mechanic.AdditionalCost>> = if (!isSpell) emptyList() else
        snap0Abilities().flatMap { inst ->
            (inst.ability as? Ability.Static)?.mechanics.orEmpty().filterIsInstance<Mechanic.AdditionalCost>().map { inst to it }
        }

    fun optionalKey(inst: AbilityInstance) = "opt:${inst.origin.key}"

    /** How often the optional cost of [inst] was chosen; null while not asked. */
    fun optionalTimes(inst: AbilityInstance): Int? = (choices.costs[optionalKey(inst)] as? NamedValue.Number)?.value

    val contributions: List<Costs.Contribution> = buildList {
        if (isSpell) {
            val base: Cost? = when {
                permission?.first?.withoutPaying == true -> null
                // CR 702.37c, 708.4: a face-down spell costs {3}
                choices.names[Casting.FACE_DOWN] != null -> Cost.mana(ManaCost.parse("{3}")!!)
                alternative != null -> alternative.second.cost
                permission?.first?.cost != null -> permission.first.cost
                else -> plain.view[itemId]?.characteristics?.manaCost?.let { Cost.mana(it) }
                    ?: (if (snap0Face()?.manaCost != null) Cost.mana(snap0Face()!!.manaCost!!) else null)
            }
            base?.let { add(Costs.Contribution("base", it)) }
            additionalCosts.forEach { (inst, m) ->
                val key = "add:${inst.origin.key}"
                when {
                    m.optional -> optionalTimes(inst)?.takeIf { it > 0 }?.let { add(Costs.Contribution(key, m.cost, it)) }
                    m.times != null -> eval.value(m.times!!).takeIf { it > 0 }?.let { add(Costs.Contribution(key, m.cost, it)) }
                    else -> add(Costs.Contribution(key, m.cost))
                }
            }
            choices.modes.forEachIndexed { p, index ->
                instructions.modes?.options?.getOrNull(index)?.extraCost?.let { add(Costs.Contribution("mode:$p", it)) }
            }
            permission?.first?.additionalCost?.let { add(Costs.Contribution("perm", it)) }
        } else {
            when (val a = ability) {
                is Ability.Activated -> add(Costs.Contribution("base", a.cost))
                is Ability.SpecialAction -> add(Costs.Contribution("base", a.cost))
                else -> Unit
            }
        }
    }

    private fun snap0Face() = plain.state.obj(itemId)?.let { o -> o.definition?.faces?.getOrNull(o.face) }

    /** Cost changes that apply (CR 601.2f): from the game's rules and the ability's own (CR 602.2b). */
    private fun changes(): Pair<List<ManaCost>, List<Pair<ManaCost, Int>>> {
        val increases = mutableListOf<ManaCost>()
        val reductions = mutableListOf<Pair<ManaCost, Int>>()
        snap.view.rules.forEach { applied: AppliedMechanic ->
            val m = applied.mechanic as? Mechanic.CostChange ?: return@forEach
            val e = snap.evaluator(applied.controller, self = applied.source, names = applied.names)
            if (!e.matches(m.what, itemId)) return@forEach
            val mana = Costs.expand(m.change, e)
            if (m.change is CostChange.More) increases += mana else reductions += mana to m.minimum
        }
        (ability as? Ability.Activated)?.rules?.costChange?.let { ch ->
            val mana = Costs.expand(ch, eval)
            if (ch is CostChange.More) increases += mana else reductions += mana to 0
        }
        return increases to reductions
    }

    val total: TotalCost by lazy {
        val (increases, reductions) = changes()
        val x = choices.announced.mapNotNull { (k, v) -> k.singleOrNull()?.let { it to v } }.toMap()
        val its = plain.view[itemId]?.characteristics?.manaCost ?: plain.view[sourceId]?.characteristics?.manaCost
        Costs.total(contributions, eval, x, its, { key -> (choices.names[ONE_OF + key] as? NamedValue.Number)?.value },
            increases, reductions)
    }

    /** Other ways to pay its mana (CR 702.51, 702.66, 702.126). */
    val manaWays: List<ManaPaymentWay> = if (!isSpell) emptyList() else
        snap0Abilities().flatMap { (it.ability as? Ability.Static)?.mechanics.orEmpty().filterIsInstance<Mechanic.PayManaBy>().map { m -> m.way } }

    /** "You can't spend mana to cast this spell": only its other ways pay (convoke, delve). */
    val cantSpendMana: Boolean = isSpell &&
        snap0Abilities().any { (it.ability as? Ability.Static)?.mechanics.orEmpty().any { m -> m == Mechanic.CantSpendMana } }

    /** The least X may be ("X can't be 0", CR 107.3). */
    private fun xMinimum(): Int = snap0Abilities().flatMap { (it.ability as? Ability.Static)?.mechanics.orEmpty() }
        .filterIsInstance<Mechanic.XAtLeast>().maxOfOrNull { it.min } ?: 0

    // ---- The next question

    fun next(): Question? {
        // CR 601.2b: modes, optional and additional costs, announced values
        instructions.modes?.let { modes -> if (choices.names[MODES_ASKED] == null) return Question.Modes(modes) }
        additionalCosts.forEach { (inst, m) -> if (m.optional && optionalTimes(inst) == null) return Question.OptionalCost(optionalKey(inst), m) }
        val announce = (instructions.announced + (if (Costs.mentionsX(contributions)) listOf("X") else emptyList())).distinct()
        announce.forEach { name -> if (name !in choices.announced) return Question.Announce(name, if (name == "X") xMinimum() else 0) }
        // CR 601.2c: targets
        Targets.slots(instructions, choices.modes).forEach { slot ->
            val forPlayers = slot.slot.perPlayer?.let { f -> snap.state.playersFrom(player).filter { eval.matches(f, it) } }
            if (forPlayers == null) {
                if (slot.key !in choices.targets) targetQuestion(slot, null)?.let { return it }
            } else forPlayers.forEach { p ->
                if (choices.names[PER_PLAYER + slot.key + ":" + p.value] == null) targetQuestion(slot, p)?.let { return it }
            }
        }
        // CR 601.2d: division
        Targets.slots(instructions, choices.modes).forEach { slot ->
            val divided = slot.slot.divided ?: return@forEach
            val chosen = choices.targets[slot.key].orEmpty()
            if (chosen.size >= 2 && choices.divided.none { it.slot == slot.key }) {
                return Question.Divide(slot.key, eval.value(divided), chosen)
            }
        }
        // CR 601.2f–h: the total cost's choices
        total.parts.forEach { kp ->
            val part = kp.part
            if (part is CostPart.OneOf) return Question.OneOf(kp.key, part, labels(part))
            val choice = Costs.objectChoice(snap, player, if (isSpell) itemId else null, part, eval) ?: return@forEach
            if (choices.names[OBJECTS + kp.key] == null) return Question.Objects(kp.key, choice)
        }
        if (isManaAbility) colorQuestion()?.let { return it }
        if (total.hasMana) return Question.Pay(total.mana)
        return null
    }

    private fun labels(part: CostPart.OneOf): List<String> {
        val texts = part.options.map { o -> o.parts.joinToString(", ") { Costs.describe(it) } }
        return texts.mapIndexed { i, t -> if (texts.count { it == t } > 1) "$t (${i + 1})" else t }
    }

    private fun targetQuestion(slot: SlotOf, forPlayer: PlayerId?): Question.Target? {
        val e = eval.with(eval.ctx.copy(slotPlayer = forPlayer))
        val min = e.value(slot.slot.min)
        val max = slot.slot.max?.let { e.value(it) }
        val options = Targets.options(snap, e, itemId, if (isSpell) null else sourceId, slot.slot).filterNot { ref ->
            slot.slot.distinctFrom.any { other -> choices.targets[other].orEmpty().contains(ref) }
        }
        return Question.Target(slot, forPlayer, options, min, max)
    }

    private fun colorQuestion(): Question.ManaColor? {
        val a = ability as? Ability.Activated ?: return null
        a.instructions.steps.forEachIndexed { i, step ->
            val add = (step as? Step.Do)?.effect as? Effect.AddMana ?: return@forEachIndexed
            if (add.mana.kind !is ManaKind.Choose && add.mana.kind !is ManaKind.AsCouldProduce) return@forEachIndexed
            val key = "$COLOR$i"
            if (choices.names[key] != null) return@forEachIndexed
            val yields = Mana.yields(snap, player, sourceId, a.copy(instructions = a.instructions.copy(steps = listOf(step))), manaNames())
            val among = yields.flatMap { it.colors }.filterNotNull().distinct()
            if (among.size > 1) return Question.ManaColor(key, among)
        }
        return null
    }

    /** [names] and the objects chosen for costs under the names they get as they were (`preBind`), before paying: what
     * a mana ability's mana sees ("any type the sacrificed land could produce", CR 605.3b, 608.2h). */
    fun manaNames(): Bindings = total.parts.fold(names) { b, kp ->
        val pre = when (val part = kp.part) {
            is CostPart.Sacrifice -> part.preBind
            is CostPart.Discard -> part.preBind
            is CostPart.Exile -> part.preBind
            is CostPart.ReturnToHand -> part.preBind
            else -> null
        }
        val chosen = choices.names[OBJECTS + kp.key] as? NamedValue.Objects
        if (pre != null && chosen != null) b.with(pre, chosen) else b
    }

    /** The decision that asks [q]. */
    fun decision(q: Question, id: DecisionId): Decision = when (q) {
        is Question.Modes -> {
            val chosenBefore = snap.state.obj(sourceId)?.facts?.modesChosen.orEmpty()
            val max = if (q.modes.maxIf != null && eval.holds(q.modes.maxIf!!)) q.modes.maxIfCount ?: q.modes.max else q.modes.max
            Decision.Modes(id, player, sourceId, q.modes.options.mapIndexed { i, m ->
                ModeOption(i, m.text, available = !(q.modes.onlyUnchosen && i in chosenBefore))
            }, q.modes.min, max, q.modes.repeatable)
        }
        is Question.OptionalCost -> if (q.mechanic.repeatable) Decision.ChooseNumber(id, player, sourceId, 0, null)
            else Decision.May(id, player, sourceId, "Pay the ${q.mechanic.keyword?.text ?: "optional"} cost?", q.mechanic.cost)
        is Question.Announce -> Decision.Announce(id, player, sourceId, q.name, q.min)
        is Question.Target -> Decision.Targets(id, player, sourceId, q.slot.key + (q.forPlayer?.let { ":$it" } ?: ""), q.options, q.min, q.max)
        is Question.Divide -> Decision.Divide(id, player, sourceId, q.amount, q.among, minEach = 1)
        is Question.OneOf -> Decision.ChooseOption(id, player, sourceId, q.labels)
        is Question.Objects -> Decision.ChooseObjects(id, player, sourceId, q.choice.options, q.choice.min, q.choice.max, q.choice.text)
        is Question.ManaColor -> Decision.ChooseColor(id, player, sourceId, q.among)
        is Question.Pay -> if (cantSpendMana) {
            Decision.ManaPayment(id, player, sourceId, q.cost, Mana.options(snap, player, q.cost, itemId, manaWays)
                .filter { it is ManaUse.Special || it is ManaUse.Life }, emptyList())
        } else Decision.ManaPayment(id, player, sourceId, q.cost,
            Mana.options(snap, player, q.cost, itemId, manaWays), Mana.suggest(snap, player, q.cost, itemId))
    }

    companion object {
        /** The target slot of an Aura spell (CR 303.4a) and of a bestowed spell (CR 702.103b). */
        const val ENCHANT_SLOT = "enchant"

        // Keys in [arcanum.engine.decision.Choices.names] the casting flow keeps for itself; [publicNames] drops them.
        const val ITEM = "\$item"
        const val FROM = "\$from"
        const val MODES_ASKED = "\$modes"
        const val ONE_OF = "\$oneof:"
        const val OBJECTS = "\$objects:"
        const val PER_PLAYER = "\$per-player:"
        const val COLOR = "\$color:"
        const val MANUAL = "\$manual"

        fun publicNames(names: Bindings): Bindings = Bindings(names.values.filterKeys { !it.startsWith("$") })

        private val ZONES = listOf(Zone.Battlefield, Zone.Hand, Zone.Library, Zone.Graveyard, Zone.Exile, Zone.Stack, Zone.Command, Zone.Outside)
        fun zoneName(zone: Zone): String = zone.toString()
        fun zoneByName(name: String): Zone? = ZONES.firstOrNull { it.toString() == name }

        /** The [Mechanic.MayPlay] a [arcanum.engine.input.Permission] names, with its origin: in an effect object, in a
         * static ability of its source, or of the card itself (now on the Stack, D41). */
        fun permissionMechanic(snap: Snapshot, p: arcanum.engine.input.Permission): Pair<Mechanic.MayPlay, AbilityOrigin?>? {
            p.effect?.let { e -> return snap.state.effects[e]?.mechanics?.filterIsInstance<Mechanic.MayPlay>()?.firstOrNull()?.let { it to null } }
            val source = p.source ?: return null
            val current = snap.state.obj(source)?.id ?: (source as? ObjectId.Card)?.card?.let { snap.state.current(it)?.id } ?: return null
            val inst = snap.view[current]?.characteristics?.abilities?.firstOrNull { it.origin == p.ability }
            val m = (inst?.ability as? Ability.Static)?.mechanics?.filterIsInstance<Mechanic.MayPlay>()?.firstOrNull() ?: return null
            return m to inst.origin
        }
    }
}

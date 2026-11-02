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
import arcanum.engine.view.Layers

/** What a cast or activation asks next (CR 601.2b–h), in rules order. */
internal sealed interface Question {
    val stage: AnnounceStage

    data class Modes(val modes: arcanum.api.Modes) : Question { override val stage get() = AnnounceStage.Modes }
    data class OptionalCost(val key: String, val mechanic: Mechanic.AdditionalCost) : Question { override val stage get() = AnnounceStage.Modes }
    /** Whether to splice [splice] onto the spell (CR 702.47a). */
    data class Splice(val key: String, val splice: Plan.Spliceable) : Question { override val stage get() = AnnounceStage.Modes }
    data class Announce(val name: String, val min: Int) : Question { override val stage get() = AnnounceStage.Announce }
    data class Target(val slot: SlotOf, val forPlayer: PlayerId?, val options: List<TargetRef>, val min: Int, val max: Int?) : Question {
        override val stage get() = AnnounceStage.Targets
    }
    data class Divide(val key: String, val amount: Int, val among: List<TargetRef>) : Question { override val stage get() = AnnounceStage.Divide }
    data class OneOf(val key: String, val part: CostPart.OneOf, val labels: List<String>) : Question { override val stage get() = AnnounceStage.Costs }
    data class Objects(val key: String, val choice: Costs.ObjectChoice) : Question { override val stage get() = AnnounceStage.Costs }
    data class ManaColor(val key: String, val among: List<Color>) : Question { override val stage get() = AnnounceStage.Costs }
    /** blb: a player chosen as a cost ("you may choose an opponent", gift, CR 702.174a). */
    data class Player(val key: String, val among: List<PlayerId>) : Question { override val stage get() = AnnounceStage.Costs }
    data class Pay(val cost: ManaCost) : Question { override val stage get() = AnnounceStage.Mana }
}

/**
 * A cast or activation worked out from its continuation (CR 601.2, 602.2): the spell's or ability's instructions, its
 * Stack item as chosen so far (put on a copy of the state, so filters that ask about "this spell" see it), its total
 * cost, and the next question. Recomputed on every call, so answers are read against the same question that was asked.
 */
internal class Plan(private val base: GameState, val c: Continuation.Announcing) {
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

    /** The alternative cost used: its static ability (looked up on the card on the Stack) and mechanic, or one another
     * object's ability gives the spell (dsk gaps: the spell is one cast from [castFrom], CR 601.2a). */
    val alternative: Pair<AbilityInstance, Mechanic.AlternativeCost>? = if (!isSpell || way.alternative == null) null
        else plain.view[itemId]?.characteristics?.abilities.orEmpty().firstOrNull { it.origin == way.alternative }?.let { inst ->
            (inst.ability as? Ability.Static)?.mechanics?.filterIsInstance<Mechanic.AlternativeCost>()?.firstOrNull()?.let { inst to it }
        } ?: (way.alternative as? AbilityOrigin.FromAbility)?.let { origin ->
            Legality.alternativeCosts(plain, itemId, player).firstOrNull { it.first.origin == origin }
        }

    /** Cast while another spell or ability resolves (CR 608.2g). */
    val byEffect: Boolean = (c.what as? Announced.Spell)?.byEffect == true

    /** The permission used to cast it from elsewhere than the hand (D41): found by where it was granted; for a spell
     * cast by an effect, the effect's terms. */
    val permission: Pair<Mechanic.MayPlay, AbilityOrigin?>? = if (byEffect) (c.what as Announced.Spell).permitted?.let { it to null }
        else way.permission?.let { p -> (c.what as? Announced.Spell)?.permitted?.let { it to p.ability } ?: permissionMechanic(plain, p) }

    val overloaded = alternative?.second?.keyword == Keyword.Overload.Kind
    // gtc
    val fused = way.fused
    val bestowed = alternative?.second?.keyword == Keyword.Bestow.Kind

    /** A card in the caster's hand with "splice onto [quality]" that matches this spell (CR 702.47a): its mechanic and
     * the instructions it would add. */
    data class Spliceable(val card: ObjectId, val name: String, val mechanic: Mechanic.SpliceOnto, val instructions: Instructions)

    /**
     * The cards that can be spliced onto this spell, in hand order (CR 702.47a): each only once (702.47b), not for a
     * spell cast by an effect. One whose target slots share a name with the spell's or an earlier card's isn't offered
     * (its targets couldn't be kept apart).
     */
    val spliceable: List<Spliceable> by lazy {
        if (!isSpell || byEffect || overloaded || fused) return@lazy emptyList()
        val e = plain.evaluator(player, self = itemId)
        val main = spellAbilities().flatMap { (it.ability as Ability.Spell).instructions.targets.map { t -> t.name } }.toMutableSet()
        plain.state.player(player).hand.filter { it != itemId }.mapNotNull { id ->
            val c = plain.view[id]?.characteristics ?: return@mapNotNull null
            val m = c.abilities.flatMap { (it.ability as? Ability.Static)?.mechanics.orEmpty() }.filterIsInstance<Mechanic.SpliceOnto>().firstOrNull()
                ?: return@mapNotNull null
            if (!e.matches(m.onto, itemId)) return@mapNotNull null
            val text = c.abilities.mapNotNull { (it.ability as? Ability.Spell)?.instructions }
            if (text.isEmpty()) return@mapNotNull null
            val added = Instructions(targets = text.flatMap { it.targets }, steps = text.flatMap { it.steps },
                announced = text.flatMap { it.announced }.distinct(), replacements = text.flatMap { it.replacements })
            if (text.any { it.modes != null } || added.targets.any { it.name in main }) return@mapNotNull null
            main += added.targets.map { it.name }
            Spliceable(id, c.name ?: "", m, added)
        }
    }

    fun spliceKey(card: ObjectId) = "splice:$card"

    /** The cards chosen to be spliced onto it so far. */
    val spliced: List<Spliceable> get() = spliceable.filter { (choices.costs[spliceKey(it.card)] as? NamedValue.Number)?.value == 1 }

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

    /** The board with the provisional item on the Stack. Its view is worked out once while [base] stays as it is
     * (plans made one after another for the same choices, [arcanum.engine.view.ViewCache.provisional]). */
    val snap: Snapshot = base.deepCopy().also { s ->
        s.stackItems[itemId] = item
        if (!isSpell && itemId !in s.stack) s.stack.add(itemId)
    }.let { s -> Snapshot(s) { base.viewCache?.provisional(item) { Layers.view(s) } ?: Layers.view(s) } }

    /** Names filters and values see: choices so far, announced values and targets by slot. */
    val names: Bindings = run {
        var b = choices.names
        choices.announced.forEach { (k, v) -> b = b.with(k, NamedValue.Number(v)) }
        choices.targets.forEach { (k, v) -> b = b.with(k, NamedValue.Targets(v)) }
        b
    }

    val eval: Evaluator = snap.evaluator(player, self = sourceId, names = names, stackItem = item)

    // ---- Instructions

    /** CR 113.3a: spell abilities are followed only as an instant or sorcery resolves (a gift's "if the gift cost was
     * paid" ability of a permanent card does nothing, 702.174b). */
    private fun spellAbilities(): List<AbilityInstance> {
        val types = plain.view[itemId]?.characteristics?.types.orEmpty()
        if (types.none { it == arcanum.api.CardType.Instant || it == arcanum.api.CardType.Sorcery }) return emptyList()
        return snap0Abilities().filter { it.ability is Ability.Spell }
    }

    private fun snap0Abilities(): List<AbilityInstance> = plain.view[itemId]?.characteristics?.abilities.orEmpty()

    private fun spellInstructions(): Instructions {
        if (overloaded) {
            snap0Abilities().firstNotNullOfOrNull { (it.ability as? Keyword.Overload)?.instructions }?.let { return it }
        }
        // gtc: CR 702.102d: a fused split spell follows its left half's instructions, then its right half's; each half's
        // targets are its own (as two modes, so a slot name both use is kept apart, CR 700.2d)
        if (fused) {
            val halves = spellAbilities().map { (it.ability as Ability.Spell).instructions }
            return Instructions(modes = arcanum.api.Modes(halves.map { arcanum.api.Mode(it) }, min = halves.size, max = halves.size, chooser = arcanum.api.Players.You),
                announced = halves.flatMap { it.announced }.distinct())
        }
        // CR 702.47b, c: the spliced cards' text follows the spell's own
        val all = spellAbilities().map { (it.ability as Ability.Spell).instructions } + spliced.map { it.instructions }
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
        overloaded || fused || spliced.isNotEmpty() -> Ability.Spell(spellInstructions())
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

    /** blb: the target slots, without those whose [arcanum.api.TargetSlot.onlyIf] doesn't hold as the targets are chosen,
     * after the optional costs are (CR 601.2b, c; a gift's targets only if it was promised, 702.174m). */
    private fun targetSlots(): List<SlotOf> = Targets.slots(instructions, choices.modes).filter { s -> s.slot.onlyIf?.let { holdsAsCast(it) } ?: true }

    /** A condition about the spell being cast, read from the choices so far: its optional costs paid (an unpaid or
     * unasked one counts as not paid); other conditions as the game is. */
    private fun holdsAsCast(condition: arcanum.api.Condition): Boolean = when (condition) {
        is arcanum.api.Condition.Paid -> condition.of == arcanum.api.Objects.Self && additionalCosts
            .filter { (_, m) -> m.keyword == condition.keyword }.sumOf { (inst, _) -> optionalTimes(inst) ?: 0 } >= condition.times
        is arcanum.api.Condition.Not -> !holdsAsCast(condition.condition)
        is arcanum.api.Condition.And -> condition.all.all { holdsAsCast(it) }
        is arcanum.api.Condition.Or -> condition.any.any { holdsAsCast(it) }
        else -> eval.holds(condition)
    }

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
            // CR 702.47a: each splice cost is an additional cost
            spliced.forEach { add(Costs.Contribution(spliceKey(it.card), it.mechanic.cost)) }
            permission?.first?.additionalCost?.let { add(Costs.Contribution("perm", it)) }
        } else {
            when (val a = ability) {
                is Ability.Activated -> add(Costs.Contribution("base", a.cost))
                is Ability.SpecialAction -> add(Costs.Contribution("base", a.cost))
                else -> Unit
            }
        }
    }

    /** The keyword whose expansion this activated ability is (cycling, crew), as the source is now. */
    fun keyword(): Keyword? {
        val expansion = origin as? AbilityOrigin.Expansion ?: return null
        return snap.view[sourceId]?.characteristics?.abilities?.firstOrNull { it.origin == expansion.keyword }?.ability as? Keyword
    }

    private fun snap0Face() = plain.state.obj(itemId)?.let { o -> o.definition?.faces?.getOrNull(o.face) }

    /** Cost changes that apply (CR 601.2f): from the game's rules and the ability's own (CR 602.2b). */
    private fun changes(): Pair<List<ManaCost>, List<Pair<ManaCost, Int>>> {
        val increases = mutableListOf<ManaCost>()
        val reductions = mutableListOf<Pair<ManaCost, Int>>()
        val chosen = chosenCostObjects()
        snap.view.rules.forEach { applied: AppliedMechanic ->
            val m = applied.mechanic as? Mechanic.CostChange ?: return@forEach
            // The spell's own reduction may count what its additional cost will use ("for each card exiled this way")
            val names = if (applied.source == itemId) applied.names.with(chosen) else applied.names
            val e = snap.evaluator(applied.controller, self = applied.source, names = names)
            if (!e.matches(m.what, itemId)) return@forEach
            // "The next spell …" (CR 611.2f): only a spell it is still waiting for
            if (!NextSpells.appliesTo(snap.state, applied.effect, e, itemId)) return@forEach
            val mana = Costs.expand(m.change, e)
            if (m.change is CostChange.More) increases += mana else reductions += mana to m.minimum
        }
        (ability as? Ability.Activated)?.rules?.costChange?.let { ch ->
            val mana = Costs.expand(ch, eval)
            if (ch is CostChange.More) increases += mana else reductions += mana to 0
        }
        return increases to reductions
    }

    /** The objects chosen so far for cost parts that bind them, under their bound names (CR 601.2b, 601.2f: the
     * total cost is worked out after the costs' choices are announced, before they are paid). */
    private fun chosenCostObjects(): Bindings {
        var b = Bindings.EMPTY
        contributions.forEach { c ->
            repeat(c.times) { t ->
                c.cost.parts.forEachIndexed { j, part ->
                    val ids = choices.names[OBJECTS + "${c.key}.$t.$j"] ?: return@forEachIndexed
                    val bindNames = when (part) {
                        is CostPart.Exile -> listOf(part.preBind, part.postBind)
                        is CostPart.Sacrifice -> listOf(part.preBind, part.postBind)
                        is CostPart.Discard -> listOf(part.preBind, part.postBind)
                        is CostPart.ReturnToHand -> listOf(part.preBind, part.postBind)
                        else -> emptyList()
                    }
                    bindNames.filterNotNull().forEach { b = b.with(it, ids) }
                }
            }
        }
        return b
    }

    val total: TotalCost by lazy {
        val (increases, reductions) = changes()
        val x = choices.announced.mapNotNull { (k, v) -> k.singleOrNull()?.let { it to v } }.toMap()
        val its = plain.view[itemId]?.characteristics?.manaCost ?: plain.view[sourceId]?.characteristics?.manaCost
        Costs.total(contributions, eval, x, its, { key -> (choices.names[ONE_OF + key] as? NamedValue.Number)?.value },
            increases, reductions)
    }

    /** Other ways to pay its mana (CR 702.51, 702.66, 702.126); an activated ability's own ways (blb gaps 2). */
    val manaWays: List<ManaPaymentWay> = if (!isSpell) (ability as? Ability.Activated)?.rules?.payManaBy.orEmpty() else
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
        spliceable.forEach { if (choices.costs[spliceKey(it.card)] == null) return Question.Splice(spliceKey(it.card), it) }
        val announce = (instructions.announced + (if (Costs.mentionsX(contributions)) listOf("X") else emptyList())).distinct()
        announce.forEach { name -> if (name !in choices.announced) return Question.Announce(name, if (name == "X") xMinimum() else 0) }
        // CR 601.2c: targets
        targetSlots().forEach { slot ->
            val forPlayers = slot.slot.perPlayer?.let { f -> snap.state.playersFrom(player).filter { eval.matches(f, it) } }
            if (forPlayers == null) {
                if (slot.key !in choices.targets) targetQuestion(slot, null)?.let { return it }
            } else forPlayers.forEach { p ->
                if (choices.names[PER_PLAYER + slot.key + ":" + p.value] == null) targetQuestion(slot, p)?.let { return it }
            }
        }
        // CR 601.2d: division
        targetSlots().forEach { slot ->
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
            if (part is CostPart.ChoosePlayer) {
                if (choices.names[PLAYER + kp.key] == null) return Question.Player(kp.key, snap.state.playersFrom(player).filter { eval.matches(part.filter, it) })
                return@forEach
            }
            val choice = Costs.objectChoice(snap, player, if (isSpell) itemId else null, part, eval) ?: return@forEach
            if (choices.names[OBJECTS + kp.key] == null) return Question.Objects(kp.key, choice)
        }
        if (isManaAbility) colorQuestion()?.let { return it }
        if (total.hasMana) return Question.Pay(total.mana)
        return null
    }

    /**
     * Why the cast or activation can't be finished whatever is chosen from here (CR 601.2c, 601.2h, 119.4), or null:
     * the [next] question is a target slot without enough legal targets (later slots may depend on earlier targets or
     * on X, so they are checked as they come), or a cost part of the total known so far has too few objects to use (or
     * objects that can't reach the total it asks), an "A or B" cost has no payable option, or it asks more life, energy
     * or cards to mill than there are (values not announced yet count as 0). Mana is paid with a decision and isn't
     * checked here.
     */
    fun blocker(next: Question? = next()): String? {
        if (next is Question.Target && next.options.size < next.min) {
            return "Not enough legal targets for ${next.slot.key}: ${next.options.size} of ${next.min}"
        }
        if (next is Question.Modes && !someModeWorks(next)) return "No mode can be chosen with what it needs (CR 700.2, 601.2c)"
        total.parts.forEach { kp ->
            val part = kp.part
            if (part is CostPart.OneOf) {
                if (part.options.none { option -> Answers.payable(this, option.parts) }) return "None of ${labels(part)} can be paid"
                return@forEach
            }
            if (choices.names[OBJECTS + kp.key] == null) {
                Costs.objectChoice(snap, player, if (isSpell) itemId else null, part, eval)?.let { choice ->
                    if (choice.options.size < choice.min) {
                        return "Not enough to ${choice.text.lowercase()}: ${choice.options.size} of ${choice.min}"
                    }
                    choice.total?.let { t ->
                        val most = choice.options.sumOf { eval.property(t.property, it).coerceAtLeast(0) }
                        if (most < eval.value(t.atLeast)) return "Their total ${t.property} can't reach ${eval.value(t.atLeast)}"
                    }
                }
            }
            if (!Answers.payable(this, listOf(part))) return "Can't pay ${Costs.describe(part)}"
        }
        return null
    }

    /** For modes of which one may be chosen: whether some available mode leaves the next question answerable. */
    private fun someModeWorks(q: Question.Modes): Boolean {
        val d = decision(q, DecisionId("check")) as Decision.Modes
        if (d.min != 1) return true
        return d.options.filter { it.available }.any { m ->
            val after = Plan(base, c.copy(choices = Answers.apply(this, q, arcanum.engine.decision.Answer.Modes(listOf(m.index)))))
            after.blocker() == null
        }
    }

    /** CR 601.2d: a divided amount with a single target in its slot goes all to that target, without asking. */
    fun wholeShares(): List<arcanum.engine.state.DividedShare> = targetSlots().mapNotNull { slot ->
        val divided = slot.slot.divided ?: return@mapNotNull null
        val chosen = choices.targets[slot.key].orEmpty().singleOrNull() ?: return@mapNotNull null
        if (choices.divided.any { it.slot == slot.key }) null else arcanum.engine.state.DividedShare(slot.key, chosen, eval.value(divided))
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
            val chosen = (choices.names[key] as? NamedValue.Colors)?.colors.orEmpty()
            // "In any combination of colors": a colour for each mana, else one for all of it
            val combination = (add.mana.kind as? ManaKind.Choose)?.combination == true
            if (choices.names[key] != null && !combination) return@forEachIndexed
            val yields = Mana.yields(snap, player, sourceId, a.copy(instructions = a.instructions.copy(steps = listOf(step))), manaNames())
            if (combination && chosen.size >= yields.sumOf { it.amount }) return@forEachIndexed
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
                ModeOption(i, m.text, available = !(q.modes.onlyUnchosen && i in chosenBefore), pawprints = m.pawprints)
            }, q.modes.min, max, q.modes.repeatable, pawprintLimit = q.modes.pawprintLimit)
        }
        is Question.OptionalCost -> if (q.mechanic.repeatable) Decision.ChooseNumber(id, player, sourceId, 0, null)
            else Decision.May(id, player, sourceId, "Pay the ${q.mechanic.keyword?.text ?: "optional"} cost?", q.mechanic.cost)
        is Question.Splice -> Decision.May(id, player, sourceId, "Splice ${q.splice.name} onto it?", q.splice.mechanic.cost)
        is Question.Announce -> Decision.Announce(id, player, sourceId, q.name, q.min)
        // dgm: "any target chosen at random": the host picks one of the legal targets (a die with a side for each)
        is Question.Target -> if (q.slot.slot.atRandom) Decision.Random(id, player, sourceId, arcanum.engine.decision.RandomKind.Die(q.options.size, 1))
            else Decision.Targets(id, player, sourceId, q.slot.key + (q.forPlayer?.let { ":$it" } ?: ""), q.options, q.min, q.max)
        is Question.Divide -> Decision.Divide(id, player, sourceId, q.amount, q.among, minEach = 1)
        is Question.OneOf -> Decision.ChooseOption(id, player, sourceId, q.labels)
        is Question.Objects -> Decision.ChooseObjects(id, player, sourceId, q.choice.options, q.choice.min, q.choice.max, q.choice.text)
        is Question.ManaColor -> Decision.ChooseColor(id, player, sourceId, q.among)
        is Question.Player -> Decision.ChoosePlayer(id, player, sourceId, q.among)
        is Question.Pay -> if (cantSpendMana) {
            Decision.ManaPayment(id, player, sourceId, q.cost, Mana.options(snap, player, q.cost, itemId, manaWays)
                .filter { it is ManaUse.Special || it is ManaUse.Life }, Mana.suggest(snap, player, q.cost, itemId, manaWays, canSpendMana = false))
        } else Decision.ManaPayment(id, player, sourceId, q.cost,
            Mana.options(snap, player, q.cost, itemId, manaWays), Mana.suggest(snap, player, q.cost, itemId, manaWays))
    }

    companion object {
        /** The target slot of an Aura spell (CR 303.4a) and of a bestowed spell (CR 702.103b). */
        const val ENCHANT_SLOT = "enchant"

        // Keys in [arcanum.engine.decision.Choices.names] the casting flow keeps for itself; [publicNames] drops them.
        const val ITEM = "\$item"
        const val FROM = "\$from"
        const val MODES_ASKED = "\$modes"
        const val ONE_OF = "\$oneof:"
        /** blb: the player chosen for a [CostPart.ChoosePlayer] cost, by its key. */
        const val PLAYER = "\$player:"
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
            val ability = inst?.ability ?: p.ability?.let { grantedByEffect(snap, it) }
            val m = (ability as? Ability.Static)?.mechanics?.filterIsInstance<Mechanic.MayPlay>()?.firstOrNull() ?: return null
            return m to (inst?.origin ?: p.ability)
        }

        /** A keyword an effect granted the card ("gains flashback until end of turn"): the card on the Stack is a new
         * object the effect doesn't apply to (CR 400.7), so the permission is read from the effect itself. */
        private fun grantedByEffect(snap: Snapshot, origin: AbilityOrigin): Ability? {
            val expansion = origin as? AbilityOrigin.Expansion ?: return null
            val from = expansion.keyword as? AbilityOrigin.FromEffect ?: return null
            val gain = snap.state.effects[from.effect]?.mechanics?.getOrNull(from.mechanic) as? Mechanic.GainAbilities ?: return null
            val keyword = gain.abilities.getOrNull(from.index) as? Keyword ?: return null
            return arcanum.engine.view.KeywordMeanings.expand(keyword).getOrNull(expansion.index)
        }
    }
}

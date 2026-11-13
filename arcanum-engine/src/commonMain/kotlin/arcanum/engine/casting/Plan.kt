package arcanum.engine.casting

import arcanum.engine.state.castOrNone
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
import arcanum.api.ManaSymbol
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

    /**
     * The question's name in its decision id. Written out, not the class's name: a shrunk Android build names a nested
     * class "Question$Pay" where the JVM says "Pay", and a device and a server must make the same decision ids.
     */
    val kind: String get() = when (this) {
        is Modes -> "Modes"
        is OptionalCost -> "OptionalCost"
        is Splice -> "Splice"
        is Announce -> "Announce"
        is Target -> "Target"
        is Divide -> "Divide"
        is OneOf -> "OneOf"
        is Objects -> "Objects"
        is ManaColor -> "ManaColor"
        is Player -> "Player"
        is CostChoice -> "CostChoice"
        is Pay -> "Pay"
        is Assist -> "Assist"
        is AssistAmount -> "AssistAmount"
        is AssistPay -> "AssistPay"
    }

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
    /** ecl: a creature type chosen as part of the cost ("you may choose a creature type and behold two creatures of that type", CR 601.2f). */
    data class CostChoice(val key: String, val part: CostPart.Choose) : Question { override val stage get() = AnnounceStage.Costs }
    data class Pay(val cost: ManaCost) : Question { override val stage get() = AnnounceStage.Mana }
    /** Assist (CR 702.132a): the caster may choose another player among [among] to help pay. */
    data class Assist(val among: List<PlayerId>) : Question { override val stage get() = AnnounceStage.Costs }
    /** Assist: how much of the [max] generic mana [player] pays. */
    data class AssistAmount(val player: PlayerId, val max: Int) : Question { override val stage get() = AnnounceStage.Costs }
    /** Assist: [player] pays [cost], generic mana of the spell's total cost. */
    data class AssistPay(val player: PlayerId, val cost: ManaCost) : Question { override val stage get() = AnnounceStage.Mana }
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
        is Announced.Ability -> ObjectId.StackItem(choices.item ?: error("No item id"))
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

    val castFrom: Zone? = choices.from

    /** The alternative cost used: its static ability (looked up on the card on the Stack) and mechanic, or one another
     * object's ability gives the spell (dsk gaps: the spell is one cast from [castFrom], CR 601.2a). */
    val alternative: Pair<AbilityInstance, Mechanic.AlternativeCost>? = if (!isSpell || way.alternative == null) null
        else plain.view[itemId]?.characteristics?.abilities.orEmpty().firstOrNull { it.origin == way.alternative }?.let { inst ->
            (inst.ability as? Ability.Static)?.mechanics?.filterIsInstance<Mechanic.AlternativeCost>()?.firstOrNull()?.let { inst to it }
        } ?: way.alternative?.takeIf { it is AbilityOrigin.FromAbility || it is AbilityOrigin.FromEffect }?.let { origin ->
            Legality.alternativeCosts(plain, itemId, player).firstOrNull { it.first.origin == origin }
        } ?: (c.what as? Announced.Spell)?.alternativeAsCast?.let { m ->
            // tmt: an alternative cost the card had only where it was cast from (granted to cards in a graveyard, CR 601.2a, b)
            AbilityInstance(Ability.Static(listOf(m), affects = arcanum.api.Affected.Objects(arcanum.api.Objects.Self)), way.alternative!!) to m
        }

    /** Cast while another spell or ability resolves (CR 608.2g). */
    val byEffect: Boolean = (c.what as? Announced.Spell)?.byEffect == true

    /** The permission used to cast it from elsewhere than the hand (D41): found by where it was granted; for a spell
     * cast by an effect, the effect's terms. */
    val permission: Pair<Mechanic.MayPlay, AbilityOrigin?>? = if (byEffect) (c.what as Announced.Spell).permitted?.let { it to null }
        else way.permission?.let { p -> (c.what as? Announced.Spell)?.permitted?.let { it to p.ability } ?: permissionMechanic(plain, p) }

    /**
     * What the other parts of the total cost use, for the mana paid before them (CR 601.2g before 601.2h): what the cost taps (its
     * own {T}, tla, CR 107.5; the creatures it asks) can't also tap for mana; what it moves away may tap for mana first, but can't
     * also be sacrificed for mana; the life it pays comes after a mana ability's damage.
     */
    val reserved: Mana.Reserved get() {
        val tapped = mutableSetOf<ObjectId>()
        val leaving = mutableSetOf<ObjectId>()
        var life = 0
        total.parts.forEach { kp ->
            val chosen = (choices.names[OBJECTS + kp.key] as? NamedValue.Objects)?.ids.orEmpty()
            when (val part = kp.part) {
                CostPart.TapSelf -> tapped += sourceId
                is CostPart.TapObjects -> tapped += chosen
                is CostPart.Sacrifice -> if (part.what == arcanum.api.CostObjects.Self) leaving += sourceId else leaving += chosen
                is CostPart.ReturnToHand -> if (part.what == arcanum.api.CostObjects.Self) leaving += sourceId else leaving += chosen
                is CostPart.Exile -> if (part.what == arcanum.api.CostObjects.Self) leaving += sourceId else leaving += chosen
                is CostPart.PayLife -> life += eval.value(part.amount)
                else -> Unit
            }
        }
        return Mana.Reserved(tapped, leaving, life)
    }

    val overloaded = alternative?.second?.keyword == Keyword.Overload.Kind
    // gtc
    val fused = way.fused
    val bestowed = alternative?.second?.keyword == Keyword.Bestow.Kind
    /** Cast for its mutate cost: a mutating creature spell (CR 702.140a). */
    val mutating = alternative?.second?.keyword == Keyword.Mutate.Kind

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
        // CR 702.140a: a mutating creature spell targets a non-Human creature with the same owner
        val mutate = if (mutating) arcanum.engine.mutate.Mutate.slot else null
        val extra = listOfNotNull(enchant, mutate)
        if (extra.isNotEmpty()) base.copy(targets = extra + base.targets) else base
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
        s._stackItems[itemId] = item
        if (!isSpell && itemId !in s.stack) s._stack.add(itemId)
    }.let { s -> Snapshot(s) { base.viewCache?.provisional(item) { Layers.view(s) } ?: Layers.view(s) } }

    /** Names filters and values see: choices so far, announced values and targets by slot. */
    val names: Bindings = run {
        var b = choices.names
        choices.announced.forEach { (k, v) -> b = b.with(k, NamedValue.Number(v)) }
        choices.targets.forEach { (k, v) -> b = b.with(k, NamedValue.Targets(v)) }
        b
    }

    // hob: a permission's cost reads the spell it lets be cast as the affected object ("pay life equal to its mana value
    // rather than pay its mana cost", CR 118.9)
    val eval: Evaluator = snap.evaluator(player, self = sourceId, names = names, stackItem = item).let { e ->
        if (isSpell) e.with(e.ctx.copy(affected = itemId)) else e
    }

    // ---- Instructions

    /** CR 113.3a: spell abilities are followed only as an instant or sorcery resolves (a gift's "if the gift cost was
     * paid" ability of a permanent card does nothing, 702.174b). */
    private fun spellAbilities(): List<AbilityInstance> {
        val types = plain.view[itemId]?.characteristics?.types.orEmpty()
        if (types.none { it == arcanum.api.CardType.Instant || it == arcanum.api.CardType.Sorcery }) return emptyList()
        return snap0Abilities().filter { it.ability is Ability.Spell }
    }

    private fun snap0Abilities(): List<AbilityInstance> = plain.view[itemId]?.characteristics?.abilities.orEmpty()

    /** sos: the card's abilities and those other objects give it as the spell it is becoming ("Each instant and sorcery spell you cast has
     * casualty 1": the spell has it as its costs are determined, CR 601.2a, 601.2b), seen with the provisional item on the Stack. */
    private fun withGrantedAsSpell(): List<AbilityInstance> {
        val own = snap0Abilities()
        val origins = own.map { it.origin }.toSet()
        return own + snap.view[itemId]?.characteristics?.abilities.orEmpty().filter { it.origin !in origins }
    }

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
        withGrantedAsSpell().flatMap { inst ->
            (inst.ability as? Ability.Static)?.mechanics.orEmpty().filterIsInstance<Mechanic.AdditionalCost>().map { inst to it }
        }

    fun optionalKey(inst: AbilityInstance) = "opt:${inst.origin.key}"

    /** msh: the keyword whose additional cost the cost part keyed [key] is part of (teamwork, CR 702.194a), if any. */
    fun costKeyword(key: String): arcanum.api.KeywordKind? =
        additionalCosts.firstOrNull { (inst, _) -> key.startsWith("add:${inst.origin.key}.") }?.second?.keyword

    /** blb: the target slots, without those whose [arcanum.api.TargetSlot.onlyIf] doesn't hold as the targets are chosen,
     * after the optional costs are (CR 601.2b, c; a gift's targets only if it was promised, 702.174m). */
    private fun targetSlots(): List<SlotOf> = Targets.slots(instructions, choices.modes).filter { s -> s.slot.onlyIf?.let { holdsAsCast(it) } ?: true }

    /** A condition about the spell being cast, read from the choices so far: its optional costs paid (an unpaid or
     * unasked one counts as not paid); other conditions as the game is. */
    private fun holdsAsCast(condition: arcanum.api.Condition): Boolean = when (condition) {
        is arcanum.api.Condition.Paid -> condition.of == arcanum.api.Objects.Self && (additionalCosts
            .filter { (_, m) -> m.keyword == condition.keyword }.sumOf { (inst, _) -> optionalTimes(inst) ?: 0 } >= condition.times ||
            // CR 702.113b: the alternative cost chosen (awaken)
            (condition.keyword != null && alternative?.second?.keyword == condition.keyword && condition.times <= 1))
        is arcanum.api.Condition.Not -> !holdsAsCast(condition.condition)
        is arcanum.api.Condition.And -> condition.all.all { holdsAsCast(it) }
        is arcanum.api.Condition.Or -> condition.any.any { holdsAsCast(it) }
        else -> eval.holds(condition)
    }

    /** woe chunk 04: whether [condition] reads the spell's own optional costs ([holdsAsCast] reads them from the choices). */
    private fun readsCastChoices(condition: arcanum.api.Condition): Boolean = when (condition) {
        is arcanum.api.Condition.Paid -> condition.of == arcanum.api.Objects.Self
        is arcanum.api.Condition.Not -> readsCastChoices(condition.condition)
        is arcanum.api.Condition.And -> condition.all.any(::readsCastChoices)
        is arcanum.api.Condition.Or -> condition.any.any(::readsCastChoices)
        else -> false
    }

    /** How often the optional cost of [inst] was chosen; null while not asked. */
    /** tdm: the spell could be cast now without an additional cost that gives it flash: sorcery timing apart from the spell itself on the
     * Stack (CR 307.1), or an instant. */
    fun flashWithoutCost(): Boolean {
        val s = base
        val sorceryTime = s.turn.active == player && s.stack.all { it == itemId } &&
            (s.turn.step == arcanum.api.TurnStep.PrecombatMain || s.turn.step == arcanum.api.TurnStep.PostcombatMain)
        return sorceryTime || snap.view[itemId]?.characteristics?.types?.contains(arcanum.api.CardType.Instant) == true
    }

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
            base?.let { add(Costs.Contribution("base", it, printed = alternative?.takeIf { a -> a.second.cost === it }?.let { a -> Costs.Printed(a.first.origin) })) }
            additionalCosts.forEach { (inst, m) ->
                val key = "add:${inst.origin.key}"
                when {
                    m.optional -> optionalTimes(inst)?.takeIf { it > 0 }?.let { add(Costs.Contribution(key, m.cost, it, Costs.Printed(inst.origin))) }
                    m.times != null -> eval.value(m.times!!).takeIf { it > 0 }?.let { add(Costs.Contribution(key, m.cost, it, Costs.Printed(inst.origin))) }
                    else -> add(Costs.Contribution(key, m.cost, printed = Costs.Printed(inst.origin)))
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
                // hob: "you may pay {0} rather than pay the equip cost" (CR 118.9, 602.2b): a free alternative is never worse, so it is used
                is Ability.Activated -> add(freeAlternative()?.let { Costs.Contribution("base", it) } ?: Costs.Contribution("base", a.cost, printed = Costs.Printed(null)))
                // CR 118.9: so for a special action ("the first card you foretell each turn costs {0} to foretell")
                is Ability.SpecialAction -> add(freeAlternative()?.let { Costs.Contribution("base", it) } ?: Costs.Contribution("base", a.cost, printed = Costs.Printed(null)))
                else -> Unit
            }
        }
    }

    /** hob: a free alternative activation cost the player has for this ability ([Mechanic.AbilityAlternativeCost]), within its
     * "first … each turn" (the abilities matching it activated earlier this turn, by the keyword they stand for). */
    private fun freeAlternative(): Cost? = snap.view.players[player]?.mechanics.orEmpty().firstNotNullOfOrNull { applied ->
        val m = applied.mechanic as? Mechanic.AbilityAlternativeCost ?: return@firstNotNullOfOrNull null
        val free = m.cost.parts.all { p -> p is CostPart.Mana && p.cost.symbols.all { it is arcanum.api.ManaSymbol.Generic && it.amount == 0 } }
        if (!free) return@firstNotNullOfOrNull null
        val e = snap.evaluator(applied.controller, self = applied.source, names = applied.names)
        if (!e.matches(m.abilities, itemId)) return@firstNotNullOfOrNull null
        if (m.firstEachTurn) {
            val keyword = (m.abilities as? ObjectFilter.AbilityOf)?.keyword?.id
            val earlier = snap.state.eventsOf(snap.state.turn.number).count { c ->
                when (val event = (c as? arcanum.engine.change.Change.Happened)?.event) {
                    is arcanum.engine.change.GameEvent.Activated -> event.player == player && !event.manaAbility && (keyword == null || event.keyword == keyword)
                    // CR 702.143c: foretelling is a special action, so no activation: its own event counts
                    is arcanum.engine.change.GameEvent.KeywordActionDone -> keyword == Keyword.Foretell.Kind.id && event.action == arcanum.api.KeywordActionKind.Foretell && event.player == player
                    else -> false
                }
            }
            if (earlier > 0) return@firstNotNullOfOrNull null
        }
        m.cost
    }

    /** The keyword whose expansion this activated ability is (cycling, crew), as the source is now. */
    fun keyword(): Keyword? {
        val expansion = origin as? AbilityOrigin.Expansion ?: return null
        return snap.view[sourceId]?.characteristics?.abilities?.firstOrNull { it.origin == expansion.keyword }?.ability as? Keyword
    }

    private fun snap0Face() = plain.state.obj(itemId)?.let { o -> o.definition.faces.getOrNull(o.face) }

    /** Cost changes that apply (CR 601.2f): from the game's rules and the ability's own (CR 602.2b). */
    private fun changes(): Pair<List<ManaCost>, List<Pair<ManaCost, Int>>> {
        val increases = mutableListOf<ManaCost>()
        val reductions = mutableListOf<Pair<ManaCost, Int>>()
        val chosen = chosenCostObjects()
        // woe chunk 04: the spell's own reductions on a condition about its optional costs ("This spell costs {2} less to cast if it's bargained"),
        // read from the choices made (CR 601.2b comes before 601.2f); the game's view can't know them before the spell is cast
        val ownOnChoices = if (!isSpell) emptyList() else withGrantedAsSpell().filter { inst ->
            (inst.ability as? Ability.Static)?.condition?.let(::readsCastChoices) == true }
        ownOnChoices.forEach { inst ->
            val static = inst.ability as Ability.Static
            if (!holdsAsCast(static.condition!!)) return@forEach
            val e = snap.evaluator(player, self = itemId, names = chosen)
            static.mechanics.filterIsInstance<Mechanic.CostChange>().filter { e.matches(it.what, itemId) }.forEach { m ->
                val mana = Costs.expand(m.change, e)
                if (m.change is CostChange.More) increases += mana else reductions += mana to m.minimum
            }
        }
        snap.view.game.rules.forEach { applied: AppliedMechanic ->
            val m = applied.mechanic as? Mechanic.CostChange ?: return@forEach
            if (applied.source == itemId && ownOnChoices.any { it.origin == applied.origin }) return@forEach
            // The spell's own reduction may count what its additional cost will use ("for each card exiled this way")
            val names = if (applied.source == itemId) applied.names.with(chosen) else applied.names
            val e = snap.evaluator(applied.controller, self = applied.source, names = names)
            if (!e.matches(m.what, itemId)) return@forEach
            // "The next spell …" (CR 611.2f): only a spell it is still waiting for
            if (!NextSpells.appliesTo(snap.state, applied.effect, e, itemId)) return@forEach
            val mana = Costs.expand(m.change, e)
            if (m.change is CostChange.More) increases += mana else reductions += mana to m.minimum
        }
        // tmt backfill: "the next noncreature spell you cast this turn has affinity for artifacts": a keyword an effect
        // grants the next spells whose meaning changes the spell's cost applies as that spell is cast (CR 601.2f, 611.2f)
        snap.state.effects.values.filter { it.affects is arcanum.api.Affected.NextSpells }.forEach { effect ->
            val granted = effect.mechanics.filterIsInstance<Mechanic.GainAbilities>().flatMap { it.abilities }
            if (granted.isEmpty()) return@forEach
            val e = snap.evaluator(effect.controller, self = effect.source, names = effect.names)
            if (!NextSpells.appliesTo(snap.state, effect.id, e, itemId)) return@forEach
            val own = snap.evaluator(player, self = itemId)
            granted.flatMap { a -> (a as? Keyword)?.let { arcanum.api.KeywordExpansions.expand(it) } ?: listOf(a) }
                .filterIsInstance<Ability.Static>().flatMap { it.mechanics }.filterIsInstance<Mechanic.CostChange>()
                .filter { own.matches(it.what, itemId) }
                .forEach { cc ->
                    val mana = Costs.expand(cc.change, own)
                    if (cc.change is CostChange.More) increases += mana else reductions += mana to cc.minimum
                }
        }
        // eoe: "Each spell cast this way costs {1} more to cast" (the permission it is cast with, CR 601.2f)
        permission?.first?.costChange?.let { ch ->
            val mana = Costs.expand(ch, eval)
            if (ch is CostChange.More) increases += mana else reductions += mana to 0
        }
        (ability as? Ability.Activated)?.rules?.costChange?.let { ch ->
            val mana = Costs.expand(ch, eval)
            if (ch is CostChange.More) increases += mana else reductions += mana to 0
        }
        // msh: CR 702.193a, b: a power-up ability costs less by its permanent's mana cost (X as 0) if the permanent entered this turn
        (ability as? Ability.Activated)?.takeIf { it.label == arcanum.api.AbilityLabel.PowerUp }?.let {
            val entered = snap.evaluator(player, self = sourceId).matches(ObjectFilter.ThisTurn(arcanum.api.EventPattern.ZoneChange(ObjectFilter.Any,
                to = arcanum.api.Zone.Battlefield), role = arcanum.api.EventRole.After), sourceId)
            val own = snap.view[sourceId]?.characteristics?.manaCost
            if (entered && own != null) reductions += Costs.withX(own, emptyMap()) to 0
        }
        return increases to reductions
    }

    /**
     * Whether the mana to pay may still fall with a choice not made yet (CR 601.2f comes after 601.2b-e; report KX8GYS):
     * a question is still open before the payment, and the spell or ability has a reduction of its own (which may read its
     * targets or what its costs use: "costs {2} less if it targets a tapped creature", emerge, harmonize), any other object has a
     * reduction, whether it applies yet or not (its amount may read the target: "Equip abilities that target this creature cost
     * {2} less", "costs {1} less for each target"), or another player may still assist (CR 702.132a). Which choice does it is for
     * the caller to try.
     */
    val costMayFall: Boolean get() {
        if (!anyReduction) return false
        val q = next()
        return q != null && q !is Question.Pay
    }

    /** Whether any reduction exists that may come to apply, whatever is left to choose (the cheap checks first: most cards a player can't pay have
     * nothing of this, on every look at their hand). */
    val anyReduction: Boolean get() {
        fun lower(m: Mechanic) = m is Mechanic.CostChange && m.change !is CostChange.More
        return (assistable > 0 && choices.assist?.amount == null) ||
            (ability as? Ability.Activated)?.rules?.costChange?.let { it !is CostChange.More } == true ||
            withGrantedAsSpell().any { inst -> (inst.ability as? Ability.Static)?.mechanics.orEmpty().any(::lower) } ||
            snap.view.game.rules.any { lower(it.mechanic) }
    }

    /**
     * The most the cost's mana can still fall by with what is known (CR 601.2f): every reduction that may come to apply, at its amount, or null when
     * one can't be bounded (its amount reads a choice, another player may assist). A reduction whose filter reads nothing chosen and doesn't match
     * this spell can't come to apply ([fixedBeforeChoices]: anything else may). An upper bound: a search may stop when even this doesn't pay (deny
     * only with proof).
     */
    val mostFall: ManaCost? get() {
        if (assistable > 0 && choices.assist?.amount == null) return null
        val symbols = mutableListOf<ManaSymbol>()
        fun add(change: CostChange): Boolean {
            if (change is CostChange.More) return true
            val times = (change.times as? Value.Fixed)?.value ?: return false
            repeat(times.coerceAtLeast(0)) { symbols += change.mana.symbols }
            return true
        }
        (ability as? Ability.Activated)?.rules?.costChange?.let { if (!add(it)) return null }
        withGrantedAsSpell().forEach { inst -> (inst.ability as? Ability.Static)?.mechanics.orEmpty().filterIsInstance<Mechanic.CostChange>().forEach { if (!add(it.change)) return null } }
        snap.view.game.rules.forEach { applied ->
            val m = applied.mechanic as? Mechanic.CostChange ?: return@forEach
            if (m.change is CostChange.More) return@forEach
            if (fixedBeforeChoices(m.what) && !snap.evaluator(applied.controller, self = applied.source, names = applied.names).matches(m.what, itemId)) return@forEach
            if (!add(m.change)) return null
        }
        return ManaCost(symbols)
    }

    /** Whether a filter on the spell can't change with what is chosen while casting (an allow-list: its mana value can, with X, CR 202.3e; a term not
     * known here may). */
    private fun fixedBeforeChoices(f: ObjectFilter): Boolean = when (f) {
        ObjectFilter.Any, ObjectFilter.Colorless, ObjectFilter.Multicolored, ObjectFilter.Monocolored, ObjectFilter.XInManaCost -> true
        is ObjectFilter.And -> f.all.all(::fixedBeforeChoices)
        is ObjectFilter.Or -> f.any.all(::fixedBeforeChoices)
        is ObjectFilter.Not -> fixedBeforeChoices(f.filter)
        is ObjectFilter.Category, is ObjectFilter.Super, is ObjectFilter.InZone, is ObjectFilter.Named, is ObjectFilter.WasCast -> true
        is ObjectFilter.Type -> f.cardType is arcanum.api.CardTypeRef.Literal
        is ObjectFilter.Sub -> f.subtype is arcanum.api.SubtypeRef.Literal
        is ObjectFilter.OfColor -> f.color is arcanum.api.ColorRef.Literal
        is ObjectFilter.ControlledBy -> f.players == arcanum.api.PlayerFilter.You || f.players == arcanum.api.PlayerFilter.Opponent || f.players == arcanum.api.PlayerFilter.Any
        is ObjectFilter.OwnedBy -> f.players == arcanum.api.PlayerFilter.You || f.players == arcanum.api.PlayerFilter.Opponent || f.players == arcanum.api.PlayerFilter.Any
        is ObjectFilter.AbilityOf -> f.source?.let(::fixedBeforeChoices) ?: true
        is ObjectFilter.Is -> f.objects == arcanum.api.Objects.Self
        else -> false
    }

    /**
     * Whether a question still open may change what it costs at all, up or down (CR 601.2f): a cost change of its own or from
     * any object, a repeated or per-mode additional cost, a mode that costs more, an "A or B" cost, another player's assist.
     * Without any, every way through has the same cost and the first one says it.
     */
    val costMayVary: Boolean get() {
        fun change(m: Mechanic) = m is Mechanic.CostChange || (m is Mechanic.AdditionalCost && m.times != null)
        val any = assistable > 0 || (ability as? Ability.Activated)?.rules?.costChange != null ||
            withGrantedAsSpell().any { inst -> (inst.ability as? Ability.Static)?.mechanics.orEmpty().any(::change) } ||
            snap.view.game.rules.any { change(it.mechanic) } ||
            instructions.modes?.options.orEmpty().any { it.extraCost != null } || additionalCosts.any { it.second.keyword == Keyword.Entwine.Kind || it.second.times != null } ||
            contributions.any { c -> c.cost.parts.any { it is CostPart.OneOf } } ||
            // Mana that rises with what is chosen ("{B}{B} for each creature type among them" read by a value)
            contributions.any { c -> c.cost.parts.any { it is CostPart.Mana && (it.times != null || it.x != null) } }
        if (!any) return false
        val q = next()
        return q != null && q !is Question.Pay
    }

    /** Whether the open slot's targets limit each other (CR 601.2c): the same controller or owner, a shared or differing quality, a
     * total, one for each: some candidates then stop being legal with others picked. */
    fun targetsLimitEachOther(key: String): Boolean = instructions.let { Targets.slots(it, choices.modes) }.any { s ->
        s.key == key.substringBefore(":") && (s.slot.sameController || s.slot.sameOwner || s.slot.sharing != null || s.slot.distinct != null || s.slot.onePer != null || s.slot.totalAtMost != null)
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
                        // CR 702.180a: the creature tapped for harmonize
                        is CostPart.TapObjects -> listOf(part.bind)
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

    /** Other ways to pay its mana (CR 702.51, 702.66, 702.126); an activated ability's own ways (blb gaps 2); ecl: also those
     * other objects give the spell ("Creature spells you cast have convoke", CR 601.2a). */
    val manaWays: List<ManaPaymentWay> by lazy {
        (if (!isSpell) (ability as? Ability.Activated)?.rules?.payManaBy.orEmpty() else
            withGrantedAsSpell().flatMap { (it.ability as? Ability.Static)?.mechanics.orEmpty().filterIsInstance<Mechanic.PayManaBy>().map { m -> m.way } }) +
            // tla: one Waterbend for each generic mana of its waterbend costs (CR 701.67b; Mana.specialPayers)
            List(total.waterbend) { ManaPaymentWay.Waterbend }
    }

    /** ecl: the permission it is cast with lets mana be spent as though it were mana of any type (CR 609.4b). */
    val manaAsAnyColor: Boolean get() = permission?.first?.manaAsAnyColor == true

    /** "You can't spend mana to cast this spell": only its other ways pay (convoke, delve). */
    val cantSpendMana: Boolean = isSpell &&
        snap0Abilities().any { (it.ability as? Ability.Static)?.mechanics.orEmpty().any { m -> m == Mechanic.CantSpendMana } }

    /** ecl: the greatest X may be ("X can't be greater than the greatest toughness among creatures you control", CR 107.3a). */
    fun xMaximum(): Int? = snap0Abilities().flatMap { (it.ability as? Ability.Static)?.mechanics.orEmpty() }
        .filterIsInstance<Mechanic.XAtMost>().minOfOrNull { eval.value(it.max) }

    /** The least X may be ("X can't be 0", CR 107.3). */
    private fun xMinimum(): Int = if (!isSpell) (ability as? Ability.Activated)?.rules?.minimumX ?: 0 // tla: an activated ability's own
        else snap0Abilities().flatMap { (it.ability as? Ability.Static)?.mechanics.orEmpty() }
            .filterIsInstance<Mechanic.XAtLeast>().maxOfOrNull { it.min } ?: 0

    // ---- The next question

    fun next(): Question? {
        // CR 601.2b: modes, optional and additional costs, announced values; ecl: optional costs first when how many modes may be
        // chosen depends on them ("If this spell's additional cost was paid, choose both instead")
        val modesFirst = instructions.modes?.maxIf == null
        if (modesFirst) instructions.modes?.let { modes -> if (choices.names[MODES_ASKED] == null) return Question.Modes(modes) }
        additionalCosts.forEach { (inst, m) -> if (m.optional && optionalTimes(inst) == null) return Question.OptionalCost(optionalKey(inst), m) }
        if (!modesFirst) instructions.modes?.let { modes -> if (choices.names[MODES_ASKED] == null) return Question.Modes(modes) }
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
            // ecl: the choice is made before the parts that read it
            if (part is CostPart.Choose) {
                if (choices.names[part.bind] == null) return Question.CostChoice(kp.key, part)
                return@forEach
            }
            if (part is CostPart.ChoosePlayer) {
                if (choices.names[PLAYER + kp.key] == null) return Question.Player(kp.key, snap.state.playersFrom(player).filter { eval.matches(part.filter, it) })
                return@forEach
            }
            val choice = Costs.objectChoice(snap, player, if (isSpell) itemId else null, part, eval) ?: return@forEach
            if (choices.names[OBJECTS + kp.key] == null) return Question.Objects(kp.key, choice)
        }
        if (isManaAbility) colorQuestion()?.let { return it }
        // CR 702.132a: before mana abilities are activated, another player may be chosen to pay part of the generic mana
        if (assistable > 0) {
            val a = choices.assist ?: return Question.Assist(snap.state.playersFrom(player).filter { it != player })
            if (assistingLeft) return if (total.hasMana && manaToPay.symbols.isNotEmpty()) Question.Pay(manaToPay) else null
            if (a.player != null && a.amount == null) return Question.AssistAmount(a.player, assistable)
            if (a.player != null && (a.amount ?: 0) > 0 && a.uses == null) return Question.AssistPay(a.player, ManaCost(listOf(ManaSymbol.Generic(a.amount!!))))
        }
        if (total.hasMana && manaToPay.symbols.isNotEmpty()) return Question.Pay(manaToPay)
        return null
    }

    /** Assist (CR 702.132a): the generic mana of the total cost another player may pay; 0 without assist. */
    val assistable: Int by lazy {
        if (!isSpell || byEffect || withGrantedAsSpell().none { it.ability == Keyword.Assist }) 0
        else total.mana.symbols.filterIsInstance<ManaSymbol.Generic>().sumOf { it.amount }
    }

    /** CR 800.4f: the player chosen to assist left the game: "that cost is not paid", so their part is 0 and the caster pays the
     * whole cost; nobody else is asked. */
    val assistingLeft: Boolean get() = choices.assist?.player?.let { base.player(it).lost } == true

    /** The generic mana the assisting player pays (CR 702.132a): 0 when nobody assists or they left the game (CR 800.4f). */
    val assistPaid: Int get() = if (assistingLeft) 0 else choices.assist?.amount ?: 0

    /** The total mana cost the caster pays: less the generic mana an assisting player pays (CR 702.132a). */
    val manaToPay: ManaCost get() {
        var left = assistPaid
        if (left == 0) return total.mana
        return ManaCost(total.mana.symbols.mapNotNull { s ->
            if (s !is ManaSymbol.Generic || left == 0) return@mapNotNull s
            val taken = minOf(left, s.amount)
            left -= taken
            if (s.amount - taken > 0) ManaSymbol.Generic(s.amount - taken) else null
        })
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
            // ecl: the parts after a choice not made yet may depend on it ("behold two creatures of that type")
            if (part is CostPart.Choose && choices.names[part.bind] == null) return null
            if (part is CostPart.OneOf) {
                if (part.options.none { option -> Answers.payable(this, option.parts) }) return "None of ${labels(part)} can be paid"
                return@forEach
            }
            // tdm: objects a cost names by a value still to be announced ("Sacrifice a creature you control with mana value X") are
            // checked once it is (CR 601.2b before 601.2h)
            val unannounced = instructions.announced.any { it !in choices.announced } &&
                kotlinx.serialization.json.Json.encodeToString(CostPart.serializer(), part).contains("\"announced\"")
            if (choices.names[OBJECTS + kp.key] == null && !unannounced) {
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
            if (!unannounced && !Answers.payable(this, listOf(part))) return "Can't pay ${Costs.describe(part)}"
        }
        return null
    }

    /**
     * CR 700.2a, 601.2b: a mode that couldn't choose its targets (a slot needing targets with too few legal ones) can't be chosen.
     * A slot whose options can't be judged yet (it reads a value announced later) doesn't rule the mode out.
     */
    private fun modeWithoutTargets(mode: arcanum.api.Mode): Boolean = mode.instructions.targets.any { slot ->
        slot.perPlayer == null && (slot.onlyIf?.let { holdsAsCast(it) } ?: true) && runCatching {
            targetQuestion(SlotOf(slot.name, slot, null), null)?.let { it.options.size < it.min } ?: false
        }.getOrDefault(false)
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
            val yields = Mana.yields(snap, player, sourceId, a.copy(instructions = a.instructions.copy(steps = listOf(Mana.withChosenColors(a, step)))), manaNames())
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
            val chosenBefore = snap.state.obj(sourceId)?.castOrNone?.modesChosen.orEmpty()
            val raised = q.modes.maxIf != null && holdsAsCast(q.modes.maxIf!!)
            val printed = if (raised) q.modes.maxIfCount ?: q.modes.max else q.modes.max
            // msh: "choose up to X"
            val counted = q.modes.maxCount?.let { minOf(printed, maxOf(q.modes.min, eval.value(it))) } ?: printed
            // CR 702.42a: entwine lets every mode be chosen (its cost is added once more than the printed number are)
            val entwine = additionalCosts.any { it.second.keyword == arcanum.api.Keyword.Entwine.Kind }
            val max = if (entwine) maxOf(counted, q.modes.options.size) else counted
            // ecl: "Choose one. If …, choose both instead": exactly that many
            // dsk backfill: "choose one or more instead" keeps the minimum (CR 700.2d)
            val min = if (raised && q.modes.min == q.modes.max && !q.modes.upToMaxIf) max else q.modes.min
            Decision.Modes(id, player, sourceId, q.modes.options.mapIndexed { i, m ->
                ModeOption(i, m.text, available = !(q.modes.onlyUnchosen && i in chosenBefore) && !modeWithoutTargets(m), pawprints = m.pawprints)
            }, min, max, q.modes.repeatable, pawprintLimit = q.modes.pawprintLimit)
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
        is Question.Objects -> Decision.ChooseObjects(id, player, sourceId, q.choice.options, q.choice.min, q.choice.max, q.choice.text,
            q.choice.total?.let { arcanum.api.Total(it.property, arcanum.api.Value.Fixed(eval.value(it.atLeast))) },
            cost = total.parts.firstOrNull { it.key == q.key }?.at)
        is Question.ManaColor -> Decision.ChooseColor(id, player, sourceId, q.among)
        is Question.Player -> Decision.ChoosePlayer(id, player, sourceId, q.among)
        is Question.Assist -> Decision.ChooseOption(id, player, sourceId, listOf(NOBODY) + q.among.map { it.value }, "Choose a player to assist")
        is Question.AssistAmount -> Decision.ChooseNumber(id, q.player, sourceId, 0, q.max)
        // CR 702.132a, 106.6: the assisting player spends mana to cast this spell: mana restricted to such spells may pay
        is Question.AssistPay -> Decision.ManaPayment(id, q.player, sourceId, q.cost, Mana.options(snap, q.player, q.cost, itemId, emptyList(), false),
            Mana.suggest(snap, q.player, q.cost, itemId, emptyList()))
        // eoe: objects chosen with the costs ("choose a creature you control or a warped creature card you own in exile", CR 601.2f)
        is Question.CostChoice -> (q.part.choice as? arcanum.api.Choice.Objects)?.let { choice ->
            Decision.ChooseObjects(id, player, sourceId, costChoiceObjects(choice), eval.value(choice.min), choice.max?.let { eval.value(it) }, "Choose",
                cost = total.parts.firstOrNull { it.key == q.key }?.at)
        } ?: Decision.ChooseSubtype(id, player, sourceId, (q.part.choice as? arcanum.api.Choice.CreatureTypeAmong)?.among)
        is Question.Pay -> if (cantSpendMana) {
            Decision.ManaPayment(id, player, sourceId, q.cost, Mana.options(snap, player, q.cost, itemId, manaWays, manaAsAnyColor, reserved)
                .filter { it is ManaUse.Special || it is ManaUse.Life }, Mana.suggest(snap, player, q.cost, itemId, manaWays, canSpendMana = false, reserved = reserved))
        } else Decision.ManaPayment(id, player, sourceId, q.cost,
            Mana.options(snap, player, q.cost, itemId, manaWays, manaAsAnyColor, reserved),
            Mana.suggest(snap, player, q.cost, itemId, manaWays, anyColor = manaAsAnyColor, reserved = reserved))
    }

    /** eoe: the objects a [CostPart.Choose] of objects may choose, in the zones its filter names (the battlefield otherwise). */
    fun costChoiceObjects(choice: arcanum.api.Choice.Objects): List<ObjectId> =
        eval.inZonesOf(choice.filter).filter { snap.state.obj(it) != null && eval.matches(choice.filter, it) }

    companion object {
        /** The target slot of an Aura spell (CR 303.4a) and of a bestowed spell (CR 702.103b). */
        const val ENCHANT_SLOT = arcanum.api.Keyword.Enchant.SLOT
        /** Assist's "no player" option (CR 702.132a: "you may choose another player"). */
        const val NOBODY = "nobody"

        // Keys in [arcanum.engine.decision.Choices.names] the casting flow keeps for itself; [publicNames] drops them.
        const val MODES_ASKED = "\$modes"
        const val ONE_OF = "\$oneof:"
        /** blb: the player chosen for a [CostPart.ChoosePlayer] cost, by its key. */
        const val PLAYER = "\$player:"
        const val OBJECTS = "\$objects:"
        const val PER_PLAYER = "\$per-player:"
        const val COLOR = "\$color:"

        fun publicNames(names: Bindings): Bindings = Bindings(names.values.filterKeys { !it.startsWith("$") })


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

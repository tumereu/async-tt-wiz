package arcanum.engine.view

import arcanum.api.Ability
import arcanum.api.Affected
import arcanum.api.CardType
import arcanum.api.Color
import arcanum.api.Condition
import arcanum.api.CounterKind
import arcanum.api.Duration
import arcanum.api.Effect
import arcanum.api.Instructions
import arcanum.api.Keyword
import arcanum.api.KeywordKind
import arcanum.api.Layer
import arcanum.api.ManaCost
import arcanum.api.ManaKind
import arcanum.api.ManaProduction
import arcanum.api.ManaSymbol
import arcanum.api.Mechanic
import arcanum.api.Objects
import arcanum.api.PlayerFilter
import arcanum.api.Players
import arcanum.api.Step
import arcanum.api.Subtype
import arcanum.api.Supertype
import arcanum.api.Timing
import arcanum.api.Zone
import arcanum.api.ActivationRules
import arcanum.api.Cost
import arcanum.api.CostPart
import arcanum.engine.query.Board
import arcanum.engine.query.Context
import arcanum.engine.query.Evaluator
import arcanum.engine.state.Bindings
import arcanum.engine.state.EffectId
import arcanum.engine.state.EffectObject
import arcanum.engine.state.GameState
import arcanum.engine.state.ObjectId
import arcanum.engine.state.PlayerId
import arcanum.engine.state.StackItemKind

/**
 * The layer system (CR 613): works out every object's characteristics from the state. Layer 1 is [Copiable]; layers
 * 2–7 apply continuous effects from static abilities of objects where they function (CR 113.6), from effect objects,
 * and from counters, layer by layer:
 *
 * - within a layer, characteristic-defining abilities first (CR 613.3, 613.4a), then by dependency (CR 613.8) and
 *   timestamp (CR 613.7);
 * - an effect that started applying in an earlier layer keeps its affected set and keeps applying even if its ability
 *   is gone (CR 613.6);
 * - a static ability's effect has its object's timestamp, or its grant's if later (CR 613.7a);
 * - after layer 4, subtypes that no longer correspond to a type go (CR 205.3d) and lands get the mana abilities of
 *   their basic land types (CR 305.6);
 * - keyword abilities expand into their abilities as they are gained (D38).
 *
 * Dependency (CR 613.8a) is decided the way the rule says: an effect depends on another in the same layer if applying
 * the other would change whether it exists, what it applies to, or what it does to them.
 */
internal object Layers {
    fun view(state: GameState): View = LayerRun(state).run()

    /** The layer a mechanic applies in; a characteristic-defining P/T applies in 7a (CR 613.4a). */
    fun layerOf(mechanic: Mechanic, characteristicDefining: Boolean): Layer? = when {
        mechanic is Mechanic.SetPowerToughness && characteristicDefining -> Layer.PowerToughnessDefining
        else -> mechanic.layer
    }

    /** Mechanics with a scope of their own: game rules (CR 613.11), not applied to the static ability's object. */
    fun isRule(mechanic: Mechanic): Boolean = mechanic is Mechanic.Replacement || mechanic is Mechanic.Prevention ||
        mechanic is Mechanic.CostChange || mechanic is Mechanic.TriggersAdditionalTime || mechanic is Mechanic.DamageCantBePrevented

    /** Whether an effect object still applies: its "for as long as" condition holds and what it lasts until is still
     * there (CR 611.2b, 610.3). Ending it is a change made by the flow that notices; the view doesn't wait for that. */
    fun active(state: GameState, board: Board, effect: EffectObject): Boolean = when (val d = effect.duration) {
        is Duration.AsLongAs -> Evaluator(board, Context(effect.controller, effect.source, effect.names)).holds(d.condition)
        is Duration.UntilLeaves -> Evaluator(board, Context(effect.controller, effect.source, effect.names)).objects(d.objects)
            .any { state.obj(it)?.zone == Zone.Battlefield }
        else -> true
    }

    /** The intrinsic mana ability of a basic land type (CR 305.6): "{T}: Add [mana symbol]." */
    fun landAbility(landType: Subtype, color: Color): AbilityInstance = AbilityInstance(
        Ability.Activated(
            cost = Cost(listOf(CostPart.TapSelf)),
            instructions = Instructions(steps = listOf(Step.Do(Effect.AddMana(
                ManaProduction(ManaKind.Fixed(ManaCost(listOf(ManaSymbol.Colored(color))))), players = Players.You,
            )))),
            rules = ActivationRules(timing = Timing.Any, activatedBy = PlayerFilter.You),
        ),
        AbilityOrigin.LandType(landType),
    )

    val ORDER: List<Layer> = listOf(
        Layer.Control, Layer.Text, Layer.Type, Layer.Color, Layer.Ability,
        Layer.PowerToughnessDefining, Layer.PowerToughnessSet, Layer.PowerToughnessModify, Layer.PowerToughnessSwitch,
    )
}

/** One object's characteristics part-way through the layers. */
internal data class Partial(
    val chars: Characteristics,
    val controller: PlayerId?,
    /** Keywords it can't have (CR 613.1f). */
    val cantHave: Set<KeywordKind> = emptySet(),
)

/** The board part-way through the layers: the evaluator reads characteristics as far as they are worked out. */
internal class WorkBoard(override val state: GameState, val work: MutableMap<ObjectId, Partial>) : Board {
    override fun characteristics(id: ObjectId): Characteristics? = work[id]?.chars

    override fun controller(id: ObjectId): PlayerId? {
        val o = state.obj(id) ?: return work[id]?.controller ?: state.stackItems[id]?.controller
        // CR 108.4a: off the battlefield and the Stack, the owner answers for a card
        return if (o.zone == Zone.Battlefield || o.zone == Zone.Stack) work[id]?.controller ?: o.controller ?: o.owner else o.owner
    }

    fun copy(): WorkBoard = WorkBoard(state, LinkedHashMap(work))
}

/** A continuous effect (CR 611) while the layers are worked out. */
internal class ContinuousEffect(
    val key: String,
    val timestamp: Long,
    /** From a characteristic-defining ability (CR 604.3). */
    val cda: Boolean,
    val mechanics: List<Mechanic>,
    val you: PlayerId,
    val self: ObjectId?,
    val names: Bindings,
    val affects: Affected?,
    /** Affected objects fixed when it was created (effect objects, CR 611.2c). */
    val locked: List<ObjectId>?,
    val condition: Condition?,
    /** For a static ability: the ability and where it is. */
    val holder: ObjectId?,
    val instance: AbilityInstance?,
    val effect: EffectId?,
) {
    fun mechanicsIn(layer: Layer) = mechanics.withIndex().filter { Layers.layerOf(it.value, cda) == layer }
}

/** What applying an effect would do in one layer: whether it exists, what it applies to, and to each what. */
private data class Resolved(val exists: Boolean, val affected: Set<ObjectId>, val ops: Map<ObjectId, List<Op>>)

/** One concrete change to a [Partial], with every value worked out. */
private sealed interface Op {
    data class Control(val player: PlayerId) : Op
    data class Types(
        val add: List<CardType>, val addSubtypes: List<Subtype>, val addSupertypes: List<Supertype>,
        val remove: List<CardType>, val replaceTypes: Boolean, val replaceSubtypes: Boolean,
    ) : Op
    data object AllCreatureTypes : Op
    data class Colors(val colors: List<Color>, val add: Boolean) : Op
    data class Gain(val abilities: List<AbilityInstance>) : Op
    data class Lose(val keyword: KeywordKind?) : Op
    data class CantHave(val keyword: KeywordKind) : Op
    data class SetPT(val power: Int?, val toughness: Int?) : Op
    data class ModifyPT(val power: Int, val toughness: Int) : Op
    data object Switch : Op
}

private class Started(val effect: ContinuousEffect, val affected: Set<ObjectId>)

internal class LayerRun(private val state: GameState) {
    private val copiable = Copiable(state)
    private val started = LinkedHashMap<String, Started>()

    fun run(): View {
        val board = WorkBoard(state, initialWork())
        Layers.ORDER.forEach { layer ->
            runLayer(board, layer)
            if (layer == Layer.Type) afterTypes(board)
        }
        finishPowerToughness(board)
        return collect(board)
    }

    // ---- Layer 1 and the starting values

    private fun initialWork(): MutableMap<ObjectId, Partial> {
        val work = LinkedHashMap<ObjectId, Partial>()
        state.objects.values.forEach { o ->
            val faces = copiable.faces(o.id) ?: return@forEach
            val controller = if (o.zone == Zone.Battlefield || o.zone == Zone.Stack) o.controller ?: o.owner else null
            work[o.id] = Partial(Copiable.initial(o, faces, o.timestamp), controller)
        }
        state.stackItems.values.filter { it.kind is StackItemKind.SpellCopy }.forEach { item ->
            val faces = copiable.faces(item.id) ?: return@forEach
            work[item.id] = Partial(Copiable.initial(null, faces, item.timestamp), item.controller)
        }
        return work
    }

    // ---- Layers 2–7

    private fun runLayer(board: WorkBoard, layer: Layer) {
        val applied = mutableSetOf<String>()
        while (true) {
            val candidates = gather(board, layer).filter { it.key !in applied }
            if (candidates.isEmpty()) return
            // CR 613.3, 613.4a: characteristic-defining abilities first
            val pool = candidates.filter { it.cda }.ifEmpty { candidates }
            val next = pickNext(board, layer, pool)
            val resolved = resolve(board, next, layer)
            if (next.key !in started && resolved.exists) started[next.key] = Started(next, resolved.affected)
            apply(board, resolved)
            applied += next.key
        }
    }

    /** CR 613.8: the next effect to apply: one that depends on no other effect still waiting (except through a
     * dependency loop, CR 613.8b), the earliest timestamp first (CR 613.7). */
    private fun pickNext(board: WorkBoard, layer: Layer, pool: List<ContinuousEffect>): ContinuousEffect {
        val byTimestamp = pool.sortedWith(compareBy({ it.timestamp }, { it.key }))
        if (pool.size == 1) return pool.single()
        val dependsOn = pool.associate { b ->
            val alone = resolve(board, b, layer)
            b.key to pool.filter { a ->
                a !== b && run {
                    val after = board.copy()
                    apply(after, resolve(board, a, layer))
                    resolve(after, b, layer) != alone
                }
            }.map { it.key }.toSet()
        }
        fun reaches(from: String, to: String): Boolean {
            val seen = mutableSetOf(from)
            val queue = ArrayDeque(listOf(from))
            while (queue.isNotEmpty()) {
                val k = queue.removeFirst()
                if (k == to && k != from) return true
                dependsOn[k].orEmpty().forEach { if (it == to) return true; if (seen.add(it)) queue.add(it) }
            }
            return false
        }
        val ready = byTimestamp.filter { b -> dependsOn[b.key].orEmpty().all { a -> reaches(a, b.key) } }
        return ready.firstOrNull() ?: byTimestamp.first()
    }

    /** Every continuous effect with a mechanic in [layer], as the board is now; effects that already started keep
     * applying (CR 613.6). */
    private fun gather(board: WorkBoard, layer: Layer): List<ContinuousEffect> {
        val found = LinkedHashMap<String, ContinuousEffect>()
        started.values.forEach { s -> if (s.effect.mechanicsIn(layer).isNotEmpty()) found[s.effect.key] = s.effect }
        staticEffects(board).forEach { e ->
            if (e.key !in found && e.mechanicsIn(layer).isNotEmpty() && conditionHolds(board, e)) found[e.key] = e
        }
        effectObjects(board).forEach { e -> if (e.key !in found && e.mechanicsIn(layer).isNotEmpty()) found[e.key] = e }
        counterEffects(board, layer).forEach { e -> if (e.key !in found) found[e.key] = e }
        return found.values.toList()
    }

    /** Static abilities of objects where they function (CR 113.6), not phased out (CR 702.26b). */
    private fun staticEffects(board: WorkBoard): List<ContinuousEffect> = board.work.flatMap { (id, partial) ->
        val o = state.obj(id) ?: return@flatMap emptyList()
        if (o.phasedOut) return@flatMap emptyList()
        val you = board.controller(id) ?: o.owner
        partial.chars.abilities.mapNotNull { instance ->
            val ability = instance.ability as? Ability.Static ?: return@mapNotNull null
            if (o.zone !in Functioning.zones(ability, partial.chars.isInstantOrSorcery())) return@mapNotNull null
            ContinuousEffect(
                key = "s:$id:${instance.origin.key}",
                // CR 613.7a: the object's timestamp, or the grant's if later
                timestamp = maxOf(o.timestamp, instance.timestamp ?: 0),
                cda = ability.characteristicDefining,
                mechanics = ability.mechanics, you = you, self = id, names = Bindings.EMPTY,
                affects = ability.affects, locked = null, condition = ability.condition,
                holder = id, instance = instance, effect = null,
            )
        }
    }

    private fun effectObjects(board: WorkBoard): List<ContinuousEffect> = state.effects.values
        .filter { Layers.active(state, board, it) }
        .map { e ->
            ContinuousEffect(
                key = "e:${e.id.value}", timestamp = e.timestamp, cda = false, mechanics = e.mechanics, you = e.controller,
                self = e.source, names = e.names, affects = e.affects, locked = e.locked, condition = null,
                holder = null, instance = null, effect = e.id,
            )
        }

    /** CR 613.4c, 613.1f, 613.7c: P/T counters in 7c and keyword counters in layer 6, at the counters' timestamps. */
    private fun counterEffects(board: WorkBoard, layer: Layer): List<ContinuousEffect> = board.work.keys.flatMap { id ->
        val o = state.obj(id) ?: return@flatMap emptyList()
        if (o.phasedOut) return@flatMap emptyList()
        o.counters.mapNotNull { entry ->
            val mechanic = when (val kind = entry.kind) {
                is CounterKind.PowerToughness -> if (layer == Layer.PowerToughnessModify && o.zone == Zone.Battlefield) {
                    Mechanic.ModifyPowerToughness(arcanum.api.Value.Fixed(kind.power * entry.count), arcanum.api.Value.Fixed(kind.toughness * entry.count))
                } else null
                is CounterKind.Keyword -> if (layer == Layer.Ability) {
                    (kind.keyword as? Keyword)?.let { Mechanic.GainAbilities(listOf(it)) }
                } else null
                else -> null
            } ?: return@mapNotNull null
            ContinuousEffect(
                key = "c:$id:${entry.kind}", timestamp = entry.timestamp, cda = false, mechanics = listOf(mechanic),
                you = board.controller(id) ?: o.owner, self = id, names = Bindings.EMPTY,
                affects = Affected.Objects(Objects.Self), locked = null, condition = null, holder = null,
                instance = AbilityInstance(arcanum.api.Ability.Unparsed("counter"), AbilityOrigin.FromCounter(entry.kind)), effect = null,
            )
        }
    }

    private fun evaluator(board: Board, e: ContinuousEffect, affected: ObjectId? = null) =
        Evaluator(board, Context(you = e.you, self = e.self, names = e.names, affected = affected))

    private fun conditionHolds(board: WorkBoard, e: ContinuousEffect): Boolean =
        e.condition?.let { evaluator(board, e).holds(it) } ?: true

    /** Whether a static ability's effect still exists on this board: its object still has the ability where it
     * functions and its condition holds. */
    private fun exists(board: WorkBoard, e: ContinuousEffect): Boolean {
        if (e.key in started) return true
        val holder = e.holder ?: return true
        val partial = board.work[holder] ?: return false
        val instance = e.instance ?: return true
        if (partial.chars.abilities.none { it.origin == instance.origin && it.ability == instance.ability }) return false
        val o = state.obj(holder) ?: return false
        if (o.zone !in Functioning.zones(instance.ability, partial.chars.isInstantOrSorcery())) return false
        return conditionHolds(board, e)
    }

    private fun affected(board: WorkBoard, e: ContinuousEffect): Set<ObjectId> {
        started[e.key]?.let { return it.affected }
        val ids = e.locked ?: when (val a = e.affects) {
            is Affected.Objects -> evaluator(board, e).objects(a.objects)
            else -> emptyList()
        }
        return ids.filter { it in board.work && state.obj(it)?.phasedOut != true }.toSet()
    }

    private fun resolve(board: WorkBoard, e: ContinuousEffect, layer: Layer): Resolved {
        if (!exists(board, e)) return Resolved(false, emptySet(), emptyMap())
        val affected = affected(board, e)
        val mechanics = e.mechanicsIn(layer)
        val ops = affected.associateWith { id -> mechanics.mapNotNull { (index, m) -> op(board, e, index, m, id) } }
        return Resolved(true, affected, ops)
    }

    private fun op(board: WorkBoard, e: ContinuousEffect, index: Int, mechanic: Mechanic, id: ObjectId): Op? {
        val ev = evaluator(board, e, affected = id)
        return when (mechanic) {
            is Mechanic.ControlledBy -> ev.players(mechanic.players).firstOrNull()?.let { Op.Control(it) }
            is Mechanic.SetTypes -> Op.Types(
                mechanic.add, mechanic.addSubtypes.flatMap { ev.subtypes(it) }, mechanic.addSupertypes, mechanic.remove,
                mechanic.replaceTypes, mechanic.replaceSubtypes,
            )
            Mechanic.AllCreatureTypes -> Op.AllCreatureTypes
            is Mechanic.SetColors -> Op.Colors(mechanic.colors.flatMap { ev.colors(it) }.distinct(), mechanic.add)
            is Mechanic.GainAbilities -> Op.Gain(Copiable.expand(mechanic.abilities.mapIndexed { i, ability ->
                AbilityInstance(ability, grantOrigin(e, index, i), e.timestamp)
            }))
            is Mechanic.GainAbilitiesOf -> Op.Gain(ev.objects(mechanic.objects).flatMap { from ->
                board.work[from]?.chars?.abilities.orEmpty()
                    .filter { it.ability is Ability.Activated }
                    .filter { !mechanic.exceptLoyalty || (it.ability as Ability.Activated).cost.parts.none { p -> p is CostPart.Loyalty } }
                    .map { AbilityInstance(it.ability, AbilityOrigin.Borrowed(from, it.origin), e.timestamp) }
            })
            is Mechanic.LoseAbilities -> Op.Lose(mechanic.keyword)
            is Mechanic.CantHaveAbility -> Op.CantHave(mechanic.keyword)
            is Mechanic.SetPowerToughness -> Op.SetPT(mechanic.power?.let { ev.value(it) }, mechanic.toughness?.let { ev.value(it) })
            is Mechanic.ModifyPowerToughness -> Op.ModifyPT(ev.value(mechanic.power), ev.value(mechanic.toughness))
            Mechanic.SwitchPowerToughness -> Op.Switch
            // Layer 1 (copy) is worked out by Copiable; text-changing effects (layer 3) have no mechanics yet.
            else -> null
        }
    }

    private fun grantOrigin(e: ContinuousEffect, mechanic: Int, index: Int): AbilityOrigin = when {
        e.effect != null -> AbilityOrigin.FromEffect(e.effect, mechanic, index)
        e.instance?.origin is AbilityOrigin.FromCounter -> e.instance.origin
        e.holder != null && e.instance != null -> AbilityOrigin.FromAbility(e.holder, e.instance.origin, mechanic, index)
        else -> AbilityOrigin.FromEffect(EffectId(e.key), mechanic, index)
    }

    private fun apply(board: WorkBoard, resolved: Resolved) {
        resolved.ops.forEach { (id, ops) ->
            val partial = board.work[id] ?: return@forEach
            board.work[id] = ops.fold(partial) { p, op -> applyOp(p, op) }
        }
    }

    private fun applyOp(p: Partial, op: Op): Partial {
        val c = p.chars
        return when (op) {
            is Op.Control -> p.copy(controller = op.player)
            is Op.Types -> {
                // CR 205.1a: setting card types keeps instant and sorcery
                val types = (if (op.replaceTypes) op.add + c.types.filter { it == CardType.Instant || it == CardType.Sorcery } else c.types + op.add)
                    .distinct().filterNot { it in op.remove }
                // CR 205.1a: new subtypes replace those of the same kind only
                val replacedKinds = if (op.replaceSubtypes) op.addSubtypes.map { Subtypes.kindOf(it) }.toSet() else emptySet()
                val subtypes = (c.subtypes.filterNot { Subtypes.kindOf(it) in replacedKinds } + op.addSubtypes).distinct()
                // CR 305.7: a land whose subtype is set to basic land types loses the abilities from its rules text and
                // old land types (it gets the new types' mana abilities after layer 4)
                val setsBasicLandType = op.replaceSubtypes && op.addSubtypes.any { it in Subtypes.basicLandTypes }
                val abilities = if (setsBasicLandType && CardType.Land in types) c.abilities.filterNot { it.origin.isOwn() } else c.abilities
                p.copy(chars = c.copy(
                    types = types, subtypes = subtypes, supertypes = (c.supertypes + op.addSupertypes).distinct(),
                    allCreatureTypes = c.allCreatureTypes && SubtypeKind.Creature !in replacedKinds, abilities = abilities,
                ))
            }
            Op.AllCreatureTypes -> p.copy(chars = c.copy(allCreatureTypes = true))
            is Op.Colors -> p.copy(chars = c.copy(colors = Color.all.filter { it in op.colors || (op.add && it in c.colors) }))
            is Op.Gain -> p.copy(chars = c.copy(abilities = c.abilities + op.abilities.filterNot { it.isKeywordIn(p.cantHave) }))
            is Op.Lose -> p.copy(chars = c.copy(abilities = if (op.keyword == null) emptyList() else c.abilities.without(op.keyword)))
            is Op.CantHave -> p.copy(chars = c.copy(abilities = c.abilities.without(op.keyword)), cantHave = p.cantHave + op.keyword)
            is Op.SetPT -> p.copy(chars = c.copy(power = op.power ?: c.power, toughness = op.toughness ?: c.toughness))
            is Op.ModifyPT -> p.copy(chars = c.copy(power = (c.power ?: 0) + op.power, toughness = (c.toughness ?: 0) + op.toughness))
            Op.Switch -> p.copy(chars = c.copy(power = c.toughness, toughness = c.power))
        }
    }

    /** CR 205.3d: drop subtypes that no longer correspond to a type; CR 305.6: lands have the mana ability of each
     * basic land type they have. */
    private fun afterTypes(board: WorkBoard) {
        board.work.replaceAll { _, p ->
            val c = p.chars
            val subtypes = c.subtypes.filter { s -> Subtypes.kindOf(s).types.any { it in c.types } }
            val allCreatureTypes = c.allCreatureTypes && SubtypeKind.Creature.types.any { it in c.types }
            val landAbilities = if (CardType.Land in c.types) {
                subtypes.mapNotNull { s -> Subtypes.basicLandTypes[s]?.let { Layers.landAbility(s, it) } }
            } else emptyList()
            p.copy(chars = c.copy(
                subtypes = subtypes, allCreatureTypes = allCreatureTypes,
                abilities = c.abilities.filterNot { it.origin is AbilityOrigin.LandType } + landAbilities,
            ))
        }
    }

    /** CR 208.3: a noncreature permanent has no power or toughness; a creature without them has 0. */
    private fun finishPowerToughness(board: WorkBoard) {
        board.work.replaceAll { id, p ->
            val c = p.chars
            val onBattlefield = state.obj(id)?.zone == Zone.Battlefield
            when {
                CardType.Creature in c.types -> p.copy(chars = c.copy(power = c.power ?: 0, toughness = c.toughness ?: 0))
                onBattlefield -> p.copy(chars = c.copy(power = null, toughness = null))
                else -> p
            }
        }
    }

    // ---- Rules that are not characteristics (D24, CR 613.10, 613.11)

    private fun collect(board: WorkBoard): View {
        val objectMechanics = LinkedHashMap<ObjectId, MutableList<AppliedMechanic>>()
        val playerMechanics = LinkedHashMap<PlayerId, MutableList<AppliedMechanic>>()
        val rules = mutableListOf<AppliedMechanic>()

        fun place(e: ContinuousEffect, mechanic: Mechanic, lockedPlayers: List<PlayerId>?) {
            val applied = AppliedMechanic(mechanic, e.you, e.timestamp, source = e.self, origin = e.instance?.origin,
                effect = e.effect, names = e.names)
            when {
                Layers.isRule(mechanic) || e.affects == null || e.affects is Affected.NextSpells -> rules += applied
                e.affects is Affected.Players -> (lockedPlayers ?: evaluator(board, e).players((e.affects as Affected.Players).players))
                    .forEach { playerMechanics.getOrPut(it) { mutableListOf() } += applied }
                e.affects is Affected.Objects -> evaluator(board, e).objects((e.affects as Affected.Objects).objects)
                    .filter { it in board.work && state.obj(it)?.phasedOut != true }
                    .forEach { objectMechanics.getOrPut(it) { mutableListOf() } += applied }
            }
        }

        staticEffects(board).filter { conditionHolds(board, it) }.forEach { e ->
            e.mechanics.filter { Layers.layerOf(it, e.cda) == null }.forEach { place(e, it, null) }
        }
        state.effects.values.filter { Layers.active(state, board, it) }.forEach { effect ->
            val e = effectObjects(board).first { it.effect == effect.id }
            effect.mechanics.filter { it.layer == null }.forEach { place(e, it, effect.lockedPlayers) }
        }

        val evaluator = Evaluator(board, Context(you = state.turn.active))
        val objects = LinkedHashMap<ObjectId, ObjectView>()
        state.objects.values.forEach { o ->
            val controller = board.work[o.id]?.controller
            objects[o.id] = ObjectView(
                id = o.id, owner = o.owner, controller = controller, zone = o.zone,
                characteristics = board.work[o.id]?.chars, mechanics = objectMechanics[o.id].orEmpty(),
                summoningSick = evaluator.summoningSick(o, controller), obj = o,
            )
        }
        state.stackItems.values.filter { it.kind is StackItemKind.SpellCopy }.forEach { item ->
            objects[item.id] = ObjectView(
                id = item.id, owner = item.controller, controller = board.work[item.id]?.controller ?: item.controller,
                zone = Zone.Stack, characteristics = board.work[item.id]?.chars, mechanics = objectMechanics[item.id].orEmpty(),
                summoningSick = false, obj = null,
            )
        }
        val players = state.players.associate { it.id to PlayerView(it.id, playerMechanics[it.id].orEmpty()) }
        return View(objects, players, rules)
    }

    private fun AbilityOrigin.isOwn(): Boolean = when (root) {
        is AbilityOrigin.Printed, is AbilityOrigin.LandType -> true
        else -> false
    }

    private fun AbilityInstance.isKeywordIn(kinds: Set<KeywordKind>): Boolean =
        kinds.isNotEmpty() && (ability as? Keyword)?.kind in kinds

    /** Without keyword [kind] and the abilities of its expansion. */
    private fun List<AbilityInstance>.without(kind: KeywordKind): List<AbilityInstance> {
        val removed = filter { (it.ability as? Keyword)?.kind == kind }.map { it.origin }.toSet()
        return filterNot { it.origin.comesFrom(removed) }
    }

    /** This origin is one of [origins] or part of the expansion of one. */
    private fun AbilityOrigin.comesFrom(origins: Set<AbilityOrigin>): Boolean =
        this in origins || (this is AbilityOrigin.Expansion && keyword.comesFrom(origins))
}

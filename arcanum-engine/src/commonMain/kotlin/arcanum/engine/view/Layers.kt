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
    /**
     * For tests: every shortcut taken to save work (a view kept across changes, an effect's resolution or the effects
     * of a layer kept across steps, a dependency not tried) is checked against working it out in full.
     */
    internal var checkShortcuts = false

    fun view(state: GameState): View {
        val cache = state.viewCache ?: return LayerRun(state).run()
        cache.view?.let { kept ->
            if (checkShortcuts) check(kept == LayerRun(state).run()) { "A kept view differs from the state's" }
            return kept
        }
        val run = LayerRun(state)
        return run.run().also { cache.set(it, readsLog = run.reads.log) }
    }

    /** [Explanation] of object [id] (spec §7.1): the layers worked out afresh, recording each effect that changed it. */
    fun explain(state: GameState, id: ObjectId): Explanation? {
        val run = LayerRun(state, traced = id)
        val view = run.run()
        val base = run.base ?: return null
        val characteristics = view[id]?.characteristics ?: return null
        return Explanation(id, base, run.trace.toList(), characteristics)
    }

    /** The layer a mechanic applies in; a characteristic-defining P/T applies in 7a (CR 613.4a). */
    fun layerOf(mechanic: Mechanic, characteristicDefining: Boolean): Layer? = when {
        mechanic is Mechanic.SetPowerToughness && characteristicDefining -> Layer.PowerToughnessDefining
        else -> mechanic.layer
    }

    /** Mechanics with a scope of their own: game rules (CR 613.11), not applied to the static ability's object. */
    fun isRule(mechanic: Mechanic): Boolean = mechanic is Mechanic.Replacement || mechanic is Mechanic.Prevention ||
        mechanic is Mechanic.CostChange || mechanic is Mechanic.TriggersAdditionalTime || mechanic is Mechanic.DamageCantBePrevented ||
        mechanic is Mechanic.EventDoesntTrigger // fra

    /** Whether an effect object still applies: its "for as long as" condition holds and what it lasts until is still
     * there (CR 611.2b, 610.3). Ending it is a change made by the flow that notices; the view doesn't wait for that. */
    fun active(state: GameState, board: Board, effect: EffectObject): Boolean = when (val d = effect.duration) {
        is Duration.AsLongAs -> Evaluator(board, Context(effect.controller, effect.source, followed(state, effect))).holds(d.condition)
        is Duration.UntilLeaves -> Evaluator(board, Context(effect.controller, effect.source, effect.names)).objects(d.objects)
            .any { state.obj(it)?.zone == Zone.Battlefield }
        // fra: while the card is where it was, or on the Stack being cast but not cast yet (CR 601.2a, 601.2i)
        is Duration.UntilCast -> ((d.objects as? arcanum.api.Objects.Bound)?.let { (effect.names[it.name] as? arcanum.engine.state.NamedValue.Objects)?.ids }
            ?: Evaluator(board, Context(effect.controller, effect.source, effect.names)).objects(d.objects)).any { id ->
            // being cast: on the Stack, not yet become cast (the provisional Stack item a cast's plan reads isn't that, CR 601.2i)
            state.obj(id) != null || (id as? ObjectId.Card)?.next()?.let { n -> state.obj(n)?.zone == Zone.Stack && state.log.none { entry ->
                (entry.change as? arcanum.engine.change.Change.StackItemAdded)?.let { it.item.id == n && arcanum.engine.change.Meaning.Cast in it.meanings } == true
            } } == true
        }
        else -> true
    }

    /** An effect on a spell and the permanent it becomes (CR 400.7d): its names mean whichever of the two exists now. */
    private fun followed(state: GameState, effect: EffectObject): arcanum.engine.state.Bindings {
        if (effect.names[arcanum.engine.resolution.Effects.FOLLOWS_SPELL] == null) return effect.names
        return arcanum.engine.state.Bindings(effect.names.values.mapValues { (_, v) ->
            if (v is arcanum.engine.state.NamedValue.Objects) arcanum.engine.state.NamedValue.Objects(v.ids.filter { state.obj(it) != null || it in state.stackItems })
            else v
        })
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

    /** blb gaps 2: the replacement effect finality counters create (CR 122.1h). */
    val FINALITY = Mechanic.Replacement(
        arcanum.api.EventPattern.ZoneChange(arcanum.api.ObjectFilter.Is(Objects.Self), from = Zone.Battlefield, to = Zone.Graveyard),
        modify = listOf(arcanum.api.Modification.ChangeDestination(arcanum.api.Destination(Zone.Exile))),
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
internal class WorkBoard(override val state: GameState, val work: MutableMap<ObjectId, Partial>, val reads: ViewReads = ViewReads()) : Board {
    // blb gaps: an object that has left its zone reads as it last existed (CR 608.2h), as in the finished view: "if you
    // cast an instant or sorcery spell this turn" tests the spell after it resolved
    override fun characteristics(id: ObjectId): Characteristics? = work[id]?.chars
        ?: if (state.obj(id) == null && id !in state.stackItems) { readingLog(); snapshot(id)?.characteristics } else null

    override fun controller(id: ObjectId): PlayerId? {
        val o = state.obj(id) ?: return work[id]?.controller ?: state.stackItems[id]?.controller
        // CR 108.4a: off the battlefield and the Stack, the owner answers for a card
        return if (o.zone == Zone.Battlefield || o.zone == Zone.Stack) work[id]?.controller ?: o.controller ?: o.owner else o.owner
    }

    /** Set when the evaluator reads a power or toughness (the dependency check of [LayerRun], CR 613.8a). */
    var powerToughnessRead = false

    override fun readingPowerToughness() {
        powerToughnessRead = true
    }

    override fun readingLog() {
        reads.log = true
    }

    fun copy(): WorkBoard = WorkBoard(state, LinkedHashMap(work), reads)
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
    /** ecl: no creature types (layer 4). */
    data object LoseAllCreatureTypes : Op
    data class Colors(val colors: List<Color>, val add: Boolean) : Op
    data class Gain(val abilities: List<AbilityInstance>) : Op
    data class Lose(val keyword: KeywordKind?) : Op
    data class CantHave(val keyword: KeywordKind) : Op
    data class SetPT(val power: Int?, val toughness: Int?) : Op
    data class ModifyPT(val power: Int, val toughness: Int) : Op
    data object Switch : Op
    // dgm
    /** CR 612: a colour word or basic land type word in its rules text (and a land type in its type line) becomes another. */
    data class Text(val from: TextChange.Word, val to: TextChange.Word) : Op
    /** tla: a name set by an effect (CR 612.8). */
    data class Name(val name: String) : Op
}

private class Started(val effect: ContinuousEffect, val affected: Set<ObjectId>)

internal class LayerRun(private val state: GameState, private val traced: ObjectId? = null) {
    /** What this run read beyond objects and characteristics (kept with the view, [ViewCache]). */
    val reads = ViewReads()
    /** For [Layers.explain]: [traced]'s characteristics after layer 1, and each effect that changed them. */
    var base: Characteristics? = null
        private set
    val trace = mutableListOf<ExplainedEffect>()
    /** Every object's characteristics after layer 1 ([ObjectView.base]). */
    private var initial: Map<ObjectId, Characteristics> = emptyMap()
    private val copiable = Copiable(state, reads)
    private val started = LinkedHashMap<String, Started>()

    fun run(): View {
        val board = WorkBoard(state, initialWork(), reads)
        initial = board.work.mapValues { it.value.chars }
        if (traced != null) base = board.work[traced]?.chars
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
        val resolved = Resolutions(board, layer)
        var gathered: List<ContinuousEffect>? = null
        var gatherReadsPowerToughness = false
        while (true) {
            val all = gathered?.also { kept -> if (Layers.checkShortcuts) checkGathered(kept, board, layer) } ?: run {
                board.powerToughnessRead = false
                gather(board, layer).also { gathered = it; gatherReadsPowerToughness = board.powerToughnessRead }
            }
            val candidates = all.filter { it.key !in applied }
            if (candidates.isEmpty()) return
            // CR 613.3, 613.4a: characteristic-defining abilities first
            val pool = candidates.filter { it.cda }.ifEmpty { candidates }
            val next = pickNext(board, layer, pool, resolved)
            val result = resolved[next]
            if (next.key !in started && result.exists) started[next.key] = Started(next, result.affected)
            val changed = changesBoard(board, result)
            val before = traced?.let { board.work[it] }
            apply(board, result)
            if (before != null) record(layer, next, before, board.work.getValue(traced!!))
            applied += next.key
            // What was worked out on the board before stays true unless it read what this effect changed
            if (changed) {
                val powerToughnessOnly = result.isPowerToughnessOnly()
                if (!powerToughnessOnly || gatherReadsPowerToughness) gathered = null
                resolved.changed(powerToughnessOnly)
            }
        }
    }

    private fun record(layer: Layer, e: ContinuousEffect, before: Partial, after: Partial) {
        if (before == after) return
        val instance = e.instance
        val source = when {
            e.effect != null -> EffectSource.EffectObject(e.effect)
            instance?.origin is AbilityOrigin.FromCounter -> {
                val kind = (instance.origin as AbilityOrigin.FromCounter).kind
                EffectSource.Counters(e.self!!, kind, state.obj(e.self)?.counters?.firstOrNull { it.kind == kind }?.count ?: 0)
            }
            e.holder != null && instance != null -> EffectSource.StaticAbility(e.holder, instance.origin, e.cda)
            else -> return
        }
        trace += ExplainedEffect(layer, source, e.timestamp, before.chars, after.chars,
            before.controller.takeIf { before.controller != after.controller },
            after.controller.takeIf { before.controller != after.controller })
    }

    /** What each effect does on the board as it is now ([resolve]), kept until the board changes in what it read. */
    private inner class Resolutions(private val board: WorkBoard, private val layer: Layer) {
        private val known = HashMap<String, Resolved>()
        private val readsPowerToughness = HashSet<String>()

        operator fun get(e: ContinuousEffect): Resolved = known[e.key]?.also { kept ->
            if (Layers.checkShortcuts) {
                val read = board.powerToughnessRead
                check(kept == resolve(board, e, layer)) { "A kept resolution of ${e.key} differs from the board's" }
                board.powerToughnessRead = read
            }
        } ?: run {
            board.powerToughnessRead = false
            resolve(board, e, layer).also {
                known[e.key] = it
                if (board.powerToughnessRead) readsPowerToughness += e.key
                board.powerToughnessRead = false
            }
        }

        fun readsPowerToughness(e: ContinuousEffect): Boolean = e.key in readsPowerToughness

        /** The board changed: only power and toughness, or more. */
        fun changed(powerToughnessOnly: Boolean) {
            if (powerToughnessOnly) known.keys.removeAll(readsPowerToughness) else known.clear()
            readsPowerToughness.retainAll(known.keys)
        }
    }

    /** CR 613.8: the next effect to apply: one that depends on no other effect still waiting (except through a
     * dependency loop, CR 613.8b), the earliest timestamp first (CR 613.7).
     *
     * Whether b depends on a is tried out: b resolved after a was applied, against b alone. A pair is tried only when
     * applying a changes the board, and not when a changes only power and toughness and b's existence, affected set
     * and values read none (most of layer 7). */
    private fun pickNext(board: WorkBoard, layer: Layer, pool: List<ContinuousEffect>, resolved: Resolutions): ContinuousEffect {
        val byTimestamp = pool.sortedWith(compareBy({ it.timestamp }, { it.key }))
        if (pool.size == 1) return pool.single()
        val beyondPowerToughness = pool.filterNot { a -> resolved[a].isPowerToughnessOnly() }.filter { a -> changesBoard(board, resolved[a]) }
        val changes by lazy { pool.filter { a -> a in beyondPowerToughness || changesBoard(board, resolved[a]) } }
        val dependsOn = pool.associate { b ->
            val bAlone = resolved[b]
            b.key to (if (resolved.readsPowerToughness(b)) changes else beyondPowerToughness).filter { a ->
                a !== b && run {
                    val after = board.copy()
                    apply(after, resolved[a])
                    resolve(after, b, layer) != bAlone
                }
            }.map { it.key }.toSet()
        }
        if (Layers.checkShortcuts) {
            val tried = pool.associate { b ->
                b.key to pool.filter { a ->
                    a !== b && run {
                        val after = board.copy()
                        apply(after, resolve(board, a, layer))
                        resolve(after, b, layer) != resolve(board, b, layer)
                    }
                }.map { it.key }.toSet()
            }
            check(tried == dependsOn) { "Dependencies without trying every pair $dependsOn differ from $tried" }
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

    /** [Layers.checkShortcuts]: the effects gathered for [layer] earlier are those gathered now. */
    private fun checkGathered(kept: List<ContinuousEffect>, board: WorkBoard, layer: Layer) {
        val read = board.powerToughnessRead
        fun ContinuousEffect.parts() = listOf(key, timestamp, cda, mechanics, you, self, names, affects, locked, condition, holder, instance, effect)
        val now = gather(board, layer)
        board.powerToughnessRead = read
        check(kept.map { it.parts() }.toSet() == now.map { it.parts() }.toSet()) { "The effects kept for $layer differ from the board's" }
    }

    /** Whether applying [resolved] would change any object's characteristics or controller on [board]. */
    private fun changesBoard(board: WorkBoard, resolved: Resolved): Boolean = resolved.ops.any { (id, ops) ->
        val partial = board.work[id] ?: return@any false
        ops.isNotEmpty() && ops.fold(partial) { p, op -> applyOp(p, op) } != partial
    }

    private fun Resolved.isPowerToughnessOnly(): Boolean =
        ops.values.all { ops -> ops.all { it is Op.SetPT || it is Op.ModifyPT || it is Op.Switch } }

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
        suspectedEffects(board, layer).forEach { e -> if (e.key !in found) found[e.key] = e }
        return found.values.toList()
    }

    /** Static abilities of objects where they function (CR 113.6), not phased out (CR 702.26b). */
    private fun staticEffects(board: WorkBoard): List<ContinuousEffect> = board.work.flatMap { (id, partial) ->
        val o = state.obj(id) ?: return@flatMap emptyList()
        if (o.phasedOut) return@flatMap emptyList()
        val you = board.controller(id) ?: o.owner
        partial.chars.abilities.mapNotNull { instance ->
            val ability = instance.ability as? Ability.Static ?: return@mapNotNull null
            // blb gaps 2: emblems' abilities work in the command zone (CR 114.4), static ones too
            if (o.zone != Zone.Command && o.zone !in Functioning.zones(ability, partial.chars.isInstantOrSorcery())) return@mapNotNull null
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
    /** spm: a suspected permanent has menace (CR 701.60c), whatever its abilities: not from an ability, so "loses all abilities"
     * before it doesn't remove it; its "can't block" is a rule ([collect]). */
    private fun suspectedEffects(board: WorkBoard, layer: Layer): List<ContinuousEffect> = if (layer != Layer.Ability) emptyList() else
        state.battlefield.filter { id -> id in board.work && state.obj(id)?.let { !it.phasedOut && arcanum.api.ObjectMark.Suspected in it.marks } == true }.map { id ->
            val o = state.obj(id)!!
            ContinuousEffect(
                key = "suspected:$id", timestamp = o.timestamp, cda = false, mechanics = listOf(Mechanic.GainAbilities(listOf(Keyword.Menace))),
                you = board.controller(id) ?: o.owner, self = id, names = Bindings.EMPTY, affects = Affected.Objects(Objects.Self), locked = null,
                condition = null, holder = null, instance = AbilityInstance(arcanum.api.Ability.Unparsed("suspected"), AbilityOrigin.Rule("suspected")), effect = null,
            )
        }

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
                // hob: CR 122.1j: a hone counter on an Equipment gives +1/+0 to the creature it's attached to
                CounterKind.Hone -> if (layer == Layer.PowerToughnessModify && o.zone == Zone.Battlefield && o.attachedTo != null) {
                    Mechanic.ModifyPowerToughness(arcanum.api.Value.Fixed(entry.count), arcanum.api.Value.Fixed(0))
                } else null
                else -> null
            } ?: return@mapNotNull null
            ContinuousEffect(
                key = "c:$id:${entry.kind}", timestamp = entry.timestamp, cda = false, mechanics = listOf(mechanic),
                you = board.controller(id) ?: o.owner, self = id, names = Bindings.EMPTY,
                affects = Affected.Objects(if (entry.kind == CounterKind.Hone) Objects.AttachedTo(Objects.Self) else Objects.Self), locked = null, condition = null, holder = null,
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
        // blb gaps 2: emblems' abilities work in the command zone (CR 114.4)
        if (o.zone != Zone.Command && o.zone !in Functioning.zones(instance.ability, partial.chars.isInstantOrSorcery())) return false
        return conditionHolds(board, e)
    }

    private fun affected(board: WorkBoard, e: ContinuousEffect): Set<ObjectId> {
        started[e.key]?.let { return it.affected }
        val ids = e.locked ?: when (val a = e.affects) {
            // spm: a keyword with an alternative cost given to spells is on the cards as the spells they would be (CR 601.2a, b)
            is Affected.Objects -> if (e.mechanics.isNotEmpty() && e.mechanics.all { grantsAlternativeCost(it) })
                evaluator(board, e).let { ev -> ev.with(ev.ctx.copy(prospectiveCast = true)) }.objects(a.objects).filter { state.obj(it)?.zone != Zone.Battlefield }
                else evaluator(board, e).objects(a.objects)
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
            Mechanic.LoseAllCreatureTypes -> Op.LoseAllCreatureTypes
            is Mechanic.SetColors -> Op.Colors(mechanic.colors.flatMap { ev.colors(it) }.distinct(), mechanic.add)
            is Mechanic.GainAbilities -> Op.Gain(Copiable.expand(mechanic.abilities.mapIndexed { i, ability ->
                AbilityInstance(ability, grantOrigin(e, index, i), e.timestamp)
            }))
            is Mechanic.GainAbilitiesOf -> Op.Gain(ev.objects(mechanic.objects).flatMap { from ->
                board.work[from]?.chars?.abilities.orEmpty()
                    // tla: triggered abilities too when the mechanic says so
                    .filter { it.ability is Ability.Activated || (mechanic.triggered && it.ability is Ability.Triggered) }
                    .filter { !mechanic.exceptLoyalty || (it.ability as? Ability.Activated)?.cost?.parts.orEmpty().none { p -> p is CostPart.Loyalty } }
                    .map { AbilityInstance(it.ability, AbilityOrigin.Borrowed(from, it.origin), e.timestamp) }
            })
            is Mechanic.LoseAbilities -> Op.Lose(mechanic.keyword)
            is Mechanic.CantHaveAbility -> Op.CantHave(mechanic.keyword)
            is Mechanic.SetPowerToughness -> Op.SetPT(mechanic.power?.let { ev.value(it) }, mechanic.toughness?.let { ev.value(it) })
            is Mechanic.ModifyPowerToughness -> Op.ModifyPT(ev.value(mechanic.power), ev.value(mechanic.toughness))
            Mechanic.SwitchPowerToughness -> Op.Switch
            // tla: layer 3, a name set by an effect (CR 612.8)
            is Mechanic.SetName -> Op.Name(mechanic.name)
            // dgm: layer 3, text-changing effects (CR 612)
            is Mechanic.ChangeText -> {
                fun word(w: arcanum.api.TextWord): TextChange.Word? = when (w) {
                    is arcanum.api.TextWord.ColorWord -> ev.colors(w.color).firstOrNull()?.let { TextChange.Word.ColorWord(it) }
                    is arcanum.api.TextWord.LandType -> ev.subtypes(w.subtype).firstOrNull()?.let { TextChange.Word.LandType(it) }
                }
                val from = word(mechanic.from)
                val to = word(mechanic.to)
                if (from == null || to == null || from == to) null else Op.Text(from, to)
            }
            // Layer 1 (copy) is worked out by Copiable.
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
            // ecl: its creature types (also every creature type from changeling) are gone; other subtypes stay
            Op.LoseAllCreatureTypes -> p.copy(chars = c.copy(allCreatureTypes = false, subtypes = c.subtypes.filterNot { Subtypes.kindOf(it) == SubtypeKind.Creature }))
            is Op.Colors -> p.copy(chars = c.copy(colors = Color.all.filter { it in op.colors || (op.add && it in c.colors) }))
            is Op.Gain -> p.copy(chars = c.copy(abilities = c.abilities + op.abilities.filterNot { it.isKeywordIn(p.cantHave) }))
            is Op.Lose -> p.copy(chars = c.copy(abilities = if (op.keyword == null) emptyList() else c.abilities.without(op.keyword)))
            is Op.CantHave -> p.copy(chars = c.copy(abilities = c.abilities.without(op.keyword)), cantHave = p.cantHave + op.keyword)
            is Op.SetPT -> p.copy(chars = c.copy(power = op.power ?: c.power, toughness = op.toughness ?: c.toughness))
            is Op.ModifyPT -> p.copy(chars = c.copy(power = (c.power ?: 0) + op.power, toughness = (c.toughness ?: 0) + op.toughness))
            Op.Switch -> p.copy(chars = c.copy(power = c.toughness, toughness = c.power))
            is Op.Name -> p.copy(chars = c.copy(name = op.name))
            is Op.Text -> {
                // dgm: CR 612.2: a basic land type word in the type line too
                val subtypes = if (op.from is TextChange.Word.LandType && op.to is TextChange.Word.LandType)
                    c.subtypes.map { if (it == op.from.subtype) op.to.subtype else it }.distinct() else c.subtypes
                p.copy(chars = c.copy(subtypes = subtypes, abilities = c.abilities.map { it.copy(ability = TextChange.ability(it.ability, op.from, op.to)) }))
            }
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
            val affects = e.affects
            when {
                // fra: an effect on a spell and the permanent it becomes ("the next spell you cast this turn can't be countered",
                // CR 400.7d) applies to the objects it locked
                !Layers.isRule(mechanic) && affects == null && e.locked != null -> e.locked!!.filter { it in board.work }
                    .forEach { objectMechanics.getOrPut(it) { mutableListOf() } += applied }
                Layers.isRule(mechanic) || affects == null || affects is Affected.NextSpells -> rules += applied
                affects is Affected.Players -> (lockedPlayers ?: evaluator(board, e).players(affects.players))
                    .forEach { playerMechanics.getOrPut(it) { mutableListOf() } += applied }
                affects is Affected.Objects -> placedOn(board, e, mechanic, affects.objects)
                    .filter { it in board.work && state.obj(it)?.phasedOut != true }
                    .forEach { objectMechanics.getOrPut(it) { mutableListOf() } += applied }
            }
        }

        staticEffects(board).forEach { e ->
            val mechanics = e.mechanics.filter { Layers.layerOf(it, e.cda) == null }
            if (mechanics.isEmpty()) return@forEach
            val affects = e.affects
            if (affects is Affected.Players && e.condition != null) {
                // A condition about the affected player ("each player who has cast a nonartifact spell this turn can't
                // cast …"): checked for each player it would apply to
                val players = evaluator(board, e).players(affects.players).filter { p ->
                    Evaluator(board, Context(you = e.you, self = e.self, names = e.names, affectedPlayer = p)).holds(e.condition)
                }
                mechanics.forEach { place(e, it, players) }
            } else if (conditionHolds(board, e)) mechanics.forEach { place(e, it, null) }
        }
        effectObjects(board).forEach { e ->
            val effect = state.effects.getValue(e.effect!!)
            effect.mechanics.filter { it.layer == null }.forEach { place(e, it, effect.lockedPlayers) }
        }
        // blb gaps 2: one or more finality counters on a permanent create one replacement effect, "If this permanent would
        // be put into a graveyard from the battlefield, exile it instead" (CR 122.1h); it doesn't come from an ability, so
        // losing all abilities doesn't stop it
        state.battlefield.forEach { id ->
            val o = state.obj(id) ?: return@forEach
            if (o.phasedOut || o.counters(CounterKind.Finality) == 0) return@forEach
            rules += AppliedMechanic(Layers.FINALITY, board.work[id]?.controller ?: o.controller ?: o.owner, o.timestamp, source = id)
        }
        // spm: a suspected creature can't block (CR 701.60c)
        state.battlefield.forEach { id ->
            val o = state.obj(id) ?: return@forEach
            if (o.phasedOut || arcanum.api.ObjectMark.Suspected !in o.marks) return@forEach
            objectMechanics.getOrPut(id) { mutableListOf() } += AppliedMechanic(Mechanic.CantBlock(arcanum.api.ObjectFilter.Any),
                board.work[id]?.controller ?: o.controller ?: o.owner, o.timestamp, source = id)
        }
        // fra: a prepared permanent's controller may cast the copy of its prepare spell (CR 722.3c)
        arcanum.engine.prepare.Prepare.permissions(state) { board.work[it]?.controller }.forEach { (id, applied) ->
            if (id in board.work) objectMechanics.getOrPut(id) { mutableListOf() } += applied
        }
        // CR 702.143a: a foretold card's owner may cast it for its foretell cost after the turn it was foretold
        arcanum.engine.foretell.Foretell.permissions(state).forEach { (id, applied) ->
            if (id in board.work) objectMechanics.getOrPut(id) { mutableListOf() } += applied
        }
        // CR 702.170d: a plotted card's owner may cast it without paying its mana cost on a later turn
        arcanum.engine.foretell.Plot.permissions(state).forEach { (id, applied) ->
            if (id in board.work) objectMechanics.getOrPut(id) { mutableListOf() } += applied
        }

        val evaluator = Evaluator(board, Context(you = state.turn.active))
        val objects = LinkedHashMap<ObjectId, ObjectView>()
        state.objects.values.forEach { o ->
            val controller = board.work[o.id]?.controller
            objects[o.id] = ObjectView(
                id = o.id, owner = o.owner, controller = controller, zone = o.zone,
                characteristics = board.work[o.id]?.chars, mechanics = objectMechanics[o.id].orEmpty(),
                summoningSick = evaluator.summoningSick(o, controller), obj = o, base = initial[o.id],
            )
        }
        state.stackItems.values.filter { it.kind is StackItemKind.SpellCopy }.forEach { item ->
            objects[item.id] = ObjectView(
                id = item.id, owner = item.controller, controller = board.work[item.id]?.controller ?: item.controller,
                zone = Zone.Stack, characteristics = board.work[item.id]?.chars, mechanics = objectMechanics[item.id].orEmpty(),
                summoningSick = false, obj = null, base = initial[item.id],
            )
        }
        val players = state.players.associate { it.id to PlayerView(it.id, playerMechanics[it.id].orEmpty()) }
        return View(objects, players, rules)
    }

    /** The objects a rule applies to; a permission "any player may …" ([arcanum.api.Players.Holder]) to the cards each
     * player could use it for. */
    private fun placedOn(board: WorkBoard, e: ContinuousEffect, mechanic: Mechanic, objects: arcanum.api.Objects): List<ObjectId> {
        val eval = evaluator(board, e)
        // dsk gaps: an alternative cost for spells cast from a zone is on the cards there, as the spells they would be
        // (CR 601.2a), and on the spell being cast
        if (mechanic is Mechanic.AlternativeCost) return eval.with(eval.ctx.copy(prospectiveCast = true)).objects(objects)
            .filter { state.obj(it)?.zone != Zone.Battlefield }
        if (mechanic !is Mechanic.MayPlay) return eval.objects(objects)
        return state.players.flatMap { p -> eval.with(eval.ctx.copy(holder = p.id)).objects(objects) }.distinct()
    }

    /** spm: abilities given are all keywords that are alternative costs (web-slinging): the spells get them as they are cast. */
    private fun grantsAlternativeCost(mechanic: Mechanic): Boolean = mechanic is Mechanic.GainAbilities && mechanic.abilities.isNotEmpty() &&
        mechanic.abilities.all { a -> a is Keyword && arcanum.api.KeywordExpansions.expand(a).orEmpty().any { e -> e is Ability.Static && e.mechanics.any { it is Mechanic.AlternativeCost } } }

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

package arcanum.engine.resolution

import arcanum.api.CardType
import arcanum.api.Choice
import arcanum.api.Color
import arcanum.api.Each
import arcanum.api.Modification
import arcanum.api.Step
import arcanum.engine.action.Act
import arcanum.engine.action.Actions
import arcanum.engine.action.Cause
import arcanum.engine.action.PlannedMove
import arcanum.engine.action.Replacement
import arcanum.engine.change.Change
import arcanum.engine.query.Context
import arcanum.engine.query.Evaluator
import arcanum.engine.state.Bindings
import arcanum.engine.state.NamedValue
import arcanum.engine.state.ObjectId
import arcanum.engine.state.PayOutcome
import arcanum.engine.state.PlayerId
import arcanum.engine.state.TriggerEvent
import arcanum.engine.view.Subtypes

/** Zone changes collected to happen as one event (CR 608.2c, 608.2e): [moves], binding [postBind] in [scope]. */
internal class Batched(val scope: Scope, val moves: List<PlannedMove>, val postBind: String?, val noRegeneration: Boolean = false)

/**
 * Where a resolution (or a replacement's instructions, or "as enters" steps) is: [cause] says who "you" and "this"
 * are, [names] what was bound so far (targets, announced values, earlier binds), [event] the triggering or replaced
 * event, [actor] who performs the current steps (the player of a "may" or a loop over players). [batch] collects zone
 * changes that happen as one event. [entering] is the object an "as enters" step changes (CR 614.12).
 */
internal class Scope(
    val act: Act,
    val cause: Cause,
    var names: Bindings,
    val event: TriggerEvent?,
    var actor: PlayerId = cause.controller,
    var batch: MutableList<Batched>? = null,
    var entering: PlannedMove? = null,
) {
    val you: PlayerId get() = cause.controller
    val self: ObjectId? get() = cause.source

    fun eval(dividedShare: Int? = null): Evaluator = act.evaluator(Context(
        you = cause.controller, self = entering?.id ?: cause.source, names = names, event = event, stackItem = cause.item,
        dividedShare = dividedShare,
    ))

    /** The objects [o] means now; "[n] of them at random" is a random pick the host answers (CR 701.20c, D25). */
    fun objects(o: arcanum.api.Objects): List<ObjectId> {
        if (o !is arcanum.api.Objects.Random) return eval().objects(o)
        val among = objects(o.of)
        val n = value(o.count).coerceAtLeast(0)
        return if (among.size <= n) among else act.randomObjects(actor, self, among, n)
    }
    fun players(p: arcanum.api.Players): List<PlayerId> = eval().players(p)
    fun value(v: arcanum.api.Value): Int = eval().value(v)
    fun holds(c: arcanum.api.Condition): Boolean = eval().holds(c)

    fun bind(name: String?, value: NamedValue) {
        if (name != null) names = names.with(name, value)
    }
}

/**
 * Runs resolution steps (CR 608.2c) in order within one committed step (D31): [Step.Do] performs an effect
 * ([Effects]); the others add choices, conditions and repetition. A decision with no answer yet stops the whole
 * committed step ([arcanum.engine.action.NeedDecision]); it is redone from its start with the answers so far (D29).
 */
internal object Steps {
    fun run(scope: Scope, steps: List<Step>) = steps.forEach { run(scope, it) }

    fun run(scope: Scope, step: Step) {
        // What the step's changes are made by, for history (D33): this scope's source and Stack item
        val recorder = scope.act.recorder
        val outer = recorder.by
        recorder.by = causedBy(scope, outer)
        try {
            runStep(scope, step)
        } finally {
            recorder.by = outer
        }
    }

    private fun causedBy(scope: Scope, outer: arcanum.engine.change.CausedBy?): arcanum.engine.change.CausedBy {
        val cause = scope.cause
        val source = scope.entering?.id ?: cause.source
        if (outer != null && outer.source == source && outer.item == cause.item?.id && outer.controller == cause.controller) return outer
        val name = source?.let { scope.act.view()[it]?.characteristics?.name }
            ?: cause.item?.snapshots?.firstOrNull { it.id == source }?.characteristics?.name
        return arcanum.engine.change.CausedBy(source, name, cause.controller, cause.item?.id)
    }

    private fun runStep(scope: Scope, step: Step) {
        val act = scope.act
        when (step) {
            is Step.Do -> Effects.perform(scope, step.effect)
            // CR 608.2c: the steps' zone changes happen at once
            is Step.Simultaneously -> together(scope) { run(scope, step.steps) }
            is Step.Bind -> scope.bind(step.bind, NamedValue.Objects(scope.objects(step.objects)))
            is Step.Pay -> pay(scope, step)
            is Step.Modify -> modify(scope, step.modification)
            is Step.May -> {
                val player = scope.players(step.player).firstOrNull() ?: return
                if (act.yesNo(player, scope.self, mayText(scope, step))) withActor(scope, player) { run(scope, step.steps) }
            }
            is Step.If -> run(scope, if (scope.holds(step.condition)) step.then else step.otherwise)
            is Step.ForEach -> forEach(scope, step)
            is Step.Repeat -> {
                // Objects named in each repetition gather ("manifest dread X times, then … each of those creatures",
                // CR 701.40e: one at a time)
                val gathered = linkedMapOf<String, List<ObjectId>>()
                repeat(scope.value(step.times).coerceAtLeast(0)) {
                    val before = scope.names.values
                    run(scope, step.steps)
                    scope.names.values.forEach { (name, value) ->
                        if (value is NamedValue.Objects && before[name] != value) gathered[name] = (gathered[name].orEmpty() + value.ids).distinct()
                    }
                }
                gathered.forEach { (name, ids) -> scope.bind(name, NamedValue.Objects(ids)) }
            }
            // dsk gaps: "… and repeat this process" (CR 608.2c); a process that would never end stops at a bound
            is Step.RepeatWhile -> {
                var times = 0
                do {
                    run(scope, step.steps)
                    times++
                } while (times < REPEAT_LIMIT && scope.holds(step.condition))
            }
            is Step.Choose -> choose(scope, step)
            is Step.Manual -> act.manual(scope.actor, scope.self, step.text)
        }
    }

    /** The most times a [Step.RepeatWhile] repeats in one resolution. */
    private const val REPEAT_LIMIT = 1000

    /** The prompt of an optional step (API review 3): its sentence of the resolving ability's printed text. */
    private fun mayText(scope: Scope, step: Step): String = arcanum.engine.decision.Texts.may(scope.cause.item?.ability, step)

    private fun withActor(scope: Scope, player: PlayerId, block: () -> Unit) {
        val saved = scope.actor
        scope.actor = player
        try { block() } finally { scope.actor = saved }
    }

    /** Runs [block] with its zone changes collected, then makes them one event (CR 608.2c, 608.2e). */
    fun together(scope: Scope, block: () -> Unit) {
        if (scope.batch != null) return block()
        val batch = mutableListOf<Batched>()
        scope.batch = batch
        try { block() } finally { scope.batch = null }
        flush(scope.act, scope.cause, batch)
    }

    fun flush(act: Act, cause: Cause, batch: List<Batched>) {
        if (batch.isEmpty()) return
        val moved = Actions.move(act, cause, batch.flatMap { it.moves }, noRegeneration = batch.any { it.noRegeneration })
        batch.forEach { b ->
            b.scope.bind(b.postBind, NamedValue.Objects(b.moves.mapNotNull { moved[it.id] }))
        }
    }

    /**
     * "For each …" (CR 608.2c, 608.2e): once per object or player in turn order. [Step.ForEach.together]: step by step,
     * every player makes that step's choices in turn order, then the step happens for all at once (CR 101.4). Names bound
     * inside are one per value; afterwards each is the group of all of them.
     */
    private fun forEach(scope: Scope, step: Step.ForEach) {
        val values: List<NamedValue> = when (val each = step.each) {
            is Each.Objects -> scope.objects(each.objects).map { NamedValue.Objects(listOf(it)) }
            is Each.Players -> {
                val players = scope.players(each.players)
                val start = step.startingWith?.let { scope.players(it).firstOrNull() } ?: scope.act.state.turn.active
                scope.act.state.playersFrom(start).filter { it in players }.map { NamedValue.Players(listOf(it)) }
            }
        }
        val outer = scope.names
        val scopes = values.map { v ->
            Scope(scope.act, scope.cause, outer.with(step.bind, v), scope.event, actorOf(scope, v), scope.batch, scope.entering)
        }
        if (step.together) {
            step.steps.forEach { s -> stepTogether(scope, scopes, s) }
        } else {
            scopes.forEach { run(it, step.steps) }
        }
        // Names bound inside the loop become the group of all of them (CR 608.2c)
        var merged = outer
        val inner = scopes.flatMap { sc -> sc.names.values.filter { (k, v) -> k != step.bind && outer[k] != v }.keys }.distinct()
        inner.forEach { name ->
            group(scopes.mapNotNull { sc -> sc.names[name]?.takeIf { it != outer[name] } })?.let { merged = merged.with(name, it) }
        }
        scope.names = merged
    }

    /** One step of a "together" loop for every player's [scopes]. "Each player may search …, put them onto the
     * battlefield, then shuffle": each decides in turn order (CR 101.4), then those who do take the inner steps one by
     * one together, so the cards are put onto the battlefield before anyone shuffles. */
    private fun stepTogether(scope: Scope, scopes: List<Scope>, s: Step) {
        if (s is Step.May) {
            val doing = scopes.filter { sc ->
                val player = sc.players(s.player).firstOrNull() ?: return@filter false
                sc.act.yesNo(player, sc.self, mayText(sc, s))
            }
            s.steps.forEach { inner -> stepTogether(scope, doing, inner) }
            return
        }
        together(scope) { scopes.forEach { it.batch = scope.batch; run(it, s); it.batch = null } }
    }

    private fun actorOf(scope: Scope, value: NamedValue): PlayerId =
        (value as? NamedValue.Players)?.ids?.firstOrNull() ?: scope.actor

    private fun group(parts: List<NamedValue>): NamedValue? {
        if (parts.isEmpty()) return null
        return when (val first = parts.first()) {
            is NamedValue.Objects -> NamedValue.Objects(parts.filterIsInstance<NamedValue.Objects>().flatMap { it.ids })
            is NamedValue.Players -> NamedValue.Players(parts.filterIsInstance<NamedValue.Players>().flatMap { it.ids }.distinct())
            is NamedValue.Targets -> NamedValue.Targets(parts.filterIsInstance<NamedValue.Targets>().flatMap { it.refs })
            is NamedValue.Number -> NamedValue.Number(parts.filterIsInstance<NamedValue.Number>().sumOf { it.value })
            // "If you do" after a loop: whether anyone did
            is NamedValue.Payment -> parts.firstOrNull { (it as? NamedValue.Payment)?.outcome == PayOutcome.Paid } ?: first
            else -> parts.last()
        }
    }

    /** A choice made during resolution (CR 608.2d): bound as [Step.Choose.bind], kept as card memory when [Step.Choose.remember]. */
    private fun choose(scope: Scope, step: Step.Choose) {
        val act = scope.act
        val chooser = scope.players(step.chooser).firstOrNull() ?: return
        val e = scope.eval()
        val source = scope.self
        val value: NamedValue = when (val choice = step.choice) {
            is Choice.Color -> {
                val among = choice.among?.flatMap { e.colors(it) }?.distinct() ?: Color.all
                if (among.isEmpty()) return
                NamedValue.Colors(listOf(act.chooseColor(chooser, source, among)))
            }
            Choice.CreatureType -> NamedValue.SubtypeValue(act.chooseSubtype(chooser, source, null))
            Choice.BasicLandType -> NamedValue.SubtypeValue(act.chooseSubtype(chooser, source, Subtypes.basicLandTypes.keys.toList()))
            is Choice.CardType -> NamedValue.CardTypeValue(act.chooseCardType(chooser, source, choice.among ?: ALL_TYPES))
            is Choice.CardName -> NamedValue.Name(act.chooseCardName(chooser, source, choice.filter))
            is Choice.Number -> NamedValue.Number(act.chooseNumber(chooser, source, e.value(choice.min), choice.max?.let { e.value(it) }))
            is Choice.Player -> {
                val options = act.state.apnap().filter { e.matches(choice.filter, it) }
                NamedValue.Players(listOfNotNull(act.choosePlayer(chooser, source, options)))
            }
            is Choice.Objects -> {
                val from = choice.from?.let { e.objects(it) }
                if (from != null) act.fill(chooser, source, from, "choose from among them")
                // I-D18: whether cards only the chooser sees decide what is offered (filled in first when unknown)
                val pool = (from ?: e.inZonesOf(choice.filter)).filter { act.state.obj(it) != null }
                val hidden = act.hiddenDecides(chooser, source, pool) { e.identityDecides(choice.filter, it) }
                val e2 = scope.eval()
                val options = pool.filter { act.state.obj(it) != null && e2.matches(choice.filter, it) }
                val chosen = act.chooseObjects(chooser, source, options, e2.value(choice.min), choice.max?.let { e2.value(it) },
                    arcanum.engine.decision.Texts.sentence(scope.cause.item?.ability, "choose", "Choose"), hidden)
                // "with different powers": no two chosen share the quality (D32: a rule-breaking answer is refused)
                choice.distinct?.let { quality ->
                    require(chosen.indices.none { i -> (0 until i).any { j -> e2.shares(quality, chosen[i], chosen[j]) } }) {
                        "The chosen objects must differ in $quality"
                    }
                }
                NamedValue.Objects(chosen)
            }
            is Choice.Option -> NamedValue.Option(act.chooseOption(chooser, source, choice.options))
        }
        scope.bind(step.bind, value)
        if (step.remember) {
            val entering = scope.entering
            if (entering != null) scope.entering = entering.copy(memory = entering.memory.with(step.bind, value))
            else source?.let { id -> act.state.obj(id)?.let { o -> act.record(Change.Memory(id, step.bind, o.memory[step.bind], value)) } }
        }
    }

    private val ALL_TYPES = listOf(CardType.Artifact, CardType.Battle, CardType.Creature, CardType.Enchantment, CardType.Instant,
        CardType.Kindred, CardType.Land, CardType.Planeswalker, CardType.Sorcery)

    /**
     * A cost paid on resolution (CR 118.12, D40): the payer chooses to pay it (unless [Step.Pay.mandatory]) if they can
     * pay all of it (CR 601.2h); the outcome is bound as paid, declined or couldn't pay.
     */
    private fun pay(scope: Scope, step: Step.Pay) {
        val act = scope.act
        val payer = scope.players(step.payer).firstOrNull() ?: scope.actor
        val cost = step.cost
        val outcome = if (cost != null) {
            val text = arcanum.engine.decision.Texts.sentence(scope.cause.item?.ability, "pay", "Pay ${Costs.describe(cost)}?")
            // I-D18: whether cards only the payer sees decide if it can be paid (filled in first when unknown)
            val hidden = Costs.hiddenDecides(scope, payer, cost)
            when {
                !Costs.canPay(scope, payer, cost) -> PayOutcome.CouldNotPay.also { act.cannotPay(payer, scope.self, text, cost, hidden) }
                !step.mandatory && !act.yesNo(payer, scope.self, text, cost) -> PayOutcome.Declined
                else -> { withActor(scope, payer) { Costs.pay(scope, payer, cost) }; PayOutcome.Paid }
            }
        } else {
            if (!step.mandatory && !act.yesNo(payer, scope.self, mayText(scope, step))) PayOutcome.Declined
            else { withActor(scope, payer) { run(scope, step.steps) }; PayOutcome.Paid }
        }
        scope.bind(step.bind, NamedValue.Payment(outcome))
    }

    /** "As ~ enters" (CR 614.12): changes how [Scope.entering] enters. */
    private fun modify(scope: Scope, modification: Modification) {
        val m = scope.entering ?: return
        val e = scope.eval()
        scope.entering = when (modification) {
            Modification.EntersTapped -> m.copy(tapped = true)
            is Modification.EntersWithCounters -> m.copy(counters = m.counters + (modification.kind to e.value(modification.amount)))
            is Modification.EntersUnderControlOf -> m.copy(controller = e.players(modification.players).firstOrNull() ?: m.controller)
            is Modification.EntersWithAbilities -> m.copy(abilities = m.abilities + modification.abilities)
            is Modification.EntersAsCopy -> {
                val options = e.each(modification.of).filter { it != m.id }
                scope.act.chooseObjects(scope.actor, m.id, options, if (modification.optional) 0 else 1, 1, "Enter as a copy of")
                    .firstOrNull()?.let { m.copy(copyOf = it to modification.except) } ?: m
            }
            is Modification.ChangeDestination -> m.copy(to = modification.to.zone)
            Modification.Skip -> null
            else -> m
        }
    }

    /** Runs "as enters" [steps] of replacement [r] for [move] (CR 614.12); null when it no longer enters. */
    fun asEnters(act: Act, r: Replacement, move: PlannedMove, steps: List<Step>): PlannedMove? {
        val cause = Cause(r.controller, move.id, key = "enters:${move.id}")
        val scope = Scope(act, cause, r.names, null, entering = move)
        run(scope, steps)
        return scope.entering
    }
}

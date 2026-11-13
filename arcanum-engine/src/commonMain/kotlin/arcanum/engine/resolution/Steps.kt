package arcanum.engine.resolution

import arcanum.api.CardType
import arcanum.api.Choice
import arcanum.api.Color
import arcanum.api.Each
import arcanum.api.Effect
import arcanum.api.Modification
import arcanum.api.Step
import arcanum.engine.CardSupport
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
            is Step.Do -> {
                Effects.perform(scope, step.effect)
                // hob: names the spell's own replacement bound as this effect's event was replaced (CR 614.15)
                if (act.ownInsteadBinds.isNotEmpty()) {
                    act.ownInsteadBinds.forEach { (name, value) -> scope.bind(name, value) }
                    act.ownInsteadBinds.clear()
                }
            }
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
            // Text a definition couldn't express: not carried out. CardSupport.manualParts names it, so the card isn't supported
            is Step.Manual -> Unit
        }
    }

    /** The memory key prefix of the names a [Choice.CardNameAmong] chose before (mh2). */
    private const val CHOSEN_NAMES = "\$chosen-names:"

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
            is Each.Objects -> scope.objects(each.objects).let { ids ->
                // inr chunk 09: "For each creature, its controller sacrifices it unless …" together: the controllers choose in turn order (CR 101.4)
                if (!step.together) ids else {
                    val board = scope.act.board()
                    val order = scope.act.state.playersFrom(scope.act.state.turn.active)
                    ids.sortedBy { id -> board.controller(id)?.let { order.indexOf(it) } ?: order.size }
                }
            }.map { NamedValue.Objects(listOf(it)) }
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
        val damage = ((step.steps.singleOrNull() as? Step.Do)?.effect as? Effect.DealDamage)?.takeIf { it.bind == null }
        if (damage != null) {
            // rvr fixes, CR 608.2f: "each of them deals damage …" (or damage to each player, an amount each) is one action on several
            // objects or players, processed simultaneously: one damage event
            Effects.dealDamageTogether(scopes, damage)
        } else if (step.together) {
            step.steps.forEach { s -> stepTogether(scope, scopes, s) }
        } else if (step.each is Each.Objects && scopes.size > 1 && step.steps.any(::casts)) {
            // rvr fixes, CR 608.2f: casting each of several objects ("for each card exiled this way, copy it, and you may cast the copy")
            // can't be done simultaneously: the resolving spell's controller chooses the order, one object at a time
            val left = values.indices.toMutableList()
            while (left.isNotEmpty()) {
                val ids = left.map { (values[it] as NamedValue.Objects).ids.single() }
                val next = if (left.size == 1) left.single()
                    else left[ids.indexOf(scope.act.chooseObjects(scope.you, scope.self, ids, 1, 1, "Choose the next card (they're cast in the order you choose)").single())]
                left -= next
                run(scopes[next], step.steps)
            }
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
        // ecl: "each opponent exiles cards … until …": a process each player repeats on their own, its moves done one by one (CR 608.2c)
        if (s is Step.RepeatWhile) { scopes.forEach { run(it, listOf(s)) }; return }
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

    /** Whether [step] casts something (a spell cast while another resolves, CR 608.2g). */
    private fun casts(step: Step): Boolean = when (step) {
        is Step.Do -> step.effect is Effect.Cast
        is Step.May -> step.steps.any(::casts)
        else -> false
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
            // mh2: "a type chosen this way", each player having chosen one
            is NamedValue.SubtypeValue -> parts.flatMap { (it as? NamedValue.SubtypeValue)?.let { v -> listOf(v.subtype) } ?: (it as? NamedValue.Subtypes)?.subtypes.orEmpty() }
                .distinct().let { if (it.size == 1) first else NamedValue.Subtypes(it) }
            // "If you do" after a loop: whether anyone did
            is NamedValue.Payment -> parts.firstOrNull { (it as? NamedValue.Payment)?.outcome == PayOutcome.Paid } ?: first
            else -> parts.last()
        }
    }

    /** A choice made during resolution (CR 608.2d): bound as [Step.Choose.bind], kept as card memory when [Step.Choose.remember]. */
    private fun choose(scope: Scope, step: Step.Choose) {
        val act = scope.act
        // inr sweep: "an opponent chooses" with several opponents: the controller picks which one chooses (CR 102.3), not the first
        val chooser = scope.players(step.chooser).let { all -> act.choosePlayer(scope.you, scope.self, all) } ?: return
        val e = scope.eval()
        val source = scope.self
        val value: NamedValue = when (val choice = step.choice) {
            is Choice.Color -> {
                val among = choice.among?.flatMap { e.colors(it) }?.distinct() ?: Color.all
                if (among.isEmpty()) return
                NamedValue.Colors(listOf(act.chooseColor(chooser, source, among)))
            }
            // rtr chunk 09: "choose two colors": different colours, one at a time
            is Choice.Colors -> {
                val chosen = mutableListOf<Color>()
                repeat(choice.count) { chosen += act.chooseColor(chooser, source, Color.all.filter { it !in chosen }) }
                NamedValue.Colors(chosen)
            }
            Choice.CreatureType -> NamedValue.SubtypeValue(act.chooseSubtype(chooser, source, null))
            // ltr chunk 10: "a creature type that hasn't been noted for this Saga": the types noted before are kept by the source
            Choice.CreatureTypeUnchosen -> {
                val key = CHOSEN_NAMES + step.bind
                val before = (source?.let { act.state.obj(it) }?.linked?.get(key) as? NamedValue.Subtypes)?.subtypes.orEmpty()
                val chosen = act.chooseSubtype(chooser, source, null)
                require(chosen !in before) { "$chosen was noted before" }
                source?.let { act.state.obj(it) }?.let { o -> act.record(Change.Memory(o.id, key, o.linked[key], NamedValue.Subtypes(before + chosen))) }
                NamedValue.SubtypeValue(chosen)
            }
            // ecl: one of the listed creature types
            is Choice.CreatureTypeAmong -> NamedValue.SubtypeValue(act.chooseSubtype(chooser, source, choice.among))
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
                if (from != null) act.show(chooser, from)
                // I-D18: whether cards only the chooser sees decide what is offered (filled in first when unknown)
                val pool = (from ?: e.inZonesOf(choice.filter)).filter { act.state.obj(it) != null }
                val hidden = act.hiddenDecides(chooser, pool) { e.identityDecides(choice.filter, it) }
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
                // blb gaps: "for each card type, … a card of that type": each chosen object stands for a value of its own
                choice.onePer?.let { quality ->
                    require(e2.eachStandsForOne(quality, chosen)) { "Each chosen object must stand for a different $quality" }
                }
                // fra: "with total mana value 6 or less"
                choice.totalAtMost?.let { limit ->
                    require(chosen.sumOf { e2.property(limit.property, it) } <= e2.value(limit.atMost)) { "The chosen objects' total is more than allowed" }
                }
                NamedValue.Objects(chosen)
            }
            is Choice.Option -> NamedValue.Option(act.chooseOption(chooser, source, choice.options))
            // mh2: "Choose a card name that hasn't been chosen from among …": the names chosen before are kept by the source
            is Choice.CardNameAmong -> {
                val key = CHOSEN_NAMES + step.bind
                val before = (source?.let { act.state.obj(it) }?.linked?.get(key) as? NamedValue.Names)?.names.orEmpty()
                val options = if (choice.unchosen) choice.names.filter { it !in before } else choice.names
                if (options.isEmpty()) return
                val name = act.chooseOption(chooser, source, options)
                require(name in options) { "Not one of $options" }
                if (choice.unchosen) source?.let { act.state.obj(it) }?.let { o ->
                    act.record(Change.Memory(o.id, key, o.linked[key], NamedValue.Names(before + name)))
                }
                NamedValue.Name(name)
            }
        }
        scope.bind(step.bind, value)
        if (step.remember) {
            // mkm fix: a secret choice is marked known only to its chooser until revealed (Secrets)
            val marker = if (step.secret) NamedValue.Players(listOf(chooser)) else null
            val entering = scope.entering
            if (entering != null) scope.entering = entering.copy(memory = entering.memory.with(step.bind, value)
                .let { m -> if (marker != null) m.with(arcanum.engine.state.Secrets.markerKey(step.bind), marker) else m })
            else source?.let { id -> act.state.obj(id)?.let { o ->
                act.record(Change.Memory(id, step.bind, o.linked[step.bind], value))
                val key = arcanum.engine.state.Secrets.markerKey(step.bind)
                if (marker != null) act.record(Change.Memory(id, key, o.linked[key], marker))
            } }
        }
    }

    private val ALL_TYPES = listOf<CardType>(CardType.Artifact, CardType.Battle, CardType.Creature, CardType.Enchantment, CardType.Instant,
        CardType.Kindred, CardType.Land, CardType.Planeswalker, CardType.Sorcery)

    /**
     * A cost paid on resolution (CR 118.12, D40): the payer chooses to pay it (unless [Step.Pay.mandatory]) if they can
     * pay all of it (CR 601.2h); the outcome is bound as paid, declined or couldn't pay.
     */
    private fun pay(scope: Scope, step: Step.Pay) {
        // "any opponent may …": each in turn order until one pays (CR 101.4)
        val payers = scope.players(step.payer).ifEmpty { listOf(scope.actor) }
        var outcome: PayOutcome = PayOutcome.CouldNotPay
        for (payer in payers) {
            when (payOne(scope, step, payer)) {
                PayOutcome.Paid -> { outcome = PayOutcome.Paid; break }
                PayOutcome.Declined -> outcome = PayOutcome.Declined
                PayOutcome.CouldNotPay -> {}
            }
        }
        scope.bind(step.bind, NamedValue.Payment(outcome))
    }

    private fun payOne(scope: Scope, step: Step.Pay, payer: PlayerId): PayOutcome {
        val act = scope.act
        val cost = step.cost
        step.payerBind?.let { scope.bind(it, NamedValue.Players(listOf(payer))) }
        return if (cost != null) {
            val text = arcanum.engine.decision.Texts.pay(scope.cause.item?.ability, "Pay ${Costs.describe(cost)}?")
            // I-D18: whether cards only the payer sees decide if it can be paid (filled in first when unknown)
            val hidden = Costs.hiddenDecides(scope, payer, cost)
            when {
                !Costs.canPay(scope, payer, cost) -> PayOutcome.CouldNotPay.also { act.cannotPay(payer, scope.self, text, cost, hidden) }
                !step.mandatory && !act.yesNo(payer, scope.self, text, cost) -> PayOutcome.Declined
                else -> { withActor(scope, payer) { Costs.pay(scope, payer, cost) }; PayOutcome.Paid }
            }
        } else {
            if (!step.mandatory && !act.yesNo(payer, scope.self, mayText(scope, step))) PayOutcome.Declined
            else {
                val mark = act.recorder.changes.size
                val chosenBefore = step.steps.filterIsInstance<Step.Choose>().associate { it.bind to scope.names[it.bind] }
                withActor(scope, payer) { run(scope, step.steps) }
                // CR 608.2c: "if you do" asks whether the action was performed, not whether it was chosen: steps that made no
                // event (what they move or sacrifice is gone, an empty library to draw from) and chose nothing weren't done
                if (didSomething(act, mark) || choseSomething(scope, chosenBefore)) PayOutcome.Paid else PayOutcome.CouldNotPay
            }
        }
    }

    /** Whether the changes recorded since [mark] include an event (D33: an object moved, created, tapped or given
     * counters, damage, life, an effect, a reveal, a search, …), not only bookkeeping: an action was performed (CR 608.2c). */
    private fun didSomething(act: Act, mark: Int): Boolean =
        act.recorder.changes.subList(mark.coerceAtMost(act.recorder.changes.size), act.recorder.changes.size).any { it.isEvent }

    /** Whether a choice whose names had the values [before] chose something ("You may choose a nonland card from it. If you do, …": the
     * choosing is the action, CR 608.2c). */
    private fun choseSomething(scope: Scope, before: Map<String, NamedValue?>): Boolean = before.any { (bind, was) ->
        when (val v = scope.names[bind]) {
            null, was -> false
            is NamedValue.Objects -> v.ids.isNotEmpty()
            is NamedValue.Players -> v.ids.isNotEmpty()
            is NamedValue.Targets -> v.refs.isNotEmpty()
            else -> true
        }
    }

    /** "As ~ enters" (CR 614.12): changes how [Scope.entering] enters. */
    private fun modify(scope: Scope, modification: Modification) {
        val m = scope.entering ?: return
        val e = scope.eval()
        scope.entering = when (modification) {
            Modification.EntersTapped -> m.copy(tapped = true)
            is Modification.EntersWithCounters -> if (!arcanum.engine.action.Actions.entersAs(scope.act, m, modification.ifEntersAs, scope.actor)) m
                else m.copy(counters = m.counters + (modification.kind to e.value(modification.amount)))
            is Modification.EntersUnderControlOf -> m.copy(controller = e.players(modification.players).firstOrNull() ?: m.controller)
            is Modification.EntersWithAbilities -> m.copy(abilities = m.abilities + modification.abilities)
            is Modification.EntersAsCopy -> {
                val options = e.each(modification.of).filter { it != m.id }
                scope.act.chooseObjects(scope.actor, m.id, options, if (modification.optional) 0 else 1, 1, "Enter as a copy of")
                    .firstOrNull()?.let { arcanum.engine.action.Actions.asCopy(scope.act, m, it, modification, e, scope.actor) } ?: m
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

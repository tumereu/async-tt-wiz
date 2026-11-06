package arcanum.engine.trigger

import arcanum.api.Ability
import arcanum.api.CounterKind
import arcanum.api.EventRole
import arcanum.api.Mechanic
import arcanum.api.Trigger
import arcanum.api.Zone
import arcanum.engine.Engine
import arcanum.engine.Pending
import arcanum.engine.Progress
import arcanum.engine.action.Act
import arcanum.engine.action.Asker
import arcanum.engine.action.NeedDecision
import arcanum.engine.change.Change
import arcanum.engine.change.Meaning
import arcanum.engine.change.Recorder
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Continuation
import arcanum.engine.decision.Decision
import arcanum.engine.decision.TriggerOption
import arcanum.engine.decision.WaitingTrigger
import arcanum.engine.query.Context
import arcanum.engine.query.Evaluator
import arcanum.engine.query.ViewBoard
import arcanum.engine.resolution.Casts
import arcanum.engine.state.GameState
import arcanum.engine.state.ObjectId
import arcanum.engine.state.ObjectSnapshot
import arcanum.engine.state.PlayerId
import arcanum.engine.state.RoleObjects
import arcanum.engine.state.StackItem
import arcanum.engine.state.StackItemKind
import arcanum.engine.state.StatusFlag
import arcanum.engine.state.TargetRef
import arcanum.engine.state.TriggerEvent
import arcanum.engine.state.TurnCount
import arcanum.engine.view.AbilityOrigin
import arcanum.engine.view.Functioning
import arcanum.engine.view.View
import arcanum.engine.view.isInstantOrSorcery

/**
 * Triggered abilities (CR 603): which abilities the recorded events trigger (looking back in time for leaves-the-
 * battlefield triggers, CR 603.10), delayed triggers (CR 603.7), state triggers (CR 603.8), and putting them on the
 * Stack in APNAP order with their choices (CR 603.3, [Continuation.PlacingTriggers]). "This turn" questions over the
 * tagged events are [arcanum.engine.query.History].
 *
 * Detection runs as each event is recorded ([Recorder.record]): the abilities that trigger wait in
 * [GameState.triggered] (D42) until the next time a player would get priority.
 */
internal object Triggers {
    /** Events whose triggers are checked on the game as it was just before (CR 603.10a). */
    fun looksBack(change: Change): Boolean = when (change) {
        // Leaving the battlefield, and an object all players can see put into a hand or library
        is Change.ZoneChange -> change.moves.any { looksBack(it) }
        is Change.Removed -> true
        is Change.Attached -> change.before != null && change.after != change.before
        is Change.Status -> change.flag == StatusFlag.PhasedOut
        else -> false
    }

    /** Finds what [change] triggers (CR 603.2) and records it as waiting. [before] is the game just before it, for
     * look-back events. */
    fun detect(recorder: Recorder, whole: Change, before: GameState?, viewBefore: View? = null) {
        if (whole is Change.TriggersWaiting || whole is Change.DelayedTriggerRemoved || whole is Change.DelayedTriggerAdded) return
        val state = recorder.state
        var now = Engine.view(state)
        // hob backfill: storied gives its designation any time its condition holds, and continuous effects apply again
        // before triggers are looked for (CR 702.195a, c): an event that completes the condition already sees it
        if (grantDesignations(recorder, now)) now = Engine.view(state)
        // fra: objects whose entering doesn't cause abilities to trigger (CR 603.2): the event without them
        val change = withoutMuted(state, now, whole) ?: return
        val found = mutableListOf<WaitingTrigger>()
        val seen = mutableSetOf<String>()
        // CR 603.10a: look back for leaving the battlefield and the other look-back events
        if (before != null) {
            val then = viewBefore ?: Engine.view(before)
            sources(before, then).forEach { s ->
                seen += "${s.id}/${s.origin.key}"
                found += check(state, ViewBoard(before, then), now, change, s, lookBack = true)
            }
        }
        // Objects that didn't exist before the event (they entered with it) trigger as they are now, on the parts of a
        // look-back event that don't look back
        sources(state, now).filter { "${it.id}/${it.origin.key}" !in seen && (before == null || before.obj(it.id) == null || before.obj(it.id)?.phasedOut == true) }
            .forEach { s ->
                // ltr chunk 12: a permanent that phased in with the event ("whenever … phases in", CR 702.26c) sees the whole event
                val phasedIn = before?.obj(s.id)?.phasedOut == true
                val part = if (before == null || phasedIn) change else seenByNewObject(change, s) ?: return@forEach
                found += check(state, ViewBoard(state, now), now, part, s, lookBack = false, whole = change)
            }
        found += discardedFromHand(state, now, change, seen)
        found += leftTheStack(state, now, change, seen)
        found += additional(state, now, change, found)
        // CR 726.2: the initiative's inherent triggered abilities
        found += arcanum.engine.dungeon.Dungeons.inherent(state, now, change)
        val delayedFired = mutableListOf<arcanum.engine.state.DelayedTrigger>()
        state.delayedTriggers.values.toList().forEach { dt ->
            if (dt.laterTurn && state.turn.number <= dt.createdTurn) return@forEach
            val e = Evaluator(ViewBoard(state, now), Context(dt.controller, dt.source, dt.names))
            val instances = instancesOf(e, state, dt.trigger, change)
            if (instances.isNotEmpty()) {
                val ability = Ability.Triggered(dt.trigger, dt.instructions)
                (if (dt.repeats) instances else instances.take(1)).forEach { instance ->
                    found += WaitingTrigger(dt.controller, ability, null, dt.source, dt.snapshots.firstOrNull { it.id == dt.source },
                        instance, delayedTrigger = dt.id, names = dt.names, snapshots = dt.snapshots, oneShot = dt.oneShot)
                }
                if (!dt.repeats) delayedFired += dt
            }
        }
        if (found.isNotEmpty()) addWaiting(recorder, found)
        delayedFired.forEach { recorder.record(Change.DelayedTriggerRemoved(it, listOf(Meaning.Triggered))) }
    }

    /**
     * CR 605.1b, 605.4a: a triggered mana ability (it triggers from tapping for mana, adds a fixed amount of mana and
     * has no targets) doesn't wait for the Stack: it resolves right after the mana ability that triggered it.
     */
    fun resolveManaTriggers(recorder: Recorder) {
        val waiting = recorder.state.triggered.toList()
        val mana = waiting.filter { isManaTrigger(it) }
        if (mana.isEmpty()) return
        recorder.record(Change.TriggersWaiting(waiting, waiting.filterNot { it in mana }))
        mana.forEach { t ->
            val act = Act(recorder, Asker.NONE)
            val scope = arcanum.engine.resolution.Scope(act, arcanum.engine.action.Cause(t.controller, t.source, null,
                "mana-trigger@${recorder.state.nextTimestamp()}"), t.names, t.event)
            (t.ability as Ability.Triggered).instructions.steps.forEach { arcanum.engine.resolution.Steps.run(scope, it) }
        }
    }

    private fun isManaTrigger(t: WaitingTrigger): Boolean {
        val ability = t.ability as? Ability.Triggered ?: return false
        val on = ability.trigger as? Trigger.On ?: return false
        val i = ability.instructions
        return i.targets.isEmpty() && i.modes == null && i.steps.isNotEmpty() &&
            on.events.all { it is arcanum.api.EventPattern.Tap && it.forMana == true } &&
            i.steps.all { step -> ((step as? arcanum.api.Step.Do)?.effect as? arcanum.api.Effect.AddMana)?.mana?.kind.let {
                // dgm: "adds one mana of any type that land produced" (CR 605.1b)
                it is arcanum.api.ManaKind.Fixed || it is arcanum.api.ManaKind.SameAsProduced
            } }
    }

    /** Puts [triggers] among those waiting to go on the Stack (CR 603.2, 603.3). */
    fun addWaiting(recorder: Recorder, triggers: List<WaitingTrigger>) {
        if (triggers.isEmpty()) return
        val before = recorder.state.triggered.toList()
        recorder.record(Change.TriggersWaiting(before, before + triggers))
    }

    // fra
    /**
     * A card's own "when you discard this card" works from the hand (CR 113.6k): found on the discarded card as it is now,
     * its abilities that work only in the hand (those that work where it went were found with the other new objects).
     */
    private fun discardedFromHand(state: GameState, now: View, change: Change, seen: Set<String>): List<WaitingTrigger> =
        (change as? Change.ZoneChange)?.moves.orEmpty().filter { it.from == Zone.Hand && Meaning.Discarded in it.meanings }.flatMap { m ->
            val o = now[m.after.id] ?: return@flatMap emptyList()
            val c = o.characteristics ?: return@flatMap emptyList()
            c.abilities.flatMap { inst ->
                val ability = inst.ability as? Ability.Triggered
                if (ability == null || Functioning.zones(ability, c.isInstantOrSorcery()) != setOf(Zone.Hand) ||
                    "${o.id}/${inst.origin.key}" in seen) emptyList()
                // the event names the card as it was in the hand
                else check(state, ViewBoard(state, now), now, change, Source(m.before.id, o.owner, ability, inst.origin), lookBack = false)
            }
        }

    /**
     * A spell's own "when this spell is put into a graveyard during its resolution" (haunt, CR 702.55a) works on the Stack (CR 113.6k):
     * found on the card as it is now, its abilities that trigger on itself leaving the Stack, the event naming the spell it was.
     */
    private fun leftTheStack(state: GameState, now: View, change: Change, seen: Set<String>): List<WaitingTrigger> =
        (change as? Change.ZoneChange)?.moves.orEmpty().filter { it.from == Zone.Stack }.flatMap { m ->
            val o = now[m.after.id] ?: return@flatMap emptyList()
            val c = o.characteristics ?: return@flatMap emptyList()
            c.abilities.flatMap { inst ->
                val ability = inst.ability as? Ability.Triggered
                val fromStack = (ability?.trigger as? Trigger.On)?.events.orEmpty().any {
                    it is arcanum.api.EventPattern.ZoneChange && it.from == Zone.Stack && it.objects == arcanum.api.ObjectFilter.Is(arcanum.api.Objects.Self)
                }
                if (ability == null || !fromStack || Functioning.zones(ability, c.isInstantOrSorcery()) != setOf(Zone.Stack) ||
                    "${o.id}/${inst.origin.key}" in seen) emptyList()
                else check(state, ViewBoard(state, now), now, change, Source(m.before.id, m.before.controller ?: o.owner, ability, inst.origin), lookBack = false)
            }
        }

    /** One triggered ability of an object where it functions (CR 113.6). */
    private data class Source(val id: ObjectId, val controller: PlayerId, val ability: Ability.Triggered, val origin: AbilityOrigin)

    private fun sources(state: GameState, view: View): List<Source> = view.objects.values.flatMap { o ->
        val c = o.characteristics ?: return@flatMap emptyList()
        val obj = o.obj ?: return@flatMap emptyList()
        if (obj.phasedOut) return@flatMap emptyList()
        c.abilities.mapNotNull { instance ->
            val ability = instance.ability as? Ability.Triggered ?: return@mapNotNull null
            // Emblems' abilities work in the command zone (CR 114.4)
            if (o.zone != Zone.Command && o.zone !in Functioning.zones(ability, c.isInstantOrSorcery())) return@mapNotNull null
            // CR 702.21a: a ward ability of an object whose ward "doesn't trigger"
            val expansion = instance.origin as? AbilityOrigin.Expansion
            if (expansion != null && o.mechanics.any { it.mechanic == arcanum.api.Mechanic.WardDoesntTrigger } &&
                c.abilities.any { it.origin == expansion.keyword && it.ability is arcanum.api.Keyword.Ward }) return@mapNotNull null
            Source(o.id, o.controller ?: o.owner, ability, instance.origin)
        }
    }

    /**
     * The part of a look-back [change] (CR 603.10a) that [s], an object that didn't exist before it, can trigger on:
     * its abilities didn't exist immediately before the event, so the parts that look back aren't seen (a card's
     * graveyard ability doesn't see the creatures that died with it, nor its own death); entering the battlefield is
     * seen (CR 603.6a). An ability that triggers on a card put into a zone "from anywhere" isn't a leaves-the-battlefield
     * ability (CR 603.6c): it sees the others' moves from the battlefield, but never the move that made its own object
     * (that was its earlier object's, CR 400.7). Null when nothing is left.
     */
    private fun seenByNewObject(change: Change, s: Source): Change? {
        val moves = (change as? Change.ZoneChange)?.moves ?: return null
        val fromAnywhere = (s.ability.trigger as? Trigger.On)?.events.orEmpty()
            .any { it is arcanum.api.EventPattern.ZoneChange && it.from == null && it.to != Zone.Battlefield }
        val seen = moves.filter { m -> !looksBack(m) || (fromAnywhere && m.after.id != s.id) }
        return if (seen.isEmpty()) null else Change.ZoneChange(seen)
    }

    /** One move that looks back (CR 603.10a): leaving the battlefield, or an object all players can see put into a hand
     * or library. */
    private fun looksBack(m: arcanum.engine.change.Move): Boolean =
        m.from == Zone.Battlefield || ((m.to == Zone.Hand || m.to == Zone.Library) && m.from != Zone.Hand && m.from != Zone.Library)

    private fun check(
        state: GameState, board: ViewBoard, now: View, change: Change, s: Source, lookBack: Boolean, whole: Change = change,
    ): List<WaitingTrigger> {
        val e = Evaluator(board, Context(s.controller, s.id))
        val instances = instancesOf(e, board.state, s.ability.trigger, change, whole, door = (s.origin.root as? AbilityOrigin.Printed)?.face)
        if (instances.isEmpty()) return emptyList()
        val nowBoard = ViewBoard(state, now)
        val sourceSeen = if (lookBack) snapshot(board, s.id) else null
        return instances.filter { instance ->
            // Conditions are checked right after the event (CR 603.4); the source may have left (last known information)
            val check = Evaluator(nowBoard, Context(s.controller, s.id, event = instance))
            (s.ability.triggersOnlyIf?.let { check.holds(it) } ?: true) && (s.ability.interveningIf?.let { check.holds(it) } ?: true)
        }.let { limited(state, s, it) }.map { instance ->
            WaitingTrigger(s.controller, s.ability, s.origin, s.id, sourceSeen ?: snapshot(nowBoard, s.id), instance, names = grantedNames(state, s.origin))
        }
    }

    // gtc
    /** The names of the effect that granted a triggered ability ("the encoded card" of cipher, CR 702.99c): the granted
     * ability refers to what the effect bound as it was created. */
    private fun grantedNames(state: GameState, origin: AbilityOrigin): arcanum.engine.state.Bindings =
        (origin as? AbilityOrigin.FromEffect)?.let { state.effects[it.effect]?.names } ?: arcanum.engine.state.Bindings.EMPTY

    private fun snapshot(board: ViewBoard, id: ObjectId): ObjectSnapshot? {
        val o = board.state.obj(id) ?: return board.snapshot(id)
        val v = board.view[id]
        return ObjectSnapshot(id, o.owner, v?.controller ?: o.controller, o.zone, v?.characteristics, o.counters, o.damage,
            o.tapped, o.token, o.attachedTo, o.facts, o.memory, attacking = board.state.combat?.attackers.orEmpty().any { it.id == id })
    }

    /** "Triggers only once each turn" (CR 603.2): counted on the source object per turn number (D41) once put on the
     * Stack, and the instances that triggered and still wait to be put there. */
    private fun limited(state: GameState, s: Source, instances: List<TriggerEvent>): List<TriggerEvent> {
        // dsk gaps: "Do this only once each turn" (CR 603.2h): only if its controller hasn't taken that action this turn
        if (s.ability.actionOncePerTurn && actionTaken(state, s.id, s.origin, s.controller)) return emptyList()
        // "This ability triggers only once": for as long as the object exists
        if (s.ability.once) {
            val waiting = state.triggered.count { it.source == s.id && it.origin?.key == s.origin.key }
            val placed = state.obj(s.id)?.facts?.triggers?.filter { it.key == s.origin.key }?.sumOf { it.count } ?: 0
            return instances.take((1 - placed - waiting).coerceAtLeast(0))
        }
        val limit = s.ability.perTurn ?: return instances
        val waiting = state.triggered.count { it.source == s.id && it.origin?.key == s.origin.key }
        val placed = state.obj(s.id)?.facts?.triggers?.firstOrNull { it.key == s.origin.key && it.turn == state.turn.number }?.count ?: 0
        return instances.take((limit - placed - waiting).coerceAtLeast(0))
    }

    // dsk gaps
    /** Whether [player] has taken the action of [source]'s "Do this only once each turn" ability [origin] this turn
     * (CR 603.2h): counted on the source object per turn number (D41). */
    fun actionTaken(state: GameState, source: ObjectId?, origin: AbilityOrigin?, player: PlayerId): Boolean {
        val key = actionKey(origin ?: return false, player)
        return state.obj(source ?: return false)?.facts?.activations.orEmpty().any { it.key == key && it.turn == state.turn.number }
    }

    /** The key under which a taken "Do this only once each turn" action is counted. */
    fun actionKey(origin: AbilityOrigin, player: PlayerId): String = "\$action:${origin.key}@$player"

    /** The instances of [trigger] in [change] (CR 603.2): one per event, or one per batch ([Trigger.On.oneOrMore], CR
     * 603.2c by wording), the nth this turn only ([Trigger.On.nthThisTurn], counting the same units: earlier instances,
     * or earlier batches, so "one or more cards for the first time each turn" keeps the whole batch); chapter abilities
     * as lore counters pass their numbers (CR 714.2b). */
    fun instancesOf(e: Evaluator, state: GameState, trigger: Trigger, change: Change, whole: Change = change, door: Int? = null): List<TriggerEvent> = when (trigger) {
        is Trigger.On -> {
            fun units(c: Change): List<TriggerEvent> {
                val all = trigger.events.flatMap { p ->
                    val found = EventMatcher.instances(e, p, c, door = door)
                    // gtc: "whenever a +1/+1 counter is put on …" triggers once per counter put (CR 603.2c)
                    if (p is arcanum.api.EventPattern.CountersPut && !trigger.oneOrMore) found.flatMap { t -> List(t.amount ?: 1) { t.copy(amount = 1) } }
                    else found
                }
                val batches = when {
                    !trigger.oneOrMore -> all
                    all.isEmpty() -> emptyList()
                    trigger.batchPerPlayer -> all.groupBy { it.players.firstOrNull() }.values.map { batch(it) }
                    else -> listOf(batch(all))
                }
                // hob: "with creatures with total power 12 or greater": only a batch whose objects add up to it (CR 603.2c)
                val total = trigger.batchTotal ?: return batches
                return batches.filter { b ->
                    b.objects.filter { it.role == EventRole.Object }.flatMap { it.ids }.distinct().sumOf { e.property(total.property, it) } >= e.value(total.atLeast)
                }
            }
            // "during each of your draw steps" ([Trigger.On.during]): only events in that step count, and only there it triggers
            val inStep = trigger.during == null || state.turn.step == trigger.during
            val now = if (inStep) units(change) else emptyList()
            val counted = if (now.isEmpty()) now else trigger.nthThisTurn?.let { n ->
                // gtc: the events before this one (the last equal one: two combats can make equal changes)
                val events = state.eventsOf(state.turn.number)
                val at = events.indexOfLast { it === whole }.takeIf { it >= 0 } ?: events.indexOfLast { it == whole }
                var before = if (at >= 0) events.take(at) else events
                // within [during]: counted from the start of this step (its latest beginning: extra combats repeat steps)
                trigger.during?.let { step ->
                    val began = before.indexOfLast { it is Change.Step && it.after.step == step && it.before.step != step }
                    before = before.drop(began + 1)
                }
                val earlier = before.flatMap { units(it) }
                // hob: for that player ("an opponent draws their second card each turn": each opponent's own draws, CR 603.2)
                fun same(a: TriggerEvent, b: TriggerEvent) = a.players.firstOrNull() == b.players.firstOrNull()
                // spm: "your first or second card each turn" ([Trigger.On.throughNth]): any of the first n
                fun nth(k: Int) = if (trigger.throughNth) k <= n else k == n
                if (!trigger.oneOrMore) now.filterIndexed { i, unit -> nth(earlier.count { same(it, unit) } + now.take(i).count { same(it, unit) } + 1) }
                else now.filter { unit ->
                    nth(earlier.count { !trigger.batchPerPlayer || it.players.firstOrNull() == unit.players.firstOrNull() } + 1)
                }
            } ?: now
            counted.filter { trigger.atLeast == null || it.objects.firstOrNull { o -> o.role == EventRole.Object }?.ids.orEmpty().size >= trigger.atLeast!! }
        }
        is Trigger.Chapter -> (change as? Change.Counters)?.let { c ->
            val holder = (c.holder as? TargetRef.Object)?.id ?: return@let null
            if (c.kind != CounterKind.Lore || holder != e.ctx.self) return@let null
            val from = c.before?.count ?: 0
            val to = c.after?.count ?: 0
            // CR 702.155a: read ahead: the turn it entered, only the chapter of exactly its number of lore counters
            val o = e.board.state.obj(holder)
            val readAhead = o != null && o.controlSince?.turn == e.board.state.turn.number &&
                e.characteristics(holder)?.hasKeyword(arcanum.api.Keyword.ReadAhead) == true
            trigger.chapters.filter { it in (from + 1)..to && (!readAhead || it == to) }
                .map { TriggerEvent(listOf(RoleObjects(EventRole.Object, listOf(holder))), amount = it) }
        }.orEmpty()
        // State triggers are checked when triggers are put on the Stack (CR 603.8)
        is Trigger.State -> emptyList()
    }

    private fun batch(instances: List<TriggerEvent>): TriggerEvent {
        val roles = instances.flatMap { it.objects }.groupBy { it.role }.map { (role, list) -> RoleObjects(role, list.flatMap { it.ids }.distinct()) }
        // fin: a batch of events without amounts counts its events ("whenever one or more Birds you control attack, look at that many cards")
        return TriggerEvent(roles, instances.flatMap { it.players }.distinct(), instances.mapNotNull { it.amount }.takeIf { it.isNotEmpty() }?.sum()
            ?: instances.size, counterKinds = instances.flatMap { it.counterKinds }.distinct())
    }

    /** fra: [change] without the parts [Mechanic.EventDoesntTrigger] mutes ("Artifacts and creatures entering the battlefield
     * don't cause abilities to trigger"); null when nothing of it is left. */
    private fun withoutMuted(state: GameState, view: View, change: Change): Change? {
        val muting = view.rules.filter { it.mechanic is Mechanic.EventDoesntTrigger }
        if (muting.isEmpty() || change !is Change.ZoneChange) return change
        val kept = change.moves.filter { mv ->
            muting.none { applied ->
                val e = Evaluator(ViewBoard(state, view), Context(applied.controller, applied.source, applied.names))
                EventMatcher.instances(e, (applied.mechanic as Mechanic.EventDoesntTrigger).event, Change.ZoneChange(listOf(mv))).isNotEmpty()
            }
        }
        return when {
            kept.size == change.moves.size -> change
            kept.isEmpty() -> null
            else -> change.copy(moves = kept)
        }
    }

    /** Records the designations [Mechanic.GetsDesignation] gives players who don't have them yet (storied, CR 702.195a);
     * true when one was given. */
    private fun grantDesignations(recorder: Recorder, view: View): Boolean {
        val missing = recorder.state.players.filter { !it.lost }.flatMap { p ->
            view.players[p.id]?.mechanics.orEmpty().mapNotNull { (it.mechanic as? Mechanic.GetsDesignation)?.designation }.distinct()
                .filter { it !in p.designations }.map { p.id to it }
        }
        missing.forEach { (player, designation) -> recorder.record(Change.PlayerDesignation(player, designation, before = false, after = true)) }
        return missing.isNotEmpty()
    }

    /** "Triggers an additional time" (CR 603.2d). */
    private fun additional(state: GameState, view: View, change: Change, found: List<WaitingTrigger>): List<WaitingTrigger> =
        view.rules.flatMap { applied ->
            val m = applied.mechanic as? Mechanic.TriggersAdditionalTime ?: return@flatMap emptyList()
            val e = Evaluator(ViewBoard(state, view), Context(applied.controller, applied.source, applied.names))
            if (m.event != null && EventMatcher.instances(e, m.event!!, change).isEmpty()) return@flatMap emptyList()
            found.filter { t -> t.delayedTrigger == null && t.source != null && e.matches(m.sources, t.source!!) }
        }

    // ---- Putting triggered abilities on the Stack (CR 603.3)

    /** Puts the triggered abilities waiting since the last check on the Stack (CR 603.3b); [Progress.Done] when there
     * were none. */
    fun place(recorder: Recorder): Progress = place(recorder, emptyList())

    fun resume(recorder: Recorder, continuation: Continuation.PlacingTriggers, answer: Answer): Progress =
        place(recorder, continuation.answers + answer)

    private fun place(recorder: Recorder, answers: List<Answer>): Progress {
        stateTriggers(recorder)
        val waiting = recorder.state.triggered.toList()
        if (waiting.isEmpty()) return Progress.Done
        val mark = recorder.changes.size
        val act = Act(recorder, Asker(answers, arcanum.engine.casting.askerPrefix(recorder.state, "triggers")))
        return try {
            // CR 610.3: an "until" return not done yet (its event happened outside the event pipeline) happens first;
            // state-based actions are checked again before triggers go on the Stack (Perform.checks goes round)
            if (waiting.any { it.oneShot }) {
                runOneShots(act)
                return Progress.Done
            }
            recorder.record(Change.TriggersWaiting(waiting, emptyList()))
            // CR 603.3b: APNAP order: the active player's go on the Stack first, so they resolve last
            act.state.apnap().forEach { player ->
                val mine = waiting.filter { it.controller == player }
                order(act, player, mine).forEach { push(act, it) }
            }
            Progress.Done
        } catch (n: NeedDecision) {
            recorder.rollback(mark)
            Progress.Waiting(Pending(n.decision, Continuation.PlacingTriggers(waiting, answers = answers)))
        }
    }

    /**
     * CR 610.3: the second one-shot effects of "until" effects whose event happened (the exiled cards return), done at
     * once without the Stack; those created by one event happen together (CR 610.3d).
     */
    fun runOneShots(act: Act) {
        val waiting = act.state.triggered.toList()
        val due = waiting.filter { it.oneShot }
        if (due.isEmpty()) return
        act.record(Change.TriggersWaiting(waiting, waiting.filterNot { it.oneShot }))
        val batch = mutableListOf<arcanum.engine.resolution.Batched>()
        val scopes = due.map { t ->
            val cause = arcanum.engine.action.Cause(t.controller, t.source, key = "until@${act.state.nextTimestamp()}")
            arcanum.engine.resolution.Scope(act, cause, t.names, t.event, batch = batch).also { scope ->
                (t.ability as? Ability.Triggered)?.instructions?.steps?.let { arcanum.engine.resolution.Steps.run(scope, it) }
                scope.batch = null
            }
        }
        arcanum.engine.resolution.Steps.flush(act, scopes.first().cause, batch)
    }

    /** CR 603.3b: a player with two or more triggers puts them on the Stack in the order they choose (P5: only when the
     * order can matter). */
    private fun order(act: Act, player: PlayerId, mine: List<WaitingTrigger>): List<WaitingTrigger> {
        // Triggers of the same ability of the same source are taken as interchangeable (P5)
        if (mine.size < 2 || mine.all { it.ability == mine.first().ability && it.source == mine.first().source }) return mine
        // API review 3: each with its printed text and origin, so two triggers of one source can be told apart
        val options = mine.mapIndexed { i, t ->
            TriggerOption(i, t.source, arcanum.engine.decision.Texts.of(t.ability, t.origin, t.sourceSeen?.characteristics), t.origin)
        }
        val order = (act.ask { Decision.OrderTriggers(it, player, null, options) } as Answer.Order).order
        require(order.sorted() == mine.indices.toList()) { "Order every trigger once: $order" }
        return order.map { mine[it] }
    }

    /** CR 603.3d: modes and targets are chosen as it is put on the Stack; without a legal target it is removed. */
    private fun push(act: Act, t: WaitingTrigger) {
        val ability = t.ability as? Ability.Triggered ?: return
        val stackId = ObjectId.StackItem("trigger:${act.state.nextTimestamp()}")
        val choices = Casts.announceOrNull(act, t.controller, t.source, ability.instructions, t.names, stackId, required = true, event = t.event) ?: return
        t.source?.let { recordTriggerCount(act, it, t.origin) }
        val item = StackItem(
            id = stackId, kind = StackItemKind.Triggered, controller = t.controller, source = t.source, ability = ability,
            origin = t.origin, targets = choices.second, modes = choices.first, names = t.names,
            divided = divide(act, t, ability, choices.first, choices.second),
            snapshots = (listOfNotNull(t.sourceSeen) + t.snapshots).distinctBy { it.id }, event = t.event, delayedTrigger = t.delayedTrigger,
            timestamp = act.state.nextTimestamp(),
        )
        act.record(Change.StackItemAdded(item, act.state.stack.size, listOf(Meaning.Triggered)))
        // tla: its source attacking made it trigger (CR 603.2): "attacking causes a triggered ability of that creature to trigger"
        val source = t.source
        if (source != null && (ability.trigger as? arcanum.api.Trigger.On)?.events.orEmpty().any { it is arcanum.api.EventPattern.Attack } &&
            t.event.objects.any { it.role == arcanum.api.EventRole.Object && source in it.ids })
            act.record(Change.Happened(arcanum.engine.change.GameEvent.AbilityTriggered(source, stackId, attack = true)))
    }

    /** dsk backfill: an amount divided among a triggered ability's targets is divided as it is put on the Stack (CR 603.3d,
     * 601.2d): a single target gets all of it; among two or more, its controller divides it, at least one each. */
    private fun divide(
        act: Act, t: WaitingTrigger, ability: Ability.Triggered, modes: List<Int>, targets: Map<String, List<TargetRef>>,
    ): List<arcanum.engine.state.DividedShare> {
        val slots = ability.instructions.targets + modes.flatMap { ability.instructions.modes?.options?.getOrNull(it)?.instructions?.targets.orEmpty() }
        if (slots.none { it.divided != null }) return emptyList()
        val e = act.evaluator(arcanum.engine.query.Context(t.controller, t.source, t.names, event = t.event))
        return slots.flatMap { slot ->
            val amount = slot.divided?.let { e.value(it) } ?: return@flatMap emptyList()
            val chosen = targets[slot.name].orEmpty()
            when (chosen.size) {
                0 -> emptyList()
                1 -> listOf(arcanum.engine.state.DividedShare(slot.name, chosen.single(), amount))
                else -> {
                    val shares = (act.ask { Decision.Divide(it, t.controller, t.source, amount, chosen, 1) } as Answer.Division).shares
                    require(shares.sumOf { it.amount } == amount && shares.all { it.amount >= 1 && it.target in chosen }) {
                        "Divide $amount among the targets, at least 1 each"
                    }
                    shares.map { arcanum.engine.state.DividedShare(slot.name, it.target, it.amount) }
                }
            }
        }
    }

    private fun recordTriggerCount(act: Act, source: ObjectId, origin: AbilityOrigin?) {
        val key = origin?.key ?: return
        val o = act.state.obj(source) ?: return
        val turn = act.state.turn.number
        val count = (o.facts.triggers.firstOrNull { it.key == key && it.turn == turn }?.count ?: 0) + 1
        val triggers = o.facts.triggers.filterNot { it.key == key && it.turn == turn } + TurnCount(key, turn, count)
        act.record(Change.Facts(source, o.facts, o.facts.copy(triggers = triggers)))
    }

    /** CR 603.8: a state trigger triggers when its condition is true, and again only after it has left the Stack. */
    private fun stateTriggers(recorder: Recorder) {
        val state = recorder.state
        val view = Engine.view(state)
        val found = sources(state, view).filter { it.ability.trigger is Trigger.State }.filter { s ->
            val pending = state.triggered.any { it.source == s.id && it.origin == s.origin } ||
                state.stackItems.values.any { it.source == s.id && it.origin == s.origin }
            !pending && Evaluator(ViewBoard(state, view), Context(s.controller, s.id)).holds((s.ability.trigger as Trigger.State).condition)
        }.map { s -> WaitingTrigger(s.controller, s.ability, s.origin, s.id, null, TriggerEvent()) }
        addWaiting(recorder, found)
    }
}

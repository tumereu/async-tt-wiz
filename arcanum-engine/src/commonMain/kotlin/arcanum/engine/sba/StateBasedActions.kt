package arcanum.engine.sba

import arcanum.api.Ability
import arcanum.api.CardType
import arcanum.api.CounterKind
import arcanum.api.Mechanic
import arcanum.api.Supertype
import arcanum.api.TargetKind
import arcanum.api.Trigger
import arcanum.api.Zone
import arcanum.engine.Pending
import arcanum.engine.Progress
import arcanum.engine.action.Act
import arcanum.engine.action.Actions
import arcanum.engine.action.Asker
import arcanum.engine.action.NeedDecision
import arcanum.engine.action.PlannedMove
import arcanum.engine.change.Change
import arcanum.engine.change.Changes
import arcanum.engine.change.Meaning
import arcanum.engine.change.Placed
import arcanum.engine.change.Recorder
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Continuation
import arcanum.engine.decision.Decision
import arcanum.engine.query.Context
import arcanum.engine.state.ObjectId
import arcanum.engine.state.TargetRef

/**
 * State-based actions (CR 704): checked whenever a player would get priority, all at once, repeated until none apply
 * (CR 704.3, through [arcanum.engine.Perform.settle]); the legend rule asks which to keep ([Continuation.StateBased]).
 * All the actions of one check happen as one event where they move objects (CR 704.3).
 */
internal object StateBasedActions {
    /** Performs every state-based action that applies, once (CR 704.3); [Progress.Done] when none did. */
    fun check(recorder: Recorder): Progress = run(recorder, emptyList())

    fun resume(recorder: Recorder, continuation: Continuation.StateBased, answer: Answer): Progress =
        run(recorder, continuation.answers + answer)

    private fun run(recorder: Recorder, answers: List<Answer>): Progress {
        val mark = recorder.changes.size
        val act = Act(recorder, Asker(answers, "sba@${recorder.state.nextTimestamp()}"))
        return try {
            once(act)
            Progress.Done
        } catch (n: NeedDecision) {
            recorder.rollback(mark)
            Progress.Waiting(Pending(n.decision, Continuation.StateBased(answers = answers)))
        }
    }

    private fun once(act: Act) {
        // Not a state-based action, but checked with them before a player gets priority (CR 117.5): cards a player may
        // look at any time are filled in for the viewer (D60)
        arcanum.engine.action.Knowledge.fillLookable(act)
        val state = act.state
        val view = act.view()
        val toGraveyard = mutableListOf<ObjectId>()
        val destroyed = mutableListOf<ObjectId>()
        val unattach = mutableListOf<ObjectId>()
        val unbestow = mutableListOf<ObjectId>()
        val removedCounters = mutableListOf<Triple<ObjectId, CounterKind, Int>>()
        val sacrificed = mutableListOf<ObjectId>()

        // CR 704.5a, b, c: a player with 0 or less life, who drew from an empty library, or with 10 or more poison
        // counters loses (unless they can't, CR 104.3b)
        val losers = state.players.filter { p ->
            // "You don't lose the game for having 0 or less life."
            !p.lost && (p.life <= 0 && view.players[p.id]?.mechanics.orEmpty().none { it.mechanic == Mechanic.NoLossForZeroLife } ||
                (p.ledger[Actions.EMPTY_DRAW] ?: 0) > 0 || p.counters(CounterKind.Poison) >= 10) &&
                view.players[p.id]?.mechanics.orEmpty().none { it.mechanic == Mechanic.CantLoseGame }
        }.map { it.id }
        val emptyDraws = state.players.filter { (it.ledger[Actions.EMPTY_DRAW] ?: 0) > 0 }

        // CR 704.5d: a token in a zone other than the battlefield ceases to exist
        val strayTokens = state.objects.values.filter { it.token && it.zone != Zone.Battlefield }

        val battlefield = state.battlefield.filter { state.obj(it)?.phasedOut == false }
        battlefield.forEach { id ->
            val o = state.objOrThrow(id)
            val c = view[id]?.characteristics ?: return@forEach
            if (c.isType(CardType.Creature)) {
                val toughness = c.toughness ?: 0
                when {
                    // CR 704.5f: toughness 0 or less: put into the graveyard (not destroyed)
                    toughness <= 0 -> toGraveyard += id
                    // CR 704.5g, h: lethal damage, or any damage from a source with deathtouch: destroyed
                    o.damage >= toughness || (o.deathtouchDamage && o.damage > 0) -> destroyed += id
                }
            }
            // CR 704.5i: a planeswalker with 0 loyalty
            if (c.isType(CardType.Planeswalker) && o.counters(CounterKind.Loyalty) <= 0) toGraveyard += id
            // CR 704.5v: a battle with 0 defense
            if (c.isType(CardType.Battle) && o.counters(CounterKind.Defense) <= 0) toGraveyard += id
            // CR 704.5m: an Aura attached to an illegal object or player, or not attached
            if (c.subtypes.any { it.name == "Aura" } && !c.isType(CardType.Creature)) {
                // CR 702.103f: a bestowed Aura attached illegally or unattached becomes unattached and stops being
                // bestowed (an exception to 704.5m)
                if (!auraLegal(act, id)) { if (o.bestowed) unbestow += id else toGraveyard += id }
            } else if (o.attachedTo != null) {
                // CR 704.5n, p: Equipment or Fortification attached illegally, or anything else attached, becomes unattached
                val host = (o.attachedTo as? TargetRef.Object)?.id
                val hostChars = host?.let { view[it]?.characteristics }
                val equipment = c.subtypes.any { it.name == "Equipment" } && !c.isType(CardType.Creature)
                val fortification = c.subtypes.any { it.name == "Fortification" } && !c.isType(CardType.Creature)
                val legal = host != null && state.obj(host)?.zone == Zone.Battlefield && when {
                    equipment -> hostChars?.isType(CardType.Creature) == true || canAttachTo(act, id, host)
                    fortification -> hostChars?.isType(CardType.Land) == true
                    else -> false
                } && !protectedFrom(act, TargetRef.Object(host), id)
                if (!legal) unattach += id
            }
            // CR 704.5q: +1/+1 and -1/-1 counters annihilate
            val plus = o.counters(CounterKind.PowerToughness(1, 1))
            val minus = o.counters(CounterKind.PowerToughness(-1, -1))
            if (plus > 0 && minus > 0) removedCounters += Triple(id, CounterKind.PowerToughness(1, 1), minOf(plus, minus))
            // CR 714.4: a Saga with lore counters at least its final chapter, and no chapter ability waiting or on the Stack
            if (c.subtypes.any { it.name == "Saga" }) {
                val final = c.abilities.mapNotNull { ((it.ability as? Ability.Triggered)?.trigger as? Trigger.Chapter)?.chapters?.maxOrNull() }.maxOrNull()
                val busy = state.stackItems.values.any { it.source == id && (it.ability as? Ability.Triggered)?.trigger is Trigger.Chapter } ||
                    state.triggered.any { it.source == id && (it.ability as? Ability.Triggered)?.trigger is Trigger.Chapter }
                if (final != null && o.counters(CounterKind.Lore) >= final && !busy) sacrificed += id
            }
        }

        // CR 704.5j: a player with two or more legendary permanents with the same name chooses one; the rest go
        battlefield.filter { view[it]?.characteristics?.supertypes?.contains(Supertype.Legendary) == true }
            .groupBy { (view[it]?.controller ?: state.objOrThrow(it).owner) to view[it]?.characteristics?.name }
            .filter { (key, ids) -> key.second != null && ids.size >= 2 }
            .forEach { (key, ids) ->
                val kept = (act.asker.ask { Decision.LegendRule(it, key.first, null, key.second!!, ids) } as Answer.Keep).kept
                require(kept in ids) { "Keep one of $ids" }
                toGraveyard += ids.filter { it != kept }
            }

        // CR 704.3: all at once
        losers.forEach { act.record(Change.Lost(it, false, true)) }
        emptyDraws.forEach { p -> act.record(Change.Ledger(p.id, Actions.EMPTY_DRAW, p.ledger[Actions.EMPTY_DRAW] ?: 0, 0)) }
        if (strayTokens.isNotEmpty()) act.record(Change.Removed(strayTokens.map { Placed(it, state.positionOf(it.id)) }
            .sortedByDescending { it.index }.let { placed -> recomputeIndexes(act, placed) }))
        removedCounters.forEach { (id, plus, n) ->
            act.record(Changes.counters(act.state, TargetRef.Object(id), plus, -n, listOf(Meaning.StateBased)))
            act.record(Changes.counters(act.state, TargetRef.Object(id), CounterKind.PowerToughness(-1, -1), -n, listOf(Meaning.StateBased)))
        }
        unattach.forEach { Actions.attach(act, it, null) }
        unbestow.forEach { id ->
            if (act.state.objOrThrow(id).attachedTo != null) Actions.attach(act, id, null)
            Bestow.cease(act, id)
        }
        val graveyard = toGraveyard.distinct()
        val destroyable = destroyed.distinct().filter { it !in graveyard && view[it]?.has { m -> m == Mechanic.Indestructible } != true }
        val moves = graveyard.map { PlannedMove(it, Zone.Graveyard, meanings = listOf(Meaning.StateBased)) } +
            destroyable.map { PlannedMove(it, Zone.Graveyard, meanings = listOf(Meaning.StateBased, Meaning.Destroyed)) } +
            sacrificed.filter { it !in graveyard && it !in destroyable }.map { PlannedMove(it, Zone.Graveyard, meanings = listOf(Meaning.StateBased, Meaning.Sacrificed)) }
        if (moves.isNotEmpty()) Actions.move(act, null, moves)
    }

    /** CR 702.103e–g: what stops a bestowed Aura or Aura spell being bestowed. */
    object Bestow {
        /** It ceases to be bestowed: the effect making it an Aura ends (it is a creature again). */
        fun cease(act: Act, id: ObjectId) {
            val o = act.state.objOrThrow(id)
            if (o.bestowed) act.record(Change.Facts(id, o.facts, o.facts.copy(alternative = null)))
        }
    }

    /** Removing several objects from the same zone: each [Placed] index is its position once the ones before it in the
     * list are gone (removal applies in order). */
    private fun recomputeIndexes(act: Act, placed: List<Placed>): List<Placed> {
        val scratch = act.state.deepCopy()
        return placed.map { p ->
            val index = scratch.zone(p.obj.zone, p.obj.owner).indexOf(p.obj.id)
            val result = Placed(p.obj, index)
            scratch.apply(Change.Removed(listOf(result)))
            result
        }
    }

    /** CR 303.4d, 704.5m: an Aura is legal attached to what its enchant ability allows. */
    private fun auraLegal(act: Act, id: ObjectId): Boolean {
        val o = act.state.objOrThrow(id)
        val host = o.attachedTo ?: return false
        val view = act.view()
        val enchant = view[id]?.mechanics.orEmpty().mapNotNull { a -> (a.mechanic as? Mechanic.CanEnchant)?.let { it to a } }
        val e = act.evaluator(Context(view[id]?.controller ?: o.owner, id))
        return when (host) {
            is TargetRef.Object -> act.state.obj(host.id)?.zone == Zone.Battlefield && host.id != id && enchant.all { (m, _) ->
                when (val what = m.what) {
                    is TargetKind.Objects -> e.matches(what.filter, host.id)
                    is TargetKind.Either -> e.matches(what.objects, host.id)
                    is TargetKind.Players -> false
                }
            }
            is TargetRef.Player -> !act.state.player(host.id).lost && enchant.all { (m, _) ->
                when (val what = m.what) {
                    is TargetKind.Players -> e.matches(what.filter, host.id)
                    is TargetKind.Either -> e.matches(what.players, host.id)
                    is TargetKind.Objects -> false
                }
            }
        } && !protectedFrom(act, host, id)
    }

    /** CR 702.16c, d: a permanent or player with protection can't be enchanted by an Aura, or equipped or fortified by
     * an Equipment or Fortification, with that quality. */
    private fun protectedFrom(act: Act, host: TargetRef, attachment: ObjectId): Boolean {
        val mechanics = when (host) {
            is TargetRef.Object -> act.view()[host.id]?.mechanics.orEmpty()
            is TargetRef.Player -> act.view().players[host.id]?.mechanics.orEmpty()
        }
        return mechanics.any { a ->
            val p = a.mechanic as? Mechanic.Protection ?: return@any false
            act.evaluator(Context(a.controller, a.source)).matches(p.from, attachment)
        }
    }

    private fun canAttachTo(act: Act, equipment: ObjectId, host: ObjectId): Boolean =
        act.view()[equipment]?.mechanics.orEmpty().any { a ->
            val m = a.mechanic as? Mechanic.CanAttachTo ?: return@any false
            act.evaluator(Context(a.controller, equipment)).matches(m.what, host)
        }
}

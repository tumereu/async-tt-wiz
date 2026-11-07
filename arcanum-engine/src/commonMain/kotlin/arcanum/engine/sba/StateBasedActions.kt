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
        val act = Act(recorder, Asker(answers, arcanum.engine.casting.askerPrefix(recorder.state, "sba")))
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
        // fin: control changes made by continuous effects (CR 613.1b)
        ControlChanges.notice(act.recorder)
        val state = act.state
        val view = act.view()
        // hob: storied (CR 702.195a): a static ability, not a state-based action, but checked with them: any time its condition
        // holds, its player gets the designation at once, for the rest of the game; the checks then run again, so continuous
        // effects apply before triggers are looked for (702.195c)
        state.players.filter { !it.lost }.forEach { p ->
            view.players[p.id]?.mechanics.orEmpty().mapNotNull { (it.mechanic as? Mechanic.GetsDesignation)?.designation }.distinct()
                .filter { it !in state.player(p.id).designations }
                .forEach { act.record(Change.PlayerDesignation(p.id, it, before = false, after = true)) }
        }
        // CR 702.179a: start your engines! gives a player with no speed speed 1 (a state-based action)
        arcanum.engine.turn.Speed.check(act, view)
        // CR 702.145c, d, f, g: day or night for daybound and nightbound permanents, and their transforming (at once, checked here)
        arcanum.engine.turn.DayNight.check(act, view)
        // CR 701.54a: a Ring-bearer another player gained control of stops being one (checked here, as the designation ends at once)
        arcanum.engine.ring.TheRing.check(act, view)
        // CR 702.95e: paired creatures that stop being creatures under the pairing player's control become unpaired at once
        arcanum.engine.soulbond.Soulbond.check(act, view)
        // CR 309.6: a dungeon whose venture marker is on its bottommost room, its room ability gone, is completed
        arcanum.engine.dungeon.Dungeons.check(act)
        val toGraveyard = mutableListOf<ObjectId>()
        val destroyed = mutableListOf<ObjectId>()
        val unattach = mutableListOf<ObjectId>()
        val unbestow = mutableListOf<ObjectId>()
        val removedCounters = mutableListOf<Triple<ObjectId, CounterKind, Int>>()
        val sacrificed = mutableListOf<ObjectId>()

        // CR 704.5a, b, c: a player with 0 or less life, who drew from an empty library, or with 10 or more poison
        // counters loses (unless they can't, CR 104.3b)
        // fra: once one player is left (another won the game, CR 104.2b) the game is over (CR 104.1): nobody loses after
        val over = state.players.count { !it.lost } <= 1
        // Each loser's reason is the first of these that holds (LossReason)
        val losers = if (over) emptyList() else state.players.filter { p ->
            !p.lost && view.players[p.id]?.mechanics.orEmpty().none { it.mechanic == Mechanic.CantLoseGame }
        }.mapNotNull { p ->
            when {
                // "You don't lose the game for having 0 or less life."
                p.life <= 0 && view.players[p.id]?.mechanics.orEmpty().none { it.mechanic == Mechanic.NoLossForZeroLife } ->
                    p.id to arcanum.engine.setup.LossReason.ZeroLife
                (p.ledger[Actions.EMPTY_DRAW] ?: 0) > 0 -> p.id to arcanum.engine.setup.LossReason.EmptyLibrary
                p.counters(CounterKind.Poison) >= 10 -> p.id to arcanum.engine.setup.LossReason.Poison
                else -> null
            }
        }
        val emptyDraws = state.players.filter { (it.ledger[Actions.EMPTY_DRAW] ?: 0) > 0 }

        // CR 704.5d: a token in a zone other than the battlefield ceases to exist
        val strayTokens = state.objects.values.filter { it.token && it.zone != Zone.Battlefield } +
            // dsk gaps: CR 704.5e: a copy of a card in a zone other than the Stack or the battlefield ceases to exist
            state.objects.values.filter { it.copyOfCard && it.zone != Zone.Battlefield && it.zone != Zone.Stack &&
                !arcanum.engine.prepare.Prepare.keptInExile(state, it) } // fra: CR 722.3c

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
            // fra: "Planeswalkers you control aren't put into their owners' graveyards for having 0 loyalty."
            if (c.isType(CardType.Planeswalker) && o.counters(CounterKind.Loyalty) <= 0 &&
                view[id]?.has { it == Mechanic.NoGraveyardForZeroLoyalty } != true) toGraveyard += id
            // CR 704.5v, w: a battle with 0 defense (a Siege not while an ability of its own that triggered waits or is on the Stack)
            if (c.isType(CardType.Battle) && o.counters(CounterKind.Defense) <= 0 && !arcanum.engine.combat.Battles.siegeWaits(state, view, id))
                toGraveyard += id
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

        // CR 704.5x, y: battles without an appropriate protector get one; with none possible, they go to the graveyard
        toGraveyard += arcanum.engine.combat.Battles.checkProtectors(act, view, battlefield)

        // CR 704.5j: a player with two or more legendary permanents with the same name chooses one; the rest go
        // fra: permanents the "legend rule" doesn't apply to
        battlefield.filter { view[it]?.characteristics?.supertypes?.contains(Supertype.Legendary) == true &&
            view[it]?.has { m -> m == Mechanic.LegendRuleDoesntApply } != true }
            .groupBy { (view[it]?.controller ?: state.objOrThrow(it).owner) to view[it]?.characteristics?.name }
            .filter { (key, ids) -> key.second != null && ids.size >= 2 }
            .forEach { (key, ids) ->
                val kept = (act.ask { Decision.LegendRule(it, key.first, null, key.second!!, ids) } as Answer.Keep).kept
                require(kept in ids) { "Keep one of $ids" }
                toGraveyard += ids.filter { it != kept }
            }

        // CR 704.3: all at once
        losers.forEach { (player, reason) -> act.record(Change.Lost(player, false, true, reason)) }
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

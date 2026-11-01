package arcanum.engine.resolution

import arcanum.api.Affected
import arcanum.api.CardDefinition
import arcanum.api.CardType
import arcanum.api.Color
import arcanum.api.CounterKind
import arcanum.api.Designation
import arcanum.api.Duration
import arcanum.api.Effect
import arcanum.api.EventPattern
import arcanum.api.FaceDefinition
import arcanum.api.Instructions
import arcanum.api.KeywordAction
import arcanum.api.KeywordExpansions
import arcanum.api.Layout
import arcanum.api.ManaKind
import arcanum.api.ManaSymbol
import arcanum.api.Mechanic
import arcanum.api.ObjectFilter
import arcanum.api.ObjectMark
import arcanum.api.Objects
import arcanum.api.PlayerFilter
import arcanum.api.Players
import arcanum.api.RecipientFilter
import arcanum.api.Step
import arcanum.api.Trigger
import arcanum.api.Value
import arcanum.api.Zone
import arcanum.engine.action.Actions
import arcanum.engine.action.Cause
import arcanum.engine.action.PlannedMove
import arcanum.engine.action.Targeting
import arcanum.engine.change.Change
import arcanum.engine.change.Changes
import arcanum.engine.change.DamageDealt
import arcanum.engine.change.GameEvent
import arcanum.engine.change.Meaning
import arcanum.engine.change.Placed
import arcanum.engine.decision.Answer
import arcanum.engine.decision.RandomKind
import arcanum.engine.decision.WaitingTrigger
import arcanum.engine.query.Evaluator
import arcanum.engine.state.DelayedTrigger
import arcanum.engine.state.EffectId
import arcanum.engine.state.EffectObject
import arcanum.engine.state.GameObject
import arcanum.engine.state.ManaUnit
import arcanum.engine.state.NamedValue
import arcanum.engine.state.ObjectId
import arcanum.engine.state.PlayerId
import arcanum.engine.state.StackItem
import arcanum.engine.state.StackItemKind
import arcanum.engine.state.StatusFlag
import arcanum.engine.state.TargetRef
import arcanum.engine.state.TokenIds
import arcanum.engine.trigger.Triggers

/**
 * Every one-shot [Effect] of arcanum-api (CR 610), performed through the event pipeline ([Actions]). Objects that left
 * their zone can be read (last known information, CR 608.2h) but not acted on (CR 400.7). Effects that produce objects
 * bind them ([Effect] KDoc): `bind`, or `preBind`/`postBind` around a zone change (D21). Parts that can't be automated
 * yet stop with a [arcanum.engine.decision.Decision.Manual] (D61).
 */
internal object Effects {
    fun perform(scope: Scope, effect: Effect) {
        val act = scope.act
        val cause = scope.cause
        when (effect) {
            is Effect.DealDamage -> dealDamage(scope, effect)
            is Effect.Fight -> {
                // CR 701.14: each deals damage equal to its power to the other, as one event
                val a = scope.objects(effect.first).firstOrNull { creatureOnBattlefield(scope, it) }
                val b = scope.objects(effect.second).firstOrNull { creatureOnBattlefield(scope, it) }
                if (a != null && b != null) {
                    val e = scope.eval()
                    Actions.damage(act, cause, listOf(
                        DamageDealt(a, TargetRef.Object(b), e.property(arcanum.api.NumericProperty.Power, a)),
                        DamageDealt(b, TargetRef.Object(a), e.property(arcanum.api.NumericProperty.Power, b)),
                    ))
                }
            }
            is Effect.Destroy -> {
                val ids = onBattlefield(scope, effect.objects)
                scope.bind(effect.preBind, NamedValue.Objects(ids))
                val batch = scope.batch
                if (batch != null) {
                    val view = act.view()
                    val ok = ids.filter { id -> view[id]?.has { it == Mechanic.Indestructible } != true }
                    batch += Batched(scope, ok.map { PlannedMove(it, Zone.Graveyard, meanings = listOf(Meaning.Destroyed)) }, effect.postBind, effect.noRegeneration)
                } else {
                    val moved = Actions.destroy(act, cause, ids, effect.noRegeneration)
                    scope.bind(effect.preBind, NamedValue.Objects(moved.keys.toList()))
                    scope.bind(effect.postBind, NamedValue.Objects(moved.values.toList()))
                }
            }
            is Effect.Sacrifice -> {
                val ids = onBattlefield(scope, effect.objects)
                scope.bind(effect.preBind, NamedValue.Objects(ids))
                moveOrBatch(scope, ids.filter { id -> act.view()[id]?.has { it == Mechanic.CantBeSacrificed } != true }
                    .map { PlannedMove(it, Zone.Graveyard, meanings = listOf(Meaning.Sacrificed)) }, effect.postBind)
            }
            is Effect.Move -> move(scope, effect)
            is Effect.Draw -> scope.players(effect.players).forEach { p ->
                val drawn = Actions.draw(act, cause, p, scope.value(effect.count))
                scope.bind(effect.preBind, NamedValue.Objects(drawn.keys.toList()))
                scope.bind(effect.postBind, NamedValue.Objects(drawn.values.toList()))
            }
            is Effect.Discard -> discard(scope, effect)
            is Effect.Mill -> {
                val milled = scope.players(effect.players).flatMap { p -> Actions.mill(act, cause, p, scope.value(effect.count)).entries }
                scope.bind(effect.preBind, NamedValue.Objects(milled.map { it.key }))
                scope.bind(effect.postBind, NamedValue.Objects(milled.map { it.value }))
            }
            is Effect.Scry -> scope.players(effect.players).forEach { p -> scry(scope, p, scope.value(effect.count), effect.bind) }
            is Effect.Surveil -> scope.players(effect.players).forEach { p -> surveil(scope, p, scope.value(effect.count), effect.bind) }
            is Effect.Search -> search(scope, effect)
            is Effect.Shuffle -> {
                val kept = effect.except?.let { scope.objects(it) }.orEmpty()
                scope.players(effect.library).forEach { Actions.shuffle(act, it, kept, scope.self) }
            }
            is Effect.Reorder -> reorder(scope, effect)
            is Effect.Reveal -> {
                val ids = scope.objects(effect.objects)
                act.fill(scope.actor, scope.self, ids, "revealed")
                if (ids.isNotEmpty()) act.record(Change.Happened(GameEvent.Revealed(scope.actor, ids)))
                scope.bind(effect.bind, NamedValue.Objects(ids))
            }
            is Effect.LookAt -> {
                val ids = scope.objects(effect.objects)
                act.fill(scope.players(effect.players).firstOrNull() ?: scope.actor, scope.self, ids, "looked at")
                scope.bind(effect.bind, NamedValue.Objects(ids))
            }
            is Effect.GainLife -> {
                val total = scope.players(effect.players).sumOf { Actions.gainLife(act, cause, it, scope.value(effect.amount)) }
                scope.bind(effect.bind, NamedValue.Number(total))
            }
            is Effect.LoseLife -> {
                val total = scope.players(effect.players).sumOf { Actions.loseLife(act, cause, it, scope.value(effect.amount)) }
                scope.bind(effect.bind, NamedValue.Number(total))
            }
            // CR 119.5: setting a life total is gaining or losing the difference
            is Effect.SetLife -> scope.players(effect.players).forEach { p ->
                val diff = scope.value(effect.amount) - act.state.player(p).life
                if (diff > 0) Actions.gainLife(act, cause, p, diff) else if (diff < 0) Actions.loseLife(act, cause, p, -diff)
            }
            is Effect.PutCounters -> putCounters(scope, effect)
            is Effect.RemoveCounters -> onBattlefield(scope, effect.objects).forEach { id ->
                val n = scope.value(effect.amount)
                val kinds = effect.kind?.let { listOf(it) } ?: act.state.objOrThrow(id).counters.map { it.kind }
                var left = n
                kinds.forEach { k -> if (left > 0) left -= Actions.removeCounters(act, TargetRef.Object(id), k, left) }
            }
            is Effect.GivePlayerCounters -> scope.players(effect.players).forEach { p ->
                Actions.putCounters(act, cause, TargetRef.Player(p), effect.kind, scope.value(effect.amount))
            }
            is Effect.Tap -> Actions.tap(act, onBattlefield(scope, effect.objects), true)
            is Effect.Untap -> Actions.tap(act, onBattlefield(scope, effect.objects), false)
            is Effect.Attach -> {
                val to = recipients(scope, effect.to).firstOrNull() ?: return
                onBattlefield(scope, effect.objects).forEach { if (TargetRef.Object(it) != to) Actions.attach(act, it, to) }
            }
            is Effect.Unattach -> onBattlefield(scope, effect.objects).forEach { Actions.attach(act, it, null) }
            // CR 701.28: only a transforming double-faced permanent transforms; it gets a new timestamp (CR 613.7g)
            is Effect.Transform -> onBattlefield(scope, effect.objects).forEach { id ->
                val o = act.state.objOrThrow(id)
                if (o.definition?.layout != Layout.Transform) return@forEach
                act.record(Change.Face(id, o.face, 1 - o.face, listOf(Meaning.Transformed)))
                act.record(Change.Restamped(id, o.timestamp, act.state.nextTimestamp()))
            }
            is Effect.Flip -> onBattlefield(scope, effect.objects).forEach { id ->
                val o = act.state.objOrThrow(id)
                if (o.definition?.layout == Layout.Flip && o.face == 0) act.record(Change.Face(id, 0, 1, listOf(Meaning.Flipped)))
            }
            is Effect.TurnFaceUp -> onBattlefield(scope, effect.objects).forEach { id ->
                val o = act.state.objOrThrow(id)
                if (!o.faceDown) return@forEach
                act.record(Change.Status(id, StatusFlag.FaceDown, true, false, listOf(Meaning.TurnedFaceUp)))
                act.record(Change.Restamped(id, o.timestamp, act.state.nextTimestamp()))
                effect.counters.forEach { c -> Actions.putCounters(act, cause, TargetRef.Object(id), c.kind, scope.value(c.amount)) }
            }
            is Effect.TurnFaceDown -> onBattlefield(scope, effect.objects).forEach { id ->
                val o = act.state.objOrThrow(id)
                if (o.faceDown) return@forEach
                act.record(Change.Status(id, StatusFlag.FaceDown, false, true, listOf(Meaning.TurnedFaceDown)))
                act.record(Change.Restamped(id, o.timestamp, act.state.nextTimestamp()))
            }
            is Effect.PhaseOut -> onBattlefield(scope, effect.objects).forEach {
                Actions.tapStatus(act, it, StatusFlag.PhasedOut, true, listOf(Meaning.PhasedOut))
            }
            is Effect.Mark -> onBattlefield(scope, effect.objects).forEach {
                Actions.tapStatus(act, it, StatusFlag.Marked(effect.mark), !effect.remove)
            }
            is Effect.GiveDesignation -> scope.players(effect.players).firstOrNull()?.let { p -> designate(scope, p, effect.designation) }
            is Effect.CreateToken -> createToken(scope, effect)
            is Effect.CopySpell -> copySpell(scope, effect)
            is Effect.Counter -> counter(scope, scope.objects(effect.objects))
            is Effect.AddMana -> scope.players(effect.players).forEach { p -> addMana(scope, p, effect.mana) }
            is Effect.ApplyMechanics -> applyMechanics(scope, effect.mechanics, effect.affects, effect.duration, effect.uses)
            is Effect.PreventDamage -> preventDamage(scope, effect)
            is Effect.RemoveFromCombat -> removeFromCombat(scope, onBattlefield(scope, effect.objects))
            is Effect.RemoveAllDamage -> onBattlefield(scope, effect.objects).forEach { id ->
                val o = act.state.objOrThrow(id)
                if (o.damage > 0 || o.deathtouchDamage) act.record(Change.DamageMarked(id, o.damage, 0, o.deathtouchDamage, false))
            }
            is Effect.CreateDelayedTrigger -> createDelayed(scope, effect.trigger, effect.instructions, effect.duration, effect.repeats)
            is Effect.RollDie -> {
                val player = scope.players(effect.players).firstOrNull() ?: scope.actor
                val rolls = (act.random(player, scope.self, RandomKind.Die(effect.sides, effect.count)) as Answer.DieRolls).results
                act.record(Change.Happened(GameEvent.DieRolled(player, effect.sides, rolls)))
                scope.bind(effect.bind, NamedValue.Rolls(rolls))
            }
            is Effect.FlipCoin -> {
                val player = scope.players(effect.players).firstOrNull() ?: scope.actor
                val won = (act.random(player, scope.self, RandomKind.CoinFlip) as Answer.CoinFlip).won
                act.record(Change.Happened(GameEvent.CoinFlipped(player, won)))
                scope.bind(effect.bind, NamedValue.Flip(won))
            }
            is Effect.ExchangeControl -> exchangeControl(scope, effect)
            is Effect.MoveCounters -> {
                val to = onBattlefield(scope, effect.to).firstOrNull() ?: return
                onBattlefield(scope, effect.from).forEach { from ->
                    val kinds = effect.kind?.let { listOf(it) } ?: act.state.objOrThrow(from).counters.map { it.kind }
                    kinds.forEach { k ->
                        val n = Actions.removeCounters(act, TargetRef.Object(from), k, scope.value(effect.amount))
                        Actions.putCounters(act, cause, TargetRef.Object(to), k, n)
                    }
                }
            }
            is Effect.ChangeTargets -> changeTargets(scope, effect)
            is Effect.SeparatePiles -> separatePiles(scope, effect)
            is Effect.Remember -> remember(scope, effect.key, scope.objects(effect.objects), effect.replace)
            is Effect.Forget -> forget(scope, effect.key, effect.objects?.let { scope.objects(it) })
            // CR 500.7: the most recently created extra turn is taken first
            is Effect.ExtraTurn -> scope.players(effect.players).forEach { p ->
                act.record(Changes.step(act.state, act.state.turn.copy(extraTurns = listOf(p) + act.state.turn.extraTurns)))
            }
            is Effect.ExtraCombat -> act.manual(scope.actor, scope.self, "Take an additional combat phase" +
                if (effect.followedByMain) " followed by an additional main phase" else "")
            is Effect.SkipNextTurn -> scope.players(effect.players).forEach { act.manual(it, scope.self, "Skip your next turn") }
            // CR 701.10f… exchanging life totals: each gains or loses the difference (CR 119.5)
            is Effect.ExchangeLife -> {
                val a = scope.players(effect.first).firstOrNull() ?: return
                val b = scope.players(effect.second).firstOrNull() ?: return
                val la = act.state.player(a).life
                val lb = act.state.player(b).life
                setLife(scope, a, lb)
                setLife(scope, b, la)
            }
            is Effect.SpellGets -> scope.objects(effect.objects).forEach { id ->
                // CR 400.7d: the effect follows the spell onto the battlefield
                val next = (id as? ObjectId.Card)?.next()
                applyMechanics(scope, effect.mechanics, null, effect.duration ?: Duration.Indefinitely, null,
                    locked = listOfNotNull(id, next))
            }
            is Effect.WinGame -> scope.players(effect.players).forEach { winner ->
                act.state.players.filter { it.id != winner && !it.lost }.forEach { lose(scope, it.id) }
            }
            is Effect.LoseGame -> scope.players(effect.players).forEach { lose(scope, it) }
            is Effect.Cast -> Casts.castByEffect(scope, effect)
            // CR 603.12: "when you do" triggers now and goes on the Stack the next time a player would get priority
            is Effect.ReflexiveTrigger -> Triggers.addWaiting(act.recorder, listOf(WaitingTrigger(
                controller = scope.you, ability = arcanum.api.Ability.Triggered(Trigger.On(emptyList()), effect.instructions),
                origin = null, source = scope.self, sourceSeen = scope.self?.let { act.snapshot(it) }, event = scope.event ?: arcanum.engine.state.TriggerEvent(),
                names = scope.names,
            )))
            is Effect.RevealUntil -> revealUntil(scope, effect)
            is Effect.CreateEmblem -> scope.players(effect.players).forEach { p -> createEmblem(scope, p, effect.abilities) }
            is Effect.Action -> keywordAction(scope, effect.action, effect.bind)
        }
    }

    // ---- Helpers

    /** The current objects among [objects] on the battlefield (CR 400.7: stale ids can't be acted on). */
    private fun onBattlefield(scope: Scope, objects: Objects): List<ObjectId> =
        scope.objects(objects).filter { scope.act.state.obj(it)?.zone == Zone.Battlefield }.distinct()

    private fun creatureOnBattlefield(scope: Scope, id: ObjectId): Boolean =
        scope.act.state.obj(id)?.zone == Zone.Battlefield && scope.act.view()[id]?.characteristics?.isType(CardType.Creature) == true

    private fun recipients(scope: Scope, r: arcanum.api.Recipients): List<TargetRef> = Actions.recipientsOf(scope.eval(), r)

    private fun moveOrBatch(scope: Scope, moves: List<PlannedMove>, postBind: String?) {
        val batch = scope.batch
        if (batch != null) {
            batch += Batched(scope, moves, postBind)
            return
        }
        val moved = Actions.move(scope.act, scope.cause, moves)
        scope.bind(postBind, NamedValue.Objects(moves.mapNotNull { moved[it.id] }))
    }

    private fun setLife(scope: Scope, player: PlayerId, to: Int) {
        val diff = to - scope.act.state.player(player).life
        if (diff > 0) Actions.gainLife(scope.act, scope.cause, player, diff) else if (diff < 0) Actions.loseLife(scope.act, scope.cause, player, -diff)
    }

    private fun lose(scope: Scope, player: PlayerId) {
        val act = scope.act
        if (act.state.player(player).lost) return
        if (act.view().players[player]?.mechanics.orEmpty().any { it.mechanic == Mechanic.CantLoseGame }) return
        act.record(Change.Lost(player, false, true))
    }

    // ---- Damage

    private fun dealDamage(scope: Scope, effect: Effect.DealDamage) {
        val act = scope.act
        val source = scope.objects(effect.source).firstOrNull() ?: return
        val targets = recipients(scope, effect.to)
        val divided = scope.cause.item?.divided.orEmpty()
        val parts = targets.map { r ->
            val share = divided.firstOrNull { it.target == r }?.amount
            DamageDealt(source, r, scope.eval(dividedShare = share).value(effect.amount))
        }
        val result = Actions.damage(act, scope.cause, parts, effect.cantBePrevented)
        effect.bind?.let { bind ->
            scope.bind(bind, NamedValue.Number(result.total))
            scope.bind(Evaluator.EXCESS_PREFIX + bind, NamedValue.Number(result.excess))
        }
    }

    // ---- Zone changes

    private fun move(scope: Scope, effect: Effect.Move) {
        val act = scope.act
        val e = scope.eval()
        val dest = effect.to
        val ids = scope.objects(effect.objects).filter { act.state.obj(it) != null }.distinct()
        if (effect.untilLeaves != null && scope.objects(effect.untilLeaves!!).none { act.state.obj(it)?.zone == Zone.Battlefield }) {
            // CR 610.3c: if the object has already left the battlefield, nothing is exiled
            return
        }
        scope.bind(effect.preBind, NamedValue.Objects(ids))
        val from = ids.firstOrNull()?.let { act.state.obj(it)?.zone }
        val controller = dest.controller?.let { e.players(it).firstOrNull() } ?: scope.actor
        val attachTo = dest.attachedTo?.let { recipients(scope, it).firstOrNull() }
        val counters = dest.counters.map { it.kind to e.value(it.amount) }
        val attacking = dest.attacking?.let { attackTarget(scope, it) }
        var ordered = ids
        if (dest.randomOrder && ids.size > 1) {
            ordered = (act.random(scope.actor, scope.self, RandomKind.Objects(ids, ids.size)) as Answer.RandomObjects).chosen
        }
        val position = dest.libraryPosition?.let { Actions.libraryPosition(it) }
        val moves = ordered.map { id ->
            val from = act.state.obj(id)!!.zone
            PlannedMove(
                id, dest.zone, position, if (dest.zone == Zone.Battlefield) controller else null,
                tapped = dest.tapped, faceDown = dest.faceDown, counters = counters, meanings = meaningsOf(from, dest.zone),
                attachTo = attachTo, transformed = dest.transformed, attacking = attacking,
            )
        }
        val batch = scope.batch
        if (batch != null) {
            batch += Batched(scope, moves, effect.postBind)
            return
        }
        val moved = Actions.move(act, scope.cause, moves)
        val after = moves.mapNotNull { moved[it.id] }
        scope.bind(effect.postBind, NamedValue.Objects(after))
        effect.untilLeaves?.let { until ->
            // CR 610.3: when that object leaves the battlefield, the cards return (a delayed trigger here, D19)
            val leaving = scope.objects(until)
            if (after.isNotEmpty()) createDelayed(scope,
                Trigger.On(EventPattern.ZoneChange(ObjectFilter.Is(Objects.Bound(UNTIL_SOURCE)), from = Zone.Battlefield)),
                Instructions(steps = listOf(Step.Do(Effect.Move(Objects.Bound(UNTIL_RETURNED), arcanum.api.Destination(
                    from ?: Zone.Battlefield,
                    controller = Players.OwnerOf(Objects.Bound(UNTIL_RETURNED))))))),
                duration = null, repeats = false,
                extra = mapOf(UNTIL_SOURCE to NamedValue.Objects(leaving), UNTIL_RETURNED to NamedValue.Objects(after)))
        }
    }

    private const val UNTIL_SOURCE = "\$until-source"
    private const val UNTIL_RETURNED = "\$until-returned"

    private fun attackTarget(scope: Scope, target: arcanum.api.AttackTarget): TargetRef? = when (target) {
        arcanum.api.AttackTarget.Chosen -> scope.act.state.opponents(scope.actor).firstOrNull()?.let { TargetRef.Player(it) }
        is arcanum.api.AttackTarget.SameAs -> {
            val of = scope.objects(target.objects).toSet()
            scope.act.state.combat?.attackers.orEmpty().firstOrNull { it.id in of }?.target
                ?: scope.act.state.opponents(scope.actor).firstOrNull()?.let { TargetRef.Player(it) }
        }
        is arcanum.api.AttackTarget.These -> recipients(scope, target.recipients).firstOrNull()
    }

    /** What a move means by where it goes (D33). */
    fun meaningsOf(from: Zone, to: Zone): List<Meaning> = buildList {
        if (to == Zone.Hand && from == Zone.Battlefield) add(Meaning.ReturnedToHand)
        if (to == Zone.Library) add(Meaning.PutIntoLibrary)
    }

    private fun discard(scope: Scope, effect: Effect.Discard) {
        val act = scope.act
        val discarded = mutableListOf<ObjectId>()
        if (effect.objects != null) {
            discarded += scope.objects(effect.objects!!).filter { act.state.obj(it)?.zone == Zone.Hand }
        } else scope.players(effect.players).forEach { p ->
            val hand = act.state.player(p).hand
            val n = scope.value(effect.count)
            discarded += if (effect.random) {
                if (hand.size <= n) hand else (act.random(p, scope.self, RandomKind.Objects(hand, n)) as Answer.RandomObjects).chosen
            } else {
                if (effect.filter != ObjectFilter.Any) act.fill(p, scope.self, hand, "discard")
                val e = scope.eval()
                val options = act.state.player(p).hand.filter { e.matches(effect.filter, it) }
                act.chooseObjects(p, scope.self, options, n, n, "Discard")
            }
        }
        scope.bind(effect.preBind, NamedValue.Objects(discarded))
        moveOrBatch(scope, discarded.map { PlannedMove(it, Zone.Graveyard, meanings = listOf(Meaning.Discarded)) }, effect.postBind)
    }

    /** CR 701.22: look at the top N, put any number on the bottom in any order and the rest on top in any order. */
    private fun scry(scope: Scope, player: PlayerId, n: Int, bind: String?) {
        val act = scope.act
        val top = act.state.player(player).library.take(n)
        if (top.isEmpty()) return
        act.fill(player, scope.self, top, "scry")
        val bottom = act.chooseObjects(player, scope.self, top, 0, top.size, "Put on the bottom")
        val kept = top.filter { it !in bottom }
        val rest = act.state.player(player).library.drop(top.size)
        val after = kept + rest + bottom
        if (after != act.state.player(player).library) act.record(Change.Reorder(Zone.Library, player, act.state.player(player).library, after))
        act.record(Change.Happened(GameEvent.Scried(player, top.size)))
        scope.bind(bind, NamedValue.Objects(bottom))
    }

    /** CR 701.25: look at the top N, put any number into the graveyard and the rest on top in any order. */
    private fun surveil(scope: Scope, player: PlayerId, n: Int, bind: String?) {
        val act = scope.act
        val top = act.state.player(player).library.take(n)
        if (top.isEmpty()) return
        act.fill(player, scope.self, top, "surveil")
        val toGraveyard = act.chooseObjects(player, scope.self, top, 0, top.size, "Put into your graveyard")
        val moved = Actions.moveTo(act, scope.cause, toGraveyard, Zone.Graveyard)
        act.record(Change.Happened(GameEvent.Surveilled(player, top.size)))
        scope.bind(bind, NamedValue.Objects(moved.values.toList()))
    }

    /** CR 701.23: the searcher looks at the zones and finds up to N matching cards (finding none is allowed, 701.23b). */
    private fun search(scope: Scope, effect: Effect.Search) {
        val act = scope.act
        val searcher = scope.players(effect.searcher).firstOrNull() ?: scope.actor
        val found = mutableListOf<ObjectId>()
        scope.players(effect.library).forEach { owner ->
            val cards = effect.zones.flatMap { act.state.zone(it, owner) }
            act.fill(searcher, scope.self, cards, "search")
            if (Zone.Library in effect.zones) act.record(Change.Happened(GameEvent.Searched(searcher, owner)))
            effect.zones.forEach { z -> scope.bind(Evaluator.SEARCHED_PREFIX + (effect.bind ?: "") + ":" + z, NamedValue.Number(1)) }
            val e = scope.eval()
            var options = cards.filter { e.matches(effect.filter, it) }
            val max = scope.value(effect.max)
            val chosen = act.chooseObjects(searcher, scope.self, options, 0, max, "Search for ${effect.filter}".take(120))
            if (effect.differentNames) {
                val names = chosen.map { e.characteristics(it)?.name }
                require(names.distinct().size == names.size) { "The cards must have different names" }
            }
            options = chosen
            found += options
        }
        if (effect.reveal && found.isNotEmpty()) act.record(Change.Happened(GameEvent.Revealed(searcher, found)))
        scope.bind(effect.bind, NamedValue.Objects(found))
    }

    /** "Put them back in any order" (CR 401.4): the chooser's order, first on top. */
    private fun reorder(scope: Scope, effect: Effect.Reorder) {
        val act = scope.act
        val ids = scope.objects(effect.objects).filter { act.state.obj(it)?.zone == Zone.Library }
        if (ids.size < 2) return
        val owner = act.state.objOrThrow(ids.first()).owner
        val chooser = scope.players(effect.chooser).firstOrNull() ?: scope.actor
        val order = act.order(chooser, scope.self, ids, "Order, first on top")
        val library = act.state.player(owner).library
        val positions = library.indices.filter { library[it] in ids }
        val after = library.toMutableList()
        positions.forEachIndexed { i, pos -> after[pos] = order[i] }
        if (after != library) act.record(Change.Reorder(Zone.Library, owner, library, after))
    }

    private fun revealUntil(scope: Scope, effect: Effect.RevealUntil) {
        val act = scope.act
        val owner = scope.players(effect.library).firstOrNull() ?: return
        val library = act.state.player(owner).library
        act.fill(owner, scope.self, library, "reveal until")
        val e = scope.eval()
        val wanted = scope.value(effect.count)
        val turned = mutableListOf<ObjectId>()
        val found = mutableListOf<ObjectId>()
        for (id in library) {
            if (found.size >= wanted) break
            turned += id
            if (e.matches(effect.filter, id)) found += id
        }
        if (effect.reveal && turned.isNotEmpty()) act.record(Change.Happened(GameEvent.Revealed(owner, turned)))
        val rest = turned.filter { it !in found }
        if (effect.into != null) {
            val moved = Actions.moveTo(act, scope.cause, turned, effect.into!!)
            scope.bind(effect.foundBind, NamedValue.Objects(found.mapNotNull { moved[it] }))
            scope.bind(effect.restBind, NamedValue.Objects(rest.mapNotNull { moved[it] }))
        } else {
            scope.bind(effect.foundBind, NamedValue.Objects(found))
            scope.bind(effect.restBind, NamedValue.Objects(rest))
        }
    }

    // ---- Counters

    private fun putCounters(scope: Scope, effect: Effect.PutCounters) {
        val act = scope.act
        val ids = scope.objects(effect.objects).filter { act.state.obj(it) != null }.distinct()
        val divided = scope.cause.item?.divided.orEmpty()
        if (effect.distributed && divided.isEmpty() && ids.size > 1) {
            // CR 608.2d: distributed as the controller chooses, at least one each
            val total = scope.value(effect.amount)
            val shares = (act.asker.ask { id ->
                arcanum.engine.decision.Decision.Divide(id, scope.you, scope.self, total, ids.map { TargetRef.Object(it) }, 1)
            } as Answer.Division).shares
            shares.forEach { s -> Actions.putCounters(act, scope.cause, s.target, effect.kind, s.amount) }
            return
        }
        ids.forEach { id ->
            val share = divided.firstOrNull { it.target == TargetRef.Object(id) }?.amount
            Actions.putCounters(act, scope.cause, TargetRef.Object(id), effect.kind, scope.eval(dividedShare = share).value(effect.amount))
        }
    }

    // ---- Tokens, copies, emblems

    private fun createToken(scope: Scope, effect: Effect.CreateToken) {
        val act = scope.act
        val e = scope.eval()
        val face = Actions.tokenFace(act, e, effect.token)
        val count = e.value(effect.count)
        val attachTo = effect.attachedTo?.let { recipients(scope, it).firstOrNull() }
        val created = scope.players(effect.controller).flatMap { p ->
            Actions.createTokens(act, scope.cause, face, count, p, effect.tapped, attachTo)
        }
        effect.attacking?.let { target ->
            val to = attackTarget(scope, target)
            val combat = act.state.combat
            if (to != null && combat != null) {
                act.record(Change.Combat(combat, combat.copy(attackers = combat.attackers + created.map { arcanum.engine.state.Attacker(it, to) })))
            }
        }
        scope.bind(effect.bind, NamedValue.Objects(created))
    }

    /** CR 707.10: a copy of a spell or ability is put on the Stack (not cast); it may get new targets. */
    private fun copySpell(scope: Scope, effect: Effect.CopySpell) {
        val act = scope.act
        val controller = scope.players(effect.controller).firstOrNull() ?: scope.you
        val copies = mutableListOf<ObjectId>()
        scope.objects(effect.objects).forEach { id ->
            val original = act.state.stackItems[id] ?: return@forEach
            val ts = act.state.nextTimestamp()
            val copyId = ObjectId.StackItem("copy:$ts")
            var targets = original.targets
            if (effect.newTargets) {
                val instructions = arcanum.engine.action.Replacements.instructionsOf(original.ability)
                val slots = instructions?.targets.orEmpty() + original.modes.flatMap { m -> instructions?.modes?.options?.getOrNull(m)?.instructions?.targets.orEmpty() }
                targets = targets.mapValues { (slot, chosen) ->
                    val s = slots.firstOrNull { it.name == slot } ?: return@mapValues chosen
                    val options = Targeting.options(act, s, controller, original.source, original.names, self = copyId)
                    if (options.size <= 1 || !act.yesNo(controller, copyId, "Choose new targets for the copy?")) chosen
                    else act.chooseTargets(controller, copyId, slot, options, chosen.size, chosen.size)
                }
            }
            val kind = if (original.kind == StackItemKind.Spell || original.kind is StackItemKind.SpellCopy) StackItemKind.SpellCopy(
                (original.kind as? StackItemKind.SpellCopy)?.copied ?: id) else original.kind
            val snapshots = original.snapshots + listOfNotNull(act.snapshot(id))
            val copy = original.copy(id = copyId, kind = kind, controller = controller, targets = targets, snapshots = snapshots, timestamp = ts)
            act.record(Change.StackItemAdded(copy, act.state.stack.size))
            act.record(Change.Happened(GameEvent.Copied(controller, copyId)))
            copies += copyId
        }
        scope.bind(effect.bind, NamedValue.Objects(copies))
    }

    /** CR 701.6: a countered spell goes to its owner's graveyard; an ability or copy just leaves the Stack. Spells that
     * can't be countered aren't (CR 701.6b). */
    fun counter(scope: Scope, ids: List<ObjectId>) {
        val act = scope.act
        ids.forEach { id ->
            val item = act.state.stackItems[id] ?: return@forEach
            if (act.view()[id]?.has { it == Mechanic.CantBeCountered } == true) return@forEach
            val index = act.state.stack.indexOf(id).takeIf { act.state.obj(id) == null }
            act.record(Change.StackItemRemoved(item, index, listOf(Meaning.Countered)))
            if (act.state.obj(id) != null) Actions.moveTo(act, scope.cause, listOf(id), Zone.Graveyard, listOf(Meaning.Countered))
        }
    }

    private fun createEmblem(scope: Scope, player: PlayerId, abilities: List<arcanum.api.Ability>) {
        val act = scope.act
        val ts = act.state.nextTimestamp()
        val o = GameObject(
            id = ObjectId.Card(TokenIds.token("${scope.cause.key}@$ts/emblem", 0), 0), owner = player, controller = player,
            zone = Zone.Command, definition = CardDefinition(Layout.Normal, listOf(FaceDefinition("Emblem", abilities = abilities))),
            token = false, timestamp = ts,
        )
        act.record(Change.Created(listOf(Placed(o, act.state.command.size)), emptyList()))
    }

    // ---- Mana (CR 106)

    private fun addMana(scope: Scope, player: PlayerId, production: arcanum.api.ManaProduction) {
        val act = scope.act
        val e = scope.eval()
        val colors: List<Color?> = when (val kind = production.kind) {
            is ManaKind.Fixed -> {
                val one = kind.mana.symbols.flatMap { symbol ->
                    when (symbol) {
                        is ManaSymbol.Colored -> listOf(symbol.color)
                        is ManaSymbol.Generic -> List(symbol.amount) { null }
                        ManaSymbol.Colorless -> listOf(null)
                        else -> emptyList()
                    }
                }
                val times = kind.times?.let { e.value(it) } ?: 1
                List(times) { one }.flatten()
            }
            is ManaKind.Choose -> {
                val among = kind.colors?.flatMap { e.colors(it) }?.distinct()?.ifEmpty { null } ?: Color.all
                val amount = e.value(kind.amount)
                if (kind.combination) List(amount) { act.chooseColor(player, scope.self, among) }
                else act.chooseColor(player, scope.self, among).let { c -> List(amount) { c } }
            }
            is ManaKind.SameAsProduced, is ManaKind.AsCouldProduce -> {
                val amount = e.value(if (kind is ManaKind.SameAsProduced) kind.amount else (kind as ManaKind.AsCouldProduce).amount)
                act.chooseColor(player, scope.self, Color.all).let { c -> List(amount) { c } }
            }
        }
        if (colors.isEmpty()) return
        val units = colors.map { ManaUnit(it, scope.self, restriction = production.restriction, spellGets = production.spellGets,
            keptUntil = production.keptUntil, whenSpent = production.whenSpent) }
        act.record(Changes.manaPool(act.state, player, act.state.player(player).manaPool + units, listOf(Meaning.ManaAdded)))
    }

    // ---- Continuous effects (CR 611)

    /**
     * Creates an effect object (CR 611.2, D19): characteristic and control changes lock their affected objects now (CR
     * 611.2c); numbers that don't depend on the affected object are fixed now (CR 608.2h); the names used are frozen (D37).
     */
    fun applyMechanics(
        scope: Scope, mechanics: List<Mechanic>, affects: Affected?, duration: Duration, uses: Int?,
        locked: List<ObjectId>? = null, amountLeft: Int? = null, extra: Map<String, NamedValue> = emptyMap(),
    ) {
        val act = scope.act
        val e = scope.eval()
        val fixed = mechanics.map { fix(e, it) }
        val layered = fixed.any { it.layer != null }
        var names = extra.entries.fold(scope.names) { n, (k, v) -> n.with(k, v) }
        // Objects named by the effect itself: its rules that aren't layer effects find them through a frozen name
        var affected = affects
        if (locked != null && affects == null) {
            names = names.with(LOCKED, NamedValue.Objects(locked))
            affected = Affected.Objects(Objects.Bound(LOCKED))
        }
        val lockedObjects = locked ?: if (layered && affects is Affected.Objects) e.objects(affects.objects) else null
        val lockedPlayers = (affects as? Affected.Players)?.let { e.players(it.players) }
        act.record(Changes.effect(act.state) { ts ->
            EffectObject(
                id = EffectId("E$ts"), mechanics = fixed, affects = affected, locked = lockedObjects, lockedPlayers = lockedPlayers,
                source = scope.self, controller = scope.you, timestamp = ts, createdTurn = act.state.turn.number, duration = duration,
                usesLeft = uses, amountLeft = amountLeft, names = names,
            )
        })
    }

    private const val LOCKED = "\$affected"


    /** Fixes a mechanic's numbers when they don't depend on each affected object (CR 608.2h, 611.2c). */
    private fun fix(e: Evaluator, mechanic: Mechanic): Mechanic = when (mechanic) {
        is Mechanic.ModifyPowerToughness -> Mechanic.ModifyPowerToughness(fixValue(e, mechanic.power), fixValue(e, mechanic.toughness))
        is Mechanic.SetPowerToughness -> Mechanic.SetPowerToughness(mechanic.power?.let { fixValue(e, it) }, mechanic.toughness?.let { fixValue(e, it) })
        // "Protection from the color of your choice": the colour chosen now (CR 608.2h), not looked up later
        is Mechanic.GainAbilities -> Mechanic.GainAbilities(mechanic.abilities.map { a ->
            if (a is arcanum.api.Keyword.Protection) arcanum.api.Keyword.Protection(fixColors(e, a.from)) else a
        })
        else -> mechanic
    }

    private fun fixColors(e: Evaluator, filter: ObjectFilter): ObjectFilter = when (filter) {
        is ObjectFilter.OfColor -> if (filter.color !is arcanum.api.ColorRef.Chosen) filter else {
            val colors = e.colors(filter.color).map { ObjectFilter.OfColor(arcanum.api.ColorRef.Literal(it)) }
            colors.singleOrNull() ?: ObjectFilter.Or(colors)
        }
        is ObjectFilter.And -> ObjectFilter.And(filter.all.map { fixColors(e, it) })
        is ObjectFilter.Or -> ObjectFilter.Or(filter.any.map { fixColors(e, it) })
        is ObjectFilter.Not -> ObjectFilter.Not(fixColors(e, filter.filter))
        else -> filter
    }

    private fun fixValue(e: Evaluator, value: Value): Value =
        if (value is Value.Fixed || mentionsAffected(value)) value else Value.Fixed(e.value(value))

    private fun mentionsAffected(value: Value): Boolean = value.toString().contains("Affected")

    /** "Prevent the next N damage" (CR 615.7, D26): a shield effect object whose amount goes down as it prevents. */
    private fun preventDamage(scope: Scope, effect: Effect.PreventDamage) {
        val targets = recipients(scope, effect.to)
        val objects = targets.filterIsInstance<TargetRef.Object>().map { it.id }
        val players = targets.filterIsInstance<TargetRef.Player>().map { it.id }
        val saved = scope.names
        scope.names = scope.names.with(SHIELDED, NamedValue.Objects(objects)).with(SHIELDED_PLAYERS, NamedValue.Players(players))
        val pattern = EventPattern.Damage(effect.from, RecipientFilter(
            objects = ObjectFilter.Is(Objects.Bound(SHIELDED)), players = PlayerFilter.Is(Players.Bound(SHIELDED_PLAYERS))))
        applyMechanics(scope, listOf(Mechanic.Prevention(pattern)), null, effect.duration, null, amountLeft = scope.value(effect.amount))
        scope.names = saved
    }

    private const val SHIELDED = "\$shielded"
    private const val SHIELDED_PLAYERS = "\$shielded-players"

    /** CR 701.19a: "The next time it would be destroyed this turn, instead remove all damage marked on it and its
     * controller taps it. If it's an attacking or blocking creature, remove it from combat." (D26) */
    private fun regenerate(scope: Scope, ids: List<ObjectId>) {
        ids.forEach { id ->
            val saved = scope.names
            scope.names = scope.names.with(Actions.REGENERATED, NamedValue.Objects(listOf(id)))
            val it = Objects.Bound(Actions.REGENERATED)
            applyMechanics(scope, listOf(Mechanic.Replacement(EventPattern.Destroyed(ObjectFilter.Is(it)), instead = Instructions(steps = listOf(
                Step.Do(Effect.RemoveAllDamage(it)), Step.Do(Effect.Tap(it)), Step.Do(Effect.RemoveFromCombat(it)))))),
                null, Duration.EndOfTurn, uses = 1)
            scope.names = saved
        }
    }

    private fun createDelayed(
        scope: Scope, trigger: Trigger, instructions: Instructions, duration: Duration?, repeats: Boolean,
        extra: Map<String, NamedValue> = emptyMap(),
    ) {
        val act = scope.act
        val names = extra.entries.fold(scope.names) { n, (k, v) -> n.with(k, v) }
        // CR 603.7c: the objects it refers to are fixed now
        val refs = names.values.values.flatMap { v ->
            when (v) {
                is NamedValue.Objects -> v.ids
                is NamedValue.Targets -> v.refs.filterIsInstance<TargetRef.Object>().map { it.id }
                else -> emptyList()
            }
        }.distinct()
        val ts = act.state.nextTimestamp()
        act.record(Change.DelayedTriggerAdded(DelayedTrigger(
            id = EffectId("D$ts"), trigger = trigger, instructions = instructions, duration = duration, repeats = repeats,
            source = scope.self, controller = scope.you, timestamp = ts, createdTurn = act.state.turn.number, names = names,
            snapshots = refs.mapNotNull { act.snapshot(it) },
        )))
    }

    private fun removeFromCombat(scope: Scope, ids: List<ObjectId>) {
        val act = scope.act
        val combat = act.state.combat ?: return
        val gone = ids.toSet()
        val after = combat.copy(
            attackers = combat.attackers.filter { it.id !in gone },
            blockers = combat.blockers.filter { it.id !in gone }.map { b -> b.copy(attackers = b.attackers.filter { it !in gone }) },
        )
        if (after != combat) act.record(Change.Combat(combat, after))
    }

    /** CR 701.12: nothing happens unless both can be exchanged; each gets a control effect for good. */
    private fun exchangeControl(scope: Scope, effect: Effect.ExchangeControl) {
        val a = onBattlefield(scope, effect.first).firstOrNull() ?: return
        val b = onBattlefield(scope, effect.second).firstOrNull() ?: return
        val ca = scope.act.controllerOf(a) ?: return
        val cb = scope.act.controllerOf(b) ?: return
        if (ca == cb) return
        applyMechanics(scope, listOf(Mechanic.ControlledBy(Players.Bound(EXCHANGE))), null, Duration.Indefinitely, null, locked = listOf(a),
            extra = mapOf(EXCHANGE to NamedValue.Players(listOf(cb))))
        applyMechanics(scope, listOf(Mechanic.ControlledBy(Players.Bound(EXCHANGE))), null, Duration.Indefinitely, null, locked = listOf(b),
            extra = mapOf(EXCHANGE to NamedValue.Players(listOf(ca))))
    }

    private const val EXCHANGE = "\$new-controller"

    /** CR 115.7: new targets for a spell or ability on the Stack. */
    private fun changeTargets(scope: Scope, effect: Effect.ChangeTargets) {
        val act = scope.act
        val chooser = scope.players(effect.chooser).firstOrNull() ?: scope.actor
        scope.objects(effect.objects).forEach { id ->
            val item = act.state.stackItems[id] ?: return@forEach
            val instructions = arcanum.engine.action.Replacements.instructionsOf(item.ability) ?: return@forEach
            val slots = instructions.targets + item.modes.flatMap { m -> instructions.modes?.options?.getOrNull(m)?.instructions?.targets.orEmpty() }
            val fixed = effect.to?.let { scope.objects(it).firstOrNull() }
            val targets = item.targets.mapValues { (slot, chosen) ->
                val s = slots.firstOrNull { it.name == slot } ?: return@mapValues chosen
                if (fixed != null) {
                    val ref = TargetRef.Object(fixed)
                    if (Targeting.legal(act, s, ref, item.controller, item.source, item.names)) listOf(ref) else chosen
                } else {
                    val options = Targeting.options(act, s, item.controller, item.source, item.names, self = id)
                    if (options.isEmpty() || (effect.optional && !act.yesNo(chooser, id, "Choose new targets?"))) chosen
                    else act.chooseTargets(chooser, id, slot, options, chosen.size, chosen.size)
                }
            }
            if (targets != item.targets) act.record(Change.StackItemUpdated(item, item.copy(targets = targets)))
        }
    }

    private fun separatePiles(scope: Scope, effect: Effect.SeparatePiles) {
        val act = scope.act
        val ids = scope.objects(effect.objects)
        val separator = scope.players(effect.separator).firstOrNull() ?: scope.actor
        val chooser = scope.players(effect.chooser).firstOrNull() ?: scope.actor
        act.fill(separator, scope.self, ids, "separate into piles")
        val first = act.chooseObjects(separator, scope.self, ids, 0, ids.size, "First pile")
        val second = ids.filter { it !in first }
        val chosen = act.chooseOption(chooser, scope.self, listOf("first", "second"))
        scope.bind(effect.chosenBind, NamedValue.Objects(if (chosen == "first") first else second))
        scope.bind(effect.otherBind, NamedValue.Objects(if (chosen == "first") second else first))
    }

    /** Card memory of the source (CR 607.2a): adds up unless replaced. */
    private fun remember(scope: Scope, key: String, ids: List<ObjectId>, replace: Boolean) {
        val act = scope.act
        val self = scope.self ?: return
        val o = act.state.obj(self) ?: return
        val before = o.memory[key]
        val old = (before as? NamedValue.Objects)?.ids.orEmpty()
        val after = NamedValue.Objects(if (replace) ids else (old + ids).distinct())
        if (after != before) act.record(Change.Memory(self, key, before, after))
    }

    private fun forget(scope: Scope, key: String, ids: List<ObjectId>?) {
        val act = scope.act
        val self = scope.self ?: return
        val o = act.state.obj(self) ?: return
        val before = o.memory[key] ?: return
        val after = if (ids == null) null else (before as? NamedValue.Objects)?.let { NamedValue.Objects(it.ids - ids.toSet()) }
        act.record(Change.Memory(self, key, before, after))
    }

    /** CR 725.1, 726.1: one monarch, one player with the initiative. */
    private fun designate(scope: Scope, player: PlayerId, designation: Designation) {
        val act = scope.act
        act.state.players.filter { it.id != player && designation in it.designations }.forEach {
            act.record(Change.PlayerDesignation(it.id, designation, true, false))
        }
        if (designation !in act.state.player(player).designations) act.record(Change.PlayerDesignation(player, designation, false, true))
    }

    // ---- Keyword actions (CR 701)

    private fun keywordAction(scope: Scope, action: KeywordAction, bind: String?) {
        val act = scope.act
        KeywordExpansions.expand(action)?.let { steps -> return Steps.run(scope, steps) }
        val cause = scope.cause
        when (action) {
            // CR 701.27a: choose any number of permanents and/or players with counters; give each another of each kind
            KeywordAction.Proliferate -> {
                val objects = act.state.battlefield.filter { act.state.obj(it)!!.counters.isNotEmpty() }
                val players = act.state.apnap().filter { act.state.player(it).counters.isNotEmpty() }
                val options = objects.map { TargetRef.Object(it) } + players.map { TargetRef.Player(it) }
                if (options.isEmpty()) return
                val chosen = act.chooseTargets(scope.actor, scope.self, "proliferate", options, 0, options.size)
                chosen.forEach { ref ->
                    val kinds = when (ref) {
                        is TargetRef.Object -> act.state.objOrThrow(ref.id).counters.map { it.kind }
                        is TargetRef.Player -> act.state.player(ref.id).counters.map { it.kind }
                    }
                    kinds.forEach { Actions.putCounters(act, cause, ref, it, 1) }
                }
            }
            // CR 701.36a: choose a creature token you control and create a copy of it
            KeywordAction.Populate -> {
                val e = scope.eval()
                val tokens = act.state.battlefield.filter { act.state.obj(it)!!.token && e.controllerOf(it) == scope.actor &&
                    e.characteristics(it)?.isType(CardType.Creature) == true }
                val chosen = act.chooseObjects(scope.actor, scope.self, tokens, 1, 1, "Populate").firstOrNull() ?: return
                scope.bind(POPULATED, NamedValue.Objects(listOf(chosen)))
                val face = Actions.tokenFace(act, scope.eval(), arcanum.api.TokenSpec.CopyOf(Objects.Bound(POPULATED)))
                scope.bind(bind, NamedValue.Objects(Actions.createTokens(act, cause, face, 1, scope.actor)))
            }
            // CR 701.44a: reveal the top card; a land goes to hand, otherwise a +1/+1 counter and you may put it in the graveyard
            is KeywordAction.Explore -> onBattlefield(scope, action.objects).forEach { explorer ->
                val controller = act.controllerOf(explorer) ?: return@forEach
                val top = act.state.player(controller).library.firstOrNull()
                if (top == null) { Actions.putCounters(act, cause, TargetRef.Object(explorer), CounterKind.PowerToughness(1, 1), 1); return@forEach }
                act.fill(controller, explorer, listOf(top), "explore")
                act.record(Change.Happened(GameEvent.Revealed(controller, listOf(top))))
                if (scope.eval().characteristics(top)?.isType(CardType.Land) == true) Actions.moveTo(act, cause, listOf(top), Zone.Hand)
                else {
                    Actions.putCounters(act, cause, TargetRef.Object(explorer), CounterKind.PowerToughness(1, 1), 1)
                    if (act.yesNo(controller, explorer, "Put it into your graveyard?")) Actions.moveTo(act, cause, listOf(top), Zone.Graveyard)
                }
            }
            // CR 701.47a: put counters on an Army you control, or create a 0/0 Army token first
            is KeywordAction.Amass -> {
                val e = scope.eval()
                val armies = act.state.battlefield.filter { e.controllerOf(it) == scope.actor &&
                    e.characteristics(it)?.hasSubtype(arcanum.api.Subtype("Army")) == true }
                val army = if (armies.isEmpty()) Actions.createTokens(act, cause, FaceDefinition("${action.subtype.name} Army Token",
                    colorIndicator = listOf(Color.Black), types = listOf(CardType.Creature),
                    subtypes = listOf(action.subtype, arcanum.api.Subtype("Army")), power = arcanum.api.PrintedNumber.Fixed(0),
                    toughness = arcanum.api.PrintedNumber.Fixed(0)), 1, scope.actor).first()
                else act.chooseObjects(scope.actor, scope.self, armies, 1, 1, "Amass").first()
                Actions.putCounters(act, cause, TargetRef.Object(army), CounterKind.PowerToughness(1, 1), e.value(action.count))
                scope.bind(bind, NamedValue.Objects(listOf(army)))
            }
            is KeywordAction.Goad -> onBattlefield(scope, action.objects).forEach { id ->
                val o = act.state.objOrThrow(id)
                if (scope.you !in o.goadedBy) act.record(Change.ObjectUpdated(o, o.copy(goadedBy = o.goadedBy + scope.you)))
            }
            // CR 701.37a: if it isn't monstrous, put N +1/+1 counters on it and it becomes monstrous
            is KeywordAction.Monstrosity -> scope.self?.takeIf { act.state.obj(it)?.zone == Zone.Battlefield }?.let { id ->
                if (ObjectMark.Monstrous in act.state.objOrThrow(id).marks) return
                Actions.putCounters(act, cause, TargetRef.Object(id), CounterKind.PowerToughness(1, 1), scope.value(action.count))
                Actions.tapStatus(act, id, StatusFlag.Marked(ObjectMark.Monstrous), true)
            }
            // CR 701.46a: if it has no +1/+1 counters, put N on it
            is KeywordAction.Adapt -> scope.self?.takeIf { act.state.obj(it)?.zone == Zone.Battlefield }?.let { id ->
                if (act.state.objOrThrow(id).counters(CounterKind.PowerToughness(1, 1)) == 0)
                    Actions.putCounters(act, cause, TargetRef.Object(id), CounterKind.PowerToughness(1, 1), scope.value(action.count))
            }
            is KeywordAction.Double -> when (val what = action.what) {
                is arcanum.api.Doubled.Counters -> onBattlefield(scope, what.objects).forEach { id ->
                    act.state.objOrThrow(id).counters.filter { what.kind == null || it.kind == what.kind }.forEach { c ->
                        Actions.putCounters(act, cause, TargetRef.Object(id), c.kind, c.count)
                    }
                }
                is arcanum.api.Doubled.Life -> scope.players(what.players).forEach { Actions.gainLife(act, cause, it, act.state.player(it).life) }
                is arcanum.api.Doubled.Power -> onBattlefield(scope, what.objects).forEach { id ->
                    val power = scope.eval().property(arcanum.api.NumericProperty.Power, id)
                    applyMechanics(scope, listOf(Mechanic.ModifyPowerToughness(Value.Fixed(power), Value.Fixed(0))), null, Duration.EndOfTurn, null, locked = listOf(id))
                }
                is arcanum.api.Doubled.Mana -> act.manual(scope.actor, scope.self, "Double the mana in your mana pool")
            }
            is KeywordAction.Regenerate -> regenerate(scope, onBattlefield(scope, action.objects))
            // CR 701.43: it won't untap during its controller's next untap step
            is KeywordAction.Exert -> onBattlefield(scope, action.objects).forEach { id ->
                val controller = act.controllerOf(id) ?: return@forEach
                scope.bind(EXERTED, NamedValue.Players(listOf(controller)))
                applyMechanics(scope, listOf(Mechanic.DoesntUntap), null,
                    Duration.ThroughNextStep(arcanum.api.TurnStep.Untap, Players.Bound(EXERTED)), null, locked = listOf(id))
            }
            // CR 701.35a: tapped down; can't attack or block and its activated abilities can't be activated until your next turn
            is KeywordAction.Detain -> onBattlefield(scope, action.objects).let { ids ->
                if (ids.isNotEmpty()) applyMechanics(scope, listOf(Mechanic.CantAttack(), Mechanic.CantBlock(ObjectFilter.Any),
                    Mechanic.CantBeActivated(manaAbilities = true)), null, Duration.UntilYourNextTurn, null, locked = ids)
            }
            // CR 701.39b: put a +1/+1 counter on each of up to N other target creatures
            is KeywordAction.Support -> act.manual(scope.actor, scope.self, "Support ${scope.value(action.count)}")
            else -> act.manual(scope.actor, scope.self, "Do by hand: ${action::class.simpleName}")
        }
    }

    private const val POPULATED = "\$populated"
    private const val EXERTED = "\$exerted"
}

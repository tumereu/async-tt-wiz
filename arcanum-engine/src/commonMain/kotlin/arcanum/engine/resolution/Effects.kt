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
                    val result = Actions.damage(act, cause, listOf(
                        DamageDealt(a, TargetRef.Object(b), e.property(arcanum.api.NumericProperty.Power, a)),
                        DamageDealt(b, TargetRef.Object(a), e.property(arcanum.api.NumericProperty.Power, b)),
                    ))
                    // tla: the damage the first dealt to the second, and its excess (CR 120.4a)
                    effect.bind?.let { bind ->
                        scope.bind(bind, NamedValue.Number(result.dealt.filter { it.source == a && it.recipient == TargetRef.Object(b) }.sumOf { it.amount }))
                        scope.bind(Evaluator.EXCESS_PREFIX + bind, NamedValue.Number(result.excessTo[TargetRef.Object(b)] ?: 0))
                    }
                } else effect.bind?.let { bind ->
                    scope.bind(bind, NamedValue.Number(0))
                    scope.bind(Evaluator.EXCESS_PREFIX + bind, NamedValue.Number(0))
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
                act.fill(scope.actor, scope.self, ids, "revealed", public = true)
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
            // CR 122.1, 702.62a: counters come off objects in any zone (time counters on a suspended card in exile)
            is Effect.RemoveCounters -> {
                var removed = 0
                scope.objects(effect.objects).distinct().filter { act.state.obj(it) != null }.forEach { id ->
                    val n = scope.value(effect.amount)
                    val kinds = effect.kind?.let { listOf(it) } ?: act.state.objOrThrow(id).counters.map { it.kind }
                    var left = n
                    kinds.forEach { k -> if (left > 0) Actions.removeCounters(act, TargetRef.Object(id), k, left).let { left -= it; removed += it } }
                }
                // gtc: "Draw that many cards": the number actually removed
                effect.bind?.let { scope.bind(it, NamedValue.Number(removed)) }
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
                // msh: a modal double-faced permanent transforms too (CR 701.27a), not into an instant or sorcery face (701.27d)
                val layout = o.definition?.layout
                if (layout != Layout.Transform && layout != Layout.ModalDoubleFaced) return@forEach
                val other = o.definition!!.faces.getOrNull(1 - o.face) ?: return@forEach
                if (layout == Layout.ModalDoubleFaced && (arcanum.api.CardType.Instant in other.types || arcanum.api.CardType.Sorcery in other.types)) return@forEach
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
                if (act.view()[id]?.has { it == Mechanic.CantBeTurnedFaceUp } == true) return@forEach
                // CR 701.40a: no longer a manifested permanent once face up
                if (ObjectMark.Manifested in o.marks) Actions.tapStatus(act, id, StatusFlag.Marked(ObjectMark.Manifested), false)
                // CR 708.8: turned face up, everyone sees it (D60)
                act.fill(o.controller ?: o.owner, scope.self, listOf(id), "turned face up", public = true)
                act.record(Change.Status(id, StatusFlag.FaceDown, true, false, listOf(Meaning.TurnedFaceUp)))
                act.record(Change.Restamped(id, o.timestamp, act.state.nextTimestamp()))
                effect.counters.forEach { c -> Actions.putCounters(act, cause, TargetRef.Object(id), c.kind, scope.value(c.amount)) }
            }
            is Effect.TurnFaceDown -> onBattlefield(scope, effect.objects).forEach { id ->
                val o = act.state.objOrThrow(id)
                if (o.faceDown) return@forEach
                act.record(Change.Status(id, StatusFlag.FaceDown, false, true, listOf(Meaning.TurnedFaceDown)))
                act.record(Change.Restamped(id, o.timestamp, act.state.nextTimestamp()))
                // D60: face down where the viewer can't look, it is forgotten
                act.state.objOrThrow(id).let { now -> if (arcanum.engine.action.Knowledge.forgets(act.state, now)) act.record(Change.Forgotten(now, arcanum.engine.action.Knowledge.forgotten(now))) }
            }
            is Effect.PhaseOut -> onBattlefield(scope, effect.objects).forEach {
                Actions.tapStatus(act, it, StatusFlag.PhasedOut, true, listOf(Meaning.PhasedOut))
            }
            is Effect.Mark -> onBattlefield(scope, effect.objects).forEach {
                // fra: becoming prepared or unprepared (CR 722.3a–c)
                if (effect.mark == arcanum.api.ObjectMark.Prepared) {
                    if (effect.remove) arcanum.engine.prepare.Prepare.unprepare(act, it) else arcanum.engine.prepare.Prepare.becomePrepared(act, it)
                } else Actions.tapStatus(act, it, StatusFlag.Marked(effect.mark), !effect.remove)
            }
            is Effect.GiveDesignation -> scope.players(effect.players).firstOrNull()?.let { p -> designate(scope, p, effect.designation) }
            is Effect.CreateToken -> createToken(scope, effect)
            is Effect.CopySpell -> copySpell(scope, effect)
            is Effect.CopyCard -> copyCards(scope, effect)
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
                // tla: a permanent that has left the battlefield ("when it leaves, put its counters on …"): the counters it had as
                // it last existed are put, none removed (CR 608.2h)
                val board = scope.eval().board
                scope.objects(effect.from).filter { act.state.obj(it)?.zone != Zone.Battlefield }.forEach { gone ->
                    board.snapshot(gone)?.counters.orEmpty().filter { c -> c.count > 0 && (effect.kind == null || c.kind == effect.kind) }.forEach { c ->
                        Actions.putCounters(act, cause, TargetRef.Object(to), c.kind, minOf(c.count, scope.value(effect.amount)))
                    }
                }
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
            // blb: CR 716.2a, b: the level is a designation of the permanent, kept as its memory
            is Effect.SetClassLevel -> onBattlefield(scope, effect.objects).forEach { id ->
                val o = act.state.obj(id) ?: return@forEach
                val before = o.memory[arcanum.engine.query.Evaluator.CLASS_LEVEL]
                val after = arcanum.engine.state.NamedValue.Number(effect.level)
                if (before != after) {
                    act.record(Change.Memory(id, arcanum.engine.query.Evaluator.CLASS_LEVEL, before, after))
                    act.record(Change.Happened(GameEvent.BecameLevel(id, effect.level)))
                }
            }
            is Effect.Forget -> forget(scope, effect.key, effect.objects?.let { scope.objects(it) })
            // CR 500.7: the most recently created extra turn is taken first
            is Effect.ExtraTurn -> scope.players(effect.players).forEach { p ->
                act.record(Changes.step(act.state, act.state.turn.copy(extraTurns = listOf(p) + act.state.turn.extraTurns)))
            }
            // gtc: CR 500.8: added directly after this phase, before phases added earlier
            is Effect.ExtraCombat -> {
                if (effect.untapAttackers) {
                    val attacked = scope.eval().each(arcanum.api.ObjectFilter.ThisTurn(arcanum.api.EventPattern.Attack(arcanum.api.ObjectFilter.Any)))
                    Actions.tap(act, attacked.filter { act.state.obj(it)?.zone == Zone.Battlefield }, false)
                }
                val added = listOf(arcanum.api.TurnStep.BeginningOfCombat) + if (effect.followedByMain) listOf(arcanum.api.TurnStep.PostcombatMain) else emptyList()
                act.record(Changes.step(act.state, act.state.turn.copy(extraPhases = added + act.state.turn.extraPhases)))
                // tla: "Only land creatures can attack during that combat phase": the others can't attack until the next main phase begins
                effect.onlyAttackers?.let { only ->
                    applyMechanics(scope, listOf(Mechanic.CantAttack()), arcanum.api.Affected.Objects(arcanum.api.Objects.Each(arcanum.api.ObjectFilter.Not(only))),
                        Duration.UntilStep(arcanum.api.TurnStep.PostcombatMain, arcanum.api.PlayerFilter.Any), null)
                }
            }
            // sos: each skip passes over one more of that player's turns (CR 614.10)
            is Effect.SkipNextTurn -> scope.players(effect.players).forEach { p ->
                act.record(Changes.step(act.state, act.state.turn.copy(skippedTurns = act.state.turn.skippedTurns + p)))
            }
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
                // Names of the spell also name the permanent it becomes, for the duration ("until you lose control of
                // the spell or the permanent it becomes"); the view reads them as whichever of the two exists
                val follow = if (next == null) emptyMap() else scope.names.values
                    .filterValues { it is NamedValue.Objects && id in it.ids }
                    .mapValues { (_, v) -> NamedValue.Objects((v as NamedValue.Objects).ids + next) } +
                    (FOLLOWS_SPELL to NamedValue.Objects(listOf(id, next)))
                applyMechanics(scope, effect.mechanics, null, effect.duration ?: Duration.Indefinitely, null,
                    locked = listOfNotNull(id, next), extra = follow)
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
            // CR 709.5f: the chooser picks a locked door of each (or the door named)
            is Effect.UnlockDoor -> {
                val unlocked = onBattlefield(scope, effect.objects).filter { id ->
                    val locked = arcanum.engine.room.Rooms.locked(act.state.objOrThrow(id))
                    val door = effect.door?.takeIf { it in locked } ?: when (locked.size) {
                        0 -> null
                        1 -> if (effect.door == null) locked.single() else null
                        else -> chooseDoor(scope, effect.chooser, id, locked, "Unlock which door?")
                    } ?: return@filter false
                    arcanum.engine.room.Rooms.unlock(act, id, door, scope.players(effect.chooser).firstOrNull())
                    true
                }
                scope.bind(effect.bind, NamedValue.Objects(unlocked))
            }
            // CR 709.5g
            is Effect.LockDoor -> onBattlefield(scope, effect.objects).forEach { id ->
                val o = act.state.objOrThrow(id)
                if (!arcanum.engine.room.Rooms.isRoom(o.definition)) return@forEach
                val open = listOf(arcanum.api.Door.Left, arcanum.api.Door.Right).filter { arcanum.engine.room.Rooms.unlocked(o, it) }
                val door = when (open.size) {
                    0 -> return@forEach
                    1 -> open.single()
                    else -> chooseDoor(scope, effect.chooser, id, open, "Lock which door?")
                }
                arcanum.engine.room.Rooms.lock(act, id, door)
            }
            // The counters [of] has (as it last existed), each kind and number, put on each object
            // msh: "For each kind of counter on target permanent or player, give that permanent or player another counter of that kind"
            // (as proliferate does for one chosen permanent or player, CR 701.34a)
            is Effect.OneMoreCounterOfEachKind -> Actions.recipientsOf(scope.eval(), effect.recipients).forEach { ref ->
                val kinds = when (ref) {
                    is TargetRef.Object -> act.state.obj(ref.id)?.takeIf { it.zone == Zone.Battlefield }?.counters?.map { it.kind }.orEmpty()
                    is TargetRef.Player -> act.state.player(ref.id).counters.map { it.kind }
                }
                kinds.distinct().forEach { Actions.putCounters(act, cause, ref, it, 1) }
            }
            // msh: CR 723.1: control of a player during their next turn, waiting until they actually take one; the host asks
            // Engine.decider who makes their decisions
            is Effect.ControlNextTurn -> {
                val controller = scope.players(effect.controller).firstOrNull() ?: return
                scope.players(effect.players).forEach { p ->
                    val control = arcanum.engine.state.PlayerControl(p, controller, combatOnly = effect.combatPhase)
                    val turn = act.state.turn
                    // tla: only their next combat phase (CR 723.2): this turn's when it is theirs and its combat is still to come
                    val now = effect.combatPhase && turn.active == p && turn.step in BEFORE_COMBAT
                    act.record(Changes.step(act.state, if (now) turn.copy(controlled = control) else turn.copy(controlNext = turn.controlNext + control)))
                }
            }
            is Effect.PutCountersOf -> {
                val board = scope.eval().board
                val counters = scope.objects(effect.of).flatMap { id -> act.state.obj(id)?.counters ?: board.snapshot(id)?.counters.orEmpty() }
                onBattlefield(scope, effect.objects).forEach { to ->
                    counters.forEach { c -> if (c.count > 0) Actions.putCounters(act, cause, TargetRef.Object(to), c.kind, c.count) }
                }
            }
        }
    }

    private fun chooseDoor(scope: Scope, chooser: Players, id: ObjectId, doors: List<arcanum.api.Door>, text: String): arcanum.api.Door {
        val player = scope.players(chooser).firstOrNull() ?: scope.actor
        val names = scope.act.state.objOrThrow(id).definition?.faces.orEmpty()
        val options = doors.map { names.getOrNull(arcanum.engine.room.Rooms.face(it))?.name ?: it.toString() }
        val chosen = scope.act.chooseOption(player, id, options)
        return doors[options.indexOf(chosen).coerceAtLeast(0)]
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

    // dsk gaps
    /**
     * "Copy them" (CR 707.12): a copy of each card, created in the zone the card is in (as the last object there) and
     * owned by the player instructed to create it (CR 112.2a), with the card's copiable values (CR 707.2). The copies
     * are named for the "cast the copies" after it; those not cast cease to exist when state-based actions are next
     * checked (CR 704.5e).
     */
    private fun copyCards(scope: Scope, effect: Effect.CopyCard) {
        val act = scope.act
        val owner = scope.players(effect.owner).firstOrNull() ?: scope.actor
        val creator = "${scope.cause.key}@${act.state.nextTimestamp()}/copy"
        val first = act.state.nextTimestamp()
        val copies = scope.objects(effect.objects).mapNotNull { act.state.obj(it) }.filter { !it.unknown }.mapIndexed { i, card ->
            GameObject(
                id = ObjectId.Card(TokenIds.token(creator, i), 0), owner = owner, zone = card.zone,
                // A card outside the battlefield and the Stack has its printed copiable values
                definition = card.definition, copyOfCard = true, timestamp = first + i,
            )
        }
        if (copies.isNotEmpty()) {
            val placed = copies.groupBy { it.zone to it.owner }.flatMap { (where, group) ->
                val start = act.state.zone(where.first, where.second).size
                group.mapIndexed { i, o -> Placed(o, start + i) }
            }
            act.record(Change.Created(placed, listOf(Meaning.CardCopied)))
        }
        scope.bind(effect.bind, NamedValue.Objects(copies.map { it.id }))
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
        var newIds: List<ObjectId>? = null
        if (dest.randomOrder && ids.size > 1 && act.state.viewer != null && dest.zone == Zone.Library) {
            // D60: in a viewer's state the random order is the host's secret: the cards go in under new ids, unknown
            newIds = act.randomObjects(scope.actor, scope.self, ids, ids.size, newIds = true)
        } else if (dest.randomOrder && ids.size > 1) {
            ordered = act.randomObjects(scope.actor, scope.self, ids, ids.size)
        } else if (dest.zone == Zone.Library && dest.libraryPosition != null && ids.size > 1) {
            // CR 401.4: cards put into one position of a library at once are arranged by their owner (first: topmost);
            // each move goes to the bottom below the last, or to the top above it
            ordered = ids.groupBy { act.state.obj(it)!!.owner }.flatMap { (owner, cards) ->
                val chosen = act.order(owner, scope.self, cards, "Order, first on top")
                if (dest.libraryPosition!! < 0) chosen else chosen.reversed()
            }
        }
        val position = dest.libraryPosition?.let { Actions.libraryPosition(it) }
        val moves = ordered.mapIndexed { i, id ->
            val from = act.state.obj(id)!!.zone
            PlannedMove(
                // ecl: "under their owners' control": each card under its own owner's (CR 110.2)
                id, dest.zone, position, if (dest.zone != Zone.Battlefield) null else (dest.controller as? arcanum.api.Players.OwnerOf)
                    ?.takeIf { id in e.objects(it.objects) }?.let { act.state.obj(id)?.owner } ?: controller,
                tapped = dest.tapped, faceDown = dest.faceDown, counters = counters, meanings = meaningsOf(from, dest.zone),
                attachTo = attachTo, transformed = dest.transformed, attacking = attacking,
                newId = newIds?.getOrNull(i) as ObjectId.Card?,
                entersAs = if (dest.zone == Zone.Battlefield) dest.entersAs else emptyList(),
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
            // CR 610.3: when that object leaves the battlefield, a second one-shot effect returns the cards right after
            // that event, without the Stack (kept as a one-shot delayed trigger, D19)
            val leaving = scope.objects(until)
            if (after.isNotEmpty()) createDelayed(scope,
                Trigger.On(EventPattern.ZoneChange(ObjectFilter.Is(Objects.Bound(UNTIL_SOURCE)), from = Zone.Battlefield)),
                Instructions(steps = listOf(Step.Do(Effect.Move(Objects.Bound(UNTIL_RETURNED), arcanum.api.Destination(
                    from ?: Zone.Battlefield,
                    controller = Players.OwnerOf(Objects.Bound(UNTIL_RETURNED))))))),
                duration = null, repeats = false,
                extra = mapOf(UNTIL_SOURCE to NamedValue.Objects(leaving), UNTIL_RETURNED to NamedValue.Objects(after)), oneShot = true)
        }
    }

    private const val UNTIL_SOURCE = "\$until-source"
    private const val UNTIL_RETURNED = "\$until-returned"

    private fun attackTarget(scope: Scope, target: arcanum.api.AttackTarget): TargetRef? = when (target) {
        arcanum.api.AttackTarget.Chosen -> scope.act.state.opponents(scope.actor).firstOrNull()?.let { TargetRef.Player(it) }
        is arcanum.api.AttackTarget.SameAs -> {
            arcanum.engine.combat.Combat.lastTarget(scope.act.state, scope.objects(target.objects))
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
                // I-D18: "discard a creature card" decided by cards only the player sees
                val hidden = act.hiddenDecides(p, scope.self, act.state.player(p).hand) { e.identityDecides(effect.filter, it) }
                val options = act.state.player(p).hand.filter { e.matches(effect.filter, it) }
                act.chooseObjects(p, scope.self, options, n, n, "Discard", hidden)
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
        val chosen = act.chooseObjects(player, scope.self, top, 0, top.size, "Put on the bottom")
        val kept = act.order(player, scope.self, top.filter { it !in chosen }, "Order on top, first on top")
        val bottom = act.order(player, scope.self, chosen, "Order on the bottom, first on top")
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
        val kept = top.filter { it !in toGraveyard }
        val ordered = act.order(player, scope.self, kept, "Order on top, first on top")
        val library = act.state.player(player).library
        if (ordered != kept && library.take(kept.size) == kept) {
            act.record(Change.Reorder(Zone.Library, player, library, ordered + library.drop(kept.size)))
        }
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
            val chosen = act.chooseObjects(searcher, scope.self, options, 0, max,
                arcanum.engine.decision.Texts.sentence(scope.cause.item?.ability, "search", "Search"))
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
        val wanted = scope.value(effect.count)
        // sos: "until you exile cards with total mana value 4 or greater": until the matching cards' total reaches it
        val total = effect.totalAtLeast?.let { scope.value(it.atLeast) }
        var sum = 0
        val turned = mutableListOf<ObjectId>()
        val found = mutableListOf<ObjectId>()
        for (id in library) {
            if (total == null && found.size >= wanted) break
            if (total != null && sum >= total) break
            // D60: each card is asked for as it is turned over, so the cards below the last one stay unknown
            act.fill(owner, scope.self, listOf(id), if (effect.reveal) "revealed" else "looked at", public = effect.reveal)
            turned += id
            if (scope.eval().matches(effect.filter, id)) {
                found += id
                effect.totalAtLeast?.let { t -> sum += scope.eval().property(t.property, id) }
            }
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
            // sos: "This spell can't be copied." (CR 707.10): no copy is created
            if (act.view()[id]?.has { it == Mechanic.CantBeCopied } == true) return@forEach
            val ts = act.state.nextTimestamp()
            val copyId = ObjectId.StackItem("copy:$ts")
            var targets = original.targets
            if (effect.newTargets) {
                val instructions = arcanum.engine.action.Replacements.instructionsOf(original.ability)
                val slots = instructions?.targets.orEmpty() + original.modes.flatMap { m -> instructions?.modes?.options?.getOrNull(m)?.instructions?.targets.orEmpty() }
                targets = targets.mapValues { (slot, chosen) ->
                    val s = slots.firstOrNull { it.name == slot } ?: return@mapValues chosen
                    val options = Targeting.options(act, s, controller, original.source, original.names, self = copyId)
                    // Asked only when another target could be chosen (a single other legal target counts)
                    if (options.all { it in chosen } || !act.yesNo(controller, copyId, "Choose new targets for the copy?")) chosen
                    else act.chooseTargets(controller, copyId, slot, options, chosen.size, chosen.size)
                }
            }
            val kind = if (original.kind == StackItemKind.Spell || original.kind is StackItemKind.SpellCopy) StackItemKind.SpellCopy(
                (original.kind as? StackItemKind.SpellCopy)?.copied ?: id) else original.kind
            val snapshots = original.snapshots + listOfNotNull(act.snapshot(id))
            val copy = original.copy(id = copyId, kind = kind, controller = controller, targets = targets, snapshots = snapshots, timestamp = ts,
                facts = if (kind is StackItemKind.SpellCopy) copiedFacts(original.facts ?: act.state.obj(id)?.facts) else original.facts)
            act.record(Change.StackItemAdded(copy, act.state.stack.size))
            act.record(Change.Happened(GameEvent.Copied(controller, copyId)))
            copies += copyId
        }
        scope.bind(effect.bind, NamedValue.Objects(copies))
    }

    /** CR 707.10: a copy of a spell copies X, the modes chosen and the additional and alternative costs paid (and the
     * objects used to pay them); it isn't cast, so it wasn't cast from anywhere and no mana was spent to cast it (the
     * Dawnglow Infusion example). */
    private fun copiedFacts(of: arcanum.engine.state.ObjectFacts?) = arcanum.engine.state.ObjectFacts(
        x = of?.x, paid = of?.paid.orEmpty(), alternative = of?.alternative, modesChosen = of?.modesChosen.orEmpty(),
        paidWith = of?.paidWith ?: arcanum.engine.state.Bindings.EMPTY,
    )

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
                // dgm: "one mana of any type that land produced" (CR 106.7): the types the tapped permanent just added
                val tapped = scope.event?.objects?.firstOrNull { it.role == arcanum.api.EventRole.Object }?.ids?.firstOrNull()
                val produced = if (kind is ManaKind.SameAsProduced && tapped != null)
                    act.state.player(player).manaPool.filter { it.source == tapped }.map { it.color }.distinct() else emptyList()
                if (produced.size == 1) List(amount) { produced.single() }
                else act.chooseColor(player, scope.self, produced.filterNotNull().ifEmpty { Color.all }).let { c -> List(amount) { c } }
            }
            // ecl: CR 105.4, 106.1: one mana of each colour among the objects
            is ManaKind.EachColorAmong -> e.objects(kind.objects).flatMap { e.characteristics(it)?.colors.orEmpty() }.distinct()
        }
        if (colors.isEmpty()) return
        val units = colors.map { ManaUnit(it, scope.self, restriction = production.restriction, spellGets = production.spellGets,
            keptUntil = production.keptUntil, whenSpent = production.whenSpent) }
        act.record(Changes.manaPool(act.state, player, act.state.player(player).manaPool + units, listOf(Meaning.ManaAdded)))
    }

    // ---- Continuous effects (CR 611)

    // gtc
    /** "Becomes a copy of …, except it has this ability" (CR 707.9b): the ability resolving, as the effect is created. */
    private fun thisAbility(scope: Scope, m: Mechanic): Mechanic {
        if (m !is Mechanic.CopyOf || arcanum.api.CopyException.ThisAbility !in m.except) return m
        val ability = scope.cause.item?.ability?.takeIf { it !is arcanum.api.Ability.Spell }
        return m.copy(except = m.except.mapNotNull { x ->
            if (x == arcanum.api.CopyException.ThisAbility) ability?.let { arcanum.api.CopyException.AddAbilities(listOf(it)) } else x
        })
    }

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
        val fixed = mechanics.map { fix(e, it) }.map { thisAbility(scope, it) }
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

    /** Marks an effect on a spell and the permanent it becomes ([Effect.SpellGets], CR 400.7d): its names hold both. */
    const val FOLLOWS_SPELL = "\$follows-spell"


    /** Fixes a mechanic's numbers when they don't depend on each affected object (CR 608.2h, 611.2c). */
    private fun fix(e: Evaluator, mechanic: Mechanic): Mechanic = when (mechanic) {
        is Mechanic.ModifyPowerToughness -> Mechanic.ModifyPowerToughness(fixValue(e, mechanic.power), fixValue(e, mechanic.toughness))
        is Mechanic.SetPowerToughness -> Mechanic.SetPowerToughness(mechanic.power?.let { fixValue(e, it) }, mechanic.toughness?.let { fixValue(e, it) })
        // sos: "that creature enters with X additional +1/+1 counters, where X is the number of colors of mana spent to cast it": X fixed as
        // the effect is created (CR 608.2h)
        is Mechanic.Replacement -> mechanic.copy(modify = mechanic.modify.map { m ->
            if (m is arcanum.api.Modification.EntersWithCounters) m.copy(amount = fixValue(e, m.amount)) else m
        })
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
        extra: Map<String, NamedValue> = emptyMap(), oneShot: Boolean = false,
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
            // An object that already left (a countered spell) is kept as it last existed (CR 608.2h), not read later from
            // a log the host may have trimmed
            snapshots = refs.mapNotNull { act.snapshot(it) ?: arcanum.engine.query.Lki.find(act.state, it) },
            oneShot = oneShot,
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
        if (after != combat) act.record(Change.Combat(combat, after, listOf(arcanum.engine.change.Meaning.RemovedFromCombat)))
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
        val first = act.chooseObjects(separator, scope.self, ids, 0, ids.size, if (effect.faceDownPile) "Face-up pile" else "First pile")
        val second = ids.filter { it !in first }
        // hob: one pile face down: the face-up pile is shown to everyone, the other stays hidden (CR 701.20)
        if (effect.faceDownPile && first.isNotEmpty()) {
            act.fill(separator, scope.self, first, "face-up pile", public = true)
            act.record(Change.Happened(GameEvent.Revealed(separator, first)))
        }
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

    /** fra: what [player] can behold (CR 701.4a): matching cards in their hand and permanents they control. */
    /**
     * msh: conniving with its replacements (CR 614.1a, 701.50a): each conniving permanent a "If a creature you control would connive,
     * instead …" applies to does the replacement's instructions instead, with it as the event's object; a replacement doesn't apply
     * again to the connive it brings (CR 614.5). False when no replacement applies (the plain expansion runs).
     */
    private fun connive(scope: Scope, action: KeywordAction.Connive): Boolean {
        val act = scope.act
        val view = act.view()
        val replacements = view.rules.filter { applied ->
            val m = applied.mechanic as? arcanum.api.Mechanic.Replacement ?: return@filter false
            m.event is arcanum.api.EventPattern.Connive && m.instead != null && scope.names[conniveGuard(applied)] == null
        }
        if (replacements.isEmpty()) return false
        val conniving = scope.objects(action.objects)
        conniving.forEach { id ->
            val applied = replacements.firstOrNull { a ->
                val m = a.mechanic as arcanum.api.Mechanic.Replacement
                Evaluator(act.board(), arcanum.engine.query.Context(a.controller, a.source, a.names)).matches((m.event as arcanum.api.EventPattern.Connive).objects, id)
            }
            if (applied == null) {
                val one = scope.names.with(CONNIVER, NamedValue.Objects(listOf(id)))
                Steps.run(Scope(act, scope.cause, one, scope.event, scope.actor), KeywordExpansions.expand(action.copy(objects = Objects.Bound(CONNIVER)))!!)
                return@forEach
            }
            val m = applied.mechanic as arcanum.api.Mechanic.Replacement
            val event = arcanum.engine.state.TriggerEvent(listOf(arcanum.engine.state.RoleObjects(arcanum.api.EventRole.Object, listOf(id))), listOfNotNull(act.controllerOf(id)))
            val names = scope.names.with(applied.names).with(conniveGuard(applied), NamedValue.Number(1))
            Steps.run(Scope(act, Cause(applied.controller, applied.source, key = "connive:$id"), names, event), m.instead!!.steps)
        }
        return true
    }

    private fun conniveGuard(applied: arcanum.engine.view.AppliedMechanic) = "\$connive-replaced:${applied.source}:${applied.origin?.key}"
    private const val CONNIVER = "\$conniver"

    /** ecl: the creatures [player] could blight (CR 701.68a): creatures they control. */
    fun blightable(scope: Scope, player: arcanum.engine.state.PlayerId): List<ObjectId> {
        val e = scope.eval()
        return scope.act.state.battlefield.filter { e.controllerOf(it) == player && e.characteristics(it)?.isType(arcanum.api.CardType.Creature) == true &&
            scope.act.state.obj(it)?.phasedOut != true }
    }

    fun beholdable(scope: Scope, player: arcanum.engine.state.PlayerId, filter: arcanum.api.ObjectFilter): List<ObjectId> {
        val e = scope.eval()
        val state = scope.act.state
        return (state.player(player).hand + state.battlefield.filter { e.controllerOf(it) == player }).filter { e.matches(filter, it) }
    }

    private fun keywordAction(scope: Scope, action: KeywordAction, bind: String?) {
        val act = scope.act
        if (action is KeywordAction.Connive && connive(scope, action)) return
        // tla: CR 701.65a: exile them; each card exiled may be cast by its owner for {2} for as long as it stays exiled
        if (action is KeywordAction.Airbend) return airbend(scope, action, bind)
        // tla: CR 701.66a, b: the land, its counters and the delayed return; earthbending happens as that trigger is created
        if (action is KeywordAction.Earthbend) {
            KeywordExpansions.expand(action)?.let { Steps.run(scope, it) }
            val land = scope.objects(arcanum.api.Objects.Target(action.slot))
            scope.bind(bind, NamedValue.Objects(land))
            if (land.isNotEmpty()) act.record(Change.Happened(GameEvent.Earthbended(scope.actor)))
            return
        }
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
                act.fill(controller, explorer, listOf(top), "explore", public = true)
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
                // hob: "If it isn't a [subtype], it becomes a [subtype] in addition to its other types" (for good)
                if (act.state.obj(army)?.zone == Zone.Battlefield && scope.eval().characteristics(army)?.hasSubtype(action.subtype) != true)
                    applyMechanics(scope, listOf(Mechanic.SetTypes(addSubtypes = listOf(arcanum.api.SubtypeRef.Literal(action.subtype)))), null,
                        Duration.Indefinitely, null, locked = listOf(army))
                scope.bind(bind, NamedValue.Objects(listOf(army)))
            }
            // fra: CR 701.4a: reveal a matching card from your hand or choose a matching permanent you control; ecl: N different ones
            is KeywordAction.Behold -> {
                val options = beholdable(scope, scope.actor, action.filter)
                val n = scope.value(action.count)
                val chosen = act.chooseObjects(scope.actor, scope.self, options, n, n, "Behold")
                val revealed = chosen.filter { act.state.obj(it)?.zone == Zone.Hand }
                if (revealed.isNotEmpty()) act.record(Change.Happened(arcanum.engine.change.GameEvent.Revealed(scope.actor, revealed)))
                scope.bind(bind, NamedValue.Objects(chosen))
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
                // CR 701.10e: each of them adds an additional amount of mana equal to the amount of each type they have
                is arcanum.api.Doubled.Mana -> scope.players(what.players).forEach { p ->
                    val pool = act.state.player(p).manaPool
                    if (pool.isNotEmpty()) act.record(Changes.manaPool(act.state, p, pool + pool.map { arcanum.engine.state.ManaUnit(it.color, snow = it.snow) },
                        listOf(arcanum.engine.change.Meaning.ManaAdded)))
                }
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
            // CR 701.40a, c: the top card of the library onto the battlefield face down as a 2/2 creature, one card at a time;
            // CR 701.58a: cloaked, it also has ward {2}
            is KeywordAction.Manifest -> scope.bind(bind, NamedValue.Objects(manifest(scope, scope.value(action.count), ObjectMark.Manifested)))
            is KeywordAction.Cloak -> scope.bind(bind, NamedValue.Objects(manifest(scope, scope.value(action.count), ObjectMark.Cloaked)))
            // CR 701.56a: for each suspended card you own and each permanent you control with a time counter on it, you may add or
            // remove a time counter
            KeywordAction.TimeTravel -> {
                val e = scope.eval()
                val suspended = act.state.exile.filter { id -> act.state.obj(id)?.let { it.owner == scope.actor && it.counters(CounterKind.Time) > 0 &&
                    e.characteristics(id)?.abilities.orEmpty().any { a -> a.ability is arcanum.api.Keyword.Suspend } } == true }
                val permanents = act.state.battlefield.filter { e.controllerOf(it) == scope.actor && act.state.obj(it)!!.counters(CounterKind.Time) > 0 }
                (suspended + permanents).forEach { id ->
                    val choice = act.chooseOption(scope.actor, id, listOf(TIME_ADD, TIME_REMOVE, TIME_NEITHER))
                    when (choice) {
                        TIME_ADD -> Actions.putCounters(act, cause, TargetRef.Object(id), CounterKind.Time, 1)
                        TIME_REMOVE -> Actions.removeCounters(act, TargetRef.Object(id), CounterKind.Time, 1)
                    }
                }
            }
            // blb: CR 701.61a: exile three cards from your graveyard or sacrifice a Food; nothing if neither can be done
            KeywordAction.Forage -> if (Costs.canPay(scope, scope.actor, arcanum.api.Cost(listOf(arcanum.engine.casting.Costs.forage)))) {
                Costs.pay(scope, scope.actor, arcanum.api.Cost(listOf(arcanum.engine.casting.Costs.forage)))
                act.record(Change.Happened(GameEvent.Foraged(scope.actor)))
            }
            is KeywordAction.ManifestDread -> scope.players(action.players).forEach { p -> manifestDread(scope, p, bind) }
            // ecl: CR 701.68a: each player puts N -1/-1 counters on a creature they control; nothing without one (701.68b); the creature
            // chosen is "the blighted creature" (701.68c)
            is KeywordAction.Blight -> {
                val n = scope.value(action.count)
                val blighted = scope.players(action.players).flatMap { p ->
                    val options = blightable(scope, p)
                    if (options.isEmpty() || n <= 0) return@flatMap emptyList<ObjectId>()
                    val chosen = act.chooseObjects(p, scope.self, options, 1, 1, "Blight $n").firstOrNull() ?: return@flatMap emptyList<ObjectId>()
                    Actions.putCounters(act, cause, TargetRef.Object(chosen), CounterKind.PowerToughness(-1, -1), n)
                    listOf(chosen)
                }
                scope.bind(bind, NamedValue.Objects(blighted))
            }
            else -> act.manual(scope.actor, scope.self, "Do by hand: ${action::class.simpleName}")
        }
    }

    /** CR 701.40a, c, 701.58a, c: the actor's top card onto the battlefield face down, [count] times one at a time, each [mark]ed
     * (manifested, cloaked); the permanents made. */
    private fun manifest(scope: Scope, count: Int, mark: ObjectMark): List<ObjectId> {
        val act = scope.act
        return (1..count).mapNotNull {
            val top = act.state.player(scope.actor).library.firstOrNull() ?: return@mapNotNull null
            val moved = Actions.move(act, scope.cause, listOf(PlannedMove(top, Zone.Battlefield, controller = scope.actor, faceDown = true)))[top]
                ?.takeIf { act.state.obj(it)?.zone == Zone.Battlefield } ?: return@mapNotNull null
            Actions.tapStatus(act, moved, StatusFlag.Marked(mark), true)
            moved
        }
    }

    private const val TIME_ADD = "add a time counter"
    private const val TIME_REMOVE = "remove a time counter"
    private const val TIME_NEITHER = "neither"

    /**
     * CR 701.62a: [player] looks at the top two cards of their library, manifests one (CR 701.40a: face down as a 2/2,
     * [ObjectMark.Manifested]) and puts the other into their graveyard; then the manifest-dread event (701.62b).
     */
    private fun manifestDread(scope: Scope, player: PlayerId, bind: String?) {
        val act = scope.act
        val top = act.state.player(player).library.take(2)
        act.fill(player, scope.self, top, "manifest dread")
        val chosen = if (top.size <= 1) top else act.chooseObjects(player, scope.self, top, 1, 1, "Manifest which card?")
        val moved = if (chosen.isEmpty()) emptyMap() else Actions.move(act, scope.cause, chosen.map {
            PlannedMove(it, Zone.Battlefield, controller = player, faceDown = true)
        })
        val manifested = chosen.firstOrNull()?.let { moved[it] }?.takeIf { act.state.obj(it)?.zone == Zone.Battlefield }
        manifested?.let { Actions.tapStatus(act, it, StatusFlag.Marked(ObjectMark.Manifested), true) }
        val rest = top.filterNot { it in chosen }.filter { act.state.obj(it) != null }
        val graveyard = Actions.moveTo(act, scope.cause, rest, Zone.Graveyard)
        act.record(Change.Happened(GameEvent.ManifestedDread(player, manifested, graveyard.values.toList())))
        scope.bind(bind, NamedValue.Objects(listOfNotNull(manifested)))
    }

    /** tla: CR 701.65a: [action]'s objects are exiled; for each card exiled, an effect lets its owner cast it from exile by paying {2}
     * rather than its mana cost for as long as it stays there (a new object leaving exile, the effect has nothing left to apply to). */
    private fun airbend(scope: Scope, action: KeywordAction.Airbend, bind: String?) {
        val act = scope.act
        val ids = scope.objects(action.objects).filter { act.state.obj(it)?.let { o -> o.zone == Zone.Battlefield || o.zone == Zone.Stack } == true }.distinct()
        if (ids.isEmpty()) { scope.bind(bind, NamedValue.Objects(emptyList())); return }
        val moved = Actions.move(act, scope.cause, ids.map { PlannedMove(it, Zone.Exile, meanings = listOf(Meaning.Exiled)) })
        val exiled = moved.values.filter { act.state.obj(it)?.zone == Zone.Exile && act.state.obj(it)?.token == false }
        exiled.forEach { card ->
            val owner = act.state.obj(card)!!.owner
            act.record(arcanum.engine.change.Changes.effect(act.state) { ts ->
                arcanum.engine.state.EffectObject(
                    id = arcanum.engine.state.EffectId("E$ts"),
                    mechanics = listOf(Mechanic.MayPlay(Zone.Exile, arcanum.api.PlayKind.Cast, cost = arcanum.api.Cost.mana(arcanum.api.ManaCost.parse("{2}")!!),
                        holder = arcanum.api.PlayerFilter.You)),
                    affects = arcanum.api.Affected.Objects(arcanum.api.Objects.Self), locked = listOf(card), source = card,
                    controller = owner, timestamp = ts, createdTurn = act.state.turn.number,
                    duration = Duration.Indefinitely,
                )
            })
        }
        scope.bind(bind, NamedValue.Objects(exiled))
        act.record(Change.Happened(GameEvent.Airbended(scope.actor)))
    }

    private val BEFORE_COMBAT = setOf(arcanum.api.TurnStep.Untap, arcanum.api.TurnStep.Upkeep, arcanum.api.TurnStep.Draw,
        arcanum.api.TurnStep.PrecombatMain)
    private const val POPULATED = "\$populated"
    private const val EXERTED = "\$exerted"
}

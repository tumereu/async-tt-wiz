package arcanum.engine.resolution

import arcanum.engine.state.mergedAbove
import arcanum.engine.state.withStatus
import arcanum.engine.state.copyOfCard
import arcanum.engine.state.deathtouchDamage
import arcanum.engine.state.goadedBy
import arcanum.engine.state.mergedBelow
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
import arcanum.api.KeywordActionKind
import arcanum.api.kind
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
import arcanum.engine.Host
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
import arcanum.engine.decision.Decision
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
 * bind them ([Effect] KDoc): `bind`, or `preBind`/`postBind` around a zone change (D21).
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
                moveOrBatch(scope, ids.filter { id -> !Actions.cantBeSacrificed(act, id, cause.controller) }
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
                act.show(scope.actor, ids, public = true)
                if (ids.isNotEmpty()) act.record(Change.Happened(GameEvent.Revealed(revealer(scope, ids), ids)))
                scope.bind(effect.bind, NamedValue.Objects(ids))
            }
            is Effect.LookAt -> {
                val ids = scope.objects(effect.objects)
                act.show(scope.players(effect.players).firstOrNull() ?: scope.actor, ids)
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
            // CR 701.28: only a transforming double-faced permanent transforms; it gets a new timestamp (CR 613.7g). What
            // represents it decides, not its copiable values (CR 712.9): a double-faced token does, a copy on a one-faced card doesn't
            is Effect.Transform -> onBattlefield(scope, effect.objects).forEach { id ->
                val o = act.state.objOrThrow(id)
                // CR 701.27, 101.2: "can't transform" wins over the instruction
                if (act.view()[id]?.has { it == Mechanic.CantTransform } == true) return@forEach
                // msh: a modal double-faced permanent transforms too (CR 701.27a), not into an instant or sorcery face (701.27d)
                val layout = o.definition.layout
                if (layout != Layout.Transform && layout != Layout.ModalDoubleFaced) return@forEach
                val other = o.definition.faces.getOrNull(1 - o.face) ?: return@forEach
                if (layout == Layout.ModalDoubleFaced && (arcanum.api.CardType.Instant in other.types || arcanum.api.CardType.Sorcery in other.types)) return@forEach
                act.record(Change.Face(id, o.face, 1 - o.face, listOf(Meaning.Transformed)))
                act.record(Change.Restamped(id, o.timestamp, act.state.nextTimestamp()))
                asTransforms(scope, id)
            }
            // CR 707.3, 110.5: a flip card flips, and so does a permanent that's a copy of one; the status is the object's own, and
            // what isn't a flip card keeps the face it has up (CR 712.8d)
            is Effect.Flip -> onBattlefield(scope, effect.objects).forEach { id ->
                val o = act.state.objOrThrow(id)
                if (act.view()[id]?.copiable?.layout != Layout.Flip || o.flipped) return@forEach
                act.record(if (o.definition.layout == Layout.Flip) Change.Face(id, 0, 1, listOf(Meaning.Flipped))
                    else Change.Status(id, StatusFlag.Flipped, false, true, listOf(Meaning.Flipped)))
            }
            // CR 116.2b, j: every way a permanent or a conspiracy in the command zone is turned face up, special actions included (CR 702.37e, 702.106c)
            is Effect.TurnFaceUp -> scope.objects(effect.objects).distinct().filter { act.state.obj(it)?.zone.let { z -> z == Zone.Battlefield || z == Zone.Command } }.forEach { id ->
                val o = act.state.objOrThrow(id)
                if (!o.faceDown) return@forEach
                if (act.view()[id]?.has { it == Mechanic.CantBeTurnedFaceUp } == true) return@forEach
                // CR 708.8: turned face up, everyone sees it
                act.show(o.controller ?: o.owner, listOf(id), public = true)
                // CR 701.40g, 701.58g: a manifested or cloaked instant or sorcery card is revealed and left face down; nothing triggers
                if (spellCardFaceDown(act.state.objOrThrow(id))) {
                    act.record(Change.Happened(GameEvent.Revealed(o.controller ?: o.owner, listOf(id))))
                    return@forEach
                }
                // CR 702.37f, 702.168e: the X of the cost it was turned up for, read by its turned-face-up abilities
                val x = effect.x?.let { Meaning.CostX(scope.value(it)) }
                act.record(Change.Status(id, StatusFlag.FaceDown, true, false, listOfNotNull(Meaning.TurnedFaceUp, x)))
                // CR 708.2a: face down again later, by an effect that lists no characteristics, it is a plain 2/2 (no disguise's ward)
                arcanum.engine.change.Changes.status(act.state, id, arcanum.engine.state.ObjectStatus.TurnedFaceUp.key, arcanum.engine.state.ObjectStatus.TurnedFaceUp)?.let(act::record)
                // CR 701.40a, 701.58a: what lasts only while it is face down ends
                o.marks.filter { arcanum.api.MarkClear.TurnedFaceUp in arcanum.api.MarkRules.of(it).clears }.forEach { Actions.tapStatus(act, id, StatusFlag.Marked(it), false) }
                // CR 613.7f: it gets a new timestamp
                act.record(Change.Restamped(id, o.timestamp, act.state.nextTimestamp()))
                effect.counters.forEach { c -> Actions.putCounters(act, cause, TargetRef.Object(id), c.kind, scope.value(c.amount)) }
                // mkm: CR 614.1e: "As this creature is turned face up, …" happens as it turns, before anything sees it face up
                asTurnedFaceUp(scope, id)
                // CR 702.106c: a conspiracy's hidden agenda reveals the chosen name
                arcanum.engine.conspiracy.Conspiracies.revealed(act, id)
            }
            is Effect.TurnFaceDown -> onBattlefield(scope, effect.objects).forEach { id ->
                val o = act.state.objOrThrow(id)
                if (o.faceDown) return@forEach
                act.record(Change.Status(id, StatusFlag.FaceDown, false, true, listOf(Meaning.TurnedFaceDown)))
                act.record(Change.Restamped(id, o.timestamp, act.state.nextTimestamp()))
            }
            is Effect.PhaseOut -> {
                // mh2: "until this enchantment leaves the battlefield": if it already left, nothing phases out (CR 610.3)
                val until = effect.untilLeaves?.let { scope.objects(it) }
                if (until != null && until.none { act.state.obj(it)?.zone == Zone.Battlefield }) { scope.bind(effect.bind, NamedValue.Objects(emptyList())); return }
                val phased = onBattlefield(scope, effect.objects).filter { act.state.obj(it)?.phasedOut == false }
                phased.forEach {
                    Actions.tapStatus(act, it, StatusFlag.PhasedOut, true, listOf(Meaning.PhasedOut))
                    if (until != null) arcanum.engine.change.Changes.status(act.state, it, arcanum.engine.state.ObjectStatus.HeldPhasedOut.key, arcanum.engine.state.ObjectStatus.HeldPhasedOut)?.let(act::record)
                }
                scope.bind(effect.bind, NamedValue.Objects(phased))
                if (until != null && phased.isNotEmpty()) createDelayed(scope,
                    Trigger.On(EventPattern.ZoneChange(ObjectFilter.Is(Objects.Bound(UNTIL_SOURCE)), from = Zone.Battlefield)),
                    Instructions(steps = listOf(Step.Do(Effect.PhaseIn(Objects.Bound(UNTIL_RETURNED))))),
                    duration = null, repeats = false,
                    extra = mapOf(UNTIL_SOURCE to NamedValue.Objects(until), UNTIL_RETURNED to NamedValue.Objects(phased)), oneShot = true)
            }
            // mh2: phasing in (CR 702.26c)
            is Effect.PhaseIn -> scope.objects(effect.objects).filter { act.state.obj(it)?.phasedOut == true }.forEach {
                Actions.tapStatus(act, it, StatusFlag.PhasedOut, false, listOf(Meaning.PhasedIn))
                arcanum.engine.change.Changes.status(act.state, it, arcanum.engine.state.ObjectStatus.HeldPhasedOut.key, null)?.let(act::record)
            }
            // A mark goes only where it can live (a warped or plotted card in exile, CR 702.185b, 702.170c; a suspected permanent, 701.60b)
            is Effect.Mark -> {
                val zones = arcanum.api.MarkRules.of(effect.mark).zones
                scope.objects(effect.objects).filter { act.state.obj(it)?.zone in zones }.distinct()
                    .forEach { Actions.setMark(act, it, effect.mark, !effect.remove, listOfNotNull(effect.cost)) }
            }
            is Effect.GiveDesignation -> scope.players(effect.players).firstOrNull()?.let { p -> designate(scope, p, effect.designation) }
            is Effect.CreateToken -> createToken(scope, effect)
            is Effect.CopySpell -> copySpell(scope, effect)
            is Effect.CopyCard -> copyCards(scope, effect)
            is Effect.CopyNamedCard -> copyNamedCard(scope, effect)
            is Effect.Counter -> counter(scope, scope.objects(effect.objects))
            is Effect.AddMana -> scope.players(effect.players).forEach { p -> addMana(scope, p, effect.mana) }
            is Effect.ApplyMechanics -> applyMechanics(scope, effect.mechanics, effect.affects, effect.duration, effect.uses)
            is Effect.PreventDamage -> preventDamage(scope, effect)
            is Effect.RemoveFromCombat -> removeFromCombat(scope, onBattlefield(scope, effect.objects))
            is Effect.RemoveAllDamage -> onBattlefield(scope, effect.objects).forEach { id ->
                val o = act.state.objOrThrow(id)
                if (o.damage > 0 || o.deathtouchDamage) act.record(Change.DamageMarked(id, o.damage, 0, o.deathtouchDamage, false))
            }
            is Effect.CreateDelayedTrigger -> createDelayed(scope, effect.trigger, effect.instructions, effect.duration, effect.repeats, laterTurn = effect.laterTurn)
            is Effect.RollDie -> {
                val player = scope.players(effect.players).firstOrNull() ?: scope.actor
                val rolls = (act.random(player, scope.self, RandomKind.Die(effect.sides, effect.count)) as Answer.DieRolls).results
                act.record(Change.Happened(GameEvent.DieRolled(player, effect.sides, rolls)))
                scope.bind(effect.bind, NamedValue.Rolls(rolls))
            }
            is Effect.FlipCoin -> {
                val player = scope.players(effect.players).firstOrNull() ?: scope.actor
                // mh2: "Flip that many coins": each flipped in turn, the wins and losses counted (CR 705)
                if (effect.count != null) {
                    var won = 0
                    var lost = 0
                    repeat(scope.value(effect.count!!).coerceAtLeast(0)) {
                        if (flipOne(scope, player)) won++ else lost++
                    }
                    scope.bind(effect.bind, NamedValue.Flips(won, lost))
                    return
                }
                scope.bind(effect.bind, NamedValue.Flip(flipOne(scope, player)))
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
                // ltr chunk 03: "of each kind not on Goldberry"
                val except = effect.exceptKindsOn?.let { onBattlefield(scope, it) }.orEmpty()
                    .flatMap { id -> act.state.obj(id)?.counters.orEmpty().filter { it.count > 0 }.map { it.kind } }.toSet()
                var moved = 0
                onBattlefield(scope, effect.from).forEach { from ->
                    val kinds = (effect.kind?.let { listOf(it) } ?: act.state.objOrThrow(from).counters.filter { it.count > 0 }.map { it.kind })
                        .filter { it !in except }
                    if (effect.chosen) {
                        // ltr chunk 03: "Move one or more counters": how many of each kind, at least [amount] in all (CR 122.5)
                        val atLeast = scope.value(effect.amount)
                        kinds.forEachIndexed { i, k ->
                            val have = act.state.objOrThrow(from).counters(k)
                            val laterHave = kinds.drop(i + 1).sumOf { act.state.objOrThrow(from).counters(it) }
                            val min = (atLeast - moved - laterHave).coerceIn(0, have)
                            val n = if (have == min) have else act.chooseNumber(cause?.controller ?: act.state.turn.active, cause?.source, min, have)
                            if (n > 0) {
                                val removed = Actions.removeCounters(act, TargetRef.Object(from), k, n)
                                Actions.putCounters(act, cause, TargetRef.Object(to), k, removed)
                                moved += removed
                            }
                        }
                    } else kinds.forEach { k ->
                        val n = Actions.removeCounters(act, TargetRef.Object(from), k, scope.value(effect.amount))
                        Actions.putCounters(act, cause, TargetRef.Object(to), k, n)
                        moved += n
                    }
                }
                effect.bind?.let { scope.bind(it, arcanum.engine.state.NamedValue.Number(moved)) }
            }
            // ltr chunk 11: one counter of each kind the triggering event put
            is Effect.PutEventCounterKinds -> {
                val kinds = scope.event?.counterKinds.orEmpty()
                onBattlefield(scope, effect.objects).forEach { id -> kinds.forEach { k -> Actions.putCounters(act, cause, TargetRef.Object(id), k, 1) } }
            }
            is Effect.ChangeTargets -> changeTargets(scope, effect)
            is Effect.SeparatePiles -> separatePiles(scope, effect)
            is Effect.Remember -> remember(scope, effect.key, scope.objects(effect.objects), effect.replace)
            // blb: CR 716.2a, b: the level is a designation of the permanent, kept as its status
            is Effect.SetClassLevel -> onBattlefield(scope, effect.objects).forEach { id ->
                val o = act.state.obj(id) ?: return@forEach
                val before = o.statuses.firstOrNull { it is arcanum.engine.state.ObjectStatus.ClassLevel }
                val after = arcanum.engine.state.ObjectStatus.ClassLevel(effect.level)
                if (before != after) {
                    act.record(Change.ObjectStatusChanged(id, before, after))
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
            // fin: CR 500.9: an additional step right after this one, before those added earlier
            is Effect.AdditionalStep -> act.record(Changes.step(act.state, act.state.turn.copy(extraSteps = listOf(effect.step) + act.state.turn.extraSteps)))
            // fin: CR 724.1a, b: waiting triggers cease to exist and every other object on the Stack is exiled; the resolving one is exiled as it
            // leaves ([Resolution.finish]) and the turn goes on from its cleanup step ([arcanum.engine.turn.Steps.endTheTurn])
            Effect.EndTurn -> {
                act.state.triggered.toList().let { waiting -> if (waiting.isNotEmpty()) act.record(Change.TriggersWaiting(waiting, emptyList())) }
                act.state.stack.filter { it != scope.cause.item?.id }.forEach { id ->
                    val item = act.state.stackItems[id] ?: return@forEach
                    if (act.state.obj(id) != null && item.kind == arcanum.engine.state.StackItemKind.Spell) {
                        act.record(Change.StackItemRemoved(item, null, listOf(arcanum.engine.change.Meaning.Exiled)))
                        Actions.moveTo(act, cause, listOf(id), Zone.Exile, emptyList())
                    } else act.record(Change.StackItemRemoved(item, act.state.stack.indexOf(id), listOf(arcanum.engine.change.Meaning.Exiled)))
                }
                act.record(Changes.step(act.state, act.state.turn.copy(endingTurn = true)))
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
            // inr chunk 08: "Exchange target opponent's life total with this creature's toughness." (CR 701.12g; nothing without both, 701.12a)
            is Effect.ExchangeLifeWithStat -> {
                val p = scope.players(effect.player).firstOrNull() ?: return
                val id = onBattlefield(scope, effect.creature).firstOrNull() ?: return
                val life = act.state.player(p).life
                val stat = scope.value(Value.Of(effect.property, effect.creature, arcanum.api.Combine.Single))
                setLife(scope, p, stat)
                val set = if (effect.property == arcanum.api.NumericProperty.Power) Mechanic.SetPowerToughness(Value.Fixed(life), null)
                    else Mechanic.SetPowerToughness(null, Value.Fixed(life))
                applyMechanics(scope, listOf(set), Affected.Objects(effect.creature), Duration.Indefinitely, null, locked = listOf(id))
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
                // CR 104.2b: an effect can't make a player win who can't win
                if (act.view().players[winner]?.mechanics.orEmpty().any { it.mechanic == Mechanic.CantWinGame }) return@forEach
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
            // CR 702.55a, b: the card is exiled haunting the creature (its memory under the keyword's id)
            is Effect.Haunt -> scope.objects(effect.card).firstOrNull { act.state.obj(it)?.zone == Zone.Graveyard }?.let { card ->
                val creature = scope.objects(effect.creature).firstOrNull()
                val exiled = Actions.move(act, scope.cause, listOf(PlannedMove(card, Zone.Exile, meanings = listOf(Meaning.Exiled))))[card]
                    ?.takeIf { act.state.obj(it)?.zone == Zone.Exile }
                if (exiled != null && creature != null)
                    act.record(Change.Memory(exiled, arcanum.api.Keyword.Haunt.id, null, arcanum.engine.state.NamedValue.Objects(listOf(creature))))
            }
            // CR 702.95a, c: soulbond's pairing
            is Effect.Pair -> scope.objects(effect.objects).firstOrNull()?.let { first ->
                arcanum.engine.soulbond.Soulbond.pair(act, scope.actor, scope.self, first, scope.objects(effect.with))
            }
            is Effect.CreateEmblem -> scope.players(effect.players).forEach { p -> createEmblem(scope, p, effect.abilities) }
            is Effect.Action -> keywordAction(scope, effect.action, effect.bind)
            // CR 709.5f: the chooser picks a locked door of each (or the door named)
            is Effect.UnlockDoor -> {
                val unlocked = onBattlefield(scope, effect.objects).filter { id ->
                    val locked = arcanum.engine.room.Rooms.locked(act.view()[id])
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
                if (!arcanum.engine.room.Rooms.isRoom(act.view()[id]?.copiable)) return@forEach
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
            // Host.decider who makes their decisions
            is Effect.ControlNextTurn -> {
                val controller = scope.players(effect.controller).firstOrNull() ?: return
                scope.players(effect.players).forEach { p ->
                    val control = arcanum.engine.state.PlayerControl(p, controller, combatOnly = effect.combatPhase, thenExtraTurn = effect.thenExtraTurn)
                    val turn = act.state.turn
                    // tla: only their next combat phase (CR 723.2): this turn's when it is theirs and its combat is still to come
                    val now = effect.combatPhase && turn.active == p && turn.step in BEFORE_COMBAT
                    act.record(Changes.step(act.state, if (now) turn.copy(controlled = control) else turn.copy(controlNext = turn.controlNext + control)))
                }
            }
            // CR 702.179c: speed increases (from none to the amount), at most 4
            is Effect.IncreaseSpeed -> scope.players(effect.players).forEach { p ->
                arcanum.engine.turn.Speed.set(act, p, arcanum.engine.turn.Speed.of(act.state, p) + scope.value(effect.amount))
            }
            // dft: speed goes down, not below the floor; no speed stays none (CR 702.179)
            is Effect.ReduceSpeed -> scope.players(effect.players).forEach { p ->
                val speed = arcanum.engine.turn.Speed.of(act.state, p)
                if (speed > effect.atLeast) arcanum.engine.turn.Speed.set(act, p, maxOf(effect.atLeast, speed - scope.value(effect.amount)))
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

    /** One coin flip by [player] (CR 705): whether they won it. */
    /**
     * CR 701.20a: who reveals [ids]. Cards revealed from one player's hand or library are revealed by that player, the
     * subject of every such wording ("Target player reveals their hand", "Reveal the top card of your library"), whoever
     * controls the effect; anything else (cards in public zones, a face-down permanent, several players' cards) by the
     * resolving ability's player.
     */
    private fun revealer(scope: Scope, ids: List<ObjectId>): PlayerId {
        val owners = ids.map { id ->
            val o = scope.act.state.obj(id) ?: return scope.actor
            if (o.zone != Zone.Hand && o.zone != Zone.Library) return scope.actor
            o.owner
        }.distinct()
        return owners.singleOrNull() ?: scope.actor
    }

    private fun flipOne(scope: Scope, player: PlayerId): Boolean {
        val act = scope.act
        // fin: "those coins come up heads and you win those flips": a replacement of the flip (CR 705.2, 614.1a)
        val linked = mutableListOf<arcanum.engine.decision.WaitingTrigger>()
        val wins = arcanum.engine.action.Replacements.apply(act, scope.cause, false, player, null,
            proposed = { w -> if (w) null else Change.Happened(GameEvent.CoinFlipped(player, false)) },
            modify = { w, r, _ -> w || r.mechanic.modify.any { it == arcanum.api.Modification.WinFlips } },
            instead = { r, instance -> Actions.runInstead(act, r, instance) }, linked = linked) ?: false
        arcanum.engine.action.Replacements.addLinked(act, linked)
        val won = wins || (act.random(player, scope.self, RandomKind.CoinFlip) as Answer.CoinFlip).won
        act.record(Change.Happened(GameEvent.CoinFlipped(player, won)))
        return won
    }

    private fun chooseDoor(scope: Scope, chooser: Players, id: ObjectId, doors: List<arcanum.api.Door>, text: String): arcanum.api.Door {
        val player = scope.players(chooser).firstOrNull() ?: scope.actor
        val names = scope.act.view()[id]?.copiable?.faces.orEmpty()
        val options = doors.map { names.getOrNull(arcanum.engine.room.Rooms.face(it))?.name ?: it.toString() }
        val chosen = scope.act.chooseOption(player, id, options)
        return doors[options.indexOf(chosen).coerceAtLeast(0)]
    }

    // ---- Helpers

    /** CR 701.40g, 701.58g: [o] is a manifested or cloaked permanent represented by an instant or sorcery card (a split card by either half). */
    private fun spellCardFaceDown(o: arcanum.engine.state.GameObject): Boolean {
        if (ObjectMark.Manifested !in o.marks && ObjectMark.Cloaked !in o.marks) return false
        val definition = o.definition
        val faces = if (definition.layout == Layout.Split) definition.faces else definition.faces.take(1)
        return faces.any { f -> f.types.any { it == CardType.Instant || it == CardType.Sorcery } }
    }

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
        // CR 104.3e: the loss names the effect's source, as the name it had while its instructions run
        val card = act.recorder.by?.name ?: scope.cause.source?.let { act.view()[it]?.characteristics?.name }
        act.record(Change.Lost(player, null, arcanum.engine.setup.LossReason.Effect(card)))
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
    /** mh2: a copy of a card by name, made in exile from the card the host gives ([Decision.NamedCard]), everyone seeing it. */
    private fun copyNamedCard(scope: Scope, effect: Effect.CopyNamedCard) {
        val act = scope.act
        val name = (scope.names[effect.name] as? NamedValue.Name)?.name ?: return scope.bind(effect.bind, NamedValue.Objects(emptyList()))
        val owner = scope.players(effect.owner).firstOrNull() ?: scope.actor
        val definition = (act.ask { Decision.NamedCard(it, owner, scope.self, name) } as Answer.NamedCard).definition
        val creator = TokenIds.created("named-copy", act.state.nextTimestamp())
        val copy = GameObject(id = ObjectId.Card(TokenIds.token(creator, 0), 0), owner = owner, zone = Zone.Exile, definition = definition,
            statuses = listOf(arcanum.engine.state.ObjectStatus.CopyOfCard), timestamp = act.state.nextTimestamp())
        act.record(Change.Created(listOf(Placed(copy, act.state.exile.size)), listOf(Meaning.CardCopied)))
        scope.bind(effect.bind, NamedValue.Objects(listOf(copy.id)))
    }

    private fun copyCards(scope: Scope, effect: Effect.CopyCard) {
        val act = scope.act
        val owner = scope.players(effect.owner).firstOrNull() ?: scope.actor
        val creator = TokenIds.created("copy", act.state.nextTimestamp())
        val first = act.state.nextTimestamp()
        val copies = scope.objects(effect.objects).mapNotNull { act.state.obj(it) }.mapIndexed { i, card ->
            GameObject(
                id = ObjectId.Card(TokenIds.token(creator, i), 0), owner = owner, zone = card.zone,
                // A card outside the battlefield and the Stack has its printed copiable values
                definition = card.definition, statuses = listOf(arcanum.engine.state.ObjectStatus.CopyOfCard), timestamp = first + i,
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
        if (dest.randomOrder && ids.size > 1) {
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
        val moves = ordered.map { id ->
            val from = act.state.obj(id)!!.zone
            PlannedMove(
                // ecl: "under their owners' control": each card under its own owner's (CR 110.2)
                id, dest.zone, position, if (dest.zone != Zone.Battlefield) null else (dest.controller as? arcanum.api.Players.OwnerOf)
                    ?.takeIf { id in e.objects(it.objects) }?.let { act.state.obj(id)?.owner } ?: controller,
                tapped = dest.tapped, faceDown = dest.faceDown, counters = counters, meanings = meaningsOf(from, dest.zone),
                attachTo = attachTo, transformed = dest.transformed, attacking = attacking,
                randomOrder = dest.randomOrder && dest.zone == Zone.Library && ids.size > 1,
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
        // dgm backfill: cards put onto the battlefield by a name the effect chose ("Put a creature card from a graveyard onto the
        // battlefield under your control. It gains haste") are found there by the rest of the effect (CR 400.7, 608.2c)
        val bound = effect.objects as? Objects.Bound
        if (effect.postBind == null && bound != null && dest.zone == Zone.Battlefield && after.isNotEmpty()) scope.bind(bound.name, NamedValue.Objects(after))
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
                if (effect.filter != ObjectFilter.Any) act.show(p, hand)
                val e = scope.eval()
                // I-D18: "discard a creature card" decided by cards only the player sees
                val hidden = act.hiddenDecides(p, act.state.player(p).hand) { e.identityDecides(effect.filter, it) }
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
        // CR 701.22b, d: scrying 0 is no event; scrying with no library is, the actions being impossible
        if (top.isEmpty()) {
            if (n > 0) act.record(Change.Happened(GameEvent.KeywordActionDone(KeywordActionKind.Scry, player, amount = 0)))
            return
        }
        act.show(player, top)
        val chosen = act.chooseObjects(player, scope.self, top, 0, top.size, "Put on the bottom")
        val kept = act.order(player, scope.self, top.filter { it !in chosen }, "Order on top, first on top")
        val bottom = act.order(player, scope.self, chosen, "Order on the bottom, first on top")
        val rest = act.state.player(player).library.drop(top.size)
        val after = kept + rest + bottom
        if (after != act.state.player(player).library) act.record(Change.Reorder(Zone.Library, player, act.state.player(player).library, after))
        act.record(Change.Happened(GameEvent.KeywordActionDone(KeywordActionKind.Scry, player, amount = top.size)))
        scope.bind(bind, NamedValue.Objects(bottom))
    }

    /** CR 701.25: look at the top N, put any number into the graveyard and the rest on top in any order. */
    private fun surveil(scope: Scope, player: PlayerId, n: Int, bind: String?) {
        val act = scope.act
        val top = act.state.player(player).library.take(n)
        // CR 701.25c, d: surveilling 0 is no event; surveilling with no library is, the actions being impossible
        if (top.isEmpty()) {
            if (n > 0) act.record(Change.Happened(GameEvent.KeywordActionDone(KeywordActionKind.Surveil, player, amount = 0)))
            return
        }
        act.show(player, top)
        val toGraveyard = act.chooseObjects(player, scope.self, top, 0, top.size, "Put into your graveyard")
        val moved = Actions.moveTo(act, scope.cause, toGraveyard, Zone.Graveyard)
        val kept = top.filter { it !in toGraveyard }
        val ordered = act.order(player, scope.self, kept, "Order on top, first on top")
        val library = act.state.player(player).library
        if (ordered != kept && library.take(kept.size) == kept) {
            act.record(Change.Reorder(Zone.Library, player, library, ordered + library.drop(kept.size)))
        }
        act.record(Change.Happened(GameEvent.KeywordActionDone(KeywordActionKind.Surveil, player, amount = top.size)))
        scope.bind(bind, NamedValue.Objects(moved.values.toList()))
    }

    /** CR 701.23: the searcher looks at the zones and finds up to N matching cards (finding none is allowed, 701.23b). */
    private fun search(scope: Scope, effect: Effect.Search) {
        val act = scope.act
        val searcher = scope.players(effect.searcher).firstOrNull() ?: scope.actor
        val found = mutableListOf<ObjectId>()
        scope.players(effect.library).forEach { owner ->
            val cards = effect.zones.flatMap { act.state.zone(it, owner) }
            act.show(searcher, cards)
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
            // Each card is looked at or revealed as it is turned over, so the cards below the last one stay hidden
            act.show(owner, listOf(id), public = effect.reveal)
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
            val shares = (act.ask { id ->
                arcanum.engine.decision.Decision.Divide(id, scope.you, scope.self, total, ids.map { TargetRef.Object(it) }, 1)
            } as Answer.Division).shares
            shares.forEach { s -> Actions.putCounters(act, scope.cause, s.target, effect.kind, s.amount) }
            return
        }
        // eoe: the amount is worked out once, before any counter is put ("put X +1/+1 counters on each creature you control, where X is
        // this creature's power", CR 608.2h), unless each object has its own divided share
        val once = if (divided.isEmpty()) scope.value(effect.amount) else null
        ids.forEach { id ->
            val share = divided.firstOrNull { it.target == TargetRef.Object(id) }?.amount
            Actions.putCounters(act, scope.cause, TargetRef.Object(id), effect.kind, once ?: scope.eval(dividedShare = share).value(effect.amount))
        }
    }

    // ---- Tokens, copies, emblems

    private fun createToken(scope: Scope, effect: Effect.CreateToken) {
        val act = scope.act
        val e = scope.eval()
        val values = Actions.tokenValues(act, e, effect.token)
        val count = e.value(effect.count)
        val attachTo = effect.attachedTo?.let { recipients(scope, it).firstOrNull() }
        val created = scope.players(effect.controller).flatMap { p ->
            Actions.createTokens(act, scope.cause, values, count, p, effect.tapped, attachTo)
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
    /** fin: "As this creature transforms into [name], …": the face it transformed into does it as it transforms (CR 614.1c, as with "as
     * enters", 614.12), before anything sees the permanent transformed; choices it remembers are kept on the permanent. */
    private fun asTransforms(scope: Scope, id: ObjectId) {
        val act = scope.act
        val controller = act.controllerOf(id) ?: return
        val steps = act.view()[id]?.characteristics?.abilities.orEmpty()
            .flatMap { (it.ability as? arcanum.api.Ability.Static)?.mechanics.orEmpty() }.filterIsInstance<Mechanic.Replacement>()
            .flatMap { r -> r.modify.filterIsInstance<arcanum.api.Modification.AsTransforms>().flatMap { it.steps } }
        if (steps.isNotEmpty()) Steps.run(Scope(act, arcanum.engine.action.Cause(controller, id, key = "transforms:$id"), arcanum.engine.state.Bindings.EMPTY, null), steps)
    }

    /** mkm: "As this creature is turned face up, …" (CR 614.1e): its now face-up characteristics' replacements of the turning do their steps
     * as part of it, not on the Stack. */
    private fun asTurnedFaceUp(scope: Scope, id: ObjectId) {
        val act = scope.act
        val controller = act.controllerOf(id) ?: return
        val steps = act.view()[id]?.characteristics?.abilities.orEmpty()
            .flatMap { (it.ability as? arcanum.api.Ability.Static)?.mechanics.orEmpty() }.filterIsInstance<Mechanic.Replacement>()
            .filter { it.event == arcanum.api.EventPattern.TurnedFaceUp(arcanum.api.ObjectFilter.Is(Objects.Self)) }
            .flatMap { r -> r.modify.filterIsInstance<arcanum.api.Modification.AsTurnedFaceUp>().flatMap { it.steps } }
        if (steps.isNotEmpty()) Steps.run(Scope(act, arcanum.engine.action.Cause(controller, id, key = "turned-face-up:$id"), arcanum.engine.state.Bindings.EMPTY, null), steps)
    }

    /**
     * fin: "meld them into [name]" (CR 701.42a, 712.4): the two exiled cards of a meld pair, owned by one player, become one permanent
     * under that player's control with the combined back face up: the card whose back face is [KeywordAction.Meld.into] enters
     * transformed, the other one merged under it ([arcanum.engine.state.GameObject.mergedBelow]: both leave the battlefield together, as two
     * cards, CR 712.21). Any other pair stays where it is (CR 701.42c).
     */
    private fun meld(scope: Scope, action: arcanum.api.KeywordAction.Meld) {
        val act = scope.act
        val cards = scope.objects(action.objects).mapNotNull { act.state.obj(it) }.filter { it.zone == Zone.Exile && !it.token }.distinctBy { it.id }
        if (cards.size != 2 || cards[0].owner != cards[1].owner) return
        val top = cards.firstOrNull { it.definition.layout is Layout.Meld && it.definition.faces.getOrNull(1)?.name == action.into } ?: return
        val other = cards.first { it.id != top.id }
        val partner = (top.definition.layout as Layout.Meld).partner
        if (other.definition.layout !is Layout.Meld || other.definition.faces.firstOrNull()?.name != partner) return
        val entered = Actions.move(act, scope.cause, listOf(PlannedMove(top.id, Zone.Battlefield, controller = top.owner, transformed = true)))[top.id]
        val now = entered?.let { act.state.obj(it) }?.takeIf { it.zone == Zone.Battlefield } ?: return
        val lone = act.state.objOrThrow(other.id)
        act.record(Change.Removed(listOf(Placed(lone, act.state.zone(Zone.Exile, lone.owner).indexOf(lone.id))), listOf(Meaning.Merged)))
        val part = arcanum.engine.state.MergedPart(lone.id as ObjectId.Card, lone.owner, lone.definition, 0, lone.token, lone.copyOfCard)
        act.record(Change.ObjectUpdated(now, arcanum.engine.state.ObjectStatus.Merged(now.mergedAbove, now.mergedBelow + part).let { now.withStatus(it.key, it) }))
    }

    private fun copySpell(scope: Scope, effect: Effect.CopySpell) {
        val act = scope.act
        val controller = scope.players(effect.controller).firstOrNull() ?: scope.you
        val copies = mutableListOf<ObjectId>()
        scope.objects(effect.objects).forEach { id ->
            // epic: a spell that has left the Stack, as it last was there (CR 702.50a)
            val original = act.state.stackItems[id] ?: scope.names.values.values.filterIsInstance<NamedValue.Spell>()
                .firstOrNull { it.item.id == id }?.item ?: return@forEach
            // sos: "This spell can't be copied." (CR 707.10): no copy is created
            if (act.view()[id]?.has { it == Mechanic.CantBeCopied } == true) return@forEach
            // fin: "This ability can't be copied" (CR 707.10)
            if ((original.ability as? arcanum.api.Ability.Activated)?.rules?.cantBeCopied == true) return@forEach
            // inr chunk 09: a copy for each object the spell could target, each targeting only that object (CR 707.10d)
            effect.forEachCouldTarget?.let { each ->
                val instructions = arcanum.engine.action.Replacements.instructionsOf(original.ability)
                val slots = instructions?.targets.orEmpty() + original.modes.flatMap { m -> instructions?.modes?.options?.getOrNull(m)?.instructions?.targets.orEmpty() }
                    .filter { it.name in original.targets }
                val provisional = ObjectId.StackItem("copy:${act.state.nextTimestamp()}")
                val legal = scope.objects(each).filter { candidate ->
                    val ref = TargetRef.Object(candidate)
                    slots.isNotEmpty() && slots.all { s -> ref in Targeting.options(act, s, controller, original.source, original.names, self = provisional) }
                }
                // The copies go on the Stack in the order of their controller's choice (CR 707.10d, 405.3): first chosen, lowest
                act.order(controller, scope.self, legal, "Order the copies: the first one chosen goes on the Stack first").forEach { candidate ->
                    val ts = act.state.nextTimestamp()
                    val copyId = ObjectId.StackItem("copy:$ts")
                    copies += addCopy(scope, id, original, copyId, ts, controller, original.targets.mapValues { listOf(TargetRef.Object(candidate)) }, effect)
                }
                return@forEach
            }
            val ts = act.state.nextTimestamp()
            val copyId = ObjectId.StackItem("copy:$ts")
            var targets = original.targets
            if (effect.newTargets) {
                val instructions = arcanum.engine.action.Replacements.instructionsOf(original.ability)
                val slots = instructions?.targets.orEmpty() + original.modes.flatMap { m -> instructions?.modes?.options?.getOrNull(m)?.instructions?.targets.orEmpty() }
                // inr sweep: a later slot sees the copy's targets so far, new ones included ("another target creature", "target creature
                // that player controls", CR 707.10c, 115.3, 601.2c)
                val now = LinkedHashMap(targets)
                for ((slot, chosen) in targets) {
                    val s = slots.firstOrNull { it.name == slot } ?: continue
                    val names = now.entries.filter { it.key != slot }.fold(original.names) { b, (k, v) -> b.with(k, NamedValue.Targets(v)) }
                    val options = Targeting.options(act, s, controller, original.source, names, chosen = now, self = copyId)
                    // Asked only when another target could be chosen (a single other legal target counts)
                    now[slot] = if (options.all { it in chosen } || !act.yesNo(controller, copyId, "Choose new targets for the copy?")) chosen
                        else act.chooseTargets(controller, copyId, slot, options, chosen.size, chosen.size)
                }
                targets = now
            }
            copies += addCopy(scope, id, original, copyId, ts, controller, targets, effect)
        }
        scope.bind(effect.bind, NamedValue.Objects(copies))
    }

    /** Puts the copy [copyId] of [original] (the spell or ability [id]) with [targets] onto the Stack. */
    private fun addCopy(scope: Scope, id: ObjectId, original: StackItem, copyId: ObjectId, ts: Long, controller: PlayerId,
                        targets: Map<String, List<TargetRef>>, effect: Effect.CopySpell): ObjectId {
        val act = scope.act
        run {
            // spm: the copy's exceptions, after those of a copy it copies (CR 707.9b)
            val kind = if (original.kind == StackItemKind.Spell || original.kind is StackItemKind.SpellCopy) {
                val copied = (original.kind as? StackItemKind.SpellCopy)?.copied ?: id
                StackItemKind.SpellCopy(copied, (original.kind as? StackItemKind.SpellCopy)?.except.orEmpty() + effect.except,
                    // CR 707.10g, 709.5b: its faces, for the token it becomes even if that spell has left the Stack by then
                    values = (arcanum.engine.view.Copiable(act.state).values(copied) ?: (original.kind as? StackItemKind.SpellCopy)?.values)?.takeIf { it.faces.size > 1 })
            } else original.kind
            val snapshots = original.snapshots + listOfNotNull(act.snapshot(id))
            val copy = original.copy(id = copyId, kind = kind, controller = controller, targets = targets, snapshots = snapshots, timestamp = ts,
                cast = if (kind is StackItemKind.SpellCopy) copiedFacts(original.cast ?: act.state.obj(id)?.cast) else original.cast)
            act.record(Change.StackItemAdded(copy, act.state.stack.size))
            act.record(Change.Happened(GameEvent.Copied(controller, copyId)))
        }
        return copyId
    }

    /** CR 707.10: a copy of a spell copies X, the modes chosen and the additional and alternative costs paid (and the
     * objects used to pay them); it isn't cast, so it wasn't cast from anywhere and no mana was spent to cast it (the
     * Dawnglow Infusion example). */
    private fun copiedFacts(of: arcanum.engine.state.CastFacts?) = arcanum.engine.state.CastFacts(
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
            id = ObjectId.Card(TokenIds.token(TokenIds.created("emblem", ts), 0), 0), owner = player, controller = player,
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
            keptUntil = production.keptUntil, whenSpent = production.whenSpent, spellGetsIf = production.spellGetsIf, spellGetsUntil = production.spellGetsUntil) }
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
        var names = extra.entries.fold(scope.names) { n, (k, v) -> n.with(k, v) }
        // fin: "that player" of the trigger's event, for an effect that lasts after the trigger has resolved
        scope.event?.players?.takeIf { it.isNotEmpty() }?.let { names = names.with(arcanum.engine.query.Evaluator.EVENT_PLAYERS, NamedValue.Players(it)) }
        // inr chunk 05: and its objects, for an effect that names them after the trigger has resolved ("it doesn't untap during its
        // controller's next untap step", CR 608.2h)
        scope.event?.objects?.forEach { r -> names = names.with(arcanum.engine.query.Evaluator.eventObjects(r.role), NamedValue.Objects(r.ids)) }
        // gtc backfill: a copy of the object an event names ("become a copy of that card", the card put into a graveyard) is
        // found now and kept by name (D37): the event is gone when the copy effect is applied later (CR 707.2)
        val fixed = mechanics.map { fix(e, it) }.map { thisAbility(scope, it) }.map { m ->
            if (m !is Mechanic.CopyOf || !m.objects.toString().contains("EventObject")) return@map m
            val of = e.objects(m.objects).firstOrNull() ?: return@map m
            names = names.with(COPY_OF, NamedValue.Objects(listOf(of)))
            m.copy(objects = Objects.Bound(COPY_OF))
        }.map { m ->
            // fin: "that player gains control of …": the player the event names, found now (CR 608.2h) and kept by name
            if (m !is Mechanic.ControlledBy || !m.players.toString().contains("Event")) return@map m
            names = names.with(NEW_CONTROLLER, NamedValue.Players(e.players(m.players)))
            m.copy(players = arcanum.api.Players.Bound(NEW_CONTROLLER))
        }
        val layered = fixed.any { it.layer != null }
        // Objects named by the effect itself: its rules that aren't layer effects find them through a frozen name
        var affected = affects
        if (locked != null && affects == null) {
            names = names.with(LOCKED, NamedValue.Objects(locked))
            affected = Affected.Objects(Objects.Bound(LOCKED))
        }
        // fin: CR 400.7j: a target the effect moved to a public zone ("return target card to the battlefield. It gains haste") is found
        // as the new object
        val lockedObjects = locked ?: if (layered && affects is Affected.Objects) e.objects(affects.objects).map { id ->
            if (affects.objects is Objects.Target) arcanum.engine.query.Lki.newObject(act.state, id) ?: id else id
        } else null
        val lockedPlayers = (affects as? Affected.Players)?.let { e.players(it.players) }
        // CR 707.2b: a copy effect has the original's copiable values as it starts; changing the original later doesn't change it
        val copiedValues = fixed.filterIsInstance<Mechanic.CopyOf>().firstOrNull()?.let { copy ->
            arcanum.engine.view.Copiable(act.state).forCopyEffect(copy, scope.you, scope.self, names)
        }
        act.record(Changes.effect(act.state) { ts ->
            EffectObject(
                id = EffectId("E$ts"), mechanics = fixed, affects = affected, locked = lockedObjects, lockedPlayers = lockedPlayers,
                source = scope.self, controller = scope.you, timestamp = ts, createdTurn = act.state.turn.number, duration = duration,
                usesLeft = uses, amountLeft = amountLeft, names = names, copiedValues = copiedValues,
            )
        })
    }

    private const val LOCKED = "\$affected"
    private const val COPY_OF = "\$copy-of"
    private const val NEW_CONTROLLER = "\$new-controller"

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
        extra: Map<String, NamedValue> = emptyMap(), oneShot: Boolean = false, laterTurn: Boolean = false,
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
            // eoe: created as a permanent enters (CR 608.3g, warp): its source is the permanent, the object the entering one becomes
            source = scope.entering?.let { (it.id as? ObjectId.Card)?.next() } ?: scope.self, controller = scope.you, timestamp = ts,
            createdTurn = act.state.turn.number, names = names,
            // An object that already left (a countered spell) is kept as it last existed (CR 608.2h), not read later from
            // a log the host may have trimmed
            snapshots = refs.mapNotNull { act.snapshot(it) ?: arcanum.engine.query.Lki.find(act.state, it) },
            oneShot = oneShot, laterTurn = laterTurn,
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
                    val legal = Targeting.options(act, s, item.controller, item.source, item.names, self = id)
                    // mh2: "The new targets can't be you or a permanent you control"
                    val options = effect.notTo?.let { not -> legal.filter { ref -> !excluded(scope, not, ref) } } ?: legal
                    if (options.isEmpty() || (effect.optional && !act.yesNo(chooser, id, "Choose new targets?"))) chosen
                    // mh2: "reselect the targets at random" (CR 115.7d): the host picks, a die with a side for each legal target
                    else if (effect.atRandom) List(minOf(chosen.size, options.size)) { options[(act.random(chooser, id,
                        arcanum.engine.decision.RandomKind.Die(options.size, 1)) as Answer.DieRolls).results.single() - 1] }.distinct()
                    else act.chooseTargets(chooser, id, slot, options, chosen.size, chosen.size)
                }
            }
            if (targets != item.targets) act.record(Change.StackItemUpdated(item, item.copy(targets = targets)))
        }
    }

    /** mh2: whether [ref] matches [filter] (an object its objects, a player its players). */
    private fun excluded(scope: Scope, filter: arcanum.api.RecipientFilter, ref: TargetRef): Boolean {
        val e = scope.eval()
        return when (ref) {
            is TargetRef.Object -> filter.objects?.let { e.matches(it, ref.id) } == true
            is TargetRef.Player -> filter.players?.let { e.matches(it, ref.id) } == true
        }
    }

    private fun separatePiles(scope: Scope, effect: Effect.SeparatePiles) {
        val act = scope.act
        val ids = scope.objects(effect.objects)
        val separator = scope.players(effect.separator).firstOrNull() ?: scope.actor
        val chooser = scope.players(effect.chooser).firstOrNull() ?: scope.actor
        act.show(separator, ids)
        val first = act.chooseObjects(separator, scope.self, ids, 0, ids.size, if (effect.faceDownPile) "Face-up pile" else "First pile")
        val second = ids.filter { it !in first }
        // hob: one pile face down: the face-up pile is shown to everyone, the other stays hidden (CR 701.20)
        if (effect.faceDownPile && first.isNotEmpty()) {
            act.show(separator, first, public = true)
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
        val before = o.linked[key]
        val old = (before as? NamedValue.Objects)?.ids.orEmpty()
        val after = NamedValue.Objects(if (replace) ids else (old + ids).distinct())
        if (after != before) act.record(Change.Memory(self, key, before, after))
    }

    private fun forget(scope: Scope, key: String, ids: List<ObjectId>?) {
        val act = scope.act
        val self = scope.self ?: return
        val o = act.state.obj(self) ?: return
        val before = o.linked[key] ?: return
        val after = if (ids == null) null else (before as? NamedValue.Objects)?.let { NamedValue.Objects(it.ids - ids.toSet()) }
        act.record(Change.Memory(self, key, before, after))
    }

    /** CR 725.1, 726.1: one monarch, one player with the initiative. */
    private fun designate(scope: Scope, player: PlayerId, designation: Designation) {
        val act = scope.act
        act.state.players.filter { it.id != player && designation in it.designations }.forEach {
            act.record(arcanum.engine.change.Changes.designation(it.id, designation, false))
        }
        if (designation !in act.state.player(player).designations) act.record(arcanum.engine.change.Changes.designation(player, designation, true))
        // CR 726.2, 726.5: taking the initiative triggers, also for the player who has it
        if (designation == Designation.Initiative) act.record(Change.Happened(GameEvent.TookInitiative(player)))
    }

    // ---- Keyword actions (CR 701)

    /**
     * Conniving (CR 701.50): one permanent at a time. CR 701.50c: several at once: the first player in APNAP order who controls (or last
     * controlled, CR 701.50b) one of them chooses which connives next, until each has. CR 701.50e: conniving 0 is no event, nothing happens.
     */
    private fun connive(scope: Scope, action: KeywordAction.Connive) {
        val act = scope.act
        if (scope.value(action.count) <= 0) return
        var left = scope.objects(action.objects).distinct()
        while (true) {
            val owned = left.mapNotNull(lastControlled(scope))
            val chooser = act.state.apnap().firstOrNull { p -> owned.any { it.first == p } } ?: owned.firstOrNull()?.first ?: break
            val theirs = owned.filter { it.first == chooser }.flatMap { it.second }
            // Ones that left get no counter, and their controller draws and discards the same whichever goes first, so nothing is asked about them alone
            val conniver = if (theirs.size == 1 || theirs.none { act.state.obj(it)?.zone == Zone.Battlefield }) theirs.first()
                else act.chooseObjects(chooser, scope.self, theirs, 1, 1, "Which one connives next?").single()
            conniveOne(scope, action, conniver)
            left = left - conniver
        }
    }

    /**
     * One permanent connives, with its replacements (CR 614.1a, 701.50a; msh): the first "If a creature you control would connive, instead …"
     * that applies to it does the replacement's instructions instead, with it as the event's object; a replacement doesn't apply again to the
     * connive it brings (CR 614.5). Otherwise the plain expansion runs for it alone and it has connived (CR 701.50f).
     */
    private fun conniveOne(scope: Scope, action: KeywordAction.Connive, id: ObjectId) {
        val act = scope.act
        val applied = act.view().game.rules.firstOrNull { a ->
            val m = a.mechanic as? arcanum.api.Mechanic.Replacement ?: return@firstOrNull false
            val event = m.event as? arcanum.api.EventPattern.Connive ?: return@firstOrNull false
            m.instead != null && scope.names[conniveGuard(a)] == null &&
                Evaluator(act.board(), arcanum.engine.query.Context(a.controller, a.source, a.names)).matches(event.objects, id)
        }
        if (applied == null) {
            val one = scope.names.with(CONNIVER, NamedValue.Objects(listOf(id)))
            val single = action.copy(objects = Objects.Bound(CONNIVER))
            val inner = Scope(act, scope.cause, one, scope.event, scope.actor)
            val done = doers(inner, single)
            Steps.run(inner, KeywordExpansions.expand(single)!!)
            done.forEach { (player, objects) -> act.record(Change.Happened(GameEvent.KeywordActionDone(KeywordActionKind.Connive, player, objects))) }
            return
        }
        val m = applied.mechanic as arcanum.api.Mechanic.Replacement
        val event = arcanum.engine.state.TriggerEvent(listOf(arcanum.engine.state.RoleObjects(arcanum.api.EventRole.Object, listOf(id))), listOfNotNull(act.controllerOf(id)))
        val names = scope.names.with(applied.names).with(conniveGuard(applied), NamedValue.Number(1))
        Steps.run(Scope(act, Cause(applied.controller, applied.source, key = "connive:$id"), names, event), m.instead!!.steps)
    }

    private fun conniveGuard(applied: arcanum.engine.view.AppliedMechanic) = "\$connive-replaced:${applied.source}:${applied.origin?.key}"
    private const val CONNIVER = "\$conniver"

    /** ecl: the creatures [player] could blight (CR 701.68a): creatures they control. */
    fun blightable(scope: Scope, player: arcanum.engine.state.PlayerId): List<ObjectId> {
        val e = scope.eval()
        return scope.act.state.battlefield.filter { e.controllerOf(it) == player && e.characteristics(it)?.isType(arcanum.api.CardType.Creature) == true &&
            scope.act.state.obj(it)?.phasedOut != true }
    }

    /** fra: what [player] can behold (CR 701.4a): matching cards in their hand and permanents they control. */
    fun beholdable(scope: Scope, player: arcanum.engine.state.PlayerId, filter: arcanum.api.ObjectFilter): List<ObjectId> {
        val e = scope.eval()
        val state = scope.act.state
        return (state.player(player).hand + state.battlefield.filter { e.controllerOf(it) == player }).filter { e.matches(filter, it) }
    }

    /**
     * Does [action], then records that it is done ([GameEvent.KeywordActionDone]), for every verb, so a new verb needs nothing here. Who did it
     * and with what is worked out first (an amount can change as the action happens). A few say so themselves, as they know more:
     * [KeywordAction.Explore], [KeywordAction.Connive] and [KeywordAction.Blight] per permanent, as each is done; [KeywordAction.ManifestDread] per player;
     * [KeywordAction.Airbend], [KeywordAction.Earthbend], [KeywordAction.Forage] and [KeywordAction.CollectEvidence] only when they happened
     * (CR 701.65b, 701.66b, 701.61a, 701.59b);
     * [KeywordAction.Foretell] with the card as it is in exile (CR 702.143c).
     */
    private fun keywordAction(scope: Scope, action: KeywordAction, bind: String?) {
        // Connive goes one permanent at a time and says so for each (CR 701.50c)
        if (action is KeywordAction.Connive) return connive(scope, action)
        val done = when (action) {
            is KeywordAction.Explore, is KeywordAction.Blight, is KeywordAction.ManifestDread, is KeywordAction.Airbend, is KeywordAction.Earthbend,
            KeywordAction.Forage, KeywordAction.Foretell, is KeywordAction.CollectEvidence -> emptyList()
            else -> doers(scope, action)
        }
        performKeywordAction(scope, action, bind)
        done.forEach { (player, objects) -> scope.act.record(Change.Happened(GameEvent.KeywordActionDone(action.kind, player, objects))) }
    }

    /** One [GameEvent.KeywordActionDone] each: who does [action], and to or with what. */
    private fun doers(scope: Scope, action: KeywordAction): List<Pair<PlayerId, List<ObjectId>>> = when (action) {
        // CR 701.16a: investigating twice is investigating, then investigating again
        is KeywordAction.Investigate -> List(scope.value(action.count).coerceAtLeast(0)) { scope.actor to emptyList() }
        // CR 701.50e: conniving 0 is no event; CR 701.50b: one that has left connives as it last existed
        is KeywordAction.Connive -> if (scope.value(action.count) <= 0) emptyList() else scope.objects(action.objects).distinct().mapNotNull(lastControlled(scope))
        // CR 701.63b: enduring 0 is nothing
        is KeywordAction.Endure -> if (scope.value(action.count) <= 0) emptyList() else scope.objects(action.objects).distinct().mapNotNull(lastControlled(scope))
        // CR 701.43c: only what is on the battlefield can be exerted
        is KeywordAction.Exert -> onBattlefield(scope, action.objects).mapNotNull(lastControlled(scope))
        is KeywordAction.Goad -> onBattlefield(scope, action.objects).map { scope.actor to listOf(it) }
        is KeywordAction.Detain -> onBattlefield(scope, action.objects).map { scope.actor to listOf(it) }
        is KeywordAction.Regenerate -> onBattlefield(scope, action.objects).map { scope.actor to listOf(it) }
        is KeywordAction.Monstrosity, is KeywordAction.Adapt ->
            listOfNotNull(scope.self?.takeIf { scope.act.state.obj(it)?.zone == Zone.Battlefield }).mapNotNull(lastControlled(scope))
        else -> listOf(scope.actor to emptyList())
    }

    /** [id] with who controls it, or who last did if it has left (CR 701.44c, 701.50b). */
    private fun lastControlled(scope: Scope): (ObjectId) -> Pair<PlayerId, List<ObjectId>>? = { id ->
        (scope.act.controllerOf(id) ?: arcanum.engine.query.Lki.find(scope.act.state, id)?.let { it.controller ?: it.owner })?.let { it to listOf(id) }
    }

    private fun performKeywordAction(scope: Scope, action: KeywordAction, bind: String?) {
        val act = scope.act
        // tla: CR 701.65a: exile them; each card exiled may be cast by its owner for {2} for as long as it stays exiled
        if (action is KeywordAction.Airbend) return airbend(scope, action, bind)
        // tla: CR 701.66a, b: the land, its counters and the delayed return; earthbending happens as that trigger is created
        if (action is KeywordAction.Earthbend) {
            KeywordExpansions.expand(action)?.let { Steps.run(scope, it) }
            val land = scope.objects(arcanum.api.Objects.Target(action.slot))
            scope.bind(bind, NamedValue.Objects(land))
            if (land.isNotEmpty()) act.record(Change.Happened(GameEvent.KeywordActionDone(KeywordActionKind.Earthbend, scope.actor, land)))
            return
        }
        // mkm chunk 02: what a keyword action is made of is done by the player doing it, its "you" (CR 701.16a: "Each player who controls the most
        // creatures investigates": each of them creates their own Clue, CR 111.2)
        KeywordExpansions.expand(action)?.let { steps ->
            if (scope.actor == scope.you) return Steps.run(scope, steps)
            val performed = Scope(scope.act, scope.cause.copy(controller = scope.actor), scope.names, scope.event, scope.actor, scope.batch, scope.entering)
            Steps.run(performed, steps)
            scope.names = performed.names
            return
        }
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
                val values = Actions.tokenValues(act, scope.eval(), arcanum.api.TokenSpec.CopyOf(Objects.Bound(POPULATED)))
                scope.bind(bind, NamedValue.Objects(Actions.createTokens(act, cause, values, 1, scope.actor)))
            }
            // CR 701.44c: one no longer on the battlefield explores too, as it last existed. CR 701.44d: several at once: the first player in APNAP
            // order who controls (or last controlled) one of them chooses which explores next, until each has
            is KeywordAction.Explore -> {
                var left = scope.objects(action.objects).distinct().filter { act.state.obj(it).let { o -> o == null || o.zone == Zone.Battlefield } }
                while (true) {
                    val owned = left.mapNotNull(lastControlled(scope))
                    val chooser = act.state.apnap().firstOrNull { p -> owned.any { it.first == p } } ?: owned.firstOrNull()?.first ?: break
                    val theirs = owned.filter { it.first == chooser }.flatMap { it.second }
                    // Ones that left get no counter and reveal the same cards whichever goes first, so nothing is asked about them alone
                    val explorer = if (theirs.none { act.state.obj(it)?.zone == Zone.Battlefield }) theirs.first()
                        else act.chooseObjects(chooser, scope.self, theirs, 1, 1, "Which one explores next?").single()
                    explore(scope, explorer, chooser)
                    left = left - explorer
                }
            }
            // CR 701.47a: put counters on an Army you control, or create a 0/0 Army token first
            is KeywordAction.Amass -> {
                val e = scope.eval()
                val armies = act.state.battlefield.filter { e.controllerOf(it) == scope.actor &&
                    e.characteristics(it)?.hasSubtype(arcanum.api.Subtype("Army")) == true }
                val army = if (armies.isEmpty()) Actions.createTokens(act, cause, arcanum.engine.state.CopiableValues.of(FaceDefinition("${action.subtype.name} Army Token",
                    colorIndicator = listOf(Color.Black), types = listOf(CardType.Creature),
                    subtypes = listOf(action.subtype, arcanum.api.Subtype("Army")), power = arcanum.api.PrintedNumber.Fixed(0),
                    toughness = arcanum.api.PrintedNumber.Fixed(0))), 1, scope.actor).first()
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
                if (scope.you !in o.goadedBy) act.record(Change.ObjectUpdated(o, arcanum.engine.state.ObjectStatus.Goaded(o.goadedBy + scope.you).let { o.withStatus(it.key, it) }))
            }
            // CR 701.37a: if it isn't monstrous, put N +1/+1 counters on it and it becomes monstrous
            is KeywordAction.Monstrosity -> scope.self?.takeIf { act.state.obj(it)?.zone == Zone.Battlefield }?.let { id ->
                if (ObjectMark.Monstrous in act.state.objOrThrow(id).marks) return
                val n = scope.value(action.count)
                Actions.putCounters(act, cause, TargetRef.Object(id), CounterKind.PowerToughness(1, 1), n)
                // CR 701.37c: what other abilities of it mean by X, as it became monstrous
                Actions.tapStatus(act, id, StatusFlag.Marked(ObjectMark.Monstrous), true, listOf(Meaning.CostX(n)))
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
                act.record(Change.Happened(GameEvent.KeywordActionDone(KeywordActionKind.Forage, scope.actor)))
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
                    act.record(Change.Happened(GameEvent.KeywordActionDone(KeywordActionKind.Blight, p, listOf(chosen))))
                    listOf(chosen)
                }
                scope.bind(bind, NamedValue.Objects(blighted))
            }
            // CR 701.54: the Ring tempts the player doing it
            KeywordAction.TheRingTemptsYou -> arcanum.engine.ring.TheRing.tempt(act, cause, scope.actor)
            // CR 701.49: venture into the dungeon, or into [quality] (701.49d)
            KeywordAction.Venture -> arcanum.engine.dungeon.Dungeons.venture(act, cause, scope.actor, null)
            is KeywordAction.VentureInto -> arcanum.engine.dungeon.Dungeons.venture(act, cause, scope.actor, action.dungeons)
            is KeywordAction.Meld -> meld(scope, action)
            // CR 702.143a, b: exile this card from its owner's hand face down; it becomes foretold
            KeywordAction.Foretell -> scope.self?.let { id -> scope.bind(bind, NamedValue.Objects(listOfNotNull(arcanum.engine.foretell.Foretell.exile(act, cause, id)))) }
            // Done above by what they are made of ([KeywordExpansions.expand]) or their own code
            is KeywordAction.Investigate, is KeywordAction.Connive, is KeywordAction.Discover, KeywordAction.Learn, is KeywordAction.Bolster, is KeywordAction.Support,
            is KeywordAction.Endure, is KeywordAction.EmpowerJace, KeywordAction.Recruit, is KeywordAction.Airbend, is KeywordAction.Earthbend ->
                error("$action is done by its expansion")
            // CR 701.59a: exile any number of cards from your graveyard with total mana value N or greater: one selection, exiled together
            // (a cost on casting or activating is the exile it is, casting's Costs.asCostPart); CR 701.59b: with no way to reach N, none
            is KeywordAction.CollectEvidence -> {
                val needed = scope.value(action.amount)
                val graveyard = act.state.player(scope.actor).graveyard.toList()
                val property = arcanum.api.NumericProperty.ManaValue
                val e = scope.eval()
                if (graveyard.sumOf { e.property(property, it) } < needed) scope.bind(bind, NamedValue.Objects(emptyList()))
                else {
                    val chosen = act.chooseReaching(scope.actor, scope.self, graveyard, if (needed > 0) 1 else 0,
                        arcanum.api.Total(property, arcanum.api.Value.Fixed(needed)), "Exile to collect evidence $needed")
                    val moved = Actions.moveTo(act, cause, chosen, Zone.Exile)
                    scope.bind(bind, NamedValue.Objects(chosen.mapNotNull { moved[it] }))
                    act.record(Change.Happened(GameEvent.KeywordActionDone(KeywordActionKind.CollectEvidence, scope.actor)))
                }
            }
        }
    }

    /** CR 701.44a: [controller] reveals their top card; a land goes to their hand, otherwise a +1/+1 counter on [explorer] (none if it has left, CR
     * 701.44c) and they may put the card into their graveyard. CR 701.44b: it has explored once that is done, even if some of it was impossible. */
    private fun explore(scope: Scope, explorer: ObjectId, controller: PlayerId) {
        val act = scope.act
        fun counter() { if (act.state.obj(explorer)?.zone == Zone.Battlefield) Actions.putCounters(act, scope.cause, TargetRef.Object(explorer), CounterKind.PowerToughness(1, 1), 1) }
        val top = act.state.player(controller).library.firstOrNull()
        if (top == null) counter()
        else {
            act.show(controller, listOf(top), public = true)
            act.record(Change.Happened(GameEvent.Revealed(controller, listOf(top))))
            if (scope.eval().characteristics(top)?.isType(CardType.Land) == true) Actions.moveTo(act, scope.cause, listOf(top), Zone.Hand)
            else {
                counter()
                if (act.yesNo(controller, explorer, "Put it into your graveyard?")) Actions.moveTo(act, scope.cause, listOf(top), Zone.Graveyard)
            }
        }
        act.record(Change.Happened(GameEvent.KeywordActionDone(KeywordActionKind.Explore, controller, listOf(explorer))))
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
        act.show(player, top)
        val chosen = if (top.size <= 1) top else act.chooseObjects(player, scope.self, top, 1, 1, "Manifest which card?")
        val moved = if (chosen.isEmpty()) emptyMap() else Actions.move(act, scope.cause, chosen.map {
            PlannedMove(it, Zone.Battlefield, controller = player, faceDown = true)
        })
        val manifested = chosen.firstOrNull()?.let { moved[it] }?.takeIf { act.state.obj(it)?.zone == Zone.Battlefield }
        manifested?.let { Actions.tapStatus(act, it, StatusFlag.Marked(ObjectMark.Manifested), true) }
        val rest = top.filterNot { it in chosen }.filter { act.state.obj(it) != null }
        val graveyard = Actions.moveTo(act, scope.cause, rest, Zone.Graveyard)
        act.record(Change.Happened(GameEvent.ManifestedDread(player, manifested, graveyard.values.toList())))
        act.record(Change.Happened(GameEvent.KeywordActionDone(KeywordActionKind.ManifestDread, player, listOfNotNull(manifested))))
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
        act.record(Change.Happened(GameEvent.KeywordActionDone(KeywordActionKind.Airbend, scope.actor, exiled)))
    }

    private val BEFORE_COMBAT = setOf(arcanum.api.TurnStep.Untap, arcanum.api.TurnStep.Upkeep, arcanum.api.TurnStep.Draw,
        arcanum.api.TurnStep.PrecombatMain)
    private const val POPULATED = "\$populated"
    private const val EXERTED = "\$exerted"
}

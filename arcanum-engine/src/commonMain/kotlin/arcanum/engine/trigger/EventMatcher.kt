package arcanum.engine.trigger

import arcanum.api.EventPattern
import arcanum.api.EventRole
import arcanum.api.ObjectFilter
import arcanum.api.PlayerFilter
import arcanum.api.RecipientFilter
import arcanum.api.Zone
import arcanum.engine.change.Change
import arcanum.engine.change.GameEvent
import arcanum.engine.change.Meaning
import arcanum.engine.change.Move
import arcanum.engine.query.Evaluator
import arcanum.engine.state.ObjectId
import arcanum.engine.state.PlayerId
import arcanum.engine.state.RoleObjects
import arcanum.engine.state.StackItemKind
import arcanum.engine.state.StatusFlag
import arcanum.engine.state.TargetRef
import arcanum.engine.state.TriggerEvent

/**
 * Matches tagged events (D33) against [EventPattern]s: what triggers wait for (CR 603.2), what replacement effects
 * replace (CR 614), and what "this turn" questions count. Each matching part of a change is one instance, with its
 * objects by role, its players and its amount ([TriggerEvent]).
 *
 * Filters on an object leaving the battlefield (or another public zone) are tested on the object as it was (CR
 * 603.10a), through the evaluator's last known information; on an object entering, as it is now. For a [proposed]
 * event (a replacement deciding whether it applies, CR 614.12), objects are tested as they are before the event.
 */
internal object EventMatcher {
    fun instances(e: Evaluator, pattern: EventPattern, change: Change, proposed: Boolean = false, door: Int? = null): List<TriggerEvent> = when (pattern) {
        is EventPattern.ZoneChange -> moves(change).filter { m ->
            (pattern.from == null || m.from == pattern.from) && (pattern.to == null || m.to == pattern.to) &&
                (pattern.notTo == null || m.to != pattern.notTo) &&
                (!pattern.resolving || Meaning.Resolved in m.meanings) &&
                e.matches(pattern.objects, tested(m, pattern.from, proposed))
        }.map { moveEvent(e, it, proposed, pattern.from) }
        is EventPattern.Cast -> if (isCast(change)) {
            val item = (change as Change.StackItemAdded).item
            val from = e.board.state.obj(item.id)?.facts?.castFrom
            if (e.matches(pattern.spell, item.id) && e.matches(pattern.by, item.controller) && (pattern.from == null || from == pattern.from))
                listOf(TriggerEvent(listOf(RoleObjects(EventRole.Object, listOf(item.id))), listOf(item.controller))) else emptyList()
        } else emptyList()
        is EventPattern.Activate -> ((change as? Change.Happened)?.event as? GameEvent.Activated)?.let { a ->
            if ((pattern.manaAbilities || !a.manaAbility) && e.matches(pattern.source, a.source) && e.matches(pattern.by, a.player))
                listOf(TriggerEvent(listOf(RoleObjects(EventRole.Object, listOf(a.source))), listOf(a.player))) else null
        }.orEmpty()
        is EventPattern.Damage -> (change as? Change.Damage)?.dealt.orEmpty().filter { d ->
            !pattern.excess && (pattern.combat == null || pattern.combat == d.combat) &&
                e.matches(pattern.source, d.source) && recipient(e, pattern.to, d.recipient)
        }.map { d ->
            TriggerEvent(
                objects = listOf(RoleObjects(EventRole.Source, listOf(d.source))) +
                    listOfNotNull((d.recipient as? TargetRef.Object)?.let { RoleObjects(EventRole.Object, listOf(it.id)) }),
                players = listOfNotNull((d.recipient as? TargetRef.Player)?.id),
                amount = d.amount,
            )
        }
        is EventPattern.GainLife -> life(e, change, pattern.players, Meaning.LifeGained)
        is EventPattern.LoseLife -> life(e, change, pattern.players, Meaning.LifeLost)
        is EventPattern.Draw -> moves(change).filter { Meaning.Drew in it.meanings && e.matches(pattern.players, it.before.owner) }
            .map { TriggerEvent(listOf(RoleObjects(EventRole.Object, listOf(it.after.id))), listOf(it.before.owner)) }
        is EventPattern.Discard -> moves(change).filter {
            Meaning.Discarded in it.meanings && e.matches(pattern.players, it.before.owner) && e.matches(pattern.cards, it.before.id)
        }.map { m -> moveEvent(e, m, proposed, Zone.Hand, EventRole.Before) }
        is EventPattern.Sacrifice -> moves(change).filter { Meaning.Sacrificed in it.meanings && e.matches(pattern.objects, it.before.id) }
            .map { m -> moveEvent(e, m, proposed, Zone.Battlefield, EventRole.Before) }
        is EventPattern.Destroyed -> moves(change).filter { Meaning.Destroyed in it.meanings && e.matches(pattern.objects, it.before.id) }
            .map { m -> moveEvent(e, m, proposed, Zone.Battlefield, EventRole.Before) }
        is EventPattern.Countered -> moves(change).filter { Meaning.Countered in it.meanings && e.matches(pattern.objects, it.before.id) }
            .map { m -> moveEvent(e, m, proposed, Zone.Stack, EventRole.Before) } +
            ((change as? Change.StackItemRemoved)?.takeIf { Meaning.Countered in it.meanings && e.matches(pattern.objects, it.item.id) }
                ?.let { listOf(TriggerEvent(listOf(RoleObjects(EventRole.Object, listOf(it.item.id))), listOf(it.item.controller))) }.orEmpty())
        is EventPattern.PlayLand -> moves(change).filter { Meaning.PlayedLand in it.meanings && e.matches(pattern.lands, it.after.id) &&
            e.matches(pattern.players, it.after.controller ?: it.after.owner) }
            .map { TriggerEvent(listOf(RoleObjects(EventRole.Object, listOf(it.after.id))), listOf(it.after.controller ?: it.after.owner)) }
        is EventPattern.CountersPut -> if (causedBy(e, change, pattern.causedBy)) counters(e, change, pattern.objects, pattern.kind, put = true, last = false) else emptyList()
        is EventPattern.CountersRemoved -> counters(e, change, pattern.objects, pattern.kind, put = false, last = pattern.last)
        is EventPattern.PlayerCountersPut -> (change as? Change.Counters)?.let { c ->
            val holder = c.holder as? TargetRef.Player ?: return@let null
            val delta = (c.after?.count ?: 0) - (c.before?.count ?: 0)
            if (delta > 0 && (pattern.kind == null || pattern.kind == c.kind) && e.matches(pattern.players, holder.id) && causedBy(e, change, pattern.causedBy))
                listOf(TriggerEvent(players = listOf(holder.id), amount = delta)) else null
        }.orEmpty()
        is EventPattern.Tap -> (change as? Change.Status)?.takeIf { it.flag == StatusFlag.Tapped && !it.before && it.after }?.let { c ->
            val forMana = Meaning.ManaAdded in c.meanings
            if ((pattern.forMana == null || pattern.forMana == forMana) && e.matches(pattern.objects, c.id))
                listOf(TriggerEvent(listOf(RoleObjects(EventRole.Object, listOf(c.id))), listOfNotNull(e.controllerOf(c.id)))) else null
        }.orEmpty()
        is EventPattern.Untap -> (change as? Change.Status)?.takeIf { it.flag == StatusFlag.Tapped && it.before && !it.after }?.let { c ->
            if (e.matches(pattern.objects, c.id)) listOf(TriggerEvent(listOf(RoleObjects(EventRole.Object, listOf(c.id))))) else null
        }.orEmpty()
        is EventPattern.Attack -> attacks(e, change, pattern)
        is EventPattern.Block -> blocks(e, change, pattern)
        is EventPattern.BecomesBlocked -> becomesBlocked(e, change, pattern)
        // CR 509.3g: when blockers are declared and the attacker has none (CR 509.1h)
        is EventPattern.AttacksUnblocked -> (change as? Change.Combat)?.takeIf { it.after?.blocksDecided == true && it.before?.blocksDecided != true }?.after
            ?.attackers.orEmpty().filter { !it.blocked && e.matches(pattern.attacker, it.id) }
            .map { TriggerEvent(listOf(RoleObjects(EventRole.Object, listOf(it.id))), listOfNotNull(defendingPlayer(e, it.target))) }
        is EventPattern.BecomesTarget -> targeted(change).flatMap { (item, refs) ->
            refs.filterIsInstance<TargetRef.Object>().map { it.id }.distinct()
                .filter { e.matches(pattern.objects, it) && e.matches(pattern.by, item) }
                .map { TriggerEvent(listOf(RoleObjects(EventRole.Object, listOf(it)), RoleObjects(EventRole.Source, listOf(item)))) }
        }
        is EventPattern.CreateToken -> (change as? Change.Created)?.takeIf { Meaning.TokenCreated in it.meanings }?.objects.orEmpty()
            .filter { e.matches(pattern.tokens, it.obj.id) }
            .map { TriggerEvent(listOf(RoleObjects(EventRole.Object, listOf(it.obj.id))), listOfNotNull(it.obj.controller ?: it.obj.owner)) }
        is EventPattern.Transform -> (change as? Change.Face)?.takeIf { Meaning.Transformed in it.meanings && e.matches(pattern.objects, it.id) }
            ?.let { listOf(TriggerEvent(listOf(RoleObjects(EventRole.Object, listOf(it.id))))) }.orEmpty()
        is EventPattern.TurnedFaceUp -> (change as? Change.Status)?.takeIf {
            it.flag == StatusFlag.FaceDown && it.before && !it.after && e.matches(pattern.objects, it.id)
        }?.let { listOf(TriggerEvent(listOf(RoleObjects(EventRole.Object, listOf(it.id))))) }.orEmpty()
        is EventPattern.Search -> ((change as? Change.Happened)?.event as? GameEvent.Searched)
            ?.takeIf { e.matches(pattern.players, it.player) }?.let { listOf(TriggerEvent(players = listOf(it.player))) }.orEmpty()
        is EventPattern.StepBegins -> (change as? Change.Step)?.takeIf {
            Meaning.StepBegan in it.meanings && it.after.step == pattern.step && e.matches(pattern.whose, it.after.active)
        }?.let { listOf(TriggerEvent(players = listOf(it.after.active))) }.orEmpty()
        is EventPattern.StepEnds -> (change as? Change.Step)?.takeIf {
            it.before.step == pattern.step && (it.after.step != it.before.step || it.after.number != it.before.number) &&
                e.matches(pattern.whose, it.before.active)
        }?.let { listOf(TriggerEvent(players = listOf(it.before.active))) }.orEmpty()
        is EventPattern.Copy -> ((change as? Change.Happened)?.event as? GameEvent.Copied)
            ?.takeIf { e.matches(pattern.by, it.player) && e.matches(pattern.spell, it.copy) }
            ?.let { listOf(TriggerEvent(listOf(RoleObjects(EventRole.Object, listOf(it.copy))), listOf(it.player))) }.orEmpty()
        is EventPattern.Scry -> ((change as? Change.Happened)?.event as? GameEvent.Scried)
            ?.takeIf { e.matches(pattern.players, it.player) }?.let { listOf(TriggerEvent(players = listOf(it.player), amount = it.count)) }.orEmpty()
        is EventPattern.Surveil -> ((change as? Change.Happened)?.event as? GameEvent.Surveilled)
            ?.takeIf { e.matches(pattern.players, it.player) }?.let { listOf(TriggerEvent(players = listOf(it.player), amount = it.count)) }.orEmpty()
        // CR 700.13: targeting an opponent, or a spell, ability or permanent an opponent controls
        is EventPattern.CommitCrime -> targeted(change).mapNotNull { (item, refs) ->
            val player = e.controllerOf(item) ?: return@mapNotNull null
            val crime = refs.any { ref ->
                when (ref) {
                    is TargetRef.Player -> ref.id != player
                    is TargetRef.Object -> (e.controllerOf(ref.id) ?: e.ownerOf(ref.id)).let { it != null && it != player }
                }
            }
            if (crime && e.matches(pattern.players, player)) TriggerEvent(players = listOf(player)) else null
        }
        is EventPattern.Attach -> (change as? Change.Attached)?.takeIf { it.after != null && it.after != it.before }?.let { c ->
            val to = (c.after as? TargetRef.Object)?.id
            if (e.matches(pattern.objects, c.id) && (to == null || e.matches(pattern.to, to)))
                listOf(TriggerEvent(listOf(RoleObjects(EventRole.Object, listOf(c.id))))) else null
        }.orEmpty()
        is EventPattern.Unattach -> unattached(e, change, pattern)
        is EventPattern.RollDie -> ((change as? Change.Happened)?.event as? GameEvent.DieRolled)
            ?.takeIf { e.matches(pattern.players, it.player) }?.let { listOf(TriggerEvent(players = listOf(it.player), amount = it.results.sum())) }.orEmpty()
        is EventPattern.FlipCoin -> ((change as? Change.Happened)?.event as? GameEvent.CoinFlipped)
            ?.takeIf { e.matches(pattern.players, it.player) }?.let { listOf(TriggerEvent(players = listOf(it.player))) }.orEmpty()
        // CR 702.29c: the card discarded to pay a cycling cost, seen as it was in the hand
        is EventPattern.Cycle -> moves(change).filter {
            Meaning.Cycled in it.meanings && e.matches(pattern.players, it.before.owner) &&
                (e.matches(pattern.cards, it.before.id) || e.matches(pattern.cards, it.after.id))
        }.map { m -> moveEvent(e, m, proposed, Zone.Hand, EventRole.Before) }
        // CR 702.122e: "becomes crewed" is a crew ability of it resolving
        is EventPattern.Crewed -> (change as? Change.StackItemRemoved)?.takeIf { Meaning.Resolved in it.meanings }?.item
            ?.takeIf { it.kind == arcanum.engine.state.StackItemKind.Activated }?.let { item ->
                val vehicle = item.source ?: return@let null
                val expansion = item.origin as? arcanum.engine.view.AbilityOrigin.Expansion ?: return@let null
                val keyword = e.characteristics(vehicle)?.abilities?.firstOrNull { it.origin == expansion.keyword }?.ability
                if (keyword is arcanum.api.Keyword.Crew && e.matches(pattern.objects, vehicle))
                    listOf(TriggerEvent(listOf(RoleObjects(EventRole.Object, listOf(vehicle))), listOf(item.controller))) else null
            }.orEmpty()
        // CR 702.171b: it becomes saddled (the saddle ability resolving marks it)
        is EventPattern.Saddled -> (change as? Change.Status)?.takeIf {
            it.flag == StatusFlag.Marked(arcanum.api.ObjectMark.Saddled) && !it.before && it.after && e.matches(pattern.objects, it.id)
        }?.let { listOf(TriggerEvent(listOf(RoleObjects(EventRole.Object, listOf(it.id))))) }.orEmpty()
        // CR 700.14: the mana a player spent to cast spells this turn reaches the amount with this payment
        is EventPattern.Expend -> ((change as? Change.Happened)?.event as? GameEvent.SpentOnSpells)
            ?.takeIf { it.before < pattern.amount && it.after >= pattern.amount && e.matches(pattern.players, it.player) }
            ?.let { listOf(TriggerEvent(players = listOf(it.player), amount = pattern.amount)) }.orEmpty()
        // Day and night aren't tracked yet.
        is EventPattern.DayNightChanges -> emptyList()
        // blb: CR 702.174c, 701.61a, 716.2a
        is EventPattern.GiveGift -> ((change as? Change.Happened)?.event as? GameEvent.GaveGift)
            ?.takeIf { e.matches(pattern.players, it.player) }?.let { listOf(TriggerEvent(players = listOf(it.player))) }.orEmpty()
        is EventPattern.Forage -> ((change as? Change.Happened)?.event as? GameEvent.Foraged)
            ?.takeIf { e.matches(pattern.players, it.player) }?.let { listOf(TriggerEvent(players = listOf(it.player))) }.orEmpty()
        is EventPattern.BecomesLevel -> ((change as? Change.Happened)?.event as? GameEvent.BecameLevel)
            ?.takeIf { it.level == pattern.level && e.matches(pattern.objects, it.id) }
            ?.let { listOf(TriggerEvent(listOf(RoleObjects(EventRole.Object, listOf(it.id))))) }.orEmpty()
        // CR 709.5h, i: given an unlocked designation; "this door" is the half the ability is printed on ([door])
        is EventPattern.Unlock -> ((change as? Change.Happened)?.event as? GameEvent.Unlocked)
            ?.takeIf { (!pattern.fully || it.fully) && (!pattern.thisDoor || door == null || it.door == door) && e.matches(pattern.objects, it.id) }
            ?.let { listOf(TriggerEvent(listOf(RoleObjects(EventRole.Object, listOf(it.id))), listOf(it.player))) }.orEmpty()
        // CR 701.62b: once the whole action is done
        is EventPattern.ManifestDread -> ((change as? Change.Happened)?.event as? GameEvent.ManifestedDread)
            ?.takeIf { e.matches(pattern.players, it.player) }
            ?.let { listOf(TriggerEvent(listOfNotNull(it.manifested?.let { m -> RoleObjects(EventRole.Object, listOf(m)) },
                RoleObjects(EventRole.Rest, it.rest)), listOf(it.player))) }.orEmpty()
    }

    /** A spell's cast event (CR 601.2i): the Stack item of a cast spell put on the Stack, tagged [Meaning.Cast]. */
    fun isCast(change: Change): Boolean = change is Change.StackItemAdded && Meaning.Cast in change.meanings

    fun castObject(change: Change): ObjectId? = (change as? Change.StackItemAdded)?.item?.id

    /** Zone-change parts of [change]: moves, and tokens and emblems coming into existence (CR 111.1, 603.6a). */
    private fun moves(change: Change): List<Move> = when (change) {
        is Change.ZoneChange -> change.moves
        is Change.Created -> change.objects.map { p ->
            // A created token comes from nowhere; its "before" is itself, never matched as a leaving object.
            Move(p.obj.copy(zone = Zone.Outside), p.obj, -1, p.index, change.meanings + Meaning.Entered)
        }
        else -> emptyList()
    }

    /** Leaving the battlefield and the other public-zone moves of CR 603.10a look back; entering looks at the new object. */
    /** The object a move pattern tests: as it was for leaving the battlefield and moves "from" a zone, but the permanent
     * as it entered for entering the battlefield, also "from your hand" (CR 603.6a, 603.10a). */
    private fun tested(m: Move, from: Zone?, proposed: Boolean): ObjectId =
        if (proposed || m.from == Zone.Battlefield || (from != null && m.to != Zone.Battlefield)) m.before.id else m.after.id

    private fun moveEvent(e: Evaluator, m: Move, proposed: Boolean, from: Zone?, objectRole: EventRole? = null): TriggerEvent {
        val tested = tested(m, from, proposed)
        val player = m.seen?.controller ?: m.before.controller ?: m.before.owner
        return TriggerEvent(
            objects = listOf(
                RoleObjects(EventRole.Object, listOf(if (objectRole == EventRole.Before) m.before.id else tested)),
                RoleObjects(EventRole.Before, listOf(m.before.id)),
                RoleObjects(EventRole.After, listOf(m.after.id)),
            ),
            players = listOf(player),
        )
    }

    private fun life(e: Evaluator, change: Change, players: PlayerFilter, meaning: Meaning): List<TriggerEvent> {
        val c = change as? Change.Life ?: return emptyList()
        if (meaning !in c.meanings || !e.matches(players, c.player)) return emptyList()
        return listOf(TriggerEvent(players = listOf(c.player), amount = kotlin.math.abs(c.after - c.before)))
    }

    private fun counters(e: Evaluator, change: Change, objects: ObjectFilter, kind: arcanum.api.CounterKind?, put: Boolean, last: Boolean): List<TriggerEvent> {
        val c = change as? Change.Counters ?: return emptyList()
        val holder = c.holder as? TargetRef.Object ?: return emptyList()
        val delta = (c.after?.count ?: 0) - (c.before?.count ?: 0)
        if ((put && delta <= 0) || (!put && delta >= 0)) return emptyList()
        if (kind != null && kind != c.kind) return emptyList()
        if (last && c.after != null) return emptyList()
        if (!e.matches(objects, holder.id)) return emptyList()
        return listOf(TriggerEvent(listOf(RoleObjects(EventRole.Object, listOf(holder.id))), listOfNotNull(e.controllerOf(holder.id)),
            kotlin.math.abs(delta)))
    }

    /** blb: whether counters were put by an effect of a spell or ability matching [filter] (its Stack item; "you put": one
     * you control). Counters with no cause (a cost, a rule) match no filter. */
    private fun causedBy(e: Evaluator, change: Change, filter: arcanum.api.ObjectFilter?): Boolean {
        if (filter == null) return true
        val by = (change as? Change.Counters)?.by ?: return false
        by.item?.let { item -> if (e.board.state.stackItems[item] != null || e.board.state.obj(item) != null) return e.matches(filter, item) }
        return filter is arcanum.api.ObjectFilter.ControlledBy && e.matches(filter.players, by.controller)
    }

    private fun recipient(e: Evaluator, filter: RecipientFilter, ref: TargetRef): Boolean = when (ref) {
        is TargetRef.Object -> if (filter.objects == null) filter.players == null else e.matches(filter.objects!!, ref.id)
        is TargetRef.Player -> if (filter.players == null) filter.objects == null else e.matches(filter.players!!, ref.id)
    }

    private fun defendingPlayer(e: Evaluator, target: TargetRef): PlayerId? = when (target) {
        is TargetRef.Player -> target.id
        is TargetRef.Object -> e.controllerOf(target.id)
    }

    private fun attacks(e: Evaluator, change: Change, pattern: EventPattern.Attack): List<TriggerEvent> {
        val c = change as? Change.Combat ?: return emptyList()
        if (Meaning.AttackersDeclared !in c.meanings) return emptyList()
        val before = c.before?.attackers.orEmpty().map { it.id }.toSet()
        val all = c.after?.attackers.orEmpty()
        val declared = all.filter { it.id !in before }
        if (pattern.alone && all.size != 1) return emptyList()
        // gtc chunk 01: battalion, "this creature and at least two other creatures attack": declared together (CR 508.1a)
        if (declared.size < 1 + pattern.withOthers) return emptyList()
        return declared.filter { a -> e.matches(pattern.attacker, a.id) && (pattern.target == null || recipient(e, pattern.target!!, a.target)) }
            .map { a ->
                TriggerEvent(
                    objects = listOf(RoleObjects(EventRole.Object, listOf(a.id))) +
                        listOfNotNull((a.target as? TargetRef.Object)?.let { RoleObjects(EventRole.Defender, listOf(it.id)) }),
                    players = listOfNotNull(defendingPlayer(e, a.target)),
                )
            }
    }

    private fun blocks(e: Evaluator, change: Change, pattern: EventPattern.Block): List<TriggerEvent> {
        val c = change as? Change.Combat ?: return emptyList()
        if (Meaning.BlockersDeclared !in c.meanings) return emptyList()
        val before = c.before?.blockers.orEmpty().map { it.id }.toSet()
        return c.after?.blockers.orEmpty().filter { it.id !in before && e.matches(pattern.blocker, it.id) }.flatMap { b ->
            val attackers = b.attackers.filter { e.matches(pattern.attacker, it) }
            when {
                attackers.isEmpty() -> emptyList()
                pattern.perAttacker -> attackers.map { a ->
                    TriggerEvent(listOf(RoleObjects(EventRole.Object, listOf(b.id)), RoleObjects(EventRole.OtherCombatant, listOf(a))))
                }
                else -> listOf(TriggerEvent(listOf(RoleObjects(EventRole.Object, listOf(b.id)), RoleObjects(EventRole.OtherCombatant, attackers))))
            }
        }
    }

    private fun becomesBlocked(e: Evaluator, change: Change, pattern: EventPattern.BecomesBlocked): List<TriggerEvent> {
        // CR 509.3c: an attacker that was unblocked (or not yet either) and is blocked after the change
        val c = change as? Change.Combat ?: return emptyList()
        val wasBlocked = c.before?.attackers.orEmpty().filter { it.blocked }.map { it.id }.toSet()
        return c.after?.attackers.orEmpty().filter { it.blocked && it.id !in wasBlocked && e.matches(pattern.attacker, it.id) }.flatMap { a ->
            val blockers = c.after?.blockers.orEmpty().filter { a.id in it.attackers && e.matches(pattern.blocker, it.id) }.map { it.id }
            when {
                blockers.isEmpty() -> emptyList()
                pattern.perBlocker -> blockers.map { b ->
                    TriggerEvent(listOf(RoleObjects(EventRole.Object, listOf(a.id)), RoleObjects(EventRole.OtherCombatant, listOf(b))))
                }
                else -> listOf(TriggerEvent(listOf(RoleObjects(EventRole.Object, listOf(a.id)), RoleObjects(EventRole.OtherCombatant, blockers))))
            }
        }
    }

    /** Spells and abilities put on the Stack (or changing targets) and what they target (CR 115.1). */
    private fun targeted(change: Change): List<Pair<ObjectId, List<TargetRef>>> = when (change) {
        is Change.StackItemAdded -> listOf(change.item.id to change.item.targets.values.flatten())
        is Change.StackItemUpdated -> {
            val old = change.before.targets.values.flatten().toSet()
            listOf(change.after.id to change.after.targets.values.flatten().filter { it !in old })
        }
        else -> emptyList()
    }.filter { it.second.isNotEmpty() }

    private fun unattached(e: Evaluator, change: Change, pattern: EventPattern.Unattach): List<TriggerEvent> {
        val c = change as? Change.Attached ?: return emptyList()
        val from = c.before ?: return emptyList()
        if (c.after == from) return emptyList()
        val fromId = (from as? TargetRef.Object)?.id
        if (!e.matches(pattern.objects, c.id) || (fromId != null && !e.matches(pattern.from, fromId))) return emptyList()
        return listOf(TriggerEvent(listOf(RoleObjects(EventRole.Object, listOf(c.id))) +
            listOfNotNull(fromId?.let { RoleObjects(EventRole.From, listOf(it)) })))
    }

    /** Copies of spells are put on the Stack too; only cast spells count as cast (CR 707.10a). */
    @Suppress("unused")
    private fun isCopy(change: Change): Boolean =
        change is Change.StackItemAdded && change.item.kind is StackItemKind.SpellCopy
}

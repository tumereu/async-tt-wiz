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
    // dgm
    /** Whether a draw of [player] now would be the first they draw in their own [step] ("except the first one they draw
     * in each of their draw steps", CR 504.1): no draw of theirs since the step began. */
    private fun firstDrawIn(state: arcanum.engine.state.GameState, step: arcanum.api.TurnStep, player: PlayerId): Boolean {
        if (state.turn.step != step || state.turn.active != player) return false
        val events = state.eventsOf(state.turn.number)
        val began = events.indexOfLast { it is Change.Step && it.after.step == step && it.before.step != step }
        return events.drop(began + 1).none { c -> c is Change.ZoneChange && c.moves.any { Meaning.Drew in it.meanings && it.before.owner == player } }
    }

    /** [filter] with "spell" read as true: for a cast whose spell is no longer on the Stack (CR 601.2a). */
    private fun castAsSpell(filter: ObjectFilter): ObjectFilter = when (filter) {
        is ObjectFilter.Category -> if (filter.category == arcanum.api.ObjectCategory.Spell) ObjectFilter.Any else filter
        is ObjectFilter.And -> ObjectFilter.And(filter.all.map { castAsSpell(it) })
        is ObjectFilter.Or -> ObjectFilter.Or(filter.any.map { castAsSpell(it) })
        else -> filter
    }

    fun instances(e: Evaluator, pattern: EventPattern, change: Change, proposed: Boolean = false, door: Int? = null): List<TriggerEvent> = when (pattern) {
        is EventPattern.ZoneChange -> {
            // tmt backfill: an object that left its zone is tested as it last existed, its controller too (CR 603.10a,
            // 608.2h), whichever board asks: "if a permanent left the battlefield under your control this turn" in a
            // static ability's condition
            val back = Evaluator(LastKnownBoard(e.board), e.ctx)
            moves(change).filter { m ->
                (pattern.from == null || m.from == pattern.from) && (pattern.to == null || m.to == pattern.to) &&
                    (pattern.notTo == null || m.to != pattern.notTo) &&
                    (!pattern.resolving || Meaning.Resolved in m.meanings) &&
                    back.matches(pattern.objects, tested(m, pattern.from, proposed))
            }.map { moveEvent(e, it, proposed, pattern.from) }
        }
        is EventPattern.Cast -> if (isCast(change)) {
            val item = (change as Change.StackItemAdded).item
            val from = e.board.state.obj(item.id)?.facts?.castFrom
            // fra backfill: an earlier cast this turn, counted for "your first … spell each turn" once that spell has
            // left the Stack, was a spell as it was cast (CR 601.2a)
            val spell = if (e.board.state.stackItems.containsKey(item.id)) pattern.spell else castAsSpell(pattern.spell)
            if (e.matches(spell, item.id) && e.matches(pattern.by, item.controller) && (pattern.from == null || from == pattern.from))
                listOf(TriggerEvent(listOf(RoleObjects(EventRole.Object, listOf(item.id))), listOf(item.controller))) else emptyList()
        } else emptyList()
        is EventPattern.Activate -> ((change as? Change.Happened)?.event as? GameEvent.Activated)?.let { a ->
            // fra: only loyalty abilities, and only those whose cost removed enough loyalty counters (CR 606.4)
            val kindMatches = when (pattern.kind) {
                null -> true
                arcanum.api.AbilityKind.Loyalty -> a.loyalty != null
                arcanum.api.AbilityKind.Mana -> a.manaAbility
                else -> true
            } && (pattern.removedLoyaltyAtLeast == null || (a.loyalty ?: 0) <= -pattern.removedLoyaltyAtLeast!!)
            if (kindMatches && (pattern.manaAbilities || !a.manaAbility) && e.matches(pattern.source, a.source) && e.matches(pattern.by, a.player))
                // gtc backfill: the activated ability is the event's object ("copy that ability", like the spell of a cast
                // event); its source is the source (CR 113.7)
                listOf(TriggerEvent(listOf(RoleObjects(EventRole.Object, listOf(a.item ?: a.source)), RoleObjects(EventRole.Source, listOf(a.source))),
                    listOf(a.player))) else null
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
        is EventPattern.Draw -> moves(change).filter { Meaning.Drew in it.meanings && e.matches(pattern.players, it.before.owner) &&
                (pattern.exceptFirstIn?.let { step -> !firstDrawIn(e.board.state, step, it.before.owner) } ?: true) }
            .map { TriggerEvent(listOf(RoleObjects(EventRole.Object, listOf(it.after.id))), listOf(it.before.owner)) }
        is EventPattern.Discard -> moves(change).filter {
            Meaning.Discarded in it.meanings && e.matches(pattern.players, it.before.owner) && e.matches(pattern.cards, it.before.id)
        }.filter { causedBy(e, change, pattern.causedBy) }.map { m -> moveEvent(e, m, proposed, Zone.Hand, EventRole.Before) }
        is EventPattern.Sacrifice -> moves(change).filter { Meaning.Sacrificed in it.meanings && e.matches(pattern.objects, it.before.id) }
            .map { m -> moveEvent(e, m, proposed, Zone.Battlefield, EventRole.Before) }
        is EventPattern.Destroyed -> moves(change).filter { Meaning.Destroyed in it.meanings && e.matches(pattern.objects, it.before.id) }
            .map { m -> moveEvent(e, m, proposed, Zone.Battlefield, EventRole.Before) }
        is EventPattern.Countered -> moves(change).filter { Meaning.Countered in it.meanings && e.matches(pattern.objects, it.before.id) }
            .map { m -> moveEvent(e, m, proposed, Zone.Stack, EventRole.Before) } +
            ((change as? Change.StackItemRemoved)?.takeIf { Meaning.Countered in it.meanings && e.matches(pattern.objects, it.item.id) }
                ?.let { listOf(TriggerEvent(listOf(RoleObjects(EventRole.Object, listOf(it.item.id))), listOf(it.item.controller))) }.orEmpty())
        is EventPattern.PlayLand -> moves(change).filter { Meaning.PlayedLand in it.meanings && e.matches(pattern.lands, it.after.id) &&
            e.matches(pattern.players, it.after.controller ?: it.after.owner) &&
            // spm: "play a land from exile", "from anywhere other than your hand"
            (pattern.from == null || it.from == pattern.from) && (pattern.notFrom == null || it.from != pattern.notFrom) }
            .map { TriggerEvent(listOf(RoleObjects(EventRole.Object, listOf(it.after.id))), listOf(it.after.controller ?: it.after.owner)) }
        is EventPattern.CountersPut -> if (causedBy(e, change, pattern.causedBy) && reaches(change, pattern.reaching))
            counters(e, change, pattern.objects, pattern.kind, put = true, last = false) else emptyList()
        is EventPattern.CountersRemoved -> counters(e, change, pattern.objects, pattern.kind, put = false, last = pattern.last)
        is EventPattern.PlayerCountersPut -> (change as? Change.Counters)?.let { c ->
            val holder = c.holder as? TargetRef.Player ?: return@let null
            val delta = (c.after?.count ?: 0) - (c.before?.count ?: 0)
            if (delta > 0 && (pattern.kind == null || pattern.kind == c.kind) && e.matches(pattern.players, holder.id) && causedBy(e, change, pattern.causedBy))
                listOf(TriggerEvent(players = listOf(holder.id), amount = delta)) else null
        }.orEmpty()
        is EventPattern.Tap -> (change as? Change.Status)?.takeIf { it.flag == StatusFlag.Tapped && !it.before && it.after }?.let { c ->
            val forMana = Meaning.ManaAdded in c.meanings
            // msh: tapped to pay a keyword's cost (CR 702.194a)
            val forCost = pattern.forCost == null || c.meanings.any { it is Meaning.PaidFor && it.keyword == pattern.forCost }
            if ((pattern.forMana == null || pattern.forMana == forMana) && forCost && e.matches(pattern.objects, c.id))
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
            ?.attackers.orEmpty().filter { !it.blocked && e.matches(pattern.attacker, it.id) &&
                // dgm: "attacks a player and isn't blocked"
                (pattern.target?.let { t -> recipient(e, t, it.target) } ?: true) }
            .map { TriggerEvent(listOf(RoleObjects(EventRole.Object, listOf(it.id))), listOfNotNull(defendingPlayer(e, it.target))) }
        is EventPattern.BecomesTarget -> targeted(change).flatMap { (item, refs) ->
            refs.filterIsInstance<TargetRef.Object>().map { it.id }.distinct()
                .filter { e.matches(pattern.objects, it) && e.matches(pattern.by, item) }
                .map { TriggerEvent(listOf(RoleObjects(EventRole.Object, listOf(it)), RoleObjects(EventRole.Source, listOf(item)))) } +
                // msh: "a player or permanent becomes the target of …"
                (pattern.players?.let { players -> refs.filterIsInstance<TargetRef.Player>().map { it.id }.distinct()
                    .filter { e.matches(players, it) && e.matches(pattern.by, item) }
                    .map { TriggerEvent(listOf(RoleObjects(EventRole.Source, listOf(item))), listOf(it)) } }.orEmpty())
        }
        // msh: conniving is only replaced (CR 614.1a, see Effects); nothing triggers on it yet
        is EventPattern.Connive -> emptyList()
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
        // CR 731.1a: day becomes night, or night becomes day
        is EventPattern.DayNightChanges -> ((change as? Change.Happened)?.event as? GameEvent.DayNightChanged)
            ?.takeIf { pattern.toNight == null || pattern.toNight == it.night }?.let { listOf(TriggerEvent()) }.orEmpty()
        // blb: CR 702.174c, 701.61a, 716.2a
        is EventPattern.GiveGift -> ((change as? Change.Happened)?.event as? GameEvent.GaveGift)
            ?.takeIf { e.matches(pattern.players, it.player) }?.let { listOf(TriggerEvent(players = listOf(it.player))) }.orEmpty()
        // tla: CR 701.65b, 701.66b, 702.189b, 701.67c
        is EventPattern.Airbend -> ((change as? Change.Happened)?.event as? GameEvent.Airbended)
            ?.takeIf { e.matches(pattern.players, it.player) }?.let { listOf(TriggerEvent(players = listOf(it.player))) }.orEmpty()
        is EventPattern.Earthbend -> ((change as? Change.Happened)?.event as? GameEvent.Earthbended)
            ?.takeIf { e.matches(pattern.players, it.player) }?.let { listOf(TriggerEvent(players = listOf(it.player))) }.orEmpty()
        is EventPattern.Firebend -> ((change as? Change.Happened)?.event as? GameEvent.Firebended)
            ?.takeIf { e.matches(pattern.players, it.player) }?.let { listOf(TriggerEvent(players = listOf(it.player))) }.orEmpty()
        is EventPattern.Waterbend -> ((change as? Change.Happened)?.event as? GameEvent.Waterbended)
            ?.takeIf { e.matches(pattern.players, it.player) }?.let { listOf(TriggerEvent(players = listOf(it.player))) }.orEmpty()
        // tla: CR 603.2: a triggered ability of a matching source triggered on that source attacking
        is EventPattern.AbilityTriggers -> ((change as? Change.Happened)?.event as? GameEvent.AbilityTriggered)
            ?.takeIf { it.attack && pattern.cause is EventPattern.Attack && e.matches(pattern.source, it.source) }
            ?.let { listOf(TriggerEvent(listOf(RoleObjects(EventRole.Object, listOf(it.item)), RoleObjects(EventRole.Source, listOf(it.source))))) }.orEmpty()
        // CR 702.140d: a spell merged with a creature as a resolving mutating creature spell
        is EventPattern.Mutates -> ((change as? Change.Happened)?.event as? GameEvent.Mutated)
            ?.takeIf { e.matches(pattern.objects, it.permanent) }
            ?.let { listOf(TriggerEvent(listOf(RoleObjects(EventRole.Object, listOf(it.permanent))))) }.orEmpty()
        // CR 702.154a, c: an attacker enlisted a creature; the creature enlisted is the other combatant
        is EventPattern.Enlists -> ((change as? Change.Happened)?.event as? GameEvent.Enlisted)
            ?.takeIf { e.matches(pattern.objects, it.attacker) }
            ?.let { listOf(TriggerEvent(listOf(RoleObjects(EventRole.Object, listOf(it.attacker)), RoleObjects(EventRole.OtherCombatant, listOf(it.enlisted))))) }.orEmpty()
        // CR 701.54d: the Ring tempted a player; the creature chosen is the event's object
        is EventPattern.RingTempts -> ((change as? Change.Happened)?.event as? GameEvent.RingTempted)
            ?.takeIf { e.matches(pattern.players, it.player) && (!pattern.chosen || it.bearer != null) }
            ?.let { listOf(TriggerEvent(listOfNotNull(it.bearer?.let { b -> RoleObjects(EventRole.Object, listOf(b)) }), listOf(it.player))) }.orEmpty()
        // CR 309.7
        is EventPattern.CompletesDungeon -> ((change as? Change.Happened)?.event as? GameEvent.CompletedDungeon)
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

    /** msh: whether the counters put make the count reach [reaching] ("the fourth plan counter is put"): fewer before, at least that
     * many after. */
    private fun reaches(change: Change, reaching: Int?): Boolean {
        if (reaching == null) return true
        val c = change as? Change.Counters ?: return false
        return (c.before?.count ?: 0) < reaching && (c.after?.count ?: 0) >= reaching
    }

    /** blb: whether counters were put by an effect of a spell or ability matching [filter] (its Stack item; "you put": one
     * you control). Counters with no cause (a cost, a rule) match no filter. */
    private fun causedBy(e: Evaluator, change: Change, filter: arcanum.api.ObjectFilter?): Boolean {
        if (filter == null) return true
        // rtr chunk 06: also a move's cause (a discard caused by a spell or ability)
        val by = when (change) { is Change.Counters -> change.by; is Change.ZoneChange -> change.by; else -> null } ?: return false
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

/** A [base] board that answers for an object that has left its zone as it last existed: its controller then (CR 608.2h).
 * The finished view reads objects that way already; the board the layers are worked out on doesn't. */
internal class LastKnownBoard(private val base: arcanum.engine.query.Board) : arcanum.engine.query.Board by base {
    override fun controller(id: ObjectId): PlayerId? = base.controller(id)
        ?: if (base.state.obj(id) == null && id !in base.state.stackItems) { base.readingLog(); base.snapshot(id)?.let { it.controller ?: it.owner } } else null
}

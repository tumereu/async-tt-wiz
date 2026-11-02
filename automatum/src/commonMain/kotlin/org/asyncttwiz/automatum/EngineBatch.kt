package org.asyncttwiz.automatum

import arcanum.api.Zone
import arcanum.engine.change.Change
import arcanum.engine.change.Meaning
import arcanum.engine.change.Move
import arcanum.engine.state.CardId
import arcanum.engine.state.CombatState
import arcanum.engine.state.GameObject
import arcanum.engine.state.GameState
import arcanum.engine.state.ObjectId
import arcanum.engine.state.StackItem
import arcanum.engine.state.StackItemKind
import arcanum.engine.state.TargetRef
import arcanum.engine.state.TurnState
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.asyncttwiz.protocol.Automatum
import org.asyncttwiz.protocol.BattlefieldPlacement
import org.asyncttwiz.protocol.CombatAction
import org.asyncttwiz.protocol.CombatDestination
import org.asyncttwiz.protocol.CombatRole
import org.asyncttwiz.protocol.CombatStackRules
import org.asyncttwiz.protocol.EngineRecord
import org.asyncttwiz.protocol.EngineRecordKind
import org.asyncttwiz.protocol.GameAction
import org.asyncttwiz.protocol.GameCommand
import org.asyncttwiz.protocol.GameplayRules
import org.asyncttwiz.protocol.IdentityGap
import org.asyncttwiz.protocol.MoveDestination
import org.asyncttwiz.protocol.MoveInsertion
import org.asyncttwiz.protocol.MoveSource
import org.asyncttwiz.protocol.MtgAction
import org.asyncttwiz.protocol.MtgCause
import org.asyncttwiz.protocol.MtgPriority
import org.asyncttwiz.protocol.MtgRef
import org.asyncttwiz.protocol.MtgStamp
import org.asyncttwiz.protocol.MtgTag
import org.asyncttwiz.protocol.PlayAction
import org.asyncttwiz.protocol.PlayBatches
import org.asyncttwiz.protocol.PlayCard
import org.asyncttwiz.protocol.PlayContainer
import org.asyncttwiz.protocol.PlayState
import org.asyncttwiz.protocol.PlayZone
import org.asyncttwiz.protocol.StackAction
import org.asyncttwiz.protocol.TurnPosition
import org.asyncttwiz.protocol.zoneContainer

/** An engine change ATW can't say with its actions; the call can't be sent (report it, D32's way out: turn Automatum off). */
class Unmappable(message: String) : IllegalStateException(message)

/**
 * One engine call's changes as one ATW batch (SYS-010, D59): [actions] with their [tags], and [state], the play state
 * after them as ATW's reducer makes it (every member was run through it while planning, with the member request ids
 * the batch's [GameCommand] gives them, so the ids it creates are the ones the server will create).
 */
data class BatchPlan(val actions: List<PlayAction>, val tags: List<MtgTag?>, val state: PlayState) {
    /** The batch to send with the planned command's request id. */
    val action: GameAction.PlayBatch get() = GameAction.PlayBatch(actions, tags.dropLastWhile { it == null })
}

/**
 * Turns engine [Change]s into ATW actions (integration spec §2 step 4, I0 "change mapping").
 *
 * Each change is applied to the engine state and then said in ATW:
 * - its structure with ATW's own actions: moves (`MoveCard`), a spell going on the Stack as an entry pointing at its card
 *   (`AddEffect`, STK-001) and leaving it by pulling the entry, abilities as entries of their source, tokens
 *   (`CreateToken`/`RemoveToken`), shuffles (`ShuffleLibrary` with the seed the host answered with), library order,
 *   steps and turns (`ChooseStep`, `EndTurn`), combat lanes;
 * - then every engine value ATW keeps is brought to the engine's: tapped, damage, face, face down, life with ATW's own
 *   actions; zone-change counts and timestamps (`Restamp`), typed counters, attachments, ledgers, mana pools, priority,
 *   Stack items and engine records with [MtgAction]s.
 *
 * The first action of each change carries its tag ([MtgTag]: meanings, what caused it, and its part of the engine's event
 * log, [EngineLog]); an event with no action of its own (damage dealt, a coin flip) rides on the next one.
 */
object EngineBatch {
    /** The batch for [call]'s changes, sent by [actor] as [command] (its request id names the members). */
    fun plan(call: EngineCall, actor: String, command: GameCommand): BatchPlan = Planner(call, actor, command).run()
}

private class Planner(private val call: EngineCall, private val actor: String, command: GameCommand) {
    private val template = command.copy(action = GameAction.PlayBatch(listOf(PlayAction.PassAttention)))
    private var atw: PlayState = call.state
    private val engine: GameState = call.start.deepCopy()
    private val ids = call.ids
    private val actions = mutableListOf<PlayAction>()
    private val tags = mutableListOf<MtgTag?>()
    private val pendingLog = mutableListOf<LogOp>()
    private var pendingCause: MtgCause? = null
    private val shuffles = call.shuffles.toMutableList()
    /** The ATW Stack entry of each engine Stack object. */
    private val entries: MutableMap<ObjectId, String> = EngineState.entries(atw.board)
        .mapNotNull { e -> e.item?.let { (if (it.data.isEmpty()) ids.obj(it.id)!! else EngineCodec.stackItem(it).id) to e.id } }.toMap().toMutableMap()

    fun run(): BatchPlan {
        for (change in call.changes) {
            val logged = engine.log.size
            engine.apply(change)
            when {
                engine.log.size > logged -> pendingLog += LogOp.Add(engine.log.last())
                engine.log.size < logged -> pendingLog += LogOp.Drop
            }
            val first = actions.size
            primary(change, first)
            settle()
            if (actions.size > first) {
                val tag = tags[first]
                val data = if (pendingLog.isEmpty()) "" else EngineCodec.encode(TagData.serializer(), TagData(pendingLog.toList()))
                tags[first] = tag(tag?.meanings ?: meanings(change), tag?.causedBy ?: cause(change) ?: pendingCause, data)
                pendingLog.clear(); pendingCause = null
            } else if (pendingLog.isNotEmpty()) pendingCause = pendingCause ?: cause(change)
        }
        if (pendingLog.isNotEmpty()) {
            if (actions.isEmpty()) throw Unmappable("engine events without any ATW action to carry them: ${call.changes}")
            val last = tags.last()
            val earlier = last?.data?.takeIf { it.isNotEmpty() }?.let { EngineCodec.decode(TagData.serializer(), it).log }.orEmpty()
            tags[tags.lastIndex] = tag(last?.meanings.orEmpty(), last?.causedBy ?: pendingCause,
                EngineCodec.encode(TagData.serializer(), TagData(earlier + pendingLog)))
        }
        return BatchPlan(actions.toList(), tags.toList(), atw)
    }

    // ---- Playing one member through ATW's reducer

    /** Plays [action] as the next member; false when it changes nothing (then it isn't part of the batch). */
    private fun play(action: PlayAction, tag: MtgTag? = null): Boolean {
        val member = PlayBatches.member(template, actions.size, action)
        val change = GameplayRules.change(atw, actor, member)
        if (change.reason != null) throw Unmappable("ATW refused $action (${change.reason})")
        if (change.mutation == null) return false
        atw = change.state!!
        actions += action
        tags += tag
        return true
    }

    private val board get() = atw.board
    private fun card(id: String): PlayCard? = board.cards.singleOrNull { it.id == id }
    private fun atwId(card: CardId): String = call.hidden[card] ?: ids.atw(card)
    private fun atwCard(o: ObjectId): PlayCard = (o as? ObjectId.Card)?.let { card(atwId(it.card)) } ?: throw Unmappable("no ATW card for $o")
    private fun container(zone: PlayZone, player: String): PlayContainer =
        board.zoneContainer(player, zone) ?: throw Unmappable("no $zone of $player")
    private fun digest() = GameplayRules.stackDigest(atw)
    private fun source(card: PlayCard) = MoveSource(card.containerId, card.placement)
    /** ATW cards that are spells on the engine's Stack: their card stays in its ATW container (STK-001). */
    private fun spellCards(): List<String> = entries.keys.filterIsInstance<ObjectId.Card>().map { atwId(it.card) }

    // ---- Structure

    private fun primary(change: Change, first: Int) {
        when (change) {
            is Change.ZoneChange -> change.moves.forEach { m ->
                val at = actions.size
                move(m)
                if (actions.size > at) tags[at] = tag(m.meanings.map(::name), cause(change), "")
            }
            is Change.Created -> change.objects.forEach { create(it.obj) }
            is Change.Removed -> change.objects.forEach { placed ->
                card(atwId(placed.obj.card))?.takeIf { it.token }?.let { play(PlayAction.RemoveToken(it.id)) }
            }
            is Change.Shuffle -> shuffle(change)
            is Change.Reorder -> reorder(change)
            is Change.Step -> step(change.before, change.after)
            is Change.Combat -> combat(change.after)
            is Change.StackItemAdded -> if (change.index != null) addItem(change.item)
            is Change.StackItemRemoved -> if (change.item.id is ObjectId.StackItem) entries.remove(change.item.id)?.let { entry ->
                if (board.stackEffects.any { it.id == entry }) play(PlayAction.Stack(StackAction.RemoveEffect(entry)))
            }
            is Change.Reverted -> primary(change.change.inverse(), first)
            else -> Unit
        }
    }

    private fun move(m: Move) {
        val card = atwCard(m.before.id)
        when {
            m.to == Zone.Stack -> {
                // D35 in ATW's terms (STK-001, STK-003): a Stack entry points at the spell's card, which stays put
                val before = board.stackEffects.map { it.id }.toSet()
                play(PlayAction.Stack(StackAction.AddEffect(card.id, digest())))
                entries[m.after.id] = board.stackEffects.single { it.id !in before }.id
            }
            m.from == Zone.Stack -> {
                val entry = entries.remove(m.before.id) ?: throw Unmappable("no Stack entry for ${m.before.id}")
                val destination = destination(m.after, card)
                if (board.stackEffects.any { it.id == entry }) play(PlayAction.MoveCard(card.id, source(card), destination, removeStackEntryId = entry))
                else play(PlayAction.MoveCard(card.id, source(card), destination))
            }
            else -> play(PlayAction.MoveCard(card.id, source(card), destination(m.after, card),
                faceDown = true.takeIf { m.after.zone == Zone.Battlefield && m.after.faceDown }))
        }
    }

    /** Where [to] is in ATW: its owner's zone (its controller's Battlefield), at the place the engine put it. */
    private fun destination(to: GameObject, card: PlayCard): MoveDestination {
        val owner = to.owner.value
        fun neighbors(list: List<ObjectId>): Pair<String?, String?> {
            val i = list.indexOf(to.id)
            return list.getOrNull(i - 1)?.let { atwCard(it).id } to list.getOrNull(i + 1)?.let { atwCard(it).id }
        }
        return when (to.zone) {
            Zone.Library -> {
                val (after, before) = neighbors(engine.player(to.owner).library)
                val library = container(PlayZone.LIBRARY, owner).id
                when {
                    after == null -> MoveDestination(library, MoveInsertion.TOP)
                    before == null -> MoveDestination(library, MoveInsertion.BOTTOM)
                    else -> MoveDestination(library, MoveInsertion.GAP, afterId = after, beforeId = before, pendingIds = spellCards() - card.id)
                }
            }
            Zone.Hand -> {
                val (after, before) = neighbors(engine.player(to.owner).hand)
                val hand = container(PlayZone.HAND, owner).id
                if (before == null) MoveDestination(hand, MoveInsertion.UNORDERED)
                else MoveDestination(hand, MoveInsertion.GAP, afterId = after, beforeId = before, pendingIds = spellCards() - card.id)
            }
            Zone.Graveyard -> MoveDestination(container(PlayZone.GRAVEYARD, owner).id, MoveInsertion.UNORDERED)
            Zone.Exile -> MoveDestination(container(PlayZone.EXILE, owner).id, MoveInsertion.UNORDERED)
            Zone.Battlefield -> {
                val field = container(PlayZone.BATTLEFIELD, (to.controller ?: to.owner).value).id
                MoveDestination(field, MoveInsertion.EMPTY, BattlefieldPlacement.cell(atw, field, card, faceDown = to.faceDown))
            }
            else -> throw Unmappable("ATW has no ${to.zone} zone")
        }
    }

    private fun create(o: GameObject) {
        if (o.zone != Zone.Battlefield) throw Unmappable("a token created in ${o.zone}")
        val definition = o.definition ?: throw Unmappable("a token without a definition")
        val before = board.cards.map { it.id }.toSet()
        play(PlayAction.CreateToken(AtwTokens.submitted(definition)))
        val token = board.cards.single { it.id !in before }
        ids.alias(token.id, o.card)
        val controller = (o.controller ?: o.owner).value
        val field = container(PlayZone.BATTLEFIELD, controller)
        if (token.containerId != field.id) play(PlayAction.MoveCard(token.id, source(token),
            MoveDestination(field.id, MoveInsertion.EMPTY, BattlefieldPlacement.cell(atw, field.id, token))))
    }

    private fun shuffle(change: Change.Shuffle) {
        val answer = shuffles.firstOrNull { it.player == change.player } ?: throw Unmappable("a shuffle the host didn't answer")
        shuffles.remove(answer)
        val library = container(PlayZone.LIBRARY, change.player.value)
        play(PlayAction.ShuffleLibrary(library.id, answer.seed))
        // CR 701.24b: cards the shuffle left out go back on top as they were
        val kept = change.after.filter { a -> change.before.any { it.id == a.id } }
        for (k in kept.reversed()) {
            val card = atwCard(k.id)
            play(PlayAction.MoveCard(card.id, source(card), MoveDestination(library.id, MoveInsertion.TOP)))
        }
        if (board.zoneContainer(change.player.value, PlayZone.LIBRARY)!!.cards.filter { it !in spellCards() } !=
            engine.player(change.player).library.map { atwCard(it).id }) throw Unmappable("the shuffle's order differs from ATW's")
    }

    private fun reorder(change: Change.Reorder) {
        val owner = change.owner?.value ?: return
        when (change.zone) {
            Zone.Library -> arrange(container(PlayZone.LIBRARY, owner), engine.player(change.owner!!).library)
            Zone.Hand -> arrange(container(PlayZone.HAND, owner), engine.player(change.owner!!).hand)
            Zone.Graveyard -> throw Unmappable("ATW keeps a graveyard in arrival order")
            else -> Unit // the Battlefield's and Exile's order are by timestamp (EngineState.from)
        }
    }

    /** Puts [container]'s cards in [order] by moving each misplaced card into its gap. */
    private fun arrange(container: PlayContainer, order: List<ObjectId>) {
        val desired = order.map { atwCard(it).id }
        val spells = spellCards().toSet()
        for (i in desired.indices) {
            val current = board.containers.single { it.id == container.id }.cards.filter { it !in spells }
            if (current[i] == desired[i]) continue
            val card = card(desired[i])!!
            play(PlayAction.MoveCard(card.id, source(card), MoveDestination(container.id, MoveInsertion.GAP,
                afterId = desired.getOrNull(i - 1), beforeId = current[i], pendingIds = (spells - card.id).toList())))
        }
    }

    private fun step(before: TurnState, after: TurnState) {
        if (after.number != before.number || after.active != before.active) {
            val other = board.players.singleOrNull { it.accountId != before.active.value }?.accountId
            if (after.number != before.number + 1 || after.active.value != other) throw Unmappable("turn ${after.number} of ${after.active}")
            play(PlayAction.EndTurn)
        }
        val target = EngineCodec.position(after.step)
        if (TurnPosition.of(board.turn) != target) play(PlayAction.ChooseStep(target))
    }

    /** The combat lanes show the engine's combat (CMB-*): a pair per attacker with its target, blockers in the pair of
     * the first attacker they block. The engine reads the combat from its record, the lanes only show it. */
    private fun combat(after: CombatState?) {
        if (after == null) {
            board.combat?.let { play(PlayAction.Combat(CombatAction.Close(it.id))) }
            return
        }
        if (board.combat == null) play(PlayAction.Combat(CombatAction.Open))
        val areaId = board.combat!!.id
        val wanted = (after.attackers.map { it.id } + after.blockers.map { it.id }).map { atwCard(it).id }.toSet()
        CombatStackRules.occurrences(board.combat).filter { it.sourceId !in wanted }
            .forEach { play(PlayAction.Combat(CombatAction.Remove(areaId, it.id))) }
        for (attacker in after.attackers) {
            val card = atwCard(attacker.id)
            if (CombatStackRules.occurrences(board.combat).any { it.sourceId == card.id }) continue
            val target = (attacker.target as? TargetRef.Object)?.let { atwCard(it.id).id }
            play(PlayAction.Combat(CombatAction.Place(card.id, CombatDestination(areaId, CombatRole.ATTACKER,
                pairGap = IdentityGap(board.combat!!.pairs.lastOrNull()?.id), attackTarget = target))))
        }
        for (blocker in after.blockers) {
            val card = atwCard(blocker.id)
            if (CombatStackRules.occurrences(board.combat).any { it.sourceId == card.id }) continue
            val attacker = blocker.attackers.firstOrNull()?.let { atwCard(it).id } ?: continue
            val pair = board.combat!!.pairs.firstOrNull { p -> p.attackers.any { it.sourceId == attacker } } ?: continue
            play(PlayAction.Combat(CombatAction.Place(card.id, CombatDestination(areaId, CombatRole.BLOCKER, pairId = pair.id))))
        }
    }

    private fun addItem(item: StackItem) {
        if (engine.stack.lastOrNull() != item.id) throw Unmappable("an ability put on the Stack below its top")
        val before = board.stackEffects.map { it.id }.toSet()
        when (val kind = item.kind) {
            is StackItemKind.SpellCopy -> {
                val copied = entries[kind.copied] ?: throw Unmappable("a copy of ${kind.copied}, which has no Stack entry")
                play(PlayAction.Stack(StackAction.CopyEffect(copied, digest())))
            }
            else -> {
                val source = item.source?.let { s -> (s as? ObjectId.Card)?.let { card(atwId(it.card)) } }
                    ?: throw Unmappable("an ability without a source card: ${item.source}")
                play(PlayAction.Stack(StackAction.AddEffect(source.id, digest())))
            }
        }
        entries[item.id] = board.stackEffects.single { it.id !in before }.id
    }

    // ---- Values: bring every engine value ATW keeps to the engine's

    private fun settle() {
        registerHidden()
        repeat(4) { if (!values()) return }
        throw Unmappable("ATW's values don't settle on the engine's")
    }

    /** A viewer's new card ids (D60) name ATW's cards from the moment they appear, counting zone changes from there. */
    private fun registerHidden() {
        if (call.hidden.isEmpty()) return
        val objects = engine.objects.values.associateBy { it.card }
        for ((hidden, real) in call.hidden) {
            if (ids.atw(hidden) == real) continue
            val o = objects[hidden] ?: continue
            val card = card(real) ?: continue
            ids.alias(real, hidden, card.zoneChanges - o.id.zoneChanges)
        }
    }

    /** One pass over every value; true when it changed something. */
    private fun values(): Boolean {
        val start = actions.size
        val objects = engine.objects.values.associateBy { it.card }
        for (id in board.cards.map { it.id }) {
            val o = objects[ids.engine(id)] ?: continue
            cardValues(id, o)
        }
        for (p in board.players) playerValues(p.accountId)
        val priority = engine.priority.takeUnless { it.holder == null && it.passes == 0 }?.let { MtgPriority(it.holder?.value, it.passes) }
        if (board.priority != priority) play(PlayAction.Mtg(MtgAction.SetPriority(board.priority, priority)))
        for (entry in EngineState.entries(board)) {
            val key = entries.entries.firstOrNull { it.value == entry.id }?.key ?: continue
            val wanted = engine.stackItems[key]?.let { EngineCodec.stackItem(it, ids::ref) }
                ?: engine.obj(key)?.takeIf { it.zone == Zone.Stack }?.let { EngineCodec.casting(ids.ref(it.id), (it.controller ?: it.owner).value, it.timestamp) }
            if (entry.item != wanted) play(PlayAction.Mtg(MtgAction.SetStackItem(entry.id, entry.item, wanted)))
        }
        val wanted = records(engine, board).associateBy { it.id }
        val current = board.engineRecords.associateBy { it.id }
        for (id in (current.keys + wanted.keys).sorted()) if (current[id] != wanted[id])
            play(PlayAction.Mtg(MtgAction.SetRecord(id, current[id], wanted[id])))
        return actions.size > start
    }

    private fun cardValues(id: String, o: GameObject) {
        fun now() = card(id)!!
        val zone = board.containers.single { it.id == now().containerId }.zone
        val onField = zone == PlayZone.BATTLEFIELD
        if (onField && now().tapped != o.tapped) play(PlayAction.SetTapped(id, o.tapped))
        if (onField && (now().damage.toIntOrNull() ?: 0) != o.damage) play(PlayAction.SetDamage(id, if (o.damage == 0) "" else "${o.damage}"))
        if ((onField || zone == PlayZone.EXILE) && now().isFaceDown != o.faceDown) play(PlayAction.SetCovered(id, o.faceDown))
        val faces = Derived.faces(atw, now())
        if (faces.size > 1 && o.face < faces.size && now().faceId != faces[o.face].faceId) play(PlayAction.SetFace(id, faces[o.face].faceId))
        fun ref() = MtgRef.Card(id, now().zoneChanges)
        val attached = o.attachedTo?.let(ids::ref)
        if (now().attachedTo != attached) play(PlayAction.Mtg(MtgAction.Attach(ref(), now().attachedTo, attached)))
        val stamp = MtgStamp(ids.zoneChanges(o.id), o.timestamp)
        if (MtgStamp(now().zoneChanges, now().timestamp) != stamp)
            play(PlayAction.Mtg(MtgAction.Restamp(id, MtgStamp(now().zoneChanges, now().timestamp), stamp)))
        val counters = EngineCodec.counters(o.counters).associateBy { it.kind }
        val held = now().typedCounters.associateBy { it.kind }
        for (kind in (held.keys + counters.keys).sorted()) if (held[kind] != counters[kind])
            play(PlayAction.Mtg(MtgAction.SetCounter(ref(), kind, held[kind], counters[kind])))
        val ledger = ledger(now(), o).text()
        if (now().ledger != ledger) play(PlayAction.Mtg(MtgAction.SetLedger(ref(), now().ledger, ledger)))
    }

    /** The ledger that makes [EngineState.from] give back [o] for [card]. */
    private fun ledger(card: PlayCard, o: GameObject): CardLedger {
        val container = board.containers.single { it.id == card.containerId }
        val derivedController = Derived.controller(o.zone, container.accountId, engine.stackItems[o.id]?.controller ?: (o.controller ?: o.owner))
        val faces = Derived.faces(atw, card)
        val derivedFace = if (faces.size > 1 && o.face < faces.size) o.face else Derived.face(atw, card)
        return CardLedger(
            card = o.card.value.takeIf { o.token && it != card.id && !ids.transient(o.card) },
            definition = o.definition.takeIf { o.token },
            faceOverride = o.face.takeIf { it != derivedFace },
            controllerOverride = (o.controller?.value ?: "").takeIf { o.controller != derivedController },
            ownerOverride = o.owner.value.takeIf { it != Derived.owner(atw, card) },
            phasedOut = o.phasedOut, marks = o.marks, deathtouchDamage = o.deathtouchDamage,
            controlSince = o.controlSince.takeIf { it != Derived.controlSince(o.zone, container.accountId, card) },
            noControlSince = o.controlSince == null && Derived.controlSince(o.zone, container.accountId, card) != null,
            memory = o.memory, facts = o.facts, goadedBy = o.goadedBy, counterOrder = EngineCodec.counterOrder(o.counters),
        )
    }

    private fun playerValues(account: String) {
        fun now() = board.players.single { it.accountId == account }
        val p = engine.player(arcanum.engine.state.PlayerId(account))
        val life = now().life.toIntOrNull()
        if (life != p.life) play(if (life == null) PlayAction.SetLife(account, "${p.life}")
            else PlayAction.AdjustLife(account, (p.life - life).let { if (it > 0) "+$it" else "$it" }))
        val ref = MtgRef.Player(account)
        val counters = EngineCodec.counters(p.counters).associateBy { it.kind }
        val held = now().typedCounters.associateBy { it.kind }
        for (kind in (held.keys + counters.keys).sorted()) if (held[kind] != counters[kind])
            play(PlayAction.Mtg(MtgAction.SetCounter(ref, kind, held[kind], counters[kind])))
        val pool = p.manaPool.map(EngineCodec::mana)
        if (now().manaPool != pool) play(PlayAction.Mtg(MtgAction.SetManaPool(account, now().manaPool, pool)))
        val ledger = PlayerLedger(p.startingLife, p.lost, p.designations, p.ledger, EngineCodec.counterOrder(p.counters)).text()
        if (now().ledger != ledger) play(PlayAction.Mtg(MtgAction.SetLedger(ref, now().ledger, ledger)))
    }

    // ---- Tags

    private fun tag(meanings: List<String>, cause: MtgCause?, data: String): MtgTag? =
        MtgTag(meanings.distinct().take(16), cause, data).takeIf { it.meanings.isNotEmpty() || cause != null || data.isNotEmpty() }

    private fun name(meaning: Meaning): String =
        EngineCodec.json.encodeToJsonElement(Meaning.serializer(), meaning).jsonObject["type"]!!.jsonPrimitive.content

    private fun meanings(change: Change): List<String> = when (change) {
        is Change.ZoneChange -> change.moves.flatMap { it.meanings }
        is Change.Created -> change.meanings
        is Change.Removed -> change.meanings
        is Change.Status -> change.meanings
        is Change.Face -> change.meanings
        is Change.Counters -> change.meanings
        is Change.Life -> change.meanings
        is Change.ManaPool -> change.meanings
        is Change.StackItemAdded -> change.meanings
        is Change.StackItemRemoved -> change.meanings
        is Change.StackItemUpdated -> change.meanings
        is Change.EffectRemoved -> change.meanings
        is Change.DelayedTriggerRemoved -> change.meanings
        is Change.ObjectUpdated -> change.meanings
        is Change.Step -> change.meanings
        is Change.Priority -> change.meanings
        is Change.Combat -> change.meanings
        else -> emptyList()
    }.map(::name)

    private fun cause(change: Change): MtgCause? {
        val by = when (change) {
            is Change.ZoneChange -> change.by
            is Change.Created -> change.by
            is Change.Status -> change.by
            is Change.Counters -> change.by
            is Change.Life -> change.by
            is Change.Happened -> change.by
            is Change.StackItemRemoved -> change.by
            is Change.Attached -> change.by
            else -> null
        } ?: return null
        return MtgCause(by.controller.value, by.source?.let(ids::ref), by.name?.takeIf(Automatum::name), by.item?.let(ids::ref))
    }
}

/** The engine values ATW keeps as records (§4.3, [EngineCodec]): one per effect object and delayed trigger, the game
 * ledger, turn and combat facts, waiting triggers and the open flow. */
internal fun records(state: GameState, board: org.asyncttwiz.protocol.BoardState): List<EngineRecord> = buildList {
    state.effects.values.forEach { add(EngineCodec.effectRecord(it)) }
    state.delayedTriggers.values.forEach { add(EngineCodec.delayedRecord(it)) }
    if (state.ledger.isNotEmpty()) add(EngineRecord(EngineCodec.LEDGER, EngineRecordKind.Ledger, data = EngineCodec.encode(ledgerSerializer, state.ledger)))
    val turn = TurnExtras(state.turn.landsPlayed, state.turn.turnStarted.takeIf { it != Derived.turnStarted(board) },
        state.turn.extraTurns, state.skipFirstDraw)
    if (turn != TurnExtras()) add(EngineRecord(EngineCodec.TURN, EngineRecordKind.Turn, data = EngineCodec.encode(TurnExtras.serializer(), turn)))
    state.combat?.let { add(EngineRecord(EngineCodec.COMBAT, EngineRecordKind.Combat, data = EngineCodec.encode(combatSerializer, it))) }
    if (state.triggered.isNotEmpty())
        add(EngineRecord(EngineCodec.TRIGGERED, EngineRecordKind.Triggered, data = EngineCodec.encode(triggeredSerializer, state.triggered)))
    state.pending?.let { add(EngineRecord(Automatum.CONTINUATION_ID, EngineRecordKind.Continuation,
        data = EngineCodec.encode(FlowRecord.serializer(), FlowRecord(state.viewer?.value, it)))) }
}.sortedBy { it.id }

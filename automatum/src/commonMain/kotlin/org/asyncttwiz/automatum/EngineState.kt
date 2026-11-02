package org.asyncttwiz.automatum

import arcanum.api.CardDefinition
import arcanum.api.Zone
import arcanum.engine.state.Attacker
import arcanum.engine.state.Blocker
import arcanum.engine.state.CombatState
import arcanum.engine.state.ControlSince
import arcanum.engine.state.GameObject
import arcanum.engine.state.GameState
import arcanum.engine.state.LoggedChange
import arcanum.engine.state.ObjectId
import arcanum.engine.state.PlayerId
import arcanum.engine.state.PlayerState
import arcanum.engine.state.PriorityState
import arcanum.engine.state.StackItem
import arcanum.engine.state.TargetRef
import arcanum.engine.state.TurnState
import org.asyncttwiz.protocol.Automatum
import org.asyncttwiz.protocol.BoardState
import org.asyncttwiz.protocol.CardVisibility
import org.asyncttwiz.protocol.CombatStackRules
import org.asyncttwiz.protocol.MtgFacts
import org.asyncttwiz.protocol.MtgRef
import org.asyncttwiz.protocol.MtgStackKind
import org.asyncttwiz.protocol.MtgTag
import org.asyncttwiz.protocol.PlayCard
import org.asyncttwiz.protocol.PlayState
import org.asyncttwiz.protocol.PlayZone
import org.asyncttwiz.protocol.StackEffect
import org.asyncttwiz.protocol.TurnPosition

/** ATW holds something the engine can't take over (§5: turning Automatum on with it asks first). */
class Unsupported(val reasons: List<String>) : IllegalStateException("Not representable for the engine: ${reasons.joinToString("; ")}")

/**
 * The engine's state of an ATW game (I-D7: ATW keeps its own game state; the engine is a pure function over it):
 * built afresh for every engine call from the shared board, the game's card definitions and the history's event log.
 */
object EngineState {
    /**
     * [state]'s board as the engine's [GameState], as [viewer] sees it (D60; null: full information).
     *
     * - [definitions]: the game's card definitions by ATW definition digest (I0-b's `CardDefinitions.forDefinitions`);
     *   a token keeps the definition the engine made it with in its ledger.
     * - [log]: this and last turn's tagged events (D33), from the history's tags ([EngineLog.from]).
     *
     * Players in ATW's order; library, hand and graveyard in their order (ATW lists a graveyard top first, the engine
     * bottom first); the Battlefield and Exile, which ATW keeps per player and the engine as one list each, in timestamp
     * order. A spell's card stays where ATW has it (STK-001, STK-003) and is on the engine's Stack while a Stack entry
     * carries its spell item. Hidden cards are unknown objects for a viewer: what ATW shows them ([CardVisibility]) and
     * what the engine lets them see (their hand, face-down permanents they control) is known.
     *
     * @throws Unsupported when the board holds something the engine can't represent ([unsupported]).
     */
    fun from(state: PlayState, definitions: Map<String, CardDefinition>, viewer: String? = null,
        log: List<LoggedChange> = emptyList()): GameState {
        unsupported(state, definitions, viewer).takeIf { it.isNotEmpty() }?.let { throw Unsupported(it) }
        val board = state.board
        val ids = CardIds.of(board)
        val containers = board.containers.associateBy { it.id }
        val records = board.engineRecords.associateBy { it.id }
        val entries = entries(board)
        // A spell being cast is on the Stack before its item is made (CR 601.2a): its entry holds only its card
        val items: Map<String, StackItem> = entries.mapNotNull { e -> e.item?.takeIf { it.data.isNotEmpty() }?.let { e.id to EngineCodec.stackItem(it) } }.toMap()
        fun stackId(entry: StackEffect): ObjectId = items[entry.id]?.id ?: ids.obj(entry.item!!.id)!!
        val spells = spellCards(board)

        val objects = LinkedHashMap<ObjectId, GameObject>()
        val byId = board.cards.associateBy { it.id }
        for (card in board.cards) {
            val container = containers.getValue(card.containerId)
            val ledger = CardLedger.of(card.ledger)
            val spell = spells[card.id]
            val zone = if (spell != null) Zone.Stack else zone(container.zone)
            val engineId = ObjectId.Card(ids.engine(card.id), card.zoneChanges)
            val derivedController = Derived.controller(zone, container.accountId, spell?.item?.controller?.let(::PlayerId))
            var obj = GameObject(
                id = engineId,
                owner = PlayerId(ledger.ownerOverride ?: Derived.owner(state, card)),
                controller = when (val c = ledger.controllerOverride) { null -> derivedController; "" -> null; else -> PlayerId(c) },
                zone = zone,
                definition = ledger.definition ?: definitions[card.definition],
                face = ledger.faceOverride ?: Derived.face(state, card),
                token = card.token,
                tapped = card.tapped,
                faceDown = card.isFaceDown,
                phasedOut = ledger.phasedOut,
                marks = ledger.marks,
                counters = EngineCodec.ordered(card.typedCounters, ledger.counterOrder),
                damage = card.damage.toIntOrNull() ?: 0,
                deathtouchDamage = ledger.deathtouchDamage,
                attachedTo = card.attachedTo?.let(ids::target),
                timestamp = card.timestamp,
                controlSince = if (ledger.noControlSince) null else ledger.controlSince
                    ?: Derived.controlSince(zone, container.accountId, card),
                memory = ledger.memory,
                facts = ledger.facts,
                goadedBy = ledger.goadedBy,
            )
            if (viewer != null && !known(board, card, obj, viewer)) obj = obj.copy(definition = null)
            objects[engineId] = obj
        }
        fun ordered(zone: PlayZone, owner: String): List<ObjectId> {
            val container = board.containers.single { it.zone == zone && it.accountId == owner && !it.temp }
            val cards = container.cards.filter { it !in spells }.map { id -> ObjectId.Card(ids.engine(id), byId.getValue(id).zoneChanges) }
            return if (zone == PlayZone.GRAVEYARD) cards.reversed() else cards
        }
        fun byTimestamp(zone: Zone) = objects.values.filter { it.zone == zone }.sortedWith(Derived.order).map { it.id }

        val turnExtras = records[EngineCodec.TURN]?.let { EngineCodec.decode(TurnExtras.serializer(), it.data) } ?: TurnExtras()
        val players = board.players.map { p ->
            val ledger = PlayerLedger.of(p.ledger)
            PlayerState(
                id = PlayerId(p.accountId), life = p.life.toInt(), startingLife = ledger.startingLife,
                counters = EngineCodec.ordered(p.typedCounters, ledger.counterOrder), manaPool = p.manaPool.map(EngineCodec::mana),
                lost = ledger.lost, designations = ledger.designations,
                library = ordered(PlayZone.LIBRARY, p.accountId), hand = ordered(PlayZone.HAND, p.accountId),
                graveyard = ordered(PlayZone.GRAVEYARD, p.accountId), ledger = ledger.ledger,
            )
        }
        val position = TurnPosition.of(board.turn)!!
        return GameState(
            players = players.toMutableList(),
            objects = objects,
            battlefield = byTimestamp(Zone.Battlefield).toMutableList(),
            stack = entries.reversed().map(::stackId).toMutableList(),
            exile = byTimestamp(Zone.Exile).toMutableList(),
            stackItems = LinkedHashMap(entries.reversed().mapNotNull { e -> items[e.id]?.let { it.id to it } }.toMap()),
            effects = LinkedHashMap(board.engineRecords.filter(EngineCodec::isEffect).map(EngineCodec::effect).associateBy { it.id }),
            delayedTriggers = LinkedHashMap(board.engineRecords.filter(EngineCodec::isDelayed).map(EngineCodec::delayed).associateBy { it.id }),
            turn = TurnState(board.turn.number.toInt(), PlayerId(board.turn.accountId), EngineCodec.step(position),
                turnExtras.landsPlayed, turnExtras.turnStarted ?: Derived.turnStarted(board), turnExtras.extraTurns),
            priority = board.priority?.let { PriorityState(it.holder?.let(::PlayerId), it.passes) } ?: PriorityState(null),
            combat = records[EngineCodec.COMBAT]?.let { EngineCodec.decode(combatSerializer, it.data) } ?: lanes(board, ids),
            ledger = records[EngineCodec.LEDGER]?.let { EngineCodec.decode(ledgerSerializer, it.data).toMutableMap() } ?: mutableMapOf(),
            log = log.toMutableList(),
            pending = records[Automatum.CONTINUATION_ID]?.let { EngineCodec.decode(FlowRecord.serializer(), it.data).pending },
            timestampCounter = MtgFacts.clock(board),
            triggered = records[EngineCodec.TRIGGERED]?.let { EngineCodec.decode(triggeredSerializer, it.data).toMutableList() } ?: mutableListOf(),
            skipFirstDraw = turnExtras.skipFirstDraw,
            viewer = viewer?.let(::PlayerId),
        )
    }

    /** Whose view an open shared flow was made in (D60): it is resumed in that view. Null when no flow is open or it was
     * made with full information. */
    fun flowViewer(board: BoardState): String? = board.engineRecords.singleOrNull { it.id == Automatum.CONTINUATION_ID }
        ?.let { EngineCodec.decode(FlowRecord.serializer(), it.data).viewer }

    /**
     * What on [state]'s board the engine can't take over (§5), each as a sentence; empty when [from] can build it:
     * manual Stack entries (STK-016, no structured item), cards in Temp piles (D38: a manual tool), cards whose card
     * data the parser couldn't read (unless hidden from [viewer]), and life totals, damage or turn numbers that aren't
     * plain numbers.
     */
    fun unsupported(state: PlayState, definitions: Map<String, CardDefinition>, viewer: String? = null): List<String> {
        val board = state.board
        val problems = mutableListOf<String>()
        val entries = entries(board)
        entries.filter { it.item == null }.forEach { problems += "Stack entry ${it.id} is a manual entry" }
        board.containers.filter { it.temp && it.cards.isNotEmpty() }.forEach { problems += "Temp pile ${it.id} holds cards" }
        val spells = spellCards(board)
        entries.mapNotNull { it.item }.filter { it.kind == MtgStackKind.Spell }.forEach { item ->
            val ref = item.id as? MtgRef.Card
            if (ref == null || board.cards.none { it.id == ref.cardId && it.zoneChanges == ref.zoneChanges })
                problems += "the spell ${item.id} isn't a card on the board"
        }
        for (card in board.cards) {
            val ledger = CardLedger.of(card.ledger)
            if (ledger.definition == null && card.definition !in definitions) {
                val zone = if (card.id in spells) Zone.Stack else board.containers.singleOrNull { it.id == card.containerId }?.zone?.let(::zone)
                val hidden = viewer != null && zone != null && zone != Zone.Battlefield && zone != Zone.Graveyard &&
                    zone != Zone.Stack && !(zone == Zone.Exile && !card.isFaceDown) &&
                    !(zone == Zone.Hand && card.owner == viewer) && !CardVisibility.isVisibleFaceUp(board, card, viewer)
                if (!hidden) problems += "card ${card.id} has no rules definition"
            }
            if (card.damage.isNotEmpty() && card.damage.toIntOrNull() == null) problems += "card ${card.id} has damage \"${card.damage}\""
        }
        board.players.filter { it.life.toIntOrNull() == null }.forEach { problems += "player ${it.accountId} has life \"${it.life}\"" }
        if (TurnPosition.of(board.turn) == null || board.turn.number.toIntOrNull() == null) problems += "the turn isn't a known step"
        return problems
    }

    /** The Stack entries, top first. */
    internal fun entries(board: BoardState): List<StackEffect> =
        CombatStackRules.stack(board).cards.map { id -> board.stackEffects.single { it.id == id } }

    /** The cards on the engine's Stack as spells (D35), by ATW card id: each is a spell item's own card object. */
    internal fun spellCards(board: BoardState): Map<String, StackEffect> = entries(board).mapNotNull { entry ->
        val ref = entry.item?.takeIf { it.kind == MtgStackKind.Spell }?.id as? MtgRef.Card ?: return@mapNotNull null
        board.cards.singleOrNull { it.id == ref.cardId && it.zoneChanges == ref.zoneChanges }?.let { it.id to entry }
    }.toMap()

    internal fun zone(zone: PlayZone): Zone = when (zone) {
        PlayZone.LIBRARY -> Zone.Library
        PlayZone.HAND -> Zone.Hand
        PlayZone.BATTLEFIELD -> Zone.Battlefield
        PlayZone.GRAVEYARD -> Zone.Graveyard
        PlayZone.EXILE -> Zone.Exile
        PlayZone.STACK -> Zone.Stack
    }

    /** What [viewer] may know of [card] (D60): what the engine lets them see where it is, a token, and whatever ATW
     * shows them (revealed cards, a library top kept revealed, an open panel). */
    private fun known(board: BoardState, card: PlayCard, o: GameObject, viewer: String): Boolean {
        val public = o.zone in setOf(Zone.Battlefield, Zone.Graveyard, Zone.Exile, Zone.Stack, Zone.Command) && !o.faceDown
        return public || card.token || (o.zone == Zone.Hand && o.owner.value == viewer) ||
            (o.faceDown && (o.zone == Zone.Battlefield || o.zone == Zone.Stack) && (o.controller ?: o.owner).value == viewer) ||
            CardVisibility.isVisibleFaceUp(board, card, viewer)
    }

    /** Combat as ATW's lanes show it, for a combat declared by hand (no engine record): each pair's attackers attack its
     * target (a planeswalker or battle card) or the defending player; its blockers block them. */
    private fun lanes(board: BoardState, ids: CardIds): CombatState? {
        val area = board.combat?.takeIf { it.pairs.isNotEmpty() } ?: return null
        val defender = CombatStackRules.defendingPlayer(board)
        fun obj(cardId: String) = ObjectId.Card(ids.engine(cardId), board.cards.single { it.id == cardId }.zoneChanges)
        val attackers = area.pairs.flatMap { pair ->
            val target = pair.attackTarget?.let { TargetRef.Object(obj(it)) } ?: TargetRef.Player(PlayerId(defender!!))
            pair.attackers.map { Attacker(obj(it.sourceId), target, blocked = pair.blocked || pair.blockers.isNotEmpty()) }
        }
        val blockers = area.pairs.flatMap { pair -> pair.blockers.map { b ->
            Blocker(obj(b.sourceId), pair.attackers.map { obj(it.sourceId) }, defender?.let(::PlayerId)) } }
        return CombatState(attackers, blockers)
    }
}

/** Values the engine derives from ATW's own fields, shared by [EngineState.from] and [EngineBatch] so both read ATW
 * the same way. */
internal object Derived {
    /** The order of the engine's Battlefield and Exile, which ATW keeps per player. */
    val order: Comparator<GameObject> = compareBy<GameObject> { it.timestamp }.thenBy { it.id.toString() }

    /** The owner ATW gives a card: the player whose deck it came from (`GameplayRules.originalOwner`); a token's owner
     * is who made it. */
    fun owner(state: PlayState, card: PlayCard): String =
        if (card.token) card.owner else state.shuffles.firstOrNull { card.id in it.order }?.accountId ?: card.owner

    /** The engine face ATW shows: its card face's position. Cards whose parts share one printed face (split, adventure,
     * flip) show face 0; the half being cast is in the ledger. */
    fun face(state: PlayState, card: PlayCard): Int =
        faces(state, card).indexOfFirst { it.faceId == card.faceId }.coerceAtLeast(0)

    fun faces(state: PlayState, card: PlayCard) =
        state.definitions.singleOrNull { it.digest == card.definition }?.definition?.faces.orEmpty()

    /** Who has controlled a permanent since when (CR 302.6) by ATW's own record of the turn it entered (rules v29);
     * one older than that has been there since before the game's turns were counted. */
    fun controlSince(zone: Zone, containerPlayer: String?, card: PlayCard): ControlSince? =
        if (zone != Zone.Battlefield || containerPlayer == null) null
        else ControlSince(PlayerId(containerPlayer), card.enteredTurn.toIntOrNull() ?: 0)

    /** The turn each player last began (CR 302.6), as ATW's alternating two-player turns say: the active player this
     * one, the other the one before. */
    fun turnStarted(board: BoardState): Map<PlayerId, Int> {
        val number = board.turn.number.toInt()
        val other = board.players.firstOrNull { it.accountId != board.turn.accountId }?.accountId
        return mapOf(PlayerId(board.turn.accountId) to number) + listOfNotNull(other?.takeIf { number > 1 }?.let { PlayerId(it) to number - 1 })
    }

    /** The controller field ATW's place says (CR 110.2): the Battlefield's player, a spell's controller. */
    fun controller(zone: Zone, containerPlayer: String?, spellController: PlayerId?): PlayerId? = when (zone) {
        Zone.Battlefield -> containerPlayer?.let(::PlayerId)
        Zone.Stack -> spellController
        else -> null
    }
}

/** The engine's event log (D33) from the history: each automated event's tag carries its part of it. */
object EngineLog {
    /** The log made by [tags], the tags of the history's events in order (manual events' tags carry none). */
    fun from(tags: List<MtgTag?>): List<LoggedChange> {
        val log = mutableListOf<LoggedChange>()
        for (tag in tags) {
            val data = tag?.data?.takeIf { it.isNotEmpty() } ?: continue
            val parsed = runCatching { EngineCodec.decode(TagData.serializer(), data) }.getOrNull() ?: continue
            for (op in parsed.log) when (op) {
                is LogOp.Add -> log += op.entry
                LogOp.Drop -> if (log.isNotEmpty()) log.removeAt(log.lastIndex)
            }
        }
        return log
    }
}


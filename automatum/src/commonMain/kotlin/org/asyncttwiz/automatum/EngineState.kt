package org.asyncttwiz.automatum

import arcanum.api.CardDefinition
import arcanum.api.Duration
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
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import org.asyncttwiz.protocol.Automatum
import org.asyncttwiz.protocol.BoardState
import org.asyncttwiz.protocol.CardVisibility
import org.asyncttwiz.protocol.CombatStackRules
import org.asyncttwiz.protocol.EngineRecordKind
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
        // I-D21: a card the device couldn't read plays with its printed values and manual abilities
        val printed = mutableMapOf<String, CardDefinition?>()
        fun definition(card: PlayCard): CardDefinition? = definitions[card.definition]
            ?: printed.getOrPut(card.definition) { PrintedCards.definition(state, card) }
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
                definition = ledger.definition ?: definition(card),
                face = ledger.faceOverride ?: Derived.face(state, card),
                token = card.token && !ledger.copyOfCard,
                copyOfCard = ledger.copyOfCard,
                tapped = card.tapped,
                // A spell's card stays in its ATW zone (STK-001): a spell cast face down is face down by its entry (STK-005)
                faceDown = spell?.faceDown ?: card.isFaceDown,
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
        // Emblems live only in their records (I-D25): ATW has no command zone
        board.engineRecords.filter(EngineCodec::isEmblem).map(EngineCodec::emblem).forEach { objects[it.id] = it }
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
                skipWhenNoChoice = p.skipWhenNoChoice,
            )
        }
        val position = TurnPosition.of(board.turn)!!
        return GameState(
            players = players.toMutableList(),
            objects = objects,
            battlefield = byTimestamp(Zone.Battlefield).toMutableList(),
            stack = entries.reversed().map(::stackId).toMutableList(),
            exile = byTimestamp(Zone.Exile).toMutableList(),
            command = byTimestamp(Zone.Command).toMutableList(),
            stackItems = LinkedHashMap(entries.reversed().mapNotNull { e -> items[e.id]?.let { it.id to it } }.toMap()),
            // I-D28, i12review #4 (CR 514.2, 511.2): a turn that changed while Automatum was off ran no cleanup, so effects
            // "until end of turn" or "until end of combat" of an earlier turn are taken as ended
            effects = LinkedHashMap(board.engineRecords.filter(EngineCodec::isEffect).map(EngineCodec::effect)
                .filterNot { it.createdTurn < board.turn.number.toInt() && (it.duration == Duration.EndOfTurn || it.duration == Duration.EndOfCombat) }
                .associateBy { it.id }),
            delayedTriggers = LinkedHashMap(board.engineRecords.filter(EngineCodec::isDelayed).map(EngineCodec::delayed).associateBy { it.id }),
            turn = TurnState(board.turn.number.toInt(), PlayerId(board.turn.accountId), EngineCodec.step(position),
                turnExtras.landsPlayed.takeIf { turnExtras.turn == null || turnExtras.turn == board.turn.number.toInt() } ?: emptyMap(), turnExtras.turnStarted ?: Derived.turnStarted(board), turnExtras.extraTurns),
            // #10 (CR 117.4, I-D28): passes in a row count only in the step they were made in; a step or turn changed by
            // hand while Automatum was off starts the count again
            priority = board.priority?.let { PriorityState(it.holder?.let(::PlayerId),
                if (it.passes > 0 && turnExtras.passesAt != null && turnExtras.passesAt != TurnExtras.at(board.turn.number, position)) 0 else it.passes)
            } ?: PriorityState(null),
            // In the declare attackers step lanes without the engine's combat are a draft of the declaration (I3b)
            // #1 (I-D28): the engine's combat record only while it is this board's combat (a combat finished by hand while
            // Automatum was off isn't Automatum's any more)
            combat = EngineCodec.currentCombat(board)
                ?: lanes(board, ids).takeUnless { position == TurnPosition.DECLARE_ATTACKERS },
            ledger = records[EngineCodec.LEDGER]?.let { EngineCodec.decode(ledgerSerializer, it.data).toMutableMap() } ?: mutableMapOf(),
            log = log.toMutableList(),
            pending = records[Automatum.CONTINUATION_ID]?.let { TagCodec.of(state, definitions).decode(FlowRecord.serializer(), it.data).pending },
            timestampCounter = MtgFacts.clock(board),
            triggered = records[EngineCodec.TRIGGERED]?.let { EngineCodec.decode(triggeredSerializer, it.data).toMutableList() } ?: mutableListOf(),
            skipFirstDraw = turnExtras.skipFirstDraw,
            viewer = viewer?.let(::PlayerId),
        )
    }

    /** Whose view an open shared flow was made in (D60): it is resumed in that view. Null when no flow is open or it was
     * made with full information. */
    fun flowViewer(board: BoardState): String? = board.engineRecords.singleOrNull { it.id == Automatum.CONTINUATION_ID }
        ?.let { runCatching { (EngineCodec.json.parseToJsonElement(it.data).jsonObject["viewer"] as? JsonPrimitive)?.contentOrNull }.getOrNull() }

    /** Whether the open shared flow on [board] waits for something done by hand (`Decision.Manual`, I-D37), read straight
     * from its continuation record without building the engine state (#5: known at once on every board). Never throws. */
    fun byHand(board: BoardState): Boolean = board.engineRecords.singleOrNull { it.id == Automatum.CONTINUATION_ID }?.let { record ->
        runCatching {
            val decision = EngineCodec.json.parseToJsonElement(record.data).jsonObject["pending"]?.jsonObject?.get("decision")?.jsonObject
            (decision?.get("type") as? JsonPrimitive)?.contentOrNull == MANUAL
        }.getOrDefault(false)
    } == true

    /** The serial name of `Decision.Manual`. */
    private const val MANUAL = "manual"

    /**
     * What on [state]'s board the engine can't take over (§5), each as a plain sentence players read (the Automatum
     * panel, a refused cast): empty when [from] can build it: manual Stack entries (STK-016, no structured item), cards
     * in Temp piles (D38: a manual tool), engine data this version can't read, and life totals, damage or turn numbers
     * that aren't plain numbers. Cards are named only when [viewer] may see them face up (I-D27; null: when everyone
     * may), players as "your" and "the other player's". A card whose card data the parser couldn't read isn't one of
     * them: it plays with its printed values and manual abilities ([PrintedCards], I-D21). [definitions] is as for [from].
     */
    fun unsupported(state: PlayState, definitions: Map<String, CardDefinition>, viewer: String? = null): List<String> {
        val board = state.board
        val words = Words(state, viewer)
        val problems = mutableListOf<String>()
        val entries = entries(board)
        entries.filter { it.item == null }.forEach { problems += "${words.entry(it)} on the Stack is a manual entry" }
        board.containers.filter { it.temp && it.cards.isNotEmpty() }.forEach { problems += "${words.player(it.accountId, true)} Temp pile holds cards" }
        entries.filter { it.item?.kind == MtgStackKind.Spell }.forEach { entry ->
            val ref = entry.item!!.id as? MtgRef.Card
            if (ref == null || board.cards.none { it.id == ref.cardId && it.zoneChanges == ref.zoneChanges })
                problems += "a spell on the Stack isn't a card on the table any more"
        }
        for (card in board.cards) {
            if (card.damage.isNotEmpty() && card.damage.toIntOrNull() == null) problems += "${words.card(card)} has damage \"${card.damage}\", not a number"
        }
        board.players.filter { it.life.toIntOrNull() == null }.forEach { problems += "${words.player(it.accountId, true)} life \"${it.life}\" isn't a number" }
        if (TurnPosition.of(board.turn) == null || board.turn.number.toIntOrNull() == null) problems += "the turn isn't a known step"
        problems += unreadable(board, TagCodec.of(state, definitions), words)
        return problems.map { it.replaceFirstChar(Char::uppercaseChar) }
    }

    /** Names in [unsupported]'s sentences: cards only as [viewer] may see them, players relative to [viewer]. */
    private class Words(private val state: PlayState, private val viewer: String?) {
        fun card(card: PlayCard?): String {
            card ?: return "a card"
            if (!CardVisibility.isVisibleFaceUp(state.board, card, viewer ?: "")) return "a hidden card"
            val faces = state.definitions.singleOrNull { it.digest == card.definition }?.definition?.faces
            return (faces?.singleOrNull { it.faceId == card.faceId } ?: faces?.firstOrNull())?.name ?: "a card"
        }
        fun entry(entry: StackEffect): String =
            if (entry.faceDown) "a face-down spell"
            else card(state.board.cards.singleOrNull { it.id == entry.sourceId })
        fun player(id: String?, possessive: Boolean): String = when {
            viewer != null && id == viewer -> if (possessive) "your" else "you"
            viewer != null && id != null -> if (possessive) "the other player's" else "the other player"
            else -> if (possessive) "a player's" else "a player"
        }
    }

    /**
     * The engine data on [board] the engine can't read back (P8: the server checks only that it is JSON; an app update
     * may change its shape, D62): records, Stack items, ledgers, typed counter kinds and mana. Each as a sentence.
     */
    private fun unreadable(board: BoardState, codec: TagCodec, words: Words): List<String> {
        val problems = mutableListOf<String>()
        val version = "can't be read by this version of the app"
        fun check(what: String, read: () -> Unit) {
            runCatching(read).onFailure { problems += "$what $version" }
        }
        for (record in board.engineRecords) check(when (record.kind) {
            EngineRecordKind.Effect -> "an effect Automatum recorded"
            EngineRecordKind.DelayedTrigger -> "a delayed trigger Automatum recorded"
            EngineRecordKind.Continuation -> "Automatum's open choice"
            EngineRecordKind.Emblem -> "an emblem"
            EngineRecordKind.PreAnswer -> "an early answer"
            else -> "what Automatum recorded about the game"
        }) {
            when (record.kind) {
                EngineRecordKind.Effect -> EngineCodec.effect(record)
                EngineRecordKind.DelayedTrigger -> EngineCodec.delayed(record)
                EngineRecordKind.Ledger -> EngineCodec.decode(ledgerSerializer, record.data)
                EngineRecordKind.Turn -> EngineCodec.decode(TurnExtras.serializer(), record.data)
                EngineRecordKind.Combat -> EngineCodec.combat(record)
                EngineRecordKind.Triggered -> EngineCodec.decode(triggeredSerializer, record.data)
                EngineRecordKind.Continuation -> codec.decode(FlowRecord.serializer(), record.data)
                EngineRecordKind.Emblem -> EngineCodec.emblem(record)
                EngineRecordKind.PreAnswer -> PreAnswers.decode(record)
            }
        }
        for (entry in board.stackEffects) {
            val item = entry.item ?: continue
            val what = "Automatum's data of ${words.entry(entry)} on the Stack"
            if (item.data.isNotEmpty()) check(what) { EngineCodec.stackItem(item) }
            else if (item.kind != MtgStackKind.Spell) problems += "$what is missing"
        }
        for (card in board.cards) {
            if (card.ledger.isNotEmpty()) check("Automatum's ledger of ${words.card(card)}") { CardLedger.of(card.ledger) }
            card.typedCounters.forEach { counter -> check("the \"${counter.kind}\" counters on ${words.card(card)}") { EngineCodec.counterKind(counter.kind) } }
        }
        for (player in board.players) {
            if (player.ledger.isNotEmpty()) check("Automatum's ledger of ${words.player(player.accountId, false)}") { PlayerLedger.of(player.ledger) }
            player.typedCounters.forEach { counter -> check("${words.player(player.accountId, true)} \"${counter.kind}\" counters") { EngineCodec.counterKind(counter.kind) } }
            player.manaPool.forEach { mana -> check("the mana in ${words.player(player.accountId, true)} pool") { EngineCodec.mana(mana) } }
        }
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
    /**
     * The log made by [tags], the tags of the history's events in order (manual events' tags carry none), on the game
     * [state] with its card [definitions] (by ATW definition digest): a tag names the cards' definitions by reference
     * ([TagCodec]).
     *
     * @throws Unsupported when a tag's engine data can't be read (made by another app version, or not by this mapping):
     *   the engine would otherwise miss events of this turn without saying so.
     */
    fun from(tags: List<MtgTag?>, state: PlayState, definitions: Map<String, CardDefinition>): List<LoggedChange> =
        Decoder(definitions).log(tags, state)

    /** The entries of [log] an engine call reads (D33, [GameState.forgetOlderTurns]): this and last turn's, for a game in
     * turn [turn]. All of them when [turn] is null. */
    fun recent(log: List<LoggedChange>, turn: Int?): List<LoggedChange> =
        if (turn == null || log.all { it.turn >= turn - 1 }) log else log.filter { it.turn >= turn - 1 }

    /**
     * [from] for one game's history as it grows (SYS-025, no full-history rebuild): each tag's engine data is read once
     * and kept, so a longer history costs only its new tags. The game's card data never changes for a card (tokens'
     * definitions are written out in full), so what was read on an earlier board reads the same on a later one.
     * Not for use from several threads at once (callers lock it). [definitions] as for [from].
     */
    class Decoder(private val definitions: Map<String, CardDefinition>) {
        private val read = HashMap<String, List<LogOp>>()
        private var codecFor: PlayState? = null
        private var codec: TagCodec? = null

        /** [from] of [tags] on [state]. @throws Unsupported as [from]. */
        fun log(tags: List<MtgTag?>, state: PlayState): List<LoggedChange> {
            val log = mutableListOf<LoggedChange>()
            val unreadable = mutableListOf<String>()
            for ((i, tag) in tags.withIndex()) {
                val data = tag?.data?.takeIf { it.isNotEmpty() } ?: continue
                val ops = read[data] ?: runCatching { codec(state).decode(data).log }.getOrElse {
                    unreadable += "an earlier Automatum step in the history (event ${i + 1}) can't be read by this version of the app"
                    continue
                }.also { read[data] = it }
                for (op in ops) when (op) {
                    is LogOp.Add -> log += op.entry
                    LogOp.Drop -> if (log.isNotEmpty()) log.removeAt(log.lastIndex)
                }
            }
            if (unreadable.isNotEmpty()) throw Unsupported(unreadable)
            return log
        }

        private fun codec(state: PlayState): TagCodec {
            if (codecFor?.board !== state.board) { codec = TagCodec.of(state, definitions); codecFor = state }
            return codec!!
        }
    }
}

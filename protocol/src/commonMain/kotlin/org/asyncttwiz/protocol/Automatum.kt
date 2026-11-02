@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)

package org.asyncttwiz.protocol

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*

/**
 * Rules v36 (Arcanum integration spec §4, phase I0): the shared game state and events ATW keeps for the Arcanum rules
 * engine. Plumbing only: nothing here changes how a game plays with Automatum off, and events recorded under older
 * rules replay from their sealed deltas unchanged (SYS-031).
 *
 * What ATW stores, in MTG terms the engine's `GameState` maps to one to one:
 * - the Automatum switch ([BoardState.automatum], [MtgAction.SetAutomatum]);
 * - per card: [PlayCard.zoneChanges] (the engine's `ObjectId.Card(card, zoneChanges)`, D21), [PlayCard.timestamp]
 *   (CR 613.7, D34), [PlayCard.typedCounters], [PlayCard.attachedTo], [PlayCard.ledger] (opaque, D22); damage is the
 *   existing [PlayCard.damage] (TBL-024);
 * - per player: [PlayPlayer.manaPool], [PlayPlayer.typedCounters], [PlayPlayer.ledger];
 * - the priority holder and passes in a row ([BoardState.priority], D57);
 * - structured Stack items on Stack entries ([StackEffect.item], D19), beside today's manual entries;
 * - opaque engine records, each its own ([BoardState.engineRecords]: effect objects, delayed triggers, the game
 *   ledger, turn and combat extras, waiting triggers, the open continuation of a shared flow, D58, I-D11);
 * - MTG tags and `CausedBy` on history events ([GameplayMutation.tag], D33).
 *
 * Every [MtgAction] carries the values it expects before and sets after; it is refused when the board changed
 * underneath it, so an engine batch built on another state is never half applied (SYS-010). Its effect is recorded
 * in the ordinary sealed delta, per record, so ATW's undo rule sees one tuple per record and never one game-wide blob.
 *
 * Automatic bookkeeping of every v36 event, with or without Automatum ([MtgFacts]): zone changes count up, objects
 * get timestamps as they enter a zone, are attached or turn face up, down or to another face; a shuffle makes the
 * library's cards new objects (CR 701.20d, D38); a step or turn change empties mana pools (CR 106.4); a single-card
 * move is tagged with the meaning the move alone defines (D33, D42).
 */
object Automatum {
    const val RULES_VERSION = 36
    /** Opaque engine JSON per field, at most this many UTF-8 bytes. */
    const val MAX_DATA_BYTES = 256 * 1024
    const val MAX_NAME_LENGTH = 256
    const val MAX_LIST = 1000
    /** An emblem's rules text ([EngineRecord.text]), at most this many UTF-8 bytes. */
    const val MAX_TEXT_BYTES = 8 * 1024
    /** The id of the one open continuation of a shared flow (D58, I-D11); private flows stay on the device. */
    const val CONTINUATION_ID = "continuation"
    /** The highest timestamp an event may give or name (2^53 − 1, exact in JSON numbers everywhere): a stamp beyond it
     * is refused, so no event can push the clock ([MtgFacts.clock]) to overflow. */
    const val MAX_TIMESTAMP = 9_007_199_254_740_991L

    /** Whether a shared flow is open: the board holds the continuation (I-D8). */
    fun flowOpen(board: BoardState): Boolean = board.engineRecords.any { it.id == CONTINUATION_ID }

    /** Whether [board] holds data "Clear Automatum data" removes ([MtgAction.ClearRecords]): engine records, the priority
     * holder, card and player ledgers. Typed counters and mana pools are game values and stay. */
    fun recorded(board: BoardState): Boolean = board.engineRecords.isNotEmpty() || board.priority != null ||
        board.cards.any { it.ledger.isNotEmpty() } || board.players.any { it.ledger.isNotEmpty() }

    /** I-D15: why Automatum can't be turned on now (a [RuleRefusals] code), or null when it can: the Stack holds a
     * manual entry (one without a structured item, STK-016) or a Temp pile holds cards (D38). */
    fun turnOnRefusal(board: BoardState): String? {
        val stack = board.containers.singleOrNull { it.zone == PlayZone.STACK }?.cards.orEmpty()
        val entries = board.stackEffects.associateBy { it.id }
        if (stack.any { entries[it]?.item == null }) return RuleRefusals.AUTOMATUM_MANUAL_STACK
        if (board.containers.any { it.temp && it.cards.isNotEmpty() }) return RuleRefusals.AUTOMATUM_TEMP_PILES
        return null
    }

    /** Empty, or one JSON value of at most [MAX_DATA_BYTES] without NUL characters. */
    fun data(value: String): Boolean = value.isEmpty() || DeckValidation.text(value, MAX_DATA_BYTES) &&
        runCatching { Json.parseToJsonElement(value) }.isSuccess

    fun name(value: String): Boolean = value.isNotEmpty() && value.length <= MAX_NAME_LENGTH && DeckValidation.text(value) &&
        value.none { it.isISOControl() }
}

/** An object or player as the engine names it (D21, D35): a card or token with its zone-change count, a player, or a
 * Stack item that isn't a card (an ability or a copy of a spell, by the engine's id). */
@Serializable sealed interface MtgRef {
    @Serializable @SerialName("card") data class Card(val cardId: String, val zoneChanges: Int) : MtgRef
    @Serializable @SerialName("player") data class Player(val accountId: String) : MtgRef
    @Serializable @SerialName("stack") data class StackItem(val id: String) : MtgRef
}

/** [holder] has priority (null: nobody, as in untap and cleanup) and [passes] players in a row have passed (D57). */
@Serializable data class MtgPriority(val holder: String?, val passes: Int = 0)

/** [count] counters of [kind] (+1/+1, loyalty, poison, …, as the engine names the kind) with the timestamp the kind
 * last got (CR 613.7c). The six colour counters stay manual markers beside these (TBL-078). */
@Serializable data class TypedCounter(val kind: String, val count: Int, val timestamp: Long)

/** One mana in a pool (CR 106.4): [color] is W, U, B, R, G or C; [data] is the engine's opaque rest of it (source,
 * restrictions, riders, how long it stays). */
@Serializable data class PoolMana(val color: String,
    @EncodeDefault(EncodeDefault.Mode.NEVER) val data: String = "") {
    companion object { val COLORS = setOf("W", "U", "B", "R", "G", "C") }
}

/** What kind of Stack object a structured item is (CR 112, 113.7, 707.10). */
@Serializable sealed interface MtgStackKind {
    @Serializable @SerialName("spell") data object Spell : MtgStackKind
    @Serializable @SerialName("spell-copy") data object SpellCopy : MtgStackKind
    @Serializable @SerialName("activated") data object Activated : MtgStackKind
    @Serializable @SerialName("triggered") data object Triggered : MtgStackKind
}

/**
 * A structured Stack item (D19) on a Stack entry: the engine's [id] for it (a card object for a spell, else its Stack
 * item id), which [ability] (the engine's origin key) of which [source], [controller], [targets] by slot, chosen
 * [modes] and announced [values] ("X"), and its [timestamp] (CR 613.7d). [data] is the engine's opaque rest: the
 * ability it runs, divisions, bound names, the event it triggered on and the last known information of the objects
 * it refers to (D21).
 */
@Serializable data class MtgStackItem(val id: MtgRef, val kind: MtgStackKind, val controller: String,
    @EncodeDefault(EncodeDefault.Mode.NEVER) val ability: String? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER) val source: MtgRef? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER) val targets: Map<String, List<MtgRef>> = emptyMap(),
    @EncodeDefault(EncodeDefault.Mode.NEVER) val modes: List<Int> = emptyList(),
    @EncodeDefault(EncodeDefault.Mode.NEVER) val values: Map<String, Int> = emptyMap(),
    val timestamp: Long,
    @EncodeDefault(EncodeDefault.Mode.NEVER) val data: String = "")

/** What an engine record holds. Only the engine reads [EngineRecord.data]. */
@Serializable sealed interface EngineRecordKind {
    /** A continuous effect (D19, D26). */
    @Serializable @SerialName("effect") data object Effect : EngineRecordKind
    /** A delayed triggered ability (CR 603.7). */
    @Serializable @SerialName("delayed-trigger") data object DelayedTrigger : EngineRecordKind
    /** Game-scoped ledger entries (D22). */
    @Serializable @SerialName("ledger") data object Ledger : EngineRecordKind
    /** Turn facts ATW's turn doesn't hold: lands played, turns begun, extra turns. */
    @Serializable @SerialName("turn") data object Turn : EngineRecordKind
    /** Combat facts ATW's combat lanes don't hold: first-strike step, blockers declared, removed targets. */
    @Serializable @SerialName("combat") data object Combat : EngineRecordKind
    /** Triggered abilities waiting to be put on the Stack (CR 603.3). */
    @Serializable @SerialName("triggered") data object Triggered : EngineRecordKind
    /** The open decision and continuation of a shared flow (D58, I-D8, I-D11), id [Automatum.CONTINUATION_ID]. */
    @Serializable @SerialName("continuation") data object Continuation : EngineRecordKind
    /** An emblem in a player's command zone (CR 114, I-D25): ATW shows its [EngineRecord.text] as a chip by its
     * [EngineRecord.player]'s life square. */
    @Serializable @SerialName("emblem") data object Emblem : EngineRecordKind
}

/** One engine record ATW stores and doesn't interpret (P2, §4.3); [timestamp] for effect objects, delayed triggers
 * and emblems (CR 613.7b), so ATW's timestamp clock ([MtgFacts.clock]) sees every stamp. An [EngineRecordKind.Emblem]
 * also says what ATW shows of it: whose it is ([player]) and its rules [text] (I-D25); other records have neither. */
@Serializable data class EngineRecord(val id: String, val kind: EngineRecordKind,
    @EncodeDefault(EncodeDefault.Mode.NEVER) val timestamp: Long? = null, val data: String,
    @EncodeDefault(EncodeDefault.Mode.NEVER) val player: String? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER) val text: String = "")

/** What made a change, for history lines ("Storm Spark dealt 3 damage to Grizzly Cub"): the spell's or ability's
 * [source], by the [name] it had then, its [controller], and the Stack [item] resolving. */
@Serializable data class MtgCause(val controller: String,
    @EncodeDefault(EncodeDefault.Mode.NEVER) val source: MtgRef? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER) val name: String? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER) val item: MtgRef? = null)

/** An event's Magic meaning (D33): the engine's meaning names ("died", "cast", …), what made it ([causedBy]), and
 * [data], the engine's opaque event with its last known information. [board] (#4, SYS-057), on the first member of an
 * automated batch: the board the batch was planned on ([MtgFacts.digest]); the member is refused, and with it the whole
 * batch (SYS-010), when the board is another one, so ordinary actions in the batch never apply to values they weren't
 * planned from. [folded]: the event is shown inside its batch's visible event (I-D22, [MtgFacts.folds]). */
@Serializable data class MtgTag(val meanings: List<String> = emptyList(),
    @EncodeDefault(EncodeDefault.Mode.NEVER) val causedBy: MtgCause? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER) val data: String = "",
    @EncodeDefault(EncodeDefault.Mode.NEVER) val board: String? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER) val folded: Boolean = false) {
    companion object {
        private val meaning = Regex("[a-z][a-z0-9-]{0,39}")
        fun valid(tag: MtgTag): Boolean = tag.meanings.size <= 16 && tag.meanings.all { meaning.matches(it) } &&
            tag.meanings.distinct().size == tag.meanings.size && Automatum.data(tag.data) &&
            (tag.board?.let(DeckValidation::digest) ?: true) &&
            (tag.meanings.isNotEmpty() || tag.causedBy != null || tag.data.isNotEmpty() || tag.board != null || tag.folded) &&
            tag.causedBy?.let { cause -> DeckValidation.uuid(cause.controller) && (cause.source?.let(MtgRules::valid) ?: true) &&
                (cause.item?.let(MtgRules::valid) ?: true) && (cause.name?.let(Automatum::name) ?: true) } ?: true
    }
}

/** Rules v36 events of the engine's state. Each `Set…` names the value it expects ([before]) and the one it sets
 * ([after]); it is refused ("inapplicable") when the current value differs, and does nothing when they are equal. */
@Serializable sealed interface MtgAction {
    /** The Automatum switch for the whole game, both players (D20, I-D4). Turning it on is refused while the Stack holds
     * a manual entry or a Temp pile holds cards (I-D15, [Automatum.turnOnRefusal]), and gives the Battlefield's and
     * Exile's cards that have no timestamp yet (games begun before rules v36) stamps in a stated order
     * ([MtgFacts.stampUnstamped]). Turning it off always applies and closes an open flow's continuation; effect records
     * stay (I-D24). */
    @Serializable @SerialName("setAutomatum") data class SetAutomatum(val on: Boolean) : MtgAction
    /** "Clear Automatum data" (I-D24): removes every engine record (effects, delayed triggers, the open continuation, …),
     * the priority holder and the card and player ledgers ([Automatum.recorded]); typed counters and mana pools stay.
     * Always applies; changes nothing when there is nothing to clear. */
    @Serializable @SerialName("clearRecords") data object ClearRecords : MtgAction
    /** The actor's own "Skip when I have no choice" (I-D18, [PlayPlayer.skipWhenNoChoice]); only its player changes it. */
    @Serializable @SerialName("setSkipWhenNoChoice") data class SetSkipWhenNoChoice(val on: Boolean) : MtgAction
    @Serializable @SerialName("setPriority") data class SetPriority(val before: MtgPriority?, val after: MtgPriority?) : MtgAction
    @Serializable @SerialName("setManaPool") data class SetManaPool(val accountId: String, val before: List<PoolMana>,
        val after: List<PoolMana>) : MtgAction
    /** Counters of one [kind] on a card object or a player; null: none of that kind. */
    @Serializable @SerialName("setCounter") data class SetCounter(val holder: MtgRef, val kind: String,
        val before: TypedCounter?, val after: TypedCounter?) : MtgAction
    /** Attaches a card object (Aura, Equipment, Fortification, CR 301.5) to an object or player, or unattaches it
     * (null); attaching gives it a new timestamp (CR 613.7e). */
    @Serializable @SerialName("attach") data class Attach(val card: MtgRef.Card, val before: MtgRef?, val after: MtgRef?) : MtgAction
    /** Sets a card's zone-change count and timestamp as the engine made them (`Change.Restamped`, a shuffle that
     * kept cards, stamps given in another order). */
    @Serializable @SerialName("restamp") data class Restamp(val cardId: String, val before: MtgStamp, val after: MtgStamp) : MtgAction
    /** The opaque ledger (D22) of a card object or player. */
    @Serializable @SerialName("setLedger") data class SetLedger(val holder: MtgRef, val before: String, val after: String) : MtgAction
    /** The structured item of Stack entry [entryId] (a [StackEffect] id); null: a manual entry. */
    @Serializable @SerialName("setStackItem") data class SetStackItem(val entryId: String, val before: MtgStackItem?,
        val after: MtgStackItem?) : MtgAction
    /** Adds (null [before]), changes or removes (null [after]) the engine record [id]. */
    @Serializable @SerialName("setRecord") data class SetRecord(val id: String, val before: EngineRecord?,
        val after: EngineRecord?) : MtgAction
}

/** A card's object identity: its zone-change count (D21) and timestamp (D34). */
@Serializable data class MtgStamp(val zoneChanges: Int, val timestamp: Long)

/** Validation and execution of [MtgAction]s (P8: the server validates the actions and the new shared objects, not
 * whether an automated batch followed the rules). */
object MtgRules {
    fun valid(ref: MtgRef): Boolean = when (ref) {
        is MtgRef.Card -> DeckValidation.uuid(ref.cardId) && ref.zoneChanges >= 0
        is MtgRef.Player -> DeckValidation.uuid(ref.accountId)
        is MtgRef.StackItem -> Automatum.name(ref.id)
    }

    /** #8 (P8): a stamp lies within 0..[Automatum.MAX_TIMESTAMP]. */
    fun stamp(value: Long): Boolean = value in 0..Automatum.MAX_TIMESTAMP

    private fun valid(counter: TypedCounter?, kind: String) = counter == null ||
        counter.kind == kind && counter.count > 0 && stamp(counter.timestamp)

    private fun valid(record: EngineRecord?, id: String) = record == null || record.id == id && Automatum.data(record.data) &&
        stamp(record.timestamp ?: 0) && (record.kind == EngineRecordKind.Continuation) == (id == Automatum.CONTINUATION_ID) &&
        if (record.kind == EngineRecordKind.Emblem) record.player?.let(DeckValidation::uuid) == true &&
            DeckValidation.text(record.text, Automatum.MAX_TEXT_BYTES)
        else record.player == null && record.text.isEmpty()

    private fun valid(item: MtgStackItem?) = item == null || valid(item.id) && DeckValidation.uuid(item.controller) &&
        (item.ability?.let(Automatum::name) ?: true) && (item.source?.let(::valid) ?: true) &&
        item.targets.size <= Automatum.MAX_LIST && item.targets.all { (slot, refs) -> Automatum.name(slot) && refs.all(::valid) } &&
        item.modes.size <= Automatum.MAX_LIST && item.modes.all { it >= 0 } &&
        item.values.size <= Automatum.MAX_LIST && item.values.keys.all(Automatum::name) && stamp(item.timestamp) && Automatum.data(item.data)

    private fun valid(priority: MtgPriority?) = priority == null || priority.passes >= 0 &&
        (priority.holder?.let(DeckValidation::uuid) ?: true)

    private fun valid(pool: List<PoolMana>) = pool.size <= Automatum.MAX_LIST &&
        pool.all { it.color in PoolMana.COLORS && Automatum.data(it.data) }

    fun valid(action: MtgAction): Boolean = when (action) {
        is MtgAction.SetAutomatum, MtgAction.ClearRecords, is MtgAction.SetSkipWhenNoChoice -> true
        is MtgAction.SetPriority -> valid(action.before) && valid(action.after)
        is MtgAction.SetManaPool -> DeckValidation.uuid(action.accountId) && valid(action.before) && valid(action.after)
        is MtgAction.SetCounter -> (action.holder is MtgRef.Card || action.holder is MtgRef.Player) && valid(action.holder) &&
            Automatum.name(action.kind) && valid(action.before, action.kind) && valid(action.after, action.kind)
        is MtgAction.Attach -> valid(action.card) && (action.before?.let(::valid) ?: true) && (action.after?.let(::valid) ?: true)
        is MtgAction.Restamp -> DeckValidation.uuid(action.cardId) &&
            listOf(action.before, action.after).all { it.zoneChanges >= 0 && stamp(it.timestamp) }
        is MtgAction.SetLedger -> (action.holder is MtgRef.Card || action.holder is MtgRef.Player) && valid(action.holder) &&
            Automatum.data(action.before) && Automatum.data(action.after)
        is MtgAction.SetStackItem -> DeckValidation.uuid(action.entryId) && valid(action.before) && valid(action.after)
        is MtgAction.SetRecord -> Automatum.name(action.id) && valid(action.before, action.id) && valid(action.after, action.id)
    }

    internal fun change(state: PlayState, actor: String, command: GameCommand, action: MtgAction): GameplayChange {
        fun reject(reason: String) = GameplayChange(reason = reason)
        val unchanged = GameplayChange(state)
        val board = state.board
        fun card(ref: MtgRef.Card): PlayCard? = board.cards.singleOrNull { it.id == ref.cardId && it.zoneChanges == ref.zoneChanges }
        fun player(id: String): PlayPlayer? = board.players.singleOrNull { it.accountId == id }
        fun cards(id: String, change: (PlayCard) -> PlayCard) = board.copy(cards = board.cards.map { if (it.id == id) change(it) else it })
        fun players(id: String, change: (PlayPlayer) -> PlayPlayer) =
            board.copy(players = board.players.map { if (it.accountId == id) change(it) else it })
        val next: BoardState = when (action) {
            is MtgAction.SetAutomatum -> when {
                board.automatum == action.on && (action.on || !Automatum.flowOpen(board)) -> return unchanged
                action.on -> {
                    Automatum.turnOnRefusal(board)?.let { return reject(it) }
                    MtgFacts.stampUnstamped(board)?.copy(automatum = true) ?: return reject("invalidStructure")
                }
                // I-D24: switching off closes the open flow; every other record stays.
                else -> board.copy(automatum = false, engineRecords = board.engineRecords.filterNot { it.id == Automatum.CONTINUATION_ID })
            }
            MtgAction.ClearRecords -> if (!Automatum.recorded(board)) return unchanged
                else board.copy(engineRecords = emptyList(), priority = null,
                    cards = board.cards.map { if (it.ledger.isEmpty()) it else it.copy(ledger = "") },
                    players = board.players.map { if (it.ledger.isEmpty()) it else it.copy(ledger = "") })
            is MtgAction.SetSkipWhenNoChoice -> {
                val player = player(actor) ?: return reject("missingReference")
                if (player.skipWhenNoChoice == action.on) return unchanged
                players(actor) { it.copy(skipWhenNoChoice = action.on) }
            }
            is MtgAction.SetPriority -> {
                if (board.priority != action.before) return reject("inapplicable")
                if (action.after?.holder != null && player(action.after.holder) == null) return reject("missingReference")
                if (action.before == action.after) return unchanged
                board.copy(priority = action.after)
            }
            is MtgAction.SetManaPool -> {
                val player = player(action.accountId) ?: return reject("missingReference")
                if (player.manaPool != action.before) return reject("inapplicable")
                if (action.before == action.after) return unchanged
                players(player.accountId) { it.copy(manaPool = action.after) }
            }
            is MtgAction.SetCounter -> {
                fun set(counters: List<TypedCounter>): List<TypedCounter>? {
                    if (counters.singleOrNull { it.kind == action.kind } != action.before) return null
                    return (counters.filterNot { it.kind == action.kind } + listOfNotNull(action.after)).sortedBy { it.kind }
                }
                if (action.before == action.after) {
                    // Still checks that the holder exists.
                    if (when (val holder = action.holder) { is MtgRef.Card -> card(holder); is MtgRef.Player -> player(holder.accountId); else -> null } == null)
                        return reject("missingReference")
                    return unchanged
                }
                when (val holder = action.holder) {
                    is MtgRef.Card -> {
                        val card = card(holder) ?: return reject("missingReference")
                        val counters = set(card.typedCounters) ?: return reject("inapplicable")
                        cards(card.id) { it.copy(typedCounters = counters) }
                    }
                    is MtgRef.Player -> {
                        val player = player(holder.accountId) ?: return reject("missingReference")
                        val counters = set(player.typedCounters) ?: return reject("inapplicable")
                        players(player.accountId) { it.copy(typedCounters = counters) }
                    }
                    is MtgRef.StackItem -> return reject("invalidInput")
                }
            }
            is MtgAction.Attach -> {
                val card = card(action.card) ?: return reject("missingReference")
                if (card.attachedTo != action.before) return reject("inapplicable")
                if (action.before == action.after) return unchanged
                cards(card.id) { it.copy(attachedTo = action.after) }
            }
            is MtgAction.Restamp -> {
                val card = board.cards.singleOrNull { it.id == action.cardId } ?: return reject("missingReference")
                if (MtgStamp(card.zoneChanges, card.timestamp) != action.before) return reject("inapplicable")
                if (action.before == action.after) return unchanged
                cards(card.id) { it.copy(zoneChanges = action.after.zoneChanges, timestamp = action.after.timestamp) }
            }
            is MtgAction.SetLedger -> when (val holder = action.holder) {
                is MtgRef.Card -> {
                    val card = card(holder) ?: return reject("missingReference")
                    if (card.ledger != action.before) return reject("inapplicable")
                    if (action.before == action.after) return unchanged
                    cards(card.id) { it.copy(ledger = action.after) }
                }
                is MtgRef.Player -> {
                    val player = player(holder.accountId) ?: return reject("missingReference")
                    if (player.ledger != action.before) return reject("inapplicable")
                    if (action.before == action.after) return unchanged
                    players(player.accountId) { it.copy(ledger = action.after) }
                }
                is MtgRef.StackItem -> return reject("invalidInput")
            }
            is MtgAction.SetStackItem -> {
                val entry = board.stackEffects.singleOrNull { it.id == action.entryId } ?: return reject("missingReference")
                if (board.containers.none { it.zone == PlayZone.STACK && entry.id in it.cards }) return reject("missingReference")
                if (entry.item != action.before) return reject("inapplicable")
                if (action.before == action.after) return unchanged
                board.copy(stackEffects = board.stackEffects.map { if (it.id == entry.id) it.copy(item = action.after) else it })
            }
            is MtgAction.SetRecord -> {
                val current = board.engineRecords.singleOrNull { it.id == action.id }
                if (current != action.before) return reject("inapplicable")
                if (action.before == action.after) return unchanged
                board.copy(engineRecords = (board.engineRecords.filterNot { it.id == action.id } + listOfNotNull(action.after))
                    .sortedBy { it.id })
            }
        }
        return GameplayRules.record(state, actor, command, next, GameplayTransition())
    }
}

/**
 * The MTG facts ATW keeps up itself in every rules v36 event, with or without Automatum (§4.1).
 *
 * **Timestamps (D34)** come from a clock that is the highest timestamp on the board ([clock]): a new stamp is one more
 * than every stamp present before and after the event, given in card order within one event (CR 613.7m, simplified as
 * D44 allows). The clock is read, not stored, so it is never a value any event writes: undoing an earlier event never
 * clashes with a later event's stamps (ATW's undo rule compares only written and read tuples, and the clock is
 * deliberately not one), and replaying an event's sealed delta keeps the stamps it recorded. After an undo the clock
 * may be lower than a stamp once given, but only stamps of objects still on the board are ever compared (CR 613.7),
 * and every later stamp is higher than all of those; so the order of stamps is always the order of events.
 */
object MtgFacts {
    /** The highest timestamp on [board]: cards and their counters, players' counters, Stack items and records. */
    fun clock(board: BoardState): Long = maxOf(
        board.cards.maxOfOrNull { card -> maxOf(card.timestamp, card.typedCounters.maxOfOrNull { it.timestamp } ?: 0) } ?: 0,
        board.players.maxOfOrNull { player -> player.typedCounters.maxOfOrNull { it.timestamp } ?: 0 } ?: 0,
        board.stackEffects.maxOfOrNull { it.item?.timestamp ?: 0 } ?: 0,
        board.engineRecords.maxOfOrNull { it.timestamp ?: 0 } ?: 0)

    /** A zone change (CR 400.7): between containers, except from one Battlefield to another (a control change). */
    fun zoneChange(from: PlayZone, fromId: String, to: PlayZone, toId: String): Boolean =
        fromId != toId && !(from == PlayZone.BATTLEFIELD && to == PlayZone.BATTLEFIELD)

    /** Libraries an action shuffles: their cards become new objects (CR 701.20d, D38). */
    private fun shuffled(from: BoardState, action: PlayAction): Set<String> = when (action) {
        is PlayAction.ShuffleLibrary -> setOf(action.containerId)
        is PlayAction.DismissContents -> from.contentsDisplays.singleOrNull { it.id == action.displayId }?.let { display ->
            val source = from.containers.singleOrNull { it.id == display.containerId } ?: return@let null
            val shuffles = if (display.search) !TempPiles.searchKeepsOrder(source, display, CURRENT_GAMEPLAY_RULES_VERSION)
                else action.rest == ContentsRest.SHUFFLE
            source.id.takeIf { shuffles && source.zone == PlayZone.LIBRARY }
        }.let(::setOfNotNull)
        else -> emptySet()
    }

    /** #11: [board] with a timestamp for every Battlefield and Exile card that has none (0: games begun before rules
     * v36, cards that never moved since), above the clock, in this order: the turn it entered the Battlefield
     * ([PlayCard.enteredTurn], unknown first), then its order in its container, Battlefield before Exile, players in
     * account order. Null when the stamps would pass [Automatum.MAX_TIMESTAMP]. */
    internal fun stampUnstamped(board: BoardState): BoardState? {
        var clock = clock(board)
        val byId = board.cards.associateBy { it.id }
        val order = listOf(PlayZone.BATTLEFIELD, PlayZone.EXILE).flatMap { zone ->
            board.containers.filter { it.zone == zone }.sortedBy { it.accountId }.flatMap { container ->
                container.cards.mapNotNull(byId::get).filter { it.timestamp == 0L }
            }.withIndex().sortedWith(compareBy({ if (zone == PlayZone.BATTLEFIELD) it.value.enteredTurn.toLongOrNull() ?: 0 else 0 },
                { it.index })).map { it.value }
        }
        if (order.isEmpty()) return board
        if (clock > Automatum.MAX_TIMESTAMP - order.size) return null
        val stamps = order.associate { it.id to ++clock }
        return board.copy(cards = board.cards.map { card -> stamps[card.id]?.let { card.copy(timestamp = it) } ?: card })
    }

    /** #4: what an automated batch was planned on ([MtgTag.board]): the board without what is only shown (piles
     * and positions, notes, the six colour counters, marks, statuses, panels, links, the move holder, the result
     * suggestion, library layers). */
    fun digest(board: BoardState): String {
        val json = GameWire.json
        fun without(element: JsonElement, vararg keys: String) = JsonObject((element as JsonObject).filterKeys { it !in keys })
        val shown = board.copy(contentsDisplays = emptyList(), links = emptyList(), projectionLinks = emptyList(),
            resultSuggestion = null, stackTextMarks = emptyMap(), attentionHolder = null)
        val element = json.encodeToJsonElement(BoardState.serializer(), shown) as JsonObject
        val reduced = JsonObject(element + mapOf(
            "players" to JsonArray(element.getValue("players").jsonArray.map { without(it, "status", "counters") }),
            "cards" to JsonArray(element.getValue("cards").jsonArray.map { without(it, "placement", "note", "eotNote", "counters", "knownToAll") }),
            "containers" to JsonArray(element.getValue("containers").jsonArray.map { without(it, "layers", "keepTopRevealed") }),
            "stackEffects" to JsonArray(element.getValue("stackEffects").jsonArray.map { without(it, "note") })))
        return "sha256:" + DeckJson.intent("mtg-board", DeckJson.canonicalElement(reduced))
    }

    /**
     * I-D26 (#7): [mutation] (a recorded rules v36 event being redone) with fresh timestamps: every stamp the event gave
     * (a timestamp its delta sets that its delta didn't hold before: moved and stamped cards, new tokens, counters,
     * Stack items, records) moves above [above] (when it isn't already), in the order the event gave them, so stamp order follows the
     * real order of events although a redo puts the event back at its old place in history. The event's action and
     * opaque engine data keep the values they were recorded with; only its recorded transition (what replay applies
     * and checks) changes, and it is sealed again.
     */
    internal fun freshStamps(mutation: GameplayMutation, above: Long): GameplayMutation {
        val delta = mutation.transition.delta ?: return mutation
        if (mutation.transition.rulesVersion < Automatum.RULES_VERSION) return mutation
        val kinds = setOf("card", "player", "effect", "record")
        fun stamps(element: JsonElement?, out: MutableSet<Long>, key: String? = null) {
            when (element) {
                is JsonObject -> element.forEach { (k, v) -> stamps(v, out, k) }
                is JsonArray -> element.forEach { stamps(it, out, key) }
                is JsonPrimitive -> if (key == "timestamp") element.longOrNull?.let(out::add)
                else -> Unit
            }
        }
        val given = sortedSetOf<Long>()
        for (entity in delta.entities) if (entity.kind in kinds) for (value in entity.values) {
            val leaf = value.path.lastOrNull()
            val old = mutableSetOf<Long>(); val new = mutableSetOf<Long>()
            if (leaf == "timestamp") { (value.from as? JsonPrimitive)?.longOrNull?.let(old::add); (value.to as? JsonPrimitive)?.longOrNull?.let(new::add) }
            else { stamps(value.from, old); stamps(value.to, new) }
            given += new - old
        }
        if (given.isEmpty()) return mutation
        // Shifted as a block (the gaps between them kept), and only when some other stamp reached them meanwhile: a redo
        // right after its undo gives back exactly the recorded event.
        val shift = above + 1 - given.first()
        if (shift <= 0 || given.last() > Automatum.MAX_TIMESTAMP - shift) return mutation
        val fresh = given.associateWith { it + shift }
        fun rewrite(element: JsonElement?, key: String? = null): JsonElement? = when (element) {
            is JsonObject -> {
                // A delta edit of one timestamp field: {"path": [..., "timestamp"], "from": …, "to": …}
                val leaf = ((element["path"] as? JsonArray)?.lastOrNull() as? JsonPrimitive)?.contentOrNull == "timestamp"
                JsonObject(element.mapValues { (k, v) -> rewrite(v, if (leaf && k == "to") "timestamp" else k)!! })
            }
            is JsonArray -> JsonArray(element.map { rewrite(it, key)!! })
            is JsonPrimitive -> if (key == "timestamp") element.longOrNull?.let { fresh[it] }?.let(::JsonPrimitive) ?: element else element
            else -> element
        }
        // A given stamp is above every stamp the board held before the event, so it appears only where the event's
        // results are recorded: the delta's new values, the concerns' after values, the created tokens.
        val json = GameWire.json
        val rewritten = json.decodeFromJsonElement(GameplayTransition.serializer(),
            rewrite(json.encodeToJsonElement(GameplayTransition.serializer(), mutation.transition))!!)
        // A concern on a written timestamp field witnesses the new value itself.
        val entities = rewritten.delta?.entities.orEmpty()
        val transition = rewritten.copy(concerns = rewritten.concerns?.let { set -> set.copy(fields = set.fields.map { tuple ->
            val field = tuple.field as? ConcernField.Written ?: return@map tuple
            if (entities.getOrNull(field.entity)?.values?.getOrNull(field.value)?.path?.lastOrNull() != "timestamp") tuple
            else tuple.copy(after = rewrite(tuple.after, "timestamp")!!)
        }) })
        val restamped = mutation.copy(transition = transition)
        return restamped.copy(transition = restamped.transition.copy(delta = restamped.transition.delta!!.copy(
            seal = GameplayDeltas.seal(restamped))))
    }

    /** [to] (the board after [action]) with zone-change counts, timestamps, the fresh facts of new objects and
     * emptied mana pools; null when a stamp would pass [Automatum.MAX_TIMESTAMP] (#8). */
    internal fun after(from: BoardState, to: BoardState, action: PlayAction): BoardState? {
        val old = from.cards.associateBy { it.id }
        val zones = (from.containers + to.containers).associate { it.id to it.zone }
        val shuffled = shuffled(from, action)
        var clock = maxOf(clock(from), clock(to))
        // A mulligan returns the shown hand, shuffles the library and draws (CR 103.5): each step counts as the same
        // moves made one by one would (returns in their move order, then draws from the top), so one Mulligan event
        // ends exactly where its single moves and shuffle end.
        val mulligan = action as? PlayAction.Mulligan
        val mulliganStamps = mutableMapOf<String, Long>()
        val mulliganed = mulligan?.let {
            val library = from.containers.singleOrNull { it.id == mulligan.libraryId }?.cards.orEmpty()
            val returns = runCatching { LibraryActions.moveCaptured(from, mulligan.displayedHand,
                MoveDestination(mulligan.libraryId, MoveInsertion.TOP)).map { it.cardId } }.getOrElse { mulligan.displayedHand }
            val draws = RandomV1.shuffle(mulligan.displayedHand + library, mulligan.seed).take(mulligan.drawCount)
            for (id in returns + draws) mulliganStamps[id] = ++clock
            (library + mulligan.displayedHand).toSet()
        }.orEmpty()
        val cards = to.cards.map { card ->
            val before = old[card.id]
            when {
                before != null && card.id in mulliganed -> {
                    val drawn = card.containerId == mulligan!!.handId
                    val steps = (if (card.id in mulligan.displayedHand) 1 else 0) + 1 + (if (drawn) 1 else 0)
                    if (steps == 1) card.copy(zoneChanges = before.zoneChanges + 1)
                    else card.copy(zoneChanges = before.zoneChanges + steps, timestamp = mulliganStamps[card.id] ?: ++clock,
                        typedCounters = emptyList(), attachedTo = null, ledger = "")
                }
                // A new object: a token created or cloned.
                before == null -> card.copy(zoneChanges = 0, timestamp = ++clock, typedCounters = emptyList(),
                    attachedTo = null, ledger = "")
                zoneChange(zones.getValue(before.containerId), before.containerId, zones.getValue(card.containerId), card.containerId) ->
                    card.copy(zoneChanges = before.zoneChanges + 1, timestamp = ++clock, typedCounters = emptyList(),
                        attachedTo = null, ledger = "")
                card.containerId in shuffled -> card.copy(zoneChanges = before.zoneChanges + 1)
                // CR 613.7: attached, turned face up or down, transformed (another face shown on the Battlefield).
                card.attachedTo != null && card.attachedTo != before.attachedTo ||
                    card.isFaceDown != before.isFaceDown ||
                    card.faceId != before.faceId && zones.getValue(card.containerId) == PlayZone.BATTLEFIELD ->
                    card.copy(timestamp = ++clock)
                else -> card
            }
        }
        val turnChanged = from.turn != to.turn
        val players = if (!turnChanged) to.players else to.players.map { if (it.manaPool.isEmpty()) it else it.copy(manaPool = emptyList()) }
        // #8: the clock can't run past its end (a stamp above it would never be valid again).
        if (clock > Automatum.MAX_TIMESTAMP) return null
        return to.copy(cards = cards, players = players)
    }

    /**
     * Whether [command]'s event is folded into its batch's visible event (I-D22: history events are natural branching
     * points, one per possible player action): engine bookkeeping (zone-change counts and timestamps, ledgers, Stack
     * item structure, engine records other than emblems) and priority passes the engine or the host made, tagged
     * [MtgTag.folded] by the client that planned the batch. Never the batch's first member, so every automated batch
     * keeps a visible event, and never anything else: manual history is unchanged.
     */
    fun folds(command: GameCommand, action: PlayAction): Boolean {
        if ((command.action as? GameAction.Play)?.tag?.folded != true) return false
        val invocation = command.invocationId ?: return false
        if (command.requestId == CommandIds.uuidV5(invocation, "member/0")) return false
        return when (val mtg = (action as? PlayAction.Mtg)?.action) {
            is MtgAction.SetPriority, is MtgAction.Restamp, is MtgAction.SetLedger, is MtgAction.SetStackItem -> true
            is MtgAction.SetRecord -> (mtg.before ?: mtg.after)?.kind != EngineRecordKind.Emblem
            else -> false
        }
    }

    /** What a single-card move means by itself (D33, D42; the engine's `Meanings.implied`), from zone [from] to [to]. */
    fun implied(from: PlayZone, to: PlayZone): List<String> = buildList {
        if (to == PlayZone.BATTLEFIELD && from != PlayZone.BATTLEFIELD) add("entered")
        if (from == PlayZone.BATTLEFIELD && to != PlayZone.BATTLEFIELD) add("left-battlefield")
        // CR 700.4: "dies" means put into a graveyard from the battlefield
        if (from == PlayZone.BATTLEFIELD && to == PlayZone.GRAVEYARD) add("died")
        if (to == PlayZone.GRAVEYARD && from != PlayZone.GRAVEYARD) add("put-into-graveyard")
        if (to == PlayZone.EXILE && from != PlayZone.EXILE) add("exiled")
    }

    /** The tag of a rules v36 event: [given] by the client, else for a manual single-card zone change its implied
     * meaning; null when there is none. */
    internal fun tag(from: BoardState, to: BoardState, action: PlayAction, given: MtgTag?): MtgTag? {
        if (given != null) return given
        val move = action as? PlayAction.MoveCard ?: return null
        val before = from.cards.singleOrNull { it.id == move.cardId } ?: return null
        val after = to.cards.singleOrNull { it.id == move.cardId } ?: return null
        val zones = (from.containers + to.containers).associate { it.id to it.zone }
        val source = zones[before.containerId] ?: return null
        val target = zones[after.containerId] ?: return null
        if (!zoneChange(source, before.containerId, target, after.containerId)) return null
        return implied(source, target).takeIf { it.isNotEmpty() }?.let { MtgTag(it) }
    }
}

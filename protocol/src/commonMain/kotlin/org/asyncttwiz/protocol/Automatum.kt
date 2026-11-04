@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)

package org.asyncttwiz.protocol

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*

/**
 * The engine's values on the board the table draws of a game on engine state (Arcanum integration spec §4,
 * [org.asyncttwiz.protocol.game]): per card its zone-change count, timestamp, typed counters and attachment; per player
 * their mana pool and typed counters; the priority holder; structured Stack items on Stack entries; and engine records
 * (the combat, emblems, the attacking player's choices and early answers).
 */
object Automatum {
    const val RULES_VERSION = 36
    /** Opaque engine JSON per field, at most this many UTF-8 bytes. */
    const val MAX_DATA_BYTES = 256 * 1024
    const val MAX_NAME_LENGTH = 256
    /** An emblem's rules text ([EngineRecord.text]), at most this many UTF-8 bytes. */
    const val MAX_TEXT_BYTES = 8 * 1024
    /** The id of the one open continuation of a shared flow (D58, I-D11); private flows stay on the device. */
    const val CONTINUATION_ID = "continuation"
    /** The highest timestamp a record may name (2^53 − 1, exact in JSON numbers everywhere). */
    const val MAX_TIMESTAMP = 9_007_199_254_740_991L

    /** Whether a shared flow is open: the board holds the continuation (I-D8). */
    fun flowOpen(board: BoardState): Boolean = board.engineRecords.any { it.id == CONTINUATION_ID }

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
    /** A pre-event (I-D33): an early answer to a decision that isn't open yet, by or for [EngineRecord.player], whose
     * decision it is; Automatum applies it when that decision opens, and the next automated batch removes it either
     * way. */
    @Serializable @SerialName("pre-answer") data object PreAnswer : EngineRecordKind
}

/** One engine record ATW shows and doesn't interpret (P2, §4.3); [timestamp] for effect objects, delayed triggers
 * and emblems (CR 613.7b). An [EngineRecordKind.Emblem]
 * also says what ATW shows of it: whose it is ([player]) and its rules [text] (I-D25); other records have neither. */
@Serializable data class EngineRecord(val id: String, val kind: EngineRecordKind,
    @EncodeDefault(EncodeDefault.Mode.NEVER) val timestamp: Long? = null, val data: String,
    @EncodeDefault(EncodeDefault.Mode.NEVER) val player: String? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER) val text: String = "")

/** A change the table's own controls make to the records beside the board (an early answer, I-D33). */
@Serializable sealed interface MtgAction {
    /** Adds (null [before]), changes or removes (null [after]) the engine record [id]. */
    @Serializable @SerialName("setRecord") data class SetRecord(val id: String, val before: EngineRecord?,
        val after: EngineRecord?) : MtgAction
}

/** Validation of the table's [MtgAction]s and the values they name. */
object MtgRules {
    fun valid(ref: MtgRef): Boolean = when (ref) {
        is MtgRef.Card -> DeckValidation.uuid(ref.cardId) && ref.zoneChanges >= 0
        is MtgRef.Player -> DeckValidation.uuid(ref.accountId)
        is MtgRef.StackItem -> Automatum.name(ref.id)
    }

    /** #8 (P8): a stamp lies within 0..[Automatum.MAX_TIMESTAMP]. */
    fun stamp(value: Long): Boolean = value in 0..Automatum.MAX_TIMESTAMP

    private fun valid(record: EngineRecord?, id: String) = record == null || record.id == id && Automatum.data(record.data) &&
        stamp(record.timestamp ?: 0) && (record.kind == EngineRecordKind.Continuation) == (id == Automatum.CONTINUATION_ID) &&
        if (record.kind == EngineRecordKind.Emblem) record.player?.let(DeckValidation::uuid) == true &&
            DeckValidation.text(record.text, Automatum.MAX_TEXT_BYTES)
        else if (record.kind == EngineRecordKind.PreAnswer) record.player?.let(DeckValidation::uuid) == true && record.text.isEmpty()
        else record.player == null && record.text.isEmpty()

    fun valid(action: MtgAction): Boolean = when (action) {
        is MtgAction.SetRecord -> Automatum.name(action.id) && valid(action.before, action.id) && valid(action.after, action.id)
    }
}

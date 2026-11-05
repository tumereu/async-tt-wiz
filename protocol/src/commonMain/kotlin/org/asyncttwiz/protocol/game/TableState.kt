package org.asyncttwiz.protocol.game

import arcanum.engine.decision.Answer
import arcanum.engine.input.Attack
import arcanum.engine.input.Block
import arcanum.engine.setup.GameResult
import arcanum.engine.state.ObjectId
import arcanum.engine.state.PlayerId
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * What only ATW cares about in a game (I-D54 §2), beside the engine's [arcanum.engine.state.GameState]: where cards
 * lie on the battlefield, early answers (I-D33), the shared attack staging and block draft before they are declared,
 * result suggestions, and the game's randomness (I-D56 §9: the game's [secret] and where the current [line] is in its
 * stream). Immutable; only [TableChange]s change it. Each player's play settings (response mode, Autotap) are private
 * and kept on the server, not here ([PlaySettings], I-D56 §13).
 *
 * Players are the engine's [PlayerId]s; ATW names each player by their account id.
 */
@Serializable
data class TableState(
    /** Battlefield objects' places, by engine object id: a card that changes zones is a new object (D21) and needs a
     * new place, so a stale entry is simply never shown. */
    val placements: Map<ObjectId, Placement> = emptyMap(),
    /** Early answers by [EarlyAnswer.key] (I-D33). */
    val earlyAnswers: Map<String, EarlyAnswer> = emptyMap(),
    /** The attack being staged before it is declared, shared by both players (owner, 2026-11-03). */
    val attackDraft: List<Attack>? = null,
    /** The blocks being drafted before they are declared, shared by both players. */
    val blockDraft: List<Block>? = null,
    /** A suggested result (draw, or one player's win) waiting for the other player's answer. */
    val suggestion: ResultSuggestion? = null,
    /** Who confirmed the result the engine reports (I-D53); once set, the result is final. */
    val resultConfirmedBy: PlayerId? = null,
    /** The game's random secret, made by the server when the play starts and held by both devices (I-D27: data a device
     * holds isn't shown): every random answer is worked out from it ([LineRandom]). */
    val secret: String = "",
    /** The line of play the state is on and how many random answers it has used (I-D56 §9). */
    val line: Line = Line(),
)

/**
 * A line of play (I-D56 §9): it starts at the game's start (line 0) and at every branch and rewind, each taking the
 * play's next [number] from one counter (never the parent's line + 1, so no stream is used twice); [drawn] random
 * answers have been made on it so far. The k-th random answer of line n comes from H(secret, n, k) ([LineRandom]).
 */
@Serializable
data class Line(val number: Int = 0, val drawn: Int = 0)

/**
 * A player's own play settings for one game (I-D56 §2, §13): private, kept on the server for that player and game,
 * never an event or part of the table or engine state, never sent to the other player. [mode] says where the player's
 * own device stops for them in live play; [alwaysAssumeResolves] assumes the other player's passes at their pauses at
 * once instead of waiting with Assume (HIS-096); [autotap] pays mana costs with the engine's suggestion (I-D47, private:
 * I-D56 choice 12); [playbackFullControl] makes catch-up playback stop wherever the player gets priority (HIS-095).
 */
@Serializable
data class PlaySettings(val mode: ResponseMode = ResponseMode.AutoSkip, val alwaysAssumeResolves: Boolean = false,
    val autotap: Boolean = true, val playbackFullControl: Boolean = false) {
    /** Where catch-up playback stops for the player (HIS-095): Auto-skip whatever their live [mode], or Full control
     * with [playbackFullControl] on. */
    val playbackMode: ResponseMode get() = if (playbackFullControl) ResponseMode.FullControl else ResponseMode.AutoSkip
}

/** Where a player's own device (and their playback) stops for them (I-D56 §2). */
@Serializable
sealed interface ResponseMode {
    /** At every point where they get priority; after casting a spell or activating an ability they keep priority. */
    @Serializable @SerialName("full") data object FullControl : ResponseMode
    /** At their stops (their own main phases with an empty Stack) and pauses (STK-036). */
    @Serializable @SerialName("normal") data object Normal : ResponseMode
    /** Normal, but where they can't respond their device passes, and it answers questions with one answer. The default
     * for new games. */
    @Serializable @SerialName("auto-skip") data object AutoSkip : ResponseMode
}

/** A battlefield object's cell: column [x] in row [y], in [pile] when it is grouped with others. */
@Serializable
data class Placement(val x: Int, val y: Int, val pile: String? = null)

/**
 * An answer [player] gives early to a decision of theirs that isn't open yet (I-D33), named by [key] (the decision's
 * kind and what it is about, [EarlyAnswer.keyOf]), valid in turn [turn] (null: any). Applied by the device running the
 * step when its decision opens, as an engine-automatic answer (I-D56 §6 c).
 */
@Serializable
data class EarlyAnswer(val player: PlayerId, val key: String, val answer: Answer, val turn: Int? = null) {
    companion object {
        /** The key of an early answer to [decision]: the decision's kind ([arcanum.engine.decision.Decision.kind], written
         * out: a shrunk Android build may name a nested class "Decision$Divide" where the JVM says "Divide", and the device
         * and the server must make the same key), its player and its source. */
        fun keyOf(decision: arcanum.engine.decision.Decision): String =
            "${decision.kind}:${decision.player.value}:${decision.source ?: "-"}"
    }
}

/** [by] suggests ending the game with [result] (a draw, or a win of either player), with their optional reason [note]
 * (TRN-046, [org.asyncttwiz.protocol.ResultNotes]); the other player answers. */
@OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
@Serializable
data class ResultSuggestion(val by: PlayerId, val result: GameResult,
    @kotlinx.serialization.EncodeDefault(kotlinx.serialization.EncodeDefault.Mode.NEVER) val note: String = "") {
    init { require(org.asyncttwiz.protocol.ResultNotes.valid(note)) { "Invalid result reason" } }
}

/** One reversible change of the [TableState]: it holds the values before and after, and applies only on a table that
 * holds its before-values (as the engine's changes, D59). */
@Serializable
sealed interface TableChange {
    fun apply(table: TableState): TableState
    fun inverse(): TableChange
    fun revert(table: TableState): TableState = inverse().apply(table)

    /** [obj]'s place on the battlefield ([before] null: it had none; [after] null: it has none). */
    @Serializable @SerialName("placed")
    data class Placed(val obj: ObjectId, val before: Placement?, val after: Placement?) : TableChange {
        override fun apply(table: TableState): TableState {
            check(table.placements[obj] == before) { "Placement of $obj isn't $before" }
            return table.copy(placements = if (after == null) table.placements - obj else table.placements + (obj to after))
        }
        override fun inverse() = copy(before = after, after = before)
    }

    @Serializable @SerialName("early-answer")
    data class EarlyAnswered(val key: String, val before: EarlyAnswer?, val after: EarlyAnswer?) : TableChange {
        init { require(after == null || after.key == key) }
        override fun apply(table: TableState): TableState {
            check(table.earlyAnswers[key] == before) { "Early answer $key isn't $before" }
            return table.copy(earlyAnswers = if (after == null) table.earlyAnswers - key else table.earlyAnswers + (key to after))
        }
        override fun inverse() = copy(before = after, after = before)
    }

    @Serializable @SerialName("attack-draft")
    data class AttackDraft(val before: List<Attack>?, val after: List<Attack>?) : TableChange {
        override fun apply(table: TableState): TableState {
            check(table.attackDraft == before) { "The attack draft isn't $before" }
            return table.copy(attackDraft = after)
        }
        override fun inverse() = copy(before = after, after = before)
    }

    @Serializable @SerialName("block-draft")
    data class BlockDraft(val before: List<Block>?, val after: List<Block>?) : TableChange {
        override fun apply(table: TableState): TableState {
            check(table.blockDraft == before) { "The block draft isn't $before" }
            return table.copy(blockDraft = after)
        }
        override fun inverse() = copy(before = after, after = before)
    }

    @Serializable @SerialName("suggestion")
    data class Suggestion(val before: ResultSuggestion?, val after: ResultSuggestion?) : TableChange {
        override fun apply(table: TableState): TableState {
            check(table.suggestion == before) { "The result suggestion isn't $before" }
            return table.copy(suggestion = after)
        }
        override fun inverse() = copy(before = after, after = before)
    }

    @Serializable @SerialName("result-confirmed")
    data class ResultConfirmed(val before: PlayerId?, val after: PlayerId?) : TableChange {
        override fun apply(table: TableState): TableState {
            check(table.resultConfirmedBy == before) { "The result's confirmation isn't $before" }
            return table.copy(resultConfirmedBy = after)
        }
        override fun inverse() = copy(before = after, after = before)
    }

    /** The [TableState.line] moves (I-D56 §9): random answers were made on it, or a new line started. Made by an
     * [EventBuilder] itself, never sent by a device. */
    @Serializable @SerialName("line")
    data class LineMoved(val before: Line, val after: Line) : TableChange {
        override fun apply(table: TableState): TableState {
            check(table.line == before) { "The line isn't $before" }
            return table.copy(line = after)
        }
        override fun inverse() = copy(before = after, after = before)
    }
}

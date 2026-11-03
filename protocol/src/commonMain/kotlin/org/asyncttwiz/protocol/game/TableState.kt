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
 * lie on the battlefield, each player's play settings, early answers (I-D33), the shared attack staging and block draft
 * before they are declared, and result suggestions. Immutable; only [TableChange]s change it.
 *
 * Players are the engine's [PlayerId]s; ATW names each player by their account id.
 */
@Serializable
data class TableState(
    /** Battlefield objects' places, by engine object id: a card that changes zones is a new object (D21) and needs a
     * new place, so a stale entry is simply never shown. */
    val placements: Map<ObjectId, Placement> = emptyMap(),
    /** Per player; a player without an entry has [PlaySettings]' defaults. */
    val settings: Map<PlayerId, PlaySettings> = emptyMap(),
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
) {
    fun settingsOf(player: PlayerId): PlaySettings = settings[player] ?: PlaySettings()
}

/** A battlefield object's cell: column [x] in row [y], in [pile] when it is grouped with others. */
@Serializable
data class Placement(val x: Int, val y: Int, val pile: String? = null)

/** A player's own play settings (I-D41 Full control, I-D47 Autotap). "Skip when I have no choice" is engine state
 * ([arcanum.engine.state.PlayerState.skipWhenNoChoice]), changed by a host step of an [EnginePart]. */
@Serializable
data class PlaySettings(val fullControl: Boolean = false, val autotap: Boolean = true)

/**
 * An answer [player] gives early to a decision of theirs that isn't open yet (I-D33), named by [key] (the decision's
 * kind and what it is about), valid in turn [turn] (null: any).
 */
@Serializable
data class EarlyAnswer(val player: PlayerId, val key: String, val answer: Answer, val turn: Int? = null)

/** [by] suggests ending the game with [result] (a draw, or a win of either player); the other player answers. */
@Serializable
data class ResultSuggestion(val by: PlayerId, val result: GameResult)

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

    @Serializable @SerialName("settings")
    data class Settings(val player: PlayerId, val before: PlaySettings, val after: PlaySettings) : TableChange {
        override fun apply(table: TableState): TableState {
            check(table.settingsOf(player) == before) { "Settings of $player aren't $before" }
            return table.copy(settings = if (after == PlaySettings()) table.settings - player else table.settings + (player to after))
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
}

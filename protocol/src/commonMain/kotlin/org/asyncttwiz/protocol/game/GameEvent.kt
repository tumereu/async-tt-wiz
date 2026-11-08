package org.asyncttwiz.protocol.game

import arcanum.engine.change.Change
import arcanum.engine.input.Input
import arcanum.engine.state.PlayerId
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * One node of a game's history tree (I-D54 §2): its [engine] part and its [table] part, applied and reverted together.
 * Either may be absent: a pass is engine only, moving a card on the battlefield is table only. [author] is the account
 * whose action it was; [parentId] the event it was made on (the play's root id for the first). [mode]: the response mode it
 * was made under (owner 2026-11-06), for an assumed input the mode of the player it is about; the other player's device
 * reads it to know that player's mode (null: made before modes were recorded, or by the server). [press]: the main button
 * press whose first pass this event is (HIS-101; null on every other event, an End turn's later passes included, on a pass
 * the device made by itself or the other player's device assumed, and on events made before presses were recorded).
 */
@Serializable
data class GameEvent(
    val id: String,
    val parentId: String,
    val author: String,
    val engine: EnginePart? = null,
    val table: List<TableChange> = emptyList(),
    val mode: ResponseMode? = null,
    val press: MainPress? = null,
) {
    /** The first step when it is an assumed input (I-D56 §13: an assumed input always begins its own event). */
    val assumed: EngineStep? get() = engine?.steps?.firstOrNull { it.input != null }?.takeIf { it.assumedBy != null }

    /** The line this event starts (I-D56 §9: its first step reshuffles the libraries' unknown parts), or null. */
    val startsLine: Int? get() = table.firstNotNullOfOrNull { (it as? TableChange.LineMoved)?.takeIf { m -> m.before.number != m.after.number }?.after?.number }
}

/** HIS-101 (owner 2026-11-08): what a press of the main button was: what the player pressed, not what it resulted in (that
 * is in the event's changes). */
@Serializable
sealed interface MainPress {
    /** "To combat", from their first main phase. */
    @Serializable @SerialName("combat") data object ToCombat : MainPress
    /** "To end step", from their second main phase in Control. */
    @Serializable @SerialName("end-step") data object ToEndStep : MainPress
    /** "End turn". */
    @Serializable @SerialName("end-turn") data object EndTurn : MainPress
}

/**
 * The engine's part of an event: each [steps]' input in order with the changes the engine made for it (D59), as one
 * acting point (I-D35): the player's input, the random answers that followed it (I-D56 §9, from the line's stream) and
 * engine-automatic answers (I-D56 §6). The inputs are kept for the server's check and for reproduction (I-D43);
 * applying and reverting use only the changes.
 *
 * [timestampBefore] is the engine's timestamp counter before the event ([arcanum.engine.state.GameState.timestampCounter],
 * which the engine never moves back): reverting the event puts it back, so the state at an event is the same however it
 * was reached (new objects' timestamps and ids read the counter).
 */
@Serializable
data class EnginePart(val timestampBefore: Long, val steps: List<EngineStep>) {
    val changes: List<Change> get() = steps.flatMap { it.changes }
}

/** [input] and the [changes] [arcanum.engine.Engine.perform] made for it; [input] null for a host's own changes made
 * without the engine (a new line's reshuffle of the libraries' unknown parts, I-D56 §9). [assumedBy]: the input is the
 * other player's, assumed by this player (I-D56 §3); confirmed when that player's local head moves past it. */
@Serializable
data class EngineStep(val input: Input? = null, val changes: List<Change>, val assumedBy: PlayerId? = null)

package org.asyncttwiz.protocol.game

import arcanum.engine.change.Change
import arcanum.engine.input.Input
import kotlinx.serialization.Serializable

/**
 * One node of a game's history tree (I-D54 §2): its [engine] part and its [table] part, applied and reverted together.
 * Either may be absent: a pass is engine only, moving a card on the battlefield is table only. [author] is the account
 * whose action it was; [parentId] the event it was made on (the play's root id for the first).
 */
@Serializable
data class GameEvent(
    val id: String,
    val parentId: String,
    val author: String,
    val engine: EnginePart? = null,
    val table: List<TableChange> = emptyList(),
)

/**
 * The engine's part of an event: each [steps]' input in order with the changes the engine made for it (D59), as one
 * acting point (I-D35): the player's input, the host's answers that followed it (random results made on the device,
 * I-D54 §3) and passes the host made for players ([arcanum.engine.Engine.shouldPassFor]). The inputs are kept for the
 * server's check and for reproduction (I-D43); applying and reverting use only the changes.
 *
 * [timestampBefore] is the engine's timestamp counter before the event ([arcanum.engine.state.GameState.timestampCounter],
 * which the engine never moves back): reverting the event puts it back, so the state at an event is the same however it
 * was reached (the engine's decision ids read the counter).
 */
@Serializable
data class EnginePart(val timestampBefore: Long, val steps: List<EngineStep>) {
    val changes: List<Change> get() = steps.flatMap { it.changes }
}

/** [input] and the [changes] [arcanum.engine.Engine.perform] made for it; [input] null for a host's own changes made
 * without the engine (a player's "Skip when I have no choice", [arcanum.engine.change.Changes]). */
@Serializable
data class EngineStep(val input: Input? = null, val changes: List<Change>)

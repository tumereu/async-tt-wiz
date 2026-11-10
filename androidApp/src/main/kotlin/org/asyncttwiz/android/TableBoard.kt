package org.asyncttwiz.android

import org.asyncttwiz.protocol.Automatum
import org.asyncttwiz.protocol.BoardState
import org.asyncttwiz.protocol.SubmittedDefinition
import org.asyncttwiz.protocol.game.EngineView

/**
 * What the table draws of a game (engine-responsiveness R4a): the render model the engine thread makes of a game on
 * engine state from its [org.asyncttwiz.protocol.game.GameModel] and table state ([EngineGame], with
 * [org.asyncttwiz.automatum.EngineProjection]), immutable plain data:
 *
 * - [board]: the cards, piles, players, turn, combat lanes, Stack entries and records as the viewer sees them, and
 *   [definitions], the card data of every card on it;
 * - [nodeId]: the event of the line the board is at (the shown point);
 * - [engine]: the engine state it shows (full information, never changed) and the table state, which the cast, choice
 *   and combat controllers read directly;
 * - [values]: what the engine thread worked out with it (R3: the computed card values, the viewer's plays, the shared
 *   flow and combat, [EngineBoardValues]).
 */
data class TableBoard(
    val gameId: String,
    val playId: String,
    val nodeId: String,
    val board: BoardState,
    val definitions: List<SubmittedDefinition>,
    val engine: EngineView? = null,
    val values: EngineBoardValues? = null,
    /** HIS-113: a step of a board's motion ([EngineGame.Transition.steps]) right after a combat damage step's damage was
     * dealt (damage marked, life changed), before what follows it (the creatures it destroys still on the battlefield). */
    val damageDealt: Boolean = false,
) {
    /** A choice of the game is open (a shared flow waits for an answer, D32): on engine state its pending decision. */
    val flowOpen: Boolean get() = engine?.let { it.state.pending != null } ?: Automatum.flowOpen(board)
}

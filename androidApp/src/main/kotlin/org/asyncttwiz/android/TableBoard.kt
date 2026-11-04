package org.asyncttwiz.android

import org.asyncttwiz.protocol.Automatum
import org.asyncttwiz.protocol.BoardState
import org.asyncttwiz.protocol.PlayState
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
 *
 * [legacy]: the old model's play a board was drawn from (today's table path, and the boards tests draw), null for a game
 * on engine state; R4b deletes it with that path.
 */
data class TableBoard(
    val gameId: String,
    val playId: String,
    val nodeId: String,
    val board: BoardState,
    val definitions: List<SubmittedDefinition>,
    val engine: EngineView? = null,
    val values: EngineBoardValues? = null,
    val legacy: PlayState? = null,
) {
    /** A choice of the game is open (a shared flow waits for an answer, D32): on engine state its pending decision. */
    val flowOpen: Boolean get() = engine?.let { it.state.pending != null } ?: Automatum.flowOpen(board)

    companion object {
        /** The board drawn from the old model's [play] (today's table path and tests); the same object for the same play,
         * so what is kept per board stays. */
        fun of(play: PlayState): TableBoard = synchronized(recent) {
            recent.firstOrNull { it.legacy === play }?.let { return it }
            TableBoard(play.gameId, play.id, play.currentNodeId, play.board, play.definitions, legacy = play).also {
                recent.addFirst(it)
                if (recent.size > RECENT) recent.removeAt(recent.lastIndex)
            }
        }

        /** The boards drawn from the latest plays, newest first (by the play's identity). */
        private val recent = ArrayDeque<TableBoard>()
        private const val RECENT = 16
    }
}

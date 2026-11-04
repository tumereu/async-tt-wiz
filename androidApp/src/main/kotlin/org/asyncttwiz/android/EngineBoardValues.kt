package org.asyncttwiz.android

import arcanum.engine.state.GameState
import arcanum.engine.state.ObjectId
import arcanum.engine.view.View
import org.asyncttwiz.automatum.CardPlays
import org.asyncttwiz.automatum.ComputedBoard
import org.asyncttwiz.automatum.HandPlays
import org.asyncttwiz.protocol.BoardState
import org.asyncttwiz.protocol.PlayState
import org.asyncttwiz.protocol.game.EngineView
import java.util.Collections
import java.util.WeakHashMap

/**
 * Engine-responsiveness R3: what the engine thread works out for a board of a game on engine state as it builds it
 * ([EngineGame]), from its one view of the state (R2), so the table's second thread ([AutomatumBoards]) no longer rebuilds
 * engine state from the board for it:
 * - [computed]: the computed card values ([ComputedBoard]: P/T, keywords, type and colour changes, until-end-of-turn
 *   marks), made with the board: the table never shows the previous board's with it;
 * - the player's hand plays ([HandPlays]): worked out on the engine thread with the board it publishes as the result of
 *   an action ([EngineGame.Shown]; an own action's early board gets them with the board published after it), or on the
 *   engine thread when asked first;
 * - the card menus' plays ([CardPlays]): lazy, on the engine thread when first asked, once per board (a board published
 *   again unchanged keeps them).
 *
 * Kept with the board's engine state ([BoardState.engine], [EngineView]: equal only to itself, shared by the copies of the
 * board the table makes) while the board is in use: [of] finds them for any copy. [state] is that state, never changed;
 * [play] is the board without it (only its cards' ids are read).
 */
internal class EngineBoardValues(
    val viewer: String,
    val computed: ComputedBoard,
    private val play: PlayState,
    private val state: GameState,
    private val view: View?,
    private val visible: Set<ObjectId>,
    private val engine: EngineWorker,
) {
    /** The hand plays once worked out (any thread reads; the engine thread writes). */
    @Volatile var handPlays: HandPlays? = null
        private set

    /** The card menus' plays once worked out. */
    @Volatile var cardPlays: CardPlays? = null
        private set

    /** The hand plays, worked out on the engine thread now when not yet (waits for it; inline on it). */
    fun handPlays(): HandPlays = handPlays ?: engine.call { workedHandPlays() }

    /** Engine thread: the hand plays, worked out now when not yet. Never throws (as the table's did: none on failure). */
    fun workedHandPlays(): HandPlays = handPlays ?: try {
        HandPlays.of(play, state, viewer, view)
    } catch (problem: Exception) {
        AppLog.warn("automatum", "Couldn't list plays: ${problem::class.java.simpleName}")
        HandPlays.NONE
    }.also { handPlays = it }

    /** The card menus' plays, worked out on the engine thread when not yet (waits for it; inline on it). */
    fun cardPlays(): CardPlays = cardPlays ?: engine.call {
        cardPlays ?: try {
            CardPlays.of(play, state, viewer, view ?: arcanum.engine.Engine.view(state), visible)
        } catch (problem: Exception) {
            AppLog.warn("automatum", "Couldn't list card plays: ${problem::class.java.simpleName}")
            CardPlays.NONE
        }.also { cardPlays = it }
    }

    companion object {
        /** Each board's values by its engine state (weakly: they go with the boards). */
        private val values: MutableMap<EngineView, EngineBoardValues> = Collections.synchronizedMap(WeakHashMap())

        /** Tests and measurements: how many computed boards the engine thread made with its boards. */
        @Volatile var made = 0
            private set

        /**
         * Engine thread: the values of [play]'s board, a board just projected from [model]'s state for [viewer] ([view] and
         * [visible]: the model's own, R2), kept with it. Null for a board without engine state.
         */
        fun make(play: PlayState, viewer: String, view: View?, visible: Set<ObjectId>, engine: EngineWorker): EngineBoardValues? {
            val engineView = play.board.engine ?: return null
            val state = engineView.state
            val shown = try { view ?: arcanum.engine.Engine.view(state) } catch (problem: Exception) { null }
            val computed = try {
                ComputedBoard.of(play, AutomatumBoards.definitions(play), viewer, state, shown ?: arcanum.engine.Engine.view(state))
            } catch (problem: Exception) {
                AppLog.warn("automatum", "Couldn't work the board out: ${problem::class.java.simpleName}")
                ComputedBoard.failed("Automatum couldn't work this board out (${problem.message?.take(120) ?: problem::class.java.simpleName})")
            }
            made++
            // Kept without the board's engine state (the key: a value holding it would keep it, and the board, for ever)
            val cards = play.copy(board = play.board.copy(engine = null))
            return EngineBoardValues(viewer, computed, cards, state, shown, visible, engine).also { values[engineView] = it }
        }

        /** The values the engine thread made for [board] as [viewer] sees it, if any. */
        fun of(board: BoardState, viewer: String): EngineBoardValues? =
            board.engine?.let { values[it] }?.takeIf { it.viewer == viewer }

        /** For tests: forget every board's values. */
        fun reset() { values.clear(); made = 0 }
    }
}

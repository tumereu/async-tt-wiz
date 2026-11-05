package org.asyncttwiz.android

import arcanum.engine.state.GameState
import arcanum.engine.state.ObjectId
import arcanum.engine.view.View
import org.asyncttwiz.automatum.CardPlays
import org.asyncttwiz.automatum.CombatFlow
import org.asyncttwiz.automatum.CombatShown
import org.asyncttwiz.automatum.ComputedBoard
import org.asyncttwiz.automatum.EngineProjection
import org.asyncttwiz.automatum.HandPlays
import org.asyncttwiz.automatum.SharedFlow

/**
 * Engine-responsiveness R3, R4a: what the engine thread works out for a board of a game on engine state as it builds it
 * ([EngineGame]), from its one view of the state (R2), carried by the board ([TableBoard.values]):
 * - [computed]: the computed card values ([ComputedBoard]: P/T, keywords, type and colour changes, until-end-of-turn
 *   marks), made with the board: the table never shows the previous board's with it;
 * - the player's hand plays ([HandPlays]): worked out on the engine thread with the board it publishes as the result of
 *   an action ([EngineGame.Shown]), or on the engine thread when asked first. An own action's board (its early board, or
 *   its whole outcome's) gets them and its card menus' plays right after it is published, before the engine thread's
 *   follow-up work (the rest of the action's event, the device's own steps): they are promised ([promisePlays]) before
 *   the board goes out, so a reader on another thread (the table's) waits for them alone, not for that work;
 * - the card menus' plays ([CardPlays]), the shared flow ([SharedFlow]: priority, the open decision) and what the table
 *   shows of combat ([CombatShown]): lazy, on the engine thread when first asked, once per board (a board published again
 *   unchanged keeps them).
 *
 * [state] is the game's own state the board shows (full information, never changed); [board] the board itself.
 */
class EngineBoardValues internal constructor(
    val viewer: String,
    val computed: ComputedBoard,
    private val board: EngineProjection.Board,
    private val gameId: String,
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

    /** The hand plays, worked out on the engine thread now when not yet (waits for it; inline on it), or, when promised
     * ([promisePlays]), as soon as the engine thread has them. */
    fun handPlays(): HandPlays = handPlays ?: promised()?.handPlays ?: engine.call { workedHandPlays() }

    /** Engine thread: the hand plays, worked out now when not yet. Never throws (as the table's did: none on failure). */
    fun workedHandPlays(): HandPlays = handPlays ?: try {
        HandPlays.of(state, viewer, view)
    } catch (problem: Exception) {
        AppLog.warn("automatum", "Couldn't list plays: ${problem::class.java.simpleName}")
        HandPlays.NONE
    }.also { handPlays = it }

    /** The card menus' plays, worked out on the engine thread when not yet (waits for it; inline on it), or, when
     * promised ([promisePlays]), as soon as the engine thread has them. */
    fun cardPlays(): CardPlays = cardPlays ?: promised()?.cardPlays ?: engine.call { workedCardPlays() }

    /** Engine thread: the card menus' plays, worked out now when not yet. Never throws (none on failure). */
    private fun workedCardPlays(): CardPlays = cardPlays ?: try {
        CardPlays.of(state, viewer, view ?: arcanum.engine.Engine.view(state), visible)
    } catch (problem: Exception) {
        AppLog.warn("automatum", "Couldn't list card plays: ${problem::class.java.simpleName}")
        CardPlays.NONE
    }.also { cardPlays = it }

    /** An open promise of the plays ([promisePlays]) and the thread that made it (the engine thread). */
    private class Promise(val kept: java.util.concurrent.CountDownLatch, val by: Thread)
    @Volatile private var promise: Promise? = null

    /**
     * Engine thread, before an own action's board goes out: its hand plays and card menus' plays are worked out next
     * ([workPlays]), before any other engine work, so a reader on another thread waits for them alone instead of
     * queueing behind the engine thread's follow-up work. Nothing when both are already worked out.
     */
    fun promisePlays() {
        if (handPlays == null || cardPlays == null) promise = Promise(java.util.concurrent.CountDownLatch(1), Thread.currentThread())
    }

    /** Engine thread: the hand plays and card menus' plays worked out now (when not yet); keeps [promisePlays]. */
    fun workPlays() {
        try { workedHandPlays(); workedCardPlays() } finally { promise?.kept?.countDown(); promise = null }
    }

    /** Waits for an open promise of the plays (never on the thread that made it): this, or null when none is open. */
    private fun promised(): EngineBoardValues? {
        val open = promise ?: return null
        if (open.by === Thread.currentThread()) return null
        open.kept.await()
        return this
    }

    /** The shared flow once worked out ([sharedFlow]); null until then (and when there is none). */
    @Volatile var sharedFlow: SharedFlow? = null
        private set
    @Volatile private var flowWorked = false

    /** The board's shared flow (priority, the open decision; I2), worked out on the engine thread when not yet (waits for
     * it; inline on it), with what its panel reads ([SharedFlow.prepared], SYS-025). Null when it can't be read. */
    fun sharedFlow(): SharedFlow? = if (flowWorked) sharedFlow else engine.call {
        if (!flowWorked) {
            sharedFlow = try { SharedFlow.of(state, board.board, gameId, DeviceRandom::seed).prepared() } catch (problem: Exception) {
                AppLog.warn("automatum", "Couldn't read the shared flow: ${problem::class.java.simpleName}")
                null
            }
            flowWorked = true
        }
        sharedFlow
    }

    /** What the table shows of combat once worked out ([combat]); null until then. */
    @Volatile var combat: CombatShown? = null
        private set

    /** What the table shows of combat on the board (I3: declarations, the damage preview, early divisions), worked out on
     * the engine thread when not yet (waits for it; inline on it); [CombatShown.NONE] outside combat. */
    fun combat(): CombatShown = combat ?: engine.call {
        combat ?: try { CombatFlow.shown(state, board.board, gameId) } catch (problem: Exception) {
            AppLog.warn("automatum", "Couldn't work combat out: ${problem::class.java.simpleName}")
            CombatShown.NONE
        }.also { combat = it }
    }

    companion object {
        /** Tests and measurements: how many computed boards the engine thread made with its boards. */
        @Volatile var made = 0
            private set

        /**
         * Engine thread: the values of [board], just projected from the game [gameId]'s [state] for [viewer] ([view] and
         * [visible]: the model's own, R2).
         */
        fun make(board: EngineProjection.Board, gameId: String, state: GameState, viewer: String, view: View?,
            visible: Set<ObjectId>, engine: EngineWorker): EngineBoardValues {
            val shown = try { view ?: arcanum.engine.Engine.view(state) } catch (problem: Exception) { null }
            val computed = try {
                ComputedBoard.of(board.board, board.definitions, AutomatumBoards.definitions(board.definitions), viewer, state,
                    shown ?: arcanum.engine.Engine.view(state))
            } catch (problem: Exception) {
                AppLog.warn("automatum", "Couldn't work the board out: ${problem::class.java.simpleName}")
                ComputedBoard.failed("Automatum couldn't work this board out (${problem.message?.take(120) ?: problem::class.java.simpleName})")
            }
            made++
            return EngineBoardValues(viewer, computed, board, gameId, state, shown, visible, engine)
        }

        /** For tests: forget the count. */
        fun reset() { made = 0 }
    }
}

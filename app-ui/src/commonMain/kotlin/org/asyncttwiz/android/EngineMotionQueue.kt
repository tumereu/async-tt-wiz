package org.asyncttwiz.android

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import arcanum.engine.state.GameState
import org.asyncttwiz.protocol.BoardState

/**
 * The table's side of [EngineMotion] (main thread): the board to draw while a published board's steps play. Started by
 * the board's [EngineGame.Shown.version]; a newer board (the player's next action, the server) ends it at once, and the
 * table goes on from wherever its cards are; except a branch's take-back (HIS-106), which plays to its end when its board
 * is published again unchanged (its status, a note, the app's return).
 */
class EngineMotionQueue {
    private var version = -1L
    private var serial = 0L
    private var steps: List<TableBoard> = emptyList()
    /** Counts the step sequences started ([steps]). Snapshot state (problem report QVERHX): a newer board ends the steps
     * at once, and whatever waits on [playing] or [stoppedAt] outside composition (a combat's hold, HIS-113; a branch's
     * take-back, HIS-106) must hear of it; as a plain field it never did, and a combat hold whose steps a newer board
     * ended waited for ever, holding playback with it. */
    private val runState = mutableLongStateOf(0L)
    private var run: Long
        get() = runState.longValue
        set(value) { runState.longValue = value }
    /** The step sequence and how many of its steps have played (state: the table recomposes on it). */
    private val played = mutableStateOf(-1L to 0)
    private val index: Int get() = played.value.let { (at, count) -> if (at == run) count else 0 }
    /** HIS-113, TBL-125: the combat moment holding the view: the steps stop at it while it holds, the attackers at the
     * first step with attackers, the damage at the step right after it was dealt ([TableBoard.damageDealt]); the hold's
     * end publishes the board anew, and the steps go on from there. */
    var stopAt by mutableStateOf<org.asyncttwiz.protocol.game.GameDevice.CombatMoment?>(null)
    /** The step sequence ([run]) and step the steps stopped at for [stopAt], or null. */
    private val stopped = mutableStateOf<Pair<Long, Int>?>(null)

    /** Composition: [version]'s board [live], with the steps of [transition] to show before it when it is new (a board
     * published again with the same transition, an action's preview, shows at once; a branch's take-back goes on, and so
     * do steps on their way to a combat moment holding the view or stopped at one, HIS-113: the server's confirmation
     * meanwhile, the hold's end). */
    fun board(version: Long, transition: EngineGame.Transition?, live: TableBoard): TableBoard {
        if (version != this.version) {
            // The table just opened: the last board shows as it is
            if (this.version < 0) transition?.let { serial = it.serial }
            this.version = version
            val same = transition != null && transition.serial == serial
            val takeBackGoesOn = same && transition.undone?.branch == true
            val combatGoesOn = same && playing && (stopped.value?.first == run || stopAt != null)
            if (combatGoesOn) AppLog.info("game", "Combat hold: the board's steps go on from step ${index + 1} of ${steps.size}" +
                (stopAt?.let { ", now held for ${it::class.simpleName}" } ?: ""))
            if (!takeBackGoesOn && !combatGoesOn) {
                steps = if (transition == null || transition.serial == serial || transition.jump) emptyList() else transition.steps
                run++
            }
            transition?.let { serial = it.serial }
        }
        return this.steps.getOrNull(index) ?: live
    }

    /** The key of what is drawn now, for the table's scene clock (each step is a scene of its own). */
    fun key(): String = "motion:$version:$index"

    /** The table finished animating to what [board] gave last: the next step, or the board itself. */
    fun settled() {
        val at = index
        if (at >= steps.size) return
        val stops = when (stopAt) {
            org.asyncttwiz.protocol.game.GameDevice.CombatMoment.Attackers -> EngineMotion.attackers(steps[at].board).isNotEmpty()
            org.asyncttwiz.protocol.game.GameDevice.CombatMoment.Damage -> steps[at].damageDealt
            null -> false
        }
        if (stops) { stopped.value = run to at; return }
        played.value = run to at + 1
    }

    /** Whether a step is drawn instead of the board. */
    val playing: Boolean get() = index < steps.size

    /** HIS-113: whether the steps stand stopped at [moment] ([stopAt]). */
    fun stoppedAt(moment: org.asyncttwiz.protocol.game.GameDevice.CombatMoment): Boolean =
        playing && stopAt == moment && stopped.value == (run to index)
}

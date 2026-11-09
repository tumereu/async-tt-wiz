package org.asyncttwiz.android

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import arcanum.engine.state.GameState
import org.asyncttwiz.protocol.BoardState

/**
 * TBL-040 on engine state (owner playtest 2026-11-04): what one published board change shows in order. An own action
 * and what follows it by itself (the passes, the spell resolving, its triggers and their tokens) arrive as one board;
 * its [EngineGame.Transition.steps] are the boards in between, which the table plays one after another, each as long as
 * its own animation (the card move speed, TBL-038) and no playback pause, so every card goes to its place in its turn.
 */
internal object EngineMotion {
    /** At most this many boards in between (the last ones): a long chain shows its end. */
    const val MAX_STEPS = 8
    /** Steps are looked for in the last this many events only (each pass is an event of its own). */
    const val MAX_EVENTS = 64

    /** What the table draws of [state]'s objects (where each is, tapped, face down, its face, controller), the Stack and
     * the attackers (HIS-113): a step that changes none of it shows nothing new. */
    fun objects(state: GameState): Any = Triple(state.objects.values.mapTo(HashSet()) {
        listOf(it.id, it.zone, it.tapped, it.faceDown, it.face, it.controller)
    }, state.stack.toList(), state.combat?.attackers?.map { it.id })

    /** What moves on [board]: each card's container, place, tapped, covered and face, the Stack's entries and the attackers
     * in the combat area. */
    fun key(board: BoardState): List<Any?> = listOf(board.cards.associate {
        it.id to listOf(it.containerId, it.placement?.position, it.tapped, it.covered, it.faceId)
    }, board.stackEffects.map { it.id }, attackers(board))

    /** The attackers in [board]'s combat area. */
    fun attackers(board: BoardState): List<String> = board.combat?.pairs.orEmpty().flatMap { pair -> pair.attackers.map { it.sourceId } }

    /**
     * The [steps] worth showing between [before] (shown before) and [after] (the last): none that looks like the board
     * before it, none that looks like the last. With [shownFirst] (the board the action already published at once,
     * [EngineGame]'s preview) the steps start with it (drawn already, it settles at once; else it shows first) and go on
     * after the step that looks like it (only it when none does: never back to an older look).
     */
    fun trim(steps: List<TableBoard>, before: TableBoard?, after: TableBoard, shownFirst: TableBoard?): List<TableBoard> {
        val keys = steps.map { key(it.board) }
        val candidates = if (shownFirst == null) steps else {
            val at = keys.lastIndexOf(key(shownFirst.board))
            listOf(shownFirst) + if (at < 0) emptyList() else steps.drop(at + 1)
        }
        val kept = ArrayList<TableBoard>()
        var last = before?.let { key(it.board) }
        val end = key(after.board)
        for (step in candidates) {
            val k = key(step.board)
            if (k == last || k == end) continue
            kept += step
            last = k
        }
        return kept
    }
}

/**
 * The table's side of [EngineMotion] (main thread): the board to draw while a published board's steps play. Started by
 * the board's [EngineGame.Shown.version]; a newer board (the player's next action, the server) ends it at once, and the
 * table goes on from wherever its cards are; except a branch's take-back (HIS-106), which plays to its end when its board
 * is published again unchanged (its status, a note, the app's return).
 */
internal class EngineMotionQueue {
    private var version = -1L
    private var serial = 0L
    private var steps: List<TableBoard> = emptyList()
    /** Counts the step sequences started ([steps]). Snapshot state (problem report QVERHX): a newer board ends the steps
     * at once, and whatever waits on [playing] or [atAttackers] outside composition (a combat's hold, HIS-113; a branch's
     * take-back, HIS-106) must hear of it; as a plain field it never did, and a combat hold whose steps a newer board
     * ended waited for ever, holding playback with it. */
    private val runState = mutableLongStateOf(0L)
    private var run: Long
        get() = runState.longValue
        set(value) { runState.longValue = value }
    /** The step sequence and how many of its steps have played (state: the table recomposes on it). */
    private val played = mutableStateOf(-1L to 0)
    private val index: Int get() = played.value.let { (at, count) -> if (at == run) count else 0 }
    /** HIS-113, TBL-125: a combat's attackers and damage in one board: its first step with the attackers stops the steps while
     * this holds (the hold's end publishes the board anew, which ends the steps). */
    var stopAtAttackers by mutableStateOf(false)
    private val stopped = mutableStateOf(-1L)

    /** Composition: [version]'s board [live], with the steps of [transition] to show before it when it is new (a board
     * published again with the same transition, an action's preview, shows at once; a branch's take-back goes on). */
    fun board(version: Long, transition: EngineGame.Transition?, live: TableBoard): TableBoard {
        if (version != this.version) {
            // The table just opened: the last board shows as it is
            if (this.version < 0) transition?.let { serial = it.serial }
            this.version = version
            val takeBackGoesOn = transition != null && transition.serial == serial && transition.undone?.branch == true
            if (!takeBackGoesOn) {
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
        if (stopAtAttackers && EngineMotion.attackers(steps[at].board).isNotEmpty()) { stopped.value = run; return }
        played.value = run to at + 1
    }

    /** Whether a step is drawn instead of the board. */
    val playing: Boolean get() = index < steps.size

    /** HIS-113: whether the steps stopped at the attackers ([stopAtAttackers]). */
    val atAttackers: Boolean get() = stopped.value == run && playing
}

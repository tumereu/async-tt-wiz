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
    /** HIS-112 (Stack animation, owner 2026-11-14): the Stack entries of the plays shown not landed yet: the steps stop at the
     * first step with any of them on the Stack while they show, so nothing of the event after it (its resolution) plays
     * under them. */
    var stopEntries by mutableStateOf<Set<String>>(emptySet())
    /** HIS-112 (owner, 2026-11-20): the cards of the special actions shown not landed yet: the steps stop before the first step
     * changing any of them (their action), so each is drawn as it was until its play lands in it. */
    var stopCards by mutableStateOf<Set<String>>(emptySet())
    /** The board itself ([board]'s live), the step after the last. */
    private var liveBoard: TableBoard? = null
    /** Whether [heldBefore] holds for [stopCards] (released once they landed) rather than for [stopEntries]. */
    private var heldForCards = false
    /** The step sequence ([run]) and step the steps stopped at for [stopAt] or [stopEntries], or null. */
    private val stopped = mutableStateOf<Pair<Long, Int>?>(null)
    /** The board [board] gave last: what stays drawn while the Stack animation holds the table ([board]'s hold). */
    private var last: TableBoard? = null
    /** HIS-112 (owner playtest 2026-11-15, report VSVLSK): the step sequence ([run]) whose first step would take an entry of a
     * play shown not landed yet off the Stack: the board before it ([before]) stays drawn until the plays have landed. */
    private val heldBefore = mutableStateOf<Long?>(null)
    private var before: TableBoard? = null

    /** Composition: [version]'s board [live], with the steps of [transition] to show before it when it is new (a board
     * published again with the same transition, an action's preview, shows at once; a branch's take-back goes on, and so
     * do steps on their way to a combat moment holding the view or stopped at one, HIS-113: the server's confirmation
     * meanwhile, the hold's end). */
    fun board(version: Long, transition: EngineGame.Transition?, live: TableBoard, hold: Boolean = false,
        /** HIS-112: the Stack entries of the plays shown not landed yet ([stopEntries] as this board brings them). */
        plays: Set<String> = emptySet(),
        /** HIS-112: the cards of the special actions shown not landed yet ([stopCards] as this board brings them). */
        cards: Set<String> = emptySet()): TableBoard {
        liveBoard = live
        // The Stack animation (owner 2026-11-14): while its step plays (an entry's target arrows) a newer board waits, so the
        // entry stays on the Stack as long as its own animation and what follows plays after it; never a take-back
        if (hold && version != this.version && this.version >= 0 && transition?.undone == null) last?.let { return it }
        if (version != this.version) {
            // The table just opened: the last board shows as it is
            if (this.version < 0) transition?.let { serial = it.serial }
            this.version = version
            val same = transition != null && transition.serial == serial
            val takeBackGoesOn = same && transition.undone?.branch == true
            // HIS-112: steps stopped where a play shown went on the Stack go on too (from there, once it landed); (owner playtest
            // 2026-11-15, report VSVLSK) so do steps on their way to a play shown not landed yet: ended by the same board
            // published again (its status, the other player's presence), they jumped to the end of its event, and the plays then
            // landed on a Stack the entries had already left, with no arrows
            val combatGoesOn = same && playing && (stopped.value?.first == run || stopAt != null || plays.isNotEmpty() || cards.isNotEmpty())
            if (combatGoesOn) AppLog.info("game", "View hold: the board's steps go on from step ${index + 1} of ${steps.size}" +
                (stopAt?.let { ", now held for ${it::class.simpleName}" } ?: "") +
                (if (plays.isNotEmpty()) ", toward the plays shown of ${plays.joinToString { it.take(24) }}" else "") +
                (if (cards.isNotEmpty()) ", toward the special actions shown of ${cards.joinToString { it.take(24) }}" else ""))
            // HIS-112 (owner, 2026-11-20): the same board published again while held before its first step for a special action
            // stays held (its time not up yet)
            val heldGoesOn = same && heldBefore.value == run && heldForCards && cards.isNotEmpty()
            if (!takeBackGoesOn && !combatGoesOn && !heldGoesOn) {
                steps = if (transition == null || transition.serial == serial || transition.jump) emptyList() else transition.steps
                run++
                heldForCards = false
                // HIS-112 (owner playtest 2026-11-15, report VSVLSK: the caster's own trigger shown over a Stack its event had
                // already resolved): a first step that takes an entry of a play shown not landed yet off the Stack waits, the
                // board before it drawn, until the plays have landed
                val from = last
                val first = steps.firstOrNull()
                // HIS-112 (owner, 2026-11-20): so does a first step (or the board itself) changing the card of a special action
                // shown not landed yet: the card as it was until its play lands in it
                val next = first ?: live.takeIf { transition != null && !transition.jump && transition.serial != serial }
                val changing = if (from != null && next != null && cards.isNotEmpty()) cards.filter { changes(from, next, it) } else emptyList()
                if (from != null && changing.isNotEmpty()) {
                    heldBefore.value = run
                    before = from
                    heldForCards = true
                    AppLog.info("game", "Board steps held before step 1 of ${steps.size}: it changes ${changing.joinToString { it.take(24) }} " +
                        "before its special action's play lands")
                } else if (from != null && first != null && plays.isNotEmpty()) {
                    val after = org.asyncttwiz.protocol.CombatStackRules.stack(first.board).cards.toSet()
                    val leaving = org.asyncttwiz.protocol.CombatStackRules.stack(from.board).cards.filter { it in plays && it !in after }
                    if (leaving.isNotEmpty()) {
                        heldBefore.value = run
                        before = from
                        AppLog.info("game", "Board steps held before step 1 of ${steps.size}: it takes ${leaving.joinToString { it.take(24) }} " +
                            "off the Stack before its play shown lands")
                    }
                }
            }
            transition?.let { serial = it.serial }
        }
        if (heldBefore.value == run) {
            val kept = before
            if (kept != null && (if (heldForCards) cards.isNotEmpty() else plays.isNotEmpty())) return kept.also { last = it }
            heldBefore.value = null
            before = null
            AppLog.info("game", "Board steps go on from step 1 of ${steps.size}: the " +
                (if (heldForCards) "special actions shown landed in their cards" else "plays shown landed"))
            heldForCards = false
        }
        return (this.steps.getOrNull(index) ?: live).also { last = it }
    }

    /** The key of what is drawn now, for the table's scene clock (each step is a scene of its own). */
    fun key(): String = "motion:$version:$index"

    /** The table finished animating to what [board] gave last: the next step, or the board itself. */
    fun settled() {
        val at = index
        if (at >= steps.size) return
        // HIS-112: the board before the first step stays drawn until the plays shown land ([board])
        if (heldBefore.value == run) return
        val stops = when (stopAt) {
            org.asyncttwiz.protocol.game.GameDevice.CombatMoment.Damage -> steps[at].damageDealt
            null -> false
        } || stopEntries.isNotEmpty() && org.asyncttwiz.protocol.CombatStackRules.stack(steps[at].board).cards.any { it in stopEntries }
        if (stops) { stopped.value = run to at; return }
        // HIS-112 (owner, 2026-11-20): before the step that changes a special action's card while its play shows
        val next = steps.getOrNull(at + 1) ?: liveBoard
        if (stopCards.isNotEmpty() && next != null && stopCards.any { changes(steps[at], next, it) }) {
            if (stopped.value != (run to at)) AppLog.info("game", "Board steps stopped at step ${at + 1} of ${steps.size}: the next changes " +
                "${stopCards.filter { changes(steps[at], next, it) }.joinToString { it.take(24) }} before its special action's play lands")
            stopped.value = run to at
            return
        }
        played.value = run to at + 1
    }

    /** Whether a step is drawn instead of the board. */
    val playing: Boolean get() = index < steps.size

    /**
     * TRN-165 (owner 2026-11-19): the main button skips the board's steps: the board itself is drawn next, whatever step was
     * playing or held (a combat's hold, a play shown's entry), [why] for the log. True when steps were playing.
     */
    fun skipToEnd(why: String): Boolean {
        val held = heldBefore.value == run
        if (!playing && !held) return false
        AppLog.info("game", "Board steps skipped ($why): from step ${index + 1} of ${steps.size} to the board itself" +
            if (held) " (held before the first step for a play shown)" else "")
        played.value = run to steps.size
        stopped.value = null
        heldBefore.value = null
        before = null
        return true
    }

    /** HIS-112: whether the board drawn is where the plays shown of [stopEntries] show: the steps stopped at the first
     * step with one of them on the Stack, or over (none had one). */
    fun reachedEntries(): Boolean = !playing || (stopEntries.isNotEmpty() || stopCards.isNotEmpty()) &&
        (stopped.value == (run to index) || heldBefore.value == run)

    /**
     * HIS-112 (owner, 2026-11-20): whether card [card] looks different on [to] than on [from]: where it is, face up or down,
     * its face, and its object (its marks: a door unlocked, CR 709.5; a new object after a zone change, CR 400.7).
     */
    private fun changes(from: TableBoard, to: TableBoard, card: String): Boolean {
        fun look(b: TableBoard): List<Any?> {
            val c = b.board.cards.firstOrNull { it.id == card }
            val o = b.engine?.state?.objects?.values?.lastOrNull { it.card.value == card }
            return listOf(c?.containerId, c?.covered, c?.faceId, o?.id, o?.zone, o?.faceDown, o?.face, o?.marks)
        }
        return look(from) != look(to)
    }

    /** HIS-113: whether the steps stand stopped at [moment] ([stopAt]). */
    fun stoppedAt(moment: org.asyncttwiz.protocol.game.GameDevice.CombatMoment): Boolean =
        playing && stopAt == moment && stopped.value == (run to index)
}

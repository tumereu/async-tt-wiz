package org.asyncttwiz.android

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.FastForward
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.WaterDrop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import arcanum.engine.input.Block
import arcanum.engine.input.Input
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
import arcanum.engine.state.GameState
import arcanum.engine.state.ObjectId
import arcanum.engine.state.PlayerId
import arcanum.engine.state.StackItemKind
import kotlinx.coroutines.delay
import kotlin.coroutines.resume
import kotlinx.coroutines.flow.first
import org.asyncttwiz.protocol.*
import org.asyncttwiz.protocol.game.AssumeResolves
import org.asyncttwiz.protocol.game.GameDevice
import org.asyncttwiz.protocol.game.GameEvent
import org.asyncttwiz.protocol.game.PlaySettings
import org.asyncttwiz.protocol.game.ResponseMode
import org.asyncttwiz.protocol.game.TableChange
import org.asyncttwiz.automatum.EngineDrafts
import org.asyncttwiz.appui.screenWidthDp
import org.asyncttwiz.appui.screenHeightDp

/**
 * The table's routes into a game played on engine state (I-D56 step C): the player's own inputs ([send]: a cast, an
 * answer, a declaration), the step arrow's pass and End turn, the opening, and the combat staging and battlefield places
 * ([local]). Null on today's model.
 */
interface EngineRoutes {
    /** The player's own [inputs] of one flow; null when sent, else why not ([GameDevice.REVEALS_HIDDEN], …). */
    fun send(inputs: List<Input>): String?
    fun pass(): Boolean
    fun endTurn(): Boolean
    fun mulligan(): Boolean
    fun keep(): Boolean
    /** TRN-062: the cards a mulligan puts on the bottom, right after it. */
    fun bottom(cards: List<String>): Boolean
    /** STK-025, HIS-099 (problem report RC3J4K): how many times a board already shown was taken back, and the last one's words. */
    fun takeBacks(): Long = 0L
    fun lastTakeBack(): String = ""
    /** Table actions the controls make: a card moved on its own battlefield (its place, shared), the combat staging,
     * attack choices and early answers (shared as table changes at the head, I-D54 §8; behind it the declaring player's
     * own until declared, I-D56 §12). False for anything else (nothing changes the game by hand). [requestId]: the batch's
     * request id the table worked [actions] out with (an action may name what an earlier one of them made, the combat
     * area its Open made); null for one action. */
    fun local(actions: List<PlayAction>, requestId: String? = null): Boolean
    /** Whether the game waits for the other player's choice now (nobody holds priority: a choice while resolving, a
     * trigger's targets or modes, their blockers, their mulligan): what the viewer tries meanwhile is refused with a shake
     * and the event text's flash (HIS-075, TBL-118). */
    fun othersChoice(): Boolean = false

    /** CMB-040 (owner, 2026-11-05): the defender's blocks the viewer places on the lanes while waiting for them (null when
     * not waiting for the defender's blocks); assumed only by the main button. */
    fun assuming(): AssumedBlocks? = null

    /** Whether the viewer's response mode for this game is Control (STK-036): the step arrow's words depend on it (TRN-063). */
    fun control(): Boolean = false

    /** HIS-110: whether the other player's reveal panel is open: it comes before any choice panel of the viewer's. */
    fun revealing(): Boolean = false

    /** HIS-112: whether a play shows large in the middle of the table: the Stack line and the wait's words give way. */
    fun showing(): Boolean = false

    /** CMB-034: whether the viewer may act on the board shown now (not while playback runs on past it). */
    fun mayAct(): Boolean = true

    /** HIS-021, HIS-112, HIS-113: what a finger held on the table pauses; null without a game's view to wait. */
    val viewPause: ViewPause? get() = null

    /** HIS-021, HIS-112, HIS-113: whether a held finger pauses something the view is waiting on now (the corner's icon). */
    fun viewPaused(): Boolean = false
}

/**
 * CMB-040 (owner, 2026-11-05): the defender's blocks the attacker places on the lanes while waiting for them, at the
 * event [tip]: by blocker (the defender's creature's card id), the attacker pairs it blocks, its own first. Personal and
 * drawn only: nothing is shared, nothing assumed until the main button's Assume.
 */
class AssumedBlocks(val tip: String) {
    var blocks by mutableStateOf<Map<String, List<String>>>(emptyMap())
        private set
    private var cache: Triple<BoardState, Map<String, List<String>>, TableBoard>? = null

    /** [blocker] blocks exactly the attackers of [pairs] (none: it blocks nothing). */
    fun set(blocker: String, pairs: List<String>) { blocks = if (pairs.isEmpty()) blocks - blocker else blocks + (blocker to pairs) }
    fun clear() { blocks = emptyMap() }

    /** [play] with these blocks drawn on its lanes (the same board while they are unchanged). */
    fun board(play: TableBoard): TableBoard {
        val placed = blocks
        cache?.takeIf { it.first === play.board && it.second == placed }?.let { return it.third }
        var board = play.board
        for ((blocker, pairs) in placed) {
            val area = board.combat ?: break
            board = runCatching { CombatStackRules.stage(board, play.definitions,
                org.asyncttwiz.protocol.CommandIds.uuidV5(blocker, "assumed-block"), CombatAction.SetBlocks(area.id, blocker,
                    pairs.filter { id -> area.pairs.any { it.id == id } }, emptyList())) }.getOrDefault(board)
        }
        val shown = if (board === play.board) play else play.copy(board = board, values = play.values?.onLanes(board))
        cache = Triple(play.board, placed, shown)
        return shown
    }

    /** The blocks to assume on [board] (the board without them): each blocker with each attacker of its pairs. */
    fun inputs(board: BoardState): List<Pair<String, String>> = blocks.flatMap { (blocker, pairs) ->
        pairs.flatMap { id -> board.combat?.pairs?.firstOrNull { it.id == id }?.attackers.orEmpty().map { blocker to it.sourceId } }
    }
}

/** The open table's engine routes and the async panels' own state (personal UI). */
class EngineTableRoutes(val viewer: String, private val game: () -> EngineGame?, private val sent: () -> Unit,
    private val shown: () -> TableBoard?, private val acknowledge: () -> Unit = {}) : EngineRoutes {
    /** TRN-065: the player tapped OK on a final result (the game is back in its lobby). */
    fun acknowledgeResult() = acknowledge()
    val engine: EngineGame? get() = game()

    /** The rewind dialog (hold Undo, or an undo that needs the other player's approval): its chosen target. */
    var rewind by mutableStateOf<RewindChoice?>(null)
    /** The note shown now (§8's branch note, SYS-012's refusal note, …), each told once. */
    var note by mutableStateOf<String?>(null)
        private set
    /** SYS-078 (owner, 2026-11-05): the notes told while another one shows, in order: each shows when the one before is
     * done, never replacing it at once. */
    private val waitingNotes = ArrayDeque<String>()

    /** Tells [text]: at once when no note shows, else after the notes before it (the same text twice in a row: once). */
    fun tell(text: String) {
        if (note == null) note = text
        else if ((waitingNotes.lastOrNull() ?: note) != text) waitingNotes.addLast(text)
    }

    /** The note shown is done (its time is up, or the player tapped it): the next one shows. */
    fun noteDone() { note = waitingNotes.removeFirstOrNull() }
    /** Bumped when an Undo can't do anything: the button shakes (I-D46 b). */
    var undoShake by mutableIntStateOf(0)

    data class RewindChoice(val target: String?)

    /** TRN-075: bumped when the move passes to the player from the other player: the main button pulses once. */
    var moveCue by mutableIntStateOf(0)

    /** TRN-074: the turn banner to show. */
    val banners = TurnBannerQueue()

    /** Timeline mode (HIS-004): the shown event the board is reviewed at; null in play mode. */
    var review: String?
        get() = reviewAt
        set(at) {
            // Report DH3QX6: every move on the timeline but Auto's own steps (its start and stop say those)
            if (at != reviewAt && !reviewPlaying) AppLog.info("timeline", "Timeline at ${at ?: "none (play mode)"}, from ${reviewAt ?: "play mode"}")
            reviewAt = at
        }
    private var reviewAt by mutableStateOf<String?>(null)
    /** Timeline mode's Auto (HIS-013). */
    var reviewPlaying: Boolean
        get() = autoPlaying
        set(on) {
            if (on != autoPlaying) AppLog.info("timeline", "Timeline Auto ${if (on) "on" else "off"} at $reviewAt")
            autoPlaying = on
        }
    private var autoPlaying by mutableStateOf(false)

    /** CMB-040: the blocks being placed to assume while waiting for the defender's (personal). */
    private var assumed: AssumedBlocks? = null

    override fun assuming(): AssumedBlocks? {
        val shown = game()?.shown ?: return null
        val status = shown.status as? GameDevice.Status.Waiting ?: return null
        if (status.offer != GameDevice.Offer.AssumeBlockers || review != null) return null
        return assumed?.takeIf { it.tip == shown.tip } ?: AssumedBlocks(shown.tip).also { assumed = it }
    }

    /** CMB-040: assumes the blocks placed (none: no blocks); a refusal is told with the engine's reason. */
    private fun assumeBlocks(g: EngineGame, placed: AssumedBlocks) {
        val s = g.shown?.engine ?: return
        val board = shown()?.board ?: return
        fun objectOf(card: String) = s.battlefield.firstOrNull { (it as? ObjectId.Card)?.card?.value == card }
        val chosen = placed.inputs(board).mapNotNull { (b, a) -> Block(objectOf(b) ?: return@mapNotNull null, objectOf(a) ?: return@mapNotNull null) }
        g.assume(chosen) { acted ->
            when (acted) {
                is GameDevice.Acted.Refused -> tell("These blocks can't be assumed: ${acted.reason}")
                else -> placed.clear()
            }
            pump()
        }
    }

    /** Whether the table is still animating a published board's steps (the motion queue), for the take-back's playback
     * to wait for its rewind (HIS-097). Set by the table. */
    var motionBusy: () -> Boolean = { false }

    /** Enters timeline mode at [at] (HIS-004); playback running meanwhile waits paused for the centre text (HIS-006). */
    fun enterReview(at: String) {
        engine?.historyRetry()
        game()?.takeIf { it.shown?.status is GameDevice.Status.Playing }?.holdPlayback()
        reviewPlaying = false; review = at
    }
    fun leaveReview() { reviewPlaying = false; review = null }

    /** After an action on the engine game: what it sends goes out and the table shows its new board. */
    fun pump() = sent()

    private fun done(acted: GameDevice.Acted?): Boolean { sent(); return acted is GameDevice.Acted.Sent || acted == GameDevice.Acted.Confirmed || acted == GameDevice.Acted.Made }

    /**
     * An own action on the engine game in its callback form ([EngineWorker.call], web client step 4c): [ask] starts it,
     * [answered] reads its result on this thread. Android waits for the engine thread, so the result is in before this
     * returns and the press's answer is that result, in the same frame as before; a page whose engine side answers later
     * gets [asked] now (the action went out; its board and any refusal's note come with the board published after it).
     * [what] names it in the log when its answer comes later (an undo's answer is logged with what it took back).
     */
    private inline fun <T, R> own(asked: R, what: String, ask: (result: (T) -> Unit) -> Unit, crossinline answered: (T) -> R): R {
        val answer = Answer<R>()
        var later = false
        ask { result ->
            if (later) AppLog.info("engine", "Own $what answered later: $result")
            answer.set(answered(result))
        }
        if (answer.arrived) return answer.get()
        later = true
        AppLog.info("engine", "Own $what asked of the engine side; its answer comes later")
        return asked
    }

    override fun send(inputs: List<Input>): String? {
        val g = game() ?: return "unavailable"
        ownAction()
        return own(null, "send", { g.send(inputs, result = it) }) { acted ->
            if (acted is GameDevice.Acted.Refused) ownActionDropped()
            sent()
            when (acted) {
                is GameDevice.Acted.Sent, GameDevice.Acted.Confirmed, GameDevice.Acted.Kept, GameDevice.Acted.Made -> null
                is GameDevice.Acted.Refused -> acted.reason
            }
        }
    }
    override fun pass(): Boolean { val g = game() ?: return done(null); return own(true, "pass", { g.pass(it) }, ::done) }
    /** TRN-017, STK-029: holding Resolve, the Resolve chain. */
    fun holdResolve(): Boolean { val g = game() ?: return done(null); return own(true, "holdResolve", { g.holdResolve(it) }, ::done) }
    override fun endTurn(): Boolean { val g = game() ?: return false; return own(true, "endTurn", { g.endTurn(it) }) { sent(); true } }
    override fun mulligan(): Boolean { val g = game() ?: return done(null); return own(true, "mulligan", { g.mulligan(it) }, ::done) }
    override fun keep(): Boolean { val g = game() ?: return done(null); return own(true, "keep", { g.keep(it) }, ::done) }
    override fun bottom(cards: List<String>): Boolean { val g = game() ?: return done(null); return own(true, "bottom", { g.bottom(cards, it) }, ::done) }
    /** TRN-046, TRN-064: an ending tool's action [what] ([ask]: its callback form), then what it sends goes out; true when it
     * sent an event (a page whose engine side answers later: asked). */
    fun ending(what: String, ask: (result: (GameDevice.Acted) -> Unit) -> Unit): Boolean =
        own(true, "ending $what", ask) { acted -> sent(); acted is GameDevice.Acted.Sent }
    override fun takeBacks(): Long = game()?.takeBacks ?: 0L
    override fun lastTakeBack(): String = game()?.lastTakeBack.orEmpty()

    override fun local(actions: List<PlayAction>, requestId: String?): Boolean {
        val g = game() ?: return false
        val play = shown() ?: return false
        // Worked out on the engine side on the board shown (I-D56 step D2; web client step 4d: in the browser's worker); what goes
        // out is sent from here after. Not on a board the device has moved on from (switchreview #6)
        return own(true, "onShown", { g.stageOwn(play, actions, requestId, it) }) { done -> sent(); done }
    }

    fun status(): GameDevice.Status? = game()?.shown?.status

    override fun control(): Boolean = game()?.shown?.settings?.mode == ResponseMode.FullControl

    override fun revealing(): Boolean = openReveal() != null

    /**
     * HIS-110 (owner, 2026-11-12: OK dismissed late): the reveals this table's OK dismissed whose dismissal the engine thread
     * hasn't published yet. OK closes the panel at once, on this phone, whatever the engine thread is busy with; the device
     * catches up ([EngineGame.dismissReveal]). By event: what was dismissed never opens again (HIS-110), so they are kept.
     */
    private val dismissedReveals = mutableStateListOf<String>()

    /** HIS-110: the reveal whose panel is open on this table: the shown one, unless its OK was tapped already. */
    fun openReveal(): GameDevice.Reveal? = game()?.shown?.reveal?.takeIf { it.event !in dismissedReveals }

    /** HIS-110: the player tapped the reveal panel's OK on [reveal]: it closes now; the engine thread dismisses it after. */
    fun dismissReveal(reveal: GameDevice.Reveal) {
        val current = game() ?: return
        if (reveal.event in dismissedReveals) return
        dismissedReveals += reveal.event
        current.dismissReveal(reveal, org.asyncttwiz.appui.nanoTime())
        sent()
    }

    override fun showing(): Boolean = game()?.shown?.show != null

    override val viewPause = ViewPause()

    /** Live and in catch-up playback, not in timeline mode (its own Auto, HIS-017) or a branch's take-back (HIS-106). */
    override fun viewPaused(): Boolean = viewPause.paused && review == null && game()?.shown?.let { s ->
        !s.takingBack && (s.show != null || s.combatHold != null || s.status is GameDevice.Status.Playing) } == true

    /** TRN-015, I-D56 §14: the step arrow acts only for the viewer: shown while they may act. */
    fun arrowShown(): Boolean = when (status()) {
        GameDevice.Status.YourMove, GameDevice.Status.Drafting, is GameDevice.Status.Stopped -> true
        else -> false
    }

    /** Whether the viewer may act on the table now (their move, or a decision stop in playback). */
    fun canAct(): Boolean = arrowShown()

    override fun mayAct(): Boolean = canAct()

    /** Whether an own action of the player's is still being worked out off the engine game (the main button's step or
     * declaration, a cast's step: [SharedFlowController.busy], [CastController.busy]), before it reaches it. Set by the
     * table. */
    var working: () -> Boolean = { false }
    /** TRN-063 (owner, 2026-11-06): the board version an own action (a main button press, a cast or answer sent) was
     * made on while its result hasn't arrived; null when none. */
    private var awaiting by mutableStateOf<Long?>(null)

    /** The player's own action is made now ([send], the main button): its result is the next board published. */
    fun ownAction() { awaiting = game()?.shown?.version }

    /** The own action made last wasn't taken (nothing will be published for it). */
    fun ownActionDropped() { awaiting = null }

    /**
     * TRN-063: whether an own action of the player's waits for its result: worked out off the engine game ([working]) or
     * made and no newer board published since. [board]: the board the table shows, read so that a reader following
     * snapshot state sees each new one.
     */
    @Suppress("UNUSED_PARAMETER")
    fun ownActionPending(board: TableBoard?): Boolean {
        if (working()) return true
        val made = awaiting ?: return false
        return (game()?.shown?.version ?: return false) <= made
    }

    /** Drops the player's open cast or activation (STK-025: nothing of it sent yet); true when there was one. Set by the
     * table's toolkit. */
    var dropInteraction: () -> Boolean = { false }
    /** HIS-107: an Undo tapped while [working]: made once that work is done ([workDone]). */
    private var undoAfterWork = false

    /** Undo (a tap, I-D56 §10, HIS-084): free at once, else the rewind dialog with that target chosen; right after the
     * player's own action, while it is still being worked out or on its way, it waits for it and then takes it back
     * (HIS-107; the dialog opens then when it isn't free, [GameDevice.Note.RequestNeeded]). */
    fun undo(): Boolean {
        // STK-025, HIS-084 (owner playtest 2026-11-05 evening): an interaction's undo point is its start: with a cast or
        // activation open (nothing sent yet) Undo drops it, landing just before it began, where it would after it was made
        if (dropInteraction()) { AppLog.info("engine", "Undo dropped the open cast"); return true }
        val g = game() ?: return false
        // Owner problem report 2026-11-05: Attack is worked out off the main thread first; an Undo tapped meanwhile was
        // made before it (taking back what came before, then the attack refused)
        if (working()) { undoAfterWork = true; AppLog.info("engine", "Undo waits for the action being worked out"); return true }
        return own(true, "undo", { g.undo(result = it) }) { u ->
            when (u) {
                is GameDevice.Undo.Free, is GameDevice.Undo.Queued -> { sent(); true }
                is GameDevice.Undo.Request -> {
                    AppLog.info("undo", "Undo dialog opened at ${u.target}: it needs a request (${u.reason})")
                    sent(); rewind = RewindChoice(u.target); true
                }
                is GameDevice.Undo.Unavailable -> { AppLog.info("engine", "Undo not taken: ${u.reason}"); undoShake++; false }
            }
        }
    }

    /** The work [working] watched is done (main thread, after its result was handed on): an Undo tapped meanwhile is made
     * now. */
    fun workDone() { if (undoAfterWork && !working()) { undoAfterWork = false; undo() } }

    /** Holding Undo opens the rewind dialog to pick any earlier point (§10). */
    fun holdUndo() {
        engine?.historyRetry()
        rewind = RewindChoice(game()?.shown?.undoTarget)
        AppLog.info("undo", "Undo dialog opened by a hold at ${rewind?.target}, tip ${game()?.shown?.tip}")
    }

    /** The other player's name, for the wait's words and the unconfirmed mark's tooltip (set by the table). */
    var opponentName: String = "The other player"

    /** A wait's own control on the main button (TRN-066, TRN-070): an Assume button, or Cancel for the viewer's own
     * undo request; holding it does [hold] (named [holdName], TRN-017: a Stack wait's Assume is the Resolve chain, another
     * Assume its tap), else says [tooltip]. [refusal]: greyed (TRN-071: assuming would show hidden information), a tap
     * shakes it and holding it says why. [assumption]: an Assume, drawn in the Assume violet (TRN-063, owner 2026-11-10);
     * false for Cancel, the viewer's own action, drawn as the main button's own actions are. */
    class WaitAction(val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector, val tooltip: String? = null,
        val refusal: String? = null, val hold: (() -> Unit)? = null, val holdName: String? = null, val assumption: Boolean = true,
        val run: () -> Unit)

    /** The control the main button shows while the other player must act, or null for none: "Assume resolves" with
     * something on the Stack (STK-028), else "Assume no response" (TRN-070, owner playtest 2026-11-05 evening); while their
     * blocks are awaited "Assume no blockers", or "Assume blockers" once blockers are placed on the lanes (CMB-040). */
    fun waitAction(): WaitAction? {
        val g = game() ?: return null
        val shown = g.shown ?: return null
        val status = shown.status as? GameDevice.Status.Waiting ?: return null
        val name = opponentName
        return when (status.offer) {
            // TRN-017 (owner 2026-11-07): holding a Stack wait's Assume is the Resolve chain; holding any other Assume, its tap
            GameDevice.Offer.AssumeResolves -> if (shown.engine.stack.isNotEmpty())
                WaitAction("Assume resolves", AssumeIcon,
                    "Assume $name lets it resolve and go on; if they respond when they come back, the game goes back there",
                    hold = { g.holdAssume { pump() } }, holdName = RESOLVE_CHAIN) { g.assume { pump() } }
                else WaitAction("Assume no response", AssumeIcon,
                    "Assume $name lets the game go on; if they act here when they come back, the game goes back there",
                    hold = { g.assume { pump() } }, holdName = "Assume no response") { g.assume { pump() } }
            // CMB-040 (owner, 2026-11-05): the blocks placed on the lanes, assumed only by this button
            GameDevice.Offer.AssumeBlockers -> assuming()?.let { placed ->
                if (placed.blocks.isEmpty()) WaitAction("Assume no blockers", AssumeIcon,
                    "Assume $name doesn't block and go on; if they block when they come back, the game goes back there. " +
                        "Tap or drag their creatures onto your attackers to assume blocks instead.",
                    hold = { assumeBlocks(g, placed) }, holdName = "Assume no blockers") { assumeBlocks(g, placed) }
                else WaitAction("Assume blockers", AssumeIcon,
                    "Assume $name blocks as placed on the lanes and go on; if they block differently when they come back, the game goes back there",
                    hold = { assumeBlocks(g, placed) }, holdName = "Assume blockers") { assumeBlocks(g, placed) }
            }
            null -> when {
                status.reason == GameDevice.Wait.Request && shown.request?.by == viewer ->
                    WaitAction("Cancel request", Icons.Outlined.Close, assumption = false) { g.cancelRequest(); pump() }
                // TRN-067, TRN-071 (owner gallery review, 2026-11-06): where assuming their answer would show hidden
                // information or a random result, Assume shows greyed; holding it says why
                status.reason == GameDevice.Wait.HiddenCards -> WaitAction(
                    if (shown.engine.stack.isNotEmpty()) "Assume resolves" else "Assume no response", AssumeIcon,
                    refusal = "Assuming this would show you hidden cards or a random result: wait for $name.") {}
                else -> null
            }
        }
    }

    /** TRN-063, STK-029 (owner playtest 2026-11-12): only a play shown or a combat's hold keeps the device from a step of
     * the player's own it makes by itself: the main button holds nothing meanwhile. */
    fun ownStepHeld(): Boolean = game()?.shown?.ownStepHeld == true

    /** TRN-004 (owner playtest 2026-11-12): the quiet status in the main button's place while the game waits for the other
     * player ([quietWaitText]), or null. */
    fun quietWait(): String? = (status() as? GameDevice.Status.Waiting)?.let { quietWaitText(it.reason, opponentName) }

    /** The wait of [othersChoice], or null: the other player is choosing, declaring blockers or deciding on a mulligan. */
    private fun choiceWait(): GameDevice.Wait? = (status() as? GameDevice.Status.Waiting)?.reason
        ?.takeIf { it is GameDevice.Wait.Choosing || it == GameDevice.Wait.Blockers || it == GameDevice.Wait.Keep }

    override fun othersChoice(): Boolean = choiceWait() != null

    /** HIS-075: the event text's status while the other player makes a choice, in the wait's words (TRN-066: "Bob is
     * choosing · Ember Divination", "Bob is deciding on a mulligan"), or null when the game doesn't wait for one. */
    fun choosingText(play: TableBoard): String? = choiceWait()?.let { waitingText(it, play, opponentName) }

    /** The wait band's words while the other player must act (TRN-066), or null. */
    fun waitingLine(play: TableBoard): String? {
        val shown = game()?.shown ?: return null
        val status = shown.status as? GameDevice.Status.Waiting ?: return null
        // TRN-066 (owner, 2026-11-09): "Waiting for Bob" says where Bob is when he is behind (the player went ahead on assumptions)
        if (status.reason == GameDevice.Wait.TheirTurn) shown.opponentAt?.let { return behindText(it, viewer, opponentName) }
        return waitingText(status.reason, play, opponentName)
    }
}

/** TRN-063 (owner, 2026-11-10): the icon of every Assume on the main button, the Assume setting's own (TRN-059). */
val AssumeIcon = Icons.Outlined.FastForward

/** TRN-063 (owner, 2026-11-10): whether the main button draws [action] in the Assume violet: an Assume that can act now (a
 * greyed one stays greyed; Cancel is the viewer's own action, drawn as the others). */
fun assumeLook(action: EngineTableRoutes.WaitAction): Boolean = action.assumption && action.refusal == null

/**
 * The wait's control on the main button (TRN-066, TRN-070, MTG Arena-like): the same shape and size; an Assume filled with
 * the Assume violet, white words and its own icon ([AssumeIcon]), so an assumption reads apart from your own actions at a
 * glance (TRN-063, owner 2026-11-10); Cancel request in the own actions' look; holding it says what it does.
 */
@Composable
fun EngineWaitArrow(action: EngineTableRoutes.WaitAction, modifier: Modifier) {
    // TRN-071: a refused Assume is greyed; a tap shakes it, holding it says why
    var shakes by remember { mutableIntStateOf(0) }
    val refused = action.refusal != null
    val assume = assumeLook(action)
    val fill = if (assume) AutomatumViolet else Color.White
    ActionSquare(action.label, "", true, modifier.shaken(shakes).shadow(3.dp, AdvanceArrowShape)
        .background(fill, AdvanceArrowShape)
        .border(1.dp, if (assume) AutomatumViolet else AutomatumStepOutline, AdvanceArrowShape).clip(AdvanceArrowShape)
        .testTag("engine-wait-action").semantics { if (refused) disabled(); stateDescription = if (assume) "Assumption" else "" },
        fill = fill, ink = when { refused -> AutomatumStepInk.copy(alpha = .38f); assume -> Color.White; else -> AutomatumStepInk },
        compactContent = true,
        gestureKey = action.label to action.refusal, tooltip = action.refusal ?: action.tooltip,
        // TRN-017: a greyed one only says why
        holdName = action.holdName.takeIf { !refused && action.hold != null }, onHold = action.hold.takeIf { !refused },
        icon = { MainButtonContent(icon = { Icon(action.icon, null, Modifier.size(20.dp), tint = LocalContentColor.current) },
            label = action.label) }, onClick = { if (refused) shakes++ else action.run() })
}

/**
 * TRN-068: the unconfirmed mark, a small violet dot at the step arrow's corner while actions of yours wait for the other
 * player to see them (only you see it). A tap or hold says what it means.
 */
@Composable
fun EngineUnconfirmedMark(opponent: String, modifier: Modifier = Modifier) {
    var tip by remember { mutableStateOf(false) }
    val words = "$opponent hasn't seen your last moves yet; if $opponent answers differently, they'll be taken back"
    Box(modifier.size(24.dp).semantics { contentDescription = words }.testTag("engine-unconfirmed")
        .pointerInput(Unit) { detectTapGestures(onPress = { tip = true; tryAwaitRelease(); tip = false }) },
        contentAlignment = Alignment.Center) {
        Box(Modifier.size(10.dp).background(AutomatumViolet, androidx.compose.foundation.shape.CircleShape)
            .border(1.5.dp, Color.White, androidx.compose.foundation.shape.CircleShape))
        if (tip) HoldTooltip(words)
    }
}

val LocalEngineRoutes = staticCompositionLocalOf<EngineTableRoutes?> { null }

/** The note for a refusal of the player's own action (SYS-012: never dropped silently), or null when it shakes instead
 * (any other refusal: what it was made on, or the main button, [TableToolkitController.refusedShake]). */
fun refusedText(reason: String): String? = when (reason) {
    GameDevice.REVEALS_HIDDEN -> CastController.WAIT_TO_CATCH_UP
    "stale", "newLine" -> NOT_TAKEN
    GameDevice.DRAFT_DROPPED -> NOT_TAKEN
    else -> null
}

/** The record of a refusal shown by a shake ([LocalShownTexts]: nothing in the app hears it; the e2e phones do). */
const val REFUSAL_SHAKEN = "Refusal shaken:"

/** A player's own action the game moved on from before it reached the server (SYS-012). */
const val NOT_TAKEN = "That wasn't taken: the game moved on first. The table shows where it is now."

/** TRN-004 (owner playtest 2026-11-12): the quiet status in the main button's place while the game waits for [opponent]
 * and the button holds nothing, in plain words; null where nobody else is waited on (your own events on their way, an undo
 * request or a result suggestion, answered in their own dialogs). */
fun quietWaitText(wait: GameDevice.Wait, opponent: String): String? = when (wait) {
    is GameDevice.Wait.Respond, is GameDevice.Wait.MayAct, GameDevice.Wait.HiddenCards -> "$opponent's answer…"
    GameDevice.Wait.Blockers -> "$opponent is blocking…"
    GameDevice.Wait.Keep -> "$opponent's mulligan…"
    is GameDevice.Wait.Choosing -> "$opponent is choosing…"
    GameDevice.Wait.TheirTurn -> "$opponent's move…"
    GameDevice.Wait.Sending, GameDevice.Wait.Request, GameDevice.Wait.Suggestion -> null
}

/** The waiting bar's words (§14) for [wait]. */
fun waitingText(wait: GameDevice.Wait, play: TableBoard, opponent: String): String? = when (wait) {
    is GameDevice.Wait.Respond -> "$opponent may respond to ${engineName(play, wait.to) ?: "your spell"}"
    is GameDevice.Wait.MayAct -> "$opponent may act · ${org.asyncttwiz.automatum.EngineProjection.position(wait.step).label}"
    GameDevice.Wait.Blockers -> "$opponent is declaring blockers"
    GameDevice.Wait.Keep -> "$opponent is deciding on a mulligan"
    is GameDevice.Wait.Choosing -> "$opponent is choosing" + (wait.source?.let { engineName(play, it) }?.let { " · $it" } ?: "")
    GameDevice.Wait.TheirTurn -> "Waiting for $opponent"
    GameDevice.Wait.HiddenCards -> "Waiting for $opponent · going on would show you hidden cards or a random result"
    GameDevice.Wait.Request -> "Waiting for the undo request to be answered"
    GameDevice.Wait.Suggestion -> "Waiting for an answer to the suggested result"
    GameDevice.Wait.Sending -> null
}

/** TRN-066 (owner, 2026-11-09): where the other player [opponent] is when they are behind [viewer]: "Bob is at turn 5 · their
 * combat" (a combat step as "combat", any other step by its name; "your" in the viewer's own turn). */
fun behindText(at: GameDevice.OpponentAt, viewer: String, opponent: String): String {
    val whose = if (at.active.value == viewer) "your" else "their"
    val position = org.asyncttwiz.automatum.EngineProjection.position(at.step)
    val step = if (position.phase == "COMBAT") "combat" else position.label.lowercase()
    return "$opponent is at turn ${at.turn} · $whose $step"
}

/** The name of engine object [id] as the board's viewer may see it, or null; an ability on the Stack is named as its
 * source's ability ("Ember Herald's ability", STK-046), a copy of a spell as its spell. */
private fun engineName(play: TableBoard, id: ObjectId): String? {
    val item = (id as? ObjectId.StackItem)?.let { play.engine?.state?.stackItems?.get(it) }
    val ability = item != null && (item.kind is StackItemKind.Activated || item.kind is StackItemKind.Triggered)
    return sourceName(play, id)?.let { if (ability) "$it's ability" else it }
}

/** The name of engine object [id] (a Stack item's source) as the board's viewer may see it, or null. */
private fun sourceName(play: TableBoard, id: ObjectId): String? {
    val view = play.engine ?: return null
    val state = view.state
    val obj = when (id) {
        is ObjectId.Card -> state.objects[id]
        is ObjectId.StackItem -> state.stackItems[id]?.source?.let { state.objects[it] }
    }
    if (obj != null) return if (!view.visible(obj.id) || obj.faceDown) "a card" else obj.definition?.faces?.getOrNull(obj.face)?.name
    return state.stackItems[id]?.snapshots?.firstOrNull()?.characteristics?.name
}

/** The response mode's name and what it means (§2), for the toggle and the response mode dialog. */
fun ResponseMode.label(): String = when (this) {
    ResponseMode.FullControl -> "Control"
    ResponseMode.Normal -> "Normal"
    ResponseMode.AutoSkip -> "Quick"
}

/** TRN-059: the colour the response mode's cell fills with, by how much control you keep. */
fun ResponseMode.color(): Color = when (this) {
    ResponseMode.FullControl -> ControlBlue
    ResponseMode.Normal -> AutomatumViolet
    ResponseMode.AutoSkip -> QuickAmber
}

/** TRN-059: the colour the Assume resolves cell fills with, by how much control you keep: Never as Control, Normal as
 * Normal, No mana (Normal going on where they can't respond, as Quick) as Quick, and Always its own red-orange. */
fun AssumeResolves.color(): Color = when (this) {
    AssumeResolves.Never -> ControlBlue
    AssumeResolves.Normal -> AutomatumViolet
    AssumeResolves.WhenNoMana -> QuickAmber
    AssumeResolves.Always -> AssumeAlwaysRed
}

fun ResponseMode.meaning(): String = when (this) {
    ResponseMode.FullControl -> "Stop wherever you get priority, also after your own spells."
    ResponseMode.Normal -> "Stop in your main phases and where the other player's spell, attack, blocks or end step lets you respond."
    ResponseMode.AutoSkip -> "As Normal, but go on by itself where you can't respond, and answer questions with only one answer."
}

private val MODES = listOf<ResponseMode>(ResponseMode.FullControl, ResponseMode.Normal, ResponseMode.AutoSkip)

/** "Assume resolves" (HIS-096): the choice's name, its short form for the quick action, and what it means. */
fun AssumeResolves.label(): String = when (this) {
    AssumeResolves.Never -> "Never"
    AssumeResolves.Normal -> "Normal"
    AssumeResolves.WhenNoMana -> "When they have no mana"
    AssumeResolves.Always -> "Always"
}

fun AssumeResolves.short(): String = when (this) {
    AssumeResolves.Never -> "Never"
    AssumeResolves.Normal -> "Normal"
    AssumeResolves.WhenNoMana -> "No mana"
    AssumeResolves.Always -> "Always"
}

fun AssumeResolves.meaning(): String = when (this) {
    AssumeResolves.Never -> "Wait wherever the other player gets priority, with Assume on the main button, as Control stops."
    AssumeResolves.Normal -> "Go on at once where Normal mode would, and at their pause in your end step and right after your " +
        "attack; wait with Assume where Normal mode stops. After something they did, their own mode decides."
    AssumeResolves.WhenNoMana -> "As Normal, and also go on at once at their pauses while the table shows them no untapped mana " +
        "source, no mana in their pool and no ability they could activate."
    AssumeResolves.Always -> "Go on at once wherever the other player gets priority after something you did, assuming they let " +
        "things resolve; they can still respond later. After something they did, their own mode decides."
}

/**
 * The quick-actions row's toggles on engine state (I-D56 §14, TRN-059): in live play the response mode (a tap goes to
 * the next of Control, Normal and Quick), "Assume resolves" (a tap goes to the next of Never, Normal, When they have no
 * mana and Always) and "Autotap mana"; in catch-up playback (HIS-095) the same three, Assume resolves greyed.
 * A new response mode applies from the next point on: the stop the player stands at stays ([GameDevice.settings]).
 */
@Composable
fun RowScope.EngineToggleCells(routes: EngineTableRoutes, enabled: Boolean) {
    val game = routes.engine ?: return
    val shown = game.shown ?: return
    val settings = shown.settings
    // Each change is made on the settings as they are at the tap (a second tap before the row is drawn again goes on from
    // the first's)
    fun set(change: (PlaySettings) -> PlaySettings) { game.settings(change(game.shown?.settings ?: settings)); routes.pump() }
    val autotap = Triple("Autotap mana", "Autotap\nmana", Icons.Outlined.WaterDrop) to
        ({ set { it.copy(autotap = !it.autotap) } } to settings.autotap)
    // TRN-059, HIS-095 (owner 2026-11-07, report WDKHSB): catch-up playback's response mode cell is live play's (name,
    // colour, a tap going on to the next mode), so the mode it stops by always shows
    val mode = Triple("Response mode", "${settings.mode.label()}\n(mode)", Icons.Outlined.Tune) to
        ({ set { it.copy(mode = MODES[(MODES.indexOf(it.mode) + 1) % MODES.size]) } } to null)
    // TRN-059 (owner playtest 2026-11-12, the always-show rule): the three cells always show in the same places; in catch-up
    // playback, where the device assumes nothing, Assume resolves stays greyed (a tap shakes it, holding it says why)
    val assumeIdle = shown.behind
    val cells = listOf(
        mode,
        // TRN-059: a tap goes on to the next of Never, Normal, When they have no mana and Always
        Triple("Assume resolves setting", "Assume:\n${settings.assume.short()}", Icons.Outlined.FastForward) to
            ({ set { it.assuming(it.assume.next) } } to null),
        autotap)
    for ((what, action) in cells) key(what.first) {
        val (name, label, icon) = what
        val (toggle, on) = action
        val choice = name == "Assume resolves setting"
        val greyed = choice && assumeIdle
        // TRN-059 (owner playtest 2026-11-06): the mode and Assume cells always fill in their choice's colour; the
        // toggles fill violet when on
        val fillColor = when {
            name == "Response mode" -> settings.mode.color()
            choice -> settings.assume.color()
            else -> AutomatumViolet
        }
        val filled = (on == true || on == null) && !greyed
        val tooltip = when (name) {
            "Response mode" -> "Response mode: ${settings.mode.label()}. ${settings.mode.meaning()} Tap for the next mode; it applies from the next point on."
            "Assume resolves setting" -> "Assume resolves: ${settings.assume.label()}. ${settings.assume.meaning()} Tap for the next choice."
            else -> "Autotap mana sources: casting pays by itself, with mana in your pool first"
        }
        val control = MenuControl(name, enabled && !greyed, selected = on == true, color = fillColor,
            refused = if (greyed) assumeIdleReason(settings.assume) else null) { toggle() }
        RingPress(control, Modifier.weight(1f).height(48.dp).semantics {
                stateDescription = if (choice) settings.assume.label() else if (on == null) settings.mode.label() else if (filled) "On" else "Off" },
            fillColor, fill = if (filled) fillColor else Color.White, role = if (on == null) Role.Button else Role.Switch,
            tooltip = tooltip) {
            val ink = (if (filled) Color.White else MenuInk).let { it.copy(alpha = if (enabled && !greyed) 1f else .45f) }
            Column(Modifier.fillMaxSize().padding(horizontal = 3.dp), horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    Icon(icon, null, Modifier.size(16.dp), tint = ink)
                    if (on != null) Icon(if (filled) Icons.Outlined.CheckCircle else Icons.Outlined.RadioButtonUnchecked, null,
                        Modifier.size(13.dp), tint = ink)
                }
                Text(if (on == null && !choice) settings.mode.label() else label, color = ink, fontSize = 11.sp, lineHeight = 12.sp, letterSpacing = 0.sp,
                    maxLines = 2, textAlign = TextAlign.Center, fontWeight = if (filled) FontWeight.Bold else FontWeight.Medium,
                    overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/** TRN-059: what holding the greyed Assume resolves cell says in catch-up playback, where the device assumes nothing. */
fun assumeIdleReason(assume: AssumeResolves): String = "Assume resolves: ${assume.label()}. Not used while you catch up on " +
    "moves already made; it works again once you're caught up."

/**
 * The async play panels of a game on engine state (I-D56 §14), docked above the hand like every Automatum panel: the
 * waiting bar with its Assume button, the Assume blockers screen, the decision-stop line, the
 * result line, the assumer's indicator, the notes, and the undo dialogs.
 */
@Composable
fun EngineAsyncHost(toolkit: TableToolkitController, routes: EngineTableRoutes, play: TableBoard, scene: TableScene?,
    names: Map<String, String>, presence: ReceivedPresence?, summary: GameSummary, peekScroll: androidx.compose.foundation.ScrollState? = null,
    peekSwipe: InspectSwipe? = null) {
    val game = routes.engine ?: return
    val shown = game.shown ?: return
    val status = shown.status
    val opponent = names[shown.opponent.value] ?: "The other player"
    // Timeline mode (HIS-004) shows an earlier board, view-only: no async panels over it, only an incoming request
    val reviewing = routes.review != null
    // TRN-066, TRN-068: the wait is the band under the other player's hand (TableRuntimeHooks.waitingLine) with its Assume or
    // Cancel in the step arrow's place, and the unconfirmed mark sits at the arrow's corner (QuickActionStrip): nothing
    // here covers the board (CMB-040: assumed blocks are placed on the lanes)
    // HIS-095 (owner playtest 2026-11-04 #1): a stop at the other player's assumption about you looks like any point
    // where you may act: no words over the board say it was assumed
    // HIS-110: a reveal, the other player's or your own, stops the view until dismissed, before anything else docked above the hand
    // (owner, 2026-11-12) OK closes it at once on this phone, before the engine thread has published the dismissal
    val reveal = routes.openReveal().takeIf { !reviewing }
    if (reveal != null) AboveHand(handRow(scene, routes.viewer)) {
        // HIS-110: named by who revealed (Bob, when Alice's spell makes him reveal his hand), as the card's text has it
        RevealPanel(play, routes.viewer, reveal, if (reveal.by.value == routes.viewer) "You" else names[reveal.by.value] ?: opponent,
            reveal.left?.owner?.let { names[it.value] } ?: opponent) {
            AppLog.info("game", "Reveal OK pressed: ${reveal.by.value.take(8)} revealed ${reveal.cards.map { it.value }} in ${reveal.event}; " +
                "the panel closes at once")
            routes.dismissReveal(reveal)
        }
    }
    // HIS-112: the other player's play (or a trigger of the viewer's own) large in the middle of the table for the
    // "Show plays and combat" time, then to the Stack; the view holds meanwhile. Timeline mode ends it
    val playbackPreferences = rememberHistoryPlaybackPreferences(routes.viewer)
    val showMillis by playbackPreferences.showPlaysState()
    LaunchedEffect(game, showMillis > 0) { game.showPlays(showMillis > 0) }
    // HIS-112 (owner playtest 2026-11-11): the next play shows the moment the one before lands, not once the device's
    // dismissal of that one is back from the engine thread: the plays this table ended are passed over until the device's
    // list drops them
    val endedShows = remember(game) { mutableStateListOf<GameDevice.PlayShow>() }
    SideEffect { if (endedShows.any { it !in shown.moments }) endedShows.retainAll(shown.moments.toSet()) }
    // HIS-112 (owner 2026-11-12): the plays put on the Stack in one go show together: they fly in, then show top first
    val shows = playShowGroup(shown.moments, endedShows)
    val show = shows.firstOrNull()
    val showPace = remember(game) { PlayShowPace() }
    val showTapScope = rememberCoroutineScope()
    if ((show != null || shown.combatHold != null) && reviewing) LaunchedEffect(show, shown.combatHold) { game.dismissShows(); routes.pump() }
    // TRN-074: a play and the turn banner never overlap: one follows the other
    if (show != null && !reviewing && scene != null && !routes.banners.holdsPlays) key(show) {
        val peekHeight = holdPreviewScreenHeight()
        PlayShowOverlay(play, scene, routes.viewer, shows, names, showMillis, routes.viewPause, tapScope = showTapScope, pace = showPace,
            // TBL-104, TBL-131: holding it inspects it as holding the Stack's entry does: scrolled and pinned by the same finger
            inspect = { held, holding ->
                if (holding) {
                    peekScroll?.let { it.dispatchRawDelta(-it.value.toFloat()) }
                    peekSwipe?.begin()
                    toolkit.peek("entry:${held.entry}")
                } else if (peekSwipe?.released() != true) toolkit.peek(null)
            },
            moved = { dx, dy, step ->
                if (peekSwipe?.moved(dx, dy) != true) peekScroll?.holdPreviewScroll(step, peekHeight)
            }) { ended -> endedShows += ended; game.dismissShow(ended); routes.pump() }
    }
    if (!reviewing) when (status) {
        is GameDevice.Status.Playing -> EnginePlayback(routes)
        else -> if (reveal != null) Unit else when (status) {
        // TRN-065 (owner 2026-11-09): the result in a dialog in the middle of the screen until both OKs stand: the player whose
        // own action made it final (a concession, an accepted suggestion) has given theirs with it and waits for the other's
        // here, with no button (nothing to take back); both go to the lobby once both OKs stand, as on a result the rules gave
        is GameDevice.Status.Over -> if (!(status.final && status.ownOk)) {
            val won = (status.result as? arcanum.engine.setup.GameResult.Won)?.winner?.value
            val causes = shown.engine.players.mapNotNull { p -> p.loss?.let { org.asyncttwiz.protocol.lossCause(it) }?.let { p.id.value to it } }.toMap()
            val text = GameEndText.of(won, status.ending, causes, routes.viewer, names)
            val ended = status.final && status.endedBy == PlayerId(routes.viewer)
            GameEndDialog(text, if (status.ownOk || ended) "Waiting for $opponent to acknowledge" else null,
                // An open undo request freezes the game (HIS-100): the answer comes first
                enabled = shown.request == null,
                takeBack = if (status.ownOk && !ended) ({
                    AppLog.info("game", "Result OK taken back at the table: ${status.ending}")
                    game.takeBackResult { routes.pump() }
                }) else null,
                ok = if (ended) null else ({
                    AppLog.info("game", "Result OK pressed at the table: ${status.ending}, final ${status.final}")
                    if (status.final) { routes.acknowledgeResult(); routes.pump() } else game.confirmResult { routes.pump() }
                }))
        }
        GameDevice.Status.Reload -> AboveHand(handRow(scene, routes.viewer)) { EngineBar("Loading the game again…", "loading line") {} }
        else -> {}
        }
    }
    // Incoming undo request (§10): Accept / Deny; the game is frozen meanwhile
    val request = shown.request
    if (request != null && request.by != routes.viewer) EngineRequestDialog(routes, opponent, summary)
    routes.rewind?.let { EngineRewindDialog(routes, it, names, opponent, summary) }
    // Notes (§8, §10): told once
    val recordShown = LocalShownTexts.current
    LaunchedEffect(game.version) {
        while (game.notes.isNotEmpty()) {
            when (val note = game.notes.removeAt(0)) {
                // HIS-097 (problem report DY6VCM): names the first real action of yours taken back, if any
                is GameDevice.Note.Branched -> routes.tell(EngineEventText.branched(names[note.by.value] ?: opponent, note.discarded,
                    note.actions, game.shown?.engine, routes.viewer))
                // HIS-099, §29.4: the other player's undo of events you had seen: your board goes back the way it came,
                // with "<name> undid: <action>" fading at the top (the transition's, EngineTableScreen)
                is GameDevice.Note.TakenBack -> {}
                is GameDevice.Note.RequestNeeded -> routes.rewind = EngineTableRoutes.RewindChoice(note.target)
                // SYS-012 (owner, 2026-11-08): any other refusal of your own action shakes what it was made on, never a
                // silent snap back (problem report N7FU6Y); the board shows where the game is
                is GameDevice.Note.Refused -> refusedText(note.reason)?.let(routes::tell) ?: run {
                    toolkit.refusedShake((note.on as? arcanum.engine.state.ObjectId.Card)?.card?.value)
                    recordShown("$REFUSAL_SHAKEN ${note.reason}")
                }
            }
        }
    }
    routes.note?.let { text ->
        RecordShown(text)
        LaunchedEffect(text) { delay(6_000); if (routes.note == text) routes.noteDone() }
        Box(Modifier.fillMaxWidth().padding(top = 56.dp), contentAlignment = Alignment.TopCenter) {
            Surface(shape = RoundedCornerShape(8.dp), color = Color(0xff2f3a37), shadowElevation = 6.dp,
                modifier = Modifier.padding(horizontal = 24.dp).clickable { routes.noteDone() }.testTag("engine-note")) {
                CardText(text, Modifier.padding(horizontal = 14.dp, vertical = 10.dp), color = Color.White,
                    style = androidx.compose.ui.text.TextStyle(fontSize = 14.sp))
            }
        }
    }
}

/** The one-line panels docked above the hand (the loading line): [text] and [buttons], with the eye at the left
 * that hides and shows it like every Automatum panel (STK-044); [what] names it for the eye. */
@Composable
private fun EngineBar(text: String, what: String, buttons: @Composable RowScope.() -> Unit) {
    var hidden by remember(text) { mutableStateOf(false) }
    Box(Modifier.testTag("engine-bar")) {
        AutomatumDockPanel(text, hidden, { hidden = !hidden }, what, buttons = buttons) {
            Text(text, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 17.sp)
        }
    }
}

/** HIS-099: [board] drawn small and view-only: the whole table laid out at a phone's size ([landscape]: turned) and
 * scaled down to [height] (a small table would lay itself out differently); [dim] while a newer one is worked out. */
@Composable
private fun BoardPreview(summary: GameSummary, board: TableBoard, viewer: String, landscape: Boolean = false,
    height: Float = PREVIEW_HEIGHT, dim: Boolean = false) {
    val tableWidth = if (landscape) PREVIEW_TABLE_HEIGHT else PREVIEW_TABLE_WIDTH
    val tableHeight = if (landscape) PREVIEW_TABLE_WIDTH else PREVIEW_TABLE_HEIGHT
    val scale = height / tableHeight
    Box(Modifier.fillMaxWidth().height(height.dp).testTag("undo-board").semantics { contentDescription = "The board it goes back to" },
        contentAlignment = Alignment.TopCenter) {
        Box(Modifier.size((tableWidth * scale).dp, height.dp).clipToBounds()
            .border(1.dp, MenuBorder, RoundedCornerShape(4.dp)).graphicsLayer { alpha = if (dim) .6f else 1f }) {
            Box(Modifier.wrapContentSize(Alignment.TopStart, unbounded = true).requiredSize(tableWidth.dp, tableHeight.dp)
                .graphicsLayer { scaleX = scale; scaleY = scale; transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0f, 0f) }) {
                TableSurface(summary, board, viewer, landscape, Modifier.fillMaxSize(), connected = false,
                    runtime = TableRuntimeHooks(liveView = false, controlsConnected = false))
            }
        }
    }
}

private const val PREVIEW_HEIGHT = 300f
/** The Undo dialog's board in landscape: a turned table, as wide as the dialog allows. */
private const val PREVIEW_HEIGHT_LANDSCAPE = 120f
private const val PREVIEW_TABLE_WIDTH = 412f
private const val PREVIEW_TABLE_HEIGHT = 892f
/** HIS-099: how long the Undo dialog's choice rests before its board is worked out (dragging through events works out none). */
const val UNDO_PREVIEW_DELAY_MS = 250L
/** HIS-111, HIS-099 (owner 2026-11-07): how close to the oldest event held the timeline's cursor (or the Undo dialog's
 * choice) comes before the next older page of history is asked for. */
const val OLDER_HISTORY_MARGIN = 12

private const val QUICK_PLAYBACK_MS = 120L

/** HIS-021 (§29.3): the playback delay at Card move speed 1×: about a third of the old 1 s default. */
const val PLAYBACK_DELAY_MS = 330L

/** HIS-021 (§29.3): the playback delay at the Card move speed [multiplier] (TBL-038): faster moves, shorter delay. */
fun playbackDelay(multiplier: Float): Long = (PLAYBACK_DELAY_MS / cardMoveMultiplier(multiplier)).toLong()

/**
 * HIS-021 (§27, §29.3, §29.6): how long playback waits after showing [shown] before the next event: nothing after an
 * event that changed nothing you can see ([EnginePlaybackPace.quiet]: only the step indicator moved; with playback's Full
 * control on, [mode], the delay too, except a pass made right after the same player's own action, [previous], HIS-104), a
 * short moment after the other player's staging and early answers, else the playback [delay].
 */
fun playbackWait(shown: GameEvent?, delay: Long, mode: ResponseMode, previous: GameEvent? = null): Long = when {
    shown == null -> 0L
    EnginePlaybackPace.quiet(shown) -> if (mode == ResponseMode.FullControl && !EnginePlaybackPace.passAfterOwnAction(shown, previous)) delay else 0L
    shown.engine == null && shown.table.all { it is TableChange.AttackDraft || it is TableChange.BlockDraft ||
        it is TableChange.EarlyAnswered } -> QUICK_PLAYBACK_MS
    else -> delay
}

/**
 * Playback (§7, §27, §29): the other player's actions shown one at a time; each event that changes something you can see
 * keeps its animation and the playback delay after it, the others (passes, steps where nothing happened, an empty
 * combat) go by at once unless Control is on.
 */
@Composable
private fun EnginePlayback(routes: EngineTableRoutes) {
    val game = routes.engine ?: return
    val multiplier by rememberCardMovePreferences(routes.viewer).multiplierState()
    LaunchedEffect(game.version) {
        val shown = game.shown ?: return@LaunchedEffect
        // HIS-021 (owner, 2026-11-08): a finger held on the table pauses playback between events; a branch's take-back goes
        // on (HIS-106)
        val pause = routes.viewPause.takeUnless { shown.takingBack }
        // HIS-097: a branch's playback of the other player's new events starts once the rewind back to the branch point
        // has played
        if (shown.takingBack) snapshotFlow { routes.motionBusy() }.first { !it }
        val last = shown.held.lastOrNull()?.takeIf { it.id == shown.localHead }
        // HIS-112, HIS-021: an event whose plays were shown already had its moment: no playback delay after it
        val wait = if (last != null && last.id == shown.showDone) 0L
            else playbackWait(last, playbackDelay(multiplier), shown.settings.mode, shown.held.getOrNull(shown.held.size - 2))
        if (pause != null) pause.wait(wait) else if (wait > 0) delay(wait)
        // Web client step 4d: the callback form (the device may be in the browser's worker; Android answers at once)
        if (game.shown?.status is GameDevice.Status.Playing) game.step { routes.pump() }
    }
}

/**
 * HIS-021 (§27, §29.6, owner 2026-11-04): which events playback lets go by at once. An event is quiet when it is only
 * priority passes and declarations of no attackers or no blockers, and what they led to changed nothing you can see: no
 * card moved, tapped, turned or changed, no life, counters or damage, no Stack entry added or resolved, no attacker or
 * block (an empty combat beginning or ending is nothing), no new turn; only the step indicator moves.
 */
object EnginePlaybackPace {
    fun quiet(event: GameEvent): Boolean {
        val part = event.engine ?: return false
        if (event.table.isNotEmpty()) return false
        if (part.steps.any { it.input?.let(::empty) == false }) return false
        return part.changes.none(::visible)
    }

    /**
     * HIS-104 (owner 2026-11-07: a manual resolve is indistinguishable): whether [event] is a player's own pass made right
     * after their own action [previous] (a cast, an activation, not a pass or an assumption), as a player in Control
     * resolving by hand after their cast: a lower mode would have passed by itself inside that action's event, so playback
     * gives it no delay of its own.
     */
    fun passAfterOwnAction(event: GameEvent, previous: GameEvent?): Boolean {
        if (previous == null || event.parentId != previous.id || event.author != previous.author) return false
        if (event.assumed != null || previous.assumed != null) return false
        val acting = previous.engine?.steps?.firstOrNull { it.input != null }?.input ?: return false
        return event.engine?.steps?.firstOrNull { it.input != null }?.input is Input.PassPriority && acting !is Input.PassPriority
    }

    private fun empty(input: Input): Boolean = input is Input.PassPriority ||
        (input is Input.DeclareAttackers && input.attacks.isEmpty()) || (input is Input.DeclareBlockers && input.blocks.isEmpty())

    /** Who attacks whom and who blocks whom: an empty combat (none, or one without attackers) shows nothing. */
    private fun fights(combat: arcanum.engine.state.CombatState?) =
        combat?.attackers.orEmpty().map { it.id to it.target } to combat?.blockers.orEmpty().map { it.id to it.attackers }

    private fun visible(change: arcanum.engine.change.Change): Boolean = when (change) {
        is arcanum.engine.change.Change.ZoneChange, is arcanum.engine.change.Change.Created, is arcanum.engine.change.Change.Removed,
        is arcanum.engine.change.Change.Status, is arcanum.engine.change.Change.Face, is arcanum.engine.change.Change.Counters,
        is arcanum.engine.change.Change.Damage, is arcanum.engine.change.Change.DamageMarked, is arcanum.engine.change.Change.Life,
        is arcanum.engine.change.Change.StackItemAdded, is arcanum.engine.change.Change.StackItemRemoved,
        is arcanum.engine.change.Change.Attached, is arcanum.engine.change.Change.ControlStart,
        is arcanum.engine.change.Change.Lost, is arcanum.engine.change.Change.Shuffle -> true
        is arcanum.engine.change.Change.Combat -> fights(change.before) != fights(change.after)
        is arcanum.engine.change.Change.Step -> change.before.number != change.after.number
        else -> false
    }
}

/** The incoming undo request (§10): the other player's message, what it takes back as far as the viewer has seen, Accept
 * or Deny; never how many actions go. */
@Composable
private fun EngineRequestDialog(routes: EngineTableRoutes, opponent: String, summary: GameSummary) {
    val game = routes.engine ?: return
    game.shownVersion
    val shownGame = game.shown ?: return
    // HIS-111: a point before what the device holds shows once its older history is in (the board is published again).
    // Web client step 4c: the request's view arrives through a callback (on Android at once, in the same composition)
    val answer = remember(game.version) {
        AnsweredValue<EngineGame.RequestView?>().also { a ->
            game.requestView(routes.viewer, shownGame.localHead, shownGame.request?.target ?: "", a::set)
        }
    }
    val preview = answer.value
    LaunchedEffect(game.version, answer.arrived && preview == null) { if (answer.arrived && preview == null) game.loadForRequest() }
    ScreenDialog({}, title = "$opponent asks to undo", actions = {
        DialogCancelButton("Deny", { game.answerRequest(false); routes.pump() }, Modifier.testTag("request-deny"))
        DialogMainButton("Accept", { game.answerRequest(true); routes.pump() }, Modifier.testTag("request-accept"), icon = "ready")
    }) {
        val message = preview?.message.orEmpty()
        if (message.isNotBlank()) Text("“$message”", style = MaterialTheme.typography.bodyLarge)
        val turn = preview?.turn
        Text("The game goes back to " + (turn?.let { if (it.number == 0) "the opening" else "turn ${it.number}, ${org.asyncttwiz.automatum.EngineProjection.position(it.step).label}" } ?: "an earlier point") +
            if (preview?.unseen == true) ", and actions you haven't seen yet are taken back." else ".", style = MaterialTheme.typography.bodyMedium)
        // HIS-099: the board it goes back to, as far as the viewer has seen (their local head's when the target is past it):
        // the table's own drawing laid out at a phone's size and drawn smaller; nothing on it acts
        preview?.board?.let { BoardPreview(summary, it, routes.viewer) }
        Text("The game waits until you answer.", style = MaterialTheme.typography.bodySmall)
    }
}

/** One point of the Undo dialog (HIS-099): the timeline [event] ([shown] as the timeline shows it); choosing it goes back
 * to just before it, [target]. */
class UndoPoint(val event: GameEvent, val shown: HistoryTimelineEvent) {
    val target: String get() = requireNotNull(event.parentId)
}

/**
 * The Undo dialog's points (HIS-099): exactly the timeline's events ([timeline], HIS-005: the player's own and the other
 * player's, no passes, HIS-101) on the main line up to the local head, older history loaded included (owner 2026-11-07:
 * back to the game's first event), oldest first; the game's first event (the server's start, nothing before it) is no
 * point.
 */
fun undoPoints(shown: EngineGame.Shown, timeline: List<HistoryTimelineEvent>): List<UndoPoint> {
    val held = HashMap<String, GameEvent>(shown.older.size + shown.held.size)
    shown.older.associateByTo(held) { it.id }
    shown.held.associateByTo(held) { it.id }
    return timeline.mapNotNull { t -> held[t.id]?.takeIf { e -> e.engine?.steps.orEmpty().none { it.input is Input.StartGame } }
        ?.let { UndoPoint(it, t) } }
}

/**
 * The Undo dialog (§10, HIS-099): the board at the chosen point on top (worked out once the choice rests a moment,
 * [UNDO_PREVIEW_DELAY_MS]), under it the timeline's strip of the points with the chosen one in the centre, dragged left
 * and right (or tapped) to choose, and its text; then Undo: done at once when it is free, else, with an optional message,
 * sent to the other player as an undo request ("Ask <name>").
 */
@Composable
private fun EngineRewindDialog(routes: EngineTableRoutes, choice: EngineTableRoutes.RewindChoice, names: Map<String, String>, opponent: String,
    summary: GameSummary) {
    val game = routes.engine ?: return
    // HIS-111: read as snapshot state, so the points follow every board published (older history coming in), not only the
    // dialog's own content
    game.shownVersion
    val published = game.shown ?: return
    val timeline = game.timeline.events(game, names)
    val points = remember(published.version, timeline) { undoPoints(published, timeline) }
    // The point opened at: the one just after the default target (HIS-084), else the newest. Until the player chooses
    // one it follows the boards published: a target not among the points yet (the game loading again after an undo shown
    // at once that the server found needs approval, SYS-025; or in older history) is sought, its older history asked
    // for, and Undo waits until it is chosen
    var chosen by remember(choice) { mutableStateOf<String?>(null) }
    val opening = points.lastOrNull { it.target == choice.target }?.event?.id
    val seeking = chosen == null && opening == null && choice.target != null && (published.needsReload || !published.olderComplete)
    LaunchedEffect(seeking, points.size, published.needsReload) { if (seeking && !published.needsReload) game.loadOlder() }
    val point = points.firstOrNull { it.event.id == (chosen ?: opening) } ?: points.lastOrNull()
    val target = point?.target
    var message by remember(choice) { mutableStateOf("") }
    // HIS-099: the board there and whether the other player must approve, worked out once the choice rests (at once when
    // the dialog opens); the last one stays meanwhile, dimmed
    var preview by remember(choice) { mutableStateOf<Pair<String, EngineGame.UndoPreview>?>(null) }
    LaunchedEffect(target, game.version) {
        val t = target ?: return@LaunchedEffect
        if (preview != null) delay(UNDO_PREVIEW_DELAY_MS)
        // HIS-005: a point whose board needs older history keeps the last board, dimmed, until its page is in (the
        // board is published again then: this runs again)
        // Web client step 4c: through a callback (on Android at once, without suspending)
        val next = kotlinx.coroutines.suspendCancellableCoroutine<EngineGame.UndoPreview> { c -> game.undoPreview(t) { c.resume(it) } }
        preview = if (next.loading) t to EngineGame.UndoPreview(preview?.second?.board, null, loading = true) else t to next
    }
    // HIS-099 (owner 2026-11-07: back to the game's first event): a choice near the oldest point held asks for older history
    val chosenIndex = points.indexOf(point)
    LaunchedEffect(chosenIndex, points.size, published.olderComplete) {
        if (point != null && chosenIndex < OLDER_HISTORY_MARGIN && !published.olderComplete) game.loadOlder()
    }
    val current = preview?.takeIf { it.first == target && !it.second.loading }?.second
    val needsRequest = (current ?: preview?.second?.takeIf { !it.loading })?.needsRequest
    val landscape = screenWidthDp() > screenHeightDp()
    ScreenDialog({ AppLog.info("undo", "Undo dialog dismissed"); routes.rewind = null }, title = "Undo", actions = {
        DialogCancelButton("Cancel", { AppLog.info("undo", "Undo dialog cancelled"); routes.rewind = null })
        DialogMainButton(if (needsRequest == true) "Ask $opponent" else "Undo", {
            val t = target ?: return@DialogMainButton
            val text = message.trim()
            game.undo(t) { u ->
                if (u is GameDevice.Undo.Request) game.requestUndo(u.target, text)
                routes.rewind = null
                routes.pump()
            }
        }, Modifier.testTag("rewind-confirm"), enabled = target != null && current != null && !seeking && !published.needsReload,
            icon = if (needsRequest == true) "send" else "withdraw")
    }) {
        if (point == null) {
            Text("Nothing to undo yet: your actions are still on their way.", style = MaterialTheme.typography.bodyMedium)
            return@ScreenDialog
        }
        val board = preview?.second?.board
        if (board != null) BoardPreview(summary, board, routes.viewer, landscape,
            if (landscape) PREVIEW_HEIGHT_LANDSCAPE else PREVIEW_HEIGHT, dim = current == null)
        else Box(Modifier.fillMaxWidth().height((if (landscape) PREVIEW_HEIGHT_LANDSCAPE else PREVIEW_HEIGHT).dp))
        // HIS-099: the timeline's strip of the points (its icons and separators), the chosen one in the centre
        HistoryTimeline(points.map { it.shown }, point.event.id, routes.viewer, {}, { chosen = it },
            Modifier.fillMaxWidth().testTag("undo-scroller"), loadingStart = game.historyLoading && !published.olderComplete)
        val lead: @Composable () -> Unit = {
            Text("Go back to just before", style = MaterialTheme.typography.bodySmall, color = Color(0xff66757a))
        }
        val text: @Composable () -> Unit = {
            Text(point.shown.description, Modifier.testTag("undo-point"), style = MaterialTheme.typography.bodyLarge, maxLines = 2)
        }
        // In landscape on one line, so the board, the strip and the text fit above the buttons
        if (landscape) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) { lead(); text() }
        else Column(verticalArrangement = Arrangement.spacedBy(2.dp)) { lead(); text() }
        if (needsRequest == true) {
            Text("$opponent has seen this or it showed you something new: they are asked first. The game waits meanwhile.",
                style = MaterialTheme.typography.bodySmall)
            ScreenTextField(message, { message = it.take(500) }, Modifier.fillMaxWidth().testTag("rewind-message"), placeholder = "Message (optional)")
        }
    }
}

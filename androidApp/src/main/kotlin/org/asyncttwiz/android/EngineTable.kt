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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DoneAll
import androidx.compose.material.icons.outlined.FastForward
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material.icons.outlined.RemoveModerator
import androidx.compose.material.icons.outlined.Shield
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
import kotlinx.coroutines.flow.first
import org.asyncttwiz.protocol.*
import org.asyncttwiz.protocol.game.AssumeResolves
import org.asyncttwiz.protocol.game.GameDevice
import org.asyncttwiz.protocol.game.GameEvent
import org.asyncttwiz.protocol.game.PlaySettings
import org.asyncttwiz.protocol.game.ResponseMode
import org.asyncttwiz.protocol.game.TableChange
import org.asyncttwiz.automatum.EngineDrafts

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
    fun keep(bottom: List<String>): Boolean
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
internal class EngineTableRoutes(val viewer: String, private val game: () -> EngineGame?, private val sent: () -> Unit,
    private val shown: () -> TableBoard?) : EngineRoutes {
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

    /** Timeline mode (HIS-004): the shown event the board is reviewed at; null in play mode. */
    var review by mutableStateOf<String?>(null)
    /** Timeline mode's Auto (HIS-013). */
    var reviewPlaying by mutableStateOf(false)

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
        when (val acted = g.assume(chosen)) {
            is GameDevice.Acted.Refused -> tell("These blocks can't be assumed: ${acted.reason}")
            else -> placed.clear()
        }
        pump()
    }

    /** Whether the table is still animating a published board's steps (the motion queue), for the take-back's playback
     * to wait for its rewind (HIS-097). Set by the table. */
    var motionBusy: () -> Boolean = { false }

    /** Enters timeline mode at [at] (HIS-004); playback running meanwhile waits paused for the centre text (HIS-006). */
    fun enterReview(at: String) {
        game()?.takeIf { it.shown?.status is GameDevice.Status.Playing }?.holdPlayback()
        reviewPlaying = false; review = at
    }
    fun leaveReview() { reviewPlaying = false; review = null }

    /** After an action on the engine game: what it sends goes out and the table shows its new board. */
    fun pump() = sent()

    private fun done(acted: GameDevice.Acted?): Boolean { sent(); return acted is GameDevice.Acted.Sent || acted == GameDevice.Acted.Confirmed || acted == GameDevice.Acted.Made }

    override fun send(inputs: List<Input>): String? {
        val g = game() ?: return "unavailable"
        val acted = g.send(inputs)
        sent()
        return when (acted) {
            is GameDevice.Acted.Sent, GameDevice.Acted.Confirmed, GameDevice.Acted.Kept, GameDevice.Acted.Made -> null
            is GameDevice.Acted.Refused -> acted.reason
        }
    }
    override fun pass(): Boolean = done(game()?.pass())
    override fun endTurn(): Boolean { val g = game() ?: return false; g.endTurn(); sent(); return true }
    override fun mulligan(): Boolean = done(game()?.mulligan())
    override fun keep(bottom: List<String>): Boolean = done(game()?.keep(bottom))

    override fun local(actions: List<PlayAction>, requestId: String?): Boolean {
        val g = game() ?: return false
        val play = shown() ?: return false
        // The device is read on the engine thread (I-D56 step D2); what goes out is sent from here after. Not on a board the
        // device has moved on from (switchreview #6)
        val done = g.onShown(false) { device -> local(g, device, play, actions, requestId) }
        sent()
        return done
    }

    private fun local(g: EngineGame, device: GameDevice, play: TableBoard, actions: List<PlayAction>, requestId: String?): Boolean {
        val places = mutableListOf<TableChange>()
        var staged: BoardState? = null
        for ((index, action) in actions.withIndex()) {
            when {
                action is PlayAction.MoveCard && action.source.containerId == action.destination.containerId &&
                    play.board.containers.any { it.id == action.source.containerId && it.zone == PlayZone.BATTLEFIELD } -> {
                    val position = action.destination.position ?: return false
                    val obj = device.model.engine.objects.keys.firstOrNull { (it as? ObjectId.Card)?.card?.value == action.cardId &&
                        device.model.engine.objects[it]?.zone == arcanum.api.Zone.Battlefield } ?: return false
                    places += TableChange.Placed(obj, device.model.table.placements[obj],
                        org.asyncttwiz.protocol.game.Placement(position.x, position.y))
                }
                action is PlayAction.Combat || (action is PlayAction.Mtg && action.action is MtgAction.SetRecord) -> {
                    val on = staged ?: play.board
                    // Each as the member of the batch the table worked it out as (its request id): a Place names the
                    // combat area its Open made (problem report XGN86Q: staging the first attacker was refused). Worked out on
                    // the board shown (engine-responsiveness R4a)
                    val id = requestId?.let { org.asyncttwiz.protocol.CommandIds.uuidV5(it, "member/$index") } ?: DeviceRandom.uuid()
                    staged = try { EngineStaging.apply(on, play.definitions, viewer, action, id) } catch (_: IllegalArgumentException) { return false }
                }
                else -> return false
            }
        }
        staged?.let { board ->
            // I-D54 §8: the staging and the attack choices are shared as table changes at the head; at a decision stop
            // behind it (playing back assumed blocks) they stay on the device and go out only as the declaration (§12)
            val drafts = EngineDrafts.changes(device.model, board, viewer)
            val acted = if (drafts.isEmpty()) null else g.table(drafts)
            if (device.session.behind || acted == GameDevice.Acted.Kept) g.stage(board)
            else if (acted is GameDevice.Acted.Refused) return false
            else g.unstage()
        }
        if (places.isNotEmpty()) g.table(places)
        return true
    }

    fun status(): GameDevice.Status? = game()?.shown?.status

    /** TRN-015, I-D56 §14: the step arrow acts only for the viewer: shown while they may act. */
    fun arrowShown(): Boolean = when (status()) {
        GameDevice.Status.YourMove, GameDevice.Status.Drafting, is GameDevice.Status.Stopped -> true
        else -> false
    }

    /** Whether the viewer may act on the table now (their move, or a decision stop in playback). */
    fun canAct(): Boolean = arrowShown()

    /** Whether an own action of the player's is still being worked out off the engine game (the main button's step or
     * declaration, a cast's step: [SharedFlowController.busy], [CastController.busy]), before it reaches it. Set by the
     * table. */
    var working: () -> Boolean = { false }
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
        return when (val u = g.undo()) {
            is GameDevice.Undo.Free, is GameDevice.Undo.Queued -> { sent(); true }
            is GameDevice.Undo.Request -> { sent(); rewind = RewindChoice(u.target); true }
            is GameDevice.Undo.Unavailable -> { AppLog.info("engine", "Undo not taken: ${u.reason}"); undoShake++; false }
        }
    }

    /** The work [working] watched is done (main thread, after its result was handed on): an Undo tapped meanwhile is made
     * now. */
    fun workDone() { if (undoAfterWork && !working()) { undoAfterWork = false; undo() } }

    /** Holding Undo opens the rewind dialog to pick any earlier point (§10). */
    fun holdUndo() { rewind = RewindChoice(game()?.shown?.undoTarget) }

    /** The other player's name, for the wait's words and the unconfirmed mark's tooltip (set by the table). */
    var opponentName: String = "The other player"

    /** A wait's own control on the main button (TRN-066, TRN-070): an Assume button, or Cancel for the viewer's own
     * undo request; holding it says [tooltip]. */
    class WaitAction(val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector, val tooltip: String? = null,
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
            GameDevice.Offer.AssumeResolves -> if (shown.engine.stack.isNotEmpty())
                WaitAction("Assume resolves", Icons.Outlined.DoneAll,
                    "Assume $name lets it resolve and go on; if they respond when they come back, the game goes back there") { g.assume(); pump() }
                else WaitAction("Assume no response", Icons.Outlined.DoneAll,
                    "Assume $name lets the game go on; if they act here when they come back, the game goes back there") { g.assume(); pump() }
            // CMB-040 (owner, 2026-11-05): the blocks placed on the lanes, assumed only by this button
            GameDevice.Offer.AssumeBlockers -> assuming()?.let { placed ->
                if (placed.blocks.isEmpty()) WaitAction("Assume no blockers", Icons.Outlined.RemoveModerator,
                    "Assume $name doesn't block and go on; if they block when they come back, the game goes back there. " +
                        "Tap or drag their creatures onto your attackers to assume blocks instead.") { assumeBlocks(g, placed) }
                else WaitAction("Assume blockers", Icons.Outlined.Shield,
                    "Assume $name blocks as placed on the lanes and go on; if they block differently when they come back, the game goes back there") {
                    assumeBlocks(g, placed) }
            }
            null -> if (status.reason == GameDevice.Wait.Request && shown.request?.by == viewer)
                WaitAction("Cancel request", Icons.Outlined.Close) { g.cancelRequest(); pump() } else null
        }
    }

    /** How many toggles the quick-actions row holds now ([EngineToggleCells]): live play's three, playback's two. */
    fun toggleCount(): Int = if (game()?.shown?.behind == true) 2 else 3

    /** The wait of [othersChoice], or null: the other player is choosing, declaring blockers or deciding on a mulligan. */
    private fun choiceWait(): GameDevice.Wait? = (status() as? GameDevice.Status.Waiting)?.reason
        ?.takeIf { it is GameDevice.Wait.Choosing || it == GameDevice.Wait.Blockers || it == GameDevice.Wait.Keep }

    override fun othersChoice(): Boolean = choiceWait() != null

    /** HIS-075: the event text's status while the other player makes a choice, in the wait's words (TRN-066: "Bob is
     * choosing · Ember Divination", "Bob is deciding on a mulligan"), or null when the game doesn't wait for one. */
    fun choosingText(play: TableBoard): String? = choiceWait()?.let { waitingText(it, play, opponentName) }

    /** The faint words over the battlefield while the other player must act (TRN-066), or null. */
    fun waitingLine(play: TableBoard): String? {
        val shown = game()?.shown ?: return null
        val status = shown.status as? GameDevice.Status.Waiting ?: return null
        return waitingText(status.reason, play, opponentName)
    }
}

/**
 * The wait's control on the main button (TRN-066, TRN-070, MTG Arena-like): the same shape and size, violet ink;
 * holding it says what it does.
 */
@Composable
internal fun EngineWaitArrow(action: EngineTableRoutes.WaitAction, modifier: Modifier) {
    ActionSquare(action.label, "", true, modifier.shadow(3.dp, AdvanceArrowShape)
        .background(Color.White, AdvanceArrowShape).border(1.dp, AutomatumViolet, AdvanceArrowShape).clip(AdvanceArrowShape)
        .testTag("engine-wait-action"),
        fill = Color.White, ink = AutomatumViolet, compactContent = true, gestureKey = action.label, tooltip = action.tooltip,
        icon = { MainButtonContent(icon = { Icon(action.icon, null, Modifier.size(20.dp), tint = LocalContentColor.current) },
            label = action.label) }, onClick = action.run)
}

/**
 * TRN-068: the unconfirmed mark, a small violet dot at the step arrow's corner while actions of yours wait for the other
 * player to see them (only you see it). A tap or hold says what it means.
 */
@Composable
internal fun EngineUnconfirmedMark(opponent: String, modifier: Modifier = Modifier) {
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

internal val LocalEngineRoutes = staticCompositionLocalOf<EngineTableRoutes?> { null }

/** The note for a refusal of the player's own action (SYS-012: never dropped silently), or null for none. */
internal fun refusedText(reason: String): String? = when (reason) {
    GameDevice.REVEALS_HIDDEN -> CastController.WAIT_TO_CATCH_UP
    "stale", "newLine" -> NOT_TAKEN
    GameDevice.DRAFT_DROPPED -> NOT_TAKEN
    // SYS-012: any other refusal of the player's own action is told too, never a silent snap back (problem report N7FU6Y)
    else -> NOT_ACCEPTED
}

/** A player's own action the game moved on from before it reached the server (SYS-012). */
internal const val NOT_TAKEN = "That wasn't taken: the game moved on first. The table shows where it is now."

/** A player's own action the game refused for another reason (SYS-012). */
internal const val NOT_ACCEPTED = "That wasn't taken: the game didn't accept it. The table shows where it is now."

/** The waiting bar's words (§14) for [wait]. */
internal fun waitingText(wait: GameDevice.Wait, play: TableBoard, opponent: String): String? = when (wait) {
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
internal fun ResponseMode.label(): String = when (this) {
    ResponseMode.FullControl -> "Full control"
    ResponseMode.Normal -> "Normal"
    ResponseMode.AutoSkip -> "Auto-skip"
}

internal fun ResponseMode.meaning(): String = when (this) {
    ResponseMode.FullControl -> "Stop wherever you get priority, also after your own spells."
    ResponseMode.Normal -> "Stop in your main phases and where the other player's spell, attack, blocks or end step lets you respond."
    ResponseMode.AutoSkip -> "As Normal, but go on by itself where you can't respond, and answer questions with only one answer."
}

private val MODES = listOf(ResponseMode.FullControl, ResponseMode.Normal, ResponseMode.AutoSkip)

/** "Assume resolves" (HIS-096): the choice's name, its short form for the quick action, and what it means. */
internal fun AssumeResolves.label(): String = when (this) {
    AssumeResolves.Never -> "Never"
    AssumeResolves.WhenNoMana -> "When they have no mana"
    AssumeResolves.Always -> "Always"
}

internal fun AssumeResolves.short(): String = when (this) {
    AssumeResolves.Never -> "Never"
    AssumeResolves.WhenNoMana -> "No mana"
    AssumeResolves.Always -> "Always"
}

internal fun AssumeResolves.meaning(): String = when (this) {
    AssumeResolves.Never -> "Wait at the other player's pauses, with Assume on the main button."
    AssumeResolves.WhenNoMana -> "Go on at once at the other player's pauses while the table shows them no untapped mana source, " +
        "no mana in their pool and no ability they could activate; otherwise wait."
    AssumeResolves.Always -> "Go on at once at the other player's pauses, assuming they let things resolve; they can still respond later."
}

/**
 * The quick-actions row's toggles on engine state (I-D56 §14, TRN-059): in live play the response mode (a tap goes to
 * the next of Full control, Normal and Auto-skip), "Assume resolves" (a tap goes to the next of Never, When they have no
 * mana and Always) and "Autotap mana"; in catch-up playback
 * (HIS-095) "Full control" (on: playback stops wherever you get priority) and "Autotap mana"; each private to the player.
 */
@Composable
internal fun RowScope.EngineToggleCells(routes: EngineTableRoutes, enabled: Boolean) {
    val game = routes.engine ?: return
    val shown = game.shown ?: return
    val settings = shown.settings
    // Each change is made on the settings as they are at the tap (a second tap before the row is drawn again goes on from
    // the first's)
    fun set(change: (PlaySettings) -> PlaySettings) { game.settings(change(game.shown?.settings ?: settings)); routes.pump() }
    val autotap = Triple("Autotap mana", "Autotap\nmana", Icons.Outlined.WaterDrop) to
        ({ set { it.copy(autotap = !it.autotap) } } to settings.autotap)
    val cells = if (shown.behind) listOf(
        Triple("Full control", "Full\ncontrol", Icons.Outlined.Tune) to
            ({ set { it.copy(playbackFullControl = !it.playbackFullControl) } } to settings.playbackFullControl),
        autotap)
    else listOf(
        Triple("Response mode", "${settings.mode.label()}\n(mode)", Icons.Outlined.Tune) to
            ({ set { it.copy(mode = MODES[(MODES.indexOf(it.mode) + 1) % MODES.size]) } } to null),
        // TRN-059: a tap goes on to the next of Never, When they have no mana and Always; filled unless Never
        Triple("Assume resolves setting", "Assume:\n${settings.assume.short()}", Icons.Outlined.FastForward) to
            ({ set { it.assuming(it.assume.next) } } to (settings.assume != AssumeResolves.Never)),
        autotap)
    for ((what, action) in cells) key(what.first) {
        val (name, label, icon) = what
        val (toggle, on) = action
        val filled = on == true
        val choice = name == "Assume resolves setting"
        val tooltip = when (name) {
            "Response mode" -> "Response mode: ${settings.mode.label()}. ${settings.mode.meaning()} Tap for the next mode."
            "Assume resolves setting" -> "Assume resolves: ${settings.assume.label()}. ${settings.assume.meaning()} Tap for the next choice."
            "Full control" -> "Full control: playback stops wherever you get priority; off, only where you could respond"
            else -> "Autotap mana sources: casting pays by itself, with mana in your pool first"
        }
        val control = MenuControl(name, enabled, selected = filled, color = AutomatumViolet) { toggle() }
        RingPress(control, Modifier.weight(1f).height(48.dp).semantics {
                stateDescription = if (on == null) settings.mode.label() else if (choice) settings.assume.label() else if (filled) "On" else "Off" },
            AutomatumViolet, fill = if (filled) AutomatumViolet else Color.White, role = if (on == null || choice) Role.Button else Role.Switch,
            tooltip = tooltip) {
            val ink = (if (filled) Color.White else MenuInk).let { it.copy(alpha = if (enabled) 1f else .45f) }
            Column(Modifier.fillMaxSize().padding(horizontal = 3.dp), horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    Icon(icon, null, Modifier.size(16.dp), tint = ink)
                    if (on != null && !choice) Icon(if (filled) Icons.Outlined.CheckCircle else Icons.Outlined.RadioButtonUnchecked, null,
                        Modifier.size(13.dp), tint = ink)
                }
                Text(if (on == null) settings.mode.label() else label, color = ink, fontSize = 11.sp, lineHeight = 12.sp, letterSpacing = 0.sp,
                    maxLines = 2, textAlign = TextAlign.Center, fontWeight = if (filled) FontWeight.Bold else FontWeight.Medium,
                    overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/**
 * The async play panels of a game on engine state (I-D56 §14), docked above the hand like every Automatum panel: the
 * waiting bar with its Assume button, the Assume blockers screen, the decision-stop line, the
 * result line, the assumer's indicator, the notes, and the undo dialogs.
 */
@Composable
internal fun EngineAsyncHost(toolkit: TableToolkitController, routes: EngineTableRoutes, play: TableBoard, scene: TableScene?,
    names: Map<String, String>, presence: ReceivedPresence?, summary: GameSummary) {
    val game = routes.engine ?: return
    val shown = game.shown ?: return
    val status = shown.status
    val opponent = names[shown.opponent.value] ?: "The other player"
    // Timeline mode (HIS-004) shows an earlier board, view-only: no async panels over it, only an incoming request
    val reviewing = routes.review != null
    // TRN-066, TRN-068: the wait is faint words over the battlefield (TableRuntimeHooks.waitingLine) with its Assume or
    // Cancel in the step arrow's place, and the unconfirmed mark sits at the arrow's corner (QuickActionStrip): nothing
    // here covers the board (CMB-040: assumed blocks are placed on the lanes)
    // HIS-095 (owner playtest 2026-11-04 #1): a stop at the other player's assumption about you looks like any point
    // where you may act: no words over the board say it was assumed
    if (!reviewing) when (status) {
        is GameDevice.Status.Playing -> EnginePlayback(routes)
        is GameDevice.Status.Over -> AboveHand(handRow(scene, routes.viewer)) {
            val won = (status.result as? arcanum.engine.setup.GameResult.Won)?.winner
            val words = when (won) { null -> "Draw"; PlayerId(routes.viewer) -> "You won"; else -> "You lost" }
            EngineBar(if (status.final) "$words · game over" else if (status.confirmable) words else "$words · waiting for $opponent to catch up", "result line") {
                if (!status.final) ChoiceButton("OK", Icons.Outlined.Check, enabled = status.confirmable, primary = true) {
                    game.confirmResult(); routes.pump() }
            }
        }
        GameDevice.Status.Reload -> AboveHand(handRow(scene, routes.viewer)) { EngineBar("Loading the game again…", "loading line") {} }
        else -> {}
    }
    // Incoming undo request (§10): Accept / Deny; the game is frozen meanwhile
    val request = shown.request
    if (request != null && request.by != routes.viewer) EngineRequestDialog(routes, opponent, summary)
    routes.rewind?.let { EngineRewindDialog(routes, it, names, opponent, summary) }
    // Notes (§8, §10): told once
    LaunchedEffect(game.version) {
        while (game.notes.isNotEmpty()) {
            when (val note = game.notes.removeAt(0)) {
                is GameDevice.Note.Branched -> routes.tell("${names[note.by.value] ?: opponent} " + when (note.discarded.assumed?.input) {
                    is Input.DeclareBlockers -> "blocked differently"
                    is Input.SetupAnswer, is Input.Answer -> "took a mulligan"
                    else -> "responded"
                } + " — your later actions were taken back")
                // HIS-099, §29.4: the other player's undo of events you had seen: your board goes back the way it came,
                // with "<name> undid: <action>" fading at the top (the transition's, EngineTableScreen)
                is GameDevice.Note.TakenBack -> {}
                is GameDevice.Note.RequestNeeded -> routes.rewind = EngineTableRoutes.RewindChoice(note.target)
                is GameDevice.Note.Refused -> refusedText(note.reason)?.let(routes::tell) ?: toolkit.dismissError()
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

/** The one-line panels docked above the hand (the result line, TRN-065): [text] and [buttons], with the eye at the left
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

/**
 * HIS-099: a small view-only board of [model] (the point an undo goes back to) as [viewer] sees it: the table's own
 * drawing laid out at a phone's size and drawn smaller; nothing on it acts.
 */
@Composable
private fun EngineBoardPreview(game: EngineGame, summary: GameSummary, model: org.asyncttwiz.protocol.game.GameModel, at: String, viewer: String) {
    val board = remember(model, at) { game.preview(model, at, viewer) } ?: return
    // The whole table laid out at a phone's size, drawn smaller (a small table would lay itself out differently)
    val scale = PREVIEW_HEIGHT / PREVIEW_TABLE_HEIGHT
    Box(Modifier.fillMaxWidth().height(PREVIEW_HEIGHT.dp).testTag("undo-board").semantics { contentDescription = "The board it goes back to" },
        contentAlignment = Alignment.TopCenter) {
        Box(Modifier.size((PREVIEW_TABLE_WIDTH * scale).dp, PREVIEW_HEIGHT.dp).clipToBounds()
            .border(1.dp, MenuBorder, RoundedCornerShape(4.dp))) {
            Box(Modifier.wrapContentSize(Alignment.TopStart, unbounded = true).requiredSize(PREVIEW_TABLE_WIDTH.dp, PREVIEW_TABLE_HEIGHT.dp)
                .graphicsLayer { scaleX = scale; scaleY = scale; transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0f, 0f) }) {
                TableSurface(summary, board, viewer, false, Modifier.fillMaxSize(), connected = false,
                    runtime = TableRuntimeHooks(liveView = false, controlsConnected = false))
            }
        }
    }
}

private const val PREVIEW_HEIGHT = 300f
private const val PREVIEW_TABLE_WIDTH = 412f
private const val PREVIEW_TABLE_HEIGHT = 892f

private const val QUICK_PLAYBACK_MS = 120L

/** HIS-021 (§29.3): the playback delay at Card move speed 1×: about a third of the old 1 s default. */
internal const val PLAYBACK_DELAY_MS = 330L

/** HIS-021 (§29.3): the playback delay at the Card move speed [multiplier] (TBL-038): faster moves, shorter delay. */
internal fun playbackDelay(multiplier: Float): Long = (PLAYBACK_DELAY_MS / cardMoveMultiplier(multiplier)).toLong()

/**
 * HIS-021 (§27, §29.3, §29.6): how long playback waits after showing [shown] before the next event: nothing after an
 * event that changed nothing you can see ([EnginePlaybackPace.quiet]: only the step indicator moved; with playback's Full
 * control on, [mode], the delay too), a short moment after the other player's staging and card places, else the playback [delay].
 */
internal fun playbackWait(shown: GameEvent?, delay: Long, mode: ResponseMode): Long = when {
    shown == null -> 0L
    EnginePlaybackPace.quiet(shown) -> if (mode == ResponseMode.FullControl) delay else 0L
    shown.engine == null && shown.table.all { it is TableChange.AttackDraft || it is TableChange.BlockDraft || it is TableChange.Placed ||
        it is TableChange.EarlyAnswered } -> QUICK_PLAYBACK_MS
    else -> delay
}

/**
 * Playback (§7, §27, §29): the other player's actions shown one at a time; each event that changes something you can see
 * keeps its animation and the playback delay after it, the others (passes, steps where nothing happened, an empty
 * combat) go by at once unless Full control is on.
 */
@Composable
private fun EnginePlayback(routes: EngineTableRoutes) {
    val game = routes.engine ?: return
    val multiplier by rememberCardMovePreferences(routes.viewer).multiplierState()
    LaunchedEffect(game.version) {
        val shown = game.shown ?: return@LaunchedEffect
        // HIS-097: a branch's playback of the other player's new events starts once the rewind back to the branch point
        // has played
        if (shown.takingBack) snapshotFlow { routes.motionBusy() }.first { !it }
        val last = shown.held.lastOrNull()?.takeIf { it.id == shown.localHead }
        val wait = playbackWait(last, playbackDelay(multiplier), shown.settings.playbackMode)
        if (wait > 0) delay(wait)
        if (game.shown?.status is GameDevice.Status.Playing) { game.step(); routes.pump() }
    }
}

/**
 * HIS-021 (§27, §29.6, owner 2026-11-04): which events playback lets go by at once. An event is quiet when it is only
 * priority passes and declarations of no attackers or no blockers, and what they led to changed nothing you can see: no
 * card moved, tapped, turned or changed, no life, counters or damage, no Stack entry added or resolved, no attacker or
 * block (an empty combat beginning or ending is nothing), no new turn; only the step indicator moves.
 */
internal object EnginePlaybackPace {
    fun quiet(event: GameEvent): Boolean {
        val part = event.engine ?: return false
        if (event.table.isNotEmpty()) return false
        if (part.steps.any { it.input?.let(::empty) == false }) return false
        return part.changes.none(::visible)
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
    val shownGame = game.shown ?: return
    val preview = remember(game.version) { game.read { it.requestPreview() } }
    ScreenDialog({}, title = "$opponent asks to undo", actions = {
        DialogCancelButton("Deny", { game.answerRequest(false); routes.pump() }, Modifier.testTag("request-deny"))
        DialogMainButton("Accept", { game.answerRequest(true); routes.pump() }, Modifier.testTag("request-accept"), icon = "ready")
    }) {
        val message = preview?.message.orEmpty()
        if (message.isNotBlank()) Text("“$message”", style = MaterialTheme.typography.bodyLarge)
        val turn = preview?.board?.engine?.turn
        Text("The game goes back to " + (turn?.let { if (it.number == 0) "the opening" else "turn ${it.number}, ${org.asyncttwiz.automatum.EngineProjection.position(it.step).label}" } ?: "an earlier point") +
            if (preview?.unseen == true) ", and actions you haven't seen yet are taken back." else ".", style = MaterialTheme.typography.bodyMedium)
        // HIS-099: the board it goes back to, as far as the viewer has seen (their local head's when the target is past it)
        preview?.let { EngineBoardPreview(game, summary, it.board, if (it.unseen) shownGame.localHead else shownGame.request?.target ?: "", routes.viewer) }
        Text("The game waits until you answer.", style = MaterialTheme.typography.bodySmall)
    }
}

/** The Undo dialog's list (§10, HIS-099): the actions shown to the player, newest first; §29.2: passes are implied, so
 * none is listed except the first pass of the player's own End turn ("You ended the turn"). */
internal fun undoChoices(shown: EngineGame.Shown): List<GameEvent> = shown.held.filter { e ->
    e.engine?.steps?.any { s -> s.input != null } == true && (!EngineEventText.pass(e) || e.id in shown.endTurns)
}.asReversed()

/** The text of an Undo dialog entry ([undoChoices]). */
internal fun undoChoiceText(event: GameEvent, shown: EngineGame.Shown, viewer: String, names: Map<String, String>): String =
    if (EngineEventText.pass(event)) "You ended the turn" else EngineEventText.of(event, shown.engine, viewer, names)

/**
 * The rewind dialog (§10): the main line up to the viewer's local head as the target picker (newest first), an optional
 * message, then Undo: done at once when it is free, else sent to the other player as an undo request.
 */
@Composable
private fun EngineRewindDialog(routes: EngineTableRoutes, choice: EngineTableRoutes.RewindChoice, names: Map<String, String>, opponent: String,
    summary: GameSummary) {
    val game = routes.engine ?: return
    val published = game.shown ?: return
    val shown = remember(game.version) { undoChoices(published) }
    var target by remember(choice) { mutableStateOf(choice.target) }
    var message by remember(choice) { mutableStateOf("") }
    val needsRequest = remember(target, game.version) { target?.let { game.undoNeedsRequest(it) } }
    ScreenDialog({ routes.rewind = null }, title = "Undo", actions = {
        DialogCancelButton("Cancel", { routes.rewind = null })
        DialogMainButton(if (needsRequest == true) "Ask $opponent" else "Undo", {
            val t = target ?: return@DialogMainButton
            when (val u = game.undo(t)) {
                is GameDevice.Undo.Request -> game.requestUndo(u.target, message.trim())
                else -> {}
            }
            routes.rewind = null
            routes.pump()
        }, Modifier.testTag("rewind-confirm"), enabled = target != null, icon = if (needsRequest == true) "send" else "withdraw")
    }) {
        Text(if (shown.isEmpty()) "Nothing to undo yet: your actions are still on their way." else "Go back to just before:", style = MaterialTheme.typography.bodyMedium)
        LazyColumn(Modifier.fillMaxWidth().heightIn(max = 280.dp)) {
            items(shown, key = { it.id }) { event ->
                val selected = event.parentId == target
                Row(Modifier.fillMaxWidth().heightIn(min = 44.dp).clickable { target = event.parentId }
                    .background(if (selected) AutomatumViolet.copy(alpha = .12f) else Color.Transparent).padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected, onClick = null)
                    Text(undoChoiceText(event, published, routes.viewer, names), Modifier.padding(start = 8.dp), fontSize = 14.sp)
                }
            }
        }
        // HIS-099: the board at the point it goes back to
        val board = remember(target, game.version) { target?.takeIf { !published.drafting }?.let { t -> game.read { runCatching { it.session.stateAt(t) }.getOrNull() } } }
        if (board != null) EngineBoardPreview(game, summary, board, target!!, routes.viewer)
        if (needsRequest == true) {
            Text("$opponent has seen this or it showed you something new: they are asked first. The game waits meanwhile.",
                style = MaterialTheme.typography.bodySmall)
            ScreenTextField(message, { message = it.take(500) }, Modifier.fillMaxWidth().testTag("rewind-message"), placeholder = "Message (optional)")
        }
    }
}

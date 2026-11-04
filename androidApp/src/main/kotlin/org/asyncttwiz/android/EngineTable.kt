package org.asyncttwiz.android

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.WaterDrop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import kotlinx.coroutines.delay
import org.asyncttwiz.protocol.*
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
     * own until declared, I-D56 §12). False for anything else (nothing changes the game by hand). */
    fun local(actions: List<PlayAction>): Boolean
}

/** The open table's engine routes and the async panels' own state (personal UI). */
internal class EngineTableRoutes(val viewer: String, private val game: () -> EngineGame?, private val sent: () -> Unit,
    private val shown: () -> PlayState?) : EngineRoutes {
    val engine: EngineGame? get() = game()

    /** The rewind dialog (hold Undo, or an undo that needs the other player's approval): its chosen target. */
    var rewind by mutableStateOf<RewindChoice?>(null)
    /** A note shown once (§8 branch note, §10 take-back note). */
    var note by mutableStateOf<String?>(null)
    /** Bumped when an Undo can't do anything: the button shakes (I-D46 b). */
    var undoShake by mutableIntStateOf(0)

    data class RewindChoice(val target: String?)

    /** Timeline mode (HIS-004): the shown event the board is reviewed at; null in play mode. */
    var review by mutableStateOf<String?>(null)
    /** Timeline mode's Auto (HIS-013). */
    var reviewPlaying by mutableStateOf(false)

    /** The Assume blockers screen opened from the waiting bar at this point (CMB-040), personal. */
    var blockersOpen by mutableStateOf<String?>(null)

    fun enterReview(at: String) { reviewPlaying = false; review = at }
    fun leaveReview() { reviewPlaying = false; review = null }

    /** After an action on the engine game: what it sends goes out and the table shows its new board. */
    fun pump() = sent()

    private fun done(acted: GameDevice.Acted?): Boolean { sent(); return acted is GameDevice.Acted.Sent || acted == GameDevice.Acted.Confirmed }

    override fun send(inputs: List<Input>): String? {
        val g = game() ?: return "unavailable"
        val acted = g.send(inputs)
        sent()
        return when (acted) {
            is GameDevice.Acted.Sent, GameDevice.Acted.Confirmed, GameDevice.Acted.Kept -> null
            is GameDevice.Acted.Refused -> acted.reason
        }
    }
    override fun pass(): Boolean = done(game()?.pass())
    override fun endTurn(): Boolean { val g = game() ?: return false; g.endTurn(); sent(); return true }
    override fun mulligan(): Boolean = done(game()?.mulligan())
    override fun keep(bottom: List<String>): Boolean = done(game()?.keep(bottom))

    override fun local(actions: List<PlayAction>): Boolean {
        val g = game() ?: return false
        val play = shown() ?: return false
        val places = mutableListOf<TableChange>()
        var staged: PlayState? = null
        for (action in actions) {
            when {
                action is PlayAction.MoveCard && action.source.containerId == action.destination.containerId &&
                    play.board.containers.any { it.id == action.source.containerId && it.zone == PlayZone.BATTLEFIELD } -> {
                    val position = action.destination.position ?: return false
                    val obj = g.device.model.engine.objects.keys.firstOrNull { (it as? ObjectId.Card)?.card?.value == action.cardId &&
                        g.device.model.engine.objects[it]?.zone == arcanum.api.Zone.Battlefield } ?: return false
                    places += TableChange.Placed(obj, g.device.model.table.placements[obj],
                        org.asyncttwiz.protocol.game.Placement(position.x, position.y))
                }
                action is PlayAction.Combat || (action is PlayAction.Mtg && action.action is MtgAction.SetRecord) -> {
                    val on = staged ?: play
                    val command = GameCommand(DeviceRandom.uuid(), GameScope(on.gameId, playId = on.id), GameAction.Play(action),
                        DeviceRandom.uuid(), "1")
                    staged = GameplayRules.preview(on, viewer, command).state ?: return false
                }
                else -> return false
            }
        }
        staged?.let { s ->
            // I-D54 §8: the staging and the attack choices are shared as table changes at the head; at a decision stop
            // behind it (playing back assumed blocks) they stay on the device and go out only as the declaration (§12)
            val board = s.board.copy(engine = play.board.engine)
            val drafts = EngineDrafts.changes(g.device.model, board, viewer)
            val acted = if (drafts.isEmpty()) null else g.table(drafts)
            if (g.device.session.behind || acted == GameDevice.Acted.Kept) g.stage(s.copy(board = board))
            else if (acted is GameDevice.Acted.Refused) { sent(); return false }
            else g.unstage()
        }
        if (places.isNotEmpty()) g.table(places)
        sent()
        return true
    }

    fun status(): GameDevice.Status? = game()?.device?.status

    /** TRN-015, I-D56 §14: the step arrow acts only for the viewer: shown while they may act. */
    fun arrowShown(): Boolean = when (status()) {
        GameDevice.Status.YourMove, GameDevice.Status.Drafting, is GameDevice.Status.Stopped -> true
        else -> false
    }

    /** Whether the viewer may act on the table now (their move, or a decision stop in playback). */
    fun canAct(): Boolean = arrowShown()

    /** Undo (a tap, I-D56 §10): free at once, else the rewind dialog with that target chosen. */
    fun undo(): Boolean {
        val g = game() ?: return false
        return when (val u = g.undo()) {
            is GameDevice.Undo.Free -> { sent(); true }
            is GameDevice.Undo.Request -> { sent(); rewind = RewindChoice(u.target); true }
            is GameDevice.Undo.Unavailable -> { undoShake++; false }
        }
    }

    /** Holding Undo opens the rewind dialog to pick any earlier point (§10). */
    fun holdUndo() { rewind = RewindChoice(game()?.device?.undoTarget()) }
}

internal val LocalEngineRoutes = staticCompositionLocalOf<EngineTableRoutes?> { null }

/** The note for a refusal of the player's own action (SYS-012: never dropped silently), or null for none. */
internal fun refusedText(reason: String): String? = when (reason) {
    GameDevice.REVEALS_HIDDEN -> CastController.WAIT_TO_CATCH_UP
    "stale", "newLine" -> NOT_TAKEN
    GameDevice.DRAFT_DROPPED -> NOT_TAKEN
    else -> null
}

/** A player's own action the game moved on from before it reached the server (SYS-012). */
internal const val NOT_TAKEN = "That wasn't taken: the game moved on first. The table shows where it is now."

/** The waiting bar's words (§14) for [wait]. */
internal fun waitingText(wait: GameDevice.Wait, play: PlayState, opponent: String): String? = when (wait) {
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

/** The name of engine object [id] as the board's viewer may see it (a Stack item's source), or null. */
private fun engineName(play: PlayState, id: ObjectId): String? {
    val view = play.board.engine ?: return null
    val state = view.state
    val obj = when (id) {
        is ObjectId.Card -> state.objects[id]
        is ObjectId.StackItem -> state.stackItems[id]?.source?.let { state.objects[it] }
    }
    if (obj != null) return if (!view.visible(obj.id) || obj.faceDown) "a card" else obj.definition?.faces?.getOrNull(obj.face)?.name
    return state.stackItems[id]?.snapshots?.firstOrNull()?.characteristics?.name
}

/** The response mode's name and what it means (§2), for the toggle and the catch-up screen. */
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

/**
 * The quick-actions row's toggles on engine state (I-D56 §14, TRN-059): the response mode (a tap goes to the next of
 * Full control, Normal and Auto-skip), "Always assume resolves" and "Autotap mana", each private to the player (§7).
 */
@Composable
internal fun RowScope.EngineToggleCells(routes: EngineTableRoutes, enabled: Boolean) {
    val game = routes.engine ?: return
    val settings = game.device.settings
    fun set(next: PlaySettings) { game.settings(next); routes.pump() }
    val cells = listOf(
        Triple("Response mode", "${settings.mode.label()}\n(mode)", Icons.Outlined.Tune) to
            ({ set(settings.copy(mode = MODES[(MODES.indexOf(settings.mode) + 1) % MODES.size])) } to null),
        Triple("Always assume resolves", "Always\nassume", Icons.Outlined.FastForward) to
            ({ set(settings.copy(alwaysAssumeResolves = !settings.alwaysAssumeResolves)) } to settings.alwaysAssumeResolves),
        Triple("Autotap mana", "Autotap\nmana", Icons.Outlined.WaterDrop) to
            ({ set(settings.copy(autotap = !settings.autotap)) } to settings.autotap))
    for ((what, action) in cells) key(what.first) {
        val (name, label, icon) = what
        val (toggle, on) = action
        val filled = on == true
        val tooltip = when (name) {
            "Response mode" -> "Response mode: ${settings.mode.label()}. ${settings.mode.meaning()} Tap for the next mode."
            "Always assume resolves" -> "Always assume resolves: go on at the other player's pauses even while they are online"
            else -> "Autotap mana sources: casting pays by itself, with mana in your pool first"
        }
        val control = MenuControl(name, enabled, selected = filled, color = AutomatumViolet) { toggle() }
        RingPress(control, Modifier.weight(1f).height(48.dp).semantics { stateDescription = if (on == null) settings.mode.label() else if (filled) "On" else "Off" },
            AutomatumViolet, fill = if (filled) AutomatumViolet else Color.White, role = if (on == null) Role.Button else Role.Switch,
            tooltip = tooltip) {
            val ink = (if (filled) Color.White else MenuInk).let { it.copy(alpha = if (enabled) 1f else .45f) }
            Column(Modifier.fillMaxSize().padding(horizontal = 3.dp), horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    Icon(icon, null, Modifier.size(16.dp), tint = ink)
                    if (on != null) Icon(if (filled) Icons.Outlined.CheckCircle else Icons.Outlined.RadioButtonUnchecked, null,
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
 * waiting bar with its Assume button, the Assume blockers screen, the catch-up screen, the decision-stop line, the
 * result line, the assumer's indicator, the notes, and the undo dialogs.
 */
@Composable
internal fun EngineAsyncHost(toolkit: TableToolkitController, routes: EngineTableRoutes, play: PlayState, scene: TableScene?,
    names: Map<String, String>, presence: ReceivedPresence?, summary: GameSummary) {
    val game = routes.engine ?: return
    val device = game.device
    val status = device.status
    val opponent = names[device.opponent.value] ?: "The other player"
    // Timeline mode (HIS-004) shows an earlier board, view-only: no async panels over it, only an incoming request
    val reviewing = routes.review != null
    // §14: the assumer's indicator
    if (!reviewing) device.indicator()?.let { EngineIndicator(it) }
    if (!reviewing) when (status) {
        is GameDevice.Status.Waiting -> if (status.offer == GameDevice.Offer.AssumeBlockers && (status.blockersScreen || routes.blockersOpen == device.session.tip))
                EngineBlockersScreen(routes, scene, play, opponent)
            else waitingText(status.reason, play, opponent)?.let { text ->
                AboveHand(handRow(scene, routes.viewer)) {
                    EngineBar(text, presence?.let { PresenceText.line(it, monotonicMillis(), java.time.ZonedDateTime.now()) }) {
                        when (status.offer) {
                            GameDevice.Offer.AssumeResolves -> ChoiceButton("Assume resolves", Icons.Outlined.DoneAll, primary = true) {
                                game.assume(); routes.pump() }
                            // The screen to assume them (editable, CMB-040)
                            GameDevice.Offer.AssumeBlockers -> ChoiceButton("Assume blockers", Icons.Outlined.Shield, primary = true) {
                                routes.blockersOpen = device.session.tip }
                            GameDevice.Offer.AssumeKeep -> ChoiceButton("Assume keep", Icons.Outlined.Check, primary = true) {
                                game.assume(); routes.pump() }
                            null -> if (status.reason == GameDevice.Wait.Request && device.request?.by == routes.viewer)
                                ChoiceButton("Cancel", Icons.Outlined.Close) { game.cancelRequest(); routes.pump() }
                        }
                    }
                }
            }
        GameDevice.Status.CatchUp -> EngineCatchUp(routes, opponent)
        is GameDevice.Status.Stopped -> AboveHand(handRow(scene, routes.viewer)) {
            EngineBar("Your decision · ${EngineEventText.of(status.event, device.model.engine, routes.viewer, names)}", null) {}
        }
        is GameDevice.Status.Playing -> EnginePlayback(routes)
        is GameDevice.Status.Over -> AboveHand(handRow(scene, routes.viewer)) {
            val won = (status.result as? arcanum.engine.setup.GameResult.Won)?.winner
            val words = when (won) { null -> "Draw"; PlayerId(routes.viewer) -> "You won"; else -> "You lost" }
            EngineBar(if (status.final) "$words · game over" else if (status.confirmable) words else "$words · waiting for $opponent to catch up", null) {
                if (!status.final) ChoiceButton("OK", Icons.Outlined.Check, enabled = status.confirmable, primary = true) {
                    game.confirmResult(); routes.pump() }
            }
        }
        GameDevice.Status.Reload -> AboveHand(handRow(scene, routes.viewer)) { EngineBar("Loading the game again…", null) {} }
        else -> {}
    }
    // Incoming undo request (§10): Accept / Deny; the game is frozen meanwhile
    val request = device.request
    if (request != null && request.by != routes.viewer) EngineRequestDialog(routes, opponent, summary)
    routes.rewind?.let { EngineRewindDialog(routes, it, names, opponent, summary) }
    // Notes (§8, §10): told once
    LaunchedEffect(game.version) {
        while (game.notes.isNotEmpty()) {
            when (val note = game.notes.removeFirst()) {
                is GameDevice.Note.Branched -> routes.note = "${names[note.by.value] ?: opponent} " + when (note.discarded.assumed?.input) {
                    is Input.DeclareBlockers -> "blocked differently"
                    is Input.SetupAnswer, is Input.Answer -> "took a mulligan"
                    else -> "responded"
                } + " — your later actions were taken back"
                is GameDevice.Note.TakenBack -> routes.note = "${names[note.by.value] ?: opponent} took back " +
                    (note.takenBack.firstOrNull()?.let { EngineEventText.of(it, device.model.engine, routes.viewer, names).replaceFirstChar(Char::lowercase) } ?: "an action")
                is GameDevice.Note.RequestNeeded -> routes.rewind = EngineTableRoutes.RewindChoice(note.target)
                is GameDevice.Note.Refused -> refusedText(note.reason)?.let { routes.note = it } ?: toolkit.dismissError()
            }
        }
    }
    routes.note?.let { text ->
        LaunchedEffect(text) { delay(6_000); if (routes.note == text) routes.note = null }
        Box(Modifier.fillMaxWidth().padding(top = 56.dp), contentAlignment = Alignment.TopCenter) {
            Surface(shape = RoundedCornerShape(8.dp), color = Color(0xff2f3a37), shadowElevation = 6.dp,
                modifier = Modifier.padding(horizontal = 24.dp).clickable { routes.note = null }.testTag("engine-note")) {
                Text(text, Modifier.padding(horizontal = 14.dp, vertical = 10.dp), color = Color.White, fontSize = 14.sp)
            }
        }
    }
}

/** The waiting bar (§14) and the other one-line panels: [text], an optional presence line, and [buttons]. */
@Composable
private fun EngineBar(text: String, presence: String?, buttons: @Composable RowScope.() -> Unit) {
    Surface(Modifier.fillMaxWidth().semantics { contentDescription = text }.testTag("engine-bar"), color = Color.White, shadowElevation = 4.dp) {
        Column {
            Box(Modifier.fillMaxWidth().height(2.dp).background(AutomatumViolet))
            Row(Modifier.fillMaxWidth().heightIn(min = 52.dp).padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Column(Modifier.weight(1f)) {
                    Text(text, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 17.sp)
                    if (presence != null) Text(presence, fontSize = 12.sp, color = MenuInk.copy(alpha = .7f))
                }
                buttons()
            }
        }
    }
}

/** §14: the assumer's indicator: how many assumptions wait to be confirmed and how many actions came since the first. */
@Composable
private fun EngineIndicator(indicator: GameDevice.Indicator) {
    Box(Modifier.fillMaxSize().padding(start = 8.dp), contentAlignment = Alignment.CenterStart) {
        Surface(shape = RoundedCornerShape(12.dp), color = Color.White, shadowElevation = 3.dp,
            border = androidx.compose.foundation.BorderStroke(1.dp, AutomatumViolet.copy(alpha = .6f)),
            modifier = Modifier.testTag("engine-indicator")) {
            Text("${indicator.assumed} assumed · ${indicator.since} ${if (indicator.since == 1) "action" else "actions"} since", Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                fontSize = 12.sp, color = AutomatumViolet, fontWeight = FontWeight.Medium)
        }
    }
}

/**
 * The Assume blockers screen (§4, §14, CMB-040): on the attacker's device only, opening with "No blocks" while the
 * defender is away (or from the waiting bar's Assume blockers while they are online). The blocks may be edited first:
 * under each attacker, the defender's untapped creatures as cards; tapping one makes it block that attacker (again: not),
 * holding one inspects it. OK assumes the blocks (the engine checks them, CR 509.1b–c; blocks with a cost to block aren't
 * assumed, CR 509.1d); Back leaves it a wait with "Assume blockers" on the waiting bar. Nothing of it is shared (§12).
 */
@Composable
private fun EngineBlockersScreen(routes: EngineTableRoutes, scene: TableScene?, play: PlayState, opponent: String) {
    val game = routes.engine ?: return
    val device = game.device
    var hidden by remember { mutableStateOf(false) }
    val tip = device.session.tip
    var blocks by remember(tip) { mutableStateOf<List<Pair<String, String>>>(emptyList()) }
    var refusal by remember(tip) { mutableStateOf<String?>(null) }
    val inspector = remember { ChoiceInspector() }
    val s = device.model.engine
    fun cardOf(id: ObjectId) = (id as? ObjectId.Card)?.card?.value
    val attackers = s.combat?.attackers.orEmpty().mapNotNull { cardOf(it.id) }
    val creatures = remember(tip) {
        val view = runCatching { arcanum.engine.Engine.view(s) }.getOrNull()
        s.battlefield.filter { id -> val o = s.obj(id); val v = view?.get(id)
            o != null && !o.tapped && v?.controller == device.opponent && v.characteristics?.isType(arcanum.api.CardType.Creature) == true
        }.mapNotNull(::cardOf)
    }
    fun objectOf(card: String) = s.battlefield.firstOrNull { cardOf(it) == card }
    AboveHand(handRow(scene, routes.viewer)) {
        AutomatumDockPanel("Assume blockers", hidden, { hidden = !hidden }, "blockers panel", buttons = {
            ChoiceButton("Back", Icons.Outlined.Close) { routes.blockersOpen = null; game.closeBlockersScreen(); routes.pump() }
            ChoiceButton("OK", Icons.Outlined.Check, primary = true) {
                val chosen = blocks.mapNotNull { (b, a) -> Block(objectOf(b) ?: return@mapNotNull null, objectOf(a) ?: return@mapNotNull null) }
                when (val acted = game.assume(chosen)) {
                    is GameDevice.Acted.Refused -> refusal = "These blocks can't be assumed: ${acted.reason}"
                    else -> routes.blockersOpen = null
                }
                routes.pump()
            }
        }) {
            Text("Assume $opponent's blockers", fontWeight = FontWeight.Bold, fontSize = 15.sp)
            ChoiceHintText("Go on assuming how $opponent blocks; if they block differently when they come back, the game " +
                "goes back to their blocks. Tap their creatures under an attacker to block it.")
            Text(if (blocks.isEmpty()) "No blocks" else "${blocks.size} ${if (blocks.size == 1) "block" else "blocks"}",
                Modifier.testTag("assumed-blocks"), fontSize = 14.sp, fontWeight = FontWeight.Medium)
            refusal?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 13.sp) }
            Column(Modifier.heightIn(max = 260.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                for (attacker in attackers) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    val shownAttacker = choiceCard(play, routes.viewer, "attacker:$attacker", attacker, true, "Attacker")
                    ChoiceCardFace(shownAttacker, Modifier.choiceCardGestures(shownAttacker.key, {}, { inspector.hold(shownAttacker, it) }), small = true)
                    Icon(androidx.compose.material.icons.Icons.Outlined.Shield, null, Modifier.size(16.dp), tint = MenuInk.copy(alpha = .6f))
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        if (creatures.isEmpty()) Text("No creatures to block", fontSize = 12.sp, color = MenuInk.copy(alpha = .7f))
                        for (blocker in creatures) {
                            val pair = blocker to attacker
                            val chosen = pair in blocks
                            val shown = choiceCard(play, routes.viewer, "block:$blocker:$attacker", blocker, true, "Creature")
                            ChoiceCardFace(shown, Modifier.testTag("assume-block-$blocker-$attacker").choiceCardGestures(shown.key, {
                                refusal = null
                                blocks = if (chosen) blocks - pair else blocks + pair
                            }, { inspector.hold(shown, it) }), selected = chosen, small = true)
                        }
                    }
                }
            }
        }
    }
    ChoiceInspection(inspector, play, routes.viewer)
}

/**
 * HIS-099: a small view-only board of [model] (the point an undo goes back to) as [viewer] sees it: the table's own
 * drawing laid out at a phone's size and drawn smaller; nothing on it acts.
 */
@Composable
private fun EngineBoardPreview(game: EngineGame, summary: GameSummary, model: org.asyncttwiz.protocol.game.GameModel, at: String, viewer: String) {
    val board = remember(model, at) { runCatching { org.asyncttwiz.automatum.EngineProjection.project(game.base, model, viewer, at, null, null, game.tokenArt) }.getOrNull() }
        ?: return
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

/** The catch-up screen (§7): coming back with the other player's actions unseen; what each mode means, the mode, Play. */
@Composable
private fun EngineCatchUp(routes: EngineTableRoutes, opponent: String) {
    val game = routes.engine ?: return
    val mode = game.device.settings.mode
    ScreenDialog({ game.startPlayback(); routes.pump() }, title = "Catching up", actions = {
        DialogMainButton("Play", { game.startPlayback(); routes.pump() }, Modifier.testTag("catch-up-play"), icon = "start")
    }) {
        Text("$opponent played on while you were away. Their actions play back, stopping wherever you decide.",
            style = MaterialTheme.typography.bodyMedium)
        for (m in MODES) Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(role = Role.RadioButton) {
                game.settings(game.device.settings.copy(mode = m)); routes.pump() }.testTag("catch-up-mode-${m.label()}"),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            RadioButton(m == mode, onClick = null)
            SettingsLabel(m.label(), m.meaning(), Modifier.weight(1f).padding(vertical = 4.dp))
        }
    }
}

private const val QUICK_PLAYBACK_MS = 120L

/** Playback (§7): the other player's actions shown one at a time, paced by the playback pause setting (HIS-021). */
@Composable
private fun EnginePlayback(routes: EngineTableRoutes) {
    val game = routes.engine ?: return
    val preferences = rememberHistoryPlaybackPreferences(routes.viewer)
    val pause by preferences.pauseState()
    LaunchedEffect(game.version) {
        // The other player's staging (moving lanes, I-D54 §8) and card places play back quickly; acting points take the pause
        val next = (game.device.status as? GameDevice.Status.Playing)?.next
        val quick = next != null && next.engine == null && next.table.all {
            it is TableChange.AttackDraft || it is TableChange.BlockDraft || it is TableChange.Placed || it is TableChange.EarlyAnswered }
        delay(if (quick) QUICK_PLAYBACK_MS else pause.coerceIn(150L, 10_000L))
        if (game.device.status is GameDevice.Status.Playing) { game.step(); routes.pump() }
    }
}

/** The incoming undo request (§10): the other player's message, what it takes back as far as the viewer has seen, Accept
 * or Deny; never how many actions go. */
@Composable
private fun EngineRequestDialog(routes: EngineTableRoutes, opponent: String, summary: GameSummary) {
    val game = routes.engine ?: return
    val preview = game.device.requestPreview()
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
        preview?.let { EngineBoardPreview(game, summary, it.board, if (it.unseen) game.device.session.localHead else game.device.request?.target ?: "", routes.viewer) }
        Text("The game waits until you answer.", style = MaterialTheme.typography.bodySmall)
    }
}

/**
 * The rewind dialog (§10): the main line up to the viewer's local head as the target picker (newest first), an optional
 * message, then Undo: done at once when it is free, else sent to the other player as an undo request.
 */
@Composable
private fun EngineRewindDialog(routes: EngineTableRoutes, choice: EngineTableRoutes.RewindChoice, names: Map<String, String>, opponent: String,
    summary: GameSummary) {
    val game = routes.engine ?: return
    val device = game.device
    val session = device.session
    val shown = remember(game.version) {
        val local = session.indexOf(session.localHead) ?: -1
        session.events.take(local + 1).filter { it.engine?.steps?.any { s -> s.input != null } == true }.asReversed()
    }
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
                    Text(EngineEventText.of(event, device.model.engine, routes.viewer, names), Modifier.padding(start = 8.dp), fontSize = 14.sp)
                }
            }
        }
        // HIS-099: the board at the point it goes back to
        val board = remember(target, game.version) { target?.takeIf { !session.drafting }?.let { runCatching { session.stateAt(it) }.getOrNull() } }
        if (board != null) EngineBoardPreview(game, summary, board, target!!, routes.viewer)
        if (needsRequest == true) {
            Text("$opponent has seen this or it showed you something new: they are asked first. The game waits meanwhile.",
                style = MaterialTheme.typography.bodySmall)
            ScreenTextField(message, { message = it.take(500) }, Modifier.fillMaxWidth().testTag("rewind-message"), placeholder = "Message (optional)")
        }
    }
}

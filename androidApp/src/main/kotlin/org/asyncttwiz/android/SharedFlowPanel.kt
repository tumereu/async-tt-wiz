package org.asyncttwiz.android

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Reply
import androidx.compose.material.icons.outlined.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.state.ObjectId
import arcanum.engine.state.StackItemKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.asyncttwiz.automatum.Divisions
import org.asyncttwiz.automatum.SharedFlow
import org.asyncttwiz.automatum.StepFlow
import org.asyncttwiz.protocol.*

/**
 * Automatum's shared flows on the table (integration spec §3 I2b, I-D11): by the Stack, in the cast panel's style,
 * - the player holding priority with something on the Stack: "Alice cast Storm Spark" with Respond and Let it resolve
 *   (Respond shrinks it to a bar over the hand while they cast a response, ZON-004);
 * - the player who decides an open choice of a resolution or a trigger: the choice, its prompt the card's sentence;
 * - the other player: "Bob is choosing — …" or "Bob may respond …", with "Choose for them" / "Pass for them" after a
 *   confirmation (D11); cards only that player may see stay hidden (I-D27).
 * The eye hides the panel to look at the board (I-D10). Nothing shows while a cast is open (its own panel does).
 */
@Composable
internal fun SharedFlowHost(toolkit: TableToolkitController, play: PlayState, scene: TableScene?, names: Map<String, String>) {
    val flows = toolkit.flows
    val shown = !toolkit.cast.open && AutomatumBoards.sharedFlowShown(play)
    LaunchedEffect(IdentityKey(play.board), shown) {
        if (!shown) { flows.show(null); return@LaunchedEffect }
        val ready = AutomatumBoards.cachedFlow(play)
        // SYS-025: the history's tags are walked and the flow worked out off the main thread (with the open decision and
        // its early answer, I-D33)
        val next = if (ready != null && play.board.engineRecords.none { it.kind == EngineRecordKind.PreAnswer }) ready
            else withContext(Dispatchers.Default) {
                (ready ?: AutomatumBoards.sharedFlow(play, toolkit.engineTags()))?.also { it.decision; it.early }
            }
        flows.show(next)
        // #8 (I-D33): a decision that opened with its early answer beside it (given while the batch opening it was on its
        // way) is answered with it by the client of whoever made the latest event, which is there to send it
        if (next?.early != null && toolkit.latestAuthor() == toolkit.viewer) flows.answerEarly()
        // CMB-033: a division of combat damage whose order can't change anything is made with the suggestion at once by
        // one client (combat review #8): the one that opened it (whoever made the latest event); the dividing player's
        // only when that one hasn't after a while (it went offline), so two clients never send the same answer
        else if (next != null && toolkit.enabled()) {
            val division = next.division
            val open = next.decision as? Decision.Divide
            val latest = toolkit.latestAuthor() == toolkit.viewer
            if (division != null && open != null && !askedInDialog(play, division) && (latest || division.player == toolkit.viewer)) {
                if (!latest) kotlinx.coroutines.delay(ORDER_FREE_FALLBACK_MILLIS)
                flows.answer(Answer.Division(open.suggested))
            }
        }
    }
    if (!shown) return
    val flow = flows.view?.takeIf { it.play.board === play.board } ?: return
    var confirm by remember(IdentityKey(play.board)) { mutableStateOf(false) }
    val viewer = toolkit.viewer
    fun name(player: String) = if (player == viewer) "You" else names[player] ?: "The other player"
    val stack = scene?.stackRegion ?: scene?.items?.firstOrNull { it.zone == PlayZone.STACK && it.kind == TableItemKind.PILE }?.bounds
    val handRow = scene?.items?.firstOrNull { it.kind == TableItemKind.HAND && it.owner == viewer }?.bounds
    val decision = flow.decision
    val holder = flow.priority
    val bar = flows.targeting || (decision == null && holder == viewer && flows.responding == play.currentNodeId)
    // CMB-033: a division of combat damage is a dialog of its own; one made by itself shows nothing meanwhile
    val dialog = decision is Decision.Divide && flow.division != null
    if (dialog && flows.answering != null && !flows.hidden && dividingInDialog(flow)) {
        val division = flow.division!!
        CompositionLocalProvider(LocalChoiceFrame provides ChoiceFrame(toolkit.viewer)) {
            CombatDivisionDialog(flows, play, names, decision as Decision.Divide, division, toolkit.combat.divisionCount(play, division))
        }
        return
    }
    // Finalreview-combat #2: the priority prompts (a pause on something on the Stack, the other player's) dock above the
    // hand in portrait while the combat area is shown, clear of its preview
    CastPanelPlacement(stack, if (bar) handRow?.center?.y else null, if (!bar && decision == null) combatDock(scene, viewer) else null) {
        CompositionLocalProvider(LocalChoiceFrame provides ChoiceFrame(viewer)) {
        when {
            decision != null && flows.answering != null -> when {
                flows.targeting -> if (!toolkit.stackTargeting) FlowTargetBar(flows, flow, play, decision as Decision.Targets)
                dialog -> if (dividingInDialog(flow)) DivisionHidden(flows)
                flows.hidden -> HiddenEye(flows)
                else -> DecisionPanel(flows, flow, play, names, decision, forOther = decision.player.value != viewer,
                    otherName = name(decision.player.value))
            }
            decision != null -> Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                WaitingBar("${name(decision.player.value)} ${if (decision.player.value == viewer) "are" else "is"} choosing — " +
                    prompt(flows, flow, play, decision), "Choose for them") { confirm = true }
                // I-D29, I-D33: meanwhile the viewer may give their own division of this damage step early
                toolkit.combat.ready(play)?.early?.firstOrNull { it.player == viewer && askedInDialog(play, it) }?.let { early ->
                    EarlyDivisionPanel(toolkit.combat, play, early, names, toolkit.enabled())
                }
            }
            holder == viewer -> when {
                flows.responding == play.currentNodeId -> RespondBar(flows, toolkit.pause, play, viewer)
                flows.hidden -> HiddenEye(flows)
                else -> RespondPrompt(flows, toolkit.pause, flow, play, names, viewer)
            }
            holder != null -> WaitingBar("${name(holder)} may respond to ${topName(flows, flow, play)}", "Pass for them",
                // STK-038 (I-D41): while this device waits for them
                toolkit.pause.running(play.currentNodeId)?.takeIf { it.role is PauseRole.Theirs && it.role.millis > 0 }
                    ?.let { { PauseProgress(toolkit.pause, it) } }) { confirm = true }
        }
        }
    }
    if (confirm) {
        val other = decision?.player?.value ?: holder
        if (other == null || other == viewer) { confirm = false; return }
        val who = name(other)
        AlertDialog(onDismissRequest = { confirm = false },
            title = { Text(if (decision != null) "Choose for $who?" else "Pass for $who?") },
            text = { Text(if (decision != null) "Automatum asks $who: ${prompt(flows, flow, play, decision)} You'll answer for them; " +
                "cards only $who can see stay hidden from you."
                else "$who may still want to respond to ${topName(flows, flow, play)}. Passing for them lets it resolve.") },
            confirmButton = { TextButton({
                confirm = false
                if (decision != null) flows.chooseFor(decision) else flows.pass(other)
            }) { Text(if (decision != null) "Choose for them" else "Pass for them") } },
            dismissButton = { TextButton({ confirm = false }) { Text("Cancel") } })
    }
}

/** Combat review #8: how long the dividing player's client waits for the client that opened an order-free division to
 * answer it before answering it itself. */
internal const val ORDER_FREE_FALLBACK_MILLIS = 4000L

/**
 * I-D40: a step that would act for the other player (the step button, the step arrow, the phase dialog, the combat quick
 * actions: anything that goes through [SharedFlowController.step]) asks first, as "Pass for them" does (STK-028); nothing
 * is sent until it is confirmed.
 */
@Composable
internal fun ActingForDialog(flows: SharedFlowController, names: Map<String, String>) {
    val asked = flows.acting ?: return
    val text = actingForText(asked, names[asked.player])
    AlertDialog(onDismissRequest = flows::cancelActing,
        title = { Text(text.title) },
        text = { Text(text.text) },
        confirmButton = { TextButton({ flows.confirmActing() }) { Text(text.button) } },
        dismissButton = { TextButton(flows::cancelActing) { Text("Cancel") } })
}

/** What the confirmation of [asked] says (I-D40); [name] is the other player's name, when known. */
internal data class ActingForText(val title: String, val text: String, val button: String)

internal fun actingForText(asked: ActingFor, name: String?): ActingForText {
    val who = name ?: "the other player"
    val subject = name ?: "The other player"
    val board = asked.board
    return when (val kind = asked.kind) {
        is ActingFor.Kind.DeclareAttackers -> ActingForText("Declare attackers for $who?",
            "$subject declares attackers now. This declares for them what the combat lanes hold: " +
                (if (kind.count == 0) "no attack." else "${kind.count} ${if (kind.count == 1) "attacker" else "attackers"}."),
            "Declare for them")
        is ActingFor.Kind.DeclareBlockers -> ActingForText("Declare blockers for $who?",
            "$subject declares blockers now. This declares for them what the combat lanes hold: " +
                (if (kind.count == 0) "no blocks." else "${kind.count} ${if (kind.count == 1) "blocker" else "blockers"}."),
            "Declare for them")
        ActingFor.Kind.NoBlocks -> ActingForText("Declare no blocks for $who?",
            "$subject declares blockers now. This clears the blocks in the combat lanes and declares no blocks for them.",
            "Declare for them")
        is ActingFor.Kind.Pass -> {
            val first = if (kind.priority) "$subject has priority and may still want to act." else "$subject declares attackers now."
            // In their own turn, passing on past combat declares no attack for them (TRN-057)
            val now = TurnPosition.of(board.turn)
            val beforeAttack = now != null && (now < TurnPosition.DECLARE_ATTACKERS || now == TurnPosition.DECLARE_ATTACKERS && !kind.priority)
            val pastCombat = when (val until = asked.until) {
                StepFlow.Until.Pass -> false
                StepFlow.Until.EndOfTurn -> true
                is StepFlow.Until.Step -> until.position > TurnPosition.DECLARE_ATTACKERS
            }
            val nothing = if (asked.player == board.turn.accountId && beforeAttack && pastCombat) ", attacking with nothing" else ""
            when (val until = asked.until) {
                StepFlow.Until.Pass -> ActingForText("Pass for $who?", "$first Passing for them lets the game go on.", "Pass for them")
                StepFlow.Until.EndOfTurn -> ActingForText("End the turn for $who?",
                    "$first Automatum passes for them until the turn ends$nothing.", "End turn for them")
                is StepFlow.Until.Step -> ActingForText("Pass for $who?",
                    "$first Automatum passes for them on to ${until.position.label}$nothing.", "Pass for them")
            }
        }
    }
}

@Composable
private fun FlowSurface(description: String, content: @Composable ColumnScope.() -> Unit) {
    Surface(Modifier.semantics { contentDescription = description }, shape = RoundedCornerShape(8.dp), color = Color.White,
        shadowElevation = 6.dp, border = BorderStroke(2.dp, AutomatumViolet)) {
        Column(Modifier.padding(10.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp), content = content)
    }
}

@Composable
private fun SlimBar(description: String, content: @Composable RowScope.() -> Unit) {
    Surface(Modifier.semantics { contentDescription = description }, shape = RoundedCornerShape(22.dp), color = Color.White,
        shadowElevation = 6.dp, border = BorderStroke(2.dp, AutomatumViolet)) {
        Row(Modifier.padding(start = 14.dp, end = 4.dp).heightIn(min = 44.dp), verticalAlignment = Alignment.CenterVertically, content = content)
    }
}

@Composable
private fun HiddenEye(flows: SharedFlowController) {
    Surface(Modifier.size(44.dp), shape = CircleShape, color = Color.White, shadowElevation = 6.dp, border = BorderStroke(2.dp, AutomatumViolet)) {
        EyeButton(hidden = true, flows::toggleHidden, "Automatum panel")
    }
}

/** The priority prompt (CR 117.3d): what is on top of the Stack, Respond and Let it resolve; at a pause (I-D41,
 * STK-037) its time running out and Hold, or "take your time" once held. */
@Composable
private fun RespondPrompt(flows: SharedFlowController, pause: PauseController, flow: SharedFlow, play: PlayState,
    names: Map<String, String>, viewer: String) {
    val node = play.currentNodeId
    val running = pause.running(node)?.takeIf { it.role is PauseRole.Yours }
    FlowSurface("Respond prompt") {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(topTitle(flows, flow, play, names, viewer), Modifier.weight(1f), fontWeight = FontWeight.Bold, fontSize = 15.sp)
            EyeButton(hidden = false, flows::toggleHidden, "Automatum panel")
        }
        if (running != null) PauseProgress(pause, running)
        else if (pause.holding(node)) Text("Holding — take your time", fontSize = 12.sp, fontWeight = FontWeight.Medium,
            color = AutomatumViolet)
        flow.top?.let { item ->
            (flow.text(item)?.takeUnless { topHidden(flows, flow) } ?: topCard(flows, flow, play)?.let { cardText(play, it) })?.let {
                Text(it, fontSize = 11.sp, lineHeight = 14.sp, color = Color(0xff4b5a5f))
            }
            val targets = item.targets.values.flatten()
            if (targets.isNotEmpty()) Text("Target: " + targets.joinToString { targetName(flows, play, names + (viewer to "You"), it) }, fontSize = 12.sp)
        }
        val more = flow.state.stack.size - 1
        if (more > 0) Text("$more more below it on the Stack", fontSize = 11.sp, color = Color(0xff4b5a5f))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End), verticalAlignment = Alignment.CenterVertically) {
            if (running != null) BarButton("Hold", Icons.Outlined.PanTool) { pause.hold(node) }
            // Responding holds the pause first, its bar running or not (#2), so the game waits for the response or it
            // becomes a branch (STK-039)
            ChoiceButton("Respond", Icons.AutoMirrored.Outlined.Reply) { flows.respond(play) }
            ChoiceButton("Let it resolve", Icons.Outlined.PlayArrow, primary = true) { if (!pause.passBehind(node)) flows.pass(viewer) }
        }
    }
}

/** Responding: the prompt as a bar over the hand while the player casts (double-tap or the hand menu's Cast). */
@Composable
private fun RespondBar(flows: SharedFlowController, pause: PauseController, play: PlayState, viewer: String) {
    SlimBar("Responding") {
        Text("Respond — cast a card from your hand", Modifier.weight(1f), fontSize = 13.sp, fontWeight = FontWeight.Medium,
            maxLines = 2, overflow = TextOverflow.Ellipsis)
        BarButton("Back", Icons.AutoMirrored.Outlined.ArrowBack, onClick = flows::stopResponding)
        BarButton("Let it resolve", Icons.Outlined.PlayArrow) { if (!pause.passBehind(play.currentNodeId)) flows.pass(viewer) }
    }
}

/** What the other player does now, with acting for them (D11) after a confirmation; [progress]: the time this device
 * waits for them (STK-038). */
@Composable
private fun WaitingBar(text: String, action: String, progress: (@Composable () -> Unit)? = null, act: () -> Unit) {
    SlimBar("Automatum waiting") {
        if (progress == null) Text(text, Modifier.weight(1f), fontSize = 13.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
        else Column(Modifier.weight(1f).padding(vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(text, fontSize = 13.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
            progress()
        }
        BarButton(action, if (action.startsWith("Pass")) Icons.Outlined.SkipNext else Icons.Outlined.SupervisorAccount, onClick = act)
    }
}

/** An open choice of a resolution or a trigger, for the player answering it. */
@Composable
private fun DecisionPanel(flows: SharedFlowController, flow: SharedFlow, play: PlayState, names: Map<String, String>,
    decision: Decision, forOther: Boolean, otherName: String) {
    FlowSurface("Automatum choice") {
        // I-D47: what the choice is and where it comes from ("Scry 2 · Carrot Cake"), then a short instruction
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(choiceTitle(flows, flow, play, decision), Modifier.weight(1f), fontWeight = FontWeight.Bold, fontSize = 15.sp)
            EyeButton(hidden = false, flows::toggleHidden, "Automatum panel")
        }
        if (forOther) Text("Choosing for $otherName", fontSize = 12.sp, fontWeight = FontWeight.Medium, color = AutomatumViolet)
        if (!hasOwnText(decision)) sourceText(flows, flow, play, decision)?.let {
            Text(it, fontSize = 11.sp, lineHeight = 14.sp, color = Color(0xff4b5a5f))
        }
        choiceHint(decision)?.let { ChoiceHintText(it) }
        CompositionLocalProvider(LocalChoiceFrame provides LocalChoiceFrame.current.copy(headed = true)) {
            when (decision) {
                is Decision.ManaPayment -> {
                    ManaSection(flows, flows, play, decision)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        ChoiceButton("Pay", Icons.Outlined.Check, enabled = flows.paid, primary = true) {
                            flows.answer(Answer.ManaPayment(flows.mana))
                        }
                    }
                }
                else -> OpenSection(flows, flows, play, names, decision)
            }
        }
    }
}

/** A choice's title (I-D47): what it is and, when it has one, the card it comes from: "Scry 2 · Carrot Cake". */
private fun choiceTitle(flows: SharedFlowController, flow: SharedFlow, play: PlayState, decision: Decision): String =
    listOfNotNull(choiceWhat(decision), decision.source?.let { sourceName(flows, flow, play, decision) }).joinToString(" · ")

/** Targets of a trigger, picked on the table as for a cast (I-D5, I-D6); there is no cancelling a shared flow. */
@Composable
private fun FlowTargetBar(flows: SharedFlowController, flow: SharedFlow, play: PlayState, decision: Decision.Targets) {
    SlimBar("Choose targets") {
        Text("${sourceName(flows, flow, play, decision)} — ${targetPrompt(decision)}", Modifier.weight(1f), fontSize = 13.sp,
            fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis)
        if (decision.max != 1 || decision.min == 0) BarButton(if (flows.picked.isEmpty()) "None" else "Done",
            if (flows.picked.isEmpty()) Icons.Outlined.Block else Icons.Outlined.Check, enabled = flows.picked.size >= decision.min,
            onClick = flows::confirmTargets)
    }
}

// ---- Words

/** "Alice cast Storm Spark", "You activated Elvish Seer", "Fertile Passing triggered". */
private fun topTitle(flows: SharedFlowController, flow: SharedFlow, play: PlayState, names: Map<String, String>, viewer: String): String {
    val item = flow.top ?: return "Something is on the Stack"
    val who = if (item.controller.value == viewer) "You" else names[item.controller.value] ?: "The other player"
    val name = topName(flows, flow, play)
    return when (item.kind) {
        StackItemKind.Spell, is StackItemKind.SpellCopy -> "$who cast $name"
        StackItemKind.Activated -> "$who activated $name"
        StackItemKind.Triggered -> "${name.replaceFirstChar { it.uppercase() }} triggered"
    }
}

/** Whether the top of the Stack (or its source) is hidden from the viewer (I-D27, STK-005: a spell cast face down). */
private fun topHidden(flows: SharedFlowController, flow: SharedFlow): Boolean {
    val top = flow.state.stack.lastOrNull() ?: return false
    val shownObject = if (top is ObjectId.StackItem) flow.state.stackItems[top]?.source else top
    return shownObject != null && !flows.shown(shownObject)
}

/** The name of the top of the Stack as its entry shows it (a card, or an ability whose source is gone, STK-022); "a
 * face-down spell" when the viewer may not see it (#11, I-D27). */
private fun topName(flows: SharedFlowController, flow: SharedFlow, play: PlayState): String {
    if (topHidden(flows, flow)) return if (flow.state.stack.lastOrNull() is ObjectId.StackItem) "an ability of a face-down card" else "a face-down spell"
    val entry = CombatStackRules.stack(play.board).cards.firstOrNull() ?: return "it"
    val card = CombatStackRules.inspection(play.board, entry) ?: return "it"
    return faceName(play, card) ?: "it"
}

/** The card the top of the Stack shows, when the viewer may see it. */
private fun topCard(flows: SharedFlowController, flow: SharedFlow, play: PlayState): String? =
    if (topHidden(flows, flow)) null
    else CombatStackRules.stack(play.board).cards.firstOrNull()?.let { CombatStackRules.inspection(play.board, it)?.id }

private fun faceName(play: PlayState, card: PlayCard): String? =
    play.definitions.singleOrNull { it.digest == card.definition }?.definition?.faces
        ?.let { faces -> faces.singleOrNull { it.faceId == card.faceId } ?: faces.firstOrNull() }?.name

/** The decision's source by name: the card or ability it is about. */
private fun sourceName(flows: SharedFlowController, flow: SharedFlow, play: PlayState, decision: Decision): String {
    val source = decision.source ?: return if (decision is Decision.OrderTriggers) "Triggers" else "Automatum"
    if (source is ObjectId.StackItem) flow.state.stackItems[source]?.source?.let { return objectName(flows, play, it) }
    return objectName(flows, play, source)
}

/** The printed text of the decision's source, for a choice without a sentence of its own; none when the viewer may not
 * see the source (I-D27: a face-down card). */
private fun sourceText(flows: SharedFlowController, flow: SharedFlow, play: PlayState, decision: Decision): String? {
    val source = decision.source ?: return null
    val card = (source as? ObjectId.StackItem)?.let { flow.state.stackItems[it]?.source } ?: source
    if (!flows.shown(card)) return null
    flow.state.stackItems[source]?.let { item -> flow.text(item)?.let { return it } }
    return flow.atwCard(source)?.let { cardText(play, it) }
}

private fun hasOwnText(decision: Decision) =
    decision is Decision.May || decision is Decision.ChooseObjects || decision is Decision.Manual || decision is Decision.OrderTriggers

/** What the decision asks, in a few words: the card's sentence when it has one. */
private fun prompt(flows: SharedFlowController, flow: SharedFlow, play: PlayState, decision: Decision): String = when (decision) {
    is Decision.May -> decision.text
    is Decision.ChooseObjects -> decision.text
    is Decision.Manual -> decision.text
    is Decision.Targets -> "${sourceName(flows, flow, play, decision)}: ${targetPrompt(decision)}"
    is Decision.OrderTriggers -> "the order of their triggers."
    is Decision.LegendRule -> "which ${decision.name} to keep."
    is Decision.ChooseReplacement -> "which replacement applies first."
    is Decision.ManaPayment -> "whether to pay ${decision.cost}."
    is Decision.Modes -> "the modes of ${sourceName(flows, flow, play, decision)}."
    is Decision.Divide -> "how to divide ${decision.amount}."
    else -> "a choice for ${sourceName(flows, flow, play, decision)}."
}

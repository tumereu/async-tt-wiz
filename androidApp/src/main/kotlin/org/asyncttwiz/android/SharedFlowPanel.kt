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
import arcanum.engine.state.TargetRef
import arcanum.engine.state.StackItemKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.asyncttwiz.automatum.Divisions
import org.asyncttwiz.automatum.SharedFlow
import org.asyncttwiz.automatum.StepFlow
import org.asyncttwiz.protocol.*

/**
 * Automatum's shared flows on the table (integration spec §3 I2b, I-D11): by the Stack, in the cast panel's style,
 * - the player holding priority with something on the Stack: "Alice cast Storm Spark" with Respond (Respond shrinks it to
 *   a bar over the hand while they cast a response, ZON-004); letting it resolve is the step arrow's "Resolve" (UI review
 *   round 2 #1: one control goes on);
 * - the player who decides an open choice of a resolution or a trigger: the choice, its prompt the card's sentence;
 * - the other player: "Bob is choosing · …" with "Choose for them" after a confirmation (D11), or "Bob may respond …"
 *   (passing for them is the step arrow's, asked first, I-D40); cards only that player may see stay hidden (I-D27).
 * The eye hides the panel to look at the board (I-D10). Nothing shows while a cast is open (its own panel does).
 */
@Composable
internal fun SharedFlowHost(toolkit: TableToolkitController, play: TableBoard, scene: TableScene?, names: Map<String, String>,
    reviewing: Boolean = false) {
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
        // I-D56 §6: on engine state the device running the step makes these engine-automatic answers itself
        if (flows.onEngine) return@LaunchedEffect
        if (next?.early != null && toolkit.latestAuthor() == toolkit.viewer) flows.answerEarly()
        // CMB-033: a division of combat damage whose order can't change anything is made with the suggestion at once by
        // one client (combat review #8): the one that opened it (whoever made the latest event); the dividing player's
        // only when that one hasn't after a while (it went offline), so two clients never send the same answer
        else if (next != null && toolkit.enabled()) {
            val division = next.division
            val open = next.decision as? Decision.Divide
            val latest = toolkit.latestAuthor() == toolkit.viewer
            if (division != null && open != null && !askedInDialog(play.board, division) && (latest || division.player == toolkit.viewer)) {
                if (!latest) kotlinx.coroutines.delay(ORDER_FREE_FALLBACK_MILLIS)
                flows.answer(Answer.Division(open.suggested))
            }
        }
    }
    if (!shown) return
    val flow = flows.view?.takeIf { it.board === play.board } ?: return
    var confirm by remember(IdentityKey(play.board)) { mutableStateOf(false) }
    val viewer = toolkit.viewer
    fun name(player: String) = if (player == viewer) "You" else names[player] ?: "The other player"
    val decision = flow.decision
    val holder = flow.priority
    // TRN-062 (owner playtest 2026-11-04 #3): the opening's Mulligan / Keep is answered only with the quick actions
    if (flows.onEngine && openingDecision(decision)) return
    // STK-027 (owner playtest 2026-11-04 #5, #6): on engine state nothing covers the board while you hold priority with
    // something on the Stack: it shows on the Stack, Resolve sits under it and a response is cast the usual way
    if (flows.onEngine && decision == null && holder == viewer) return
    // CMB-033: a division of combat damage has a panel of its own; one made by itself shows nothing meanwhile
    val dialog = decision is Decision.Divide && flow.division != null
    // UI review #4 (decided for the owner): every Automatum panel docks full width exactly above the viewer's hand, like
    // the cast panel, the eye always at the same spot
    AboveHand(handRow(scene, viewer)) {
        CompositionLocalProvider(LocalChoiceFrame provides ChoiceFrame(viewer)) {
        when {
            decision != null && flows.answering != null -> when {
                flows.targeting -> if (toolkit.stackTargeting) StackTargetPicker(toolkit, play)
                    else FlowTargetBar(flows, flow, play, decision as Decision.Targets)
                // UI review round 2 #7: docked above the hand like every Automatum panel
                dialog -> if (dividingInDialog(flow)) flow.division?.let { division ->
                    CombatDivisionPanel(flows, play, names, decision as Decision.Divide, division, toolkit.combat.divisionCount(play, division))
                }
                else -> DecisionPanel(flows, flow, play, names, decision, forOther = decision.player.value != viewer,
                    otherName = name(decision.player.value))
            }
            // I-D56 §14: on engine state the waiting bar ([EngineAsyncHost]) says what the other player decides; nobody
            // chooses or passes for them (STK-031, STK-038 gone)
            flows.onEngine && holder != viewer -> {}
            decision != null -> Column {
                // I-D29, I-D33: meanwhile the viewer may give their own division of this damage step early
                toolkit.combat.ready(play)?.early?.firstOrNull { it.player == viewer && askedInDialog(play.board, it) }?.let { early ->
                    EarlyDivisionPanel(toolkit.combat, play, early, names, toolkit.enabled())
                }
                // UI review round 2 #2 (decided for the owner): the observer's own words on one line, "Bob is choosing ·
                // Fertile Passing", the source drawn as a card (I-D48)
                WaitingBar(play, "${name(decision.player.value)} ${if (decision.player.value == viewer) "are" else "is"} choosing · " +
                    observerWhat(flows, flow, play, decision), observedCard(flows, flow, play, viewer, decision),
                    "Choose for them") { confirm = true }
            }
            holder == viewer -> when {
                flows.responding == play.nodeId -> RespondBar(flows)
                else -> RespondPrompt(flows, toolkit.pause, flow, play, names, viewer, reviewing)
            }
            // UI review round 2 #1: passing for them is the step arrow's "Resolve" (asked first, I-D40)
            holder != null -> WaitingBar(play, "${name(holder)} may respond to ${topName(flows, flow, play)}",
                topCard(flows, flow, play)?.let { choiceCard(play, viewer, "top", it, true, topName(flows, flow, play)) }, null,
                // STK-038 (I-D41): while this device waits for them
                toolkit.pause.running(play.nodeId)?.takeIf { it.role is PauseRole.Theirs && it.role.millis > 0 }
                    ?.let { { PauseProgress(toolkit.pause, it) } }) {}
        }
        }
    }
    if (confirm) {
        val other = decision?.player?.value
        if (decision == null || other == null || other == viewer) { confirm = false; return }
        val who = name(other)
        AlertDialog(onDismissRequest = { confirm = false },
            title = { Text("Choose for $who?") },
            text = { Text("Automatum asks $who: ${prompt(flows, flow, play, decision)} You'll answer for them; " +
                "cards only $who can see stay hidden from you.") },
            confirmButton = { TextButton({ confirm = false; flows.chooseFor(decision) }) { IconText(Icons.Outlined.SupervisorAccount, "Choose for them") } },
            dismissButton = { TextButton({ confirm = false }) { IconText(Icons.Outlined.Close, "Cancel") } })
    }
}

/** The opening's mulligan decision (Keep or Mulligan, CR 103.5), which the quick actions answer (TRN-062). */
internal fun openingDecision(decision: Decision?): Boolean =
    decision is Decision.ChooseOption && decision.options.containsAll(listOf("Keep", "Mulligan"))

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
        confirmButton = { BarButton(text.button, Icons.Outlined.SkipNext) { flows.confirmActing() } },
        dismissButton = { BarButton("Cancel", Icons.Outlined.Close, onClick = flows::cancelActing) })
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

/** STK-027: what the respond panel says in timeline mode, where its Respond is greyed. */
internal const val REVIEW_RESPOND = "Hold Auto to respond on a branch"

/** The priority prompt (CR 117.3d), docked above the hand (UI review #4): the card on top of the Stack drawn as a card
 * (hold: Inspect) beside "Alice cast Storm Spark", an ability's own line, its targets (cards as cards) and Respond (the
 * step arrow's "Resolve" lets it resolve, UI review round 2 #1); at a pause (I-D41, STK-037) its time running out and
 * Hold, or "take your time" once held. */
@Composable
private fun RespondPrompt(flows: SharedFlowController, pause: PauseController, flow: SharedFlow, play: TableBoard,
    names: Map<String, String>, viewer: String, reviewing: Boolean = false) {
    val node = play.nodeId
    val running = pause.running(node)?.takeIf { it.role is PauseRole.Yours }
    val inspector = remember(node) { ChoiceInspector() }
    AutomatumDockPanel("Respond prompt", flows.hidden, flows::toggleHidden, "Automatum panel", buttons = {
        if (running != null) ChoiceButton("Hold", Icons.Outlined.PanTool) { pause.hold(node) }
        // Responding holds the pause first, its bar running or not (#2), so the game waits for the response or it
        // becomes a branch (STK-039)
        // UI review round 2 N15 (STK-027): in timeline mode a response is made on a branch, by holding Auto (HIS-036)
        ChoiceButton("Respond", Icons.AutoMirrored.Outlined.Reply, enabled = !reviewing, primary = true) { flows.respond(play) }
    }) {
        val item = flow.top
        val hidden = topHidden(flows, flow)
        val entry = CombatStackRules.stack(play.board).cards.firstOrNull()?.let { CombatStackRules.inspection(play.board, it)?.id }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (entry != null) choiceCard(play, viewer, "top", entry, !hidden, topName(flows, flow, play)).let { card ->
                ChoiceCardFace(card, Modifier.choiceCardGestures(card.key, tap = {}, hold = { inspector.hold(card, it) }), small = true)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(topTitle(flows, flow, play, names, viewer), fontWeight = FontWeight.Bold, fontSize = 15.sp)
                // An ability says which of its source's abilities it is; a spell is its card (hold it to read it)
                if (item != null && !hidden && item.kind !is StackItemKind.Spell && item.kind !is StackItemKind.SpellCopy)
                    flow.text(item)?.let { Text(it, fontSize = 11.sp, lineHeight = 14.sp, color = Color(0xff4b5a5f)) }
                if (reviewing) Text(REVIEW_RESPOND, fontSize = 12.sp, color = Color(0xff4b5a5f))
                else if (running != null) PauseProgress(pause, running)
                else if (pause.holding(node)) Text("Holding — take your time", fontSize = 12.sp, fontWeight = FontWeight.Medium,
                    color = AutomatumViolet)
            }
        }
        val targets = item?.targets?.values?.flatten().orEmpty()
        if (targets.isNotEmpty()) AboutRow("Target", targets.map { target ->
            when (target) {
                is TargetRef.Object -> AboutItem.Card(choiceCardOf(flows, play, viewer, target.id))
                is TargetRef.Player -> AboutItem.Player(if (target.id.value == viewer) "You" else names[target.id.value] ?: "A player")
            }
        }, inspector)
        val more = flow.state.stack.size - 1
        if (more > 0) Text("$more more below it on the Stack", fontSize = 11.sp, color = Color(0xff4b5a5f))
    }
    ChoiceInspection(inspector, play, viewer)
}

/** What a prompt or choice is about: a card (drawn as a card) or a player (named). */
private sealed interface AboutItem {
    data class Card(val card: ChoiceCardData) : AboutItem
    data class Player(val name: String) : AboutItem
}

/** "Target: [card] You", "About: [card]" (UI review #5): the objects a prompt or a choice is about. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AboutRow(label: String, items: List<AboutItem>, inspector: ChoiceInspector) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("$label:", Modifier.align(Alignment.CenterVertically), fontSize = 12.sp, color = Color(0xff4b5a5f))
        for (item in items) when (item) {
            is AboutItem.Card -> ChoiceCardFace(item.card, Modifier.choiceCardGestures(item.card.key, tap = {},
                hold = { inspector.hold(item.card, it) }), small = true)
            is AboutItem.Player -> Row(Modifier.align(Alignment.CenterVertically), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Person, null, Modifier.size(18.dp), tint = MenuInk)
                Text(item.name, fontSize = 12.sp)
            }
        }
    }
}

/** Responding: the prompt as a bar above the hand while the player casts (double-tap or the hand menu's Cast); the step
 * arrow's "Resolve" lets it resolve instead (UI review round 2 #1). */
@Composable
private fun RespondBar(flows: SharedFlowController) {
    AutomatumDockBar("Responding") {
        Text("Respond — cast a card from your hand", Modifier.weight(1f), fontSize = 13.sp, fontWeight = FontWeight.Medium,
            maxLines = 2, overflow = TextOverflow.Ellipsis)
        BarButton("Back", Icons.AutoMirrored.Outlined.ArrowBack, onClick = flows::stopResponding)
    }
}

/** What the other player does now, on one line beside the [card] it is about (I-D48: hold it to inspect it), with acting
 * for them ([action], D11) after a confirmation; [progress]: the time this device waits for them (STK-038). */
@Composable
private fun WaitingBar(play: TableBoard, text: String, card: ChoiceCardData?, action: String?, progress: (@Composable () -> Unit)? = null, act: () -> Unit) {
    val inspector = remember(card?.key) { ChoiceInspector() }
    AutomatumDockBar("Automatum waiting") {
        if (card != null) ChoiceCardFace(card, Modifier.padding(end = 8.dp, top = 2.dp, bottom = 2.dp)
            .choiceCardGestures(card.key, tap = {}, hold = { inspector.hold(card, it) }), small = true)
        if (progress == null) Text(text, Modifier.weight(1f), fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        else Column(Modifier.weight(1f).padding(vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(text, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            progress()
        }
        if (action != null) BarButton(action, Icons.Outlined.SupervisorAccount, onClick = act)
    }
    ChoiceInspection(inspector, play, LocalChoiceFrame.current.viewer)
}

/** What the other player is choosing, in the observer's words (UI review round 2 #2): the card it comes from ("Fertile
 * Passing"), else what it is ("Order triggers", "Legend rule"), never the chooser's own "You may". */
internal fun observerWhat(flows: SharedFlowController, flow: SharedFlow, play: TableBoard, decision: Decision): String =
    commonSource(decision)?.let { sourceName(flows, flow, play, decision, it) }
        ?: if (decision is Decision.May) "a choice" else choiceWhat(decision)

/** The card an open choice comes from, drawn on the other player's waiting bar (I-D48); null when none is shown. */
private fun observedCard(flows: SharedFlowController, flow: SharedFlow, play: TableBoard, viewer: String, decision: Decision): ChoiceCardData? {
    val source = commonSource(decision) ?: return null
    val card = (if (source is ObjectId.StackItem) flow.state.stackItems[source]?.source else source) ?: return null
    return choiceCardOf(flows, play, viewer, card).takeIf { it.cardId != null }
}

/** The decision's source, or the one source all the triggers to order share (UI review round 2 N10). */
private fun commonSource(decision: Decision): ObjectId? = decision.source
    ?: (decision as? Decision.OrderTriggers)?.triggers?.map { it.source }?.distinct()?.singleOrNull()

/** An open choice of a resolution or a trigger, for the player answering it, docked above the hand (UI review #4): the
 * source's card beside the title saying what the choice is and where it comes from (I-D47), an ability's own line, the
 * cards it is about (what it triggered on, its targets; UI review #5), a short instruction, then the choice. */
@Composable
private fun DecisionPanel(flows: SharedFlowController, flow: SharedFlow, play: TableBoard, names: Map<String, String>,
    decision: Decision, forOther: Boolean, otherName: String) {
    val viewer = LocalChoiceFrame.current.viewer
    val inspector = remember(decision.id) { ChoiceInspector() }
    // The Stack item the choice belongs to: named by the decision, or the resolving top of the Stack from its source
    val item = decision.source?.let { source -> flow.state.stackItems[source] ?: flow.top?.takeIf { it.source == source || it.id == source } }
    val sourceCard = item?.source ?: decision.source?.takeIf { it !is ObjectId.StackItem }
    // UI review round 2 N3: the choice's own buttons sit in the dock's bottom row beside the eye, as in the cast panel
    val slot = remember(decision.id) { DockSlot() }
    AutomatumDockPanel("Automatum choice", flows.hidden, flows::toggleHidden, "Automatum panel", buttons = { slot.buttons?.invoke(this) }) {
        CompositionLocalProvider(LocalDockSlot provides slot) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            sourceCard?.let { choiceCardOf(flows, play, viewer, it) }?.takeIf { it.cardId != null }?.let { card ->
                ChoiceCardFace(card, Modifier.choiceCardGestures(card.key, tap = {}, hold = { inspector.hold(card, it) }), small = true)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                // I-D47: what the choice is and where it comes from ("Scry 2 · Carrot Cake")
                Text(choiceTitle(flows, flow, play, decision), fontWeight = FontWeight.Bold, fontSize = 15.sp)
                if (forOther) Text("Choosing for $otherName", fontSize = 12.sp, fontWeight = FontWeight.Medium, color = AutomatumViolet)
                // An ability's own line (a spell is its card: hold it to read it)
                if (!hasOwnText(decision) && item != null && (item.kind is StackItemKind.Activated || item.kind is StackItemKind.Triggered) &&
                    sourceCard?.let(flows::shown) != false) flow.text(item)?.let {
                    Text(it, fontSize = 11.sp, lineHeight = 14.sp, color = Color(0xff4b5a5f))
                }
            }
        }
        // UI review #5: which objects the choice is about, e.g. the creature that died
        val about = item?.let { (it.event?.objects.orEmpty().flatMap { r -> r.ids } +
            it.targets.values.flatten().filterIsInstance<TargetRef.Object>().map { t -> t.id }).distinct() - setOfNotNull(sourceCard) }.orEmpty()
            // One card once: an object that changed zones is the same card (its earlier object can't be shown any more)
            .groupBy { flows.atwCard(it) ?: it.toString() }.filterKeys { it != sourceCard?.let(flows::atwCard) }
            .values.map { same -> same.firstOrNull(flows::shown) ?: same.first() }
        if (about.isNotEmpty()) AboutRow("About", about.map { AboutItem.Card(choiceCardOf(flows, play, viewer, it)) }, inspector)
        choiceHint(decision)?.let { ChoiceHintText(it) }
        CompositionLocalProvider(LocalChoiceFrame provides LocalChoiceFrame.current.copy(headed = true)) {
            when (decision) {
                is Decision.ManaPayment -> {
                    ManaSection(flows, flows, play, decision)
                    DockButtons {
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
    ChoiceInspection(inspector, play, viewer)
}

/** A choice's title (I-D47): what it is and, when it has one, the card it comes from: "Scry 2 · Carrot Cake", "Order
 * triggers · Peak Ascendancy" (the source all the triggers share, UI review round 2 N10). */
internal fun choiceTitle(flows: SharedFlowController, flow: SharedFlow, play: TableBoard, decision: Decision): String =
    listOfNotNull(choiceWhat(decision), commonSource(decision)?.let { sourceName(flows, flow, play, decision, it) }).joinToString(" · ")

/** Targets of a trigger, picked on the table as for a cast (I-D5, I-D6); there is no cancelling a shared flow. */
@Composable
private fun FlowTargetBar(flows: SharedFlowController, flow: SharedFlow, play: TableBoard, decision: Decision.Targets) {
    AutomatumDockBar("Choose targets", eye = true) {
        Text("${sourceName(flows, flow, play, decision)} — ${targetPrompt(decision)}", Modifier.weight(1f), fontSize = 13.sp,
            fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis)
        if (decision.max != 1 || decision.min == 0) BarButton(if (flows.picked.isEmpty()) "None" else "Done",
            if (flows.picked.isEmpty()) Icons.Outlined.Block else Icons.Outlined.Check, enabled = flows.picked.size >= decision.min,
            onClick = flows::confirmTargets)
    }
}

// ---- Words

/** "Alice cast Storm Spark", "You activated Elvish Seer", "Fertile Passing triggered". */
private fun topTitle(flows: SharedFlowController, flow: SharedFlow, play: TableBoard, names: Map<String, String>, viewer: String): String {
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
private fun topName(flows: SharedFlowController, flow: SharedFlow, play: TableBoard): String {
    if (topHidden(flows, flow)) return if (flow.state.stack.lastOrNull() is ObjectId.StackItem) "an ability of a face-down card" else "a face-down spell"
    val entry = CombatStackRules.stack(play.board).cards.firstOrNull() ?: return "it"
    val card = CombatStackRules.inspection(play.board, entry) ?: return "it"
    return faceName(play, card) ?: "it"
}

/** The card the top of the Stack shows, when the viewer may see it. */
private fun topCard(flows: SharedFlowController, flow: SharedFlow, play: TableBoard): String? =
    if (topHidden(flows, flow)) null
    else CombatStackRules.stack(play.board).cards.firstOrNull()?.let { CombatStackRules.inspection(play.board, it)?.id }

private fun faceName(play: TableBoard, card: PlayCard): String? =
    play.definitions.singleOrNull { it.digest == card.definition }?.definition?.faces
        ?.let { faces -> faces.singleOrNull { it.faceId == card.faceId } ?: faces.firstOrNull() }?.name

/** The decision's source ([source], its own by default) by name: the card or ability it is about. */
private fun sourceName(flows: SharedFlowController, flow: SharedFlow, play: TableBoard, decision: Decision,
    source: ObjectId? = decision.source): String {
    source ?: return if (decision is Decision.OrderTriggers) "Triggers" else "Automatum"
    if (source is ObjectId.StackItem) flow.state.stackItems[source]?.source?.let { return objectName(flows, play, it) }
    return objectName(flows, play, source)
}

private fun hasOwnText(decision: Decision) =
    decision is Decision.May || decision is Decision.ChooseObjects || decision is Decision.Manual || decision is Decision.OrderTriggers

/** What the decision asks, in a few words: the card's sentence when it has one. */
private fun prompt(flows: SharedFlowController, flow: SharedFlow, play: TableBoard, decision: Decision): String = when (decision) {
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

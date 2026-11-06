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
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Announced
import arcanum.engine.decision.Continuation
import arcanum.engine.decision.Decision
import org.asyncttwiz.automatum.CastFlow
import arcanum.engine.state.ObjectId
import arcanum.engine.state.TargetRef
import arcanum.engine.state.StackItem
import arcanum.engine.state.StackItemKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.asyncttwiz.automatum.Divisions
import org.asyncttwiz.automatum.SharedFlow
import org.asyncttwiz.automatum.StepFlow
import org.asyncttwiz.protocol.*

/**
 * Automatum's shared flows on the table (integration spec §3 I2b, I-D11): by the Stack, in the cast panel's style,
 * - the player who decides an open choice of a resolution or a trigger: the choice, its prompt the card's sentence;
 * - the other player's choice while the viewer holds priority: "Bob is choosing · …" with "Choose for them" after a
 *   confirmation (D11); cards only that player may see stay hidden (I-D27).
 * Nothing covers the board while the viewer holds priority with something on the Stack (STK-027), and the waiting bar
 * ([EngineAsyncHost]) says what the other player decides (I-D56 §14).
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
        // SYS-025: the flow worked out off the main thread (with the open decision and its early answer, I-D33)
        val next = if (ready != null && play.board.engineRecords.none { it.kind == EngineRecordKind.PreAnswer }) ready
            else withContext(Dispatchers.Default) {
                (ready ?: AutomatumBoards.sharedFlow(play))?.also { it.decision; it.early }
            }
        // I-D56 §6: the device running the step makes the engine-automatic answers itself
        flows.show(next)
    }
    if (!shown) return
    val flow = flows.view?.takeIf { it.board === play.board } ?: return
    var confirm by remember(IdentityKey(play.board)) { mutableStateOf(false) }
    val viewer = toolkit.viewer
    fun name(player: String) = if (player == viewer) "You" else names[player] ?: "The other player"
    val decision = flow.decision
    val holder = flow.priority
    // TRN-062 (owner playtest 2026-11-04 #3): the opening's Mulligan / Keep is answered only with the quick actions
    if (openingDecision(decision)) return
    // STK-027 (owner playtest 2026-11-04 #5, #6): nothing covers the board while you hold priority with something on the
    // Stack: it shows on the Stack, Resolve sits under it and a response is cast the usual way
    if (decision == null && holder == viewer) return
    // CMB-033: a division of combat damage has a panel of its own; one made by itself shows nothing meanwhile
    val dialog = decision is Decision.Divide && flow.division != null
    // CMB-033 (owner, 2026-11-05): divisions open attached to the combat band, never above the hand
    if (decision != null && flows.answering != null && !flows.targeting && dialog) {
        if (dividingInDialog(flow)) flow.division?.let { division ->
            BandAttached(scene) {
                CompositionLocalProvider(LocalChoiceFrame provides ChoiceFrame(viewer)) {
                    CombatDivisionPanel(flows, play, names, decision as Decision.Divide, division, toolkit.combat.divisionCount(play, division))
                }
            }
        }
        return
    }
    val early = if (decision != null && flows.answering == null && holder == viewer)
        toolkit.combat.ready(play)?.early?.firstOrNull { it.player == viewer && askedInDialog(play.board, it) } else null
    if (early != null) BandAttached(scene) {
        CompositionLocalProvider(LocalChoiceFrame provides ChoiceFrame(viewer)) {
            EarlyDivisionPanel(toolkit.combat, play, early, names, toolkit.enabled())
        }
    }
    // UI review #4 (decided for the owner): every Automatum panel docks full width exactly above the viewer's hand, like
    // the cast panel, the eye always at the same spot
    AboveHand(handRow(scene, viewer)) {
        CompositionLocalProvider(LocalChoiceFrame provides ChoiceFrame(viewer)) {
        when {
            decision != null && flows.answering != null -> when {
                flows.targeting -> FlowTargetBar(flows, flow, play, decision as Decision.Targets,
                    decisionSource(flows, flow, play, viewer, decision))
                else -> DecisionPanel(flows, flow, play, names, decision, forOther = decision.player.value != viewer,
                    otherName = name(decision.player.value))
            }
            // I-D56 §14: the waiting bar ([EngineAsyncHost]) says what the other player decides; nobody chooses or passes
            // for them (STK-031, STK-038 gone)
            holder != viewer -> {}
            decision != null -> Column {
                // I-D29, I-D33: meanwhile the viewer may give their own division of this damage step early, attached to
                // the combat band (CMB-033)
                // UI review round 2 #2 (decided for the owner): the observer's own words on one line, "Bob is choosing ·
                // Fertile Passing", the source drawn as a card (I-D48)
                WaitingBar(play, "${name(decision.player.value)} ${if (decision.player.value == viewer) "are" else "is"} choosing · " +
                    observerWhat(flows, flow, play, decision), observedCard(flows, flow, play, viewer, decision),
                    "Choose for them") { confirm = true }
            }
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

/** The Stack item an open choice belongs to: named by the decision, or the resolving top of the Stack from its source. */
private fun decisionItem(flow: SharedFlow, decision: Decision): StackItem? =
    decision.source?.let { source -> flow.state.stackItems[source] ?: flow.top?.takeIf { it.source == source || it.id == source } }

/**
 * Where an open choice comes from (STK-044), drawn at the right of its panel or target bar: an ability as its Stack token
 * (STK-046: its source's art, its marker; holding it inspects its tile when it is on the Stack, then the source card), a
 * spell or permanent as its card (covered when the viewer may not see it), the one source all the triggers to order share
 * (UI review round 2 N10); null when it has none (a discard to hand size, the legend rule).
 */
internal fun decisionSource(flows: FlowQuestions, flow: SharedFlow, play: TableBoard, viewer: String, decision: Decision): ChoiceCardData? {
    if (decision is Decision.OrderTriggers) return commonSource(decision)?.let { choiceCardOf(flows, play, viewer, it) }?.takeIf { it.cardId != null }
    val item = decisionItem(flow, decision)
    val source = item?.source ?: decision.source?.takeIf { it !is ObjectId.StackItem } ?: return null
    val card = choiceCardOf(flows, play, viewer, source).takeIf { it.cardId != null && it.shown }
        // An ability whose source has left since (a fetch land sacrificed to activate it, STK-056): its Stack entry
        ?: return stackEntrySource(flow, play, viewer, item)
            ?: choiceCardOf(flows, play, viewer, source).takeIf { it.cardId != null }
    if (item == null) {
        // An ability being put on the Stack (a trigger's targets and modes, CR 603.3d): its source's card asks
        val ability = when (val c = flow.state.pending?.continuation) {
            is Continuation.PlacingTriggers -> (c.current?.what as? Announced.Ability)?.takeIf { it.source == source }?.ability
                ?: c.waiting.firstOrNull { it.source == source && it.controller == decision.player }?.ability
            is Continuation.Announcing -> (c.what as? Announced.Ability)?.takeIf { it.source == source }?.ability
            else -> null
        }
        val triggered = ability is arcanum.api.Ability.Triggered
        if (!triggered && ability !is arcanum.api.Ability.Activated) return card
        return card.copy(key = "ability/$source", ability = StackAbilityShown(triggered, card.name, CastFlow.text(ability).orEmpty().trim()))
    }
    val triggered = when (item.kind) {
        is StackItemKind.Triggered -> true
        is StackItemKind.Activated -> false
        else -> return card
    }
    // Its entry on the Stack, when the ability is there yet (its Inspect tile, STK-050)
    val ref = flow.ref(TargetRef.Object(item.id))
    val entry = play.board.stackEffects.firstOrNull { it.item?.id == ref }?.id
    return card.copy(key = entry ?: "ability/${item.id}", ability = StackAbilityShown(triggered, card.name, flow.text(item).orEmpty().trim()))
}

/** The ability [item] as its entry on the Stack shows it (STK-046: its source card as the table keeps it, the
 * ability's text), or null when it is no ability on the Stack or [viewer] may not see its source. */
private fun stackEntrySource(flow: SharedFlow, play: TableBoard, viewer: String, item: StackItem?): ChoiceCardData? {
    if (item == null || (item.kind !is StackItemKind.Triggered && item.kind !is StackItemKind.Activated)) return null
    val ref = flow.ref(TargetRef.Object(item.id))
    val entry = play.board.stackEffects.firstOrNull { it.item?.id == ref }?.id ?: return null
    val ability = StackAbilities.of(play, entry, viewer) ?: return null
    val card = CombatStackRules.inspection(play.board, entry) ?: return null
    return choiceCard(play, viewer, entry, card.id, true, ability.source).copy(ability = ability)
}

/** An open choice of a resolution or a trigger, for the player answering it, docked above the hand (UI review #4): the
 * title saying only what the choice is (I-D47, STK-030) with the source at the right (STK-044), an ability's own line,
 * the cards it is about (what it triggered on, its targets; UI review #5), a short instruction, then the choice. */
@Composable
private fun DecisionPanel(flows: SharedFlowController, flow: SharedFlow, play: TableBoard, names: Map<String, String>,
    decision: Decision, forOther: Boolean, otherName: String) {
    val viewer = LocalChoiceFrame.current.viewer
    val inspector = remember(decision.id) { ChoiceInspector() }
    val item = decisionItem(flow, decision)
    val sourceCard = item?.source ?: decision.source?.takeIf { it !is ObjectId.StackItem }
    val source = decisionSource(flows, flow, play, viewer, decision)
    // UI review round 2 N3: the choice's own buttons sit in the dock's bottom row beside the eye, as in the cast panel
    val slot = remember(decision.id) { DockSlot() }
    AutomatumDockPanel("Automatum choice", flows.hidden, flows::toggleHidden, "Automatum panel", buttons = { slot.buttons?.invoke(this) }) {
        CompositionLocalProvider(LocalDockSlot provides slot) {
        // I-D47, STK-030: the title says only what the choice is ("Scry 2"); its source is the card at the right
        ChoiceHead(choiceWhat(decision), source, inspector) {
            if (forOther) Text("Choosing for $otherName", fontSize = 12.sp, fontWeight = FontWeight.Medium, color = AutomatumViolet)
            // An ability's own line (a spell is its card: hold it to read it)
            if (!hasOwnText(decision) && item != null && (item.kind is StackItemKind.Activated || item.kind is StackItemKind.Triggered) &&
                sourceCard?.let(flows::shown) != false) flow.text(item)?.let {
                CardText(it, color = Color(0xff4b5a5f), style = androidx.compose.ui.text.TextStyle(fontSize = 11.sp, lineHeight = 14.sp))
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
        ChoiceInspection(inspector, play, viewer)
        }
    }
}

/** Targets of a trigger, picked on the table as for a cast (I-D5, I-D6); there is no cancelling a shared flow. The title
 * says only the choice, the [source] sits at the right (STK-044). */
@Composable
private fun FlowTargetBar(flows: SharedFlowController, flow: SharedFlow, play: TableBoard, decision: Decision.Targets,
    source: ChoiceCardData?) {
    val inspector = remember(decision.id) { ChoiceInspector() }
    AutomatumDockBar("Choose targets", eye = true) {
        Text(targetTitle(decision), Modifier.weight(1f).semantics { heading() }, fontSize = 13.sp,
            fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis)
        if (decision.max != 1 || decision.min == 0) BarButton(if (flows.picked.isEmpty()) "None" else "Done",
            if (flows.picked.isEmpty()) Icons.Outlined.Block else Icons.Outlined.Check, enabled = flows.picked.size >= decision.min,
            onClick = flows::confirmTargets)
        if (source != null) ChoiceSource(source, inspector, Modifier.padding(start = 4.dp, end = 6.dp, top = 2.dp, bottom = 2.dp))
        ChoiceInspection(inspector, play, LocalChoiceFrame.current.viewer)
    }
}

// ---- Words





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

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
import org.asyncttwiz.automatum.Divisions
import org.asyncttwiz.automatum.SharedFlow
import org.asyncttwiz.automatum.StepFlow
import org.asyncttwiz.protocol.*

/** The choice panel's waits (HIS-112, TBL-125), for the report: when [decision] waits and for what ([held]), and when it opens. */
@Composable
private fun ChoiceHoldLog(decision: String?, held: String?) {
    val last = remember { arrayOfNulls<String>(2) }
    SideEffect {
        if (decision == null) { last[0] = null; last[1] = null; return@SideEffect }
        if (last[0] == decision && last[1] == held) return@SideEffect
        if (held != null) AppLog.info("decision", "Choice panel of $decision waits for $held")
        else if (last[0] == decision && last[1] != null) AppLog.info("decision", "Choice panel of $decision opens: nothing before it plays any more")
        last[0] = decision; last[1] = held
    }
}

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
fun SharedFlowHost(toolkit: TableToolkitController, play: TableBoard, scene: TableScene?, names: Map<String, String>,
    reviewing: Boolean = false) {
    val flows = toolkit.flows
    // CMB-098: the viewer's costs panel, whose payment is the board's flow's ([SharedFlowController.declarationPayment])
    val costs = !reviewing && toolkit.combat.costPanelOpen(play)
    val shown = !toolkit.cast.open && (AutomatumBoards.sharedFlowShown(play) || costs)
    LaunchedEffect(IdentityKey(play.board), shown) {
        if (!shown) { flows.show(null); return@LaunchedEffect }
        // SYS-025: the flow worked out off the main thread (with the open decision, its early answer, I-D33, and what its
        // panel draws), then shown at once; in the browser asked of the engine's worker, shown when it
        // answers
        if (!flows.remote && AutomatumBoards.cachedSharedValue(play) == null)
            toolkit.boardWork(Dispatchers.Default) { AutomatumBoards.sharedValue(play) }
        // I-D56 §6: the device running the step makes the engine-automatic answers itself
        flows.load(play)
    }
    if (!shown) return
    // HIS-110: the other player's reveal panel comes first; the choice shows once it is dismissed
    if (toolkit.engine?.revealing() == true) return
    // CMB-098: no choice is open while a declaration is awaited: the costs panel, its payment made on the table
    if (costs && !play.flowOpen) {
        AboveHand(handRow(scene, toolkit.viewer), PromptPlace.Board) {
            CompositionLocalProvider(LocalChoiceFrame provides ChoiceFrame(toolkit.viewer)) { CombatCostPanel(toolkit, play) }
        }
        return
    }
    // STK-030: a new board asking the same choice keeps its panel: picks live in the panel and would go with it
    val flow = flows.flowOn(play) ?: return
    var confirm by remember(IdentityKey(play.board)) { mutableStateOf(false) }
    val viewer = toolkit.viewer
    fun name(player: String) = flowPlayerName(player, viewer, names)
    val decision = flow.decision
    val holder = flow.priority
    // HIS-112, STK-058, TBL-125 (owner playtest 2026-11-15: "'You may pay' was asked before the trigger had animated to its
    // place on the Stack"): a choice asked while something resolves opens once what leads to it has played on this table
    val held = if (decision != null && !reviewing) toolkit.engine?.choiceHeld() else null
    ChoiceHoldLog(decision?.let { "${it::class.simpleName} ${it.id.value} of ${it.player.value.take(8)}" }, held)
    if (held != null) return
    // TRN-062 (owner playtest 2026-11-04 #3): the opening's Mulligan / Keep is answered only with the quick actions
    if (openingDecision(decision)) return
    // TRN-062 (owner problem report 5DDSKK): the bottom after a mulligan is the Keep / Bottom screen's ([OpeningKeepHost])
    if (decision != null && decision.player.value == viewer && AutomatumOpening.bottomOwed(play) > 0) return
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
        toolkit.combat.ready(play)?.early?.firstOrNull { it.player == viewer } else null
    if (early != null) BandAttached(scene) {
        CompositionLocalProvider(LocalChoiceFrame provides ChoiceFrame(viewer)) {
            EarlyDivisionPanel(toolkit.combat, play, early, names, toolkit.enabled())
        }
    }
    // UI review #4 (decided for the owner): every Automatum panel docks full width exactly above the viewer's hand, like
    // the cast panel, the eye always at the same spot
    // WEB-019: on the desktop table picking on the board and paying there are a strip, the other choices a centred overlay
    val place = if (decision != null && flows.answering != null && !flows.targeting && !flows.paying) PromptPlace.Choice else PromptPlace.Board
    AboveHand(handRow(scene, viewer), place) {
        CompositionLocalProvider(LocalChoiceFrame provides ChoiceFrame(viewer)) {
        when {
            decision != null && flows.answering != null -> when {
                flows.targeting -> flows.pick?.let { pick ->
                    FlowTargetBar(flows, flow, play, pick, decisionSource(flows, flow, play, viewer, decision), names)
                }
                // STK-049 (owner playtest 2026-11-09): a payment while something resolves is made on the table, its bar slim
                flows.paying -> flows.payment?.let { payment ->
                    FlowPayBar(flows, flow, play, decision, payment, decisionSource(flows, flow, play, viewer, decision))
                }
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
fun openingDecision(decision: Decision?): Boolean =
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
                hold = { inspector.hold(item.card, it) }, moved = inspector::moved), small = true)
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
            .choiceCardGestures(card.key, tap = {}, hold = { inspector.hold(card, it) }, moved = inspector::moved), small = true)
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
fun observerWhat(flows: SharedFlowController, flow: SharedShown, play: TableBoard, decision: Decision): String =
    commonSource(decision)?.let { sourceName(flows, flow, play, decision, it) }
        ?: if (decision is Decision.May) "a choice" else choiceWhat(decision)

/** The card an open choice comes from, drawn on the other player's waiting bar (I-D48); null when none is shown. */
private fun observedCard(flows: SharedFlowController, flow: SharedShown, play: TableBoard, viewer: String, decision: Decision): ChoiceCardData? {
    val source = commonSource(decision) ?: return null
    val card = (if (source is ObjectId.StackItem) flow.state.stackItems[source]?.source else source) ?: return null
    return choiceCardOf(flows, play, viewer, card).takeIf { it.cardId != null }
}

/** The decision's source, or the one source all the triggers to order share (UI review round 2 N10). */
private fun commonSource(decision: Decision): ObjectId? = decision.source
    ?: (decision as? Decision.OrderTriggers)?.triggers?.map { it.source }?.distinct()?.singleOrNull()

/** The Stack item an open choice belongs to: named by the decision, or the resolving top of the Stack from its source; a Stack
 * item that isn't there (MDLPM7, D3: a copy asked about before it is put on the Stack) is the resolving top, never nothing. */
private fun decisionItem(flow: SharedShown, decision: Decision): StackItem? =
    decision.source?.let { source -> flow.state.stackItems[source] ?: flow.top?.takeIf { it.source == source || it.id == source }
        ?: flow.top?.takeIf { source is ObjectId.StackItem }?.also {
            AppLog.warn("decision", "Choice ${decision.id.value} names Stack item $source, which isn't on the Stack: about the resolving ${it.id}")
        } }

/**
 * Where an open choice comes from (STK-044), drawn at the right of its panel or target bar: an ability as its Stack token
 * (STK-046: its source's art, its marker; holding it inspects its tile alone when it is on the Stack, STK-050), a
 * spell or permanent as its card (covered when the viewer may not see it), the one source all the triggers to order share
 * (UI review round 2 N10); null when it has none (a discard to hand size, the legend rule).
 */
fun decisionSource(flows: FlowQuestions, flow: SharedShown, play: TableBoard, viewer: String, decision: Decision): ChoiceCardData? {
    if (decision is Decision.OrderTriggers) return commonSource(decision)?.let { choiceCardOf(flows, play, viewer, it) }?.takeIf { it.cardId != null }
    val item = decisionItem(flow, decision)
    // MDLPM7: an ability on the Stack (a copy too) is its entry's token as the Stack draws it: the source's face, its copy marker
    stackEntrySource(flow, play, viewer, item)?.let { return it }
    val source = item?.source ?: decision.source?.takeIf { it !is ObjectId.StackItem } ?: return null
    val card = choiceCardOf(flows, play, viewer, source).takeIf { it.cardId != null && it.shown }
        // An ability whose source has left since (a fetch land sacrificed to activate it, STK-056): its Stack entry
        ?: return stackEntrySource(flow, play, viewer, item)
            ?: choiceCardOf(flows, play, viewer, source).takeIf { it.cardId != null }
    if (item == null) {
        // An ability being put on the Stack (a trigger's targets and modes, CR 603.3d): its source's card asks
        val ability = when (val c = flow.state.pending?.continuation) {
            is Continuation.PlacingTriggers -> (c.current?.what as? Announced.Trigger)?.trigger?.takeIf { it.source == source }?.ability
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
    return card.copy(key = entry ?: "ability/${item.id}", ability = StackAbilityShown(triggered, card.name, itemText(flows, play, item).orEmpty().trim(),
        copy = item.copyOf != null))
}

/** The ability [item] as its entry on the Stack shows it (STK-046: its source card as the table keeps it, the
 * ability's text), or null when it is no ability on the Stack or [viewer] may not see its source. */
private fun stackEntrySource(flow: SharedShown, play: TableBoard, viewer: String, item: StackItem?): ChoiceCardData? {
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
private fun DecisionPanel(flows: SharedFlowController, flow: SharedShown, play: TableBoard, names: Map<String, String>,
    decision: Decision, forOther: Boolean, otherName: String) {
    val viewer = LocalChoiceFrame.current.viewer
    val inspector = remember(decision.id) { ChoiceInspector() }
    val item = decisionItem(flow, decision)
    val sourceCard = item?.source ?: decision.source?.takeIf { it !is ObjectId.StackItem }
    val source = decisionSource(flows, flow, play, viewer, decision)
    val about = remember(decision.id, flow.state) { aboutObjects(flows, flow, decision) }
    LaunchedEffect(decision.id) {
        if (about.isNotEmpty() || item != null) AppLog.info("decision", "Choice ${decision.id.value} (${decision.kind}) about " +
            about.joinToString(prefix = "[", postfix = "]") { "${flows.atwCard(it) ?: it}${if (flows.shown(it)) "" else " (hidden)"}" } +
            " of item ${item?.id}: event ${item?.event?.objects.orEmpty().flatMap { it.ids }}, targets ${item?.targets?.values?.flatten()}, " +
            "named by its resolution ${item?.let { SharedFlow.resolutionObjects(flow.state, it.id) }.orEmpty()}")
    }
    DecisionPanelContent(flows, flows.hidden, flows::toggleHidden, play, names, decision, source,
        askedLine(flows, flow, play, decision, source), about, otherName.takeIf { forOther }, copy = forCopy(flow, decision))
}

/**
 * STK-178 (owner 2026-11-21, E12): whether [decision] is asked for a copy of its source (CR 707.10c: a copy's new targets are
 * chosen before it is put on the Stack, so the question names the spell or ability it copies, still on the Stack under what
 * resolves), or its source is itself a copy of a spell: its panel then names it "Copy of ...".
 */
private fun forCopy(flow: SharedShown, decision: Decision): Boolean {
    val item = decisionItem(flow, decision) ?: return false
    if (item.kind is StackItemKind.SpellCopy) return true
    val source = decision.source as? ObjectId.StackItem ?: return false
    val top = flow.top ?: return false
    return source in flow.state.stackItems && top.id != source
}

/**
 * STK-030, UI review #5: the objects an open choice of a resolution or a trigger is about ("About:"): what it triggered on
 * and its targets, then what its resolution has named so far that the viewer may see (owner playtest 2026-11-19, N18,
 * problem report MDLPM7: "You may cast that card …" names the card its earlier step exiled), never its source nor the
 * choice's own options and other cards (drawn with the choice already, STK-224); one card once.
 */
private fun aboutObjects(flows: SharedFlowController, flow: SharedShown, decision: Decision): List<ObjectId> {
    val item = decisionItem(flow, decision) ?: return emptyList()
    val sourceCard = item.source ?: decision.source?.takeIf { it !is ObjectId.StackItem }
    val options: Set<ObjectId> = when (decision) {
        // STK-224: the group's cards it can't choose show with the choice ("Other cards"), not as what it is about
        is Decision.ChooseObjects -> (decision.options + decision.others).toSet()
        // STK-229: a pile choice's cards show in its piles (a face-down pile's as backs), not as what it is about
        is Decision.ChooseOption -> decision.piles?.let { (it.first + it.second).toSet() }.orEmpty()
        is Decision.Targets -> decision.options.filterIsInstance<TargetRef.Object>().map { it.id }.toSet()
        else -> emptySet()
    }
    val named = SharedFlow.resolutionObjects(flow.state, item.id).filter { it !in options && it != item.id && flows.shown(it) }
    return ((item.event?.objects.orEmpty().flatMap { r -> r.ids } +
        item.targets.values.flatten().filterIsInstance<TargetRef.Object>().map { t -> t.id } + named).distinct() - setOfNotNull(sourceCard))
        // One card once: an object that changed zones is the same card (its earlier object can't be shown any more)
        .groupBy { flows.atwCard(it) ?: it.toString() }.filterKeys { it != sourceCard?.let(flows::atwCard) }
        .values.map { same -> same.firstOrNull(flows::shown) ?: same.first() }
}

/**
 * STK-030 (owner playtest 2026-11-06): the sentence an open question of a resolution or a trigger asks about, shown under
 * its title without holding: an ability's whole text (a keyword's trigger as its keyword's printed line), a spell's
 * sentence of what is chosen ([askedSentence]); null where the question's own words are that sentence ([hasOwnText]) or
 * the viewer may not see the source.
 */
private fun askedLine(flows: FlowQuestions, flow: SharedShown, play: TableBoard, decision: Decision, source: ChoiceCardData?): String? {
    if (hasOwnText(decision)) return null
    val item = decisionItem(flow, decision)
    val sourceCard = item?.source ?: decision.source?.takeIf { it !is ObjectId.StackItem }
    if (sourceCard?.let(flows::shown) == false) return null
    if (item != null && (item.kind is StackItemKind.Activated || item.kind is StackItemKind.Triggered)) return itemText(flows, play, item)
    // An ability being put on the Stack (its targets and modes, CR 603.3d): its token carries its text
    source?.ability?.text?.takeIf { it.isNotBlank() }?.let { return it }
    return askedSentence(sourceCard?.let { rulesTextOf(play, flows.atwCard(it)) }, decision)
}

/**
 * An open choice's panel docked above the hand ([DecisionPanel]): its title saying only what the choice is (I-D47,
 * STK-030) with its [source] at the right (STK-044), "Choosing for <name>" when [forOther] names the player chosen for,
 * the sentence it asks about ([line]), the cards it is about ([about]), a short instruction, then the choice itself.
 * STK-178 (owner 2026-11-21, E12): beside the source, under the title, its name and an ability's whole text ([copy]: named as
 * the copy the choice is for).
 */
@Composable
fun DecisionPanelContent(questions: FlowQuestions, hidden: Boolean, toggleHidden: () -> Unit, play: TableBoard,
    names: Map<String, String>, decision: Decision, source: ChoiceCardData?, line: String?, about: List<ObjectId> = emptyList(),
    forOther: String? = null, copy: Boolean = false) {
    val viewer = LocalChoiceFrame.current.viewer
    val inspector = remember(decision.id) { ChoiceInspector() }
    // UI review round 2 N3: the choice's own buttons sit in the dock's bottom row beside the eye, as in the cast panel
    val slot = remember(decision.id) { DockSlot() }
    // STK-049 (owner gallery review, 2026-11-06): a "you may pay" of mana is its payment, with "Don't pay" beside Pay
    val payment = questions.paymentAfter(decision)
    val askedLine = if (payment != null && decision is Decision.May) decision.text else line
    val words = ChoiceSourceWords.of(source, copy)
    // E12: an ability's whole text shows once, also where it is the sentence the question asks about
    val abilityText = words?.text?.takeIf { it != askedLine?.trim() }
    // STK-181 (owner 2026-11-22, F1): a question that only repeats clauses of the ability text above it isn't shown
    val shownLine = askedLine?.takeUnless { QuestionLine.repeats(it, abilityText) }
    // F1: nor is a "you may" of the choice itself (OpenSection's) that repeats them
    val ownRepeats = decision is Decision.May && payment == null && QuestionLine.repeats(decision.text, abilityText)
    LaunchedEffect(decision.id) {
        AppLog.info("decision", "Choice panel of ${decision.id.value} (${decision.kind}): from ${words?.name ?: "no source named"}" +
            (if (copy) " (for a copy)" else "") + ", ability text ${if (words?.text == null) "none" else if (abilityText == null) "as its question" else "shown"}" +
            (if (askedLine != null && shownLine == null || ownRepeats) ", question repeats the ability text: not shown" else ""))
    }
    AutomatumDockPanel("Automatum choice", hidden, toggleHidden, "Automatum panel", buttons = { slot.buttons?.invoke(this) }) {
        CompositionLocalProvider(LocalDockSlot provides slot) {
        // I-D47, STK-030: the title says only what the choice is ("Scry 2"); its source is the card at the right
        ChoiceHead(payment?.let(::choiceWhat) ?: choiceWhat(decision), source, inspector) {
            if (forOther != null) Text("Choosing for $forOther", fontSize = 12.sp, fontWeight = FontWeight.Medium, color = AutomatumViolet)
            if (words != null) ChoiceSourceName(words.name)
            abilityText?.let {
                CardText(it, Modifier.semantics { contentDescription = "Ability: $it" }, color = Color(0xff4b5a5f),
                    style = androidx.compose.ui.text.TextStyle(fontSize = 11.sp, lineHeight = 14.sp))
            }
            shownLine?.let {
                CardText(it, Modifier.semantics { contentDescription = "Question: $it" }, color = Color(0xff4b5a5f),
                    style = androidx.compose.ui.text.TextStyle(fontSize = 11.sp, lineHeight = 14.sp))
            }
        }
        if (about.isNotEmpty()) AboutRow("About", about.map { AboutItem.Card(choiceCardOf(questions, play, viewer, it)) }, inspector)
        (if (payment != null) PAY_OR_DECLINE else choiceHint(decision))?.let { ChoiceHintText(it) }
        CompositionLocalProvider(LocalChoiceFrame provides LocalChoiceFrame.current.copy(headed = true, shownText = abilityText)) {
            when {
                decision is Decision.ManaPayment -> {
                    ManaSection(questions, questions, play, decision)
                    val onMain = payOnMain(questions, decision, decision)
                    if (!onMain) DockButtons { ChoiceButton("Pay", Icons.Outlined.Check, enabled = questions.paid, primary = true) { questions.payMana() } }
                }
                payment != null -> {
                    ManaSection(questions, questions, play, payment)
                    val onMain = payOnMain(questions, decision, payment)
                    DockButtons {
                        ChoiceButton("Don't pay", Icons.Outlined.Close) { questions.answer(Answer.YesNo(false)) }
                        if (!onMain) ChoiceButton("Pay", Icons.Outlined.Check, enabled = questions.paid, primary = true) { questions.payMana() }
                    }
                }
                else -> OpenSection(questions, questions, play, names, decision)
            }
        }
        ChoiceInspection(inspector, play, viewer)
        }
    }
}

/** STK-049, TRN-063 (owner playtest 2026-11-09): Pay on the main button (greyed with why while the sources chosen don't pay); false
 * where there is no main button (the caller shows its own). */
@Composable
private fun payOnMain(questions: FlowQuestions, decision: Decision, payment: Decision.ManaPayment): Boolean =
    MainButtonAction(MainAction("Pay", Icons.Outlined.Check, refusal = notPaying(payment).takeIf { !questions.paid },
        tooltip = "Pay ${payment.cost}") {
        AppLog.info("decision", "Pay on the main button for ${decision.id.value}: ${questions.mana.size} sources")
        questions.payMana()
    })

/** TRN-063: why Pay on the main button is greyed. */
fun notPaying(payment: Decision.ManaPayment) = "Not paid yet: produce the mana for ${payment.cost} on the board."

/**
 * STK-049 (owner playtest 2026-11-09): a mana payment while something resolves ("unless its controller pays {1}", "you may pay {1}",
 * a cost to attack or block), made on the table as a cast's payment is (STK-024): the slim bar titled "Pay {1}" over the sentence it
 * asks about, "Paid" or "Not paid yet" beside the title, pool mana and life as chips, "Don't pay" where it may be declined, the
 * source at the right; the sources are tapped on the table (Autotap's already chosen when it is on), and Pay is the main button.
 */
@Composable
private fun FlowPayBar(flows: SharedFlowController, flow: SharedShown, play: TableBoard, decision: Decision, payment: Decision.ManaPayment,
    source: ChoiceCardData?) {
    val inspector = remember(decision.id) { ChoiceInspector() }
    // STK-178 (owner 2026-11-21, E12): the source named under the title, and an ability's whole text as the sentence, as a target
    // bar draws it
    val words = ChoiceSourceWords.of(source, forCopy(flow, decision))
    val line = words?.text ?: if (decision is Decision.May) decision.text else askedLine(flows, flow, play, decision, source)
    AutomatumDockBar("Pay on the table", eye = true) {
        TargetBarTitle(choiceWhat(payment), line, if (flows.paid) "Paid" else "Not paid yet", source = words?.name)
        // Pool mana and life, which the table can't show, as chips
        // STK-024 (owner 2026-11-14): a source whose cost uses other cards is tapped on the table, never a chip
        val others = org.asyncttwiz.automatum.ManaPicks.groups(payment).filter { group -> flows.tableSource(group.first()) == null &&
            !org.asyncttwiz.automatum.ManaPicks.usesObjects(group.first()) }
        for (group in others) {
            val used = flows.mana.firstOrNull { it in group }
            val use = used ?: group.first()
            // STK-049 (owner problem report 2026-11-22): a pool mana is its symbol in its colour on a small card, as the sources are
            if (use is arcanum.engine.decision.ManaUse.Pool) PoolManaTile(poolManaLook(flows, play, payment.player,
                LocalChoiceFrame.current.viewer, use), used != null, { flows.toggleMana(group) }, Modifier.padding(start = 4.dp, top = 2.dp, bottom = 2.dp))
            else {
                val label = org.asyncttwiz.automatum.ManaWays.label(use)
                FilterChip(used != null, { flows.toggleMana(group) },
                    modifier = Modifier.padding(start = 4.dp).semantics { contentDescription = "Mana source $label${if (used != null) ", chosen" else ""}" },
                    label = { CardText(label, style = androidx.compose.ui.text.TextStyle(fontSize = 12.sp)) },
                    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = AutomatumViolet.copy(alpha = .18f)))
            }
        }
        if (decision is Decision.May) BarButton("Don't pay", Icons.Outlined.Close) {
            AppLog.info("decision", "Don't pay tapped for ${decision.id.value}")
            flows.answer(Answer.YesNo(false))
        }
        if (!payOnMain(flows, decision, payment)) BarButton("Pay", Icons.Outlined.Check, enabled = flows.paid) { flows.payMana() }
        if (source != null) ChoiceSource(source, inspector, Modifier.padding(start = 4.dp, end = 6.dp, top = 2.dp, bottom = 2.dp))
        ChoiceInspection(inspector, play, LocalChoiceFrame.current.viewer)
    }
}

/** Targets of a trigger, picked on the table as for a cast (I-D5, I-D6); there is no cancelling a shared flow. The title
 * says only the choice, the [source] sits at the right (STK-044). */
@Composable
private fun FlowTargetBar(flows: SharedFlowController, flow: SharedShown, play: TableBoard, pick: TablePick,
    source: ChoiceCardData?, names: Map<String, String>) {
    // D1: a choice of battlefield permanents (an edict's sacrifice, "choose a creature") is picked as targets are
    // (owner 2026-11-11, which pick is which): the whole text, the phrase picked now shining and earlier picks named in it
    val pieces = flows.pickText(play)?.let { StackTargetText.pickPieces(play, it, flow::ref, LocalChoiceFrame.current.viewer, names) }
    TablePickBar(pick, askedLine(flows, flow, play, pick.decision, source), pieces, flows.picked.size, flows.pickDone, flows::confirmTargets,
        source, play, "decision", copy = forCopy(flow, pick.decision))
}

// ---- Words





/** The text of ability [item] (STK-046, STK-050): a keyword's trigger as its keyword's printed line on its source
 * ([org.asyncttwiz.automatum.StackAbilityText.of]). */
fun itemText(flows: FlowQuestions, play: TableBoard, item: StackItem): String? =
    org.asyncttwiz.automatum.StackAbilityText.of(item, item.source?.let { rulesTextOf(play, flows.atwCard(it)) })

/** The printed rules text of ATW card [cardId]'s face on [play]'s board, when it has one. */
fun rulesTextOf(play: TableBoard, cardId: String?): String? {
    val card = play.board.cards.firstOrNull { it.id == cardId } ?: return null
    return play.definitions.firstOrNull { it.digest == card.definition }?.definition?.faces
        ?.let { faces -> faces.firstOrNull { it.faceId == card.faceId } ?: faces.firstOrNull() }?.rulesText
}

private fun faceName(play: TableBoard, card: PlayCard): String? =
    play.definitions.singleOrNull { it.digest == card.definition }?.definition?.faces
        ?.let { faces -> faces.singleOrNull { it.faceId == card.faceId } ?: faces.firstOrNull() }?.shownName

/** The decision's source ([source], its own by default) by name: the card or ability it is about. */
private fun sourceName(flows: SharedFlowController, flow: SharedShown, play: TableBoard, decision: Decision,
    source: ObjectId? = decision.source): String {
    source ?: return sourcelessName(decision)
    if (source is ObjectId.StackItem) flow.state.stackItems[source]?.source?.let { return objectName(flows, play, it) }
    return objectName(flows, play, source)
}

/** What names the source of [decision] when it has none: triggers of several sources are "Triggers" by design; any other
 * choice is a fault, reading "Automatum" (LOB-090). */
fun sourcelessName(decision: Decision): String = if (decision is Decision.OrderTriggers) "Triggers"
    else org.asyncttwiz.protocol.Faults.placeholder("SharedFlowPanel", "choice without a source to name", "Automatum",
        mapOf("decision" to decision.id.value, "kind" to (decision::class.simpleName ?: "")))

/** A player an open choice's panel names: "You" for [viewer], else their name; without one "The other player", reported
 * (LOB-090). */
fun flowPlayerName(player: String, viewer: String, names: Map<String, String>): String =
    if (player == viewer) "You" else org.asyncttwiz.automatum.PlayerNames.of(names, player, "The other player", "SharedFlowPanel")

/** Whether [decision]'s panel shows the sentence it asks about itself (STK-030): a "you may" its sentence, a search its
 * title (STK-056), a skipped step its text, triggers to order their own lines; every other question of an ability shows
 * the ability's text under its title. */
private fun hasOwnText(decision: Decision) =
    decision is Decision.May || decision is Decision.Manual || decision is Decision.OrderTriggers ||
        (decision is Decision.ChooseObjects && ObjectsShape.of(decision).search)

/** What the decision asks, in a few words: the card's sentence when it has one. */
private fun prompt(flows: SharedFlowController, flow: SharedShown, play: TableBoard, decision: Decision): String = when (decision) {
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

/** STK-049: the instruction of a cost one may pay or decline. */
const val PAY_OR_DECLINE = "Tap the sources to pay with, then Pay; or Don't pay."

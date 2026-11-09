package org.asyncttwiz.android

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.decision.ManaUse
import arcanum.engine.input.Input
import arcanum.engine.state.GameState
import arcanum.engine.state.ObjectId
import arcanum.engine.state.TargetRef
import org.asyncttwiz.automatum.CastFlow
import org.asyncttwiz.automatum.ManaPicks
import org.asyncttwiz.protocol.*

/**
 * The cast panel (integration spec I-D6, §3 I2, I-D47): shown only while a cast has a real choice (modes, X, optional or
 * alternative costs, targets; with Autotap off also the mana). It spans the table's width docked exactly above the
 * viewer's hand, with the card small at its right as in every Automatum panel (STK-044; hold: Inspect) and its
 * sections in the rules order (CR 601.2b–h). Targets are chosen on the table: the panel shrinks to a slim bar while legal
 * targets glow. The eye (I-D10) stays where it is: the rest of the panel fades away around it and comes back. Back and
 * Undo drop the whole cast (STK-025); there is no Cancel.
 */
@Composable
internal fun CastPanelHost(toolkit: TableToolkitController, play: TableBoard, scene: TableScene?, names: Map<String, String>) {
    val cast = toolkit.cast
    // Targets are chosen: a pile opened to pick them from closes again once they are (i12review #14)
    LaunchedEffect(cast.targeting || toolkit.flows.targeting) { if (!cast.targeting && !toolkit.flows.targeting) toolkit.targetingEnded() }
    val flow = cast.flow ?: return
    // The board the player acts on (never one reviewed in the timeline, i12review #10) may have moved on
    LaunchedEffect(IdentityKey(play.board)) { cast.follow() }
    BackHandler { cast.cancel() }
    val stack = scene?.stackRegion ?: scene?.items?.firstOrNull { it.zone == PlayZone.STACK && it.kind == TableItemKind.PILE }?.bounds
    // The slim bar sits over the own hand row (hand cards are never targets), so the battlefield stays free to tap
    val handRow = scene?.items?.firstOrNull { it.kind == TableItemKind.HAND && it.owner == toolkit.viewer }?.bounds
    // STK-044: the card cast (an ability as its token) at the right of the panel and of its target bar
    val source = castSource(play, toolkit.viewer, flow, cast.abilityText)
    if (cast.targeting) {
        // The target bar stays while a target is picked from the Stack's list (STK-048)
        AboveHand(handRow) { TargetBar(cast, flow, play, source, toolkit.viewer) }
        return
    }
    // STK-024: the panel's height says which of the sources paying lie under it
    var panelHeight by remember { mutableFloatStateOf(0f) }
    val density = LocalDensity.current
    // I-D48: the choices' cards know who looks at them (outlines, Inspect)
    DockedAboveHand(handRow) {
        Box(Modifier.onSizeChanged { panelHeight = with(density) { it.height.toDp().value } }) {
            CompositionLocalProvider(LocalChoiceFrame provides ChoiceFrame(toolkit.viewer)) {
                CastPanel(cast, flow, play, names, source) { card -> scene == null || handRow == null || scene.inView(card, handRow.y - panelHeight) }
            }
        }
    }
}

/** The source of a cast (STK-044): the card cast, or for an activation ([abilityText] its text) the ability as its
 * Stack token would show it (STK-046), the source's art with the marker. */
internal fun castSource(play: TableBoard, viewer: String, flow: CastFlow, abilityText: String?): ChoiceCardData {
    val card = choiceCard(play, viewer, "source/${flow.cardId}", flow.cardId, shown = true, fallback = CastController.cardName(play, flow.cardId))
    return if (abilityText == null) card else card.copy(ability = StackAbilityShown(triggered = false, source = card.name, text = abilityText))
}

/** I-D47: the table's full width, its bottom exactly on the top of the viewer's hand row [hand] (the table's bottom
 * without one), at most up to the table's top. */
@Composable
private fun DockedAboveHand(hand: TableBounds?, content: @Composable () -> Unit) {
    val docked = dockedPanelReporter()
    Layout(content = content, modifier = Modifier.fillMaxSize()) { measurables, constraints ->
        val bottom = (hand?.y?.dp?.roundToPx() ?: constraints.maxHeight).coerceIn(0, constraints.maxHeight)
        val placeable = measurables.firstOrNull()?.measure(Constraints(minWidth = constraints.maxWidth, maxWidth = constraints.maxWidth,
            maxHeight = bottom))
        // D2: the table pans past the open panel
        docked((placeable?.height ?: 0).toDp().value)
        layout(constraints.maxWidth, constraints.maxHeight) {
            placeable?.place(0, bottom - placeable.height)
        }
    }
}

/**
 * What the panel sections need of an open flow, private (a cast, [CastController]) or shared (resolution and triggers,
 * [SharedFlowController]): the engine's state for names, answering, the engine's listed answers, the mana picked for a
 * payment, and whether the viewer may be shown an object (I-D27: hidden cards stay hidden, also to a player choosing
 * for the other one).
 */
internal interface FlowQuestions {
    val engine: GameState
    fun atwCard(id: ObjectId): String?
    fun answer(answer: Answer): Boolean
    /** Answers with [answer] and, in the same step, the orders the engine asks next of the same cards ([then], I-D48: a
     * two-area choice's order); where that isn't possible, only [answer] (the orders are then asked as usual). */
    fun answerWith(answer: Answer, then: List<Answer.Objects>): Boolean = answer(answer)
    fun listedAnswers(): List<Answer>
    /** CR 616.1 (STK-030): answers which replacement applies first with the player's preference list [preference] (all of
     * [decision]'s options, the first applying first; [org.asyncttwiz.automatum.ReplacementPreference]); where later
     * questions can't be answered with it in the same step, only the first. */
    fun answerReplacements(decision: Decision.ChooseReplacement, preference: List<arcanum.engine.decision.ReplacementOption>): Boolean =
        answer(Answer.Replacement(preference.first().index))
    /** STK-049: the payment a "you may pay {mana}" question [decision] goes on to, asked in one panel with "Don't pay"; null
     * when it has none (or isn't such a question). */
    fun paymentAfter(decision: Decision): Decision.ManaPayment? = null
    /** Castability §3: what may be picked in the open choice of cards in a panel with [chosen] picked, given to [done] (null: anything
     * the decision offers), worked out off the main thread. */
    fun selectionOf(chosen: List<ObjectId>, done: (arcanum.engine.Selection?) -> Unit) = done(null)
    /** Pays the open payment (or says yes to the open "you may pay" and pays its [paymentAfter]) with [mana]. */
    fun payMana(): Boolean = answer(Answer.ManaPayment(mana))
    val mana: List<ManaUse>
    val paid: Boolean
    fun toggleMana(group: List<ManaUse>)
    fun shown(id: ObjectId): Boolean = true
    /** The most a number asked ([Decision.Announce], [Decision.ChooseNumber]) may be: the decision's own most, or (an X
     * of a cast, STK-023) the most the cast can still be paid with, below its least when even that can't be paid; null
     * when there is no most. */
    fun most(decision: Decision): Int? = when (decision) {
        is Decision.Announce -> decision.max
        is Decision.ChooseNumber -> decision.max
        else -> null
    }
}

/**
 * STK-023 (owner playtest 2026-11-05 evening): in the cast panel a choice built up in place (X, several modes, a
 * division) has no OK of its own: the panel's Cast answers it and goes on ([ChoiceOk]). [answer] answers the open
 * decision [decision], null while the choice isn't complete.
 */
internal class CastOk(
    /** The panel's main button ("Cast", "Activate", "Play"), named in the choices' hints instead of OK. */
    val label: String = "Cast",
) {
    var decision by mutableStateOf<arcanum.engine.decision.DecisionId?>(null); private set
    var answer by mutableStateOf<(() -> Unit)?>(null); private set
    fun set(decision: arcanum.engine.decision.DecisionId, answer: (() -> Unit)?) { this.decision = decision; this.answer = answer }
    fun clear(decision: arcanum.engine.decision.DecisionId) { if (this.decision == decision) { this.decision = null; answer = null } }
}

/** The cast panel the composed choice sits in ([CastOk]), null elsewhere. */
internal val LocalCastOk = compositionLocalOf<CastOk?> { null }

/**
 * A choice's OK: its own button (enabled when [enabled]) answering with [answer]; in the cast panel ([LocalCastOk]) no
 * button: the panel's Cast answers it (STK-023).
 */
@Composable
internal fun ChoiceOk(decision: Decision, enabled: Boolean, answer: () -> Unit) {
    val ok = LocalCastOk.current
    if (ok == null) { ChoiceButton("OK", Icons.Outlined.Check, enabled = enabled, primary = true, onClick = answer); return }
    SideEffect { ok.set(decision.id, answer.takeIf { enabled }) }
    DisposableEffect(ok, decision.id) { onDispose { ok.clear(decision.id) } }
}

/** Left of the Stack (or of the table's right edge), centred on it vertically (or on [middle], dp), inside the table.
 * [dock] (the viewer's hand row, finalreview-combat #2): in portrait the content docks just above it instead, across
 * the table's middle, clear of the combat area's panel and its preview. */
@Composable
internal fun CastPanelPlacement(stack: TableBounds?, middle: Float?, dock: TableBounds? = null, content: @Composable () -> Unit) {
    Layout(content = content, modifier = Modifier.fillMaxSize()) { measurables, constraints ->
        val margin = 8.dp.roundToPx()
        val docked = dock != null && constraints.maxHeight > constraints.maxWidth
        val width = minOf((if (docked) 420 else 300).dp.roundToPx(), constraints.maxWidth - 2 * margin).coerceAtLeast(0)
        val placeable = measurables.firstOrNull()?.measure(Constraints(maxWidth = width,
            maxHeight = (constraints.maxHeight * .78f).toInt().coerceAtLeast(0)))
        layout(constraints.maxWidth, constraints.maxHeight) {
            placeable ?: return@layout
            if (docked) {
                val above = dock!!.y.dp.roundToPx() - margin
                placeable.place(((constraints.maxWidth - placeable.width) / 2).coerceAtLeast(margin),
                    (above - placeable.height).coerceIn(margin, (constraints.maxHeight - placeable.height - margin).coerceAtLeast(margin)))
                return@layout
            }
            val stackX = stack?.x?.dp?.roundToPx() ?: constraints.maxWidth
            val centerY = (middle ?: stack?.center?.y)?.dp?.roundToPx() ?: (constraints.maxHeight / 2)
            val x = (stackX - placeable.width - margin).coerceIn(margin, (constraints.maxWidth - placeable.width - margin).coerceAtLeast(margin))
            val y = (centerY - placeable.height / 2).coerceIn(margin, (constraints.maxHeight - placeable.height - margin).coerceAtLeast(margin))
            placeable.place(x, y)
        }
    }
}

@Composable
private fun TargetBar(cast: CastController, flow: CastFlow, play: TableBoard, source: ChoiceCardData, viewer: String) {
    val pick = cast.pick ?: return
    val decision = pick.decision
    val inspector = remember { ChoiceInspector() }
    // UI review #4, #9: docked above the hand like every Automatum panel, never over it; STK-044: the title says only the
    // choice, the card it is for sits at the right. D1: a cost of battlefield permanents (a sacrifice) is picked the same way
    AutomatumDockBar(pick.description, eye = true) {
        // STK-047 (owner playtest 2026-11-06): the sentence the targets are for, under the title
        TargetBarTitle(pick.title, cast.abilityText ?: askedSentence(rulesTextOf(play, flow.cardId), decision))
        if (pick.max != 1 || pick.min == 0) TextButton(cast::confirmTargets, enabled = cast.pickDone) {
            IconText(Icons.Outlined.Check, if (cast.picked.isEmpty()) "None" else "Done")
        }
        ChoiceSource(source, inspector, Modifier.padding(start = 4.dp, end = 6.dp, top = 2.dp, bottom = 2.dp))
        ChoiceInspection(inspector, play, viewer)
    }
}

/** I-D48: a button's icon beside its words. */
@Composable
internal fun IconText(icon: ImageVector, text: String) {
    Icon(icon, null, Modifier.size(18.dp))
    Spacer(Modifier.width(4.dp))
    Text(text)
}

/**
 * The panel docked above the hand (I-D47): its title saying what is done with which card ([castTitle]; an ability's text
 * under it) with the card at the right as in every Automatum panel ([source], STK-044), the sections, then a bottom row
 * with the eye at its left and Cancel and Cast at its right. The eye never moves: hiding fades the panel (its
 * background, sections and buttons) away around it, leaving only the eye.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CastPanel(cast: CastController, flow: CastFlow, play: TableBoard, names: Map<String, String>, source: ChoiceCardData, inView: (String) -> Boolean) {
    val inspector = remember { ChoiceInspector() }
    // STK-023: a choice built up in place is answered by Cast (no OK of its own)
    val ok = remember(flow.activation, flow.begin is Input.PlayLand) { CastOk(actionLabel(flow)) }
    val shown by animateFloatAsState(if (cast.hidden) 0f else 1f, tween(180), label = "Cast panel")
    Column(Modifier.fillMaxWidth()
        .then(if (cast.hidden) Modifier else Modifier.pointerInput(Unit) {}.semantics { contentDescription = "Cast panel" })
        .drawBehind {
            drawRect(Color.White.copy(alpha = shown))
            drawLine(AutomatumViolet.copy(alpha = shown), Offset(0f, 0f), Offset(size.width, 0f), 2.dp.toPx())
        }) {
        AnimatedVisibility(!cast.hidden, enter = fadeIn(tween(180)) + expandVertically(tween(180), expandFrom = Alignment.Bottom),
            exit = fadeOut(tween(180)) + shrinkVertically(tween(180), shrinkTowards = Alignment.Bottom)) {
            Column(Modifier.padding(start = 10.dp, end = 10.dp, top = 8.dp).heightIn(max = 360.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(6.dp)) {
                // A long name wraps onto a second line, then is cut with …
                ChoiceHead(castTitle(flow, source.name), source, inspector, titleLines = 2) {
                    // STK-023 (owner playtest 2026-11-06): an ability's text; for a spell the sentence its open choice asks
                    // about (an X, a division, a colour), never its modes' (they are the chips) or the payment's
                    (cast.abilityText ?: flow.pending?.takeIf { it !is Decision.Modes && it !is Decision.ManaPayment && it !is Decision.May }
                        ?.let { askedSentence(rulesTextOf(play, flow.cardId), it) })?.let {
                        CardText(it, Modifier.semantics { contentDescription = "Question: $it" }, color = Color(0xff4b5a5f),
                            style = TextStyle(fontSize = 11.sp, lineHeight = 14.sp))
                    }
                }
                ChoiceInspection(inspector, play, LocalChoiceFrame.current.viewer)
                // ZON-080, ZON-082 (owner 2026-11-07): a hand card's ways and extras, as chips (its way filled violet)
                cast.hand?.let { hand ->
                    if (hand.ways.size > 1) Section("Cast as") {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            hand.ways.forEachIndexed { index, chip -> WayChip(chip, index == hand.way, { cast.handWay(index) }, {}) }
                        }
                    }
                    if (hand.extras.isNotEmpty()) Section("Extras") {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            hand.extras.forEachIndexed { index, chip ->
                                ExtraChip(chip, hand.times.getOrElse(index) { 0 }, { cast.handExtra(index) }, { cast.handLess(index) }, {})
                            }
                        }
                    }
                }
                if (cast.hand == null && cast.ways.size > 1) Section("Cast as") {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        for (way in cast.ways) FilterChip(way == flow.begin, { if (way != flow.begin) cast.way(way) },
                            label = { Text(wayLabel(play, flow, way), fontSize = 12.sp) })
                    }
                }
                flow.steps.forEachIndexed { index, (decision, answer) -> AnsweredSection(cast, flow, play, names, index, decision, answer) }
                when (val open = flow.pending) {
                    null -> Text(if (flow.activation) "Ready." else "Ready to cast.", fontSize = 12.sp)
                    // STK-024 (owner 2026-11-07): the payment is the line below
                    is Decision.ManaPayment -> Unit
                    // A hand card's panel still choosing its way: Cast goes on to the targets (D1: or the permanents a cost picks)
                    else -> cast.pick?.let { CardText("Then ${it.prompt} on the table.", style = TextStyle(fontSize = 12.sp)) }
                        ?: CompositionLocalProvider(LocalCastOk provides ok) { OpenSection(cast, cast, play, names, open) }
                }
                PaymentLine(cast, play, inView)
                // UI review round 2 #4 (I-D46 b): why the last step was refused, inside the panel rather than over the table
                cast.notice?.let { CardText(it, color = Color(0xffb3261e), style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium)) }
            }
        }
        // A fixed height: the eye stays exactly where it is whether the buttons beside it show or not
        Row(Modifier.fillMaxWidth().height(52.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = CircleShape, color = Color.White, border = BorderStroke(2.dp,
                AutomatumViolet.copy(alpha = 1f - shown)), shadowElevation = if (cast.hidden) 6.dp else 0.dp) {
                HoldTip(if (cast.hidden) "Show the cast panel" else "Hide the cast panel") { EyeButton(hidden = cast.hidden, cast::toggleHidden) }
            }
            Spacer(Modifier.weight(1f))
            AnimatedVisibility(!cast.hidden, enter = fadeIn(tween(180)), exit = fadeOut(tween(180))) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // STK-023, STK-052 (owner playtest 2026-11-05 evening): no Cancel (Undo and Back drop the cast); Cast also
                    // answers a choice built up in place (X) and goes on
                    val answering = ok.answer.takeIf { ok.decision != null && ok.decision == flow.pending?.id }
                    // STK-024 (owner 2026-11-07): greyed while the chosen mana doesn't pay (the payment line says why)
                    Button({ answering?.invoke() ?: cast.cast() }, enabled = !cast.busy && cast.paid && (answering != null || flow.complete ||
                        flow.pending is Decision.ManaPayment || (cast.choosing && cast.pick != null)),
                        colors = ButtonDefaults.buttonColors(containerColor = AutomatumViolet)) {
                        IconText(if (flow.activation) Icons.Outlined.Bolt else Icons.Outlined.AutoAwesome, actionLabel(flow))
                    }
                }
            }
        }
    }
}

/** I-D48: an icon-only control without a hold action of its own says what it does on hold. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun HoldTip(description: String, content: @Composable () -> Unit) {
    TooltipBox(positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
        tooltip = { PlainTooltip { CardText(description) } }, state = rememberTooltipState()) { content() }
}

/** A section of a choice: what it is ([title]) and a short instruction ([hint], I-D47); in a panel whose own title says
 * both already ([ChoiceFrame.headed]) only its content. */
@Composable
internal fun Section(title: String, hint: String? = null, content: @Composable ColumnScope.() -> Unit) {
    val headed = LocalChoiceFrame.current.headed
    Column(verticalArrangement = Arrangement.spacedBy(if (headed) 6.dp else 2.dp)) {
        if (!headed) {
            HorizontalDivider(color = AutomatumViolet.copy(alpha = .25f))
            CardText(title, color = AutomatumViolet, style = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Bold))
            (LocalCastOk.current?.let { ok -> hint?.replace("then OK.", "then ${ok.label}.") } ?: hint)?.let { ChoiceHintText(it) }
        }
        content()
    }
}

/** A choice already made: changed in place (a toggle, a stepper), or opened again ("Change") when it is made on the
 * table. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AnsweredSection(cast: CastController, flow: CastFlow, play: TableBoard, names: Map<String, String>,
    index: Int, decision: Decision, answer: Answer) {
    when {
        decision is Decision.May && answer is Answer.YesNo -> Section(mayTitle(decision)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    CardText(decision.text, style = TextStyle(fontSize = 12.sp))
                    decision.cost?.let { CostLine(it) }
                }
                Switch(answer.yes, { cast.change(index, Answer.YesNo(it)) }, Modifier.semantics { contentDescription = decision.text })
            }
        }
        decision is Decision.Announce && answer is Answer.Number -> Section(decision.name) {
            Stepper(answer.value, decision.min, cast.most(decision)?.coerceAtLeast(answer.value)) { cast.change(index, Answer.Number(it)) }
        }
        decision is Decision.ChooseNumber && answer is Answer.Number -> Section("Number") {
            Stepper(answer.value, decision.min, cast.most(decision)) { cast.change(index, Answer.Number(it)) }
        }
        decision is Decision.Modes && answer is Answer.Modes && decision.max == 1 -> Section("Mode") {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                for (mode in decision.options) FilterChip(mode.index in answer.chosen, { cast.change(index, Answer.Modes(listOf(mode.index))) },
                    enabled = mode.available, label = { CardText(mode.text ?: "Mode ${mode.index + 1}", style = TextStyle(fontSize = 12.sp)) })
            }
        }
        else -> Section(sectionTitle(decision)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(answerLabel(cast, play, names, decision, answer), Modifier.weight(1f), fontSize = 12.sp)
                TextButton({ cast.back(index) }) { IconText(Icons.Outlined.Edit, "Change") }
            }
        }
    }
}

/** The open question (but targets, chosen on the table, and mana, [ManaSection]) of a cast or a shared flow. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun OpenSection(cast: FlowQuestions, flow: FlowQuestions, play: TableBoard, names: Map<String, String>, decision: Decision) {
    // I-D47, I-D48: each section says what the choice is with a short instruction; cards are drawn as cards, buttons
    // carry icons
    val viewer = LocalChoiceFrame.current.viewer
    val inspector = remember(decision.id) { ChoiceInspector() }
    fun card(id: ObjectId?) = id?.let { choiceCardOf(flow, play, viewer, it) }
    when (decision) {
        is Decision.May -> Section(mayTitle(decision)) {
            // STK-030 (owner playtest 2026-11-06): the sentence asked about and the cost, without holding anything
            CardText(decision.text, Modifier.semantics { contentDescription = "Question: ${decision.text}" }, style = TextStyle(fontSize = 12.sp))
            decision.cost?.let { CostLine(it) }
            DockButtons {
                ChoiceButton("No", Icons.Outlined.Close) { cast.answer(Answer.YesNo(false)) }
                ChoiceButton("Yes", Icons.Outlined.Check, enabled = decision.payable, primary = true) { cast.answer(Answer.YesNo(true)) }
            }
            if (!decision.payable) Text("It can't be paid now.", fontSize = 11.sp, color = Color(0xff4b5a5f))
        }
        is Decision.Announce, is Decision.ChooseNumber -> {
            val min = (decision as? Decision.Announce)?.min ?: (decision as Decision.ChooseNumber).min
            // STK-023 (owner playtest 2026-11-05 evening): − / + stop at the least and the most Automatum allows (an X:
            // the most the cast can be paid with)
            val max = cast.most(decision)
            var value by remember(decision.id) { mutableIntStateOf(min) }
            val payable = max == null || max >= min
            Section(choiceWhat(decision), choiceHint(decision)) {
                Stepper(value, min, max?.coerceAtLeast(min)) { value = it }
                if (!payable) Text("It can't be paid now.", fontSize = 11.sp, color = Color(0xff4b5a5f))
                OkRow(decision, payable) { cast.answer(Answer.Number(value)) }
            }
        }
        is Decision.Modes -> ModesSection(cast, decision)
        is Decision.ChooseOption -> Section(choiceWhat(decision), choiceHint(decision)) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                for (option in decision.options) ChoiceButton(option.replaceFirstChar { it.uppercase() }, Icons.Outlined.RadioButtonUnchecked) {
                    cast.answer(Answer.Option(option))
                }
            }
        }
        is Decision.ChooseColor -> Section(choiceWhat(decision), choiceHint(decision)) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                for (color in decision.among) OutlinedButton({ cast.answer(Answer.ColorChoice(color)) },
                    contentPadding = PaddingValues(start = 10.dp, end = 14.dp)) {
                    Canvas(Modifier.size(16.dp)) {
                        drawCircle(colorSwatch(color.symbol))
                        drawCircle(Color(0x66000000), style = Stroke(1f * density))
                    }
                    Spacer(Modifier.width(6.dp))
                    Text(colorName(color.symbol))
                }
            }
        }
        is Decision.ChooseObjects -> ObjectsSection(cast, flow, play, decision)
        is Decision.Divide -> {
            // Pre-filled with the engine's suggestion when it has one (combat damage: lethal to each in turn, I-D29)
            val suggested = decision.suggested.associate { it.target to it.amount }
            var shares by remember(decision.id) { mutableStateOf(decision.among.associateWith { suggested[it] ?: decision.minEach }) }
            val lethal = decision.lethal.associate { it.target to it.amount }
            Section(choiceWhat(decision), choiceHint(decision)) {
                for (target in decision.among) CardRow(card((target as? TargetRef.Object)?.id), inspector) {
                    if (target is TargetRef.Player) Icon(Icons.Outlined.Person, null, Modifier.size(24.dp), tint = MenuInk)
                    Text(targetName(flow, play, names, target) + (lethal[target]?.let { " (lethal $it)" } ?: ""),
                        Modifier.weight(1f), fontSize = 12.sp)
                    Stepper(shares.getValue(target), decision.minEach, decision.amount) { shares = shares + (target to it) }
                }
                OkRow(decision, shares.values.sum() == decision.amount) {
                    cast.answer(Answer.Division(shares.map { arcanum.engine.decision.Share(it.key, it.value) }))
                }
            }
        }
        is Decision.Manual -> Section(choiceWhat(decision), choiceHint(decision)) {
            CardText(decision.text, style = TextStyle(fontSize = 12.sp))
            // UI review round 2 #1: in a dock panel the step arrow's "Done" answers it (TRN-063); elsewhere its own button
            if (LocalDockSlot.current == null) ChoiceButton("Done", Icons.Outlined.Check, primary = true) { cast.answer(Answer.Done) }
        }
        // STK-030 (owner gallery review, 2026-11-06): drag-to-reorder lists in a suggested order, then OK
        is Decision.OrderTriggers -> OrderTriggersSection(cast, flow, play, decision)
        is Decision.LegendRule -> LegendRuleSection(cast, flow, play, decision)
        is Decision.ChooseReplacement -> ReplacementOrderSection(cast, flow, play, decision)
        // Owner gallery review (2026-11-06, bug): each player as their avatar beside their name, as on their life square
        is Decision.ChoosePlayer -> Section(choiceWhat(decision), choiceHint(decision)) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                for (player in decision.options) PlayerChoiceButton(player.value, names[player.value] ?: "A player") {
                    cast.answer(Answer.Player(player))
                }
            }
        }
        // I-D27 (final cast review #1): any card name, suggested from the device's card catalog, and every creature type,
        // never the game's own cards
        is Decision.ChooseCardName -> CardNameSection(cast, decision)
        is Decision.ChooseSubtype -> if (decision.among == null) CreatureTypeSection(cast, decision)
            else ListedSection(cast, flow, play, names, decision)
        else -> ListedSection(cast, flow, play, names, decision)
    }
    ChoiceInspection(inspector, play, viewer)
}

/** A choice's OK alone ([ChoiceOk]): in a row of buttons of its own, or in the cast panel none (its Cast answers it). */
@Composable
internal fun OkRow(decision: Decision, enabled: Boolean, answer: () -> Unit) {
    if (LocalCastOk.current != null) ChoiceOk(decision, enabled, answer) else DockButtons { ChoiceOk(decision, enabled, answer) }
}

/** Choices the panel has no control of its own for: the engine's listed answers (a card type, a basic land type). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ListedSection(cast: FlowQuestions, flow: FlowQuestions, play: TableBoard, names: Map<String, String>, decision: Decision) {
    Section(choiceWhat(decision), choiceHint(decision)) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            for (answer in flow.listedAnswers()) ChoiceButton(answerLabel(flow, play, names, decision, answer), Icons.Outlined.RadioButtonUnchecked) {
                cast.answer(answer)
            }
        }
    }
}

/**
 * Choosing modes (CR 700.2): one taps to answer when one is chosen; otherwise taps choose and unchoose, and OK sends the
 * choice. A mode that may be chosen more than once (CR 700.2d, a Season's pawprint modes, CR 700.2i) is added again by
 * each tap, shows how often it is chosen and is taken back one at a time with "−"; "Clear" takes back all. Pawprint
 * costs are on each mode, and OK waits while the pawprints chosen are over the limit (final cast review #5).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ModesSection(cast: FlowQuestions, decision: Decision.Modes) {
    var chosen by remember(decision.id) { mutableStateOf(listOf<Int>()) }
    val pawprints = chosen.sumOf { i -> decision.options.firstOrNull { it.index == i }?.pawprints ?: 0 }
    val overLimit = decision.pawprintLimit?.let { pawprints > it } == true
    // I-D48: every mode chip carries a mark (chosen or not), "−" is an icon with its tooltip on hold
    Section(choiceWhat(decision), choiceHint(decision)) {
        if (decision.repeatable) {
            for (mode in decision.options) {
                val count = chosen.count { it == mode.index }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    FilterChip(count > 0, { if (chosen.size < decision.max) chosen = chosen + mode.index },
                        modifier = Modifier.weight(1f), enabled = mode.available, leadingIcon = { ModeMark(count > 0) },
                        label = { ModeLabel(mode, count) })
                    if (count > 0) TipIconButton(Icons.Outlined.Remove, "Choose one fewer: ${modeText(mode)}") {
                        chosen = chosen.toMutableList().also { it.removeAt(it.lastIndexOf(mode.index)) }
                    }
                }
            }
        } else FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            for (mode in decision.options) FilterChip(mode.index in chosen, {
                if (decision.max == 1) cast.answer(Answer.Modes(listOf(mode.index)))
                else chosen = if (mode.index in chosen) chosen - mode.index else chosen + mode.index
            }, enabled = mode.available, leadingIcon = { ModeMark(mode.index in chosen) }, label = { ModeLabel(mode, 0) })
        }
        decision.pawprintLimit?.let { limit ->
            Text("Pawprints: $pawprints of $limit", fontSize = 11.sp, color = if (overLimit) Color(0xffb3261e) else Color(0xff4b5a5f))
        }
        if (decision.max != 1) {
            val fits = chosen.size in decision.min..decision.max && !overLimit
            if (decision.repeatable) DockButtons {
                ChoiceButton("Clear", Icons.Outlined.ClearAll, enabled = chosen.isNotEmpty()) { chosen = emptyList() }
                ChoiceOk(decision, fits) { cast.answer(Answer.Modes(chosen)) }
            } else OkRow(decision, fits) { cast.answer(Answer.Modes(chosen)) }
        }
    }
}

/** A mode's text with its pawprint cost in front ("{P}{P}") unless the text says it already, and how often it is chosen
 * when more than once. */
@Composable
private fun ModeLabel(mode: arcanum.engine.decision.ModeOption, count: Int) {
    val text = modeText(mode)
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        if (mode.pawprints > 0 && !text.startsWith("{P}")) Text("{P}".repeat(mode.pawprints), fontSize = 12.sp, fontWeight = FontWeight.Bold)
        CardText(text, Modifier.weight(1f, fill = false), style = TextStyle(fontSize = 12.sp))
        if (count > 1) Text("×$count", fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}

private fun modeText(mode: arcanum.engine.decision.ModeOption) = mode.text ?: "Mode ${mode.index + 1}"

/** The mana section: the engine's suggested tapping shown tapped, the other sources untapped; a tap toggles one. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ManaSection(cast: FlowQuestions, flow: FlowQuestions, play: TableBoard, decision: Decision.ManaPayment) {
    Section("Mana · ${decision.cost}", choiceHint(decision)) {
        val groups = ManaPicks.groups(decision)
        val viewer = LocalChoiceFrame.current.viewer
        val inspector = remember(decision.id) { ChoiceInspector() }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            for (group in groups) {
                val used = cast.mana.firstOrNull { it in group }
                val label = manaLabel(flow, play, used ?: group.first(), group)
                // I-D48: a mana ability's source is drawn as its card, turned sideways while it is tapped for the payment
                val source = (group.first() as? ManaUse.Ability)?.source
                if (source != null) {
                    val card = choiceCardOf(flow, play, viewer, source, tapped = used != null)
                    ChoiceCardFace(card, Modifier.choiceCardGestures(card.key, tap = { cast.toggleMana(group) }, hold = { inspector.hold(card, it) }, moved = inspector::moved)
                        .semantics { contentDescription = "Mana source $label${if (used != null) ", tapped" else ""}" },
                        badge = label.substringAfterLast(' '))
                } else FilterChip(used != null, { cast.toggleMana(group) },
                    modifier = Modifier.semantics { contentDescription = "Mana source $label${if (used != null) ", tapped" else ""}" },
                    label = { Text(label, fontSize = 12.sp) },
                    leadingIcon = { TapGlyph(used != null) },
                    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = AutomatumViolet.copy(alpha = .18f)))
            }
        }
        Text(if (cast.paid) "Paid" else "Not paid yet", fontSize = 11.sp,
            color = if (cast.paid) Color(0xff237d68) else Color(0xffb3261e))
        ChoiceInspection(inspector, play, viewer)
    }
}

/**
 * STK-024 (owner 2026-11-07, gallery review 2026-11-09): what pays the open cast reads from the table, its sources outlined
 * there (Autotap's suggestion, until the player taps sources there); this section, compact, says only that and how to change
 * it, or in red why it isn't paid (Cast is greyed then). A source is named only while the table can't show it ([inView]);
 * pool mana, life and other ways the table can't tap are chips here. The names stay in the section's description for screen readers.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PaymentLine(cast: CastController, play: TableBoard, inView: (String) -> Boolean) {
    val payment = cast.payment ?: return
    val due = payment.cost.toString()
    if (due.isEmpty()) return
    val names = cast.mana.map { useName(cast, play, it) }
    val reason = when {
        cast.paid -> null
        names.isEmpty() -> "Tap sources on the table to pay $due."
        else -> "Not enough to pay $due: tap more sources on the table."
    }
    val onTable = cast.mana.filter { cast.tableSource(it) != null }
    val outOfView = onTable.filterNot { inView(cast.tableSource(it)!!) }.map { useName(cast, play, it) }
    Section("Mana · $due") {
        val line = if (names.isEmpty()) "Nothing chosen yet." else "Paid with ${names.joinToString()}"
        Column(Modifier.semantics { contentDescription = "Payment: $line" }, verticalArrangement = Arrangement.spacedBy(2.dp)) {
            if (outOfView.isNotEmpty()) CardText("Out of view: ${outOfView.joinToString()}", style = TextStyle(fontSize = 12.sp))
            if (reason != null) CardText(reason, Modifier.semantics { contentDescription = "Not paid: $reason" }, color = Color(0xffb3261e),
                style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium))
            else Text(if (onTable.isEmpty()) "Tap a source on the table to change it." else "Outlined sources pay. Tap sources on the table to change them.",
                fontSize = 11.sp, color = Color(0xff4b5a5f))
        }
        val others = ManaPicks.groups(payment).filter { group -> cast.tableSource(group.first()) == null }
        if (others.isNotEmpty()) FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            for (group in others) {
                val used = cast.mana.firstOrNull { it in group }
                val label = useName(cast, play, used ?: group.first())
                FilterChip(used != null, { cast.toggleMana(group) },
                    modifier = Modifier.semantics { contentDescription = "Mana source $label${if (used != null) ", chosen" else ""}" },
                    label = { Text(label, fontSize = 12.sp) }, leadingIcon = { TapGlyph(used != null) },
                    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = AutomatumViolet.copy(alpha = .18f)))
            }
        }
    }
}

/** STK-024: whether the battlefield card [card] can be seen on the table with an open panel's top at [top] (dp): at least half
 * of it inside its battlefield, above the panel. */
internal fun TableScene.inView(card: String, top: Float): Boolean {
    val item = items.firstOrNull { it.cardId == card && it.zone == PlayZone.BATTLEFIELD } ?: return false
    val field = viewports.firstOrNull { it.id == item.viewport }?.bounds ?: item.bounds
    val seen = item.bounds.intersection(field) ?: return false
    return seen.width >= item.bounds.width / 2 && minOf(seen.bottom, top) - seen.y >= item.bounds.height / 2
}

/** A payment's use as the payment line names it: its source card, "Pool mana", "2 life". */
private fun useName(flow: FlowQuestions, play: TableBoard, use: ManaUse): String = when (use) {
    is ManaUse.Ability -> objectName(flow, play, use.source) + usedWith(flow, play, use)
    is ManaUse.Pool -> "Pool mana"
    is ManaUse.Life -> "2 life"
    is ManaUse.Special -> use.objects.joinToString { objectName(flow, play, it) }
}

/** What a source's cost uses besides itself, as the panel names it ("Blood Altar: Bear", CR 605.3a). */
private fun usedWith(flow: FlowQuestions, play: TableBoard, use: ManaUse.Ability): String {
    val objects = use.answers.filterIsInstance<Answer.Objects>().flatMap { it.chosen }
    return if (objects.isEmpty()) "" else ": " + objects.joinToString { objectName(flow, play, it) }
}

@Composable
private fun ModeMark(chosen: Boolean) =
    Icon(if (chosen) Icons.Outlined.CheckCircle else Icons.Outlined.RadioButtonUnchecked, null, Modifier.size(16.dp))

/** A card turned sideways when tapped, upright when not. */
@Composable
private fun TapGlyph(tapped: Boolean) {
    Canvas(Modifier.size(16.dp)) {
        val w = size.minDimension * .5f; val h = size.minDimension * .72f
        val (cw, ch) = if (tapped) h to w else w to h
        drawRoundRect(if (tapped) AutomatumViolet else Color(0xff4b5a5f), Offset((size.width - cw) / 2, (size.height - ch) / 2), Size(cw, ch),
            androidx.compose.ui.geometry.CornerRadius(2f), style = Stroke(1.6f * density))
    }
}

/** The eye (I-D10): hides the panel to look at the board, and shows it again. */
@Composable
internal fun EyeButton(hidden: Boolean, toggle: () -> Unit, what: String = "cast panel") {
    IconButton(toggle, Modifier.size(40.dp).semantics { contentDescription = if (hidden) "Show $what" else "Hide $what" }) {
        Canvas(Modifier.size(22.dp)) {
            val stroke = Stroke(1.8f * density)
            drawOval(AutomatumViolet, Offset(1f, size.height * .25f), Size(size.width - 2f, size.height * .5f), style = stroke)
            drawCircle(AutomatumViolet, size.minDimension * .14f, center)
            if (hidden) drawLine(AutomatumViolet, Offset(2f, size.height - 2f), Offset(size.width - 2f, 2f), 1.8f * density)
        }
    }
}

// ---- Words

/** The cast panel's title (STK-023, STK-044): what is done and the card it is done with, [name] ("Cast Storm Spark",
 * "Play Mountain", "Activate Ember Herald": an ability by its source's name), the card also at the panel's right. */
internal fun castTitle(flow: CastFlow, name: String) = when {
    flow.begin is Input.PlayLand -> "Play $name"
    flow.activation -> "Activate $name"
    else -> "Cast $name"
}

/** The panel's main button: Play for a land, Activate for an ability or special action (§3 I4), else Cast. */
private fun actionLabel(flow: CastFlow) = when {
    flow.begin is Input.PlayLand -> "Play"
    flow.activation -> "Activate"
    else -> "Cast"
}

internal fun cardText(play: TableBoard, cardId: String): String? {
    val card = play.board.cards.singleOrNull { it.id == cardId } ?: return null
    val faces = play.definitions.singleOrNull { it.digest == card.definition }?.definition?.faces ?: return null
    return faces.joinToString("\n") { it.rulesText }.trim().ifEmpty { null }
}

private fun wayLabel(play: TableBoard, flow: CastFlow, way: Input): String = when (way) {
    is Input.Cast -> CastFlow.wayLabel(flow.state.objects.values.firstOrNull { (it.id as? ObjectId.Card)?.card ==
        (way.card as ObjectId.Card).card }?.definition, way) ?: "Cast"
    is Input.PlayLand -> if (way.face == 0) "Play" else "Play back face"
    else -> "Cast"
}

/** A target bar's title (STK-044, STK-047): only the choice, "Choose any target", "Choose up to 2 target creature". */
internal fun targetTitle(d: Decision.Targets): String = targetPrompt(d).replaceFirstChar { it.uppercase() }

internal fun targetPrompt(d: Decision.Targets): String {
    val slot = d.slot.substringBefore(':').substringBefore('@')
    val what = if (slot == "any" || slot.isEmpty()) "any target" else "target $slot"
    return when {
        d.max == null -> "choose any number of targets ($what)"
        d.min == 0 -> "choose up to ${d.max} $what"
        (d.max ?: 1) > 1 -> "choose ${d.max} $what"
        else -> "choose $what"
    }
}

private fun mayTitle(d: Decision.May) = choiceWhat(d)

/** STK-030: "Cost" and the cost a question asks to pay, as a chip with its symbols ("{2}", "Forage"). */
@Composable
internal fun CostLine(cost: arcanum.api.Cost) {
    val text = costText(cost)
    Row(Modifier.semantics(mergeDescendants = true) { contentDescription = "Cost: $text" }, verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Cost", fontSize = 11.sp, color = Color(0xff4b5a5f))
        // TBL-132 (owner gallery review 2026-11-06): holding a cost naming a keyword action ("Forage") explains it
        RuleTip(text) {
            Surface(shape = RoundedCornerShape(10.dp), color = Color(0xfffff4e0), border = BorderStroke(1.dp, Color(0xffd08a1e))) {
                CardText(text, Modifier.padding(horizontal = 8.dp, vertical = 2.dp), style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium))
            }
        }
    }
}

private fun sectionTitle(d: Decision): String = when (d) {
    is Decision.Targets -> "Target"
    is Decision.Modes -> "Modes"
    is Decision.ChooseObjects -> d.text
    is Decision.ChooseOption -> "Choice"
    is Decision.ChooseColor -> "Colour"
    is Decision.Divide -> "Division"
    is Decision.Manual -> "Skipped"
    else -> "Choice"
}

private fun answerLabel(flow: FlowQuestions, play: TableBoard, names: Map<String, String>, decision: Decision, answer: Answer): String = when (answer) {
    is Answer.Targets -> answer.chosen.joinToString { targetName(flow, play, names, it) }.ifEmpty { "None" }
    is Answer.Objects -> answer.chosen.joinToString { objectName(flow, play, it) }.ifEmpty { "None" }
    is Answer.Modes -> answer.chosen.joinToString(" · ") { i -> (decision as? Decision.Modes)?.options?.firstOrNull { it.index == i }?.text ?: "Mode ${i + 1}" }
    is Answer.Number -> answer.value.toString()
    is Answer.YesNo -> if (answer.yes) "Yes" else "No"
    is Answer.Option -> answer.chosen
    is Answer.ColorChoice -> colorName(answer.color.symbol)
    is Answer.Player -> names[answer.player.value] ?: "A player"
    is Answer.Division -> answer.shares.joinToString { "${it.amount} to ${targetName(flow, play, names, it.target)}" }
    is Answer.Keep -> objectName(flow, play, answer.kept)
    is Answer.SubtypeChoice -> answer.subtype.name
    is Answer.CardTypeChoice -> answer.cardType.toString()
    is Answer.CardName -> answer.name
    is Answer.Replacement -> (decision as? Decision.ChooseReplacement)?.options?.firstOrNull { it.index == answer.index }?.text ?: "Replacement ${answer.index + 1}"
    Answer.Done -> "Done"
    else -> answer::class.simpleName ?: "Answer"
}

internal fun targetName(flow: FlowQuestions, play: TableBoard, names: Map<String, String>, target: TargetRef): String = when (target) {
    is TargetRef.Player -> names[target.id.value] ?: "A player"
    is TargetRef.Object -> objectName(flow, play, target.id)
}

/** An object's name, or "A hidden card" when the viewer mustn't see it (I-D27). */
internal fun objectName(flow: FlowQuestions, play: TableBoard, id: ObjectId): String =
    if (!flow.shown(id)) "A hidden card"
    else flow.engine.obj(id)?.let { it.definition?.faces?.getOrNull(it.face)?.name }
        ?: flow.atwCard(id)?.let { CastController.cardName(play, it) } ?: "An object"

private fun manaLabel(flow: FlowQuestions, play: TableBoard, use: ManaUse, group: List<ManaUse>): String = when (use) {
    is ManaUse.Ability -> objectName(flow, play, use.source) + usedWith(flow, play, use) + " " + group.filterIsInstance<ManaUse.Ability>()
        .map { it.color?.symbol ?: 'C' }.distinct().joinToString("/")
    is ManaUse.Pool -> "Pool mana"
    is ManaUse.Life -> "2 life"
    is ManaUse.Special -> use.objects.joinToString { objectName(flow, play, it) }
}

private fun colorName(symbol: Char) = when (symbol) {
    'W' -> "White"; 'U' -> "Blue"; 'B' -> "Black"; 'R' -> "Red"; 'G' -> "Green"; else -> "Colorless"
}

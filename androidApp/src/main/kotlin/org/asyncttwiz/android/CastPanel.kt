package org.asyncttwiz.android

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
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
 * The cast panel (integration spec I-D6, §3 I2): one floating panel by the Stack while a cast is open. Its sections
 * follow the rules order (CR 601.2b–h): the way to cast (face, alternative cost), then each choice (modes, X, optional
 * costs) with the card's text above, then the mana section with the engine's suggested tapping. Targets are chosen on
 * the table: the panel shrinks to a slim bar while legal targets glow. The eye hides the panel to look at the board
 * (I-D10); "Cancel" and Back drop the whole cast. "Accept automatically" (D3) is this player's own setting here.
 */
@Composable
internal fun CastPanelHost(toolkit: TableToolkitController, play: PlayState, scene: TableScene?, names: Map<String, String>) {
    val cast = toolkit.cast
    val context = LocalContext.current
    val preferences = remember(context) { context.applicationContext.getSharedPreferences("automatum-cast", Context.MODE_PRIVATE) }
    val key = "accept/${toolkit.viewer}"
    var accept by remember(key) { mutableStateOf(preferences.getBoolean(key, false)) }
    SideEffect { cast.acceptAutomatically = accept }
    // Targets are chosen: a pile opened to pick them from closes again once they are (i12review #14)
    LaunchedEffect(cast.targeting || toolkit.flows.targeting) { if (!cast.targeting && !toolkit.flows.targeting) toolkit.targetingEnded() }
    val flow = cast.flow ?: return
    // The board the player acts on (never one reviewed in the timeline, i12review #10) may have moved on
    LaunchedEffect(IdentityKey(play.board)) { cast.follow() }
    BackHandler { cast.cancel() }
    val stack = scene?.stackRegion ?: scene?.items?.firstOrNull { it.zone == PlayZone.STACK && it.kind == TableItemKind.PILE }?.bounds
    // The slim bar sits over the own hand row (hand cards are never targets), so the battlefield stays free to tap
    val handRow = scene?.items?.firstOrNull { it.kind == TableItemKind.HAND && it.owner == toolkit.viewer }?.bounds
    CastPanelPlacement(stack, if (cast.targeting) handRow?.center?.y else null) {
        when {
            cast.targeting -> TargetBar(cast, flow, play)
            cast.hidden -> Surface(Modifier.size(44.dp), shape = CircleShape, color = Color.White, shadowElevation = 6.dp,
                border = BorderStroke(2.dp, AutomatumViolet)) {
                EyeButton(hidden = true, cast::toggleHidden)
            }
            else -> CastPanel(cast, flow, play, names, accept) { accept = it; preferences.edit().putBoolean(key, it).apply() }
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
    fun listedAnswers(): List<Answer>
    val mana: List<ManaUse>
    val paid: Boolean
    fun toggleMana(group: List<ManaUse>)
    fun shown(id: ObjectId): Boolean = true
}

/** Left of the Stack (or of the table's right edge), centred on it vertically (or on [middle], dp), inside the table. */
@Composable
internal fun CastPanelPlacement(stack: TableBounds?, middle: Float?, content: @Composable () -> Unit) {
    Layout(content = content, modifier = Modifier.fillMaxSize()) { measurables, constraints ->
        val margin = 8.dp.roundToPx()
        val width = minOf(300.dp.roundToPx(), constraints.maxWidth - 2 * margin).coerceAtLeast(0)
        val placeable = measurables.firstOrNull()?.measure(Constraints(maxWidth = width,
            maxHeight = (constraints.maxHeight * .78f).toInt().coerceAtLeast(0)))
        layout(constraints.maxWidth, constraints.maxHeight) {
            placeable ?: return@layout
            val stackX = stack?.x?.dp?.roundToPx() ?: constraints.maxWidth
            val centerY = (middle ?: stack?.center?.y)?.dp?.roundToPx() ?: (constraints.maxHeight / 2)
            val x = (stackX - placeable.width - margin).coerceIn(margin, (constraints.maxWidth - placeable.width - margin).coerceAtLeast(margin))
            val y = (centerY - placeable.height / 2).coerceIn(margin, (constraints.maxHeight - placeable.height - margin).coerceAtLeast(margin))
            placeable.place(x, y)
        }
    }
}

@Composable
private fun TargetBar(cast: CastController, flow: CastFlow, play: PlayState) {
    val decision = flow.pending as Decision.Targets
    Surface(Modifier.semantics { contentDescription = "Choose targets" }, shape = RoundedCornerShape(22.dp), color = Color.White,
        shadowElevation = 6.dp, border = BorderStroke(2.dp, AutomatumViolet)) {
        Row(Modifier.padding(start = 14.dp, end = 4.dp).heightIn(min = 44.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("${spellName(flow, play)} — ${targetPrompt(decision)}", Modifier.weight(1f), fontSize = 13.sp,
                fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (cast.busy) Working()
            if (decision.max != 1 || decision.min == 0) TextButton(cast::confirmTargets, enabled = cast.picked.size >= decision.min) {
                Text(if (cast.picked.isEmpty()) "None" else "Done")
            }
            TextButton(cast::cancel, Modifier.semantics { contentDescription = "Cancel cast" }) { Text("Cancel") }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CastPanel(cast: CastController, flow: CastFlow, play: PlayState, names: Map<String, String>,
    accept: Boolean, setAccept: (Boolean) -> Unit) {
    Surface(Modifier.semantics { contentDescription = "Cast panel" }, shape = RoundedCornerShape(8.dp), color = Color.White,
        shadowElevation = 6.dp, border = BorderStroke(2.dp, AutomatumViolet)) {
        Column(Modifier.padding(10.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(spellName(flow, play), Modifier.weight(1f), fontWeight = FontWeight.Bold, fontSize = 15.sp)
                if (cast.busy) Working()
                EyeButton(hidden = false, cast::toggleHidden)
            }
            cardText(play, flow.cardId)?.let { Text(it, fontSize = 11.sp, lineHeight = 14.sp, color = Color(0xff4b5a5f)) }
            if (cast.ways.size > 1) Section("Cast as") {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    for (way in cast.ways) FilterChip(way == flow.begin, { if (way != flow.begin) cast.way(way) },
                        label = { Text(wayLabel(play, flow, way), fontSize = 12.sp) })
                }
            }
            flow.steps.forEachIndexed { index, (decision, answer) -> AnsweredSection(cast, flow, play, names, index, decision, answer) }
            when (val open = flow.pending) {
                null -> Text("Ready to cast.", fontSize = 12.sp)
                is Decision.ManaPayment -> ManaSection(cast, cast, play, open)
                else -> OpenSection(cast, cast, play, names, open)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(accept, setAccept, Modifier.semantics { contentDescription = "Accept automatically" })
                Text("Accept automatically", fontSize = 12.sp)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                TextButton(cast::cancel, Modifier.semantics { contentDescription = "Cancel cast" }) { Text("Cancel") }
                Button(cast::cast, enabled = !cast.busy && (cast.paid || flow.complete),
                    colors = ButtonDefaults.buttonColors(containerColor = AutomatumViolet)) { Text(if (land(flow)) "Play" else "Cast") }
            }
        }
    }
}

/** Automatum is working out the last step (SYS-025): a small violet spinner; the panel takes no input meanwhile. */
@Composable
private fun Working() {
    CircularProgressIndicator(Modifier.size(18.dp).semantics { contentDescription = "Automatum is working" },
        color = AutomatumViolet, strokeWidth = 2.dp)
}

@Composable
internal fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        HorizontalDivider(color = AutomatumViolet.copy(alpha = .25f))
        Text(title, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = AutomatumViolet)
        content()
    }
}

/** A choice already made: changed in place (a toggle, a stepper), or opened again ("Change") when it is made on the
 * table. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AnsweredSection(cast: CastController, flow: CastFlow, play: PlayState, names: Map<String, String>,
    index: Int, decision: Decision, answer: Answer) {
    when {
        decision is Decision.May && answer is Answer.YesNo -> Section(mayTitle(decision)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(decision.text, Modifier.weight(1f), fontSize = 12.sp)
                Switch(answer.yes, { cast.change(index, Answer.YesNo(it)) }, Modifier.semantics { contentDescription = decision.text })
            }
        }
        decision is Decision.Announce && answer is Answer.Number -> Section(decision.name) {
            Stepper(answer.value, decision.min, decision.max) { cast.change(index, Answer.Number(it)) }
        }
        decision is Decision.ChooseNumber && answer is Answer.Number -> Section("Number") {
            Stepper(answer.value, decision.min, decision.max) { cast.change(index, Answer.Number(it)) }
        }
        decision is Decision.Modes && answer is Answer.Modes && decision.max == 1 -> Section("Mode") {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                for (mode in decision.options) FilterChip(mode.index in answer.chosen, { cast.change(index, Answer.Modes(listOf(mode.index))) },
                    enabled = mode.available, label = { Text(mode.text ?: "Mode ${mode.index + 1}", fontSize = 12.sp) })
            }
        }
        else -> Section(sectionTitle(decision)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(answerLabel(cast, play, names, decision, answer), Modifier.weight(1f), fontSize = 12.sp)
                TextButton({ cast.back(index) }) { Text("Change") }
            }
        }
    }
}

/** The open question (but targets, chosen on the table, and mana, [ManaSection]) of a cast or a shared flow. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun OpenSection(cast: FlowQuestions, flow: FlowQuestions, play: PlayState, names: Map<String, String>, decision: Decision) {
    when (decision) {
        is Decision.May -> Section(mayTitle(decision)) {
            Text(decision.text, fontSize = 12.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button({ cast.answer(Answer.YesNo(true)) }, enabled = decision.payable) { Text("Yes") }
                OutlinedButton({ cast.answer(Answer.YesNo(false)) }) { Text("No") }
            }
            if (!decision.payable) Text("It can't be paid now.", fontSize = 11.sp, color = Color(0xff4b5a5f))
        }
        is Decision.Announce, is Decision.ChooseNumber -> {
            val min = (decision as? Decision.Announce)?.min ?: (decision as Decision.ChooseNumber).min
            val max = (decision as? Decision.Announce)?.max ?: (decision as? Decision.ChooseNumber)?.max
            var value by remember(decision.id) { mutableIntStateOf(min) }
            Section((decision as? Decision.Announce)?.name ?: "Number") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Stepper(value, min, max) { value = it }
                    Spacer(Modifier.weight(1f))
                    Button({ cast.answer(Answer.Number(value)) }) { Text("OK") }
                }
            }
        }
        is Decision.Modes -> {
            var chosen by remember(decision.id) { mutableStateOf(listOf<Int>()) }
            Section(if (decision.max == 1) "Choose one" else "Choose ${decision.min}–${decision.max}") {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    for (mode in decision.options) FilterChip(mode.index in chosen, {
                        if (decision.max == 1) cast.answer(Answer.Modes(listOf(mode.index)))
                        else chosen = if (mode.index in chosen && !decision.repeatable) chosen - mode.index else chosen + mode.index
                    }, enabled = mode.available, label = { Text(mode.text ?: "Mode ${mode.index + 1}", fontSize = 12.sp) })
                }
                if (decision.max != 1) Button({ cast.answer(Answer.Modes(chosen)) }, enabled = chosen.size in decision.min..decision.max) { Text("OK") }
            }
        }
        is Decision.ChooseOption -> Section("Choose") {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                for (option in decision.options) OutlinedButton({ cast.answer(Answer.Option(option)) }) { Text(option, fontSize = 12.sp) }
            }
        }
        is Decision.ChooseColor -> Section("Choose a colour") {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                for (color in decision.among) OutlinedButton({ cast.answer(Answer.ColorChoice(color)) }) { Text(colorName(color.symbol)) }
            }
        }
        is Decision.ChooseObjects -> {
            var chosen by remember(decision.id) { mutableStateOf(listOf<ObjectId>()) }
            Section(decision.text) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    for (o in decision.options) FilterChip(o in chosen, { chosen = if (o in chosen) chosen - o else chosen + o },
                        label = { Text(objectName(flow, play, o), fontSize = 12.sp) })
                }
                Button({ cast.answer(Answer.Objects(chosen)) }, enabled = chosen.size >= decision.min &&
                    (decision.max == null || chosen.size <= decision.max!!)) { Text("OK") }
            }
        }
        is Decision.Divide -> {
            // Pre-filled with the engine's suggestion when it has one (combat damage: lethal to each in turn, I-D29)
            val suggested = decision.suggested.associate { it.target to it.amount }
            var shares by remember(decision.id) { mutableStateOf(decision.among.associateWith { suggested[it] ?: decision.minEach }) }
            val lethal = decision.lethal.associate { it.target to it.amount }
            Section("Divide ${decision.amount}") {
                for (target in decision.among) Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(targetName(flow, play, names, target) + (lethal[target]?.let { " (lethal $it)" } ?: ""),
                        Modifier.weight(1f), fontSize = 12.sp)
                    Stepper(shares.getValue(target), decision.minEach, decision.amount) { shares = shares + (target to it) }
                }
                Button({ cast.answer(Answer.Division(shares.map { arcanum.engine.decision.Share(it.key, it.value) })) },
                    enabled = shares.values.sum() == decision.amount) { Text("OK") }
            }
        }
        is Decision.Manual -> Section("By hand") {
            Text(decision.text, fontSize = 12.sp)
            Button({ cast.answer(Answer.Done) }) { Text("Done") }
        }
        is Decision.OrderTriggers -> {
            // CR 603.3b: the first tapped goes on the Stack first (and resolves last)
            var order by remember(decision.id) { mutableStateOf(listOf<Int>()) }
            Section("Order triggers") {
                Text("Tap them in the order they go on the Stack: the first goes on first and resolves last.", fontSize = 11.sp,
                    color = Color(0xff4b5a5f))
                for (trigger in decision.triggers) {
                    val place = order.indexOf(trigger.index)
                    FilterChip(place >= 0, { order = if (place >= 0) order - trigger.index else order + trigger.index },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text((if (place >= 0) "${place + 1} · " else "") + triggerLabel(flow, play, trigger), fontSize = 12.sp) })
                }
                Button({ cast.answer(Answer.Order(order)) }, enabled = order.size == decision.triggers.size) { Text("OK") }
            }
        }
        is Decision.LegendRule -> Section("Legend rule") {
            Text("Choose the ${decision.name} to keep; the others go to the graveyard.", fontSize = 12.sp)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                decision.among.forEachIndexed { i, o -> OutlinedButton({ cast.answer(Answer.Keep(o)) }) {
                    Text("Keep ${objectName(flow, play, o)}${if (decision.among.size > 1) " ${i + 1}" else ""}", fontSize = 12.sp)
                } }
            }
        }
        is Decision.ChooseReplacement -> Section("Which applies first") {
            for (option in decision.options) OutlinedButton({ cast.answer(Answer.Replacement(option.index)) }, Modifier.fillMaxWidth()) {
                Text(option.text ?: (option.source?.let { objectName(flow, play, it) } ?: "Replacement ${option.index + 1}"), fontSize = 12.sp)
            }
        }
        is Decision.ChoosePlayer -> Section("Choose a player") {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                for (player in decision.options) OutlinedButton({ cast.answer(Answer.Player(player)) }) { Text(names[player.value] ?: "A player") }
            }
        }
        else -> Section(sectionTitle(decision)) {
            // Choices the panel has no control of its own for yet: the engine's listed answers
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                for (answer in flow.listedAnswers()) OutlinedButton({ cast.answer(answer) }) {
                    Text(answerLabel(flow, play, names, decision, answer), fontSize = 12.sp)
                }
            }
        }
    }
}

/** The mana section: the engine's suggested tapping shown tapped, the other sources untapped; a tap toggles one. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ManaSection(cast: FlowQuestions, flow: FlowQuestions, play: PlayState, decision: Decision.ManaPayment) {
    Section("Mana · ${decision.cost}") {
        val groups = ManaPicks.groups(decision)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            for (group in groups) {
                val used = cast.mana.firstOrNull { it in group }
                val label = manaLabel(flow, play, used ?: group.first(), group)
                FilterChip(used != null, { cast.toggleMana(group) },
                    modifier = Modifier.semantics { contentDescription = "Mana source $label${if (used != null) ", tapped" else ""}" },
                    label = { Text(label, fontSize = 12.sp) },
                    leadingIcon = { TapGlyph(used != null) },
                    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = AutomatumViolet.copy(alpha = .18f)))
            }
        }
        Text(if (cast.paid) "Paid" else "Not paid yet", fontSize = 11.sp,
            color = if (cast.paid) Color(0xff237d68) else Color(0xffb3261e))
    }
}

@Composable
internal fun Stepper(value: Int, min: Int, max: Int?, set: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        OutlinedButton({ set(value - 1) }, Modifier.size(36.dp), enabled = value > min, contentPadding = PaddingValues(0.dp)) { Text("−") }
        Text("$value", Modifier.padding(horizontal = 10.dp).semantics { contentDescription = "Value $value" }, fontWeight = FontWeight.Bold)
        OutlinedButton({ set(value + 1) }, Modifier.size(36.dp), enabled = max == null || value < max, contentPadding = PaddingValues(0.dp)) { Text("+") }
    }
}

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

private fun land(flow: CastFlow) = flow.begin is Input.PlayLand

private fun spellName(flow: CastFlow, play: PlayState): String {
    val cast = flow.begin as? Input.Cast
    val definition = cast?.let { flow.state.obj(it.card)?.definition ?: flow.state.objects.values.firstOrNull { o ->
        (o.id as? ObjectId.Card)?.card == (it.card as ObjectId.Card).card }?.definition }
    return definition?.faces?.getOrNull(cast.way.face)?.name ?: CastController.cardName(play, flow.cardId)
}

internal fun cardText(play: PlayState, cardId: String): String? {
    val card = play.board.cards.singleOrNull { it.id == cardId } ?: return null
    val faces = play.definitions.singleOrNull { it.digest == card.definition }?.definition?.faces ?: return null
    return faces.joinToString("\n") { it.rulesText }.trim().ifEmpty { null }
}

private fun wayLabel(play: PlayState, flow: CastFlow, way: Input): String = when (way) {
    is Input.Cast -> CastFlow.wayLabel(flow.state.objects.values.firstOrNull { (it.id as? ObjectId.Card)?.card ==
        (way.card as ObjectId.Card).card }?.definition, way) ?: "Cast"
    is Input.PlayLand -> if (way.face == 0) "Play" else "Play back face"
    else -> "Cast"
}

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

private fun mayTitle(d: Decision.May) = if (d.cost != null) "Optional cost" else "Choice"

private fun sectionTitle(d: Decision): String = when (d) {
    is Decision.Targets -> "Target"
    is Decision.Modes -> "Modes"
    is Decision.ChooseObjects -> d.text
    is Decision.ChooseOption -> "Choice"
    is Decision.ChooseColor -> "Colour"
    is Decision.Divide -> "Division"
    is Decision.Manual -> "By hand"
    else -> "Choice"
}

private fun answerLabel(flow: FlowQuestions, play: PlayState, names: Map<String, String>, decision: Decision, answer: Answer): String = when (answer) {
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
    is Answer.SubtypeChoice -> answer.subtype.toString()
    is Answer.CardTypeChoice -> answer.cardType.toString()
    is Answer.CardName -> answer.name
    is Answer.Replacement -> (decision as? Decision.ChooseReplacement)?.options?.firstOrNull { it.index == answer.index }?.text ?: "Replacement ${answer.index + 1}"
    Answer.Done -> "Done"
    else -> answer::class.simpleName ?: "Answer"
}

internal fun targetName(flow: FlowQuestions, play: PlayState, names: Map<String, String>, target: TargetRef): String = when (target) {
    is TargetRef.Player -> names[target.id.value] ?: "A player"
    is TargetRef.Object -> objectName(flow, play, target.id)
}

/** An object's name, or "A hidden card" when the viewer mustn't see it (I-D27). */
internal fun objectName(flow: FlowQuestions, play: PlayState, id: ObjectId): String =
    if (!flow.shown(id)) "A hidden card"
    else flow.engine.obj(id)?.let { it.definition?.faces?.getOrNull(it.face)?.name }
        ?: flow.atwCard(id)?.let { CastController.cardName(play, it) } ?: "An object"

/** A trigger waiting to be put on the Stack: its source's name and its printed text. */
private fun triggerLabel(flow: FlowQuestions, play: PlayState, trigger: arcanum.engine.decision.TriggerOption): String =
    listOfNotNull(trigger.source?.let { objectName(flow, play, it) }, trigger.text).joinToString(": ").ifEmpty { "Trigger ${trigger.index + 1}" }

private fun manaLabel(flow: FlowQuestions, play: PlayState, use: ManaUse, group: List<ManaUse>): String = when (use) {
    is ManaUse.Ability -> objectName(flow, play, use.source) + " " + group.filterIsInstance<ManaUse.Ability>()
        .map { it.color?.symbol ?: 'C' }.distinct().joinToString("/")
    is ManaUse.Pool -> "Pool mana"
    is ManaUse.Life -> "2 life"
    is ManaUse.Special -> use.objects.joinToString { objectName(flow, play, it) }
}

private fun colorName(symbol: Char) = when (symbol) {
    'W' -> "White"; 'U' -> "Blue"; 'B' -> "Black"; 'R' -> "Red"; 'G' -> "Green"; else -> "Colorless"
}

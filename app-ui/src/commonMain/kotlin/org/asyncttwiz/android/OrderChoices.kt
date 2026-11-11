package org.asyncttwiz.android

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DragHandle
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.decision.ReplacementOption
import org.asyncttwiz.protocol.Faults

/** [list] with the item at [from] moved to [to]. */
fun <T> List<T>.moved(from: Int, to: Int): List<T> =
    if (from == to || from !in indices || to !in indices) this else toMutableList().also { it.add(to, it.removeAt(from)) }

/**
 * STK-030 (owner gallery review, 2026-11-06): the order of triggers shown top to bottom as they resolve, the top first: the
 * engine's suggested order ([Decision.OrderTriggers.triggers], put on the Stack in that order, CR 603.3b) reversed. The
 * answer is the order they go on the Stack ([triggerAnswer]).
 */
fun suggestedTriggerOrder(decision: Decision.OrderTriggers): List<Int> = decision.triggers.map { it.index }.reversed()

/** The order [shown] (top first: resolves first) as the engine takes it: the first goes on the Stack first (CR 603.3b). */
fun triggerAnswer(shown: List<Int>): Answer.Order = Answer.Order(shown.reversed())

/**
 * A drag-to-reorder list (STK-030, owner gallery review 2026-11-06): one row per key in [keys]' order, each with a handle at
 * its left; dragging a handle up or down moves its row past the others ([move] from, to), the dragged row following the
 * finger. Each row offers "Move up" and "Move down" to accessibility services too. [row] draws a row's content after its
 * handle.
 */
@Composable
fun DragOrderList(keys: List<String>, move: (Int, Int) -> Unit, describe: (String) -> String,
    row: @Composable RowScope.(key: String, index: Int) -> Unit) {
    val heights = remember { mutableStateMapOf<String, Int>() }
    var dragging by remember { mutableStateOf<String?>(null) }
    var offset by remember { mutableFloatStateOf(0f) }
    val gap = with(LocalDensity.current) { 6.dp.toPx() }
    val current by rememberUpdatedState(keys)
    val moveNow by rememberUpdatedState(move)
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        keys.forEachIndexed { index, key ->
            val lifted = dragging == key
            val actions = listOfNotNull(
                CustomAccessibilityAction("Move up") { moveNow(index, index - 1); true }.takeIf { index > 0 },
                CustomAccessibilityAction("Move down") { moveNow(index, index + 1); true }.takeIf { index < keys.lastIndex })
            Row(Modifier.fillMaxWidth().zIndex(if (lifted) 1f else 0f)
                .onSizeChanged { heights[key] = it.height }
                .graphicsLayer { translationY = if (lifted) offset else 0f; shadowElevation = if (lifted) 8f else 0f }
                .background(if (lifted) Color(0xfff6f3fb) else Color.White, RoundedCornerShape(8.dp))
                .border(1.dp, if (lifted) AutomatumViolet else Color(0x22000000), RoundedCornerShape(8.dp))
                .semantics { contentDescription = describe(key); customActions = actions }
                .padding(end = 6.dp, top = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(Modifier.size(width = 36.dp, height = 44.dp).pointerInput(key) {
                    detectVerticalDragGestures(onDragStart = { dragging = key; offset = 0f },
                        onDragEnd = { dragging = null; offset = 0f }, onDragCancel = { dragging = null; offset = 0f }) { change, dy ->
                        change.consume()
                        offset += dy
                        val at = current.indexOf(key)
                        if (offset > 0 && at < current.lastIndex) {
                            val next = (heights[current[at + 1]] ?: 0) + gap
                            if (offset > next / 2) { moveNow(at, at + 1); offset -= next }
                        } else if (offset < 0 && at > 0) {
                            val previous = (heights[current[at - 1]] ?: 0) + gap
                            if (-offset > previous / 2) { moveNow(at, at - 1); offset += previous }
                        }
                    }
                }.semantics { contentDescription = "Drag to reorder" }, contentAlignment = Alignment.Center) {
                    Icon(Icons.Outlined.DragHandle, null, Modifier.size(22.dp), tint = Color(0xff78909c))
                }
                // Its place: 1 is the first (resolves or applies first)
                Box(Modifier.size(20.dp).background(AutomatumViolet, CircleShape), contentAlignment = Alignment.Center) {
                    Text("${index + 1}", color = Color.White, fontSize = 11.sp, lineHeight = 12.sp, fontWeight = FontWeight.Bold)
                }
                row(key, index)
            }
        }
    }
}

/** A row's card and its whole line (a trigger's, a replacement effect's): holding the card inspects it. */
@Composable
private fun RowScope.OrderRowContent(card: ChoiceCardData, line: String, inspector: ChoiceInspector) {
    ChoiceCardFace(card, Modifier.choiceCardGestures(card.key, tap = {}, hold = { inspector.hold(card, it) }, moved = inspector::moved))
    CardText(line, Modifier.weight(1f), color = Color(0xff26343a),
        style = androidx.compose.ui.text.TextStyle(fontSize = 11.sp, lineHeight = 14.sp))
}

/**
 * STK-030, CR 603.3b (owner gallery review, 2026-11-06): triggers to order as a drag-to-reorder list, each its source's
 * card with its own line, opening in the suggested order with the one that resolves first at the top; OK puts them on.
 */
@Composable
fun OrderTriggersSection(cast: FlowQuestions, flow: FlowQuestions, play: TableBoard, decision: Decision.OrderTriggers) {
    val viewer = LocalChoiceFrame.current.viewer
    val inspector = remember(decision.id) { ChoiceInspector() }
    var order by remember(decision.id) { mutableStateOf(suggestedTriggerOrder(decision)) }
    val triggers = decision.triggers.associateBy { it.index }
    // 3UE2NW: what each row is made of, so a report of the panel explains itself
    LaunchedEffect(decision.id) {
        AppLog.info("decision", "Order triggers ${decision.id.value}: " + decision.triggers.joinToString("; ") { trigger ->
            val source = trigger.source
            "#${trigger.index} source ${source ?: "none"}" +
                (source?.let { " (${objectName(flow, play, it)}, shown ${flow.shown(it)})" } ?: "") + ", text ${trigger.text ?: "none"}"
        })
    }
    fun line(index: Int): String {
        val trigger = triggers[index] ?: return Faults.placeholder("OrderTriggersSection", "trigger row without its trigger",
            "Trigger ${index + 1}", mapOf("decision" to decision.id.value, "index" to "$index"))
        val source = trigger.source
        // The ability's own text (public as it goes on the Stack); a keyword's trigger ("Offspring") reads as the keyword's
        // printed line on its source, when the source may be shown
        trigger.text?.let { text ->
            val rules = source?.takeIf(flow::shown)?.let { rulesTextOf(play, flow.atwCard(it)) }
            return org.asyncttwiz.automatum.StackAbilityText.keywordLine(text, rules) ?: text
        }
        return Faults.placeholder("OrderTriggersSection", "trigger without its text",
            source?.let { "${objectName(flow, play, it)}'s triggered ability" } ?: "Trigger ${index + 1}",
            mapOf("decision" to decision.id.value, "index" to "$index", "source" to source.toString()))
    }
    fun card(index: Int): ChoiceCardData {
        val source = triggers[index]?.source
        return (source?.let { choiceCardOf(flow, play, viewer, it) } ?: ChoiceCardData("trigger/$index", null,
            Faults.placeholder("OrderTriggersSection", "trigger without a source", "Trigger ${index + 1}",
                mapOf("decision" to decision.id.value, "index" to "$index")), null, shown = true))
            .copy(key = "trigger/$index")
    }
    Section(choiceWhat(decision), choiceHint(decision)) {
        DragOrderList(order.map { "$it" }, { from, to -> order = order.moved(from, to) },
            describe = { key -> "Trigger: ${line(key.toInt())}" }) { key, _ ->
            val index = key.toInt()
            OrderRowContent(card(index), line(index), inspector)
        }
        OkRow(decision, true) { cast.answer(triggerAnswer(order)) }
    }
    ChoiceInspection(inspector, play, viewer)
}

/**
 * STK-030, CR 616.1 (owner gallery review, 2026-11-06): which replacement effects apply first as a drag-to-reorder
 * preference list, each its source's card with its whole text, in the engine's order at first; OK applies them in this
 * order, skipping those no longer applicable, and asks again only when a new one appears ([FlowQuestions.answerReplacements]).
 */
@Composable
fun ReplacementOrderSection(cast: FlowQuestions, flow: FlowQuestions, play: TableBoard, decision: Decision.ChooseReplacement) {
    val viewer = LocalChoiceFrame.current.viewer
    val inspector = remember(decision.id) { ChoiceInspector() }
    var order by remember(decision.id) { mutableStateOf(decision.options.map { it.index }) }
    val options = decision.options.associateBy { it.index }
    // A replacement without a source may be the game rules' own; one with neither a source nor a text can't be described
    fun name(option: ReplacementOption) = option.source?.let { objectName(flow, play, it) }
        ?: if (option.text != null) "Replacement ${option.index + 1}"
        else Faults.placeholder("ReplacementOrderSection", "replacement without a source or text", "Replacement ${option.index + 1}",
            mapOf("decision" to decision.id.value, "index" to "${option.index}"))
    fun card(option: ReplacementOption): ChoiceCardData =
        (option.source?.let { choiceCardOf(flow, play, viewer, it) } ?: ChoiceCardData("replacement/${option.index}", null, name(option), null,
            shown = true)).copy(key = "replacement/${option.index}")
    Section(choiceWhat(decision), choiceHint(decision)) {
        DragOrderList(order.map { "$it" }, { from, to -> order = order.moved(from, to) },
            describe = { key -> options.getValue(key.toInt()).let { "Replacement: ${it.text ?: name(it)}" } }) { key, _ ->
            val option = options.getValue(key.toInt())
            OrderRowContent(card(option), option.text ?: name(option), inspector)
        }
        OkRow(decision, true) { cast.answerReplacements(decision, order.map(options::getValue)) }
    }
    ChoiceInspection(inspector, play, viewer)
}

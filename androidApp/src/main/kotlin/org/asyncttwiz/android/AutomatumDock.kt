package org.asyncttwiz.android

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import org.asyncttwiz.protocol.CombatStackRules
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import org.asyncttwiz.appui.screenHeightDp

/**
 * One place for every Automatum panel (UI review #4, decided for the owner 2026-11-03): like the cast panel (I-D47), the
 * table's full width with its bottom exactly on the top of the viewer's hand row [hand] (the table's bottom without one),
 * so the hand is never covered. In landscape it is at most [LANDSCAPE_WIDTH] wide from the left edge, so the combat area and
 * its damage preview in the middle of the wide table stay visible (CMB-029).
 */
@Composable
internal fun AboveHand(hand: TableBounds?, content: @Composable () -> Unit) {
    val docked = dockedPanelReporter()
    Layout(content = content, modifier = Modifier.fillMaxSize()) { measurables, constraints ->
        val bottom = (hand?.y?.dp?.roundToPx() ?: constraints.maxHeight).coerceIn(0, constraints.maxHeight)
        val width = if (constraints.maxWidth > constraints.maxHeight) minOf(LANDSCAPE_WIDTH.roundToPx(), constraints.maxWidth)
            else constraints.maxWidth
        val placeable = measurables.firstOrNull()?.measure(Constraints(minWidth = width, maxWidth = width, maxHeight = bottom))
        docked((placeable?.height ?: 0).toDp().value)
        layout(constraints.maxWidth, constraints.maxHeight) { placeable?.place(0, bottom - placeable.height) }
    }
}

/**
 * D2 (SYS-041): the heights of the Automatum panels and bars open over the table's bottom, read by the table's pan
 * bounds when a pan starts (personal UI, never state that recomposes anything).
 */
internal class DockedPanels {
    private val heights = mutableMapOf<Any, Float>()
    /** The tallest open panel's height (dp); 0 with none open. */
    val height: Float get() = heights.values.maxOrNull() ?: 0f
    fun report(key: Any, height: Float) { if (height > 0f) heights[key] = height else heights.remove(key) }
}

internal val LocalDockedPanels = compositionLocalOf<DockedPanels?> { null }

/** Reports this docked panel's measured height (dp) to the table's [DockedPanels] while it is open. */
@Composable
internal fun dockedPanelReporter(): (Float) -> Unit {
    val panels = LocalDockedPanels.current ?: return {}
    val key = remember { Any() }
    DisposableEffect(panels, key) { onDispose { panels.report(key, 0f) } }
    return remember(panels, key) { { height: Float -> panels.report(key, height) } }
}

internal val LANDSCAPE_WIDTH = 420.dp

/** The viewer's hand row on [scene], where Automatum's panels dock ([AboveHand]). */
internal fun handRow(scene: TableScene?, viewer: String): TableBounds? =
    scene?.items?.firstOrNull { it.kind == TableItemKind.HAND && it.owner == viewer }?.bounds

/**
 * An Automatum panel docked above the hand ([AboveHand]) in the cast panel's look: white with a violet top edge, its
 * [content] above a bottom row of fixed height with the eye at its left and the panel's [buttons] at its right (Hold
 * first, then the others, the main one last). The eye never moves: [hidden] fades everything else away around it.
 * [what] names the panel for the eye's description and hold tip.
 */
@Composable
internal fun AutomatumDockPanel(description: String, hidden: Boolean, toggleHidden: () -> Unit, what: String,
    buttons: @Composable RowScope.() -> Unit = {}, content: @Composable ColumnScope.() -> Unit) {
    val shown by animateFloatAsState(if (hidden) 0f else 1f, tween(180), label = description)
    Column(Modifier.fillMaxWidth()
        .then(if (hidden) Modifier else Modifier.pointerInput(Unit) {}.semantics { contentDescription = description })
        .drawBehind {
            drawRect(Color.White.copy(alpha = shown))
            drawLine(AutomatumViolet.copy(alpha = shown), Offset(0f, 0f), Offset(size.width, 0f), 2.dp.toPx())
        }) {
        // What the content saves (rememberSaveable: a choice's picks) outlives hiding it with the eye (STK-044)
        val saved = androidx.compose.runtime.saveable.rememberSaveableStateHolder()
        // Measured after the button row, so a panel given little room (attached to the combat band) scrolls inside
        AnimatedVisibility(!hidden, Modifier.weight(1f, fill = false), enter = fadeIn(tween(180)) + expandVertically(tween(180), expandFrom = Alignment.Bottom),
            exit = fadeOut(tween(180)) + shrinkVertically(tween(180), shrinkTowards = Alignment.Bottom)) {
            saved.SaveableStateProvider(description) {
                Column(Modifier.padding(start = 10.dp, end = 10.dp, top = 8.dp).heightIn(max = 440.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(6.dp), content = content)
            }
        }
        DockButtonRow(hidden, shown, toggleHidden, what, buttons)
    }
}

/** The dock's bottom row: the eye at a fixed spot at the left, [buttons] at the right (hidden with the panel). */
@Composable
private fun DockButtonRow(hidden: Boolean, shown: Float, toggleHidden: () -> Unit, what: String, buttons: @Composable RowScope.() -> Unit) {
    Row(Modifier.fillMaxWidth().height(52.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Surface(shape = CircleShape, color = Color.White, border = BorderStroke(2.dp, AutomatumViolet.copy(alpha = 1f - shown)),
            shadowElevation = if (hidden) 6.dp else 0.dp) {
            HoldTip(if (hidden) "Show the $what" else "Hide the $what") { EyeButton(hidden = hidden, toggleHidden, what) }
        }
        Spacer(Modifier.weight(1f))
        AnimatedVisibility(!hidden, enter = fadeIn(tween(180)), exit = fadeOut(tween(180))) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp), content = buttons)
        }
    }
}

/** A slim Automatum bar docked above the hand ([AboveHand]): one line of [content] (a prompt, then its buttons), in the
 * dock's look. A choice's bar ([eye], the target bars) has the eye at its left (STK-044, owner playtest 2026-11-04 #4:
 * every choice can be hidden to look at the board, and shown again); hidden, only the eye stays, at the same spot. */
@Composable
internal fun AutomatumDockBar(description: String, eye: Boolean = false, content: @Composable RowScope.() -> Unit) {
    if (!eye) {
        Row(Modifier.fillMaxWidth().heightIn(min = 52.dp).pointerInput(Unit) {}.semantics { contentDescription = description }
            .drawBehind {
                drawRect(Color.White)
                drawLine(AutomatumViolet, Offset(0f, 0f), Offset(size.width, 0f), 2.dp.toPx())
            }.padding(start = 14.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically, content = content)
        return
    }
    var hidden by remember(description) { mutableStateOf(false) }
    val shown by animateFloatAsState(if (hidden) 0f else 1f, tween(180), label = description)
    Row(Modifier.fillMaxWidth().heightIn(min = 52.dp)
        .then(if (hidden) Modifier else Modifier.pointerInput(Unit) {}.semantics { contentDescription = description })
        .drawBehind {
            drawRect(Color.White.copy(alpha = shown))
            drawLine(AutomatumViolet.copy(alpha = shown), Offset(0f, 0f), Offset(size.width, 0f), 2.dp.toPx())
        }.padding(start = 4.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Surface(shape = CircleShape, color = Color.White, border = BorderStroke(2.dp, AutomatumViolet.copy(alpha = 1f - shown)),
            shadowElevation = if (hidden) 6.dp else 0.dp) {
            HoldTip(if (hidden) "Show the bar" else "Hide the bar") { EyeButton(hidden = hidden, { hidden = !hidden }, "bar") }
        }
        AnimatedVisibility(!hidden, Modifier.weight(1f), enter = fadeIn(tween(180)), exit = fadeOut(tween(180))) {
            Row(Modifier.fillMaxWidth().padding(start = 6.dp), verticalAlignment = Alignment.CenterVertically, content = content)
        }
    }
}

/** UI review round 2 N3: where an open choice in a dock panel puts its own buttons (OK, Yes / No, Keep): the panel's
 * bottom row beside the eye, as in the cast and respond panels ([DockButtons]). */
internal class DockSlot {
    var buttons by mutableStateOf<(@Composable RowScope.() -> Unit)?>(null)
}

/** The dock panel the composed choice sits in, null outside one. */
internal val LocalDockSlot = compositionLocalOf<DockSlot?> { null }

/** A choice's own buttons: in its dock panel's bottom row ([LocalDockSlot]); elsewhere (the cast panel) right-aligned
 * under the choice. The main button last. */
@Composable
internal fun DockButtons(content: @Composable RowScope.() -> Unit) {
    val slot = LocalDockSlot.current
    if (slot == null) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
            verticalAlignment = Alignment.CenterVertically, content = content)
        return
    }
    SideEffect { slot.buttons = content }
    DisposableEffect(slot) { onDispose { slot.buttons = null } }
}

/** A target bar's title ("Choose any target") over the sentence it asks about ([line], STK-047), in the bar's room; [count]: how
 * many are picked of how many (owner playtest 2026-11-09), beside the title. */
@Composable
internal fun RowScope.TargetBarTitle(title: String, line: String?, count: String? = null) {
    Column(Modifier.weight(1f).padding(vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(1.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CardText(title, Modifier.weight(1f, fill = false).semantics { heading() }, style = androidx.compose.ui.text.TextStyle(fontSize = 13.sp,
                fontWeight = FontWeight.Medium), maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
            if (count != null) Text(count, Modifier.semantics { contentDescription = "Picked: $count" }, fontSize = 12.sp,
                fontWeight = FontWeight.Medium, color = AutomatumViolet, maxLines = 1)
        }
        if (line != null) TargetBarLine(line)
    }
}

/** STK-047: the sentence in up to 4 lines. Only when they cut it, a tap on it shows it whole, scrolling past half the
 * table's height, and another goes back; otherwise it takes no taps. */
@Composable
private fun TargetBarLine(line: String) {
    var cut by remember(line) { mutableStateOf(false) }
    var whole by remember(line) { mutableStateOf(false) }
    val scroll = rememberScrollState()
    Box((if (cut) Modifier.clickable(onClickLabel = if (whole) "Show less" else "Show the whole sentence") { whole = !whole } else Modifier)
        .heightIn(max = screenHeightDp().dp / 2).verticalScroll(scroll, enabled = whole)) {
        CardText(line, Modifier.semantics { contentDescription = "Question: $line" }, color = Color(0xff4b5a5f),
            style = androidx.compose.ui.text.TextStyle(fontSize = 11.sp, lineHeight = 13.sp), maxLines = if (whole) Int.MAX_VALUE else 4,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, onTextLayout = { if (!whole) cut = it.hasVisualOverflow })
    }
}

/** The test tag of a choice's source card ([ChoiceSource]). */
internal const val CHOICE_SOURCE_TAG = "choice-source"

/**
 * STK-044 (owner 2026-11-05): where the choice comes from, at the right of every Automatum panel and target bar: its card,
 * small (an ability as its Stack ability token, STK-046); holding it inspects it (TBL-104) through [inspector].
 */
@Composable
internal fun ChoiceSource(card: ChoiceCardData, inspector: ChoiceInspector, modifier: Modifier = Modifier) {
    ChoiceCardFace(card, modifier.testTag(CHOICE_SOURCE_TAG)
        .choiceCardGestures(card.key, tap = {}, hold = { inspector.hold(card, it) }, moved = inspector::moved), small = true)
}

/**
 * The head of an Automatum panel (STK-044): its [title], saying only what the choice is ("Scry 2", "Choose a target · 2"),
 * the lines [below] it, then at the right [trailing] (a count) and the choice's [source] ([ChoiceSource]), when it has one.
 */
@Composable
internal fun ChoiceHead(title: String, source: ChoiceCardData?, inspector: ChoiceInspector,
    trailing: @Composable RowScope.() -> Unit = {}, titleLines: Int = Int.MAX_VALUE, below: @Composable ColumnScope.() -> Unit = {}) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            // A cost in the title ("Pay {2}{G}") shows its symbols (STK-030)
            CardText(title, Modifier.semantics { heading() }, style = androidx.compose.ui.text.TextStyle(fontWeight = FontWeight.Bold, fontSize = 15.sp),
                maxLines = titleLines, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
            below()
        }
        trailing()
        if (source != null) ChoiceSource(source, inspector)
    }
}


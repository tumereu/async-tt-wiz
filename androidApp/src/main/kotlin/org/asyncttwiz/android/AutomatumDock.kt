package org.asyncttwiz.android

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
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

/**
 * One place for every Automatum panel (UI review #4, decided for the owner 2026-11-03): like the cast panel (I-D47), the
 * table's full width with its bottom exactly on the top of the viewer's hand row [hand] (the table's bottom without one),
 * so the hand is never covered. In landscape it is at most [LANDSCAPE_WIDTH] wide from the left edge, so the combat area and
 * its damage preview in the middle of the wide table stay visible (CMB-029).
 */
@Composable
internal fun AboveHand(hand: TableBounds?, content: @Composable () -> Unit) {
    Layout(content = content, modifier = Modifier.fillMaxSize()) { measurables, constraints ->
        val bottom = (hand?.y?.dp?.roundToPx() ?: constraints.maxHeight).coerceIn(0, constraints.maxHeight)
        val width = if (constraints.maxWidth > constraints.maxHeight) minOf(LANDSCAPE_WIDTH.roundToPx(), constraints.maxWidth)
            else constraints.maxWidth
        val placeable = measurables.firstOrNull()?.measure(Constraints(minWidth = width, maxWidth = width, maxHeight = bottom))
        layout(constraints.maxWidth, constraints.maxHeight) { placeable?.place(0, bottom - placeable.height) }
    }
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
        // Measured after the button row, so a panel given little room (attached to the combat band) scrolls inside
        AnimatedVisibility(!hidden, Modifier.weight(1f, fill = false), enter = fadeIn(tween(180)) + expandVertically(tween(180), expandFrom = Alignment.Bottom),
            exit = fadeOut(tween(180)) + shrinkVertically(tween(180), shrinkTowards = Alignment.Bottom)) {
            Column(Modifier.padding(start = 10.dp, end = 10.dp, top = 8.dp).heightIn(max = 440.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(6.dp), content = content)
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

/** The test tag of a choice's source card ([ChoiceSource]). */
internal const val CHOICE_SOURCE_TAG = "choice-source"

/**
 * STK-044 (owner 2026-11-05): where the choice comes from, at the right of every Automatum panel and target bar: its card,
 * small (an ability as its Stack ability token, STK-046); holding it inspects it (TBL-104) through [inspector].
 */
@Composable
internal fun ChoiceSource(card: ChoiceCardData, inspector: ChoiceInspector, modifier: Modifier = Modifier) {
    ChoiceCardFace(card, modifier.testTag(CHOICE_SOURCE_TAG)
        .choiceCardGestures(card.key, tap = {}, hold = { inspector.hold(card, it) }), small = true)
}

/**
 * The head of an Automatum panel (STK-044): its [title], saying only what the choice is ("Scry 2", "Choose a target · 2"),
 * the lines [below] it, then at the right [trailing] (a count) and the choice's [source] ([ChoiceSource]), when it has one.
 */
@Composable
internal fun ChoiceHead(title: String, source: ChoiceCardData?, inspector: ChoiceInspector,
    trailing: @Composable RowScope.() -> Unit = {}, below: @Composable ColumnScope.() -> Unit = {}) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, Modifier.semantics { heading() }, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            below()
        }
        trailing()
        if (source != null) ChoiceSource(source, inspector)
    }
}

/**
 * Picking a Stack entry as a target (STK-048, UI review round 2 #7): docked above the hand like every Automatum panel,
 * the Stack's entries top first drawn as cards (hold: Inspect), the legal ones ringed in violet, the others dimmed; a tap
 * on a legal one picks it. Back returns to picking on the table. [source]: what the target is chosen for (STK-044).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun StackTargetPicker(toolkit: TableToolkitController, play: TableBoard, source: ChoiceCardData?) {
    val viewer = toolkit.viewer
    val targets = toolkit.stackTargets(play)
    val stack = CombatStackRules.stack(play.board)
    val entries = contentsEntries(play, PileContents(stack.id, "Stack", stack.cards, true), viewer)
    var hidden by remember { mutableStateOf(false) }
    val inspector = remember { ChoiceInspector() }
    AutomatumDockPanel("Stack target picker", hidden, { hidden = !hidden }, "target picker", buttons = {
        ChoiceButton("Back", Icons.AutoMirrored.Outlined.ArrowBack, onClick = toolkit::closeStackTargets)
    }) {
        ChoiceHead("Choose a target · ${targets.size}", source, inspector) {
            ChoiceHintText("Tap a spell or ability ringed in violet (1 is the top of the Stack).")
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            entries.forEachIndexed { index, entry ->
                val id = entry.cardId
                val legal = id in targets
                val card = choiceCard(play, viewer, id, CombatStackRules.inspection(play.board, id)?.id, entry.faceVisible, entry.name)
                    .copy(own = CombatStackRules.controller(play.board, id) == viewer)
                    .let { c -> entry.ability?.let { c.copy(name = it.name, ability = it) } ?: c }
                ChoiceCardFace(card, Modifier.alpha(if (legal) 1f else .4f)
                    .then(if (legal) Modifier.semantics { contentDescription = "Legal target" } else Modifier)
                    .choiceCardGestures(id, tap = { if (legal) toolkit.pickStackTarget(id) }, hold = { inspector.hold(card, it) }),
                    selected = legal, badge = "${index + 1}")
            }
        }
        ChoiceInspection(inspector, play, viewer)
    }
}

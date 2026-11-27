package org.asyncttwiz.android

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloat
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
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.drawscope.DrawScope
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
 * its damage preview in the middle of the wide table stay visible (CMB-029); there [hand] is the bottom bar's part of the hand
 * ([handRow]), so the panel stands on the bar, and the hand's cards standing over the battlefield are drawn in front of it
 * ([LocalHandFront], STK-044, owner 2026-11-26).
 */
@Composable
fun AboveHand(hand: TableBounds?, place: PromptPlace = PromptPlace.Board, content: @Composable () -> Unit) {
    // WEB-019: on the desktop table a strip bottom-centre or a centred overlay, by [place]
    LocalDesktopGeometry.current?.let { DesktopPrompt(it, place, content); return }
    val docked = dockedPanelReporter()
    val placed = remember { arrayOfNulls<Rect>(1) }
    Layout(content = content, modifier = Modifier.fillMaxSize().handInFront(placed)) { measurables, constraints ->
        val bottom = (hand?.y?.dp?.roundToPx() ?: constraints.maxHeight).coerceIn(0, constraints.maxHeight)
        val width = if (constraints.maxWidth > constraints.maxHeight) minOf(LANDSCAPE_WIDTH.roundToPx(), constraints.maxWidth)
            else constraints.maxWidth
        val placeable = measurables.firstOrNull()?.measure(Constraints(minWidth = width, maxWidth = width, maxHeight = bottom))
        docked((placeable?.height ?: 0).toDp().value)
        placed[0] = placeable?.let { Rect(0f, (bottom - it.height).toFloat(), it.width.toFloat(), bottom.toFloat()) }
        layout(constraints.maxWidth, constraints.maxHeight) { placeable?.place(0, bottom - placeable.height) }
    }
}

/**
 * STK-044, TBL-238 (owner 2026-11-26): draws the viewer's hand cards standing over the battlefield again, within [clip] (px, the
 * docked panel's place), in front of what lies there: the table provides one in the phone's landscape layout, where a panel
 * stands on the bottom bar under the hand's large cards; none elsewhere.
 */
fun interface HandFront {
    fun DrawScope.drawFront(clip: Rect)
}

/** The table's [HandFront], null where nothing docked has hand cards in front of it. */
val LocalHandFront = compositionLocalOf<HandFront?> { null }

/** A docked panel's layout drawing the hand's cards in front of the panel where it was placed ([placed], px), with [LocalHandFront]. */
@Composable
fun Modifier.handInFront(placed: Array<Rect?>): Modifier {
    val front = LocalHandFront.current ?: return this
    return drawWithContent {
        drawContent()
        placed[0]?.let { with(front) { drawFront(it) } }
    }
}

/**
 * D2 (SYS-041): the heights of the Automatum panels and bars open over the table's bottom, read by the table's pan
 * bounds when a pan starts (personal UI, never state that recomposes anything).
 */
class DockedPanels {
    private val heights = mutableMapOf<Any, Float>()
    /** The tallest open panel's height (dp); 0 with none open. */
    val height: Float get() = heights.values.maxOrNull() ?: 0f
    fun report(key: Any, height: Float) { if (height > 0f) heights[key] = height else heights.remove(key) }
}

val LocalDockedPanels = compositionLocalOf<DockedPanels?> { null }

/** Reports this docked panel's measured height (dp) to the table's [DockedPanels] while it is open. */
@Composable
fun dockedPanelReporter(): (Float) -> Unit {
    val panels = LocalDockedPanels.current ?: return {}
    val key = remember { Any() }
    DisposableEffect(panels, key) { onDispose { panels.report(key, 0f) } }
    return remember(panels, key) { { height: Float -> panels.report(key, height) } }
}

val LANDSCAPE_WIDTH = 420.dp

/** The viewer's hand row on [scene], where Automatum's panels dock ([AboveHand]): in the phone's landscape layout its part in the
 * bottom bar (STK-044, owner 2026-11-26: a panel stands on the bar, its fan's cards above it drawn in front of the panel). */
fun handRow(scene: TableScene?, viewer: String): TableBounds? =
    scene?.items?.firstOrNull { it.kind == TableItemKind.HAND && it.owner == viewer }?.let { it.handBand ?: it.bounds }

/**
 * An Automatum panel docked above the hand ([AboveHand]) in the cast panel's look: white with a violet top edge, its
 * [content] above a bottom row of fixed height with the eye at its left and the panel's [buttons] at its right (Hold
 * first, then the others, the main one last). The eye never moves: [hidden] fades everything else away around it.
 * [what] names the panel for the eye's description and hold tip. [resizable]: its content has the drag handle and the
 * shared size of the large panels ([ResizablePanelContent], STK-222), as every panel above the hand has, not one attached to
 * the combat band (CMB-033) nor a one-line bar.
 */
@Composable
fun AutomatumDockPanel(description: String, hidden: Boolean, toggleHidden: () -> Unit, what: String,
    buttons: @Composable RowScope.() -> Unit = {}, resizable: Boolean = true, content: @Composable ColumnScope.() -> Unit) {
    val shown by animateFloatAsState(if (hidden) 0f else 1f, tween(180), label = description)
    // WEB-019: hidden, a centred overlay's dimming goes with it
    val dim = LocalPromptDim.current
    if (dim != null) SideEffect { dim.shown = shown }
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
                // STK-222, STK-223: at most the shared panel size high, with its drag handle
                ResizablePanelContent(440.dp, what, Modifier.padding(start = 10.dp, end = 10.dp, top = 8.dp), resizable,
                    Arrangement.spacedBy(6.dp), content)
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
fun AutomatumDockBar(description: String, eye: Boolean = false, content: @Composable RowScope.() -> Unit) {
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
    val dim = LocalPromptDim.current
    if (dim != null) SideEffect { dim.shown = shown }
    Row(Modifier.fillMaxWidth().heightIn(min = 52.dp)
        .then(if (hidden) Modifier else Modifier.pointerInput(Unit) {}.semantics { contentDescription = description })
        .drawBehind {
            drawRect(Color.White.copy(alpha = shown))
            drawLine(AutomatumViolet.copy(alpha = shown), Offset(0f, 0f), Offset(size.width, 0f), 2.dp.toPx())
        }.padding(start = 4.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        // STK-176 (owner-reported 2026-11-26: the eye moved): at the bar's bottom left, in a row of the panels' bottom row's height,
        // so it sits where every panel's eye does however tall the bar's words and card make it, and stays there hidden
        Box(Modifier.align(Alignment.Bottom).height(52.dp), contentAlignment = Alignment.Center) {
            Surface(shape = CircleShape, color = Color.White, border = BorderStroke(2.dp, AutomatumViolet.copy(alpha = 1f - shown)),
                shadowElevation = if (hidden) 6.dp else 0.dp) {
                HoldTip(if (hidden) "Show the bar" else "Hide the bar") { EyeButton(hidden = hidden, { hidden = !hidden }, "bar") }
            }
        }
        AnimatedVisibility(!hidden, Modifier.weight(1f), enter = fadeIn(tween(180)), exit = fadeOut(tween(180))) {
            Row(Modifier.fillMaxWidth().padding(start = 6.dp), verticalAlignment = Alignment.CenterVertically, content = content)
        }
    }
}

/** UI review round 2 N3: where an open choice in a dock panel puts its own buttons (OK, Yes / No, Keep): the panel's
 * bottom row beside the eye, as in the cast and respond panels ([DockButtons]). */
class DockSlot {
    var buttons by mutableStateOf<(@Composable RowScope.() -> Unit)?>(null)
}

/** The dock panel the composed choice sits in, null outside one. */
val LocalDockSlot = compositionLocalOf<DockSlot?> { null }

/** A choice's own buttons: in its dock panel's bottom row ([LocalDockSlot]); elsewhere (the cast panel) right-aligned
 * under the choice. The main button last. */
@Composable
fun DockButtons(content: @Composable RowScope.() -> Unit) {
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
 * many are picked of how many (owner playtest 2026-11-09), beside the title. STK-178 (owner 2026-11-21, E12): [source] names
 * what the choice comes from, between the title and the sentence. */
@Composable
fun RowScope.TargetBarTitle(title: String, line: String?, count: String? = null, pieces: List<PickPiece>? = null, source: String? = null,
    asked: AskedSpan? = null) {
    Column(Modifier.weight(1f).padding(vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(1.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CardText(title, Modifier.weight(1f, fill = false).semantics { heading() }, style = androidx.compose.ui.text.TextStyle(fontSize = 13.sp,
                fontWeight = FontWeight.Medium), maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
            if (count != null) Text(count, Modifier.semantics { contentDescription = "Picked: $count" }, fontSize = 12.sp,
                fontWeight = FontWeight.Medium, color = AutomatumViolet, maxLines = 1)
        }
        if (source != null) ChoiceSourceName(source, small = true)
        // STK-047 (owner 2026-11-11, which pick is which): the whole text, the phrase picked now shining, earlier picks named
        if (!pieces.isNullOrEmpty()) TargetBarLine(pieces.joinToString("") { it.text }, pieces)
        // STK-230, STK-142 (owner 2026-11-25): the part [asked] about (a payment's "you may pay") shines as the phrase picked now does
        else if (line != null && asked != null && asked.end <= line.length) TargetBarLine(line, listOf(PickPiece(line.substring(0, asked.start)),
            PickPiece(asked.of(line), now = true), PickPiece(line.substring(asked.end))).filter { it.text.isNotEmpty() })
        else if (line != null) TargetBarLine(line, null)
    }
}

/**
 * STK-047 (owner 2026-11-11, which pick is which): a piece of a target bar's text: its words, the account id of a chosen
 * name's [controller] (which colours it, as on the Stack, STK-057), or the phrase being picked [now].
 */
data class PickPiece(val text: String, val controller: String? = null, val now: Boolean = false)

/** The violet the phrase picked now shines in, softly and slowly (owner 2026-11-11: gentle, calm, readable). */
private val PickNowViolet = Color(0xff5b3fb0)

/** STK-047: the sentence in up to 4 lines. Only when they cut it, a tap on it shows it whole, scrolling past half the
 * table's height, and another goes back; otherwise it takes no taps. [pieces]: the text as picks name it, the phrase picked
 * now in violet on a softly pulsing light violet, chosen names in their controllers' colours. */
@Composable
private fun TargetBarLine(line: String, pieces: List<PickPiece>?) {
    var cut by remember(line) { mutableStateOf(false) }
    var whole by remember(line) { mutableStateOf(false) }
    val scroll = rememberScrollState()
    val viewer = LocalChoiceFrame.current.viewer
    val now = pieces?.firstOrNull { it.now }?.text
    val glow = pickNowGlow(now != null)
    Box((if (cut) Modifier.clickable(onClickLabel = if (whole) "Show less" else "Show the whole sentence") { whole = !whole } else Modifier)
        .heightIn(max = screenHeightDp().dp / 2).verticalScroll(scroll, enabled = whole)) {
        val style = androidx.compose.ui.text.TextStyle(fontSize = 11.sp, lineHeight = 13.sp)
        val textModifier = Modifier.semantics { contentDescription = "Question: $line" }
        if (pieces == null) CardText(line, textModifier, color = Color(0xff4b5a5f), style = style, maxLines = if (whole) Int.MAX_VALUE else 4,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, onTextLayout = { if (!whole) cut = it.hasVisualOverflow })
        else CardTextStyled(pieces.map { piece ->
            piece.text to when {
                piece.now -> pickNowStyle(glow)
                piece.controller != null -> androidx.compose.ui.text.SpanStyle(fontWeight = FontWeight.Medium,
                    color = if (piece.controller == viewer) OwnTableColor else OpponentTableColor)
                else -> null
            }
        }, textModifier, color = Color(0xff4b5a5f), style = style, maxLines = if (whole) Int.MAX_VALUE else 4,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, onTextLayout = { if (!whole) cut = it.hasVisualOverflow })
        if (now != null) Box(Modifier.size(0.dp).semantics { contentDescription = "Choosing now: $now" })
    }
}

/** STK-142: how strongly the phrase picked now glows, softly pulsing while [on]; 0 when not. */
@Composable
private fun pickNowGlow(on: Boolean): Float = if (!on) 0f else androidx.compose.animation.core.rememberInfiniteTransition(label = "pick now")
    .animateFloat(.12f, .30f, androidx.compose.animation.core.infiniteRepeatable(tween(1400), androidx.compose.animation.core.RepeatMode.Reverse),
        label = "pick now glow").value

/** STK-142: the phrase picked now, violet on a light violet [glow]. */
private fun pickNowStyle(glow: Float) = androidx.compose.ui.text.SpanStyle(color = PickNowViolet, fontWeight = FontWeight.SemiBold,
    background = AutomatumViolet.copy(alpha = glow))

/**
 * STK-230, STK-142 (owner 2026-11-25): a spell's or ability's whole [text] under a panel's title, small and grey, with the part
 * the panel is about ([asked]: the clause its question asks, the reveal it shows) drawn as the target bar draws the phrase
 * picked now: violet on a softly pulsing light violet. Its semantics say the text (with [label] before it) and, apart, the part
 * highlighted ("Highlighted: …").
 */
@Composable
fun AskedText(text: String, asked: AskedSpan?, label: String, modifier: Modifier = Modifier) {
    val style = androidx.compose.ui.text.TextStyle(fontSize = 11.sp, lineHeight = 14.sp)
    val textModifier = modifier.semantics { contentDescription = "$label: $text" }
    if (asked == null || asked.end > text.length) {
        CardText(text, textModifier, color = Color(0xff4b5a5f), style = style)
        return
    }
    val glow = pickNowGlow(true)
    Box {
        CardTextStyled(listOf(text.substring(0, asked.start) to null, asked.of(text) to pickNowStyle(glow), text.substring(asked.end) to null)
            .filter { it.first.isNotEmpty() }, textModifier, color = Color(0xff4b5a5f), style = style)
        Box(Modifier.size(0.dp).semantics { contentDescription = "Highlighted: ${asked.of(text)}" })
    }
}

/** The test tag of a choice's source card ([ChoiceSource]). */
const val CHOICE_SOURCE_TAG = "choice-source"

/** The test tag of a choice's source named in words beside its card ([ChoiceSourceName], STK-178). */
const val CHOICE_SOURCE_NAME_TAG = "choice-source-name"

/**
 * STK-178 (owner 2026-11-21, E12): what a choice comes from in words, beside its source card: the source's [name] ("Ember
 * Kindler's ability", "Copy of Spark Seer's triggered ability", a spell's or permanent's name) and an ability's whole [text].
 */
data class ChoiceSourceWords(val name: String, val text: String?) {
    companion object {
        /** The words of [source] as its card or token at the panel's right shows it; [copy]: the choice is asked for a copy of it
         * before the copy is put on the Stack (CR 707.10c), so it is named as the copy. None for no source or one the viewer
         * may not see. */
        fun of(source: ChoiceCardData?, copy: Boolean = false): ChoiceSourceWords? {
            if (source == null || !source.shown) return null
            val ability = source.ability?.let { if (copy && !it.copy) it.copy(copy = true) else it }
            if (ability != null) return ChoiceSourceWords(ability.name, ability.text.trim().ifEmpty { null })
            return ChoiceSourceWords(if (copy) "Copy of ${source.name}" else source.name, null)
        }
    }
}

/** STK-178 (E12): the name of what a choice comes from, under the panel's or bar's title ([small] in a slim bar). */
@Composable
fun ChoiceSourceName(name: String, small: Boolean = false) {
    Text(name, Modifier.testTag(CHOICE_SOURCE_NAME_TAG), color = MenuInk, fontSize = if (small) 11.sp else 12.sp,
        lineHeight = if (small) 13.sp else 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 2,
        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
}

/**
 * STK-044 (owner 2026-11-05): where the choice comes from, at the right of every Automatum panel and target bar: its card,
 * small (an ability as its Stack ability token, STK-046); holding it inspects it (TBL-104) through [inspector].
 */
@Composable
fun ChoiceSource(card: ChoiceCardData, inspector: ChoiceInspector, modifier: Modifier = Modifier) {
    ChoiceCardFace(card, modifier.testTag(CHOICE_SOURCE_TAG)
        .choiceCardGestures(card.key, tap = {}, hold = { inspector.hold(card, it) }, moved = inspector::moved), small = true)
}

/**
 * The head of an Automatum panel (STK-044): its [title], saying only what the choice is ("Scry 2", "Choose a target · 2"),
 * the lines [below] it, then at the right [trailing] (a count) and the choice's [source] ([ChoiceSource]), when it has one.
 */
@Composable
fun ChoiceHead(title: String, source: ChoiceCardData?, inspector: ChoiceInspector,
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


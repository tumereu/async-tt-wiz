package org.asyncttwiz.android

import android.os.SystemClock
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.AlignmentLine
import androidx.compose.ui.layout.HorizontalAlignmentLine
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs
import org.asyncttwiz.protocol.TableActions

internal val LocalCardRingAnchors = compositionLocalOf<Map<String, TableBounds>> { emptyMap() }
/** TBL-067: the areas table popups keep off when they fit elsewhere (both hands). */
internal val LocalPopupAvoid = compositionLocalOf<List<TableBounds>> { emptyList() }
private val LocalRingInputTargets = compositionLocalOf<(Any, TableBounds?, Boolean) -> Unit> { { _, _, _ -> } }

/** Register controls (or a list's scroll viewport), never a button grid's container or gaps. */
internal fun Modifier.ringInputTarget(draggable: Boolean = false): Modifier = composed {
    val register = LocalRingInputTargets.current
    val key = remember { Any() }
    DisposableEffect(register, key) { onDispose { register(key, null, draggable) } }
    onGloballyPositioned {
        val windowOrigin = it.localToScreen(Offset.Zero) - it.localToWindow(Offset.Zero)
        val bounds = it.boundsInWindow()
        register(key, TableBounds(windowOrigin.x + bounds.left, windowOrigin.y + bounds.top, bounds.width, bounds.height), draggable)
    }
}
internal data class RingEntry(val id: String, val label: String, val control: MenuControl, val fill: Color? = null)

internal fun ringShortLabel(name: String): String = name.removePrefix("Move to ").removePrefix("Move ").substringBefore(" · ")

/** A layout cell nobody supplied stays visible and disabled; a layout never shows a hole. */
internal fun ringPlaceholder(id: String): RingEntry = RingEntry(id, ringShortLabel(id), MenuControl(id, false) {})

/** One thing the engine offers for the card (§3 I4): a full-width row above the menu's cells, its [icon] by kind. */
internal data class EngineRow(val control: MenuControl, val icon: androidx.compose.ui.graphics.vector.ImageVector)

/** A card's menu (TBL-063, TBL-064): [rows], the engine's actions (§3 I4), above the one [page]'s cells. */
@Composable
internal fun CardActionRing(identity: String, viewId: String,
    entries: List<RingEntry>, own: Boolean, ownerColor: Color, dismiss: () -> Unit,
    page: CardRingPage, rows: List<EngineRow> = emptyList()) {
    val slots = CardRingPages.layout(page.actions)
    val gridRows = inlinePopupGridRows(slots)
    val rowsHeight = rows.size * (EngineRowHeight + InlinePopupStyle.cellGap)
    val reserved = gridRows * CardRingGeometry.cell + (gridRows - 1).coerceAtLeast(0) * InlinePopupStyle.cellGap + rowsHeight
    val anchor = LocalCardRingAnchors.current[viewId]
    BoxWithConstraints(Modifier.fillMaxSize()) {
    val gridWidth = CardRingGeometry.gridSide + (CardRingPages.columns - 1) * InlinePopupStyle.cellGap
    // A full-height header must not force the menu over its card on small screens.
    // Keep the header fixed and scroll only the grid when no full-height side fits.
    val contentHeight = anchor?.let { card ->
        val vertical = maxOf(card.y - 20f, maxHeight.value - card.bottom - 20f)
        val beside = maxOf(card.x - 20f, maxWidth.value - card.right - 20f) >= gridWidth
        if (beside) maxHeight.value - 24f else vertical
    } ?: (maxHeight.value - 24f)
    TableInlinePopup(identity, viewId, anchor, own, ownerColor, dismiss,
        description = "Card action ring", title = "Card actions") {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            key(identity, viewId) {
                val maxHeight = contentHeight.coerceAtLeast(48f)
                Column(Modifier.heightIn(min = minOf(reserved, maxHeight).dp, max = maxHeight.dp)
                    .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(InlinePopupStyle.cellGap.dp)) {
                    for (row in rows) EngineActionRow(row, ownerColor)
                    repeat(gridRows) { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(InlinePopupStyle.cellGap.dp)) {
                            var column = 0
                            while (column < CardRingPages.columns) {
                                val slot = slots.firstOrNull { it.cell == row * CardRingPages.columns + column }
                                val entry = slot?.let { s -> entries.firstOrNull { it.id == s.id } ?: ringPlaceholder(s.id) }
                                if (entry == null) Spacer(Modifier.size(CardRingGeometry.cell.dp))
                                else InlinePopupCell(entry.control, entry.label, ownerColor, fill = entry.fill)
                                column += slot?.span ?: 1
                            }
                        }
                    }
                }
            }
        }
    }
    }
}

private const val EngineRowHeight = 40f

/** An engine action of the card (§3 I4): its icon and its text (an ability's printed text), greyed with its reason
 * when Automatum doesn't allow it now. */
@Composable
internal fun EngineActionRow(row: EngineRow, color: Color) {
    val control = row.control
    RingPress(control, Modifier.width((CardRingGeometry.gridSide + (CardRingPages.columns - 1) * InlinePopupStyle.cellGap).dp)
        .heightIn(min = EngineRowHeight.dp), AutomatumViolet) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
            androidx.compose.material3.Icon(row.icon, null, Modifier.size(20.dp),
                tint = AutomatumViolet.copy(alpha = if (control.enabled) 1f else .35f))
            CardText(control.name, Modifier.weight(1f).padding(start = 8.dp), color = MenuInk.copy(alpha = if (control.enabled) 1f else .38f),
                style = androidx.compose.ui.text.TextStyle(fontSize = 12.sp, lineHeight = 14.sp, fontWeight = FontWeight.Medium),
                maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

private val InlinePopupFocusLine = HorizontalAlignmentLine(::minOf)

/** Marks the part of a panel that a popup opened beside its anchor lines up with (e.g. the life row by the life square). */
internal fun Modifier.inlinePopupFocus() = layout { measurable, constraints ->
    val placeable = measurable.measure(constraints)
    layout(placeable.width, placeable.height, mapOf(InlinePopupFocusLine to placeable.height / 2)) { placeable.place(0, 0) }
}

internal data class InlinePopupAnchor(val id: String, val bounds: TableBounds?, val own: Boolean)
internal val LocalInlinePopupAnchor = compositionLocalOf { InlinePopupAnchor("table", null, true) }
internal val LocalInlinePopupColor = compositionLocalOf { OwnTableColor }
private val LocalInlinePopupDismiss = compositionLocalOf<(() -> Unit)?> { null }
internal object InlinePopupStyle {
    const val cellGap = 2f
    const val enterMillis = 90
    const val exitMillis = 70
    const val initialScale = .88f
}

internal fun inlinePopupGridRows(slots: List<CardRingSlot>): Int =
    slots.maxOfOrNull { (it.cell + it.span + CardRingPages.columns - 1) / CardRingPages.columns } ?: 0

/** All on-table context panels share placement, animation, input guard, relay and visual chrome.
 * The table host owns outside gestures, so a second tap can dismiss and draw/rotate immediately. */
@Composable
internal fun TableInlinePopup(identity: String, viewId: String, anchor: TableBounds?, own: Boolean,
    ownerColor: Color, dismiss: () -> Unit, width: Dp = CardRingGeometry.squareSide.dp,
    height: Dp = CardRingGeometry.squareSide.dp, description: String = "Table context popup",
    title: String = description, switchPage: (Int) -> Unit = {}, sheet: Boolean = false,
    placement: TableBounds? = null, content: @Composable () -> Unit) {
    val guard = remember(identity, viewId) { CardRingInputGuard(SystemClock.uptimeMillis()) }
    val currentSwitch by rememberUpdatedState(switchPage)
    val relay = LocalTableTapRelay.current
    val inputTargets = remember { mutableMapOf<Any, TableBounds>() }
    val dragTargets = remember { mutableMapOf<Any, TableBounds>() }
    var origin by remember { mutableStateOf(Offset.Zero) }
    val registerTarget = remember { { key: Any, bounds: TableBounds?, draggable: Boolean ->
        if (bounds == null) inputTargets.remove(key) else inputTargets.put(key, bounds)
        if (bounds != null && draggable) dragTargets[key] = bounds else dragTargets.remove(key)
        Unit
    } }
    val currentDismiss by rememberUpdatedState(dismiss)
    val avoid = LocalPopupAvoid.current
    var entered by remember(identity, viewId) { mutableStateOf(false) }
    var closing by remember(identity, viewId) { mutableStateOf(false) }
    val requestDismiss = { closing = true }
    val currentRequestDismiss by rememberUpdatedState(requestDismiss)
    LaunchedEffect(identity, viewId) {
        DebugJank.animation("popup", true)
        entered = true
        kotlinx.coroutines.delay(InlinePopupStyle.enterMillis.toLong())
        DebugJank.animation("popup", false)
    }
    DisposableEffect(identity, viewId) { onDispose { DebugJank.animation("popup", false) } }
    val progress by animateFloatAsState(if (entered && !closing) 1f else 0f,
        tween(if (closing) InlinePopupStyle.exitMillis else InlinePopupStyle.enterMillis), label = "popup",
        finishedListener = { if (closing && it == 0f) currentDismiss() })
    DisposableEffect(relay, identity, viewId) {
        val input = TableRingInput(viewId, { screen ->
            inputTargets.values.none { it.contains(TablePoint(screen.x, screen.y)) }
        }, { currentRequestDismiss() })
        relay?.ring = input
        onDispose { if (relay?.ring === input) relay.ring = null }
    }
    BackHandler(onBack = requestDismiss)
    CompositionLocalProvider(LocalRingInputTargets provides registerTarget, LocalInlinePopupDismiss provides requestDismiss,
        LocalInlinePopupColor provides ownerColor) {
    BoxWithConstraints(Modifier.fillMaxSize().semantics {
        contentDescription = if (description == "Table context popup") title else description
    }) {
        val card = remember(identity, viewId, maxWidth, maxHeight) {
            anchor ?: TableBounds(maxWidth.value / 2 - 20, maxHeight.value / 2 - 28, 40f, 56f)
        }
        // Standalone fallback; the stable table host relays outside presses before they reach here.
        Box(Modifier.fillMaxSize().pointerInput(Unit) { detectTapGestures(onTap = { requestDismiss() }) })
        Layout(content = {
        val popupModifier = Modifier
            .graphicsLayer { alpha = progress; scaleX = InlinePopupStyle.initialScale + (1 - InlinePopupStyle.initialScale) * progress; scaleY = scaleX }
            .onGloballyPositioned { origin = it.localToScreen(Offset.Zero) }
            // Only registered controls are inside; empty cells and inter-control gaps relay to the table.
            .semantics { contentDescription = if (description == "Card action ring") "Card action grid" else "Context action grid" }
            .pointerInput(guard) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                // The relay alone can replace the first tap with the table's double-tap action.
                if (!down.isConsumed) {
                    val screen = origin + down.position
                    val valueDrag = dragTargets.values.any { it.contains(TablePoint(screen.x, screen.y)) }
                    // The opening gesture's tail must not adjust a counter either, so value drags respect the guard.
                    val blocked = closing || !guard.accepts(down.uptimeMillis)
                    var swipe = false
                    var delta = Offset.Zero
                    if (blocked) down.consume()
                    do {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        val change = event.changes.firstOrNull { it.id == down.id }
                        if (change != null) {
                            delta = change.position - down.position
                            if (!valueDrag && abs(delta.x) > 32.dp.toPx() && abs(delta.x) > abs(delta.y) * 1.5f) swipe = true
                        }
                        if (blocked || swipe) event.changes.forEach { it.consume() }
                    } while (event.changes.any { it.pressed })
                    if (swipe && !blocked) currentSwitch(if (delta.x < 0) 1 else -1)
                }
            }
        }
        if (sheet) Surface(popupModifier, shape = RoundedCornerShape(8.dp), color = Color.White,
            border = BorderStroke(1.dp, ownerColor), shadowElevation = 4.dp) { content() }
        else Box(popupModifier) { content() }
        }, modifier = Modifier.fillMaxSize()) { measurables, constraints ->
            val maxW = minOf(width.roundToPx(), (constraints.maxWidth - 24.dp.roundToPx()).coerceAtLeast(0))
            val maxH = (constraints.maxHeight - 24.dp.roundToPx()).coerceAtLeast(0)
            val child = measurables.single().measure(androidx.compose.ui.unit.Constraints(
                // A sheet is as tall as its content, up to [height] (STK-018: no empty panel under a short list).
                minWidth = if (sheet) maxW else 0, maxWidth = maxW, minHeight = 0,
                maxHeight = if (sheet) minOf(height.roundToPx(), maxH) else maxH))
            val screenW = constraints.maxWidth.toDp().value; val screenH = constraints.maxHeight.toDp().value
            val childW = child.width.toDp().value; val childH = child.height.toDp().value
            val focus = child[InlinePopupFocusLine].takeIf { it != AlignmentLine.Unspecified }?.toDp()?.value ?: (childH / 2)
            // An explicit placement pins the top-left corner (e.g. beside the card panels); it is only clamped on screen.
            val bounds = placement?.let { b -> TableBounds(b.x.coerceIn(0f, (screenW - childW).coerceAtLeast(0f)),
                b.y.coerceIn(0f, (screenH - childH).coerceAtLeast(0f)), childW, childH) }
                ?: inlinePopupBounds(screenW, screenH, card, own, childW, childH, focus, avoid)
            layout(constraints.maxWidth, constraints.maxHeight) {
                child.place(bounds.x.dp.roundToPx(), bounds.y.dp.roundToPx())
            }
        }
    }
    }
}

/** Prefer a free region beside the captured anchor; clamp measured content on tight screens. */
internal fun inlinePopupBounds(width: Float, height: Float, anchor: TableBounds, own: Boolean,
    desiredWidth: Float, desiredHeight: Float, focusY: Float = desiredHeight / 2,
    avoid: List<TableBounds> = emptyList()): TableBounds {
    CardRingGeometry.place(width, height, anchor, own, desiredWidth, desiredHeight, focusY, avoid)?.let { return it.square }
    val w = desiredWidth.coerceAtMost((width - 24).coerceAtLeast(0f))
    val h = desiredHeight.coerceAtMost((height - 24).coerceAtLeast(0f))
    val y = if (anchor.y >= h + 20) anchor.y - h - 8 else anchor.bottom + 8
    return TableBounds((anchor.center.x - w / 2).coerceIn(12f, (width - w - 12).coerceAtLeast(12f)),
        y.coerceIn(12f, (height - h - 12).coerceAtLeast(12f)), w, h)
}

@Composable
internal fun InlinePopupPanel(dismiss: () -> Unit, title: String, width: Dp = 360.dp, height: Dp = 288.dp,
    key: String = title, sheet: Boolean = false, content: @Composable ColumnScope.() -> Unit) {
    // Opening a card menu from a row clears the toolkit anchor while this panel is still visible.
    // Capture the opening anchor and key by a stable identity (not a count-bearing title) so the
    // input guard does not restart and swallow the row's second tap.
    val current = LocalInlinePopupAnchor.current
    val anchor = remember(key) { current }
    val color = if (anchor.own) OwnTableColor else OpponentTableColor
    TableInlinePopup(key, anchor.id, anchor.bounds, anchor.own, color, dismiss, width, height,
        description = title, title = title, sheet = sheet) {
        Column(Modifier.padding(if (sheet) 8.dp else 0.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(if (sheet) 0.dp else 4.dp)) {
            if (sheet) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                    InlinePopupCell(MenuControl("Close", click = LocalInlinePopupDismiss.current ?: dismiss), "Close", color)
                }
            }
            content()
        }
    }
}

@Composable
internal fun InlinePopupCell(control: MenuControl, label: String = control.name, owner: Color = LocalInlinePopupColor.current,
    modifier: Modifier = Modifier.size(CardRingGeometry.cell.dp), fill: Color? = null) {
    val entry = RingEntry(control.name, label, control, fill = fill)
    RingPress(entry.control, modifier, entry.control.color ?: owner, fill = entry.fill ?: Color.White) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            val twoLines = '\n' in entry.label
            MenuIcon(entry.control.name, Modifier.size(if (twoLines) 18.dp else 24.dp),
                tint = (entry.control.color ?: owner).copy(alpha = if (entry.control.enabled) 1f else .35f))
            val textModifier = if (twoLines) Modifier.semantics { hideFromAccessibility() }
                else Modifier.clearAndSetSemantics {}
            CellLabel(entry.label, MenuInk.copy(alpha = if (entry.control.enabled) 1f else .38f),
                Modifier.padding(horizontal = 2.dp), textModifier)
        }
    }
}

/**
 * A menu or quick-action cell's label (TBL-064): 8 sp on up to two lines, wrapped only between words. Where a word is
 * wider than the cell, or the lines don't fit under the icon (large system text), the label is drawn smaller instead of
 * breaking a word ("Graveyar d") or being cut off.
 */
@Composable
internal fun CellLabel(text: String, color: Color, modifier: Modifier = Modifier, textModifier: Modifier = Modifier) {
    val measurer = rememberTextMeasurer()
    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        val style = LocalTextStyle.current.merge(CellLabelStyle)
        val scale = remember(text, style, constraints, measurer) { cellLabelScale(measurer, text, style, constraints) }
        Text(text, textModifier, color = color, maxLines = 2, overflow = TextOverflow.Clip,
            style = style.copy(fontSize = style.fontSize * scale, lineHeight = style.lineHeight * scale))
    }
}

private val CellLabelStyle = TextStyle(fontSize = 8.sp, lineHeight = 9.sp, letterSpacing = 0.sp,
    fontWeight = FontWeight.Medium, textAlign = TextAlign.Center)

/** The factor (at most 1, at least about .5) [text] is drawn at so its widest word fits one line of [constraints]' width
 * and its at most two lines fit their height. */
internal fun cellLabelScale(measurer: TextMeasurer, text: String, style: TextStyle, constraints: Constraints): Float {
    if (!constraints.hasBoundedWidth || text.isBlank()) return 1f
    val width = constraints.maxWidth
    fun at(scale: Float) = style.copy(fontSize = style.fontSize * scale, lineHeight = style.lineHeight * scale)
    val widest = text.split(' ', '\n').filter { it.isNotEmpty() }
        .maxOf { measurer.measure(it, style, softWrap = false, maxLines = 1).size.width }
    var scale = if (widest > width) width * .97f / widest else 1f
    if (!constraints.hasBoundedHeight) return scale
    repeat(6) {
        val layout = measurer.measure(text, at(scale), maxLines = 2, constraints = Constraints(maxWidth = width))
        if (!layout.didOverflowHeight && layout.size.height <= constraints.maxHeight) return scale
        scale *= .9f
    }
    return scale
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun RingPress(control: MenuControl, modifier: Modifier, color: Color,
    fill: Color = Color.White, role: Role = Role.Button, bordered: Boolean = true, tooltip: String = control.name,
    content: @Composable () -> Unit) {
    var holding by remember { mutableStateOf(false) }
    // I-D46: a refused control stays greyed; a tap shakes it and holding it says why
    val reason = control.refused.takeIf { !control.enabled }
    var shakes by remember { mutableIntStateOf(0) }
    Surface(modifier.shaken(shakes).pressFeedback(control.enabled).ringInputTarget().semantics(mergeDescendants = true) {
            contentDescription = control.name; selected = control.selected; if (!control.enabled) disabled() }
        .combinedClickable(enabled = control.enabled || reason != null, role = role, hapticFeedbackEnabled = false,
            onLongClick = { holding = true }, onClick = if (reason != null) { { shakes += 1 } } else control.click),
        shape = if (bordered) RoundedCornerShape(3.dp) else androidx.compose.ui.graphics.RectangleShape,
        color = fill.compositeOver(Color.White), shadowElevation = if (bordered) 3.dp else 0.dp,
        border = if (bordered) BorderStroke(if (control.selected) 2.dp else 1.dp, color.copy(alpha = if (control.enabled) .5f else .2f)) else null) {
        Box(contentAlignment = Alignment.Center) {
            content()
            if (holding) HoldTooltip(reason ?: tooltip)
        }
    }
    if (holding) LaunchedEffect(control.name) { kotlinx.coroutines.delay(2000); holding = false }
}

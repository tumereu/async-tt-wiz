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
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs
import org.asyncttwiz.protocol.TableActions

internal val LocalCardRingAnchors = compositionLocalOf<Map<String, TableBounds>> { emptyMap() }
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
internal data class RingCompound(val value: String, val minus: MenuControl, val plus: MenuControl,
    val drag: ((Long) -> Unit)? = null, val dragValue: String = value, val corner: Boolean = false)
internal data class RingEntry(val id: String, val label: String, val control: MenuControl,
    val compound: RingCompound? = null, val fill: Color? = null,
    val drag: ((Long) -> Unit)? = null, val dragValue: String = "0")

internal fun ringShortLabel(name: String): String = when (name) {
    "Declare + tap" -> "Decl+tap"
    "Declare without tapping" -> "Declare"
    "Ping / Unping" -> "Ping"
    "Add Stack effect" -> "Effect"
    "Select / deselect" -> "Select"
    "Remove Stack effect" -> "Rm effect"
    "Remove from combat" -> "Rm combat"
    "Source actions" -> "Source"
    else -> name.removePrefix("Move to ").removePrefix("Move ").substringBefore(" · ")
}

@Composable
internal fun CardActionRing(game: String, viewer: String, identity: String, viewId: String,
    entries: List<RingEntry>, own: Boolean, ownerColor: Color, dismiss: () -> Unit, token: Boolean = false, inHand: Boolean = false) {
    val context = LocalContext.current
    val store = remember(context, viewer, game) { CardRingPageStore(context, viewer, game) }
    var page by remember(viewer, game) { mutableIntStateOf(store.read()) }
    val pages = CardRingPages.resolve(entries.map { it.id }, token = token, inHand = inHand)
    val selectedPage = page.coerceIn(pages.indices)
    val slots = CardRingPages.layout(pages[selectedPage].actions)
    val anchor = LocalCardRingAnchors.current[viewId]
    val selectPage: (Int) -> Unit = { page = it; store.write(it) }
    val switchPage: (Int) -> Unit = { delta -> selectPage(CardRingPages.adjacent(selectedPage, delta, pages.size)) }
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
        description = "Card action ring", title = "Card actions", switchPage = switchPage) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            InlinePopupPages(pages.map { it.name }, selectedPage, ownerColor, selectPage)
            key(identity, viewId, selectedPage) {
                Column(Modifier.heightIn(max = (contentHeight - 52f).coerceAtLeast(48f).dp)
                    .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(InlinePopupStyle.cellGap.dp)) {
                    repeat(inlinePopupGridRows(slots)) { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(InlinePopupStyle.cellGap.dp)) {
                            var column = 0
                            while (column < CardRingPages.columns) {
                                val slot = slots.firstOrNull { it.cell == row * CardRingPages.columns + column }
                                val entry = slot?.let { s -> entries.firstOrNull { it.id == s.id } }
                                if (entry == null) Spacer(Modifier.size(CardRingGeometry.cell.dp))
                                else if (entry.compound != null) RingWidget(entry, ownerColor)
                                else InlinePopupCell(entry.control, entry.label, ownerColor,
                                    fill = entry.fill, drag = entry.drag, dragValue = entry.dragValue)
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
                minWidth = if (sheet) maxW else 0, maxWidth = maxW,
                minHeight = if (sheet) minOf(height.roundToPx(), maxH) else 0,
                maxHeight = if (sheet) minOf(height.roundToPx(), maxH) else maxH))
            val screenW = constraints.maxWidth.toDp().value; val screenH = constraints.maxHeight.toDp().value
            val childW = child.width.toDp().value; val childH = child.height.toDp().value
            // An explicit placement pins the top-left corner (e.g. beside the card panels); it is only clamped on screen.
            val bounds = placement?.let { b -> TableBounds(b.x.coerceIn(0f, (screenW - childW).coerceAtLeast(0f)),
                b.y.coerceIn(0f, (screenH - childH).coerceAtLeast(0f)), childW, childH) }
                ?: inlinePopupBounds(screenW, screenH, card, own, childW, childH)
            layout(constraints.maxWidth, constraints.maxHeight) {
                child.place(bounds.x.dp.roundToPx(), bounds.y.dp.roundToPx())
            }
        }
    }
    }
}

/** Prefer a free region beside the captured anchor; clamp measured content on tight screens. */
internal fun inlinePopupBounds(width: Float, height: Float, anchor: TableBounds, own: Boolean,
    desiredWidth: Float, desiredHeight: Float): TableBounds {
    CardRingGeometry.place(width, height, anchor, own, desiredWidth, desiredHeight)?.let { return it.square }
    val w = desiredWidth.coerceAtMost((width - 24).coerceAtLeast(0f))
    val h = desiredHeight.coerceAtMost((height - 24).coerceAtLeast(0f))
    val y = if (anchor.y >= h + 20) anchor.y - h - 8 else anchor.bottom + 8
    return TableBounds((anchor.center.x - w / 2).coerceIn(12f, (width - w - 12).coerceAtLeast(12f)),
        y.coerceIn(12f, (height - h - 12).coerceAtLeast(12f)), w, h)
}

@Composable
internal fun InlinePopupPages(pages: List<String>, selected: Int, color: Color, select: (Int) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Row(Modifier.width((CardRingGeometry.gridSide + (CardRingPages.columns - 1) * InlinePopupStyle.cellGap).dp)
        .height(48.dp).semantics {
        contentDescription = "Action page ${selected + 1} of ${pages.size}, ${pages[selected]}"
    }, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        RingPress(MenuControl("Previous page") { expanded = false; select(CardRingPages.adjacent(selected, -1, pages.size)) },
            Modifier.size(48.dp), color) { Text("◀", color = MenuInk) }
        Box(Modifier.weight(1f)) {
            RingPress(MenuControl("Choose action page") { expanded = !expanded }, Modifier.fillMaxWidth().height(48.dp), color) {
                Text(pages[selected], fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    color = MenuInk, fontWeight = FontWeight.Bold)
            }
            DropdownMenu(expanded, { expanded = false }, modifier = Modifier.heightIn(max = 240.dp).ringInputTarget(draggable = true)) {
                pages.forEachIndexed { index, name ->
                    DropdownMenuItem(text = { Text(name) }, modifier = Modifier.ringInputTarget().semantics {
                        contentDescription = "$name page"; this.selected = index == selected
                    }, onClick = { expanded = false; select(index) })
                }
            }
        }
        RingPress(MenuControl("Next page") { expanded = false; select(CardRingPages.adjacent(selected, 1, pages.size)) },
            Modifier.size(48.dp), color) { Text("▶", color = MenuInk) }
    }
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
private fun RingWidget(entry: RingEntry, owner: Color) {
    val compound = requireNotNull(entry.compound)
    ValueControl(compound.value, entry.control, compound.minus, compound.plus, entry.control.color ?: owner,
        entry.fill ?: Color.White, compound.dragValue, compound.corner, compound.drag)
}

@Composable
internal fun InlinePopupCell(control: MenuControl, label: String = control.name, owner: Color = LocalInlinePopupColor.current,
    modifier: Modifier = Modifier.size(CardRingGeometry.cell.dp), fill: Color? = null,
    drag: ((Long) -> Unit)? = null, dragValue: String = "0") {
    val entry = RingEntry(control.name, label, control, fill = fill, drag = drag, dragValue = dragValue)
    var delta by remember(entry.id) { mutableStateOf<Long?>(null) }
    val dragModifier = if (entry.drag != null) Modifier.ringInputTarget(draggable = true)
        .valueDragGestures(entry.control.enabled && TableActions.numeric(entry.dragValue),
            preview = { delta = it }, commit = entry.drag) else Modifier
    RingPress(entry.control, modifier.then(dragModifier), entry.control.color ?: owner, fill = entry.fill ?: Color.White) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            val twoLines = '\n' in entry.label
            MenuIcon(entry.control.name, Modifier.size(if (twoLines) 18.dp else 24.dp),
                tint = (entry.control.color ?: owner).copy(alpha = if (entry.control.enabled) 1f else .35f))
            val textModifier = if (twoLines) Modifier.semantics { hideFromAccessibility() }
                else Modifier.clearAndSetSemantics {}
            Text(entry.label, Modifier.padding(horizontal = 2.dp).then(textModifier),
                fontSize = 8.sp, lineHeight = 9.sp, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Clip,
                letterSpacing = 0.sp, color = MenuInk.copy(alpha = if (entry.control.enabled) 1f else .38f), fontWeight = FontWeight.Medium)
        }
        delta?.let { HoldTooltip(valueDragLabel(entry.dragValue, it)) }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun RingPress(control: MenuControl, modifier: Modifier, color: Color,
    fill: Color = Color.White, role: Role = Role.Button, bordered: Boolean = true, content: @Composable () -> Unit) {
    var tooltip by remember { mutableStateOf(false) }
    Surface(modifier.pressFeedback(control.enabled).ringInputTarget().semantics(mergeDescendants = true) { contentDescription = control.name; selected = control.selected }
        .combinedClickable(enabled = control.enabled, role = role, hapticFeedbackEnabled = false,
            onLongClick = { tooltip = true }, onClick = control.click),
        shape = if (bordered) RoundedCornerShape(3.dp) else androidx.compose.ui.graphics.RectangleShape,
        color = fill.compositeOver(Color.White), shadowElevation = if (bordered) 3.dp else 0.dp,
        border = if (bordered) BorderStroke(if (control.selected) 2.dp else 1.dp, color.copy(alpha = if (control.enabled) .5f else .2f)) else null) {
        Box(contentAlignment = Alignment.Center) {
            content()
            if (tooltip) HoldTooltip(control.name)
        }
    }
    if (tooltip) LaunchedEffect(control.name) { kotlinx.coroutines.delay(2000); tooltip = false }
}

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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.boundsInWindow
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
    entries: List<RingEntry>, own: Boolean, ownerColor: Color, dismiss: () -> Unit) {
    val context = LocalContext.current
    val store = remember(context, viewer, game) { CardRingPageStore(context, viewer, game) }
    var page by remember(viewer, game) { mutableIntStateOf(store.read()) }
    val pages = CardRingPages.resolve(entries.map { it.id })
    val selectedPage = page.coerceIn(pages.indices)
    val slots = CardRingPages.layout(pages[selectedPage].actions)
    val anchor = LocalCardRingAnchors.current[viewId]
    val selectPage: (Int) -> Unit = { page = it; store.write(it) }
    val switchPage: (Int) -> Unit = { delta -> selectPage((selectedPage + delta).mod(pages.size)) }
    TableInlinePopup(identity, viewId, anchor, own, ownerColor, dismiss,
        description = "Card action ring", title = "Card actions", switchPage = switchPage) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) {
            InlinePopupPages(pages.map { it.name }, selectedPage, ownerColor, selectPage)
            key(identity, viewId, selectedPage) {
                Column(Modifier.size(CardRingGeometry.gridSide.dp)) {
                    repeat(4) { row ->
                        Row {
                            var column = 0
                            while (column < 4) {
                                val slot = slots.firstOrNull { it.cell == row * 4 + column }
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

internal data class InlinePopupAnchor(val id: String, val bounds: TableBounds?, val own: Boolean)
internal val LocalInlinePopupAnchor = compositionLocalOf { InlinePopupAnchor("table", null, true) }
private val LocalInlinePopupDismiss = compositionLocalOf<(() -> Unit)?> { null }
internal object InlinePopupStyle {
    const val enterMillis = 90
    const val exitMillis = 70
    const val initialScale = .88f
}

/** All on-table context panels share placement, animation, input guard, relay and visual chrome.
 * The table host owns outside gestures, so a second tap can dismiss and draw/rotate immediately. */
@Composable
internal fun TableInlinePopup(identity: String, viewId: String, anchor: TableBounds?, own: Boolean,
    ownerColor: Color, dismiss: () -> Unit, width: Dp = CardRingGeometry.squareSide.dp,
    height: Dp = CardRingGeometry.squareSide.dp, description: String = "Table context popup",
    title: String = description, switchPage: (Int) -> Unit = {}, content: @Composable () -> Unit) {
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
    CompositionLocalProvider(LocalRingInputTargets provides registerTarget, LocalInlinePopupDismiss provides requestDismiss) {
    BoxWithConstraints(Modifier.fillMaxSize().semantics { contentDescription = description; paneTitle = title }) {
        val card = anchor ?: TableBounds(maxWidth.value / 2 - 20, maxHeight.value / 2 - 28, 40f, 56f)
        // Capture the opening anchor/viewport only: neither sync nor a page switch moves this square.
        val square = remember(identity, viewId, maxWidth, maxHeight) {
            inlinePopupBounds(maxWidth.value, maxHeight.value, card, own, width.value, height.value)
        }
        // Standalone fallback; the stable table host relays outside presses before they reach here.
        Box(Modifier.fillMaxSize().pointerInput(Unit) { detectTapGestures(onTap = { requestDismiss() }) })
        Surface(Modifier.offset(square.x.dp, square.y.dp).size(square.width.dp, square.height.dp)
            .graphicsLayer { alpha = progress; scaleX = InlinePopupStyle.initialScale + (1 - InlinePopupStyle.initialScale) * progress; scaleY = scaleX }
            .onGloballyPositioned { origin = it.localToScreen(Offset.Zero) }
            .ringInputTarget().semantics { contentDescription = if (description == "Card action ring") "Card action grid" else "Context action grid" }
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
        }, shape = RoundedCornerShape(8.dp), color = Color.White,
            border = BorderStroke(1.dp, ownerColor), shadowElevation = 4.dp) {
            content()
        }
    }
    }
}

/** Preserve the card grid's opening position, falling back to a clamped anchor for tight screens. */
internal fun inlinePopupBounds(width: Float, height: Float, anchor: TableBounds, own: Boolean,
    desiredWidth: Float, desiredHeight: Float): TableBounds {
    if (desiredWidth == CardRingGeometry.squareSide && desiredHeight == CardRingGeometry.squareSide)
        CardRingGeometry.place(width, height, anchor, own)?.let { return it.square }
    val w = desiredWidth.coerceAtMost((width - 24).coerceAtLeast(0f))
    val h = desiredHeight.coerceAtMost((height - 24).coerceAtLeast(0f))
    val y = if (anchor.y >= h + 20) anchor.y - h - 8 else anchor.bottom + 8
    return TableBounds((anchor.center.x - w / 2).coerceIn(12f, (width - w - 12).coerceAtLeast(12f)),
        y.coerceIn(12f, (height - h - 12).coerceAtLeast(12f)), w, h)
}

@Composable
internal fun InlinePopupPages(pages: List<String>, selected: Int, color: Color, select: (Int) -> Unit) {
    Row(Modifier.width(CardRingGeometry.gridSide.dp).height(16.dp).semantics {
        contentDescription = "Action page ${selected + 1} of ${pages.size}, ${pages[selected]}"
    }) {
        pages.forEachIndexed { index, name ->
            RingPress(MenuControl("$name page", selected = index == selected) { select(index) },
                Modifier.weight(1f).fillMaxHeight(), color,
                fill = if (index == selected) color.copy(alpha = .18f) else Color.White, role = Role.Tab) {
                Text(name, fontSize = 8.sp, lineHeight = 9.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    color = MenuInk, fontWeight = if (index == selected) FontWeight.Bold else FontWeight.Normal)
            }
        }
    }
}

@Composable
internal fun InlinePopupPanel(dismiss: () -> Unit, title: String, width: Dp = 360.dp, height: Dp = 288.dp,
    key: String = title, content: @Composable ColumnScope.() -> Unit) {
    // Opening a card menu from a row clears the toolkit anchor while this panel is still visible.
    // Capture the opening anchor and key by a stable identity (not a count-bearing title) so the
    // input guard does not restart and swallow the row's second tap.
    val current = LocalInlinePopupAnchor.current
    val anchor = remember(key) { current }
    val color = if (anchor.own) OwnTableColor else OpponentTableColor
    TableInlinePopup(key, anchor.id, anchor.bounds, anchor.own, color, dismiss, width, height, title = title) {
        Column(Modifier.padding(8.dp).verticalScroll(rememberScrollState())) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                InlinePopupCell(MenuControl("Close", click = LocalInlinePopupDismiss.current ?: dismiss), "Close", color)
            }
            content()
        }
    }
}

@Composable
private fun RingWidget(entry: RingEntry, owner: Color) {
    val compound = requireNotNull(entry.compound)
    var delta by remember(entry.id) { mutableStateOf<Long?>(null) }
    val color = entry.control.color ?: owner
    val fill = entry.fill ?: Color.White
    val shape = RoundedCornerShape(3.dp)
    Row(Modifier.size(96.dp, 48.dp).clip(shape).border(1.dp, color.copy(alpha = .5f), shape)) {
        RingPress(compound.minus, Modifier.width(24.dp).fillMaxHeight(), color, fill, bordered = false) {
            Text("−", color = MenuInk.copy(alpha = if (compound.minus.enabled) 1f else .35f), fontWeight = FontWeight.Bold)
        }
        val numeric = if (compound.corner) cornerDragValue(compound.dragValue, 0) != null
            else TableActions.numeric(compound.dragValue)
        val drag = if (compound.drag != null) Modifier.ringInputTarget(draggable = true)
            .valueDragGestures(entry.control.enabled && numeric, preview = { delta = it },
                side = ValueDragSide.TOP.takeIf { compound.corner }, commit = compound.drag) else Modifier
        RingPress(entry.control, Modifier.width(48.dp).fillMaxHeight().then(drag), color, fill, bordered = false) {
            Text(compound.value, Modifier.padding(horizontal = 2.dp).clearAndSetSemantics {}, fontSize = 12.sp,
                maxLines = 1, overflow = TextOverflow.Ellipsis, color = MenuInk.copy(alpha = if (entry.control.enabled) 1f else .35f), fontWeight = FontWeight.Bold)
            delta?.let { HoldTooltip(if (compound.corner) cornerDragLabel(compound.dragValue, it) else valueDragLabel(compound.dragValue, it)) }
        }
        RingPress(compound.plus, Modifier.width(24.dp).fillMaxHeight(), color, fill, bordered = false) {
            Text("+", color = MenuInk.copy(alpha = if (compound.plus.enabled) 1f else .35f), fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
internal fun InlinePopupCell(control: MenuControl, label: String = control.name, owner: Color = OwnTableColor,
    modifier: Modifier = Modifier.size(CardRingGeometry.cell.dp), fill: Color? = null,
    drag: ((Long) -> Unit)? = null, dragValue: String = "0") {
    val entry = RingEntry(control.name, label, control, fill = fill, drag = drag, dragValue = dragValue)
    var delta by remember(entry.id) { mutableStateOf<Long?>(null) }
    val dragModifier = if (entry.drag != null) Modifier.ringInputTarget(draggable = true)
        .valueDragGestures(entry.control.enabled && TableActions.numeric(entry.dragValue),
            preview = { delta = it }, commit = entry.drag) else Modifier
    RingPress(entry.control, modifier.then(dragModifier), entry.control.color ?: owner, fill = entry.fill ?: Color.White) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            MenuIcon(entry.control.name, tint = (entry.control.color ?: owner).copy(alpha = if (entry.control.enabled) 1f else .35f))
            Text(entry.label.replace('\n', ' '), Modifier.padding(horizontal = 2.dp).clearAndSetSemantics {},
                fontSize = 8.sp, lineHeight = 9.sp, textAlign = TextAlign.Center, maxLines = 1, overflow = TextOverflow.Ellipsis,
                letterSpacing = 0.sp, color = MenuInk.copy(alpha = if (entry.control.enabled) 1f else .38f), fontWeight = FontWeight.Medium)
        }
        delta?.let { HoldTooltip(valueDragLabel(entry.dragValue, it)) }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RingPress(control: MenuControl, modifier: Modifier, color: Color,
    fill: Color = Color.White, role: Role = Role.Button, bordered: Boolean = true, content: @Composable () -> Unit) {
    var tooltip by remember { mutableStateOf(false) }
    Surface(modifier.pressFeedback(control.enabled).ringInputTarget().semantics(mergeDescendants = true) { contentDescription = control.name; selected = control.selected }
        .combinedClickable(enabled = control.enabled, role = role, hapticFeedbackEnabled = false,
            onLongClick = { tooltip = true }, onClick = control.click),
        shape = if (bordered) RoundedCornerShape(3.dp) else androidx.compose.ui.graphics.RectangleShape,
        color = if (control.enabled) fill else fill.copy(alpha = .4f),
        border = if (bordered) BorderStroke(if (control.selected) 2.dp else 1.dp, color.copy(alpha = if (control.enabled) .5f else .2f)) else null) {
        Box(contentAlignment = Alignment.Center) {
            content()
            if (tooltip) HoldTooltip(control.name)
        }
    }
    if (tooltip) LaunchedEffect(control.name) { kotlinx.coroutines.delay(2000); tooltip = false }
}

package org.asyncttwiz.android

import android.os.SystemClock
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs
import org.asyncttwiz.protocol.TableActions

internal val LocalCardRingAnchors = compositionLocalOf<Map<String, TableBounds>> { emptyMap() }
private val LocalRingInputTargets = compositionLocalOf<(Any, TableBounds?, Boolean) -> Unit> { { _, _, _ -> } }

private fun Modifier.ringInputTarget(draggable: Boolean = false): Modifier = composed {
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
    val drag: ((Long) -> Unit)? = null, val dragValue: String = value)
internal data class RingEntry(val id: String, val label: String, val control: MenuControl,
    val compound: RingCompound? = null, val fill: Color? = null,
    val drag: ((Long) -> Unit)? = null, val dragValue: String = "0")

/** Wide pills become three independent cells without changing their gameplay callbacks. */
internal fun ringCells(entries: List<RingEntry>): List<RingEntry> = entries.flatMap { entry ->
    val compound = entry.compound
    if (compound == null) listOf(entry) else listOf(
        RingEntry("${entry.id}/minus", "− ${entry.label}", compound.minus, fill = entry.fill),
        entry.copy(label = "${entry.label} ${compound.value}", compound = null,
            drag = compound.drag, dragValue = compound.dragValue),
        RingEntry("${entry.id}/plus", "+ ${entry.label}", compound.plus, fill = entry.fill))
}

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
    val cells = ringCells(entries)
    val pages = CardRingPages.resolve(cells.map { it.id })
    val selectedPage = page.coerceIn(pages.indices)
    val shown = pages[selectedPage].actions.mapNotNull { id -> cells.firstOrNull { it.id == id } }
    val anchor = LocalCardRingAnchors.current[viewId]
    val guard = remember(identity, viewId) { CardRingInputGuard(SystemClock.uptimeMillis()) }
    val selectPage: (Int) -> Unit = { page = it; store.write(it) }
    val switchPage: (Int) -> Unit = { delta -> selectPage((selectedPage + delta).mod(pages.size)) }
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
    DisposableEffect(relay, identity, viewId) {
        val input = TableRingInput(viewId, { screen ->
            inputTargets.values.none { it.contains(TablePoint(screen.x, screen.y)) }
        }, { currentDismiss() })
        relay?.ring = input
        onDispose { if (relay?.ring === input) relay.ring = null }
    }
    BackHandler(onBack = dismiss)
    CompositionLocalProvider(LocalRingInputTargets provides registerTarget) {
    BoxWithConstraints(Modifier.fillMaxSize().semantics { contentDescription = "Card action ring"; paneTitle = "Card actions" }) {
        val card = anchor ?: TableBounds(maxWidth.value / 2 - 20, maxHeight.value / 2 - 28, 40f, 56f)
        // Capture the opening anchor/viewport only: neither sync nor a page switch moves this square.
        val placement = remember(identity, viewId) { CardRingGeometry.place(maxWidth.value, maxHeight.value, card, own) }
        if (placement == null) {
            LaunchedEffect(identity, viewId) { dismiss() }
            return@BoxWithConstraints
        }
        val square = placement.square
        // Standalone fallback; the stable table host relays outside presses before they reach here.
        Box(Modifier.fillMaxSize().pointerInput(dismiss) { detectTapGestures(onTap = { dismiss() }) })
        Surface(Modifier.offset(square.x.dp, square.y.dp).size(square.width.dp, square.height.dp)
            .onGloballyPositioned { origin = it.localToScreen(Offset.Zero) }
            .ringInputTarget().semantics { contentDescription = "Card action grid" }
            .pointerInput(guard) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                // The relay alone can replace the first tap with the table's double-tap action.
                if (!down.isConsumed) {
                    val screen = origin + down.position
                    val valueDrag = dragTargets.values.any { it.contains(TablePoint(screen.x, screen.y)) }
                    // The opening gesture's tail must not adjust a counter either, so value drags respect the guard.
                    val blocked = !guard.accepts(down.uptimeMillis)
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
            Column(Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) {
                Row(Modifier.width(CardRingGeometry.gridSide.dp).height(16.dp).semantics {
                    contentDescription = "Action page ${selectedPage + 1} of ${pages.size}, ${pages[selectedPage].name}"
                }) {
                    pages.forEachIndexed { index, tab ->
                        RingPress(MenuControl("${tab.name} page", selected = index == selectedPage) { selectPage(index) },
                            Modifier.weight(1f).fillMaxHeight(), ownerColor, fill = if (index == selectedPage) ownerColor.copy(alpha = .18f) else Color.White,
                            role = Role.Tab) {
                            Text(tab.name, fontSize = 8.sp, lineHeight = 9.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                color = MenuInk, fontWeight = if (index == selectedPage) FontWeight.Bold else FontWeight.Normal)
                        }
                    }
                }
                key(identity, viewId, selectedPage) {
                    Column(Modifier.size(CardRingGeometry.gridSide.dp)) {
                        repeat(4) { row ->
                            Row {
                                repeat(4) { column ->
                                    val entry = shown.getOrNull(row * 4 + column)
                                    if (entry == null) Spacer(Modifier.size(CardRingGeometry.cell.dp))
                                    else RingCell(entry, ownerColor, Modifier.size(CardRingGeometry.cell.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    }
}

@Composable
private fun RingCell(entry: RingEntry, owner: Color, modifier: Modifier) {
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
    fill: Color = Color.White, role: Role = Role.Button, content: @Composable () -> Unit) {
    var tooltip by remember { mutableStateOf(false) }
    Surface(modifier.pressFeedback(control.enabled).ringInputTarget().semantics(mergeDescendants = true) { contentDescription = control.name; selected = control.selected }
        .combinedClickable(enabled = control.enabled, role = role, hapticFeedbackEnabled = false,
            onLongClick = { tooltip = true }, onClick = control.click),
        shape = RoundedCornerShape(3.dp), color = if (control.enabled) fill else fill.copy(alpha = .4f),
        border = BorderStroke(if (control.selected) 2.dp else 1.dp, color.copy(alpha = if (control.enabled) .5f else .2f))) {
        Box(contentAlignment = Alignment.Center) {
            content()
            if (tooltip) HoldTooltip(control.name)
        }
    }
    if (tooltip) LaunchedEffect(control.name) { kotlinx.coroutines.delay(2000); tooltip = false }
}

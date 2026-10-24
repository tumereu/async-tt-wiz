package org.asyncttwiz.android

import android.os.SystemClock
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import kotlin.math.abs

/** Change just this flag for icon-only bubbles. Accessible names and hold tooltips remain. */
internal const val CardRingLabels = true
internal val LocalCardRingAnchors = compositionLocalOf<Map<String, TableBounds>> { emptyMap() }
private val LocalRingInputTargets = compositionLocalOf<(Any, TableBounds?) -> Unit> { { _, _ -> } }

private fun Modifier.ringInputTarget(): Modifier = composed {
    val register = LocalRingInputTargets.current
    val key = remember { Any() }
    DisposableEffect(register, key) { onDispose { register(key, null) } }
    onGloballyPositioned {
        val windowOrigin = it.localToScreen(Offset.Zero) - it.localToWindow(Offset.Zero)
        val bounds = it.boundsInWindow()
        register(key, TableBounds(windowOrigin.x + bounds.left, windowOrigin.y + bounds.top, bounds.width, bounds.height))
    }
}
internal data class RingCompound(val value: String, val minus: MenuControl, val plus: MenuControl)
internal data class RingEntry(val id: String, val label: String, val control: MenuControl,
    val compound: RingCompound? = null, val fill: Color? = null)

internal fun ringShortLabel(name: String): String = when (name) {
    "Declare + tap" -> "Declare\n+ tap"
    "Declare without tapping" -> "Declare"
    "Ping / Unping" -> "Ping"
    "Add Stack effect" -> "Effect"
    "Select / deselect" -> "Select"
    "Remove Stack effect" -> "Remove\neffect"
    "Remove from combat" -> "Remove\ncombat"
    "Source actions" -> "Source"
    else -> name.removePrefix("Move to ").removePrefix("Move ").replace(" · ", "\n")
}

@Composable
internal fun CardActionRing(game: String, viewer: String, identity: String, viewId: String,
    entries: List<RingEntry>, own: Boolean, ownerColor: Color, dismiss: () -> Unit) {
    val context = LocalContext.current
    val store = remember(context, viewer, game) { CardRingPageStore(context, viewer, game) }
    var page by remember(viewer, game) { mutableIntStateOf(store.read()) }
    val pages = CardRingPages.resolve(entries.map { it.id })
    val shown = pages[page].actions.mapNotNull { id -> entries.firstOrNull { it.id == id } }
    val anchor = LocalCardRingAnchors.current[viewId]
    val guard = remember(identity, viewId) { CardRingInputGuard(SystemClock.uptimeMillis()) }
    var targets by remember { mutableStateOf(emptyList<TableBounds>()) }
    var gridMode by remember { mutableStateOf(false) }
    val switchPage: (Int) -> Unit = { delta -> page = (page + delta).mod(3); store.write(page) }
    val currentSwitch by rememberUpdatedState(switchPage)
    val relay = LocalTableTapRelay.current
    val inputTargets = remember { mutableMapOf<Any, TableBounds>() }
    val registerTarget = remember { { key: Any, bounds: TableBounds? ->
        if (bounds == null) inputTargets.remove(key) else inputTargets.put(key, bounds)
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
    BoxWithConstraints(Modifier.fillMaxSize().semantics { contentDescription = "Card action ring"; paneTitle = "Card actions" }
        .pointerInput(guard) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                // The relay alone can replace the first tap with the table's double-tap action.
                if (!down.isConsumed) {
                    val point = TablePoint(down.position.x / density, down.position.y / density)
                    val blocked = targets.any { it.contains(point) } && !guard.accepts(down.uptimeMillis)
                    var swipe = false
                    var delta = androidx.compose.ui.geometry.Offset.Zero
                    if (blocked) down.consume()
                    do {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        val change = event.changes.firstOrNull { it.id == down.id }
                        if (change != null) {
                            delta = change.position - down.position
                            // Vertical drags in a fallback grid scroll its rows, not its pages.
                            if (delta.getDistance() > 32.dp.toPx() && (!gridMode || abs(delta.x) > abs(delta.y) * 1.5f)) swipe = true
                        }
                        if (blocked || swipe) event.changes.forEach { it.consume() }
                    } while (event.changes.any { it.pressed })
                    if (swipe && !blocked) currentSwitch(if (if (abs(delta.x) >= abs(delta.y)) delta.x < 0 else delta.y < 0) 1 else -1)
                }
            }
        }) {
        // Standalone fallback; the stable table host relays outside presses before they reach here.
        Box(Modifier.fillMaxSize().pointerInput(dismiss) { detectTapGestures(onTap = { dismiss() }) })
        val card = anchor ?: TableBounds(maxWidth.value / 2 - 20, maxHeight.value / 2 - 28, 40f, 56f)
        val font = LocalDensity.current.fontScale
        val side = ringBubbleSide(font)
        val pill = 144f + (side - 56f)
        val sizes = shown.map { RingSize(if (it.compound != null) pill else side, side) } + RingSize(88f, 48f)
        val placement = remember(maxWidth, maxHeight, card, sizes, own) { CardRingGeometry.place(maxWidth.value, maxHeight.value, card, sizes, own) }
        SideEffect { targets = placement.grid?.let(::listOf) ?: placement.bubbles; gridMode = placement.grid != null }
        val pageControl = MenuControl("Action page ${page + 1} of 3, ${pages[page].name}; next page") { switchPage(1) }
        val indicator: @Composable (Modifier) -> Unit = { modifier ->
            RingPress(pageControl, modifier, ownerColor, RoundedCornerShape(24.dp)) {
                Row(Modifier.padding(horizontal = 8.dp), horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
                    for (i in 0..2) {
                        if (i > 0) Text("·", color = MenuBorder, fontSize = 10.sp)
                        Text("${i + 1}", color = if (i == page) ownerColor else MenuInk.copy(alpha = .5f),
                            fontSize = 12.sp, fontWeight = if (i == page) FontWeight.Black else FontWeight.Normal)
                    }
                }
            }
        }
        key(identity, viewId, page) {
            if (placement.grid == null) {
                for ((index, entry) in shown.withIndex()) {
                    val b = placement.bubbles[index]
                    SpeechBubbleTail(card, b, (entry.control.color ?: ownerColor).copy(alpha = .65f))
                    RingBubble(entry, ownerColor, Modifier.offset(b.x.dp, b.y.dp).size(b.width.dp, b.height.dp).ringEntrance(index))
                }
                val b = placement.bubbles.last()
                indicator(Modifier.offset(b.x.dp, b.y.dp).size(b.width.dp, b.height.dp).ringEntrance(shown.size))
            } else {
                val region = placement.grid
                val width = minOf(region.width, 408f)
                val rows = ringGridRows(sizes.dropLast(1), width)
                val visibleRows = ((region.height - 48f) / (side + 8f)).toInt().coerceIn(1, minOf(4, rows.size.coerceAtLeast(1)))
                val viewportHeight = visibleRows * (side + 8f) - 8f
                val height = viewportHeight + 56f
                val x = (card.center.x - width / 2).coerceIn(region.x, (region.right - width).coerceAtLeast(region.x))
                val y = (if (region.bottom <= card.y) region.bottom - height else if (region.y >= card.bottom) region.y else card.center.y - height / 2)
                    .coerceIn(region.y, (region.bottom - height).coerceAtLeast(region.y))
                Column(Modifier.offset(x.dp, y.dp).size(width.dp, height.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Column(Modifier.height(viewportHeight.dp).ringInputTarget().verticalScroll(rememberScrollState()).semantics { contentDescription = "Card action grid" },
                        verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        var index = 0
                        for (row in rows) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            for (size in row) {
                                val at = index++
                                RingBubble(shown[at], ownerColor, Modifier.size(size.width.dp, size.height.dp).ringEntrance(at))
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        indicator(Modifier.size(88.dp, 48.dp))
                        if (rows.size > visibleRows && width >= 184f) Text("Scroll for more", color = ownerColor, fontSize = 9.sp, letterSpacing = 0.sp)
                    }
                }
            }
        }
    }
    }
}

internal fun ringGridRows(sizes: List<RingSize>, width: Float): List<List<RingSize>> {
    val rows = mutableListOf<MutableList<RingSize>>()
    var used = 0f
    for (size in sizes) {
        if (rows.isEmpty() || used + 8 + size.width > width) { rows += mutableListOf<RingSize>(); used = 0f }
        rows.last() += size
        used += size.width + if (used > 0) 8 else 0
    }
    return rows
}

@Composable
private fun Modifier.ringEntrance(index: Int): Modifier {
    var entered by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { entered = true }
    val progress by animateFloatAsState(if (entered) 1f else 0f, tween(150, delayMillis = minOf(index * 8, 48)), label = "Bubble entrance")
    return graphicsLayer { alpha = progress; scaleX = .65f + .35f * progress; scaleY = scaleX }
}

@Composable
private fun RingBubble(entry: RingEntry, owner: Color, modifier: Modifier) {
    val compound = entry.compound
    if (compound == null) RingPress(entry.control, modifier, entry.control.color ?: owner, CircleShape) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            if (entry.id.startsWith("corner/")) Text(entry.label, fontSize = 15.sp, fontWeight = FontWeight.Bold,
                color = MenuInk.copy(alpha = if (entry.control.enabled) 1f else .35f))
            else MenuIcon(entry.control.name, tint = (entry.control.color ?: owner).copy(alpha = if (entry.control.enabled) 1f else .35f))
            if (CardRingLabels && !entry.id.startsWith("corner/")) Text(entry.label, Modifier.padding(horizontal = 4.dp).clearAndSetSemantics {},
                fontSize = 8.sp, lineHeight = 9.sp, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis,
                letterSpacing = 0.sp, color = MenuInk.copy(alpha = if (entry.control.enabled) 1f else .38f), fontWeight = FontWeight.Medium)
        }
    } else Surface(modifier, shape = RoundedCornerShape(40.dp), color = entry.fill ?: Color.White,
        border = BorderStroke(1.dp, entry.control.color ?: owner.copy(alpha = .5f)), shadowElevation = 3.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            RingPress(compound.minus, Modifier.width(48.dp).fillMaxHeight(), owner, CircleShape, paint = false) { Text("−", fontSize = 22.sp, color = MenuInk.copy(alpha = if (compound.minus.enabled) 1f else .3f)) }
            RingPress(entry.control, Modifier.weight(1f).fillMaxHeight(), owner, CircleShape, paint = false) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(compound.value, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MenuInk)
                    if (CardRingLabels) Text(entry.label, fontSize = 8.sp, lineHeight = 9.sp, maxLines = 1, color = MenuInk)
                }
            }
            RingPress(compound.plus, Modifier.width(48.dp).fillMaxHeight(), owner, CircleShape, paint = false) { Text("+", fontSize = 22.sp, color = MenuInk.copy(alpha = if (compound.plus.enabled) 1f else .3f)) }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RingPress(control: MenuControl, modifier: Modifier, color: Color, shape: androidx.compose.ui.graphics.Shape,
    paint: Boolean = true, content: @Composable () -> Unit) {
    var tooltip by remember { mutableStateOf(false) }
    Surface(modifier.ringInputTarget().semantics(mergeDescendants = true) { contentDescription = control.name; selected = control.selected }
        .combinedClickable(enabled = control.enabled, role = Role.Button, hapticFeedbackEnabled = false,
            onLongClick = { tooltip = true }, onClick = control.click),
        shape = shape, color = if (paint) Color.White else Color.Transparent,
        border = if (paint) BorderStroke(if (control.selected) 2.dp else 1.dp, color.copy(alpha = .65f)) else null,
        shadowElevation = if (paint) 3.dp else 0.dp) {
        Box(contentAlignment = Alignment.Center) { content() }
    }
    if (tooltip) Popup(alignment = Alignment.TopCenter, onDismissRequest = { tooltip = false }) {
        Surface(Modifier.widthIn(max = 280.dp).combinedClickable(onClick = { tooltip = false }), color = MenuInk,
            contentColor = Color.White, shape = RoundedCornerShape(8.dp), shadowElevation = 4.dp) {
            Text(control.name, Modifier.padding(10.dp), style = MaterialTheme.typography.labelMedium)
        }
        LaunchedEffect(control.name) { kotlinx.coroutines.delay(2000); tooltip = false }
    }
}

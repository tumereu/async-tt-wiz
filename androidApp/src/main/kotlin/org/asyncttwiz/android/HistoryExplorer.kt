package org.asyncttwiz.android

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculateCentroidSize
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlin.math.roundToInt
import kotlin.math.abs

/** Full-screen, two-axis grouped history picker. Tap reviews the group endpoint and closes. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HistoryExplorer(controller: HistoryExplorerController,
    authorColors: Map<String, Color>, showHidden: Boolean, onShowHidden: (Boolean) -> Unit,
    onDismiss: () -> Unit, onReview: (org.asyncttwiz.protocol.PlayState) -> Unit,
    texts: Map<String, HistoryText> = emptyMap()) {
    val state = controller.state
    val graph = state.graph
    var menu by remember { mutableStateOf(false) }
    var viewport by remember(controller) { mutableStateOf(HistoryTreeViewport()) }
    var picked by remember { mutableStateOf<String?>(null) }
    val density = LocalDensity.current.density
    val activeColor = Color(0xff268849)
    val edgeColor = Color(0xff98abaa)
    val selectionColor = Color(0xff3457b3)
    var framed by remember(controller) { mutableStateOf(false) }
    LaunchedEffect(state.reviewed, state.busy, picked) {
        if (!state.busy) state.reviewed?.let {
            onReview(it)
            if (picked == it.currentNodeId) { picked = null; onDismiss() }
        }
    }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = Color.White) {
            Column(Modifier.fillMaxSize().padding(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box {
                        TextButton(onClick = { menu = true }) { Text("Game tree ▾", color = MenuInk) }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(text = { Text(if (showHidden) "Hide simple alternatives" else "Show hidden branches") },
                                onClick = { onShowHidden(!showHidden); menu = false })
                        }
                    }
                    Spacer(Modifier.weight(1f))
                    IconButton(onDismiss) { Icon(menuIcon("Close"), "Close game tree", tint = MenuInk) }
                }
                BoxWithConstraints(Modifier.fillMaxWidth().weight(1f).semantics {
                    contentDescription = "Game history group tree"
                    stateDescription = "Zoom ${viewport.zoom}"
                }.background(Color(0xfff4f8f6)).clipToBounds().pointerInput(IdentityKey(graph), density) {
                        // Observe before chips consume their downs. Only claim movement beyond
                        // slop, preserving chip taps/holds but allowing pinch/pan over chips too.
                        awaitEachGesture {
                            awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                            var pastSlop = false
                            var totalZoom = 1f
                            var totalPan = Offset.Zero
                            do {
                                val event = awaitPointerEvent(PointerEventPass.Initial)
                                val pan = event.calculatePan()
                                val zoom = event.calculateZoom()
                                totalZoom *= zoom; totalPan += pan
                                if (!pastSlop) pastSlop = totalPan.getDistance() > viewConfiguration.touchSlop ||
                                    abs(1f - totalZoom) * event.calculateCentroidSize(useCurrent = false) > viewConfiguration.touchSlop
                                if (pastSlop) {
                                    val centroid = event.calculateCentroid(useCurrent = false)
                                    graph?.let { viewport = viewport.transform(HistoryPoint(centroid.x / density, centroid.y / density),
                                        HistoryPoint(pan.x / density, pan.y / density), zoom, it, size.width / density, size.height / density) }
                                    event.changes.filter { it.positionChanged() }.forEach { it.consume() }
                                }
                            } while (event.changes.any { it.pressed })
                        }
                    }) {
                    LaunchedEffect(graph, state.busy, maxWidth, maxHeight) {
                        if (!framed && graph != null && !state.busy) {
                            val point = graph.centeredOn(requireNotNull(state.selectedNodeId), maxWidth.value, maxHeight.value)
                            viewport = HistoryTreeViewport(point)
                            framed = true
                        }
                    }
                    if (graph != null) {
                        val visible = viewport.visible(graph, maxWidth.value, maxHeight.value)
                        Canvas(Modifier.fillMaxSize()) {
                            fun HistoryPoint.screen() = viewport.screen(this).let { Offset(it.x * density, it.y * density) }
                            val ranges = graph.rangesById
                            for (range in visible) {
                                ranges[range.parentRangeId]?.position?.let { parent ->
                                    val point = range.position
                                    val from = parent.screen(); val to = point.screen()
                                    val active = range.id in graph.activeRanges
                                    // A branch changes lanes only at its fork; subsequent groups remain parallel.
                                    val corner = Offset(from.x, to.y)
                                    drawLine(if (active) activeColor else edgeColor, from, corner, (if (active) 2f else 1f) * density)
                                    drawLine(if (active) activeColor else edgeColor, corner, to, (if (active) 2f else 1f) * density)
                                }
                            }
                        }
                        val events = graph.eventsById
                        for (range in visible) {
                            val event = events.getValue(range.targetId)
                            val point = range.position
                            val screen = viewport.screen(point)
                            val x = screen.x; val y = screen.y
                            val zoom = viewport.zoom
                            if (x >= -24f * zoom && x <= maxWidth.value + 24 * zoom && y >= -16 * zoom && y <= maxHeight.value + 32 * zoom) {
                                val text = texts[event.id]
                                HistoryEventChip(event.copy(description = text?.description ?: event.description),
                                    authorColors[event.author] ?: Color(0xff59656a), graph.rangeOf[state.selectedNodeId] == range.id,
                                    Modifier.offset { IntOffset(((x - 10) * density).roundToInt(), ((y - 10) * density).roundToInt()) }
                                        .graphicsLayer { scaleX = zoom; scaleY = zoom }
                                        .background(Color(0xfff4f8f6)).combinedClickable(
                                            onClick = { picked = event.id; controller.select(event.id) },
                                            onLongClick = { picked = null; controller.select(event.id) }))
                                range.turnStart?.let { turn ->
                                    Text(turn.label, color = authorColors[turn.player] ?: Color(0xff59656a),
                                        fontSize = 9.sp, lineHeight = 12.sp,
                                        modifier = Modifier.offset { IntOffset(((x - 10) * density).roundToInt(), ((y - 28 * zoom) * density).roundToInt()) }
                                            .graphicsLayer { scaleX = zoom; scaleY = zoom }
                                            .background(Color.White, MaterialTheme.shapes.extraSmall).padding(horizontal = 2.dp)
                                            .semantics { contentDescription = "Turn ${turn.number}, player ${turn.player}" })
                                }
                            }
                        }
                        Canvas(Modifier.fillMaxSize()) {
                            fun HistoryPoint.screen() = viewport.screen(this).let { Offset(it.x * density, it.y * density) }
                            for (range in visible) if (range.id in graph.headRanges) graph.positions[range.targetId]?.let {
                                drawCircle(activeColor, 2.5f * density * viewport.zoom, it.screen() + Offset(0f, 14 * density * viewport.zoom))
                            }
                            graph.positions[graph.currentNodeId]?.let {
                                drawCircle(activeColor, 12 * density * viewport.zoom, it.screen(), style = Stroke(1.5f * density))
                            }
                            graph.positions[state.selectedNodeId]?.let {
                                drawRect(selectionColor, it.screen() - Offset(11 * density * viewport.zoom, 12 * density * viewport.zoom),
                                    Size(22 * density * viewport.zoom, 24 * density * viewport.zoom), style = Stroke(1.5f * density))
                            }
                        }
                    }
                    if (state.busy) CircularProgressIndicator(Modifier.align(Alignment.Center).size(24.dp))
                    state.error?.let { Text(it, Modifier.align(Alignment.Center)) }
                }
                state.selectedNodeId?.let { id -> Text(texts[id]?.description ?: graph?.eventsById?.get(id)?.description.orEmpty(),
                    style = MaterialTheme.typography.labelSmall, maxLines = 2) }
            }
        }
    }
}

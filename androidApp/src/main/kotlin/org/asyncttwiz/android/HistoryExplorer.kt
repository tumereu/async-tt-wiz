package org.asyncttwiz.android

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlin.math.roundToInt

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
    var pan by remember { mutableStateOf(Offset.Zero) }
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
                BoxWithConstraints(Modifier.fillMaxWidth().weight(1f).semantics { contentDescription = "Game history group tree" }
                    .background(Color(0xfff4f8f6)).clipToBounds().pointerInput(graph?.width, graph?.height) {
                        detectDragGestures { change, drag ->
                            change.consume()
                            graph?.pan(HistoryPoint(pan.x, pan.y), drag.x / density, drag.y / density,
                                size.width / density, size.height / density)?.let { pan = Offset(it.x, it.y) }
                        }
                    }) {
                    LaunchedEffect(graph, state.busy, maxWidth, maxHeight) {
                        if (!framed && graph != null && !state.busy) {
                            val point = graph.centeredOn(requireNotNull(state.selectedNodeId), maxWidth.value, maxHeight.value)
                            pan = Offset(point.x, point.y)
                            framed = true
                        }
                    }
                    if (graph != null) {
                        Canvas(Modifier.fillMaxSize()) {
                            fun HistoryPoint.screen() = Offset((x + pan.x) * density, (y + pan.y) * density)
                            val ranges = graph.ranges.associateBy { it.id }
                            for (range in graph.ranges) {
                                ranges[range.parentRangeId]?.position?.let { parent ->
                                    val point = range.position
                                    val from = parent.screen(); val to = point.screen()
                                    val active = range.nodeIds.any { it in graph.activeNodeIds }
                                    // A branch changes lanes only at its fork; subsequent groups remain parallel.
                                    val corner = Offset(from.x, to.y)
                                    drawLine(if (active) activeColor else edgeColor, from, corner, (if (active) 2f else 1f) * density)
                                    drawLine(if (active) activeColor else edgeColor, corner, to, (if (active) 2f else 1f) * density)
                                }
                            }
                        }
                        val events = graph.events.associateBy { it.id }
                        for (range in graph.ranges) {
                            val event = events.getValue(range.targetId)
                            val point = range.position
                            val x = point.x + pan.x; val y = point.y + pan.y
                            if (x >= -12f && x <= maxWidth.value + 12 && y >= -16 && y <= maxHeight.value + 16) {
                                val text = texts[event.id]
                                HistoryEventChip(event.copy(description = text?.description ?: event.description),
                                    authorColors[event.author] ?: Color(0xff59656a), state.selectedNodeId in range.nodeIds,
                                    Modifier.offset { IntOffset(((x - 10) * density).roundToInt(), ((y - 10) * density).roundToInt()) }
                                        .background(Color(0xfff4f8f6)).combinedClickable(
                                            onClick = { picked = event.id; controller.select(event.id) },
                                            onLongClick = { picked = null; controller.select(event.id) }))
                            }
                        }
                        Canvas(Modifier.fillMaxSize()) {
                            fun HistoryPoint.screen() = Offset((x + pan.x) * density, (y + pan.y) * density)
                            for (id in graph.branchHeadIds) graph.positions[id]?.let {
                                drawCircle(activeColor, 2.5f * density, it.screen() + Offset(0f, 14 * density))
                            }
                            graph.positions[graph.currentNodeId]?.let {
                                drawCircle(activeColor, 12 * density, it.screen(), style = Stroke(1.5f * density))
                            }
                            graph.positions[state.selectedNodeId]?.let {
                                drawRect(selectionColor, it.screen() - Offset(11 * density, 12 * density),
                                    Size(22 * density, 24 * density), style = Stroke(1.5f * density))
                            }
                        }
                    }
                    if (state.busy) CircularProgressIndicator(Modifier.align(Alignment.Center).size(24.dp))
                    state.error?.let { Text(it, Modifier.align(Alignment.Center)) }
                }
                state.selectedNodeId?.let { id -> Text(texts[id]?.description ?: graph?.events?.find { it.id == id }?.description.orEmpty(),
                    style = MaterialTheme.typography.labelSmall, maxLines = 2) }
            }
        }
    }
}

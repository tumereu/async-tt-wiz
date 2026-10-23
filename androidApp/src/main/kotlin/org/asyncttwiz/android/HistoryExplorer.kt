package org.asyncttwiz.android

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import kotlin.math.roundToInt

/** Production compact, two-axis pannable group explorer. No sample data, implicit activation, archive
 * toolbar or local authoritative tree. Normal table wiring/complete I22/I25 projections remain I24b.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HistoryExplorer(controller: HistoryExplorerController, connected: Boolean,
    authorColors: Map<String, Color>, showHidden: Boolean, onShowHidden: (Boolean) -> Unit,
    onDismiss: () -> Unit, onReview: (org.asyncttwiz.protocol.PlayState) -> Unit,
    onAdvanced: (() -> Unit)? = null, texts: Map<String, HistoryText> = emptyMap()) {
    val state = controller.state
    val graph = state.graph
    var menu by remember { mutableStateOf(false) }
    var tooltip by remember { mutableStateOf<String?>(null) }
    var pan by remember { mutableStateOf(Offset.Zero) }
    val density = LocalDensity.current.density
    val activeColor = Color(0xff268849)
    val edgeColor = MaterialTheme.colorScheme.outline
    val selectionColor = MaterialTheme.colorScheme.onSurface
    LaunchedEffect(state.reviewed) { state.reviewed?.let(onReview) }
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = MaterialTheme.shapes.medium, tonalElevation = 6.dp) {
            Column(Modifier.widthIn(max = 420.dp).padding(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box {
                        TextButton(onClick = { menu = true }) { Text("History ▾") }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(text = { Text("Frame current") }, onClick = {
                                graph?.positions?.get(graph.currentNodeId)?.let { pan = Offset(130 - it.x, 85 - it.y) }
                                menu = false
                            })
                            DropdownMenuItem(text = { Text(if (showHidden) "Hide simple alternatives" else "Show hidden branches") },
                                onClick = { onShowHidden(!showHidden); menu = false })
                            onAdvanced?.let { action -> DropdownMenuItem(text = { Text("Branch details…") },
                                onClick = { menu = false; action() }) }
                        }
                    }
                    Spacer(Modifier.weight(1f))
                    val actionable = connected && !state.busy && state.error == null && state.selectedNodeId != null && !state.archivedSelection
                    val enabled by rememberUpdatedState(actionable)
                    var activation by remember { mutableStateOf<HistoryActivation?>(null) }
                    Surface(Modifier.semantics {
                        role = Role.Button
                        if (!actionable) disabled()
                        onClick { if (actionable) controller.makeCurrent() else false }
                    }.pointerInput(controller) {
                        detectTapGestures(onPress = { activation = if (enabled) controller.captureMakeCurrent() else null; tryAwaitRelease() },
                            onTap = { if (enabled) controller.makeCurrent(activation) })
                    }) { Text("Make current", Modifier.padding(12.dp)) }
                }
                if (state.archivedSelection && onAdvanced != null) TextButton(onClick = onAdvanced) { Text("Restore via branch details") }
                Box(Modifier.fillMaxWidth().height(220.dp).clipToBounds().pointerInput(graph?.width, graph?.height) {
                    detectDragGestures { change, drag ->
                        change.consume(); tooltip = null
                        val candidate = pan + drag / density
                        graph?.pan(HistoryPoint(pan.x, pan.y), candidate.x - pan.x, candidate.y - pan.y,
                            size.width / density, size.height / density)?.let { pan = Offset(it.x, it.y) }
                    }
                }) {
                    if (graph != null) {
                        Canvas(Modifier.fillMaxSize()) {
                            fun HistoryPoint.screen() = Offset((x + pan.x) * density, (y + pan.y) * density)
                            val ranges = graph.ranges.associateBy { it.id }
                            for (range in graph.ranges) {
                                val parent = ranges[range.parentRangeId] ?: continue
                                val from = parent.position.screen(); val to = range.position.screen()
                                val elbow = (from.x + to.x) / 2
                                fun edge(end: Offset, color: Color, width: Float) {
                                    val path = Path().apply {
                                        moveTo(from.x, from.y); lineTo(elbow, from.y)
                                        lineTo(elbow, to.y); lineTo(end.x, end.y)
                                    }
                                    drawPath(path, color, style = Stroke(width * density))
                                }
                                edge(to, edgeColor, 1f)
                                val activeEnd = range.nodeIds.lastOrNull { it in graph.activeNodeIds }
                                if (activeEnd != null) edge(graph.positions.getValue(activeEnd).screen(), activeColor, 2f)
                            }
                            graph.positions[graph.currentNodeId]?.let { drawCircle(activeColor, 12 * density,
                                it.screen(), style = Stroke(2 * density)) }
                            graph.positions[state.selectedNodeId]?.let {
                                val point = it.screen()
                                drawRect(selectionColor, point - Offset(15 * density, 15 * density),
                                    androidx.compose.ui.geometry.Size(30 * density, 30 * density), style = Stroke(density))
                            }
                        }
                        for (range in graph.ranges) {
                            val color = authorColors[range.author] ?: MaterialTheme.colorScheme.onSurface
                            val text = historyRangeText(range, texts)
                            Box(Modifier.offset {
                                IntOffset(((range.position.x + pan.x - 24) * density).roundToInt(),
                                    ((range.position.y + pan.y - 24) * density).roundToInt())
                            }.size(48.dp).semantics {
                                contentDescription = "${text.description}, ${range.nodeIds.size} events, review group end"
                            }.combinedClickable(onClick = { tooltip = null; controller.select(range.targetId) },
                                onLongClick = { tooltip = text.description }), contentAlignment = Alignment.Center) {
                                Text(range.glyph.symbol, color = color)
                            }
                        }
                        tooltip?.let { Text(it, Modifier.align(Alignment.BottomCenter).border(1.dp, edgeColor).padding(4.dp)) }
                    }
                    if (state.busy) CircularProgressIndicator(Modifier.align(Alignment.Center).size(24.dp))
                    state.error?.let { Text(it, Modifier.align(Alignment.Center)) }
                }
            }
        }
    }
}

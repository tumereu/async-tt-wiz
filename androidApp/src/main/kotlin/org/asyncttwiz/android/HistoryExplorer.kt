package org.asyncttwiz.android

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
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
    val edgeColor = Color(0xff98abaa)
    val selectionColor = Color(0xff3457b3)
    var framed by remember(controller) { mutableStateOf(false) }
    LaunchedEffect(graph) {
        if (!framed && graph != null) {
            val point = graph.positions[state.selectedNodeId] ?: graph.positions.getValue(graph.currentNodeId)
            pan = Offset(150f - point.x, 80f - point.y)
            framed = true
        }
    }
    LaunchedEffect(state.reviewed) { state.reviewed?.let(onReview) }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        val window = (LocalView.current.parent as? DialogWindowProvider)?.window
        SideEffect { window?.setDimAmount(0f) }
        BoxWithConstraints(Modifier.fillMaxSize().pointerInput(onDismiss) { detectTapGestures(onTap = { onDismiss() }) }
            .padding(top = 52.dp, end = 8.dp, start = 8.dp)) {
        Surface(modifier = Modifier.align(Alignment.TopEnd).width(if (maxWidth > 600.dp) 360.dp else 320.dp)
                .pointerInput(Unit) { detectTapGestures(onTap = {}) },
            shape = MaterialTheme.shapes.medium, color = Color.White, shadowElevation = 8.dp) {
            Column(Modifier.padding(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box {
                        TextButton(onClick = { menu = true }) { Text("Game tree ▾", color = MenuInk) }
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
                    IconButton(onDismiss) { Icon(menuIcon("Close"), "Close game tree", tint = MenuInk) }
                }
                if (state.archivedSelection && onAdvanced != null) TextButton(onClick = onAdvanced) { Text("Restore via branch details") }
                Box(Modifier.fillMaxWidth().height(160.dp).semantics { contentDescription = "Game history group tree" }
                    .background(Color(0xfff4f8f6)).border(1.dp, Color(0xffdbe4df))
                    .clipToBounds().pointerInput(graph?.width, graph?.height) {
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
                                fun edge(end: Offset, color: Color, width: Float) {
                                    val path = Path().apply {
                                        moveTo(from.x, from.y); lineTo(end.x, end.y)
                                    }
                                    drawPath(path, color, style = Stroke(width * density))
                                }
                                edge(to, edgeColor, 1f)
                                val activeEnd = range.nodeIds.lastOrNull { it in graph.activeNodeIds }
                                if (activeEnd != null) edge(graph.positions.getValue(activeEnd).screen(), activeColor, 2f)
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
                                Box(Modifier.size(32.dp).background(Color.White, CircleShape).border(1.dp, Color(0xffb9ccca), CircleShape),
                                    contentAlignment = Alignment.Center) {
                                    CompositionLocalProvider(LocalContentColor provides color) { HistoryEventIcon(range.glyph.icon) }
                                }
                            }
                        }
                        Canvas(Modifier.fillMaxSize()) {
                            fun HistoryPoint.screen() = Offset((x + pan.x) * density, (y + pan.y) * density)
                            graph.positions[graph.currentNodeId]?.let { drawCircle(activeColor, 16 * density,
                                it.screen(), style = Stroke(2 * density)) }
                            graph.positions[state.selectedNodeId]?.let {
                                drawRect(selectionColor, it.screen() - Offset(22 * density, 22 * density),
                                    androidx.compose.ui.geometry.Size(44 * density, 44 * density), style = Stroke(1.5f * density))
                            }
                        }
                        tooltip?.let { Text(it, Modifier.align(Alignment.BottomCenter).border(1.dp, edgeColor).padding(4.dp)) }
                    }
                    if (state.busy) CircularProgressIndicator(Modifier.align(Alignment.Center).size(24.dp))
                    state.error?.let { Text(it, Modifier.align(Alignment.Center)) }
                }
                Spacer(Modifier.height(6.dp))
                val actionable = connected && !state.busy && state.error == null && state.selectedNodeId != null && !state.archivedSelection
                val enabled by rememberUpdatedState(actionable)
                var activation by remember { mutableStateOf<HistoryActivation?>(null) }
                Surface(Modifier.fillMaxWidth().height(48.dp).border(1.dp, MenuBorder, MaterialTheme.shapes.extraSmall).semantics {
                    role = Role.Button; contentDescription = "Make current"
                    if (!actionable) disabled()
                    onClick { if (actionable) controller.makeCurrent() else false }
                }.pointerInput(controller) {
                    detectTapGestures(onPress = { activation = if (enabled) controller.captureMakeCurrent() else null; tryAwaitRelease() },
                        onTap = { if (enabled) controller.makeCurrent(activation) })
                }, color = Color.White, contentColor = if (actionable) MenuInk else Color.Gray) {
                    Row(horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                        TableChromeIcon("current"); Spacer(Modifier.width(8.dp)); Text("Make current")
                    }
                }
            }
        }
        }
    }
}

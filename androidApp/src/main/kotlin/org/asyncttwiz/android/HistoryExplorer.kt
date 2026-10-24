package org.asyncttwiz.android

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
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

/** Full-screen, two-axis history picker. Tap reviews and closes; hold selects for branch actions. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HistoryExplorer(controller: HistoryExplorerController, connected: Boolean,
    authorColors: Map<String, Color>, showHidden: Boolean, onShowHidden: (Boolean) -> Unit,
    onDismiss: () -> Unit, onReview: (org.asyncttwiz.protocol.PlayState) -> Unit,
    onAdvanced: (() -> Unit)? = null, texts: Map<String, HistoryText> = emptyMap(),
    continuationReady: Boolean = true, onContinue: ((String) -> Unit)? = null) {
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
    LaunchedEffect(graph) {
        if (!framed && graph != null) {
            val point = graph.positions[state.selectedNodeId] ?: graph.positions.getValue(graph.currentNodeId)
            pan = Offset(150f - point.x, 80f - point.y)
            framed = true
        }
    }
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
                            DropdownMenuItem(text = { Text("Frame current") }, onClick = {
                                graph?.positions?.get(graph.currentNodeId)?.let { pan = Offset(150 - it.x, 80 - it.y) }
                                menu = false
                            })
                            DropdownMenuItem(text = { Text(if (showHidden) "Hide simple alternatives" else "Show hidden branches") },
                                onClick = { onShowHidden(!showHidden); menu = false })
                        }
                    }
                    Spacer(Modifier.weight(1f))
                    IconButton(onDismiss) { Icon(menuIcon("Close"), "Close game tree", tint = MenuInk) }
                }
                Text("Tap to review · hold for branch actions", style = MaterialTheme.typography.labelSmall)
                Text("Green: current path · square: review cursor · dot: branch head", style = MaterialTheme.typography.labelSmall)
                BoxWithConstraints(Modifier.fillMaxWidth().weight(1f).semantics { contentDescription = "Game history group tree" }
                    .background(Color(0xfff4f8f6)).clipToBounds().pointerInput(graph?.width, graph?.height) {
                        detectDragGestures { change, drag ->
                            change.consume()
                            graph?.pan(HistoryPoint(pan.x, pan.y), drag.x / density, drag.y / density,
                                size.width / density, size.height / density)?.let { pan = Offset(it.x, it.y) }
                        }
                    }) {
                    if (graph != null) {
                        Canvas(Modifier.fillMaxSize()) {
                            fun HistoryPoint.screen() = Offset((x + pan.x) * density, (y + pan.y) * density)
                            val ranges = graph.ranges.associateBy { it.id }
                            for (range in graph.ranges) {
                                var previous = ranges[range.parentRangeId]?.position
                                for (id in range.nodeIds) {
                                    val point = graph.positions.getValue(id)
                                    previous?.let { parent ->
                                        val from = parent.screen(); val to = point.screen()
                                        val active = id in graph.activeNodeIds
                                        // A branch changes lanes only at its fork; subsequent events remain parallel.
                                        val corner = Offset(from.x, to.y)
                                        drawLine(if (active) activeColor else edgeColor, from, corner, (if (active) 2f else 1f) * density)
                                        drawLine(if (active) activeColor else edgeColor, corner, to, (if (active) 2f else 1f) * density)
                                    }
                                    previous = point
                                }
                            }
                        }
                        for (event in graph.events) {
                            val point = graph.positions.getValue(event.id)
                            val x = point.x + pan.x; val y = point.y + pan.y
                            if (x >= -12f && x <= maxWidth.value + 12 && y >= -16 && y <= maxHeight.value + 16) {
                                val text = texts[event.id]
                                HistoryEventChip(event.copy(description = text?.description ?: event.description),
                                    authorColors[event.author] ?: Color(0xff59656a), event.id == state.selectedNodeId,
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
                val actionable = connected && !state.busy && state.error == null && state.selectedNodeId != null
                state.selectedNodeId?.let { id -> Text(texts[id]?.description ?: graph?.events?.find { it.id == id }?.description.orEmpty(),
                    style = MaterialTheme.typography.labelSmall, maxLines = 2) }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    if (onContinue != null) TextButton(enabled = actionable && continuationReady && !state.archivedSelection,
                        onClick = { state.selectedNodeId?.let(onContinue) }) { Text("Continue from here") }
                    val canActivate by rememberUpdatedState(actionable && !state.archivedSelection)
                    var activation by remember { mutableStateOf<HistoryActivation?>(null) }
                    Surface(Modifier.height(48.dp).padding(horizontal = 8.dp).pressFeedback(canActivate).border(1.dp, MenuBorder, MaterialTheme.shapes.extraSmall).semantics {
                        role = Role.Button; contentDescription = "Make current"
                        if (!canActivate) disabled()
                        onClick { if (canActivate) controller.makeCurrent() else false }
                    }.pointerInput(controller) {
                        detectTapGestures(onPress = {
                            activation = if (canActivate) controller.captureMakeCurrent() else null
                            tryAwaitRelease()
                        }, onTap = { if (canActivate) controller.makeCurrent(activation) })
                    }, color = Color.White, contentColor = if (canActivate) MenuInk else Color.Gray) {
                        Row(Modifier.padding(horizontal = 8.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                            TableChromeIcon("current"); Spacer(Modifier.width(8.dp)); Text("Make current")
                        }
                    }
                    if (onAdvanced != null) TextButton(onClick = onAdvanced) { Text("Branch details…") }
                }
            }
        }
    }
}

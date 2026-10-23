package org.asyncttwiz.android

import android.graphics.Paint
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.isActive
import org.asyncttwiz.protocol.*

/** Normal production surface. One pointer arbiter, one Actions host, and no authoritative local board. */
@Composable
internal fun TableSurface(game: GameSummary, play: PlayState, accountId: String, landscape: Boolean,
    modifier: Modifier = Modifier, connected: Boolean = false, onIntent: ((TableIntent) -> Boolean)? = null,
    placements: Map<String, TablePlacement> = emptyMap(), toolkit: TableToolkitController? = null,
    accepted: PlayState? = null, runtime: TableRuntimeHooks = TableRuntimeHooks()) {
    val layoutState: MutableState<TableLayoutState> = toolkit?.layoutState ?: remember(play.id, accountId) { mutableStateOf(TableLayoutState()) }
    val selectionState: MutableState<TableSelection> = toolkit?.selectionState ?: remember(play.id, accountId) { mutableStateOf(TableSelection()) }
    var layout by layoutState
    var selection by selectionState
    var actions by remember(play.id, accountId) { mutableStateOf<TableIntent.Actions?>(null) }
    var modeMenu by remember(play.id, accountId) { mutableStateOf(false) }
    val inspector = remember(play.id, accountId) { CardInspectionController() }
    val currentPlay by rememberUpdatedState(play)
    val callback by rememberUpdatedState(onIntent)
    val writable = connected && onIntent != null
    val currentWritable by rememberUpdatedState(writable)
    val currentSelection by rememberUpdatedState(selection)
    val currentRuntime by rememberUpdatedState(runtime)
    var presentation by remember(play.id, accountId) { mutableStateOf<TableScene?>(null) }
    var pressedPing by remember(play.id, accountId) { mutableStateOf<EventPingIntent?>(null) }
    val coordinator = remember(play.id, accountId) {
        lateinit var owner: TableGestureCoordinator
        var beforeSelection = selection
        owner = TableGestureCoordinator({ intent ->
            when (intent) {
                is TableIntent.Camera -> layout = layout.copy(camera = intent.camera)
                is TableIntent.Scroll -> layout = if (intent.vertical)
                    layout.copy(scrollY = layout.scrollY + (intent.viewportId to intent.offset))
                    else layout.copy(scroll = layout.scroll + (intent.viewportId to intent.offset))
                is TableIntent.Selection -> { beforeSelection = selection; selection = intent.selection }
                is TableIntent.Actions -> {
                    val captured = owner.capturedActions ?: intent
                    if (toolkit == null) actions = captured else {
                        val item = owner.actionItem
                        if (item != null) toolkit.open(captured, item, currentSelection)
                    }
                }
                is TableIntent.Inspect -> {
                    if (toolkit != null) { toolkit.inspect(intent.cardId); return@TableGestureCoordinator }
                    val state = currentPlay
                    val card = state.board.cards.firstOrNull { it.id == intent.cardId }
                    val permitted = TablePresentation.inspection(state, accountId, intent.cardId)
                    if (card != null && permitted != null) inspector.open(InspectedCard.retained(card.definition, permitted))
                }
                is TableIntent.Ping -> {
                    val captured = pressedPing
                    pressedPing = null
                    if (captured != null && currentRuntime.controlsConnected == true && captured.reference.playId == currentPlay.id)
                        currentRuntime.ping?.invoke(captured)
                }
                else -> {
                    val sent = currentWritable && callback?.invoke(intent) == true
                    if (!sent && intent is TableIntent.Move) selection = beforeSelection
                }
            }
        })
        owner
    }
    var preview by remember(coordinator) { mutableStateOf(TableGesturePreview()) }
    var clockEpoch by remember(coordinator) { mutableIntStateOf(0) }
    var pointerTime by remember(coordinator) { mutableLongStateOf(0L) }
    DisposableEffect(coordinator) { onDispose { coordinator.cancel(); inspector.close() } }
    LaunchedEffect(coordinator, clockEpoch) {
        // Tick only an active recognition window, not an always-running animation at an idle table.
        // Relative frame time also supports Compose's controlled test clock (not wall-clock polling).
        val base = pointerTime
        if (coordinator.busy) {
            val start = withFrameNanos { it }
            while (isActive && coordinator.busy) withFrameNanos { now ->
                coordinator.tick(base + (now - start) / 1_000_000); preview = coordinator.preview
            }
        }
    }
    BoxWithConstraints(modifier.fillMaxWidth().background(Color(0xfff3f5f5))) {
        val toolbarWidth = 144f + listOfNotNull(runtime.chat, runtime.history).size * 48f
        val targetScene = remember(game, play, accountId, maxWidth, maxHeight, landscape, layout, placements, toolbarWidth) {
            TablePresentation.scene(game, play, accountId, maxWidth.value.coerceAtLeast(1f),
                maxHeight.value.coerceAtLeast(1f), landscape, layout, placements, toolbarWidth)
        }
        var previousLayout by remember { mutableStateOf(layout) }
        var previousSize by remember { mutableStateOf(maxWidth to maxHeight) }
        val navigation = previousLayout != layout || previousSize != (maxWidth to maxHeight)
        // One interpolated scene feeds Canvas, hit testing AND semantic nodes. No destination-only overlays.
        val animation = remember(play.id, accountId) { HistorySceneAnimation(targetScene) }
        val progress = remember(animation) { androidx.compose.animation.core.Animatable(1f) }
        LaunchedEffect(targetScene) {
            animation.retarget(targetScene, progress.value)
            progress.snapTo(0f)
            if (navigation) progress.snapTo(1f) else progress.animateTo(1f, tween(180))
        }
        val frame = animation.frame(progress.value)
        val animatedItems = frame.filter { it.alpha > 0f }.map { it.item.copy(bounds = it.bounds) }
        val scene = TablePresentation.clip(targetScene.copy(items = animatedItems), maxWidth.value, maxHeight.value)
        val annotations = TableAnnotationGeometry(scene, game.inviter.accountId)
        val pingReference = runtime.pingReference ?: EventReference(play.id, play.currentNodeId)
        val activePings = play.currentGroup?.id?.let { EventAnnotations.activePings(play.annotations, it) }.orEmpty()
        var visitedGroup by remember(play.id, accountId) { mutableStateOf(play.currentGroup?.id) }
        SideEffect { previousLayout = layout; previousSize = maxWidth to maxHeight }
        SideEffect {
            presentation = scene
            var retained = selection.retain((play.board.cards.map { it.id } + play.board.stackEffects.map { it.id } +
                CombatStackRules.occurrences(play.board.combat).map { it.id }).toSet())
            if (visitedGroup != play.currentGroup?.id && retained.mode == TableMode.PING) retained = retained.copy(mode = TableMode.NORMAL)
            visitedGroup = play.currentGroup?.id
            if (retained != selection) selection = retained
            coordinator.update(scene, retained, writable || (retained.mode == TableMode.PING &&
                runtime.ping != null && runtime.controlsConnected == true))
        }
        val density = LocalDensity.current.density
        Box(Modifier.fillMaxSize().semantics {
            contentDescription = "Shared table"
            stateDescription = "${selection.ids.size} selected, ${selection.mode.name.lowercase()} mode"
            customActions = listOf(
                CustomAccessibilityAction("Zoom in") { coordinator.transform(TablePoint(0f, 0f), 1.2f, scene.fields.last().bounds.center); true },
                CustomAccessibilityAction("Zoom out") { coordinator.transform(TablePoint(0f, 0f), 1 / 1.2f, scene.fields.last().bounds.center); true },
                CustomAccessibilityAction("Pan left") { layout = layout.copy(camera = layout.camera.copy(x = layout.camera.x - 84)); true },
                CustomAccessibilityAction("Pan right") { layout = layout.copy(camera = layout.camera.copy(x = layout.camera.x + 84)); true },
                CustomAccessibilityAction("Pan up") { layout = layout.copy(camera = layout.camera.copy(y = layout.camera.y + 84)); true },
                CustomAccessibilityAction("Pan down") { layout = layout.copy(camera = layout.camera.copy(y = layout.camera.y - 84)); true },
                CustomAccessibilityAction("Clear selection") { selection = selection.copy(ids = emptySet()); true }
            ) + scene.viewports.filter { it.maxScrollY > 0 }.flatMap { p -> listOf(
                CustomAccessibilityAction("Scroll docks up") { layout = layout.copy(scrollY = layout.scrollY + (p.id to (p.scrollY - 68f).coerceAtLeast(0f))); true },
                CustomAccessibilityAction("Scroll docks down") { layout = layout.copy(scrollY = layout.scrollY + (p.id to (p.scrollY + 68f).coerceAtMost(p.maxScrollY))); true }
            ) }
        }.pointerInput(coordinator) {
            try {
                awaitEachGesture {
                    val first = awaitFirstDown(requireUnconsumed = false)
                    val point = TablePoint(first.position.x / density, first.position.y / density)
                    val state = currentPlay
                    val reference = currentRuntime.pingReference ?: EventReference(state.id, state.currentNodeId)
                    pressedPing = presentation?.let { TableAnnotationGeometry(it, game.inviter.accountId).capture(state, reference, point) }
                    coordinator.down(point, first.uptimeMillis)
                    pointerTime = first.uptimeMillis; clockEpoch++; preview = coordinator.preview
                    first.consume()
                    var transformed = false
                    do {
                        val event = awaitPointerEvent()
                        val active = event.changes.filter { it.pressed }
                        if (active.size >= 2) {
                            transformed = true
                            val a = active[0]; val b = active[1]
                            val center = (a.position + b.position) / 2f
                            val before = (a.previousPosition + b.previousPosition) / 2f
                            val previousSpan = (a.previousPosition - b.previousPosition).getDistance()
                            val span = (a.position - b.position).getDistance()
                            val bothWereDown = a.previousPressed && b.previousPressed
                            coordinator.transform(TablePoint(if (bothWereDown) (center.x - before.x) / density else 0f,
                                if (bothWereDown) (center.y - before.y) / density else 0f),
                                if (bothWereDown && previousSpan > 0) span / previousSpan else 1f,
                                TablePoint(center.x / density, center.y / density))
                        } else if (!transformed) {
                            val p = event.changes.firstOrNull { it.id == first.id }
                            if (p == null) coordinator.cancel() else {
                                val point = TablePoint(p.position.x / density, p.position.y / density)
                                if (p.pressed) coordinator.move(point, p.uptimeMillis) else coordinator.up(point, p.uptimeMillis)
                            }
                        }
                        event.changes.forEach { it.consume() }
                        pointerTime = event.changes.maxOf { it.uptimeMillis }; clockEpoch++
                        preview = coordinator.preview
                    } while (event.changes.any { it.pressed })
                    if (transformed) coordinator.cancel()
                    pressedPing = null
                }
            } finally { coordinator.cancel(); pressedPing = null }
        }) {
            Canvas(Modifier.fillMaxSize()) { drawTable(scene, accountId, selection, preview, play.board.links,
                frame.associate { it.item.id to it.alpha }, activePings,
                play.board.projectionLinks, annotations) }
            for (field in scene.fields) Box(Modifier.offset(field.bounds.x.dp, field.bounds.y.dp)
                .size(field.bounds.width.dp, field.bounds.height.dp).semantics {
                    val player = if (field.owner == game.inviter.accountId) game.inviter else game.recipient
                    contentDescription = "${player.username} Battlefield, ${play.board.containers.single { it.id == field.containerId }.cards.size} cards"
                    if (runtime.ping != null && runtime.controlsConnected == true) customActions = annotations.locations(field).map { (target, _) ->
                        val captured = tablePingIntent(play, pingReference, target)
                        val position = requireNotNull(target.position)
                        CustomAccessibilityAction("${if (captured.add) "Ping" else "Unping"} location column ${position.x}, row ${Math.floorDiv(position.y, 2)}") {
                            currentRuntime.controlsConnected == true && currentRuntime.ping?.invoke(captured) == true
                        }
                    }
                })
            for (ping in activePings) annotations.endpoint(ping.target)?.let { point ->
                val captured = EventPingIntent(ping.reference.reference, listOf(ping.target), false)
                Box(Modifier.offset((point.x - 10).dp, (point.y - 10).dp).size(20.dp).semantics {
                    contentDescription = "Ping ${ping.to!!.ordinal}, ${ping.target.kind.name.lowercase()}, author ${ping.recording.author}"
                    if (runtime.ping != null && runtime.controlsConnected == true) onClick("Unping") {
                        currentRuntime.controlsConnected == true && currentRuntime.ping?.invoke(captured) == true
                    }
                })
            }
            // Semantics-only children do NOT install a second pointer detector or expanded hit targets.
            // Each exposed fragment has exactly the geometry used to draw/hit-test that fragment.
            for (item in scene.items) {
                if (item.id.startsWith("stackDrawing/")) continue
                if (item.kind == TableItemKind.HAND && item.cards.isNotEmpty()) continue
                val b = item.visible.maxByOrNull { it.width * it.height } ?: continue
                key(item.id) {
                    Box(Modifier.offset(b.x.dp, b.y.dp).size(b.width.dp, b.height.dp).semantics {
                        contentDescription = item.label
                         if (item.peekOwners.isNotEmpty()) stateDescription = "Visible card owners: " + item.peekOwners.joinToString { owner ->
                            if (owner == game.inviter.accountId) game.inviter.username else game.recipient.username
                        }
                         selected = item.cardId in selection.ids
                         if (item.sourceId != null && scene.items.any { it.cardId != item.cardId && it.sourceId == item.sourceId })
                             stateDescription = "Related source and projections share this source identity; selection is independent"
                        onClick("Actions") { coordinator.actions(item); true }
                        val commands = mutableListOf<CustomAccessibilityAction>()
                        if (item.cardId != null) {
                            commands += CustomAccessibilityAction("Inspect") { coordinator.inspect(item); true }
                            commands += CustomAccessibilityAction("Select or deselect") { coordinator.select(item); true }
                            if (writable && !item.frozen) {
                                commands += CustomAccessibilityAction("Tap selected scope") { coordinator.rotate(item, true); true }
                                commands += CustomAccessibilityAction("Untap selected scope") { coordinator.rotate(item, false); true }
                                for (f in scene.fields) commands += CustomAccessibilityAction("Move to ${if (f.owner == accountId) "your" else "opponent"} battlefield") {
                                    coordinator.accessibleMove(item, TableDestination.Grid(f.containerId, 0, 0)); true
                                }
                                for (dest in play.board.containers.filter { it.zone in listOf(PlayZone.LIBRARY, PlayZone.HAND, PlayZone.GRAVEYARD, PlayZone.EXILE, PlayZone.STACK) }) {
                                    commands += CustomAccessibilityAction("Move to ${if (dest.accountId == accountId) "your" else if (dest.accountId == null) "shared" else "opponent"} ${dest.zone.name.lowercase()}") {
                                        coordinator.accessibleMove(item, TableDestination.Container(dest.id, dest.zone)); true
                                    }
                                }
                            }
                        }
                        scene.viewports.firstOrNull { it.id == item.viewport && it.maxScroll > 0 }?.let { port ->
                            commands += CustomAccessibilityAction("Scroll toward start") { layout = layout.copy(scroll = layout.scroll + (port.id to (port.scroll - port.bounds.width / 2).coerceAtLeast(0f))); true }
                            commands += CustomAccessibilityAction("Scroll toward end") { layout = layout.copy(scroll = layout.scroll + (port.id to (port.scroll + port.bounds.width / 2).coerceAtMost(port.maxScroll))); true }
                        }
                          val ping = tablePingTarget(play, item)?.let { tablePingIntent(play, pingReference, it) }
                          if (ping != null && runtime.ping != null && runtime.controlsConnected == true)
                              commands += CustomAccessibilityAction(if (ping.add) "Ping this target" else "Unping this target") {
                                  currentRuntime.controlsConnected == true && currentRuntime.ping?.invoke(ping) == true
                              }
                         customActions = commands
                    })
                }
            }
        }
        if (toolkit != null) {
            val endingColor = if (play.board.turn.accountId == accountId) OwnTableColor else OpponentTableColor
            Row(Modifier.align(Alignment.TopEnd)) {
                runtime.chat?.let { ActionSquare("Chat", "□", true, onClick = it) }
                runtime.history?.let { ActionSquare("History", "⑂", true, onClick = it) }
                if (!runtime.reviewing) TurnIndicator(play.board.turn, if (play.board.turn.accountId == game.inviter.accountId) game.inviter.username else game.recipient.username,
                    endingColor, toolkit::phasePicker)
                if (!runtime.reviewing) ActionSquare("Table actions", "⋮", true, onClick = toolkit::tableMenu)
            }
            if (!runtime.reviewing) QuickActionStrip(play, toolkitQuickContext(play, selection, toolkit.explicitStack), toolkit.table, endingColor,
                Modifier.align(Alignment.BottomEnd).padding(end = 48.dp), perform = toolkit::perform)
            runtime.undo?.let { undo -> ActionSquare("Undo", "↶", runtime.controlsConnected ?: connected,
                Modifier.align(Alignment.BottomStart), holdName = if (runtime.redo != null) "Redo" else null,
                onHold = runtime.redo?.let { redo -> { redo(); Unit } }, onClick = { undo() }) }
            // These are existing fixed references, never a fresh View request or a refill on rendering.
            Row(Modifier.align(Alignment.CenterStart).widthIn(max = 220.dp).horizontalScroll(rememberScrollState())) {
                for (display in play.board.contentsDisplays) TextButton(onClick = { toolkit.contents(display) }) {
                    Text("${if (display.reveal) "Reveal" else "View"} · ${display.cardIds.size}")
                }
            }
            TableToolkitScreen(toolkit, play, game, landscape, selection, select = { selection = selection.toggle(it) }, accepted,
                tokenCatalog = runtime.tokenCatalog)
            toolkit.error?.let { Text(it, Modifier.align(Alignment.BottomCenter).background(Color.White), color = MaterialTheme.colorScheme.error) }
        }
        // Screen-fixed personal control remains reachable; it does not compete over card hit regions.
        Box(Modifier.align(Alignment.BottomEnd)) {
            TextButton(onClick = { coordinator.cancel(); modeMenu = true }, contentPadding = PaddingValues(0.dp),
                modifier = Modifier.size(48.dp).background(Color.White).semantics {
                    contentDescription = "Interaction mode"; stateDescription = "${selection.mode.name.lowercase()}, ${selection.ids.size} selected"
                }) { Text("Mode") }
            DropdownMenu(expanded = modeMenu, onDismissRequest = { modeMenu = false }) {
                for (mode in listOf(TableMode.NORMAL, TableMode.SELECT) + if (runtime.ping != null) listOf(TableMode.PING) else emptyList()) DropdownMenuItem(text = { Text(mode.name.lowercase().replaceFirstChar(Char::uppercase)) },
                    onClick = { coordinator.cancel(); selection = selection.copy(mode = mode); modeMenu = false },
                    modifier = Modifier.semantics { selected = selection.mode == mode })
                runtime.redo?.let { redo -> DropdownMenuItem(text = { Text("Redo") }, enabled = runtime.controlsConnected ?: connected,
                    onClick = { modeMenu = false; redo() }) }
            }
        }
        actions?.let { captured ->
            val item = scene.items.firstOrNull { it.id == captured.itemId } ?: captured.scope?.contextId?.let { id ->
                play.board.cards.singleOrNull { it.id == id }?.let { card ->
                    TableItem("card/$id", TableBounds(0f, 0f, 0f, 0f), "Card Actions", "", card.owner,
                        play.board.containers.single { it.id == card.containerId }.zone, kind = TableItemKind.CARD, cardId = id)
                }
            }
            if (item == null) SideEffect { actions = null } else TableActionsScreen(game, play, accountId,
                captured, item, selection, writable, landscape, dismiss = { actions = null },
                inspect = { id -> coordinator.inspect(item.copy(cardId = id)) },
                select = { id -> selection = selection.toggle(id) },
                open = { id ->
                    val scope = selection.scope(id, play.board.cards.map { it.id })
                    actions = TableIntent.Actions("card/$id", scope, BasicTableController.sources(play, scope.cardIds))
                }, request = { intent -> if (currentWritable) callback?.invoke(intent) == true else false },
                submit = { action, invocation ->
                    // The normal production request adapter also handles player Set editors.
                    if (currentWritable) callback?.invoke(TableIntent.Submit(action, invocation)) == true else false
                })
        }
    }
    CardInspection(inspector)
}

private fun DrawScope.drawTable(scene: TableScene, viewer: String, selection: TableSelection, preview: TableGesturePreview,
    links: List<PlayLink> = emptyList(), alphas: Map<String, Float> = emptyMap(), pings: List<RecordedPingChange> = emptyList(),
    projectionLinks: List<ProjectionLink> = emptyList(), annotations: TableAnnotationGeometry) {
    val density = density
    fun xy(x: Float, y: Float) = Offset(x * density, y * density)
    fun size(w: Float, h: Float) = Size(w * density, h * density)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.DKGRAY; textSize = 11 * density }
    fun text(value: String, x: Float, y: Float, max: Int = 16) {
        drawContext.canvas.nativeCanvas.drawText(value.take(max), x * density, y * density, paint)
    }
    for (f in scene.fields) {
        val b = f.bounds
        val color = if (f.owner == viewer) OwnTableColor else OpponentTableColor
        drawRect(color.copy(alpha = .10f), xy(b.x, b.y), size(b.width, b.height))
        val outerY = if (f.owner == viewer) b.bottom - b.height / 3 else b.y
        drawRect(color.copy(alpha = .10f), xy(b.x, outerY), size(b.width, b.height / 3))
    }
    for ((from, to) in scene.combatDividers) drawLine(Color.Black.copy(alpha = .12f), xy(from.x, from.y), xy(to.x, to.y), density)
    fun endpoint(endpoint: LinkEndpoint): TablePoint? = scene.items.firstOrNull { item ->
        if (endpoint.kind == LinkEndpointKind.CARD) item.cardId == endpoint.id else item.id == "life/${endpoint.id}"
    }?.takeIf { it.visible.isNotEmpty() }?.bounds?.center
    for (link in links) {
        val a = endpoint(link.first); val b = endpoint(link.second)
        if (a != null && b != null) drawLine(Color(0xff667b89), xy(a.x, a.y), xy(b.x, b.y), 2 * density)
    }
    for (link in projectionLinks) {
        val first = scene.items.firstOrNull { it.cardId == link.first && it.visible.isNotEmpty() }?.bounds?.center
        val second = scene.items.firstOrNull { it.cardId == link.second && it.visible.isNotEmpty() }?.bounds?.center
        if (first != null && second != null) drawLine(Color(0xff667b89), xy(first.x, first.y), xy(second.x, second.y), 2 * density)
    }
    val focused = selection.ids + listOfNotNull(preview.pressedId?.let { id -> scene.items.firstOrNull { it.id == id }?.cardId })
    val relatedSources = scene.items.filter { it.cardId in focused }.mapNotNull { it.sourceId }.toSet()
    for (i in scene.items) for (part in i.visible) clipRect(part.x * density, part.y * density, part.right * density, part.bottom * density) {
        drawContext.canvas.saveLayer(androidx.compose.ui.geometry.Rect(part.x * density, part.y * density,
            part.right * density, part.bottom * density), androidx.compose.ui.graphics.Paint().apply { alpha = alphas[i.id] ?: 1f })
        try {
        val b = i.bounds
        val color = if (i.owner == null) Color(0xff59656a) else if (i.owner == viewer) OwnTableColor else OpponentTableColor
        when (i.kind) {
            TableItemKind.HAND -> if (i.cards.isEmpty()) text(i.value, b.x + 8, b.y + 18)
            TableItemKind.LIFE -> {
                drawRect(color.copy(alpha = .2f), xy(b.x, b.y), size(b.width, b.height))
                text(i.value, b.x + 8, b.y + 25)
            }
            TableItemKind.COUNTER -> {
                val marker = when (i.counter) {
                    CounterColor.RED -> Color(0xffb44b51); CounterColor.BLUE -> Color(0xff497ca7)
                    CounterColor.GREEN -> Color(0xff46845d); CounterColor.YELLOW -> Color(0xffc7ab3d)
                    CounterColor.PURPLE -> Color(0xff8c639d); else -> Color.Gray
                }
                drawCircle(marker.copy(alpha = .3f), 14 * density, xy(b.center.x, b.center.y))
                text(i.value, b.center.x - 7, b.center.y + 4)
            }
            TableItemKind.PILE -> {
                for (peek in minOf(2, (i.cards.size - 1).coerceAtLeast(0)) downTo 0) {
                    val d = peek * 4 * scene.camera.zoom
                    val peekColor = i.peekOwners.getOrNull(peek)?.let { if (it == viewer) OwnTableColor else OpponentTableColor } ?: color
                    drawRoundRect(Color.White, xy(b.x + d, b.y + d), size(scene.cardWidth, scene.cardHeight), CornerRadius(3 * density))
                    drawRoundRect(peekColor, xy(b.x + d, b.y + d), size(scene.cardWidth, scene.cardHeight), CornerRadius(3 * density), style = Stroke(density))
                }
                text(i.zone?.name?.lowercase().orEmpty(), b.x + 3, b.y + scene.cardHeight / 2, 9)
                drawCircle(color, 9 * density, xy(b.x + 9, b.y + 9))
                paint.color = android.graphics.Color.WHITE; text(i.value, b.x + 3, b.y + 13, 5); paint.color = android.graphics.Color.DKGRAY
            }
            TableItemKind.CARD -> {
                // Rotate the whole face, not just its outline. Actual dimensions remain common across zones.
                rotate(if (i.tapped) 90f else 0f, xy(b.center.x, b.center.y)) {
                    val x = b.center.x - scene.cardWidth / 2; val y = b.center.y - scene.cardHeight / 2
                    drawRoundRect(if (i.covered) Color(0xffe0e6e7) else Color.White, xy(x, y), size(scene.cardWidth, scene.cardHeight), CornerRadius(3 * density))
                    drawRoundRect(color, xy(x, y), size(scene.cardWidth, scene.cardHeight), CornerRadius(3 * density),
                        style = Stroke(2 * density, pathEffect = if (i.projection) PathEffect.dashPathEffect(floatArrayOf(5 * density, 3 * density)) else null))
                    if (i.value.isNotEmpty()) text(i.value, x + 3, y + 15, 9)
                    else drawLine(color.copy(alpha = .4f), xy(x + 6, y + 8), xy(x + scene.cardWidth - 6, y + scene.cardHeight - 8), density)
                     text(i.corner, x + scene.cardWidth - 22, y + scene.cardHeight - 5, 8)
                     if (i.token) text("T", x + 3, y + scene.cardHeight - 5, 1)
                     if (i.note.isNotEmpty()) text(i.note, x + 3, y + 30, 8)
                     i.counters.entries.take(6).forEachIndexed { n, entry ->
                         drawCircle(counterColor(entry.key), 5 * density, xy(x + 6 + n * 7, y + scene.cardHeight - 18))
                         text(entry.value, x + 3 + n * 7, y + scene.cardHeight - 16, 3)
                     }
                 }
            }
        }
        if (i.sourceId != null && i.sourceId in relatedSources) drawRect(Color(0xffbd982f), xy(b.x + 3, b.y + 3),
            size((b.width - 6).coerceAtLeast(0f), (b.height - 6).coerceAtLeast(0f)), style = Stroke(2 * density))
        if (i.cardId in selection.ids) drawRect(Color(0xff397ccc), xy(b.x, b.y), size(b.width, b.height), style = Stroke(3 * density))
        if (i.id == preview.pressedId) drawRect(Color(0xffe7ab24), xy(b.x, b.y), size(b.width, b.height), style = Stroke(2 * density))
        } finally { drawContext.canvas.restore() }
    }
    scene.items.firstOrNull { it.zone == PlayZone.STACK && it.kind == TableItemKind.PILE }?.let { i ->
        val b = i.bounds
        if (i.visible.isNotEmpty()) {
            drawCircle(Color(0xff59656a), 9 * density, xy(b.x + 9, b.y + 9))
            paint.color = android.graphics.Color.WHITE; text(i.value, b.x + 3, b.y + 13, 5); paint.color = android.graphics.Color.DKGRAY
        }
    }
    for (ping in pings) {
        val p = annotations.endpoint(ping.target) ?: continue
        drawCircle(if (ping.recording.author == viewer) OwnTableColor else OpponentTableColor, 10 * density, xy(p.x, p.y))
        paint.color = android.graphics.Color.WHITE
        text(ping.to!!.ordinal, p.x - 4, p.y + 4, 5)
        paint.color = android.graphics.Color.DKGRAY
    }
    preview.rectangle?.let { b ->
        drawRect(OwnTableColor.copy(alpha = .14f), xy(b.x, b.y), size(b.width, b.height))
        drawRect(OwnTableColor, xy(b.x, b.y), size(b.width, b.height), style = Stroke(density))
    }
    preview.point?.let { p ->
        if (preview.scope != null) {
            drawRoundRect(if (preview.destination == null) OpponentTableColor.copy(alpha = .35f) else OwnTableColor.copy(alpha = .35f),
                xy(p.x - scene.cardWidth / 2, p.y - scene.cardHeight / 2), size(scene.cardWidth, scene.cardHeight), CornerRadius(3 * density))
            text("${preview.scope.cardIds.size}", p.x, p.y)
        }
        preview.delta?.let { text(if (it >= 0) "+$it" else "$it", p.x, p.y) }
    }
}

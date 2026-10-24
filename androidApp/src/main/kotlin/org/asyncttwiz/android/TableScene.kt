package org.asyncttwiz.android

import android.graphics.Paint
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import kotlinx.coroutines.isActive
import org.asyncttwiz.protocol.*

/** No fabricated cards, values or actionable board while the accepted boundary is unavailable. */
@Composable
internal fun TableUnavailable(title: String, landscape: Boolean, modifier: Modifier = Modifier,
    navigation: (() -> Unit)? = null, message: String? = null, loading: Boolean = true,
    retry: () -> Unit, returnCurrent: (() -> Unit)? = null) {
    BoxWithConstraints(modifier.fillMaxSize().background(Color(0xfff5f8f7))) {
        val side = if (landscape) TablePresentation.sidebarWidth(maxWidth.value) else 0f
        Canvas(Modifier.fillMaxSize().clearAndSetSemantics {}) {
            val d = density
            val left = (side + 6f) * d
            val top = (if (landscape) 52f else 106f) * d
            val bottom = size.height - (if (landscape) 56f else 108f) * d
            val middle = (top + bottom) / 2
            val gap = (if (landscape) 0f else 116f) * d
            drawRect(Color(0xfff4e8ea), Offset(left, top), Size(size.width - left - 6 * d, middle - gap - top))
            drawRect(Color(0xffe2efe6), Offset(left, middle + gap), Size(size.width - left - 6 * d, bottom - middle - gap))
        }
        Row(Modifier.height(48.dp), verticalAlignment = Alignment.CenterVertically) {
            ActionSquare("Open navigation", "", navigation != null, Modifier.width(48.dp),
                icon = { TableChromeIcon("menu") }, onClick = { navigation?.invoke() })
            Text(title, fontWeight = FontWeight.Bold, fontSize = 13.sp)
        }
        Surface(Modifier.align(Alignment.Center).padding(16.dp).widthIn(max = 368.dp),
            shape = MaterialTheme.shapes.medium, color = Color.White, shadowElevation = 4.dp) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(if (loading) "Loading table…" else "Table unavailable", fontWeight = FontWeight.Bold)
                message?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                if (loading) CircularProgressIndicator(Modifier.size(24.dp), color = OwnTableColor)
                else Button(retry, Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.extraSmall,
                    colors = ButtonDefaults.buttonColors(containerColor = OwnTableColor)) {
                    TableChromeIcon("current"); Spacer(Modifier.width(8.dp)); Text("Reconnect")
                }
                returnCurrent?.let { TextButton(it, Modifier.fillMaxWidth()) { Text("Return to current") } }
            }
        }
    }
}

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
    var localStackBubble by remember(play.id, accountId) { mutableStateOf<String?>(null) }
    var modeMenu by remember(play.id, accountId) { mutableStateOf(false) }
    val inspector = remember(play.id, accountId) { CardInspectionController() }
    var inspectedCard by remember(play.id, accountId) { mutableStateOf<PlayCard?>(null) }
    val currentPlay by rememberUpdatedState(play)
    val callback by rememberUpdatedState(onIntent)
    val writable = connected && onIntent != null
    val currentWritable by rememberUpdatedState(writable)
    val currentSelection by rememberUpdatedState(selection)
    val currentRuntime by rememberUpdatedState(runtime)
    var presentation by remember(play.id, accountId) { mutableStateOf<TableScene?>(null) }
    var pressedPing by remember(play.id, accountId) { mutableStateOf<EventPingIntent?>(null) }
    val density = LocalDensity.current.density
    val coordinator = remember(play.id, accountId, density) {
        lateinit var owner: TableGestureCoordinator
        var beforeSelection = selection
        owner = TableGestureCoordinator({ intent ->
            when (intent) {
                is TableIntent.StackBubbles -> if (toolkit != null) toolkit.showStackBubbles(intent.entryId) else localStackBubble = intent.entryId
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
                    if (card != null && permitted != null) {
                        inspectedCard = card
                        inspector.open(InspectedCard.retained(card.definition, permitted))
                    }
                }
                is TableIntent.Ping -> {
                    val captured = pressedPing
                    pressedPing = null
                    if (captured != null && currentRuntime.controlsConnected == true && captured.reference.playId == currentPlay.id)
                        currentRuntime.ping?.invoke(captured)
                }
                else -> {
                    if (intent is TableIntent.StackDestination) { toolkit?.showStackBubbles(null); localStackBubble = null }
                    val sent = currentWritable && callback?.invoke(intent) == true
                    if (!sent && intent is TableIntent.Move) selection = beforeSelection
                }
            }
        }, density = density)
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
    BoxWithConstraints(modifier.fillMaxWidth().background(Color(0xfff5f8f7)).pointerInput(coordinator) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            val point = coordinator.pointFromPixels(down.position.x, down.position.y)
            val bubble = presentation?.stackBubbles
            if (bubble != null && bubble.controls.none { it.bounds.contains(point) }) {
                toolkit?.showStackBubbles(null); localStackBubble = null
            }
        }
    }) {
        val compactHeader = maxWidth < 400.dp
        val toolbarWidth = if (compactHeader) 240f else TablePresentation.TOOLBAR
        val targetScene = remember(game, play, accountId, maxWidth, maxHeight, landscape, layout, placements, toolbarWidth, runtime.reviewing) {
            TablePresentation.scene(game, play, accountId, maxWidth.value.coerceAtLeast(1f),
                maxHeight.value.coerceAtLeast(1f), landscape, layout, placements, toolbarWidth, runtime.reviewing)
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
        val baseScene = TablePresentation.clip(targetScene.copy(items = animatedItems), maxWidth.value, maxHeight.value)
        val bubbleId = if (runtime.reviewing) null else toolkit?.stackBubbleEntry ?: localStackBubble
        LaunchedEffect(toolkit, bubbleId, play) {
            // The rendered history/animation boundary can briefly lag the session prediction during
            // acceptance. Validate personal focus against the session, not that older rendered board.
            if (toolkit != null) toolkit.refreshStackBubbles()
            else if (bubbleId != null && bubbleId !in CombatStackRules.stack(play.board).cards) localStackBubble = null
        }
        val bubbleCard = baseScene.items.find { bubbleId != null && it.cardId == bubbleId && it.zone == PlayZone.STACK && it.visible.isNotEmpty() }
        val bubble = TablePresentation.stackBubbles(baseScene, play, bubbleId, maxWidth.value, maxHeight.value)
        val scene = baseScene.copy(stackBubbles = bubble)
        val annotations = TableAnnotationGeometry(scene, game.inviter.accountId)
        val artwork = tableArtwork(scene)
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
                    val point = coordinator.pointFromPixels(first.position.x, first.position.y)
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
                            coordinator.transform(coordinator.pointFromPixels(if (bothWereDown) center.x - before.x else 0f,
                                if (bothWereDown) center.y - before.y else 0f),
                                if (bothWereDown && previousSpan > 0) span / previousSpan else 1f,
                                coordinator.pointFromPixels(center.x, center.y))
                        } else if (!transformed) {
                            val p = event.changes.firstOrNull { it.id == first.id }
                            if (p == null) coordinator.cancel() else {
                                val point = coordinator.pointFromPixels(p.position.x, p.position.y)
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
                play.board.projectionLinks, annotations, artwork) }
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
                if (item.kind == TableItemKind.HAND && item.cards.isNotEmpty()) continue
                val b = if (item.zone == PlayZone.STACK && item.kind == TableItemKind.PILE)
                    item.stackBadge
                    else item.visible.maxByOrNull { it.width * it.height } ?: continue
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
                                if (item.zone != PlayZone.STACK) {
                                    commands += CustomAccessibilityAction("Tap selected scope") { coordinator.rotate(item, true); true }
                                    commands += CustomAccessibilityAction("Untap selected scope") { coordinator.rotate(item, false); true }
                                }
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
            for (item in scene.items.filter { it.zone == PlayZone.STACK && it.kind == TableItemKind.CARD && it.visible.isNotEmpty() }) {
                play.board.stackTextMarks[item.cardId]?.let { index ->
                    StackMarkChip(index, Modifier.offset((item.bounds.right - 17).dp, item.bounds.y.dp)
                        .semantics { contentDescription = "Stack text mark ${index + 1}" })
                }
            }
            if (bubble != null) StackBubbleOverlay(bubble, StackText.options(play, bubble.entryId),
                if (bubbleCard?.owner == accountId) OwnTableColor else OpponentTableColor, preview, writable) { intent ->
                if (intent is TableIntent.StackDestination) { toolkit?.showStackBubbles(null); localStackBubble = null }
                callback?.invoke(intent)
            }
        }
        TableChromeHeader(game, play, accountId, landscape, runtime, toolkit, compactHeader)
        if (runtime.reviewing) runtime.reviewPanel?.invoke()
        if (toolkit != null) {
            val endingColor = if (play.board.turn.accountId == accountId) OwnTableColor else OpponentTableColor
            if (!runtime.reviewing) QuickActionStrip(play, toolkitQuickContext(play, selection, toolkit.explicitStack), toolkit.table, endingColor,
                Modifier.align(Alignment.BottomEnd).padding(end = if (landscape) 82.dp else 112.dp, bottom = 4.dp), perform = toolkit::perform)
            if (!runtime.reviewing) runtime.undo?.let { undo -> ActionSquare("Undo", "↶", runtime.controlsConnected ?: connected,
                Modifier.align(Alignment.BottomStart).padding(start = 10.dp, bottom = 4.dp), holdName = if (runtime.redo != null) "Redo" else null,
                onHold = runtime.redo?.let { redo -> { redo() } }, icon = { TableChromeIcon("undo") }, onClick = { undo() }) }
            TableToolkitScreen(toolkit, play, game, landscape, selection, select = { selection = selection.toggle(it) }, accepted,
                tokenCatalog = runtime.tokenCatalog)
        }
        FloatingTransientError(runtime.errorMessage ?: toolkit?.error,
            { if (runtime.errorMessage != null) runtime.dismissError?.invoke() else toolkit?.dismissError() },
            Modifier.align(Alignment.BottomCenter).padding(bottom = 64.dp))
        // Screen-fixed personal control remains reachable; it does not compete over card hit regions.
        if (!runtime.reviewing) Box(Modifier.align(Alignment.BottomEnd).padding(end = 10.dp, bottom = 4.dp)) {
            ActionSquare("Interaction mode", "", true,
                modifier = Modifier.width(66.dp).height(48.dp).background(Color.White).border(1.dp, OwnTableColor).semantics {
                    contentDescription = "Interaction mode"; stateDescription = "${selection.mode.name.lowercase()}, ${selection.ids.size} selected"
                }, ink = OwnTableColor, icon = { TableChromeIcon(when (selection.mode) { TableMode.NORMAL -> "mode"; TableMode.SELECT -> "select"; TableMode.PING -> "ping" }) },
                onClick = { coordinator.cancel(); modeMenu = true })
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
    CardInspection(inspector, token = inspectedCard?.token == true,
        owner = inspectedCard?.let { if (it.owner == accountId) OwnTableColor else OpponentTableColor })
}

/** Compact game chrome shares the surface, so landscape hands occupy the same top/bottom rails. */
@Composable
private fun BoxScope.TableChromeHeader(game: GameSummary, play: PlayState, viewer: String, landscape: Boolean,
    runtime: TableRuntimeHooks, toolkit: TableToolkitController?, compact: Boolean) {
    var overflow by remember { mutableStateOf(false) }
    val turnColor = if (play.board.turn.accountId == viewer) OwnTableColor else OpponentTableColor
    Row(Modifier.align(Alignment.TopStart).height(48.dp)
        .fillMaxWidth(if (landscape) .366f else 1f), verticalAlignment = Alignment.CenterVertically) {
        ActionSquare("Open navigation", "", runtime.navigation != null, Modifier.width(48.dp), icon = { TableChromeIcon("menu") }, onClick = { runtime.navigation?.invoke() })
        Text(game.gameName, Modifier.weight(1f).padding(horizontal = if (compact) 6.dp else 8.dp), fontSize = if (compact) 12.sp else 13.sp,
            lineHeight = 14.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
        if (!landscape) Spacer(Modifier.width(if (compact) 240.dp else TablePresentation.TOOLBAR.dp))
    }
    val buttonWidth = 48f
    Row(Modifier.align(Alignment.TopEnd).height(48.dp).padding(end = if (compact) 0.dp else 8.dp),
        horizontalArrangement = Arrangement.spacedBy(if (compact) 0.dp else 4.dp), verticalAlignment = Alignment.CenterVertically) {
        ActionSquare("Turn ${play.board.turn.number}, ${play.board.turn.phase}, ${play.board.turn.step}. Choose phase/step",
            "", toolkit != null && !runtime.reviewing,
            Modifier.width(buttonWidth.dp).border(2.dp, turnColor), fill = phaseColor(play.board.turn.phase), ink = Color(0xff25333b),
            icon = { Box(Modifier.size(22.dp).border(1.dp, turnColor), contentAlignment = Alignment.Center) {
                Text(play.board.turn.number, fontSize = 15.sp, lineHeight = 16.sp, modifier = Modifier.clearAndSetSemantics {})
            } },
            onClick = { toolkit?.phasePicker() })
        if (runtime.reviewing) {
            ActionSquare("Continue playing from here", "", runtime.continueHere != null, Modifier.width(buttonWidth.dp).background(Color.White),
                icon = { TableChromeIcon("fork") }, onClick = { runtime.continueHere?.invoke() })
            ActionSquare("Return to current", "", runtime.returnCurrent != null, Modifier.width(buttonWidth.dp).background(Color.White), icon = { TableChromeIcon("current") }, onClick = { runtime.returnCurrent?.invoke() })
        } else {
            ActionSquare(if (runtime.continuationArmed) "History · continuation armed" else "History", "", runtime.history != null,
                Modifier.width(buttonWidth.dp).then(if (runtime.continuationArmed) Modifier.border(2.dp, OwnTableColor) else Modifier.background(Color.White)),
                // Transparent fill keeps the default dark ink; an opaque fill would switch the icon to white.
                fill = if (runtime.continuationArmed) Color(0xffedf3ef) else Color.Transparent,
                ink = if (runtime.continuationArmed) OwnTableColor else null,
                icon = { TableChromeIcon(if (runtime.continuationArmed) "fork" else "history") }, onClick = { runtime.history?.invoke() })
            ActionSquare("History tree", "", runtime.tree != null, Modifier.width(buttonWidth.dp).background(Color.White), icon = { TableChromeIcon("tree") }, onClick = { runtime.tree?.invoke() })
        }
        if (runtime.reviewing) ActionSquare("History tree", "", runtime.tree != null, Modifier.width(buttonWidth.dp).background(Color.White), icon = { TableChromeIcon("tree") }, onClick = { runtime.tree?.invoke() })
        Box {
            ActionSquare("Chat, ${runtime.chatUnread} unread messages", "", runtime.chat != null, Modifier.width(buttonWidth.dp).background(Color.White), icon = { TableChromeIcon("chat") }, onClick = { runtime.chat?.invoke() })
            if (runtime.chatUnread > 0) Box(Modifier.align(Alignment.TopEnd).padding(6.dp).size(6.dp).background(OpponentTableColor, androidx.compose.foundation.shape.CircleShape))
        }
        if (!runtime.reviewing) Box {
            ActionSquare("Game controls", "", true, Modifier.width(buttonWidth.dp).background(Color.White), icon = { TableChromeIcon("overflow") }, onClick = { overflow = true })
            DropdownMenu(overflow, { overflow = false }, modifier = Modifier.width(248.dp), containerColor = Color.White) {
                Text("Game controls", Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.labelMedium, color = Color(0xff597478))
                runtime.redo?.let { redo -> DropdownMenuItem(text = { Text("Redo") }, leadingIcon = { TableChromeIcon("redo") }, enabled = runtime.controlsConnected == true,
                    onClick = { overflow = false; redo() }) }
                toolkit?.let { DropdownMenuItem(text = { Text("Table actions") }, leadingIcon = { TableChromeIcon("grid") }, onClick = { overflow = false; it.tableMenu() }) }
                runtime.returnCurrent?.let { DropdownMenuItem(text = { Text("Return to current") }, onClick = { overflow = false; it() }) }
                runtime.menuContent?.invoke()
                // Retained displays stay reachable without labels floating over the board.
                if (toolkit != null) for (display in play.board.contentsDisplays) DropdownMenuItem(
                    text = { Text("${if (display.reveal) "Reveal" else "View"} · ${display.cardIds.size}") },
                    onClick = { overflow = false; toolkit.contents(display) })
                DropdownMenuItem(text = { Text("Close menu") }, leadingIcon = { TableChromeIcon("close") }, onClick = { overflow = false })
            }
        }
    }
}

/** Small native vectors, not font-dependent ligatures or emoji. */
@Composable
internal fun TableChromeIcon(kind: String) {
    val color = LocalContentColor.current
    Canvas(Modifier.size(24.dp).clearAndSetSemantics {}) {
        val u = size.width / 24f
        fun line(x: Float, y: Float, a: Float, b: Float) = drawLine(color, Offset(x * u, y * u), Offset(a * u, b * u), 1.7f * u)
        fun box(x: Float, y: Float, w: Float, h: Float) = drawRect(color, Offset(x * u, y * u), Size(w * u, h * u), style = Stroke(1.6f * u))
        when (kind) {
            "menu" -> for (y in listOf(5f, 12f, 19f)) line(4f, y, 20f, y)
            "chat" -> { box(3f, 3f, 17f, 14f); line(3f, 17f, 3f, 22f); line(3f, 22f, 8f, 17f) }
            "tree" -> { box(2f, 2f, 5f, 5f); box(17f, 3f, 5f, 5f); box(17f, 17f, 5f, 5f); line(7f, 5f, 12f, 5f); line(12f, 5f, 12f, 20f); line(12f, 20f, 17f, 20f); line(12f, 6f, 17f, 6f) }
            "fork" -> { line(6f, 21f, 6f, 4f); line(6f, 11f, 18f, 4f); line(12f, 4f, 18f, 4f); line(18f, 4f, 18f, 10f) }
            "history" -> { drawArc(color, -150f, 305f, false, Offset(4f * u, 4f * u), Size(16f * u, 16f * u), style = Stroke(1.7f * u)); line(3f, 3f, 3f, 9f); line(3f, 9f, 9f, 9f); line(12f, 7f, 12f, 12f); line(12f, 12f, 16f, 15f) }
            "overflow" -> for (y in listOf(5f, 12f, 19f)) drawCircle(color, 1.6f * u, Offset(12f * u, y * u))
            "undo" -> { line(10f, 4f, 4f, 9f); line(4f, 9f, 10f, 13f); line(4f, 9f, 15f, 9f); drawArc(color, -90f, 240f, false, Offset(10f * u, 9f * u), Size(10f * u, 10f * u), style = Stroke(1.7f * u)) }
            "redo" -> { line(14f, 4f, 20f, 9f); line(20f, 9f, 14f, 13f); line(20f, 9f, 9f, 9f); drawArc(color, -90f, -240f, false, Offset(4f * u, 9f * u), Size(10f * u, 10f * u), style = Stroke(1.7f * u)) }
            "close" -> { line(5f, 5f, 19f, 19f); line(5f, 19f, 19f, 5f) }
            "grid" -> for (x in listOf(5f, 12f, 19f)) for (y in listOf(5f, 12f, 19f)) drawCircle(color, 1.6f * u, Offset(x * u, y * u))
            "card" -> box(5f, 2f, 14f, 20f)
            "stack" -> { box(3f, 2f, 13f, 17f); line(7f, 21f, 20f, 21f); line(20f, 21f, 20f, 6f) }
            "previous" -> { line(18f, 5f, 18f, 19f); line(18f, 5f, 7f, 12f); line(18f, 19f, 7f, 12f); line(5f, 5f, 5f, 19f) }
            "next" -> { line(6f, 5f, 6f, 19f); line(6f, 5f, 17f, 12f); line(6f, 19f, 17f, 12f); line(19f, 5f, 19f, 19f) }
            "current" -> { box(2f, 3f, 20f, 15f); line(8f, 21f, 16f, 21f); line(10f, 7f, 16f, 11f); line(16f, 11f, 10f, 15f); line(10f, 15f, 10f, 7f) }
            "ping" -> { drawCircle(color, 7f * u, center, style = Stroke(1.7f * u)); drawCircle(color, 2f * u, center) }
            "select" -> box(4f, 4f, 16f, 16f)
            else -> { drawArc(color, 180f, 180f, false, Offset(4f * u, 2f * u), Size(9f * u, 10f * u), style = Stroke(1.7f * u)); line(8f, 7f, 8f, 17f); line(8f, 17f, 14f, 22f); line(14f, 22f, 19f, 18f); line(19f, 18f, 19f, 13f); line(19f, 13f, 12f, 12f); line(12f, 12f, 12f, 6f); line(20f, 22f, 22f, 24f); line(22f, 24f, 24f, 22f) }
        }
    }
}

@Composable
private fun tableArtwork(scene: TableScene): Map<String, ImageBitmap> {
    val images = mutableMapOf<String, ImageBitmap>()
    val context = LocalContext.current
    for (reference in scene.items.filter { it.visible.isNotEmpty() && !it.covered }.mapNotNull { it.artwork }.distinct()) key(reference) {
        val width = with(LocalDensity.current) { scene.cardWidth.dp.roundToPx() }.coerceIn(1, 4096)
        val height = with(LocalDensity.current) { scene.cardHeight.dp.roundToPx() }.coerceIn(1, 4096)
        val image by produceState<ImageBitmap?>(null, reference, width, height) {
            val request = CardArtworkRequest(reference, width, height)
            val result = PublicArtwork.get(context).loader.execute(coil3.request.ImageRequest.Builder(context)
                .data(request).size(width, height).build()) as? coil3.request.SuccessResult
            value = (result?.image as? coil3.BitmapImage)?.bitmap?.asImageBitmap()
        }
        image?.let { images[reference.key] = it }
    }
    return images
}

private fun DrawScope.drawTable(scene: TableScene, viewer: String, selection: TableSelection, preview: TableGesturePreview,
    links: List<PlayLink> = emptyList(), alphas: Map<String, Float> = emptyMap(), pings: List<RecordedPingChange> = emptyList(),
    projectionLinks: List<ProjectionLink> = emptyList(), annotations: TableAnnotationGeometry, artwork: Map<String, ImageBitmap> = emptyMap()) {
    val density = density
    fun xy(x: Float, y: Float) = Offset(x * density, y * density)
    fun size(w: Float, h: Float) = Size(w * density, h * density)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.DKGRAY; textSize = 11 * density }
    fun text(value: String, x: Float, y: Float, max: Int = 16) {
        drawContext.canvas.nativeCanvas.drawText(value.take(max), x * density, y * density, paint)
    }
    fun fittedText(value: String, x: Float, y: Float, width: Float, right: Boolean = false) {
        var label = value
        if (paint.measureText(label) > width * density) {
            while (label.isNotEmpty() && paint.measureText("$label…") > width * density) label = label.dropLast(1)
            label += "…"
        }
        text(label, if (right) x - paint.measureText(label) / density else x, y, label.length)
    }
    fun cardFace(i: TableItem, x: Float, y: Float, color: Color, title: String) {
        val z = scene.camera.zoom
        val outline = cardSilhouette(scene.cardWidth * density, scene.cardHeight * density, i.token, x * density, y * density, 2 * density)
        drawPath(outline, if (i.covered) Color(0xff506b61) else Color.White)
        drawPath(outline, color,
            style = Stroke(1.5f * density, pathEffect = if (i.projection) PathEffect.dashPathEffect(floatArrayOf(5 * density, 3 * density)) else null))
        if (i.covered) return
        paint.textSize = 7 * z * density; paint.isFakeBoldText = true
        val titleInset = if (i.token) scene.cardWidth * .22f else 2 * z
        fittedText(title, x + titleInset, y + 8 * z, scene.cardWidth - 2 * titleInset)
        paint.isFakeBoldText = false
        val art = i.artwork?.key?.let(artwork::get)
        if (art != null) drawImage(art, dstOffset = IntOffset(((x + 2 * z) * density).toInt(), ((y + 11 * z) * density).toInt()),
            dstSize = IntSize(((scene.cardWidth - 4 * z) * density).toInt().coerceAtLeast(1), ((scene.cardHeight - 21 * z) * density).toInt().coerceAtLeast(1)))
        else {
            paint.textSize = 17 * z * density
            text(i.typeIcon, x + (scene.cardWidth - paint.measureText(i.typeIcon) / density) / 2, y + scene.cardHeight * .7f, i.typeIcon.length)
        }
        paint.textSize = 7 * z * density
        fittedText(i.corner, x + scene.cardWidth - titleInset, y + scene.cardHeight - 3 * z, scene.cardWidth - 2 * titleInset, right = true)
        paint.textSize = 11 * density
    }
    scene.viewports.firstOrNull { it.id == "sidebar" }?.bounds?.let { b ->
        drawRect(Color.White, xy(6f, b.y), size((b.width - 6).coerceAtLeast(0f), b.height))
        drawRect(Color(0xffb9cbc5), xy(6f, b.y), size((b.width - 6).coerceAtLeast(0f), b.height), style = Stroke(density * .7f))
    }
    for (f in scene.fields) {
        val b = f.gridBounds
        val viewport = f.bounds
        clipRect(viewport.x * density, viewport.y * density, viewport.right * density, viewport.bottom * density) {
        val color = if (f.owner == viewer) Color(0xffe2efe6) else Color(0xfff4e8ea)
        drawRect(color, xy(b.x, b.y), size(b.width, b.height))
        val lands = f.landsBounds(f.owner == viewer, scene.camera)
        drawRect(if (f.owner == viewer) Color(0xffc4dcd0) else Color(0xffe9d1d3),
            xy(lands.x, lands.y), size(lands.width, lands.height))
        }
    }
    for (hand in scene.items.filter { it.kind == TableItemKind.HAND }) {
        val b = hand.bounds
        if (hand.owner != viewer) drawRect(OpponentTableColor.copy(alpha = .16f), xy(b.x, b.y), size(b.width, b.height))
        else {
            drawRoundRect(Color.White, xy(b.x, b.y), size(b.width, b.height), CornerRadius(5 * density))
            drawRoundRect(OwnTableColor.copy(alpha = .6f), xy(b.x, b.y), size(b.width, b.height), CornerRadius(5 * density), style = Stroke(density / 2))
        }
        text(hand.value, b.right - 18f, b.center.y + 4f, 4)
    }
    scene.viewports.firstOrNull { it.id == "combat" }?.bounds?.let { b ->
        clipRect(b.x * density, b.y * density, b.right * density, b.bottom * density) {
            drawRect(Color(0xfff5f1df), xy(b.x, b.y), size(b.width, b.height))
            for ((from, to) in scene.combatDividers) drawLine(Color.Black.copy(alpha = .12f), xy(from.x, from.y), xy(to.x, to.y), density)
        }
    }
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
             TableItemKind.HAND -> Unit
            TableItemKind.LIFE -> {
                drawRect(color, xy(b.x, b.y), size(b.width, b.height))
                paint.color = android.graphics.Color.WHITE; paint.textSize = 24 * density; paint.isFakeBoldText = true
                fittedText(i.value, b.right - 5, b.y + 32, b.width - 10, right = true)
                paint.color = android.graphics.Color.DKGRAY; paint.textSize = 11 * density; paint.isFakeBoldText = false
            }
            TableItemKind.COUNTER -> {
                val marker = when (i.counter) {
                    CounterColor.RED -> Color(0xffb44b51); CounterColor.BLUE -> Color(0xff497ca7)
                    CounterColor.GREEN -> Color(0xff46845d); CounterColor.YELLOW -> Color(0xffc7ab3d)
                    CounterColor.PURPLE -> Color(0xff8c639d); else -> Color.Gray
                }
                drawCircle(marker, 13 * density, xy(b.center.x, b.center.y), style = Stroke(density * .7f))
                paint.isFakeBoldText = true
                text(i.value, b.center.x - paint.measureText(i.value) / density / 2, b.center.y + 4)
                paint.isFakeBoldText = false
            }
            TableItemKind.PILE -> {
                if (i.cards.isEmpty()) {
                    paint.textSize = 24 * density; paint.color = android.graphics.Color.rgb(126, 148, 149)
                    text(if (i.zone == PlayZone.STACK) "▱" else "⊘", b.x + 10, b.y + 36, 1)
                    paint.textSize = 11 * density; paint.color = android.graphics.Color.DKGRAY
                } else if (i.zone != PlayZone.STACK) {
                    for (peek in minOf(2, i.cards.size - 1) downTo 0) {
                        val d = peek * 4 * scene.camera.zoom
                        val peekColor = i.peekOwners.getOrNull(peek)?.let { if (it == viewer) OwnTableColor else OpponentTableColor } ?: color
                        val x = b.x + 8 * scene.camera.zoom - d; val y = b.y + 8 * scene.camera.zoom - d
                        if (peek == 0) cardFace(i, x, y, peekColor, i.note) else {
                            drawRoundRect(if (i.covered) Color(0xff506b61) else Color.White, xy(x, y), size(scene.cardWidth, scene.cardHeight), CornerRadius(2 * density))
                            drawRoundRect(peekColor, xy(x, y), size(scene.cardWidth, scene.cardHeight), CornerRadius(2 * density), style = Stroke(density))
                        }
                    }
                    if (i.covered) {
                        paint.color = android.graphics.Color.WHITE; paint.textSize = 17 * scene.camera.zoom * density
                        text("✧", b.x + 16 * scene.camera.zoom, b.y + 34 * scene.camera.zoom, 1)
                        paint.color = android.graphics.Color.DKGRAY; paint.textSize = 11 * density
                    }
                }
            }
            TableItemKind.CARD -> {
                // Rotate the whole face, not just its outline. Actual dimensions remain common across zones.
                rotate(if (i.tapped) 90f else 0f, xy(b.center.x, b.center.y)) {
                    val x = b.center.x - scene.cardWidth / 2; val y = b.center.y - scene.cardHeight / 2
                    cardFace(i, x, y, color, i.value)
                    paint.textSize = 7 * scene.camera.zoom * density
                    if (i.note.isNotEmpty()) fittedText(i.note, x + 3, y + 20, scene.cardWidth - 6)
                     i.counters.entries.take(6).forEachIndexed { n, entry ->
                         drawCircle(counterColor(entry.key), 5 * density, xy(x + 6 + n * 7, y + scene.cardHeight - 18))
                         text(entry.value, x + 3 + n * 7, y + scene.cardHeight - 16, 3)
                      }
                     paint.textSize = 11 * density
                 }
            }
        }
        if (i.sourceId != null && i.sourceId in relatedSources) drawRect(Color(0xffbd982f), xy(b.x + 3, b.y + 3),
            size((b.width - 6).coerceAtLeast(0f), (b.height - 6).coerceAtLeast(0f)), style = Stroke(2 * density))
        if (i.cardId in selection.ids) drawRect(Color(0xff397ccc), xy(b.x, b.y), size(b.width, b.height), style = Stroke(3 * density))
        if (i.id == preview.pressedId) drawRect(Color(0xffe7ab24), xy(b.x, b.y), size(b.width, b.height), style = Stroke(2 * density))
        } finally { drawContext.canvas.restore() }
    }
    // Badge bounds belong to the pile, not its top card's face. Draw above Stack projections too.
    for (i in scene.items.filter { it.kind == TableItemKind.PILE }) {
        val b = i.bounds
        val port = scene.viewports.single { it.id == i.viewport }.bounds
        if (i.visible.isNotEmpty()) clipRect(port.x * density, port.y * density, port.right * density, port.bottom * density) {
            drawCircle(Color(0xff345451), 10 * density, xy(b.x + 10, b.y + 10))
            drawCircle(Color.White, 10 * density, xy(b.x + 10, b.y + 10), style = Stroke(density))
            paint.color = android.graphics.Color.WHITE; paint.isFakeBoldText = true
            text(i.value, b.x + 10 - paint.measureText(i.value) / density / 2, b.y + 14, 5)
            paint.color = android.graphics.Color.DKGRAY; paint.isFakeBoldText = false
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

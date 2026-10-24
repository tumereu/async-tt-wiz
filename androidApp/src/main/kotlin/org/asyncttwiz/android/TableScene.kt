package org.asyncttwiz.android

import android.graphics.Paint
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.layout
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
    PrefetchGameArtwork(play.definitions)
    val layoutState: MutableState<TableLayoutState> = toolkit?.layoutState ?: remember(play.id, accountId) { mutableStateOf(TableLayoutState()) }
    val selectionState: MutableState<TableSelection> = toolkit?.selectionState ?: remember(play.id, accountId) { mutableStateOf(TableSelection()) }
    var layout by layoutState
    var selection by selectionState
    var actions by remember(play.id, accountId) { mutableStateOf<TableIntent.Actions?>(null) }
    var localStackBubble by remember(play.id, accountId) { mutableStateOf<String?>(null) }
    var combatBubbleId by remember(play.id, accountId) { mutableStateOf<String?>(null) }
    val inspector = remember(play.id, accountId) { CardInspectionController() }
    var inspectedCard by remember(play.id, accountId) { mutableStateOf<PlayCard?>(null) }
    var peek by remember(play.id, accountId) { mutableStateOf<ManualInspection?>(null) }
    val peekScroll = rememberScrollState()
    BackHandler(enabled = actions != null || localStackBubble != null || combatBubbleId != null || inspectedCard != null && inspector.state != null || peek != null) {
        when {
            inspector.state != null -> { inspector.close(); inspectedCard = null }
            peek != null -> peek = null
            actions != null -> actions = null
            else -> { localStackBubble = null; combatBubbleId = null }
        }
    }
    var screenOrigin by remember { mutableStateOf(Offset.Zero) }
    val currentPlay by rememberUpdatedState(play)
    val callback by rememberUpdatedState(onIntent)
    val writable = connected && onIntent != null
    val currentWritable by rememberUpdatedState(writable)
    val currentSelection by rememberUpdatedState(selection)
    val currentRuntime by rememberUpdatedState(runtime)
    var presentation by remember(play.id, accountId) { mutableStateOf<(() -> TableScene)?>(null) }
    var pressedPing by remember(play.id, accountId) { mutableStateOf<EventPingIntent?>(null) }
    val density = LocalDensity.current.density
    val coordinator = remember(play.id, accountId, density) {
        lateinit var owner: TableGestureCoordinator
        var beforeSelection = selection
        owner = TableGestureCoordinator({ intent ->
            when (intent) {
                TableIntent.DismissTapUi -> { actions = null; toolkit?.dismissTapUi(); localStackBubble = null; combatBubbleId = null }
                is TableIntent.CombatBubbles -> {
                    combatBubbleId = intent.occurrenceId
                    if (intent.occurrenceId != null) { actions = null; toolkit?.dismissTapUi(); localStackBubble = null }
                }
                is TableIntent.Peek -> {
                    if (intent.cardId != null) peekScroll.dispatchRawDelta(-peekScroll.value.toFloat())
                    if (toolkit != null) toolkit.peek(intent.cardId)
                    else peek = intent.cardId?.let { id ->
                        val state = currentPlay
                        CombatStackRules.inspection(state.board, id)?.let { card -> ManualInspection(state.gameId, state.id, id,
                            ManualDisclosure.permittedFaces(state, card, accountId, inspecting = true).map { it.faceId },
                            stackEntryId = id.takeIf { it in CombatStackRules.stack(state.board).cards }) }
                    }
                }
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
        }, density = density, singleTapIsLocal = {
            (toolkit == null || it.zone != PlayZone.LIBRARY) && toolkit?.tokens?.pickingLink != true
        }, onPeekScroll = { peekScroll.dispatchRawDelta(it * density) })
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
    val relay = remember(coordinator) { TableTapRelay(
        accepts = { p, t -> coordinator.replacesTap(coordinator.pointFromPixels(p.x - screenOrigin.x, p.y - screenOrigin.y), t) },
        down = { p, t -> coordinator.down(coordinator.pointFromPixels(p.x - screenOrigin.x, p.y - screenOrigin.y), t) },
        up = { p, t ->
            coordinator.up(coordinator.pointFromPixels(p.x - screenOrigin.x, p.y - screenOrigin.y), t)
            pointerTime = t; clockEpoch++; preview = coordinator.preview; pressedPing = null
        },
        cancel = { coordinator.cancel(); clockEpoch++; preview = coordinator.preview; pressedPing = null },
        dismiss = { actions = null; toolkit?.dismissTapUi(); localStackBubble = null; combatBubbleId = null },
        ringDown = { p, t, card ->
            val point = coordinator.pointFromPixels(p.x - screenOrigin.x, p.y - screenOrigin.y)
            val state = currentPlay
            val reference = currentRuntime.pingReference ?: EventReference(state.id, state.currentNodeId)
            pressedPing = presentation?.invoke()?.let { TableAnnotationGeometry(it, game.inviter.accountId).capture(state, reference, point) }
            coordinator.down(point, t, card)
            pointerTime = t; clockEpoch++; preview = coordinator.preview
        },
        move = { p, t ->
            coordinator.move(coordinator.pointFromPixels(p.x - screenOrigin.x, p.y - screenOrigin.y), t)
            pointerTime = t; clockEpoch++; preview = coordinator.preview
        },
        transform = { pan, scale, center ->
            coordinator.transform(coordinator.pointFromPixels(pan.x, pan.y), scale,
                coordinator.pointFromPixels(center.x - screenOrigin.x, center.y - screenOrigin.y))
            preview = coordinator.preview
        }) }
    CompositionLocalProvider(LocalTableTapRelay provides relay) {
    BoxWithConstraints(modifier.fillMaxWidth().onGloballyPositioned { screenOrigin = it.localToScreen(Offset.Zero) }.tableRingRelay(relay)
        .background(Color(0xfff5f8f7)).pointerInput(coordinator) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            val point = coordinator.pointFromPixels(down.position.x, down.position.y)
            val presented = presentation?.invoke()
            val bubble = presented?.stackBubbles
            if (bubble != null && bubble.controls.none { it.bounds.contains(point) }) {
                toolkit?.showStackBubbles(null); localStackBubble = null
            }
            if (presented?.combatBubbles?.controls?.none { it.bounds.contains(point) } == true) combatBubbleId = null
        }
    }) {
        val compactHeader = maxWidth < 400.dp
        val toolbarWidth = if (compactHeader) 240f else TablePresentation.TOOLBAR
        val bandTarget = maxOf(12f, CombatStackPresentation.combat(play.board, 28f, 40f).height + 4f)
        val bandHeight by animateFloatAsState(bandTarget, tween(180), label = "Combat band")
        var previousBand by remember { mutableFloatStateOf(bandHeight) }
        val targetScene = remember(game, play, accountId, maxWidth, maxHeight, landscape, layout, placements, toolbarWidth, runtime.reviewing, bandHeight) {
            TablePresentation.scene(game, play, accountId, maxWidth.value.coerceAtLeast(1f),
                maxHeight.value.coerceAtLeast(1f), landscape, layout, placements, toolbarWidth, runtime.reviewing, bandHeight)
        }
        var previousLayout by remember { mutableStateOf(layout) }
        var previousSize by remember { mutableStateOf(maxWidth to maxHeight) }
        val navigation = previousLayout != layout || previousSize != (maxWidth to maxHeight) || previousBand != bandHeight
        // One interpolated scene feeds Canvas, hit testing AND semantic nodes. No destination-only overlays.
        val animation = remember(play.id, accountId) { HistorySceneAnimation(targetScene) }
        val progress = remember(animation) { androidx.compose.animation.core.Animatable(1f) }
        var animationRevision by remember(animation) { mutableIntStateOf(0) }
        SideEffect { animation.updateMetadata(targetScene) }
        LaunchedEffect(animation, targetScene.animationTargets, play.currentNodeId, runtime.sceneVersion) {
            val settled = runtime.onSceneSettled
            if (animation.retarget(targetScene, progress.value)) {
                progress.snapTo(0f)
                animationRevision++
                if (navigation) progress.snapTo(1f) else progress.animateTo(1f, tween(animation.durationMillis, easing = androidx.compose.animation.core.LinearEasing))
            } else if (progress.value < 1f) {
                progress.animateTo(1f, tween(animation.durationMillis, easing = androidx.compose.animation.core.LinearEasing))
            }
            animationRevision++
            withFrameNanos { }
            settled?.invoke(play.currentNodeId)
        }
        val bubbleId = if (runtime.reviewing) null else toolkit?.stackBubbleEntry ?: localStackBubble
        // Only draw/layout/semantics (and the small open-overlay host) observe the clock. Sampling
        // and clipping are shared once per frame, not repeated for every accessibility child.
        val rendered = remember(animation, targetScene, bubbleId, combatBubbleId, runtime.reviewing, play, maxWidth, maxHeight) { derivedStateOf {
            animationRevision
            val frame = animation.frame(progress.value, targetScene)
            val base = TablePresentation.clip(targetScene.copy(items = frame.filter { it.alpha > 0f }
                .map { it.item.copy(bounds = it.bounds) }), maxWidth.value, maxHeight.value)
            val combatCard = base.items.find { it.cardId == combatBubbleId && it.viewport == "combat" && it.visible.isNotEmpty() }
            val combatBubble = if (runtime.reviewing || combatCard == null ||
                CombatStackRules.occurrences(play.board.combat).none { it.id == combatBubbleId }) null else
                StackBubbleGeometry.combat(play.board, requireNotNull(combatBubbleId), combatCard.bounds, maxWidth.value, maxHeight.value)
            TableRenderFrame(base.copy(stackBubbles = TablePresentation.stackBubbles(base, play, bubbleId, maxWidth.value, maxHeight.value),
                combatBubbles = combatBubble),
                frame.associate { it.item.id to it.alpha })
        } }
        val scene = targetScene
        val semanticItems = remember(animation, targetScene, animationRevision) {
            (animation.frame(0f, targetScene).map { it.item } + targetScene.items).distinctBy { it.id }
        }
        LaunchedEffect(toolkit, bubbleId, play) {
            // The rendered history/animation boundary can briefly lag the session prediction during
            // acceptance. Validate personal focus against the session, not that older rendered board.
            if (toolkit != null) toolkit.refreshStackBubbles()
            else if (bubbleId != null && bubbleId != CombatStackRules.stack(play.board).cards.firstOrNull()) localStackBubble = null
        }
        LaunchedEffect(play, runtime.reviewing) {
            if (runtime.reviewing || CombatStackRules.occurrences(play.board.combat).none { it.id == combatBubbleId }) combatBubbleId = null
        }
        val annotations = TableAnnotationGeometry(scene, game.inviter.accountId)
        val artwork = tableArtwork(scene.copy(items = semanticItems))
        val drawCache = remember(density, targetScene) { TableDrawCache(density) }
        val pingReference = runtime.pingReference ?: EventReference(play.id, play.currentNodeId)
        val activePings = play.currentGroup?.id?.let { EventAnnotations.activePings(play.annotations, it) }.orEmpty()
        var visitedGroup by remember(play.id, accountId) { mutableStateOf(play.currentGroup?.id) }
        SideEffect { previousLayout = layout; previousSize = maxWidth to maxHeight; previousBand = bandHeight }
        SideEffect {
            presentation = { rendered.value.scene }
            var retained = selection.atStep(TurnPosition.of(play.board.turn)).retain((play.board.cards.map { it.id } + play.board.stackEffects.map { it.id } +
                CombatStackRules.occurrences(play.board.combat).map { it.id }).toSet())
            if (visitedGroup != play.currentGroup?.id && retained.mode == TableMode.PING) retained = retained.copy(mode = TableMode.NORMAL)
            visitedGroup = play.currentGroup?.id
            if (retained != selection) selection = retained
            coordinator.update(scene, retained, writable || (retained.mode == TableMode.PING &&
                runtime.ping != null && runtime.controlsConnected == true), sceneSource = { rendered.value.scene })
        }
        Box(Modifier.fillMaxSize().semantics {
            contentDescription = "Shared table"
            stateDescription = "${selection.ids.size} selected, ${selection.mode.name.lowercase()} mode"
            customActions = listOf(
                CustomAccessibilityAction("Zoom in") { coordinator.transform(TablePoint(0f, 0f), 1.2f, scene.fields.last().bounds.center); coordinator.cancel(); clockEpoch++; true },
                CustomAccessibilityAction("Zoom out") { coordinator.transform(TablePoint(0f, 0f), 1 / 1.2f, scene.fields.last().bounds.center); coordinator.cancel(); clockEpoch++; true },
                CustomAccessibilityAction("Pan left") { coordinator.panBy(-84f, 0f); true },
                CustomAccessibilityAction("Pan right") { coordinator.panBy(84f, 0f); true },
                CustomAccessibilityAction("Pan up") { coordinator.panBy(0f, -84f); true },
                CustomAccessibilityAction("Pan down") { coordinator.panBy(0f, 84f); true },
                 CustomAccessibilityAction("Fit table") { scene.world?.fit()?.let { layout = layout.copy(camera = it) }; true },
                CustomAccessibilityAction("Clear selection") { selection = selection.copy(ids = emptySet()); true }
            ) + scene.viewports.filter { it.maxScrollY > 0 }.flatMap { p -> listOf(
                CustomAccessibilityAction("Scroll docks up") { layout = layout.copy(scrollY = layout.scrollY + (p.id to (p.scrollY - 68f).coerceAtLeast(0f))); true },
                CustomAccessibilityAction("Scroll docks down") { layout = layout.copy(scrollY = layout.scrollY + (p.id to (p.scrollY + 68f).coerceAtMost(p.maxScrollY))); true }
            ) }
        }.pointerInput(coordinator) {
            try {
                awaitEachGesture {
                    val first = awaitFirstDown()
                    val point = coordinator.pointFromPixels(first.position.x, first.position.y)
                    val state = currentPlay
                    val reference = currentRuntime.pingReference ?: EventReference(state.id, state.currentNodeId)
                    pressedPing = presentation?.invoke()?.let { TableAnnotationGeometry(it, game.inviter.accountId).capture(state, reference, point) }
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
            } finally { coordinator.cancel(); preview = coordinator.preview; pressedPing = null }
        }) {
            Canvas(Modifier.fillMaxSize()) {
                val frame = rendered.value
                drawTable(frame.scene, accountId, selection, preview, play.board.links, frame.alphas, activePings,
                    play.board.projectionLinks, TableAnnotationGeometry(frame.scene, game.inviter.accountId), artwork, drawCache)
            }
            preview.delta?.let { delta ->
                scene.items.firstOrNull { it.id == preview.pressedId }?.let { item ->
                    val anchor = item.visible.maxByOrNull { it.width * it.height } ?: item.bounds
                    Box(Modifier.offset(anchor.x.dp, anchor.y.dp).size(anchor.width.dp, anchor.height.dp)) {
                        HoldTooltip(valueDragLabel(item.value, delta.toLong()))
                    }
                }
            }
            for (field in scene.fields.filter { it.bounds.width > 0 && it.bounds.height > 0 }) Box(Modifier.offset(field.bounds.x.dp, field.bounds.y.dp)
                .size(field.bounds.width.dp, field.bounds.height.dp).semantics {
                    val player = if (field.owner == game.inviter.accountId) game.inviter else game.recipient
                    contentDescription = "${player.username} Battlefield, ${play.board.containers.single { it.id == field.containerId }.cards.size} cards"
                    stateDescription = if (field.holdsAttention) "${player.username}'s move" else "Waiting for move"
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
                Box(Modifier.tableFrameBounds {
                    val p = TableAnnotationGeometry(rendered.value.scene, game.inviter.accountId).endpoint(ping.target) ?: point
                    TableBounds(p.x - 10, p.y - 10, 20f, 20f)
                }.semantics {
                    contentDescription = "Ping ${ping.to!!.ordinal}, ${ping.target.kind.name.lowercase()}, author ${ping.recording.author}"
                    if (runtime.ping != null && runtime.controlsConnected == true) onClick("Unping") {
                        currentRuntime.controlsConnected == true && currentRuntime.ping?.invoke(captured) == true
                    }
                })
            }
            // Semantics-only children do NOT install a second pointer detector or expanded hit targets.
            // Each exposed fragment has exactly the geometry used to draw/hit-test that fragment.
            for (item in semanticItems) {
                if (item.kind == TableItemKind.HAND && item.cards.isNotEmpty()) continue
                key(item.id) {
                    Box(Modifier.tableFrameBounds {
                        rendered.value.items[item.id]?.let { current ->
                            if (current.zone == PlayZone.STACK && current.kind == TableItemKind.PILE) current.stackBadge
                            else current.visible.maxByOrNull { it.width * it.height }
                        }
                    }.semantics {
                        contentDescription = item.label
                         if (item.peekOwners.isNotEmpty()) stateDescription = "Visible card owners: " + item.peekOwners.joinToString { owner ->
                            if (owner == game.inviter.accountId) game.inviter.username else game.recipient.username
                        }
                          selected = item.cardId in (preview.rectangle?.let(rendered.value.scene::rectangle) ?: selection.ids)
                         if (item.sourceId != null && scene.items.any { it.cardId != item.cardId && it.sourceId == item.sourceId })
                             stateDescription = "Related source and projections share this source identity; selection is independent"
                        onClick("Actions") { coordinator.actions(item); true }
                        val commands = mutableListOf<CustomAccessibilityAction>()
                        if (item.cardId != null) {
                            commands += CustomAccessibilityAction("Inspect") { coordinator.inspect(item); true }
                            commands += CustomAccessibilityAction("Select or deselect") { coordinator.select(item); true }
                            if (writable && scene.canDrag(item)) {
                                if (item.zone != PlayZone.STACK) {
                                    commands += CustomAccessibilityAction("Tap selected scope") { coordinator.rotate(item, true); true }
                                    commands += CustomAccessibilityAction("Untap selected scope") { coordinator.rotate(item, false); true }
                                }
                                for ((label, destination) in libraryDestinations(play.board, sourceIds = listOf(item.cardId))) {
                                    val zone = play.board.containers.single { it.id == destination.containerId }.zone
                                    val bound = destination.position?.let { TableDestination.Grid(destination.containerId, it.x, it.y, ownerOnly = true) }
                                        ?: TableDestination.Container(destination.containerId, zone, destination.insertion == MoveInsertion.BOTTOM)
                                    commands += CustomAccessibilityAction(label) { coordinator.accessibleMove(item, bound); true }
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
                    StackMarkChip(index, Modifier.offset {
                        val b = rendered.value.items[item.id]?.bounds ?: item.bounds
                        androidx.compose.ui.unit.IntOffset((b.right - 17).dp.roundToPx(), b.y.dp.roundToPx())
                    }
                        .semantics { contentDescription = "Stack text mark ${index + 1}" })
                }
            }
            if (bubbleId != null) AnimatedStackBubbleOverlay(rendered, play, accountId, preview, writable) { intent ->
                if (intent is TableIntent.StackDestination) { toolkit?.showStackBubbles(null); localStackBubble = null }
                callback?.invoke(intent)
            }
            if (combatBubbleId != null) AnimatedCombatBubbleOverlay(rendered, accountId, preview, writable) { intent, combatCard ->
                if (intent is TableIntent.Ping) {
                    val target = tablePingTarget(play, combatCard)
                    if (target != null && currentRuntime.controlsConnected == true)
                        currentRuntime.ping?.invoke(tablePingIntent(currentPlay, pingReference, target))
                } else if (currentWritable) callback?.invoke(intent)
            }
        }
        if (runtime.reviewing) scene.world?.viewport?.let { board ->
            // Purely visual siblings: no pointer handlers, so review gestures and chrome stay usable.
            Box(Modifier.offset(board.x.dp, board.y.dp).size(board.width.dp, board.height.dp)
                .background(Color.Black.copy(alpha = .12f)), contentAlignment = Alignment.Center) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    CompositionLocalProvider(LocalContentColor provides Color(0xff253d44).copy(alpha = .38f)) {
                        TableChromeIcon("history")
                        Text("Timeline mode", style = MaterialTheme.typography.titleMedium, color = LocalContentColor.current)
                    }
                }
            }
        }
        TableChromeHeader(game, play, accountId, landscape, runtime, toolkit, compactHeader)
        if (runtime.reviewing) Box(Modifier.fillMaxWidth().height(48.dp).background(Color.Black.copy(alpha = .14f)))
        if (runtime.reviewing) runtime.reviewPanel?.invoke()
        if (toolkit != null) {
            val endingColor = if (play.board.turn.accountId == accountId) OwnTableColor else OpponentTableColor
            if (!runtime.reviewing) QuickActionStrip(play, toolkitQuickContext(play, selection, toolkit.explicitStack, accountId), toolkit.table, endingColor,
                Modifier.align(Alignment.BottomEnd), perform = toolkit::perform)
            if (!runtime.reviewing) runtime.undo?.let { undo -> ActionSquare("Undo", "↶", runtime.controlsConnected ?: connected,
                Modifier.align(Alignment.BottomStart).padding(start = 10.dp, bottom = 4.dp), holdName = if (runtime.redo != null) "Redo" else null,
                onHold = runtime.redo?.let { redo -> { redo() } }, icon = { TableChromeIcon("undo") }, onClick = { undo() }) }
        }
        FloatingTransientError(runtime.errorMessage ?: toolkit?.error,
            { if (runtime.errorMessage != null) runtime.dismissError?.invoke() else toolkit?.dismissError() },
            Modifier.align(Alignment.BottomCenter).padding(bottom = 64.dp))
        // Screen-fixed personal control remains reachable; it does not compete over card hit regions.
        if (!runtime.reviewing) Box(Modifier.offset(
            ((scene.world?.viewport?.right ?: maxWidth.value) - 56f).dp,
            ((scene.world?.viewport?.bottom ?: (maxHeight.value - 56f)) - 56f).dp)) {
            ActionSquare("Interaction mode", "", true,
                modifier = Modifier.size(48.dp).background(Color.White).border(1.dp, OwnTableColor).semantics {
                    contentDescription = "Interaction mode"; stateDescription = "${selection.mode.name.lowercase()}, ${selection.ids.size} selected"
                }, ink = OwnTableColor, icon = { TableChromeIcon(when (selection.mode) { TableMode.NORMAL -> "mode"; TableMode.SELECT -> "select"; TableMode.PING -> "ping" }) },
                onClick = { coordinator.cancel(); selection = selection.cycleMode(runtime.ping != null && runtime.controlsConnected == true) })
        }
        AnimatedCardRingAnchors(rendered, actions != null || toolkit?.capture != null) {
        if (toolkit != null) TableToolkitScreen(toolkit, play, game, landscape, selection, select = { selection = selection.toggle(it) }, accepted,
            tokenCatalog = runtime.tokenCatalog, peekScroll = peekScroll)
        actions?.let { captured ->
            val item = scene.items.firstOrNull { it.id == captured.itemId } ?: captured.scope?.contextId?.let { id ->
                play.board.cards.singleOrNull { it.id == id }?.let { card ->
                    TableItem("card/$id", TableBounds(0f, 0f, 0f, 0f), "Card Actions", "", card.owner,
                        play.board.containers.single { it.id == card.containerId }.zone, kind = TableItemKind.CARD, cardId = id)
                }
            }
            if (item == null) SideEffect { actions = null }
            else if (item.zone == PlayZone.STACK) ManageStackDialog(play, accountId, writable, { actions = null },
                perform = { if (currentWritable) callback?.invoke(it) }, cardActions = { id ->
                    val scope = TableScope(id, listOf(id))
                    actions = TableIntent.Actions("card/$id", scope, BasicTableController.sources(play, listOf(id)))
                })
            else TableActionsScreen(game, play, accountId,
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
    }
    CardInspection(inspector, token = inspectedCard?.token == true,
        owner = inspectedCard?.let { if (it.owner == accountId) OwnTableColor else OpponentTableColor })
    peek?.let { GameplayCardInspection(play, accountId, it, { peek = null }, peek = true, peekScroll = peekScroll) }
    }
}

private class TableRenderFrame(val scene: TableScene, val alphas: Map<String, Float>) {
    val items = scene.items.associateBy { it.id }
}

/** The animated accessibility rectangle participates in layout, without recomposing its node. */
private fun Modifier.tableFrameBounds(bounds: () -> TableBounds?): Modifier = layout { measurable, _ ->
    val b = bounds() ?: TableBounds(0f, 0f, 0f, 0f)
    val width = b.width.dp.roundToPx().coerceAtLeast(0)
    val height = b.height.dp.roundToPx().coerceAtLeast(0)
    val child = measurable.measure(androidx.compose.ui.unit.Constraints.fixed(width, height))
    layout(width, height) { child.place(b.x.dp.roundToPx(), b.y.dp.roundToPx()) }
}

@Composable
private fun AnimatedStackBubbleOverlay(frame: State<TableRenderFrame>, play: PlayState, viewer: String,
    preview: TableGesturePreview, enabled: Boolean, perform: (TableIntent) -> Unit) {
    val scene = frame.value.scene
    val bubble = scene.stackBubbles ?: return
    val card = scene.items.find { it.cardId == bubble.entryId && it.zone == PlayZone.STACK }
    StackBubbleOverlay(bubble, stackTextOptions(play, bubble.entryId, viewer),
        if (card?.owner == viewer) OwnTableColor else OpponentTableColor, preview, enabled, perform)
}

@Composable
private fun AnimatedCombatBubbleOverlay(frame: State<TableRenderFrame>, viewer: String,
    preview: TableGesturePreview, enabled: Boolean, perform: (TableIntent, TableItem) -> Unit) {
    val scene = frame.value.scene
    val bubble = scene.combatBubbles ?: return
    val card = scene.items.find { it.cardId == bubble.entryId && it.viewport == "combat" } ?: return
    StackBubbleOverlay(bubble, emptyList(),
        if (card.owner == viewer) OwnTableColor else OpponentTableColor, preview, enabled) { perform(it, card) }
}

@Composable
private fun AnimatedCardRingAnchors(frame: State<TableRenderFrame>, active: Boolean, content: @Composable () -> Unit) {
    val anchors = if (!active) emptyMap() else frame.value.scene.items.filter { it.visible.isNotEmpty() }.mapNotNull { item ->
        item.cardId?.let { id -> id to if (item.visible.any { it.contains(item.bounds.center) }) item.bounds
            else item.visible.maxBy { it.width * it.height } }
    }.toMap()
    CompositionLocalProvider(LocalCardRingAnchors provides anchors, content = content)
}

/** Compact game chrome shares the surface, so landscape hands occupy the same top/bottom rails. */
@Composable
private fun BoxScope.TableChromeHeader(game: GameSummary, play: PlayState, viewer: String, landscape: Boolean,
    runtime: TableRuntimeHooks, toolkit: TableToolkitController?, compact: Boolean) {
    val turnColor = if (play.board.turn.accountId == viewer) OwnTableColor else OpponentTableColor
    val attention = attentionPresentation(game, play, viewer, runtime.reviewing, toolkit?.enabled() == true)
    Row(Modifier.align(Alignment.TopStart).height(48.dp)
        .fillMaxWidth(if (landscape) .366f else 1f), verticalAlignment = Alignment.CenterVertically) {
        ActionSquare("Open navigation", "", runtime.navigation != null, Modifier.width(48.dp), icon = { TableChromeIcon("menu") }, onClick = { runtime.navigation?.invoke() })
        Column(Modifier.weight(1f).padding(horizontal = 4.dp)) {
            Text(game.gameName, fontSize = 11.sp, lineHeight = 12.sp, fontWeight = FontWeight.Bold,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            ActionSquare(attention.label, "", attention.enabled,
                Modifier.fillMaxWidth().height(30.dp).clip(androidx.compose.foundation.shape.RoundedCornerShape(15.dp)),
                fill = attention.color.copy(alpha = .12f), ink = attention.color,
                icon = {
                    Row(Modifier.padding(horizontal = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (attention.own) Text("Your move", fontSize = 11.sp, maxLines = 1)
                        else {
                            Text(attention.name, Modifier.weight(1f, fill = false), fontSize = 11.sp,
                                maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("'s move", fontSize = 11.sp, maxLines = 1)
                        }
                    }
                }, onClick = { if (!runtime.reviewing) toolkit?.submit(PlayAction.PassAttention) })
        }
        if (!landscape) Spacer(Modifier.width(if (compact) 240.dp else TablePresentation.TOOLBAR.dp))
    }
    val buttonWidth = 48f
    Row(Modifier.align(Alignment.TopEnd).height(48.dp).padding(end = if (compact) 0.dp else 8.dp),
        horizontalArrangement = Arrangement.spacedBy(if (compact) 0.dp else 4.dp), verticalAlignment = Alignment.CenterVertically) {
        val turnPlayer = if (play.board.turn.accountId == game.inviter.accountId) game.inviter else game.recipient
        ActionSquare("Turn ${play.board.turn.number}, ${turnPlayer.username}'s turn, ${turnStepName(play.board.turn)}. Choose phase/step",
            "", toolkit != null && !runtime.reviewing,
            Modifier.width(buttonWidth.dp).border(2.dp, turnColor), fill = phaseColor(play.board.turn.phase), ink = Color(0xff25333b),
            icon = { TurnStepIcon(play.board.turn) },
            onClick = { toolkit?.phasePicker() })
        if (runtime.continuationArmed) {
            ActionSquare("Cancel continuation", "", runtime.cancelContinuation != null,
                Modifier.size(48.dp).border(2.dp, OwnTableColor), fill = Color(0xffedf3ef), ink = OwnTableColor,
                icon = { TableChromeIcon("fork") }, onClick = { runtime.cancelContinuation?.invoke() })
        } else if (runtime.reviewing) {
            ActionSquare("Continue playing from here", "", runtime.continueHere != null, Modifier.width(buttonWidth.dp).background(Color.White),
                icon = { TableChromeIcon("fork") }, onClick = { runtime.continueHere?.invoke() })
            ActionSquare("Return to current", "", runtime.returnCurrent != null, Modifier.width(buttonWidth.dp).background(Color.White), icon = { TableChromeIcon("current") }, onClick = { runtime.returnCurrent?.invoke() })
        } else {
            ActionSquare("History", "", runtime.history != null, Modifier.width(buttonWidth.dp).background(Color.White),
                icon = { TableChromeIcon("history") }, onClick = { runtime.history?.invoke() })
        }
        Box {
            ActionSquare("Chat, ${runtime.chatUnread} unread messages", "", runtime.chat != null, Modifier.width(buttonWidth.dp).background(Color.White), icon = { TableChromeIcon("chat") }, onClick = { runtime.chat?.invoke() })
            if (runtime.chatUnread > 0) Box(Modifier.align(Alignment.TopEnd).padding(6.dp).size(6.dp).background(OpponentTableColor, androidx.compose.foundation.shape.CircleShape))
        }
        if (!runtime.reviewing) ActionSquare("Table actions", "", toolkit != null,
            Modifier.width(buttonWidth.dp).background(Color.White), icon = { TableChromeIcon("grid") },
            onClick = { toolkit?.tableMenu() })
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

/** Uniform scale and centered clipping, never independent width/height stretching. */
private fun DrawScope.drawTableArtwork(image: ImageBitmap, bounds: TableBounds) {
    val left = bounds.x * density; val top = bounds.y * density
    val width = bounds.width * density; val height = bounds.height * density
    val factor = maxOf(width / image.width, height / image.height)
    clipRect(left, top, left + width, top + height) {
        translate(left + (width - image.width * factor) / 2, top + (height - image.height * factor) / 2) {
            scale(factor, factor, Offset.Zero) { drawImage(image) }
        }
    }
}

/** Scoped to an endpoint/density, so labels and native resources are bounded and reused in flight. */
private class TableDrawCache(density: Float) {
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.DKGRAY; textSize = 11 * density }
    val layerPaint = androidx.compose.ui.graphics.Paint()
    private data class TextKey(val text: String, val size: Float, val bold: Boolean)
    private data class FitKey(val text: TextKey, val width: Float)
    private data class FaceKey(val width: Float, val height: Float, val token: Boolean)
    private val measurements = mutableMapOf<TextKey, Float>()
    private val centerOffsets = mutableMapOf<Float, Float>()
    private val labels = mutableMapOf<FitKey, String>()
    private val outlines = mutableMapOf<FaceKey, androidx.compose.ui.graphics.Path>()
    fun measure(value: String): Float = measurements.getOrPut(TextKey(value, paint.textSize, paint.isFakeBoldText)) { paint.measureText(value) }
    fun centerOffset(): Float = centerOffsets.getOrPut(paint.textSize) { paint.fontMetrics.let { (it.ascent + it.descent) / 2 } }
    fun fitted(value: String, width: Float): String = labels.getOrPut(FitKey(TextKey(value, paint.textSize, paint.isFakeBoldText), width)) {
        var label = value
        if (measure(label) > width) {
            while (label.isNotEmpty() && measure("$label…") > width) label = label.dropLast(1)
            label += "…"
        }
        label
    }
    fun outline(width: Float, height: Float, token: Boolean) = outlines.getOrPut(FaceKey(width, height, token)) {
        cardSilhouette(width, height, token, 0f, 0f, width / 14f)
    }
}

private fun DrawScope.drawTable(scene: TableScene, viewer: String, selection: TableSelection, preview: TableGesturePreview,
    links: List<PlayLink> = emptyList(), alphas: Map<String, Float> = emptyMap(), pings: List<RecordedPingChange> = emptyList(),
    projectionLinks: List<ProjectionLink> = emptyList(), annotations: TableAnnotationGeometry, artwork: Map<String, ImageBitmap> = emptyMap(),
    cache: TableDrawCache) {
    val density = density
    fun xy(x: Float, y: Float) = Offset(x * density, y * density)
    fun size(w: Float, h: Float) = Size(w * density, h * density)
    val paint = cache.paint
    fun text(value: String, x: Float, y: Float, max: Int = 16) {
        drawContext.canvas.nativeCanvas.drawText(value.take(max), x * density, y * density, paint)
    }
    fun fittedText(value: String, x: Float, y: Float, width: Float, right: Boolean = false) {
        val label = cache.fitted(value, width * density)
        text(label, if (right) x - cache.measure(label) / density else x, y, label.length)
    }
    fun centeredText(value: String, bounds: TableBounds, maxSize: Float) {
        paint.textSize = maxSize * density
        val available = (bounds.width - 8f).coerceAtLeast(1f) * density
        val measured = cache.measure(value)
        if (measured > available) paint.textSize *= available / measured
        val x = bounds.center.x * density - cache.measure(value) / 2
        val baseline = bounds.center.y * density - cache.centerOffset()
        drawContext.canvas.nativeCanvas.drawText(value, x, baseline, paint)
    }
    fun emptySlot(i: TableItem, color: Color) {
        val z = 1f
        val x = i.bounds.x + 8f * z; val y = i.bounds.y + 8f * z
        val w = 28f; val h = 40f
        val ink = if (i.zone == PlayZone.STACK) Color(0xff795497) else color.copy(alpha = .7f)
        drawRoundRect(ink, xy(x, y), size(w, h), CornerRadius(2 * density), style = Stroke(density))
        val cx = x + w / 2; val cy = y + h / 2
        fun line(a: Float, b: Float, c: Float, d: Float) = drawLine(ink,
            xy(cx + a * z, cy + b * z), xy(cx + c * z, cy + d * z), 1.4f * density * z)
        when (i.zone) {
            PlayZone.GRAVEYARD -> {
                val stone = androidx.compose.ui.graphics.Path().apply {
                    moveTo((cx - 7 * z) * density, (cy + 8 * z) * density)
                    lineTo((cx - 7 * z) * density, (cy - 3 * z) * density)
                    quadraticTo(cx * density, (cy - 13 * z) * density, (cx + 7 * z) * density, (cy - 3 * z) * density)
                    lineTo((cx + 7 * z) * density, (cy + 8 * z) * density); close()
                }
                drawPath(stone, ink, style = Stroke(1.4f * density * z))
                line(0f, -3f, 0f, 5f); line(-3f, 0f, 3f, 0f); line(-10f, 8f, 10f, 8f)
            }
            PlayZone.EXILE -> {
                drawOval(ink, xy(cx - 7 * z, cy - 10 * z), size(14 * z, 20 * z), style = Stroke(1.4f * density * z))
                drawOval(ink, xy(cx - 3 * z, cy - 6 * z), size(6 * z, 12 * z), style = Stroke(density * z))
                line(-10f, -6f, -7f, -4f); line(7f, 4f, 10f, 6f)
            }
            PlayZone.STACK -> {
                for (dy in listOf(-4f, 2f)) {
                    line(-9f, dy, 0f, dy - 5f); line(0f, dy - 5f, 9f, dy)
                    line(9f, dy, 0f, dy + 5f); line(0f, dy + 5f, -9f, dy)
                }
            }
            PlayZone.LIBRARY -> {
                line(-7f, -8f, 5f, -8f); line(-7f, -8f, -7f, 7f)
                line(-7f, 7f, 5f, 7f); line(5f, -8f, 5f, 7f)
                line(-3f, 10f, 8f, 10f); line(8f, -5f, 8f, 10f)
            }
            else -> Unit
        }
    }
    fun cardFace(i: TableItem, x: Float, y: Float, color: Color, title: String) {
        translate(x * density, y * density) {
        val x = 0f; val y = 0f
        val z = if (i.kind == TableItemKind.CARD) i.faceTransform().width / 28f else 1f
        val cardWidth = 28f * z; val cardHeight = 40f * z
        val outline = cache.outline(cardWidth * density, cardHeight * density, i.token)
        drawPath(outline, if (i.covered) Color(0xff506b61) else Color.White)
        val art = i.artwork?.key?.let(artwork::get)
        val mode = tableArtworkMode(i.artwork, art != null)
        if (!i.covered && mode == TableArtworkMode.CUSTOM_CARD) clipPath(outline) {
            drawTableArtwork(requireNotNull(art), TableBounds(x, y, cardWidth, cardHeight))
            if (i.corner.isNotEmpty()) {
                paint.textSize = 7 * z * density
                val badgeWidth = (cache.measure(i.corner) / density + 4 * z).coerceAtMost(cardWidth - 4 * z)
                drawRoundRect(Color.White, xy(x + cardWidth - badgeWidth - 2 * z, y + cardHeight - 11 * z),
                    size(badgeWidth, 10 * z), CornerRadius(z * density))
                fittedText(i.corner, x + cardWidth - 4 * z, y + cardHeight - 3 * z, badgeWidth - 4 * z, right = true)
            }
        }
        drawPath(outline, color,
            style = Stroke(1.5f * density * z, pathEffect = if (i.projection) PathEffect.dashPathEffect(floatArrayOf(5 * density * z, 3 * density * z)) else null))
        if (i.covered) return@translate
        if (mode == TableArtworkMode.CUSTOM_CARD) { paint.textSize = 11 * density; return@translate }
        paint.textSize = 7 * z * density; paint.isFakeBoldText = true
        val titleInset = if (i.token) cardWidth * .22f else 2 * z
        fittedText(title, x + titleInset, y + 8 * z, cardWidth - 2 * titleInset)
        paint.isFakeBoldText = false
        if (mode == TableArtworkMode.ART_CROP) drawTableArtwork(requireNotNull(art),
            TableBounds(x + 2 * z, y + 11 * z, cardWidth - 4 * z, cardHeight - 21 * z))
        else {
            paint.textSize = 17 * z * density
            text(i.typeIcon, x + (cardWidth - cache.measure(i.typeIcon) / density) / 2, y + cardHeight * .7f, i.typeIcon.length)
        }
        paint.textSize = 7 * z * density
        fittedText(i.corner, x + cardWidth - titleInset, y + cardHeight - 3 * z, cardWidth - 2 * titleInset, right = true)
        paint.textSize = 11 * density
        }
    }
    scene.viewports.firstOrNull { it.id == "sidebar" }?.bounds?.let { b ->
        drawRect(Color.White, xy(6f, b.y), size((b.width - 6).coerceAtLeast(0f), b.height))
        drawRect(Color(0xffb9cbc5), xy(6f, b.y), size((b.width - 6).coerceAtLeast(0f), b.height), style = Stroke(density * .7f))
    }
    for (f in scene.fields) {
        val b = f.gridBounds
        val viewport = scene.world?.horizontalBand(b) ?: f.bounds
        clipRect(viewport.x * density, viewport.y * density, viewport.right * density, viewport.bottom * density) {
        val color = if (f.owner == viewer) Color(0xffe2efe6) else Color(0xfff4e8ea)
        drawRect(color, xy(viewport.x, b.y), size(viewport.width, b.height))
        val lands = f.landsBounds(f.owner == viewer, scene.camera)
        drawRect(if (f.owner == viewer) Color(0xffc4dcd0) else Color(0xffe9d1d3),
            xy(viewport.x, lands.y), size(viewport.width, lands.height))
        if (f.holdsAttention) drawRect((if (f.owner == viewer) OwnTableColor else OpponentTableColor).copy(alpha = .12f),
            xy(viewport.x, viewport.y), size(viewport.width, viewport.height))
        }
    }
    scene.world?.let { world -> world.horizontalBand(TableBounds(0f, scene.fields.first().gridBounds.bottom,
        0f, scene.fields.last().gridBounds.y - scene.fields.first().gridBounds.bottom)) }?.let { b ->
        clipRect(b.x * density, b.y * density, b.right * density, b.bottom * density) {
            drawRect(Color(0xfff5f1df), xy(b.x, b.y), size(b.width, b.height))
            for ((from, to) in scene.combatDividers) drawLine(Color.Black.copy(alpha = .22f), xy(from.x, from.y), xy(to.x, to.y), density)
            if (scene.combatDrops.isNotEmpty() && scene.items.none { it.viewport == "combat" }) {
                paint.alpha = 110
                fittedText("Drag cards here for combat", b.x + 8f, b.center.y + 4f, b.width - 16f)
                paint.alpha = 255
            }
        }
    }
    fun endpoint(endpoint: LinkEndpoint): TablePoint? = scene.items.firstOrNull { item ->
        if (endpoint.kind == LinkEndpointKind.CARD) item.cardId == endpoint.id else item.id == "life/${endpoint.id}"
    }?.visibleAnchor
    for (link in links) {
        val a = endpoint(link.first); val b = endpoint(link.second)
        if (a != null && b != null) drawLine(Color(0xff667b89), xy(a.x, a.y), xy(b.x, b.y), 2 * density)
    }
    for (link in projectionLinks) {
        val first = scene.items.firstOrNull { it.cardId == link.first }?.visibleAnchor
        val second = scene.items.firstOrNull { it.cardId == link.second }?.visibleAnchor
        if (first != null && second != null) drawLine(Color(0xff667b89), xy(first.x, first.y), xy(second.x, second.y), 2 * density)
    }
    // HUD surfaces cover world drawings and link lines, including the screen-pinned Stack gutter.
    for (port in scene.viewports.filter { it.hand || it.id.startsWith("dock/") || it.id.startsWith("counters/") }) {
        val b = port.bounds
        drawRect(Color.White, xy(b.x, b.y), size(b.width, b.height))
    }
    for (hand in scene.items.filter { it.kind == TableItemKind.HAND }) {
        val b = hand.bounds
        val border = if (hand.owner == viewer) OwnTableColor else OpponentTableColor
        drawRoundRect(Color.White, xy(b.x, b.y), size(b.width, b.height), CornerRadius(5 * density))
        drawRoundRect(border.copy(alpha = .6f), xy(b.x, b.y), size(b.width, b.height), CornerRadius(5 * density), style = Stroke(density / 2))
        text(hand.value, b.right - 18f, b.center.y + 4f, 4)
    }
    scene.stackRegion?.let { b ->
        val purple = Color(0xff2a1740)
        // Layered translucent contours make a soft shadow in the shared Canvas renderer.
        for (spread in 8 downTo 1) drawRoundRect(purple.copy(alpha = .025f),
            xy(b.x - spread / 2f, b.y + 2f - spread / 2f), size(b.width + spread, b.height + spread),
            CornerRadius(5 * density))
        drawRoundRect(Color(0xffeee3f5), xy(b.x, b.y), size(b.width, b.height), CornerRadius(4 * density))
        drawRoundRect(purple, xy(b.x + 1.5f, b.y + 1.5f), size(b.width - 3f, b.height - 3f),
            CornerRadius(3 * density), style = Stroke(3 * density))
    }
    val highlighted = preview.rectangle?.let(scene::rectangle) ?: selection.ids
    val focused = highlighted + listOfNotNull(preview.pressedId?.let { id -> scene.items.firstOrNull { it.id == id }?.cardId })
    val relatedSources = scene.items.filter { it.cardId in focused }.mapNotNull { it.sourceId }.toSet()
    for (i in scene.items) for (part in i.visible) clipRect(part.x * density, part.y * density, part.right * density, part.bottom * density) {
        val alpha = alphas[i.id] ?: 1f
        // Opaque cards need only the existing clip, not a separate GPU render target per fragment.
        if (alpha < 1f) drawContext.canvas.saveLayer(androidx.compose.ui.geometry.Rect(part.x * density, part.y * density,
            part.right * density, part.bottom * density), cache.layerPaint.apply { this.alpha = alpha })
        try {
        val b = i.bounds
        val color = if (i.owner == null) Color(0xff59656a) else if (i.owner == viewer) OwnTableColor else OpponentTableColor
        when (i.kind) {
             TableItemKind.HAND -> Unit
            TableItemKind.LIFE -> {
                drawRect(color, xy(b.x, b.y), size(b.width, b.height))
                paint.color = android.graphics.Color.WHITE; paint.textSize = 24 * density; paint.isFakeBoldText = true
                centeredText(i.value, b, 24f)
                paint.color = android.graphics.Color.DKGRAY; paint.textSize = 11 * density; paint.isFakeBoldText = false
            }
            TableItemKind.COUNTER -> {
                val marker = when (i.counter) {
                    CounterColor.RED -> Color(0xffb44b51); CounterColor.BLUE -> Color(0xff426f97)
                    CounterColor.GREEN -> Color(0xff3c744f); CounterColor.YELLOW -> Color(0xffc7ab3d)
                    CounterColor.PURPLE -> Color(0xff8c639d); else -> Color(0xff626b6e)
                }
                drawCircle(marker, 14 * density, xy(b.center.x, b.center.y))
                paint.isFakeBoldText = true
                paint.color = if (i.counter == CounterColor.YELLOW) android.graphics.Color.rgb(32, 42, 46) else android.graphics.Color.WHITE
                centeredText(i.value, TableBounds(b.center.x - 14f, b.center.y - 14f, 28f, 28f), 11f)
                paint.color = android.graphics.Color.DKGRAY; paint.textSize = 11 * density; paint.isFakeBoldText = false
            }
            TableItemKind.PILE -> {
                if (i.cards.isEmpty()) {
                    if (i.zone in listOf(PlayZone.LIBRARY, PlayZone.GRAVEYARD, PlayZone.EXILE, PlayZone.STACK)) emptySlot(i, color)
                } else if (i.zone != PlayZone.STACK) {
                    for (peek in minOf(2, i.cards.size - 1) downTo 0) {
                        val d = peek * 4f
                        val peekColor = i.peekOwners.getOrNull(peek)?.let { if (it == viewer) OwnTableColor else OpponentTableColor } ?: color
                        val x = b.x + 8 - d; val y = b.y + 8 - d
                        if (peek == 0) cardFace(i, x, y, peekColor, i.note) else {
                            drawRoundRect(if (i.covered) Color(0xff506b61) else Color.White, xy(x, y), size(28f, 40f), CornerRadius(2 * density))
                            drawRoundRect(peekColor, xy(x, y), size(28f, 40f), CornerRadius(2 * density), style = Stroke(density))
                        }
                    }
                    if (i.covered) {
                        paint.color = android.graphics.Color.WHITE; paint.textSize = 17 * density
                        text("✧", b.x + 16, b.y + 34, 1)
                        paint.color = android.graphics.Color.DKGRAY; paint.textSize = 11 * density
                    }
                }
            }
             TableItemKind.CARD -> {
                   val transform = if (i.viewport == "combat") TableCardTransform(b.width, b.height, 0f) else i.faceTransform()
                  val z = transform.width / 28f
                  val cardWidth = transform.width; val cardHeight = transform.height
                  rotate(transform.rotation, xy(b.center.x, b.center.y)) {
                     val x = b.center.x - cardWidth / 2; val y = b.center.y - cardHeight / 2
                    cardFace(i, x, y, color, i.value)
                     paint.textSize = 7 * z * density
                     if (i.note.isNotEmpty()) fittedText(i.note, x + 3 * z, y + 20 * z, cardWidth - 6 * z)
                     i.counters.entries.take(6).forEachIndexed { n, entry ->
                          drawCircle(counterColor(entry.key), 5 * z * density, xy(x + (6 + n * 7) * z, y + cardHeight - 18 * z))
                          text(entry.value, x + (3 + n * 7) * z, y + cardHeight - 16 * z, 3)
                      }
                     paint.textSize = 11 * density
                 }
            }
        }
        if (i.sourceId != null && i.sourceId in relatedSources) drawRect(Color(0xffbd982f), xy(b.x + 3, b.y + 3),
            size((b.width - 6).coerceAtLeast(0f), (b.height - 6).coerceAtLeast(0f)), style = Stroke(2 * density))
        if (i.cardId in highlighted) drawRect(Color(0xff397ccc), xy(b.x, b.y), size(b.width, b.height), style = Stroke(3 * density))
        if (i.id == preview.pressedId) drawRect(Color(0xffe7ab24), xy(b.x, b.y), size(b.width, b.height), style = Stroke(2 * density))
        } finally { if (alpha < 1f) drawContext.canvas.restore() }
    }
    // Badge bounds belong to the pile, not its top card's face. Draw above Stack projections too.
    for (i in scene.items.filter { it.kind == TableItemKind.PILE }) {
        val b = i.bounds
        val port = scene.viewports.single { it.id == i.viewport }.bounds
        if (i.visible.isNotEmpty()) clipRect(port.x * density, port.y * density, port.right * density, port.bottom * density) {
            drawCircle(Color(0xff345451), 10 * density, xy(b.x + 10, b.y + 10))
            drawCircle(Color.White, 10 * density, xy(b.x + 10, b.y + 10), style = Stroke(density))
            paint.color = android.graphics.Color.WHITE; paint.isFakeBoldText = true
            text(i.value, b.x + 10 - cache.measure(i.value) / density / 2, b.y + 14, 5)
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
    val landing = tableDragLanding(scene, preview)
    landing.combatInsertion?.let { (from, to) -> scene.world?.viewport?.let { port ->
        clipRect(port.x * density, port.y * density, port.right * density, port.bottom * density) {
            drawLine(Color.Black.copy(alpha = .18f), xy(from.x, from.y), xy(to.x, to.y), density)
        }
    } }
    for ((b, insertionX) in listOfNotNull(landing.hand?.let { it to landing.insertionX }) + landing.additionalHands) {
        drawRect(OwnTableColor.copy(alpha = .18f), xy(b.x, b.y), size(b.width, b.height))
        insertionX?.let { x -> drawLine(OwnTableColor, xy(x, b.y + 3), xy(x, b.bottom - 3), 2 * density) }
    }
    for (b in landing.outlines) {
        drawRoundRect(OwnTableColor.copy(alpha = .08f), xy(b.x, b.y), size(b.width, b.height), CornerRadius(2 * density))
        drawRoundRect(OwnTableColor.copy(alpha = .5f), xy(b.x, b.y), size(b.width, b.height), CornerRadius(2 * density),
            style = Stroke(density, pathEffect = PathEffect.dashPathEffect(floatArrayOf(4 * density, 3 * density))))
    }
    preview.point?.let { p ->
        if (preview.scope != null) {
            drawRoundRect(if (preview.destination == null) OpponentTableColor.copy(alpha = .35f) else OwnTableColor.copy(alpha = .35f),
                xy(p.x - scene.cardWidth / 2, p.y - scene.cardHeight / 2), size(scene.cardWidth, scene.cardHeight), CornerRadius(3 * density))
            text("${preview.scope.cardIds.size}", p.x, p.y)
        }
    }
}

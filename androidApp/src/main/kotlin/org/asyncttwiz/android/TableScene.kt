package org.asyncttwiz.android

import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.collectLatest
import org.asyncttwiz.protocol.*

/** Normal production surface. One pointer arbiter, one Actions host, and no authoritative local board. */
@Composable
internal fun TableSurface(game: GameSummary, play: PlayState, accountId: String, landscape: Boolean,
    modifier: Modifier = Modifier, connected: Boolean = false, onIntent: ((TableIntent) -> Boolean)? = null,
    placements: Map<String, TablePlacement> = emptyMap(), toolkit: TableToolkitController? = null,
    accepted: PlayState? = null, runtime: TableRuntimeHooks = TableRuntimeHooks()) {
    PrefetchGameArtwork(play, accountId)
    val layoutState: MutableState<TableLayoutState> = toolkit?.layoutState ?: remember(play.id, accountId) { mutableStateOf(TableLayoutState()) }
    val selectionState: MutableState<TableSelection> = toolkit?.selectionState ?: remember(play.id, accountId) { mutableStateOf(TableSelection()) }
    var layout by layoutState
    var selection by selectionState
    var gestureError by remember(play.id, accountId) { mutableStateOf<String?>(null) }
    val peekScroll = rememberScrollState()
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
            // A pending result blocks game actions only; looking at cards (hold preview, inspect) stays personal and allowed.
            if (currentRuntime.gameActionsBlocked && !gameLockAllows(intent)) return@TableGestureCoordinator
            if (intent !is TableIntent.Camera && intent !is TableIntent.Scroll)
                DebugJank.action(intent::class.simpleName ?: "TableIntent")
            when (intent) {
                TableIntent.DismissTapUi -> {
                    DebugLatency.tap(game.id, "popupClosing")
                    toolkit?.dismissTapUi()
                }
                is TableIntent.Peek -> {
                    if (intent.cardId != null) currentRuntime.pauseForInspection?.invoke()
                    if (intent.cardId != null) peekScroll.dispatchRawDelta(-peekScroll.value.toFloat())
                    toolkit?.peek(intent.cardId)
                }
                is TableIntent.StackBubbles -> toolkit?.showStackBubbles(intent.entryId)
                is TableIntent.SearchPile -> toolkit?.openPublicPile(intent.containerId, toggle = false)
                is TableIntent.Camera -> layout = layout.copy(camera = intent.camera)
                is TableIntent.Scroll -> layout = if (intent.vertical)
                    layout.copy(scrollY = layout.scrollY + (intent.viewportId to intent.offset))
                    else layout.copy(scroll = layout.scroll + (intent.viewportId to intent.offset))
                is TableIntent.Selection -> if (!currentRuntime.reviewing) { beforeSelection = selection; selection = intent.selection }
                is TableIntent.Actions -> {
                    val captured = owner.capturedActions ?: intent
                    if (toolkit != null) {
                        val item = owner.actionItem
                        if (item != null) toolkit.open(captured, item, currentSelection)
                    }
                }
                is TableIntent.Inspect -> toolkit?.inspect(intent.cardId)
                is TableIntent.Ping -> {
                    val captured = pressedPing
                    pressedPing = null
                    if (captured != null && currentRuntime.controlsConnected == true && captured.reference.playId == currentPlay.id)
                        currentRuntime.ping?.invoke(captured)
                }
                else -> {
                    if (intent is TableIntent.StackDestination) toolkit?.showStackBubbles(null)
                    val sent = currentWritable && callback?.invoke(intent) == true
                    if (intent is TableIntent.Rotate && intent.toggle) {
                        gestureError = if (sent) null else "Rotation not queued. Reconnect and try again."
                        DebugLatency.tap(game.id, if (sent) "rotated" else if (!currentWritable) "dropped:sceneNotWritable" else "dropped:controllerRefused",
                            cardId = intent.scope.contextId)
                    }
                    if (!sent && intent is TableIntent.Move) selection = beforeSelection
                }
            }
        }, density = density, onPeekScroll = { peekScroll.dispatchRawDelta(it * density) }, diagnostic = { event ->
            if (event.startsWith("dropped:")) gestureError = "Rotation not queued (${event.substringAfter(':')}). Reconnect and try again."
            DebugLatency.tap(game.id, event, toolkit?.popupItem != null)
        })
        owner
    }
    var preview by remember(coordinator) { mutableStateOf(TableGesturePreview()) }
    var clockEpoch by remember(coordinator) { mutableIntStateOf(0) }
    var pointerTime by remember(coordinator) { mutableLongStateOf(0L) }
    DisposableEffect(coordinator) { onDispose { coordinator.cancel() } }
    LaunchedEffect(coordinator) {
        // Tick only an active recognition window, not an always-running animation at an idle table.
        // Relative frame time also supports Compose's controlled test clock (not wall-clock polling).
        // Each pointer event restarts the window here, outside composition: a held finger reports a
        // move every frame, and keying an effect on the epoch recomposed the whole table each time.
        snapshotFlow { clockEpoch }.collectLatest {
            val base = pointerTime
            if (coordinator.busy) {
                val start = withFrameNanos { it }
                while (isActive && coordinator.busy) withFrameNanos { now ->
                    coordinator.tick(base + (now - start) / 1_000_000); preview = coordinator.preview
                }
            }
        }
    }
    // Composition reads only these parts of the gesture preview; drag points reach just the Canvas.
    val previewDragging by remember(coordinator) { derivedStateOf { preview.scope != null || preview.delta != null } }
    val previewValue by remember(coordinator) { derivedStateOf { preview.delta?.let { preview.pressedId to it } } }
    val relay = remember(coordinator) { TableTapRelay(
        accepts = { p, t -> coordinator.replacesTap(coordinator.pointFromPixels(p.x - screenOrigin.x, p.y - screenOrigin.y), t) },
        down = { p, t -> coordinator.down(coordinator.pointFromPixels(p.x - screenOrigin.x, p.y - screenOrigin.y), t) },
        up = { p, t ->
            coordinator.up(coordinator.pointFromPixels(p.x - screenOrigin.x, p.y - screenOrigin.y), t)
            pointerTime = t; clockEpoch++; preview = coordinator.preview; pressedPing = null
        },
        cancel = { coordinator.cancel(); clockEpoch++; preview = coordinator.preview; pressedPing = null },
        dismiss = { toolkit?.dismissTapUi() },
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
    val context = LocalContext.current
    val statusStore = remember(context, accountId, game.id) { StatusBubbleStore(context, accountId, game.id) }
    val statusBubbles = remember(statusStore, runtime.statusHistory) { StatusBubbles(statusStore, runtime.statusHistory ?: { null }) }
    val statusVersion = statusBubbles.version
    val statusModes = remember(statusBubbles, statusVersion, play.currentNodeId, play.board.players) { statusBubbles.modes(play) }
    val presenceDot = rememberPresenceDot(runtime.opponentPresence)
    CompositionLocalProvider(LocalTableTapRelay provides relay, LocalTableChanges provides runtime.changes,
        LocalStatusBubbles provides statusBubbles, LocalOpponentPresence provides runtime.opponentPresence) {
    BoxWithConstraints(modifier.fillMaxWidth().onGloballyPositioned { screenOrigin = it.localToScreen(Offset.Zero) }.tableRingRelay(relay)
        .background(Color(0xfff5f8f7)).pointerInput(coordinator) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            val point = coordinator.pointFromPixels(down.position.x, down.position.y)
            val presented = presentation?.invoke()
            // Changing the interaction mode keeps the Graveyard/Exile panel open.
            val modeButton = presented?.modeButton ?: TableBounds(size.width / density - 56f, size.height / density - 112f, 48f, 48f)
            if (presented?.localPanelOutside(point) == true && !modeButton.contains(point) &&
                relay.ring?.outside?.invoke(screenOrigin + down.position) != false &&
                presented.hit(point)?.id != toolkit?.layoutState?.value?.localPileId) toolkit?.closeLocalPile()
            val bubble = presented?.stackBubbles
            if (bubble != null && bubble.controls.none { it.bounds.contains(point) }) {
                toolkit?.showStackBubbles(null)
            }
        }
    }) {
        val compactHeader = maxWidth < 400.dp
        val toolbarWidth = if (compactHeader) 192f else TablePresentation.TOOLBAR
        val cardAppearance = rememberCardAppearance()
        val fieldScale = cardScale(cardAppearance.cardSize)
        val bandTarget = maxOf(12f, CombatStackPresentation.combat(play.board, 28f * fieldScale, 40f * fieldScale,
            play.definitions, mapOf(game.inviter.accountId to game.inviter.username, game.recipient.accountId to game.recipient.username), accountId,
            cardAppearance.text(CardTextScalePart.TITLE)).height + 4f)
        val bandHeight by animateFloatAsState(bandTarget, tween(180), label = "Combat band")
        var previousBand by remember { mutableFloatStateOf(bandHeight) }
        val logicalPositions = remember(IdentityKey(play.board)) { TablePresentation.logicalPlacements(play) }
        val targetScene = remember(game, IdentityKey(play), accountId, maxWidth, maxHeight, landscape, layout, placements, toolbarWidth, runtime.reviewing, bandHeight, cardAppearance) {
            TablePresentation.scene(game, play, accountId, maxWidth.value.coerceAtLeast(1f),
                maxHeight.value.coerceAtLeast(1f), landscape, layout, placements, toolbarWidth, runtime.reviewing, bandHeight, cardAppearance, logicalPositions)
        }
        var previousLayout by remember { mutableStateOf(layout) }
        var previousSize by remember { mutableStateOf(maxWidth to maxHeight) }
        val navigation = previousLayout != layout || previousSize != (maxWidth to maxHeight) || previousBand != bandHeight
        // One interpolated scene feeds Canvas, hit testing AND semantic nodes. No destination-only overlays.
        val animation = remember(play.id, accountId) { HistorySceneAnimation(targetScene) }
        val movePreferences = rememberCardMovePreferences(accountId)
        val moveMultiplier by movePreferences.multiplierState()
        SideEffect { animation.cardMoveSpeedDpPerMs = DEFAULT_CARD_MOVE_SPEED * moveMultiplier }
        val progress = remember(animation) { androidx.compose.animation.core.Animatable(1f) }
        var animationRevision by remember(animation) { mutableIntStateOf(0) }
        SideEffect { animation.updateMetadata(targetScene) }
        LaunchedEffect(animation, targetScene.animationTargets, play.currentNodeId, runtime.sceneVersion) {
            val settled = runtime.onSceneSettled
            val faceChanges = runtime.changes?.active?.keys?.filter { it.kind == TableChangeKind.FACE }?.map { it.target }?.toSet().orEmpty()
            val shuffles = if (navigation) emptySet() else runtime.changes?.shuffles(play.currentNodeId).orEmpty()
            if (animation.retarget(targetScene, progress.value, faceChanges, shuffles, play.currentNodeId)) {
                DebugLatency.tapAnimation(game.id, animation.rotatingCardIds)
                progress.snapTo(0f)
                animationRevision++
                if (navigation || runtime.changes?.animates(play.currentNodeId) == false) progress.snapTo(1f)
                else progress.animateTo(1f, tween(animation.durationMillis, easing = androidx.compose.animation.core.LinearEasing))
            } else if (progress.value < 1f) {
                // Metadata can cancel this effect mid-scene, but cannot slow its existing clock.
                progress.animateTo(1f, tween(animation.remainingDurationMillis(progress.value), easing = androidx.compose.animation.core.LinearEasing))
            }
            animationRevision++
            withFrameNanos { }
            settled?.invoke(play.currentNodeId)
        }
        val bubbleId = if (runtime.reviewing) null else toolkit?.stackBubbleEntry
        // Only draw/layout/semantics (and the small open-overlay host) observe the clock. Sampling
        // and clipping are shared once per frame, not repeated for every accessibility child.
        val rendered = remember(animation, targetScene, bubbleId, runtime.reviewing, IdentityKey(play), maxWidth, maxHeight) { derivedStateOf {
            animationRevision
            val frame = animation.frame(progress.value, targetScene)
            val base = TablePresentation.clip(targetScene.copy(items = frame.filter { it.alpha > 0f }
                .map { it.item.copy(bounds = it.bounds) }), maxWidth.value, maxHeight.value)
            TableRenderFrame(base.copy(stackBubbles = TablePresentation.stackBubbles(base, play, bubbleId, maxWidth.value, maxHeight.value)),
                frame.associate { it.item.id to it.alpha })
        } }
        val scene = targetScene
        JankTableState(runtime.reviewing, selection.mode,
            toolkit?.panel ?: "card".takeIf { toolkit?.capture != null || toolkit?.manual?.scope != null },
            previewDragging, { progress.isRunning },
            { rendered.value.scene.items.count { it.kind == TableItemKind.CARD && it.visible.isNotEmpty() } })
        val semanticItems = remember(animation, targetScene, animationRevision) {
            // Target metadata owns the accepted disclosure; start-frame metadata may still be a back
            // during a flip. Bounds continue to come exclusively from the rendered frame below.
            (targetScene.items + animation.frame(0f, targetScene).map { it.item }).distinctBy { it.id }
        }
        LaunchedEffect(toolkit, bubbleId, IdentityKey(play)) {
            // The rendered history/animation boundary can briefly lag the session prediction during
            // acceptance. Validate personal focus against the session, not that older rendered board.
            toolkit?.refreshStackBubbles()
        }
        LaunchedEffect(IdentityKey(play), layout.localPileId) {
            if (layout.localPileId != null && play.board.containers.none { it.id == layout.localPileId && it.cards.isNotEmpty() })
                layout = layout.copy(localPileId = null)
        }
        val pingProjection = remember(play.id) { ActivePingProjection() }
        val annotations = TableAnnotationGeometry(scene, game.inviter.accountId, pingProjection)
        val artwork = tableArtwork(scene.copy(items = semanticItems))
        val drawCache = remember(density, play.id) { TableDrawCache(density) }
        val refreshRate = androidx.compose.ui.platform.LocalView.current.display?.refreshRate ?: 60f
        val pingReference = runtime.pingReference ?: EventReference(play.id, play.currentNodeId)
        val activePings = pingProjection.records(play)
        val pingPulse = if (activePings.isNotEmpty()) pingPulseProgress() else null
        var visitedGroup by remember(play.id, accountId) { mutableStateOf(play.currentGroup?.id) }
        SideEffect { previousLayout = layout; previousSize = maxWidth to maxHeight; previousBand = bandHeight }
        SideEffect {
            presentation = { rendered.value.scene }
            var retained = selection.atStep(TurnPosition.of(play.board.turn)).retain((play.board.cards.map { it.id } + play.board.stackEffects.map { it.id } +
                CombatStackRules.occurrences(play.board.combat).map { it.id }).toSet())
            if (runtime.reviewing) retained = retained.copy(ids = emptySet(), mode = if (retained.mode == TableMode.SELECT) TableMode.NORMAL else retained.mode)
            if (visitedGroup != play.currentGroup?.id && retained.mode == TableMode.PING) retained = retained.copy(mode = TableMode.NORMAL)
            visitedGroup = play.currentGroup?.id
            if (retained != selection) selection = retained
            coordinator.update(scene, retained, writable || (retained.mode == TableMode.PING &&
                runtime.ping != null && runtime.controlsConnected == true), sceneSource = { rendered.value.scene }, reviewing = runtime.reviewing)
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
                    pressedPing = presentation?.invoke()?.let { TableAnnotationGeometry(it, game.inviter.accountId, pingProjection).capture(state, reference, point) }
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
            val barColor by animateColorAsState(tableBarColor(play.board, accountId, runtime.reviewing), tween(200), label = "Table bars")
            Box(Modifier.fillMaxWidth().height(48.dp).background(barColor))
            Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(tableBottomHeight(landscape).dp)
                .background(barColor))
            Canvas(Modifier.fillMaxSize()) {
                DebugLatency.tapFrame(game.id, progress.value, refreshRate)
                val frame = rendered.value
                val panelCard = toolkit?.popupItem?.cardId?.takeIf { toolkit.panel?.startsWith("panelCard/") == true }
                drawTable(frame.scene, accountId, play.board.turn.accountId, selection.copy(ids = selection.ids + listOfNotNull(panelCard)), preview, play.board.links, frame.alphas, activePings,
                    play.board.projectionLinks, TableAnnotationGeometry(frame.scene, game.inviter.accountId), artwork, drawCache,
                    pingPulse?.value ?: 0f, statusModes, presenceDot)
            }
            previewValue?.let { (pressedId, delta) ->
                scene.items.firstOrNull { it.id == pressedId }?.let { item ->
                    val anchor = item.visible.maxByOrNull { it.width * it.height } ?: item.bounds
                    Box(Modifier.offset(anchor.x.dp, anchor.y.dp).size(anchor.width.dp, anchor.height.dp)) {
                        HoldTooltip(valueDragLabel(item.value, delta.toLong()))
                    }
                }
            }
            TableSemanticNodes(game, play, scene, semanticItems, rendered, annotations, pingProjection, pingReference,
                activePings, runtime, { currentRuntime }, coordinator, writable, toolkit, selectionState, { preview }, layoutState)
            for (item in scene.items.filter { it.zone == PlayZone.STACK && it.kind == TableItemKind.CARD && it.visible.isNotEmpty() }) {
                play.board.stackTextMarks[item.cardId]?.let { indices ->
                    StackMarkChips(indices, Modifier.offset {
                        val b = rendered.value.items[item.id]?.bounds ?: item.bounds
                        androidx.compose.ui.unit.IntOffset(b.x.dp.roundToPx(), b.y.dp.roundToPx())
                    }
                        .width(item.bounds.width.dp)
                        .semantics { contentDescription = "Stack text marks ${indices.sorted().joinToString { (it + 1).toString() }}" })
                }
            }
            if (bubbleId != null) AnimatedStackBubbleOverlay(rendered, play, accountId, { preview }, writable) { intent ->
                if (intent is TableIntent.StackDestination) toolkit?.showStackBubbles(null)
                callback?.invoke(intent)
            }
        }
        TableBoardOverlays(game, play, accountId, runtime, scene, rendered)
        TableChromeHeader(game, play, accountId, landscape, runtime, toolkit, compactHeader, scene.appearance)
        CardPanelHeaders(scene, accountId, writable, toolkit)
        TableControlsLayer(play, accountId, landscape, connected, runtime, toolkit, scene, selectionState, coordinator,
            { gestureError }, { gestureError = null })
        AnimatedCardRingAnchors(rendered, toolkit?.capture != null) {
         if (toolkit != null) TableToolkitScreen(toolkit, play, game, landscape, selection, accepted,
              tokenCatalog = runtime.tokenCatalog, peekScroll = peekScroll, scene = scene, onTimeline = runtime.history,
              selectionEnabled = !runtime.reviewing)
        }
    }
    }
}

internal fun gameLockAllows(intent: TableIntent): Boolean = intent is TableIntent.Camera || intent is TableIntent.Scroll ||
    intent == TableIntent.DismissTapUi || intent is TableIntent.Peek || intent is TableIntent.Inspect || intent is TableIntent.SearchPile

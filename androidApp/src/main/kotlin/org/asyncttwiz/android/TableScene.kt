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
import kotlinx.coroutines.launch
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
    var layout by layoutState
    var gestureError by remember(play.id, accountId) { mutableStateOf<String?>(null) }
    val peekScroll = rememberScrollState()
    val peekScreenHeight by rememberUpdatedState(holdPreviewScreenHeight())
    var screenOrigin by remember { mutableStateOf(Offset.Zero) }
    val currentPlay by rememberUpdatedState(play)
    val callback by rememberUpdatedState(onIntent)
    val writable = connected && onIntent != null
    val currentWritable by rememberUpdatedState(writable)
    val currentRuntime by rememberUpdatedState(runtime)
    var presentation by remember(play.id, accountId) { mutableStateOf<(() -> TableScene)?>(null) }
    val density = LocalDensity.current.density
    val coordinator = remember(play.id, accountId, density) {
        lateinit var owner: TableGestureCoordinator
        owner = TableGestureCoordinator({ intent ->
            // A pending result blocks game actions only; looking at cards (hold preview, inspect) stays personal and allowed.
            if (currentRuntime.gameActionsBlocked && !gameLockAllows(intent)) return@TableGestureCoordinator
            if (intent !is TableIntent.Camera && intent !is TableIntent.Scroll && intent !is TableIntent.Overscroll)
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
                is TableIntent.SearchPile -> toolkit?.openPublicPile(intent.containerId, toggle = false)
                is TableIntent.Camera -> layout = layout.copy(camera = intent.camera)
                is TableIntent.Overscroll -> layout = layout.copy(overscroll = if (intent.offset == 0f) layout.overscroll - intent.viewportId
                    else layout.overscroll + (intent.viewportId to intent.offset))
                is TableIntent.Scroll -> layout = if (intent.vertical)
                    layout.copy(scrollY = layout.scrollY + (intent.viewportId to intent.offset))
                    else layout.copy(scroll = layout.scroll + (intent.viewportId to intent.offset))
                TableIntent.EmptyTap -> {
                    // TBL-122: a tap on the empty table closes a battlefield card's ability chips
                    toolkit?.closeAbilities()
                    if (!currentRuntime.reviewing) {
                        // CMB-032, CMB-034: a tap on the empty table clears the combat selection (a blocker finishes early)
                        toolkit?.combat?.clearSelection()
                        // ZON-079: tapping anywhere else lowers a raised hand card
                        toolkit?.hand?.lower()
                    }
                }
                is TableIntent.Actions -> {
                    val captured = owner.capturedActions ?: intent
                    if (toolkit != null) {
                        val item = owner.actionItem
                        if (item != null) toolkit.open(captured, item)
                    }
                }
                is TableIntent.Inspect -> toolkit?.inspect(intent.cardId)
                else -> {
                    val sent = currentWritable && callback?.invoke(intent) == true
                    if (intent is TableIntent.Rotate && intent.toggle) {
                        gestureError = if (sent) null else "Rotation not queued. Reconnect and try again."
                        DebugLatency.tap(game.id, if (sent) "rotated" else if (!currentWritable) "dropped:sceneNotWritable" else "dropped:controllerRefused",
                            cardId = intent.scope.contextId)
                    }
                }
            }
        }, density = density, onPeekScroll = { peekScroll.holdPreviewScroll(it * density, peekScreenHeight) }, diagnostic = { event ->
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
    val previewDragging by remember(coordinator) { derivedStateOf { preview.scope != null } }
    val relay = remember(coordinator) { TableTapRelay(
        accepts = { p, t -> coordinator.replacesTap(coordinator.pointFromPixels(p.x - screenOrigin.x, p.y - screenOrigin.y), t) },
        down = { p, t -> coordinator.down(coordinator.pointFromPixels(p.x - screenOrigin.x, p.y - screenOrigin.y), t) },
        up = { p, t ->
            coordinator.up(coordinator.pointFromPixels(p.x - screenOrigin.x, p.y - screenOrigin.y), t)
            pointerTime = t; clockEpoch++; preview = coordinator.preview
        },
        cancel = { coordinator.cancel(); clockEpoch++; preview = coordinator.preview },
        dismiss = { toolkit?.dismissTapUi() },
        ringDown = { p, t, card ->
            val point = coordinator.pointFromPixels(p.x - screenOrigin.x, p.y - screenOrigin.y)
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
    val presenceDot = rememberPresenceDot(runtime.opponentPresence)
    val computedBoard = rememberComputedBoard(play, accountId, toolkit?.let { t -> { t.engineTags() } })
    CompositionLocalProvider(LocalTableTapRelay provides relay, LocalTableChanges provides runtime.changes, LocalComputedBoard provides computedBoard,
        LocalOpponentPresence provides runtime.opponentPresence) {
    BoxWithConstraints(modifier.fillMaxWidth().onGloballyPositioned { screenOrigin = it.localToScreen(Offset.Zero) }.tableRingRelay(relay)
        .background(Color(0xfff5f8f7)).pointerInput(coordinator) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            val point = coordinator.pointFromPixels(down.position.x, down.position.y)
            val presented = presentation?.invoke()
            if (presented?.localPanelOutside(point) == true &&
                relay.ring?.outside?.invoke(screenOrigin + down.position) != false &&
                presented.hit(point)?.id != toolkit?.layoutState?.value?.localPileId) toolkit?.closeLocalPile()
        }
    }) {
        val compactHeader = maxWidth < 400.dp
        val toolbarWidth = if (compactHeader) 240f else TablePresentation.TOOLBAR
        val cardAppearance = rememberCardAppearance()
        val logicalPositions = remember(IdentityKey(play.board)) { TablePresentation.logicalPlacements(play) }
        val computed = LocalComputedBoard.current
        val targetScene = remember(game, IdentityKey(play), accountId, maxWidth, maxHeight, landscape, layout, placements, toolbarWidth, runtime.reviewing, cardAppearance, computed) {
            TablePresentation.scene(game, play, accountId, maxWidth.value.coerceAtLeast(1f),
                maxHeight.value.coerceAtLeast(1f), landscape, layout, placements, toolbarWidth, runtime.reviewing, cardAppearance, logicalPositions,
                computed, landscapeHome = true)
        }
        var previousLayout by remember { mutableStateOf(layout) }
        var previousSize by remember { mutableStateOf(maxWidth to maxHeight) }
        // ZON-079 (owner playtest 2026-11-04 #9): raising or lowering a hand card is the only layout change that animates,
        // at the card move speed; any other layout change (camera, panels) or a new size jumps
        val raiseOnly = previousLayout != layout && previousLayout.copy(raised = layout.raised) == layout
        val navigation = previousLayout != layout && !raiseOnly || previousSize != (maxWidth to maxHeight)
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
                if (navigation || !raiseOnly && runtime.changes?.animates(play.currentNodeId) == false) progress.snapTo(1f)
                else progress.animateTo(1f, tween(animation.durationMillis, easing = androidx.compose.animation.core.LinearEasing))
            } else if (progress.value < 1f) {
                // Metadata can cancel this effect mid-scene, but cannot slow its existing clock.
                progress.animateTo(1f, tween(animation.remainingDurationMillis(progress.value), easing = androidx.compose.animation.core.LinearEasing))
            }
            animationRevision++
            withFrameNanos { }
            settled?.invoke(play.currentNodeId)
        }
        // Only draw/layout/semantics (and the small open-overlay host) observe the clock. Sampling
        // and clipping are shared once per frame, not repeated for every accessibility child.
        val rendered = remember(animation, targetScene, runtime.reviewing, IdentityKey(play), maxWidth, maxHeight) { derivedStateOf {
            animationRevision
            val frame = animation.frame(progress.value, targetScene)
            val base = TablePresentation.clip(targetScene.copy(items = frame.filter { it.alpha > 0f }
                .map { it.item.copy(bounds = it.bounds) }), maxWidth.value, maxHeight.value)
            TableRenderFrame(base, frame.associate { it.item.id to it.alpha })
        } }
        val scene = targetScene
        JankTableState(runtime.reviewing,
            toolkit?.panel ?: "card".takeIf { toolkit?.capture != null },
            previewDragging, { progress.isRunning },
            { rendered.value.scene.items.count { it.kind == TableItemKind.CARD && it.visible.isNotEmpty() } })
        val semanticItems = remember(animation, targetScene, animationRevision) {
            // Target metadata owns the accepted disclosure; start-frame metadata may still be a back
            // during a flip. Bounds continue to come exclusively from the rendered frame below.
            (targetScene.items + animation.frame(0f, targetScene).map { it.item }).distinctBy { it.id }
        }
        LaunchedEffect(IdentityKey(play), layout.localPileId) {
            if (layout.localPileId != null && play.board.containers.none { it.id == layout.localPileId && it.cards.isNotEmpty() })
                layout = layout.copy(localPileId = null)
        }
        val artwork = tableArtwork(scene.copy(items = semanticItems))
        val drawCache = remember(density, play.id) { TableDrawCache(density) }
        val refreshRate = androidx.compose.ui.platform.LocalView.current.display?.refreshRate ?: 60f
        SideEffect { previousLayout = layout; previousSize = maxWidth to maxHeight }
        SideEffect {
            presentation = { rendered.value.scene }
            coordinator.update(scene, writable, sceneSource = { rendered.value.scene }, reviewing = runtime.reviewing)
        }
        Box(Modifier.fillMaxSize().semantics {
            contentDescription = "Shared table"
            customActions = listOf(
                CustomAccessibilityAction("Zoom in") { coordinator.transform(TablePoint(0f, 0f), 1.2f, scene.fields.last().bounds.center); coordinator.cancel(); clockEpoch++; true },
                CustomAccessibilityAction("Zoom out") { coordinator.transform(TablePoint(0f, 0f), 1 / 1.2f, scene.fields.last().bounds.center); coordinator.cancel(); clockEpoch++; true },
                CustomAccessibilityAction("Pan left") { coordinator.panBy(-84f, 0f); true },
                CustomAccessibilityAction("Pan right") { coordinator.panBy(84f, 0f); true },
                CustomAccessibilityAction("Pan up") { coordinator.panBy(0f, -84f); true },
                CustomAccessibilityAction("Pan down") { coordinator.panBy(0f, 84f); true },
                 CustomAccessibilityAction("Fit table") { scene.world?.fit()?.let { layout = layout.copy(camera = it) }; true }
            ) + scene.viewports.filter { it.maxScrollY > 0 }.flatMap { p -> listOf(
                CustomAccessibilityAction("Scroll docks up") { layout = layout.copy(scrollY = layout.scrollY + (p.id to (p.scrollY - 68f).coerceAtLeast(0f))); true },
                CustomAccessibilityAction("Scroll docks down") { layout = layout.copy(scrollY = layout.scrollY + (p.id to (p.scrollY + 68f).coerceAtMost(p.maxScrollY))); true }
            ) }
        }.pointerInput(coordinator) {
            try {
                awaitEachGesture {
                    val first = awaitFirstDown()
                    val point = coordinator.pointFromPixels(first.position.x, first.position.y)
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
                }
            } finally { coordinator.cancel(); preview = coordinator.preview }
        }) {
            val barColor by animateColorAsState(tableBarColor(play.board, accountId, runtime.reviewing), tween(200), label = "Table bars")
            // A cast or an Automatum decision choosing targets (I-D6): its legal targets glow
            val castGlow = toolkit?.cast?.glow(play).orEmpty() + toolkit?.flows?.glow(play).orEmpty()
            // I-D46: the viewer's playable cards glow (live table only); a refused gesture shakes what it was made on.
            // TBL-117 (owner playtest 2026-11-04 #8): while Automatum works a new board out the cards that stayed where they
            // were keep their glow, and each glow fades in and out, so a card that stays playable never blinks
            val glow = rememberPlayableGlow(play, if (runtime.reviewing) emptySet() else toolkit?.playableOrNull(play))
            val shaking = toolkit?.shaking
            // ZON-079 (I-D50): what Autotap would tap for the raised hand card (live table only)
            val raisedSources = if (runtime.reviewing) emptySet() else toolkit?.hand?.sources(play).orEmpty()
            val shakeOffset = remember(shaking) { androidx.compose.animation.core.Animatable(0f) }
            LaunchedEffect(shaking) { if (shaking != null) shakeOffset.animateTo(0f, shakeKeyframes()) }
            Box(Modifier.fillMaxWidth().height(48.dp).background(barColor))
            Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(tableBottomHeight(landscape).dp)
                .background(barColor))
            Canvas(Modifier.fillMaxSize()) {
                DebugLatency.tapFrame(game.id, progress.value, refreshRate)
                val frame = rendered.value
                drawTable(frame.scene, accountId, play.board.turn.accountId, preview, frame.alphas, artwork, drawCache,
                    presenceDot, castGlow, glow.alphas(), shaking?.let { it.id to shakeOffset.value }?.takeIf { it.second != 0f }, raisedSources)
            }
            TableSemanticNodes(game, play, scene, semanticItems, rendered, runtime, { currentRuntime }, coordinator, writable, toolkit,
                { preview }, layoutState)
            // Automatum (I3, I-D29): the damage preview and blockers blocking several attackers, for both players
            // I-D32: arrows of every block, and with Automatum the staging, selection, preview and badges
            CombatAreaOverlay(toolkit?.combat, play, scene, accountId, writable && !runtime.reviewing,
                    fold = { hide -> layout = layout.copy(combatHidden = if (hide) play.board.combat?.id else null) }) { item ->
                    rendered.value.items[item.id]?.bounds ?: item.bounds }
        }
        TableBoardOverlays(game, play, accountId, runtime, scene, rendered)
        TableChromeHeader(game, play, accountId, landscape, runtime, toolkit, compactHeader, scene.appearance)
        CardPanelHeaders(scene, accountId, toolkit)
        TableControlsLayer(play, accountId, landscape, connected, runtime, toolkit, scene, { gestureError }, { gestureError = null })
        // TBL-067: table popups keep off both hands where they fit elsewhere
        val hands = remember(scene) { scene.items.filter { it.kind == TableItemKind.HAND }.map { it.bounds } }
        AnimatedCardRingAnchors(rendered, toolkit?.capture != null) {
         if (toolkit != null) CompositionLocalProvider(LocalPopupAvoid provides hands) {
             TableToolkitScreen(toolkit, play, game, landscape, accepted,
              peekScroll = peekScroll, scene = scene, onTimeline = runtime.history,
              reviewing = runtime.reviewing && !runtime.continuationArmed)
         }
        }
    }
    }
}

internal fun gameLockAllows(intent: TableIntent): Boolean = intent is TableIntent.Camera || intent is TableIntent.Scroll ||
    intent is TableIntent.Overscroll ||
    intent == TableIntent.DismissTapUi || intent is TableIntent.Peek || intent is TableIntent.Inspect || intent is TableIntent.SearchPile

/** TBL-117: the playable glow of each card, fading between boards (read while drawing, so only the canvas redraws). */
internal class PlayableGlow {
    val levels = androidx.compose.runtime.mutableStateMapOf<String, androidx.compose.animation.core.Animatable<Float, androidx.compose.animation.core.AnimationVector1D>>()
    var shown: Set<String> = emptySet()
    var containers: Map<String, String> = emptyMap()
    fun alphas(): Map<String, Float> = levels.mapValues { it.value.value }.filterValues { it > 0f }
}

/** How long a card's playable glow takes to fade in or out (TBL-117). */
internal const val GLOW_FADE_MILLIS = 220

/**
 * TBL-117: the glow of [play]'s playable cards, [target] (null while Automatum still works the board out: the cards
 * still in the container they glowed in keep glowing until it is known). Each card's glow fades to its new strength.
 */
@Composable
internal fun rememberPlayableGlow(play: PlayState, target: Set<String>?): PlayableGlow {
    val glow = remember(play.id) { PlayableGlow() }
    val containers = remember(IdentityKey(play.board)) { play.board.cards.associate { it.id to it.containerId } }
    val next = target ?: remember(IdentityKey(play.board), glow.shown) {
        glow.shown.filterTo(HashSet()) { id -> glow.containers[id] != null && glow.containers[id] == containers[id] }
    }
    LaunchedEffect(next) {
        glow.shown = next
        glow.containers = containers
        kotlinx.coroutines.coroutineScope {
            for (id in next) {
                val level = glow.levels.getOrPut(id) { androidx.compose.animation.core.Animatable(0f) }
                launch { level.animateTo(1f, androidx.compose.animation.core.tween(GLOW_FADE_MILLIS)) }
            }
            for ((id, level) in glow.levels.toMap()) if (id !in next) launch {
                level.animateTo(0f, androidx.compose.animation.core.tween(GLOW_FADE_MILLIS))
                if (id !in glow.shown) glow.levels.remove(id)
            }
        }
    }
    return glow
}

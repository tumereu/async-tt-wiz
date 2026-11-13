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
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.collectLatest
import org.asyncttwiz.protocol.*
import org.asyncttwiz.appui.displayRefreshRate

/** Normal production surface. One pointer arbiter, one Actions host, and no authoritative local board. */
@Composable
fun TableSurface(game: GameSummary, play: TableBoard, accountId: String, landscape: Boolean,
    modifier: Modifier = Modifier, connected: Boolean = false, onIntent: ((TableIntent) -> Boolean)? = null,
    placements: Map<String, TablePlacement> = emptyMap(), toolkit: TableToolkitController? = null,
    runtime: TableRuntimeHooks = TableRuntimeHooks()) {
    PrefetchGameArtwork(play, accountId)
    val layoutState: MutableState<TableLayoutState> = toolkit?.layoutState ?: remember(play.playId, accountId) { mutableStateOf(TableLayoutState()) }
    var layout by layoutState
    val peekScroll = rememberScrollState()
    // TBL-104 (owner 2026-11-06): a drag to the left while inspecting pins the Inspect
    val peekSwipe = remember { InspectSwipe() }
    SideEffect { peekSwipe.onClose = { toolkit?.peek(null) } }
    val peekScreenHeight by rememberUpdatedState(holdPreviewScreenHeight())
    var screenOrigin by remember { mutableStateOf(Offset.Zero) }
    val currentPlay by rememberUpdatedState(play)
    val callback by rememberUpdatedState(onIntent)
    val writable = connected && onIntent != null
    val currentWritable by rememberUpdatedState(writable)
    val currentRuntime by rememberUpdatedState(runtime)
    var presentation by remember(play.playId, accountId) { mutableStateOf<(() -> TableScene)?>(null) }
    // HIS-021, HIS-112, HIS-113: a finger held on the table, or an Inspect open, pauses what the view is waiting on
    val viewPause = LocalEngineRoutes.current?.viewPause
    val currentViewPause by rememberUpdatedState(viewPause)
    DisposableEffect(viewPause, toolkit) {
        viewPause?.inspecting = { toolkit?.peek != null }
        onDispose { viewPause?.finger = false; viewPause?.inspecting = { false } }
    }
    val density = LocalDensity.current.density
    val coordinator = remember(play.playId, accountId, density) {
        lateinit var owner: TableGestureCoordinator
        owner = TableGestureCoordinator({ intent ->
            // A pending result blocks game actions only; looking at cards (hold preview, inspect) stays personal and allowed.
            if (currentRuntime.gameActionsBlocked && !gameLockAllows(intent) &&
                !(intent is TableIntent.Actions && owner.actionItem?.publicPile == true)) return@TableGestureCoordinator
            if (intent !is TableIntent.Camera && intent !is TableIntent.Scroll && intent !is TableIntent.Overscroll)
                DebugJank.action(intent::class.simpleName ?: "TableIntent")
            when (intent) {
                TableIntent.DismissTapUi -> {
                    DebugLatency.tap(game.id, "popupClosing")
                    toolkit?.dismissTapUi()
                }
                is TableIntent.Peek -> {
                    if (intent.cardId != null) { peekScroll.dispatchRawDelta(-peekScroll.value.toFloat()); peekSwipe.begin() }
                    // Lifting the finger after the pinned swipe keeps the Inspect open
                    else if (intent.released && peekSwipe.released()) return@TableGestureCoordinator
                    // TBL-111: holding a collapsed group inspects all its members, its top card first
                    val group = presentation?.invoke()?.let { shown -> shown.groupOf(intent.cardId)
                        ?.takeIf { it.members.size > 1 && it.key != shown.expandedGroup } }
                    toolkit?.peek(intent.cardId, group?.inspectOrder)
                }
                is TableIntent.SearchPile -> toolkit?.openPublicPile(intent.containerId, toggle = false)
                is TableIntent.Camera -> layout = layout.copy(camera = intent.camera)
                is TableIntent.Overscroll -> layout = layout.copy(overscroll = if (intent.offset == 0f) layout.overscroll - intent.viewportId
                    else layout.overscroll + (intent.viewportId to intent.offset))
                is TableIntent.Scroll -> layout = if (intent.vertical)
                    layout.copy(scrollY = layout.scrollY + (intent.viewportId to intent.offset))
                    else layout.copy(scroll = layout.scroll + (intent.viewportId to intent.offset))
                TableIntent.EmptyTap, is TableIntent.Actions -> if (currentViewPause?.justTappedShow == true && !owner.tapOnHand) {
                    // HIS-112: the second tap of a double tap on a shown play, the play gone from under it by then
                } else if (currentRuntime.holdTap != null && !owner.tapOnHand) {
                    // HIS-113 (owner, 2026-11-08): while a combat holds the view a single tap on the table (not the hand) does
                    // nothing; a double tap skips it ([TableGestureCoordinator.viewHoldSkip])
                } else if (currentRuntime.centreAction != null && !owner.tapOnHand) {
                    // HIS-094: with the catch-up's centre text showing, a tap on the table (not the hand) starts the playback
                    currentRuntime.centreAction?.run?.invoke()
                } else if (currentRuntime.boardTap != null) {
                    // HIS-095: while playback runs a tap on the table only pauses it
                    currentRuntime.boardTap?.invoke()
                } else if (intent is TableIntent.Actions) {
                    val captured = owner.capturedActions ?: intent
                    if (toolkit != null) {
                        val item = owner.actionItem
                        val shown = presentation?.invoke()
                        // TBL-122: a battlefield card's tap is its group's (acts at once, or opens the group)
                        if (item != null) { if (shown != null) toolkit.tap(captured, item, shown) else toolkit.open(captured, item) }
                    }
                } else if (!currentRuntime.reviewing && presentation?.invoke()?.let { toolkit?.tapCombatSlot(it, owner.tapPoint) } == true) {
                    // CMB-034, CMB-040: the selected creature blocks the attacker whose slot was tapped
                } else {
                    // TBL-122: a tap on the empty table closes a battlefield card's ability chips and an opened group
                    toolkit?.closeAbilities()
                    toolkit?.collapse()
                    if (!currentRuntime.reviewing) {
                        // CMB-032, CMB-034: a tap on the empty table clears the combat selection (a blocker finishes early)
                        toolkit?.combat?.clearSelection()
                        // ZON-079: tapping anywhere else lowers a raised hand card
                        toolkit?.hand?.lower()
                    }
                }
                else -> if (currentWritable) callback?.invoke(intent)
            }
        }, density = density, onPeekScroll = { peekScroll.holdPreviewScroll(it * density, peekScreenHeight) },
            onPeekSwipe = peekSwipe::moved, diagnostic = { event -> DebugLatency.tap(game.id, event, toolkit?.popupItem != null) },
            onHeld = { held -> currentViewPause?.finger = held })
        owner.viewHoldSkip = { currentRuntime.holdTap }
        owner.combatDrag = { item -> toolkit?.combatDraggable(item) ?: true }
        owner
    }
    var preview by remember(coordinator) { mutableStateOf(TableGesturePreview()) }
    var clockEpoch by remember(coordinator) { mutableIntStateOf(0) }
    var pointerTime by remember(coordinator) { mutableLongStateOf(0L) }
    // WEB-023 to WEB-029: the mouse and the finger key on the desktop table (nothing of it in the phone layout)
    val desktopFrame = LocalDesktopFrame.current
    val mouseScope = rememberCoroutineScope()
    val fingerKey = remember(coordinator) { DesktopFingerKey() }
    val desktopHover = remember(coordinator, toolkit) { DesktopHover(mouseScope) { target ->
        toolkit?.hand?.hover((target as? HoverTarget.Hand)?.cardId)
        toolkit?.hoveredAbilities = (target as? HoverTarget.Field)?.cardId
        toolkit?.hoveredAbility = null
    } }
    val mouse = remember(coordinator) { DesktopTableMouse(coordinator, { p -> presentation?.invoke()?.hit(p) }, mouseScope) }
    SideEffect {
        coordinator.holdInspects = desktopFrame == null
        mouse.hoverChips = desktopHover
        mouse.interrupted = { fingerKey.interrupt() }
        mouse.moved = { clockEpoch++; preview = coordinator.preview }
        mouse.quickLookAllowed = { toolkit?.peek == null && toolkit?.popupItem == null }
        mouse.chipTarget = { item ->
            val id = item.cardId
            when {
                id == null || currentRuntime.reviewing || toolkit == null -> null
                item.kind == TableItemKind.CARD && item.zone == PlayZone.HAND && item.owner == accountId -> HoverTarget.Hand(id)
                item.battlefieldCard && item.owner == accountId -> HoverTarget.Field(id)
                else -> null
            }
        }
    }
    /** WEB-024: opens Inspect of [id] as the finger key does (TBL-111: a collapsed group's members together). */
    fun keyInspect(id: String) {
        mouse.dismissLook()
        peekScroll.dispatchRawDelta(-peekScroll.value.toFloat())
        peekSwipe.holdByKey()
        val group = presentation?.invoke()?.let { shown -> shown.groupOf(id)?.takeIf { it.members.size > 1 && it.key != shown.expandedGroup } }
        toolkit?.peek(id, group?.inspectOrder)
    }
    DesktopKeyboard.Listen(desktopFrame != null) { key ->
        if (key.key != "Alt" && key.key != "Shift") {
            if (key.down && !key.repeat) fingerKey.interrupt()
            return@Listen false
        }
        if (key.repeat) return@Listen fingerKey.held
        if (key.down && key.textField) return@Listen false
        // The card under the pointer, as a held finger would inspect it (a pile's shown card too; not a hand row's backing)
        val card = mouse.hovered?.let { item -> item.cardId ?: item.cards.firstOrNull()?.takeIf { !item.covered && item.kind != TableItemKind.HAND } }
        val effects = if (key.down) fingerKey.press(key.key, key.time, card, toolkit?.peek != null) else fingerKey.release(key.key, key.time)
        if (effects.isNotEmpty()) AppLog.info("table", "Finger key ${key.key} ${if (key.down) "down" else "up"}${card?.let { " over $it" }.orEmpty()}: " +
            effects.joinToString { it.toString() })
        for (effect in effects) when (effect) {
            is DesktopFingerKey.Effect.Pause -> currentViewPause?.key = effect.on
            is DesktopFingerKey.Effect.Inspect -> keyInspect(effect.cardId)
            DesktopFingerKey.Effect.Pin -> peekSwipe.pinByKey()
            DesktopFingerKey.Effect.Close -> { peekSwipe.begin(); toolkit?.peek(null) }
        }
        effects.isNotEmpty() || fingerKey.held
    }
    DisposableEffect(viewPause) { onDispose { viewPause?.key = false } }
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
    // D2 (SYS-041): the table pans past an open Automatum panel by its height
    val dockedPanels = remember(coordinator) { DockedPanels().also { panels -> coordinator.panelInset = { panels.height } } }
    val computedBoard = rememberComputedBoard(play, accountId)
    CompositionLocalProvider(LocalTableTapRelay provides relay, LocalTableChanges provides runtime.changes, LocalComputedBoard provides computedBoard,
        LocalOpponentPresence provides runtime.opponentPresence, LocalTableQuickChat provides runtime.quickChat,
        LocalDockedPanels provides dockedPanels, LocalDesktopHover provides desktopHover.takeIf { desktopFrame != null }) {
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
        // WEB-015: the desktop table (the desktop layout's frame), drawn as landscape with its own arrangement
        val desktop = LocalDesktopFrame.current
        val desktopGeometry = desktop?.let { DesktopGeometry(maxWidth.value, maxHeight.value, (maxWidth.value - it.dockWidth).coerceAtLeast(1f),
            DesktopTable.band(cardAppearance, runtime.timelineOpen)) }
        val logicalPositions = remember(IdentityKey(play.board)) { TablePresentation.logicalPlacements(play) }
        val computed = LocalComputedBoard.current
        // CMB-028: the viewer declares attackers: the band's lanes keep room for the next
        val attackerSlots = !runtime.reviewing && writable && toolkit?.combat?.readyOrLast(play)?.choices
            ?.let { it.attackers && it.declaring == accountId } == true
        // STK-024: what pays the open payment lies in its own pile, to the right of its stack
        val paying = if (runtime.reviewing) emptySet() else toolkit?.paySources().orEmpty()
        val laid = if (paying.isEmpty()) layout else layout.copy(paying = paying)
        // ZON-079 (owner, 2026-11-12): your cards from other zones a permission lets you cast or play, at your hand row's end
        // (worked out with the board; the engine's own casts from elsewhere join them once worked out)
        val cachedPlays = AutomatumBoards.cachedHandPlays(play, accountId)
        val handRow = remember(IdentityKey(play.board), play.values, cachedPlays, accountId, runtime.reviewing, toolkit) {
            if (runtime.reviewing || toolkit == null) emptyList() else HandRow.cards(play, accountId)
        }
        // A board the scene can't be built from (a state nobody tested, prod crash WCRF3Q) keeps the last scene shown, reported
        val lastScene = remember(play.playId, accountId) { LastScene() }
        val targetScene = remember(game, IdentityKey(play), accountId, maxWidth, maxHeight, landscape, laid, placements, toolbarWidth, runtime.reviewing, runtime.timelineOpen, cardAppearance, computed, attackerSlots, handRow, desktop) {
            try {
                TablePresentation.scene(game, play, accountId, maxWidth.value.coerceAtLeast(1f),
                    maxHeight.value.coerceAtLeast(1f), landscape, laid, placements, toolbarWidth, runtime.reviewing, cardAppearance, logicalPositions,
                    computed, landscapeHome = true, timeline = runtime.timelineOpen, attackerSlots = attackerSlots, handRow = handRow,
                    desktop = desktop).also { lastScene.scene = it }
            } catch (failure: RuntimeException) {
                Faults.report("TableScene", "table scene not built", mapOf("game" to game.id, "node" to play.nodeId,
                    "failure" to failure.stackTraceToString().take(4_000)))
                lastScene.scene ?: throw failure
            }
        }
        var previousLayout by remember { mutableStateOf(laid) }
        var previousSize by remember { mutableStateOf(maxWidth to maxHeight) }
        // ZON-079 (owner playtest 2026-11-04 #9): raising or lowering a hand card, opening or closing a battlefield group
        // (TBL-122) and a source joining or leaving a payment's pile (STK-024) are the only layout changes that animate, at the card move speed; any other layout change (camera,
        // panels) or a new size jumps
        val raiseOnly = previousLayout != laid &&
            previousLayout.copy(raised = laid.raised, expandedGroup = laid.expandedGroup, paying = laid.paying) == laid
        val navigation = previousLayout != laid && !raiseOnly || previousSize != (maxWidth to maxHeight)
        // One interpolated scene feeds Canvas, hit testing AND semantic nodes. No destination-only overlays.
        val animation = remember(play.playId, accountId) { HistorySceneAnimation(targetScene) }
        val movePreferences = rememberCardMovePreferences(accountId)
        val moveMultiplier by movePreferences.multiplierState()
        SideEffect { animation.cardMoveSpeedDpPerMs = DEFAULT_CARD_MOVE_SPEED * moveMultiplier }
        val progress = remember(animation) { androidx.compose.animation.core.Animatable(1f) }
        var animationRevision by remember(animation) { mutableIntStateOf(0) }
        SideEffect { animation.updateMetadata(targetScene) }
        LaunchedEffect(animation, targetScene.animationTargets, play.nodeId, runtime.sceneVersion) {
            val settled = runtime.onSceneSettled
            val faceChanges = runtime.changes?.active?.keys?.filter { it.kind == TableChangeKind.FACE }?.map { it.target }?.toSet().orEmpty()
            val shuffles = if (navigation) emptySet() else runtime.changes?.shuffles(play.nodeId).orEmpty()
            if (animation.retarget(targetScene, progress.value, faceChanges, shuffles, play.nodeId)) {
                DebugLatency.tapAnimation(game.id, animation.rotatingCardIds)
                progress.snapTo(0f)
                animationRevision++
                if (navigation || !raiseOnly && runtime.changes?.animates(play.nodeId) == false) progress.snapTo(1f)
                else progress.animateTo(1f, tween(animation.durationMillis, easing = androidx.compose.animation.core.LinearEasing))
            } else if (progress.value < 1f) {
                // Metadata can cancel this effect mid-scene, but cannot slow its existing clock.
                progress.animateTo(1f, tween(animation.remainingDurationMillis(progress.value), easing = androidx.compose.animation.core.LinearEasing))
            }
            animationRevision++
            withFrameNanos { }
            settled?.invoke(play.nodeId)
        }
        // Only draw/layout/semantics (and the small open-overlay host) observe the clock. Sampling
        // and clipping are shared once per frame, not repeated for every accessibility child.
        val rendered = remember(animation, targetScene, runtime.reviewing, IdentityKey(play), maxWidth, maxHeight) { derivedStateOf {
            animationRevision
            val frame = animation.frame(progress.value, targetScene)
            // CMB-001: the battlefields slide with their cards, so the combat band between them opens and closes with them
            val base = TablePresentation.clip(targetScene.copy(items = frame.filter { it.alpha > 0f }
                .map { it.item.copy(bounds = it.bounds) }, fields = animation.fields(progress.value, targetScene)), maxWidth.value, maxHeight.value)
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
        val drawCache = remember(density, play.playId) { TableDrawCache(density) }
        val refreshRate = displayRefreshRate()
        SideEffect { previousLayout = laid; previousSize = maxWidth to maxHeight }
        val tableWidth = maxWidth.value; val tableHeight = maxHeight.value
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
        }.then(if (desktopFrame != null) Modifier.desktopTableMouse(mouse, density) else Modifier).pointerInput(coordinator) {
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
            // STK-047 (owner 2026-11-07): the targets picked so far show picked, alone or in an opened group (TBL-130)
            val pickedGlow = toolkit?.cast?.pickedGlow(play).orEmpty() + toolkit?.flows?.pickedGlow(play).orEmpty()
            // I-D46: the viewer's playable cards glow (live table only); a refused gesture shakes what it was made on.
            // TBL-117 (owner playtest 2026-11-04 #8): while Automatum works a new board out the cards that stayed where they
            // were keep their glow, and each glow fades in and out, so a card that stays playable never blinks
            val glow = rememberPlayableGlow(play, if (runtime.reviewing) emptySet() else toolkit?.playableOrNull(play))
            val shaking = toolkit?.shaking
            // ZON-079 (I-D50): what Autotap would tap for the raised hand card (live table only)
            // STK-024 (owner 2026-11-07): and while the cast panel is open, the sources that pay it
            val raisedSources = if (runtime.reviewing) emptySet() else toolkit?.hand?.sources(play).orEmpty() + toolkit?.paySources().orEmpty()
            // CMB-028 (owner, 2026-11-05): while the viewer declares attackers, the creatures that may attack glow red-orange;
            // CMB-034, CMB-040 (owner, 2026-11-08): as do those that may block while the viewer places blockers
            val assumingBlocks = LocalEngineRoutes.current?.assuming() != null
            val placingBlocks = assumingBlocks || LocalEngineRoutes.current?.mayAct() != false
            val attackGlow = if (runtime.reviewing || !writable) emptySet() else toolkit?.combat?.let {
                it.attackable(play, accountId) + if (placingBlocks) it.blockable(play, accountId, assumingBlocks) else emptySet() }.orEmpty()
            val shakeOffset = remember(shaking) { androidx.compose.animation.core.Animatable(0f) }
            LaunchedEffect(shaking) { if (shaking != null) shakeOffset.animateTo(0f, shakeKeyframes()) }
            // TBL-117, TBL-122: what a tap on each collapsed group of several does, for its glow's colour (live table only)
            val groupTaps = if (toolkit == null || runtime.reviewing) emptyMap() else scene.battlefieldGroups
                .filter { it.members.size > 1 && it.key != scene.expandedGroup }.associate { it.key to toolkit.groupTap(play, it) }
            // TBL-130 (owner 2026-11-07): while targets are picked, the members of the opened group that aren't legal targets
            // are dimmed (as the Stack list's, STK-048)
            val dimmed = if (toolkit?.let { it.cast.targeting || it.flows.targeting } != true) emptySet() else scene.battlefieldGroups
                .firstOrNull { it.key == scene.expandedGroup }?.members.orEmpty().filterNotTo(HashSet()) { it in castGlow }
            // TBL-117 (owner playtest 2026-11-06): the glow of the card a finger is down on grows a little and brightens
            val pressedId by remember(coordinator) { derivedStateOf { preview.pressedId } }
            val pressGlow = rememberPressGlow(pressedId)
            if (desktopGeometry != null) {
                // WEB-016: each seat header in its player's colour across its battlefield; WEB-018: the bottom band, your hand
                // and the action area, in the bars' colour
                for (port in scene.viewports.filter { it.id.startsWith("dock/") && it.id.count { c -> c == '/' } == 1 }) {
                    val owner = port.id.substringAfter("dock/")
                    Box(Modifier.offset(0.dp, port.bounds.y.dp).size(desktopGeometry.tableRight.dp, DesktopTable.SEAT.dp)
                        .background(seatHeaderColor(owner, accountId, runtime.reviewing)))
                }
                Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(desktopGeometry.band.dp).background(barColor))
            } else {
            Box(Modifier.fillMaxWidth().height(48.dp).background(barColor))
            Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(tableBottomHeight(landscape, runtime.timelineOpen).dp)
                .background(barColor))
            }
            Canvas(Modifier.fillMaxSize()) {
                DebugLatency.tapFrame(game.id, progress.value, refreshRate)
                val frame = rendered.value
                val glows = TableGlows.of(frame.scene.battlefieldGroups, groupTaps, glow.alphas(), castGlow, attackGlow, pickedGlow)
                val alphas = if (dimmed.isEmpty()) frame.alphas else frame.alphas + frame.scene.items
                    .filter { it.battlefieldCard && it.cardId in dimmed }.associate { it.id to minOf(frame.alphas[it.id] ?: 1f, TARGET_DIMMED) }
                drawTable(frame.scene, accountId, play.board.turn.accountId, preview, alphas, artwork, drawCache,
                    presenceDot, glows.targets, glows.playable, shaking?.let { it.id to shakeOffset.value }?.takeIf { it.second != 0f }, raisedSources,
                    attackable = glows.attack, expandGlow = glows.expand, pressed = pressGlow.value, picked = glows.picked, pileBadges = false)
            }
            // STK-047 (owner, 2026-11-12): a pile holding a card the viewer may target or choose glows over its whole face,
            // pulsing, in a layer of its own
            PileChoiceGlow(scene, castGlow, rendered)
            TableSemanticNodes(game, play, scene, semanticItems, rendered, runtime, { currentRuntime }, coordinator, writable, toolkit,
                { preview }, layoutState, groupTaps)
            // Automatum (I3, I-D29): the damage preview and blockers blocking several attackers, for both players
            // I-D32: arrows of every block, and with Automatum the staging, selection, preview and badges
            // CMB-001 (owner playtest 2026-11-11): the chips on the band's upper edge move with the battlefield above it as
            // the band opens or closes
            val bandShift = { rendered.value.scene.fields.firstOrNull()?.gridBounds?.bottom
                ?.minus(scene.fields.firstOrNull()?.gridBounds?.bottom ?: 0f) ?: 0f }
            CombatAreaOverlay(toolkit?.combat, play, scene, accountId, writable && !runtime.reviewing,
                    pressed = { pressGlow.value }, dragged = { preview.scope?.contextId }, bandShift = bandShift) { item ->
                    rendered.value.items[item.id]?.bounds ?: item.bounds }
            // CMB-001 (owner playtest 2026-11-11): when the band closes, what sat on it or by it fades out where it was
            ClosingCombatBand(play, scene, accountId) { item -> rendered.value.items[item.id]?.bounds ?: item.bounds }
            // STK-058 (owner playtest 2026-11-07): the top Stack entry's target arrows, for both players; not while the viewer
            // picks targets themselves (their violet glow, STK-047)
            val stackNames = remember(game) { gameNames(game) }
            if (toolkit?.let { it.cast.targeting || it.flows.targeting } != true)
                StackTargetArrows(play, scene, accountId, stackNames) { item -> rendered.value.items[item.id]?.bounds ?: item.bounds }
            // STK-058 (owner gallery review 2026-11-07): the piles' count bubbles over every arrow and overlay above the table
            Canvas(Modifier.fillMaxSize()) { drawPileBadges(rendered.value.scene, drawCache) }
            // TRN-053 (owner gallery review 2026-11-06): the emblems' round chips; holding one inspects the player's emblems
            EmblemChipsOverlay(scene, accountId)
            // WEB-016: each seat header's avatar and name
            if (desktopGeometry != null) SeatHeaderNames(game, scene, accountId)
            // TRN-072, TRN-073: a player's states and the game's day or night; holding one explains it
            StateChipsOverlay(scene)
            // STK-024 (owner playtest 2026-11-09): each source paying the open payment carries the mana it pays at its corner
            if (toolkit != null && !runtime.reviewing) PaymentCornerChips(toolkit, scene) { item -> rendered.value.items[item.id]?.bounds ?: item.bounds }
            // STK-047 (owner 2026-11-11, which pick is which): what each picked card is for, at its other corner
            if (toolkit != null && !runtime.reviewing) PickRoleChips(toolkit, play, scene) { item -> rendered.value.items[item.id]?.bounds ?: item.bounds }
            // LOB-136, LOB-137 (owner, 2026-11-08): chat messages as speech bubbles at the life squares, over what lies there;
            // last here, so a tap on the other player's takes that tap from the table and its chips
            runtime.chatBubbles?.let { ChatBubblesOverlay(scene, accountId, it, tableWidth, tableHeight) }
        }
        CompositionLocalProvider(LocalDesktopGeometry provides desktopGeometry) {
        TableBoardOverlays(game, play, accountId, runtime, scene, rendered)
        // WEB-026: the quick look of the card the mouse rests on
        if (desktopGeometry != null && toolkit != null) mouse.quickLook?.let { item ->
            DesktopQuickLook(play, accountId, item, toolkit, desktopGeometry.tableRight, tableHeight)
        }
        // WEB-020: on the desktop table the top row is the screen's, above the table
        if (desktopGeometry == null) TableChromeHeader(game, play, accountId, landscape, runtime, toolkit, compactHeader, scene.appearance)
        CardPanelHeaders(scene, accountId, toolkit)
        TableControlsLayer(game, play, accountId, landscape, connected, runtime, toolkit, scene)
        // WEB-017: the right dock, under the table's popups and panels
        if (desktopGeometry != null && desktop.dockOpen) runtime.desktopDock?.let { dock ->
            Box(Modifier.align(Alignment.TopEnd).width(DesktopTable.DOCK.dp).height((maxHeight.value - desktopGeometry.band).coerceAtLeast(0f).dp)) { dock() }
        }
        // TBL-067: table popups keep off both hands where they fit elsewhere
        val hands = remember(scene) { scene.items.filter { it.kind == TableItemKind.HAND }.map { it.bounds } }
        AnimatedCardRingAnchors(rendered, toolkit?.capture != null) {
         if (toolkit != null) CompositionLocalProvider(LocalPopupAvoid provides hands) {
             TableToolkitScreen(toolkit, play, game, landscape,
              peekScroll = peekScroll, scene = scene, peekSwipe = peekSwipe, onTimeline = runtime.history,
              reviewing = runtime.reviewing, gameActionsBlocked = runtime.gameActionsBlocked)
         }
        }
        }
    }
    }
}

fun gameLockAllows(intent: TableIntent): Boolean = intent is TableIntent.Camera || intent is TableIntent.Scroll ||
    intent is TableIntent.Overscroll ||
    intent == TableIntent.DismissTapUi || intent is TableIntent.Peek || intent is TableIntent.SearchPile

/** TBL-117: the playable glow of each card, fading between boards (read while drawing, so only the canvas redraws). */
class PlayableGlow {
    val levels = androidx.compose.runtime.mutableStateMapOf<String, androidx.compose.animation.core.Animatable<Float, androidx.compose.animation.core.AnimationVector1D>>()
    var shown: Set<String> = emptySet()
    var containers: Map<String, String> = emptyMap()
    fun alphas(): Map<String, Float> = levels.mapValues { it.value.value }.filterValues { it > 0f }
}

/**
 * TBL-117 (owner playtest 2026-11-06): the item a finger is down on ([pressed]) and how far its glow has grown (0–1),
 * growing in and shrinking back after the release; null when nothing is pressed.
 */
@Composable
fun rememberPressGlow(pressed: String?): State<Pair<String, Float>?> {
    var last by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(pressed) { if (pressed != null) last = pressed }
    val current = rememberUpdatedState(pressed)
    val level = animateFloatAsState(if (pressed != null) 1f else 0f, tween(PRESS_GLOW_MILLIS), label = "Press glow")
    return remember { derivedStateOf { (current.value ?: last)?.let { id -> (id to level.value).takeIf { it.second > 0f } } } }
}

/** How long a card's playable glow takes to fade in or out (TBL-117). */
const val GLOW_FADE_MILLIS = 220

/** How opaque an opened group's members that aren't legal targets are drawn while targets are picked (TBL-130). */
private const val TARGET_DIMMED = .4f

/**
 * TBL-117: the glow of [play]'s playable cards, [target] (null while Automatum still works the board out: the cards
 * still in the container they glowed in keep glowing until it is known). Each card's glow fades to its new strength.
 */
@Composable
fun rememberPlayableGlow(play: TableBoard, target: Set<String>?): PlayableGlow {
    val glow = remember(play.playId) { PlayableGlow() }
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

/** The last table scene built, shown again while a newer board's can't be built. */
private class LastScene { var scene: TableScene? = null }

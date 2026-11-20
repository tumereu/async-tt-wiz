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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.map
import androidx.compose.runtime.snapshots.Snapshot
import org.asyncttwiz.protocol.*
import org.asyncttwiz.appui.displayRefreshRate

/** Normal production surface. One pointer arbiter, one Actions host, and no authoritative local board. */
@Composable
fun TableSurface(game: GameSummary, play: TableBoard, accountId: String, landscape: Boolean,
    modifier: Modifier = Modifier, connected: Boolean = false, onIntent: ((TableIntent) -> Boolean)? = null,
    placements: Map<String, TablePlacement> = emptyMap(), toolkit: TableToolkitController? = null,
    runtime: TableRuntimeHooks = TableRuntimeHooks()) {
    // TBL-139: with hand or battlefield cards drawn as full pictures, their whole printed cards are warmed too
    val prefetchAppearance = rememberCardAppearance()
    PrefetchGameArtwork(play, accountId, prefetchAppearance.handFullPicture || prefetchAppearance.battlefieldFullPicture)
    // HIS-113 (full e2e run 2026-11-15): when a table is made and let go reaches the app log; one made anew mid-game drops
    // its gestures (a held finger), its animation and what it remembers
    DisposableEffect(Unit) {
        AppLog.info("table", "Table made for ${accountId.take(8)} (game ${game.id.take(8)}, play ${play.playId.take(8)})")
        TableProbes.made?.invoke(accountId)
        onDispose { AppLog.info("table", "Table let go for ${accountId.take(8)} (game ${game.id.take(8)})") }
    }
    val layoutState: MutableState<TableLayoutState> = toolkit?.layoutState ?: remember(play.playId, accountId) { mutableStateOf(TableLayoutState()) }
    var layout by layoutState
    // Web frame rate (owner, 2026-11-15: the table stutters in Firefox and Chrome): composition reads the layout without its
    // camera, so a pan or zoom step recomposes nothing ([sceneNow] below)
    val layoutShown = toolkit?.layoutShown ?: remember(layoutState) { layoutState.withoutCamera() }
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
                // HIS-139, HIS-148 (owner 2026-11-19): no tap on the table skips a play shown or a combat's hold (the main button
                // does, TRN-165): taps act as at any other time
                TableIntent.EmptyTap, is TableIntent.Actions -> if (currentRuntime.centreAction != null && !owner.tapOnHand) {
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
            onHeld = { held ->
                // HIS-021, HIS-113: what a held finger pauses (a play shown, a combat's hold) reaches the app log with it
                AppLog.info("table", if (held) "Finger held on the table (pauses what the view waits on)" else "Finger on the table lifted")
                currentViewPause?.finger = held
            })
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
    // WEB-015 (owner 2026-11-16): the viewer's hand card the mouse is on, which is drawn over the others, and stays so while
    // the pointer is on its chips (WEB-027) (read so only a change of it rebuilds the scene, not every item the pointer passes)
    val handHover by remember(mouse, desktopHover, accountId) { derivedStateOf {
        mouse.hovered?.takeIf { it.kind == TableItemKind.CARD && it.zone == PlayZone.HAND && it.owner == accountId }?.cardId
            ?: (desktopHover.target as? HoverTarget.Hand)?.cardId
    } }
    SideEffect {
        coordinator.holdInspects = desktopFrame == null
        mouse.hoverChips = desktopHover
        mouse.moved = { clockEpoch++; preview = coordinator.preview }
        mouse.quickLookAllowed = { toolkit?.peek == null && toolkit?.popupItem == null }
        // WEB-031: F zooms to fit, as a double click on the empty battlefield does
        toolkit?.fitTable = if (desktopFrame == null) null else ({ coordinator.fit(); clockEpoch++; preview = coordinator.preview })
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
    // WEB-024: the cards outside the scene the finger key inspects (the play shown)
    val fingerTargets = remember(coordinator) { FingerTargets() }
    fun applyFinger(effects: List<DesktopFingerKey.Effect>) {
        for (effect in effects) when (effect) {
            is DesktopFingerKey.Effect.Pause -> currentViewPause?.key = effect.on
            is DesktopFingerKey.Effect.Inspect -> keyInspect(effect.cardId)
            DesktopFingerKey.Effect.Close -> { peekSwipe.begin(); toolkit?.peek(null) }
        }
    }
    DesktopKeyboard.Listen(desktopFrame != null) { key ->
        if (key.key != "Alt" && key.key != "Shift") {
            // WEB-031: Shift with another key is a chord (Shift+Space), not the finger
            if (key.down && !key.repeat) fingerKey.chord().takeIf { it.isNotEmpty() }?.let { effects ->
                AppLog.info("table", "Finger key Shift lifted by ${key.key} (a chord): ${effects.joinToString()}")
                applyFinger(effects)
            }
            return@Listen false
        }
        if (key.repeat) return@Listen fingerKey.held
        if (key.down && key.textField) return@Listen false
        // The card under the pointer, as a held finger would inspect it (a pile's shown card too; not a hand row's backing);
        // the play shown first, which lies over the table
        val card = fingerTargets.hovered ?: mouse.hovered?.let { item ->
            item.cardId ?: item.cards.firstOrNull()?.takeIf { !item.covered && item.kind != TableItemKind.HAND } }
        val effects = if (key.down) fingerKey.press(key.key, card) else fingerKey.release(key.key)
        // WEB-025 (prod report 96PB2X): a release the page never sent says why it came (the window lost the focus, a text field
        // took the keyboard, a later event without the key held)
        val how = if (key.down) "down" else key.lost?.let { "let go ($it)" } ?: "up"
        if (effects.isNotEmpty()) AppLog.info("table", "Finger key ${key.key} $how${card?.let { " over $it" }.orEmpty()}: " +
            effects.joinToString { it.toString() })
        applyFinger(effects)
        // A pause the finger key left on with nothing held any more (its key-up went to a table made before this one): a
        // release of a finger key always ends it
        if (!key.down && !fingerKey.held && currentViewPause?.key == true) {
            AppLog.info("table", "Finger key ${key.key} $how: the pause it had left on ends (no finger key held here)")
            currentViewPause?.key = false
        }
        effects.isNotEmpty() || fingerKey.held
    }
    DisposableEffect(viewPause) { onDispose { viewPause?.key = false } }
    // WEB-025: a finger key held as this table goes (the table made anew) leaves no pause on: the new one doesn't know it's held
    DisposableEffect(fingerKey) { onDispose {
        if (fingerKey.held && viewPause?.key == true) {
            AppLog.info("table", "Finger key held as the table went: the pause it held ends")
            viewPause.key = false
        }
    } }
    // WEB-025, HIS-021: the left button held still on the table pauses the view; the window losing the focus lets it go (its
    // button-up may never come)
    DesktopKeyboard.OnFocusLost(desktopFrame != null) { why ->
        if (coordinator.fingerHeld) {
            AppLog.info("table", "Left button held on the table let go ($why): the pause it held ends")
            coordinator.cancel()
        }
    }
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
        LocalDockedPanels provides dockedPanels, LocalDesktopHover provides desktopHover.takeIf { desktopFrame != null },
        LocalFingerTargets provides fingerTargets.takeIf { desktopFrame != null }) {
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
        SideEffect { TableProbes.composed?.invoke(accountId) }
        val compactHeader = maxWidth < 400.dp
        val toolbarWidth = if (compactHeader) 240f else TablePresentation.TOOLBAR
        val cardAppearance = rememberCardAppearance()
        // WEB-015: the desktop table (the desktop layout's frame), drawn as landscape with its own arrangement
        val desktop = LocalDesktopFrame.current
        val desktopGeometry = desktop?.let { DesktopGeometry(maxWidth.value, maxHeight.value, (maxWidth.value - it.dockWidth).coerceAtLeast(1f),
            DesktopTable.band(runtime.timelineOpen)) }
        val logicalPositions = remember(IdentityKey(play.board)) { TablePresentation.logicalPlacements(play) }
        val computed = LocalComputedBoard.current
        // CMB-028: the viewer declares attackers: the band's lanes keep room for the next
        val attackerSlots = !runtime.reviewing && writable && toolkit?.combat?.readyOrLast(play)?.choices
            ?.let { it.attackers && it.declaring == accountId } == true
        // STK-024: what pays the open payment lies in its own pile, to the right of its stack
        val paying = if (runtime.reviewing) emptySet() else toolkit?.paySources().orEmpty()
        val shownLayout = layoutShown.value
        // WEB-015 (owner 2026-11-16): on the desktop table the hand card the mouse is on is drawn over the others in the fan
        val laid = (if (paying.isEmpty()) shownLayout else shownLayout.copy(paying = paying))
            .let { if (desktop != null && handHover != it.hovered) it.copy(hovered = handHover) else it }
        // ZON-079 (owner, 2026-11-12): your cards from other zones a permission lets you cast or play, at your hand row's end
        // (worked out with the board; the engine's own casts from elsewhere join them once worked out)
        val cachedPlays = AutomatumBoards.cachedHandPlays(play, accountId)
        val handRow = remember(IdentityKey(play.board), play.values, cachedPlays, accountId, runtime.reviewing, toolkit) {
            if (runtime.reviewing || toolkit == null) emptyList() else HandRow.cards(play, accountId)
        }
        // A board the scene can't be built from (a state nobody tested, prod crash WCRF3Q) keeps the last scene shown, reported
        val lastScene = remember(play.playId, accountId) { LastScene() }
        // Web frame rate (owner, 2026-11-15): the scene as laid out now, with the camera, which only drawing, layout, hit testing
        // and what follows the cards read: a pan or zoom step lays the scene out again there and recomposes none of the table
        // (the camera is read only here). Composition reads it as it was when it last ran ([targetScene]), for everything but
        // where the battlefields' things are
        val camera = remember(layoutState) { derivedStateOf(structuralEqualityPolicy()) { layoutState.value.camera } }
        val sceneNow = remember(game, IdentityKey(play), accountId, maxWidth, maxHeight, landscape, laid, placements, toolbarWidth, runtime.reviewing, runtime.timelineOpen, cardAppearance, computed, attackerSlots, handRow, desktop) {
            val inputs = Any()
            derivedStateOf {
                val full = laid.copy(camera = camera.value)
                val built = try {
                    TablePresentation.scene(game, play, accountId, maxWidth.value.coerceAtLeast(1f),
                        maxHeight.value.coerceAtLeast(1f), landscape, full, placements, toolbarWidth, runtime.reviewing, cardAppearance, logicalPositions,
                        computed, landscapeHome = true, timeline = runtime.timelineOpen, attackerSlots = attackerSlots, handRow = handRow,
                        desktop = desktop).also { lastScene.scene = it }
                } catch (failure: RuntimeException) {
                    Faults.report("TableScene", "table scene not built", mapOf("game" to game.id, "node" to play.nodeId,
                        "failure" to failure.stackTraceToString().take(4_000)))
                    lastScene.scene ?: throw failure
                }
                LaidScene(built, full, maxWidth to maxHeight, inputs)
            }
        }
        val targetScene = Snapshot.withoutReadObservation { sceneNow.value.scene }
        // One interpolated scene feeds Canvas, hit testing AND semantic nodes. No destination-only overlays.
        val animation = remember(play.playId, accountId) { HistorySceneAnimation(targetScene) }
        val movePreferences = rememberCardMovePreferences(accountId)
        val moveMultiplier by movePreferences.multiplierState()
        SideEffect { animation.cardMoveSpeedDpPerMs = DEFAULT_CARD_MOVE_SPEED * moveMultiplier }
        val progress = remember(animation) { androidx.compose.animation.core.Animatable(1f) }
        // Bumped as the animation is retargeted: [animationRevision] recomposes (the semantics' items), [frameRevision] only
        // redraws (a retarget for a camera change alone)
        var animationRevision by remember(animation) { mutableIntStateOf(0) }
        val frameRevision = remember(animation) { mutableIntStateOf(0) }
        SideEffect { animation.updateMetadata(targetScene) }
        // ZON-079 (owner playtest 2026-11-04 #9): raising or lowering a hand card, opening or closing a battlefield group
        // (TBL-122), a source joining or leaving a payment's pile (STK-024) and the desktop hand card the mouse is on rising
        // (WEB-015) are the only layout changes that animate, at the card move speed; any other layout change (camera, panels)
        // or a new size jumps ([LaidBefore])
        val currentSceneNow by rememberUpdatedState(sceneNow)
        val laidBefore = remember(animation) { LaidBefore(Snapshot.withoutReadObservation { sceneNow.value }) }
        // CMB-001 (owner playtest 2026-11-16): whether the scene animated to last had the combat band open, for the log of
        // its opening and closing
        val bandShown = remember(animation) { booleanArrayOf(targetScene.combatPanel != null) }
        LaunchedEffect(animation, play.nodeId, runtime.sceneVersion) {
            snapshotFlow { currentSceneNow.value }
                .map { now -> laidBefore.next(now) }
                .distinctUntilChangedBy { it.now.scene.animationTargets }
                .collectLatest { step ->
                    val target = step.now.scene
                    val rt = currentRuntime
                    val settled = rt.onSceneSettled
                    fun bump() { frameRevision.intValue++; if (!step.cameraOnly) animationRevision++ }
                    val faceChanges = rt.changes?.active?.keys?.filter { it.kind == TableChangeKind.FACE }?.map { it.target }?.toSet().orEmpty()
                    val shuffles = if (step.navigation) emptySet() else rt.changes?.shuffles(play.nodeId).orEmpty()
                    if (animation.retarget(target, progress.value, faceChanges, shuffles, play.nodeId, rt.landedEntries.toSet(),
                            rt.takenBack)) {
                        DebugLatency.tapAnimation(game.id, animation.rotatingCardIds)
                        progress.snapTo(0f)
                        bump()
                        val instant = step.navigation || !step.raiseOnly && rt.changes?.animates(play.nodeId) == false
                        val millis = (animation.durationMillis * rt.sceneTimeShare).toInt().coerceAtLeast(1)
                        // CMB-001 (owner playtest 2026-11-16: the band closed at once for the attacker): how the band opens or
                        // closes reaches the app log, and why when it jumps
                        val bandOpen = target.combatPanel != null
                        if (bandOpen != bandShown[0]) {
                            bandShown[0] = bandOpen
                            AppLog.info("combat", "Combat band ${if (bandOpen) "opens" else "closes"} for ${accountId.take(8)} at " +
                                "${play.nodeId.take(8)}: " + if (!instant) "over $millis ms" else if (step.navigation)
                                    "at once (the layout or the table's size changed with it)" else "at once (a board that doesn't animate)")
                            TableProbes.bandMoved?.invoke(accountId, bandOpen, if (instant) 0 else millis)
                        }
                        if (instant) progress.snapTo(1f)
                        else progress.animateTo(1f, tween(millis, easing = androidx.compose.animation.core.LinearEasing))
                    } else if (progress.value < 1f) {
                        // Metadata can cancel this effect mid-scene, but cannot slow its existing clock.
                        progress.animateTo(1f, tween(animation.remainingDurationMillis(progress.value), easing = androidx.compose.animation.core.LinearEasing))
                    }
                    bump()
                    withFrameNanos { }
                    settled?.invoke(play.nodeId)
                }
        }
        // Only draw/layout/semantics (and the small open-overlay host) observe the clock. Sampling
        // and clipping are shared once per frame, not repeated for every accessibility child.
        val rendered = remember(animation, sceneNow, runtime.reviewing, IdentityKey(play), maxWidth, maxHeight) { derivedStateOf {
            frameRevision.intValue
            val target = sceneNow.value.scene
            val frame = animation.frame(progress.value, target)
            // CMB-001: the battlefields slide with their cards, so the combat band between them opens and closes with them
            val base = TablePresentation.clip(target.copy(items = frame.filter { it.alpha > 0f }
                .map { it.item.copy(bounds = it.bounds) }, fields = animation.fields(progress.value, target)), maxWidth.value, maxHeight.value)
            TableRenderFrame(base, frame.associate { it.item.id to it.alpha })
        } }
        val scene = targetScene
        JankTableState(runtime.reviewing,
            toolkit?.panel ?: "card".takeIf { toolkit?.capture != null },
            previewDragging, { progress.isRunning },
            { rendered.value.scene.items.count { it.kind == TableItemKind.CARD && it.visible.isNotEmpty() } })
        val semanticItems = remember(animation, IdentityKey(targetScene), animationRevision) {
            // Target metadata owns the accepted disclosure; start-frame metadata may still be a back
            // during a flip. Bounds continue to come exclusively from the rendered frame below.
            (targetScene.items + animation.frame(0f, targetScene).map { it.item }).distinctBy { it.id }
        }
        LaunchedEffect(IdentityKey(play), shownLayout.localPileId) {
            if (layout.localPileId != null && play.board.containers.none { it.id == layout.localPileId && it.cards.isNotEmpty() })
                layout = layout.copy(localPileId = null)
        }
        // The pictures at the sizes the cards are drawn now (a zoom asks again only past a size step, TABLE_ARTWORK_STEP)
        val artItems = remember(animation, sceneNow) { derivedStateOf {
            frameRevision.intValue
            val now = sceneNow.value.scene
            (now.items + animation.frame(0f, now).map { it.item }).distinctBy { it.id }
        } }
        val artwork = tableArtwork(artItems)
        val drawCache = remember(density, play.playId) { TableDrawCache(density) }
        val refreshRate = displayRefreshRate()
        val tableWidth = maxWidth.value; val tableHeight = maxHeight.value
        // The frame drawn last: the gestures go on from it while the camera moves (each pointer move of a pan writes the camera;
        // the scene is laid out again for the next frame drawn, not for every move in between)
        val drawnFrame = remember(rendered) { arrayOfNulls<TableRenderFrame>(1) }
        SideEffect {
            presentation = { rendered.value.scene }
            coordinator.update(scene, writable, sceneSource = { (drawnFrame[0] ?: rendered.value).scene }, reviewing = runtime.reviewing)
        }
        Box(Modifier.fillMaxSize().semantics {
            contentDescription = "Shared table"
            customActions = listOf(
                CustomAccessibilityAction("Zoom in") { coordinator.transform(TablePoint(0f, 0f), 1.2f, sceneNow.value.scene.fields.last().bounds.center); coordinator.cancel(); clockEpoch++; true },
                CustomAccessibilityAction("Zoom out") { coordinator.transform(TablePoint(0f, 0f), 1 / 1.2f, sceneNow.value.scene.fields.last().bounds.center); coordinator.cancel(); clockEpoch++; true },
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
            // Read while drawing: its fade (a turn's change) redraws the bars only, recomposing nothing
            val barColor = animateColorAsState(tableBarColor(play.board, accountId, runtime.reviewing), tween(200), label = "Table bars")
            val bars = Modifier.drawBehind { drawRect(barColor.value) }
            // A cast or an Automatum decision choosing targets (I-D6): its legal targets glow
            val castGlow = toolkit?.cast?.glow(play).orEmpty() + toolkit?.flows?.glow(play).orEmpty()
            // STK-047 (owner 2026-11-07): the targets picked so far show picked, alone or in an opened group (TBL-130)
            val pickedGlow = toolkit?.cast?.pickedGlow(play).orEmpty() + toolkit?.flows?.pickedGlow(play).orEmpty()
            // I-D46: the viewer's playable cards glow (live table only); a refused gesture shakes what it was made on.
            // TBL-117 (owner playtest 2026-11-04 #8): while Automatum works a new board out the cards that stayed where they
            // were keep their glow, and each glow fades in and out, so a card that stays playable never blinks
            val glow = rememberPlayableGlow(play, if (runtime.reviewing) emptySet() else toolkit?.playableOrNull(play))
            // TBL-117 (owner, 2026-11-17): the viewer's permanents whose only use now is mana glow fainter, fading as the cyan does
            val manaGlow = rememberPlayableGlow(play, if (runtime.reviewing) emptySet() else toolkit?.manaOnlyOrNull(play))
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
            // TBL-118 (owner 2026-11-19): the shake's reason rises from the item it shook (in the middle when the table doesn't
            // draw it)
            val refusalBubbles = LocalRefusalBubbles.current
            val canvasOrigin = remember { arrayOf(androidx.compose.ui.geometry.Offset.Zero) }
            LaunchedEffect(shaking) {
                val s = shaking ?: return@LaunchedEffect
                val item = rendered.value.scene.items.firstOrNull { it.id == s.id || it.cardId == s.id }
                val anchor = item?.bounds?.let { b -> androidx.compose.ui.geometry.Rect(canvasOrigin[0].x + b.x * density,
                    canvasOrigin[0].y + b.y * density, canvasOrigin[0].x + b.right * density, canvasOrigin[0].y + b.bottom * density) }
                refusalBubbles?.show(s.reason, anchor)
            }
            // TBL-117, TBL-122: what a tap on each collapsed group of several does, for its glow's colour (live table only)
            val groupTaps = if (toolkit == null || runtime.reviewing) emptyMap() else scene.battlefieldGroups
                .filter { it.members.size > 1 && it.key != scene.expandedGroup }.associate { it.key to toolkit.groupTap(play, it) }
            // TBL-130 (owner 2026-11-07): while targets are picked, the members of the opened group that aren't legal targets
            // are dimmed (as the Stack list's, STK-048)
            val dimmed = if (toolkit?.let { it.cast.targeting || it.flows.targeting } != true) emptySet() else scene.battlefieldGroups
                .firstOrNull { it.key == scene.expandedGroup }?.members.orEmpty().filterNotTo(HashSet()) { it in castGlow }
            // ZON-095 (owner notes 2026-11-14): while a cast, a choice or another panel is open the viewer's hand is dimmed
            val handDim = toolkit != null && !runtime.reviewing && toolkit.handDimmed(play)
            // TBL-117 (owner playtest 2026-11-06): the glow of the card a finger is down on grows a little and brightens
            val pressedId by remember(coordinator) { derivedStateOf { preview.pressedId } }
            val pressGlow = rememberPressGlow(pressedId)
            // WEB-016: on the desktop table both seat boxes, the action area and the timeline strip float over the battlefields,
            // drawn with the table ([TableScene.floats])
            if (desktopGeometry == null) {
            Box(Modifier.fillMaxWidth().height(48.dp).then(bars))
            Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(tableBottomHeight(landscape, runtime.timelineOpen).dp)
                .then(bars))
            }
            Canvas(Modifier.fillMaxSize().onGloballyPositioned { canvasOrigin[0] = it.positionInRoot() }) {
                DebugLatency.tapFrame(game.id, progress.value, refreshRate)
                val frame = rendered.value
                drawnFrame[0] = frame
                val glows = TableGlows.of(frame.scene.battlefieldGroups, groupTaps, glow.alphas(), castGlow, attackGlow, pickedGlow,
                    mana = manaGlow.alphas())
                val targetAlphas = if (dimmed.isEmpty()) frame.alphas else frame.alphas + frame.scene.items
                    .filter { it.battlefieldCard && it.cardId in dimmed }.associate { it.id to minOf(frame.alphas[it.id] ?: 1f, TARGET_DIMMED) }
                val alphas = if (!handDim) targetAlphas else targetAlphas + frame.scene.items
                    .filter { it.kind == TableItemKind.CARD && it.zone == PlayZone.HAND && it.owner == accountId }
                    .associate { it.id to minOf(targetAlphas[it.id] ?: 1f, HAND_DIMMED) }
                drawTable(frame.scene, accountId, play.board.turn.accountId, preview, alphas, artwork, drawCache,
                    presenceDot, glows.targets, glows.playable, shaking?.let { it.id to shakeOffset.value }?.takeIf { it.second != 0f }, raisedSources,
                    attackable = glows.attack, expandGlow = glows.expand, mana = glows.mana, pressed = pressGlow.value, picked = glows.picked, pileBadges = false)
            }
            // STK-047 (owner, 2026-11-12): a pile holding a card the viewer may target or choose glows over its whole face,
            // pulsing, in a layer of its own
            // Owner notes 2026-11-14: as does a closed group a tap spreads open to pick from (TBL-130)
            val spreadGroups = remember(IdentityKey(scene), groupTaps, castGlow) {
                TableGlows.of(scene.battlefieldGroups, groupTaps, emptyMap(), castGlow).spread
            }
            PileChoiceGlow(scene, castGlow, rendered, spreadGroups)
            TableSemanticNodes(game, play, scene, semanticItems, rendered, runtime, { currentRuntime }, coordinator, writable, toolkit,
                { preview }, layoutState, groupTaps, fieldsNow = { sceneNow.value.scene.fields })
            // Automatum (I3, I-D29): the damage preview and blockers blocking several attackers, for both players
            // I-D32: arrows of every block, and with Automatum the staging, selection, preview and badges
            // CMB-001 (owner playtest 2026-11-11): the chips on the band's upper edge move with the battlefield above it as
            // the band opens or closes
            val bandShift = { rendered.value.scene.fields.firstOrNull()?.gridBounds?.bottom
                ?.minus(sceneNow.value.scene.fields.firstOrNull()?.gridBounds?.bottom ?: 0f) ?: 0f }
            // What lies on or by the combat band, which pans and zooms with the battlefields: laid out with the camera as it is now
            CurrentScene(sceneNow) { now ->
                CombatAreaOverlay(toolkit?.combat, play, now, accountId, writable && !runtime.reviewing,
                    pressed = { pressGlow.value }, dragged = { preview.scope?.contextId }, bandShift = bandShift) { item ->
                    rendered.value.items[item.id]?.bounds ?: item.bounds }
            }
            // CMB-001 (owner playtest 2026-11-11): when the band closes, what sat on it or by it fades out where it was
            CurrentScene(sceneNow) { now -> ClosingCombatBand(play, now, accountId) { item -> rendered.value.items[item.id]?.bounds ?: item.bounds } }
            // STK-058 (owner playtest 2026-11-07): the top Stack entry's target arrows, for both players; not while the viewer
            // picks targets themselves (their violet glow, STK-047)
            val stackNames = remember(game) { gameNames(game) }
            // Stack animation (owner spec 2026-11-14): on the live table they grow in step 4 and fade in step 5
            if (toolkit?.let { it.cast.targeting || it.flows.targeting } != true)
                StackTargetArrows(play, scene, accountId, stackNames,
                    LocalEngineRoutes.current?.stackAnimation?.takeIf { !runtime.reviewing }, moveMultiplier) { item ->
                    rendered.value.items[item.id]?.bounds ?: item.bounds }
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
            // WEB-026: the quick look of the card the mouse rests on, in a scope of its own: its coming and going recomposes only
            // it. Inside the table's own box, whose mouse and gestures get every move and click over it: nothing to press, it
            // never takes them from the table (as a box over it, the mouse moving onto it kept it shown and a click on the card
            // under it was lost, e2e GiftTargetPhraseDesktopTest)
            if (desktopGeometry != null && toolkit != null) QuickLookHost(mouse) { item ->
                DesktopQuickLook(play, accountId, item, toolkit, desktopGeometry.tableRight, tableHeight)
            }
        }
        CompositionLocalProvider(LocalDesktopGeometry provides desktopGeometry) {
        TableBoardOverlays(game, play, accountId, runtime, scene, rendered, sceneNow)
        // WEB-020: on the desktop table the top row is the screen's, above the table
        if (desktopGeometry == null) TableChromeHeader(game, play, accountId, landscape, runtime, toolkit, compactHeader, scene.appearance)
        CardPanelHeaders(scene, accountId, toolkit)
        TableControlsLayer(game, play, accountId, landscape, connected, runtime, toolkit, scene)
        // WEB-017: the right chat dock, under the table's popups and panels
        if (desktopGeometry != null && desktop.dockOpen) runtime.desktopDock?.let { dock ->
            Box(Modifier.align(Alignment.TopEnd).width(DesktopTable.DOCK.dp).height((maxHeight.value - desktopGeometry.band).coerceAtLeast(0f).dp)) { dock() }
        }
        // TBL-067: table popups keep off both hands where they fit elsewhere
        val hands = remember(IdentityKey(scene)) { scene.items.filter { it.kind == TableItemKind.HAND }.map { it.bounds } }
        AnimatedCardRingAnchors(rendered, toolkit?.capture != null) {
         // Its chips and panels by the cards (a battlefield card's ability chips, what is attached to the combat band) follow the
         // camera: laid out with it as it is now
         if (toolkit != null) CompositionLocalProvider(LocalPopupAvoid provides hands) { CurrentScene(sceneNow) { now ->
             TableToolkitScreen(toolkit, play, game, landscape,
              peekScroll = peekScroll, scene = now, peekSwipe = peekSwipe, onTimeline = runtime.history,
              reviewing = runtime.reviewing, gameActionsBlocked = runtime.gameActionsBlocked)
         } }
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

/** ZON-095 (owner notes 2026-11-14): the viewer's hand while a cast, a choice or another panel is open. */
private const val HAND_DIMMED = .4f

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

/** Test probe (web frame rate, 2026-11-15): told each time a table's own scope recomposes, with the account it is drawn for. */
object TableProbes {
    @kotlin.concurrent.Volatile var composed: ((me: String) -> Unit)? = null
    /** Told each time a table is made (HIS-113: a table made anew mid-game drops a finger held on it), with its account. */
    @kotlin.concurrent.Volatile var made: ((me: String) -> Unit)? = null
    /** Told each time a table's combat target chips begin to fade in (CMB-001: once per combat), with its account and the
     * combat ([combatBandKey]). */
    @kotlin.concurrent.Volatile var chipsFadeIn: ((me: String, combat: String) -> Unit)? = null
    /** CMB-054, CMB-055, TBL-205: told each time a table's combat band begins to open ([open]) or close, with its account and how
     * long its animation is ([millis]; 0: at once). */
    @kotlin.concurrent.Volatile var bandMoved: ((me: String, open: Boolean, millis: Int) -> Unit)? = null
    /** CMB-055: told each time what sat on a closing combat band begins to fade out where it was, with its account, how many
     * target chips fade and how long the fade is. */
    @kotlin.concurrent.Volatile var chipsFadeOut: ((me: String, chips: Int, millis: Int) -> Unit)? = null
}

/** WEB-026: [content] for the card whose quick look [mouse] shows, if any; read here alone (web frame rate, 2026-11-15). */
@Composable
private fun QuickLookHost(mouse: DesktopTableMouse, content: @Composable (TableItem) -> Unit) { mouse.quickLook?.let { content(it) } }

/** The last table scene built, shown again while a newer board's can't be built. */
private class LastScene { var scene: TableScene? = null }

/** A table scene as laid out ([TableSurface]'s sceneNow): [laid] its layout (with the camera), [size] the table's, [inputs] the
 * composition's inputs it was built from (the same object while only the camera has changed). */
class LaidScene(val scene: TableScene, val laid: TableLayoutState, val size: Pair<androidx.compose.ui.unit.Dp, androidx.compose.ui.unit.Dp>,
    val inputs: Any)

/** A change of the table's scene as its animation takes it: [navigation] (the camera or another layout change but a raised
 * card, an opened group or a payment's pile, or a new size: it jumps), [raiseOnly] (only those: it animates), [cameraOnly]
 * (the camera alone: nothing composition reads changed). */
class SceneStep(val now: LaidScene, val navigation: Boolean, val raiseOnly: Boolean, val cameraOnly: Boolean)

/** The scene laid out before ([next] compares each with it, as the table's layout changes). */
private class LaidBefore(private var before: LaidScene) {
    fun next(now: LaidScene): SceneStep {
        val was = before
        before = now
        val raiseOnly = was.laid != now.laid &&
            was.laid.copy(raised = now.laid.raised, expandedGroup = now.laid.expandedGroup, paying = now.laid.paying,
                hovered = now.laid.hovered) == now.laid
        val navigation = was.laid != now.laid && !raiseOnly || was.size != now.size
        val cameraOnly = was.inputs === now.inputs && was.size == now.size && was.laid.copy(camera = now.laid.camera) == now.laid
        return SceneStep(now, navigation, raiseOnly, cameraOnly)
    }
}

/** [content] with the table's scene as laid out now ([now], the camera's place too): only this recomposes as the camera moves,
 * for what lies by the battlefields' cards and follows them (web frame rate, 2026-11-15). */
@Composable
fun CurrentScene(now: State<LaidScene>, content: @Composable (TableScene) -> Unit) { content(now.value.scene) }

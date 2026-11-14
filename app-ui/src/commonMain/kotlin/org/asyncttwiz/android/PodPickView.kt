package org.asyncttwiz.android

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.isPrimaryPressed
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import org.asyncttwiz.protocol.*
import org.asyncttwiz.appui.BackHandler
import kotlin.math.abs
import kotlin.math.roundToInt
import org.asyncttwiz.appui.AppPlatform
import org.asyncttwiz.appui.Preferences
import org.asyncttwiz.appui.ScreenOrientation
import org.asyncttwiz.appui.screenOrientation
import org.asyncttwiz.appui.passThroughPopupProperties

val PodPickGreen = Color(0xFF237A38)
val PodPickRed = Color(0xFFB3261E)

/** Without [onMarks] the marks live only in this composition (previews and tests). */
@Composable
fun PodPickView(
    pack: DraftPrivatePack?,
    settings: DraftSettings,
    definitions: Map<String, CardDefinition>,
    busy: Boolean,
    onConfirm: (keep: List<String>, burn: List<String>) -> Unit,
    modifier: Modifier = Modifier,
    librarians: List<DraftCopy> = emptyList(),
    onConfirmLibrarians: ((List<String>, List<String>, List<String>) -> Unit)? = null,
    nextPack: DraftPrivatePack? = null,
    columns: PodPickColumns = rememberPodPickColumns(),
    waitingText: String = "Waiting for other players",
    onMainMenu: (() -> Unit)? = null,
    savedMarks: PodPickMarks? = null,
    onMarks: ((PodPickMarks) -> Unit)? = null,
    /** One line above the cards, e.g. "Round 2/6 · Pick 2 · from Matti"; scrolls away with them. */
    orientation: String? = null,
) {
    val localMarks = remember { mutableStateMapOf<DraftPrivatePack, PodPickMarks>() }
    val marks = ((if (onMarks != null) savedMarks else localMarks[pack]) ?: PodPickMarks()).forPack(pack)
    val setMarks: (PodPickMarks) -> Unit = { next -> if (onMarks != null) onMarks(next) else if (pack != null) localMarks[pack] = next }
    var inspected by remember(pack?.id) { mutableStateOf<DraftCopy?>(null) }
    // WEB-024: the pack card under the mouse (desktop layout)
    var hovered by remember(pack?.id) { mutableStateOf<String?>(null) }
    // One card menu for the whole screen: the latest tap wins over simultaneous ones.
    var menuFor by remember(pack?.id) { mutableStateOf<String?>(null) }
    var tileSize by remember { mutableStateOf(IntSize.Zero) }
    val previewScroll = rememberScrollState()
    val previewScreen = holdPreviewScreenHeight()
    PodArtPrefetch(remember(pack, nextPack) { listOfNotNull(pack, nextPack).flatMap { it.cards } }, definitions, tileSize)
    if (pack == null) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) { PodPickWaiting(waitingText, onMainMenu) }
        return
    }

    // Spent Librarians join the pack as ordinary cards for this pick: keep one back, burn it or leave it to pass on.
    val shown = remember(pack, marks.librarians, librarians) { pack.withSpentLibrarians(marks.librarians, librarians) }
    val rules = remember(pack, settings, marks.librarians) { PodPickRules(pack, settings, marks.librarians) }
    val baseRules = remember(pack, settings) { PodPickRules(pack, settings) }
    val columnCount = columns.columns(shown.cards.size)
    val burnIds = rules.burnIds(marks)
    val copies = remember(shown) { shown.cards.associateBy { it.id } }
    val refund: (String) -> Unit = { setMarks(marks.refundLibrarian(it, pack, settings)) }
    val mark: (String, PodPickMark) -> Unit = { id, kind ->
        setMarks(marks.toggle(id, kind, rules))
    }
    val unmark: (String) -> Unit = { id -> setMarks(marks.unmark(id)) }
    val inspect: (DraftCopy, Boolean) -> Unit = { copy, open ->
        if (open) {
            previewScroll.dispatchRawDelta(-previewScroll.value.toFloat())
            menuFor = null
            inspected = copy
        } else if (inspected == copy) inspected = null
    }
    // WEB-024, WEB-031 (desktop layout): the card under the pointer: holding Alt (Shift too) inspects it, a tap of Alt pins it
    // (Alt again or Esc closes it); K / B / U keep, burn or unmark it
    val desktop = LocalAppLayout.current == AppLayout.Desktop
    val fingerKey = remember { DesktopFingerKey() }
    var pinned by remember(pack.id) { mutableStateOf(false) }
    if (inspected == null && pinned) pinned = false
    BackHandler(enabled = desktop && pinned) { AppLog.info("pod", "Pinned Inspect closed by Esc"); inspected = null }
    val bindings = currentKeyBindings()
    val confirm: () -> Unit = {
        if (rules.canConfirm(marks, busy)) {
            if (onConfirmLibrarians != null) onConfirmLibrarians(marks.keeps.toList(), rules.burnIds(marks).toList(), marks.librarians.toList())
            else onConfirm(marks.keeps.toList(), rules.burnIds(marks).toList())
        }
    }
    DesktopKeyboard.Listen(desktop) { key ->
        // WEB-044 (D9): Enter is Confirm, when it can be pressed; not while a text field has the keyboard
        if (key.key == "Enter") {
            if (!key.down || key.textField) return@Listen false
            if (!key.repeat) {
                AppLog.info("pod", "Hotkey Enter on the pick: ${if (rules.canConfirm(marks, busy)) "Confirm (keeps ${marks.keeps}, burns ${rules.burnIds(marks)})" else "Confirm not ready"}")
                confirm()
            }
            return@Listen true
        }
        fun apply(effects: List<DesktopFingerKey.Effect>) {
            if (effects.isNotEmpty()) AppLog.info("pod", "Finger key ${key.key} ${if (key.down) "down" else "up"}${hovered?.let { " over $it" }.orEmpty()}: ${effects.joinToString()}")
            for (effect in effects) when (effect) {
                is DesktopFingerKey.Effect.Inspect -> copies[effect.cardId]?.let { inspect(it, true) }
                DesktopFingerKey.Effect.Pin -> pinned = true
                DesktopFingerKey.Effect.Close -> inspected = null
                is DesktopFingerKey.Effect.Pause -> Unit
            }
        }
        if (key.key == "Alt" || key.key == "Shift") {
            if (key.repeat) return@Listen fingerKey.held
            if (key.down && key.textField) return@Listen false
            val effects = if (key.down) fingerKey.press(key.key, key.time, hovered, inspected != null) else fingerKey.release(key.key, key.time)
            apply(effects)
            return@Listen effects.isNotEmpty() || fingerKey.held
        }
        if (key.down && !key.repeat) apply(fingerKey.chord())
        val action = hotkeyOf(key, bindings)
        val id = hovered?.takeIf { it in copies }
        if (action == null || id == null || key.repeat) return@Listen false
        when (action) {
            HotkeyAction.Keep -> mark(id, PodPickMark.KEEP)
            HotkeyAction.Burn -> if (rules.manualBurns > 0) mark(id, PodPickMark.BURN)
            HotkeyAction.Unmark -> unmark(id)
            else -> return@Listen false
        }
        AppLog.info("pod", "Hotkey ${key.key} on $id: ${action.id}")
        true
    }

    Column(modifier.fillMaxSize().padding(horizontal = 8.dp)) {
        key(pack.id) {
            LazyVerticalGrid(GridCells.Fixed(columnCount), Modifier.weight(1f).fillMaxWidth().testTag("pod-pick-list"),
                state = rememberLazyGridState(), horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp), contentPadding = PaddingValues(vertical = 4.dp)) {
                if (orientation != null) item(key = "orientation", span = { GridItemSpan(maxLineSpan) }) {
                    Text(orientation, Modifier.fillMaxWidth().testTag("pod-pick-orientation"),
                        color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelMedium,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                items(shown.cards, key = { it.id }) { copy ->
                    PodPickCard(copy, definitions[copy.definition], marks.markOf(copy.id),
                        automaticBurn = rules.automaticBurns && copy.id in burnIds,
                        burnEnabled = rules.manualBurns > 0,
                        onMark = { mark(copy.id, it) }, onUnmark = { unmark(copy.id) },
                        onInspect = { inspect(copy, it) },
                        onInspectScroll = { previewScroll.holdPreviewScroll(it, previewScreen) },
                        menu = menuFor == copy.id,
                        onMenu = { open ->
                            if (open) { inspected = null; menuFor = copy.id } else if (menuFor == copy.id) menuFor = null
                        },
                        // The first column's size is stable while scrolling; prefetch decodes at it.
                        onSize = { if (copy == shown.cards.first()) tileSize = it },
                        onRefund = if (copy.id in marks.librarians) { { refund(copy.id) } } else null,
                        onHover = { over -> if (over) hovered = copy.id else if (hovered == copy.id) hovered = null },
                        desktop = desktop, hovered = desktop && hovered == copy.id)
                }
            }
        }
        PodPickTrays(marks.keeps, burnIds, rules, baseRules, copies, definitions, unmark, inspect,
            onInspectScroll = { previewScroll.holdPreviewScroll(it, previewScreen) },
            librarianSlots = settings.librarians > 0,
            spent = marks.librarians, librarians = librarians,
            spend = { setMarks(marks.spendLibrarian(librarians.map { it.id }, pack, settings)) },
            refund = refund, desktop = desktop)
        Button(onClick = confirm, enabled = rules.canConfirm(marks, busy), modifier = Modifier.fillMaxWidth()) {
            ButtonLabel("ready", if (marks.librarians.isEmpty()) "Confirm" else
                "Confirm · ${marks.librarians.size} ${if (marks.librarians.size == 1) "Librarian" else "Librarians"}")
        }
    }
    inspected?.let { PodPickArtPreview(it, definitions[it.definition], previewScroll) }
}

@Composable
private fun rememberPodPickPreferences() = AppPlatform.current.let { platform ->
    remember(platform) { platform.preferences("pod-pick") }
}

/** Personal per-orientation cards-per-row choice, hoisted so the header's view settings panel can change it. */
@Stable
class PodPickColumns constructor(private val preferences: Preferences, private val key: String, default: Int) {
    var preferred by mutableIntStateOf(preferences.getInt(key, default))
        private set
    fun choose(value: Int) { preferred = value.coerceIn(PodPickColumnRange); preferences.edit().putInt(key, preferred).apply() }
    fun columns(cards: Int) = preferred.coerceIn(1, cards.coerceAtLeast(1))
}

@Composable
fun rememberPodPickColumns(): PodPickColumns {
    val landscape = screenOrientation() == ScreenOrientation.LANDSCAPE
    val preferences = rememberPodPickPreferences()
    // WEB-044 (§7.6): the desktop layout keeps its own choice, more cards per row by default (wireframe D9)
    val desktop = desktopLayout()
    val key = if (desktop) "columns-desktop" else if (landscape) "columns-landscape" else "columns-portrait"
    return remember(preferences, key) { PodPickColumns(preferences, key, if (desktop) 8 else if (landscape) 5 else 3) }
}

const val PodHoldMillis = 150L
const val PodHoldSettleMillis = 300L

/** One recognizer owns tap, horizontal swipe and the short, release-to-close hold.
 * For [PodHoldSettleMillis] after the preview appears, movement past slop closes it and is
 * left to the swipe or the underlying scroll; a settled hold pans the preview and only lifting
 * the finger closes it (DRF-063, owner playtest 2026-11-09: a drag back past where the hold
 * began closed it). */
suspend fun PointerInputScope.podPickGestures(
    onTap: () -> Unit,
    onInspect: (Boolean) -> Unit,
    onInspectScroll: (Float) -> Unit = {},
    onDrag: ((Float) -> Unit)? = null,
    onDragEnd: (Float) -> Unit = {},
    onEnd: () -> Unit = {},
    /** The desktop layout's mouse (§5): a left click does this, however long it is held, at its place on the card; the
     * mouse never holds, swipes or inspects (Alt does, WEB-024). Null: the mouse works as a finger (the phone layout). */
    mouseClick: ((Offset) -> Unit)? = null,
    /** With [mouseClick]: a left-button drag past the slop drags the card ([MouseDrag]) instead of clicking. */
    mouseDrag: MouseDrag? = null,
) = awaitEachGesture {
    val down = awaitFirstDown(requireUnconsumed = false)
    val slop = viewConfiguration.touchSlop
    if (mouseClick != null && down.type == PointerType.Mouse) {
        // Another button (the right one's burn, its own handler's), or a press something else took: nothing here
        if (down.isConsumed || !currentEvent.buttons.isPrimaryPressed) return@awaitEachGesture
        down.consume()
        var dragging = false
        var ended = false
        try {
            while (true) {
                val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: return@awaitEachGesture
                change.consume()
                if (!dragging && mouseDrag != null && (change.position - down.position).getDistance() > slop) dragging = true
                if (dragging) mouseDrag?.move?.invoke(change.position)
                if (!change.pressed) {
                    if (dragging) { ended = true; mouseDrag?.end?.invoke(true) }
                    else if ((change.position - down.position).getDistance() <= slop) mouseClick(change.position)
                    return@awaitEachGesture
                }
            }
        } finally {
            if (dragging && !ended) mouseDrag?.end?.invoke(false)
        }
    }
    var dragging = false
    var held = false
    var settled = false
    var done = false
    var released = false
    var delta = Offset.Zero
    suspend fun AwaitPointerEventScope.next() {
        val event = awaitPointerEvent()
        val change = event.changes.firstOrNull { it.id == down.id }
        if (change == null || change.isConsumed || event.changes.size > 1) {
            if (held) AppLog.info("hold", "Card hold ended: " + when {
                change == null -> "finger gone"; change.isConsumed -> "taken by another gesture"; else -> "a second finger" })
            done = true; return
        }
        delta = change.position - down.position
        val horizontal = abs(delta.x) > abs(delta.y)
        when {
            dragging -> { change.consume(); onDrag?.invoke(delta.x) }
            settled -> { onInspectScroll(-change.positionChange().y); change.consume() }
            // Movement wins over the timers, even on a late input frame.
            delta.getDistance() > slop -> if (onDrag != null && horizontal) {
                if (held) { held = false; onInspect(false); AppLog.info("hold", "Card hold ended early: the finger moved sideways, a swipe") }
                dragging = true
                change.consume(); onDrag(delta.x)
            } else {
                if (held) AppLog.info("hold", "Card hold ended early: the finger moved, a scroll")
                done = true // Unconsumed, so the list or tray scroll continues from here.
            }
        }
        if (change.changedToUpIgnoreConsumed()) {
            if (!done && !dragging && !held) onTap()
            if (!done) change.consume()
            released = true
            done = true
        }
    }
    try {
        if (withTimeoutOrNull(PodHoldMillis) { while (!done && !dragging) next() } == null) {
            held = true
            onInspect(true)
            if (withTimeoutOrNull(PodHoldSettleMillis) { while (!done && !dragging) next() } == null) {
                settled = true
                AppLog.info("hold", "Card hold settled: only lifting the finger closes Inspect")
            }
        }
        while (!done) next()
        if (released && dragging) onDragEnd(delta.x)
    } finally {
        if (held) { onInspect(false); if (released) AppLog.info("hold", "Card hold ended: finger lifted") }
        onEnd()
    }
}

/** A card dragged with the mouse's left button (desktop layout): [move] with the pointer's place on the card as it goes,
 * [end] when the button is released ([dropped]) or the drag is cancelled. */
class MouseDrag(val move: (Offset) -> Unit, val end: (dropped: Boolean) -> Unit)

@Composable
fun PodPickWaiting(text: String = "Waiting for other players", onMainMenu: (() -> Unit)? = null) {
    Column(Modifier.padding(horizontal = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text, Modifier.testTag("pod-waiting"), color = MaterialTheme.colorScheme.onSurface.copy(alpha = .6f),
            textAlign = TextAlign.Center)
        if (onMainMenu != null) TextButton(onMainMenu) { ButtonLabel("menu", "Main menu") }
    }
}

@Composable
private fun PodPickTrays(
    keeps: List<String>, burns: List<String>, rules: PodPickRules, baseRules: PodPickRules,
    copies: Map<String, DraftCopy>, definitions: Map<String, CardDefinition>,
    onUnmark: (String) -> Unit, onInspect: (DraftCopy, Boolean) -> Unit,
    onInspectScroll: (Float) -> Unit, librarianSlots: Boolean,
    spent: List<String>, librarians: List<DraftCopy>, spend: () -> Unit, refund: (String) -> Unit, desktop: Boolean = false,
) {
    // Freeze geometry for the pack. Extra slots and the chip pan within the original keep tray.
    val keepSlots = podPickKeepSlots(baseRules.requiredKeeps, librarianSlots)
    BoxWithConstraints(Modifier.fillMaxWidth().padding(top = 4.dp)) {
        val layout = podPickTrayLayout(maxWidth, keepSlots, baseRules.requiredBurns)
        val keep: @Composable () -> Unit = {
            PodPickTray("Keep", keeps, rules.requiredKeeps, copies, definitions, PodPickGreen,
                false, Modifier.width(layout.keepWidth), onUnmark, onInspect, onInspectScroll, desktop = desktop,
                visibleIds = keeps.take(baseRules.requiredKeeps)) {
                spent.forEachIndexed { index, id ->
                    val copy = keeps.getOrNull(baseRules.requiredKeeps + index)?.let(copies::get)
                    PodLibrarianSlot(id, copy, copy?.let { definitions[it.definition] }, { refund(id) }, onInspect, desktop)
                }
                librarians.firstOrNull()?.let { librarian ->
                    val left = librarians.size - spent.size
                    val currentSpend by rememberUpdatedState(spend)
                    val currentInspect by rememberUpdatedState(onInspect)
                    Surface(shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, PodPickGreen), modifier = Modifier.height(48.dp)
                            .semantics {
                                role = Role.Button
                                contentDescription = "Librarian, $left left"
                                onClick { if (left > 0) currentSpend(); true }
                            }
                            .pointerInput(left, desktop) { podPickGestures(onTap = { if (left > 0) currentSpend() },
                                onInspect = { currentInspect(librarian, it) }, onInspectScroll = onInspectScroll,
                                mouseClick = if (desktop) ({ _ -> if (left > 0) currentSpend() }) else null) }) {
                        Row(Modifier.padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Icon(Icons.Default.MenuBook, null, Modifier.size(16.dp))
                            Text("$left")
                        }
                    }
                }
            }
        }
        val burn: @Composable () -> Unit = {
            if (baseRules.requiredBurns > 0) PodPickTray("Burn", burns, rules.requiredBurns, copies, definitions, PodPickRed,
                rules.automaticBurns, Modifier.width(layout.burnWidth), onUnmark, onInspect, onInspectScroll, desktop = desktop)
        }
        if (layout.singleRow) {
            Row(horizontalArrangement = Arrangement.spacedBy(PodPickTrayGap)) { keep(); burn() }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(PodPickTrayGap)) { keep(); burn() }
        }
    }
}

@Composable
private fun PodLibrarianSlot(id: String, copy: DraftCopy?, definition: CardDefinition?, refund: () -> Unit,
    inspect: (DraftCopy, Boolean) -> Unit, desktop: Boolean = false) {
    val color = PodPickGreen
    val currentRefund by rememberUpdatedState(refund)
    val currentInspect by rememberUpdatedState(inspect)
    Surface(modifier = Modifier.width(PodPickTrayCardWidth).height(48.dp)
        .testTag("pod-librarian-slot-$id").semantics {
            role = Role.Button
            contentDescription = "Refund Librarian"
            onClick { currentRefund(); true }
        }
        .pointerInput(copy, desktop) { podPickGestures(onTap = { currentRefund() },
            onInspect = { open -> copy?.let { currentInspect(it, open) } },
            mouseClick = if (desktop) ({ _ -> currentRefund() }) else null) }) {
        Box {
            if (copy != null) PodPickArtwork(copy, definition, Modifier.fillMaxSize())
            Canvas(Modifier.matchParentSize()) {
                drawRoundRect(color, style = Stroke(2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx()))))
            }
            Icon(Icons.Default.MenuBook, "Librarian", Modifier.align(Alignment.TopEnd).background(color).size(14.dp), tint = Color.White)
        }
    }
}

@Composable
private fun PodPickCard(
    copy: DraftCopy,
    definition: CardDefinition?,
    mark: PodPickMark?,
    automaticBurn: Boolean,
    burnEnabled: Boolean,
    onMark: (PodPickMark) -> Unit,
    onUnmark: () -> Unit,
    onInspect: (Boolean) -> Unit,
    onInspectScroll: (Float) -> Unit,
    menu: Boolean,
    onMenu: (Boolean) -> Unit,
    onSize: (IntSize) -> Unit,
    /** Present for a spent Librarian shown in the pack: takes it back out (refund). */
    onRefund: (() -> Unit)? = null,
    /** WEB-024: the mouse came onto the card (true) or left it (desktop layout). */
    onHover: (Boolean) -> Unit = {},
    /** The desktop layout (§5, WEB-044): a click keeps, the ⋯ of the [hovered] card opens its menu. */
    desktop: Boolean = false,
    hovered: Boolean = false,
) {
    val currentOnMenu by rememberUpdatedState(onMenu)
    var swipe by remember { mutableStateOf(PodPickSwipeState()) }
    var width by remember { mutableIntStateOf(0) }
    var liftedMark by remember { mutableStateOf<PodPickMark?>(null) }
    val copyAlpha = remember { Animatable(1f) }
    var flashMark by remember { mutableStateOf(PodPickMark.KEEP) }
    var flashSequence by remember { mutableIntStateOf(0) }
    val flashAlpha = remember { Animatable(0f) }
    val currentOnMark by rememberUpdatedState(onMark)
    val currentOnInspect by rememberUpdatedState(onInspect)
    val effectiveMark = mark ?: PodPickMark.BURN.takeIf { automaticBurn }
    val currentMark by rememberUpdatedState(effectiveMark)
    val selection = updateTransition(effectiveMark, label = "Pod pick selection")
    val keepAlpha by selection.animateFloat({ tween(200) }, label = "Keep") { if (it == PodPickMark.KEEP) 1f else 0f }
    val burnAlpha by selection.animateFloat({ tween(200) }, label = "Burn") { if (it == PodPickMark.BURN) 1f else 0f }
    LaunchedEffect(swipe.phase, swipe.generation) {
        if (swipe.phase == PodPickSwipePhase.FADING) {
            val fading = swipe
            copyAlpha.snapTo(1f)
            copyAlpha.animateTo(0f, tween(150))
            if (swipe == fading) swipe = PodPickSwipeState()
        } else {
            copyAlpha.snapTo(1f)
        }
    }
    LaunchedEffect(flashSequence) {
        if (flashSequence > 0) {
            flashAlpha.snapTo(.4f)
            flashAlpha.animateTo(0f, tween(200))
        }
    }
    // WEB-024: hovering the card or its ⋯ is hovering the card
    Box(if (desktop) Modifier.mouseHover(copy.id, onHover) else Modifier) {
        Box(Modifier.fillMaxWidth().aspectRatio(1f / 1.4f).onSizeChanged { width = it.width; onSize(it) }
            .testTag("pod-pick-card-${copy.id}").semantics {
                contentDescription = definition?.name ?: "Card data unavailable"
                stateDescription = when (effectiveMark) {
                    PodPickMark.KEEP -> "Keep"
                    PodPickMark.BURN -> if (automaticBurn) "Automatic burn" else "Burn"
                    null -> "Unmarked"
                }
                onClick { currentOnMenu(true); true }
            }
            // WEB-028 (owner, §5): on the desktop layout a right-click burns the card (again: unmarks it), as a swipe left does
            .then(if (!desktop) Modifier else Modifier.desktopControl(listOf(copy.id, burnEnabled), onRight = {
                if (currentMark == PodPickMark.BURN && !automaticBurn) { AppLog.info("pod", "Right-click unmarked ${copy.id}"); onUnmark() }
                else if (burnEnabled && currentMark != PodPickMark.BURN) {
                    AppLog.info("pod", "Right-click burned ${copy.id}")
                    flashMark = PodPickMark.BURN
                    flashSequence++
                    currentOnMark(PodPickMark.BURN)
                }
            }))
            .pointerInput(burnEnabled, desktop) {
                podPickGestures(onTap = { currentOnMenu(true) }, onInspect = { currentOnInspect(it) }, onInspectScroll = onInspectScroll,
                    // WEB-044 (owner, §5): on the desktop layout a click keeps the card (again: unmarks it; a burned one switches
                    // to keep), as a swipe to the right does
                    mouseClick = if (!desktop) null else ({ _ ->
                        AppLog.info("pod", "Click ${if (currentMark == PodPickMark.KEEP) "unmarked" else "kept"} ${copy.id}")
                        if (currentMark != PodPickMark.KEEP) { flashMark = PodPickMark.KEEP; flashSequence++ }
                        currentOnMark(PodPickMark.KEEP)
                    }),
                    onDrag = {
                        if (swipe.phase != PodPickSwipePhase.DRAGGING) liftedMark = currentMark
                        swipe = swipe.drag(it)
                    },
                    onDragEnd = { drag ->
                        swipe = swipe.drag(drag).finish(width, burnEnabled)
                        swipe.decision?.let {
                            flashMark = it
                            flashSequence++
                            currentOnMark(it)
                        }
                    },
                    onEnd = { swipe = swipe.finish(width, burnEnabled, cancelled = true) },
                )
            }) {
            PodPickCardFace(copy, definition, keepAlpha, burnAlpha,
                (if (flashMark == PodPickMark.KEEP) PodPickGreen else PodPickRed).copy(alpha = flashAlpha.value))
            if (onRefund != null) Icon(Icons.Default.MenuBook, "Spent Librarian", Modifier.align(Alignment.TopStart)
                .background(PodPickGreen, RoundedCornerShape(4.dp)).padding(2.dp).size(16.dp), tint = Color.White)
        }
        // WEB-044 (§12 item 8): the hovered card's ⋯ opens its menu (Unmark, Refund Librarian)
        if (hovered) Surface(Modifier.align(Alignment.TopEnd).padding(top = 32.dp, end = 4.dp).size(28.dp)
            .testTag("pod-pick-more-${copy.id}").clickable(onClickLabel = "Card menu", role = Role.Button) {
                AppLog.info("pod", "⋯ opened the menu of ${copy.id}"); currentOnMenu(true)
            }, shape = CircleShape, color = Color.White, border = BorderStroke(1.dp, ScreenBorder), shadowElevation = 2.dp) {
            Box(contentAlignment = Alignment.Center) { Text("⋯", color = Color(0xff222222), fontWeight = FontWeight.Bold) }
        }
        if (swipe.phase != PodPickSwipePhase.IDLE && width > 0) {
            PodPickSwipeCopy(copy, definition, width, swipe, liftedMark,
                if (swipe.phase == PodPickSwipePhase.DRAGGING) 1f else copyAlpha.value, burnEnabled)
        }
        PodCardMenu(menu, dismiss = { onMenu(false) }) {
            if (mark != PodPickMark.KEEP) DropdownMenuItem(text = { Text("Keep") }, onClick = { onMenu(false); onMark(PodPickMark.KEEP) })
            if (burnEnabled && mark != PodPickMark.BURN) DropdownMenuItem(text = { Text("Burn") }, onClick = { onMenu(false); onMark(PodPickMark.BURN) })
            if (mark != null) DropdownMenuItem(text = { Text("Unmark") }, onClick = { onMenu(false); onUnmark() })
            if (onRefund != null) DropdownMenuItem(text = { Text("Refund Librarian") }, onClick = { onMenu(false); onRefund() })
        }
    }
}

@Composable
private fun PodPickCardFace(
    copy: DraftCopy, definition: CardDefinition?, keepAlpha: Float, burnAlpha: Float,
    tint: Color = Color.Transparent, reuseArtwork: Boolean = false,
) {
    Box(Modifier.fillMaxSize().clip(RoundedCornerShape(4.dp)).background(MaterialTheme.colorScheme.surfaceVariant)) {
        PodPickArtwork(copy, definition, Modifier.fillMaxSize(), reuseArtwork, crop = true)
        Box(Modifier.matchParentSize().background(tint, RoundedCornerShape(4.dp)))
        listOf(PodPickMark.KEEP to keepAlpha, PodPickMark.BURN to burnAlpha).forEach { (kind, alpha) ->
            if (alpha > 0f) {
                val color = if (kind == PodPickMark.KEEP) PodPickGreen else PodPickRed
                Box(Modifier.matchParentSize().graphicsLayer { this.alpha = alpha }
                    .border(3.dp, color, RoundedCornerShape(4.dp))) {
                    Text(if (kind == PodPickMark.KEEP) "✓" else "🔥",
                        Modifier.align(Alignment.TopEnd).graphicsLayer {
                            scaleX = .85f + .15f * alpha
                            scaleY = scaleX
                        }.background(color).padding(4.dp), color = Color.White)
                }
            }
        }
    }
}

/** A non-touchable window escapes grid clipping without taking the next card's touches. */
@Composable
private fun PodPickSwipeCopy(
    copy: DraftCopy, definition: CardDefinition?, width: Int, swipe: PodPickSwipeState,
    mark: PodPickMark?, alpha: Float, burnEnabled: Boolean,
) {
    val density = LocalDensity.current
    val cardWidth = with(density) { width.toDp() }
    val cardHeight = cardWidth * 1.4f
    // Reserve space for the rotated corners instead of clipping them to the popup window.
    val padding = cardHeight * .1f
    val paddingPx = with(density) { padding.roundToPx() }
    val position = remember(swipe.offset, paddingPx) { object : PopupPositionProvider {
        override fun calculatePosition(anchorBounds: IntRect, windowSize: IntSize,
            layoutDirection: LayoutDirection, popupContentSize: IntSize) =
            anchorBounds.topLeft + IntOffset(swipe.offset.roundToInt() - paddingPx, -paddingPx)
    } }
    val progress = swipe.progress(width)
    val tint = when {
        swipe.offset > 0f -> PodPickGreen
        burnEnabled -> PodPickRed
        else -> Color.Transparent
    }.copy(alpha = if (swipe.offset > 0f || burnEnabled) .35f * progress else 0f)
    Popup(position, properties = passThroughPopupProperties()) {
        Box(Modifier.requiredSize(cardWidth + padding * 2, cardHeight + padding * 2).padding(padding)) {
            Box(Modifier.fillMaxSize().testTag("pod-pick-swipe-copy-${copy.id}").clearAndSetSemantics {}
                .graphicsLayer {
                    this.alpha = .7f * alpha
                    rotationZ = 8f * progress * if (swipe.offset >= 0f) 1f else -1f
                    shadowElevation = with(density) { 8.dp.toPx() }
                    shape = RoundedCornerShape(4.dp)
                }) {
                PodPickCardFace(copy, definition, if (mark == PodPickMark.KEEP) 1f else 0f,
                    if (mark == PodPickMark.BURN) 1f else 0f, tint, reuseArtwork = true)
            }
        }
    }
}

@Composable
private fun PodPickTray(
    label: String, ids: List<String>, required: Int, copies: Map<String, DraftCopy>,
    definitions: Map<String, CardDefinition>, color: Color, automatic: Boolean,
    modifier: Modifier,
    onUnmark: (String) -> Unit, onInspect: (DraftCopy, Boolean) -> Unit,
    onInspectScroll: (Float) -> Unit,
    desktop: Boolean = false,
    visibleIds: List<String> = ids,
    trailing: @Composable RowScope.() -> Unit = {},
) {
    val currentOnUnmark by rememberUpdatedState(onUnmark)
    val currentOnInspect by rememberUpdatedState(onInspect)
    Box(modifier.height(56.dp).background(color.copy(alpha = .16f), RoundedCornerShape(4.dp))
        .padding(4.dp).testTag("pod-pick-${label.lowercase()}-tray")) {
        Row(Modifier.fillMaxWidth().height(48.dp).testTag("pod-pick-${label.lowercase()}-cards")
            .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            visibleIds.forEach { id -> copies[id]?.let { copy ->
                key(id) {
                    PodPickArtwork(copy, definitions[copy.definition], Modifier.width(PodPickTrayCardWidth).fillMaxHeight()
                        .testTag("pod-pick-tray-$id").semantics {
                            contentDescription = "${definitions[copy.definition]?.name ?: "Card"}, ${if (automatic) "automatic burn" else label.lowercase()} tray"
                            if (!automatic) onClick { currentOnUnmark(id); true }
                        }.pointerInput(id, automatic, desktop) {
                            podPickGestures(onTap = { if (!automatic) currentOnUnmark(id) },
                                 onInspect = { currentOnInspect(copy, it) }, onInspectScroll = onInspectScroll,
                                 mouseClick = if (desktop) ({ _ -> if (!automatic) currentOnUnmark(id) }) else null)
                        })
                }
            } }
            trailing()
        }
        // The count overlays the tray corner (it may cover part of a card); touches pass through.
        Text("${if (label == "Keep") "✓" else "🔥"} ${ids.size}/$required",
            Modifier.align(Alignment.TopStart).background(color, RoundedCornerShape(4.dp))
                .padding(horizontal = 3.dp).semantics { contentDescription = "$label ${ids.size}/$required" },
            color = Color.White, style = MaterialTheme.typography.labelSmall, maxLines = 1)
    }
}

@Composable
private fun PodPickArtwork(copy: DraftCopy, definition: CardDefinition?, modifier: Modifier, reuseArtwork: Boolean = false,
    crop: Boolean = false) {
    val reference = remember(copy.definition, definition) { podTileArtwork(definition) }
    BoxWithConstraints(modifier) {
        val density = LocalDensity.current
        CardArtwork(reference,
            definition?.name ?: "Card data unavailable",
            with(density) { maxWidth.roundToPx() }.coerceIn(1, 4096),
            with(density) { maxHeight.roundToPx() }.coerceIn(1, 4096), Modifier.fillMaxSize(),
            placeholder = { PodCardPlaceholder(definition?.name ?: "Card data unavailable") },
            previewReference = reference.takeIf { reuseArtwork }, crop = crop)
    }
}

package org.asyncttwiz.android

import android.content.Context
import android.content.SharedPreferences
import android.content.res.Configuration
import android.view.WindowManager
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
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
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
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
import androidx.compose.ui.window.PopupProperties
import org.asyncttwiz.protocol.*
import kotlin.math.abs
import kotlin.math.roundToInt

internal val PodPickGreen = Color(0xFF237A38)
internal val PodPickRed = Color(0xFFB3261E)

/** Without [onMarks] the marks live only in this composition (previews and tests). */
@Composable
internal fun PodPickView(
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
    // One card menu for the whole screen: the latest tap wins over simultaneous ones.
    var menuFor by remember(pack?.id) { mutableStateOf<String?>(null) }
    var tileSize by remember { mutableStateOf(IntSize.Zero) }
    val previewScroll = rememberScrollState()
    PodArtPrefetch(listOfNotNull(pack, nextPack), definitions, tileSize)
    if (pack == null) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) { PodPickWaiting(waitingText, onMainMenu) }
        return
    }

    val rules = remember(pack, settings, marks.librarians.size) { PodPickRules(pack, settings, marks.librarians.size) }
    val baseRules = remember(pack, settings) { PodPickRules(pack, settings) }
    val columnCount = columns.columns(pack.cards.size)
    val burnIds = rules.burnIds(marks)
    val copies = remember(pack) { pack.cards.associateBy { it.id } }
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
                items(pack.cards, key = { it.id }) { copy ->
                    PodPickCard(copy, definitions[copy.definition], marks.markOf(copy.id),
                        automaticBurn = rules.automaticBurns && copy.id in burnIds,
                        burnEnabled = rules.manualBurns > 0,
                        onMark = { mark(copy.id, it) }, onUnmark = { unmark(copy.id) },
                        onInspect = { inspect(copy, it) },
                        onInspectScroll = { previewScroll.dispatchRawDelta(it) },
                        menu = menuFor == copy.id,
                        onMenu = { open ->
                            if (open) { inspected = null; menuFor = copy.id } else if (menuFor == copy.id) menuFor = null
                        },
                        // The first column's size is stable while scrolling; prefetch decodes at it.
                        onSize = { if (copy == pack.cards.first()) tileSize = it })
                }
            }
        }
        PodPickTrays(marks.keeps, burnIds, rules, baseRules, copies, definitions, unmark, inspect,
            onInspectScroll = { previewScroll.dispatchRawDelta(it) },
            librarianSlots = settings.librarians > 0,
            spent = marks.librarians, librarians = librarians,
            spend = { setMarks(marks.spendLibrarian(librarians.map { it.id }, pack, settings)) },
            refund = { setMarks(marks.refundLibrarian(it, pack, settings)) })
        Button(onClick = { if (rules.canConfirm(marks, busy)) {
            if (onConfirmLibrarians != null) onConfirmLibrarians(marks.keeps.toList(), rules.burnIds(marks).toList(), marks.librarians.toList())
            else onConfirm(marks.keeps.toList(), rules.burnIds(marks).toList())
        } }, enabled = rules.canConfirm(marks, busy), modifier = Modifier.fillMaxWidth()) {
            Text(if (marks.librarians.isEmpty()) "Confirm" else
                "Confirm · ${marks.librarians.size} ${if (marks.librarians.size == 1) "Librarian" else "Librarians"}")
        }
    }
    inspected?.let { PodPickArtPreview(it, definitions[it.definition], previewScroll) }
}

@Composable
private fun rememberPodPickPreferences() = LocalContext.current.applicationContext.let { context ->
    remember(context) { context.getSharedPreferences("pod-pick", Context.MODE_PRIVATE) }
}

/** Personal per-orientation cards-per-row choice, hoisted so the header's view settings panel can change it. */
@Stable
class PodPickColumns internal constructor(private val preferences: SharedPreferences, private val key: String, default: Int) {
    var preferred by mutableIntStateOf(preferences.getInt(key, default))
        private set
    fun choose(value: Int) { preferred = value.coerceIn(PodPickColumnRange); preferences.edit().putInt(key, preferred).apply() }
    fun columns(cards: Int) = preferred.coerceIn(1, cards.coerceAtLeast(1))
}

@Composable
internal fun rememberPodPickColumns(): PodPickColumns {
    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    val preferences = rememberPodPickPreferences()
    val key = if (landscape) "columns-landscape" else "columns-portrait"
    return remember(preferences, key) { PodPickColumns(preferences, key, if (landscape) 5 else 3) }
}

internal const val PodHoldMillis = 150L
internal const val PodHoldSettleMillis = 300L

/** One recognizer owns tap, horizontal swipe and the short, release-to-close hold.
 * For [PodHoldSettleMillis] after the preview appears, movement past slop closes it and is
 * left to the swipe or the underlying scroll; only a settled hold pans the preview. */
internal suspend fun PointerInputScope.podPickGestures(
    onTap: () -> Unit,
    onInspect: (Boolean) -> Unit,
    onInspectScroll: (Float) -> Unit = {},
    onDrag: ((Float) -> Unit)? = null,
    onDragEnd: (Float) -> Unit = {},
    onEnd: () -> Unit = {},
) = awaitEachGesture {
    val down = awaitFirstDown(requireUnconsumed = false)
    val slop = viewConfiguration.touchSlop
    var dragging = false
    var held = false
    var settled = false
    var done = false
    var released = false
    var delta = Offset.Zero
    suspend fun AwaitPointerEventScope.next() {
        val event = awaitPointerEvent()
        val change = event.changes.firstOrNull { it.id == down.id }
        if (change == null || change.isConsumed || event.changes.size > 1) { done = true; return }
        delta = change.position - down.position
        val horizontal = abs(delta.x) > abs(delta.y)
        when {
            dragging -> { change.consume(); onDrag?.invoke(delta.x) }
            settled -> if (abs(delta.x) > slop && horizontal) done = true else {
                onInspectScroll(-change.positionChange().y); change.consume()
            }
            // Movement wins over the timers, even on a late input frame.
            delta.getDistance() > slop -> if (onDrag != null && horizontal) {
                if (held) { held = false; onInspect(false) }
                dragging = true
                change.consume(); onDrag(delta.x)
            } else done = true // Unconsumed, so the list or tray scroll continues from here.
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
            if (withTimeoutOrNull(PodHoldSettleMillis) { while (!done && !dragging) next() } == null) settled = true
        }
        while (!done) next()
        if (released && dragging) onDragEnd(delta.x)
    } finally {
        if (held) onInspect(false)
        onEnd()
    }
}

@Composable
internal fun PodPickWaiting(text: String = "Waiting for other players", onMainMenu: (() -> Unit)? = null) {
    Column(Modifier.padding(horizontal = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text, Modifier.testTag("pod-waiting"), color = MaterialTheme.colorScheme.onSurface.copy(alpha = .6f),
            textAlign = TextAlign.Center)
        if (onMainMenu != null) TextButton(onMainMenu) { Text("Main menu") }
    }
}

@Composable
private fun PodPickTrays(
    keeps: List<String>, burns: List<String>, rules: PodPickRules, baseRules: PodPickRules,
    copies: Map<String, DraftCopy>, definitions: Map<String, CardDefinition>,
    onUnmark: (String) -> Unit, onInspect: (DraftCopy, Boolean) -> Unit,
    onInspectScroll: (Float) -> Unit, librarianSlots: Boolean,
    spent: List<String>, librarians: List<DraftCopy>, spend: () -> Unit, refund: (String) -> Unit,
) {
    // Freeze geometry for the pack. Extra slots and the chip pan within the original keep tray.
    val keepSlots = podPickKeepSlots(baseRules.requiredKeeps, librarianSlots)
    BoxWithConstraints(Modifier.fillMaxWidth().padding(top = 4.dp)) {
        val layout = podPickTrayLayout(maxWidth, keepSlots, baseRules.requiredBurns)
        val keep: @Composable () -> Unit = {
            PodPickTray("Keep", keeps, rules.requiredKeeps, copies, definitions, PodPickGreen,
                false, Modifier.width(layout.keepWidth), onUnmark, onInspect, onInspectScroll,
                visibleIds = keeps.take(baseRules.requiredKeeps)) {
                spent.forEachIndexed { index, id ->
                    val copy = keeps.getOrNull(baseRules.requiredKeeps + index)?.let(copies::get)
                    PodLibrarianSlot(id, copy, copy?.let { definitions[it.definition] }, { refund(id) }, onInspect)
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
                            .pointerInput(left) { podPickGestures(onTap = { if (left > 0) currentSpend() },
                                onInspect = { currentInspect(librarian, it) }, onInspectScroll = onInspectScroll) }) {
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
                rules.automaticBurns, Modifier.width(layout.burnWidth), onUnmark, onInspect, onInspectScroll)
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
    inspect: (DraftCopy, Boolean) -> Unit) {
    val color = PodPickGreen
    val currentRefund by rememberUpdatedState(refund)
    val currentInspect by rememberUpdatedState(inspect)
    Surface(modifier = Modifier.width(PodPickTrayCardWidth).height(48.dp)
        .testTag("pod-librarian-slot-$id").semantics {
            role = Role.Button
            contentDescription = "Refund Librarian"
            onClick { currentRefund(); true }
        }
        .pointerInput(copy) { podPickGestures(onTap = { currentRefund() },
            onInspect = { open -> copy?.let { currentInspect(it, open) } }) }) {
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
    Box {
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
            .pointerInput(burnEnabled) {
                podPickGestures(onTap = { currentOnMenu(true) }, onInspect = { currentOnInspect(it) }, onInspectScroll = onInspectScroll,
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
        }
        if (swipe.phase != PodPickSwipePhase.IDLE && width > 0) {
            PodPickSwipeCopy(copy, definition, width, swipe, liftedMark,
                if (swipe.phase == PodPickSwipePhase.DRAGGING) 1f else copyAlpha.value, burnEnabled)
        }
        PodCardMenu(menu, dismiss = { onMenu(false) }) {
            if (mark != PodPickMark.KEEP) DropdownMenuItem(text = { Text("Keep") }, onClick = { onMenu(false); onMark(PodPickMark.KEEP) })
            if (burnEnabled && mark != PodPickMark.BURN) DropdownMenuItem(text = { Text("Burn") }, onClick = { onMenu(false); onMark(PodPickMark.BURN) })
            if (mark != null) DropdownMenuItem(text = { Text("Unmark") }, onClick = { onMenu(false); onUnmark() })
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
    Popup(position, properties = PopupProperties(
        flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
        dismissOnBackPress = false, dismissOnClickOutside = false, excludeFromSystemGesture = false,
    )) {
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
                        }.pointerInput(id, automatic) {
                            podPickGestures(onTap = { if (!automatic) currentOnUnmark(id) },
                                 onInspect = { currentOnInspect(copy, it) }, onInspectScroll = onInspectScroll)
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
    val reference = remember(copy.definition, definition) { podTileArtwork(copy.definition, definition) }
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

package org.asyncttwiz.android

import android.content.Context
import android.content.res.Configuration
import android.view.WindowManager
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
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

private val PodPickGreen = Color(0xFF237A38)
private val PodPickRed = Color(0xFFB3261E)

@Composable
fun PodPickView(
    pack: DraftPrivatePack?,
    settings: DraftSettings,
    definitions: Map<String, CardDefinition>,
    busy: Boolean,
    onConfirm: (keep: List<String>, burn: List<String>) -> Unit,
    modifier: Modifier = Modifier,
    chrome: @Composable (@Composable () -> Unit) -> Unit = { control ->
        Row(Modifier.fillMaxWidth().height(40.dp), horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically) { control() }
    },
) {
    val packMarks = remember { mutableStateMapOf<DraftPrivatePack, PodPickMarks>() }
    val marks = (packMarks[pack] ?: PodPickMarks()).forPack(pack)
    var inspected by remember(pack?.id) { mutableStateOf<DraftCopy?>(null) }
    val previewScroll = rememberScrollState()
    if (pack == null) {
        Column(modifier.fillMaxSize()) {
            chrome {}
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                PodPickWaiting()
            }
        }
        return
    }

    val rules = remember(pack, settings) { PodPickRules(pack, settings) }
    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    val preferences = rememberPodPickPreferences()
    val preferenceKey = if (landscape) "columns-landscape" else "columns-portrait"
    var preferredColumns by remember(preferences, preferenceKey) {
        mutableIntStateOf(preferences.getInt(preferenceKey, if (landscape) 5 else 3))
    }
    val maxColumns = pack.cards.size.coerceAtLeast(1)
    val columns = preferredColumns.coerceIn(1, maxColumns)
    val burnIds = rules.burnIds(marks)
    val copies = remember(pack) { pack.cards.associateBy { it.id } }
    val mark: (String, PodPickMark) -> Unit = { id, kind ->
        packMarks[pack] = marks.toggle(id, kind, rules)
    }
    val unmark: (String) -> Unit = { id -> packMarks[pack] = marks.unmark(id) }
    val inspect: (DraftCopy?) -> Unit = {
        if (it != null) previewScroll.dispatchRawDelta(-previewScroll.value.toFloat())
        inspected = it
    }

    Column(modifier.fillMaxSize().padding(horizontal = 8.dp)) {
        chrome {
            PodColumnControl("$columns", "Cards per row", columns, 1..maxColumns) {
                preferredColumns = it
                preferences.edit().putInt(preferenceKey, preferredColumns).apply()
            }
        }
        key(pack.id) {
            LazyVerticalGrid(GridCells.Fixed(columns), Modifier.weight(1f).fillMaxWidth().testTag("pod-pick-list"),
                state = rememberLazyGridState(), horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp), contentPadding = PaddingValues(vertical = 4.dp)) {
                items(pack.cards, key = { it.id }) { copy ->
                    PodPickCard(copy, definitions[copy.definition], marks.markOf(copy.id),
                        automaticBurn = rules.automaticBurns && copy.id in burnIds,
                        burnEnabled = rules.manualBurns > 0,
                        onMark = { mark(copy.id, it) }, onUnmark = { unmark(copy.id) },
                        onInspect = { inspect(if (it) copy else null) },
                        onInspectScroll = { previewScroll.dispatchRawDelta(it) })
                }
            }
        }
        PodPickTrays(marks.keeps, burnIds, rules, copies, definitions, unmark, inspect,
            onInspectScroll = { previewScroll.dispatchRawDelta(it) })
        Button(onClick = { if (rules.canConfirm(marks, busy)) {
            onConfirm(marks.keeps.toList(), rules.burnIds(marks).toList())
        } }, enabled = rules.canConfirm(marks, busy), modifier = Modifier.fillMaxWidth()) {
            Text("Confirm")
        }
    }
    inspected?.let { PodPickArtPreview(it, definitions[it.definition], previewScroll) }
}

@Composable
private fun rememberPodPickPreferences() = LocalContext.current.applicationContext.let { context ->
    remember(context) { context.getSharedPreferences("pod-pick", Context.MODE_PRIVATE) }
}

/** One recognizer owns tap, horizontal swipe and the short, release-to-close hold. */
internal suspend fun PointerInputScope.podPickGestures(
    onTap: () -> Unit,
    onInspect: (Boolean) -> Unit,
    onInspectScroll: (Float) -> Unit = {},
    onDrag: ((Float) -> Unit)? = null,
    onDragEnd: (Float) -> Unit = {},
    onEnd: () -> Unit = {},
) = awaitEachGesture {
    val down = awaitFirstDown(requireUnconsumed = false)
    var dragging = false
    var held = false
    var cancelled = false
    var released = false
    var delta = Offset.Zero
    try {
        val finishedBeforeHold = withTimeoutOrNull(150L) {
            while (!released && !dragging && !cancelled) {
                val event = awaitPointerEvent()
                val change = event.changes.firstOrNull { it.id == down.id }
                if (change == null || change.isConsumed || event.changes.size > 1) {
                    cancelled = true
                    break
                }
                delta = change.position - down.position
                // Movement wins over the timer, even on a late input frame.
                if (delta.getDistance() > viewConfiguration.touchSlop) {
                    dragging = onDrag != null && abs(delta.x) > abs(delta.y)
                    cancelled = !dragging
                    if (dragging) { change.consume(); onDrag?.invoke(delta.x) }
                }
                if (change.changedToUpIgnoreConsumed()) {
                    released = true
                    if (!dragging && !cancelled) { change.consume(); onTap() }
                }
            }
            true
        }
        if (finishedBeforeHold == null) {
            held = true
            onInspect(true)
        }
        while (!released && !cancelled) {
            val event = awaitPointerEvent()
            val change = event.changes.firstOrNull { it.id == down.id }
            if (change == null || change.isConsumed || event.changes.size > 1) break
            delta = change.position - down.position
            if (held && abs(delta.x) > viewConfiguration.touchSlop && abs(delta.x) > abs(delta.y)) break
            if (held) { onInspectScroll(-change.positionChange().y); change.consume() }
            if (dragging) { change.consume(); onDrag?.invoke(delta.x) }
            if (change.changedToUpIgnoreConsumed()) {
                change.consume()
                released = true
            }
        }
        if (released && dragging && !held) onDragEnd(delta.x)
    } finally {
        if (held) onInspect(false)
        onEnd()
    }
}

@Composable
internal fun PodPickWaiting() {
    Text("Waiting for other players", color = MaterialTheme.colorScheme.onSurface.copy(alpha = .4f))
}

@Composable
private fun PodPickTrays(
    keeps: List<String>, burns: List<String>, rules: PodPickRules,
    copies: Map<String, DraftCopy>, definitions: Map<String, CardDefinition>,
    onUnmark: (String) -> Unit, onInspect: (DraftCopy?) -> Unit,
    onInspectScroll: (Float) -> Unit,
) {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val style = MaterialTheme.typography.bodySmall
    val keepLabel = with(density) { measurer.measure(AnnotatedString("✓ ${rules.requiredKeeps}/${rules.requiredKeeps}"), style).size.width.toDp() + 4.dp }
    val burnLabel = with(density) { measurer.measure(AnnotatedString("🔥 ${rules.requiredBurns}/${rules.requiredBurns}"), style).size.width.toDp() + 4.dp }
    BoxWithConstraints(Modifier.fillMaxWidth().padding(top = 4.dp)) {
        val layout = podPickTrayLayout(maxWidth, rules.requiredKeeps, rules.requiredBurns, keepLabel, burnLabel)
        val keep: @Composable () -> Unit = {
            PodPickTray("Keep", keeps, rules.requiredKeeps, copies, definitions, PodPickGreen,
                false, Modifier.width(layout.keepWidth), keepLabel, onUnmark, onInspect, onInspectScroll)
        }
        val burn: @Composable () -> Unit = {
            if (rules.requiredBurns > 0) PodPickTray("Burn", burns, rules.requiredBurns, copies, definitions, PodPickRed,
                rules.automaticBurns, Modifier.width(layout.burnWidth), burnLabel, onUnmark, onInspect, onInspectScroll)
        }
        if (layout.singleRow) {
            Row(horizontalArrangement = Arrangement.spacedBy(PodPickTrayGap)) { keep(); burn() }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(PodPickTrayGap)) { keep(); burn() }
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
) {
    var menu by remember { mutableStateOf(false) }
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
        Box(Modifier.fillMaxWidth().aspectRatio(1f / 1.4f).onSizeChanged { width = it.width }
            .testTag("pod-pick-card-${copy.id}").semantics {
                contentDescription = definition?.name ?: "Card data unavailable"
                stateDescription = when (effectiveMark) {
                    PodPickMark.KEEP -> "Keep"
                    PodPickMark.BURN -> if (automaticBurn) "Automatic burn" else "Burn"
                    null -> "Unmarked"
                }
                onClick { menu = true; true }
            }
            .pointerInput(burnEnabled) {
                podPickGestures(onTap = { menu = true }, onInspect = { currentOnInspect(it) }, onInspectScroll = onInspectScroll,
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
        PodCardMenu(menu, dismiss = { menu = false }) {
            if (mark != PodPickMark.KEEP) DropdownMenuItem(text = { Text("Keep") }, onClick = { menu = false; onMark(PodPickMark.KEEP) })
            if (burnEnabled && mark != PodPickMark.BURN) DropdownMenuItem(text = { Text("Burn") }, onClick = { menu = false; onMark(PodPickMark.BURN) })
            if (mark != null) DropdownMenuItem(text = { Text("Unmark") }, onClick = { menu = false; onUnmark() })
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
    modifier: Modifier, labelWidth: Dp,
    onUnmark: (String) -> Unit, onInspect: (DraftCopy?) -> Unit,
    onInspectScroll: (Float) -> Unit,
) {
    val currentOnUnmark by rememberUpdatedState(onUnmark)
    val currentOnInspect by rememberUpdatedState(onInspect)
    Row(modifier.height(56.dp).background(color.copy(alpha = .16f), RoundedCornerShape(4.dp))
        .padding(4.dp).testTag("pod-pick-${label.lowercase()}-tray"), verticalAlignment = Alignment.CenterVertically) {
        Text("${if (label == "Keep") "✓" else "🔥"} ${ids.size}/$required",
            Modifier.width(labelWidth).semantics { contentDescription = "$label ${ids.size}/$required" },
            style = MaterialTheme.typography.bodySmall, maxLines = 1)
        Row(Modifier.weight(1f).height(48.dp).testTag("pod-pick-${label.lowercase()}-cards")
            .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            ids.forEach { id -> copies[id]?.let { copy ->
                key(id) {
                    PodPickArtwork(copy, definitions[copy.definition], Modifier.width(PodPickTrayCardWidth).fillMaxHeight()
                        .testTag("pod-pick-tray-$id").semantics {
                            contentDescription = "${definitions[copy.definition]?.name ?: "Card"}, ${if (automatic) "automatic burn" else label.lowercase()} tray"
                            if (!automatic) onClick { currentOnUnmark(id); true }
                        }.pointerInput(id, automatic) {
                            podPickGestures(onTap = { if (!automatic) currentOnUnmark(id) },
                                 onInspect = { currentOnInspect(if (it) copy else null) }, onInspectScroll = onInspectScroll)
                        })
                }
            } }
        }
    }
}

@Composable
private fun PodPickArtwork(copy: DraftCopy, definition: CardDefinition?, modifier: Modifier, reuseArtwork: Boolean = false,
    crop: Boolean = false) {
    val card = remember(copy.definition, definition) {
        definition?.takeIf { it.faces.isNotEmpty() }?.let { InspectedCard.retained(copy.definition, it) }
    }
    BoxWithConstraints(modifier) {
        val density = LocalDensity.current
        val reference = card?.artwork?.firstOrNull()?.takeIf { it.printing != null || it.customUrl != null }?.copy(size = "normal")
        CardArtwork(reference,
            definition?.name ?: "Card data unavailable",
            with(density) { maxWidth.roundToPx() }.coerceIn(1, 4096),
            with(density) { maxHeight.roundToPx() }.coerceIn(1, 4096), Modifier.fillMaxSize(),
            placeholder = { PodCardPlaceholder(definition?.name ?: "Card data unavailable") },
            previewReference = reference.takeIf { reuseArtwork }, crop = crop)
    }
}

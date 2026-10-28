package org.asyncttwiz.android

import android.view.WindowManager
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.round
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.toSize
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import kotlinx.coroutines.Job
import org.asyncttwiz.storage.ArtworkReference
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.asyncttwiz.protocol.*
import kotlin.math.abs
import kotlin.math.roundToInt

private val PodBuildSidePadding = 48.dp
/** Vertical gap between whole cards in one column. */
internal val PodBuildCardGap = 1.dp
internal val PodBuildBlue = Color(0xFF1F5FAF)
private const val PodBuildSettleMillis = 250

/** Section colours, shared by the headers and the swipe projection: Main green, Side blue, Trash red. */
internal fun PodDeckCategory.podBuildColor(): Color = when (this) {
    PodDeckCategory.MAIN -> PodPickGreen
    PodDeckCategory.SIDE -> PodBuildBlue
    PodDeckCategory.TRASH -> PodPickRed
}

/** Where a horizontal drag of [dragX] px would move a card of [category]; null before any movement. */
internal fun podBuildSwipeTarget(category: PodDeckCategory, dragX: Float): PodDeckCategory? = when {
    dragX > 0f -> PodDeckRules.swipe(category, PodDeckSwipe.RIGHT)
    dragX < 0f -> PodDeckRules.swipe(category, PodDeckSwipe.LEFT)
    else -> null
}

private data class PodBuildLeaving(val sequence: Int, val tile: PodBuildTile, val bounds: Rect)

private class PodBuildAnchor { var coordinates: LayoutCoordinates? = null }

@Composable
fun PodBuildView(
    build: PodDeckBuild,
    copies: Map<String, DraftCopy>,
    definitions: Map<String, CardDefinition>,
    sideboardLimit: Int?,
    editable: Boolean,
    canSubmit: Boolean,
    onMove: (copyId: String, category: PodDeckCategory) -> Unit,
    onAdjustBasic: (index: Int, delta: Int) -> Unit,
    onSubmit: () -> Unit,
    modifier: Modifier = Modifier,
    deckName: String = "Deck",
    onRename: (String) -> Unit = {},
    submitted: Boolean = !editable,
    columnPreference: PodBuildColumns = rememberPodBuildColumns(),
    sideBySide: PodBuildColumns = rememberPodBuildSideBySide(),
    canUndo: Boolean = false,
    /** Reverts the latest Build change; returns the moved copy and where it went back to. */
    onUndo: () -> Pair<String, PodDeckCategory>? = { null },
    /** The review feed's Undo moving a copy back, which also drops that move from [onUndo]'s history. */
    onRevert: (copyId: String, category: PodDeckCategory) -> Unit = onMove,
    onAdjustSideBasic: (index: Int, delta: Int) -> Unit = { _, _ -> },
) {
    val columns = columnPreference.count
    val submitEnabled = podBuildSubmitEnabled(build, copies, sideboardLimit, editable, canSubmit)
    var confirmSubmit by remember { mutableStateOf(false) }
    var naming by remember { mutableStateOf(false) }
    // One card popup at a time: a menu or the hold preview, whichever came last.
    val menus = remember { PodCardMenuOwner() }
    var inspected by remember { mutableStateOf<Pair<String, InspectedCard>?>(null) }
    val previewScroll = rememberScrollState()
    var feedback by remember { mutableStateOf<Pair<Int, PodDeckCategory>?>(null) }
    var feedbackId by remember { mutableIntStateOf(0) }
    LaunchedEffect(feedback) { if (feedback != null) { delay(850); feedback = null } }
    // A confirmed move fades the card out where it was, fades it in where it lands and slides the rest.
    var leaving by remember { mutableStateOf<PodBuildLeaving?>(null) }
    var arriving by remember { mutableStateOf(emptySet<String>()) }
    var sliding by remember { mutableStateOf(false) }
    var settle by remember { mutableIntStateOf(0) }
    LaunchedEffect(settle) {
        if (settle > 0) { delay(PodBuildSettleMillis + 150L); sliding = false; arriving = emptySet() }
    }
    val origin = remember { PodBuildAnchor() }
    val move: (String, PodDeckCategory) -> Unit = { id, destination ->
        if (editable) {
            feedback = ++feedbackId to destination
            arriving = setOf(id)
            sliding = true
            settle++
            onMove(id, destination)
        }
    }
    var feed by remember { mutableStateOf<PodBuildFeed?>(null) }
    val openFeed: (PodDeckCategory, Int?) -> Unit = { category, column ->
        if (editable) feed = podBuildFeed(category, podBuildColumns(build, category, copies, definitions, columns),
            PodDeckColumns.buckets(columns).map { it.label }, column)
    }
    Box(modifier.onGloballyPositioned { origin.coordinates = it }) {
      Column(Modifier.fillMaxSize()) {
        // Nothing sticks: the name, headers and columns all scroll away together.
        LazyColumn(Modifier.weight(1f).fillMaxWidth().testTag("pod-build"), contentPadding = PaddingValues(bottom = 12.dp)) {
            item("name") {
                Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(horizontal = PodBuildSidePadding),
                    verticalAlignment = Alignment.CenterVertically) {
                    Text(deckName, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                    IconButton({ naming = true }) { EntryIcon("Name deck", "edit") }
                }
            }
            for (category in PodDeckCategory.entries) {
                val count = podBuildCount(build, category)
                val buckets = podBuildColumns(build, category, copies, definitions, columns)
                val sectionColor = category.podBuildColor()
                val feedable = editable && podBuildFeedDemotion(category) != null
                item(key = "header-$category") {
                    Surface(color = sectionColor.copy(alpha = .16f).compositeOver(MaterialTheme.colorScheme.surfaceContainer),
                        modifier = Modifier.fillMaxWidth().then(if (sliding) Modifier.animateItem(fadeInSpec = null,
                            fadeOutSpec = null, placementSpec = tween(PodBuildSettleMillis)) else Modifier)) {
                        val reviewable = feedable && count > 0
                        Row(Modifier.fillMaxWidth().padding(horizontal = PodBuildSidePadding).heightIn(min = 40.dp)
                            .then(if (reviewable) Modifier.testTag("pod-build-feed-header-$category")
                                .clickable(onClickLabel = "Review one by one") { openFeed(category, null) } else Modifier),
                            verticalAlignment = Alignment.CenterVertically) {
                            Text(when (category) { PodDeckCategory.MAIN -> podBuildMainTitle(build, copies, definitions); PodDeckCategory.SIDE -> podBuildSideTitle(build, copies, definitions)
                                else -> "${category.podBuildLabel()} $count" },
                                Modifier.weight(1f).padding(vertical = 8.dp), color = sectionColor,
                                style = MaterialTheme.typography.titleSmall)
                            if (reviewable) Text("Review ›", color = sectionColor.copy(alpha = .8f),
                                style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
                if (category == PodDeckCategory.MAIN) item("basics") { PodBuildBasics(build.basics, editable, onAdjustBasic) }
                if (category == PodDeckCategory.SIDE) item("side-basics") { PodBuildBasics(build.sideBasics, editable, onAdjustSideBasic, side = true) }
                item("columns-$category") {
                    BoxWithConstraints(Modifier.fillMaxWidth().then(if (sliding) Modifier.animateItem(fadeInSpec = null,
                        fadeOutSpec = null, placementSpec = tween(PodBuildSettleMillis)) else Modifier)
                        .padding(horizontal = PodBuildSidePadding, vertical = 12.dp)) {
                        val width by animateDpAsState((maxWidth - (columns - 1).dp * 2) / columns, label = "column-width")
                        val tint = MaterialTheme.colorScheme.primary.copy(alpha = .045f)
                        Row(Modifier.fillMaxWidth().animateContentSize().drawBehind {
                            repeat(columns) { index -> drawRect(tint, Offset(index * (width.toPx() + 2.dp.toPx()), 0f), Size(width.toPx(), size.height)) }
                        }, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                            buckets.forEachIndexed { index, bucket ->
                                Column(Modifier.width(width).testTag("pod-build-column-$category-$index"),
                                    verticalArrangement = Arrangement.spacedBy(PodBuildCardGap)) {
                                    val label = PodDeckColumns.buckets(columns)[index].label
                                    val reviewable = feedable && bucket.isNotEmpty()
                                    // Tappable labels read in the section colour on a faint chip; touch expands to 48dp.
                                    // The copy count sits on its own line under the label.
                                    Column(Modifier.fillMaxWidth().then(if (reviewable) Modifier
                                        .testTag("pod-build-feed-label-$category-$index")
                                        .clip(RoundedCornerShape(4.dp)).background(sectionColor.copy(alpha = .1f))
                                        .clickable(onClickLabel = "Review ${podBuildFeedColumnLabel(label)} one by one") {
                                            openFeed(category, index)
                                        } else Modifier).padding(vertical = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                        val color = if (reviewable) sectionColor else Color.Unspecified
                                        Text(label, fontSize = 11.sp, color = color, textAlign = TextAlign.Center)
                                        Text("${bucket.sumOf { it.copyIds.size }}", Modifier.testTag("pod-build-column-count-$category-$index"),
                                            fontSize = 10.sp, lineHeight = 12.sp, textAlign = TextAlign.Center,
                                            color = if (reviewable) sectionColor.copy(alpha = .8f) else MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    PodBuildGrid(sideBySide.count) {
                                        // Keyed by card, not copy, so a stack that loses one copy stays in place.
                                        bucket.forEach { tile -> key(tile.digest) {
                                            val tileKey = tile.copyIds.first()
                                            Box(Modifier.podBuildSlide(sliding)) {
                                                PodBuildCard(tile, category, editable, move, onInspect = { open ->
                                                    val card = tile.definition?.takeIf { it.faces.isNotEmpty() }
                                                        ?.let { InspectedCard.retained(tile.digest, it) }
                                                    if (open && card != null) {
                                                        menus.open = null
                                                        previewScroll.dispatchRawDelta(-previewScroll.value.toFloat())
                                                        inspected = tileKey to card
                                                    } else if (!open && inspected?.first == tileKey) inspected = null
                                                }, onInspectScroll = { previewScroll.dispatchRawDelta(it) }, menus = menus,
                                                    fadeIn = tile.copyIds.any { it in arriving },
                                                    onLeave = { bounds ->
                                                        val offset = origin.coordinates?.takeIf { it.isAttached }?.positionInRoot() ?: Offset.Zero
                                                        leaving = PodBuildLeaving((leaving?.sequence ?: 0) + 1, tile, bounds.translate(-offset))
                                                    })
                                            }
                                        } }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        Surface(Modifier.fillMaxWidth().padding(horizontal = PodBuildSidePadding).height(48.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
            if (submitted) Box(contentAlignment = Alignment.Center) { Text("✓ Submitted", style = MaterialTheme.typography.labelLarge) }
            else Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                IconButton({
                    onUndo()?.let { (id, destination) ->
                        feedback = ++feedbackId to destination
                        arriving = setOf(id)
                    }
                    sliding = true
                    settle++
                }, Modifier.size(48.dp).testTag("pod-build-undo").semantics { contentDescription = "Undo last change" },
                    enabled = editable && canUndo) { TableChromeIcon("undo") }
                Button({ confirmSubmit = true }, Modifier.weight(1f).fillMaxHeight(), enabled = submitEnabled,
                    shape = MaterialTheme.shapes.extraSmall) { Text("Submit deck (${podBuildCount(build, PodDeckCategory.MAIN) + build.basics.total})") }
            }
        }
      }
        leaving?.let { gone -> key(gone.sequence) {
            PodBuildLeavingCard(gone) { if (leaving?.sequence == gone.sequence) leaving = null }
        } }
        feedback?.let { (_, category) ->
            Surface(Modifier.align(Alignment.TopCenter).padding(top = 8.dp), color = MaterialTheme.colorScheme.primaryContainer,
                shape = MaterialTheme.shapes.small, shadowElevation = 4.dp) {
                Text("→ ${category.podBuildLabel()}", Modifier.padding(12.dp))
            }
        }
    }
    if (confirmSubmit && submitEnabled) ScreenDialog({ confirmSubmit = false }, title = "Submit deck?", actions = {
        DialogCancelButton("Cancel", { confirmSubmit = false })
        DialogMainButton("Submit", { confirmSubmit = false; onSubmit() })
    })
    if (naming) {
        val name = rememberDialogField(deckName)
        ScreenDialog({ naming = false }, title = "Deck name", actions = {
            DialogCancelButton("Cancel", { naming = false })
            DialogMainButton("Save name", { onRename(name.text.trim()); naming = false }, enabled = name.text.isNotBlank())
        }) {
            OutlinedTextField(name.value, { name.value = it.copy(text = it.text.take(120)) }, Modifier.fillMaxWidth().dialogField(name),
                singleLine = true, label = { Text("Name") })
        }
    }
    LaunchedEffect(submitEnabled) { if (!submitEnabled) confirmSubmit = false }
    feed?.let { current ->
        val next = current.column?.takeIf { current.finished }?.let { column ->
            podBuildFeedNextColumn(podBuildColumns(build, current.category, copies, definitions, columns), column)
                ?.takeIf { it < current.labels.size }
        }
        PodBuildFeedDialog(current, podBuildCount(build, PodDeckCategory.MAIN) + build.basics.total, editable,
            nextLabel = next?.let { podBuildFeedColumnLabel(current.labels[it]) },
            onChange = { feed = it }, onMove = { id, category -> if (editable) onMove(id, category) },
            onRevert = { id, category -> if (editable) onRevert(id, category) },
            onNext = { next?.let { openFeed(current.category, it) } }, onClose = { feed = null })
    }
    inspected?.second?.let { PodCardArtPreview(it, it.definition.name, previewScroll) }
}

/** Lays its cards out [perRow] wide, row-major, all of one width with [PodBuildCardGap] between them.
 * A single layout parent keeps keyed cards (and their slide animation) when they change rows. */
@Composable
private fun PodBuildGrid(perRow: Int, content: @Composable () -> Unit) {
    Layout(content, Modifier.fillMaxWidth()) { measurables, constraints ->
        val gap = PodBuildCardGap.roundToPx()
        val width = constraints.maxWidth
        val cardWidth = podBuildCardWidth(width, perRow, gap)
        val placeables = measurables.map { it.measure(Constraints.fixedWidth(cardWidth)) }
        val rows = placeables.chunked(perRow)
        val heights = rows.map { row -> row.maxOf { it.height } }
        val tops = heights.runningFold(0) { top, height -> top + height + gap }
        layout(width, (heights.sum() + gap * (rows.size - 1)).coerceAtLeast(0)) {
            placeables.forEachIndexed { index, placeable ->
                val (row, column) = podBuildGridSlot(index, perRow)
                placeable.place(column * (cardWidth + gap), tops[row])
            }
        }
    }
}

/** While [enabled], a changed position within the parent slides from the old place instead of jumping. */
private fun Modifier.podBuildSlide(enabled: Boolean): Modifier = composed {
    val scope = rememberCoroutineScope()
    val active by rememberUpdatedState(enabled)
    val offset = remember { Animatable(IntOffset.Zero, IntOffset.VectorConverter) }
    var placed by remember { mutableStateOf<IntOffset?>(null) }
    onPlaced { coordinates ->
        val now = coordinates.positionInParent().round()
        val previous = placed
        placed = now
        if (previous != null && previous != now && active) scope.launch {
            offset.snapTo(offset.value + previous - now)
            offset.animateTo(IntOffset.Zero, tween(PodBuildSettleMillis))
        }
    }.offset { offset.value }
}

/** The moved card fading out at its old place; it never takes touches. */
@Composable
private fun PodBuildLeavingCard(leaving: PodBuildLeaving, done: () -> Unit) {
    val alpha = remember { Animatable(1f) }
    val currentDone by rememberUpdatedState(done)
    LaunchedEffect(Unit) { alpha.animateTo(0f, tween(PodBuildSettleMillis)); currentDone() }
    val density = LocalDensity.current
    val name = leaving.tile.definition?.name ?: "Card details unavailable"
    val reference = remember(leaving.tile) { podTileArtwork(leaving.tile.digest, leaving.tile.definition) }
    Box(Modifier.offset { leaving.bounds.topLeft.round() }
        .size(with(density) { leaving.bounds.width.toDp() }, with(density) { leaving.bounds.height.toDp() })
        .graphicsLayer { this.alpha = alpha.value }.testTag("pod-build-leaving-${leaving.tile.copyIds.first()}")
        .clearAndSetSemantics {}) {
        CardArtwork(reference, name, leaving.bounds.width.roundToInt().coerceAtLeast(1),
            leaving.bounds.height.roundToInt().coerceAtLeast(1), Modifier.fillMaxSize(),
            placeholder = { PodCardPlaceholder(name) }, previewReference = reference)
    }
}

@Composable
private fun PodBuildBasics(basics: PodDeckBasics, editable: Boolean, onAdjust: (Int, Int) -> Unit, side: Boolean = false) {
    var open by remember { mutableStateOf(false) }
    Box(Modifier.padding(horizontal = PodBuildSidePadding, vertical = 4.dp)) {
        Surface(onClick = { open = true }, enabled = editable, shape = MaterialTheme.shapes.small,
            color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth()
                .height(48.dp).testTag(if (side) "pod-build-side-basics" else "pod-build-basics")
                .semantics { contentDescription = if (side) "Edit Side basic lands" else "Edit basic lands" }) {
            Row(Modifier.fillMaxSize().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                DraftColor.entries.forEachIndexed { index, color ->
                    CardText("{${color.name}} ${basics.podBuildValues()[index]}", Modifier.weight(1f), maxLines = 1)
                }
            }
        }
    }
    if (open && editable) ScreenDialog({ open = false }, title = if (side) "Side basic lands" else "Basic lands",
        actions = { DialogMainButton("Done", { open = false }) }) {
            listOf("Plains", "Island", "Swamp", "Mountain", "Forest").forEachIndexed { index, name ->
                val count = basics.podBuildValues()[index]
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    CardText("{${DraftColor.entries[index].name}}", Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
                    OutlinedIconButton({ onAdjust(index, -1) }, enabled = count > 0) { Text("−", Modifier.semantics { contentDescription = "Remove $name" }) }
                    Text("$count", Modifier.width(48.dp), textAlign = TextAlign.Center)
                    OutlinedIconButton({ onAdjust(index, 1) }, enabled = count < Int.MAX_VALUE) { Text("+", Modifier.semantics { contentDescription = "Add $name" }) }
                }
            }
    }
}

@Composable
internal fun PodBuildCard(
    tile: PodBuildTile,
    category: PodDeckCategory,
    editable: Boolean,
    onMove: (String, PodDeckCategory) -> Unit,
    onInspect: (Boolean) -> Unit,
    onInspectScroll: (Float) -> Unit = {},
    menus: PodCardMenuOwner = remember { PodCardMenuOwner() },
    fadeIn: Boolean = false,
    onLeave: (Rect) -> Unit = {},
) {
    val key = tile.copyIds.first()
    // The card stays in place; a projection follows the finger and snaps back below the threshold.
    var drag by remember { mutableFloatStateOf(0f) }
    var snapBack by remember { mutableStateOf<Job?>(null) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(editable) { if (!editable) { if (menus.open == key) menus.open = null; snapBack?.cancel(); drag = 0f } }
    val appear = remember { Animatable(if (fadeIn) 0f else 1f) }
    LaunchedEffect(Unit) { appear.animateTo(1f, tween(PodBuildSettleMillis)) }
    val anchor = remember { PodBuildAnchor() }
    val latestMove by rememberUpdatedState(onMove)
    val latestLeave by rememberUpdatedState(onLeave)
    val latestInspect by rememberUpdatedState(onInspect)
    val latestInspectScroll by rememberUpdatedState(onInspectScroll)
    val latestCopies by rememberUpdatedState(tile.copyIds.size)
    val threshold = with(LocalDensity.current) { 20.dp.toPx() }
    val right = PodDeckRules.swipe(category, PodDeckSwipe.RIGHT)
    val left = PodDeckRules.swipe(category, PodDeckSwipe.LEFT)
    val name = tile.definition?.name ?: "Card details unavailable"
    val confirm: (PodDeckCategory) -> Unit = { destination ->
        // Only a card that leaves its place entirely fades out there; a stack just loses one copy.
        if (latestCopies == 1) anchor.coordinates?.takeIf { it.isAttached }?.let {
            latestLeave(Rect(it.positionInRoot(), it.size.toSize()))
        }
        latestMove(key, destination)
    }
    BoxWithConstraints(Modifier.fillMaxWidth().testTag("pod-build-card-$key")) {
        val density = LocalDensity.current
        val width = with(density) { maxWidth.roundToPx().coerceAtLeast(1) }
        val height = (width * 1.4f).roundToInt().coerceAtLeast(1)
        val reference = remember(tile.digest, tile.definition) { podTileArtwork(tile.digest, tile.definition) }
        Box(Modifier.fillMaxWidth().aspectRatio(1f / 1.4f)
            .onGloballyPositioned { anchor.coordinates = it }
            .graphicsLayer { alpha = appear.value }
            // Same recognizer as the pick screen: tap menu, horizontal swipe, release-to-close hold.
            .pointerInput(key, category, threshold, editable) {
                podPickGestures(onTap = { if (editable) menus.open = key },
                    onInspect = { latestInspect(it) }, onInspectScroll = { latestInspectScroll(it) },
                    onDrag = if (editable) { x -> snapBack?.cancel(); drag = x } else null,
                    onDragEnd = { x ->
                        podBuildSwipeTarget(category, x)?.takeIf { abs(x) >= threshold }?.let {
                            drag = 0f
                            confirm(it)
                        }
                    },
                    onEnd = {
                        val from = drag
                        if (from != 0f) snapBack = scope.launch {
                            animate(from, 0f, animationSpec = tween(150)) { value, _ -> drag = value }
                        }
                    })
            }
            .semantics {
                contentDescription = "$name, ${tile.copyIds.size} copies, ${category.podBuildLabel()}"
                if (editable) onClick("Move $name") { menus.open = key; true }
            }) {
            CardArtwork(reference, name, width, height, Modifier.fillMaxSize(), placeholder = { PodCardPlaceholder(name) })
            if (tile.copyIds.size > 1) Surface(Modifier.align(Alignment.TopEnd), color = MaterialTheme.colorScheme.secondaryContainer) {
                Text("×${tile.copyIds.size}", Modifier.padding(2.dp), fontSize = 11.sp)
            }
        }
        podBuildSwipeTarget(category, drag)?.let { target ->
            PodBuildSwipeCopy(key, reference, name, width, drag, (abs(drag) / threshold).coerceIn(0f, 1f), target)
        }
        PodCardMenu(expanded = menus.open == key && editable, dismiss = { if (menus.open == key) menus.open = null }) {
            listOf(right, left).forEach { destination ->
                DropdownMenuItem(text = { Text("→ ${destination.podBuildLabel()}") }, onClick = {
                    menus.open = null
                    confirm(destination)
                })
            }
        }
    }
}

/** A non-touchable window escapes the list's clipping, like the pick screen's swipe copy. */
@Composable
internal fun PodBuildSwipeCopy(
    key: String, reference: ArtworkReference?, name: String, width: Int, drag: Float, progress: Float,
    target: PodDeckCategory,
) {
    val density = LocalDensity.current
    val cardWidth = with(density) { width.toDp() }
    val cardHeight = cardWidth * 1.4f
    // Reserve space for the rotated corners instead of clipping them to the popup window.
    val padding = cardHeight * .1f
    val paddingPx = with(density) { padding.roundToPx() }
    val position = remember(drag, paddingPx) { object : PopupPositionProvider {
        override fun calculatePosition(anchorBounds: IntRect, windowSize: IntSize,
            layoutDirection: LayoutDirection, popupContentSize: IntSize) =
            anchorBounds.topLeft + IntOffset(drag.roundToInt() - paddingPx, -paddingPx)
    } }
    Popup(position, properties = PopupProperties(
        flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
        dismissOnBackPress = false, dismissOnClickOutside = false, excludeFromSystemGesture = false,
    )) {
        Box(Modifier.requiredSize(cardWidth + padding * 2, cardHeight + padding * 2).padding(padding)) {
            Box(Modifier.fillMaxSize().testTag("pod-build-swipe-copy-$key")
                .clearAndSetSemantics { stateDescription = target.podBuildLabel() }
                .graphicsLayer {
                    alpha = .7f
                    rotationZ = 8f * progress * if (drag >= 0f) 1f else -1f
                    shadowElevation = with(density) { 8.dp.toPx() }
                    shape = RoundedCornerShape(4.dp)
                    clip = true
                }) {
                CardArtwork(reference, name, width, (width * 1.4f).roundToInt().coerceAtLeast(1), Modifier.fillMaxSize(),
                    placeholder = { PodCardPlaceholder(name) }, previewReference = reference)
                Box(Modifier.matchParentSize().background(target.podBuildColor().copy(alpha = .35f * progress)))
            }
        }
    }
}

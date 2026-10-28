package org.asyncttwiz.android

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.launch
import org.asyncttwiz.protocol.PodDeckCategory
import kotlin.math.abs

private const val PodBuildFeedFlickMillis = 180

private data class PodBuildFeedFlick(val sequence: Int, val card: PodBuildFeedCard, val offset: Float, val left: Boolean, val color: Color)

/** Full-screen one-card-at-a-time review of a Build column or section; system Back closes it. */
@Composable
internal fun PodBuildFeedDialog(
    feed: PodBuildFeed,
    mainCount: Long,
    editable: Boolean,
    nextLabel: String?,
    onChange: (PodBuildFeed) -> Unit,
    onMove: (copyId: String, category: PodDeckCategory) -> Unit,
    onNext: () -> Unit,
    onClose: () -> Unit,
) {
    val keepColor = feed.category.podBuildColor()
    val demoteColor = feed.target.podBuildColor()
    var flick by remember { mutableStateOf<PodBuildFeedFlick?>(null) }
    val latestFeed by rememberUpdatedState(feed)
    val decide: (Boolean, Float) -> Unit = { demote, offset ->
        val current = latestFeed
        current.current?.takeIf { editable }?.let { card ->
            if (demote) onMove(card.copyId, current.target)
            onChange(current.decide(demote))
            flick = PodBuildFeedFlick((flick?.sequence ?: 0) + 1, card, offset, demote, if (demote) demoteColor else keepColor)
        }
    }
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        TrackGameDialogInput()
        ScreenTheme {
            Surface(Modifier.fillMaxSize().testTag("pod-build-feed"), color = MaterialTheme.colorScheme.surface) {
                Column(Modifier.fillMaxSize()) {
                    Row(Modifier.fillMaxWidth().height(56.dp).padding(start = 4.dp, end = 16.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        ActionSquare("Undo", "↶", editable && feed.canUndo, icon = { TableChromeIcon("undo") }, onClick = {
                            val (previous, restore) = latestFeed.undo()
                            restore?.let { onMove(it, previous.category) }
                            onChange(previous)
                        })
                        Text(feed.title, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, color = keepColor,
                            maxLines = 1)
                        Text(feed.progress, style = MaterialTheme.typography.labelLarge)
                        Text("Main $mainCount", style = MaterialTheme.typography.labelLarge, color = PodPickGreen)
                    }
                    BoxWithConstraints(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 16.dp),
                        contentAlignment = Alignment.Center) {
                        // Leave room for the hint row under the card.
                        val cardWidth = minOf(maxWidth, (maxHeight - 40.dp) / 1.4f).coerceAtLeast(1.dp)
                        val current = feed.current
                        if (current == null) PodBuildFeedEnd(feed.summary, nextLabel, onNext, onClose)
                        else key(feed.position, current.copyId) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                PodBuildFeedCardView(current, cardWidth, keepColor, demoteColor, feed.target, editable,
                                    onDecide = decide)
                                Row(Modifier.width(cardWidth).height(40.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Text("◀ ${feed.target.podBuildLabel()}", Modifier.weight(1f), color = demoteColor,
                                        style = MaterialTheme.typography.labelLarge)
                                    Text("Keep ▶", color = keepColor, style = MaterialTheme.typography.labelLarge)
                                }
                            }
                        }
                        flick?.let { gone -> key(gone.sequence) {
                            PodBuildFeedFlickCard(gone, cardWidth) { if (flick?.sequence == gone.sequence) flick = null }
                        } }
                    }
                }
            }
        }
    }
}

@Composable
private fun PodBuildFeedEnd(summary: String, nextLabel: String?, onNext: () -> Unit, onClose: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(24.dp)) {
        Text(summary, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (nextLabel != null) {
                DialogCancelButton("Done", onClose)
                DialogMainButton("Next: $nextLabel →", onNext)
            } else DialogMainButton("Done", onClose)
        }
    }
}

/** The card under review: tap flips double-faced cards; a horizontal swipe keeps (right) or demotes (left). */
@Composable
private fun PodBuildFeedCardView(
    card: PodBuildFeedCard, width: Dp, keepColor: Color, demoteColor: Color, target: PodDeckCategory,
    editable: Boolean, onDecide: (demote: Boolean, offset: Float) -> Unit,
) {
    val faces = remember(card.definition) { card.definition?.let(::podPreviewFaces).orEmpty().ifEmpty { listOf(0) } }
    var face by remember { mutableIntStateOf(0) }
    val offset = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val widthPx = with(density) { width.toPx() }
    // Same feel as the pick screen: commit past 30% of the card width.
    val threshold = widthPx * .3f
    val latestDecide by rememberUpdatedState(onDecide)
    val name = card.definition?.name ?: "Card details unavailable"
    val progress = (abs(offset.value) / threshold).coerceIn(0f, 1f)
    val tint = (if (offset.value >= 0f) keepColor else demoteColor).copy(alpha = .35f * progress)
    Box(Modifier.size(width, width * 1.4f).testTag("pod-build-feed-card-${card.copyId}")
        .graphicsLayer {
            translationX = offset.value
            rotationZ = 8f * progress * if (offset.value >= 0f) 1f else -1f
        }
        .semantics {
            contentDescription = name
            if (faces.size > 1) stateDescription = "Face ${face + 1} of ${faces.size}"
            if (editable) customActions = listOf(
                CustomAccessibilityAction("Keep") { latestDecide(false, 0f); true },
                CustomAccessibilityAction("Move to ${target.podBuildLabel()}") { latestDecide(true, 0f); true },
            )
        }
        .pointerInput(faces.size) { detectTapGestures(onTap = { if (faces.size > 1) face = (face + 1) % faces.size }) }
        .pointerInput(editable, threshold) {
            if (!editable) return@pointerInput
            var dragged = 0f
            detectHorizontalDragGestures(
                onDragStart = { dragged = offset.value },
                onDragEnd = {
                    val x = dragged
                    if (abs(x) >= threshold) latestDecide(x < 0f, x)
                    else scope.launch { offset.animateTo(0f, tween(150)) }
                },
                onDragCancel = { scope.launch { offset.animateTo(0f, tween(150)) } },
            ) { change, amount ->
                change.consume()
                dragged += amount
                scope.launch { offset.snapTo(dragged) }
            }
        }) {
        PodBuildFeedArtwork(card, faces[face], width)
        Box(Modifier.matchParentSize().background(tint, RoundedCornerShape(4.dp)))
    }
}

/** The decided card flies off the side it was swiped to and fades, revealing the next one. */
@Composable
private fun PodBuildFeedFlickCard(flick: PodBuildFeedFlick, width: Dp, done: () -> Unit) {
    val widthPx = with(LocalDensity.current) { width.toPx() }
    val direction = if (flick.left) -1f else 1f
    val travel = remember { Animatable(0f) }
    val currentDone by rememberUpdatedState(done)
    LaunchedEffect(Unit) { travel.animateTo(1f, tween(PodBuildFeedFlickMillis)); currentDone() }
    // Hint-row height below the card keeps the flying copy level with the card it replaced.
    Box(Modifier.padding(bottom = 40.dp).size(width, width * 1.4f).graphicsLayer {
        translationX = flick.offset + direction * widthPx * travel.value
        rotationZ = 8f * direction
        alpha = 1f - travel.value
    }) {
        val faces = flick.card.definition?.let(::podPreviewFaces).orEmpty().ifEmpty { listOf(0) }
        PodBuildFeedArtwork(flick.card, faces.first(), width)
        Box(Modifier.matchParentSize().background(flick.color.copy(alpha = .35f), RoundedCornerShape(4.dp)))
    }
}

@Composable
private fun PodBuildFeedArtwork(card: PodBuildFeedCard, face: Int, width: Dp) {
    val density = LocalDensity.current
    val definition = card.definition
    val name = definition?.faces?.getOrNull(face)?.name ?: definition?.name ?: "Card details unavailable"
    val reference = remember(card.digest, definition, face) {
        definition?.takeIf { it.faces.isNotEmpty() }?.let { InspectedCard.retained(card.digest, it) }?.artwork?.getOrNull(face)
    }
    CardArtwork(reference?.copy(size = "large"), name,
        with(density) { width.roundToPx() }.coerceIn(1, 4096),
        with(density) { (width * 1.4f).roundToPx() }.coerceIn(1, 4096),
        Modifier.fillMaxSize(), placeholder = { PodCardPlaceholder(name) }, previewReference = reference?.copy(size = "normal"))
}

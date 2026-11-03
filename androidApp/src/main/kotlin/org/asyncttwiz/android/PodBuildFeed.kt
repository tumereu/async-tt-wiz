package org.asyncttwiz.android

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.FlipToBack
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.compose.ui.platform.LocalView
import android.view.ViewGroup
import kotlinx.coroutines.launch
import org.asyncttwiz.protocol.PodDeckCategory
import kotlin.math.abs
import kotlin.math.floor

private const val PodBuildFeedFlickMillis = 180

/** A decided card flying off: [direction] -1 left, 1 right (moved by a swipe or its button), 0 up (kept by a tap). */
private data class PodBuildFeedFlick(val sequence: Int, val card: PodBuildFeedCard, val offset: Float, val direction: Int, val color: Color)

/**
 * Full-screen one-card-at-a-time review of a Build column or section; the corner ✕ and system Back close it. A tap keeps
 * the card where it is, swipes move it as in the columns (DRF-099), and the bottom row's buttons do the same.
 * Once the last card's flick has played, [onFinish] gets the finished feed (the caller closes it).
 */
@Composable
internal fun PodBuildFeedDialog(
    feed: PodBuildFeed,
    mainCount: Long,
    editable: Boolean,
    onChange: (PodBuildFeed) -> Unit,
    onMove: (copyId: String, category: PodDeckCategory) -> Unit,
    onClose: () -> Unit,
    onFinish: (PodBuildFeed) -> Unit = { onClose() },
    onRevert: (copyId: String, category: PodDeckCategory) -> Unit = onMove,
) {
    val keepColor = feed.category.podBuildColor()
    val rightColor = feed.right.podBuildColor()
    val leftColor = feed.left.podBuildColor()
    var flick by remember { mutableStateOf<PodBuildFeedFlick?>(null) }
    var face by remember(feed.position) { mutableIntStateOf(0) }
    val latestFeed by rememberUpdatedState(feed)
    val latestFinish by rememberUpdatedState(onFinish)
    // direction: -1 left, 1 right (moved), 0 kept
    val decide: (Int, Float) -> Unit = { direction, offset ->
        val current = latestFeed
        current.current?.takeIf { editable }?.let { card ->
            val destination = when (direction) { 1 -> current.right; -1 -> current.left; else -> null }
            destination?.let { onMove(card.copyId, it) }
            onChange(current.decide(destination))
            flick = PodBuildFeedFlick((flick?.sequence ?: 0) + 1, card, offset, direction,
                destination?.podBuildColor() ?: keepColor)
        }
    }
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        TrackGameDialogInput()
        // A wrap-content dialog window can flip between neighbouring pixel widths; pin it to the screen.
        val window = (LocalView.current.parent as? DialogWindowProvider)?.window
        SideEffect { window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT) }
        ScreenTheme {
            Surface(Modifier.fillMaxSize().testTag("pod-build-feed"), color = MaterialTheme.colorScheme.surface) {
                Column(Modifier.fillMaxSize()) {
                    Row(Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        ActionSquare("Undo", "↶", editable && feed.canUndo, icon = { TableChromeIcon("undo") }, onClick = {
                            val (previous, restore) = latestFeed.undo()
                            restore?.let { onRevert(it, previous.category) }
                            onChange(previous)
                        })
                        Text(feed.title, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, color = keepColor,
                            maxLines = 1)
                        Text(feed.progress, style = MaterialTheme.typography.labelLarge)
                        Text("Main $mainCount", style = MaterialTheme.typography.labelLarge, color = PodPickGreen)
                        ScreenIconButton("Close review", "close", onClose)
                    }
                    BoxWithConstraints(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 16.dp),
                        contentAlignment = Alignment.Center) {
                        // Leave room for the button row under the card. Whole pixels, so the card never
                        // rounds wider than the space it was sized for (that fed back into the window size).
                        val density = LocalDensity.current
                        val cardWidth = with(density) {
                            floor(minOf(maxWidth.toPx(), (maxHeight - PodBuildFeedButtonsHeight).toPx() / 1.4f)).coerceAtLeast(1f).toDp()
                        }
                        val current = feed.current
                        // Past the last card only its flick shows; the feed closes when it has played.
                        if (current != null) key(feed.position, current.copyId) {
                            val faces = remember(current.definition) {
                                current.definition?.let(::podPreviewFaces).orEmpty().ifEmpty { listOf(0) } }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                PodBuildFeedCardView(current, faces[face.coerceIn(0, faces.lastIndex)], faces.size, cardWidth,
                                    rightColor, leftColor, feed, editable, onDecide = decide)
                                // The same as the gestures: icon + word buttons in each destination's colour (I-D48)
                                Row(Modifier.width(cardWidth).height(PodBuildFeedButtonsHeight),
                                    verticalAlignment = Alignment.CenterVertically) {
                                    PodBuildFeedButton("Move to ${feed.left.podBuildLabel()}", feed.left.podBuildLabel(),
                                        Icons.AutoMirrored.Outlined.ArrowBack, leftColor, editable, iconFirst = true,
                                        modifier = Modifier.testTag("pod-build-feed-left")) { decide(-1, 0f) }
                                    Spacer(Modifier.weight(1f))
                                    if (faces.size > 1) HoldTip("Other side") {
                                        IconButton({ face = (face + 1) % faces.size }, Modifier.size(40.dp)
                                            .testTag("pod-build-feed-flip")) {
                                            Icon(Icons.Outlined.FlipToBack, "Other side", Modifier.size(20.dp))
                                        }
                                    }
                                    PodBuildFeedButton("Keep", "Keep", Icons.Outlined.Check, keepColor, editable, iconFirst = true,
                                        modifier = Modifier.testTag("pod-build-feed-keep")) { decide(0, 0f) }
                                    Spacer(Modifier.weight(1f))
                                    PodBuildFeedButton("Move to ${feed.right.podBuildLabel()}", feed.right.podBuildLabel(),
                                        Icons.AutoMirrored.Outlined.ArrowForward, rightColor, editable, iconFirst = false,
                                        modifier = Modifier.testTag("pod-build-feed-right")) { decide(1, 0f) }
                                }
                            }
                        }
                        flick?.let { gone -> key(gone.sequence) {
                            PodBuildFeedFlickCard(gone, cardWidth) {
                                if (flick?.sequence == gone.sequence) {
                                    flick = null
                                    latestFeed.takeIf { it.finished }?.let(latestFinish)
                                }
                            }
                        } }
                    }
                }
            }
        }
    }
}

/** Height of the button row under the reviewed card. */
private val PodBuildFeedButtonsHeight = 48.dp

/** One of the review's bottom buttons: an icon beside its word ([label]) in [color]; [description] for accessibility. */
@Composable
private fun PodBuildFeedButton(description: String, label: String, icon: ImageVector, color: Color, enabled: Boolean,
    iconFirst: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    TextButton(onClick, modifier.heightIn(min = 40.dp).semantics { contentDescription = description }, enabled = enabled,
        colors = ButtonDefaults.textButtonColors(contentColor = color), contentPadding = PaddingValues(horizontal = 8.dp)) {
        if (iconFirst) { Icon(icon, null, Modifier.size(18.dp)); Spacer(Modifier.width(4.dp)) }
        Text(label, style = MaterialTheme.typography.labelLarge)
        if (!iconFirst) { Spacer(Modifier.width(4.dp)); Icon(icon, null, Modifier.size(18.dp)) }
    }
}

/** The card under review ([face] of its [faceCount] shown): a tap keeps it, a horizontal swipe moves it as in the
 * columns (right to [PodBuildFeed.right], left to [PodBuildFeed.left], DRF-099). */
@Composable
private fun PodBuildFeedCardView(
    card: PodBuildFeedCard, face: Int, faceCount: Int, width: Dp, rightColor: Color, leftColor: Color, feed: PodBuildFeed,
    editable: Boolean, onDecide: (direction: Int, offset: Float) -> Unit,
) {
    val offset = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val widthPx = with(density) { width.toPx() }
    // Same feel as the pick screen: commit past 30% of the card width.
    val threshold = widthPx * .3f
    // Gestures restart only with the card itself: a recomposition (size, flags) must never drop a swipe in progress.
    val latestThreshold by rememberUpdatedState(threshold)
    val latestEditable by rememberUpdatedState(editable)
    val latestDecide by rememberUpdatedState(onDecide)
    val name = card.definition?.name ?: "Card details unavailable"
    val progress = (abs(offset.value) / threshold).coerceIn(0f, 1f)
    val tint = (if (offset.value >= 0f) rightColor else leftColor).copy(alpha = .35f * progress)
    Box(Modifier.size(width, width * 1.4f).testTag("pod-build-feed-card-${card.copyId}")
        .graphicsLayer {
            translationX = offset.value
            rotationZ = 8f * progress * if (offset.value >= 0f) 1f else -1f
        }
        .semantics {
            contentDescription = name
            if (faceCount > 1) stateDescription = "Face ${face + 1} of $faceCount"
            if (editable) customActions = listOf(
                CustomAccessibilityAction("Keep") { latestDecide(0, 0f); true },
                CustomAccessibilityAction("Move to ${feed.right.podBuildLabel()}") { latestDecide(1, 0f); true },
                CustomAccessibilityAction("Move to ${feed.left.podBuildLabel()}") { latestDecide(-1, 0f); true },
            )
        }
        // DRF-108: a tap does what a swipe right did before: keeps the card where it is
        .pointerInput(Unit) { detectTapGestures(onTap = { if (latestEditable) latestDecide(0, 0f) }) }
        .pointerInput(Unit) {
            var dragged = 0f
            detectHorizontalDragGestures(
                onDragStart = { dragged = offset.value },
                onDragEnd = {
                    val x = dragged
                    if (latestEditable && abs(x) >= latestThreshold) latestDecide(if (x < 0f) -1 else 1, x)
                    else scope.launch { offset.animateTo(0f, tween(150)) }
                },
                onDragCancel = { scope.launch { offset.animateTo(0f, tween(150)) } },
            ) { change, amount ->
                if (!latestEditable) return@detectHorizontalDragGestures
                change.consume()
                dragged += amount
                scope.launch { offset.snapTo(dragged) }
            }
        }) {
        PodBuildFeedArtwork(card, face, width)
        Box(Modifier.matchParentSize().background(tint, RoundedCornerShape(4.dp)))
    }
}

/** The decided card flies off the side it was swiped to (up when kept) and fades, revealing the next one. */
@Composable
private fun PodBuildFeedFlickCard(flick: PodBuildFeedFlick, width: Dp, done: () -> Unit) {
    val widthPx = with(LocalDensity.current) { width.toPx() }
    val direction = flick.direction.toFloat()
    val travel = remember { Animatable(0f) }
    val currentDone by rememberUpdatedState(done)
    LaunchedEffect(Unit) { travel.animateTo(1f, tween(PodBuildFeedFlickMillis)); currentDone() }
    // Hint-row height below the card keeps the flying copy level with the card it replaced.
    // Nothing in it handles pointers, so it never takes a touch meant for the next card.
    Box(Modifier.padding(bottom = PodBuildFeedButtonsHeight).size(width, width * 1.4f).clearAndSetSemantics {}.graphicsLayer {
        translationX = flick.offset + direction * widthPx * travel.value
        if (flick.direction == 0) translationY = -widthPx * .5f * travel.value
        rotationZ = 8f * direction
        alpha = 1f - travel.value
    }) {
        val faces = flick.card.definition?.let(::podPreviewFaces).orEmpty().ifEmpty { listOf(0) }
        PodBuildFeedArtwork(flick.card, faces.first(), width, touchable = false)
        Box(Modifier.matchParentSize().background(flick.color.copy(alpha = .35f), RoundedCornerShape(4.dp)))
    }
}

@Composable
private fun PodBuildFeedArtwork(card: PodBuildFeedCard, face: Int, width: Dp, touchable: Boolean = true) {
    val density = LocalDensity.current
    val definition = card.definition
    val name = definition?.faces?.getOrNull(face)?.name ?: definition?.name ?: "Card details unavailable"
    val reference = remember(card.digest, definition, face) {
        definition?.takeIf { it.faces.isNotEmpty() }?.let { InspectedCard.retained(card.digest, it) }?.artwork?.getOrNull(face)
    }
    CardArtwork(reference?.copy(size = "large"), name,
        with(density) { width.roundToPx() }.coerceIn(1, 4096),
        with(density) { (width * 1.4f).roundToPx() }.coerceIn(1, 4096),
        Modifier.fillMaxSize(), placeholder = {
            // The usual placeholder is a Surface, which blocks touches to anything beneath it.
            if (touchable) PodCardPlaceholder(name)
            else Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(4.dp)),
                contentAlignment = Alignment.Center) { Text(name, textAlign = TextAlign.Center) }
        }, previewReference = reference?.copy(size = "normal"))
}

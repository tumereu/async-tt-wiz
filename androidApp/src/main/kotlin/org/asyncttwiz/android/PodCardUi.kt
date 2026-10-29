package org.asyncttwiz.android

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.ViewColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import org.asyncttwiz.protocol.CardDefinition
import org.asyncttwiz.protocol.DraftCopy

@Composable
internal fun PodCardPlaceholder(name: String) {
    Surface(Modifier.fillMaxSize(), shape = MaterialTheme.shapes.extraSmall,
        color = MaterialTheme.colorScheme.surfaceVariant,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
        Box(Modifier.padding(3.dp), contentAlignment = Alignment.Center) {
            Text(name, fontSize = 10.sp, lineHeight = 12.sp, maxLines = 3,
                overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
        }
    }
}

/** Physical sides only: split/adventure/flip/room parts still use their single printed image.
 * Used by the Build feed, which flips between them on tap; hold previews show the front only. */
internal fun podPreviewFaces(definition: CardDefinition): List<Int> {
    if (definition.faces.isEmpty()) return emptyList()
    return if (definition.layout in setOf("transform", "modal_dfc", "meld", "reversible_card", "double_faced_token") ||
        definition.faces.any { "Battle" in it.typeLine } || definition.token && definition.faces.size > 1) {
        // A meld snapshot can include the other front component; the result is the last side.
        listOf(0, definition.faces.lastIndex).distinct()
    } else listOf(0)
}

@Composable
internal fun PodPickArtPreview(copy: DraftCopy, definition: CardDefinition?, scroll: ScrollState = rememberScrollState()) {
    val card = remember(copy.definition, definition) {
        definition?.takeIf { it.faces.isNotEmpty() }?.let { InspectedCard.retained(copy.definition, it) }
    }
    PodCardArtPreview(card, definition?.name ?: "Card data unavailable", scroll = scroll)
}

@Composable
internal fun PodCardArtPreview(card: InspectedCard?, fallback: String, scroll: ScrollState = rememberScrollState()) {
    // Shown only while a finger holds a card; that finger's release closes it.
    Popup(alignment = Alignment.Center, properties = PopupProperties(focusable = false)) {
        PodArtPreviewContent(card, fallback, scroll)
    }
}

@Composable
internal fun PodArtPreviewContent(card: InspectedCard?, fallback: String, scroll: ScrollState = rememberScrollState()) {
    // Like the in-game hold preview: one face (the front of a double-faced card), never both stacked.
    val faces = listOf(0)
    BoxWithConstraints(Modifier.fillMaxSize().testTag("pod-pick-art-preview"), contentAlignment = Alignment.Center) {
        val roomWidth = maxWidth - 16.dp
        val roomHeight = maxHeight - 16.dp
        // A card printed sideways shows turned and as large as fits (held previews have no rotate button).
        val turns = faces.map { card?.orientation(it)?.degrees ?: 0 }
        val sizes = turns.map { degrees ->
            if (degrees % 180 != 0) inspectedArtworkSize(true, roomWidth, if (faces.size == 1) roomHeight else Dp.Infinity)
                .let { it.width to it.height }
            else (if (faces.size == 1) minOf(roomWidth, roomHeight / 1.4f) else minOf(roomWidth, InspectedArtworkMaxWidth))
                .let { it to it * 1.4f }
        }
        val density = LocalDensity.current
        ArtworkViewport(Modifier.width(sizes.maxOf { it.first }).heightIn(max = roomHeight)
            .semantics { contentDescription = "Card artwork viewport" }, scroll) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                faces.forEachIndexed { position, index ->
                    val (width, height) = sizes[position]
                    val name = if (faces.size == 1) fallback else card?.definition?.faces?.getOrNull(index)?.name ?: fallback
                    val reference = card?.artwork?.getOrNull(index)
                    CardArtwork(reference?.copy(size = "large"), name,
                        with(density) { width.roundToPx() }.coerceIn(1, 4096),
                        with(density) { height.roundToPx() }.coerceIn(1, 4096),
                        Modifier.requiredSize(width, height).testTag("pod-preview-face-$index"),
                        placeholder = { PodCardPlaceholder(name) }, previewReference = reference?.copy(size = "normal"),
                        degrees = turns[position])
                }
            }
        }
    }
}

/** Screen-wide owner of the single open card menu: simultaneous taps leave only the latest open. */
@Stable
internal class PodCardMenuOwner {
    var open by mutableStateOf<Any?>(null)
}

/** Keep a card action menu at the card's top edge, rather than below its full height. */
@Composable
internal fun PodCardMenu(expanded: Boolean, dismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    if (!expanded) return
    val margin = with(LocalDensity.current) { 8.dp.roundToPx() }
    val position = remember(margin) { object : PopupPositionProvider {
        override fun calculatePosition(anchorBounds: IntRect, windowSize: IntSize,
            layoutDirection: LayoutDirection, popupContentSize: IntSize): IntOffset {
            val x = if (layoutDirection == LayoutDirection.Ltr) anchorBounds.left else anchorBounds.right - popupContentSize.width
            return IntOffset(x.coerceIn(margin, (windowSize.width - popupContentSize.width - margin).coerceAtLeast(margin)),
                anchorBounds.top.coerceIn(margin, (windowSize.height - popupContentSize.height - margin).coerceAtLeast(margin)))
        }
    } }
    Popup(position, onDismissRequest = dismiss, properties = PopupProperties(focusable = true)) {
        Surface(shape = MaterialTheme.shapes.extraSmall, shadowElevation = 8.dp,
            color = MaterialTheme.colorScheme.surfaceContainer) {
            Column(Modifier.widthIn(min = 112.dp, max = 200.dp).heightIn(max = 320.dp)
                .verticalScroll(rememberScrollState()).padding(vertical = 4.dp), content = content)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PodColumnControl(label: String, description: String, value: Int, range: IntRange, change: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.semantics(mergeDescendants = true) {
        contentDescription = description
        text = AnnotatedString(label)
        progressBarRangeInfo = ProgressBarRangeInfo(value.toFloat(), range.first.toFloat()..range.last.toFloat(),
            (range.last - range.first - 1).coerceAtLeast(0))
        setProgress { change(it.toInt().coerceIn(range)); true }
    }) {
        PodColumnButton("Fewer ${description.replaceFirstChar(Char::lowercase)}", false, value > range.first) { change(value - 1) }
        TooltipBox(positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
            tooltip = { PlainTooltip { Text(description) } }, state = rememberTooltipState()) {
            Row(Modifier.height(32.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                Icon(Icons.Default.ViewColumn, contentDescription = null, modifier = Modifier.size(16.dp))
                Text(label, style = MaterialTheme.typography.labelMedium, maxLines = 1)
            }
        }
        PodColumnButton("More ${description.replaceFirstChar(Char::lowercase)}", true, value < range.last) { change(value + 1) }
    }
}

@Composable
private fun PodColumnButton(description: String, add: Boolean, enabled: Boolean, action: () -> Unit) {
    CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp) {
        IconButton(action, Modifier.size(32.dp), enabled = enabled) {
            Icon(if (add) Icons.Default.Add else Icons.Default.Remove, description, Modifier.size(20.dp))
        }
    }
}

@Composable
internal fun PodSmallButton(description: String, text: String, enabled: Boolean, width: Dp = 32.dp, action: () -> Unit) {
    CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp) {
        TextButton(action, enabled = enabled, modifier = Modifier.size(width, 32.dp)
            .semantics { contentDescription = description }, contentPadding = PaddingValues(0.dp)) {
            Text(text, fontSize = 12.sp, maxLines = 1)
        }
    }
}

package org.asyncttwiz.android

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.asyncttwiz.protocol.*
import org.asyncttwiz.storage.ArtworkReference

@Composable
internal fun PreparationScreen(controller: LobbyController, actor: String, landscape: Boolean, hamburger: (() -> Unit)? = null) {
    val state = controller.state
    val preparation = state.preparation ?: return
    val deck = preparation.players.find { it.accountId == actor }?.deck ?: return
    val connected = state.detail?.id in state.connectedGames
    val targets = remember(deck.id) { mutableMapOf<SavedDeckSection, Rect>() }
    val alpha = remember { Animatable(0f) }
    val feedback = state.transferFeedback
    LaunchedEffect(feedback) {
        if (feedback != null) { alpha.snapTo(1f); alpha.animateTo(0f, tween(1500)) } else alpha.snapTo(0f)
    }
    Column(Modifier.fillMaxSize()) {
        ScreenHeader("Prepare deck", hamburger)
        // Always allocated, including before the first move and after the text fades.
        Box(Modifier.fillMaxWidth().height(40.dp).padding(horizontal = 16.dp), contentAlignment = Alignment.CenterStart) {
            Text(if (!connected) "Disconnected · preparation is read-only" else feedback?.text.orEmpty(), Modifier.alpha(if (!connected) 1f else alpha.value).semantics {
                liveRegion = LiveRegionMode.Polite
                contentDescription = feedback?.text?.let { "Latest transfer: $it" } ?: "Latest transfer"
            }, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
        }
        val section: @Composable (SavedDeckSection, Modifier) -> Unit = { source, modifier ->
            val title = if (source == SavedDeckSection.MAINBOARD) "Mainboard" else "Sideboard"
            val opposite = if (source == SavedDeckSection.MAINBOARD) SavedDeckSection.SIDEBOARD else SavedDeckSection.MAINBOARD
            OutlinedCard(modifier.onGloballyPositioned { targets[source] = it.boundsInRoot() }) {
                Text("$title · ${deck.count(source)}", Modifier.padding(12.dp), style = MaterialTheme.typography.titleMedium)
                val rows = deck.entries.filter { it.section == source }.groupBy { it.definition }
                LazyColumn(Modifier.fillMaxWidth().heightIn(max = if (landscape) 260.dp else 340.dp)
                    .semantics { contentDescription = "$title preparation" }) {
                    items(rows.keys.toList(), key = { "${deck.id}/$source/$it" }) { digest ->
                        val definition = preparation.definitions.find { it.digest == digest }?.definition
                        val name = definition?.name ?: "Card"
                        val quantity = rows.getValue(digest).sumOf { it.quantity.toLong() }
                        fun accessible(all: Boolean): Boolean = controller.captureTransfer(digest, source, all)?.let(controller::transfer) == true
                        Row(Modifier.fillMaxWidth().animateItem(fadeInSpec = tween(180), placementSpec = tween(180), fadeOutSpec = tween(180))
                            .semantics {
                                customActions = listOf(CustomAccessibilityAction("Move one $name to ${opposite.label()}") { accessible(false) },
                                    CustomAccessibilityAction("Move all $name to ${opposite.label()}") { accessible(true) })
                            }.padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(quantity.toString(), Modifier.widthIn(min = 28.dp).padding(horizontal = 4.dp), style = MaterialTheme.typography.bodySmall)
                            Row(Modifier.weight(1f).heightIn(min = 56.dp).clickable(onClickLabel = "Inspect $name") { controller.inspectPrepared(digest) }
                                .semantics { contentDescription = "Inspect $name in $title" }, verticalAlignment = Alignment.CenterVertically) {
                                val face = definition?.faces?.firstOrNull()
                                DeckThumbnail(face?.let { ArtworkReference(it.scryfallId, 0, digest, "small", it.imageUri) }, name, 22.dp, 30.dp)
                                Text(name, Modifier.padding(start = 8.dp, end = 4.dp), style = MaterialTheme.typography.bodyMedium)
                            }
                            for (all in listOf(false, true)) {
                                PreparationHandle("${if (all) "Move all" else "Move one"} $name from $title", if (all) "∞" else "1",
                                    connected, { controller.captureTransfer(digest, source, all) }, controller::transfer,
                                    { point -> targets[opposite]?.contains(point) == true })
                            }
                        }
                    }
                    if (rows.isEmpty()) item { Text("Empty", Modifier.padding(12.dp)) }
                }
            }
        }
        if (landscape) Row(Modifier.weight(1f).padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            section(SavedDeckSection.MAINBOARD, Modifier.weight(1f))
            section(SavedDeckSection.SIDEBOARD, Modifier.weight(1f))
        } else Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
            section(SavedDeckSection.MAINBOARD, Modifier.fillMaxWidth())
            section(SavedDeckSection.SIDEBOARD, Modifier.fillMaxWidth())
        }
    }
    DeckCardInspection(controller.inspection)
}

private fun SavedDeckSection.label() = if (this == SavedDeckSection.MAINBOARD) "Mainboard" else "Sideboard"

@Composable
private fun PreparationHandle(label: String, glyph: String, enabled: Boolean, capture: () -> PreparedTransfer?,
    submit: (PreparedTransfer) -> Boolean, opposite: (androidx.compose.ui.geometry.Offset) -> Boolean) {
    val currentCapture by rememberUpdatedState(capture)
    val currentSubmit by rememberUpdatedState(submit)
    val currentOpposite by rememberUpdatedState(opposite)
    var origin by remember { mutableStateOf(androidx.compose.ui.geometry.Offset.Zero) }
    var dragging by remember { mutableStateOf(false) }
    Surface(Modifier.width(24.dp).height(48.dp).pressFeedback(enabled).onGloballyPositioned { origin = it.positionInRoot() }
        .semantics {
            contentDescription = label; role = Role.Button
            if (!enabled) disabled()
            onClick(label) { enabled && currentCapture()?.let(currentSubmit) == true }
        }.pointerInput(enabled) {
            if (!enabled) return@pointerInput
            awaitEachGesture {
                val down = awaitFirstDown()
                val gesture = PreparationGesture(currentCapture(), viewConfiguration.touchSlop)
                try {
                    do {
                        val event = awaitPointerEvent(PointerEventPass.Main)
                        val change = event.changes.firstOrNull { it.id == down.id }
                        if (change == null || event.changes.count { it.pressed } > 1) { gesture.cancel(); break }
                        gesture.move((change.position - down.position).getDistance(), change.isConsumed)
                        dragging = gesture.dragging
                        if (gesture.dragging) change.consume()
                        if (!change.pressed) {
                            gesture.release(currentOpposite(origin + change.position))?.let(currentSubmit)
                            change.consume(); break
                        }
                    } while (event.changes.any { it.pressed })
                } finally { dragging = false }
            }
        }, border = BorderStroke(1.dp, if (dragging) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
        color = if (dragging) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface) {
        Box(contentAlignment = Alignment.Center) { Text(glyph, color = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

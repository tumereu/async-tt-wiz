package org.asyncttwiz.android

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.asyncttwiz.protocol.*
import org.asyncttwiz.storage.ArtworkReference
import kotlin.math.roundToInt

@Composable
internal fun PreparationScreen(controller: LobbyController, actor: String, landscape: Boolean, hamburger: (() -> Unit)? = null) {
    val state = controller.state
    val preparation = state.preparation ?: return
    val deck = preparation.players.find { it.accountId == actor }?.deck ?: return
    val connected = state.detail?.id in state.connectedGames
    val pending = state.detail?.id in state.pendingGames
    val alpha = remember { Animatable(0f) }
    val feedback = state.transferFeedback
    LaunchedEffect(feedback) {
        if (feedback != null) { alpha.snapTo(1f); alpha.animateTo(0f, tween(1500)) } else alpha.snapTo(0f)
    }
    val undo = controller.preparationUndoStep()
    Box(Modifier.fillMaxSize()) {
    Column(Modifier.fillMaxSize()) {
        ScreenHeader(state.ownPodDeckName ?: "Prepare deck", hamburger)
        // Always allocated, including before the first move and after the text fades.
        Box(Modifier.fillMaxWidth().height(40.dp).padding(horizontal = 16.dp), contentAlignment = Alignment.CenterStart) {
            Text(if (!connected) "Disconnected · preparation is read-only" else feedback?.text.orEmpty(), Modifier.alpha(if (!connected) 1f else alpha.value).semantics {
                liveRegion = LiveRegionMode.Polite
                contentDescription = feedback?.text?.let { "Latest transfer: $it" } ?: "Latest transfer"
            }, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
        }
        val section: @Composable (SavedDeckSection, Modifier) -> Unit = { source, modifier ->
            val title = source.label()
            val opposite = if (source == SavedDeckSection.MAINBOARD) SavedDeckSection.SIDEBOARD else SavedDeckSection.MAINBOARD
            OutlinedCard(modifier) {
                Text("$title · ${deck.count(source)}", Modifier.padding(12.dp), style = MaterialTheme.typography.titleMedium)
                val rows = deck.entries.filter { it.section == source && it.suppliedBasic == null }.groupBy { it.definition }
                LazyColumn(Modifier.fillMaxWidth().heightIn(max = if (landscape) 260.dp else 340.dp)
                    .semantics { contentDescription = "$title preparation" }) {
                    if (preparation.podRules != null) item {
                        PodBasicSteppers(PodGamePreparation.basics(deck, source), connected && !pending,
                            deck.entries.sumOf { it.quantity.toLong() } < DeckValidation.MAX_CARDS,
                            if (source == SavedDeckSection.SIDEBOARD) title else null) { land, delta -> controller.changePodBasic(land, delta, source) }
                    }
                    items(rows.keys.toList(), key = { "${deck.id}/$source/$it" }) { digest ->
                        val definition = preparation.definitions.find { it.digest == digest }?.definition
                        val name = definition?.name ?: "Card"
                        val face = definition?.faces?.firstOrNull()
                        PreparationRow(name, rows.getValue(digest).sumOf { it.quantity.toLong() }, title, opposite, connected,
                            { all -> controller.captureTransfer(digest, source, all) }, controller::transfer, { controller.inspectPrepared(digest) },
                            Modifier.animateItem(fadeInSpec = tween(180), placementSpec = tween(180), fadeOutSpec = tween(180))) {
                            DeckThumbnail(face?.let { ArtworkReference(it.scryfallId, 0, digest, "small", it.imageUri) }, name, 22.dp, 30.dp)
                        }
                    }
                    if (rows.isEmpty()) item { Text("Empty", Modifier.padding(12.dp)) }
                }
            }
        }
        // Room below the lists so the floating undo never covers a row.
        if (landscape) Row(Modifier.weight(1f).padding(start = 16.dp, end = 16.dp, bottom = 64.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            section(SavedDeckSection.MAINBOARD, Modifier.weight(1f))
            section(SavedDeckSection.SIDEBOARD, Modifier.weight(1f))
        } else Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, bottom = 72.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)) {
            section(SavedDeckSection.MAINBOARD, Modifier.fillMaxWidth())
            section(SavedDeckSection.SIDEBOARD, Modifier.fillMaxWidth())
        }
    }
        if (undo != null) {
            val enabled = connected && (undo !is PreparationStep.Basics || !pending)
            Surface(Modifier.align(Alignment.BottomStart).padding(16.dp).size(48.dp).testTag("preparation-undo"),
                shape = CircleShape, color = MaterialTheme.colorScheme.surfaceContainerHigh, shadowElevation = 4.dp) {
                IconButton({ controller.undoPreparation() }, enabled = enabled, modifier = Modifier.semantics { contentDescription = "Undo" }) {
                    TableChromeIcon("undo")
                }
            }
        }
    }
    DeckCardInspection(controller.inspection)
}

/** Tap inspects, hold opens the move menu, a horizontal swipe either way past the threshold moves one copy across. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PreparationRow(name: String, quantity: Long, title: String, opposite: SavedDeckSection, enabled: Boolean,
    capture: (Boolean) -> PreparedTransfer?, submit: (PreparedTransfer) -> Boolean, inspect: () -> Unit, modifier: Modifier,
    thumbnail: @Composable () -> Unit) {
    val currentCapture by rememberUpdatedState(capture)
    val currentSubmit by rememberUpdatedState(submit)
    var offset by remember { mutableFloatStateOf(0f) }
    var width by remember { mutableFloatStateOf(0f) }
    var menu by remember { mutableStateOf(false) }
    var captured by remember { mutableStateOf<PreparedTransfer?>(null) }
    val threshold = preparationSwipeThreshold(width, with(LocalDensity.current) { 120.dp.toPx() })
    val destination = if (opposite == SavedDeckSection.MAINBOARD) PodPickGreen else PodBuildBlue
    val drag = rememberDraggableState { offset += it }
    fun move(all: Boolean) = currentCapture(all)?.let(currentSubmit) == true
    Box(modifier.fillMaxWidth().onSizeChanged { width = it.width.toFloat() }) {
        val progress = preparationSwipeProgress(offset, threshold)
        Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).offset { IntOffset(offset.roundToInt(), 0) }
            .background(if (offset == 0f) Color.Transparent else destination.copy(alpha = if (progress >= 1f) .45f else .1f + .2f * progress))
            .draggable(drag, Orientation.Horizontal, enabled = enabled,
                onDragStarted = { captured = currentCapture(false) },
                onDragStopped = {
                    val transfer = captured; captured = null
                    if (transfer != null && preparationSwipeCommits(offset, threshold)) currentSubmit(transfer)
                    animate(offset, 0f, animationSpec = spring(stiffness = Spring.StiffnessMediumLow)) { value, _ -> offset = value }
                })
            .combinedClickable(onClickLabel = "Inspect $name", onLongClickLabel = "Move or inspect $name",
                onClick = inspect, onLongClick = { menu = true })
            .semantics {
                contentDescription = "Inspect $name in $title"
                customActions = listOf(CustomAccessibilityAction("Move one $name to ${opposite.label()}") { move(false) },
                    CustomAccessibilityAction("Move all $name to ${opposite.label()}") { move(true) })
            }.padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(quantity.toString(), Modifier.widthIn(min = 28.dp).padding(horizontal = 4.dp), style = MaterialTheme.typography.bodySmall)
            thumbnail()
            Text(name, Modifier.weight(1f).padding(start = 8.dp, end = 4.dp), style = MaterialTheme.typography.bodyMedium)
        }
        DropdownMenu(menu, { menu = false }) {
            val target = opposite.label()
            if (quantity > 1) DropdownMenuItem(text = { Text("Move one to $target") }, enabled = enabled,
                onClick = { menu = false; move(false) })
            DropdownMenuItem(text = { Text(if (quantity > 1) "Move all $quantity to $target" else "Move to $target") }, enabled = enabled,
                onClick = { menu = false; move(true) })
            DropdownMenuItem(text = { Text("Inspect") }, onClick = { menu = false; inspect() })
        }
    }
}

private fun SavedDeckSection.label() = if (this == SavedDeckSection.MAINBOARD) "Mainboard" else "Sideboard"

@Composable
internal fun PodBasicSteppers(basics: PodDeckBasics, enabled: Boolean, canAdd: Boolean,
    section: String? = null, change: (BasicLand, Int) -> Boolean) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        for (land in BasicLand.entries) Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center) {
            val count = land.count(basics)
            Text(when (land) {
                BasicLand.PLAINS -> "W"; BasicLand.ISLAND -> "U"; BasicLand.SWAMP -> "B"
                BasicLand.MOUNTAIN -> "R"; BasicLand.FOREST -> "G"
            }, fontSize = 11.sp, modifier = Modifier.semantics { contentDescription = land.cardName })
            PodSmallButton("Remove ${land.cardName}${section?.let { " from $it" }.orEmpty()}", "−", enabled && count > 0, width = 22.dp) { change(land, -1) }
            Text(count.toString(), Modifier.weight(1f).semantics { contentDescription = "${land.cardName}${section?.let { " in $it" }.orEmpty()} count: $count" },
                fontSize = 11.sp, maxLines = 1, textAlign = TextAlign.Center, overflow = TextOverflow.Ellipsis)
            PodSmallButton("Add ${land.cardName}${section?.let { " to $it" }.orEmpty()}", "+", enabled && canAdd, width = 22.dp) { change(land, 1) }
        }
    }
}

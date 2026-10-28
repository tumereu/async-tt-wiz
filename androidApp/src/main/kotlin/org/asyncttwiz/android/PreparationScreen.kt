package org.asyncttwiz.android

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.asyncttwiz.protocol.*
import kotlin.math.roundToInt

private val PreparationGutter = 16.dp
private val PreparationTileMinimum = 80.dp
private val PreparationTileGap = 4.dp
/** Same distance as a Build card swipe. */
private val PreparationSwipeThreshold = 20.dp
private val PreparationBadge = Color(0xE6202A2E)

private fun SavedDeckSection.preparationColor() = if (this == SavedDeckSection.MAINBOARD) PodPickGreen else PodBuildBlue

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
    val definitions = remember(preparation.definitions) { preparation.definitions.associate { it.digest to it.definition } }
    // One card popup at a time: the tap menu or the hold preview, whichever came last.
    val menus = remember { PodCardMenuOwner() }
    var held by remember { mutableStateOf<Pair<String, PreparationTile>?>(null) }
    val previewScroll = rememberScrollState()
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
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            val columns = preparationColumns((maxWidth - PreparationGutter * 2).value, PreparationTileMinimum.value, PreparationTileGap.value)
            // One scroll for both sections; bottom room keeps the floating undo off the last row.
            LazyColumn(Modifier.fillMaxSize().testTag("preparation"), contentPadding = PaddingValues(bottom = 80.dp)) {
                for (source in SavedDeckSection.entries) {
                    val title = source.label()
                    val opposite = if (source == SavedDeckSection.MAINBOARD) SavedDeckSection.SIDEBOARD else SavedDeckSection.MAINBOARD
                    val color = source.preparationColor()
                    val groups = preparationGroups(deck, source, definitions)
                    if (source == SavedDeckSection.SIDEBOARD) item("gap-$source") { Spacer(Modifier.height(16.dp)) }
                    item("header-$source") {
                        Surface(Modifier.fillMaxWidth().animateItem(fadeInSpec = null, fadeOutSpec = null, placementSpec = tween(180)),
                            color = color.copy(alpha = .16f).compositeOver(MaterialTheme.colorScheme.surfaceContainer)) {
                            Text("$title · ${deck.count(source)}", Modifier.padding(horizontal = PreparationGutter, vertical = 10.dp)
                                .semantics { heading() }, color = color, style = MaterialTheme.typography.titleLarge)
                        }
                    }
                    if (preparation.podRules != null) item("basics-$source") {
                        Box(Modifier.animateItem(fadeInSpec = null, fadeOutSpec = null, placementSpec = tween(180))
                            .padding(horizontal = PreparationGutter - 4.dp, vertical = 4.dp)) {
                            PodBasicSteppers(PodGamePreparation.basics(deck, source), connected && !pending,
                                deck.entries.sumOf { it.quantity.toLong() } < DeckValidation.MAX_CARDS,
                                if (source == SavedDeckSection.SIDEBOARD) title else null) { land, delta -> controller.changePodBasic(land, delta, source) }
                        }
                    }
                    if (groups.isEmpty() && deck.count(source) == 0L) item("empty-$source") {
                        Text("Empty", Modifier.padding(horizontal = PreparationGutter, vertical = 12.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    for (group in groups) item("group-$source-${group.type}") {
                        Column(Modifier.fillMaxWidth().animateItem(fadeInSpec = tween(180), placementSpec = tween(180), fadeOutSpec = tween(180))
                            .padding(horizontal = PreparationGutter)) {
                            Text("${group.type.label} · ${group.count}", Modifier.padding(top = 12.dp, bottom = 6.dp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.titleSmall)
                            PreparationGrid(columns, PreparationTileGap) {
                                // Keyed by card, so a tile that loses one copy keeps its gesture and place.
                                for (tile in group.tiles) key(tile.digest) {
                                    val key = "$source/${tile.digest}"
                                    PreparationCard(tile, key, title, opposite, connected, menus,
                                        { all -> controller.captureTransfer(tile.digest, source, all) }, controller::transfer,
                                        { controller.inspectPrepared(tile.digest) },
                                        onHold = { open ->
                                            if (open) {
                                                menus.open = null
                                                previewScroll.dispatchRawDelta(-previewScroll.value.toFloat())
                                                held = key to tile
                                            } else if (held?.first == key) held = null
                                        }, onHoldScroll = { previewScroll.dispatchRawDelta(it) })
                                }
                            }
                        }
                    }
                }
            }
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
    held?.second?.let { tile ->
        val card = remember(tile.digest, tile.definition) {
            tile.definition?.takeIf { it.faces.isNotEmpty() }?.let { InspectedCard.retained(tile.digest, it) }
        }
        PodCardArtPreview(card, tile.name, previewScroll)
    }
    DeckCardInspection(controller.inspection)
}

/** Row-major tiles [columns] wide, all one width, [gap] apart. One layout parent keeps keyed tiles across rows. */
@Composable
private fun PreparationGrid(columns: Int, gap: Dp, content: @Composable () -> Unit) {
    Layout(content, Modifier.fillMaxWidth()) { measurables, constraints ->
        val gapPx = gap.roundToPx()
        val width = constraints.maxWidth
        val cell = ((width - gapPx * (columns - 1)) / columns).coerceAtLeast(1)
        val rows = measurables.map { it.measure(Constraints.fixedWidth(cell)) }.chunked(columns)
        val heights = rows.map { row -> row.maxOf { it.height } }
        layout(width, (heights.sum() + gapPx * (rows.size - 1)).coerceAtLeast(0)) {
            var top = 0
            rows.forEachIndexed { index, row ->
                row.forEachIndexed { column, placeable -> placeable.place(column * (cell + gapPx), top) }
                top += heights[index] + gapPx
            }
        }
    }
}

/** The transfer captured when a swipe starts; its release submits exactly this object. */
private class PreparationSwipe { var tried = false; var transfer: PreparedTransfer? = null }

/** Tap opens the move menu, a short hold previews the card until release, a horizontal swipe either
 * way past the Build threshold moves one copy across. Only a real release commits; a cancelled gesture never moves. */
@Composable
private fun PreparationCard(tile: PreparationTile, key: String, title: String, opposite: SavedDeckSection, enabled: Boolean,
    menus: PodCardMenuOwner, capture: (Boolean) -> PreparedTransfer?, submit: (PreparedTransfer) -> Boolean, inspect: () -> Unit,
    onHold: (Boolean) -> Unit, onHoldScroll: (Float) -> Unit) {
    val currentCapture by rememberUpdatedState(capture)
    val currentSubmit by rememberUpdatedState(submit)
    val currentHold by rememberUpdatedState(onHold)
    val currentHoldScroll by rememberUpdatedState(onHoldScroll)
    var drag by remember { mutableFloatStateOf(0f) }
    var snapBack by remember { mutableStateOf<Job?>(null) }
    val swipe = remember { PreparationSwipe() }
    val scope = rememberCoroutineScope()
    LaunchedEffect(enabled) { if (!enabled) { snapBack?.cancel(); drag = 0f } }
    val threshold = with(LocalDensity.current) { PreparationSwipeThreshold.toPx() }
    val name = tile.name
    val target = opposite.label()
    fun move(all: Boolean) = currentCapture(all)?.let(currentSubmit) == true
    BoxWithConstraints(Modifier.fillMaxWidth().testTag("preparation-card-$key")) {
        val width = with(LocalDensity.current) { maxWidth.roundToPx().coerceAtLeast(1) }
        val height = (width * 1.4f).roundToInt().coerceAtLeast(1)
        val reference = remember(tile.digest, tile.definition) { podTileArtwork(tile.digest, tile.definition) }
        Box(Modifier.fillMaxWidth().aspectRatio(1f / 1.4f)
            .pointerInput(key, enabled, threshold) {
                podPickGestures(onTap = { menus.open = key }, onInspect = { currentHold(it) }, onInspectScroll = { currentHoldScroll(it) },
                    onDrag = if (enabled) { x ->
                        snapBack?.cancel()
                        if (!swipe.tried) { swipe.tried = true; swipe.transfer = currentCapture(false) }
                        drag = x
                    } else null,
                    onDragEnd = { x ->
                        val transfer = swipe.transfer
                        if (transfer != null && preparationSwipeCommits(x, threshold)) { drag = 0f; currentSubmit(transfer) }
                    },
                    onEnd = {
                        swipe.tried = false; swipe.transfer = null
                        val from = drag
                        if (from != 0f) snapBack = scope.launch { animate(from, 0f, animationSpec = tween(150)) { value, _ -> drag = value } }
                    })
            }
            .clearAndSetSemantics {
                contentDescription = "$name, ${tile.quantity} copies, $title"
                onClick("Actions for $name") { menus.open = key; true }
                customActions = listOf(CustomAccessibilityAction("Move one $name to $target") { enabled && move(false) },
                    CustomAccessibilityAction("Move all $name to $target") { enabled && move(true) },
                    CustomAccessibilityAction("Inspect $name") { inspect(); true })
            }) {
            CardArtwork(reference, name, width, height, Modifier.fillMaxSize(), placeholder = { PodCardPlaceholder(name) })
            if (tile.quantity > 1) Text("×${tile.quantity}", Modifier.align(Alignment.BottomStart).padding(2.dp)
                .background(PreparationBadge, RoundedCornerShape(3.dp)).padding(horizontal = 3.dp),
                color = Color.White, fontSize = 11.sp, lineHeight = 16.sp, maxLines = 1)
        }
        if (drag != 0f) PodBuildSwipeCopy(key, reference, name, width, drag, preparationSwipeProgress(drag, threshold),
            if (opposite == SavedDeckSection.MAINBOARD) PodDeckCategory.MAIN else PodDeckCategory.SIDE)
        PodCardMenu(expanded = menus.open == key, dismiss = { if (menus.open == key) menus.open = null }) {
            if (tile.quantity > 1) DropdownMenuItem(text = { Text("Move one to $target") }, enabled = enabled,
                onClick = { menus.open = null; move(false) })
            DropdownMenuItem(text = { Text(if (tile.quantity > 1) "Move all ${tile.quantity} to $target" else "Move to $target") },
                enabled = enabled, onClick = { menus.open = null; move(true) })
            DropdownMenuItem(text = { Text("Inspect") }, onClick = { menus.open = null; inspect() })
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

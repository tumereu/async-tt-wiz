package org.asyncttwiz.android

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
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
private val PreparationTileGap = 4.dp
/** Same distance as a Build card swipe. */
private val PreparationSwipeThreshold = 20.dp
private val PreparationBadge = Color(0xE6202A2E)

private fun SavedDeckSection.preparationColor() = sectionColor()

@Composable
fun PreparationScreen(controller: LobbyController, actor: String, landscape: Boolean, hamburger: (() -> Unit)? = null) {
    val state = controller.state
    val preparation = state.preparation ?: return
    val deck = preparation.players.find { it.accountId == actor }?.deck ?: return
    val connected = state.detail?.id in state.connectedGames
    val undo = controller.preparationUndoStep()
    val definitions = remember(preparation.definitions) { preparation.definitions.associate { it.digest to it.definition } }
    val sections = SavedDeckSection.PREPARED.map { source ->
        PreparationSection.of(SavedDeckSection.PREPARED, source, deck.count(source), preparationGroups(deck, source, definitions))
    }
    PreparationLayout(state.ownPodDeckName ?: "Prepare deck", "preparation", DeckViewScreen.PREPARATION, connected,
        state.transferFeedback, sections, object : PreparationMoves {
            override fun capture(digest: String, source: SavedDeckSection) =
                controller.captureTransfer(digest, source, false)?.let { transfer -> { controller.transfer(transfer) } }
            override fun move(digest: String, source: SavedDeckSection, all: Boolean) =
                controller.captureTransfer(digest, source, all)?.let(controller::transfer) == true
        }, undo = if (undo == null) null else ({ controller.undoPreparation() }),
        undoEnabled = connected, navigation = hamburger, actions = {
            if (controller.podDeckEditable()) PodDeckHeaderActions(controller, deck, definitions, state.ownPodDeckName.orEmpty())
        })
}

/**
 * DRF-105, DRF-166 (owner, 2026-11-11): a pod game's own deck, also after submitting, renamed (the pen) or given another
 * cover (its thumbnail: a tap lists the deck's Main and Side cards, basics not among them) from Prepare deck; the pod deck
 * changes, and with it this game's lobby.
 */
@Composable
private fun PodDeckHeaderActions(controller: LobbyController, deck: PreparedDeck, definitions: Map<String, CardDefinition>, name: String) {
    var naming by remember { mutableStateOf(false) }
    var choosingCover by remember { mutableStateOf(false) }
    val choices = remember(deck) {
        deck.entries.filter { it.suppliedBasic == null && it.section != SavedDeckSection.MAYBEBOARD }.map { it.definition }.distinct()
    }
    val cover = deck.cover?.takeIf { it in choices } ?: choices.firstOrNull()
    PodBuildCoverThumbnail(cover?.let(definitions::get), Modifier.testTag("preparation-cover")
        .clickable(enabled = choices.isNotEmpty(), onClickLabel = "Choose deck cover") { choosingCover = true }
        .semantics { contentDescription = "Choose deck cover" })
    HoldTip("Name deck") {
        IconButton({ naming = true }, Modifier.testTag("preparation-rename")) { EntryIcon("Name deck", "edit") }
    }
    if (naming) PodDeckNameDialog(name, { controller.renamePodDeck(it); naming = false }) { naming = false }
    if (choosingCover) PodDeckCoverDialog(choices, definitions, cover, "preparation-cover", { controller.coverPodDeck(it); choosingCover = false }) {
        choosingCover = false
    }
}

/** One section of a preparation screen: its cards and the section a swipe moves them to. */
class PreparationSection(val source: SavedDeckSection, val opposite: SavedDeckSection, val count: Long,
    val groups: List<PreparationGroup>) {
    companion object {
        /** [source] among the screen's two [sections]: a swipe either way moves its cards to the other (the shared rule,
         * [SectionSwipe]; LOB-048, DRF-141). */
        fun of(sections: List<SavedDeckSection>, source: SavedDeckSection, count: Long, groups: List<PreparationGroup>) =
            PreparationSection(source, SectionSwipe.target(sections, source, right = true), count, groups)
    }
}

/** What a preparation card does: [capture] the one-copy move a swipe commits on release, [move] one or all copies
 * across from the menu. */
interface PreparationMoves {
    fun capture(digest: String, source: SavedDeckSection): (() -> Boolean)?
    fun move(digest: String, source: SavedDeckSection, all: Boolean): Boolean
}

/** Withholds a preparation's cards until their first read, with a retry after a failure (see [InitialLoadArea]). */
class PreparationLoad(val loaded: Boolean, val failed: Boolean, val retry: () -> Unit, val loading: Boolean)

/**
 * The preparation screen (LOB-047–LOB-051) shared by game deck preparation and Prepare cube (DRF-142): [title] with
 * optional [navigation] and header [actions]; a line saying what last moved, or "Disconnected · preparation is
 * read-only" while not [connected]; a columns control remembered for [columnsScreen]; one scrolling page of [sections]
 * as pictures by type with swipe, tap menu and hold to inspect; and a floating Undo shown while [undo] is set. [tag]
 * names the list ("preparation"), its cards ("preparation-card-…") and the Undo button ("preparation-undo").
 */
@Composable
fun PreparationLayout(title: String, tag: String, columnsScreen: DeckViewScreen, connected: Boolean,
    feedback: TransferFeedback?, sections: List<PreparationSection>?, moves: PreparationMoves, undo: (() -> Unit)?,
    undoEnabled: Boolean, navigation: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {}, load: PreparationLoad? = null, overlay: @Composable BoxScope.() -> Unit = {}) {
    val alpha = remember { Animatable(0f) }
    LaunchedEffect(feedback) {
        if (feedback != null) { alpha.snapTo(1f); alpha.animateTo(0f, tween(1500)) } else alpha.snapTo(0f)
    }
    // One card popup at a time: the tap menu or the held card's Inspect, whichever came last.
    val menus = remember { PodCardMenuOwner() }
    var held by remember { mutableStateOf<Pair<String, PreparationTile>?>(null) }
    val previewScroll = rememberScrollState()
    val previewScreen = holdPreviewScreenHeight()
    val columns = rememberDeckViewColumns(columnsScreen)
    Box(Modifier.fillMaxSize()) {
    Column(Modifier.fillMaxSize()) {
        ScreenHeader(title, navigation, actions = actions)
        // Always allocated, including before the first move and after the text fades.
        Row(Modifier.fillMaxWidth().height(40.dp).padding(start = 16.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(if (!connected) "Disconnected · preparation is read-only" else feedback?.text.orEmpty(), Modifier.weight(1f).alpha(if (!connected) 1f else alpha.value).semantics {
                liveRegion = LiveRegionMode.Polite
                contentDescription = feedback?.text?.let { "Latest transfer: $it" } ?: "Latest transfer"
            }, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
            DeckViewColumnsControl(columns)
        }
        // One scroll for both sections; bottom room keeps the floating undo off the last row.
        @Composable fun cards(modifier: Modifier) = LazyColumn(modifier.testTag(tag), contentPadding = PaddingValues(bottom = 80.dp)) {
            for (section in sections.orEmpty()) {
                val source = section.source
                val title = source.label()
                visualDeckSection(source, section.count, section.groups, columns.count) { tile ->
                    val key = "$source/${tile.digest}"
                    PreparationCard(tile, key, "$tag-card-$key", title, section.opposite, connected, menus,
                        { moves.capture(tile.digest, source) }, { all -> moves.move(tile.digest, source, all) },
                        onHold = { open ->
                            if (open) {
                                menus.open = null
                                previewScroll.dispatchRawDelta(-previewScroll.value.toFloat())
                                held = key to tile
                            } else if (held?.first == key) held = null
                        }, onHoldScroll = { previewScroll.holdPreviewScroll(it, previewScreen) })
                }
            }
        }
        if (load == null) cards(Modifier.weight(1f).fillMaxWidth())
        else InitialLoadArea(load.loaded, Modifier.weight(1f).fillMaxWidth(), load.failed, load.retry, load.loading) {
            cards(Modifier.fillMaxSize())
        }
    }
        if (undo != null) {
            Surface(Modifier.align(Alignment.BottomStart).padding(16.dp).size(48.dp).testTag("$tag-undo"),
                shape = CircleShape, color = MaterialTheme.colorScheme.surfaceContainerHigh, shadowElevation = 4.dp) {
                HoldTip("Undo") {
                    IconButton(undo, enabled = undoEnabled, modifier = Modifier.semantics { contentDescription = "Undo" }) {
                        TableChromeIcon("undo")
                    }
                }
            }
        }
        overlay()
    }
    held?.second?.let { tile ->
        val card = remember(tile.digest, tile.definition) {
            tile.definition?.takeIf { it.faces.isNotEmpty() }?.let { InspectedCard.retained(it) }
        }
        PodCardArtPreview(card, tile.name, previewScroll)
    }
}

/** One section of the visual deck shared by game preparation, saved-deck and saved-cube viewing: a coloured "Mainboard · 60"
 * [header] (a cube has no sections) with an optional [headerAction] at its right end, then type
 * groups of card tiles [columns] wide drawn by [tile], or, with [cellWidth], tiles that wide, as many per row as fit. */
fun LazyListScope.visualDeckSection(source: SavedDeckSection, count: Long, groups: List<PreparationGroup>, columns: Int,
    header: Boolean = true, headerAction: (@Composable () -> Unit)? = null, cellWidth: Dp? = null, empty: String = "Empty",
    tile: @Composable (PreparationTile) -> Unit) {
    val title = source.label()
    val color = source.preparationColor()
    if (source != SavedDeckSection.MAINBOARD) item("gap-$source") { Spacer(Modifier.height(16.dp)) }
    if (header) item("header-$source") {
        Surface(Modifier.fillMaxWidth().animateItem(fadeInSpec = null, fadeOutSpec = null, placementSpec = tween(180)),
            color = color.copy(alpha = .16f).compositeOver(MaterialTheme.colorScheme.surfaceContainer)) {
            @Composable fun headerText(modifier: Modifier) = Text("$title · $count", modifier.padding(horizontal = PreparationGutter, vertical = 10.dp)
                .semantics { heading() }, color = color, style = MaterialTheme.typography.titleLarge)
            if (headerAction == null) headerText(Modifier)
            else Row(verticalAlignment = Alignment.CenterVertically) {
                headerText(Modifier.weight(1f))
                headerAction()
            }
        }
    }
    if (groups.isEmpty() && count == 0L) item("empty-$source") {
        Text(empty, Modifier.padding(horizontal = PreparationGutter, vertical = 12.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    for (group in groups) item("group-$source-${group.type}") {
        Column(Modifier.fillMaxWidth().animateItem(fadeInSpec = tween(180), placementSpec = tween(180), fadeOutSpec = tween(180))
            .padding(horizontal = PreparationGutter)) {
            Text("${group.type.label} · ${group.count}", Modifier.padding(top = 12.dp, bottom = 6.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.titleSmall)
            PreparationGrid(columns, PreparationTileGap, cellWidth) {
                // Keyed by card, so a tile that loses one copy keeps its gesture and place.
                for (card in group.tiles) key(card.digest) { tile(card) }
            }
        }
    }
}

/** A read-only deck tile: a short hold inspects the card until release (a tap does nothing). Nothing moves. */
@Composable
fun VisualDeckCard(tile: PreparationTile, key: String, title: String,
    onHold: (Boolean) -> Unit, onHoldScroll: (Float) -> Unit) {
    val currentHold by rememberUpdatedState(onHold)
    val currentHoldScroll by rememberUpdatedState(onHoldScroll)
    val name = tile.name
    BoxWithConstraints(Modifier.fillMaxWidth().testTag("deck-card-$key")) {
        val width = with(LocalDensity.current) { maxWidth.roundToPx().coerceAtLeast(1) }
        Box(Modifier.fillMaxWidth().aspectRatio(1f / 1.4f)
            .pointerInput(key) {
                podPickGestures(onTap = {}, onInspect = { currentHold(it) }, onInspectScroll = { currentHoldScroll(it) })
            }
            .clearAndSetSemantics {
                contentDescription = "$name, ${tile.quantity} copies, $title"
            }) { PreparationTileFace(tile, width) }
    }
}

/** Card art filling the tile, with a "×3" badge when the tile holds more than one copy. */
@Composable
private fun BoxScope.PreparationTileFace(tile: PreparationTile, width: Int,
    reference: org.asyncttwiz.storage.ArtworkReference? = remember(tile.digest, tile.definition) { podTileArtwork(tile.definition) }) {
    val height = (width * 1.4f).roundToInt().coerceAtLeast(1)
    CardArtwork(reference, tile.name, width, height, Modifier.fillMaxSize(), placeholder = { PodCardPlaceholder(tile.name) })
    if (tile.quantity > 1) Text("×${tile.quantity}", Modifier.align(Alignment.BottomStart).padding(2.dp)
        .background(PreparationBadge, RoundedCornerShape(3.dp)).padding(horizontal = 3.dp),
        color = Color.White, fontSize = 11.sp, lineHeight = 16.sp, maxLines = 1)
}

/** Row-major tiles [columns] wide, all one width, [gap] apart; with [cellWidth] tiles that wide, as many per row as fit.
 * One layout parent keeps keyed tiles across rows. */
@Composable
private fun PreparationGrid(columns: Int, gap: Dp, cellWidth: Dp? = null, content: @Composable () -> Unit) {
    Layout(content, Modifier.fillMaxWidth()) { measurables, constraints ->
        val gapPx = gap.roundToPx()
        val width = constraints.maxWidth
        val fixed = cellWidth?.roundToPx()?.coerceIn(1, width.coerceAtLeast(1))
        val columns = if (fixed == null) columns else ((width + gapPx) / (fixed + gapPx)).coerceAtLeast(1)
        val cell = fixed ?: ((width - gapPx * (columns - 1)) / columns).coerceAtLeast(1)
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

/** The moves captured when a swipe starts, one per direction (right: true); its release commits exactly the one of the
 * direction it ends in. */
private class DeckCardSwipe { var tried = false; var commits: Map<Boolean, (() -> Boolean)?> = emptyMap() }

/** Tap opens the move menu, a short hold inspects the card until release, a horizontal swipe either
 * way past the Build threshold moves one copy across. Only a real release commits; a cancelled gesture never moves. */
@Composable
private fun PreparationCard(tile: PreparationTile, key: String, tag: String, title: String, opposite: SavedDeckSection,
    enabled: Boolean, menus: PodCardMenuOwner, capture: () -> (() -> Boolean)?, move: (Boolean) -> Boolean,
    onHold: (Boolean) -> Unit, onHoldScroll: (Float) -> Unit) {
    val currentCapture by rememberUpdatedState(capture)
    val currentMove by rememberUpdatedState(move)
    val name = tile.name
    val target = opposite.label()
    fun move(all: Boolean) = currentMove(all)
    SwipeDeckCard(tile, key, tag, title, { opposite }, enabled, menus,
        capture = { _ -> currentCapture() },
        onHold = onHold, onHoldScroll = onHoldScroll,
        actions = listOf(CustomAccessibilityAction("Move one $name to $target") { enabled && move(false) },
            CustomAccessibilityAction("Move all $name to $target") { enabled && move(true) })) { close ->
        if (tile.quantity > 1) DropdownMenuItem(text = { Text("Move one to $target") }, enabled = enabled,
            onClick = { close(); move(false) })
        DropdownMenuItem(text = { Text(if (tile.quantity > 1) "Move all ${tile.quantity} to $target" else "Move to $target") },
            enabled = enabled, onClick = { close(); move(true) })
    }
}

/**
 * A deck tile that moves: tap opens its [menu], a short hold inspects the card until release, a horizontal swipe
 * either way past the Build threshold commits the action [capture] returned for its direction (right or not) when the
 * swipe started (one copy to that direction's [target], [SectionSwipe]). Only a real release commits; a cancelled
 * gesture never moves. Shared by game preparation, the card list editor and Prepare cube.
 */
@Composable
fun SwipeDeckCard(tile: PreparationTile, key: String, tag: String, title: String, target: (right: Boolean) -> SavedDeckSection,
    enabled: Boolean, menus: PodCardMenuOwner, capture: (right: Boolean) -> (() -> Boolean)?,
    onHold: (Boolean) -> Unit, onHoldScroll: (Float) -> Unit, actions: List<CustomAccessibilityAction> = emptyList(),
    menu: @Composable ColumnScope.(close: () -> Unit) -> Unit) {
    val currentCapture by rememberUpdatedState(capture)
    val currentHold by rememberUpdatedState(onHold)
    val currentHoldScroll by rememberUpdatedState(onHoldScroll)
    var drag by remember { mutableFloatStateOf(0f) }
    var snapBack by remember { mutableStateOf<Job?>(null) }
    val swipe = remember { DeckCardSwipe() }
    val scope = rememberCoroutineScope()
    LaunchedEffect(enabled) { if (!enabled) { snapBack?.cancel(); drag = 0f } }
    val threshold = with(LocalDensity.current) { PreparationSwipeThreshold.toPx() }
    val name = tile.name
    BoxWithConstraints(Modifier.fillMaxWidth().testTag(tag)) {
        val width = with(LocalDensity.current) { maxWidth.roundToPx().coerceAtLeast(1) }
        val reference = remember(tile.digest, tile.definition) { podTileArtwork(tile.definition) }
        Box(Modifier.fillMaxWidth().aspectRatio(1f / 1.4f)
            .pointerInput(key, enabled, threshold) {
                podPickGestures(onTap = { menus.open = key }, onInspect = { currentHold(it) }, onInspectScroll = { currentHoldScroll(it) },
                    onDrag = if (enabled) { x ->
                        snapBack?.cancel()
                        if (!swipe.tried) { swipe.tried = true; swipe.commits = listOf(true, false).associateWith { currentCapture(it) } }
                        drag = x
                    } else null,
                    onDragEnd = { x ->
                        val commit = swipe.commits[x > 0f]
                        if (commit != null && preparationSwipeCommits(x, threshold)) { drag = 0f; commit() }
                    },
                    onEnd = {
                        swipe.tried = false; swipe.commits = emptyMap()
                        val from = drag
                        if (from != 0f) snapBack = scope.launch { animate(from, 0f, animationSpec = tween(150)) { value, _ -> drag = value } }
                    })
            }
            .clearAndSetSemantics {
                contentDescription = "$name, ${tile.quantity} copies, $title"
                onClick("Actions for $name") { menus.open = key; true }
                if (actions.isNotEmpty()) customActions = actions
            }) { PreparationTileFace(tile, width, reference) }
        if (drag != 0f) target(drag > 0f).let { to ->
            PodBuildSwipeCopy(key, reference, name, width, drag, preparationSwipeProgress(drag, threshold), to.category(),
                tint = to.sectionFill(), label = to.shortLabel())
        }
        PodCardMenu(expanded = menus.open == key, dismiss = { if (menus.open == key) menus.open = null }) {
            menu { if (menus.open == key) menus.open = null }
        }
    }
}

private fun SavedDeckSection.label() = sectionLabel()

private fun SavedDeckSection.category() = when (this) {
    SavedDeckSection.MAINBOARD -> PodDeckCategory.MAIN
    SavedDeckSection.SIDEBOARD -> PodDeckCategory.SIDE
    SavedDeckSection.MAYBEBOARD -> PodDeckCategory.MAYBE
}


package org.asyncttwiz.android

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import org.asyncttwiz.storage.ArtworkReference

/** One picture of a hold-to-inspect view (TBL-104): a card's face, a covered card, or a Stack ability's tile. */
internal sealed interface InspectTile {
    val key: String

    /** One physical picture of a card: [reference] (null: the placeholder card), turned [degrees] clockwise; game cards
     * carry their [owner]'s frame, and the holder's own face-down card is dimmed with "Face down" (TBL-109). */
    data class Face(override val key: String, val reference: ArtworkReference?, val name: String, val degrees: Int = 0,
        val owner: Color? = null, val token: Boolean = false, val projection: Boolean = false,
        val faceDown: Boolean = false) : InspectTile

    /** A card the viewer may not see, or whose card data is missing: a card-shaped tile with [text]. */
    data class Covered(override val key: String, val text: String) : InspectTile

    /** STK-050: a triggered or activated ability on the Stack: a dark rounded panel with the source's [art] and under it
     * the source's name, the ability's text and [details] (its chosen modes and targets), outlined in its controller's
     * colour ([own]). */
    data class Ability(override val key: String, val ability: StackAbilityShown, val details: List<String>,
        val own: Boolean, val art: ArtworkReference? = null) : InspectTile
}

/**
 * The physical pictures of [card] (TBL-106), the face it shows ([shown]) first: one per side of a double-faced card
 * (transform, modal, meld, battle, double-faced token), one for a card whose parts share one printed image (split,
 * adventure, flip, aftermath, Room). A flip card on its flipped half ([flipped]) is turned upside down.
 */
internal fun inspectFaces(card: InspectedCard, shown: Int = 0, flipped: Boolean = false, key: String = "",
    owner: Color? = null, token: Boolean = false, projection: Boolean = false, faceDown: Boolean = false): List<InspectTile.Face> {
    val physical = podPreviewFaces(card.definition)
    val start = shown.takeIf { it in physical } ?: physical.firstOrNull() ?: return emptyList()
    return (listOf(start) + (physical - start)).map { index ->
        val orientation = card.orientation(index)
        val degrees = if (physical.size == 1 && flipped && orientation.alternate != null) orientation.alternate else orientation.degrees
        val name = if (physical.size == 1) card.definition.name else card.definition.faces[index].name
        InspectTile.Face("$key/$index", card.artwork[index], name, degrees, owner, token, projection, faceDown)
    }
}

/** Every hold-to-inspect view (TBL-104, SYS-049): shown while a finger holds a card, closed by its release. It covers
 * the whole window wherever it is opened from (a card on the table, in a panel or in a list). */
@Composable
internal fun InspectPopup(tiles: List<InspectTile>, scroll: ScrollState) = InspectPopup(listOf(InspectGroup("card", tiles)), scroll)

/** [InspectPopup] of several cards ([groups]), each with what changed on it; [swipe]: its pinned mode, when it has one. */
@Composable
internal fun InspectPopup(groups: List<InspectGroup>, scroll: ScrollState, swipe: InspectSwipe? = null) {
    Popup(WindowOrigin, properties = PopupProperties(focusable = false)) { InspectContent(groups, scroll, swipe) }
}

/** Places a popup at the window's top left corner, whatever its anchor. */
private object WindowOrigin : PopupPositionProvider {
    override fun calculatePosition(anchorBounds: IntRect, windowSize: IntSize, layoutDirection: LayoutDirection,
        popupContentSize: IntSize): IntOffset = IntOffset.Zero
}

/**
 * A line of Inspect under (in landscape: beside) an inspected card (TBL-104, owner 2026-11-06): one thing changed on or
 * added to it ("+1/+1 counter ×2", "+3/+3 until end of turn", "Attached to Ember Cub", "Target: Bob"); [source] names
 * what an effect comes from, shown only on request in pinned mode.
 */
internal data class InspectChange(val text: String, val source: String? = null)

/** One card in Inspect: its pictures ([tiles]: a card's faces, or an ability's tile) kept together, then its [changes]. */
internal data class InspectGroup(val key: String, val tiles: List<InspectTile>, val changes: List<InspectChange> = emptyList())

@Composable
internal fun InspectContent(tiles: List<InspectTile>, scroll: ScrollState) = InspectContent(listOf(InspectGroup("card", tiles)), scroll)

/**
 * Inspect (TBL-104): the cards' pictures over the dimmed screen, each the size of a card filling the screen, in portrait
 * one under another with each card's changes under it, in landscape side by side with them beside it; a thin line
 * separates cards (a card's faces stay together). [scroll] follows the holding finger. With [swipe] (pinned mode) a drag
 * to the left shrinks the cards and slides the changes next to them, and a close button comes in at the top right.
 */
@Composable
internal fun InspectContent(groups: List<InspectGroup>, scroll: ScrollState, swipe: InspectSwipe? = null) {
    val pinned = swipe?.pinned == true
    val progress by animateFloatAsState(swipe?.let { if (it.pinned) 1f else it.progress } ?: 0f,
        if (pinned) tween(200) else snap(), label = "inspect pin")
    BoxWithConstraints(Modifier.fillMaxSize().background(Color.Black.copy(alpha = .45f + .2f * progress)).testTag("card-inspection")
        .semantics { paneTitle = "Card inspection" }, contentAlignment = Alignment.Center) {
        val landscape = maxWidth > maxHeight
        val room = DpSize(maxWidth - 16.dp, maxHeight - 16.dp)
        val content = Modifier.semantics { contentDescription = "Card inspection content" }
        // Pinned, the close button keeps its own room at the top
        val buttonRoom = CLOSE_BUTTON_ROOM * progress
        if (landscape) Row(content.padding(top = buttonRoom).widthIn(max = room.width).horizontalScroll(scroll),
            horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            val cardsRoom = DpSize(room.width, room.height - buttonRoom)
            val changesWidth = if (pinned) pinnedChangesWidth(groups, cardsRoom, room.width) else SIDE_CHANGES_WIDTH
            groups.forEachIndexed { index, group ->
                key(group.key) {
                    if (index > 0) GroupSeparator(Modifier.padding(horizontal = 4.dp).width(1.dp).height(cardsRoom.height * .7f))
                    LandscapeGroup(group, cardsRoom, changesWidth, pinned)
                }
            }
        } else Column(content.heightIn(max = room.height).verticalScroll(scroll).padding(top = buttonRoom),
            verticalArrangement = Arrangement.spacedBy(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            groups.forEachIndexed { index, group ->
                key(group.key) {
                    if (index > 0) GroupSeparator(Modifier.padding(vertical = 4.dp).height(1.dp).width(room.width * .8f))
                    PortraitGroup(group, room, progress, pinned)
                }
            }
        }
        if (swipe != null && progress > 0f) InspectCloseButton(swipe::close, Modifier.align(Alignment.TopEnd).padding(12.dp)
            .graphicsLayer { alpha = progress; scaleX = .6f + .4f * progress; scaleY = .6f + .4f * progress })
    }
}

/** The room pinned Inspect keeps above the cards for its close button. */
private val CLOSE_BUTTON_ROOM = 60.dp

/** The thin line between two cards in Inspect (owner 2026-11-06). */
@Composable
private fun GroupSeparator(modifier: Modifier) {
    Box(modifier.background(Color.White.copy(alpha = .55f)))
}

/** In pinned mode the cards take this share of the width, their changes the rest (owner 2026-11-06). */
private const val PINNED_CARD_SHARE = .56f

/** In landscape a card's changes sit beside it, this wide. */
private val SIDE_CHANGES_WIDTH = 220.dp
/** Pinned landscape Inspect narrows the changes down to this, wrapping their lines, to keep them on the screen. */
private val MIN_SIDE_CHANGES_WIDTH = 120.dp

/**
 * Pinned landscape Inspect: how wide each card's changes may be so that the row of [groups] fits [width] (TBL-131: the
 * lines wrap rather than run off the screen); never wider than [SIDE_CHANGES_WIDTH] or narrower than
 * [MIN_SIDE_CHANGES_WIDTH] (then the row scrolls).
 */
internal fun pinnedChangesWidth(groups: List<InspectGroup>, room: DpSize, width: Dp): Dp {
    val panels = groups.count { it.changes.isNotEmpty() }
    if (panels == 0) return SIDE_CHANGES_WIDTH
    val gap = 8.dp
    // The pictures and the gaps between everything in the row (a separator is 1 dp with 4 dp on each side)
    val fixed = groups.fold(0.dp) { total, group -> total + group.tiles.fold(0.dp) { sum, tile -> sum + inspectTileSize(tile, room).width } +
        gap * (group.tiles.size - 1 + if (group.changes.isNotEmpty()) 1 else 0) } + (gap * 2 + 9.dp) * (groups.size - 1)
    return ((width - fixed) / panels).coerceIn(MIN_SIDE_CHANGES_WIDTH, SIDE_CHANGES_WIDTH)
}

/**
 * A card in portrait Inspect: its pictures, then its changes under them. At [progress] of the pinned swipe the pictures
 * shrink towards [PINNED_CARD_SHARE] of the width while the changes fold away under them and slide in beside them.
 */
@Composable
private fun PortraitGroup(group: InspectGroup, room: DpSize, progress: Float, pinned: Boolean) {
    val gap = 12.dp
    val pinnedCards = room.width * PINNED_CARD_SHARE
    val beside = room.width - pinnedCards - gap
    val cardsRoom = DpSize(room.width - (room.width - pinnedCards) * progress, room.height)
    val width = group.tiles.maxOfOrNull { inspectTileSize(it, cardsRoom).width } ?: cardsRoom.width
    Row(verticalAlignment = Alignment.Top) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            group.tiles.forEach { tile -> key(tile.key) { InspectTileView(tile, cardsRoom) } }
            if (group.changes.isNotEmpty() && progress < .5f) {
                val left = 1f - progress * 2f
                ChangesPanel(group.changes, false, Modifier.width(width).folded(left).graphicsLayer { alpha = left })
            }
        }
        if (group.changes.isNotEmpty() && progress > 0f) Box(Modifier.width((beside + gap) * progress).clipToBounds()) {
            ChangesPanel(group.changes, pinned, Modifier.wrapContentWidth(Alignment.Start, unbounded = true).padding(start = gap).width(beside)
                .graphicsLayer { alpha = progress; translationX = (1f - progress) * 32.dp.toPx() })
        }
    }
}

/** A card in landscape Inspect: its pictures side by side, its changes beside them, [changesWidth] wide. */
@Composable
private fun LandscapeGroup(group: InspectGroup, room: DpSize, changesWidth: Dp, pinned: Boolean) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        group.tiles.forEach { tile -> key(tile.key) { InspectTileView(tile, room, fit = true) } }
        if (group.changes.isNotEmpty()) ChangesPanel(group.changes, pinned, Modifier.align(Alignment.Top).width(changesWidth))
    }
}

/** Lays this out [share] of its height tall (0: folded away), from its top. */
private fun Modifier.folded(share: Float): Modifier = layout { measurable, constraints ->
    val placeable = measurable.measure(constraints)
    layout(placeable.width, (placeable.height * share.coerceIn(0f, 1f)).roundToInt()) { placeable.place(0, 0) }
}

/** What changed on a card, one line each, on a dark rounded panel; [interactive] (pinned): tapping or holding a line
 * with a source shows where it comes from. */
@Composable
private fun ChangesPanel(changes: List<InspectChange>, interactive: Boolean, modifier: Modifier = Modifier) {
    Column(modifier.semantics { contentDescription = "Card changes" }.clip(RoundedCornerShape(12.dp)).background(InspectChangesColor)
        .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)) {
        changes.forEach { change -> ChangeLine(change, interactive) }
    }
}

@Composable
private fun ChangeLine(change: InspectChange, interactive: Boolean) {
    var open by remember(change) { mutableStateOf(false) }
    var pressed by remember(change) { mutableStateOf(false) }
    val source = change.source.takeIf { interactive }
    Column(if (source == null) Modifier else Modifier.fillMaxWidth().pointerInput(change) {
        detectTapGestures(onPress = { pressed = true; tryAwaitRelease(); pressed = false }, onTap = { open = !open })
    }) {
        CardText(change.text, color = Color.White, style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp, lineHeight = 20.sp))
        if (source != null && (open || pressed)) Text("From $source", color = InspectSourceColor, fontSize = 13.sp, lineHeight = 17.sp)
    }
}

/** Pinned Inspect's close button, at the top right. */
@Composable
private fun InspectCloseButton(close: () -> Unit, modifier: Modifier) {
    Surface(onClick = close, modifier = modifier.size(44.dp).semantics { contentDescription = "Close inspection" },
        shape = CircleShape, color = Color.White, contentColor = MenuInk, shadowElevation = 6.dp) {
        Box(contentAlignment = Alignment.Center) { Icon(Icons.Outlined.Close, null, Modifier.size(24.dp)) }
    }
}

/** The changes panel's dark fill and the colour of an effect's source in it. */
private val InspectChangesColor = Color(0xf21d1f25)
private val InspectSourceColor = Color(0xffc9b8ff)

/** A tile's size within [room]: an upright card as large as fits; a card printed sideways turned and no taller than
 * an upright inspected card (TBL-108). */
internal fun inspectTileSize(tile: InspectTile, room: DpSize): DpSize =
    if (tile is InspectTile.Face && tile.degrees % 180 != 0) inspectedArtworkSize(true, room.width, room.height)
    else minOf(room.width, room.height / 1.4f).let { DpSize(it, it * 1.4f) }

@Composable
private fun InspectTileView(tile: InspectTile, room: DpSize, fit: Boolean = false) {
    val size = inspectTileSize(tile, room)
    when (tile) {
        is InspectTile.Face -> InspectFaceView(tile, size)
        is InspectTile.Covered -> Surface(Modifier.size(size).semantics { contentDescription = tile.text },
            shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceVariant) {
            Box(Modifier.padding(16.dp), contentAlignment = Alignment.Center) {
                Text(tile.text, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
            }
        }
        is InspectTile.Ability -> AbilityTile(tile, size, fit)
    }
}

@Composable
private fun InspectFaceView(tile: InspectTile.Face, size: DpSize) {
    val density = LocalDensity.current
    val (width, height) = size
    Box(Modifier.size(width, height).semantics { contentDescription = "Card image: ${tile.name}" }) {
        CardArtwork(tile.reference?.copy(size = "large"), tile.name,
            with(density) { width.roundToPx() }.coerceIn(1, 4096), with(density) { height.roundToPx() }.coerceIn(1, 4096),
            Modifier.requiredSize(width, height)
                // Inspection always shows the whole printed card, including token corners.
                .then(if (tile.owner != null) Modifier.cardFrame(tile.token, tile.owner, tile.projection) else Modifier),
            placeholder = { InspectFacePlaceholder(tile.reference, tile.name) },
            previewReference = tile.reference?.copy(size = "normal"), degrees = tile.degrees)
        // The holder's own face-down card: the front stays readable but is plainly marked.
        if (tile.faceDown) Box(Modifier.matchParentSize().background(Color.Black.copy(alpha = .55f)), contentAlignment = Alignment.Center) {
            Text("Face down", color = Color.White, style = MaterialTheme.typography.headlineSmall)
        }
    }
}

/** A face whose picture isn't downloaded: the placeholder card with its name and the download state (LOB-099). */
@Composable
private fun InspectFacePlaceholder(reference: ArtworkReference?, name: String) {
    Surface(Modifier.fillMaxSize(), shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceVariant) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally) {
            Text(name, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
            if (reference?.printing != null || reference?.customUrl != null) InspectorArtworkPlaceholder(reference, name)
        }
    }
}

/** STK-050: an ability on the Stack as a dark rounded panel as wide as an inspected card and as tall as its content (never
 * card-shaped): the source's art crop only (no border, title or text box; owner 2026-11-06), then the kind chip with
 * "Triggered ability" (or "Activated ability"), the source's name, the ability's text and what was chosen for it, outlined
 * in the controller's colour. Taller than the screen it scrolls with the rest (TBL-127); where it can't ([fit]: side by
 * side in landscape) the art gives way first. */
@Composable
private fun AbilityTile(tile: InspectTile.Ability, size: DpSize, fit: Boolean) {
    val shape = RoundedCornerShape(12.dp)
    val density = LocalDensity.current
    val artHeight = size.width / ABILITY_ART_RATIO
    val bounded = if (fit) Modifier.heightIn(max = size.height) else Modifier
    Box(Modifier.width(size.width).then(bounded).clip(shape).background(AbilityTokenColor)
        .border(2.dp, if (tile.own) OwnTableColor else OpponentTableColor, shape)
        .semantics { contentDescription = "${tile.ability.kind} of ${tile.ability.source}" }) {
        CompositionLocalProvider(LocalContentColor provides Color.White) {
            Column(Modifier.fillMaxWidth().then(bounded)) {
                Box(Modifier.fillMaxWidth().then(if (fit) Modifier.weight(1f, fill = false) else Modifier).height(artHeight)
                    .background(AbilityTokenArtColor)
                    .semantics { contentDescription = "Art of ${tile.ability.source}" }) {
                    CardArtwork(tile.art, "", with(density) { size.width.roundToPx() }.coerceIn(1, 4096),
                        with(density) { artHeight.roundToPx() }.coerceIn(1, 4096), Modifier.fillMaxSize(), crop = true,
                        placeholder = {})
                }
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AbilityKindChip(tile.ability, 24.dp)
                        Text(tile.ability.kind, style = MaterialTheme.typography.labelLarge)
                    }
                    Text(tile.ability.source, style = MaterialTheme.typography.titleLarge)
                    if (tile.ability.text.isNotEmpty()) CardText(tile.ability.text, style = MaterialTheme.typography.bodyLarge)
                    tile.details.forEach { CardText(it, style = MaterialTheme.typography.bodyMedium) }
                }
            }
        }
    }
}

/** The source's art in an ability's Inspect panel (STK-050): an art crop's width to height. */
private const val ABILITY_ART_RATIO = 1.37f

/** The fallback tile of a card without card data: its [name] on the placeholder card. */
internal fun placeholderFace(key: String, name: String): InspectTile.Face = InspectTile.Face(key, null, name)

/** Hold-to-inspect of a card outside a game ([card] null: no card data, [fallback] names it). */
internal fun inspectTiles(card: InspectedCard?, fallback: String): List<InspectTile> =
    card?.let { inspectFaces(it) }?.takeIf { it.isNotEmpty() } ?: listOf(placeholderFace("card", fallback))

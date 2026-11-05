package org.asyncttwiz.android

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign

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

    /** STK-046: a triggered or activated ability on the Stack: a card-shaped tile with the source's name, the ability's
     * text and [details] (its chosen modes and targets), outlined in its controller's colour ([own]). */
    data class Ability(override val key: String, val ability: StackAbilityShown, val details: List<String>,
        val own: Boolean) : InspectTile
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
internal fun InspectPopup(tiles: List<InspectTile>, scroll: ScrollState) {
    Popup(WindowOrigin, properties = PopupProperties(focusable = false)) { InspectContent(tiles, scroll) }
}

/** Places a popup at the window's top left corner, whatever its anchor. */
private object WindowOrigin : PopupPositionProvider {
    override fun calculatePosition(anchorBounds: IntRect, windowSize: IntSize, layoutDirection: LayoutDirection,
        popupContentSize: IntSize): IntOffset = IntOffset.Zero
}

/**
 * Inspect (TBL-104): only pictures, over the dimmed screen: each the size of a card filling the screen, in portrait one
 * under another, in landscape side by side; [scroll] follows the holding finger.
 */
@Composable
internal fun InspectContent(tiles: List<InspectTile>, scroll: ScrollState) {
    BoxWithConstraints(Modifier.fillMaxSize().background(Color.Black.copy(alpha = .45f)).testTag("card-inspection")
        .semantics { paneTitle = "Card inspection" }, contentAlignment = Alignment.Center) {
        val landscape = maxWidth > maxHeight
        val room = DpSize(maxWidth - 16.dp, maxHeight - 16.dp)
        val content = Modifier.semantics { contentDescription = "Card inspection content" }
        if (landscape) Row(content.widthIn(max = room.width).horizontalScroll(scroll),
            horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            tiles.forEach { tile -> key(tile.key) { InspectTileView(tile, room) } }
        } else Column(content.heightIn(max = room.height).verticalScroll(scroll),
            verticalArrangement = Arrangement.spacedBy(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            tiles.forEach { tile -> key(tile.key) { InspectTileView(tile, room) } }
        }
    }
}

/** A tile's size within [room]: an upright card as large as fits; a card printed sideways turned and no taller than
 * an upright inspected card (TBL-108). */
internal fun inspectTileSize(tile: InspectTile, room: DpSize): DpSize =
    if (tile is InspectTile.Face && tile.degrees % 180 != 0) inspectedArtworkSize(true, room.width, room.height)
    else minOf(room.width, room.height / 1.4f).let { DpSize(it, it * 1.4f) }

@Composable
private fun InspectTileView(tile: InspectTile, room: DpSize) {
    val size = inspectTileSize(tile, room)
    when (tile) {
        is InspectTile.Face -> InspectFaceView(tile, size)
        is InspectTile.Covered -> Surface(Modifier.size(size).semantics { contentDescription = tile.text },
            shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceVariant) {
            Box(Modifier.padding(16.dp), contentAlignment = Alignment.Center) {
                Text(tile.text, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
            }
        }
        is InspectTile.Ability -> AbilityTile(tile, size)
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

/** STK-046: an ability on the Stack as a card-shaped tile: its kind, the source's name, the ability's text and its
 * chosen modes and targets, dark with the controller's outline as its ability token. */
@Composable
private fun AbilityTile(tile: InspectTile.Ability, size: DpSize) {
    val shape = RoundedCornerShape(12.dp)
    Box(Modifier.size(size).clip(shape).background(AbilityTokenColor)
        .border(2.dp, if (tile.own) OwnTableColor else OpponentTableColor, shape)
        .semantics { contentDescription = "${tile.ability.kind} of ${tile.ability.source}" }) {
        CompositionLocalProvider(LocalContentColor provides Color.White) {
            Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AbilityMarker(tile.ability, 28.dp)
                    Text(tile.ability.kind, style = MaterialTheme.typography.labelLarge)
                }
                Text(tile.ability.source, style = MaterialTheme.typography.titleLarge)
                if (tile.ability.text.isNotEmpty()) CardText(tile.ability.text, style = MaterialTheme.typography.bodyLarge)
                tile.details.forEach { CardText(it, style = MaterialTheme.typography.bodyMedium) }
            }
        }
    }
}

/** The fallback tile of a card without card data: its [name] on the placeholder card. */
internal fun placeholderFace(key: String, name: String): InspectTile.Face = InspectTile.Face(key, null, name)

/** Hold-to-inspect of a card outside a game ([card] null: no card data, [fallback] names it). */
internal fun inspectTiles(card: InspectedCard?, fallback: String): List<InspectTile> =
    card?.let { inspectFaces(it) }?.takeIf { it.isNotEmpty() } ?: listOf(placeholderFace("card", fallback))

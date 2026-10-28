package org.asyncttwiz.android

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import org.asyncttwiz.protocol.SubmittedDefinition
import org.asyncttwiz.protocol.DefinitionFace
import org.asyncttwiz.protocol.PlayContainer
import org.asyncttwiz.protocol.PlayState
import org.asyncttwiz.protocol.PlayZone
import org.asyncttwiz.storage.ArtworkReference
import org.asyncttwiz.protocol.DeckJson
import kotlinx.serialization.json.*

internal fun ArtworkReference.forTable() = copy(size = if (customUrl != null) "normal" else "art_crop")

/** Server definitions support more faces than the artwork provider. Keep the ordinary fallback. */
internal fun DefinitionFace.tableArtwork(index: Int, digest: String): ArtworkReference? =
    if (scryfallId == null && imageUri == null) null else artwork(index, digest)

/** MTGJSON side letters describe provider rows, not necessarily physical image sides. Retain
 * the exact provider face name as well, notably for meld and separately printed token sides. */
internal fun DefinitionFace.artwork(index: Int, digest: String, size: String = "normal"): ArtworkReference? {
    if (index !in 0..15) return null
    val source = sourceData?.let { runCatching { DeckJson.format.parseToJsonElement(it).jsonObject }.getOrNull() }
    val side = (source?.get("side") as? JsonPrimitive)?.contentOrNull
    val providerIndex = if (side.isNullOrEmpty()) index else side.singleOrNull()?.let { it - 'a' }
    val providerName = (source?.get("faceName") as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }
        ?: (source?.get("name") as? JsonPrimitive)?.contentOrNull
    return providerIndex?.takeIf { it in 0..15 }?.let {
        ArtworkReference(scryfallId, it, digest, size, imageUri, providerName)
    }
}

internal enum class TableArtworkMode { TYPE_ICON, ART_CROP, CUSTOM_CARD }

/** A failed or pending custom image keeps the ordinary name/icon/corner fallback. */
internal fun tableArtworkMode(reference: ArtworkReference?, imageAvailable: Boolean): TableArtworkMode = when {
    reference == null || !imageAvailable -> TableArtworkMode.TYPE_ICON
    reference.customUrl != null -> TableArtworkMode.CUSTOM_CARD
    else -> TableArtworkMode.ART_CROP
}

/** Exactly what the table (art crop, or a custom full card) and then the inspector (large) request for
 * every face, in definition order: all table images first, since they are what a player sees first. */
internal fun gameArtworkReferences(definitions: List<SubmittedDefinition>): List<ArtworkReference> {
    val faces = definitions.flatMap { submitted ->
        submitted.definition.faces.mapIndexedNotNull { index, face -> face.tableArtwork(index, submitted.digest) }
    }
    return (faces.map { it.forTable() } + faces.map { it.copy(size = "large") }).distinct()
}

/** The viewer's hand, then their battlefield, then the shared battlefield/Stack, then everything else. */
internal fun gameArtworkReferences(play: PlayState, viewer: String): List<ArtworkReference> {
    val byDigest = play.definitions.associateBy { it.digest }
    val cards = play.board.cards.associateBy { it.id }
    fun rank(container: PlayContainer) = when {
        container.accountId == viewer && container.zone == PlayZone.HAND -> 0
        container.accountId == viewer && container.zone == PlayZone.BATTLEFIELD -> 1
        container.zone == PlayZone.BATTLEFIELD || container.zone == PlayZone.STACK -> 2
        else -> 3
    }
    val ordered = play.board.containers.sortedBy(::rank).flatMap { it.cards }.mapNotNull { cards[it]?.definition } +
        play.definitions.map { it.digest }
    return gameArtworkReferences(ordered.distinct().mapNotNull(byDigest::get))
}

/** Warm the retained game definitions in the background queue while this game is open. */
@Composable
internal fun PrefetchGameArtwork(play: PlayState, viewer: String) {
    val context = LocalContext.current
    // Ordered by the board when the game opens (or its definitions change), not on every move.
    val references = remember(play.gameId, viewer, play.definitions) { gameArtworkReferences(play, viewer) }
    DisposableEffect(play.gameId, references) {
        val tag = "game:${play.gameId}"
        PublicArtwork.prefetch(context, tag, references)
        onDispose { PublicArtwork.cancelPrefetch(tag) }
    }
}

/** Saved deck cards in the sizes the table and the inspector use, downloaded ahead of any game. */
internal fun prefetchDeckArtwork(context: android.content.Context, deckId: String, definitions: List<SubmittedDefinition>) {
    PublicArtwork.prefetch(context, "deck:$deckId", gameArtworkReferences(definitions))
}

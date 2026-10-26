package org.asyncttwiz.android

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import org.asyncttwiz.protocol.SubmittedDefinition
import org.asyncttwiz.protocol.DefinitionFace
import org.asyncttwiz.storage.ArtworkReference

internal fun ArtworkReference.forTable() = copy(size = if (customUrl != null) "normal" else "art_crop")

/** Server definitions support more faces than the artwork provider. Keep the ordinary fallback. */
internal fun DefinitionFace.tableArtwork(index: Int, digest: String): ArtworkReference? =
    if (index !in 0..15 || scryfallId == null && imageUri == null) null
    else ArtworkReference(scryfallId, index, digest, customUrl = imageUri)

internal enum class TableArtworkMode { TYPE_ICON, ART_CROP, CUSTOM_CARD }

/** A failed or pending custom image keeps the ordinary name/icon/corner fallback. */
internal fun tableArtworkMode(reference: ArtworkReference?, imageAvailable: Boolean): TableArtworkMode = when {
    reference == null || !imageAvailable -> TableArtworkMode.TYPE_ICON
    reference.customUrl != null -> TableArtworkMode.CUSTOM_CARD
    else -> TableArtworkMode.ART_CROP
}

internal fun gameArtworkReferences(definitions: List<SubmittedDefinition>): List<ArtworkReference> = definitions.flatMap { submitted ->
    submitted.definition.faces.flatMapIndexed { index, face ->
        face.tableArtwork(index, submitted.digest)?.let { listOf(it, it.forTable()) }.orEmpty()
    }
}.distinct()

/** Warm only retained game definitions, through the same bounded public repository as visible art. */
@Composable
internal fun PrefetchGameArtwork(definitions: List<SubmittedDefinition>) {
    val context = LocalContext.current
    val references = remember(definitions) { gameArtworkReferences(definitions) }
    LaunchedEffect(references) {
        if (references.isEmpty()) return@LaunchedEffect
        val repository = PublicArtwork.get(context).repository
        for (reference in references) runInterruptible(Dispatchers.IO) { repository.load(reference) }
    }
}

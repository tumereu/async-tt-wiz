package org.asyncttwiz.android

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntSize
import coil3.request.ImageRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import org.asyncttwiz.protocol.CardDefinition
import org.asyncttwiz.protocol.DraftPrivatePack
import org.asyncttwiz.storage.ArtworkPriority
import org.asyncttwiz.storage.ArtworkReference

internal const val PodArtPrefetchParallelism = 4

/** The tile image of a draft card: the retained printing's first face at normal size. */
internal fun podTileArtwork(digest: String, definition: CardDefinition?): ArtworkReference? =
    definition?.takeIf { it.faces.isNotEmpty() }?.let { InspectedCard.retained(digest, it) }
        ?.artwork?.firstOrNull()?.takeIf { it.printing != null || it.customUrl != null }?.copy(size = "normal")

/** Every card of the given packs, in pack order, without duplicates. */
internal fun podPrefetchArtwork(packs: List<DraftPrivatePack>, definitions: Map<String, CardDefinition>): List<ArtworkReference> =
    packs.flatMap { it.cards }.mapNotNull { podTileArtwork(it.definition, definitions[it.definition]) }.distinct()

/** Bounded parallel prefetch. Each load keeps the artwork service's own retry loop until it
 * succeeds; a reference is cancelled only when it leaves the wanted set, so a pack that moves
 * from "next" to "shown" keeps its downloads running. Call from one thread. */
internal class ArtworkPrefetcher(private val scope: CoroutineScope, parallelism: Int = PodArtPrefetchParallelism,
    private val load: suspend (ArtworkReference) -> Unit) {
    private val permits = Semaphore(parallelism)
    private val jobs = LinkedHashMap<ArtworkReference, Job>()

    fun update(references: List<ArtworkReference>) {
        val wanted = references.toSet()
        jobs.keys.filter { it !in wanted }.forEach { jobs.remove(it)?.cancel() }
        references.forEach { reference ->
            if (reference !in jobs) jobs[reference] = scope.launch { permits.withPermit { load(reference) } }
        }
    }

    fun cancel() { jobs.values.forEach { it.cancel() }; jobs.clear() }
}

/** Starts tile-sized downloads for whole packs, not only the tiles the lazy grid has composed.
 * The decode size matches the tiles, so a composed tile reuses the memory-cached image. */
@Composable
internal fun PodArtPrefetch(packs: List<DraftPrivatePack>, definitions: Map<String, CardDefinition>, tileSize: IntSize) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val references = remember(packs, definitions) { podPrefetchArtwork(packs, definitions) }
    val width = tileSize.width.coerceIn(0, 4096)
    val height = tileSize.height.coerceIn(0, 4096)
    if (width == 0 || height == 0) return
    val prefetcher = remember(context, width, height) {
        ArtworkPrefetcher(scope) { reference ->
            PublicArtwork.get(context).loader.execute(ImageRequest.Builder(context)
                .data(CardArtworkRequest(reference, width, height, ArtworkPriority.PREFETCH)).size(width, height).build())
        }
    }
    DisposableEffect(prefetcher) { onDispose { prefetcher.cancel() } }
    LaunchedEffect(prefetcher, references) { prefetcher.update(references) }
}

package org.asyncttwiz.android

import androidx.compose.foundation.Image
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.Alignment
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.compose.LocalPlatformContext
import coil3.compose.asPainter
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.asyncttwiz.storage.*

/** Explicit decode target participates in the key: a small thumbnail cannot substitute for inspection.
 * Priority only orders downloads; a prefetched decode is the same memory-cache entry. */
data class CardArtworkRequest(val reference: ArtworkReference, val width: Int, val height: Int,
    val priority: ArtworkPriority = ArtworkPriority.VISIBLE) {
    init { require(width in 1..4096 && height in 1..4096) }
    val key get() = "${reference.key}|${width}x$height"
}

/** The process's artwork service as the UI uses it (Android: [CardArtworkService]): pictures through [loader], downloads
 * ahead through [prefetch]. */
abstract class ArtworkService : AutoCloseable {
    abstract val loader: ImageLoader
    /** Reference keys whose latest attempt failed; their own loads keep retrying in the background. */
    abstract val unavailable: StateFlow<Set<String>>
    /** Downloads (no decode) into the disk cache at prefetch priority, one attempt per reference. */
    internal abstract val prefetch: ArtworkPrefetchQueue
    /** Waiting artwork retries run now, over fresh connections. */
    abstract fun wake(reason: String)
}

/** [create]: a new service with its files where the context keeps them (the platform's, [AppServices.artworkService]). */
internal class ArtworkServiceProvider(
    private val create: (PlatformContext) -> ArtworkService = { AppServices.current.artworkService(it) }) {
    @Volatile private var service: ArtworkService? = null
    fun current(): ArtworkService? = service
    // Construction scans, repairs and trims the disk cache. Publish only the fully ready service;
    // even a caller waiting for the first request must never acquire this lock on the UI thread.
    suspend fun get(context: PlatformContext): ArtworkService = service ?: withContext(Dispatchers.IO) {
        synchronized(this@ArtworkServiceProvider) {
            service ?: create(context).also { service = it }
        }
    }
}

object PublicArtwork {
    private val provider = ArtworkServiceProvider()
    private val starting = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val wanted = HashMap<String, List<ArtworkReference>>()
    suspend fun get(context: PlatformContext): ArtworkService = provider.get(context)
    internal fun current(): ArtworkService? = provider.current()

    /** Non-blocking: replaces the batch [tag] in the process-wide background queue. */
    internal fun prefetch(context: PlatformContext, tag: String, references: List<ArtworkReference>) {
        val ready = synchronized(this) {
            current()?.also { wanted.remove(tag); it.prefetch.submit(tag, references) }
                ?: run { wanted[tag] = references; null }
        }
        if (ready == null) starting.launch {
            val service = get(context)
            synchronized(this@PublicArtwork) { wanted.remove(tag)?.let { service.prefetch.submit(tag, it) } }
        }
    }

    /** Queued work of [tag] is dropped; at most the bounded in-flight attempts finish. */
    internal fun cancelPrefetch(tag: String) = synchronized(this) {
        wanted.remove(tag)
        current()?.prefetch?.cancel(tag)
    }
}

/** Same bounded, pannable viewport for gameplay inspection and draft face stacks. */
@Composable
internal fun ArtworkViewport(modifier: Modifier = Modifier, scroll: ScrollState = rememberScrollState(),
    content: @Composable () -> Unit) {
    // Separate nodes keep each axis's accessibility ScrollBy action intact. Combining both
    // modifiers on one node replaces that action and can make bring-into-view loop forever.
    Box(modifier.horizontalScroll(rememberScrollState()), contentAlignment = Alignment.TopCenter) {
        Box(Modifier.verticalScroll(scroll), contentAlignment = Alignment.TopCenter) { content() }
    }
}

/** Reusable ordinary native presentation, not a catalog browser or a new inspector.
 * Text is visible while missing/loading/invalid; rebind/disposal cancels the Coil request and
 * cannot display a late previous face. No HTML, name guessing, retries per frame or saved secrets. */
@Composable
fun CardArtwork(reference: ArtworkReference?, fallback: String, width: Int, height: Int,
    modifier: Modifier = Modifier, service: ArtworkService? = null, crop: Boolean = false,
    placeholder: (@Composable () -> Unit)? = null, previewReference: ArtworkReference? = null, degrees: Int = 0) {
    val context = LocalPlatformContext.current
    // A quarter-turned image is printed upright: decode it at the box's swapped size.
    val quarter = degrees % 180 != 0
    val decodeWidth = if (quarter) height else width
    val decodeHeight = if (quarter) width else height
    val request = reference?.takeIf { it.printing != null || it.customUrl != null }?.let { CardArtworkRequest(it, decodeWidth, decodeHeight) }
    val previewRequest = previewReference?.takeIf { it.printing != null || it.customUrl != null }?.let { CardArtworkRequest(it, decodeWidth, decodeHeight) }
    val availableService = service ?: PublicArtwork.current()
    // Inspection can reuse the displayed thumbnail on its very first frame, even
    // though the thumbnail's decode target differs from the full-size request.
    val cachedPreview = remember(previewReference, availableService) {
        val cache = availableService?.loader?.memoryCache
        val key = previewReference?.let { ref -> cache?.keys?.firstOrNull { it.key.startsWith("${ref.key}|") } }
        key?.let { cache?.get(it)?.image }
    }
    val preview by produceState<Pair<CardArtworkRequest, coil3.Image?>?>(null, previewRequest, service) {
        value = null
        if (previewRequest != null && cachedPreview == null) {
            val artwork = service ?: PublicArtwork.get(context)
            val result = artwork.loader.execute(ImageRequest.Builder(context).data(previewRequest).size(decodeWidth, decodeHeight).build())
            currentCoroutineContext().ensureActive()
            value = previewRequest to (result as? SuccessResult)?.image
        }
    }
    val result by produceState<Pair<CardArtworkRequest, coil3.Image?>?>(null, request, service) {
        value = null
        if (request != null) {
            val artwork = service ?: PublicArtwork.get(context)
            val result = artwork.loader.execute(ImageRequest.Builder(context).data(request).size(decodeWidth, decodeHeight).build())
            currentCoroutineContext().ensureActive()
            value = request to (result as? SuccessResult)?.image
        }
    }
    Box(modifier, contentAlignment = Alignment.Center) {
        val loaded = result?.takeIf { it.first == request }?.second ?: cachedPreview ?:
            preview?.takeIf { it.first == previewRequest }?.second
        if (loaded == null) { if (placeholder != null) placeholder() else Text(fallback) }
        else Image(loaded.asPainter(context), contentDescription = fallback, modifier = Modifier.fillMaxSize().turned(degrees),
            contentScale = if (crop) ContentScale.Crop else ContentScale.Fit)
    }
}

/** Turns content clockwise by a multiple of 90°; a quarter turn lays it out in the swapped box. */
internal fun Modifier.turned(degrees: Int): Modifier = when {
    degrees % 360 == 0 -> this
    degrees % 180 == 0 -> graphicsLayer { rotationZ = degrees.toFloat() }
    else -> layout { measurable, constraints ->
        val placeable = measurable.measure(Constraints(constraints.minHeight, constraints.maxHeight,
            constraints.minWidth, constraints.maxWidth))
        layout(placeable.height, placeable.width) {
            placeable.placeWithLayer((placeable.height - placeable.width) / 2, (placeable.width - placeable.height) / 2) {
                rotationZ = degrees.toFloat()
            }
        }
    }
}

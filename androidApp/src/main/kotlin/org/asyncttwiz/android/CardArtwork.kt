package org.asyncttwiz.android

import android.content.ComponentCallbacks2
import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
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
import androidx.compose.ui.platform.LocalContext
import coil3.ImageLoader
import coil3.asImage
import coil3.compose.asPainter
import coil3.decode.DataSource
import coil3.fetch.Fetcher
import coil3.fetch.ImageFetchResult
import coil3.key.Keyer
import coil3.memory.MemoryCache
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import org.asyncttwiz.storage.*
import java.io.File

/** Explicit decode target participates in the key: a small thumbnail cannot substitute for inspection.
 * Priority only orders downloads; a prefetched decode is the same memory-cache entry. */
data class CardArtworkRequest(val reference: ArtworkReference, val width: Int, val height: Int,
    val priority: ArtworkPriority = ArtworkPriority.VISIBLE) {
    init { require(width in 1..4096 && height in 1..4096) }
    val key get() = "${reference.key}|${width}x$height"
}

object ArtworkDecoder {
    fun bounds(bytes: ByteArray): Pair<Int, Int>? {
        if (bytes.size > HttpArtworkRepository.MAX_IMAGE) return null
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
        val width = options.outWidth; val height = options.outHeight
        return if (width in 1..4096 && height in 1..4096 && width.toLong() * height <= 16_000_000)
            width to height else null
    }
    fun decode(bytes: ByteArray, width: Int, height: Int, memoryBudget: Long): Bitmap? {
        val (sourceWidth, sourceHeight) = bounds(bytes) ?: return null
        var sample = 1
        // Decode at scene resolution, and never allocate a bitmap larger than the cache budget.
        while ((sourceWidth / (sample * 2) >= width && sourceHeight / (sample * 2) >= height) ||
            ((sourceWidth.toLong() / sample) * (sourceHeight / sample) * 4 > memoryBudget)) sample *= 2
        val options = BitmapFactory.Options().apply { inSampleSize = sample; inPreferredConfig = Bitmap.Config.ARGB_8888 }
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)?.takeIf { it.allocationByteCount <= memoryBudget }
    }
}

/** One public artwork service per process; no account token or Activity is retained. */
class CardArtworkService(context: Context, directory: File = File(context.cacheDir, "public-artwork"),
    policy: ArtworkUrlPolicy = ArtworkUrlPolicy(BuildConfig.ART_TEST_ORIGIN.takeIf { BuildConfig.DEBUG && it.isNotEmpty() }),
    apiBase: String = BuildConfig.ART_BASE) : AutoCloseable, ComponentCallbacks2 {
    val memoryBudget = minOf(64L * 1024 * 1024, Runtime.getRuntime().maxMemory() / 8)
    val disk = ArtworkDiskCache(directory)
    private val application = context.applicationContext
    // Only provider API metadata is spaced (also when a debug mirror serves it); image bytes are
    // bounded by the repository's download gate, separately from the decode permits below.
    private val transport = HttpArtworkTransport(policy,
        "AsyncTabletopWizards/${BuildConfig.VERSION_NAME} (artwork; contact: ${BuildConfig.ART_CONTACT.ifEmpty { "not configured" }})",
        throttled = { scryfallApiRequest(it) || it.toString().startsWith(apiBase) })
    private val diagnostics = ArtworkDiagnostics()
    val repository = HttpArtworkRepository(disk, transport, policy, apiBase,
        imageCheck = { bytes, _ -> ArtworkDecoder.bounds(bytes) != null }, gate = ArtworkGate(DOWNLOADS), log = diagnostics)
    private val failed = MutableStateFlow<Set<String>>(emptySet())
    /** Reference keys whose latest attempt failed; their own loads keep retrying in the background. */
    val unavailable: StateFlow<Set<String>> = failed
    private fun attempted(reference: ArtworkReference, loaded: Boolean) = failed.update { keys ->
        when {
            loaded -> if (reference.key in keys) keys - reference.key else keys
            reference.key in keys -> keys
            keys.size >= 1024 -> setOf(reference.key)
            else -> keys + reference.key
        }
    }
    private val background = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    /** Downloads (no decode) into the disk cache at prefetch priority, one attempt per reference. */
    internal val prefetch = ArtworkPrefetchQueue(background) { reference ->
        val bytes = runInterruptible { repository.load(reference, ArtworkPriority.PREFETCH) }
        currentCoroutineContext().ensureActive()
        attempted(reference, bytes != null)
        bytes != null
    }
    private val connectivity = application.getSystemService(ConnectivityManager::class.java)
    private val connection = MutableStateFlow(0L)
    private var online = false
    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
            val available = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
            if (available && !online) wake("network back")
            online = available
        }
        override fun onLost(network: Network) { online = false }
        // A background app's network can be cut off while the device itself stays online.
        override fun onBlockedStatusChanged(network: Network, blocked: Boolean) { if (!blocked) wake("network allowed again") }
    }

    /** Waiting artwork retries run now, over fresh connections: after the app's network was cut
     * off (app in background, network change) pooled connections can be dead. */
    fun wake(reason: String) {
        transport.evictConnections()
        repository.connectivityRestored()
        connection.value += 1
        diagnostics.woke(reason)
    }
    private val decodes = Semaphore(2)
    val loader = ImageLoader.Builder(application)
        .memoryCache { MemoryCache.Builder().maxSizeBytes(memoryBudget).weakReferencesEnabled(false).build() }
        .diskCache(null) // Our encoded cache owns retry windows, conditional validators and versions.
        .components {
            add(Keyer<CardArtworkRequest> { data, _ -> data.key })
            add(Fetcher.Factory<CardArtworkRequest> { data, _, _ -> Fetcher {
                val retry = ArtworkRetrySchedule(connectivityGeneration = { connection.value })
                var image: Bitmap? = null
                while (image == null) {
                    currentCoroutineContext().ensureActive()
                    // Network waits never hold a decode permit.
                    val bytes = withContext(Dispatchers.IO) { runInterruptible { repository.load(data.reference, data.priority) } }
                    currentCoroutineContext().ensureActive()
                    image = bytes?.let { decodes.withPermit { withContext(Dispatchers.IO) {
                        ArtworkDecoder.decode(it, data.width, data.height, memoryBudget).also { bitmap ->
                            if (bitmap == null) repository.rejectDecoded(data.reference)
                            else repository.decoded(data.reference)
                        }
                    } } }
                    currentCoroutineContext().ensureActive()
                    attempted(data.reference, image != null)
                    if (image == null) {
                        retry.failed()
                        val generation = connection.value
                        val wait = retry.remainingMillis()
                        if (wait > 0) withTimeoutOrNull(wait) { connection.first { it != generation } }
                    }
                }
                currentCoroutineContext().ensureActive()
                ImageFetchResult(image.asImage(), true, DataSource.DISK)
            } })
        }.build()
    init {
        application.registerComponentCallbacks(this)
        connectivity.registerDefaultNetworkCallback(networkCallback)
        background.launch { while (true) { delay(ArtworkDiagnostics.WINDOW); diagnostics.roll() } }
    }
    override fun onConfigurationChanged(newConfig: Configuration) = Unit
    override fun onLowMemory() { loader.memoryCache?.clear() }
    override fun onTrimMemory(level: Int) {
        if (level >= ComponentCallbacks2.TRIM_MEMORY_BACKGROUND) loader.memoryCache?.clear()
        else if (level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW) loader.memoryCache?.trimToSize(memoryBudget / 2)
    }
    override fun close() {
        background.cancel()
        connectivity.unregisterNetworkCallback(networkCallback)
        application.unregisterComponentCallbacks(this)
        loader.shutdown()
        transport.close()
    }
    companion object {
        /** Concurrent image/metadata downloads; Scryfall's CDN is unthrottled, its API is spaced. */
        const val DOWNLOADS = 6
    }
}

internal class ArtworkServiceProvider {
    @Volatile private var service: CardArtworkService? = null
    fun current(): CardArtworkService? = service
    // Construction scans, repairs and trims the disk cache. Publish only the fully ready service;
    // even a caller waiting for the first request must never acquire this lock on the UI thread.
    suspend fun get(context: Context): CardArtworkService = service ?: withContext(Dispatchers.IO) {
        synchronized(this@ArtworkServiceProvider) {
            service ?: CardArtworkService(context.applicationContext).also { service = it }
        }
    }
}

object PublicArtwork {
    private val provider = ArtworkServiceProvider()
    private val starting = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val wanted = HashMap<String, List<ArtworkReference>>()
    suspend fun get(context: Context): CardArtworkService = provider.get(context)
    internal fun current(): CardArtworkService? = provider.current()

    /** Non-blocking: replaces the batch [tag] in the process-wide background queue. */
    internal fun prefetch(context: Context, tag: String, references: List<ArtworkReference>) {
        val ready = synchronized(this) {
            current()?.also { wanted.remove(tag); it.prefetch.submit(tag, references) }
                ?: run { wanted[tag] = references; null }
        }
        if (ready == null) starting.launch {
            val service = get(context.applicationContext)
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
    modifier: Modifier = Modifier, service: CardArtworkService? = null, crop: Boolean = false,
    placeholder: (@Composable () -> Unit)? = null, previewReference: ArtworkReference? = null, degrees: Int = 0) {
    val context = LocalContext.current
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

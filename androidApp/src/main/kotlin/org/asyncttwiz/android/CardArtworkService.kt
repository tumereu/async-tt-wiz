package org.asyncttwiz.android

import android.content.ComponentCallbacks2
import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import coil3.ImageLoader
import coil3.asImage
import coil3.decode.DataSource
import coil3.fetch.Fetcher
import coil3.fetch.ImageFetchResult
import coil3.key.Keyer
import coil3.memory.MemoryCache
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

/** One public artwork service per process; no account token or Activity is retained.
 * Open only so Robolectric tests can serve placeholder art through [loader] (test PlaceholderArtwork). */
open class CardArtworkService(context: Context, directory: File = File(context.cacheDir, "public-artwork"),
    policy: ArtworkUrlPolicy = ArtworkUrlPolicy(BuildConfig.ART_TEST_ORIGIN.takeIf { BuildConfig.DEBUG && it.isNotEmpty() }),
    apiBase: String = BuildConfig.ART_BASE) : ArtworkService(), ComponentCallbacks2 {
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
    override val unavailable: StateFlow<Set<String>> = failed
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
    internal override val prefetch = ArtworkPrefetchQueue(background) { reference ->
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
     * off (app in background, network change) pooled connections can be dead. Callers include
     * onResume, so the work runs off the main thread (closing a connection writes to the network). */
    override fun wake(reason: String) {
        background.launch {
            transport.evictConnections()
            repository.connectivityRestored()
            connection.value += 1
            diagnostics.woke(reason)
        }
    }
    private val decodes = Semaphore(2)
    override val loader: ImageLoader = ImageLoader.Builder(application)
        .memoryCache { MemoryCache.Builder().maxSizeBytes(memoryBudget).weakReferencesEnabled(false).build() }
        .diskCache(null) // Our encoded cache owns the kept pictures and the retry windows.
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

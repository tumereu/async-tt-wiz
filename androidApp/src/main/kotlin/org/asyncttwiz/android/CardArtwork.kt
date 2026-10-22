package org.asyncttwiz.android

import android.content.ComponentCallbacks2
import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import org.asyncttwiz.storage.*
import java.io.File

/** Explicit decode target participates in the key: a small thumbnail cannot substitute for inspection. */
data class CardArtworkRequest(val reference: ArtworkReference, val width: Int, val height: Int) {
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
    private val transport = HttpArtworkTransport(policy,
        "AsyncTabletopWizards/${BuildConfig.VERSION_NAME} (artwork; contact: ${BuildConfig.ART_CONTACT.ifEmpty { "not configured" }})")
    val repository = HttpArtworkRepository(disk, transport, policy, apiBase,
        imageCheck = { bytes, _ -> ArtworkDecoder.bounds(bytes) != null })
    private val decodes = Semaphore(2)
    val loader = ImageLoader.Builder(application)
        .memoryCache { MemoryCache.Builder().maxSizeBytes(memoryBudget).weakReferencesEnabled(false).build() }
        .diskCache(null) // Our encoded cache also holds negative results, conditional validators and versions.
        .components {
            add(Keyer<CardArtworkRequest> { data, _ -> data.key })
            add(Fetcher.Factory<CardArtworkRequest> { data, _, _ -> Fetcher {
                decodes.withPermit { withContext(Dispatchers.IO) {
                    val bytes = runInterruptible { repository.load(data.reference) } ?: error("Artwork unavailable")
                    currentCoroutineContext().ensureActive()
                    val bitmap = ArtworkDecoder.decode(bytes, data.width, data.height, memoryBudget)
                    if (bitmap == null) { repository.rejectDecoded(data.reference); error("Artwork unavailable") }
                    repository.decoded(data.reference)
                    currentCoroutineContext().ensureActive()
                    ImageFetchResult(bitmap.asImage(), true, DataSource.DISK)
                } }
            } })
        }.build()
    init { application.registerComponentCallbacks(this) }
    override fun onConfigurationChanged(newConfig: Configuration) = Unit
    override fun onLowMemory() { loader.memoryCache?.clear() }
    override fun onTrimMemory(level: Int) {
        if (level >= ComponentCallbacks2.TRIM_MEMORY_BACKGROUND) loader.memoryCache?.clear()
        else if (level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW) loader.memoryCache?.trimToSize(memoryBudget / 2)
    }
    override fun close() { application.unregisterComponentCallbacks(this); loader.shutdown(); transport.close() }
}

object PublicArtwork {
    @Volatile private var service: CardArtworkService? = null
    fun get(context: Context): CardArtworkService = service ?: synchronized(this) {
        service ?: CardArtworkService(context.applicationContext).also { service = it }
    }
}

/** Reusable ordinary native presentation, not a catalog browser or a new inspector.
 * Text is visible while missing/loading/invalid; rebind/disposal cancels the Coil request and
 * cannot display a late previous face. No HTML, name guessing, retries per frame or saved secrets. */
@Composable
fun CardArtwork(reference: ArtworkReference?, fallback: String, width: Int, height: Int,
    modifier: Modifier = Modifier, service: CardArtworkService = PublicArtwork.get(LocalContext.current)) {
    val context = LocalContext.current
    val request = reference?.let { CardArtworkRequest(it, width, height) }
    val result by produceState<Pair<CardArtworkRequest, coil3.Image?>?>(null, request, service) {
        value = null
        if (request != null) {
            val result = service.loader.execute(ImageRequest.Builder(context).data(request).size(width, height).build())
            currentCoroutineContext().ensureActive()
            value = request to (result as? SuccessResult)?.image
        }
    }
    Box(modifier) {
        val loaded = result?.takeIf { it.first == request }?.second
        if (loaded == null) Text(fallback)
        else Image(loaded.asPainter(context), contentDescription = fallback, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
    }
}

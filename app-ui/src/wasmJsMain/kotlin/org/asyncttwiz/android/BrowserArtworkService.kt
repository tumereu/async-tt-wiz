@file:OptIn(ExperimentalWasmJsInterop::class)

package org.asyncttwiz.android

import coil3.ImageLoader
import coil3.PlatformContext
import coil3.asImage
import coil3.decode.DataSource
import coil3.fetch.Fetcher
import coil3.fetch.ImageFetchResult
import coil3.key.Keyer
import coil3.memory.MemoryCache
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.await
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import org.asyncttwiz.appui.AppBuild
import org.asyncttwiz.storage.ArtworkAttempt
import org.asyncttwiz.storage.ArtworkLog
import org.asyncttwiz.storage.ArtworkReference
import org.asyncttwiz.storage.ArtworkResponse
import org.asyncttwiz.storage.ArtworkStore
import org.asyncttwiz.storage.ArtworkStored
import org.asyncttwiz.storage.AsyncArtworkTransport
import org.asyncttwiz.storage.BrowserArtworkRepository
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.Canvas
import org.jetbrains.skia.Codec
import org.jetbrains.skia.Data
import org.jetbrains.skia.FilterMipmap
import org.jetbrains.skia.FilterMode
import org.jetbrains.skia.Image
import org.jetbrains.skia.MipmapMode
import org.jetbrains.skia.Rect

/** Card pictures decoded by Skia (the browser's Compose draws with it): bounded, at about the size they are drawn. */
object BrowserArtworkDecoder {
    /** The picture's size, or null when [bytes] aren't a picture of a size the app draws. */
    fun bounds(bytes: ByteArray): Pair<Int, Int>? {
        if (bytes.size > BrowserArtworkRepository.MAX_IMAGE) return null
        val codec = runCatching { Codec.makeFromData(Data.makeFromBytes(bytes)) }.getOrNull() ?: return null
        return try {
            val width = codec.width; val height = codec.height
            if (width in 1..4096 && height in 1..4096 && width.toLong() * height <= 16_000_000) width to height else null
        } finally { codec.close() }
    }

    /** [bytes] decoded at about [width]×[height] (halved while still twice as large), never over [memoryBudget] bytes. */
    fun decode(bytes: ByteArray, width: Int, height: Int, memoryBudget: Long): Bitmap? {
        val (sourceWidth, sourceHeight) = bounds(bytes) ?: return null
        var sample = 1
        while ((sourceWidth / (sample * 2) >= width && sourceHeight / (sample * 2) >= height) ||
            ((sourceWidth.toLong() / sample) * (sourceHeight / sample) * 4 > memoryBudget)) sample *= 2
        val image = runCatching { Image.makeFromEncoded(bytes) }.getOrNull() ?: return null
        return try {
            val targetWidth = (sourceWidth / sample).coerceAtLeast(1)
            val targetHeight = (sourceHeight / sample).coerceAtLeast(1)
            val bitmap = Bitmap()
            if (!bitmap.allocN32Pixels(targetWidth, targetHeight)) { bitmap.close(); return null }
            val canvas = Canvas(bitmap)
            try {
                canvas.drawImageRect(image, Rect.makeWH(sourceWidth.toFloat(), sourceHeight.toFloat()),
                    Rect.makeWH(targetWidth.toFloat(), targetHeight.toFloat()),
                    FilterMipmap(FilterMode.LINEAR, MipmapMode.NONE), null, true)
            } finally { canvas.close() }
            bitmap.setImmutable()
            bitmap
        } finally { image.close() }
    }
}

/**
 * The browser's card pictures (docs/specs/web-client.md §1 item 4), as [CardArtworkService] on Android: fetched by
 * [BrowserArtworkRepository] (kept for good in the Cache API), decoded by Skia, through a Coil [loader] because the
 * shared screens draw pictures with one. No Coil network or decoding module is used: every byte passes through the
 * repository and [BrowserArtworkDecoder].
 */
class BrowserArtworkService(context: PlatformContext = PlatformContext.INSTANCE, apiBase: String = AppBuild.ART_BASE,
    testOrigin: String? = AppBuild.ART_TEST_ORIGIN.takeIf { AppBuild.DEBUG && it.isNotEmpty() }) : ArtworkService() {
    val memoryBudget = 64L * 1024 * 1024
    private val clock = BrowserArtworkRepository.monotonicMillis()
    val repository = BrowserArtworkRepository(CacheApiArtworkStore, BrowserArtworkTransport(clock), apiBase, testOrigin, clock,
        imageCheck = { bytes, _ -> BrowserArtworkDecoder.bounds(bytes) != null }, log = ArtworkAppLog)
    private val failed = MutableStateFlow<Set<String>>(emptySet())
    override val unavailable: StateFlow<Set<String>> = failed
    private fun attempted(reference: ArtworkReference, loaded: Boolean) = failed.update { keys ->
        when {
            loaded -> if (reference.key in keys) keys - reference.key else keys
            reference.key in keys -> keys
            keys.size >= 1024 -> setOf(reference.key)
            else -> keys + reference.key
        }
    }
    private val background = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    override val prefetch = ArtworkPrefetchQueue(background) { reference ->
        val bytes = repository.load(reference)
        attempted(reference, bytes != null)
        bytes != null
    }

    override fun wake(reason: String) {
        AppLog.info("artwork", "Waiting pictures tried again: $reason")
        repository.connectivityRestored()
    }

    override val loader: ImageLoader = ImageLoader.Builder(context)
        .memoryCache { MemoryCache.Builder().maxSizeBytes(memoryBudget).build() }
        .diskCache(null) // The repository's Cache API store keeps the pictures.
        .components {
            add(Keyer<CardArtworkRequest> { data, _ -> data.key })
            add(Fetcher.Factory<CardArtworkRequest> { data, _, _ -> Fetcher {
                val bitmap = repository.loadDecoded(data.reference, { attempted(data.reference, it) }) { bytes ->
                    BrowserArtworkDecoder.decode(bytes, data.width, data.height, memoryBudget)
                }
                ImageFetchResult(bitmap.asImage(), true, DataSource.DISK)
            } })
        }.build()

    init { onOnline { wake("network back") } }

    override fun close() {
        background.cancel()
        loader.shutdown()
    }
}

/** Pictures and card lookups kept in the browser's Cache API. */
internal object CacheApiArtworkStore : ArtworkStore {
    private const val CACHE = "atw-artwork-v1"
    override suspend fun read(key: String): ArtworkStored? = cacheRead(CACHE, key).await<JsAny?>()?.let {
        ArtworkStored(responseType(it), responseBytes(it).toByteArray())
    }
    override suspend fun write(key: String, stored: ArtworkStored) {
        cacheWrite(CACHE, key, stored.type, stored.bytes.toJsBytes()).await<JsAny?>()
    }
    override suspend fun delete(key: String) { cacheDelete(CACHE, key).await<JsAny?>() }
}

/** The browser's fetch, as Android's HttpArtworkTransport: no credentials; a Retry-After (429) on [clock]'s time. */
internal class BrowserArtworkTransport(private val clock: () -> Long) : AsyncArtworkTransport {
    override suspend fun get(url: String, maximum: Long): ArtworkResponse {
        val response = fetchBytes(url, maximum.toDouble(), "application/json, image/png, image/jpeg, image/webp").await<JsAny>()
        val status = responseStatus(response)
        val retry = if (status == 429) responseRetryAfter(response).trim().toLongOrNull()?.takeIf { it >= 0 }
            ?.let { clock() + it * 1000 } ?: (clock() + 60_000) else 0
        return ArtworkResponse(status, if (status == 200) responseBytes(response).toByteArray() else byteArrayOf(),
            responseType(response), retry)
    }
}

/** Failed picture downloads and loads in the app log (successes are not logged: there are thousands). */
private object ArtworkAppLog : ArtworkLog {
    override fun attempt(attempt: ArtworkAttempt) {
        if (attempt.failure != null) AppLog.info("artwork", "${if (attempt.image) "Picture" else "Card data"} ${attempt.url} failed " +
            "(${attempt.failure}, ${attempt.failures} in a row); next try in ${attempt.retryInMillis} ms")
    }
    override fun failed(reference: ArtworkReference, failure: Throwable) =
        AppLog.warn("artwork", "Picture ${reference.key} couldn't be loaded", failure)
}

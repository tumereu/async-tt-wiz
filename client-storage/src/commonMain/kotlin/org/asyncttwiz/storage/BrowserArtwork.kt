package org.asyncttwiz.storage

import kotlin.time.TimeSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeoutOrNull

// Card pictures in the browser (docs/specs/web-client.md §1 item 4): the same lookups and downloads as Android's
// HttpArtworkRepository (Scryfall's card data names the image; what once came is kept for good, LOB-097; failures wait
// before they are tried again), over the browser's fetch and its Cache API (app-ui's wasm side). Single-threaded.

/** A kept picture or card lookup: its content type and bytes. */
class ArtworkStored(val type: String, val bytes: ByteArray)

/** Where the browser keeps pictures and card lookups (the Cache API), by key. */
interface ArtworkStore {
    suspend fun read(key: String): ArtworkStored?
    suspend fun write(key: String, stored: ArtworkStored)
    suspend fun delete(key: String)
}

/** One HTTP GET of at most [maximum] bytes (the browser's fetch); failures throw. */
fun interface AsyncArtworkTransport {
    suspend fun get(url: String, maximum: Long): ArtworkResponse
}

/** [ArtworkStore] in memory: tests, and a browser without the Cache API. */
class MemoryArtworkStore : ArtworkStore {
    val entries = LinkedHashMap<String, ArtworkStored>()
    override suspend fun read(key: String) = entries[key]
    override suspend fun write(key: String, stored: ArtworkStored) { entries[key] = stored }
    override suspend fun delete(key: String) { entries.remove(key) }
}

/**
 * All work is demand-driven; one download per key at a time, shared by its callers. Failures are ordinary missing art.
 * [origin]: in debug builds the one origin pictures may come from (a local mirror over http), else any https address.
 * [transport]'s [ArtworkResponse.retryAt] is a time on [now]'s clock.
 */
class BrowserArtworkRepository(
    private val store: ArtworkStore,
    private val transport: AsyncArtworkTransport,
    private val apiBase: String = "https://api.scryfall.com/cards/",
    private val origin: String? = null,
    private val now: () -> Long = monotonicMillis(),
    private val imageCheck: (ByteArray, String) -> Boolean = { _, _ -> true },
    private val log: ArtworkLog = ArtworkLog.NONE,
    downloads: Int = 6,
    private val apiSpacing: Long = 100,
) {
    private class Failure(val status: Int, val expires: Long, val failures: Int, val generation: Long)

    private val failures = HashMap<String, Failure>()
    private val pending = HashMap<String, CompletableDeferred<ArtworkStored?>>()
    private val gate = Semaphore(downloads)
    private val throttle = Mutex()
    private var nextApiRequest = 0L
    private var coolingUntil = 0L
    private val connection = MutableStateFlow(0L)
    private val loadedUrls = HashMap<String, String>()

    init { require(allowed(apiBase) && apiBase.endsWith('/')) }

    /** The bytes of [reference]'s picture, or null when there are none now (tried again after a pause). */
    suspend fun load(reference: ArtworkReference): ByteArray? = try {
        var result: ByteArray? = null
        val urls = buildList { reference.customUrl?.let(::add); addAll(resolve(reference, emptySet())) }.distinct()
        for (url in urls) {
            if (!allowed(url)) continue
            val entry = fetch("image|${reference.key}|$url", url, MAX_IMAGE, image = true)
            if (entry != null) { loadedUrls[reference.key] = url; result = entry.bytes; break }
            // A missing source can use another kept/provider image immediately.
            if (failures["image|${reference.key}|$url"]?.status != 404) break
        }
        result
    } catch (cancelled: CancellationException) { throw cancelled } catch (failure: Exception) {
        runCatching { log.failed(reference, failure) }
        null
    }

    /**
     * [reference]'s picture decoded with [decode], tried until it is (a failure waits before the next try, connectivity
     * coming back ends the wait); [attempted] hears each try's outcome. Ends only with a picture or cancellation.
     */
    suspend fun <T : Any> loadDecoded(reference: ArtworkReference, attempted: (Boolean) -> Unit = {}, decode: suspend (ByteArray) -> T?): T {
        var failed = 0
        while (true) {
            currentCoroutineContext().ensureActive()
            val image = load(reference)?.let { bytes -> decode(bytes).also { if (it == null) rejectDecoded(reference) } }
            attempted(image != null)
            if (image != null) return image
            failed = (failed + 1).coerceAtMost(6)
            val generation = connection.value
            withTimeoutOrNull(backoff(failed)) { connection.first { it != generation } }
        }
    }

    /** Waiting retries run now (the browser came back online). Kept pictures stay. */
    fun connectivityRestored() { connection.value += 1 }

    /** The bytes [load] gave for [reference] weren't a picture after all: dropped, and tried again only after a pause. */
    suspend fun rejectDecoded(reference: ArtworkReference) {
        val url = loadedUrls.remove(reference.key) ?: return
        val key = "image|${reference.key}|$url"
        val failed = ((failures[key]?.failures ?: 0) + 1).coerceAtMost(6)
        failures[key] = Failure(503, now() + backoff(failed), failed, connection.value)
        runCatching { store.delete(key) }
    }

    private suspend fun resolve(reference: ArtworkReference, visited: Set<String>): List<String> {
        val id = reference.printing ?: return emptyList()
        if (id in visited || visited.size >= 3 || !ScryfallImages.validId(id)) return emptyList()
        val key = "metadata|$apiBase|$id"
        val entry = fetch(key, "$apiBase$id", 1024 * 1024, image = false) ?: return emptyList()
        val lookup = try { ScryfallImages.lookup(reference, entry.bytes.decodeToString(), id) } catch (failure: Exception) {
            // A kept lookup that can't be read is a failure to retry, not to keep.
            runCatching { store.delete(key) }
            failures[key] = Failure(503, now() + backoff(6), 6, connection.value)
            throw failure
        }
        return when (lookup) {
            is ScryfallImages.Lookup.MeldResult -> resolve(reference.copy(printing = lookup.printing, face = 0), visited + id)
            is ScryfallImages.Lookup.Images -> lookup.urls
        }
    }

    /** The kept or downloaded bytes at [url] (kept under [key]), or null while it is failing. */
    private suspend fun fetch(key: String, url: String, maximum: Long, image: Boolean): ArtworkStored? {
        // Card art is permanent: what once came is used as it is, at once, with no request (LOB-097).
        store.read(key)?.let { return it }
        failures[key]?.let { if (it.expires > now() && it.generation == connection.value) return null }
        while (true) {
            val other = pending[key] ?: break
            try { return other.await() } catch (cancelled: CancellationException) {
                // The owner was cancelled (its card scrolled away): this caller downloads instead.
                currentCoroutineContext().ensureActive()
            }
        }
        val mine = CompletableDeferred<ArtworkStored?>()
        pending[key] = mine
        try {
            return download(key, url, maximum, image).also { mine.complete(it) }
        } catch (failure: Throwable) {
            mine.completeExceptionally(failure)
            throw failure
        } finally {
            pending.remove(key)
        }
    }

    private suspend fun download(key: String, url: String, maximum: Long, image: Boolean): ArtworkStored? {
        val old = failures[key]
        val generation = connection.value
        var error: Exception? = null
        var started = now()
        val response = try {
            gate.withPermit {
                spaceApiRequest(url)
                started = now()
                transport.get(url, maximum)
            }
        } catch (cancelled: CancellationException) { throw cancelled } catch (failure: Exception) {
            error = failure
            ArtworkResponse(503)
        }
        if (response.retryAt > 0) coolingUntil = maxOf(coolingUntil, response.retryAt)
        val valid = response.status == 200 && response.bytes.size <= maximum &&
            if (image) ScryfallImages.raster(response.bytes, response.type) && imageCheck(response.bytes, response.type)
            else jsonObjectOrNull(response.bytes.decodeToString()) != null
        val failed = if (valid) 0 else ((old?.failures ?: 0) + 1).coerceAtMost(6)
        val result = if (valid) ArtworkStored(response.type, response.bytes).also {
            failures.remove(key)
            // Not kept (storage full or refused): used now and downloaded again next time
            try { store.write(key, it) } catch (cancelled: CancellationException) { throw cancelled } catch (_: Exception) {}
        } else {
            failures[key] = Failure(if (response.status == 404) 404 else 503,
                maxOf(now() + backoff(failed), response.retryAt), failed, generation)
            null
        }
        val reason = when {
            valid -> null
            error != null -> "${error::class.simpleName}: ${error.message.orEmpty().take(200)}"
            response.status == 200 -> if (image) "not a usable image" else "unusable card data"
            else -> "HTTP ${response.status}"
        }
        runCatching { log.attempt(ArtworkAttempt(image, url, reason, now() - started, response.bytes.size, failed,
            if (reason == null) 0 else ((failures[key]?.expires ?: now()) - now()).coerceAtLeast(0))) }
        return result
    }

    /** Scryfall asks for 50-100 ms between API calls; a provider Retry-After cools down every request. */
    private suspend fun spaceApiRequest(url: String) {
        check(now() >= coolingUntil) { "Artwork provider cooling down" }
        if (!url.startsWith(apiBase) && !url.startsWith("https://api.scryfall.com/")) return
        throttle.withLock {
            val wait = nextApiRequest - now()
            if (wait > 0) delay(wait)
            nextApiRequest = now() + apiSpacing
        }
    }

    private fun allowed(url: String): Boolean {
        if (url.length > 4096 || '#' in url) return false
        val scheme = url.substringBefore("://", "")
        val rest = url.substringAfter("://", "")
        val authority = rest.substringBefore('/').substringBefore('?')
        if (authority.isEmpty() || '@' in authority) return false
        return if (origin != null) "$scheme://$authority" == origin else scheme == "https" &&
            (':' !in authority || authority.endsWith(":443"))
    }

    companion object {
        const val MAX_IMAGE = 10L * 1024 * 1024

        /** How long a failed picture waits before its next try: 1 s doubling to 30 s. */
        fun backoff(failures: Int): Long = minOf(30_000L, 1_000L shl (failures - 1).coerceIn(0, 5))

        /** Milliseconds on a clock that only goes forward, from now. */
        fun monotonicMillis(): () -> Long {
            val start = TimeSource.Monotonic.markNow()
            return { start.elapsedNow().inWholeMilliseconds }
        }
    }
}

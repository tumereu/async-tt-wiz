package org.asyncttwiz.storage

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.io.*
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/** Retained definition consumers can construct this without consulting a newer local catalog.
 * Face is an explicit provider face index, not a name search or first-face guess. */
data class ArtworkReference(val printing: String?, val face: Int, val version: String,
    val size: String = "normal", val customUrl: String? = null, val faceName: String? = null) {
    init { require(face in 0..15 && version.length <= 256 && size in listOf("small", "normal", "large", "png", "art_crop")) }
    val key: String get() = "$printing|$face|$size|$version|$customUrl|$faceName"
    companion object {
        fun from(record: CatalogPrinting, version: String, size: String = "normal"): ArtworkReference? {
            val face = if (record.side.isEmpty()) 0 else record.side.singleOrNull()?.let { it - 'a' } ?: return null
            if (face !in 0..15) return null
            val name = runCatching { JsonParser.parseString(record.json).asJsonObject.get("faceName")?.asString }
                .getOrNull()?.takeIf { it.isNotBlank() } ?: record.name
            return ArtworkReference(record.scryfallId, face, version, size, faceName = name)
        }
    }
}

internal data class ArtworkEntry(val status: Int, val expires: Long, val failures: Int,
    val type: String, val etag: String, val modified: String, val bytes: ByteArray)

/** Disposable public-only disk LRU. A single atomic entry includes bytes and validators.
 * Essential catalog/private databases cannot be reached by eviction. */
class ArtworkDiskCache(private val directory: File, val budget: Long = 256L * 1024 * 1024,
    private val now: () -> Long = System::currentTimeMillis) {
    init {
        require(budget in 1..256L * 1024 * 1024); check(directory.isDirectory || directory.mkdirs())
        // One application service owns this disposable directory. A process-killed write is not
        // an entry and must not accumulate outside the LRU budget across subsequent launches.
        directory.listFiles()?.filter { Regex("[a-f0-9]{64}\\.art\\.part").matches(it.name) }
            ?.forEach { check(it.delete()) }
        trim()
    }
    @Synchronized internal fun read(key: String): ArtworkEntry? = runCatching {
        val file = File(directory, "${hash(key)}.art")
        if (!file.isFile || file.length() > MAX_ENTRY) return null
        DataInputStream(file.inputStream().buffered()).use { input ->
            check(input.readInt() == 1)
            val status = input.readInt(); val expiry = input.readLong(); val failures = input.readInt()
            val type = input.readUTF(); val etag = input.readUTF(); val modified = input.readUTF()
            val count = input.readInt(); check(count in 0..HttpArtworkRepository.MAX_IMAGE.toInt())
            val bytes = ByteArray(count); input.readFully(bytes); check(input.read() == -1)
            file.setLastModified(now())
            ArtworkEntry(status, expiry, failures, type, etag, modified, bytes)
        }
    }.getOrNull()
    @Synchronized internal fun write(key: String, entry: ArtworkEntry) {
        val file = File(directory, "${hash(key)}.art")
        val part = File(directory, "${file.name}.part")
        try {
            FileOutputStream(part).use { output ->
                DataOutputStream(output.buffered()).apply {
                    writeInt(1); writeInt(entry.status); writeLong(entry.expires); writeInt(entry.failures)
                    writeUTF(entry.type.take(1024)); writeUTF(entry.etag.take(1024)); writeUTF(entry.modified.take(1024))
                    writeInt(entry.bytes.size); write(entry.bytes); flush()
                }
                output.fd.sync()
            }
            check(part.renameTo(file)); file.setLastModified(now())
            trim()
        } finally { part.delete() }
    }
    @Synchronized fun trim() {
        val files = directory.listFiles()?.filter { Regex("[a-f0-9]{64}\\.art").matches(it.name) }.orEmpty()
        var total = files.sumOf { it.length() }
        for (file in files.sortedBy { it.lastModified() }) {
            if (total <= budget) break
            val length = file.length(); check(file.delete()); total -= length
        }
    }
    @Synchronized internal fun remove(key: String) {
        val file = File(directory, "${hash(key)}.art")
        check(!file.exists() || file.delete())
    }
    companion object {
        private const val MAX_ENTRY = HttpArtworkRepository.MAX_IMAGE + 8192
        fun hash(key: String): String = MessageDigest.getInstance("SHA-256").digest(key.toByteArray())
            .joinToString("") { "%02x".format(it) }
    }
}

/** One artwork network request. [failure] is null when it produced usable bytes (or a 304). */
data class ArtworkAttempt(val image: Boolean, val url: String, val failure: String?, val millis: Long,
    val bytes: Int, val failures: Int, val retryInMillis: Long)

/** Diagnostics for every artwork download; the default records nothing. */
interface ArtworkLog {
    fun attempt(attempt: ArtworkAttempt) {}
    /** A load that ended without art for a reason other than a failed request. */
    fun failed(reference: ArtworkReference, failure: Throwable) {}
    companion object { val NONE = object : ArtworkLog {} }
}

/** All work is demand-driven. Coalesces each metadata/image key across card instances.
 * Failures are ordinary missing art, never a semantic definition error. */
class HttpArtworkRepository(private val cache: ArtworkDiskCache, private val transport: ArtworkTransport,
    private val policy: ArtworkUrlPolicy = ArtworkUrlPolicy(),
    private val apiBase: String = "https://api.scryfall.com/cards/",
    private val now: () -> Long = System::currentTimeMillis,
    private val imageCheck: (ByteArray, String) -> Boolean = Companion::raster,
    private val gate: ArtworkGate = ArtworkGate(),
    private val log: ArtworkLog = ArtworkLog.NONE) {
    private class Pending(val future: CompletableFuture<ArtworkEntry?>, val ticket: ArtworkGate.Ticket)
    private val pending = ConcurrentHashMap<String, Pending>()
    private val connection = AtomicLong()
    private val failedConnections = ConcurrentHashMap<String, Long>()
    private val loadedUrls = ConcurrentHashMap<String, String>()
    init { policy.validate(apiBase); require(apiBase.endsWith('/')) }

    /** Blocking. Thread interruption is a cancellation, never a provider failure: it records no
     * failure window and a joined caller simply takes the download over. */
    fun load(reference: ArtworkReference, priority: ArtworkPriority = ArtworkPriority.VISIBLE): ByteArray? {
        return try {
            val urls = sequence {
                reference.customUrl?.let { yield(it) }
                yieldAll(resolve(reference, priority))
            }.distinct()
            for (url in urls) {
                policy.validate(url)
                val entry = fetch("image|${reference.key}|$url", url, MAX_IMAGE, image = true, priority)
                if (entry?.status == 200) {
                    loadedUrls[reference.key] = url
                    return entry.bytes
                }
                // A missing source can use another retained/provider image immediately.
                if (entry?.status != 404) break
            }
            null
        } catch (failure: Exception) {
            if (failure !is InterruptedException) runCatching { log.failed(reference, failure) }
            null
        }
    }

    /** Called once per offline -> online transition; successful public cache entries stay intact. */
    fun connectivityRestored() { connection.incrementAndGet() }

    fun rejectDecoded(reference: ArtworkReference) {
        runCatching {
            val url = loadedUrls.remove(reference.key) ?: return
            val key = "image|${reference.key}|$url"
            val marker = cache.read("decode|$key")?.takeIf { it.expires > now() }
            val failures = ((marker?.failures ?: 0) + 1).coerceAtMost(6)
            val delay = ArtworkRetrySchedule.backoff(failures)
            cache.write(key, ArtworkEntry(503, now() + delay, failures, "", "", "", byteArrayOf()))
            failedConnections[key] = connection.get()
            cache.write("decode|$key", ArtworkEntry(503, now() + DAY, failures, "", "", "", byteArrayOf()))
        }
    }

    /** A plausible raster header is not a successful decode. Clear its separate failure history
     * only after Android has actually produced a valid bounded bitmap. */
    fun decoded(reference: ArtworkReference) {
        runCatching {
            val url = loadedUrls.remove(reference.key) ?: return
            cache.remove("decode|image|${reference.key}|$url")
        }
    }

    private fun resolve(reference: ArtworkReference, priority: ArtworkPriority, visited: Set<String> = emptySet()): List<String> {
        val id = reference.printing ?: return emptyList()
        if (id in visited || visited.size >= 3 || runCatching { UUID.fromString(id).toString() == id }.getOrDefault(false).not()) return emptyList()
        val entry = fetch("metadata-v2|$apiBase|$id|${reference.version}", "$apiBase$id", 1024 * 1024, image = false, priority)
            ?.takeIf { it.status == 200 } ?: return emptyList()
        val card = JsonParser.parseString(entry.bytes.toString(Charsets.UTF_8)).asJsonObject
        check(card.get("object")?.asString == "card" && card.get("id")?.asString == id)
        val top = card.getAsJsonObject("image_uris")
        val faces = card.getAsJsonArray("card_faces")?.map { it.asJsonObject }.orEmpty()
        // Reversible cards and some double-faced tokens print the SAME name on both sides: the
        // provider index is exact when its name agrees; the name only corrects a differing index.
        val indexed = faces.getOrNull(reference.face)
        val named = reference.faceName?.let { name -> indexed?.takeIf { it.get("name")?.asString == name }
            ?: faces.firstOrNull { it.get("name")?.asString == name } }
        val layout = card.get("layout")?.asString
        // Spell parts share a physical image, even when MTGJSON labels the row b/c.
        // A named top-level printing (meld parts and separately printed token sides) is also exact.
        val shared = layout in setOf("split", "aftermath", "adventure", "flip", "room")
        val topMatches = reference.faceName != null && card.get("name")?.asString == reference.faceName
        val images = if (top != null && (shared || topMatches || reference.face == 0)) top else
            (named ?: indexed)?.getAsJsonObject("image_uris")
        if (layout == "meld" && reference.faceName != null && !topMatches) {
            val result = card.getAsJsonArray("all_parts")?.firstOrNull {
                it.asJsonObject.get("component")?.asString == "meld_result" &&
                    it.asJsonObject.get("name")?.asString == reference.faceName
            }?.asJsonObject?.get("id")?.asString
            if (result != null) return resolve(reference.copy(printing = result, face = 0), priority, visited + id)
        }
        if (images == null) return emptyList()
        // Never replace an art crop with a full card, or a physical back with a front.
        val sizes = if (reference.size == "art_crop") listOf("art_crop") else
            listOf(reference.size, "normal", "large", "png", "small").distinct()
        return sizes.mapNotNull { images.get(it)?.asString }.distinct()
    }
    private fun fetch(key: String, url: String, maximum: Long, image: Boolean, priority: ArtworkPriority): ArtworkEntry? {
        while (true) {
            val mine = Pending(CompletableFuture(), gate.ticket(priority))
            val existing = pending.putIfAbsent(key, mine)
            if (existing == null) return own(key, url, maximum, image, mine)
            if (priority == ArtworkPriority.VISIBLE) gate.promote(existing.ticket)
            try { return existing.future.get() }
            // The owner was cancelled (e.g. its card scrolled away): this caller downloads instead.
            catch (_: java.util.concurrent.CancellationException) { continue }
        }
    }

    private fun own(key: String, url: String, maximum: Long, image: Boolean, mine: Pending): ArtworkEntry? {
        var result: ArtworkEntry? = null
        var cancelled = true
        try {
            result = download(key, url, maximum, image, mine.ticket)
            cancelled = false
            return result
        } catch (interrupted: InterruptedException) {
            throw interrupted
        } catch (_: Exception) {
            cancelled = false
            return null
        } finally {
            // Unpublish before completing, so a joined caller that takes over cannot rejoin this attempt.
            pending.remove(key, mine)
            if (cancelled) mine.future.cancel(false) else mine.future.complete(result)
        }
    }

    private fun download(key: String, url: String, maximum: Long, image: Boolean, ticket: ArtworkGate.Ticket): ArtworkEntry {
        val old = cache.read(key)
        val generation = connection.get()
        val sameConnection = (failedConnections[key] ?: 0L) == generation
        if (old != null && old.expires > now() && (old.status == 200 || sameConnection)) return old
        if (Thread.currentThread().isInterrupted) throw InterruptedException()
        var error: Exception? = null
        var started = now()
        val response = try { gate.run(ticket) { started = now(); transport.get(url, maximum, old?.etag.orEmpty(), old?.modified.orEmpty()) } }
            catch (failure: Exception) {
                // A cancelled request (interrupted wait, throttle sleep or socket) is not an outage.
                if (failure is InterruptedException || Thread.currentThread().isInterrupted) throw InterruptedException()
                error = failure
                ArtworkResponse(503)
            }
        val valid = response.status == 200 && response.bytes.size <= maximum &&
            if (image) raster(response.bytes, response.type) && imageCheck(response.bytes, response.type) else validMetadata(response.bytes)
        val failures = if (valid || response.status == 304) 0 else ((old?.failures ?: 0) + 1).coerceAtMost(6)
        val expiry = now() + when {
            valid || response.status == 304 -> DAY
            else -> ArtworkRetrySchedule.backoff(failures)
        }
        val entry = when {
            valid -> ArtworkEntry(200, expiry, 0, response.type, response.etag, response.modified, response.bytes)
            response.status == 304 && old?.status == 200 -> old.copy(expires = expiry, failures = 0)
            // Reuse good cached bytes during an outage, with backoff before revalidation.
            old?.status == 200 -> old.copy(expires = maxOf(expiry, response.retryAt), failures = failures)
            else -> ArtworkEntry(if (response.status == 404) 404 else 503, maxOf(expiry, response.retryAt),
                failures, "", "", "", byteArrayOf())
        }
        cache.write(key, entry)
        if (entry.status != 200) failedConnections[key] = generation else failedConnections.remove(key)
        val failure = when {
            valid || response.status == 304 -> null
            error != null -> describe(error)
            response.status == 200 -> if (image) "not a usable image" else "unusable card data"
            else -> "HTTP ${response.status}"
        }
        runCatching { log.attempt(ArtworkAttempt(image, url, failure, now() - started, response.bytes.size, failures,
            if (failure == null) 0 else (entry.expires - now()).coerceAtLeast(0))) }
        return entry
    }
    private fun describe(error: Throwable): String = generateSequence(error) { it.cause.takeIf { cause -> cause !== it } }.take(3)
        .joinToString(" <- ") { "${it.javaClass.simpleName}: ${it.message.orEmpty().take(200)}" }

    private fun validMetadata(bytes: ByteArray): Boolean = runCatching {
        // Bound nesting before Gson's tree parser, not after it.
        val reader = com.google.gson.stream.JsonReader(bytes.inputStream().reader(Charsets.UTF_8))
        fun value(depth: Int) {
            check(depth <= 32)
            when (reader.peek()) {
                com.google.gson.stream.JsonToken.BEGIN_OBJECT -> { reader.beginObject(); while (reader.hasNext()) { reader.nextName(); value(depth + 1) }; reader.endObject() }
                com.google.gson.stream.JsonToken.BEGIN_ARRAY -> { reader.beginArray(); while (reader.hasNext()) value(depth + 1); reader.endArray() }
                else -> reader.skipValue()
            }
        }
        reader.use { value(0); check(it.peek() == com.google.gson.stream.JsonToken.END_DOCUMENT) }
        JsonParser.parseString(bytes.toString(Charsets.UTF_8)) is JsonObject
    }.getOrDefault(false)
    companion object {
        const val MAX_IMAGE = 10L * 1024 * 1024
        const val DAY = 24 * 60 * 60_000L
        /** Only raster bytes are ever handed to Android. Content-Type alone cannot admit HTML/SVG. */
        fun raster(bytes: ByteArray, type: String): Boolean = when (type.lowercase()) {
            "image/png" -> bytes.size >= 8 && bytes.take(8) == listOf(137,80,78,71,13,10,26,10).map(Int::toByte)
            "image/jpeg" -> bytes.size >= 3 && bytes[0] == 0xff.toByte() && bytes[1] == 0xd8.toByte() && bytes[2] == 0xff.toByte()
            "image/webp" -> bytes.size >= 12 && bytes.copyOfRange(0,4).toString(Charsets.US_ASCII) == "RIFF" &&
                bytes.copyOfRange(8,12).toString(Charsets.US_ASCII) == "WEBP"
            else -> false
        }
    }
}

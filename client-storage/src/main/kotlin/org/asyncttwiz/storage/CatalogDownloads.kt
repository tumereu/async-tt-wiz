package org.asyncttwiz.storage

import java.io.*
import java.net.HttpURLConnection
import java.net.URI
import java.security.MessageDigest
import java.util.Properties
import java.util.zip.GZIPInputStream

interface CatalogSource {
    fun metadata(): CatalogMetadata
    fun size(): Long?
    fun checksum(): String
    fun download(output: OutputStream, progress: (Long) -> Unit)
}

object CatalogUpdatePolicy {
    fun changed(installed: CatalogMetadata?, available: CatalogMetadata?): Boolean = available != null &&
        (installed == null || installed.date != available.date || installed.version != available.version)
    fun download(installed: CatalogMetadata?, available: CatalogMetadata?, metered: Boolean, consent: Boolean): Boolean =
        changed(installed, available) && (consent || (installed != null && !metered))
}

/** Dedicated public provider connection: no service client, bearer, cookies or user-controlled URL. */
class HttpCatalogSource(
    private val base: String = "https://mtgjson.com/api/v5/",
    private val syntheticOrigin: String? = null,
    private val userAgent: String = "AsyncTabletopWizards/0.1.0 (catalog client)",
) : CatalogSource {
    init {
        val uri = URI(base)
        require(uri.rawUserInfo == null && uri.rawQuery == null && uri.rawFragment == null && base.endsWith('/'))
        if (syntheticOrigin == null) require(uri.scheme == "https" && uri.host == "mtgjson.com" && uri.port == -1)
        else {
            val test = URI(syntheticOrigin)
            require(test.scheme == "http" && test.host == "127.0.0.1" && test.port in 1..65535 &&
                test.rawUserInfo == null && test.rawPath.isNullOrEmpty() && test.rawQuery == null && test.rawFragment == null)
            require(origin(uri) == syntheticOrigin)
        }
    }

    override fun metadata(): CatalogMetadata = request("Meta.json", 10_000) { connection ->
        connection.inputStream.use { MtgJsonCatalog().metadata(LimitedInput(it, 1024 * 1024)) }
    }
    override fun size(): Long? = request("AllPrintings.json.gz", 10_000, "HEAD") { connection ->
        connection.contentLengthLong.takeIf { it > 0 && it <= MAX_BYTES }
    }
    override fun checksum(): String = request("AllPrintings.json.gz.sha256", 10_000) { connection ->
        connection.inputStream.use { LimitedInput(it, 1024).readBytes().toString(Charsets.US_ASCII).trim() }
            .also { check(Regex("[a-fA-F0-9]{64}").matches(it)) }.lowercase()
    }
    override fun download(output: OutputStream, progress: (Long) -> Unit) = request("AllPrintings.json.gz", 30 * 60_000) { connection ->
        check(connection.contentLengthLong <= MAX_BYTES)
        connection.inputStream.use { input ->
            var count = 0L
            val bytes = ByteArray(64 * 1024)
            while (true) {
                check(!Thread.currentThread().isInterrupted)
                val read = input.read(bytes)
                if (read == -1) break
                count += read
                check(count <= MAX_BYTES)
                output.write(bytes, 0, read)
                progress(count)
            }
            check(connection.contentLengthLong < 0 || count == connection.contentLengthLong)
        }
    }

    private fun <T> request(path: String, timeout: Int, method: String = "GET", block: (HttpURLConnection) -> T): T {
        val deadline = System.nanoTime() + timeout * 1_000_000L
        var uri = URI(base).resolve(path)
        repeat(4) { redirect ->
            // Validate every redirect; cross-origin redirects and HTTPS downgrades never carry any request.
            require(uri.rawUserInfo == null && uri.rawFragment == null && origin(uri) == origin(URI(base)))
            val connection = uri.toURL().openConnection() as HttpURLConnection
            try {
                val remaining = ((deadline - System.nanoTime()) / 1_000_000).toInt().coerceAtLeast(1)
                check(System.nanoTime() < deadline)
                connection.instanceFollowRedirects = false
                connection.connectTimeout = minOf(remaining, 10_000)
                connection.readTimeout = minOf(remaining, 30_000)
                connection.requestMethod = method
                connection.useCaches = false
                connection.setRequestProperty("User-Agent", userAgent)
                connection.setRequestProperty("Accept", if (path.endsWith(".json")) "application/json" else "application/octet-stream")
                connection.setRequestProperty("Accept-Encoding", "identity")
                when (connection.responseCode) {
                    200 -> {
                        check(System.nanoTime() < deadline)
                        val bounded = object : HttpURLConnection(connection.url) {
                            override fun disconnect() = connection.disconnect()
                            override fun usingProxy() = connection.usingProxy()
                            override fun connect() = connection.connect()
                            override fun getContentLengthLong() = connection.contentLengthLong
                            override fun getInputStream(): InputStream = object : FilterInputStream(connection.inputStream) {
                                private fun remaining() {
                                    check(System.nanoTime() < deadline) { "Catalog request timed out" }
                                    connection.readTimeout = minOf(30_000, ((deadline - System.nanoTime()) / 1_000_000).toInt().coerceAtLeast(1))
                                }
                                override fun read(): Int { remaining(); return `in`.read().also { check(System.nanoTime() < deadline) } }
                                override fun read(bytes: ByteArray, offset: Int, length: Int): Int {
                                    remaining(); return `in`.read(bytes, offset, length).also { check(System.nanoTime() < deadline) }
                                }
                            }
                        }
                        return block(bounded)
                    }
                    301, 302, 303, 307, 308 -> {
                        check(redirect < 3)
                        uri = uri.resolve(requireNotNull(connection.getHeaderField("Location")))
                    }
                    else -> error("Catalog source unavailable")
                }
            } finally { connection.disconnect() }
        }
        error("Too many redirects")
    }

    companion object {
        const val MAX_BYTES = 4L * 1024 * 1024 * 1024
        private fun origin(uri: URI): String = "${uri.scheme}://${uri.rawAuthority}"
    }
}

internal class LimitedInput(input: InputStream, private val maximum: Long) : FilterInputStream(input) {
    private var count = 0L
    override fun read(): Int = super.read().also { if (it != -1) add(1) }
    override fun read(bytes: ByteArray, offset: Int, length: Int): Int = `in`.read(bytes, offset, length).also { if (it > 0) add(it) }
    private fun add(size: Int) { count += size; check(count <= maximum) { "Catalog size limit" } }
}

class CatalogInstaller(private val directory: File, private val repository: CatalogRepository,
    private val source: CatalogSource, private val freeBytes: () -> Long = { directory.usableSpace },
    private val decodedLimit: Long = HttpCatalogSource.MAX_BYTES) {
    fun install(expected: CatalogMetadata, size: Long?, progress: (String) -> Unit = {}): CatalogMetadata {
        check(directory.isDirectory || directory.mkdirs())
        val reserve = 64L * 1024 * 1024
        val compressed = size ?: HttpCatalogSource.MAX_BYTES
        check(freeBytes() >= compressed + minOf(decodedLimit, compressed * 12) + reserve) { "Not enough free space" }
        val download = File(directory, "download.gz.part")
        try {
            val checksum = source.checksum()
            val digest = MessageDigest.getInstance("SHA-256")
            FileOutputStream(download).use { file ->
                java.security.DigestOutputStream(file, digest).use { output ->
                    source.download(output) { bytes ->
                        check(freeBytes() >= reserve)
                        progress("Downloading card records: ${bytes / 1024} KiB")
                    }
                    output.flush(); file.fd.sync()
                }
            }
            check(digest.digest().joinToString("") { "%02x".format(it) } == checksum) { "Catalog checksum mismatch" }
            return repository.stage { index ->
                GZIPInputStream(FileInputStream(download), 64 * 1024).use { gzip ->
                    MtgJsonCatalog().import(LimitedInput(gzip, decodedLimit), index, expected.copy(checksum = checksum)) { count ->
                        check(freeBytes() >= reserve)
                        progress("Indexing card records: $count")
                    }
                }
            }
        } finally { download.delete() }
    }
}

/** Public acquisition scheduling survives process death independently of account/session data. */
class CatalogCadence(private val file: File) {
    data class State(val success: Long = -1, val attempt: Long = -1, val failures: Int = 0)
    fun state(): State {
        if (!file.exists()) return State()
        return runCatching {
            val properties = Properties().apply { file.inputStream().use(::load) }
            State(properties.getProperty("success").toLong(), properties.getProperty("attempt").toLong(),
                properties.getProperty("failures").toInt().coerceIn(0, 3))
        }.getOrDefault(State())
    }
    fun due(now: Long): Boolean {
        val state = state()
        if (state.attempt > now || state.success > now) return true // clock correction, not a permanent lockout
        return if (state.failures > 0) now - state.attempt >= listOf(15 * 60_000L, 60 * 60_000L, 6 * 60 * 60_000L)[state.failures - 1]
        else state.success < 0 || now - state.success >= 24 * 60 * 60_000L
    }
    fun checked(now: Long, successful: Boolean) {
        val previous = state()
        val next = if (successful) State(now, now, 0) else State(previous.success, now, (previous.failures + 1).coerceAtMost(3))
        file.parentFile?.mkdirs()
        val temporary = File(file.parentFile, "${file.name}.new")
        FileOutputStream(temporary).use { output ->
            Properties().apply {
                setProperty("success", next.success.toString()); setProperty("attempt", next.attempt.toString())
                setProperty("failures", next.failures.toString())
            }.store(output, null)
            output.fd.sync()
        }
        check(temporary.renameTo(file))
    }
}

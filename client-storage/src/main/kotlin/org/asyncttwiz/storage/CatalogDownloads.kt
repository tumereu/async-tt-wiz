package org.asyncttwiz.storage

import java.io.*
import java.net.HttpURLConnection
import java.net.URI
import java.security.MessageDigest
import java.util.Properties
import java.util.zip.GZIPInputStream
import java.time.LocalDate

interface CatalogSource {
    fun metadata(): CatalogMetadata
    fun size(): Long?
    fun checksum(): String
    fun download(output: OutputStream, progress: (Long) -> Unit)
    fun setList(): CatalogSetList = error("Set list unavailable")
    fun setSource(code: String): CatalogSource = error("Set source unavailable")
}

data class CatalogSetList(val metadata: CatalogMetadata, val sets: List<CatalogSet>)
data class CatalogUpdatePlan(val metadata: CatalogMetadata, val sets: List<CatalogSet> = emptyList(), val full: Boolean = false)

/** MTGJSON escapes Windows reserved filenames (CON is the Conflux set). Never accept a path. */
internal fun catalogSetFilename(code: String): String {
    require(Regex("[A-Z0-9]{1,16}").matches(code))
    return if (code in setOf("CON", "PRN", "AUX", "NUL") || Regex("(?:COM|LPT)[1-9]").matches(code)) "${code}_" else code
}

object CatalogUpdatePolicy {
    fun changed(installed: CatalogMetadata?, available: CatalogMetadata?): Boolean = available != null &&
        (installed == null || installed.indexVersion != available.indexVersion)
    fun download(installed: CatalogMetadata?, available: CatalogMetadata?, metered: Boolean, consent: Boolean): Boolean =
        changed(installed, available) && (consent || (installed != null && !metered))
    fun plan(installed: CatalogMetadata?, installedSets: List<CatalogSet>, available: CatalogSetList,
        today: LocalDate = LocalDate.now()): CatalogUpdatePlan? {
        if (changed(installed, available.metadata)) return CatalogUpdatePlan(available.metadata, full = true)
        val codes = installedSets.map { it.code }.toSet()
        // Future releases are previews too; keep refreshing them until sixty days after release.
        val sets = available.sets.filter { it.code !in codes || !LocalDate.parse(it.releaseDate).isBefore(today.minusDays(60)) }
        return sets.takeIf { it.isNotEmpty() }?.let { CatalogUpdatePlan(available.metadata, it) }
    }
    fun canDownload(installed: CatalogMetadata?, plan: CatalogUpdatePlan?, metered: Boolean, consent: Boolean): Boolean =
        plan != null && (consent || (installed != null && !metered))
    fun fullRefresh(available: CatalogMetadata) = CatalogUpdatePlan(available, full = true)
}

/** Dedicated public provider connection: no service client, bearer, cookies or user-controlled URL. */
class HttpCatalogSource(
    private val base: String = "https://mtgjson.com/api/v5/",
    private val syntheticOrigin: String? = null,
    private val userAgent: String = "AsyncTabletopWizards/0.1.0 (catalog client)",
    private val filename: String = "AllPrintings",
) : CatalogSource {
    init {
        val uri = URI(base)
        require(filename == "AllPrintings" || Regex("[A-Z0-9]{1,16}_?").matches(filename))
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
    override fun setList(): CatalogSetList = request("SetList.json", 10_000) { connection ->
        connection.inputStream.use { MtgJsonCatalog().setList(LimitedInput(it, 16 * 1024 * 1024)) }
    }
    override fun setSource(code: String): CatalogSource = HttpCatalogSource(base, syntheticOrigin, userAgent, catalogSetFilename(code))
    private val maximum get() = if (filename == "AllPrintings") MAX_BYTES else MAX_SET_BYTES
    override fun size(): Long? = request("$filename.json.gz", 10_000, "HEAD") { connection ->
        connection.contentLengthLong.takeIf { it > 0 && it <= maximum }
    }
    override fun checksum(): String = request("$filename.json.gz.sha256", 10_000) { connection ->
        connection.inputStream.use { LimitedInput(it, 1024).readBytes().toString(Charsets.US_ASCII).trim() }
            .also { check(Regex("[a-fA-F0-9]{64}").matches(it)) }.lowercase()
    }
    override fun download(output: OutputStream, progress: (Long) -> Unit) = request("$filename.json.gz", 30 * 60_000) { connection ->
        check(connection.contentLengthLong <= maximum)
        connection.inputStream.use { input ->
            var count = 0L
            val bytes = ByteArray(64 * 1024)
            while (true) {
                check(!Thread.currentThread().isInterrupted)
                val read = input.read(bytes)
                if (read == -1) break
                count += read
                check(count <= maximum)
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
        const val MAX_SET_BYTES = 256L * 1024 * 1024
        const val MAX_DECODED_SET_BYTES = 1024L * 1024 * 1024
        private fun origin(uri: URI): String = "${uri.scheme}://${uri.rawAuthority}"
    }
}

internal class LimitedInput(input: InputStream, private val maximum: Long) : FilterInputStream(input) {
    private var count = 0L
    override fun read(): Int = super.read().also { if (it != -1) add(1) }
    override fun read(bytes: ByteArray, offset: Int, length: Int): Int = `in`.read(bytes, offset, length).also { if (it > 0) add(it) }
    private fun add(size: Int) { count += size; check(count <= maximum) { "Catalog size limit" } }
}

enum class CatalogPhase { DOWNLOADING, INDEXING, FINISHING }

/** Byte counts always refer to the compressed download, including during gzip decoding. */
data class CatalogProgress(val phase: CatalogPhase, val done: Long, val total: Long?, val records: Long = 0)

internal class CountingInput(input: InputStream) : FilterInputStream(input) {
    var count: Long = 0
        private set
    override fun read(): Int = `in`.read().also { if (it != -1) count++ }
    override fun read(bytes: ByteArray, offset: Int, length: Int): Int =
        `in`.read(bytes, offset, length).also { if (it > 0) count += it }
    override fun skip(bytes: Long): Long = `in`.skip(bytes).also { count += it }
}

class CatalogInstaller(private val directory: File, private val repository: CatalogRepository,
    private val source: CatalogSource, private val freeBytes: () -> Long = { directory.usableSpace },
    private val decodedLimit: Long = HttpCatalogSource.MAX_BYTES) {
    fun install(expected: CatalogMetadata, size: Long?, progress: (CatalogProgress) -> Unit = {}): CatalogMetadata {
        check(directory.isDirectory || directory.mkdirs())
        val reserve = 64L * 1024 * 1024
        val compressed = size ?: HttpCatalogSource.MAX_BYTES
        check(freeBytes() >= compressed + minOf(decodedLimit, compressed * 12) + reserve) { "Not enough free space" }
        val download = File(directory, "download.gz.part")
        try {
            val checksum = source.checksum()
            val digest = MessageDigest.getInstance("SHA-256")
            progress(CatalogProgress(CatalogPhase.DOWNLOADING, 0, size))
            FileOutputStream(download).use { file ->
                java.security.DigestOutputStream(file, digest).use { output ->
                    source.download(output) { bytes ->
                        check(freeBytes() >= reserve)
                        progress(CatalogProgress(CatalogPhase.DOWNLOADING, bytes, size))
                    }
                    output.flush(); file.fd.sync()
                }
            }
            check(digest.digest().joinToString("") { "%02x".format(it) } == checksum) { "Catalog checksum mismatch" }
            val indexingTotal = size ?: download.length()
            return repository.stage(finishing = { progress(CatalogProgress(CatalogPhase.FINISHING, 0, null)) }) { index ->
                progress(CatalogProgress(CatalogPhase.INDEXING, 0, indexingTotal))
                CountingInput(FileInputStream(download)).use { compressedInput ->
                    GZIPInputStream(compressedInput, 64 * 1024).use { gzip ->
                        MtgJsonCatalog().import(LimitedInput(gzip, decodedLimit), index, expected.copy(checksum = checksum)) { count ->
                            check(freeBytes() >= reserve)
                            progress(CatalogProgress(CatalogPhase.INDEXING, compressedInput.count, indexingTotal, count))
                        }
                    }
                }
            }
        } finally { download.delete() }
    }

    fun install(plan: CatalogUpdatePlan, size: Long? = null, progress: (CatalogProgress) -> Unit = {}): CatalogMetadata {
        if (plan.full) return install(plan.metadata, size, progress)
        require(plan.sets.isNotEmpty() && plan.sets.map { it.code }.distinct().size == plan.sets.size)
        check(repository.metadata()?.indexVersion == CATALOG_INDEX_VERSION)
        check(directory.isDirectory || directory.mkdirs())
        val downloads = mutableListOf<Triple<CatalogSet, File, String>>()
        val reserve = 64L * 1024 * 1024
        try {
            plan.sets.forEach { set ->
                val setSource = source.setSource(set.code)
                val total = setSource.size()
                val compressed = total ?: HttpCatalogSource.MAX_SET_BYTES
                val installedSize = directory.listFiles()?.filter { it.extension == "db" }?.sumOf { it.length() } ?: 0
                check(freeBytes() >= installedSize + compressed + minOf(HttpCatalogSource.MAX_DECODED_SET_BYTES, compressed * 12) + reserve) {
                    "Not enough free space"
                }
                val file = File(directory, "${catalogSetFilename(set.code)}.gz.part")
                val checksum = setSource.checksum()
                downloads += Triple(set, file, checksum)
                val digest = MessageDigest.getInstance("SHA-256")
                progress(CatalogProgress(CatalogPhase.DOWNLOADING, 0, total))
                FileOutputStream(file).use { output ->
                    java.security.DigestOutputStream(output, digest).use { checked ->
                        setSource.download(checked) { bytes ->
                            check(bytes <= HttpCatalogSource.MAX_SET_BYTES && freeBytes() >= reserve)
                            progress(CatalogProgress(CatalogPhase.DOWNLOADING, bytes, total))
                        }
                        checked.flush(); output.fd.sync()
                    }
                }
                check(digest.digest().joinToString("") { "%02x".format(it) } == checksum) { "Catalog checksum mismatch" }
            }
            return repository.stage(copyInstalled = true,
                finishing = { progress(CatalogProgress(CatalogPhase.FINISHING, 0, null)) }) { index ->
                downloads.forEach { (set, file, checksum) ->
                    index.removeSet(set.code)
                    progress(CatalogProgress(CatalogPhase.INDEXING, 0, file.length()))
                    CountingInput(FileInputStream(file)).use { compressed ->
                        GZIPInputStream(compressed, 64 * 1024).use { gzip ->
                            MtgJsonCatalog().import(LimitedInput(gzip, minOf(decodedLimit, HttpCatalogSource.MAX_DECODED_SET_BYTES)),
                                index, plan.metadata.copy(checksum = checksum), set.code) { count ->
                                check(freeBytes() >= reserve)
                                progress(CatalogProgress(CatalogPhase.INDEXING, compressed.count, file.length(), count))
                            }
                        }
                    }
                }
                plan.metadata.copy(checksum = "") // A mixed catalog has no AllPrintings checksum.
            }
        } finally { downloads.forEach { it.second.delete() } }
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

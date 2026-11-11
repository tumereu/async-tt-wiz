package org.asyncttwiz.storage

import java.io.*
import java.net.HttpURLConnection
import java.net.URI
import java.util.Locale
import java.util.logging.Logger
import kotlinx.serialization.json.Json
import org.asyncttwiz.catalog.CardCatalogFiles
import org.asyncttwiz.catalog.CardCatalogFormat
import org.asyncttwiz.catalog.CatalogCards
import org.asyncttwiz.catalog.CatalogFaceRecord
import org.asyncttwiz.catalog.CatalogHeader
import org.asyncttwiz.catalog.CatalogPrintingRecord
import org.asyncttwiz.catalog.CatalogRecipeRecord
import org.asyncttwiz.catalog.CatalogRecord
import org.asyncttwiz.catalog.CatalogSetRecord
import org.asyncttwiz.catalog.CatalogVersion
import org.asyncttwiz.protocol.CardColors
import org.asyncttwiz.protocol.CardSearchRules

/** The ATW server's card catalog (docs/specs/web-client.md section 3.4): which one is current, and its file by checksum. */
interface CatalogSource {
    /** The server's current catalog (`GET /v1/catalog`). */
    fun current(): CatalogVersion
    /** Streams catalog [checksum]'s file (`GET /v1/catalog/{checksum}`) into [output], with the bytes so far. */
    fun download(checksum: String, output: OutputStream, progress: (Long) -> Unit)
}

/** The catalog endpoints of the ATW server at [origin] (open without sign-in, LOB-091): HTTPS, or the debug loopback. */
class HttpCatalogSource(private val origin: String, private val userAgent: String = "AsyncTabletopWizards (card catalog)") : CatalogSource {
    init {
        val uri = URI(origin)
        require(uri.rawUserInfo == null && uri.rawPath.isNullOrEmpty() && uri.rawQuery == null && uri.rawFragment == null &&
            (uri.scheme == "https" || (uri.scheme == "http" && uri.host == "127.0.0.1"))) { "Not a catalog origin" }
    }

    override fun current(): CatalogVersion = request("/v1/catalog", 15_000) { connection ->
        val text = connection.inputStream.use { LimitedInput(it, 64 * 1024).readBytes() }.toString(Charsets.UTF_8)
        json.decodeFromString(CatalogVersion.serializer(), text).also { check(CardCatalogFiles.valid(it.checksum)) }
    }

    override fun download(checksum: String, output: OutputStream, progress: (Long) -> Unit) {
        require(CardCatalogFiles.valid(checksum))
        request("/v1/catalog/$checksum", 30 * 60_000) { connection ->
            check(connection.contentLengthLong <= MAX_BYTES) { "Card catalog too large" }
            connection.inputStream.use { input ->
                var count = 0L
                val bytes = ByteArray(64 * 1024)
                while (true) {
                    check(!Thread.currentThread().isInterrupted)
                    val read = input.read(bytes)
                    if (read == -1) break
                    count += read
                    check(count <= MAX_BYTES) { "Card catalog too large" }
                    output.write(bytes, 0, read)
                    progress(count)
                }
                check(connection.contentLengthLong < 0 || count == connection.contentLengthLong) { "Card catalog download cut short" }
            }
        }
    }

    private fun <T> request(path: String, timeout: Int, block: (HttpURLConnection) -> T): T {
        val connection = URI(origin + path).toURL().openConnection() as HttpURLConnection
        try {
            connection.instanceFollowRedirects = false
            connection.connectTimeout = minOf(timeout, 15_000)
            connection.readTimeout = minOf(timeout, 30_000)
            connection.useCaches = false
            connection.setRequestProperty("User-Agent", userAgent)
            connection.setRequestProperty("Accept-Encoding", "identity")
            val status = connection.responseCode
            check(status == 200) { "Card catalog unavailable: HTTP $status for $path" }
            return block(connection)
        } finally { connection.disconnect() }
    }

    companion object {
        /** Far above the catalog's ~13 MB (section 3.3). */
        const val MAX_BYTES = 512L * 1024 * 1024
        private val json = Json { ignoreUnknownKeys = true }
    }
}

internal class LimitedInput(input: InputStream, private val maximum: Long) : FilterInputStream(input) {
    private var count = 0L
    override fun read(): Int = super.read().also { if (it != -1) add(1) }
    override fun read(bytes: ByteArray, offset: Int, length: Int): Int = `in`.read(bytes, offset, length).also { if (it > 0) add(it) }
    private fun add(size: Int) { count += size; check(count <= maximum) { "Catalog size limit" } }
}

enum class CatalogPhase { DOWNLOADING, INDEXING, FINISHING }

/** Downloading and indexing count the compressed file's bytes; finishing counts printings (its card search goes through
 * every one). */
data class CatalogProgress(val phase: CatalogPhase, val done: Long, val total: Long?) {
    /** How much of the whole installation is done, 0 to 99 ("Card records updating · 43%", section 3.6): the download is
     * the first [DOWNLOAD_SHARE] percent, indexing up to [FINISHING_FROM], finishing (the card search) the rest to 99. */
    val percent: Int get() {
        val fraction = total?.takeIf { it > 0 }?.let { (done.toDouble() / it).coerceIn(0.0, 1.0) } ?: 0.0
        return when (phase) {
            CatalogPhase.DOWNLOADING -> (fraction * DOWNLOAD_SHARE).toInt()
            CatalogPhase.INDEXING -> (DOWNLOAD_SHARE + fraction * (FINISHING_FROM - DOWNLOAD_SHARE)).toInt()
            CatalogPhase.FINISHING -> (FINISHING_FROM + fraction * (99 - FINISHING_FROM)).toInt()
        }
    }

    // After the download, the records and finishing took about as long each on an API 29 emulator (2026-11-10)
    private companion object { const val DOWNLOAD_SHARE = 20; const val FINISHING_FROM = 60 }
}

internal class CountingInput(input: InputStream) : FilterInputStream(input) {
    var count: Long = 0
        private set
    override fun read(): Int = `in`.read().also { if (it != -1) count++ }
    override fun read(bytes: ByteArray, offset: Int, length: Int): Int =
        `in`.read(bytes, offset, length).also { if (it > 0) count += it }
    override fun skip(bytes: Long): Long = `in`.skip(bytes).also { count += it }
}

/**
 * Installs the server's card catalog (docs/specs/web-client.md section 3.5) as the device's card records: downloads the
 * file, indexes it record by record into a new generation of [repository] (search, imports, support marks), and
 * publishes it only when the file's checksum is the one asked for. Always the whole file. Blocking: a worker only.
 */
class CatalogInstaller(private val directory: File, private val repository: CatalogRepository,
    private val source: CatalogSource, private val freeBytes: () -> Long = { directory.usableSpace },
    private val log: (String) -> Unit = { Logger.getLogger(CatalogInstaller::class.java.name).info(it) }) {
    /** Installs catalog [expected] (the app's built-in checksum; null: the server's current one) and returns its metadata. */
    fun install(expected: String?, progress: (CatalogProgress) -> Unit = {}): CatalogMetadata {
        check(directory.isDirectory || directory.mkdirs())
        val current = try { source.current() } catch (failure: Exception) {
            if (expected == null) throw failure
            log("Card catalog: the server's current version couldn't be read (${failure.message}); downloading $expected")
            null
        }
        val checksum = expected ?: current!!.checksum
        require(CardCatalogFiles.valid(checksum)) { "Not a catalog checksum" }
        val size = current?.takeIf { it.checksum == checksum }?.size
        if (current != null && current.checksum != checksum) log("Card catalog: the server's current is ${current.checksum}, this app needs $checksum")
        val reserve = 64L * 1024 * 1024
        check(freeBytes() >= (size ?: (16L * 1024 * 1024)) * 24 + reserve) { "Not enough free space" }
        val download = File(directory, "catalog.jsonl.gz.part")
        try {
            log("Card catalog: downloading $checksum (${size ?: "unknown"} bytes)")
            val downloading = System.nanoTime()
            progress(CatalogProgress(CatalogPhase.DOWNLOADING, 0, size))
            FileOutputStream(download).use { file ->
                source.download(checksum, file) { bytes ->
                    check(freeBytes() >= reserve) { "Not enough free space" }
                    progress(CatalogProgress(CatalogPhase.DOWNLOADING, bytes, size))
                }
                file.flush(); file.fd.sync()
            }
            val total = download.length()
            log("Card catalog: downloaded $total bytes in ${(System.nanoTime() - downloading) / 1_000_000} ms; indexing")
            return repository.stage(finishing = { done, all -> progress(CatalogProgress(CatalogPhase.FINISHING, done, all)) }, log = log) { index ->
                progress(CatalogProgress(CatalogPhase.INDEXING, 0, total))
                indexAlongsideReading(download, checksum, CatalogRecordIndexer(index)) { bytes ->
                    check(freeBytes() >= reserve) { "Not enough free space" }
                    progress(CatalogProgress(CatalogPhase.INDEXING, bytes, total))
                }.also { log("Card catalog $checksum indexed: ${it.records} printings") }
            }
        } finally { download.delete() }
    }

    /**
     * Indexes the catalog in [download] with [indexer] on this thread while a thread of its own unzips, checks and decodes
     * the file into batches ([CatalogRecordReader]): on a phone each side takes about half the time. [read] gets the file's
     * bytes read so far, on this thread.
     */
    private fun indexAlongsideReading(download: File, checksum: String, indexer: CatalogRecordIndexer,
        read: (Long) -> Unit): CatalogMetadata {
        class Read(val bytes: Long)
        class Done(val metadata: CatalogMetadata)
        val queue = java.util.concurrent.ArrayBlockingQueue<Any>(4)
        val stopped = java.util.concurrent.atomic.AtomicBoolean()
        val readerTimes = CatalogPhaseTimes()
        fun hand(item: Any) {
            readerTimes.time("waits for writes") {
                while (!queue.offer(item, 100, java.util.concurrent.TimeUnit.MILLISECONDS)) check(!stopped.get()) { "Indexing stopped" }
            }
        }
        val reader = Thread({
            val outcome: Any = try {
                readerTimes.time("file reads") {
                    CountingInput(FileInputStream(download)).use { counted ->
                        val records = CatalogRecordReader(readerTimes)
                        var last = 0L
                        val sum = CardCatalogFiles.stream(counted, recipes = false) { record ->
                            check(!stopped.get()) { "Indexing stopped" }
                            records.add(record)?.let(::hand)
                            if (counted.count - last >= 64 * 1024) { last = counted.count; hand(Read(counted.count)) }
                        }
                        check(sum == checksum) { "Card catalog checksum mismatch: asked for $checksum, read $sum" }
                        val (rest, metadata) = records.finish(checksum)
                        rest?.let(::hand)
                        Done(metadata)
                    }
                }
            } catch (failure: Throwable) { failure }
            try { hand(outcome) } catch (_: IllegalStateException) {} // The indexing side has stopped
        }, "catalog-reader")
        reader.isDaemon = true
        reader.start()
        try {
            while (true) {
                val item = indexer.index.times.time("waits for reads") { queue.take() }
                when (item) {
                    is CatalogWrites -> indexer.write(item)
                    is Read -> read(item.bytes)
                    is Done -> return item.metadata
                    is Throwable -> throw item
                }
            }
        } finally {
            stopped.set(true)
            reader.join()
            indexer.index.times.addAll(readerTimes, "alongside: ")
        }
    }
}

/** What a batch of a catalog's records adds to card records: sets with their marks, printings with what the index takes
 * from them, and the supported printings' ids. */
internal class CatalogWrites(val sets: List<CatalogSetRecord>, val printings: List<CatalogIndex.StagedPrinting>,
    val supported: List<String>)

/**
 * Reads a catalog's records, in file order, into batches of [CatalogWrites]: each printing as the record the app keeps
 * (CatalogCards.sourceRecord) with its search traits, the sets with their marks, and the supported printings. Touches no
 * database, so it can run on a thread of its own; [times] gets where its time went.
 */
internal class CatalogRecordReader(private val times: CatalogPhaseTimes = CatalogPhaseTimes()) {
    private var header: CatalogHeader? = null
    private val faces = ArrayList<CatalogFaceRecord>()
    private var sets = ArrayList<CatalogSetRecord>()
    private var batch = ArrayList<CatalogIndex.StagedPrinting>(BATCH)
    private var supported = ArrayList<String>(BATCH)
    private var setCount = 0
    private var printings = 0L

    /** Takes [record]; returns a batch to write once one is full. */
    fun add(record: CatalogRecord): CatalogWrites? {
        when (record) {
            is CatalogHeader -> {
                check(header == null) { "Two catalog headers" }
                check(record.format == CardCatalogFormat.FORMAT) { "Catalog format ${record.format}, expected ${CardCatalogFormat.FORMAT}" }
                check(record.formats == CardSearchRules.FORMATS) { "The catalog's formats aren't this app's" }
                header = record
            }
            is CatalogSetRecord -> {
                checkNotNull(header) { "No catalog header" }
                sets += record
                setCount++
            }
            is CatalogFaceRecord -> { check(record.i == faces.size) { "Face ${record.i} out of order" }; faces += record }
            is CatalogPrintingRecord -> {
                val face = faces.getOrNull(record.face) ?: error("Printing ${record.id} names a face the catalog doesn't have")
                val printing = times.time("printing records") {
                    CatalogPrinting(record.id, face.name, record.set, record.number, "English", face.side, record.scryfall,
                        record.token, record.others, CatalogCards.sourceRecordText(record, face))
                }
                // What addBatch reads back from the record: its scryfallOracleId (search key) and faceName, put there from the face
                batch += CatalogIndex.StagedPrinting(printing, CatalogPrintingTraits(record.rarity, CardColors.mask(face.identity),
                    face.legal, if (record.token) record.makers else record.tokens),
                    face.oracle.ifEmpty { "name:${face.name.lowercase(Locale.ROOT)}" }, face.faceName.ifEmpty { null })
                if (face.automatum && !record.token) supported += record.id
                printings++
                if (batch.size == BATCH) return take()
            }
            is CatalogRecipeRecord -> Unit // Packs are the server's (section 3.7)
        }
        return null
    }

    /** The last batch (null: nothing left) and the catalog's metadata, once the file is read whole. */
    fun finish(checksum: String): Pair<CatalogWrites?, CatalogMetadata> {
        val read = checkNotNull(header) { "No catalog header" }
        check(read.sets == setCount && read.faces == faces.size && read.printings.toLong() == printings) {
            "Catalog incomplete: header says ${read.sets}/${read.faces}/${read.printings}, read $setCount/${faces.size}/$printings"
        }
        return (if (sets.isEmpty() && batch.isEmpty()) null else take()) to
            CatalogMetadata(read.mtgjsonDate, read.mtgjsonVersion, checksum, printings)
    }

    private fun take(): CatalogWrites = CatalogWrites(sets, batch, supported).also {
        sets = ArrayList(); batch = ArrayList(BATCH); supported = ArrayList(BATCH)
    }

    private companion object { const val BATCH = 2000 }
}

/** Puts a catalog's records, in file order, into a card records generation ([index]), read with a [CatalogRecordReader]
 * (on this thread with [add], or on another one handing over [write]). [readBack] (tests): the index reads its search key
 * and face name back from each record, as [CatalogIndex.addBatch] does, instead of taking them from the face. */
internal class CatalogRecordIndexer(val index: CatalogIndex, private val readBack: Boolean = false) {
    private val reader = CatalogRecordReader(index.times)

    fun add(record: CatalogRecord) { reader.add(record)?.let(::write) }

    fun finish(checksum: String): CatalogMetadata = reader.finish(checksum).let { (rest, metadata) -> rest?.let(::write); metadata }

    fun write(writes: CatalogWrites) {
        writes.sets.forEach { index.addSet(it.code, it.released, it.name, it.type, it.draftable, it.automatum, it.unsupported, it.keyrune) }
        if (writes.printings.isEmpty()) return
        if (readBack) index.addBatch(writes.printings.map { it.record }, writes.printings.map { it.trait }) else index.addPrintings(writes.printings)
        index.markSupported(writes.supported)
    }
}

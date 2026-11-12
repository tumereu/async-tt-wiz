package org.asyncttwiz.storage

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.yield
import org.asyncttwiz.catalog.CatalogVersion
import org.asyncttwiz.protocol.CardSearchCard
import org.asyncttwiz.protocol.CardSearchParse
import org.asyncttwiz.protocol.CardSearchQuery
import org.asyncttwiz.protocol.DefinitionFace

// The card records in the browser (docs/specs/web-client.md §3.5, §3.6, §9.3): the server's card catalog downloaded whole,
// checked against the checksum the app expects (as Android does), kept in the browser's storage as the file it is (gzip
// lines, IndexedDB) and read into memory ([MemoryCardCatalog]) on each start. The browser's sides (fetch, IndexedDB,
// DecompressionStream, SHA-256) are app-ui's wasm side; F is the platform's handle of a catalog file.

/** The ATW server's card catalog as the browser reaches it (open without sign-in, LOB-091). */
interface CatalogDownloads<F> {
    /** The server's current catalog (`GET /v1/catalog`). */
    suspend fun current(): CatalogVersion
    /** Catalog [checksum]'s file (`GET /v1/catalog/{checksum}`), with the bytes so far. */
    suspend fun download(checksum: String, progress: (Long) -> Unit): F
}

/** Where the browser keeps the installed catalog file (IndexedDB): one file, named by its checksum. */
interface CatalogFiles<F> {
    /** The installed file's checksum, null without one. */
    suspend fun installed(): String?
    /** The file of [checksum], null when it isn't kept. */
    suspend fun read(checksum: String): F?
    /** Keeps [file] as catalog [checksum], the installed one, and drops any other, all or none. */
    suspend fun install(checksum: String, file: F)
    /** Drops whatever is kept. */
    suspend fun clear()
}

/** A catalog file unpacked: the SHA-256 of its lines ([checksum], the catalog's version), how long they are ([size],
 * in bytes) and how far [next] has read them ([read]). */
interface UnpackedCatalog {
    val checksum: String
    val size: Long
    val read: Long
    /** The next part of the lines' text, null after the last. */
    suspend fun next(): String?
}

/** The browser's card records as the app reads them: the installed catalog in memory once loaded ([current]), nothing
 * before (as Android without card records). */
class BrowserCardRecords : CardCatalog, CatalogSnapshots {
    var current: MemoryCardCatalog? = null
        internal set

    override fun metadata(): CatalogMetadata? = current?.metadata()
    override fun draftableSets(): List<DraftableSet> = current?.draftableSets().orEmpty()
    override fun unsupported(printingIds: Collection<String>): Set<String>? = current?.unsupported(printingIds)
    override fun unsupportedPictures(scryfallIds: Collection<String>): Set<String>? = current?.unsupportedPictures(scryfallIds)
    override fun cards(printingIds: Collection<String>): Map<String, List<CatalogPrinting>> = current?.cards(printingIds).orEmpty()
    override fun tokenArt(face: DefinitionFace, sourceName: String?, sourcePrintingId: String?): CatalogPrinting? =
        current?.tokenArt(face, sourceName, sourcePrintingId)
    override fun emblemArt(text: String): String? = current?.emblemArt(text)
    override fun avatarChoices(query: String, previous: List<CatalogAvatar>): List<CatalogAvatar> =
        current?.avatarChoices(query, previous).orEmpty()
    override fun tokenGeneration(): String? = current?.tokenGeneration()
    override fun searchCards(query: String, cursor: CatalogCardCursor?, limit: Int, budget: Int): CatalogCardSearch {
        current?.let { return it.searchCards(query, cursor, limit, budget) }
        require(limit in 1..200 && budget in 1..100_000)
        val parsed = CardSearchQuery.parse(query)
        if (parsed is CardSearchParse.Invalid) return CatalogCardSearch.Invalid(parsed.error)
        throw CatalogNotInstalledException()
    }
    override fun searchData(printings: Collection<CatalogPrinting>): Map<String, CardSearchCard> =
        current?.searchData(printings).orEmpty()
    override fun <T> lookups(read: (CatalogLookups?) -> T): T = read(current)
}

/**
 * Makes the browser's card records the catalog the app expects ([run]): the kept file when it is that one, else a download
 * from the server, checked against the expected checksum before it is kept or read. A failed attempt is tried once more by
 * itself (LOB-091), as on Android; the controller then says so with Retry. Public data only: no account or session.
 */
class BrowserCatalogInstaller<F>(
    private val records: BrowserCardRecords,
    private val downloads: CatalogDownloads<F>,
    private val files: CatalogFiles<F>,
    private val unpack: suspend (F) -> UnpackedCatalog,
    private val log: (String) -> Unit = {},
    private val retryPause: Long = 2_000,
    private val pause: suspend () -> Unit = { yield() },
) {
    /**
     * Makes the card records catalog [expected] (the app's built-in checksum; null in local and test builds: the server's
     * current one) and returns their metadata; [progress] hears how far it is. Throws when it couldn't.
     */
    suspend fun run(expected: String?, progress: (CatalogProgress) -> Unit = {}): CatalogMetadata {
        expected?.let { require(validChecksum(it)) { "Not a catalog checksum" } }
        val installed = try { files.installed() } catch (failure: Exception) {
            if (failure is CancellationException) throw failure
            log("Card records: the kept catalog couldn't be read (${failure.message}); downloading again")
            null
        }
        records.current?.takeIf { it.generation == installed && (expected == null || expected == installed) }?.let {
            return it.metadata()
        }
        val wanted = expected ?: try { downloads.current().checksum } catch (failure: Exception) {
            if (failure is CancellationException) throw failure
            if (installed != null) {
                log("Card records: the server's card catalog couldn't be read (${failure.message}); keeping the kept $installed")
                load(installed, progress)?.let { return it }
            }
            null
        }
        if (wanted != null && wanted == installed) {
            load(installed, progress)?.let {
                log("Card records are the app's catalog $wanted (${it.records} printings)")
                return it
            }
        }
        log("Card records ${installed ?: "not kept"}; installing ${wanted ?: "the server's current catalog"}")
        return try { install(wanted, progress) } catch (failure: Exception) {
            if (failure is CancellationException) throw failure
            log("Card records download failed (${failure.message}); trying once more")
            delay(retryPause)
            install(wanted, progress)
        }
    }

    /** The kept file of [checksum] read into the card records, or null (and the kept file dropped) when it isn't that
     * catalog any more. */
    private suspend fun load(checksum: String, progress: (CatalogProgress) -> Unit): CatalogMetadata? = try {
        val file = files.read(checksum) ?: error("The kept catalog file is missing")
        read(file, checksum, progress).also { records.current = it }.metadata()
    } catch (failure: Exception) {
        if (failure is CancellationException) throw failure
        log("Card records: the kept catalog $checksum couldn't be read (${failure.message}); dropping it")
        try { files.clear() } catch (cleared: Exception) {
            if (cleared is CancellationException) throw cleared
            log("Card records: the kept catalog couldn't be dropped (${cleared.message})")
        }
        null
    }

    private suspend fun install(expected: String?, progress: (CatalogProgress) -> Unit): CatalogMetadata {
        val current = try { downloads.current() } catch (failure: Exception) {
            if (failure is CancellationException || expected == null) throw failure
            log("Card records: the server's current version couldn't be read (${failure.message}); downloading $expected")
            null
        }
        val checksum = expected ?: current?.checksum ?: throw IllegalStateException("The server's current catalog is unknown")
        require(validChecksum(checksum)) { "Not a catalog checksum" }
        val size = current?.takeIf { it.checksum == checksum }?.size
        if (current != null && current.checksum != checksum) log("Card records: the server's current is ${current.checksum}, this app needs $checksum")
        log("Card records: downloading $checksum (${size ?: "unknown"} bytes)")
        progress(CatalogProgress(CatalogPhase.DOWNLOADING, 0, size))
        val file = downloads.download(checksum) { bytes -> progress(CatalogProgress(CatalogPhase.DOWNLOADING, bytes, size)) }
        val catalog = read(file, checksum, progress)
        files.install(checksum, file)
        records.current = catalog
        log("Card records $checksum installed: ${catalog.metadata().records} printings")
        return catalog.metadata()
    }

    /** [file] read whole into card records, checked to be catalog [checksum] before any of it is read. */
    private suspend fun read(file: F, checksum: String, progress: (CatalogProgress) -> Unit): MemoryCardCatalog {
        val unpacked = unpack(file)
        check(unpacked.checksum == checksum) { "Card catalog checksum mismatch: asked for $checksum, read ${unpacked.checksum}" }
        progress(CatalogProgress(CatalogPhase.INDEXING, 0, unpacked.size))
        val builder = MemoryCardCatalogBuilder()
        val carry = StringBuilder()
        var lines = 0
        while (true) {
            val part = unpacked.next() ?: break
            var from = 0
            while (true) {
                val end = part.indexOf('\n', from)
                if (end < 0) { carry.append(part, from, part.length); break }
                if (carry.isEmpty()) builder.add(part.substring(from, end))
                else { carry.append(part, from, end); builder.add(carry.toString()); carry.clear() }
                from = end + 1
                if (++lines % 2_000 == 0) pause()
            }
            progress(CatalogProgress(CatalogPhase.INDEXING, unpacked.read, unpacked.size))
        }
        if (carry.isNotEmpty()) builder.add(carry.toString())
        return builder.finish(checksum, pause) { done, all -> progress(CatalogProgress(CatalogPhase.FINISHING, done, all)) }
    }

    companion object {
        private val checksumPattern = Regex("[0-9a-f]{64}")
        /** A catalog version: a SHA-256 in lower-case hexadecimal. */
        fun validChecksum(checksum: String): Boolean = checksumPattern.matches(checksum)
    }
}

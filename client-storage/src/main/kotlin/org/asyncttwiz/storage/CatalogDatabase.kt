package org.asyncttwiz.storage

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import java.io.File
import java.io.FileOutputStream
import java.util.Locale
import java.util.UUID

data class CatalogMetadata(val date: String, val version: String, val checksum: String = "", val records: Long = 0)

/** Raw provider JSON retains exact display strings, optional defaults and explicit face identities. */
data class CatalogPrinting(val id: String, val name: String, val set: String, val number: String,
    val language: String, val side: String, val scryfallId: String?, val token: Boolean,
    val otherFaces: List<String>, val json: String)

/** A page retains every exact face on one installed generation; no later per-face lookup is needed. */
data class CatalogToken(val printing: CatalogPrinting, val faces: List<CatalogPrinting>)
data class CatalogTokenCursor(val generation: String, val query: String, val normalized: String, val id: String)
data class CatalogTokenPage(val generation: String, val metadata: CatalogMetadata,
    val tokens: List<CatalogToken>, val next: CatalogTokenCursor?)
class CatalogNotInstalledException : IllegalStateException("Card records are not installed. Download card records, then retry.")
class CatalogGenerationChangedException : IllegalStateException("Card records changed. Search again.")

/** Public, replaceable catalog only. Never opens or edits the account/accepted-replica database. */
class CatalogRepository(private val directory: File, private val openDriver: (File) -> SqlDriver) {
    init { check(directory.isDirectory || directory.mkdirs()) }

    private fun activeFile(): File? {
        val pointer = File(directory, "current")
        if (!pointer.exists()) return null
        val name = pointer.readText()
        check(Regex("[a-f0-9-]{36}\\.db").matches(name))
        return File(directory, name).also { check(it.isFile) }
    }

    @Synchronized fun metadata(): CatalogMetadata? = activeFile()?.let { file ->
        CatalogIndex(openDriver(file)).use { it.metadata() }
    }

    /** Exact normalized name, optionally exact set/collector number. Ambiguity is returned, not guessed. */
    @Synchronized fun find(name: String, set: String? = null, number: String? = null, limit: Int = 100): List<CatalogPrinting> =
        activeFile()?.let { file -> CatalogIndex(openDriver(file)).use { it.find(name, set, number, limit) } }.orEmpty()

    @Synchronized fun printing(id: String): CatalogPrinting? = activeFile()?.let { file ->
        CatalogIndex(openDriver(file)).use { it.printing(id) }
    }

    /** Worker-only, including pointer IO. Identity is the installed file, not its possibly repeated metadata. */
    @Synchronized fun tokenGeneration(): String? = activeFile()?.name

    /** Literal, case-normalized substring search, bounded keyset pages. Never scans the full catalog per query.
     * Compatibility indexing of pre-search generations is transactional/non-destructive and worker-only.
     */
    @Synchronized fun searchTokens(query: String, cursor: CatalogTokenCursor? = null, limit: Int = 40): CatalogTokenPage {
        require(limit in 1..100)
        val normalized = query.trim().lowercase(Locale.ROOT)
        require(normalized.length <= 512) { "Search is too long." }
        val file = activeFile() ?: throw CatalogNotInstalledException()
        if (cursor != null) {
            if (cursor.generation != file.name) throw CatalogGenerationChangedException()
            require(cursor.query == normalized) { "Page belongs to a different search." }
        }
        return CatalogIndex(openDriver(file)).use { index ->
            index.prepareTokenSearch()
            val found = index.searchTokens(normalized, cursor, limit + 1)
            val visible = found.take(limit)
            val tokens = visible.map { printing ->
                val faces = (printing.otherFaces + printing.id).distinct().map { id ->
                    checkNotNull(index.printing(id)) { "Token face is unavailable." }
                }.sortedWith(compareBy<CatalogPrinting> { it.side }.thenBy { it.id })
                CatalogToken(printing, faces)
            }
            val last = visible.lastOrNull()
            CatalogTokenPage(file.name, index.metadata(), tokens,
                if (found.size > limit && last != null) CatalogTokenCursor(file.name, normalized,
                    last.name.lowercase(Locale.ROOT), last.id) else null)
        }
    }

    /** Keep every line/face of an import on one generation while a refresh waits. */
    @Synchronized internal fun <T> snapshot(read: (CatalogIndex?) -> T): T = activeFile()?.let { file ->
        CatalogIndex(openDriver(file)).use { read(it) }
    } ?: read(null)

    /** Unpublished generations may survive process death; only the atomic pointer is readable. */
    @Synchronized fun stage(block: (CatalogIndex) -> CatalogMetadata): CatalogMetadata {
        val file = File(directory, "${UUID.randomUUID()}.db")
        var published = false
        try {
            val metadata = CatalogIndex(openDriver(file)).use { index ->
                index.create()
                val result = block(index)
                index.validate()
                index.prepareTokenSearch()
                index.finish(result)
                result.copy(records = index.count())
            }
            // SQLite's FULL synchronous commit precedes publication; do not switch an open staging DB.
            val pointer = File(directory, "current.new")
            FileOutputStream(pointer).use { it.write(file.name.toByteArray(Charsets.UTF_8)); it.fd.sync() }
            check(pointer.renameTo(File(directory, "current")))
            published = true
            // Readers and writers share this lock. Reclaim only our old, no-longer-referenced generations.
            directory.listFiles()?.filter { it.name.matches(Regex("[a-f0-9-]{36}\\.db")) && it != file }
                ?.forEach { it.delete() }
            return metadata
        } finally {
            if (!published) file.delete()
        }
    }
}

/** One disk-backed generation. Caller confines it to its catalog worker. Uses SQLDelight drivers. */
class CatalogIndex internal constructor(driver: SqlDriver) : app.cash.sqldelight.TransacterImpl(driver), AutoCloseable {
    internal fun create() {
        sql("PRAGMA synchronous=FULL")
        sql("PRAGMA page_size=4096")
        check(long("PRAGMA max_page_count=1048576") == 1048576L) // Row-returning PRAGMA on Android as well as JDBC
        sql("CREATE TABLE metadata (date TEXT NOT NULL, version TEXT NOT NULL, checksum TEXT NOT NULL, records INTEGER NOT NULL)")
        sql("CREATE TABLE printing (id TEXT NOT NULL PRIMARY KEY, name TEXT NOT NULL, normalized TEXT NOT NULL, set_code TEXT NOT NULL, number TEXT NOT NULL, language TEXT NOT NULL, side TEXT NOT NULL, scryfall TEXT, token INTEGER NOT NULL, json TEXT NOT NULL)")
        sql("CREATE INDEX name_lookup ON printing(normalized, set_code, number)")
        sql("CREATE TABLE face (source TEXT NOT NULL, target TEXT NOT NULL, PRIMARY KEY(source, target))")
        sql("CREATE INDEX face_target ON face(target, source)")
    }

    fun addBatch(records: List<CatalogPrinting>) = transaction {
        records.forEach { record ->
            sql("INSERT INTO printing VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)", listOf(record.id, record.name,
                record.name.lowercase(Locale.ROOT), record.set, record.number, record.language, record.side,
                record.scryfallId, if (record.token) "1" else "0", record.json))
            record.otherFaces.forEach { target -> sql("INSERT INTO face VALUES (?, ?)", listOf(record.id, target)) }
        }
    }

    internal fun validate() {
        check(count() > 0) { "Empty catalog" }
        check(long("SELECT count(*) FROM face f LEFT JOIN printing p ON p.id=f.target WHERE p.id IS NULL OR f.source=f.target") == 0L)
        check(long("SELECT count(*) FROM face f LEFT JOIN face r ON r.source=f.target AND r.target=f.source WHERE r.source IS NULL") == 0L)
        check(long("SELECT count(*) FROM face f JOIN printing a ON a.id=f.source JOIN printing b ON b.id=f.target WHERE a.set_code != b.set_code OR a.side='' OR b.side='' OR a.side=b.side") == 0L)
        check(texts("PRAGMA quick_check") == listOf("ok"))
    }

    internal fun finish(meta: CatalogMetadata) = sql("INSERT INTO metadata VALUES (?, ?, ?, ?)",
        listOf(meta.date, meta.version, meta.checksum, count().toString()))

    fun count(): Long = long("SELECT count(*) FROM printing")
    fun metadata(): CatalogMetadata = driver.executeQuery(null, "SELECT date, version, checksum, records FROM metadata", { cursor ->
        check(cursor.next().value)
        QueryResult.Value(CatalogMetadata(cursor.getString(0)!!, cursor.getString(1)!!, cursor.getString(2)!!, cursor.getLong(3)!!))
    }, 0).value

    fun printing(id: String): CatalogPrinting? = rows("SELECT * FROM printing WHERE id=?", listOf(id)).singleOrNull()
    fun find(name: String, set: String?, number: String?, limit: Int): List<CatalogPrinting> {
        require(limit in 1..1000)
        val args = mutableListOf(name.lowercase(Locale.ROOT))
        var where = "normalized=?"
        if (set != null) { where += " AND set_code=?"; args += set }
        if (number != null) { where += " AND number=?"; args += number }
        return rows("SELECT * FROM printing WHERE $where ORDER BY id LIMIT $limit", args)
    }

    /** Only the separate public generation is extended. A crash rolls back both postings and ready marker.
     * Backfill reads bounded batches, not an in-memory catalog; old printing/face/metadata rows stay intact.
     */
    internal fun prepareTokenSearch() = transaction {
        sql("CREATE TABLE IF NOT EXISTS token_search_ready (version INTEGER NOT NULL PRIMARY KEY)")
        if (long("SELECT count(*) FROM token_search_ready WHERE version=1") == 0L) {
            sql("CREATE INDEX IF NOT EXISTS token_name_lookup ON printing(token, normalized, id)")
            sql("CREATE TABLE IF NOT EXISTS token_search (gram TEXT NOT NULL, normalized TEXT NOT NULL, printing_id TEXT NOT NULL, PRIMARY KEY(gram, normalized, printing_id))")
            var afterName = ""
            var afterId = ""
            while (true) {
                check(!Thread.currentThread().isInterrupted)
                val batch = rows("SELECT * FROM printing WHERE token=1 AND (normalized, id) > (?, ?) ORDER BY normalized, id LIMIT 256",
                    listOf(afterName, afterId))
                if (batch.isEmpty()) break
                batch.forEach { record ->
                    val name = record.name.lowercase(Locale.ROOT)
                    val grams = (1..minOf(3, name.length)).flatMap { size -> name.windowed(size) }.toSet()
                    grams.forEach { gram -> sql("INSERT OR IGNORE INTO token_search VALUES (?, ?, ?)", listOf(gram, name, record.id)) }
                }
                val last = batch.last()
                afterName = last.name.lowercase(Locale.ROOT)
                afterId = last.id
            }
            sql("INSERT INTO token_search_ready VALUES (1)")
        }
    }

    internal fun searchTokens(query: String, cursor: CatalogTokenCursor?, limit: Int): List<CatalogPrinting> {
        val args = mutableListOf<String>()
        val from: String
        var where: String
        if (query.isEmpty()) {
            from = "printing p INDEXED BY token_name_lookup"
            where = "p.token=1"
        } else {
            // One/two-character searches also use postings. '%'/'_' are literal, never LIKE wildcards.
            from = "token_search s JOIN printing p ON p.id=s.printing_id"
            where = "s.gram=? AND instr(p.normalized, ?) > 0"
            args += query.take(3); args += query
        }
        val prefix = if (query.isEmpty()) "p" else "s"
        val id = if (query.isEmpty()) "id" else "printing_id"
        if (cursor != null) {
            where += " AND ($prefix.normalized, $prefix.$id) > (?, ?)"
            args += cursor.normalized; args += cursor.id
        }
        return rows("SELECT p.* FROM $from WHERE $where ORDER BY $prefix.normalized, $prefix.$id LIMIT $limit", args)
    }

    private fun rows(query: String, args: List<String>): List<CatalogPrinting> = driver.executeQuery(null, query, { cursor ->
        val rows = mutableListOf<CatalogPrinting>()
        while (cursor.next().value) {
            val id = cursor.getString(0)!!
            rows += CatalogPrinting(id, cursor.getString(1)!!, cursor.getString(3)!!, cursor.getString(4)!!,
                cursor.getString(5)!!, cursor.getString(6)!!, cursor.getString(7), cursor.getLong(8) == 1L,
                emptyList(), cursor.getString(9)!!)
        }
        QueryResult.Value(rows)
    }, args.size) { args.forEachIndexed { index, value -> bindString(index, value) } }.value.map { record ->
        record.copy(otherFaces = texts("SELECT target FROM face WHERE source=? ORDER BY target", listOf(record.id)))
    }

    private fun sql(query: String, args: List<String?> = emptyList()) {
        driver.execute(null, query, args.size) { args.forEachIndexed { index, value -> bindString(index, value) } }
    }
    private fun long(query: String): Long = driver.executeQuery(null, query, { cursor ->
        check(cursor.next().value); QueryResult.Value(cursor.getLong(0)!!)
    }, 0).value
    private fun texts(query: String, args: List<String> = emptyList()): List<String> = driver.executeQuery(null, query, { cursor ->
        val result = mutableListOf<String>()
        while (cursor.next().value) result += cursor.getString(0)!!
        QueryResult.Value(result)
    }, args.size) { args.forEachIndexed { index, value -> bindString(index, value) } }.value

    override fun close() = driver.close()
}

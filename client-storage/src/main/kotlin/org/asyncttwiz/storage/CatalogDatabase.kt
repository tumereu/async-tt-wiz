package org.asyncttwiz.storage

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import com.google.gson.JsonParser
import kotlinx.serialization.encodeToString
import org.asyncttwiz.protocol.*
import java.io.File
import java.io.FileOutputStream
import java.util.Locale
import java.util.UUID
import java.util.logging.Logger

internal const val CATALOG_INDEX_VERSION = 2
data class CatalogMetadata(val date: String, val version: String, val checksum: String = "", val records: Long = 0,
    val indexVersion: Int = CATALOG_INDEX_VERSION)

data class CatalogSet(val code: String, val releaseDate: String, val installedAt: Long = 0, val checksum: String = "")

/** Minimal provider snapshot retains exact display strings, defaults and explicit face identities. */
data class CatalogPrinting(val id: String, val name: String, val set: String, val number: String,
    val language: String, val side: String, val scryfallId: String?, val token: Boolean,
    val otherFaces: List<String>, val json: String, val releaseDate: String = "")

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
    // Token search and deck import may use separate repository objects for the same public DB.
    // Publication/reclamation must share their read lock, not just this object's monitor.
    private val lock = locks.computeIfAbsent(directory.canonicalPath) { Any() }
    private val tokenCache = tokenCaches.computeIfAbsent(directory.canonicalPath) { TokenCache() }

    private companion object {
        val locks = java.util.concurrent.ConcurrentHashMap<String, Any>()
        class TokenCache { @Volatile var list: CatalogTokenList? = null }
        val tokenCaches = java.util.concurrent.ConcurrentHashMap<String, TokenCache>()
    }

    private fun activeFile(): File? {
        val pointer = File(directory, "current")
        if (!pointer.exists()) return null
        val name = pointer.readText()
        check(Regex("[a-f0-9-]{36}\\.db").matches(name))
        return File(directory, name).also { check(it.isFile) }
    }

    fun metadata(): CatalogMetadata? = synchronized(lock) {
        activeFile()?.let { file -> CatalogIndex(openDriver(file)).use { it.metadata() } }
    }

    fun installedSets(): List<CatalogSet> = synchronized(lock) {
        activeFile()?.let { file -> CatalogIndex(openDriver(file)).use { it.installedSets() } }.orEmpty()
    }

    /** Exact normalized name, optionally exact set/collector number. Ambiguity is returned, not guessed. */
    fun find(name: String, set: String? = null, number: String? = null, limit: Int = 100): List<CatalogPrinting> = synchronized(lock) {
        activeFile()?.let { file -> CatalogIndex(openDriver(file)).use { it.find(name, set, number, limit) } }.orEmpty()
    }

    fun printing(id: String): CatalogPrinting? = synchronized(lock) {
        activeFile()?.let { file -> CatalogIndex(openDriver(file)).use { it.printing(id) } }
    }

    /** Worker-only; query the installed public generation, never account data. */
    fun avatarChoices(query: String = "", previous: List<CatalogAvatar> = emptyList()): List<CatalogAvatar> = synchronized(lock) {
        activeFile()?.let { file -> CatalogIndex(openDriver(file)).use { it.avatarChoices(query, previous) } }.orEmpty()
    }

    /** Worker-only, including pointer IO. Identity is the installed file, not its possibly repeated metadata. */
    fun tokenGeneration(): String? = synchronized(lock) { activeFile()?.name }

    /** Startup/import worker only. Compatibility preparation must never be called by a dialog. */
    fun prepareTokens() = synchronized(lock) {
        val file = activeFile() ?: return@synchronized
        CatalogIndex(openDriver(file)).use { index ->
            index.prepareTokenSearch()
            if (!index.hasTokenChoices()) index.transaction { index.rebuildTokenChoices(index.metadata()) }
        }
    }

    /** Loads a prepared table once per generation; never builds provider data on demand. */
    fun loadTokens(): CatalogTokenList = synchronized(lock) {
        val file = activeFile() ?: throw CatalogNotInstalledException()
        tokenCache.list?.takeIf { it.generation == file.name } ?: CatalogIndex(openDriver(file)).use { index ->
            if (!index.hasTokenChoices()) throw CatalogTokensNotReadyException()
            CatalogTokenList(file.name, index.tokenChoices()).also { tokenCache.list = it }
        }
    }

    /** Provider-printing search for import tooling, not the Create token dialog. Preparation is worker-only. */
    fun searchTokens(query: String, cursor: CatalogTokenCursor? = null, limit: Int = 40): CatalogTokenPage = synchronized(lock) {
        require(limit in 1..100)
        val normalized = query.trim().lowercase(Locale.ROOT)
        require(normalized.length <= 512) { "Search is too long." }
        val file = activeFile() ?: throw CatalogNotInstalledException()
        if (cursor != null) {
            if (cursor.generation != file.name) throw CatalogGenerationChangedException()
            require(cursor.query == normalized) { "Page belongs to a different search." }
        }
        return CatalogIndex(openDriver(file)).use { index ->
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
    internal fun <T> snapshot(read: (CatalogIndex?) -> T): T = synchronized(lock) {
        activeFile()?.let { file -> CatalogIndex(openDriver(file)).use { read(it) } } ?: read(null)
    }

    /** Unpublished generations may survive process death; only the atomic pointer is readable. */
    fun stage(copyInstalled: Boolean = false, finishing: () -> Unit = {},
        block: (CatalogIndex) -> CatalogMetadata): CatalogMetadata = synchronized(lock) {
        val file = File(directory, "${UUID.randomUUID()}.db")
        var published = false
        var tokenChoices: List<CatalogTokenChoice> = emptyList()
        try {
            if (copyInstalled) {
                val active = checkNotNull(activeFile())
                active.copyTo(file)
            }
            val metadata = CatalogIndex(openDriver(file)).use { index ->
                if (!copyInstalled) index.create()
                val result = block(index)
                finishing()
                val start = System.nanoTime()
                var completed = result
                index.transaction {
                    val records = index.validate()
                    index.prepareTokenSearch()
                    index.prepareAvatarChoices(rebuild = true)
                    completed = result.copy(records = records)
                    tokenChoices = index.rebuildTokenChoices(completed)
                    index.finish(completed)
                }
                Logger.getLogger(CatalogRepository::class.java.name).info("Catalog finishing: ${(System.nanoTime() - start) / 1_000_000} ms")
                completed
            }
            // SQLite's FULL synchronous commit precedes publication; do not switch an open staging DB.
            val pointer = File(directory, "current.new")
            FileOutputStream(pointer).use { it.write(file.name.toByteArray(Charsets.UTF_8)); it.fd.sync() }
            check(pointer.renameTo(File(directory, "current")))
            published = true
            tokenCache.list = CatalogTokenList(file.name, tokenChoices)
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
    private var duplicateRecords = 0L
    internal fun create() {
        sql("PRAGMA synchronous=FULL")
        sql("PRAGMA page_size=4096")
        check(long("PRAGMA max_page_count=1048576") == 1048576L) // Row-returning PRAGMA on Android as well as JDBC
        sql("CREATE TABLE metadata (date TEXT NOT NULL, version TEXT NOT NULL, checksum TEXT NOT NULL, records INTEGER NOT NULL)")
        sql("CREATE TABLE printing (id TEXT NOT NULL PRIMARY KEY, name TEXT NOT NULL, normalized TEXT NOT NULL, set_code TEXT NOT NULL, number TEXT NOT NULL, language TEXT NOT NULL, side TEXT NOT NULL, scryfall TEXT, token INTEGER NOT NULL, json TEXT NOT NULL)")
        sql("CREATE INDEX name_lookup ON printing(normalized, set_code, number)")
        sql("CREATE TABLE face (source TEXT NOT NULL, target TEXT NOT NULL, PRIMARY KEY(source, target))")
        sql("CREATE INDEX face_target ON face(target, source)")
        sql("CREATE INDEX set_lookup ON printing(set_code)")
        sql("CREATE TABLE catalog_set (code TEXT NOT NULL PRIMARY KEY, release_date TEXT NOT NULL, installed_at INTEGER NOT NULL, checksum TEXT NOT NULL)")
        createImportNames()
        sql("PRAGMA user_version=$CATALOG_INDEX_VERSION")
    }

    internal fun addSet(code: String, releaseDate: String, checksum: String = "") =
        sql("INSERT INTO catalog_set VALUES (?, ?, ?, ?)", listOf(code, releaseDate, System.currentTimeMillis().toString(), checksum))

    internal fun installedSets(): List<CatalogSet> {
        if (long("PRAGMA user_version") < CATALOG_INDEX_VERSION) return emptyList()
        return driver.executeQuery(null, "SELECT code, release_date, installed_at, checksum FROM catalog_set", { cursor ->
            val sets = mutableListOf<CatalogSet>()
            while (cursor.next().value) sets += CatalogSet(cursor.getString(0)!!, cursor.getString(1)!!,
                cursor.getLong(2)!!, cursor.getString(3)!!)
            QueryResult.Value(sets)
        }, 0).value
    }

    /** Only used on an unpublished copy. Other sets, including cross-set broken links, survive until validation. */
    internal fun removeSet(code: String) = transaction {
        val ids = "SELECT id FROM printing WHERE set_code=?"
        sql("DELETE FROM import_name WHERE printing_id IN ($ids)", listOf(code))
        sql("DELETE FROM face WHERE source IN ($ids)", listOf(code))
        if (long("SELECT EXISTS(SELECT 1 FROM sqlite_master WHERE name='token_search')") != 0L) {
            sql("DELETE FROM token_search WHERE printing_id IN ($ids)", listOf(code))
            sql("DELETE FROM token_search_ready")
        }
        sql("DELETE FROM printing WHERE set_code=?", listOf(code))
        sql("DELETE FROM catalog_set WHERE code=?", listOf(code))
    }

    fun addBatch(records: List<CatalogPrinting>) = transaction {
        records.forEach { record ->
            sql("INSERT OR IGNORE INTO printing VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)", listOf(record.id, record.name,
                record.name.lowercase(Locale.ROOT), record.set, record.number, record.language, record.side,
                record.scryfallId, if (record.token) "1" else "0", record.json))
            if (long("SELECT changes()") == 0L) { duplicateRecords++; return@forEach }
            record.otherFaces.forEach { target -> sql("INSERT INTO face VALUES (?, ?)", listOf(record.id, target)) }
            addImportNames(record)
        }
    }

    internal fun validate(): Long {
        // Unsupported/missing faces quarantine the whole connected printing, not the catalog.
        // Work stays on disk, including propagation when a malformed provider group has many links.
        transaction {
            sql("CREATE TABLE unsupported_printing (id TEXT NOT NULL PRIMARY KEY)")
            sql("""INSERT OR IGNORE INTO unsupported_printing
                SELECT f.source FROM face f
                LEFT JOIN printing a ON a.id=f.source LEFT JOIN printing b ON b.id=f.target
                LEFT JOIN face r ON r.source=f.target AND r.target=f.source
                WHERE b.id IS NULL OR f.source=f.target OR r.source IS NULL OR
                    a.set_code != b.set_code OR a.side='' OR b.side='' OR a.side=b.side""")
            do {
                sql("""INSERT OR IGNORE INTO unsupported_printing
                    SELECT f.target FROM face f JOIN unsupported_printing u ON u.id=f.source
                    UNION SELECT f.source FROM face f JOIN unsupported_printing u ON u.id=f.target""")
            } while (long("SELECT changes()") > 0)
            val quarantined = long("SELECT count(*) FROM printing WHERE id IN (SELECT id FROM unsupported_printing)")
            sql("DELETE FROM import_name WHERE printing_id IN (SELECT id FROM unsupported_printing)")
            sql("DELETE FROM face WHERE source IN (SELECT id FROM unsupported_printing) OR target IN (SELECT id FROM unsupported_printing)")
            sql("DELETE FROM printing WHERE id IN (SELECT id FROM unsupported_printing)")
            sql("DROP TABLE unsupported_printing")
            Logger.getLogger(CatalogIndex::class.java.name).info(
                "Catalog index: skippedDuplicates=$duplicateRecords quarantinedFaces=$quarantined")
        }
        val records = count()
        check(records > 0) { "Empty catalog" }
        // One short-circuit integrity query replaces three full edge scans. Check both endpoints too.
        check(long("""SELECT EXISTS(SELECT 1 FROM face f
            LEFT JOIN printing a ON a.id=f.source LEFT JOIN printing b ON b.id=f.target
            LEFT JOIN face r ON r.source=f.target AND r.target=f.source
            WHERE a.id IS NULL OR b.id IS NULL OR f.source=f.target OR r.source IS NULL OR
                a.set_code != b.set_code OR a.side='' OR b.side='' OR a.side=b.side)""") == 0L)
        check(texts("PRAGMA quick_check") == listOf("ok"))
        return records
    }

    internal fun finish(meta: CatalogMetadata) = transaction {
        sql("DELETE FROM metadata")
        sql("INSERT INTO metadata VALUES (?, ?, ?, ?)", listOf(meta.date, meta.version, meta.checksum, meta.records.toString()))
    }

    internal fun hasTokenChoices(): Boolean = long("SELECT EXISTS(SELECT 1 FROM sqlite_master WHERE name='token_choice')") != 0L

    /** Rebuilt on the unpublished generation, in the transaction that commits its metadata. */
    internal fun rebuildTokenChoices(meta: CatalogMetadata): List<CatalogTokenChoice> {
        sql("DROP TABLE IF EXISTS token_choice")
        sql("CREATE TABLE token_choice (gameplay_key TEXT NOT NULL PRIMARY KEY, identity TEXT NOT NULL, name TEXT NOT NULL, detail TEXT NOT NULL, search_names TEXT NOT NULL, definition_json TEXT NOT NULL, digest TEXT NOT NULL)")
        sql("CREATE INDEX IF NOT EXISTS token_name_lookup ON printing(token, normalized, id)")
        data class Candidate(val choice: CatalogTokenChoice, val artwork: Boolean, val release: String, val id: String)
        val distinct = mutableMapOf<String, Candidate>()
        var afterName = ""
        var afterId = ""
        while (true) {
            check(!Thread.currentThread().isInterrupted)
            val batch = rows("SELECT * FROM printing WHERE token=1 AND (normalized,id) > (?,?) ORDER BY normalized,id LIMIT 256", listOf(afterName, afterId))
            if (batch.isEmpty()) break
            for (printing in batch) {
                val faces = (printing.otherFaces + printing.id).distinct().map { checkNotNull(printing(it)) }
                    .sortedWith(compareBy<CatalogPrinting> { it.side }.thenBy { it.id })
                if (printing.id != faces.first().id) continue
                val definition = ResolvedDeckEntry(DeckSection.MAINBOARD, 1, faces, emptyList()).definition(meta)
                val submitted = SubmittedDefinition(DeckJson.digest(definition), definition)
                if (!TokenLinkRules.validDefinition(submitted)) continue
                val candidate = Candidate(CatalogTokenChoice.from(submitted), definition.faces.any {
                    !it.scryfallId.isNullOrBlank() || !it.imageUri.isNullOrBlank()
                }, printing.releaseDate, printing.id)
                val key = tokenGameplayIdentity(definition)
                val previous = distinct[key]
                if (previous == null || compareValuesBy(candidate, previous, { it.artwork }, { it.release }, { it.id }) > 0) distinct[key] = candidate
            }
            afterName = batch.last().name.lowercase(Locale.ROOT); afterId = batch.last().id
        }
        for ((key, candidate) in distinct) {
            val choice = candidate.choice
            sql("INSERT INTO token_choice VALUES (?,?,?,?,?,?,?)", listOf(key, choice.identity, choice.name, choice.detail,
                DeckJson.format.encodeToString(choice.searchNames), DeckJson.format.encodeToString(choice.submitted.definition), choice.submitted.digest))
        }
        return distinct.values.map { it.choice }.sortedWith(compareBy({ it.name.lowercase(Locale.ROOT) }, { it.identity }))
    }

    internal fun tokenChoices(): List<CatalogTokenChoice> = driver.executeQuery(null,
        "SELECT identity,name,detail,search_names,definition_json,digest FROM token_choice ORDER BY lower(name),identity", { cursor ->
            val choices = mutableListOf<CatalogTokenChoice>()
            while (cursor.next().value) choices += CatalogTokenChoice(
                SubmittedDefinition(cursor.getString(5)!!, DeckJson.format.decodeFromString<CardDefinition>(cursor.getString(4)!!)),
                cursor.getString(0)!!, cursor.getString(1)!!, cursor.getString(2)!!,
                DeckJson.format.decodeFromString<List<String>>(cursor.getString(3)!!))
            QueryResult.Value(choices)
        }, 0).value

    fun count(): Long = long("SELECT count(*) FROM printing")
    fun metadata(): CatalogMetadata = driver.executeQuery(null, "SELECT date, version, checksum, records FROM metadata", { cursor ->
        check(cursor.next().value)
        QueryResult.Value(CatalogMetadata(cursor.getString(0)!!, cursor.getString(1)!!, cursor.getString(2)!!, cursor.getLong(3)!!))
    }, 0).value.copy(indexVersion = long("PRAGMA user_version").toInt())

    fun printing(id: String): CatalogPrinting? = rows("SELECT * FROM printing WHERE id=?", listOf(id)).singleOrNull()
    private var scryfallLookupPrepared = false
    /** Cube Cobra supplies Scryfall printing identities, not MTGJSON UUIDs. Prepare on the import worker
     * so already installed catalogs need neither a format bump nor a fresh download. */
    fun findScryfall(id: String): List<CatalogPrinting> {
        if (!scryfallLookupPrepared) {
            sql("CREATE INDEX IF NOT EXISTS printing_scryfall_lookup ON printing(scryfall)")
            scryfallLookupPrepared = true
        }
        return rows("SELECT * FROM printing WHERE scryfall=? ORDER BY id", listOf(id.lowercase(Locale.ROOT)))
    }

    internal fun avatarChoices(query: String, previous: List<CatalogAvatar>): List<CatalogAvatar> {
        if (query.isNotBlank()) return rows("SELECT * FROM printing WHERE id IN (SELECT min(id) FROM printing WHERE scryfall IS NOT NULL AND instr(normalized, ?) > 0 GROUP BY normalized ORDER BY normalized LIMIT 60) ORDER BY normalized,id",
            listOf(query.trim().lowercase(Locale.ROOT).take(512)), enrich = false)
            .mapNotNull { it.avatarChoice(false) }.distinctBy { it.name }
        prepareAvatarChoices()
        val names = previous.take(6).map { it.name.lowercase(Locale.ROOT) }
        val repeats = if (names.isEmpty()) "" else "a.normalized IN (${names.joinToString { "?" }}), "
        // Randomize only the small, deduplicated eligibility index, then fetch six retained records.
        return rows("SELECT p.* FROM printing p JOIN (SELECT printing_id FROM avatar_choice a ORDER BY ${repeats}random() LIMIT 6) chosen ON p.id=chosen.printing_id",
            names, enrich = false).mapNotNull { it.avatarChoice(true) }
    }

    /** Backfills existing installations once; refreshes rebuild on the unpublished generation. */
    internal fun prepareAvatarChoices(rebuild: Boolean = false) = transaction {
        if (rebuild) sql("DROP TABLE IF EXISTS avatar_choice")
        if (long("SELECT EXISTS(SELECT 1 FROM sqlite_master WHERE name='avatar_choice')") != 0L) return@transaction
        sql("CREATE TABLE avatar_choice (normalized TEXT NOT NULL PRIMARY KEY, printing_id TEXT NOT NULL)")
        var after = ""
        while (true) {
            check(!Thread.currentThread().isInterrupted)
            // This is only a coarse prefilter; eligibility uses the retained type line, not rules text.
            val batch = rows("SELECT * FROM printing WHERE id>? AND token=0 AND scryfall IS NOT NULL AND json LIKE '%Legendary%' AND (json LIKE '%Creature%' OR json LIKE '%Planeswalker%') ORDER BY id LIMIT 256",
                listOf(after), enrich = false)
            if (batch.isEmpty()) break
            batch.filter { it.avatarChoice(true) != null }.forEach {
                sql("INSERT OR IGNORE INTO avatar_choice VALUES (?, ?)", listOf(it.name.lowercase(Locale.ROOT), it.id))
            }
            after = batch.last().id
        }
    }
    fun find(name: String, set: String?, number: String?, limit: Int, afterId: String? = null): List<CatalogPrinting> {
        require(limit in 1..1000)
        prepareImportNames()
        val args = mutableListOf(name.lowercase(Locale.ROOT))
        var where = "n.normalized=?"
        if (set != null) { where += " AND p.set_code=?"; args += set }
        if (number != null) { where += " AND p.number=?"; args += number }
        if (afterId != null) { where += " AND p.id>?"; args += afterId }
        return rows("SELECT p.* FROM import_name n JOIN printing p ON p.id=n.printing_id WHERE $where ORDER BY p.id LIMIT $limit", args)
    }

    private fun createImportNames() = sql("CREATE TABLE import_name (normalized TEXT NOT NULL, printing_id TEXT NOT NULL, PRIMARY KEY(normalized, printing_id))")

    private fun addImportNames(record: CatalogPrinting) {
        val faceName = JsonParser.parseString(record.json).asJsonObject.get("faceName")?.takeUnless { it.isJsonNull }?.asString
        listOfNotNull(record.name, faceName?.takeIf { it.isNotEmpty() }).map { it.lowercase(Locale.ROOT) }.distinct().forEach {
            sql("INSERT INTO import_name VALUES (?, ?)", listOf(it, record.id))
        }
    }

    /** Old generations retain every record; aliases can be backfilled without the original download. */
    private fun prepareImportNames() {
        if (long("SELECT count(*) FROM sqlite_master WHERE type='table' AND name='import_name'") != 0L) return
        transaction {
            createImportNames()
            var after = ""
            while (true) {
                check(!Thread.currentThread().isInterrupted)
                val batch = rows("SELECT * FROM printing WHERE id>? ORDER BY id LIMIT 256", listOf(after))
                if (batch.isEmpty()) break
                batch.forEach(::addImportNames)
                after = batch.last().id
            }
        }
    }

    private val releaseDates: Map<String, String> by lazy {
        if (long("SELECT count(*) FROM sqlite_master WHERE type='table' AND name='catalog_set'") == 0L) emptyMap()
        else driver.executeQuery(null, "SELECT code, release_date FROM catalog_set", { cursor ->
            val dates = mutableMapOf<String, String>()
            while (cursor.next().value) dates[cursor.getString(0)!!] = cursor.getString(1)!!
            QueryResult.Value(dates)
        }, 0).value
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

    private fun rows(query: String, args: List<String>, enrich: Boolean = true): List<CatalogPrinting> = driver.executeQuery(null, query, { cursor ->
        val rows = mutableListOf<CatalogPrinting>()
        while (cursor.next().value) {
            val id = cursor.getString(0)!!
            rows += CatalogPrinting(id, cursor.getString(1)!!, cursor.getString(3)!!, cursor.getString(4)!!,
                cursor.getString(5)!!, cursor.getString(6)!!, cursor.getString(7), cursor.getLong(8) == 1L,
                emptyList(), cursor.getString(9)!!)
        }
        QueryResult.Value(rows)
    }, args.size) { args.forEachIndexed { index, value -> bindString(index, value) } }.value.map { record ->
        if (!enrich) record else record.copy(otherFaces = texts("SELECT target FROM face WHERE source=? ORDER BY target", listOf(record.id)),
            releaseDate = releaseDates[record.set].orEmpty())
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

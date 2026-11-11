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

// The card records' format, kept as the file's user_version. Card records are always the app's built-in card catalog
// (docs/specs/web-client.md section 3.5): a new app with a new format comes with a new catalog checksum, so the records
// are downloaded and indexed again anyway.
internal const val CATALOG_INDEX_VERSION = 6
// CatalogMetadata and CatalogPrinting are in :catalog (CatalogRecords.kt).

/** Public, replaceable catalog only. Never opens or edits the account/accepted-replica database. */
class CatalogRepository(private val directory: File, private val openDriver: (File) -> SqlDriver) : CardCatalog {
    init { check(directory.isDirectory || directory.mkdirs()) }
    // Workers (deck import, card search) may use separate repository objects for the same public DB.
    // Publication/reclamation must share their read lock, not just this object's monitor.
    private val lock = locks.computeIfAbsent(directory.canonicalPath) { Any() }

    private companion object {
        val locks = java.util.concurrent.ConcurrentHashMap<String, Any>()
    }

    private fun activeFile(): File? {
        val pointer = File(directory, "current")
        if (!pointer.exists()) return null
        val name = pointer.readText()
        check(Regex("[a-f0-9-]{36}\\.db").matches(name))
        return File(directory, name).also { check(it.isFile) }
    }

    override fun metadata(): CatalogMetadata? = synchronized(lock) {
        activeFile()?.let { file -> CatalogIndex(openDriver(file)).use { it.metadata() } }
    }

    /** Worker-only: the draft sets (DRF-088) with their support marks, newest first. */
    override fun draftableSets(): List<DraftableSet> = snapshot { it?.draftableSets().orEmpty() }

    /** Worker-only (LOB-115): which of [printingIds] the card catalog doesn't mark as supported (section 3.8); null
     * without card records. A printing the records don't have is unsupported. */
    override fun unsupported(printingIds: Collection<String>): Set<String>? = snapshot { it?.unsupported(printingIds) }

    /** Worker-only (section 3.9): which of the pictures [scryfallIds] are of no printing the card catalog marks as
     * supported; null without card records. */
    override fun unsupportedPictures(scryfallIds: Collection<String>): Set<String>? = snapshot { it?.unsupportedPictures(scryfallIds) }

    /** Exact normalized name, optionally exact set/collector number. Ambiguity is returned, not guessed. */
    fun find(name: String, set: String? = null, number: String? = null, limit: Int = 100): List<CatalogPrinting> = synchronized(lock) {
        activeFile()?.let { file -> CatalogIndex(openDriver(file)).use { it.find(name, set, number, limit) } }.orEmpty()
    }

    fun printing(id: String): CatalogPrinting? = synchronized(lock) {
        activeFile()?.let { file -> CatalogIndex(openDriver(file)).use { it.printing(id) } }
    }

    /** Owner 2026-11-10 (card data from the server's catalog): the cards of [printingIds] as saved decks and cubes hold
     * them, each the faces of the card the printing is a face of, front first ([CatalogIndex.cards]); printings the
     * records don't have are left out, and nothing without card records. Worker-only. */
    override fun cards(printingIds: Collection<String>): Map<String, List<CatalogPrinting>> = synchronized(lock) {
        activeFile()?.let { file -> CatalogIndex(openDriver(file)).use { it.cards(printingIds) } }.orEmpty()
    }

    /** The token printing a token Automatum makes is drawn with ([TokenArtMatch]), null for the plain frame or
     * without card records. Worker-only. */
    override fun tokenArt(face: DefinitionFace, sourceName: String?, sourcePrintingId: String?): CatalogPrinting? = synchronized(lock) {
        activeFile()?.let { file -> CatalogIndex(openDriver(file)).use { it.tokenArt(face, sourceName, sourcePrintingId) } }
    }

    /** TRN-053 (owner gallery review 2026-11-06): the scryfall id of the picture of the emblem printing whose rules text
     * is [text] ([EmblemArt.same]), null when no emblem printing has it or without card records. Worker-only. */
    override fun emblemArt(text: String): String? = synchronized(lock) {
        activeFile()?.let { file -> CatalogIndex(openDriver(file)).use { it.emblemArt(text) } }
    }

    /** Worker-only; query the installed public generation, never account data. */
    override fun avatarChoices(query: String, previous: List<CatalogAvatar>): List<CatalogAvatar> = synchronized(lock) {
        activeFile()?.let { file -> CatalogIndex(openDriver(file)).use { it.avatarChoices(query, previous) } }.orEmpty()
    }

    /** Worker-only, including pointer IO. Identity is the installed file, not its possibly repeated metadata. */
    override fun tokenGeneration(): String? = synchronized(lock) { activeFile()?.name }

    /**
     * Worker-only Scryfall-style card search (CardSearchQuery syntax) over the installed generation: distinct cards
     * sorted by name, each shown with its preferred matching printing. At most [budget] cards are evaluated per
     * call; continue with [CatalogCardSearch.Page.next]. Malformed queries return [CatalogCardSearch.Invalid].
     */
    override fun searchCards(query: String, cursor: CatalogCardCursor?, limit: Int, budget: Int): CatalogCardSearch =
        synchronized(lock) { searchCatalogCards(activeFile(), openDriver, query, cursor, limit, budget) }

    /**
     * Worker-only: the given printings' cards as search knows them, by printing id, with what card records don't carry
     * (colour identity, format legalities, printings and rarities). Printings without such a card are left out.
     */
    override fun searchData(printings: Collection<CatalogPrinting>): Map<String, CardSearchCard> =
        synchronized(lock) { activeFile()?.let { file -> CatalogIndex(openDriver(file)).use { it.searchData(printings) } }.orEmpty() }

    /** Keep every line/face of an import on one generation while a refresh waits. */
    internal fun <T> snapshot(read: (CatalogIndex?) -> T): T = synchronized(lock) {
        activeFile()?.let { file -> CatalogIndex(openDriver(file)).use { read(it) } } ?: read(null)
    }

    /**
     * Builds a new generation with [block] (the records, in as many batches as it likes) and publishes it. Unpublished
     * generations may survive process death; only the atomic pointer is readable. [finishing] is told how far finishing
     * is, as printings done of all (the card search goes through them all, the rest is quick); [log] where the time went.
     */
    fun stage(finishing: (done: Long, total: Long) -> Unit = { _, _ -> },
        log: (String) -> Unit = Logger.getLogger(CatalogRepository::class.java.name)::info,
        block: (CatalogIndex) -> CatalogMetadata): CatalogMetadata = synchronized(lock) {
        val file = File(directory, "${UUID.randomUUID()}.db")
        var published = false
        try {
            val times = CatalogPhaseTimes()
            var searchTimes = ""
            val metadata = CatalogIndex(openDriver(file)).use { index ->
                index.create()
                // The records in one transaction, unsynced: the generation is a throwaway until the durable commit below
                val result = times.time("records") { index.transactionWithResult { block(index) } }
                log("Card catalog records (journal ${index.journal()}): ${index.times}")
                index.times.clear()
                var completed = result
                times.time("finishing") {
                    finishing(0, result.records.coerceAtLeast(1))
                    // Still unsynced, as the records
                    index.transaction {
                        times.time("lookups") { index.createLookups() }
                        val records = times.time("validate") { index.validate() }
                        index.createAvatarChoices()
                        times.time("card search") {
                            index.rebuildCardSearch(onPrinting = index::offerAvatarChoice) { done -> finishing(done, records) }
                            index.flushAvatarChoices()
                        }
                        completed = result.copy(records = records)
                        times.time("token lookup") { index.createTokenLookup() }
                    }
                    // The metadata last, with a FULL synchronous commit: syncing the file makes all of it durable
                    times.time("durable commit") {
                        index.durable()
                        index.finish(completed)
                    }
                    finishing(completed.records, completed.records)
                    searchTimes = index.times.toString()
                }
                completed
            }
            // The metadata commit's full sync, of the whole file, precedes publication
            val publishing = System.nanoTime()
            val pointer = File(directory, "current.new")
            FileOutputStream(pointer).use { it.write(file.name.toByteArray(Charsets.UTF_8)); it.fd.sync() }
            check(pointer.renameTo(File(directory, "current")))
            published = true
            // Readers and writers share this lock. Reclaim only our old, no-longer-referenced generations.
            directory.listFiles()?.filter { it.name.matches(Regex("[a-f0-9-]{36}\\.db")) && it != file }
                ?.forEach { it.delete() }
            log("Card catalog install phases: $times, publish ${(System.nanoTime() - publishing) / 1_000_000} ms; card search: $searchTimes")
            return metadata
        } finally {
            if (!published) file.delete()
        }
    }
}

/** One disk-backed generation. Caller confines it to its catalog worker. Uses SQLDelight drivers. */
class CatalogIndex internal constructor(driver: SqlDriver) : app.cash.sqldelight.TransacterImpl(driver), AutoCloseable,
    MtgJsonCatalogSink {
    /** Where the records' time went, for the install's log. */
    internal val times = CatalogPhaseTimes()
    private var duplicateRecords = 0L
    internal fun create() {
        // A new generation is a throwaway until published (a crash restarts the install), so nothing is synced to disk while
        // it is built; [durable] restores full sync for the last commit, which publication waits for.
        sql("PRAGMA synchronous=OFF")
        // Room for the B-trees keyed by printing id and name while records arrive in file order (native memory, not heap)
        sql("PRAGMA cache_size=-32768")
        sql("PRAGMA page_size=4096")
        check(long("PRAGMA max_page_count=1048576") == 1048576L) // Row-returning PRAGMA on Android as well as JDBC
        sql("CREATE TABLE metadata (date TEXT NOT NULL, version TEXT NOT NULL, checksum TEXT NOT NULL, records INTEGER NOT NULL)")
        // Search columns follow json so positional reads of the first ten columns stay valid.
        sql("CREATE TABLE printing (id TEXT NOT NULL PRIMARY KEY, name TEXT NOT NULL, normalized TEXT NOT NULL, set_code TEXT NOT NULL, number TEXT NOT NULL, language TEXT NOT NULL, side TEXT NOT NULL, scryfall TEXT, token INTEGER NOT NULL, json TEXT NOT NULL, " +
            "oracle TEXT NOT NULL DEFAULT '', rarity TEXT NOT NULL DEFAULT '', identity INTEGER, legal INTEGER NOT NULL DEFAULT 0)")
        sql("CREATE TABLE face (source TEXT NOT NULL, target TEXT NOT NULL, PRIMARY KEY(source, target))")
        sql("CREATE TABLE catalog_set (code TEXT NOT NULL PRIMARY KEY, release_date TEXT NOT NULL, name TEXT NOT NULL DEFAULT '', " +
            "type TEXT NOT NULL DEFAULT '', draftable INTEGER NOT NULL DEFAULT 0, automatum INTEGER NOT NULL DEFAULT 0, " +
            "unsupported TEXT NOT NULL DEFAULT '[]', keyrune TEXT NOT NULL DEFAULT '')")
        // The printings of the cards the card catalog marks as supported (section 3.8)
        sql("CREATE TABLE automatum_printing (id TEXT NOT NULL PRIMARY KEY) WITHOUT ROWID")
        createImportNames()
        createTokenRelations()
        sql("PRAGMA user_version=$CATALOG_INDEX_VERSION")
    }

    /** The lookups by name, set and other face, built once the records are in (cheaper than keeping them up per row). */
    internal fun createLookups() {
        sql("CREATE INDEX name_lookup ON printing(normalized, set_code, number)")
        sql("CREATE INDEX face_target ON face(target, source)")
        sql("CREATE INDEX set_lookup ON printing(set_code)")
    }

    /** Full sync again for the last commit, which syncs the whole file before publication (see [create]). Outside a
     * transaction. */
    internal fun durable() = sql("PRAGMA synchronous=FULL")

    /** The journal mode, for the install's log. */
    internal fun journal(): String = texts("PRAGMA journal_mode").singleOrNull().orEmpty()

    override fun addSet(code: String, releaseDate: String, checksum: String) =
        sql("INSERT INTO catalog_set (code, release_date, name) VALUES (?, ?, ?)", listOf(code, releaseDate, code))

    /** A set with its name, kind and marks in one row: what [addSet], [setName], [setType] and [markSet] make together. */
    internal fun addSet(code: String, releaseDate: String, name: String, type: String, draftable: Boolean, automatum: Boolean,
        unsupported: List<String>, keyrune: String = "") = execute("INSERT INTO catalog_set (code, release_date, name, type, draftable, " +
        "automatum, unsupported, keyrune) VALUES (?, ?, ?, ?, ?, ?, ?, ?)", listOf(code, releaseDate, name, type, if (draftable) 1L else 0L,
        if (automatum) 1L else 0L, DeckJson.format.encodeToString(unsupported), keyrune), ADD_SET)

    override fun setName(code: String, name: String) = sql("UPDATE catalog_set SET name=? WHERE code=?", listOf(name, code))

    override fun setType(code: String, type: String) = sql("UPDATE catalog_set SET type=? WHERE code=?", listOf(type, code))

    override fun setKeyrune(code: String, keyrune: String) = sql("UPDATE catalog_set SET keyrune=? WHERE code=?", listOf(keyrune, code))

    /** Set [code]'s marks (section 3.8): a kind of set that is drafted ([draftable], DRF-088), whether Automatum can play
     * every card its packs can hold, and the names of those it can't. */
    internal fun markSet(code: String, draftable: Boolean, automatum: Boolean, unsupported: List<String>) =
        sql("UPDATE catalog_set SET draftable=?, automatum=?, unsupported=? WHERE code=?", listOf(if (draftable) "1" else "0",
            if (automatum) "1" else "0", DeckJson.format.encodeToString(unsupported), code))

    /** Marks [printingIds] as printings of cards Automatum supports (section 3.8). */
    internal fun markSupported(printingIds: Collection<String>) = times.time("supported marks") {
        insertRows("INSERT OR IGNORE INTO automatum_printing", printingIds.map { listOf(it) })
    }

    internal fun unsupported(printingIds: Collection<String>): Set<String> {
        val ids = printingIds.toSet()
        // API29's SQLite allows 999 bound values per statement.
        val supported = ids.chunked(500).flatMap { chunk ->
            texts("SELECT id FROM automatum_printing WHERE id IN (${chunk.joinToString(",") { "?" }})", chunk)
        }.toSet()
        return ids - supported
    }

    internal fun unsupportedPictures(scryfallIds: Collection<String>): Set<String> {
        val ids = scryfallIds.map { it.lowercase(Locale.ROOT) }.toSet()
        sql("CREATE INDEX IF NOT EXISTS printing_scryfall_lookup ON printing(scryfall)")
        val supported = ids.chunked(500).flatMap { chunk ->
            texts("SELECT DISTINCT p.scryfall FROM printing p JOIN automatum_printing a ON a.id=p.id WHERE p.scryfall IN " +
                chunk.joinToString(",", "(", ")") { "?" }, chunk)
        }.toSet()
        return ids - supported
    }

    internal fun draftableSets(): List<DraftableSet> = driver.executeQuery(null,
        """SELECT s.code, s.name, s.release_date, (SELECT count(*) FROM printing p WHERE p.set_code=s.code AND p.token=0),
            s.automatum, s.unsupported, s.keyrune FROM catalog_set s WHERE s.draftable=1 ORDER BY s.release_date DESC, s.code""", { cursor ->
            val sets = mutableListOf<DraftableSet>()
            while (cursor.next().value) sets += DraftableSet(cursor.text(0), cursor.text(1), cursor.text(2),
                cursor.number(3), cursor.getLong(4) == 1L,
                runCatching { DeckJson.format.decodeFromString<List<String>>(cursor.text(5)) }.getOrDefault(emptyList()),
                cursor.getString(6).orEmpty())
            QueryResult.Value(sets)
        }, 0).value

    fun addBatch(records: List<CatalogPrinting>) = addBatch(records, emptyList())

    /** [traits] parallels [records]; missing entries leave the search traits unknown. */
    override fun addBatch(records: List<CatalogPrinting>, traits: List<CatalogPrintingTraits?>) {
        val staged = times.time("record reads") {
            records.mapIndexed { position, record ->
                val json = JsonParser.parseString(record.json).asJsonObject
                StagedPrinting(record, traits.getOrNull(position), cardSearchKey(record, json), faceName(json))
            }
        }
        addPrintings(staged)
    }

    /** A printing to index, with what the index takes from its record: its card's search key ([cardSearchKey]) and its
     * face name (an import name besides the card name; null without one). */
    internal class StagedPrinting(val record: CatalogPrinting, val trait: CatalogPrintingTraits?, val oracle: String,
        val faceName: String?)

    /**
     * Indexes [printings] with a few multi-row statements per table. The first record of an id wins: a later one (in this
     * batch or an earlier one) is skipped whole, its faces, import names and token relations too.
     */
    internal fun addPrintings(printings: List<StagedPrinting>) = transaction {
        val fresh = ArrayList<StagedPrinting>(printings.size)
        times.time("printings") {
            printings.chunked(PRINTINGS_PER_INSERT).forEach { chunk ->
                val inserted = insertRows("INSERT OR IGNORE INTO printing (id, name, normalized, set_code, number, language, side, " +
                    "scryfall, token, json, oracle, rarity, identity, legal)", chunk.map { staged ->
                    val record = staged.record
                    val trait = staged.trait
                    listOf(record.id, record.name, record.name.lowercase(Locale.ROOT), record.set, record.number, record.language,
                        record.side, record.scryfallId, if (record.token) 1L else 0L, record.json, staged.oracle,
                        trait?.rarity.orEmpty(), trait?.identity?.toLong(), trait?.legal ?: 0L)
                })
                if (inserted == chunk.size.toLong()) fresh += chunk
                else {
                    // An id there already, or twice here: the rows added for this chunk are the newest (nothing is deleted
                    // while records arrive), and of an id twice here the first was added
                    val added = texts("SELECT id FROM printing ORDER BY rowid DESC LIMIT $inserted").toHashSet()
                    chunk.filterTo(fresh) { added.remove(it.record.id) }
                }
            }
        }
        duplicateRecords += printings.size - fresh.size
        times.time("faces") {
            insertRows("INSERT INTO face", fresh.flatMap { staged -> staged.record.otherFaces.map { listOf(staged.record.id, it) } })
        }
        times.time("import names") {
            insertRows("INSERT INTO import_name", fresh.flatMap { staged ->
                importNames(staged.record.name, staged.faceName).map { listOf(it, staged.record.id) }
            })
        }
        times.time("token relations") {
            val relations = fresh.flatMap { staged -> staged.trait?.related.orEmpty().map { other ->
                if (staged.record.token) listOf(other, staged.record.id) else listOf(staged.record.id, other)
            } }
            if (relations.isNotEmpty() && !tokenRelationsReady) { createTokenRelations(); tokenRelationsReady = true }
            insertRows("INSERT OR IGNORE INTO token_relation", relations)
        }
    }

    /**
     * Runs "[insert] VALUES (...), (...)" for [rows] in statements of up to about [MAX_SQL] characters, and returns how
     * many rows they added. Text and whole numbers go into the statement as literals: on Android each bound value costs
     * far more than SQLite reading it from the statement. Decimals (exactly as bound), text holding NUL and the smallest
     * Long are bound. A literal stores the same value, type and text as binding it.
     */
    internal fun insertRows(insert: String, rows: List<List<Any?>>): Long {
        var changed = 0L
        val statement = StringBuilder()
        val args = ArrayList<Any?>()
        fun run() {
            if (statement.isEmpty()) return
            changed += execute(statement.toString(), args)
            statement.setLength(0); args.clear()
        }
        for (row in rows) {
            if (args.isNotEmpty() && args.size + row.size > MAX_BOUND) run()
            statement.append(if (statement.isEmpty()) "$insert VALUES (" else ",(")
            row.forEachIndexed { column, value ->
                if (column > 0) statement.append(',')
                when {
                    value == null -> statement.append("NULL")
                    value is String && value.indexOf('\u0000') < 0 -> statement.append('\'').append(
                        if (value.indexOf('\'') < 0) value else value.replace("'", "''")).append('\'')
                    (value is Long && value != Long.MIN_VALUE) || value is Int -> statement.append(value)
                    else -> { statement.append('?'); args += value }
                }
            }
            statement.append(')')
            if (statement.length >= MAX_SQL) run()
        }
        run()
        return changed
    }

    // LOB-125. Without rows for a card, its tokens' pictures come from their characteristics alone.
    private var tokenRelationsReady = false
    /** A card's related tokens: (a card's printing id, a token's printing id) from the card's own links, and (a card
     * name in lower case, a token's printing id) from the token's list of the cards that make it. */
    private fun createTokenRelations() = sql("CREATE TABLE IF NOT EXISTS token_relation (source TEXT NOT NULL, " +
        "token TEXT NOT NULL, PRIMARY KEY(source, token)) WITHOUT ROWID")
    private fun hasTokenRelations() = long("SELECT EXISTS(SELECT 1 FROM sqlite_master WHERE name='token_relation')") != 0L

    /**
     * The token printing a token Automatum makes is drawn with ([TokenArtMatch.best]): among the related tokens of the
     * card that made it ([sourcePrintingId]'s own links and the tokens listing [sourceName]), else among all tokens of
     * the token's name; ties prefer the set of the source's printing.
     */
    internal fun tokenArt(face: DefinitionFace, sourceName: String?, sourcePrintingId: String?): CatalogPrinting? {
        fun dated(rows: List<CatalogPrinting>) = rows.map { it.copy(releaseDate = releaseDates[it.set].orEmpty()) }
        val sources = listOfNotNull(sourcePrintingId, sourceName?.lowercase(Locale.ROOT)).distinct()
        val related = if (sources.isEmpty() || !hasTokenRelations()) emptyList() else dated(rows(
            "SELECT p.* FROM token_relation r JOIN printing p ON p.id=r.token WHERE p.token=1 AND r.source IN " +
                sources.joinToString(",", "(", ")") { "?" }, sources, enrich = false))
        val named = dated(rows("SELECT * FROM printing WHERE token=1 AND normalized=?",
            listOf(TokenArtMatch.name(face.name)), enrich = false))
        val set = sourcePrintingId?.let { printing(it)?.set }
        return TokenArtMatch.best(face, set, related, named)
    }

    /** The newest emblem printing (a token record with layout "emblem") with a picture whose rules text is [text]. */
    internal fun emblemArt(text: String): String? {
        val wanted = EmblemArt.normalized(text).ifEmpty { return null }
        return rows("SELECT * FROM printing WHERE token=1 AND json LIKE ? ORDER BY id", listOf("%\"layout\":\"emblem\"%"), enrich = false)
            .mapNotNull { printing ->
                val json = runCatching { JsonParser.parseString(printing.json).asJsonObject }.getOrNull() ?: return@mapNotNull null
                val printed = json.get("text")?.takeIf { it.isJsonPrimitive }?.asString ?: return@mapNotNull null
                printing.takeIf { printing.scryfallId != null && EmblemArt.normalized(printed) == wanted }
            }.maxByOrNull { releaseDates[it.set].orEmpty() }?.scryfallId
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

    /** The token art lookup's index ([tokenArt]), on the unpublished generation. */
    internal fun createTokenLookup() = sql("CREATE INDEX IF NOT EXISTS token_name_lookup ON printing(token, normalized, id)")

    fun count(): Long = long("SELECT count(*) FROM printing")
    fun metadata(): CatalogMetadata = driver.executeQuery(null, "SELECT date, version, checksum, records FROM metadata", { cursor ->
        check(cursor.next().value)
        QueryResult.Value(CatalogMetadata(cursor.text(0), cursor.text(1), cursor.text(2), cursor.number(3)))
    }, 0).value

    fun printing(id: String): CatalogPrinting? = rows("SELECT * FROM printing WHERE id=?", listOf(id)).singleOrNull()

    /** Each of [printingIds] with the faces of its card (the printing and those it links to), in side then id order as a
     * deck import resolves them; ids the records don't have are left out. A few queries for a whole list. */
    internal fun cards(printingIds: Collection<String>): Map<String, List<CatalogPrinting>> {
        fun read(ids: Collection<String>) = ids.chunked(500).flatMap { chunk ->
            rows("SELECT * FROM printing WHERE id IN (${chunk.joinToString(",") { "?" }})", chunk) }.associateBy { it.id }
        val wanted = printingIds.distinct()
        val first = read(wanted)
        val all = first + read(first.values.flatMap { it.otherFaces }.filter { it !in first }.distinct())
        return wanted.mapNotNull { id -> all[id]?.let { printing ->
            id to (printing.otherFaces + printing.id).distinct().mapNotNull { all[it] }
                .sortedWith(compareBy<CatalogPrinting> { it.side }.thenBy { it.id })
        } }.toMap()
    }
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

    /** Backfills an installation from before the table once; finishing builds it during the card search pass. */
    internal fun prepareAvatarChoices() = transaction {
        if (long("SELECT EXISTS(SELECT 1 FROM sqlite_master WHERE name='avatar_choice')") != 0L) return@transaction
        createAvatarChoices()
        var after = ""
        while (true) {
            check(!Thread.currentThread().isInterrupted)
            val batch = rows("SELECT * FROM printing WHERE id>? AND token=0 AND scryfall IS NOT NULL AND json LIKE '%Legendary%' AND (json LIKE '%Creature%' OR json LIKE '%Planeswalker%') ORDER BY id LIMIT 256",
                listOf(after), enrich = false)
            if (batch.isEmpty()) break
            batch.forEach(::offerAvatarChoice)
            after = batch.last().id
        }
        flushAvatarChoices()
    }

    /** An empty table of random avatar choices, one printing per card name. */
    internal fun createAvatarChoices() {
        sql("DROP TABLE IF EXISTS avatar_choice")
        sql("CREATE TABLE avatar_choice (normalized TEXT NOT NULL PRIMARY KEY, printing_id TEXT NOT NULL)")
    }

    private val avatarOffers = ArrayList<List<String>>()

    /** Adds [printing] to the avatar choices when eligible; the first eligible printing of a name stays. Written in
     * batches: [flushAvatarChoices] after the last. */
    internal fun offerAvatarChoice(printing: CatalogPrinting) {
        // A cheap prefilter before parsing; eligibility uses the retained type line, not rules text.
        if (printing.token || printing.scryfallId == null || "Legendary" !in printing.json) return
        if (printing.avatarChoice(true) == null) return
        avatarOffers += listOf(printing.name.lowercase(Locale.ROOT), printing.id)
        if (avatarOffers.size >= MAX_BOUND / 2) flushAvatarChoices()
    }

    /** Writes the avatar choices offered so far, in the order offered (so the first of a name stays). */
    internal fun flushAvatarChoices() {
        insertRows("INSERT OR IGNORE INTO avatar_choice", avatarOffers)
        avatarOffers.clear()
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
        importNames(record.name, faceName(JsonParser.parseString(record.json).asJsonObject)).forEach {
            sql("INSERT INTO import_name VALUES (?, ?)", listOf(it, record.id))
        }
    }

    /** The names a deck import finds a printing by: its card name and its face name, lower case. */
    private fun importNames(name: String, faceName: String?): List<String> =
        listOfNotNull(name, faceName).map { it.lowercase(Locale.ROOT) }.distinct()

    /** A record's face name, null without one (or an empty one). */
    private fun faceName(json: com.google.gson.JsonObject): String? =
        json.get("faceName")?.takeUnless { it.isJsonNull }?.asString?.takeIf { it.isNotEmpty() }

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
                batch.forEach { addImportNames(it) }
                after = batch.last().id
            }
        }
    }

    internal val releaseDates: Map<String, String> by lazy {
        driver.executeQuery(null, "SELECT code, release_date FROM catalog_set", { cursor ->
            val dates = mutableMapOf<String, String>()
            while (cursor.next().value) dates[cursor.text(0)] = cursor.text(1)
            QueryResult.Value(dates)
        }, 0).value
    }

    internal fun rows(query: String, args: List<String>, enrich: Boolean = true): List<CatalogPrinting> = driver.executeQuery(null, query, { cursor ->
        val rows = mutableListOf<CatalogPrinting>()
        while (cursor.next().value) {
            val id = cursor.text(0)
            rows += CatalogPrinting(id, cursor.text(1), cursor.text(3), cursor.text(4),
                cursor.text(5), cursor.text(6), cursor.getString(7), cursor.getLong(8) == 1L,
                emptyList(), cursor.text(9))
        }
        QueryResult.Value(rows)
    }, args.size) { args.forEachIndexed { index, value -> bindString(index, value) } }.value.map { record ->
        if (!enrich) record else record.copy(otherFaces = texts("SELECT target FROM face WHERE source=? ORDER BY target", listOf(record.id)),
            releaseDate = releaseDates[record.set].orEmpty())
    }

    internal fun sql(query: String, args: List<String?> = emptyList()) {
        driver.execute(null, query, args.size) { args.forEachIndexed { index, value -> bindString(index, value) } }
    }
    internal fun long(query: String): Long = driver.executeQuery(null, query, { cursor ->
        check(cursor.next().value); QueryResult.Value(cursor.number(0))
    }, 0).value
    internal fun texts(query: String, args: List<String> = emptyList()): List<String> = driver.executeQuery(null, query, { cursor ->
        val result = mutableListOf<String>()
        while (cursor.next().value) result += cursor.text(0)
        QueryResult.Value(result)
    }, args.size) { args.forEachIndexed { index, value -> bindString(index, value) } }.value

    /** Typed binding (String, Long, Int, Double or null). A non-null [cached] id reuses the prepared statement;
     * give it only to constant SQL text. */
    internal fun <T> select(query: String, args: List<Any?>, cached: Int? = null, read: (app.cash.sqldelight.db.SqlCursor) -> T): List<T> =
        driver.executeQuery(cached, query, { cursor ->
            val result = mutableListOf<T>()
            while (cursor.next().value) result += read(cursor)
            QueryResult.Value(result)
        }, args.size) { args.forEachIndexed { index, value -> when (value) {
            null -> bindString(index, null)
            is String -> bindString(index, value)
            is Long -> bindLong(index, value)
            is Int -> bindLong(index, value.toLong())
            is Double -> bindDouble(index, value)
            else -> error("Unsupported argument")
        } } }.value

    /** Runs [query] and returns how many rows it changed. */
    internal fun execute(query: String, args: List<Any?>, cached: Int? = null): Long =
        driver.execute(cached, query, args.size) { args.forEachIndexed { index, value -> when (value) {
            null -> bindString(index, null)
            is String -> bindString(index, value)
            is Long -> bindLong(index, value)
            is Int -> bindLong(index, value.toLong())
            is Double -> bindDouble(index, value)
            else -> error("Unsupported argument")
        } } }.value

    override fun close() = driver.close()
}

/** API 29's SQLite allows 999 bound values per statement; [MAX_VALUES] per IN list leaves room for other arguments. */
internal const val MAX_BOUND = 999
internal const val MAX_VALUES = 500

// A prepared-statement cache id for the sets' constant SQL (arbitrary, distinct from SQLDelight's and the search's).
private const val ADD_SET = 0x5EA4B001
/** About how long an insert statement of many rows gets, in characters (SQLite takes up to a million bytes). */
private const val MAX_SQL = 100_000
/** Printings per insert (about 80 KB of statement); how many rows it added tells whether one was there already. */
private const val PRINTINGS_PER_INSERT = 200

/** Wall time per named phase of an install, summed over repeats, listed in the order first timed (the install's log). */
internal class CatalogPhaseTimes {
    private val nanos = LinkedHashMap<String, Long>()
    fun add(phase: String, elapsed: Long) { nanos[phase] = (nanos[phase] ?: 0L) + elapsed }
    fun clear() = nanos.clear()
    fun addAll(other: CatalogPhaseTimes, prefix: String) = other.nanos.forEach { (phase, elapsed) -> add(prefix + phase, elapsed) }
    inline fun <T> time(phase: String, block: () -> T): T {
        val start = System.nanoTime()
        try { return block() } finally { add(phase, System.nanoTime() - start) }
    }
    override fun toString() = nanos.entries.joinToString { "${it.key} ${it.value / 1_000_000} ms" }
}

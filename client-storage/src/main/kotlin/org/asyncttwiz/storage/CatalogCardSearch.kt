package org.asyncttwiz.storage

import app.cash.sqldelight.db.SqlDriver
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.serialization.json.Json
import org.asyncttwiz.protocol.*
import java.io.File
import java.util.Locale

/** Search-only provider data stored beside a printing: lower-case rarity, colour identity mask, legal-format bits
 * (CardSearchRules.FORMATS order, legal or restricted). */
internal data class CatalogPrintingTraits(val rarity: String, val identity: Int?, val legal: Long)

/** One search result: a card (oracle identity) shown with its preferred matching printing; faces front first. */
data class CatalogCard(val key: String, val name: String, val faces: List<CatalogPrinting>) {
    val printing: CatalogPrinting get() = faces.first()
}
data class CatalogCardCursor(val generation: String, val query: String, val nameKey: String, val key: String)

sealed interface CatalogCardSearch {
    /** Cards sorted by name. [examined] counts card rows the matcher evaluated for this page (the work done).
     * A page may hold fewer than the limit and still have [next] when the work budget ran out first. */
    data class Page(val generation: String, val metadata: CatalogMetadata, val cards: List<CatalogCard>,
        val next: CatalogCardCursor?, val examined: Int) : CatalogCardSearch
    /** The query text cannot be searched; [error] says why and where. */
    data class Invalid(val error: CardSearchError) : CatalogCardSearch
}

/** Installed card records predate search; the regular catalog update replaces them. */
class CatalogSearchNotReadyException : IllegalStateException("Card search needs updated card records. Update card records, then retry.")

/** Grouping identity of a printing: Scryfall's oracle id, or the name for records without one. */
internal fun cardSearchKey(record: CatalogPrinting, json: JsonObject): String =
    json.get("identifiers")?.takeIf { it.isJsonObject }?.asJsonObject?.get("scryfallOracleId")
        ?.takeIf { it.isJsonPrimitive }?.asString?.takeIf { it.isNotEmpty() } ?: "name:${record.name.lowercase(Locale.ROOT)}"

private val searchJson = Json { ignoreUnknownKeys = true }
private val NON_CARD_LAYOUTS = setOf("art_series", "token", "double_faced_token", "emblem")

internal fun CatalogIndex.hasCardSearch(): Boolean =
    long("SELECT EXISTS(SELECT 1 FROM sqlite_master WHERE name='search_ready')") != 0L &&
        long("SELECT count(*) FROM search_ready WHERE version=1") == 1L

/**
 * Rebuilt on the unpublished generation in the finishing transaction, from installed printings only: one row per
 * card with its preferred printing (the deck import default) and every printing's set and rarity. Reads bounded
 * batches in oracle order, never the whole catalog.
 */
internal fun CatalogIndex.rebuildCardSearch() {
    listOf("search_ready", "search_card", "search_data", "search_printing").forEach { sql("DROP TABLE IF EXISTS $it") }
    // Filter columns stay narrow so scans are cheap; the matcher's card data is fetched only for prefiltered rows.
    sql("""CREATE TABLE search_card (oracle TEXT NOT NULL PRIMARY KEY, name_key TEXT NOT NULL, printing_id TEXT NOT NULL,
        types TEXT NOT NULL, texts TEXT NOT NULL, colors INTEGER NOT NULL, identity INTEGER NOT NULL, mv REAL NOT NULL,
        legal INTEGER NOT NULL, rarities INTEGER NOT NULL, layout TEXT NOT NULL, extra INTEGER NOT NULL,
        pow_min REAL, pow_max REAL, tou_min REAL, tou_max REAL, loy_min REAL, loy_max REAL)""")
    sql("CREATE TABLE search_data (oracle TEXT NOT NULL PRIMARY KEY, data TEXT NOT NULL)")
    sql("CREATE TABLE search_printing (oracle TEXT NOT NULL, rank INTEGER NOT NULL, printing_id TEXT NOT NULL, set_code TEXT NOT NULL, rarity TEXT NOT NULL, PRIMARY KEY(oracle, rank))")
    val started = System.nanoTime()
    sql("CREATE INDEX IF NOT EXISTS printing_oracle ON printing(oracle, id)")
    val group = mutableListOf<SearchRow>()
    var afterOracle = ""
    var afterId = ""
    while (true) {
        check(!Thread.currentThread().isInterrupted)
        val batch = select("""SELECT id, name, set_code, number, language, side, token, json, oracle, rarity, identity, legal
            FROM printing WHERE (oracle, id) > (?, ?) ORDER BY oracle, id LIMIT 512""", listOf(afterOracle, afterId), SEARCH_BATCH) { cursor ->
            SearchRow(cursor.getString(0)!!, cursor.getString(1)!!, cursor.getString(2)!!, cursor.getString(3)!!,
                cursor.getString(4)!!, cursor.getString(5)!!, cursor.getLong(6) == 1L, cursor.getString(7)!!,
                cursor.getString(8)!!, cursor.getString(9)!!, cursor.getLong(10)?.toInt(), cursor.getLong(11)!!)
        }
        if (batch.isEmpty()) break
        for (row in batch) {
            if (group.isNotEmpty() && group.first().oracle != row.oracle) { addSearchCard(group); group.clear() }
            if (!row.token && row.side in setOf("", "a") && row.layout !in NON_CARD_LAYOUTS) group += row
        }
        afterOracle = batch.last().oracle; afterId = batch.last().id
    }
    if (group.isNotEmpty()) addSearchCard(group)
    // Covers every numeric filter, so those scans never read the wide text columns.
    sql("""CREATE INDEX search_card_name ON search_card(name_key, oracle, extra, colors, identity, mv, legal, rarities, layout,
        pow_min, pow_max, tou_min, tou_max, loy_min, loy_max, printing_id)""")
    sql("CREATE INDEX search_printing_set ON search_printing(set_code, oracle)")
    sql("CREATE TABLE search_ready (version INTEGER NOT NULL)")
    sql("INSERT INTO search_ready VALUES (1)")
    java.util.logging.Logger.getLogger(CatalogIndex::class.java.name).info("Card search index: ${(System.nanoTime() - started) / 1_000_000} ms")
}

// Prepared-statement cache ids for the constant SQL of the rebuild (arbitrary, distinct from SQLDelight's).
private const val SEARCH_BATCH = 0x5EA4C001
private const val SEARCH_FACES = 0x5EA4C002
private const val SEARCH_FACE = 0x5EA4C003
private const val SEARCH_CARD = 0x5EA4C004
private const val SEARCH_PRINTING = 0x5EA4C005
private const val SEARCH_DATA = 0x5EA4C006

private class SearchRow(val id: String, val name: String, val set: String, val number: String, val language: String,
    val side: String, val token: Boolean, val raw: String, val oracle: String, val rarity: String, val identity: Int?, val legal: Long) {
    val json: JsonObject by lazy { JsonParser.parseString(raw).asJsonObject }
    val layout: String get() = text(json, "layout") ?: "normal"
    val english get() = language.equals("English", ignoreCase = true)
    val promo get() = json.get("isPromo")?.takeIf { it.isJsonPrimitive }?.asBoolean == true
    val paper get() = json.get("isOnlineOnly")?.takeIf { it.isJsonPrimitive }?.asBoolean != true &&
        json.get("availability")?.takeIf { it.isJsonArray }?.asJsonArray?.any { it.isJsonPrimitive && it.asString == "paper" } != false
}

private fun text(json: JsonObject, key: String): String? =
    json.get(key)?.takeIf { it.isJsonPrimitive }?.asString

private fun CatalogIndex.addSearchCard(group: List<SearchRow>) {
    // Same preference as a deck import without a printing: English, not promo, paper, newest, lowest number.
    val ranked = group.sortedWith(compareByDescending<SearchRow> { it.english }.thenBy { it.promo }.thenByDescending { it.paper }
        .thenByDescending { releaseDates[it.set].orEmpty() }
        .thenComparator { a, b -> compareCollectorNumbers(a.number, b.number) }
        .thenBy { it.number }.thenBy { it.set }.thenBy { it.id })
    val preferred = ranked.first()
    val others = select("SELECT p.id, p.name, p.side, p.json FROM face f JOIN printing p ON p.id=f.target WHERE f.source=?",
        listOf(preferred.id), SEARCH_FACES) { Triple(it.getString(0)!!, it.getString(2)!!, it.getString(1)!! to it.getString(3)!!) }
    val faces = (others.filter { it.first != preferred.id } + Triple(preferred.id, preferred.side, preferred.name to preferred.raw))
        .sortedWith(compareBy<Triple<String, String, Pair<String, String>>> { it.second }.thenBy { it.first })
        .map { (id, _, record) -> searchFace(record.first, if (id == preferred.id) preferred.json else JsonParser.parseString(record.second).asJsonObject) }
    val known = group.mapNotNull { it.identity }
    val identity = if (known.isNotEmpty()) known.fold(0) { mask, value -> mask or value }
        else faces.fold(0) { mask, face -> mask or face.colors or CardColors.mask(PodDeckManaCost.parse(face.manaCost).colors.map { it.name }) }
    val legalBits = group.fold(0L) { mask, row -> mask or row.legal }
    val legal = CardSearchRules.FORMATS.filterIndexed { bit, _ -> legalBits and (1L shl bit) != 0L }.toSet()
    val card = CardSearchCard(preferred.name, preferred.layout, faces, identity and CardColors.ALL, legal,
        ranked.map { CardSearchPrinting(it.set.lowercase(Locale.ROOT), it.rarity) }.distinct())
    val rarities = card.printings.fold(0L) { mask, printing ->
        CardSearchRules.RARITIES.indexOf(printing.rarity).takeIf { it >= 0 }?.let { mask or (1L shl it) } ?: mask
    }
    fun range(stat: (CardSearchFace) -> String?): List<Double?> = faces.mapNotNull { CardSearchRules.statValue(stat(it)) }
        .map { it.coerceIn(-1e300, 1e300) }.let { listOf(it.minOrNull(), it.maxOrNull()) }
    execute("INSERT INTO search_card VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)", listOf(preferred.oracle,
        CardSearchRules.foldName(card.name), preferred.id,
        faces.joinToString("\n") { CardSearchRules.foldText(it.typeLine) },
        faces.joinToString("\n") { CardSearchRules.foldText(it.text) },
        card.colorUnits.fold(0L) { bits, mask -> bits or (1L shl mask) }, 1L shl card.identity, card.manaValue,
        legalBits, rarities, card.layout, if (card.extra) 1L else 0L) + range { it.power } + range { it.toughness } +
        range { it.loyalty }, SEARCH_CARD)
    execute("INSERT INTO search_data VALUES (?, ?)", listOf(preferred.oracle, searchJson.encodeToString(CardSearchCard.serializer(), card)), SEARCH_DATA)
    ranked.forEachIndexed { rank, row ->
        execute("INSERT INTO search_printing VALUES (?, ?, ?, ?, ?)", listOf(row.oracle, rank.toLong(), row.id,
            row.set.lowercase(Locale.ROOT), row.rarity), SEARCH_PRINTING)
    }
}

private fun searchFace(name: String, json: JsonObject): CardSearchFace {
    val cost = text(json, "manaCost").orEmpty()
    val colors = json.get("colors")?.takeIf(JsonElement::isJsonArray)?.asJsonArray
        ?.filter { it.isJsonPrimitive }?.map { it.asString }?.let(CardColors::mask)
        ?: CardColors.mask(PodDeckManaCost.parse(cost).colors.map { it.name })
    return CardSearchFace(text(json, "faceName")?.takeIf { it.isNotEmpty() } ?: name, text(json, "type").orEmpty(),
        text(json, "text").orEmpty(), cost, colors, text(json, "power"), text(json, "toughness"), text(json, "loyalty"))
}

/** Worker-only. Runs on one installed generation; see [CatalogRepository.searchCards]. */
internal fun searchCatalogCards(file: File?, open: (File) -> SqlDriver, query: String, cursor: CatalogCardCursor?,
    limit: Int, budget: Int): CatalogCardSearch {
    require(limit in 1..200 && budget in 1..100_000)
    val parsed = when (val result = CardSearchQuery.parse(query)) {
        is CardSearchParse.Invalid -> return CatalogCardSearch.Invalid(result.error)
        is CardSearchParse.Valid -> result.query
    }
    val text = query.trim()
    file ?: throw CatalogNotInstalledException()
    if (cursor != null) {
        if (cursor.generation != file.name) throw CatalogGenerationChangedException()
        require(cursor.query == text) { "Page belongs to a different search." }
    }
    return CatalogIndex(open(file)).use { index ->
        if (!index.hasCardSearch()) throw CatalogSearchNotReadyException()
        val (filter, filterArgs) = CardSearchSql.where(parsed)
        class Hit(val oracle: String, val nameKey: String, val printingId: String, val card: CardSearchCard, val printing: Int)
        val hits = mutableListOf<Hit>()
        var afterName = cursor?.nameKey.orEmpty()
        var afterKey = cursor?.key.orEmpty()
        var examined = 0
        var exhausted = false
        scan@ while (true) {
            // Fetch only what this page can still use: matches needed (plus one to detect a next page) or budget left.
            val size = minOf(budget - examined, (limit + 1 - hits.size).coerceIn(16, 256))
            val rows = index.select("""SELECT search_card.oracle, name_key, printing_id, data FROM search_card
                JOIN search_data ON search_data.oracle = search_card.oracle
                WHERE (name_key, search_card.oracle) > (?, ?) AND ($filter) ORDER BY name_key, search_card.oracle LIMIT ?""",
                listOf(afterName, afterKey) + filterArgs + size.toLong()) { row ->
                arrayOf(row.getString(0)!!, row.getString(1)!!, row.getString(2)!!, row.getString(3)!!)
            }
            for ((oracle, nameKey, printingId, data) in rows) {
                check(!Thread.currentThread().isInterrupted)
                examined++
                afterName = nameKey; afterKey = oracle
                val card = searchJson.decodeFromString(CardSearchCard.serializer(), data)
                parsed.match(card)?.let { hits += Hit(oracle, nameKey, printingId, card, it) }
                if (hits.size > limit) break@scan
            }
            if (rows.size < size) { exhausted = true; break }
            if (examined >= budget) break
        }
        val visible = hits.take(limit)
        val chosen = visible.map { hit ->
            if (hit.printing == 0) hit.printingId else hit.card.printings[hit.printing].let { wanted ->
                index.texts("SELECT printing_id FROM search_printing WHERE oracle=? AND set_code=? AND rarity=? ORDER BY rank LIMIT 1",
                    listOf(hit.oracle, wanted.set, wanted.rarity)).single()
            }
        }
        val printings = index.printings(chosen)
        val faces = printings + index.printings(printings.values.flatMap { it.otherFaces }.filter { it !in printings })
        val cards = visible.zip(chosen).map { (hit, id) ->
            val printing = checkNotNull(faces[id]) { "Card printing is unavailable." }
            CatalogCard(hit.oracle, printing.name, (printing.otherFaces + printing.id).distinct()
                .map { checkNotNull(faces[it]) { "Card face is unavailable." } }
                .sortedWith(compareBy<CatalogPrinting> { it.side }.thenBy { it.id }))
        }
        val next = when {
            hits.size > limit -> visible.last().let { CatalogCardCursor(file.name, text, it.nameKey, it.oracle) }
            exhausted -> null
            else -> CatalogCardCursor(file.name, text, afterName, afterKey)
        }
        CatalogCardSearch.Page(file.name, index.metadata(), cards, next, examined)
    }
}

/** Same records as [CatalogIndex.printing] for many ids, in two queries instead of two per id. */
private fun CatalogIndex.printings(ids: Collection<String>): Map<String, CatalogPrinting> = ids.distinct().chunked(400).flatMap { chunk ->
    val marks = chunk.joinToString { "?" }
    val links = select("SELECT source, target FROM face WHERE source IN ($marks) ORDER BY source, target", chunk) {
        it.getString(0)!! to it.getString(1)!!
    }.groupBy({ it.first }, { it.second })
    rows("SELECT * FROM printing WHERE id IN ($marks)", chunk, enrich = false).map {
        it.copy(otherFaces = links[it.id].orEmpty(), releaseDate = releaseDates[it.set].orEmpty())
    }
}.associateBy { it.id }

/**
 * SQL prefilter for a parsed query: a condition every matching card satisfies, built from the indexed columns.
 * Exact translations may be negated; printing-level and per-face stat terms are left to the shared matcher.
 */
internal object CardSearchSql {
    class Clause(val sql: String, val args: List<Any>, val exact: Boolean)

    fun where(query: CardSearchQuery): Pair<String, List<Any>> {
        val parts = mutableListOf<String>()
        val args = mutableListOf<Any>()
        if (!query.includeExtras) parts += "extra=0"
        query.root?.let(::translate)?.let { parts.add(it.sql); args.addAll(it.args) }
        return (parts.joinToString(" AND ") { "($it)" }.ifEmpty { "1" }) to args
    }

    fun translate(node: CardSearchNode): Clause? = when (node) {
        is CardSearchNode.And -> {
            val parts = node.terms.map(::translate)
            val present = parts.filterNotNull()
            if (node.terms.isEmpty()) Clause("1", emptyList(), true)
            else if (present.isEmpty()) null
            else Clause(present.joinToString(" AND ") { "(${it.sql})" }, present.flatMap { it.args }, parts.all { it?.exact == true })
        }
        is CardSearchNode.Or -> {
            val parts = node.terms.map(::translate)
            if (parts.any { it == null }) null
            else Clause(parts.joinToString(" OR ") { "(${it!!.sql})" }, parts.flatMap { it!!.args }, parts.all { it!!.exact })
        }
        is CardSearchNode.Not -> translate(node.term)?.takeIf { it.exact }?.let { Clause("NOT (${it.sql})", it.args, true) }
        is CardSearchNode.Name -> Clause("instr(name_key, ?) > 0", listOf(node.text), exact = !node.exact)
        is CardSearchNode.TypeLine -> Clause("instr(types, ?) > 0", listOf(node.text), true)
        is CardSearchNode.OracleText -> if ('~' in node.text) null else Clause("instr(texts, ?) > 0", listOf(node.text), true)
        is CardSearchNode.Colors -> {
            // Bit m of the column is set when a colour unit has exactly the colours m; enumerate the 32 sets.
            val bits = (0..CardColors.ALL).filter { mask ->
                if (node.count != null) CardSearchQuery.compare(CardColors.count(mask).toDouble(), node.op, node.count!!.toDouble())
                else CardSearchQuery.compareColors(mask, node.op, node.mask)
            }.fold(0L) { bits, mask -> bits or (1L shl mask) }
            Clause("(${if (node.identity) "identity" else "colors"} & ?) != 0", listOf(bits), true)
        }
        is CardSearchNode.Stat -> when {
            node.stat == CardSearchStat.MANA_VALUE -> Clause("mv ${node.op.symbol} ?", listOf(node.value), true)
            node.other != null || node.op == CardSearchOp.NE -> null
            else -> {
                // Some face's value satisfies the comparison only if the face range allows it (NULL: no such stat).
                val column = when (node.stat) { CardSearchStat.POWER -> "pow"; CardSearchStat.TOUGHNESS -> "tou"; else -> "loy" }
                when (node.op) {
                    CardSearchOp.LT, CardSearchOp.LE -> Clause("${column}_min ${node.op.symbol} ?", listOf(node.value), false)
                    CardSearchOp.GT, CardSearchOp.GE -> Clause("${column}_max ${node.op.symbol} ?", listOf(node.value), false)
                    else -> Clause("${column}_min <= ? AND ${column}_max >= ?", listOf(node.value, node.value), false)
                }
            }
        }
        is CardSearchNode.Rarity -> {
            val bits = CardSearchRules.RARITIES.indices.filter { CardSearchQuery.compare(it.toDouble(), node.op, node.rank.toDouble()) }
                .fold(0L) { bits, rank -> bits or (1L shl rank) }
            Clause("(rarities & ?) != 0", listOf(bits), false)
        }
        is CardSearchNode.SetCode -> Clause("search_card.oracle IN (SELECT oracle FROM search_printing WHERE set_code = ?)", listOf(node.code), false)
        is CardSearchNode.Format -> Clause("(legal & ?) != 0", listOf(1L shl CardSearchRules.FORMATS.indexOf(node.format)), true)
        is CardSearchNode.Layout -> Clause("layout IN (${node.layouts.joinToString { "?" }})", node.layouts.toList(), true)
    }
}

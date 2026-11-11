package org.asyncttwiz.storage

import app.cash.sqldelight.db.SqlDriver
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.serialization.json.Json
import org.asyncttwiz.protocol.*
import java.io.File
import java.util.Locale


/** Each name a player can choose for this card with its own type line (CR 201.4b-f): a split card's halves, a
 * double-faced card's sides, an alternative name, each apart from the others. */
fun CatalogCard.namedParts(): List<CatalogNamedPart> = faces.map { face ->
    val raw = JsonParser.parseString(face.json).asJsonObject
    fun field(key: String) = raw.get(key)?.takeIf { it.isJsonPrimitive }?.asString.orEmpty()
    CatalogNamedPart(field("faceName").ifBlank { face.name }, field("type"))
}

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
 * batches in oracle order, never the whole catalog, and writes the cards a few hundred at a time. [onPrinting] sees every
 * installed printing on the way (enrich-free: no other faces or release date), so other finishing data can be built in the
 * same pass; [onProgress] how many printings have been read.
 */
internal fun CatalogIndex.rebuildCardSearch(onPrinting: (CatalogPrinting) -> Unit = {}, onProgress: (Long) -> Unit = {}) {
    listOf("search_ready", "search_card", "search_data", "search_printing").forEach { sql("DROP TABLE IF EXISTS $it") }
    // Filter columns stay narrow so scans are cheap; the matcher's card data is fetched only for prefiltered rows.
    sql("""CREATE TABLE search_card (oracle TEXT NOT NULL PRIMARY KEY, name_key TEXT NOT NULL, printing_id TEXT NOT NULL,
        types TEXT NOT NULL, texts TEXT NOT NULL, colors INTEGER NOT NULL, identity INTEGER NOT NULL, mv REAL NOT NULL,
        legal INTEGER NOT NULL, rarities INTEGER NOT NULL, layout TEXT NOT NULL, extra INTEGER NOT NULL,
        pow_min REAL, pow_max REAL, tou_min REAL, tou_max REAL, loy_min REAL, loy_max REAL)""")
    sql("CREATE TABLE search_data (oracle TEXT NOT NULL PRIMARY KEY, data TEXT NOT NULL)")
    sql("CREATE TABLE search_printing (oracle TEXT NOT NULL, rank INTEGER NOT NULL, printing_id TEXT NOT NULL, set_code TEXT NOT NULL, rarity TEXT NOT NULL, PRIMARY KEY(oracle, rank))")
    times.time("search order") { sql("CREATE INDEX IF NOT EXISTS printing_oracle ON printing(oracle, id)") }
    var group = mutableListOf<SearchRow>()
    val cards = SearchCards(this)
    var afterOracle = ""
    var afterId = ""
    var read = 0L
    while (true) {
        check(!Thread.currentThread().isInterrupted)
        val batch = times.time("search reads") {
            select("""SELECT id, name, set_code, number, language, side, token, json, oracle, rarity, identity, legal, scryfall
                FROM printing WHERE (oracle, id) > (?, ?) ORDER BY oracle, id LIMIT 1024""", listOf(afterOracle, afterId), SEARCH_BATCH) { cursor ->
                SearchRow(cursor.text(0), cursor.text(1), cursor.text(2), cursor.text(3),
                    cursor.text(4), cursor.text(5), cursor.getLong(6) == 1L, cursor.text(7),
                    cursor.text(8), cursor.text(9), cursor.getLong(10)?.toInt(), cursor.number(11), cursor.getString(12))
            }
        }
        if (batch.isEmpty()) break
        for (row in batch) {
            times.time("avatar choices") {
                onPrinting(CatalogPrinting(row.id, row.name, row.set, row.number, row.language, row.side, row.scryfall, row.token,
                    emptyList(), row.raw))
            }
            if (group.isNotEmpty() && group.first().oracle != row.oracle) { cards.add(group); group = mutableListOf() }
            if (times.time("search layouts") { !row.token && row.side in setOf("", "a") && row.layout !in NON_CARD_LAYOUTS }) group += row
        }
        afterOracle = batch.last().oracle; afterId = batch.last().id
        read += batch.size
        onProgress(read)
    }
    if (group.isNotEmpty()) cards.add(group)
    cards.flush()
    times.time("search indexes") {
        // Covers every numeric filter, so those scans never read the wide text columns.
        sql("""CREATE INDEX search_card_name ON search_card(name_key, oracle, extra, colors, identity, mv, legal, rarities, layout,
            pow_min, pow_max, tou_min, tou_max, loy_min, loy_max, printing_id)""")
        sql("CREATE INDEX search_printing_set ON search_printing(set_code, oracle)")
    }
    sql("CREATE TABLE search_ready (version INTEGER NOT NULL)")
    sql("INSERT INTO search_ready VALUES (1)")
}

// Prepared-statement cache ids for the constant SQL of the rebuild (arbitrary, distinct from SQLDelight's).
private const val SEARCH_BATCH = 0x5EA4C001
private const val SEARCH_FACES = 0x5EA4C002
private const val SEARCH_FACE = 0x5EA4C003

private class SearchRow(val id: String, val name: String, val set: String, val number: String, val language: String,
    val side: String, val token: Boolean, val raw: String, val oracle: String, val rarity: String, val identity: Int?, val legal: Long,
    val scryfall: String?) {
    // Worked out once each: the ranking compares them many times
    val json: JsonObject by lazy(LazyThreadSafetyMode.NONE) { JsonParser.parseString(raw).asJsonObject }
    val layout: String by lazy(LazyThreadSafetyMode.NONE) { text(json, "layout") ?: "normal" }
    val english by lazy(LazyThreadSafetyMode.NONE) { language.equals("English", ignoreCase = true) }
    val promo by lazy(LazyThreadSafetyMode.NONE) { json.get("isPromo")?.takeIf { it.isJsonPrimitive }?.asBoolean == true }
    val paper by lazy(LazyThreadSafetyMode.NONE) { json.get("isOnlineOnly")?.takeIf { it.isJsonPrimitive }?.asBoolean != true &&
        json.get("availability")?.takeIf { it.isJsonArray }?.asJsonArray?.any { it.isJsonPrimitive && it.asString == "paper" } != false }
}

private fun text(json: JsonObject, key: String): String? =
    json.get(key)?.takeIf { it.isJsonPrimitive }?.asString

/** The search rows of cards (each a group of printings of one oracle), written [PENDING] cards at a time: one query for
 * their other faces, then a few multi-row inserts per table. */
private class SearchCards(private val index: CatalogIndex) {
    private val pending = ArrayList<List<SearchRow>>(PENDING)

    fun add(group: List<SearchRow>) {
        pending += group
        if (pending.size == PENDING) flush()
    }

    fun flush() {
        if (pending.isEmpty()) return
        // Same preference as a deck import without a printing: English, not promo, paper, newest, lowest number.
        val ranked = index.times.time("search ranks") { pending.map { group ->
            group.sortedWith(compareByDescending<SearchRow> { it.english }.thenBy { it.promo }.thenByDescending { it.paper }
                .thenByDescending { index.releaseDates[it.set].orEmpty() }
                .thenComparator { a, b -> compareCollectorNumbers(a.number, b.number) }
                .thenBy { it.number }.thenBy { it.set }.thenBy { it.id })
        } }
        val sources = ranked.map { it.first().id }
        val faces = index.times.time("search faces") {
            sources.distinct().chunked(MAX_VALUES).flatMap { chunk ->
                index.select("SELECT f.source, p.id, p.name, p.side, p.json FROM face f JOIN printing p ON p.id=f.target WHERE f.source IN " +
                    chunk.joinToString(",", "(", ")") { "?" }, chunk, SEARCH_FACES.takeIf { chunk.size == MAX_VALUES }) {
                    it.text(0) to Triple(it.text(1), it.text(3), it.text(2) to it.text(4))
                }
            }.groupBy({ it.first }, { it.second })
        }
        val cards = ArrayList<List<Any?>>(ranked.size)
        val data = ArrayList<List<Any?>>(ranked.size)
        val printings = ArrayList<List<Any?>>()
        index.times.time("search cards") {
            for (group in ranked) index.searchRows(group, faces[group.first().id].orEmpty(), cards, data, printings)
        }
        index.times.time("search writes") {
            index.insertRows("INSERT INTO search_card", cards)
            index.insertRows("INSERT INTO search_data", data)
            index.insertRows("INSERT INTO search_printing", printings)
        }
        pending.clear()
    }

    private companion object { const val PENDING = 256 }
}

/** One card's search rows from its printings, best first ([ranked]), and the other faces of the first ([others]: id, side,
 * name and record), added to [cards], [data] and [printings]. */
private fun CatalogIndex.searchRows(ranked: List<SearchRow>, others: List<Triple<String, String, Pair<String, String>>>,
    cards: MutableList<List<Any?>>, data: MutableList<List<Any?>>, printings: MutableList<List<Any?>>) {
    val group = ranked // order-free folds below
    val preferred = ranked.first()
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
    cards += listOf(preferred.oracle,
        CardSearchRules.foldName(card.name), preferred.id,
        faces.joinToString("\n") { CardSearchRules.foldText(it.typeLine) },
        faces.joinToString("\n") { CardSearchRules.foldText(it.text) },
        card.colorUnits.fold(0L) { bits, mask -> bits or (1L shl mask) }, 1L shl card.identity, card.manaValue,
        legalBits, rarities, card.layout, if (card.extra) 1L else 0L) + range { it.power } + range { it.toughness } +
        range { it.loyalty }
    data += listOf(preferred.oracle, times.time("search encoding") { searchJson.encodeToString(CardSearchCard.serializer(), card) })
    ranked.forEachIndexed { rank, row ->
        printings += listOf(row.oracle, rank.toLong(), row.id, row.set.lowercase(Locale.ROOT), row.rarity)
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
                arrayOf(row.text(0), row.text(1), row.text(2), row.text(3))
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

/** See [CatalogRepository.searchData]. */
internal fun CatalogIndex.searchData(printings: Collection<CatalogPrinting>): Map<String, CardSearchCard> {
    if (!hasCardSearch()) return emptyMap()
    val keys = printings.mapNotNull { printing ->
        runCatching { printing.id to cardSearchKey(printing, JsonParser.parseString(printing.json).asJsonObject) }.getOrNull()
    }
    val found = keys.map { it.second }.distinct().chunked(400).flatMap { chunk ->
        select("SELECT oracle, data FROM search_data WHERE oracle IN (${chunk.joinToString { "?" }})", chunk) {
            it.text(0) to it.text(1)
        }
    }.toMap()
    return keys.mapNotNull { (id, key) -> found[key]?.let { id to searchJson.decodeFromString(CardSearchCard.serializer(), it) } }.toMap()
}

/** Same records as [CatalogIndex.printing] for many ids, in two queries instead of two per id. */
private fun CatalogIndex.printings(ids: Collection<String>): Map<String, CatalogPrinting> = ids.distinct().chunked(400).flatMap { chunk ->
    val marks = chunk.joinToString { "?" }
    val links = select("SELECT source, target FROM face WHERE source IN ($marks) ORDER BY source, target", chunk) {
        it.text(0) to it.text(1)
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
            val present = parts.filterNotNull()
            if (present.size != parts.size) null
            else Clause(present.joinToString(" OR ") { "(${it.sql})" }, present.flatMap { it.args }, present.all { it.exact })
        }
        is CardSearchNode.Not -> translate(node.term)?.takeIf { it.exact }?.let { Clause("NOT (${it.sql})", it.args, true) }
        is CardSearchNode.Name -> Clause("instr(name_key, ?) > 0", listOf(node.text), exact = !node.exact)
        is CardSearchNode.TypeLine -> Clause("instr(types, ?) > 0", listOf(node.text), true)
        is CardSearchNode.OracleText -> if ('~' in node.text) null else Clause("instr(texts, ?) > 0", listOf(node.text), true)
        is CardSearchNode.Colors -> {
            // Bit m of the column is set when a colour unit has exactly the colours m; enumerate the 32 sets.
            val bits = (0..CardColors.ALL).filter { mask ->
                node.count?.let { count -> CardSearchQuery.compare(CardColors.count(mask).toDouble(), node.op, count.toDouble()) }
                    ?: CardSearchQuery.compareColors(mask, node.op, node.mask)
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

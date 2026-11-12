package org.asyncttwiz.storage

import kotlin.random.Random
import org.asyncttwiz.catalog.CardCatalogFormat
import org.asyncttwiz.catalog.CatalogCards
import org.asyncttwiz.catalog.CatalogFaceRecord
import org.asyncttwiz.catalog.CatalogHeader
import org.asyncttwiz.catalog.CatalogPrintingRecord
import org.asyncttwiz.catalog.CatalogRecipeRecord
import org.asyncttwiz.catalog.CatalogRecord
import org.asyncttwiz.catalog.CatalogSetRecord
import org.asyncttwiz.protocol.CardColors
import org.asyncttwiz.protocol.CardSearchCard
import org.asyncttwiz.protocol.CardSearchFace
import org.asyncttwiz.protocol.CardSearchParse
import org.asyncttwiz.protocol.CardSearchPrinting
import org.asyncttwiz.protocol.CardSearchQuery
import org.asyncttwiz.protocol.CardSearchRules
import org.asyncttwiz.protocol.DefinitionFace

/**
 * The card records in memory (the browser, docs/specs/web-client.md §1 item 4 and §3.5): a card catalog read whole from
 * its lines, with the lookups Android's card records index keeps on disk (CatalogRepository), answering the same reads the
 * same way: deck imports ([CatalogLookups]), card search, support marks, token and emblem pictures, avatar choices.
 * [generation] names it (the catalog's checksum). Built by [MemoryCardCatalogBuilder]; read on one thread.
 */
class MemoryCardCatalog internal constructor(
    val generation: String,
    private val header: CatalogHeader,
    private val faces: List<CatalogFaceRecord>,
    /** The printings kept, by id (those whose faces don't link up are left out, as Android's validation does). */
    private val printings: Map<String, CatalogPrintingRecord>,
    private val sets: Map<String, CatalogSetRecord>,
    /** The printings of cards the catalog marks supported (section 3.8), kept or not, as Android marks them. */
    private val supported: Set<String>,
) : CardCatalog, CatalogLookups {
    private val releaseDates: Map<String, String> = sets.mapValues { it.value.released }
    private val ordered: List<String> = printings.keys.sorted()
    private val others: Map<String, List<String>> = printings.mapValues { (_, record) -> record.others.distinct().filter { it in printings }.sorted() }
    private val byName = HashMap<String, MutableList<String>>()
    private val byScryfall = HashMap<String, MutableList<String>>()
    private val tokensByName = HashMap<String, MutableList<String>>()
    private val relations = HashMap<String, MutableList<String>>()
    private val emblems = ArrayList<String>()
    private lateinit var cards: List<SearchEntry>
    private lateinit var cardByOracle: Map<String, SearchEntry>

    init {
        for (id in ordered) {
            val record = printings.getValue(id)
            val face = faces[record.face]
            listOfNotNull(face.name, face.faceName.ifEmpty { null }).map { it.lowercase() }.distinct()
                .forEach { byName.getOrPut(it) { ArrayList() } += id }
            record.scryfall?.let { byScryfall.getOrPut(it) { ArrayList() } += id }
            if (record.token) {
                tokensByName.getOrPut(face.name.lowercase()) { ArrayList() } += id
                if (face.layout == "emblem") emblems += id
                record.makers.forEach { relations.getOrPut(it) { ArrayList() } += id }
            } else record.tokens.forEach { token -> relations.getOrPut(id) { ArrayList() } += token }
        }
    }

    /** One card of the search: its printings best first ([ranked]), and the card as search matches it. */
    internal class SearchEntry(val oracle: String, val nameKey: String, val ranked: List<String>, val card: CardSearchCard)

    /** Builds the card search, a few hundred cards between pauses ([pause]); [progress] counts printings done of all. */
    internal suspend fun buildSearch(pause: suspend () -> Unit, progress: (Long, Long) -> Unit) {
        val groups = LinkedHashMap<String, MutableList<String>>()
        for (id in ordered) {
            val record = printings.getValue(id)
            val face = faces[record.face]
            if (!record.token && face.side in setOf("", "a") && face.layout !in NON_CARD_LAYOUTS)
                groups.getOrPut(oracleOf(face)) { ArrayList() } += id
        }
        val entries = ArrayList<SearchEntry>(groups.size)
        var done = 0L
        for ((oracle, ids) in groups) {
            entries += searchEntry(oracle, ids)
            done += ids.size
            if (entries.size % 500 == 0) { progress(done, ordered.size.toLong()); pause() }
        }
        entries.sortWith(compareBy<SearchEntry> { it.nameKey }.thenBy { it.oracle })
        cards = entries
        cardByOracle = entries.associateBy { it.oracle }
        progress(ordered.size.toLong(), ordered.size.toLong())
    }

    private fun oracleOf(face: CatalogFaceRecord) = face.oracle.ifEmpty { "name:${face.name.lowercase()}" }

    // Same preference as a deck import without a printing: English, not promo, paper, newest, lowest number.
    private val preference = compareBy<CatalogPrintingRecord> { it.promo }.thenByDescending { paper(it) }
        .thenByDescending { releaseDates[it.set].orEmpty() }
        .thenComparator { a, b -> compareCollectorNumbers(a.number, b.number) }
        .thenBy { it.number }.thenBy { it.set }.thenBy { it.id }

    private fun paper(record: CatalogPrintingRecord) = !record.onlineOnly && "paper" in record.availability

    private fun searchEntry(oracle: String, ids: List<String>): SearchEntry {
        val group = ids.map { printings.getValue(it) }
        val ranked = group.sortedWith(preference)
        val preferred = ranked.first()
        val preferredFace = faces[preferred.face]
        val cardFaces = (others.getValue(preferred.id).filter { it != preferred.id } + preferred.id)
            .map { printings.getValue(it) }
            .sortedWith(compareBy<CatalogPrintingRecord> { faces[it.face].side }.thenBy { it.id })
            .map { searchFace(faces[it.face]) }
        val identity = group.fold(0) { mask, record -> mask or CardColors.mask(faces[record.face].identity) }
        val legalBits = group.fold(0L) { mask, record -> mask or faces[record.face].legal }
        val legal = CardSearchRules.FORMATS.filterIndexed { bit, _ -> legalBits and (1L shl bit) != 0L }.toSet()
        val card = CardSearchCard(preferredFace.name, preferredFace.layout, cardFaces, identity and CardColors.ALL, legal,
            ranked.map { CardSearchPrinting(it.set.lowercase(), it.rarity) }.distinct())
        return SearchEntry(oracle, CardSearchRules.foldName(card.name), ranked.map { it.id }, card)
    }

    private fun searchFace(face: CatalogFaceRecord) = CardSearchFace(face.faceName.ifEmpty { face.name }, face.type, face.text,
        face.manaCost, CardColors.mask(face.colors), face.power.ifEmpty { null }, face.toughness.ifEmpty { null },
        face.loyalty.ifEmpty { null })

    /** Printing [id] as the app reads card records: its record, other faces and set's release date. */
    private fun record(id: String): CatalogPrinting? = printings[id]?.let { record ->
        val face = faces[record.face]
        CatalogPrinting(record.id, face.name, record.set, record.number, "English", face.side, record.scryfall, record.token,
            others.getValue(id), CatalogCards.sourceRecordText(record, face), releaseDates[record.set].orEmpty())
    }

    override fun metadata(): CatalogMetadata = CatalogMetadata(header.mtgjsonDate, header.mtgjsonVersion, generation, printings.size.toLong())

    override fun printing(id: String): CatalogPrinting? = record(id)

    override fun find(name: String, set: String?, number: String?, limit: Int, afterId: String?): List<CatalogPrinting> {
        require(limit in 1..1000)
        return byName[name.lowercase()].orEmpty().asSequence().filter { id ->
            val record = printings.getValue(id)
            (set == null || record.set == set) && (number == null || record.number == number) && (afterId == null || id > afterId)
        }.take(limit).mapNotNull(::record).toList()
    }

    override fun findScryfall(id: String): List<CatalogPrinting> = byScryfall[id.lowercase()].orEmpty().mapNotNull(::record)

    override fun draftableSets(): List<DraftableSet> {
        val counts = HashMap<String, Long>()
        printings.values.forEach { if (!it.token) counts[it.set] = (counts[it.set] ?: 0) + 1 }
        return sets.values.filter { it.draftable }
            .sortedWith(compareByDescending<CatalogSetRecord> { it.released }.thenBy { it.code })
            .map { DraftableSet(it.code, it.name, it.released, counts[it.code] ?: 0, it.automatum, it.unsupported, it.keyrune) }
    }

    override fun unsupported(printingIds: Collection<String>): Set<String> = printingIds.toSet() - supported

    override fun unsupportedPictures(scryfallIds: Collection<String>): Set<String> {
        val ids = scryfallIds.map { it.lowercase() }.toSet()
        return ids.filterTo(LinkedHashSet()) { id -> byScryfall[id].orEmpty().none { it in supported } }
    }

    override fun cards(printingIds: Collection<String>): Map<String, List<CatalogPrinting>> = printingIds.distinct()
        .mapNotNull { id -> record(id)?.let { printing ->
            id to (printing.otherFaces + printing.id).distinct().mapNotNull(::record)
                .sortedWith(compareBy<CatalogPrinting> { it.side }.thenBy { it.id })
        } }.toMap()

    override fun tokenArt(face: DefinitionFace, sourceName: String?, sourcePrintingId: String?): CatalogPrinting? {
        val sources = listOfNotNull(sourcePrintingId, sourceName?.lowercase()).distinct()
        val related = sources.flatMap { relations[it].orEmpty() }.filter { printings[it]?.token == true }.mapNotNull(::record)
        val named = tokensByName[TokenArtMatch.name(face.name)].orEmpty().mapNotNull(::record)
        val set = sourcePrintingId?.let { printings[it]?.set }
        return TokenArtMatch.best(face, set, related, named)
    }

    override fun emblemArt(text: String): String? {
        val wanted = EmblemArt.normalized(text).ifEmpty { return null }
        return emblems.map { printings.getValue(it) }
            .filter { it.scryfall != null && EmblemArt.normalized(faces[it.face].text) == wanted }
            .maxByOrNull { releaseDates[it.set].orEmpty() }?.scryfall
    }

    // Names with a picture, each with its first printing (by id), in name order: the avatar search
    private val avatarNames: List<Pair<String, String>> by lazy {
        val first = HashMap<String, String>()
        for (id in ordered) {
            val record = printings.getValue(id)
            if (record.scryfall == null) continue
            first.getOrPut(faces[record.face].name.lowercase()) { id }
        }
        first.entries.map { it.key to it.value }.sortedBy { it.first }
    }

    // The random avatar choices: one legendary creature or planeswalker printing per name (the first in card order)
    private val avatarPool: List<Pair<String, String>> by lazy {
        val chosen = LinkedHashMap<String, String>()
        printings.values.sortedWith(compareBy<CatalogPrintingRecord> { oracleOf(faces[it.face]) }.thenBy { it.id }).forEach { record ->
            val face = faces[record.face]
            if (record.token || record.scryfall == null || "Legendary" !in face.type) return@forEach
            val name = face.name.lowercase()
            if (name !in chosen && record(record.id)?.avatarChoice(true) != null) chosen[name] = record.id
        }
        chosen.entries.map { it.key to it.value }
    }

    override fun avatarChoices(query: String, previous: List<CatalogAvatar>): List<CatalogAvatar> {
        if (query.isNotBlank()) {
            val wanted = query.trim().lowercase().take(512)
            return avatarNames.asSequence().filter { wanted in it.first }.take(60).mapNotNull { record(it.second) }
                .mapNotNull { it.avatarChoice(false) }.distinctBy { it.name }.toList()
        }
        val repeats = previous.take(6).map { it.name.lowercase() }.toSet()
        return avatarPool.shuffled(Random).sortedBy { it.first in repeats }.take(6)
            .mapNotNull { record(it.second)?.avatarChoice(true) }
    }

    override fun tokenGeneration(): String = generation

    override fun searchCards(query: String, cursor: CatalogCardCursor?, limit: Int, budget: Int): CatalogCardSearch {
        require(limit in 1..200 && budget in 1..100_000)
        val parsed = when (val result = CardSearchQuery.parse(query)) {
            is CardSearchParse.Invalid -> return CatalogCardSearch.Invalid(result.error)
            is CardSearchParse.Valid -> result.query
        }
        val text = query.trim()
        if (cursor != null) {
            if (cursor.generation != generation) throw CatalogGenerationChangedException()
            require(cursor.query == text) { "Page belongs to a different search." }
        }
        class Hit(val entry: SearchEntry, val printing: Int)
        val hits = ArrayList<Hit>()
        var position = if (cursor == null) 0 else firstAfter(cursor.nameKey, cursor.key)
        // Android examines only the rows its SQL prefilter lets through; in memory every card is matched, so the work
        // budget covers more cards here for about the same work.
        val evaluations = budget * MATCHES_PER_EXAMINED
        var examined = 0
        while (position < cards.size && hits.size <= limit && examined < evaluations) {
            val entry = cards[position++]
            examined++
            hits += Hit(entry, parsed.match(entry.card) ?: continue)
        }
        val exhausted = position >= cards.size && hits.size <= limit
        val visible = hits.take(limit)
        val found = visible.map { hit ->
            val chosen = if (hit.printing == 0) hit.entry.ranked.first() else hit.entry.card.printings[hit.printing].let { wanted ->
                hit.entry.ranked.first { printings.getValue(it).set.lowercase() == wanted.set && printings.getValue(it).rarity == wanted.rarity }
            }
            val printing = checkNotNull(record(chosen)) { "Card printing is unavailable." }
            CatalogCard(hit.entry.oracle, printing.name, (printing.otherFaces + printing.id).distinct()
                .map { checkNotNull(record(it)) { "Card face is unavailable." } }
                .sortedWith(compareBy<CatalogPrinting> { it.side }.thenBy { it.id }))
        }
        val next = when {
            hits.size > limit -> visible.last().entry.let { CatalogCardCursor(generation, text, it.nameKey, it.oracle) }
            exhausted -> null
            else -> cards[position - 1].let { CatalogCardCursor(generation, text, it.nameKey, it.oracle) }
        }
        return CatalogCardSearch.Page(generation, metadata(), found, next, examined)
    }

    /** The position of the first card after ([nameKey], [oracle]) in search order. */
    private fun firstAfter(nameKey: String, oracle: String): Int {
        var low = 0
        var high = cards.size
        while (low < high) {
            val middle = (low + high) ushr 1
            val entry = cards[middle]
            val compared = entry.nameKey.compareTo(nameKey).let { if (it != 0) it else entry.oracle.compareTo(oracle) }
            if (compared <= 0) low = middle + 1 else high = middle
        }
        return low
    }

    override fun searchData(printings: Collection<CatalogPrinting>): Map<String, CardSearchCard> = printings.mapNotNull { printing ->
        val json = jsonObjectOrNull(printing.json) ?: return@mapNotNull null
        val key = (json["identifiers"] as? kotlinx.serialization.json.JsonObject)?.primitive("scryfallOracleId")?.takeIf { it.isNotEmpty() }
            ?: "name:${printing.name.lowercase()}"
        cardByOracle[key]?.let { printing.id to it.card }
    }.toMap()

    internal companion object {
        val NON_CARD_LAYOUTS = setOf("art_series", "token", "double_faced_token", "emblem")
        /** Cards matched in memory per card Android's prefilter would let through (see [searchCards]). */
        const val MATCHES_PER_EXAMINED = 10
    }
}

/**
 * Reads a card catalog's lines, in file order, into a [MemoryCardCatalog] ([add] each line, then [finish]), checking it as
 * Android's installer does: one header of this app's format and formats, every record the header counts, faces in order.
 */
class MemoryCardCatalogBuilder {
    private var header: CatalogHeader? = null
    private val sets = LinkedHashMap<String, CatalogSetRecord>()
    private var setCount = 0
    private val faces = ArrayList<CatalogFaceRecord>()
    private val printings = ArrayList<CatalogPrintingRecord>()
    private var recipes = 0

    /** Takes one line of the catalog (empty lines are skipped). */
    fun add(line: String) {
        if (line.isEmpty()) return
        // The booster recipes are the server's (section 3.7): counted, not kept
        if (line.startsWith(RECIPE_LINE)) { checkNotNull(header) { "No catalog header" }; recipes++; return }
        add(CardCatalogFormat.decode(line))
    }

    fun add(record: CatalogRecord) {
        when (record) {
            is CatalogHeader -> {
                check(header == null) { "Two catalog headers" }
                check(record.format == CardCatalogFormat.FORMAT) { "Catalog format ${record.format}, expected ${CardCatalogFormat.FORMAT}" }
                check(record.formats == CardSearchRules.FORMATS) { "The catalog's formats aren't this app's" }
                header = record
            }
            is CatalogSetRecord -> { checkNotNull(header) { "No catalog header" }; sets.getOrPut(record.code) { record }; setCount++ }
            is CatalogFaceRecord -> { check(record.i == faces.size) { "Face ${record.i} out of order" }; faces += record }
            is CatalogPrintingRecord -> {
                check(record.face in faces.indices) { "Printing ${record.id} names a face the catalog doesn't have" }
                printings += record
            }
            is CatalogRecipeRecord -> recipes++
        }
    }

    /** The card records, once every line is read; [pause] lets the page draw between parts of the work, [progress] counts
     * printings done of all while the card search is built. */
    suspend fun finish(checksum: String, pause: suspend () -> Unit, progress: (Long, Long) -> Unit = { _, _ -> }): MemoryCardCatalog {
        val read = checkNotNull(header) { "No catalog header" }
        check(read.sets == setCount && read.faces == faces.size && read.printings == printings.size && read.recipes == recipes) {
            "Catalog incomplete: header says ${read.sets}/${read.faces}/${read.printings}/${read.recipes}, " +
                "read $setCount/${faces.size}/${printings.size}/$recipes"
        }
        // The first record of an id wins, as in Android's index
        val byId = LinkedHashMap<String, CatalogPrintingRecord>()
        printings.forEach { byId.getOrPut(it.id) { it } }
        val supported = byId.values.filter { faces[it.face].automatum && !it.token }.mapTo(HashSet()) { it.id }
        val kept = linkedFaces(byId)
        pause()
        val catalog = MemoryCardCatalog(checksum, read, faces, kept, sets, supported)
        pause()
        catalog.buildSearch(pause, progress)
        check(kept.isNotEmpty()) { "Empty catalog" }
        return catalog
    }

    /** The printings whose faces link up (Android's validation): a link to a missing printing, to itself, one way only,
     * across sets or between faces without distinct sides leaves out its printing and every printing linked to it. */
    private fun linkedFaces(byId: Map<String, CatalogPrintingRecord>): Map<String, CatalogPrintingRecord> {
        fun side(id: String) = faces[byId.getValue(id).face].side
        val broken = HashSet<String>()
        val linkedFrom = HashMap<String, MutableList<String>>()
        for (record in byId.values) for (target in record.others.distinct()) {
            linkedFrom.getOrPut(target) { ArrayList() } += record.id
            val other = byId[target]
            if (other == null || target == record.id || record.id !in other.others || other.set != record.set ||
                side(record.id).isEmpty() || side(target).isEmpty() || side(record.id) == side(target)) broken += record.id
        }
        val queue = ArrayDeque(broken)
        while (queue.isNotEmpty()) {
            val id = queue.removeFirstOrNull() ?: break
            val neighbours = byId[id]?.others.orEmpty() + linkedFrom[id].orEmpty()
            neighbours.forEach { if (broken.add(it)) queue += it }
        }
        return if (broken.isEmpty()) byId else byId.filterKeys { it !in broken }
    }

    private companion object {
        // How a recipe record's line starts: the class discriminator comes first (CardCatalogFormat.encode)
        const val RECIPE_LINE = "{\"t\":\"recipe\""
    }
}

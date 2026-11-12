package org.asyncttwiz.storage

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject

/** One consistent view of the installed card records as a deck import reads them (Android: a catalog generation,
 * CatalogIndex; the browser: [MemoryCardCatalog]). */
interface CatalogLookups {
    fun metadata(): CatalogMetadata
    /** Exact normalized name (card or face name), optionally exact set/collector number, in id order after [afterId]. */
    fun find(name: String, set: String?, number: String?, limit: Int, afterId: String? = null): List<CatalogPrinting>
    /** The printings with picture (Scryfall id) [id], in id order. */
    fun findScryfall(id: String): List<CatalogPrinting>
    fun printing(id: String): CatalogPrinting?
}

/** Where a deck import reads card records from: [lookups] runs its block on one generation, with null when none is
 * installed. */
interface CatalogSnapshots {
    fun <T> lookups(read: (CatalogLookups?) -> T): T
    /** Whether the work reading them was cancelled (Android: the worker thread interrupted). */
    fun cancelled(): Boolean = false
}

/** Worker-only resolution on the installed catalog; exact face text/defaults are retained. */
class DeckImportResolver(private val repository: CatalogSnapshots) : DeckResolver {
    override fun resolve(text: String): DeckImportResult = resolve(DeckTextParser.parse(text))
    override fun resolve(parsed: ParsedDeck, maxCards: Int,
        allowSubstitutions: Boolean): DeckImportResult = repository.lookups { catalog ->
        val issues = parsed.issues.toMutableList()
        val substitutions = mutableListOf<ImportIssue>()
        val entries = linkedMapOf<Pair<DeckSection, String>, ResolvedDeckEntry>()
        val metadata = catalog?.metadata()
        if (catalog == null) {
            issues += ImportIssue(0, "Card records are not installed. Download card records, then retry lookup.")
        } else for (line in parsed.lines) {
            check(!repository.cancelled())
            // Page through every matching printing: prolific cards must not become ambiguous just
            // because they exceed a lookup page, nor may a later, different identity be ignored.
            fun find(set: String?, number: String?) = buildList {
                var after: String? = null
                do {
                    check(!repository.cancelled())
                    val page = catalog.find(line.name, set, number, 256, after)
                    addAll(page)
                    after = page.lastOrNull()?.id
                } while (page.size == 256)
            }
            var found = if (line.scryfallId == null) find(line.set, line.number) else catalog.findScryfall(line.scryfallId)
            var replaced = false
            if (found.isEmpty() && allowSubstitutions && (line.set != null || line.scryfallId != null)) {
                // Printing choice is cosmetic: stay as close to the requested printing as the catalog allows.
                if (line.scryfallId != null && line.set != null && line.number != null) found = find(line.set, line.number)
                if (found.isEmpty() && line.set != null && line.number != null) found = find(line.set, null)
                if (found.isEmpty()) found = find(null, null)
                replaced = true
            }
            val groups = found.groupBy { (it.otherFaces + it.id).sorted().joinToString("|") }
            val matching = groups.values.map { group ->
                val record = group.first()
                Candidate((record.otherFaces + record.id).map { id -> checkNotNull(catalog.printing(id)) }
                    .sortedWith(compareBy<CatalogPrinting> { it.side }.thenBy { it.id }))
            }
            // Art-series/token faces can share names with playable cards. Likewise a spell
            // on another card's back (e.g. Emeritus of Conflict // Lightning Bolt) is not
            // the card named Lightning Bolt. Prefer full/front names, retaining back-face
            // lookup only when there is no primary match; genuine front-name conflicts stay errors.
            val cards = matching.filterNot { it.printing.token }.ifEmpty { matching }
            val candidates = cards.filter { it.primaryName(line.name) }.ifEmpty { cards }
            // Several printings of one card are never ambiguous; only different cards sharing a name are.
            // A cube import still picks a deterministic card then, and reports it as a substitution.
            val ambiguous = !sameCard(candidates)
            if (candidates.isEmpty() || (ambiguous && !allowSubstitutions)) {
                issues += ImportIssue(line.line, if (found.isEmpty())
                    "${line.name}${line.printingSuffix} could not be resolved. Check the name/printing or update card records."
                    else "${line.name} is ambiguous. Include an exact (SET) collector-number.")
                continue
            }
            val faces = candidates.minWith(defaultPrinting).faces
            if (replaced) substitutions += ImportIssue(line.line,
                "${line.name}: requested ${line.scryfallId ?: line.printingSuffix.trim()} → (${faces.first().set}) ${faces.first().number}")
            else if (ambiguous) substitutions += ImportIssue(line.line,
                "${line.name}: several different cards share this name → (${faces.first().set}) ${faces.first().number}")
            val key = line.section to faces.first().id
            val previous = entries[key]
            val quantity = (previous?.quantity?.toLong() ?: 0) + line.quantity
            if (quantity !in 1..maxCards.toLong()) {
                issues += ImportIssue(line.line, "Combined quantity for ${line.name} exceeds $maxCards.")
            } else entries[key] = ResolvedDeckEntry(line.section, quantity.toInt(), faces,
                previous?.sourceLines.orEmpty() + line.line)
        }
        // A partial resolution is useful only as diagnostics, never as a preview/save payload.
        DeckImportResult(parsed.text, if (issues.isEmpty()) entries.values.toList() else emptyList(), issues, metadata, substitutions)
    }

    private class Candidate(val faces: List<CatalogPrinting>) {
        val printing = faces.first()
        val json: List<JsonObject> = faces.map { jsonObject(it.json) }
        val oracleIds = json.mapNotNull { it.present("identifiers")?.jsonObject
            ?.present("scryfallOracleId")?.asText() }
            .distinct().sorted()
        val shape = faces.zip(json).map { (face, raw) ->
            listOf(face.name.lowercase(), raw.present("faceName")?.asText()?.lowercase().orEmpty(),
                face.side, face.token.toString())
        }
        val english = printing.language.equals("English", ignoreCase = true)
        val promo = json.first().present("isPromo")?.asBoolean() == true
        val paper = json.first().let { raw -> raw.present("isOnlineOnly")?.asBoolean() != true &&
            (raw.present("availability")?.jsonArray?.any { it.asText() == "paper" } != false) }
        fun primaryName(name: String) = printing.name.equals(name, ignoreCase = true) ||
            json.first().present("faceName")?.asText()?.equals(name, ignoreCase = true) == true
    }

    private fun sameCard(candidates: List<Candidate>): Boolean {
        if (candidates.isEmpty()) return false
        // Known, conflicting oracle identities are never merged by their display name. Missing
        // oracle metadata can fall back to matching the complete name and all explicit faces.
        val known = candidates.map { it.oracleIds }.filter { it.isNotEmpty() }.distinct()
        if (known.size > 1) return false
        if (known.size == 1 && candidates.all { it.oracleIds.isNotEmpty() }) return true
        return candidates.map { it.shape }.distinct().size == 1
    }

    private val defaultPrinting = compareByDescending<Candidate> { it.english }
        .thenBy { it.promo }.thenByDescending { it.paper }
        .thenByDescending { it.printing.releaseDate }
        .thenComparator { a, b -> compareCollectorNumbers(a.printing.number, b.printing.number) }
        .thenBy { it.printing.number }.thenBy { it.printing.set }.thenBy { it.printing.id }
}

/** Numeric ordering (2 before 10), with deterministic handling of provider suffixes like 12a. */
internal fun compareCollectorNumbers(a: String, b: String): Int {
    val parts = Regex("[0-9]+|[^0-9]+")
    val left = parts.findAll(a).map { it.value }.toList()
    val right = parts.findAll(b).map { it.value }.toList()
    for ((x, y) in left.zip(right)) {
        val compared = if (x.first() in '0'..'9' && y.first() in '0'..'9') compareDigits(x, y)
            else x.compareTo(y, ignoreCase = true)
        if (compared != 0) return compared
    }
    return left.size.compareTo(right.size)
}

/** Two runs of digits compared as whole numbers of any length: leading zeros left out, then the longer is larger. */
private fun compareDigits(x: String, y: String): Int {
    val a = x.trimStart('0')
    val b = y.trimStart('0')
    return if (a.length != b.length) a.length.compareTo(b.length) else a.compareTo(b)
}

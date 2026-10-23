package org.asyncttwiz.storage

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.util.Locale

enum class DeckSection(val title: String) { MAINBOARD("Mainboard"), SIDEBOARD("Sideboard") }
data class ImportIssue(val line: Int, val message: String)
data class ImportLine(val line: Int, val section: DeckSection, val quantity: Int,
    val name: String, val set: String?, val number: String?)
data class ParsedDeck(val text: String, val lines: List<ImportLine>, val issues: List<ImportIssue>)
data class ResolvedDeckEntry(val section: DeckSection, val quantity: Int,
    val faces: List<CatalogPrinting>, val sourceLines: List<Int>) {
    val printing get() = faces.first()
}
data class DeckImportResult(val text: String, val entries: List<ResolvedDeckEntry>,
    val issues: List<ImportIssue>, val catalog: CatalogMetadata?) {
    val resolved get() = issues.isEmpty() && entries.isNotEmpty()
    fun count(section: DeckSection): Long = entries.filter { it.section == section }.sumOf { it.quantity.toLong() }
}

/** Common text syntax only: no legality, fuzzy substitution or silent extra-section mapping. */
object DeckTextParser {
    private val entry = Regex("([0-9]+)[xX]?\\s+(.+)")
    private val suffix = Regex("(.+?)\\s+\\(([A-Za-z0-9_-]+)\\)\\s+(\\S+)")
    fun parse(text: String): ParsedDeck {
        val lines = text.split(Regex("\\r\\n|\\n|\\r"))
        val explicit = lines.any { it.trim().lowercase(Locale.ROOT) in listOf("deck", "mainboard", "sideboard") }
        var section = DeckSection.MAINBOARD
        var sawMain = false
        val result = mutableListOf<ImportLine>()
        val issues = mutableListOf<ImportIssue>()
        lines.forEachIndexed { index, original ->
            var value = original.trim()
            val line = index + 1
            if (value.isEmpty()) {
                if (!explicit && sawMain) section = DeckSection.SIDEBOARD
                return@forEachIndexed
            }
            when (value.lowercase(Locale.ROOT)) {
                "deck", "mainboard" -> { section = DeckSection.MAINBOARD; return@forEachIndexed }
                "sideboard" -> { section = DeckSection.SIDEBOARD; return@forEachIndexed }
            }
            var target = section
            if (value.startsWith("SB:", ignoreCase = true)) {
                target = DeckSection.SIDEBOARD; value = value.substring(3).trimStart()
            }
            val match = entry.matchEntire(value)
            val quantity = match?.groupValues?.get(1)?.toIntOrNull()
            if (match == null) {
                issues += ImportIssue(line, "Use quantity Card Name, Deck or Sideboard. Unsupported section or entry: $value")
            } else if (quantity == null || quantity <= 0) {
                issues += ImportIssue(line, "Quantity must be a positive whole number no greater than ${Int.MAX_VALUE}.")
            } else {
                val name = match.groupValues[2].trimEnd()
                val printing = suffix.matchEntire(name)
                result += ImportLine(line, target, quantity, printing?.groupValues?.get(1) ?: name,
                    printing?.groupValues?.get(2)?.uppercase(Locale.ROOT), printing?.groupValues?.get(3))
                if (target == DeckSection.MAINBOARD) sawMain = true
            }
        }
        if (result.isEmpty() && issues.isEmpty()) issues += ImportIssue(1, "Paste at least one quantity and card name.")
        return ParsedDeck(text, result, issues)
    }
}

/** Worker-only resolution on the installed, disk-backed catalog; complete raw face JSON is retained. */
class DeckImportResolver(private val repository: CatalogRepository) {
    fun resolve(text: String): DeckImportResult = repository.snapshot { catalog ->
        val parsed = DeckTextParser.parse(text)
        val issues = parsed.issues.toMutableList()
        val entries = linkedMapOf<Pair<DeckSection, String>, ResolvedDeckEntry>()
        val metadata = catalog?.metadata()
        if (catalog == null) {
            issues += ImportIssue(0, "Card records are not installed. Download card records, then retry lookup.")
        } else for (line in parsed.lines) {
            check(!Thread.currentThread().isInterrupted)
            // Page through every matching printing: prolific cards must not become ambiguous just
            // because they exceed a lookup page, nor may a later, different identity be ignored.
            val found = buildList {
                var after: String? = null
                do {
                    check(!Thread.currentThread().isInterrupted)
                    val page = catalog.find(line.name, line.set, line.number, 256, after)
                    addAll(page)
                    after = page.lastOrNull()?.id
                } while (page.size == 256)
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
            val ambiguous = if (line.set != null) candidates.size != 1 else !sameCard(candidates)
            if (ambiguous) {
                issues += ImportIssue(line.line, if (found.isEmpty())
                    "${line.name}${line.set?.let { " ($it) ${line.number}" }.orEmpty()} could not be resolved. Check the name/printing or update card records."
                    else "${line.name} is ambiguous. Include an exact (SET) collector-number.")
                continue
            }
            val faces = candidates.minWith(defaultPrinting).faces
            val key = line.section to faces.first().id
            val previous = entries[key]
            val quantity = (previous?.quantity?.toLong() ?: 0) + line.quantity
            if (quantity > Int.MAX_VALUE) {
                issues += ImportIssue(line.line, "Combined quantity for ${line.name} exceeds ${Int.MAX_VALUE}.")
            } else entries[key] = ResolvedDeckEntry(line.section, quantity.toInt(), faces,
                previous?.sourceLines.orEmpty() + line.line)
        }
        // A partial resolution is useful only as diagnostics, never as a preview/save payload.
        DeckImportResult(text, if (issues.isEmpty()) entries.values.toList() else emptyList(), issues, metadata)
    }

    private class Candidate(val faces: List<CatalogPrinting>) {
        val printing = faces.first()
        val json: List<JsonObject> = faces.map { JsonParser.parseString(it.json).asJsonObject }
        val oracleIds = json.mapNotNull { it.get("identifiers")?.takeUnless { it.isJsonNull }?.asJsonObject
            ?.get("scryfallOracleId")?.takeUnless { it.isJsonNull }?.asString }
            .distinct().sorted()
        val shape = faces.zip(json).map { (face, raw) ->
            listOf(face.name.lowercase(Locale.ROOT), raw.get("faceName")?.takeUnless { it.isJsonNull }?.asString?.lowercase(Locale.ROOT).orEmpty(),
                face.side, face.token.toString())
        }
        val english = printing.language.equals("English", ignoreCase = true)
        val promo = json.first().get("isPromo")?.takeUnless { it.isJsonNull }?.asBoolean == true
        val paper = json.first().let { raw -> raw.get("isOnlineOnly")?.takeUnless { it.isJsonNull }?.asBoolean != true &&
            (raw.get("availability")?.takeUnless { it.isJsonNull }?.asJsonArray?.any { it.asString == "paper" } != false) }
        fun primaryName(name: String) = printing.name.equals(name, ignoreCase = true) ||
            json.first().get("faceName")?.takeUnless { it.isJsonNull }?.asString?.equals(name, ignoreCase = true) == true
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

    /** Numeric ordering (2 before 10), with deterministic handling of provider suffixes like 12a. */
    private fun compareCollectorNumbers(a: String, b: String): Int {
        val parts = Regex("[0-9]+|[^0-9]+")
        val left = parts.findAll(a).map { it.value }.toList()
        val right = parts.findAll(b).map { it.value }.toList()
        for ((x, y) in left.zip(right)) {
            val compared = if (x.first() in '0'..'9' && y.first() in '0'..'9') x.toBigInteger().compareTo(y.toBigInteger())
                else x.compareTo(y, ignoreCase = true)
            if (compared != 0) return compared
        }
        return left.size.compareTo(right.size)
    }
}

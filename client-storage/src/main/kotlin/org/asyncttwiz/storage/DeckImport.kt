package org.asyncttwiz.storage

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.util.Locale
import org.asyncttwiz.protocol.DeckValidation

enum class DeckSection(val title: String) { MAINBOARD("Mainboard"), SIDEBOARD("Sideboard") }
data class ImportIssue(val line: Int, val message: String)
data class ImportLine(val line: Int, val section: DeckSection, val quantity: Int,
    val name: String, val set: String?, val number: String?, val scryfallId: String? = null) {
    /** " (SET) number", " (SET)" or empty, in the list syntax the parser accepts back. */
    val printingSuffix: String get() = set?.let { " ($it)" + number?.let { n -> " $n" }.orEmpty() }.orEmpty()
}
data class ParsedDeck(val text: String, val lines: List<ImportLine>, val issues: List<ImportIssue>)
data class ResolvedDeckEntry(val section: DeckSection, val quantity: Int,
    val faces: List<CatalogPrinting>, val sourceLines: List<Int>) {
    val printing get() = faces.first()
}
data class DeckImportResult(val text: String, val entries: List<ResolvedDeckEntry>,
    val issues: List<ImportIssue>, val catalog: CatalogMetadata?, val substitutions: List<ImportIssue> = emptyList()) {
    val resolved get() = issues.isEmpty() && entries.isNotEmpty()
    fun count(section: DeckSection): Long = entries.filter { it.section == section }.sumOf { it.quantity.toLong() }
}

/**
 * Common list syntaxes only: no legality or fuzzy substitution. Accepts Arena/MTGO/Moxfield/Archidekt/
 * MTGGoldfish text, Forge ([Main], Name|SET), XMage ([SET:number] Name) and CSV exports with a name column.
 * Commander/Companion sections join the main deck; maybeboard/considering and metadata sections are skipped.
 */
object DeckTextParser {
    private enum class Target { MAIN, SIDE, SKIP }
    private val entry = Regex("([0-9]+)[xX]?\\s+(.+)")
    private val suffix = Regex("(.+?)\\s+\\(([A-Za-z0-9_-]+)\\)(?:\\s+(\\S+))?")
    private val xmage = Regex("\\[([A-Za-z0-9_-]+):([^\\]\\s]+)\\]\\s*(.+)")
    private val trailingTag = Regex("\\s*(?:\\[[^\\]]*\\]|\\^[^\\^]*\\^|<[^>]*>|\\*[A-Za-z]+\\*)$")
    private val bracketHeader = Regex("\\[([A-Za-z ]+)\\]")
    private val headerCount = Regex("\\s*\\(\\s*[0-9]+\\s*\\)$")
    private val setCode = Regex("[A-Za-z0-9_-]{1,16}")
    private val singleSlash = Regex("\\s*(?<!/)/(?!/)\\s*")
    private val uuid = Regex("[0-9a-f]{8}(?:-[0-9a-f]{4}){3}-[0-9a-f]{12}")
    private val headers = mapOf(
        "deck" to Target.MAIN, "main" to Target.MAIN, "mainboard" to Target.MAIN, "main deck" to Target.MAIN,
        "commander" to Target.MAIN, "commanders" to Target.MAIN, "companion" to Target.MAIN, "companions" to Target.MAIN,
        "sideboard" to Target.SIDE, "side" to Target.SIDE,
        "maybeboard" to Target.SKIP, "maybe" to Target.SKIP, "considering" to Target.SKIP,
        "about" to Target.SKIP, "metadata" to Target.SKIP)

    private sealed interface Kind
    private data class Header(val target: Target) : Kind
    private data object Comment : Kind
    /** A section header, an ignored comment/metadata line, or null for an entry. */
    private fun kind(value: String): Kind? {
        bracketHeader.matchEntire(value)?.let { return Header(headers[it.groupValues[1].trim().lowercase(Locale.ROOT)] ?: Target.SKIP) }
        val prefixed = value.startsWith("#") || value.startsWith("//")
        val title = value.trimStart('#', '/').trim().removeSuffix(":").replace(headerCount, "").trim().lowercase(Locale.ROOT)
        headers[title]?.let { return Header(it) }
        return if (prefixed || value.startsWith("NAME:") || value.startsWith("LAYOUT ")) Comment else null
    }

    fun parse(text: String, maxCards: Int = DeckValidation.MAX_CARDS, includeSideboard: Boolean = true,
        bareNames: Boolean = false): ParsedDeck {
        val lines = text.split(Regex("\\r\\n|\\n|\\r"))
        val result = mutableListOf<ImportLine>()
        val issues = mutableListOf<ImportIssue>()
        var total = 0L
        fun add(line: Int, target: Target, quantity: Int?, name: String, set: String?, number: String?, scryfall: String?) {
            if (target == Target.SKIP || (!includeSideboard && target == Target.SIDE)) return
            if (quantity == null || quantity !in 1..maxCards) {
                issues += ImportIssue(line, "Quantity must be a whole number from 1 to $maxCards.")
            } else if (total + quantity > maxCards) {
                issues += ImportIssue(line, "A list may contain at most $maxCards cards including sideboard.")
            } else if (name.isBlank()) {
                issues += ImportIssue(line, "Missing card name.")
            } else {
                total += quantity
                result += ImportLine(line, if (target == Target.SIDE) DeckSection.SIDEBOARD else DeckSection.MAINBOARD,
                    quantity, name.replace(singleSlash, " // "), set?.uppercase(Locale.ROOT), number, scryfall)
            }
        }
        val first = lines.firstOrNull { it.isNotBlank() }
        val header = first?.let { csvRecords(it).firstOrNull()?.second }.orEmpty().map { it.trim().lowercase(Locale.ROOT) }
        if (header.size > 1 && header.any { it in csvName }) {
            parseCsv(text, ::add)
        } else {
            val kinds = lines.map { it.trim().takeIf(String::isNotEmpty)?.let(::kind) }
            val explicit = kinds.any { it is Header }
            // MTGO: without any section header, exactly two blank-separated blocks are main and sideboard.
            val blockOf = IntArray(lines.size)
            var blocks = 0
            var inBlock = false
            lines.forEachIndexed { index, value ->
                if (value.isBlank()) inBlock = false
                else if (kinds[index] == null) { if (!inBlock) { blocks++; inBlock = true }; blockOf[index] = blocks }
            }
            var section = Target.MAIN
            lines.forEachIndexed { index, original ->
                var value = original.trim()
                val line = index + 1
                if (value.isEmpty()) return@forEachIndexed
                when (val kind = kinds[index]) {
                    is Header -> { section = kind.target; return@forEachIndexed }
                    Comment -> return@forEachIndexed
                    null -> {}
                }
                var target = if (!explicit && blocks == 2 && blockOf[index] == 2) Target.SIDE else section
                if (value.startsWith("SB:", ignoreCase = true)) {
                    if (target != Target.SKIP) target = Target.SIDE
                    value = value.substring(3).trimStart()
                }
                if (target == Target.SKIP || (!includeSideboard && target == Target.SIDE)) return@forEachIndexed
                val match = entry.matchEntire(value)
                if (match == null && !bareNames) {
                    issues += ImportIssue(line, "Use quantity Card Name, Deck or Sideboard. Unsupported section or entry: $value")
                    return@forEachIndexed
                }
                val (name, set, number) = card(match?.groupValues?.get(2) ?: value)
                add(line, target, if (match == null) 1 else match.groupValues[1].toIntOrNull(), name, set, number, null)
            }
        }
        if (result.isEmpty() && issues.isEmpty()) issues += ImportIssue(1, "Paste at least one quantity and card name.")
        return ParsedDeck(text, result, issues)
    }

    /** Name plus optional printing from "[SET:n] Name", "Name|SET" or "Name (SET) n"; finish/category tags are ignored. */
    private fun card(value: String): Triple<String, String?, String?> {
        var rest = value.trim()
        var set: String? = null
        var number: String? = null
        xmage.matchEntire(rest)?.let { set = it.groupValues[1]; number = it.groupValues[2]; rest = it.groupValues[3].trim() }
        while (true) {
            val stripped = rest.replace(trailingTag, "").trim()
            if (stripped == rest || stripped.isEmpty()) break
            rest = stripped
        }
        if ('|' in rest) {
            val parts = rest.split('|')
            rest = parts[0].trim()
            if (set == null) set = parts[1].trim().takeIf { setCode.matches(it) }
        } else if (set == null) suffix.matchEntire(rest)?.let {
            rest = it.groupValues[1].trim(); set = it.groupValues[2]; number = it.groupValues[3].takeIf(String::isNotEmpty)
        }
        return Triple(rest, set, number)
    }

    private val csvName = setOf("name", "card name", "card")
    private val csvCount = setOf("count", "quantity", "qty", "amount")
    private val csvSet = setOf("set", "set code", "edition", "edition code")
    private val csvNumber = setOf("collector number", "collector_number", "collectornumber", "number", "cn", "card number")
    private val csvScryfall = setOf("scryfall id", "scryfall_id", "scryfallid")
    private val csvBoard = setOf("board", "section")

    private fun parseCsv(text: String, add: (Int, Target, Int?, String, String?, String?, String?) -> Unit) {
        val records = csvRecords(text)
        val columns = records.first().second.map { it.trim().lowercase(Locale.ROOT) }
        fun column(names: Set<String>) = columns.indexOfFirst { it in names }.takeIf { it >= 0 }
        val name = checkNotNull(column(csvName))
        val count = column(csvCount)
        val maybe = column(setOf("maybeboard"))
        for ((line, fields) in records.drop(1)) {
            if (fields.all(String::isBlank)) continue
            fun field(index: Int?) = index?.let(fields::getOrNull)?.trim()?.takeIf(String::isNotEmpty)
            val target = if (field(maybe).equals("true", ignoreCase = true)) Target.SKIP
                else headers[field(column(csvBoard))?.lowercase(Locale.ROOT).orEmpty()] ?: Target.MAIN
            add(line, target, if (count == null) 1 else field(count)?.toIntOrNull(), field(name).orEmpty(),
                field(column(csvSet))?.takeIf { setCode.matches(it) }, field(column(csvNumber)),
                field(column(csvScryfall))?.lowercase(Locale.ROOT)?.takeIf { uuid.matches(it) })
        }
    }

    /** RFC 4180 records (quoted commas, doubled quotes and line breaks) with their 1-based first line. */
    private fun csvRecords(text: String): List<Pair<Int, List<String>>> {
        val records = mutableListOf<Pair<Int, List<String>>>()
        var fields = mutableListOf<String>()
        val field = StringBuilder()
        var quoted = false
        var line = 1
        var start = 1
        var index = 0
        fun endRecord() {
            fields += field.toString(); field.clear()
            if (fields.size > 1 || fields.single().isNotBlank()) records += start to fields
            fields = mutableListOf()
        }
        while (index < text.length) {
            val c = text[index]
            when {
                quoted && c == '"' && text.getOrNull(index + 1) == '"' -> { field.append('"'); index++ }
                c == '"' -> quoted = !quoted
                quoted -> { field.append(c); if (c == '\n') line++ }
                c == ',' -> { fields += field.toString(); field.clear() }
                c == '\r' || c == '\n' -> {
                    if (c == '\r' && text.getOrNull(index + 1) == '\n') index++
                    endRecord(); line++; start = line
                }
                else -> field.append(c)
            }
            index++
        }
        if (field.isNotEmpty() || fields.isNotEmpty()) endRecord()
        return records
    }
}

/** Worker-only resolution on the installed, disk-backed catalog; exact face text/defaults are retained. */
class DeckImportResolver(private val repository: CatalogRepository) {
    fun resolve(text: String): DeckImportResult = resolve(DeckTextParser.parse(text))
    fun resolve(parsed: ParsedDeck, maxCards: Int = DeckValidation.MAX_CARDS,
        allowSubstitutions: Boolean = false): DeckImportResult = repository.snapshot { catalog ->
        val issues = parsed.issues.toMutableList()
        val substitutions = mutableListOf<ImportIssue>()
        val entries = linkedMapOf<Pair<DeckSection, String>, ResolvedDeckEntry>()
        val metadata = catalog?.metadata()
        if (catalog == null) {
            issues += ImportIssue(0, "Card records are not installed. Download card records, then retry lookup.")
        } else for (line in parsed.lines) {
            check(!Thread.currentThread().isInterrupted)
            // Page through every matching printing: prolific cards must not become ambiguous just
            // because they exceed a lookup page, nor may a later, different identity be ignored.
            fun find(set: String?, number: String?) = buildList {
                var after: String? = null
                do {
                    check(!Thread.currentThread().isInterrupted)
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

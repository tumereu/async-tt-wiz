package org.asyncttwiz.storage

import org.asyncttwiz.protocol.DeckValidation

enum class DeckSection(val title: String) { MAINBOARD("Mainboard"), SIDEBOARD("Sideboard"), MAYBEBOARD("Maybeboard") }
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
 * Commander/Companion sections join the main deck; maybeboard/considering go to Maybe; metadata sections are skipped.
 */
object DeckTextParser {
    private enum class Target { MAIN, SIDE, MAYBE, SKIP }
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
        "maybeboard" to Target.MAYBE, "maybe" to Target.MAYBE, "considering" to Target.MAYBE,
        "about" to Target.SKIP, "metadata" to Target.SKIP)

    private sealed interface Kind
    private data class Header(val target: Target) : Kind
    private data object Comment : Kind
    /** A section header, an ignored comment/metadata line, or null for an entry. */
    private fun kind(value: String): Kind? {
        bracketHeader.matchEntire(value)?.let { return Header(headers[it.groupValues[1].trim().lowercase()] ?: Target.SKIP) }
        val prefixed = value.startsWith("#") || value.startsWith("//")
        val title = value.trimStart('#', '/').trim().removeSuffix(":").replace(headerCount, "").trim().lowercase()
        headers[title]?.let { return Header(it) }
        return if (prefixed || value.startsWith("NAME:") || value.startsWith("LAYOUT ")) Comment else null
    }

    /** [sideboard] is where sideboard lines go: Side for decks, Maybe for cubes (which have no Side). */
    fun parse(text: String, maxCards: Int = DeckValidation.MAX_CARDS, sideboard: DeckSection = DeckSection.SIDEBOARD,
        bareNames: Boolean = false): ParsedDeck {
        val lines = text.split(Regex("\\r\\n|\\n|\\r"))
        val result = mutableListOf<ImportLine>()
        val issues = mutableListOf<ImportIssue>()
        var total = 0L
        fun add(line: Int, target: Target, quantity: Int?, name: String, set: String?, number: String?, scryfall: String?) {
            if (target == Target.SKIP) return
            if (quantity == null || quantity !in 1..maxCards) {
                issues += ImportIssue(line, "Quantity must be a whole number from 1 to $maxCards.")
            } else if (total + quantity > maxCards) {
                issues += ImportIssue(line, "A list may contain at most $maxCards cards in all sections.")
            } else if (name.isBlank()) {
                issues += ImportIssue(line, "Missing card name.")
            } else {
                total += quantity
                result += ImportLine(line, when (target) {
                        Target.SIDE -> sideboard
                        Target.MAYBE -> DeckSection.MAYBEBOARD
                        else -> DeckSection.MAINBOARD
                    },
                    quantity, name.replace(singleSlash, " // "), set?.uppercase(), number, scryfall)
            }
        }
        val first = lines.firstOrNull { it.isNotBlank() }
        val header = first?.let { csvRecords(it).firstOrNull()?.second }.orEmpty().map { it.trim().lowercase() }
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
                    if (target == Target.MAIN) target = Target.SIDE
                    value = value.substring(3).trimStart()
                }
                if (target == Target.SKIP) return@forEachIndexed
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
        val columns = records.first().second.map { it.trim().lowercase() }
        fun column(names: Set<String>) = columns.indexOfFirst { it in names }.takeIf { it >= 0 }
        val name = checkNotNull(column(csvName))
        val count = column(csvCount)
        val maybe = column(setOf("maybeboard"))
        for ((line, fields) in records.drop(1)) {
            if (fields.all(String::isBlank)) continue
            fun field(index: Int?) = index?.let(fields::getOrNull)?.trim()?.takeIf(String::isNotEmpty)
            val target = if (field(maybe).equals("true", ignoreCase = true)) Target.MAYBE
                else headers[field(column(csvBoard))?.lowercase().orEmpty()] ?: Target.MAIN
            add(line, target, if (count == null) 1 else field(count)?.toIntOrNull(), field(name).orEmpty(),
                field(column(csvSet))?.takeIf { setCode.matches(it) }, field(column(csvNumber)),
                field(column(csvScryfall))?.lowercase()?.takeIf { uuid.matches(it) })
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

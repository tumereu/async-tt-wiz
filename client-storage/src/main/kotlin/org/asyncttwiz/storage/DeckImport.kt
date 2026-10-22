package org.asyncttwiz.storage

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
            val found = catalog.find(line.name, line.set, line.number, 1000)
            // A full multi-face name produces one candidate per explicit face; those are one printing,
            // not an ambiguity. Distinct printings are never chosen by name/order/availability.
            val groups = found.groupBy { (it.otherFaces + it.id).sorted().joinToString("|") }
            if (groups.size != 1 || found.size == 1000) {
                issues += ImportIssue(line.line, if (found.isEmpty())
                    "${line.name}${line.set?.let { " ($it) ${line.number}" }.orEmpty()} could not be resolved. Check the name/printing or update card records."
                    else "${line.name} is ambiguous. Include an exact (SET) collector-number.")
                continue
            }
            val candidate = found.first()
            val faces = (candidate.otherFaces + candidate.id).map { id -> checkNotNull(catalog.printing(id)) }
                .sortedWith(compareBy<CatalogPrinting> { it.side }.thenBy { it.id })
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
}

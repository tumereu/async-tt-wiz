package org.asyncttwiz.storage

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.util.Locale
import org.asyncttwiz.protocol.CubeValidation

class CubeImportResolver(repository: CatalogRepository) {
    private val resolver = DeckImportResolver(repository)
    fun resolve(text: String, includeSideboard: Boolean = false): DeckImportResult = resolve(parse(text, includeSideboard))
    fun resolve(parsed: ParsedDeck): DeckImportResult = resolver.resolve(parsed.copy(lines = parsed.lines.map {
        it.copy(section = DeckSection.MAINBOARD)
    }), CubeValidation.MAX_CARDS, allowSubstitutions = true)
    companion object {
        /** Cube lists may also be bare card names, one copy per line (Cube Cobra plaintext export). */
        fun parse(text: String, includeSideboard: Boolean = false): ParsedDeck =
            DeckTextParser.parse(text, CubeValidation.MAX_CARDS, includeSideboard, bareNames = true)
    }
}

/** Pure conversion, independent of HTTP. Every mainboard record is resolved or reported, never dropped. */
object CubeCobraImport {
    // cubecobra.com/cube/<page>/<id> or /cube/api/cubeJSON/<id>, anywhere in shared text; scheme and www optional.
    private val link = Regex("(?i)(?:https?://)?(?<![A-Za-z0-9.@-])(?:www\\.)?cubecobra\\.com/cube/(?:api/cubejson|[A-Za-z0-9_-]+)/([A-Za-z0-9_-]+)(?![A-Za-z0-9_-])[^\\s]*")

    /** The first Cube Cobra cube link in [text], as written, or null. */
    fun link(text: String?): String? = text?.let { link.find(it)?.value }

    fun cubeId(value: String): String {
        val trimmed = value.trim()
        val id = link.find(trimmed)?.groupValues?.get(1) ?: trimmed
        require(CubeValidation.provenance(id)) { "Enter a Cube Cobra URL or cube id." }
        return id
    }
    fun parse(json: String): ParsedDeck {
        val root = JsonParser.parseString(json).asJsonObject
        val cards = root.getAsJsonObject("cards")?.getAsJsonArray("mainboard")
            ?: error("Cube Cobra response has no mainboard.")
        val lines = mutableListOf<ImportLine>()
        val issues = mutableListOf<ImportIssue>()
        val text = mutableListOf<String>()
        cards.forEachIndexed { index, element ->
            val line = index + 1
            try {
                val card = element.asJsonObject
                val details = card.getAsJsonObject("details") ?: card
                val scryfall = details.string("scryfall_id") ?: details.string("scryfallId") ?: details.string("id") ?: card.string("cardID")
                val name = details.string("name") ?: card.string("name") ?: scryfall?.let { "Scryfall $it" } ?: error("missing card name or printing id")
                val quantity = card.get("quantity")?.asString?.toIntOrNull() ?: if (card.has("quantity")) 0 else 1
                require(quantity in 1..CubeValidation.MAX_CARDS)
                val set = (details.string("set") ?: details.string("setCode"))?.uppercase(Locale.ROOT)
                val number = details.string("collector_number") ?: details.string("collectorNumber")
                text += "$quantity $name" + if (set != null && number != null) " ($set) $number" else ""
                lines += ImportLine(line, DeckSection.MAINBOARD, quantity, name, set.takeIf { number != null }, number.takeIf { set != null }, scryfall)
            } catch (_: Exception) {
                text += element.toString()
                issues += ImportIssue(line, "Invalid Cube Cobra mainboard card: $element")
            }
        }
        if (lines.sumOf { it.quantity.toLong() } > CubeValidation.MAX_CARDS) issues += ImportIssue(0, "A cube may contain at most 3000 copies.")
        if (cards.isEmpty) issues += ImportIssue(0, "Cube Cobra mainboard is empty.")
        return ParsedDeck(text.joinToString("\n"), lines, issues)
    }
    private fun JsonObject.string(key: String) = get(key)?.takeUnless { it.isJsonNull }?.asString?.takeIf { it.isNotBlank() }
}

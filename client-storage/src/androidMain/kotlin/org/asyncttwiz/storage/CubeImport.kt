package org.asyncttwiz.storage

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.util.Locale
import org.asyncttwiz.protocol.CubeValidation

class CubeImportResolver(repository: CatalogRepository) : CubeResolver {
    private val resolver = DeckImportResolver(repository)
    override fun resolve(text: String): DeckImportResult = resolve(parse(text))
    /** Cubes have Main and Maybe only: anything else becomes Maybe. */
    override fun resolve(parsed: ParsedDeck): DeckImportResult = resolver.resolve(parsed.copy(lines = parsed.lines.map {
        if (it.section == DeckSection.MAINBOARD) it else it.copy(section = DeckSection.MAYBEBOARD)
    }), CubeValidation.MAX_CARDS, allowSubstitutions = true)
    companion object {
        /** Cube lists may also be bare card names, one copy per line (Cube Cobra plaintext export).
         * Sideboard lines go to Maybe, as do maybeboard sections. */
        fun parse(text: String): ParsedDeck = parseCubeList(text)
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
    /** Mainboard to Main and maybeboard to Maybe (either may be empty); the cube's basics (a separate list) are ignored.
     * The text lists Main first, then a "Maybeboard" header and Maybe; line numbers are text lines. */
    fun parse(json: String): ParsedDeck {
        val root = JsonParser.parseString(json).asJsonObject
        val boards = root.getAsJsonObject("cards")
        boards ?: error("Cube Cobra response has no card lists.")
        val cards = boards.get("mainboard")?.takeIf { it.isJsonArray }?.asJsonArray ?: com.google.gson.JsonArray()
        val maybe = boards.get("maybeboard")?.takeIf { it.isJsonArray }?.asJsonArray
        val lines = mutableListOf<ImportLine>()
        val issues = mutableListOf<ImportIssue>()
        val text = mutableListOf<String>()
        fun board(section: DeckSection, records: Iterable<com.google.gson.JsonElement>, label: String) = records.forEach { element ->
            val line = text.size + 1
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
                lines += ImportLine(line, section, quantity, name, set.takeIf { number != null }, number.takeIf { set != null }, scryfall)
            } catch (_: Exception) {
                text += element.toString()
                issues += ImportIssue(line, "Invalid Cube Cobra $label card: $element")
            }
        }
        board(DeckSection.MAINBOARD, cards, "mainboard")
        if (maybe != null && !maybe.isEmpty) {
            text += DeckSection.MAYBEBOARD.title
            board(DeckSection.MAYBEBOARD, maybe, "maybeboard")
        }
        if (lines.sumOf { it.quantity.toLong() } > CubeValidation.MAX_CARDS) issues += ImportIssue(0, "A cube may contain at most 3000 copies.")
        // A partial cube with only a maybeboard imports as Maybe; only a cube with no cards at all is refused.
        if (lines.isEmpty() && issues.isEmpty()) issues += ImportIssue(0, "This Cube Cobra cube has no cards.")
        return ParsedDeck(text.joinToString("\n"), lines, issues)
    }
    private fun JsonObject.string(key: String) = get(key)?.takeUnless { it.isJsonNull }?.asString?.takeIf { it.isNotBlank() }
}

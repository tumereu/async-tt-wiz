package org.asyncttwiz.storage

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import org.asyncttwiz.protocol.CubeValidation

class CubeImportResolver(repository: CatalogSnapshots) : CubeResolver {
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
    // Case-insensitive by option, not an inline (?i): the browser's regular expressions have no inline flags.
    private val link = Regex("(?:https?://)?(?<![A-Za-z0-9.@-])(?:www\\.)?cubecobra\\.com/cube/(?:api/cubejson|[A-Za-z0-9_-]+)/([A-Za-z0-9_-]+)(?![A-Za-z0-9_-])[^\\s]*",
        RegexOption.IGNORE_CASE)

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
        val root = jsonObject(json)
        // Lists that aren't objects or arrays read as before: "cards" of another kind fails, other boards are empty
        val boards = root["cards"]?.let { it as JsonObject }
        boards ?: error("Cube Cobra response has no card lists.")
        val cards = boards.array("mainboard") ?: JsonArray(emptyList())
        val maybe = boards.array("maybeboard")
        val lines = mutableListOf<ImportLine>()
        val issues = mutableListOf<ImportIssue>()
        val text = mutableListOf<String>()
        fun board(section: DeckSection, records: Iterable<JsonElement>, label: String) = records.forEach { element ->
            val line = text.size + 1
            try {
                val card = element.jsonObject
                val details = card["details"]?.let { it as JsonObject } ?: card
                val scryfall = details.string("scryfall_id") ?: details.string("scryfallId") ?: details.string("id") ?: card.string("cardID")
                val name = details.string("name") ?: card.string("name") ?: scryfall?.let { "Scryfall $it" } ?: error("missing card name or printing id")
                val quantity = card["quantity"]?.let(::text)?.toIntOrNull() ?: if ("quantity" in card) 0 else 1
                require(quantity in 1..CubeValidation.MAX_CARDS)
                val set = (details.string("set") ?: details.string("setCode"))?.uppercase()
                val number = details.string("collector_number") ?: details.string("collectorNumber")
                text += "$quantity $name" + if (set != null && number != null) " ($set) $number" else ""
                lines += ImportLine(line, section, quantity, name, set.takeIf { number != null }, number.takeIf { set != null }, scryfall)
            } catch (_: Exception) {
                text += element.toString()
                issues += ImportIssue(line, "Invalid Cube Cobra $label card: $element")
            }
        }
        board(DeckSection.MAINBOARD, cards, "mainboard")
        if (maybe != null && maybe.isNotEmpty()) {
            text += DeckSection.MAYBEBOARD.title
            board(DeckSection.MAYBEBOARD, maybe, "maybeboard")
        }
        if (lines.sumOf { it.quantity.toLong() } > CubeValidation.MAX_CARDS) issues += ImportIssue(0, "A cube may contain at most 3000 copies.")
        // A partial cube with only a maybeboard imports as Maybe; only a cube with no cards at all is refused.
        if (lines.isEmpty() && issues.isEmpty()) issues += ImportIssue(0, "This Cube Cobra cube has no cards.")
        return ParsedDeck(text.joinToString("\n"), lines, issues)
    }

    private fun JsonObject.string(key: String) = this[key]?.takeUnless { it is JsonNull }?.let(::text)?.takeIf { it.isNotBlank() }

    /** A value read as text the way the import always has: a string, number or boolean, or a list of exactly one of them;
     * anything else (null, an object, a longer list) is no card. */
    private fun text(value: JsonElement): String = when (value) {
        is JsonNull -> throw IllegalStateException("null where text was expected")
        is JsonPrimitive -> value.content
        is JsonArray -> text(value.singleOrNull() ?: throw IllegalStateException("A list where text was expected"))
        is JsonObject -> throw IllegalStateException("An object where text was expected")
    }
}

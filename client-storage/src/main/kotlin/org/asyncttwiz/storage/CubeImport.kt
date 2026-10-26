package org.asyncttwiz.storage

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.net.URI
import java.util.Locale
import org.asyncttwiz.protocol.CubeValidation

class CubeImportResolver(repository: CatalogRepository) {
    private val resolver = DeckImportResolver(repository)
    fun resolve(text: String, includeSideboard: Boolean = false): DeckImportResult = resolve(
        DeckTextParser.parse(text, CubeValidation.MAX_CARDS, includeSideboard))
    fun resolve(parsed: ParsedDeck): DeckImportResult = resolver.resolve(parsed.copy(lines = parsed.lines.map {
        it.copy(section = DeckSection.MAINBOARD)
    }), CubeValidation.MAX_CARDS, allowSubstitutions = true)
}

/** Pure conversion, independent of HTTP. Every mainboard record is resolved or reported, never dropped. */
object CubeCobraImport {
    fun cubeId(value: String): String {
        val trimmed = value.trim()
        val id = if (trimmed.contains("://")) {
            val uri = URI(trimmed)
            require(uri.scheme == "https" && uri.host in listOf("cubecobra.com", "www.cubecobra.com") && uri.userInfo == null)
            val parts = uri.path.trim('/').split('/')
            require(parts.size >= 3 && parts[0] == "cube" && parts[1] in listOf("overview", "list", "analysis", "blog", "playtest", "edit", "api"))
            if (parts[1] == "api") { require(parts.size == 4 && parts[2] == "cubeJSON"); parts[3] } else parts[2]
        } else trimmed
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

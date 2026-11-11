package org.asyncttwiz.android

import org.asyncttwiz.appui.parseUri
import kotlinx.serialization.json.*
import org.asyncttwiz.protocol.DeckValidation
import org.asyncttwiz.storage.*

enum class DeckImportSite(val label: String) { ARCHIDEKT("Archidekt") }
data class DeckImportLocation(val site: DeckImportSite, val id: String) {
    val endpoint: String get() = when (site) {
        DeckImportSite.ARCHIDEKT -> "https://archidekt.com/api/decks/$id/"
    }
}
data class ImportedDeck(val name: String, val parsed: ParsedDeck)

/** Provider URLs are identifiers only: requests always use a fixed public HTTPS origin. */
object DeckUrlImport {
    fun location(input: String): DeckImportLocation? = runCatching {
        val value = input.trim()
        val site: DeckImportSite
        val id: String
        if (value.contains("://")) {
            val uri = parseUri(value)
            require(requireNotNull(uri.scheme).lowercase() in listOf("http", "https") && uri.rawUserInfo == null && uri.port == -1)
            val host = uri.host?.lowercase()?.removePrefix("www.")
            val parts = requireNotNull(uri.path).trim('/').split('/')
            when (host) {
                "archidekt.com" -> {
                    require(parts.size >= 2 && parts[0] in listOf("decks", "deck"))
                    site = DeckImportSite.ARCHIDEKT; id = parts[1]
                }
                else -> return null
            }
        } else if (':' in value) {
            site = DeckImportSite.entries.singleOrNull { it.name.equals(value.substringBefore(':'), true) } ?: return null
            id = value.substringAfter(':')
        } else {
            return null
        }
        require(id.matches(Regex("[1-9][0-9]{0,17}")))
        DeckImportLocation(site, id)
    }.getOrNull()

    fun parse(site: DeckImportSite, body: String): ImportedDeck = when (site) {
        DeckImportSite.ARCHIDEKT -> archidekt(Json.parseToJsonElement(body).jsonObject)
    }

    /** Archidekt cards carry category names: "Maybeboard" goes to Maybe, "Sideboard" (unless also
     * "Commander") to Side, everything else to Main. */
    private fun archidekt(root: JsonObject): ImportedDeck {
        val main = mutableListOf<Pair<Int, String>>()
        val side = mutableListOf<Pair<Int, String>>()
        val maybe = mutableListOf<Pair<Int, String>>()
        for (element in root.getValue("cards").jsonArray) {
            val entry = element.jsonObject
            val categories = entry["categories"]?.jsonArray.orEmpty().map { it.jsonPrimitive.content.lowercase() }
            val card = entry.getValue("card").jsonObject.getValue("oracleCard").jsonObject
            val row = entry.getValue("quantity").jsonPrimitive.int to card.string("name")
            if ("maybeboard" in categories) maybe += row
            else if ("sideboard" in categories && "commander" !in categories) side += row else main += row
        }
        return counted(root.string("name"), main, side, maybe)
    }

    private fun JsonObject.string(key: String) = requireNotNull(getValue(key).jsonPrimitive.contentOrNull)

    private fun counted(name: String, main: List<Pair<Int, String>>, side: List<Pair<Int, String>>,
        maybe: List<Pair<Int, String>>): ImportedDeck {
        require(name.isNotBlank())
        // Validate before creating text: malformed provider entries must never turn into extra lines/sections.
        (main + side + maybe).forEach { (quantity, card) ->
            require(quantity in 1..DeckValidation.MAX_CARDS && card.isNotBlank() && '\n' !in card && '\r' !in card)
        }
        fun rows(rows: List<Pair<Int, String>>) = rows.map { "${it.first} ${it.second}" }
        val text = (listOf("Deck") + rows(main) + listOf("Sideboard") + rows(side) +
            (if (maybe.isEmpty()) emptyList() else listOf("Maybeboard") + rows(maybe))).joinToString("\n")
        return ImportedDeck(name, DeckTextParser.parse(text))
    }
}

object ImportClipboard {
    fun cube(text: String?): String? = cubeCobraLink(text) ?: text?.trim()?.takeIf { value ->
        runCatching { cubeCobraId(value) }.isSuccess
    }
    fun deck(text: String?): String? = text?.trim()?.takeIf { DeckUrlImport.location(it) != null }
}

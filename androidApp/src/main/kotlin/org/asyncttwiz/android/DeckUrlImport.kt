package org.asyncttwiz.android

import java.net.URI
import java.util.Locale
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
            val uri = URI(value)
            require(uri.scheme.lowercase(Locale.ROOT) in listOf("http", "https") && uri.rawUserInfo == null && uri.port == -1)
            val host = uri.host?.lowercase(Locale.ROOT)?.removePrefix("www.")
            val parts = uri.path.trim('/').split('/')
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

    private fun archidekt(root: JsonObject): ImportedDeck {
        val main = mutableListOf<Pair<Int, String>>()
        val side = mutableListOf<Pair<Int, String>>()
        for (element in root.getValue("cards").jsonArray) {
            val entry = element.jsonObject
            val categories = entry["categories"]?.jsonArray.orEmpty().map { it.jsonPrimitive.content.lowercase(Locale.ROOT) }
            if ("maybeboard" in categories) continue
            val card = entry.getValue("card").jsonObject.getValue("oracleCard").jsonObject
            val row = entry.getValue("quantity").jsonPrimitive.int to card.string("name")
            if ("sideboard" in categories && "commander" !in categories) side += row else main += row
        }
        return counted(root.string("name"), main, side)
    }

    private fun JsonObject.string(key: String) = requireNotNull(getValue(key).jsonPrimitive.contentOrNull)

    private fun counted(name: String, main: List<Pair<Int, String>>, side: List<Pair<Int, String>>): ImportedDeck {
        require(name.isNotBlank())
        // Validate before creating text: malformed provider entries must never turn into extra lines/sections.
        (main + side).forEach { (quantity, card) ->
            require(quantity in 1..DeckValidation.MAX_CARDS && card.isNotBlank() && '\n' !in card && '\r' !in card)
        }
        val text = (listOf("Deck") + main.map { "${it.first} ${it.second}" } + listOf("Sideboard") + side.map { "${it.first} ${it.second}" }).joinToString("\n")
        return ImportedDeck(name, DeckTextParser.parse(text))
    }
}

internal object ImportClipboard {
    fun cube(text: String?): String? = CubeCobraImport.link(text) ?: text?.trim()?.takeIf { value ->
        runCatching { CubeCobraImport.cubeId(value) }.isSuccess
    }
    fun deck(text: String?): String? = text?.trim()?.takeIf { DeckUrlImport.location(it) != null }
}

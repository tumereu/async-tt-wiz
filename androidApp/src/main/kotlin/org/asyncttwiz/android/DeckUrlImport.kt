package org.asyncttwiz.android

import java.net.URI
import java.util.Locale
import kotlinx.serialization.json.*
import org.asyncttwiz.protocol.DeckValidation
import org.asyncttwiz.storage.*

enum class DeckImportSite(val label: String) { ARCHIDEKT("Archidekt"), MTGGOLDFISH("MTGGoldfish"), MOXFIELD("Moxfield") }
data class DeckImportLocation(val site: DeckImportSite, val id: String) {
    val endpoint: String get() = when (site) {
        DeckImportSite.ARCHIDEKT -> "https://archidekt.com/api/decks/$id/"
        DeckImportSite.MTGGOLDFISH -> "https://www.mtggoldfish.com/deck/download/$id"
        DeckImportSite.MOXFIELD -> "https://api2.moxfield.com/v3/decks/all/$id"
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
                "mtggoldfish.com" -> {
                    require(parts.size >= 2 && parts[0] == "deck")
                    site = DeckImportSite.MTGGOLDFISH
                    id = if (parts[1] == "download") { require(parts.size == 3); parts[2] } else parts[1]
                }
                "moxfield.com" -> {
                    require(parts.size >= 2 && parts[0] == "decks")
                    site = DeckImportSite.MOXFIELD; id = parts[1]
                }
                else -> return null
            }
        } else if (':' in value) {
            site = DeckImportSite.entries.singleOrNull { it.name.equals(value.substringBefore(':'), true) } ?: return null
            id = value.substringAfter(':')
        } else {
            // Moxfield public IDs are distinguishable; numeric IDs require a site prefix.
            site = DeckImportSite.MOXFIELD; id = value
        }
        require(when (site) {
            DeckImportSite.ARCHIDEKT, DeckImportSite.MTGGOLDFISH -> id.matches(Regex("[1-9][0-9]{0,17}"))
            DeckImportSite.MOXFIELD -> id.matches(Regex("[A-Za-z0-9_-]{22}"))
        })
        DeckImportLocation(site, id)
    }.getOrNull()

    fun parse(site: DeckImportSite, body: String, name: String? = null): ImportedDeck = when (site) {
        DeckImportSite.ARCHIDEKT -> archidekt(Json.parseToJsonElement(body).jsonObject)
        DeckImportSite.MOXFIELD -> moxfield(Json.parseToJsonElement(body).jsonObject)
        DeckImportSite.MTGGOLDFISH -> {
            val declaredName = Regex("(?im)^//\\s*NAME:\\s*(.+)$").find(body)?.groupValues?.get(1)?.trim()
            val text = body.lineSequence().filterNot { it.trimStart().startsWith("//") || it.trimStart().startsWith('#') }.joinToString("\n")
            ImportedDeck(requireNotNull(name ?: declaredName).also { require(it.isNotBlank()) }, DeckTextParser.parse(text))
        }
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

    private fun moxfield(root: JsonObject): ImportedDeck {
        val boards = root.getValue("boards").jsonObject
        fun rows(key: String): List<Pair<Int, String>> {
            val board = boards[key]?.jsonObject ?: return emptyList()
            val cards = board.getValue("cards")
            val entries = when (cards) {
                is JsonObject -> cards.values.toList()
                is JsonArray -> cards.toList()
                else -> error("Invalid Moxfield board")
            }
            return entries.map {
                val entry = it.jsonObject
                entry.getValue("quantity").jsonPrimitive.int to entry.getValue("card").jsonObject.string("name")
            }
        }
        require("mainboard" in boards)
        return counted(root.string("name"), rows("mainboard") + rows("commanders"), rows("sideboard"))
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

    /** Goldfish's text download omits its name; read only the public deck page's title. */
    fun goldfishName(html: String): String {
        val heading = Regex("(?is)<h1\\b[^>]*class\\s*=\\s*[\"'][^\"']*\\btitle\\b[^\"']*[\"'][^>]*>(.*?)</h1>")
            .find(html)?.groupValues?.get(1)
        if (heading != null) {
            val name = decodeEntities(heading.substringBefore("<span").replace(Regex("<[^>]+>"), "")).trim()
            if (name.isNotBlank()) return name
        }
        val title = Regex("(?is)<title[^>]*>(.*?)</title>").find(html)?.groupValues?.get(1) ?: error("Missing deck name")
        return decodeEntities(title).replace(Regex("(?i)\\s*[-|]\\s*MTGGoldfish\\s*$"), "").trim().also { require(it.isNotBlank()) }
    }
    private fun decodeEntities(value: String): String = Regex("&(#x[0-9a-fA-F]+|#[0-9]+|amp|quot|apos|lt|gt|nbsp);").replace(value) {
        val entity = it.groupValues[1]
        when (entity) {
            "amp" -> "&"; "quot" -> "\""; "apos" -> "'"; "lt" -> "<"; "gt" -> ">"; "nbsp" -> " "
            else -> runCatching { String(Character.toChars(if (entity.startsWith("#x")) entity.drop(2).toInt(16) else entity.drop(1).toInt())) }.getOrDefault(it.value)
        }
    }
}

internal object ImportClipboard {
    fun cube(text: String?): String? = text?.trim()?.takeIf { value ->
        runCatching { CubeCobraImport.cubeId(value) }.isSuccess
    }
    fun deck(text: String?): String? = text?.trim()?.takeIf { DeckUrlImport.location(it) != null }
}

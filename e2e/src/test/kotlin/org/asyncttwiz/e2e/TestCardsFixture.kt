package org.asyncttwiz.e2e

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.JsonPrimitive
import java.io.File
import java.util.UUID

/**
 * The test cards' records in the test cards' catalog (tests/fixtures/card-catalog/AllPrintings.json): the MTGJSON sets
 * [TestCards.SET] and [TestCards.BASICS], made from [TestCards]. TestCardsFixtureTest checks the file holds exactly these;
 * `-PtestCards=record` writes them into it, in the file's own layout (Python's `json.dump(indent=1)`).
 */
object TestCardsFixture {
    private val setNames = mapOf(TestCards.SET to "End-to-End Test Cards", TestCards.BASICS to "Test Basic Lands")

    /** The generated sets by code. Neither is a kind of set the Sets tab lists ("box"), so drafts and set pods ignore them. */
    fun sets(cards: List<TestCard> = TestCards.all): Map<String, JsonObject> {
        val names = cards.map { it.name }
        require(names.size == names.toSet().size) { "Two test cards share a name: ${names.groupBy { it }.filterValues { it.size > 1 }.keys}" }
        return cards.groupBy { it.set }.toSortedMap().mapValues { (code, inSet) ->
            JsonObject().apply {
                addProperty("code", code)
                addProperty("name", setNames.getValue(code))
                addProperty("type", "box")
                addProperty("releaseDate", "2020-02-01")
                val (tokens, cards) = inSet.partition { it.token }
                add("cards", JsonArray().apply { cards.forEachIndexed { index, card -> records(card, code, "${index + 1}").forEach(::add) } })
                add("tokens", JsonArray().apply { tokens.forEachIndexed { index, card -> records(card, code, "T${index + 1}").forEach(::add) } })
            }
        }
    }

    private fun records(card: TestCard, set: String, number: String): List<JsonObject> = card.faces.mapIndexed { index, face ->
        JsonObject().apply {
            addProperty("uuid", face.id)
            addProperty("name", card.name)
            addProperty("setCode", set)
            addProperty("number", number)
            addProperty("language", "English")
            addProperty("layout", card.layout)
            addProperty("type", face.typeLine)
            val colors = colors(face.manaCost)
            add("colors", strings(colors))
            add("colorIdentity", strings(colors))
            addProperty("rarity", "common")
            add("availability", strings(listOf("paper")))
            add("legalities", JsonObject().apply {
                listOf("commander", "legacy", "modern", "vintage").forEach { addProperty(it, "Legal") }
                addProperty("pauper", "Not Legal")
            })
            add("identifiers", JsonObject().apply {
                card.scryfallId?.let { addProperty("scryfallId", it) }
                addProperty("scryfallOracleId", UUID.nameUUIDFromBytes("e2e/oracle/${face.name}".encodeToByteArray()).toString())
            })
            mapOf("manaCost" to face.manaCost, "text" to face.rulesText, "power" to face.power, "toughness" to face.toughness,
                "loyalty" to face.loyalty).forEach { (key, value) -> if (value.isNotEmpty()) addProperty(key, value) }
            if (card.faces.size > 1) {
                addProperty("faceName", face.name)
                addProperty("side", ('a' + index).toString())
                add("otherFaceIds", strings(card.faces.filter { it !== face }.map { it.id }))
            }
        }
    }

    /** A face's colours from its mana cost (CR 202.2), WUBRG order. */
    private fun colors(cost: String): List<String> {
        val symbols = Regex("\\{([^}]*)}").findAll(cost).map { it.groupValues[1] }.toList()
        return "WUBRG".filter { color -> symbols.any { color in it } }.map { it.toString() }
    }

    private fun strings(values: List<String>) = JsonArray().apply { values.forEach { add(it) } }

    /** [file] (an MTGJSON AllPrintings file) with [sets] in place of its sets of the same codes, sets by code. */
    fun merged(file: File, sets: Map<String, JsonObject> = sets()): String {
        val root = JsonParser.parseString(file.readText()).asJsonObject
        val data = root.getAsJsonObject("data")
        val merged = JsonObject()
        (data.keySet() + sets.keys).sorted().forEach { code -> merged.add(code, sets[code] ?: data.get(code)) }
        root.add("data", merged)
        return StringBuilder().also { write(root, 0, it) }.append('\n').toString()
    }

    /** As Python's `json.dumps(value, indent=1, ensure_ascii=False)` writes [value]. */
    private fun write(value: JsonElement, depth: Int, out: StringBuilder) {
        fun indent(level: Int) { out.append('\n'); repeat(level) { out.append(' ') } }
        when {
            value.isJsonObject -> {
                val entries = value.asJsonObject.entrySet()
                if (entries.isEmpty()) { out.append("{}"); return }
                out.append('{')
                entries.forEachIndexed { i, (key, element) ->
                    if (i > 0) out.append(',')
                    indent(depth + 1); quote(key, out); out.append(": "); write(element, depth + 1, out)
                }
                indent(depth); out.append('}')
            }
            value.isJsonArray -> {
                val items = value.asJsonArray
                if (items.isEmpty) { out.append("[]"); return }
                out.append('[')
                items.forEachIndexed { i, element -> if (i > 0) out.append(','); indent(depth + 1); write(element, depth + 1, out) }
                indent(depth); out.append(']')
            }
            value.isJsonNull -> out.append("null")
            (value as JsonPrimitive).isString -> quote(value.asString, out)
            else -> out.append(value.toString())
        }
    }

    private fun quote(text: String, out: StringBuilder) {
        out.append('"')
        for (c in text) when (c) {
            '"' -> out.append("\\\""); '\\' -> out.append("\\\\"); '\n' -> out.append("\\n"); '\r' -> out.append("\\r")
            '\t' -> out.append("\\t"); '\b' -> out.append("\\b"); '\u000C' -> out.append("\\f")
            else -> if (c < ' ') out.append("\\u%04x".format(c.code)) else out.append(c)
        }
        out.append('"')
    }
}

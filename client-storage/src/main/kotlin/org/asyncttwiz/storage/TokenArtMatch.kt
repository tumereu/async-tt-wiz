package org.asyncttwiz.storage

import com.google.gson.JsonParser
import org.asyncttwiz.protocol.DefinitionFace
import java.util.Locale

/**
 * The best catalog token printing for a token Automatum makes (integration spec §4.6, tokens), drawn with its picture.
 *
 * 1. The related tokens of the card whose effect made it ([best]'s `related`) that have the token's name: the one
 *    sharing most of its characteristics (types and subtypes, colours, P/T, rules text), then of the source's set
 *    (`sourceSet`), then the newest.
 * 2. Otherwise a token of the token's name (`named`) with all those characteristics, of the source's set first, then
 *    the newest.
 * 3. Otherwise none: the plain token frame.
 *
 * Only printings with a picture of their front (a Scryfall id, no back side) are chosen.
 */
internal object TokenArtMatch {
    /** A token's characteristics, compared word by word: type words, subtypes, colours, P/T and rules-text parts. */
    data class Traits(val name: String, val types: Set<String>, val subtypes: Set<String>, val colors: Set<Char>,
        val power: String, val toughness: String, val text: List<String>) {
        /** How many of the characteristics other than the name agree (0–4). */
        fun agreement(other: Traits) = listOf(types == other.types && subtypes == other.subtypes, colors == other.colors,
            power == other.power && toughness == other.toughness, text == other.text).count { it }
    }

    fun best(face: DefinitionFace, sourceSet: String?, related: List<CatalogPrinting>, named: List<CatalogPrinting>): CatalogPrinting? {
        val token = traits(face)
        fun usable(rows: List<CatalogPrinting>) = rows.filter { it.token && !it.scryfallId.isNullOrBlank() && it.side in setOf("", "a") }
            .mapNotNull { row -> traits(row)?.takeIf { it.name == token.name }?.let { row to it } }
        val tie = compareBy<Pair<CatalogPrinting, Traits>> { if (sourceSet != null && it.first.set == sourceSet) 0 else 1 }
            .thenByDescending { it.first.releaseDate }.thenBy { it.first.id }
        usable(related).sortedWith(compareByDescending<Pair<CatalogPrinting, Traits>> { token.agreement(it.second) }.then(tie))
            .firstOrNull()?.let { return it.first }
        return usable(named).filter { token.agreement(it.second) == 4 }.sortedWith(tie).firstOrNull()?.first
    }

    /** A token's name as the catalog writes it: without the "Token" a token's name ends with (CR 111.4), lower case. */
    fun name(name: String): String = name.trim().lowercase(Locale.ROOT).removeSuffix(" token").trim()

    fun traits(face: DefinitionFace): Traits {
        val colors = (face.colorIndicator ?: face.manaCost).uppercase(Locale.ROOT).filter { it in "WUBRG" }.toSet()
        return traits(face.name, face.typeLine, colors, face.power, face.toughness, face.rulesText)
    }

    private fun traits(row: CatalogPrinting): Traits? {
        val json = runCatching { JsonParser.parseString(row.json).asJsonObject }.getOrNull() ?: return null
        fun field(key: String) = json.get(key)?.takeIf { it.isJsonPrimitive }?.asString.orEmpty()
        val colors = json.get("colors")?.takeIf { it.isJsonArray }?.asJsonArray
            ?.mapNotNull { element -> element.takeIf { it.isJsonPrimitive }?.asString?.singleOrNull() }.orEmpty().toSet()
        return traits(field("faceName").ifBlank { row.name }, field("type"), colors, field("power"), field("toughness"), field("text"))
    }

    private fun traits(name: String, typeLine: String, colors: Set<Char>, power: String, toughness: String, text: String): Traits {
        fun words(part: String) = part.lowercase(Locale.ROOT).split(Regex("\\s+")).filter { it.isNotBlank() }.toSet()
        val dash = typeLine.indexOf('—')
        val front = if (dash < 0) typeLine else typeLine.substring(0, dash)
        val sub = if (dash < 0) "" else typeLine.substring(dash + 1)
        // Reminder text is left out; keyword lists ("Flying, haste") and separate lines ("Flying\nHaste") read alike
        val parts = text.lowercase(Locale.ROOT).replace(Regex("\\([^)]*\\)"), " ").split(Regex("[\\n,.;]"))
            .map { it.trim().replace(Regex("\\s+"), " ") }.filter { it.isNotEmpty() }.sorted()
        return Traits(name(name), words(front) - "token", words(sub), colors.map { it.uppercaseChar() }.toSet(),
            power.trim(), toughness.trim(), parts)
    }
}

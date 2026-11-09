package arcanum.tools

import arcanum.api.Ability
import java.security.MessageDigest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonPrimitive

/** One ability's model as JSON. */
internal fun abilityJson(ability: Ability): JsonElement = Json.encodeToJsonElement(Ability.serializer(), ability)

/**
 * The unit of "covered": the canonical form of one parsed ability, so two abilities with one shape are read and played
 * alike. Printed text and printed names go; a literal number becomes `0`, `1` or `N` (so one target differs from two), keeping
 * its sign (`+1/+1` differs from `-1/-1`); a mana cost becomes a placeholder, in which X stays. Everything else stays exact:
 * effect types, filters, durations, conditions, bound names, colours of mana added.
 */
internal fun abilityShape(ability: JsonElement): String = maskShape(ability, null).toString()

internal fun abilityShape(ability: Ability): String = abilityShape(abilityJson(ability))

/** A short id of a [abilityShape]. */
internal fun shapeId(shape: String): String =
    MessageDigest.getInstance("SHA-1").digest(shape.toByteArray()).joinToString("") { "%02x".format(it) }.take(12)

private val PRINTED_FIELDS = setOf("text", "span", "abilityWord", "printed", "requirement")
private val COST_FIELDS = setOf("cost", "manaCost")
private val MANA_FIELDS = setOf("mana", "producing")
private val GENERIC_MANA = Regex("""\{(\d+)\}""")

private fun maskShape(e: JsonElement, key: String?): JsonElement = when (e) {
    is JsonObject -> {
        // A card's or token's printed name, or a filter "named …"; a slot's or binding's name is structure and stays.
        val printedName = (e["type"] as? JsonPrimitive)?.content == "name" || "types" in e
        JsonObject(e.filterKeys { it !in PRINTED_FIELDS }.toSortedMap().mapValues { (k, v) ->
            if ((k == "name" && printedName) || k == "into") JsonPrimitive("<name>") else maskShape(v, k)
        })
    }
    is JsonArray -> JsonArray(e.map { maskShape(it, key) })
    is JsonPrimitive -> when {
        e.isString -> maskString(e.content, key)
        e.booleanOrNull != null || e.content == "null" -> e
        else -> JsonPrimitive(maskNumber(e.content))
    }
}

private fun maskNumber(number: String): String {
    val value = number.toLongOrNull() ?: return number
    val size = when (kotlin.math.abs(value)) { 0L -> "0"; 1L -> "1"; else -> "N" }
    return if (value < 0) "-$size" else size
}

private fun maskString(text: String, key: String?): JsonPrimitive = JsonPrimitive(when {
    key in COST_FIELDS && '{' in text -> if ("{X}" in text) "{X}{mana}" else "{mana}"
    key in MANA_FIELDS -> GENERIC_MANA.replace(text) { "{${maskNumber(it.groupValues[1])}}" }
    else -> text
})

/** The top `type` of an ability's JSON, which is what kind of ability it is. */
internal fun abilityKind(ability: JsonElement): String = (ability as? JsonObject)?.get("type")?.jsonPrimitive?.content ?: "?"

package arcanum.api

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Checks for definitions that the types allow but the model forbids. Run on hand-written definitions and on the
 * parser's output.
 */
object DefinitionChecks {
    /** Every [Mechanic.KeywordOnly] kind; a JVM test checks the list is complete. */
    val keywordOnly: List<Mechanic.KeywordOnly> = listOf(
        Mechanic.Deathtouch, Mechanic.Lifelink, Mechanic.FirstStrike, Mechanic.DoubleStrike, Mechanic.Trample(),
        Mechanic.Indestructible, Mechanic.Infect, Mechanic.Wither, Mechanic.Toxic(1), Mechanic.Protection(ObjectFilter.Any),
        Mechanic.Phasing, Mechanic.CanEnchant(TargetKind.Objects(ObjectFilter.Any)),
    )

    private val json = Json
    private val keywordOnlyNames: Set<String> = keywordOnly.mapTo(mutableSetOf()) {
        json.encodeToJsonElement(Mechanic.serializer(), it).jsonObject.getValue("type").jsonPrimitive.content
    }

    /** Problems in [card], one readable line each; empty when it is fine. */
    fun problems(card: CardDefinition): List<String> {
        val found = mutableListOf<String>()
        walk(json.encodeToJsonElement(CardDefinition.serializer(), card), inMechanics = false, found)
        return found
    }

    // Mechanic lists are the values of "mechanics" (static abilities, ApplyMechanics).
    private fun walk(element: JsonElement, inMechanics: Boolean, found: MutableList<String>) {
        when (element) {
            is JsonArray -> element.forEach { walk(it, inMechanics, found) }
            is JsonObject -> {
                val type = (element["type"] as? JsonPrimitive)?.content
                if (inMechanics && type in keywordOnlyNames)
                    found += "keyword-only mechanic \"$type\" used directly; grant the keyword ability instead (CR 613.1f)"
                if (type == "of" && element.type("how") == "single" && element.type("objects") == "each")
                    found += "a single object's number read from a group; say total, greatest or least"
                element.forEach { (key, value) -> walk(value, key == "mechanics", found) }
            }
            is JsonPrimitive -> Unit
        }
    }

    private fun JsonObject.type(key: String): String? = ((this[key] as? JsonObject)?.get("type") as? JsonPrimitive)?.content
}

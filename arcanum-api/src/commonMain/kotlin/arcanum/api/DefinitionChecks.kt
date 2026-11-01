package arcanum.api

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Checks for definitions that the types allow but the model forbids. Run on hand-written definitions and on the
 * parser's output. Reported:
 * - a keyword-only mechanic used directly ([Mechanic.KeywordOnly]);
 * - a single object's number read from a group ([Combine.Single] over [Objects.Each]);
 * - a target slot, bound name or announced value used in a face but defined nowhere in it (names live in the Stack
 *   item, so a cost's names are visible to the spell);
 * - [Value.DividedShare] without a divided target slot;
 * - an event role that none of the triggered ability's events has.
 *
 * The checks walk the definition's JSON form, so they see every nested part without a visitor per type.
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

    /** Roles each event pattern has (by its JSON type); patterns not listed have only [EventRole.Object]. */
    private val roles: Map<String, Set<String>> = mapOf(
        "zone-change" to setOf("before", "after"),
        "damage" to setOf("object", "source"),
        "block" to setOf("object", "attacker"),
        "becomes-target" to setOf("object", "source"),
    )

    /** Problems in [card], one readable line each; empty when it is fine. */
    fun problems(card: CardDefinition): List<String> {
        val found = mutableListOf<String>()
        val tree = json.encodeToJsonElement(CardDefinition.serializer(), card)
        walk(tree, inMechanics = false, found)
        tree.jsonObject.getValue("faces").jsonArray.forEach { checkReferences(it.jsonObject, found) }
        return found
    }

    private fun walk(element: JsonElement, inMechanics: Boolean, found: MutableList<String>) {
        when (element) {
            is JsonArray -> element.forEach { walk(it, inMechanics, found) }
            is JsonObject -> {
                val type = element.typeName()
                if (inMechanics && type in keywordOnlyNames)
                    found += "keyword-only mechanic \"$type\" used directly; grant the keyword ability instead (CR 613.1f)"
                if (type == "of" && element.typeOf("how") == "single" && element.typeOf("objects") == "each")
                    found += "a single object's number read from a group; say total, greatest or least"
                if (type == "triggered") checkRoles(element, found)
                // Mechanic lists are the values of "mechanics" (static abilities, ApplyMechanics).
                element.forEach { (key, value) -> walk(value, key == "mechanics", found) }
            }
            is JsonPrimitive -> Unit
        }
    }

    private fun checkReferences(face: JsonObject, found: MutableList<String>) {
        val named = mutableSetOf<String>()
        val slots = mutableSetOf<String>()
        val dividedSlots = mutableSetOf<String>()
        val announced = mutableSetOf<String>()
        val usedNames = mutableSetOf<String>()
        val usedSlots = mutableSetOf<String>()
        val usedAnnounced = mutableSetOf<String>()
        var usesDividedShare = false
        visit(face) { element ->
            listOf("bind", "preBind", "postBind", "foundBind", "restBind").forEach { key -> element.string(key)?.let { named += it } }
            // A target slot is the only object with "name" and "what" and no type.
            if (element.typeName() == null && "what" in element) element.string("name")?.let {
                slots += it
                if (element["divided"] != null) dividedSlots += it
            }
            (element["announced"] as? JsonArray)?.forEach { announced += it.jsonPrimitive.content }
            when (element.typeName()) {
                "bound", "excess", "did" -> element.string("name")?.let { usedNames += it }
                "target" -> element.string("slot")?.let { usedSlots += it }
                "announced" -> usedAnnounced += element.string("name") ?: "X"
                "divided-share" -> usesDividedShare = true
            }
        }
        val name = face.string("name")
        (usedNames - named).forEach { found += "$name: \"$it\" is used but never named" }
        (usedSlots - slots).forEach { found += "$name: target slot \"$it\" is used but not declared" }
        (usedAnnounced - announced).forEach { found += "$name: \"$it\" is used but never announced" }
        if (usesDividedShare && dividedSlots.isEmpty()) found += "$name: a divided share is read without a divided target slot"
    }

    private fun checkRoles(ability: JsonObject, found: MutableList<String>) {
        val trigger = ability["trigger"] as? JsonObject ?: return
        val events = (trigger["events"] as? JsonArray)?.mapNotNull { (it as? JsonObject)?.typeName() } ?: return
        val allowed = events.flatMap { roles[it] ?: setOf("object") }.toSet()
        val used = mutableSetOf<String>()
        listOfNotNull(ability["instructions"], ability["interveningIf"]).forEach { part ->
            visit(part) { if (it.typeName() == "event") used += it.typeOf("role") ?: "object" }
        }
        (used - allowed).forEach { found += "event role \"$it\" is not part of the trigger's events $events" }
    }

    private fun visit(element: JsonElement, action: (JsonObject) -> Unit) {
        when (element) {
            is JsonArray -> element.forEach { visit(it, action) }
            is JsonObject -> { action(element); element.values.forEach { visit(it, action) } }
            is JsonPrimitive -> Unit
        }
    }

    private fun JsonObject.typeName(): String? = (this["type"] as? JsonPrimitive)?.content
    private fun JsonObject.typeOf(key: String): String? = (this[key] as? JsonObject)?.typeName()
    private fun JsonObject.string(key: String): String? = (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content
}

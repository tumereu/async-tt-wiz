package arcanum.api

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.int
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Checks for definitions that the types allow but the model forbids. Run on hand-written definitions and on the
 * parser's output. Reported:
 * - a keyword-only mechanic used directly ([Mechanic.KeywordOnly]), also in mana riders;
 * - a single object's number read from something that can be several objects ([Combine.Single] over a group, union,
 *   "except", library cards);
 * - a target slot, bound name or announced value used in an ability but defined nowhere in it; names live in the
 *   Stack item, so a spell also sees the names of its card's additional and alternative costs; abilities granted by
 *   the ability or on a token it creates are their own scope;
 * - [Value.DividedShare] without a divided target slot;
 * - an event role that the trigger's events don't have, each trigger (delayed ones too) checked against its own;
 * - "as enters" steps other than pay, choose, if and modify (CR 614.12), or with no pay or choose (then it is a plain
 *   modification with the static ability's condition);
 * - a pay with both or neither of a cost and steps; an "any target" slot not read whole with `Recipients.target`, or
 *   `target` used for another slot; a repeat around a single effect that has its own count;
 * - canonical forms: `Exists` and `HappenedThisTurn` rather than comparing counts with a number; `withoutPaying` rather
 *   than an empty cost; keyword costs only from keywords; counters divided among targets on casting; "each player
 *   [does]" loops together; boast, exhaust and power-up labels only on activated abilities;
 * - a replacement that neither replaces nor modifies; target, mode and choice counts that can't be met;
 * - a chapter trigger on a face that isn't a Saga; a characteristic-defining ability with a condition or other scope;
 * - event-scoped mechanics (replacement, prevention, cost change, extra triggers) given an `affects`, in static
 *   abilities and in effects;
 * - the "affected object/player" reference outside a static ability's or effect's mechanics.
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
        "block" to setOf("object", "other-combatant"),
        "becomes-blocked" to setOf("object", "other-combatant"),
        "becomes-target" to setOf("object", "source"),
    )
    private val eventScoped = setOf("replacement", "prevention", "cost-change", "triggers-additional-time", "damage-cant-be-prevented")
    private val manyObjects = setOf("each", "union", "except", "library-cards")
    private val asEntersSteps = setOf("pay", "choose", "if", "modify")
    private val nameKeys = listOf("bind", "preBind", "postBind", "foundBind", "restBind", "chosenBind", "otherBind")

    /** Problems in [card], one readable line each; empty when it is fine. */
    fun problems(card: CardDefinition): List<String> {
        val found = mutableListOf<String>()
        val tree = json.encodeToJsonElement(CardDefinition.serializer(), card)
        walk(tree, inMechanics = false, found)
        tree.jsonObject.getValue("faces").jsonArray.forEach { checkFace(it.jsonObject, found) }
        return found
    }

    private fun walk(element: JsonElement, inMechanics: Boolean, found: MutableList<String>) {
        when (element) {
            is JsonArray -> element.forEach { walk(it, inMechanics, found) }
            is JsonObject -> {
                val type = element.typeName()
                if (inMechanics && type in keywordOnlyNames)
                    found += "keyword-only mechanic \"$type\" used directly; grant the keyword ability instead (CR 613.1f)"
                if (type == "of" && element.typeOf("how") == "single" && element.typeOf("objects") in manyObjects)
                    found += "a single object's number read from several objects; say total, greatest or least"
                if (type == "triggered") checkRoles(element["trigger"], listOfNotNull(element["instructions"], element["interveningIf"]), found)
                if (type == "delayed-trigger") checkRoles(element["trigger"], listOfNotNull(element["instructions"]), found)
                if (type == "as-enters") {
                    // Deep: steps nested in an If are steps of the entering too.
                    visitAll(element["steps"] ?: JsonArray(emptyList())) { step ->
                        if (step.typeName() == "do") found += "\"as enters\" may only pay, choose, check or modify how it enters, not do effects (CR 614.12)"
                    }
                    visitShallow(element["steps"]) { step ->
                        if (step.typeName() !in asEntersSteps && step.typeName() != "do") found += "\"as enters\" may only pay, choose, check or modify how it enters, not \"${step.typeName()}\" (CR 614.12)"
                    }
                    var decides = false
                    visitAll(element["steps"] ?: JsonArray(emptyList())) { if (it.typeName() == "pay" || it.typeName() == "choose") decides = true }
                    if (!decides) found += "\"as enters\" without a payment or choice: write the modification directly, with the static ability's condition"
                }
                if (type == "pay" && ((element["cost"] != null) == ((element["steps"] as? JsonArray)?.isNotEmpty() == true)))
                    found += "a pay needs exactly one of a cost or steps"
                if (type == "repeat") {
                    val steps = (element["steps"] as? JsonArray).orEmpty()
                    val effect = (steps.singleOrNull() as? JsonObject)?.takeIf { it.typeName() == "do" }?.get("effect") as? JsonObject
                    if (effect?.typeName() in setOf("create-token", "draw", "mill", "put-counters", "damage", "gain-life", "lose-life"))
                        found += "repeat around \"${effect?.typeName()}\": use its own count (one event, not several)"
                }
                if (type == "apply" && element["affects"].let { it != null && it !is kotlinx.serialization.json.JsonNull } &&
                    (element["mechanics"] as? JsonArray).orEmpty().any { (it as? JsonObject)?.typeName() in eventScoped })
                    found += "an effect with event-scoped mechanics changes the rules of the game; leave its affects empty (CR 611.2c)"
                if (type == "compare" && element.typeOf("right") == "fixed") {
                    val left = element["left"] as? JsonObject
                    if (left?.typeName() == "count" && left.typeOf("objects") == "each") found += "a count of matching objects compared with a number: use Exists"
                    if (left?.typeName() == "events-this-turn") found += "a count of this turn's events compared with a number: use HappenedThisTurn"
                }
                if ((type == "may-play" || type == "alternative-cost") && (element["cost"] as? JsonObject)?.let { (it["parts"] as? JsonArray)?.isEmpty() } == true)
                    found += "an empty cost: write \"without paying its mana cost\" as withoutPaying"
                if ((type == "additional-cost" || type == "alternative-cost") && element["keyword"] != null)
                    found += "a keyword's cost comes from its keyword ability; write the keyword, not the cost"
                if (type == "put-counters" && element.boolean("distributed") && element.typeOf("objects") == "target")
                    found += "counters divided among targets are divided on casting: use the target slot's divided amount (CR 601.2d)"
                if (type == "for-each" && element.typeOf("each") == "players" && !element.boolean("together"))
                    found += "\"each player/opponent [does]\" happens together (CR 101.4, 608.2e): set together"
                if (type in setOf("static", "triggered") && element.typeOf("label") in setOf("exhaust", "boast", "power-up"))
                    found += "the ${element.typeOf("label")} label belongs on an activated ability"
                if (type == "objects" && "filter" in element && "min" in element) {
                    val min = element.fixed("min"); val max = element.fixed("max")
                    if (min != null && max != null) checkCounts("choice", min, max, found)
                }
                if (type == "replacement" && element["instead"] == null && element["modify"] == null)
                    found += "a replacement that neither replaces nor modifies the event"
                if (type == "static") checkStatic(element, found)
                if (type == null && element["options"] is JsonArray) {
                    val options = (element["options"] as JsonArray).size
                    val min = element.int("min") ?: 1
                    val max = element.int("max") ?: 1
                    checkCounts("modes", min, max, found)
                    if (!element.boolean("repeatable") && max > options) found += "modes: maximum $max is more than the $options options"
                }
                if ("what" in element && type == null) {
                    // Absent means the default 1; null means any number; a computed value isn't checked.
                    val min = if ("min" in element) element.fixed("min") else 1
                    val max = if ("max" in element) element.fixed("max") else 1
                    if (min != null && max != null) checkCounts("target slot", min, max, found)
                }
                // Mechanic lists: "mechanics" (static abilities, ApplyMechanics) and "spellGets" (mana riders).
                element.forEach { (key, value) -> walk(value, key == "mechanics" || key == "spellGets", found) }
            }
            is JsonPrimitive -> Unit
        }
    }

    private fun checkStatic(ability: JsonObject, found: MutableList<String>) {
        val mechanics = (ability["mechanics"] as? JsonArray).orEmpty().mapNotNull { (it as? JsonObject)?.typeName() }
        if (ability["affects"] != null && mechanics.any { it in eventScoped })
            found += "event-scoped mechanics carry their own scope; leave the static ability's affects at its default"
        if (ability.boolean("characteristicDefining") && (ability["condition"] != null || ability["affects"] != null))
            found += "a characteristic-defining ability affects only its own object and has no condition (CR 604.3a)"
    }

    private fun checkCounts(what: String, min: Int, max: Int, found: MutableList<String>) {
        if (min > max) found += "$what: minimum $min is more than maximum $max"
    }

    private fun checkFace(face: JsonObject, found: MutableList<String>) {
        val name = face.string("name")
        val abilities = (face["abilities"] as? JsonArray).orEmpty().map { it.jsonObject }
        val subtypes = (face["subtypes"] as? JsonArray).orEmpty().mapNotNull { (it as? JsonObject)?.string("name") ?: (it as? JsonPrimitive)?.content }
        // Names made by the card's additional and alternative costs are visible to its spell (they share the Stack item).
        val costNames = mutableSetOf<String>()
        abilities.filter { it.typeName() == "static" }.forEach { static ->
            (static["mechanics"] as? JsonArray).orEmpty().filter { (it as? JsonObject)?.typeName() in setOf("additional-cost", "alternative-cost") }
                .forEach { collectNames(it, costNames) }
        }
        fun check(ability: JsonObject, extra: Set<String>) {
            val scope = Scope()
            scope.named += extra
            scope.visit(ability, root = true)
            (scope.usedNames - scope.named).forEach { found += "$name: \"$it\" is used but never named in its ability" }
            (scope.usedSlots - scope.slots).forEach { found += "$name: target slot \"$it\" is used but not declared in its ability" }
            (scope.usedAnnounced - scope.announced).forEach { found += "$name: \"$it\" is used but never announced" }
            if (scope.usesDividedShare && scope.dividedSlots.isEmpty()) found += "$name: a divided share is read without a divided target slot"
            if (scope.usesAffected && ability.typeName() != "static") found += "$name: \"the affected object/player\" outside a static ability or effect"
            scope.recipientUses.forEach { (slot, halves, byTarget) ->
                val either = slot in scope.eitherSlots
                if (byTarget && !either) found += "$name: Recipients(target = \"$slot\") is for \"any target\" slots; name the objects or players"
                if (!byTarget && either) found += "$name: \"any target\" slot \"$slot\" must be read whole with Recipients(target = …)"
            }
            // Abilities granted by this one see its names, frozen when the effect is created (D37).
            scope.nested.forEach { check(it, scope.named + extra) }
            if (ability.typeName() == "triggered" && (ability["trigger"] as? JsonObject)?.typeName() == "chapter" && "Saga" !in subtypes)
                found += "$name: chapter abilities belong to Sagas"
        }
        abilities.forEach { check(it, if (it.typeName() == "spell") costNames else emptySet()) }
    }

    /** Names, slots and references in one ability; granted abilities and token faces are collected as their own scopes. */
    private class Scope {
        val named = mutableSetOf<String>()
        val slots = mutableSetOf<String>()
        val dividedSlots = mutableSetOf<String>()
        val announced = mutableSetOf<String>()
        val usedNames = mutableSetOf<String>()
        val usedSlots = mutableSetOf<String>()
        val usedAnnounced = mutableSetOf<String>()
        var usesDividedShare = false
        var usesAffected = false
        val nested = mutableListOf<JsonObject>()
        val eitherSlots = mutableSetOf<String>()
        /** Recipients naming a target slot: slot, uses objects, uses players, written with `target`. */
        val recipientUses = mutableListOf<Triple<String, Pair<Boolean, Boolean>, Boolean>>()

        fun visit(element: JsonElement, root: Boolean = false, inMechanics: Boolean = false) {
            when (element) {
                is JsonArray -> element.forEach { visit(it, inMechanics = inMechanics) }
                is JsonObject -> {
                    nameKeys.forEach { key -> element.string(key)?.let { named += it } }
                    if (element.typeName() == null && "what" in element) element.string("name")?.let {
                        slots += it
                        if (element["divided"] != null) dividedSlots += it
                        if (element.typeOf("what") == "either") eitherSlots += it
                    }
                    if (element.typeName() == null && ("objects" in element || "players" in element || "target" in element) && "what" !in element) {
                        val objectSlot = (element["objects"] as? JsonObject)?.takeIf { it.typeName() == "target" }?.string("slot")
                        val playerSlot = (element["players"] as? JsonObject)?.takeIf { it.typeName() == "target" }?.string("slot")
                        element.string("target")?.let { recipientUses += Triple(it, true to true, true) }
                        (objectSlot ?: playerSlot)?.let { recipientUses += Triple(it, (objectSlot != null) to (playerSlot != null), false) }
                    }
                    (element["announced"] as? JsonArray)?.forEach { announced += it.jsonPrimitive.content }
                    when (element.typeName()) {
                        "bound", "excess", "did", "could-not", "won-flip", "non-empty", "chose" -> element.string("name")?.let { usedNames += it }
                        "target" -> element.string("slot")?.let { usedSlots += it }
                        "announced" -> usedAnnounced += element.string("name") ?: "X"
                        "divided-share" -> usesDividedShare = true
                        "affected" -> if (!inMechanics) usesAffected = true
                    }
                    element.forEach { (key, value) ->
                        when {
                            // Abilities granted by this one, and a created token's abilities, are their own scopes.
                            key == "abilities" && !root -> (value as? JsonArray)?.forEach { (it as? JsonObject)?.let(nested::add) }
                            else -> visit(value, inMechanics = inMechanics || key == "mechanics" || key == "condition" && element.typeName() == "static")
                        }
                    }
                }
                is JsonPrimitive -> Unit
            }
        }
    }

    private fun collectNames(element: JsonElement, into: MutableSet<String>) = visitAll(element) { obj ->
        nameKeys.forEach { key -> obj.string(key)?.let { into += it } }
    }

    private fun checkRoles(trigger: JsonElement?, parts: List<JsonElement>, found: MutableList<String>) {
        val events = ((trigger as? JsonObject)?.get("events") as? JsonArray)?.mapNotNull { (it as? JsonObject)?.typeName() } ?: return
        val allowed = events.flatMap { roles[it] ?: setOf("object") }.toSet()
        val used = mutableSetOf<String>()
        fun visit(element: JsonElement) {
            when (element) {
                is JsonArray -> element.forEach(::visit)
                is JsonObject -> {
                    if (element.typeName() == "event") used += element.typeOf("role") ?: "object"
                    // A nested delayed trigger, or abilities granted here, have their own events.
                    if (element.typeName() == "delayed-trigger") return
                    element.forEach { (key, value) -> if (key != "abilities") visit(value) }
                }
                is JsonPrimitive -> Unit
            }
        }
        parts.forEach(::visit)
        (used - allowed).forEach { found += "event role \"$it\" is not part of the trigger's events $events" }
    }

    private fun visitShallow(element: JsonElement?, action: (JsonObject) -> Unit) {
        (element as? JsonArray)?.forEach { (it as? JsonObject)?.let(action) }
    }

    private fun visitAll(element: JsonElement, action: (JsonObject) -> Unit) {
        when (element) {
            is JsonArray -> element.forEach { visitAll(it, action) }
            is JsonObject -> { action(element); element.values.forEach { visitAll(it, action) } }
            is JsonPrimitive -> Unit
        }
    }

    private fun JsonObject.typeName(): String? = (this["type"] as? JsonPrimitive)?.content
    private fun JsonObject.typeOf(key: String): String? = (this[key] as? JsonObject)?.typeName()
    private fun JsonObject.string(key: String): String? = (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content
    private fun JsonObject.int(key: String): Int? = (this[key] as? JsonPrimitive)?.intOrNull
    private fun JsonObject.boolean(key: String): Boolean = (this[key] as? JsonPrimitive)?.content == "true"
    /** A [Value.Fixed] number under [key], or null when it is absent or computed. */
    private fun JsonObject.fixed(key: String): Int? =
        (this[key] as? JsonObject)?.takeIf { it.typeName() == "fixed" }?.let { (it["value"] as? JsonPrimitive)?.int }
}

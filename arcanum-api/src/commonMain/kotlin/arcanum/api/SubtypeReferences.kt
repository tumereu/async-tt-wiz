package arcanum.api

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * The subtypes (CR 205.3) a card's abilities refer to: every subtype one of their selectors picks objects by
 * ([ObjectFilter.Sub]: "Lizards you control", "each saddled Mount", "an Equipment", "enchant Forest", "the chosen type"), in
 * any nested part (a cost, a trigger, a condition, a count, a granted ability), and in the expansions of its keyword
 * abilities ([KeywordExpansions.expand]). Not references: the card's own type line, the subtypes of tokens it creates and the
 * subtypes an effect gives ("becomes a 3/3 Elemental"). A host can use them to point out which subtypes on the battlefield a
 * card cares about.
 *
 * A [SubtypeRef.Literal] names the subtype; a [SubtypeRef.Remembered] is "the chosen type" the object keeps under its key
 * (CR 607.2d), which only the game state can answer; a [SubtypeRef.Chosen] is chosen during one resolution and has no value
 * outside it.
 *
 * Like [DefinitionChecks], it walks the definition's JSON form, so it sees every nested part without a visitor per type.
 */
object SubtypeReferences {
    private val json = Json
    private val subName: String = json.encodeToJsonElement(ObjectFilter.serializer(), ObjectFilter.Sub(Subtype("x")))
        .jsonObject.getValue("type").jsonPrimitive.content

    /** The subtypes [ability] refers to; for a keyword ability those of its expansion too (backup's printed abilities aside). */
    fun of(ability: Ability): Set<SubtypeRef> {
        val found = LinkedHashSet<SubtypeRef>()
        collect(json.encodeToJsonElement(Ability.serializer(), ability), found)
        if (ability is Keyword) KeywordExpansions.expand(ability)?.forEach { expanded ->
            collect(json.encodeToJsonElement(Ability.serializer(), expanded), found)
        }
        return found
    }

    /** The subtypes [face]'s abilities and dungeon rooms refer to ([FaceDefinition.referencedSubtypes], worked out once per face). */
    fun of(face: FaceDefinition): Set<SubtypeRef> = face.referencedSubtypes

    /** The subtypes any face of [definition] refers to. */
    fun of(definition: CardDefinition): Set<SubtypeRef> = definition.faces.flatMapTo(LinkedHashSet()) { it.referencedSubtypes }

    internal fun ofFace(face: FaceDefinition): Set<SubtypeRef> {
        val found = LinkedHashSet<SubtypeRef>()
        face.abilitySubtypeReferences.forEach { found += it }
        face.rooms.forEach { collect(json.encodeToJsonElement(Instructions.serializer(), it.instructions), found) }
        return found
    }

    private fun collect(element: JsonElement, found: MutableSet<SubtypeRef>) {
        when (element) {
            is JsonArray -> element.forEach { collect(it, found) }
            is JsonObject -> {
                val ref = element["subtype"]
                if ((element["type"] as? JsonPrimitive)?.content == subName && element.size == 2 && ref is JsonObject)
                    runCatching { json.decodeFromJsonElement(SubtypeRef.serializer(), ref) }.getOrNull()?.let { found += it }
                element.values.forEach { collect(it, found) }
            }
            else -> Unit
        }
    }
}

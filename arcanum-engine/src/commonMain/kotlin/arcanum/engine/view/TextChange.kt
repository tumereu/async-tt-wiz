package arcanum.engine.view

import arcanum.api.Ability
import arcanum.api.Color
import arcanum.api.Subtype
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive

// dgm
/**
 * Text-changing effects (CR 612, layer 3): every instance of a colour word or a basic land type word in an ability
 * becomes another. Words are the definitions' literal colours and land types (a colour in a filter, protection, a
 * token's colour; a land type in a filter or landwalk); mana symbols aren't words (CR 612.2), so what a mana ability
 * produces stays. The ability is rewritten through its serialised form, so every place a word can be is covered.
 */
internal object TextChange {
    sealed interface Word {
        data class ColorWord(val color: Color) : Word
        data class LandType(val subtype: Subtype) : Word
    }

    private val json = Json

    /** [ability] with [from] replaced by [to]; unchanged when it holds no such word. */
    fun ability(ability: Ability, from: Word, to: Word): Ability {
        val encoded = json.encodeToJsonElement(Ability.serializer(), ability)
        val rewritten = rewrite(encoded, from, to)
        return if (rewritten == encoded) ability else json.decodeFromJsonElement(Ability.serializer(), rewritten)
    }

    private fun rewrite(e: JsonElement, from: Word, to: Word): JsonElement = when (e) {
        is JsonObject -> {
            val literal = (e["type"] as? JsonPrimitive)?.content == "literal"
            JsonObject(e.mapValues { (key, value) ->
                when {
                    // CR 612.2: mana symbols aren't words
                    key == "mana" -> value
                    literal && key == "color" && from is Word.ColorWord && to is Word.ColorWord && value.isSymbol(from.color) ->
                        JsonPrimitive(to.color.symbol.toString())
                    key == "colorIndicator" && value is JsonArray && from is Word.ColorWord && to is Word.ColorWord ->
                        JsonArray(value.map { if (it.isSymbol(from.color)) JsonPrimitive(to.color.symbol.toString()) else it })
                    literal && key == "subtype" && from is Word.LandType && to is Word.LandType &&
                        ((value as? JsonObject)?.get("name") as? JsonPrimitive)?.content == from.subtype.name ->
                        JsonObject(mapOf("name" to JsonPrimitive(to.subtype.name)))
                    else -> rewrite(value, from, to)
                }
            })
        }
        is JsonArray -> JsonArray(e.map { rewrite(it, from, to) })
        else -> e
    }

    private fun JsonElement.isSymbol(color: Color): Boolean =
        (this as? JsonPrimitive)?.takeIf { it.isString }?.jsonPrimitive?.content == color.symbol.toString()
}

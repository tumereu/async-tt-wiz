package org.asyncttwiz.automatum

import arcanum.api.Ability
import arcanum.api.CardDefinition
import arcanum.api.Keyword
import arcanum.engine.view.KeywordMeanings
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Whether Automatum plays a card fully by the rules (I-D51): a card is supported when its definition has no part done
 * by hand — no text the parser couldn't read ([Ability.Unparsed]), no `Manual` step or cost part, no keyword the engine
 * only carries out by hand ([KeywordMeanings.isManual]) and none of the few effects the engine always leaves to the
 * players. Worked out from the parsed definition, so it follows the parser and engine of this app version.
 */
object AutomatumSupport {
    private val json = Json

    /** The JSON type names of [Ability]'s kinds that are keywords (every concrete [Keyword]). */
    private val keywordNames: Set<String> by lazy {
        val kinds = Ability.serializer().descriptor.getElementDescriptor(1)
        (0 until kinds.elementsCount).map(kinds::getElementName).toSet() -
            setOf("spell", "activated", "triggered", "static", "special-action", "unparsed")
    }

    /** Effects the engine always hands to the players (its `manual` calls outside Manual steps). */
    private val manualEffects = setOf("support")

    /** Whether [definition] has any part Automatum leaves to the players. */
    fun byHand(definition: CardDefinition): Boolean = handParts(definition).isNotEmpty()

    /** The parts of [definition] Automatum leaves to the players, one short line each (empty: fully supported). */
    fun handParts(definition: CardDefinition): List<String> {
        val found = mutableListOf<String>()
        walk(json.encodeToJsonElement(CardDefinition.serializer(), definition), found)
        return found
    }

    private fun walk(element: JsonElement, found: MutableList<String>) {
        when (element) {
            is JsonArray -> element.forEach { walk(it, found) }
            is JsonObject -> {
                when (val type = (element["type"] as? JsonPrimitive)?.takeIf { it.isString }?.content) {
                    "unparsed" -> { found += "unread text: ${(element["text"] as? JsonPrimitive)?.content.orEmpty()}"; return }
                    "manual" -> found += "by hand: ${(element["text"] as? JsonPrimitive)?.content.orEmpty()}"
                    in manualEffects -> found += "by hand: $type"
                    in keywordNames -> {
                        val keyword = runCatching { json.decodeFromJsonElement(Ability.serializer(), element) }.getOrNull() as? Keyword
                        if (keyword != null && KeywordMeanings.isManual(keyword)) found += "keyword by hand: ${keyword.text}"
                    }
                }
                element.values.forEach { walk(it, found) }
            }
            is JsonPrimitive -> Unit
        }
    }
}

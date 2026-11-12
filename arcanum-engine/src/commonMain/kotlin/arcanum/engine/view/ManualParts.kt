package arcanum.engine.view

import arcanum.api.Ability
import arcanum.api.CardDefinition
import arcanum.api.Keyword
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * The parts of a card definition this engine leaves to the players ([arcanum.engine.CardSupport.manualParts]): text the
 * parser couldn't read ([Ability.Unparsed]), a `Manual` step or cost part, and a keyword the engine doesn't carry out
 * ([KeywordMeanings.isManual]). Follows [arcanum.engine.CardSupport.SUPPORT_VERSION].
 */
internal object ManualParts {
    private val json = Json

    /** The JSON type names of [Ability]'s kinds that are keywords (every concrete [Keyword]). */
    private val keywordNames: Set<String> by lazy {
        val kinds = Ability.serializer().descriptor.getElementDescriptor(1)
        (0 until kinds.elementsCount).map(kinds::getElementName).toSet() -
            setOf("spell", "activated", "triggered", "static", "special-action", "unparsed")
    }

    fun of(definition: CardDefinition): List<String> {
        val found = mutableListOf<String>()
        walk(json.encodeToJsonElement(CardDefinition.serializer(), definition), found)
        return found.distinct()
    }

    private fun walk(element: JsonElement, found: MutableList<String>) {
        when (element) {
            is JsonArray -> element.forEach { walk(it, found) }
            is JsonObject -> {
                when ((element["type"] as? JsonPrimitive)?.takeIf { it.isString }?.content) {
                    "unparsed" -> { found += "unread text: ${(element["text"] as? JsonPrimitive)?.content.orEmpty()}"; return }
                    "manual" -> found += "by hand: ${(element["text"] as? JsonPrimitive)?.content.orEmpty()}"
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

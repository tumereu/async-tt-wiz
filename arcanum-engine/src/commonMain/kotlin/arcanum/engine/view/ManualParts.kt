package arcanum.engine.view

import arcanum.api.Ability
import arcanum.api.CardDefinition
import arcanum.api.Choice
import arcanum.api.Effect
import arcanum.api.Keyword
import arcanum.api.Objects
import arcanum.api.Step
import arcanum.api.isManaAbility
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * The parts of a card definition this engine leaves to the players ([arcanum.engine.Engine.manualParts]): text the
 * parser couldn't read ([Ability.Unparsed]), a `Manual` step or cost part, a keyword the engine only carries out by
 * hand ([KeywordMeanings.isManual]), the few effects and keyword actions the engine always leaves to the players, and the
 * shapes it can't finish at run time: a cost paid on resolution that is "its mana cost" or has an X nothing defines, a
 * mana ability with another step that asks something, a special action with a step besides turning itself face up,
 * unlocking its door or moving itself. Follows [arcanum.engine.Engine.SUPPORT_VERSION].
 */
internal object ManualParts {
    private val json = Json

    /** The JSON type names of [Ability]'s kinds that are keywords (every concrete [Keyword]). */
    private val keywordNames: Set<String> by lazy {
        val kinds = Ability.serializer().descriptor.getElementDescriptor(1)
        (0 until kinds.elementsCount).map(kinds::getElementName).toSet() -
            setOf("spell", "activated", "triggered", "static", "special-action", "unparsed")
    }

    /** Effects the engine always hands to the players (its `manual` calls outside Manual steps). */
    private val manualEffects = emptySet<String>()

    /** Keyword actions the engine hands to the players (`Effects.keywordAction`'s `manual` call): by their JSON type. */
    private val manualKeywordActions = setOf("learn")

    /** Steps of a mana ability's resolution, besides adding the mana, that ask a player something: the engine can't ask
     * while a mana ability resolves (CR 605.3b), so it leaves them to the players (`Payment.finishManaAbility`). */
    private val askingSteps = setOf("choose", "may", "pay")
    private val askingEffects = setOf("scry", "surveil", "search", "look", "reorder", "two-piles", "keyword-action", "reveal-until")

    fun of(definition: CardDefinition): List<String> {
        val found = mutableListOf<String>()
        walk(json.encodeToJsonElement(CardDefinition.serializer(), definition), found)
        definition.faces.flatMap { it.abilities }.forEach { ability ->
            when {
                ability is Ability.Activated && ability.isManaAbility() -> manaAbility(ability, found)
                ability is Ability.SpecialAction -> specialAction(ability, found)
            }
        }
        return found.distinct()
    }

    /** A mana ability's steps besides adding mana (and the colour its mana binds) that ask something. */
    private fun manaAbility(ability: Ability.Activated, found: MutableList<String>) {
        val rest = ability.instructions.steps.filterNot { (it is Step.Do && it.effect is Effect.AddMana) ||
            (it is Step.Choose && (it.choice is Choice.Color || it.choice is Choice.Colors)) }
        val asks = rest.any { step ->
            types(json.encodeToJsonElement(Step.serializer(), step)).any { it in askingSteps || it in askingEffects }
        }
        if (asks) found += "by hand: the rest of the mana ability ${ability.text.orEmpty()}".trim()
    }

    /** A special action's steps besides turning itself face up, unlocking its door or moving itself (CR 116.2). */
    private fun specialAction(ability: Ability.SpecialAction, found: MutableList<String>) {
        val leftover = ability.instructions.steps.filterNot { step ->
            val effect = (step as? Step.Do)?.effect
            (effect is Effect.TurnFaceUp && effect.objects == Objects.Self) ||
                (effect is Effect.UnlockDoor && effect.objects == Objects.Self && effect.door != null) ||
                (effect is Effect.Move && effect.objects == Objects.Self && effect.to.attacking == null)
        }
        if (leftover.isNotEmpty()) found += "by hand: the rest of the special action ${ability.text.orEmpty()}".trim()
    }

    /** Every JSON type name in [element]. */
    private fun types(element: JsonElement): Sequence<String> = when (element) {
        is JsonArray -> element.asSequence().flatMap(::types)
        is JsonObject -> listOfNotNull((element["type"] as? JsonPrimitive)?.takeIf { it.isString }?.content).asSequence() +
            element.values.asSequence().flatMap(::types)
        is JsonPrimitive -> emptySequence()
    }

    /** A cost paid on resolution (a `pay` step) the engine can't work out: "its mana cost", or an X nothing defines. */
    private fun resolutionCost(cost: JsonElement, found: MutableList<String>) {
        when (cost) {
            is JsonArray -> cost.forEach { resolutionCost(it, found) }
            is JsonObject -> {
                when ((cost["type"] as? JsonPrimitive)?.content) {
                    "its-mana-cost" -> found += "by hand: pay its mana cost"
                    "mana" -> (cost["cost"] as? JsonPrimitive)?.content?.takeIf { cost["x"] == null && "{X}" in it }
                        ?.let { found += "by hand: pay $it" }
                }
                cost.values.forEach { resolutionCost(it, found) }
            }
            is JsonPrimitive -> Unit
        }
    }

    private fun walk(element: JsonElement, found: MutableList<String>) {
        when (element) {
            is JsonArray -> element.forEach { walk(it, found) }
            is JsonObject -> {
                when (val type = (element["type"] as? JsonPrimitive)?.takeIf { it.isString }?.content) {
                    "unparsed" -> { found += "unread text: ${(element["text"] as? JsonPrimitive)?.content.orEmpty()}"; return }
                    "manual" -> found += "by hand: ${(element["text"] as? JsonPrimitive)?.content.orEmpty()}"
                    in manualEffects -> found += "by hand: $type"
                    // A keyword action, as an effect or as part of a cost
                    "keyword-action", "action" -> (element["action"] as? JsonObject)?.let { action ->
                        val kind = (action["type"] as? JsonPrimitive)?.content
                        if (kind in manualKeywordActions) found += "by hand: $kind"
                    }
                    "pay" -> element["cost"]?.let { resolutionCost(it, found) }
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

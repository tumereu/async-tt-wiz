package org.asyncttwiz.automatum

import arcanum.api.Color
import arcanum.api.ManaPaymentWay
import arcanum.engine.decision.Answer
import arcanum.engine.decision.ManaUse
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * STK-024 (owner playtest 2026-11-09): the ways one source can pay (a land of two colours, a creature with a mana ability and
 * convoke, several mana abilities) and which of them a tap on it takes: the sensible one ([sensible]), the one after which the
 * rest of the cost can still be paid, as the engine works it out; when the spell cares about the colours spent on it
 * (converge, sunburst, adamant, "if {R} was spent", [ColorCare]) the one that serves that first.
 */
object ManaWays {
    /** What a spell cares about in the colours of the mana spent to cast it. */
    sealed interface ColorCare {
        /** Nothing: any colour does. */
        data object None : ColorCare
        /** The number of colours spent (converge, sunburst): a colour not spent yet serves it. */
        data object Distinct : ColorCare
        /** Mana of these colours ("if {R} was spent", adamant's "at least three white mana"). */
        data class Of(val colors: Set<Color>) : ColorCare
    }

    /** What the spell [spell] on [state] cares about in the colours spent on it ([care] of its abilities as they are, keywords
     * such as sunburst expanded); [ColorCare.None] without one. */
    fun care(state: arcanum.engine.state.GameState, spell: arcanum.engine.state.ObjectId?): ColorCare {
        spell ?: return ColorCare.None
        val abilities = arcanum.engine.Engine.view(state)[spell]?.characteristics?.abilities.orEmpty().map { it.ability }
        return care(abilities)
    }

    /**
     * What a spell with [abilities] cares about in the colours spent to cast it: the number of colours wherever its text reads
     * how many colours of mana were spent ([arcanum.api.Value.ManaSpent] with distinct colours: converge, sunburst), else the
     * colours it reads the amount of ("if {R}{R} was spent", adamant); [ColorCare.None] for anything else.
     */
    fun care(abilities: List<arcanum.api.Ability>): ColorCare {
        var distinct = false
        val colors = mutableSetOf<Color>()
        fun visit(element: JsonElement) {
            when (element) {
                is JsonArray -> element.forEach(::visit)
                is JsonObject -> {
                    if ((element["type"] as? JsonPrimitive)?.content == "mana-spent") {
                        if ((element["distinctColors"] as? JsonPrimitive)?.content == "true") distinct = true
                        (element["color"] as? JsonPrimitive)?.content?.singleOrNull()?.let(Color::of)?.let(colors::add)
                    }
                    element.values.forEach(::visit)
                }
                is JsonPrimitive -> Unit
            }
        }
        abilities.forEach { ability -> runCatching { Json.encodeToJsonElement(arcanum.api.Ability.serializer(), ability) }.getOrNull()?.let(::visit) }
        return when {
            distinct -> ColorCare.Distinct
            colors.isNotEmpty() -> ColorCare.Of(colors)
            else -> ColorCare.None
        }
    }

    /** The colours of mana [use] makes as far as its payment says: the colour it chooses, or each colour of a combination it
     * chooses; none for colourless mana, life and the ways that spend no mana (convoke, improvise, delve). */
    fun colorsOf(use: ManaUse): Set<Color> = when (use) {
        is ManaUse.Ability -> (listOfNotNull(use.color) + use.answers.filterIsInstance<Answer.ColorChoice>().map { it.color }).toSet()
        else -> emptySet()
    }

    /** Whether [use] serves [care] beside the colours [spent] by the rest of the payment. */
    fun serves(use: ManaUse, care: ColorCare, spent: Set<Color>): Boolean = when (care) {
        ColorCare.None -> false
        ColorCare.Distinct -> (colorsOf(use) - spent).isNotEmpty()
        is ColorCare.Of -> colorsOf(use).any { it in care.colors }
    }

    /** [ways] in the order a tap tries them: those serving [care] (beside the colours [spent] by the rest) first, otherwise as the
     * engine lists them. */
    fun preferred(ways: List<ManaUse>, care: ColorCare, spent: Set<Color>): List<ManaUse> =
        if (care == ColorCare.None) ways else ways.sortedBy { if (serves(it, care, spent)) 0 else 1 }

    /**
     * STK-024 (owner playtest 2026-11-09): the way a tap on a source with [ways] pays: the first in [preferred] order after which
     * the rest of the cost can still be paid ([completes]: the engine's own check of the whole payment with that way kept);
     * the first preferred one when none can (the payment then shows what is missing). Null for no ways.
     */
    fun sensible(ways: List<ManaUse>, care: ColorCare, spent: Set<Color>, completes: (ManaUse) -> Boolean): ManaUse? {
        val ordered = preferred(ways, care, spent)
        return ordered.firstOrNull(completes) ?: ordered.firstOrNull()
    }

    /** A way as the chip of a source's ways names it: the mana it makes ("{G}", "{W}{U}"; "{C}" for colourless or what the
     * payment doesn't name), "Convoke", "Improvise" and the like for the ways that spend no mana, "2 life", "Pool mana". */
    fun label(use: ManaUse): String = when (use) {
        is ManaUse.Ability -> colorsOf(use).takeIf { it.isNotEmpty() }?.let { colors ->
            val combination = use.answers.filterIsInstance<Answer.ColorChoice>().map { it.color }
            (combination.ifEmpty { colors.toList() }).joinToString("") { "{${it.symbol}}" }
        } ?: "{C}"
        is ManaUse.Special -> wayName(use.way)
        is ManaUse.Life -> "2 life"
        is ManaUse.Pool -> "Pool mana"
    }

    /** A way to pay that spends no mana, by its keyword. */
    fun wayName(way: ManaPaymentWay): String = when (way) {
        ManaPaymentWay.TapCreatures -> "Convoke"
        ManaPaymentWay.TapCreaturesForAnyMana -> "Tap for any mana"
        ManaPaymentWay.TapArtifacts -> "Improvise"
        ManaPaymentWay.ExileFromGraveyard -> "Delve"
        ManaPaymentWay.Waterbend -> "Waterbend"
    }
}

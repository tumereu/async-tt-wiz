package org.asyncttwiz.protocol.game

import arcanum.api.Ability
import arcanum.api.CardDefinition
import arcanum.api.Layout
import arcanum.parser.OracleCard
import arcanum.parser.OracleParser
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import org.asyncttwiz.protocol.DefinitionFace
import org.asyncttwiz.protocol.PrintedPart
import org.asyncttwiz.protocol.CardDefinition as GameCard

/**
 * A game card's rules as the Arcanum parser reads them (I-D2, I-D54 switch-over item 1): its printed faces from ATW's
 * card data ([printed]) and the engine definition parsed from them ([parse]). The same on the device (which caches the
 * results) and on the server (which plays the game's decks as engine state).
 */
object GameCards {
    /** A game card's layout and printed faces, or why they can't be read. */
    sealed interface Printed {
        data class Card(val layout: Layout, val faces: List<OracleCard>) : Printed
        data class Unreadable(val problem: String) : Printed
    }

    /** What the parser made of a card: [definition] keeps every ability it couldn't read as [Ability.Unparsed] (played
     * by hand), [manualAbilities] counts them; null [definition]: the printed values can't be read ([problem]). */
    data class Parsed(val definition: CardDefinition?, val manualAbilities: Int = 0, val problem: String? = null)

    /** Layouts whose parts share one physical face (PhysicalCardFaces keeps them as [DefinitionFace.parts]). */
    private val SHARED_FACE = setOf("split", "aftermath", "adventure", "flip")

    fun printed(card: GameCard): Printed {
        val single = card.faces.singleOrNull()
        val faces = when {
            single != null && single.parts.isNotEmpty() -> single.parts.map { part(it) }
            // Made before parts were kept: the halves are only in the combined text.
            single != null && card.layout in SHARED_FACE -> return Printed.Unreadable("the parts of this ${card.layout} card aren't in its card data")
            else -> card.faces.map { face(it, card.token) }
        }
        val layout = OracleParser.layout(card.layout, faces) ?: return Printed.Unreadable("faces of layout ${card.layout}")
        return Printed.Card(layout, faces)
    }

    /** [card] parsed with [OracleParser] ([parse] with another parser in tests). */
    fun parse(card: GameCard, parse: (Layout, List<OracleCard>) -> CardDefinition = OracleParser::parseCard): Parsed =
        when (val printed = printed(card)) {
            is Printed.Card -> try {
                val definition = parse(printed.layout, printed.faces)
                Parsed(definition, definition.faces.sumOf { face -> face.abilities.count { it is Ability.Unparsed } })
            } catch (error: RuntimeException) {
                // The parser throws on printed values it can't read (a type line, a mana cost).
                Parsed(null, problem = error.message ?: error::class.simpleName)
            }
            is Printed.Unreadable -> Parsed(null, problem = printed.problem)
        }

    private fun face(face: DefinitionFace, token: Boolean) = OracleCard(
        name = face.name,
        manaCost = face.manaCost.ifBlank { null },
        // A token record's type line starts with "Token", which isn't a card type.
        typeLine = if (token) face.typeLine.removePrefix("Token ") else face.typeLine,
        text = face.rulesText,
        power = face.power.ifBlank { null },
        toughness = face.toughness.ifBlank { null },
        loyalty = face.loyalty.ifBlank { null },
        defense = face.defense.ifBlank { null },
        // Card data made before the field existed: read it from the catalog record the face was made from.
        colorIndicator = face.colorIndicator ?: face.sourceData?.let { data ->
            runCatching { colorIndicator(Json.parseToJsonElement(data).jsonObject) }.getOrNull()
        },
    )

    private fun part(part: PrintedPart) = OracleCard(
        name = part.name,
        manaCost = part.manaCost.ifBlank { null },
        typeLine = part.typeLine,
        text = part.rulesText,
        power = part.power.ifBlank { null },
        toughness = part.toughness.ifBlank { null },
        loyalty = part.loyalty.ifBlank { null },
        defense = part.defense.ifBlank { null },
        colorIndicator = part.colorIndicator,
    )

    /** A catalog record's colour indicator: its colours, when it has no mana cost (CR 204). */
    fun colorIndicator(record: JsonObject): String? {
        if ((record["manaCost"] as? JsonPrimitive)?.contentOrNull?.isNotBlank() == true) return null
        return (record["colors"] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
            ?.filter { it.length == 1 && it in "WUBRG" }?.distinct()?.sortedBy { "WUBRG".indexOf(it) }
            ?.joinToString("")?.takeIf { it.isNotEmpty() }
    }
}

package org.asyncttwiz.automatum

import arcanum.api.Ability
import arcanum.api.CardDefinition
import arcanum.api.CardType
import arcanum.api.Color
import arcanum.api.FaceDefinition
import arcanum.api.Layout
import arcanum.api.ManaCost
import arcanum.api.PrintedNumber
import arcanum.api.Subtype
import arcanum.api.Supertype
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import org.asyncttwiz.protocol.DefinitionFace
import org.asyncttwiz.protocol.PrintedPart
import org.asyncttwiz.protocol.CardDefinition as AtwCard

/**
 * A card the device couldn't read plays with its printed values and manual abilities (I-D21) instead of blocking the
 * game: the engine definition of what ATW's card data says, read leniently. Its name, mana cost, colour indicator, the
 * types and subtypes of its type line it knows, P/T, loyalty and defense; its whole rules text is one unparsed ability,
 * which players carry out by hand.
 */
object PrintedCards {
    /** The engine definition of [card]'s printed values. */
    fun definition(card: AtwCard): CardDefinition {
        val single = card.faces.singleOrNull()
        if (single != null && single.parts.size > 1) {
            val layout = when (card.layout) {
                "split", "aftermath" -> Layout.Split
                "flip" -> Layout.Flip
                "adventure" -> Layout.Adventure
                else -> null
            }
            if (layout != null) return CardDefinition(layout, single.parts.map { part(it, card.token) })
            return CardDefinition(Layout.Normal, listOf(part(single.parts.first(), card.token)))
        }
        val layout = when (card.layout) {
            "transform" -> Layout.Transform
            "modal_dfc" -> Layout.ModalDoubleFaced
            else -> null
        }
        if (layout != null && card.faces.size == 2) return CardDefinition(layout, card.faces.map { face(it, card.token) })
        return CardDefinition(Layout.Normal, listOfNotNull(card.faces.firstOrNull()?.let { face(it, card.token) }))
    }

    private fun face(face: DefinitionFace, token: Boolean) = printed(face.name, face.manaCost, face.typeLine, face.rulesText,
        face.power, face.toughness, face.loyalty, face.defense, face.colorIndicator ?: indicator(face.sourceData), token)

    private fun part(part: PrintedPart, token: Boolean) = printed(part.name, part.manaCost, part.typeLine, part.rulesText,
        part.power, part.toughness, part.loyalty, part.defense, part.colorIndicator, token)

    private fun printed(name: String, manaCost: String, typeLine: String, rulesText: String, power: String, toughness: String,
        loyalty: String, defense: String, colorIndicator: String?, token: Boolean): FaceDefinition {
        val (front, back) = typeLine.split('—', limit = 2).let { it[0] to it.getOrNull(1).orEmpty() }
        val words = front.trim().split(Regex("\\s+")).map { it.lowercase() }.filter { it.isNotEmpty() && !(token && it == "token") }
        return FaceDefinition(
            name = name,
            manaCost = ManaCost.parse(manaCost.trim()),
            colorIndicator = colorIndicator?.mapNotNull(Color::of)?.distinct()?.takeIf { it.isNotEmpty() },
            supertypes = words.mapNotNull(SUPERTYPES::get),
            types = words.mapNotNull(TYPES::get),
            subtypes = back.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }.map(::Subtype),
            power = number(power), toughness = number(toughness), loyalty = number(loyalty), defense = number(defense),
            abilities = listOfNotNull(rulesText.trim().takeIf { it.isNotEmpty() }?.let(Ability::Unparsed)),
        )
    }

    private val TYPES: Map<String, CardType> = mapOf("artifact" to CardType.Artifact, "battle" to CardType.Battle,
        "creature" to CardType.Creature, "enchantment" to CardType.Enchantment, "instant" to CardType.Instant,
        "kindred" to CardType.Kindred, "tribal" to CardType.Kindred, "land" to CardType.Land,
        "planeswalker" to CardType.Planeswalker, "sorcery" to CardType.Sorcery)
    private val SUPERTYPES: Map<String, Supertype> = mapOf("basic" to Supertype.Basic, "legendary" to Supertype.Legendary,
        "snow" to Supertype.Snow, "world" to Supertype.World)

    private fun number(text: String): PrintedNumber? {
        val value = text.trim()
        if (value.isEmpty()) return null
        value.toIntOrNull()?.let { return PrintedNumber.Fixed(it) }
        if (value == "X") return PrintedNumber.X
        if (value == "*") return PrintedNumber.Star()
        Regex("(\\d+)\\+\\*").matchEntire(value)?.let { return PrintedNumber.Star(it.groupValues[1].toInt()) }
        return null
    }

    /** A catalog record's colour indicator (CR 204): a coloured face without a mana cost has one (CR 202.2). */
    private fun indicator(sourceData: String?): String? {
        val record = sourceData?.let { runCatching { Json.parseToJsonElement(it) as? JsonObject }.getOrNull() } ?: return null
        if ((record["manaCost"] as? JsonPrimitive)?.contentOrNull?.isNotBlank() == true) return null
        return (record["colors"] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
            ?.filter { it.length == 1 && it in "WUBRG" }?.joinToString("")?.takeIf { it.isNotEmpty() }
    }
}

package org.asyncttwiz.storage

import kotlinx.serialization.json.*
import org.asyncttwiz.protocol.*
import java.util.Locale

/** Ready-to-render and ready-to-submit; no provider parsing or identity hashing on the dialog path. */
data class CatalogTokenChoice(val submitted: SubmittedDefinition, val identity: String, val name: String,
    val detail: String, val searchNames: List<String>) {
    companion object {
        fun from(submitted: SubmittedDefinition): CatalogTokenChoice {
            val definition = submitted.definition
            val face = definition.faces.first()
            val detail = listOf(face.corner.ifEmpty {
                if (face.power.isNotEmpty() || face.toughness.isNotEmpty()) "${face.power}/${face.toughness}" else ""
            }, face.rulesText.ifBlank { TokenIdentity.attributes(face) }).filter(String::isNotBlank).joinToString(" · ")
            return CatalogTokenChoice(submitted, TokenIdentity.key(definition), definition.name, detail,
                (listOf(definition.name) + definition.faces.map { it.name }).distinct().map { it.lowercase(Locale.ROOT) })
        }
    }
}

data class CatalogTokenList(val generation: String, val choices: List<CatalogTokenChoice>)
class CatalogTokensNotReadyException : IllegalStateException("Token catalog is being prepared. Retry shortly.")

/** Shared by catalog precomputation, deck snapshots and inspection. */
fun ResolvedDeckEntry.definition(catalog: CatalogMetadata?): CardDefinition {
    val sides = physicalSides(faces)
    val ids = sides.map { it.id }.toSet()
    val definitions = sides.map { face ->
        val raw = DeckJson.format.parseToJsonElement(face.json).jsonObject
        fun field(key: String) = (raw[key] as? JsonPrimitive)?.contentOrNull.orEmpty()
        val power = field("power"); val toughness = field("toughness")
        val loyalty = field("loyalty"); val defense = field("defense")
        DefinitionFace(face.id, field("faceName").ifBlank { face.name }, face.otherFaces.filter { it in ids },
            field("type"), field("manaCost"), field("text"), power, toughness, loyalty, defense,
             scryfallId = face.scryfallId, sourceData = face.json, colorIndicator = catalogColorIndicator(raw))
            .let { it.copy(corner = it.defaultCorner()) }
    }
    val raw = DeckJson.format.parseToJsonElement(printing.json).jsonObject
    val definition = CardDefinition(printing.name, definitions, provider = "mtgjson",
        sourceVersion = catalog?.let { "${it.date}/${it.version}" }, printingId = printing.id,
        layout = (raw["layout"] as? JsonPrimitive)?.contentOrNull ?: "normal", token = printing.token)
    // Linked token/designation records are physical sides, even for provider layouts such as
    // double_faced_token or token. Do not flatten them like split/adventure spell parts.
    return if (printing.token && definition.layout !in setOf("adventure", "split", "flip", "aftermath")) definition
        else PhysicalCardFaces.normalize(definition)
}

/** Whether [printing], with [linked] (it and the faces it links to), is a meld result, the shared back of two fronts ([physicalSides]). */
internal fun meldResult(printing: CatalogPrinting, linked: List<CatalogPrinting>): Boolean =
    printing.side != FRONT_SIDE && linked.count { it.side == FRONT_SIDE } > 1

/**
 * The sides of one physical card, from a printing and the faces it links to, in the given order. Problem report 7ZZC89:
 * MTGJSON links a meld result to both its fronts (each front only to the result), while a physical meld card is one
 * front with the result on its back (CR 712.4), so of two fronts only the first stays.
 */
internal fun physicalSides(faces: List<CatalogPrinting>): List<CatalogPrinting> {
    val front = faces.firstOrNull { it.side == FRONT_SIDE }
    return faces.filter { it.side != FRONT_SIDE || it === front }
}

private const val FRONT_SIDE = "a"

/**
 * A catalog face's colour indicator (CR 204) as WUBRG letters. The catalog keeps a face's colours, not its indicator:
 * a coloured face without a mana cost has its colour from an indicator (CR 202.2), as a double-faced card's back face.
 */
internal fun catalogColorIndicator(record: JsonObject): String? = org.asyncttwiz.protocol.game.GameCards.colorIndicator(record)

/** Owner's gameplay identity, deliberately excluding printing metadata and redundant keyword tags. */
internal fun tokenGameplayIdentity(definition: CardDefinition): String = DeckJson.intent("catalog-token", DeckJson.canonical(
    listOf(listOf(definition.name)) + definition.faces.map { face ->
        val colors = (face.sourceData?.let { DeckJson.format.parseToJsonElement(it).jsonObject["colors"] } as? JsonArray)
            ?.map { it.jsonPrimitive.content }?.sorted().orEmpty()
        listOf(face.typeLine, face.rulesText, face.power, face.toughness, DeckJson.canonical(colors))
    }))

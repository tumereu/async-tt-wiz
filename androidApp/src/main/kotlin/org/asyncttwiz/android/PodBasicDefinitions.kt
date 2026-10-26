package org.asyncttwiz.android

import kotlinx.serialization.json.*
import org.asyncttwiz.protocol.*
import org.asyncttwiz.storage.*

/** Fixed default: English M21 core-set paper, non-promo, lowest collector number then UUID.
 * M21's lowest-numbered basics are regular-frame art. No fallback to device-dependent printings.
 * Catalog build metadata is omitted so a catalog refresh alone does not change these definitions. */
internal fun podBasicDefinitions(repository: CatalogRepository): List<SubmittedDefinition> = BasicLand.entries.map { land ->
    val printing = repository.find(land.cardName, set = "M21", limit = 1000).filter { card ->
        val raw = DeckJson.format.parseToJsonElement(card.json).jsonObject
        card.name == land.cardName && card.language in listOf("English", "en") && !card.token && card.otherFaces.isEmpty() &&
            raw["isPromo"]?.jsonPrimitive?.booleanOrNull != true && raw["isOnlineOnly"]?.jsonPrimitive?.booleanOrNull != true &&
            ((raw["availability"] as? JsonArray)?.any { it.jsonPrimitive.content == "paper" } != false)
    }.minWithOrNull(compareBy<CatalogPrinting> { it.number.toIntOrNull() ?: Int.MAX_VALUE }.thenBy { it.number }.thenBy { it.id })
        ?: throw LobbyFailure("basic_definitions_unavailable")
    val definition = ResolvedDeckEntry(DeckSection.MAINBOARD, 1, listOf(printing), emptyList()).definition(null)
    SubmittedDefinition(DeckJson.digest(definition), definition)
}.also { if (PodGamePreparation.basicDefinitions(it) == null) throw LobbyFailure("basic_definitions_unavailable") }

package org.asyncttwiz.android

import kotlinx.serialization.json.*
import org.asyncttwiz.protocol.*
import org.asyncttwiz.storage.DeckImportResult
import java.util.UUID

/** Immutable, volatile retry payload. Built on the import worker, not the UI thread. */
data class DeckSavePlan(val definitions: SubmitDefinitions, val deck: CreateDeck) {
    companion object {
        fun from(result: DeckImportResult, name: String): DeckSavePlan {
            if (!result.resolved || name.isBlank() || !DeckValidation.text(name)) throw DeckFailure("invalid_deck")
            val definitions = linkedMapOf<String, SubmittedDefinition>()
            val entries = result.entries.map { entry ->
                val faces = entry.faces.map { face ->
                    val raw = DeckJson.format.parseToJsonElement(face.json).jsonObject
                    fun field(key: String) = (raw[key] as? JsonPrimitive)?.contentOrNull.orEmpty()
                    val power = field("power"); val toughness = field("toughness")
                    val loyalty = field("loyalty"); val defense = field("defense")
                    DefinitionFace(face.id, field("faceName").ifBlank { face.name }, face.otherFaces,
                        field("type"), field("manaCost"), field("text"), power, toughness, loyalty, defense,
                        when { power.isNotEmpty() || toughness.isNotEmpty() -> "$power/$toughness"
                            loyalty.isNotEmpty() -> loyalty; else -> defense }, face.scryfallId, sourceData = face.json)
                }
                val raw = DeckJson.format.parseToJsonElement(entry.printing.json).jsonObject
                val definition = CardDefinition(entry.printing.name, faces, provider = "mtgjson",
                    sourceVersion = result.catalog?.let { "${it.date}/${it.version}" }, printingId = entry.printing.id,
                    layout = (raw["layout"] as? JsonPrimitive)?.contentOrNull ?: "normal", token = entry.printing.token)
                if (!DeckValidation.definition(definition)) throw DeckFailure("invalid_definition")
                val digest = DeckJson.digest(definition)
                definitions[digest] = SubmittedDefinition(digest, definition)
                SavedDeckEntry(SavedDeckSection.valueOf(entry.section.name), digest, entry.quantity)
            }.groupBy { it.section to it.definition }.map { (_, rows) ->
                val quantity = rows.sumOf { it.quantity.toLong() }
                if (quantity > Int.MAX_VALUE) throw DeckFailure("invalid_deck")
                rows.first().copy(quantity = quantity.toInt())
            }
            return DeckSavePlan(SubmitDefinitions(UUID.randomUUID().toString(), definitions.values.toList()),
                CreateDeck(UUID.randomUUID().toString(), name, entries))
        }
    }
}

internal fun deckError(failure: Exception): String = when ((failure as? DeckFailure)?.code) {
    "not_found" -> "This saved deck is no longer available. Return to your collection to refresh."
    "invalid_cover" -> "Choose a card that is still in this deck."
    "invalid_deck" -> "Enter a deck name and check the quantities. Your pasted list is kept."
    "invalid_definition", "definitions_unavailable" -> "Card metadata could not be accepted. Edit the list or update card records and retry."
    "request_too_large" -> "This import exceeds the service transfer limit. Your pasted list is kept."
    "authentication_required" -> "Your session is no longer valid. Sign in again to access saved decks."
    "request_conflict" -> "This save identity conflicts with an earlier request. Edit the list or name before saving again."
    "try_later" -> "The service is busy. Wait a moment, then retry."
    else -> "Could not reach the deck service. Your input is kept. Check your connection and retry."
}

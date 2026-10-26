package org.asyncttwiz.android

import org.asyncttwiz.protocol.*
import org.asyncttwiz.storage.DeckImportResult
import org.asyncttwiz.storage.CatalogMetadata
import org.asyncttwiz.storage.ResolvedDeckEntry
import org.asyncttwiz.storage.definition as catalogDefinition
import java.util.UUID

/** Immutable, volatile retry payload. Built on the import worker, not the UI thread. */
data class DeckSavePlan(val definitions: SubmitDefinitions, val deck: CreateDeck,
    val target: String? = null, val mode: DeckImportMode = DeckImportMode.OVERWRITE,
    val cover: String? = null, val editMetadata: Boolean = false) {
    fun update() = UpdateDeck(deck.requestId, name = deck.name.takeIf { editMetadata }, cover = cover,
        contents = DeckContentsImport(deck.entries, mode))
    companion object {
        fun from(result: DeckImportResult, name: String, target: String? = null,
            mode: DeckImportMode = DeckImportMode.OVERWRITE, cover: String? = null,
            retained: Map<String, CardDefinition> = emptyMap(), editMetadata: Boolean = false): DeckSavePlan {
            if (!result.resolved || name.isBlank() || !DeckValidation.text(name)) throw DeckFailure("invalid_deck")
            val definitions = linkedMapOf<String, SubmittedDefinition>()
            val normalizedDigests = mutableMapOf<String, String>()
            val entries = result.entries.map { entry ->
                val original = retained[entry.printing.id] ?: entry.definition(result.catalog)
                val definition = PhysicalCardFaces.normalize(original)
                if (!DeckValidation.definition(definition)) throw DeckFailure("invalid_definition")
                val digest = DeckJson.digest(definition)
                normalizedDigests[DeckJson.digest(original)] = digest
                definitions[digest] = SubmittedDefinition(digest, definition)
                SavedDeckEntry(SavedDeckSection.valueOf(entry.section.name), digest, entry.quantity)
            }.groupBy { it.section to it.definition }.map { (_, rows) ->
                val quantity = rows.sumOf { it.quantity.toLong() }
                if (quantity > Int.MAX_VALUE) throw DeckFailure("invalid_deck")
                rows.first().copy(quantity = quantity.toInt())
            }
            val normalizedCover = normalizedDigests[cover] ?: cover
            if (normalizedCover != null && entries.none { it.definition == normalizedCover }) throw DeckFailure("invalid_cover")
            return DeckSavePlan(SubmitDefinitions(UUID.randomUUID().toString(), definitions.values.toList()),
                CreateDeck(UUID.randomUUID().toString(), name, entries), target, mode, normalizedCover, editMetadata)
        }
    }
}

internal fun ResolvedDeckEntry.definition(catalog: CatalogMetadata?): CardDefinition = catalogDefinition(catalog)

internal fun deckError(failure: Exception): String = when ((failure as? DeckFailure)?.code) {
    "not_found" -> "This saved deck is no longer available. Return to your collection to refresh."
    "invalid_cover" -> "Choose a card that is still in this deck."
    "quantity_overflow" -> "Merged quantities exceed the supported number range. Edit the list or choose Overwrite. Your pasted list is kept."
    "invalid_deck" -> "Enter a deck name and check the quantities. Your pasted list is kept."
    "invalid_definition", "definitions_unavailable" -> "Card metadata could not be accepted. Edit the list or update card records and retry."
    "request_too_large" -> "This import exceeds the service transfer limit. Your pasted list is kept."
    "authentication_required" -> "Your session is no longer valid. Sign in again to access saved decks."
    "request_conflict" -> "This save identity conflicts with an earlier request. Edit the list or name before saving again."
    "try_later" -> "The service is busy. Wait a moment, then retry."
    else -> "Could not reach the deck service. Your input is kept. Check your connection and retry."
}

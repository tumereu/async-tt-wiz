package org.asyncttwiz.storage

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.asyncttwiz.protocol.CardDefinition

/**
 * Card data saved before it kept the printed values a rules engine reads (I-D21): a split, aftermath, adventure or flip
 * card's halves ([org.asyncttwiz.protocol.DefinitionFace.parts]) and a face's colour indicator
 * ([org.asyncttwiz.protocol.DefinitionFace.colorIndicator]). Such card data is rebuilt from the installed catalog, as
 * importing the card today would make it.
 */
object CardDataRefresh {
    /** Layouts whose halves share one printed face (PhysicalCardFaces keeps them as parts). */
    private val SHARED_FACE = setOf("split", "aftermath", "adventure", "flip")

    /** Whether [definition] is catalog card data made before those fields existed, and missing one of them. */
    fun stale(definition: CardDefinition): Boolean {
        if (definition.provider != "mtgjson" || definition.printingId == null) return false
        val single = definition.faces.singleOrNull()
        if (single != null && definition.layout in SHARED_FACE && single.parts.isEmpty()) return true
        return definition.faces.any { face ->
            face.parts.isEmpty() && face.colorIndicator == null && face.sourceData?.let { data ->
                runCatching { catalogColorIndicator(Json.parseToJsonElement(data).jsonObject) }.getOrNull()
            } != null
        }
    }

    /**
     * [definition] rebuilt from the catalog printing it was made from ([printing] reads a catalog record by id), as a
     * deck import makes it; null when the catalog doesn't have that printing (or one of its faces) or can't give the
     * missing values either.
     */
    fun rebuild(definition: CardDefinition, metadata: CatalogMetadata?, printing: (String) -> CatalogPrinting?): CardDefinition? {
        val first = definition.printingId?.let(printing) ?: return null
        val faces = (first.otherFaces + first.id).distinct().map { printing(it) ?: return null }
            .sortedWith(compareBy<CatalogPrinting> { it.side }.thenBy { it.id })
        // As a deck import builds it (halves kept as parts, PhysicalCardFaces)
        val rebuilt = ResolvedDeckEntry(DeckSection.MAINBOARD, 1, faces, emptyList()).definition(metadata)
        return rebuilt.takeIf { !stale(it) && it.name == definition.name }
    }

    /** [rebuild] from [repository]'s installed catalog. Worker only. */
    fun rebuild(definition: CardDefinition, repository: CatalogRepository): CardDefinition? =
        rebuild(definition, repository.metadata(), repository::printing)
}

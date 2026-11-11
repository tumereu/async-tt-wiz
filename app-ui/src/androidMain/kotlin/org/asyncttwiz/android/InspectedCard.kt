package org.asyncttwiz.android

import org.asyncttwiz.protocol.*
import org.asyncttwiz.storage.*

/** Captured presentation only. Never looks up a newer catalog, edits a deck or records game exposure. */
data class InspectedCard(val definition: CardDefinition, val artwork: List<ArtworkReference?>) {
    init { require(definition.faces.isNotEmpty() && artwork.size == definition.faces.size) }
    companion object {
        fun retained(definition: CardDefinition) = InspectedCard(definition,
            definition.faces.mapIndexed { index, face -> face.artwork(index, "large") })
        fun preview(entry: ResolvedDeckEntry, catalog: CatalogMetadata?) = InspectedCard(entry.definition(catalog),
            entry.faces.map { ArtworkReference.from(it, "large") })
    }
}

/** A flip card currently on its flipped half: inspection shows it turned 180° so that half reads upright. */
internal fun flippedOnTable(definition: CardDefinition, faceId: String): Boolean =
    definition.layout == "flip" && definition.faces.indexOfFirst { it.faceId == faceId } > 0

package org.asyncttwiz.android

import androidx.compose.runtime.*
import org.asyncttwiz.protocol.*
import org.asyncttwiz.storage.*

/** Captured presentation only. Never looks up a newer catalog, edits a deck or records game exposure. */
data class InspectedCard(val definition: CardDefinition, val artwork: List<ArtworkReference?>) {
    init { require(definition.faces.isNotEmpty() && artwork.size == definition.faces.size) }
    companion object {
        fun retained(identity: String, definition: CardDefinition) = InspectedCard(definition,
            definition.faces.mapIndexed { index, face -> face.artwork(index, identity, "large") })
        fun preview(entry: ResolvedDeckEntry, catalog: CatalogMetadata?) = InspectedCard(entry.definition(catalog),
            entry.faces.map { ArtworkReference.from(it, catalog?.checksum.orEmpty(), "large") })
    }
}

data class CardInspectionState(val card: InspectedCard, val face: Int = 0, val enlarged: Boolean = false)

/** Owned by the existing rotation-retained feature controller; private state is cleared on Logout/exit. */
class CardInspectionController {
    var state by mutableStateOf<CardInspectionState?>(null)
        private set
    fun open(card: InspectedCard) { state = CardInspectionState(card) }
    fun face(index: Int) { state?.let { if (index in it.card.definition.faces.indices) state = it.copy(face = index, enlarged = false) } }
    fun zoom() { state = state?.let { it.copy(enlarged = !it.enlarged) } }
    fun close() { state = null }
}

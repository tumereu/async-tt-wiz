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

/** [turned] is the personal rotate-button state of the shown face (aftermath/flip halves). */
data class CardInspectionState(val card: InspectedCard, val face: Int = 0, val enlarged: Boolean = false, val turned: Boolean = false) {
    val degrees get() = card.orientation(face).shown(turned)
}

/** Owned by the existing rotation-retained feature controller; private state is cleared on Logout/exit. */
class CardInspectionController {
    var state by mutableStateOf<CardInspectionState?>(null)
        private set
    /** Opens on [face] (e.g. the face a game card currently shows); an index outside the card opens its first face.
     * [turned] opens with the rotate button already pressed (a flipped flip card shows its picture upside down). */
    fun open(card: InspectedCard, face: Int = 0, turned: Boolean = false) {
        val index = face.takeIf { it in card.definition.faces.indices } ?: 0
        state = CardInspectionState(card, index, turned = turned && index == face && card.orientation(index).alternate != null)
    }
    fun face(index: Int) { state?.let { if (index in it.card.definition.faces.indices) state = it.copy(face = index, enlarged = false, turned = false) } }
    fun zoom() { state = state?.let { it.copy(enlarged = !it.enlarged) } }
    fun turn() { state = state?.let { it.copy(turned = !it.turned) } }
    fun close() { state = null }
}

/** A flip card currently on its flipped half: inspection opens turned 180° so that half reads upright. */
internal fun flippedOnTable(definition: CardDefinition, faceId: String): Boolean =
    definition.layout == "flip" && definition.faces.indexOfFirst { it.faceId == faceId } > 0

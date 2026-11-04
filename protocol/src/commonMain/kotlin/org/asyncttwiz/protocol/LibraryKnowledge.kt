package org.asyncttwiz.protocol

import kotlinx.serialization.Serializable

/** One segment of a Library (or Temp pile), top first; its [cards] are the next cards of the container's
 * concrete order (they always concatenate to exactly the container's cards). An unordered layer's order is
 * unknown to everyone (positional knowledge, not face visibility). The [id] stays stable while other layers
 * are added, split or removed, so edits touch only the layers (and members) they really change. */
@Serializable data class LibraryLayer(val id: String, val ordered: Boolean, val cards: List<String>) {
    val size: Int get() = cards.size
}

/** A layer with its concrete members, top first. */
data class LibrarySegment(val layer: LibraryLayer, val cards: List<String> = layer.cards)

/** Read helpers for the layer model. Every LIBRARY-zone container's layers concatenate to its cards, with no
 * empty layers; a lone card's place is known, and neighbouring known (ordered) layers are merged. */
object LibraryLayers {
    /** Layers that do not cover exactly the cards (never produced by the rules) carry no knowledge;
     * the next change of that container replaces them with one unordered layer. */
    private fun consistent(container: PlayContainer) =
        container.layers.sumOf { it.size } == container.cards.size && container.layers.flatMap { it.cards } == container.cards

    fun segmentOf(container: PlayContainer, cardId: String): LibrarySegment? =
        container.layers.firstOrNull { cardId in it.cards }?.takeIf { consistent(container) }?.let(::LibrarySegment)
    /** Whether the card's place in its Library is known to everyone. */
    fun known(container: PlayContainer, cardId: String): Boolean = segmentOf(container, cardId)?.layer?.ordered == true

    /** A freshly built Library: its whole content in one unordered layer. */
    fun initial(id: String, cards: List<String>): List<LibraryLayer> = normalize(listOf(LibraryLayer(id, false, cards)))

    fun valid(container: PlayContainer): Boolean {
        if (container.zone != PlayZone.LIBRARY) return container.layers.isEmpty()
        val layers = container.layers
        return layers.flatMap { it.cards } == container.cards && layers.all { it.size > 0 && DeckValidation.uuid(it.id) } &&
            layers.map { it.id }.distinct().size == layers.size && layers.none { !it.ordered && it.size < 2 } &&
            layers.zipWithNext().none { (a, b) -> a.ordered && b.ordered }
    }

    internal fun normalize(parts: List<LibraryLayer>): List<LibraryLayer> {
        val result = mutableListOf<LibraryLayer>()
        for (raw in parts) {
            if (raw.cards.isEmpty()) continue
            val part = if (raw.cards.size == 1) raw.copy(ordered = true) else raw
            val last = result.lastOrNull()
            if (last != null && last.ordered && part.ordered) result[result.lastIndex] = last.copy(cards = last.cards + part.cards)
            else result += part
        }
        return result
    }
}

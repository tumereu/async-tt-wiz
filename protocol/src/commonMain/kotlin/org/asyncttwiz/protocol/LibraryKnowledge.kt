package org.asyncttwiz.protocol

import kotlinx.serialization.Serializable

/** One segment of a Library (or Temp pile), top first; its [cards] are the next cards of the container's
 * concrete order (they always concatenate to exactly the container's cards). An unordered layer's order is
 * unknown to everyone (positional knowledge, not face visibility). The [id] stays stable while other layers
 * are added, split or removed, so edits touch only the layers (and members) they really change. */
@Serializable data class LibraryLayer(val id: String, val ordered: Boolean, val cards: List<String>) {
    val size: Int get() = cards.size
}

/** A branch start reshuffles ONE unordered layer within itself. */
@Serializable data class LibraryReseed(val containerId: String, val layerId: String, val from: List<String>,
    val to: List<String>, val algorithm: String = LibraryLayers.RESEED_ALGORITHM)

/** A layer with its concrete members, top first. */
data class LibrarySegment(val layer: LibraryLayer, val cards: List<String> = layer.cards)

/** Read helpers for the layer model. Every LIBRARY-zone container's layers concatenate to its cards, with no
 * empty layers; a lone card's place is known, and neighbouring known (ordered) layers are merged. */
object LibraryLayers {
    const val RESEED_ALGORITHM = "branch-reseed-v2"

    /** Layers that do not cover exactly the cards (never produced by the rules) carry no knowledge;
     * the next change of that container replaces them with one unordered layer. */
    private fun consistent(container: PlayContainer) =
        container.layers.sumOf { it.size } == container.cards.size && container.layers.flatMap { it.cards } == container.cards

    fun segments(container: PlayContainer): List<LibrarySegment> =
        if (!consistent(container)) emptyList() else container.layers.map(::LibrarySegment)
    fun segmentOf(container: PlayContainer, cardId: String): LibrarySegment? =
        container.layers.firstOrNull { cardId in it.cards }?.takeIf { consistent(container) }?.let(::LibrarySegment)
    /** Whether the card's place in its Library is known to everyone. */
    fun known(container: PlayContainer, cardId: String): Boolean = segmentOf(container, cardId)?.layer?.ordered == true
    /** The known-order block at the top (empty when the top layer is unordered). */
    fun knownTop(container: PlayContainer): List<String> =
        container.layers.takeIf { consistent(container) }?.firstOrNull()?.takeIf { it.ordered }?.cards.orEmpty()
    /** The known-order block at the bottom (empty when the bottom layer is unordered). */
    fun knownBottom(container: PlayContainer): List<String> =
        container.layers.takeIf { consistent(container) }?.lastOrNull()?.takeIf { it.ordered }?.cards.orEmpty()

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

/** Knowledge is positional, not face visibility. Looking alone does not fix an unknown order;
 * explicitly placing the looked-at block does. No operation here reads retained history. */
internal object LibraryKnowledge {
    fun seed(state: PlayState, actor: String, libraryId: String, layerId: String): String {
        // A play without an entropy field gets a domain-separated play-id hash as its stable secret;
        // never generate fresh entropy when loading an existing game.
        val secret = state.librarySecret ?: encoded(DeckJson.intent("legacy-library-secret-v1", state.id))
        return encoded(DeckJson.intent(LibraryLayers.RESEED_ALGORITHM,
            listOf(secret, state.currentNodeId, actor, libraryId, layerId).joinToString("\u0000")))
    }

    private fun encoded(hex: String) = RandomV1.encodeSeed(hex.chunked(2).map { it.toInt(16).toByte() }.toByteArray())

    /** Every unordered layer of every Library-zone container, each with its own deterministic seed. */
    fun reseeds(state: PlayState, actor: String): List<LibraryReseed> = state.board.containers
        .filter { it.zone == PlayZone.LIBRARY }.flatMap { library ->
            LibraryLayers.segments(library).filter { !it.layer.ordered && it.cards.size >= 2 }.map { segment ->
                LibraryReseed(library.id, segment.layer.id, segment.cards,
                    RandomV1.shuffle(segment.cards, seed(state, actor, library.id, segment.layer.id), LibraryLayers.RESEED_ALGORITHM))
            }
        }

    fun applyReseeds(state: PlayState, changes: List<LibraryReseed>, reverse: Boolean): PlayState? {
        if (changes.isEmpty()) return state
        if (changes.map { it.containerId to it.layerId }.distinct().size != changes.size) return null
        var board = state.board
        for (change in changes) {
            val library = board.containers.singleOrNull { it.id == change.containerId && it.zone == PlayZone.LIBRARY } ?: return null
            val from = if (reverse) change.to else change.from
            val to = if (reverse) change.from else change.to
            val index = library.layers.indexOfFirst { it.id == change.layerId }
            val layer = library.layers.getOrNull(index) ?: return null
            val start = library.layers.take(index).sumOf { it.size }
            if (change.algorithm != LibraryLayers.RESEED_ALGORITHM || layer.ordered || layer.cards != from ||
                start + layer.size > library.cards.size || library.cards.subList(start, start + layer.size) != from ||
                from.size != to.size || from.toSet() != to.toSet() || to.distinct().size != to.size) return null
            board = board.copy(containers = board.containers.map {
                if (it.id == library.id) it.copy(cards = it.cards.take(start) + to + it.cards.drop(start + layer.size),
                    layers = it.layers.map { l -> if (l.id == layer.id) l.copy(cards = to) else l }) else it
            })
        }
        return state.copy(board = board)
    }

    /** Selective edits can change a layer's incoming order, but never reroll the retained result.
     * The layer and its membership must still match the accepted reseed. */
    fun rebaseReseeds(state: PlayState, changes: List<LibraryReseed>): List<LibraryReseed>? = changes.map { change ->
        val library = state.board.containers.singleOrNull { it.id == change.containerId && it.zone == PlayZone.LIBRARY }
            ?: return null
        val segment = LibraryLayers.segments(library).singleOrNull { it.layer.id == change.layerId && !it.layer.ordered } ?: return null
        if (segment.cards.size != change.from.size || segment.cards.toSet() != change.from.toSet()) return null
        change.copy(from = segment.cards)
    }

    /** Source edge intent is explicit; ordinary MEMBER moves never silently pick another card.
     * A fork may also translate a strictly captured edge after validating it on its original board. */
    fun resolveEdge(state: PlayState, command: GameCommand, beforeReseed: PlayState? = null): GameCommand {
        var move = (command.action as? GameAction.Play)?.action as? PlayAction.MoveCard ?: return command
        val destination = move.destination
        if (destination.insertion == MoveInsertion.GAP && destination.libraryInsertion != null) {
            val target = state.board.containers.singleOrNull { it.id == destination.containerId && it.zone == PlayZone.LIBRARY }
            if (target != null) {
                val reduced = target.cards.filterNot { it == move.cardId || it in destination.pendingIds }
                val after = when {
                    beforeReseed != null && destination.libraryInsertion == MoveInsertion.BOTTOM && destination.beforeId == null &&
                        destination.afterId !in LibraryLayers.knownBottom(target) -> reduced.lastOrNull()
                    else -> destination.afterId
                }
                if (after == null || after in reduced) {
                    val before = reduced.getOrNull(if (after == null) 0 else reduced.indexOf(after) + 1)
                    move = move.copy(destination = destination.copy(afterId = after, beforeId = before))
                }
            }
        }
        val resolved = command.copy(action = GameAction.Play(move))
        if (!move.source.takeFromEdge && beforeReseed == null) return resolved
        val source = state.board.containers.singleOrNull { it.id == move.source.containerId && it.zone == PlayZone.LIBRARY }
            ?: return resolved
        val old = beforeReseed?.board?.containers?.singleOrNull { it.id == source.id }
        val sliceSize = move.source.bottomSlice.size
        if (old != null && !move.source.takeFromEdge) {
            if (move.source.edge == MoveSourceEdge.TOP && old.cards.firstOrNull() != move.cardId ||
                move.source.edge == MoveSourceEdge.BOTTOM && old.cards.lastOrNull() != move.cardId ||
                sliceSize > 0 && (old.cards.takeLast(sliceSize) != move.source.bottomSlice || move.source.bottomSlice.first() != move.cardId)) return resolved
        }
        val slice = if (sliceSize > 0) source.cards.takeLast(sliceSize) else emptyList()
        val id = when {
            slice.isNotEmpty() -> slice.first()
            move.source.edge == MoveSourceEdge.TOP -> source.cards.firstOrNull()
            move.source.edge == MoveSourceEdge.BOTTOM -> source.cards.lastOrNull()
            else -> null
        } ?: return resolved
        return command.copy(action = GameAction.Play(move.copy(cardId = id, source = move.source.copy(bottomSlice = slice))))
    }

    /** The layers of every Library-zone container after [action] turned [before] into [board]. New layers get
     * request-derived IDs; untouched layers keep theirs. Cards that left are dropped from their layers. */
    fun after(before: BoardState, board: BoardState, action: PlayAction, requestId: String,
        rulesVersion: Int = CURRENT_GAMEPLAY_RULES_VERSION): BoardState =
        board.copy(containers = board.containers.map { library ->
            if (library.zone != PlayZone.LIBRARY) return@map library
            // A Temp pile turned on by this very action starts empty.
            val old = before.containers.singleOrNull { it.id == library.id } ?: return@map library
            library.copy(layers = layers(before, old, library, action, requestId, rulesVersion))
        })

    private fun layers(before: BoardState, old: PlayContainer, library: PlayContainer, action: PlayAction,
        requestId: String, rulesVersion: Int): List<LibraryLayer> {
        var created = 0
        fun newId() = CommandIds.uuidV5(requestId, "layer/${library.id}/${created++}")
        val members = library.cards.toSet()
        fun all(): List<LibraryLayer> = listOf(LibraryLayer(newId(), false, library.cards))
        var placed = emptySet<String>()
        // Applied to the remaining parts once departed and re-placed cards are removed.
        var place: (List<LibraryLayer>) -> List<LibraryLayer> = { it }
        fun known(cardId: String) {
            placed = setOf(cardId)
            place = { parts -> insertKnown(parts, cardId, library.cards.indexOf(cardId), ::newId) }
        }
        fun edge(ids: List<String>, top: Boolean, ordered: Boolean) {
            placed = ids.toSet()
            place = { parts ->
                val block = LibraryLayer(newId(), ordered, if (top) library.cards.take(ids.size) else library.cards.takeLast(ids.size))
                if (top) listOf(block) + parts else parts + block
            }
        }
        var shuffled = false
        // Rules v30: a Search close places its waiting cards around the shuffled rest.
        var closing: List<LibraryLayer>? = null
        when (action) {
            is PlayAction.Mulligan -> shuffled = action.libraryId == library.id
            is PlayAction.ShuffleLibrary -> shuffled = action.containerId == library.id
            // A placed card's position is known; a gap move that changed nothing physically changes nothing.
            is PlayAction.MoveCard -> if (action.destination.containerId == library.id && action.cardId in members &&
                ((action.destination.libraryInsertion ?: action.destination.insertion) in setOf(MoveInsertion.TOP, MoveInsertion.BOTTOM) ||
                    old.cards != library.cards)) known(action.cardId)
            is PlayAction.RandomHand -> if (action.result == HandRandomResult.LIBRARY_BOTTOM && action.cardId in members &&
                action.cardId !in old.cards) known(action.cardId)
            is PlayAction.MoveToLibraryRandom -> if (action.libraryId == library.id)
                edge(action.cardIds.filter { it in members }, top = !action.bottom, ordered = false)
            is PlayAction.DismissContents -> before.contentsDisplays.singleOrNull {
                it.id == action.displayId && it.containerId == library.id }?.let { display ->
                val ids = display.cardIds.filter { it in members }
                // Rules v31: closing a Temp pile Search leaves the pile (and what is known of it) as it is.
                if (!TempPiles.searchKeepsOrder(old, display, rulesVersion)) when (if (display.search) ContentsRest.SHUFFLE else action.rest) {
                    ContentsRest.SHUFFLE -> { shuffled = true; closing = SearchQueue.closeLayers(display, library.cards, ::newId) }
                    // Rules v31 Temp Look: each looked-at card is known where it went back; others stay as they were.
                    ContentsRest.TOP -> if (TempPiles.lookKeepsPlace(old, display, ContentsRest.TOP, rulesVersion)) {
                        placed = ids.toSet()
                        place = { parts -> ids.sortedBy { library.cards.indexOf(it) }.fold(parts) { acc, id ->
                            insertKnown(acc, id, library.cards.indexOf(id), ::newId) } }
                    } else edge(ids, top = true, ordered = true)
                    ContentsRest.BOTTOM -> edge(ids, top = false, ordered = true)
                    // Seeded by nobody's choice: their order stays unknown, as one layer of their own.
                    ContentsRest.BOTTOM_RANDOM -> edge(ids, top = false, ordered = false)
                    ContentsRest.GRAVEYARD, ContentsRest.EXILE -> Unit
                }
            }
            else -> Unit
        }
        // Inconsistent layers (never produced by the rules) stay untouched until something changes there.
        if (!shuffled && placed.isEmpty() && library.cards == old.cards &&
            LibraryLayers.segments(old).isEmpty() && library.layers == old.layers) return old.layers
        var parts = if (shuffled) closing ?: all() else {
            // Inconsistent layers know nothing: the old cards count as one hidden layer.
            val segments = LibraryLayers.segments(old).map { it.layer }.ifEmpty { listOf(LibraryLayer(newId(), false, old.cards)) }
            val remaining = segments.map { layer ->
                LibraryLayer(layer.id, layer.ordered, layer.cards.filter { it in members && it !in placed })
            }.filter { it.cards.isNotEmpty() }
            place(remaining)
        }
        // Anything else that reordered the library leaves its whole order unknown rather than guessing.
        if (parts.flatMap { it.cards } != library.cards) parts = all()
        // A continuously exposed top is a fixed identity across branch reseeds, even after a shuffle.
        val first = parts.firstOrNull()
        if (library.keepTopRevealed && first != null && !first.ordered && first.cards.size >= 2)
            parts = listOf(LibraryLayer(newId(), true, first.cards.take(1)), first.copy(cards = first.cards.drop(1))) + parts.drop(1)
        val result = LibraryLayers.normalize(parts)
        // Unchanged knowledge keeps the exact previous list (and IDs).
        return if (result == old.layers) old.layers else result
    }

    /** Inserts a card of known place at [index]; inside a layer, that layer splits there (upper part keeps its ID). */
    private fun insertKnown(parts: List<LibraryLayer>, cardId: String, index: Int, newId: () -> String): List<LibraryLayer> {
        val result = mutableListOf<LibraryLayer>()
        var at = 0
        var inserted = false
        val card = { LibraryLayer(newId(), true, listOf(cardId)) }
        for (part in parts) {
            if (!inserted && index == at) { result += card(); inserted = true }
            if (!inserted && index > at && index < at + part.cards.size) {
                val k = index - at
                result += part.copy(cards = part.cards.take(k))
                result += card()
                result += LibraryLayer(newId(), part.ordered, part.cards.drop(k))
                inserted = true
            } else result += part
            at += part.cards.size
        }
        if (!inserted) result += card()
        return result
    }
}

package org.asyncttwiz.protocol

import kotlinx.serialization.Serializable

@Serializable data class LibraryReseed(val containerId: String, val from: List<String>, val to: List<String>,
    val algorithm: String = "branch-reseed-v1")

/** Knowledge is positional, not face visibility. Looking alone does not fix an unknown order;
 * explicitly placing the looked-at block does. No operation here reads retained history. */
internal object LibraryKnowledge {
    fun seed(state: PlayState, actor: String, libraryId: String): String {
        // Pre-feature games have no entropy field. A domain-separated play-id hash is their stable
        // compatibility secret; never generate fresh entropy when loading an existing game.
        val secret = state.librarySecret ?: encoded(DeckJson.intent("legacy-library-secret-v1", state.id))
        return encoded(DeckJson.intent("branch-reseed-v1", listOf(secret, state.currentNodeId, actor, libraryId).joinToString("\u0000")))
    }

    private fun encoded(hex: String) = RandomV1.encodeSeed(hex.chunked(2).map { it.toInt(16).toByte() }.toByteArray())

    fun reseeds(state: PlayState, actor: String): List<LibraryReseed> = state.board.containers
        .filter { it.zone == PlayZone.LIBRARY }.map { library ->
            LibraryReseed(library.id, library.unknownMiddle.toList(),
                RandomV1.shuffle(library.unknownMiddle, seed(state, actor, library.id), "branch-reseed-v1"))
        }

    fun applyReseeds(state: PlayState, changes: List<LibraryReseed>, reverse: Boolean): PlayState? {
        if (changes.isEmpty()) return state
        if (changes.map { it.containerId }.distinct().size != changes.size) return null
        var board = state.board
        for (change in changes) {
            val library = board.containers.singleOrNull { it.id == change.containerId && it.zone == PlayZone.LIBRARY } ?: return null
            val from = if (reverse) change.to else change.from
            val to = if (reverse) change.from else change.to
            if (change.algorithm != "branch-reseed-v1" || library.unknownMiddle != from ||
                from.size != to.size || from.toSet() != to.toSet() || to.distinct().size != to.size) return null
            board = board.copy(containers = board.containers.map {
                if (it.id == library.id) it.copy(cards = it.knownTop + to + it.knownBottom) else it
            })
        }
        return state.copy(board = board)
    }

    /** Selective edits can change the incoming unknown order, but never reroll the retained result.
     * Membership (including the known/unknown boundary) must still match the accepted reseed. */
    fun rebaseReseeds(state: PlayState, changes: List<LibraryReseed>): List<LibraryReseed>? = changes.map { change ->
        val library = state.board.containers.singleOrNull { it.id == change.containerId && it.zone == PlayZone.LIBRARY }
            ?: return null
        val from = library.unknownMiddle
        if (from.size != change.from.size || from.toSet() != change.from.toSet()) return null
        change.copy(from = from)
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
                        destination.afterId !in target.knownBottom -> reduced.lastOrNull()
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

    fun after(before: BoardState, board: BoardState, action: PlayAction): BoardState = board.copy(containers = board.containers.map { library ->
        if (library.zone != PlayZone.LIBRARY) return@map library
        // A Temp pile turned on by this very action (rules v29) starts empty with nothing known.
        val old = before.containers.singleOrNull { it.id == library.id } ?: return@map library
        val members = library.cards.toSet()
        var top = old.knownTop.filter { it in members }
        var bottom = old.knownBottom.filter { it in members }
        fun place(ids: List<String>, insertion: MoveInsertion) {
            val placed = ids.toSet()
            top = top.filterNot { it in placed }
            bottom = bottom.filterNot { it in placed }
            if (insertion == MoveInsertion.TOP) {
                val fixed = (top + ids).toSet()
                top = library.cards.takeWhile { it in fixed }
            } else {
                val fixed = (bottom + ids).toSet()
                bottom = library.cards.takeLastWhile { it in fixed }
            }
        }
        when (action) {
            is PlayAction.Mulligan -> if (action.libraryId == library.id) { top = emptyList(); bottom = emptyList() }
            is PlayAction.ShuffleLibrary -> if (action.containerId == library.id) { top = emptyList(); bottom = emptyList() }
            is PlayAction.MoveCard -> if (action.destination.containerId == library.id && action.cardId in members) {
                val insertion = action.destination.libraryInsertion ?: action.destination.insertion
                if (insertion == MoveInsertion.TOP || insertion == MoveInsertion.BOTTOM) place(listOf(action.cardId), insertion)
            }
            is PlayAction.RandomHand -> if (action.result == HandRandomResult.LIBRARY_BOTTOM && action.cardId in members &&
                action.cardId !in old.cards) place(listOf(action.cardId), MoveInsertion.BOTTOM)
            is PlayAction.DismissContents -> before.contentsDisplays.singleOrNull {
                it.id == action.displayId && it.containerId == library.id }?.let { display ->
                val ids = display.cardIds.filter { it in members }
                when (if (display.search) ContentsRest.SHUFFLE else action.rest) {
                    ContentsRest.SHUFFLE -> { top = emptyList(); bottom = emptyList() }
                    ContentsRest.TOP -> place(ids, MoveInsertion.TOP)
                    ContentsRest.BOTTOM, ContentsRest.BOTTOM_RANDOM -> place(ids, MoveInsertion.BOTTOM)
                    ContentsRest.GRAVEYARD, ContentsRest.EXILE -> Unit
                }
            }
            else -> Unit
        }
        // An arbitrary GAP edit may split a formerly-known edge. Keep only contiguous edge blocks.
        val topSet = top.toSet(); val bottomSet = bottom.toSet()
        top = library.cards.takeWhile { it in topSet }
        bottom = library.cards.takeLastWhile { it in bottomSet && it !in topSet }
        // A continuously exposed top is a fixed identity across branch reseeds, even after shuffle.
        if (library.keepTopRevealed && top.isEmpty()) library.cards.firstOrNull()?.let { id ->
            top = listOf(id)
            bottom = bottom.filterNot { it == id }
        }
        library.copy(knownTop = top, knownBottom = bottom)
    })
}

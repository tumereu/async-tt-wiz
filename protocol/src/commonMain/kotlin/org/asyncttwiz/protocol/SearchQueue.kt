package org.asyncttwiz.protocol

/** Rules v30: while a Search panel is open on a Library, every card put on that Library's top or bottom (from
 * the panel or from anywhere else, by either player, one by one or in random order) goes there at once, with
 * the ordinary zone-entry resets, but WAITS: the close shuffles only the rest of the Library and leaves the
 * waiting cards at its top and bottom, exactly as if they had been put there one by one after the shuffle.
 * The display's queues name the waiting cards; their order is the Library's own (each put already placed its
 * card where it belongs relative to the others). A random-order put is one entry, kept as its own hidden layer.
 * Temp piles never queue; their Search close shuffles them until rules v31 and keeps their order from then on
 * ([TempPiles.searchKeepsOrder]). */
object SearchQueue {
    const val RULES_VERSION = 30

    /** The open Search on a (non-Temp) Library, if any. */
    fun search(board: BoardState): ContentsDisplay? = board.contentsDisplays.firstOrNull { display ->
        display.search && board.containers.any { it.id == display.containerId && it.zone == PlayZone.LIBRARY && !it.temp }
    }

    fun topCount(display: ContentsDisplay): Int = display.queuedTop.sumOf { it.size }
    fun bottomCount(display: ContentsDisplay): Int = display.queuedBottom.sumOf { it.size }

    /** The queues once [action] produced [board]: a top or bottom put onto the searched Library waits at that
     * edge (a put elsewhere in it, or a shuffle of it, joins the close shuffle); cards that left stop waiting. */
    internal fun update(board: BoardState, action: PlayAction): BoardState {
        val display = search(board) ?: return board
        val library = board.containers.single { it.id == display.containerId }
        val members = library.cards.toSet()
        var top = display.queuedTop
        var bottom = display.queuedBottom
        fun without(ids: Set<String>) {
            top = top.map { entry -> entry.filterNot(ids::contains) }
            bottom = bottom.map { entry -> entry.filterNot(ids::contains) }
        }
        when (action) {
            is PlayAction.MoveCard -> if (action.destination.containerId == library.id && action.cardId in members) {
                without(setOf(action.cardId))
                when (action.destination.libraryInsertion ?: action.destination.insertion) {
                    MoveInsertion.TOP -> top = top + listOf(listOf(action.cardId))
                    MoveInsertion.BOTTOM -> bottom = bottom + listOf(listOf(action.cardId))
                    else -> Unit
                }
            }
            is PlayAction.MoveToLibraryRandom -> if (action.libraryId == library.id) {
                // Membership only: the group's hidden order lives in the Library itself.
                val group = action.cardIds.filter { it in members }.sorted()
                without(group.toSet())
                if (group.isNotEmpty()) if (action.bottom) bottom = bottom + listOf(group) else top = top + listOf(group)
            }
            is PlayAction.ShuffleLibrary -> if (action.containerId == library.id) { top = emptyList(); bottom = emptyList() }
            else -> Unit
        }
        top = top.map { entry -> entry.filter(members::contains) }.filter { it.isNotEmpty() }
        bottom = bottom.map { entry -> entry.filter(members::contains) }.filter { it.isNotEmpty() }
        if (top == display.queuedTop && bottom == display.queuedBottom) return board
        val updated = display.copy(queuedTop = top, queuedBottom = bottom)
        return board.copy(contentsDisplays = board.contentsDisplays.map { if (it.id == display.id) updated else it })
    }

    private fun parts(display: ContentsDisplay, cards: List<String>): Triple<List<String>, List<String>, List<String>> {
        val top = display.queuedTop.flatten().toSet()
        val bottom = display.queuedBottom.flatten().toSet()
        return Triple(cards.filter { it in top }, cards.filter { it !in top && it !in bottom }, cards.filter { it in bottom })
    }

    /** The Library order after the close: the waiting top cards, the shuffled rest, the waiting bottom cards.
     * With nothing waiting this is exactly the plain close shuffle. */
    internal fun closeOrder(display: ContentsDisplay, cards: List<String>, seed: String): List<String> {
        if (display.queuedTop.isEmpty() && display.queuedBottom.isEmpty()) return RandomV1.shuffle(cards, seed)
        val (top, rest, bottom) = parts(display, cards)
        return top + RandomV1.shuffle(rest, seed) + bottom
    }

    /** The layers after the close (null when nothing waited): each waiting card known where it is, each
     * random-order put one hidden layer of its own, the shuffled rest one hidden layer. Not yet normalized. */
    internal fun closeLayers(display: ContentsDisplay, cards: List<String>, newId: () -> String): List<LibraryLayer>? {
        if (display.queuedTop.isEmpty() && display.queuedBottom.isEmpty()) return null
        val (top, rest, bottom) = parts(display, cards)
        return block(top, display.queuedTop, newId) + LibraryLayer(newId(), false, rest) + block(bottom, display.queuedBottom, newId)
    }

    private fun block(cards: List<String>, entries: List<List<String>>, newId: () -> String): List<LibraryLayer> {
        val entryOf = entries.withIndex().flatMap { (index, entry) -> entry.map { it to index } }.toMap()
        val result = mutableListOf<LibraryLayer>()
        for (id in cards) {
            val entry = entryOf.getValue(id)
            val last = result.lastOrNull()
            if (entries[entry].size > 1 && last != null && !last.ordered && entryOf[last.cards.first()] == entry)
                result[result.lastIndex] = last.copy(cards = last.cards + id)
            else result += LibraryLayer(newId(), entries[entry].size < 2, listOf(id))
        }
        return result
    }
}

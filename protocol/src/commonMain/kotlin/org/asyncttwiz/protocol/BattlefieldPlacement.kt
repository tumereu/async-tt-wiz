package org.asyncttwiz.protocol

/** Where a card goes when a menu, a Stack choice or an owner redirect puts it onto a Battlefield (never a drag
 * to an explicit cell): the first free cell of its normal row. That is the land row (0) only when the face it
 * shows is a Land, else the main row (1). A face-down card or one of unknown definition goes to the main row,
 * like a face-down token copy ([TokenStacks.row]). Request planning only; the processor accepts any free cell. */
object BattlefieldPlacement {
    fun row(definitions: List<SubmittedDefinition>, card: PlayCard, faceDown: Boolean = card.isFaceDown): Int =
        if (faceDown) 1 else TokenStacks.row(definitions.singleOrNull { it.digest == card.definition }
            ?.definition?.faces?.firstOrNull { it.faceId == card.faceId })

    /** The first cell of [row] in [containerId] that no card holds and that is not in [taken]. */
    fun firstFree(board: BoardState, containerId: String, row: Int, taken: Set<BoardPosition> = emptySet()): BoardPosition {
        val occupied = board.cards.filter { it.containerId == containerId }.mapNotNull { it.placement?.position }.toSet() + taken
        return BoardPosition(generateSequence(0) { it + 1 }.first { BoardPosition(it, row) !in occupied }, row)
    }

    /** [card]'s cell in Battlefield [containerId]; [taken] holds cells already planned for other cards of one batch. */
    fun cell(state: PlayState, containerId: String, card: PlayCard, taken: Set<BoardPosition> = emptySet(),
        faceDown: Boolean = card.isFaceDown): BoardPosition =
        firstFree(state.board, containerId, row(state.definitions, card, faceDown), taken)

    /** One cell per card of [cards], in order, each on its own row and never two in one cell. */
    fun cells(state: PlayState, containerId: String, cards: List<PlayCard>): Map<String, BoardPosition> {
        val taken = mutableSetOf<BoardPosition>()
        return cards.associate { card -> card.id to cell(state, containerId, card, taken).also { taken += it } }
    }
}

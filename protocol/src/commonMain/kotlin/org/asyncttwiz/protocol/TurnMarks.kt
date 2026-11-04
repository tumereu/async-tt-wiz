package org.asyncttwiz.protocol

/** Rules v29: which cards entered the Battlefield this turn. A card arriving on a Battlefield from any other
 * container (another zone, a Temp pile, or the other player's Battlefield) or created there stores the
 * current turn number, inside the same mutation; leaving the Battlefield clears it. Moves within one
 * Battlefield keep it. There is no cleanup event: the mark lapses when the turn number changes. */
object EnteredTurn {
    /** Whether [card] entered the Battlefield in the turn [board] is in (rules v29, [PlayCard.enteredTurn]). */
    fun marked(board: BoardState, card: PlayCard): Boolean =
        card.enteredTurn.isNotEmpty() && card.enteredTurn == board.turn.number
}

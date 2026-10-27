package org.asyncttwiz.protocol

/** Acceptance-only cleanup. Retained events always replay their exact sealed deltas. */
internal object TurnMarks {
    fun passed(from: BoardState, to: BoardState) =
        from.turn.number != to.turn.number || from.turn.accountId != to.turn.accountId

    fun clear(from: BoardState, to: BoardState): BoardState {
        val turn = passed(from, to)
        val old = from.cards.associateBy { it.id }
        return to.copy(cards = to.cards.map { card ->
            val previous = old[card.id]
            val changedZone = previous != null && previous.containerId != card.containerId &&
                (from.containers.single { it.id == previous.containerId }.zone != PlayZone.BATTLEFIELD ||
                    to.containers.single { it.id == card.containerId }.zone != PlayZone.BATTLEFIELD)
            if (turn || changedZone) card.copy(damage = "", eotCorner = "", eotNote = "") else card
        })
    }

    fun changes(from: BoardState, to: BoardState): List<GameplayFieldChange> {
        val before = from.cards.associateBy { it.id }
        return to.cards.flatMap { card ->
            val old = before.getValue(card.id)
            listOf(GameplayFieldChange(card.id, GameplayField.DAMAGE, old.damage, card.damage),
                GameplayFieldChange(card.id, GameplayField.EOT_CORNER, old.eotCorner, card.eotCorner),
                GameplayFieldChange(card.id, GameplayField.EOT_NOTE, old.eotNote, card.eotNote)).filter { it.from != it.to }
        }
    }
}

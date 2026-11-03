package org.asyncttwiz.protocol

/** Acceptance-only cleanup. Retained events always replay their exact sealed deltas. */
internal object TurnMarks {
    fun passed(from: BoardState, to: BoardState) =
        from.turn.number != to.turn.number || from.turn.accountId != to.turn.accountId

    fun clear(from: BoardState, to: BoardState): BoardState {
        fun cleared(card: PlayCard) = card.copy(damage = "", eotCorner = "", eotNote = "")
        if (passed(from, to)) return to.copy(cards = to.cards.map(::cleared))
        // Only a card that changed zones is cleared, and only a changed card can have (the rest are skipped unhashed).
        var cards: MutableList<PlayCard>? = null
        CardChanges.each(from.cards, to.cards) { index, previous, card ->
            val changedZone = previous != null && previous.containerId != card.containerId &&
                (from.containers.single { it.id == previous.containerId }.zone != PlayZone.BATTLEFIELD ||
                    to.containers.single { it.id == card.containerId }.zone != PlayZone.BATTLEFIELD)
            if (changedZone) (cards ?: to.cards.toMutableList().also { cards = it })[index] = cleared(card)
        }
        return cards?.let { to.copy(cards = it) } ?: to
    }

    fun changes(from: BoardState, to: BoardState): List<GameplayFieldChange> {
        val changes = mutableListOf<GameplayFieldChange>()
        CardChanges.each(from.cards, to.cards) { _, previous, card ->
            val old = previous ?: throw NoSuchElementException("Key ${card.id} is missing in the map.")
            if (old.damage != card.damage) changes += GameplayFieldChange(card.id, GameplayField.DAMAGE, old.damage, card.damage)
            if (old.eotCorner != card.eotCorner) changes += GameplayFieldChange(card.id, GameplayField.EOT_CORNER, old.eotCorner, card.eotCorner)
            if (old.eotNote != card.eotNote) changes += GameplayFieldChange(card.id, GameplayField.EOT_NOTE, old.eotNote, card.eotNote)
        }
        return changes
    }
}

/** Rules v29: which cards entered the Battlefield this turn. A card arriving on a Battlefield from any other
 * container (another zone, a Temp pile, or the other player's Battlefield) or created there stores the
 * current turn number, inside the same mutation; leaving the Battlefield clears it. Moves within one
 * Battlefield keep it. There is no cleanup event: the mark lapses when the turn number changes. */
object EnteredTurn {
    const val RULES_VERSION = 29

    fun marked(board: BoardState, card: PlayCard): Boolean =
        card.enteredTurn.isNotEmpty() && card.enteredTurn == board.turn.number

    /** Cards whose container changed (or which are new), with the value their arrival assigns. */
    internal fun arrivals(from: BoardState, to: BoardState): Map<String, String> {
        if (from.cards === to.cards) return emptyMap()
        val zones by lazy { from.containers.associate { it.id to it.zone } + to.containers.associate { it.id to it.zone } }
        val result = mutableMapOf<String, String>()
        // An unchanged card kept its container
        CardChanges.each(from.cards, to.cards) { _, previous, card ->
            if (previous != null && previous.containerId == card.containerId) return@each
            val entering = zones[card.containerId] == PlayZone.BATTLEFIELD
            if (entering || (previous != null && zones[previous.containerId] == PlayZone.BATTLEFIELD))
                result[card.id] = if (entering) to.turn.number else ""
        }
        return result
    }

    internal fun stamp(from: BoardState, to: BoardState): BoardState {
        val arrivals = arrivals(from, to)
        if (arrivals.isEmpty()) return to
        var changed = false
        val cards = to.cards.map { card ->
            val value = arrivals[card.id]
            if (value == null || value == card.enteredTurn) card else { changed = true; card.copy(enteredTurn = value) }
        }
        return if (changed) to.copy(cards = cards) else to
    }
}

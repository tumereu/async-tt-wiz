package org.asyncttwiz.android

import org.asyncttwiz.protocol.*

/**
 * What a drag means (integration spec I-D36, I-D28, I-D52): the engine action it stands for, or a refusal with the
 * reason. Life and zone moves by hand are refused; only moving a card within its own Battlefield, which changes nothing
 * Automatum reads, stays as it is. A hand is never reordered (I-D50, ZON-081).
 */
internal object AutomatumDrags {
    sealed interface Route {
        /** A hand card onto the Battlefield or the Stack: played (a land) or cast through the engine (ZON-004). */
        data object Cast : Route
        /** Within its own Battlefield: only its place changes. */
        data object Place : Route
        /** Within its own hand (I-D50, ZON-081): no reordering; the card goes back, nothing is said. */
        data object Stay : Route
        /** Into the combat area: the draft of the declaration the engine awaits ([CombatController.draftRefusal]). */
        data object Combat : Route
        /** A combat copy dragged out of the lanes onto the Battlefield: it leaves the draft. */
        data object LeaveDraft : Route
        data class Refused(val reason: String) : Route
    }

    fun route(play: TableBoard, intent: TableIntent.Move): Route {
        val board = play.board
        val ids = intent.scope.cardIds.ifEmpty { listOf(intent.scope.contextId) }
        if (ids.any { it in CombatStackRules.stack(board).cards }) return Route.Refused(STACK)
        val destination = intent.destination
        if (destination is TableDestination.Combat) return Route.Combat
        val target = when (destination) {
            is TableDestination.Container -> board.containers.singleOrNull { it.id == destination.id }
            is TableDestination.Grid -> board.containers.singleOrNull { it.id == destination.containerId }
            is TableDestination.CardPile -> board.cards.singleOrNull { it.id == destination.cardId }
                ?.let { card -> board.containers.singleOrNull { it.id == card.containerId } }
            is TableDestination.Combat -> null
        } ?: return Route.Refused(MANUAL_MOVE)
        if (ids.any { id -> CombatStackRules.occurrences(board.combat).any { it.id == id } })
            return if (target.zone == PlayZone.BATTLEFIELD) Route.LeaveDraft else Route.Refused(MANUAL_MOVE)
        val from = ids.map { id -> board.cards.singleOrNull { it.id == id }?.containerId
            ?.let { c -> board.containers.singleOrNull { it.id == c } } ?: return Route.Refused(MANUAL_MOVE) }
        if (from.all { it.zone == PlayZone.HAND } && target.zone in setOf(PlayZone.BATTLEFIELD, PlayZone.STACK)) return Route.Cast
        if (from.all { it.id == target.id } && target.zone == PlayZone.HAND) return Route.Stay
        if (from.all { it.id == target.id } && target.zone == PlayZone.BATTLEFIELD) return Route.Place
        return Route.Refused(MANUAL_MOVE)
    }

    /** Whether [viewer] is in their opening (TRN-027–TRN-029, CR 103.5, [AutomatumOnly.opening]): the host's setup
     * before the engine's game begins; [openingAction]s stay allowed, and the first turn begins once both players are Ready. */
    fun opening(board: BoardState, viewer: String?): Boolean = AutomatumOnly.opening(board, viewer)

    /** Whether [action] is one of [viewer]'s opening actions ([opening], I-D49): a mulligan of their own hand and library,
     * or putting their own hand cards on the bottom of their library (the London mulligan's bottom on keeping, CR 103.5);
     * the server's own check ([AutomatumOnly.allowed]). */
    fun openingAction(board: BoardState, viewer: String?, action: PlayAction): Boolean = viewer != null && opening(board, viewer) &&
        (action is PlayAction.Mulligan || action is PlayAction.MoveCard) && AutomatumOnly.allowed(board, viewer, action)

    const val MANUAL_MOVE = "Cards move only by the rules."
    const val STACK = "Automatum resolves the Stack: let it resolve."
    const val LIFE = "Life changes only by the rules."
    const val ONE_AT_A_TIME = "Cast or play one card at a time."
    /** Any other change made by hand (I-D28, I-D52: [AutomatumOnly]). */
    const val MANUAL = "The game changes only by the rules."
}

package org.asyncttwiz.android

import org.asyncttwiz.protocol.*

/**
 * What a drag means (integration spec I-D36, I-D28, I-D52): the engine action it stands for, or a refusal with the
 * reason. Life and zone moves by hand are refused. Nothing is placed by hand: a hand is never reordered (I-D50, ZON-081)
 * and the battlefield lays itself out (ZON-077, TBL-004).
 */
object AutomatumDrags {
    sealed interface Route {
        /** A hand card onto the Battlefield or the Stack: played (a land) or cast through the engine (ZON-004). */
        data object Cast : Route
        /** Within its own hand or Battlefield (I-D50, ZON-081, ZON-077): no reordering and no placing by hand (the
         * battlefield lays itself out); the card goes back, nothing is said. */
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
        // ZON-077: within its own hand or Battlefield the card goes back, nothing said (no reordering, no placing by hand)
        if (from.all { it.id == target.id } && (target.zone == PlayZone.HAND || target.zone == PlayZone.BATTLEFIELD)) return Route.Stay
        return Route.Refused(MANUAL_MOVE)
    }

    /** Whether [viewer] is in their opening (TRN-027–TRN-029, CR 103.5): the first turn, their status still the opening's
     * "Mulliganing"; [openingAction]s stay allowed, and the first turn begins once both players are Ready. */
    fun opening(board: BoardState, viewer: String?): Boolean = board.turn.number == "1" && PlayerStatus.opening(board, viewer)

    /** Whether [action] is one of [viewer]'s opening actions ([opening], I-D49): putting their own hand cards on the bottom
     * of their library (the London mulligan's bottom on keeping, CR 103.5) ([allowed]). */
    fun openingAction(board: BoardState, viewer: String?, action: PlayAction): Boolean = viewer != null && opening(board, viewer) &&
        action is PlayAction.MoveCard && allowed(board, viewer, action)

    /**
     * Whether [actor] may make [action] by hand (I-D52: the game changes only by the rules): what has no game effect
     * ("Inspect other side", passing the move), their own early answers (I-D33), the combat staging in the lanes (not
     * closing it), and in their opening their own hand cards to the bottom of
     * their own library.
     */
    fun allowed(board: BoardState, actor: String, action: PlayAction): Boolean = when (action) {
        is PlayAction.InspectFace, PlayAction.PassAttention -> true
        is PlayAction.Mtg -> when (val mtg = action.action) {
            is MtgAction.SetRecord -> listOfNotNull(mtg.before, mtg.after).let { records ->
                records.isNotEmpty() && records.all { it.kind == EngineRecordKind.PreAnswer && it.player == actor } }
        }
        is PlayAction.MoveCard -> openingBottom(board, actor, action)
        is PlayAction.Combat -> action.action !is CombatAction.Close
        is PlayAction.Stack -> false
    }

    private fun own(board: BoardState, actor: String, containerId: String, zone: PlayZone) =
        board.containers.any { it.id == containerId && it.zone == zone && it.accountId == actor }

    /** One of [actor]'s own hand cards to the bottom of their own library during their opening (CR 103.5). */
    private fun openingBottom(board: BoardState, actor: String, action: PlayAction.MoveCard): Boolean =
        opening(board, actor) && action.destination.insertion == MoveInsertion.BOTTOM &&
            own(board, actor, action.destination.containerId, PlayZone.LIBRARY) &&
            board.cards.singleOrNull { it.id == action.cardId }?.let { own(board, actor, it.containerId, PlayZone.HAND) } == true

    const val MANUAL_MOVE = "Cards move only by the rules."
    const val STACK = "Automatum resolves the Stack: let it resolve."
    const val ONE_AT_A_TIME = "Cast or play one card at a time."
    /** Any other change made by hand (I-D28, I-D52, [allowed]). */
    const val MANUAL = "The game changes only by the rules."
}

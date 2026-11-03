package org.asyncttwiz.android

import org.asyncttwiz.protocol.*

/**
 * What a drag means with Automatum on (integration spec I-D36, I-D28): the engine action it stands for, or a refusal
 * with the reason. Life and zone moves by hand are refused while Automatum is on; only moving a card within its own
 * Battlefield, which changes nothing Automatum reads, stays as it is. A hand is never reordered (I-D50, ZON-081).
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

    fun route(play: PlayState, intent: TableIntent.Move): Route {
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

    /**
     * Whether [viewer] is in their opening with Automatum on (TRN-027–TRN-029, CR 103.5): the first turn and their status
     * still the opening "Mulliganing". The opening is the host's setup before the engine's game begins: [openingAction]s
     * stay allowed, and the first turn begins once both players are Ready.
     */
    fun opening(board: BoardState, viewer: String?): Boolean = board.automatum && board.turn.number == "1" &&
        PlayerStatus.opening(board, viewer)

    /** Whether [action] is one of [viewer]'s opening actions ([opening], I-D49): a mulligan of their own hand and library,
     * or putting their own hand cards on the bottom of their library (the London mulligan's bottom on keeping, CR 103.5). */
    fun openingAction(board: BoardState, viewer: String?, action: PlayAction): Boolean {
        if (!opening(board, viewer)) return false
        fun own(containerId: String, zone: PlayZone) = board.containers.any { it.id == containerId && it.zone == zone && it.accountId == viewer }
        return when (action) {
            is PlayAction.Mulligan -> own(action.libraryId, PlayZone.LIBRARY) && own(action.handId, PlayZone.HAND)
            is PlayAction.MoveCard -> action.destination.insertion == MoveInsertion.BOTTOM &&
                own(action.destination.containerId, PlayZone.LIBRARY) &&
                board.cards.singleOrNull { it.id == action.cardId }?.let { own(it.containerId, PlayZone.HAND) } == true
            else -> false
        }
    }

    const val MANUAL_MOVE = "With Automatum on, cards move only by the rules. Switch Automatum off to move them by hand."
    const val STACK = "Automatum resolves the Stack: let it resolve, or switch Automatum off to move it by hand."
    const val LIFE = "With Automatum on, life changes only by the rules. Switch Automatum off to change it by hand."
    const val ONE_AT_A_TIME = "Cast or play one card at a time."
    /** Any other change made by hand while Automatum is on (I-D28, §3 I4). */
    const val MANUAL = "With Automatum on, the game changes only by the rules. Switch Automatum off to change it by hand."

    /**
     * Whether [action] changes the game by hand, which Automatum on refuses (I-D28) unless its open choice is something
     * to do by hand (I-D37). What has no game effect stays: notes, pings, statuses, marks, links, the six colour
     * counters Automatum ignores, dice and coins, inspecting, result suggestions, passing the move, keeping a library's
     * top card revealed, closing a panel, and Automatum's own records.
     */
    fun manual(action: PlayAction): Boolean = when (action) {
        is PlayAction.Ping, is PlayAction.Unping, is PlayAction.SetStatus, is PlayAction.SetNote, is PlayAction.SetEotNote,
        is PlayAction.SetProjectionNote, is PlayAction.Link, is PlayAction.RemoveLink, is PlayAction.LinkProjections,
        is PlayAction.RemoveProjectionLink, is PlayAction.RollDie, is PlayAction.FlipCoin, is PlayAction.InspectFace,
        is PlayAction.SuggestResult, is PlayAction.DeclineResult, is PlayAction.WithdrawResult, is PlayAction.EventComment,
        PlayAction.PassAttention, is PlayAction.SetKeepTopRevealed, is PlayAction.DismissContents,
        is PlayAction.SetCounter, is PlayAction.AdjustCounter, is PlayAction.ClearCardCounter,
        is PlayAction.SetPlayerCounter, is PlayAction.AdjustPlayerCounter, is PlayAction.Mtg -> false
        is PlayAction.Stack -> action.action !is StackAction.MarkText
        else -> true
    }
}

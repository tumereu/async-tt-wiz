package org.asyncttwiz.android

import org.asyncttwiz.protocol.*

/** Captured menu identity, not a freshly resolved selection on click. */
data class CombatStackTarget(val viewId: String, val sourceId: String?)

/** Stateless invocation lowering; prediction is an output, never a local authoritative table.
 * The caller supplies the normal session envelope/IDs and enqueues these exact commands.
 */
object CombatStackController {
    fun bind(board: BoardState, viewId: String): CombatStackTarget {
        require(board.cards.any { it.id == viewId } || board.stackEffects.any { it.id == viewId } ||
            CombatStackRules.occurrences(board.combat).any { it.id == viewId }) { "Unknown target" }
        return CombatStackTarget(viewId, CombatStackRules.liveSource(board, viewId)?.id)
    }
    fun sources(targets: List<CombatStackTarget>) = targets.mapNotNull { it.sourceId }.distinct()

    fun attackerPairs(board: BoardState, viewIds: List<String>): List<CombatPair> =
        board.combat?.pairs.orEmpty().filter { pair -> pair.attackers.any { it.id in viewIds } }


    /** Move the live source, not the projection, using its owner's bin and normal move defaults. */
    fun graveyard(board: BoardState, occurrenceId: String): PlayAction.MoveCard {
        require(CombatStackRules.occurrences(board.combat).any { it.id == occurrenceId })
        val card = requireNotNull(CombatStackRules.liveSource(board, occurrenceId))
        val graveyard = board.containers.single { it.accountId == card.owner && it.zone == PlayZone.GRAVEYARD }
        return PlayAction.MoveCard(card.id, MoveSource(card.containerId, card.placement),
            MoveDestination(graveyard.id, MoveInsertion.UNORDERED))
    }
}

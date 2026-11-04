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

    /** Cross-lane attacker-row drops retarget whole pairs; blocker-row drops still change roles. A drop beside a
     * card of another pair (CMB-027) joins that pair instead, so it attacks that pair's target. */
    fun retargetDrop(board: BoardState, viewIds: List<String>, destination: CombatDestination): List<CombatPair> {
        if (destination.role != CombatRole.ATTACKER) return emptyList()
        if (destination.pairId != null && destination.insertion == CombatInsertion.GAP) return emptyList()
        val pairs = attackerPairs(board, viewIds)
        if (pairs.flatMap { it.attackers }.none { it.id in viewIds } || viewIds.any { id ->
                pairs.none { pair -> pair.attackers.any { it.id == id } } }) return emptyList()
        val target = destination.pairId?.let { id -> board.combat?.pairs?.find { it.id == id }?.attackTarget }
            ?: destination.attackTarget
        return pairs.filter { it.attackTarget != target }
    }


    /** Move the live source, not the projection, using its owner's bin and normal move defaults. */
    fun graveyard(board: BoardState, occurrenceId: String): PlayAction.MoveCard {
        require(CombatStackRules.occurrences(board.combat).any { it.id == occurrenceId })
        val card = requireNotNull(CombatStackRules.liveSource(board, occurrenceId))
        val graveyard = board.containers.single { it.accountId == card.owner && it.zone == PlayZone.GRAVEYARD }
        return PlayAction.MoveCard(card.id, MoveSource(card.containerId, card.placement),
            MoveDestination(graveyard.id, MoveInsertion.UNORDERED))
    }




    /** Pre-action visual ordering. Mixed views preserve the supplied scene order, never projections-first. */
    fun orderedTargets(board: BoardState, selected: List<String>): List<CombatStackTarget> {
        val bound = selected.map { bind(board, it) }
        val combatIds = board.combat?.let { area -> area.pairs.flatMap { it.blockers } + area.pairs.flatMap { it.attackers } }
            .orEmpty().map { it.id }
        val ordered = when {
            selected.all { it in combatIds } -> combatIds.filter { it in selected }
            selected.all { id -> board.cards.any { it.id == id } } -> board.cards.filter { it.id in selected }
                .sortedWith(compareBy<PlayCard> { it.placement?.position?.y ?: 0 }
                    .thenBy { it.placement?.position?.x ?: 0 }
                    .thenBy { card -> board.containers.find { it.id == card.containerId }?.cards?.indexOf(card.id) ?: 0 })
                .map { it.id }
            else -> selected
        }
        return ordered.map { id -> bound.first { it.viewId == id } }.distinctBy { it.sourceId ?: it.viewId }
    }



}

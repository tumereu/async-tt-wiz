package org.asyncttwiz.android

import org.asyncttwiz.protocol.*

/**
 * Combat lanes staged on a test board as the table stages them on the board shown (R4b: [EngineStaging], the combat lane
 * reducer [CombatStackRules.stage], no play rebuilt): opening the combat area, declaring, placing a selection at a
 * destination as the toolkit's drop does, retargeting attacks and removing occurrences. Each staged action gets its own
 * request id (from [ids]), which names the pairs and occurrences it makes.
 */
internal class CombatBoards(private val actor: String, private val ids: () -> String = counter()) {
    /** The actions that changed a board, in order (what the toolkit sends as one batch). */
    val applied = mutableListOf<PlayAction>()

    fun apply(state: PlayState, action: PlayAction): PlayState {
        val next = EngineStaging.apply(state.board, state.definitions, actor, action, ids())
        if (next != state.board) applied += action
        return state.copy(board = next)
    }

    /** Taps [targets]' sources first when [tap], opens the combat area and declares each source on the Battlefield. */
    fun declare(state: PlayState, targets: List<CombatStackTarget>, tap: Boolean): PlayState {
        val eligible = CombatStackController.sources(targets).filter { id -> state.board.cards.find { it.id == id }?.let { card ->
            state.board.containers.find { it.id == card.containerId }?.zone == PlayZone.BATTLEFIELD } == true }
        if (eligible.isEmpty()) return state
        var next = if (!tap) state else state.copy(board = state.board.copy(cards = state.board.cards.map {
            if (it.id in eligible) it.copy(tapped = true) else it }))
        next = apply(next, PlayAction.Combat(CombatAction.Open))
        val areaId = next.board.combat!!.id
        for (source in eligible) next = apply(next, PlayAction.Combat(CombatAction.Declare(areaId, source)))
        return next
    }

    /** Every pair of the attackers among [viewIds] attacks [cardId] (null: the defending player). */
    fun attackTarget(state: PlayState, viewIds: List<String>, cardId: String?): PlayState =
        CombatStackController.attackerPairs(state.board, viewIds).fold(state) { next, pair ->
            apply(next, PlayAction.Combat(CombatAction.SetAttackTarget(state.board.combat!!.id, pair.id, cardId)))
        }

    /** [selected] placed in the given order at [destination] (a staging the lanes hold, built for a test). */
    fun place(state: PlayState, selected: List<String>, destination: CombatDestination): PlayState {
        val targets = selected.map { CombatStackController.bind(state.board, it) }.distinctBy { it.sourceId ?: it.viewId }
        val sourceIds = CombatStackController.sources(targets)
        require(sourceIds.size == targets.size && sourceIds.isNotEmpty()) { "No live source" }
        var next = state
        var target = destination
        var prefix: String? = null
        var rightAnchor = destination.cardGap.beforeId
        for ((index, source) in sourceIds.withIndex()) {
            val area = requireNotNull(next.board.combat)
            val occurrence = CombatStackRules.occurrences(area).find { it.sourceId == source }
            val pending = sourceIds.drop(index + 1)
            target = if (prefix != null) target.copy(insertion = CombatInsertion.GAP, cardGap = IdentityGap(prefix, rightAnchor),
                pendingSourceIds = pending) else target.copy(pendingSourceIds = pending)
            next = apply(next, PlayAction.Combat(CombatAction.Place(source, target, occurrence?.let { CombatStackRules.location(area, it.id) })))
            val placedArea = next.board.combat!!
            val placed = CombatStackRules.occurrences(placedArea).single { it.sourceId == source }
            val location = CombatStackRules.location(placedArea, placed.id)!!
            if (index == 0) {
                val side = placedArea.pairs.single { it.id == location.pairId }.side(location.role)
                val right = side.dropWhile { it.id != placed.id }.drop(1).firstOrNull { it.sourceId !in pending }?.id
                rightAnchor = right
                target = target.copy(pairId = location.pairId, insertion = CombatInsertion.GAP, cardGap = IdentityGap(placed.id, right))
            }
            prefix = placed.id
        }
        return next
    }

    /** The combat occurrences among [targets] removed from the combat area. */
    fun remove(state: PlayState, targets: List<CombatStackTarget>): PlayState = targets.distinctBy { it.viewId }.fold(state) { next, target ->
        apply(next, PlayAction.Combat(CombatAction.Remove(next.board.combat!!.id, target.viewId)))
    }

    companion object {
        private fun counter(): () -> String {
            var n = 0
            return { java.util.UUID.nameUUIDFromBytes("combat-board-${n++}".toByteArray()).toString() }
        }
    }
}

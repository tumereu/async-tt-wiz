package org.asyncttwiz.android

import org.asyncttwiz.protocol.BoardState
import org.asyncttwiz.protocol.CombatStackRules
import org.asyncttwiz.protocol.CommandIds
import org.asyncttwiz.protocol.EngineRecordKind
import org.asyncttwiz.protocol.MoveInsertion
import org.asyncttwiz.protocol.MoveSourceEdge
import org.asyncttwiz.protocol.MtgAction
import org.asyncttwiz.protocol.MtgRules
import org.asyncttwiz.protocol.PlayAction
import org.asyncttwiz.protocol.PlayPlacement
import org.asyncttwiz.protocol.PlayZone
import org.asyncttwiz.protocol.SubmittedDefinition

/**
 * Engine-responsiveness R4a: what the table's own controls change on the board shown of a game on engine state, worked
 * out on that board alone (no play is rebuilt for it): a card's place within its Battlefield (TBL-012), the combat lanes
 * a declaration is staged in (CMB-009–CMB-034, [CombatStackRules.stage]) and the early answers beside the board (the
 * attacker's choices and early divisions, I-D33). The same checks and ids as the gameplay change of today's model; the
 * device shares the result as table changes ([EngineTableRoutes]). Anything else is left to the routes, which refuse it.
 */
internal object EngineStaging {
    /**
     * [board] (with its card data [definitions]) after [action] made by [actor] as the request [requestId] (a batch's
     * member: its own request id, which names what it makes); [board] itself when nothing changes or the action is none
     * of these.
     * @throws IllegalArgumentException with the reason when it is refused.
     */
    fun apply(board: BoardState, definitions: List<SubmittedDefinition>, actor: String, action: PlayAction, requestId: String): BoardState =
        when (action) {
            is PlayAction.Combat -> CombatStackRules.stage(board, definitions, requestId, action.action)
            is PlayAction.Mtg -> (action.action as? MtgAction.SetRecord)?.let { record(board, actor, action, it) } ?: board
            is PlayAction.MoveCard -> place(board, action, requestId) ?: board
            else -> board
        }

    /** Whether [apply] works [action] out on [board] (else it is left to the routes, which refuse it). */
    fun handles(board: BoardState, action: PlayAction): Boolean = when (action) {
        is PlayAction.Combat -> true
        is PlayAction.Mtg -> action.action is MtgAction.SetRecord
        is PlayAction.MoveCard -> action.removeStackEntryId == null && action.source.containerId == action.destination.containerId &&
            board.containers.any { it.id == action.destination.containerId && it.zone == PlayZone.BATTLEFIELD }
        else -> false
    }

    /** I-D33: an early answer set or withdrawn; only its own player gives or changes it. */
    private fun record(board: BoardState, actor: String, action: PlayAction.Mtg, set: MtgAction.SetRecord): BoardState {
        require(MtgRules.valid(action.action)) { "invalidInput" }
        val current = board.engineRecords.singleOrNull { it.id == set.id }
        require(current == set.before) { "inapplicable" }
        require(set.after?.kind != EngineRecordKind.PreAnswer || set.after?.player == actor) { "accessDenied" }
        if (set.before == set.after) return board
        return board.copy(engineRecords = (board.engineRecords.filterNot { it.id == set.id } + listOfNotNull(set.after)).sortedBy { it.id })
    }

    /** A card moved within its own Battlefield (its place, TBL-012), or null when [action] is another move. */
    private fun place(board: BoardState, action: PlayAction.MoveCard, requestId: String): BoardState? {
        if (action.removeStackEntryId != null || action.source.containerId != action.destination.containerId) return null
        val container = board.containers.singleOrNull { it.id == action.destination.containerId } ?: return null
        if (container.zone != PlayZone.BATTLEFIELD) return null
        val card = requireNotNull(board.cards.singleOrNull { it.id == action.cardId }) { "missingReference" }
        require(card.containerId == container.id && card.id in container.cards && card.placement == action.source.placement) { "movementConflict" }
        val cards = board.cards.associateBy { it.id }
        fun members(placement: PlayPlacement?) = container.cards.filter { id -> cards.getValue(id).placement == placement }
        val sourceMembers = members(card.placement)
        require(action.source.edge == MoveSourceEdge.MEMBER) { "invalidInput" }
        require(action.source.bottomSlice.isEmpty()) { "movementConflict" }
        require(action.expectedStackDigest == null) { "invalidInput" }
        val destination = action.destination
        require(destination.insertion in setOf(MoveInsertion.EMPTY, MoveInsertion.PILE_TOP, MoveInsertion.PILE_BOTTOM, MoveInsertion.PILE_GAP)) { "invalidInput" }
        val position = requireNotNull(destination.position) { "invalidInput" }
        require(destination.insertion == MoveInsertion.PILE_GAP || destination.afterId == null && destination.beforeId == null) { "invalidInput" }
        val reduced = container.cards.filter { it != card.id }
        val placement: PlayPlacement
        val ordered: List<String>
        if (destination.insertion == MoveInsertion.EMPTY) {
            require(destination.pileId == null) { "invalidInput" }
            require(reduced.none { id -> cards.getValue(id).placement?.position == position }) { "movementConflict" }
            // An unchanged solo placement is a no-op, retaining its stable pile identity
            val solo = card.placement?.position == position && sourceMembers.size == 1
            if (solo && action.faceDown == null) return board
            val pile = if (solo) card.placement!!.pileId else CommandIds.uuidV5(requestId, "pile/0")
            require(board.cards.none { it.id != card.id && it.placement?.pileId == pile }) { "inapplicable" }
            placement = PlayPlacement(position, pile)
            ordered = emptyList()
        } else {
            placement = PlayPlacement(position, requireNotNull(destination.pileId) { "invalidInput" })
            ordered = members(placement).filter { it != card.id }
            require(ordered.isNotEmpty() && board.cards.any { it.id in ordered && it.placement == placement }) { "movementConflict" }
            require(ordered.size < 3) { "movementConflict" }
            if (destination.insertion == MoveInsertion.PILE_GAP)
                require(requireNotNull(destination.targetCardId) { "invalidInput" } in ordered) { "movementConflict" }
        }
        val index = when (destination.insertion) {
            MoveInsertion.PILE_BOTTOM -> ordered.size
            MoveInsertion.PILE_GAP -> {
                val neighbors = ordered.filter { it !in destination.pendingIds }
                val left = destination.afterId?.let { neighbors.indexOf(it).takeIf { i -> i >= 0 } ?: throw IllegalArgumentException("movementConflict") } ?: -1
                require(neighbors.getOrNull(left + 1) == destination.beforeId) { "movementConflict" }
                destination.afterId?.let { ordered.indexOf(it) + 1 } ?: 0
            }
            else -> 0
        }
        val inserted = ordered.toMutableList().also { it.add(index, card.id) }
        val faceDown = action.faceDown ?: card.isFaceDown
        val moved = card.copy(placement = placement, covered = faceDown, explicitlyCovered = faceDown)
        return board.copy(cards = board.cards.map { if (it.id == card.id) moved else it },
            containers = board.containers.map { if (it.id == container.id) it.copy(cards = reduced.filter { id -> id !in ordered } + inserted) else it })
    }
}

/**
 * Engine-responsiveness R4a: the members of one action of the player's (a drag of several cards, a combat drop) lowered one
 * by one against the board shown of a game on engine state ([EngineStaging]), as today's model drafts a batch: each
 * member sees the board the ones before it made; one that changes nothing isn't kept. [requestId] is the batch's: a
 * member's own request id is derived from it as the batch's members are ([org.asyncttwiz.protocol.PlayBatches]).
 */
internal class EngineDraft(val initial: TableBoard, private val actor: String, val requestId: String = DeviceRandom.uuid()) {
    var state = initial
        private set
    val actions = mutableListOf<PlayAction>()
    var overflow = false
        private set

    fun add(action: PlayAction): Boolean {
        if (actions.size >= 100) { overflow = true; return false }
        val member = CommandIds.uuidV5(requestId, "member/${actions.size}")
        val next = try { EngineStaging.apply(state.board, state.definitions, actor, action, member) }
            catch (_: IllegalArgumentException) { return false }
        if (next != state.board || !EngineStaging.handles(state.board, action)) {
            actions += action
            state = state.copy(board = next)
        }
        return true
    }
}

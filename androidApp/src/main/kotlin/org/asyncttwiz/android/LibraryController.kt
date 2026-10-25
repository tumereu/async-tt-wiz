package org.asyncttwiz.android

import java.security.SecureRandom
import java.util.UUID
import org.asyncttwiz.protocol.*
import org.asyncttwiz.protocol.LibraryActions as SharedLibraryActions

data class LibrarySubmission(val submitted: Int, val refused: Int)

/** Input adapter only. The existing session supplies prediction/accepted state and owns all requests. */
class LibraryController(
    val playId: String,
    val libraryId: String,
    val handId: String,
    private val state: () -> PlayState?,
    private val connected: () -> Boolean,
    private val submit: (PlayAction) -> Boolean,
    private val seed: () -> String = {
        RandomV1.encodeSeed(ByteArray(32).also { SecureRandom().nextBytes(it) })
    },
    private val request: ((PlayAction, String) -> Boolean)? = null,
    private val moveRequest: ((TableIntent.Move) -> LibrarySubmission)? = null
) {
    private fun current(): PlayState? = state()?.takeIf { it.id == playId }
    fun available() = connected() && current() != null
    private fun send(actions: List<PlayAction>, invocation: String = UUID.randomUUID().toString()): LibrarySubmission {
        var sent = 0; var refused = 0
        var prefixAccepted = true
        for (action in actions) {
            val routed = if (action is PlayAction.MoveCard) current()?.let {
                action.copy(source = action.source.copy(takeFromEdge = prefixAccepted &&
                    (action.source.edge != MoveSourceEdge.MEMBER || action.source.bottomSlice.isNotEmpty())),
                    destination = ownerMoveDestination(it.board, action.cardId, action.destination))
            } ?: action else action
            if (available() && (request?.invoke(routed, invocation) ?: submit(routed))) sent++ else {
                refused++
                // A refused prefix is never permission to refill its missing draw/mill with another card.
                prefixAccepted = false
            }
        }
        return LibrarySubmission(sent, refused)
    }
    fun draw(count: Int = 1, selection: ContentsSelection = ContentsSelection.TOP): LibrarySubmission {
        val current = current()?.takeIf { available() && count >= 0 } ?: return LibrarySubmission(0, 1)
        if (selection == ContentsSelection.BOTTOM) return move(selection, count, MoveDestination(handId, MoveInsertion.UNORDERED))
        return send(SharedLibraryActions.draw(current.board, libraryId, handId, count))
    }
    fun show(selection: ContentsSelection = ContentsSelection.ALL, count: Int = 1, reveal: Boolean = false) =
        if (count < 0 || current()?.board?.contentsDisplays?.isNotEmpty() != false) LibrarySubmission(0, 1)
        else send(listOf(PlayAction.ShowContents(libraryId, selection, count, reveal)))
    fun dismiss(displayId: String, rest: ContentsRest = ContentsRest.TOP): LibrarySubmission {
        if (!available()) return LibrarySubmission(0, 1)
        return send(listOf(PlayAction.DismissContents(displayId, rest, seed())))
    }
    fun shuffle(): LibrarySubmission {
        if (!available()) return LibrarySubmission(0, 1)
        return send(listOf(PlayAction.ShuffleLibrary(libraryId, seed())))
    }
    fun move(selection: ContentsSelection, count: Int, destination: MoveDestination): LibrarySubmission {
        val current = current()?.takeIf { available() && count >= 0 } ?: return LibrarySubmission(0, 1)
        val stack = current.board.containers.any { it.id == destination.containerId && it.zone == PlayZone.STACK }
        if (stack && current.board.containers.any { it.id == libraryId && it.zone == PlayZone.LIBRARY }) return LibrarySubmission(0, 1)
        if (stack || current.board.containers.any { it.id == destination.containerId && it.zone == PlayZone.BATTLEFIELD }) {
            val source = current.board.containers.single { it.id == libraryId }
            val ids = LibraryRules.slice(source.cards, selection, count)
            val sources = BasicTableController.sources(current, ids, top = selection == ContentsSelection.TOP).mapValues { (id, value) ->
                if (selection == ContentsSelection.BOTTOM) value.copy(bottomSlice = ids.drop(ids.indexOf(id))) else value
            }
            return moveAcross(current, ids, sources, destination)
        }
        val ids = LibraryRules.slice(current.board.containers.single { it.id == libraryId }.cards, selection, count)
        val moves = ownerMoves(current.board, ids, destination).map { action -> action.copy(source = action.source.copy(
            edge = if (selection == ContentsSelection.TOP && action.destination.containerId != libraryId) MoveSourceEdge.TOP else MoveSourceEdge.MEMBER,
            bottomSlice = if (selection == ContentsSelection.BOTTOM && action.destination.containerId != libraryId) ids.drop(ids.indexOf(action.cardId)) else emptyList())) }
        // Keep source slice order across owners (TOP/BOTTOM guards refer to that original slice).
        return send(moves.sortedBy { ids.indexOf(it.cardId) })
    }
    fun moveCards(capturedIds: List<String>, destination: MoveDestination): LibrarySubmission {
        val current = current()?.takeIf { available() } ?: return LibrarySubmission(0, 1)
        if (capturedIds.any { id -> current.board.cards.none { it.id == id } }) return LibrarySubmission(0, 1)
        if (current.board.containers.any { it.id == destination.containerId && it.zone in setOf(PlayZone.BATTLEFIELD, PlayZone.STACK) })
            return moveAcross(current, capturedIds, BasicTableController.sources(current, capturedIds), destination)
        return send(ownerMoves(current.board, capturedIds, destination))
    }
    private fun ownerMoves(board: BoardState, ids: List<String>, destination: MoveDestination): List<PlayAction.MoveCard> =
        ids.groupBy { ownerMoveDestination(board, it, destination) }.flatMap { (target, members) ->
            SharedLibraryActions.moveCaptured(board, members, target)
        }
    private fun moveAcross(current: PlayState, ids: List<String>, sources: Map<String, TableMoveSource>, destination: MoveDestination): LibrarySubmission {
        if (ids.isEmpty()) return LibrarySubmission(0, 0)
        val target = current.board.containers.single { it.id == destination.containerId }
        val bound = if (target.zone == PlayZone.STACK) {
            if (ids.any { id -> current.board.cards.singleOrNull { it.id == id }?.let {
                    CombatStackRules.canProject(current.board, it) } != true }) return LibrarySubmission(0, ids.size)
            TableDestination.Container(target.id, target.zone)
        } else destination.position?.let { TableDestination.Grid(target.id, it.x, it.y, ownerOnly = true) } ?: return LibrarySubmission(0, ids.size)
        return moveRequest?.invoke(TableIntent.Move(TableScope(ids.first(), ids), bound, sources, UUID.randomUUID().toString()))
            ?: LibrarySubmission(0, ids.size)
    }
    fun mulligan(displayedHand: List<String>, count: Int = 7, minusOne: Boolean = false): LibrarySubmission {
        val initial = current()?.takeIf { available() && count >= 0 } ?: return LibrarySubmission(0, 1)
        val hand = initial.board.containers.singleOrNull { it.id == handId && it.zone == PlayZone.HAND }
            ?: return LibrarySubmission(0, 1)
        if (displayedHand.distinct().size != displayedHand.size || displayedHand.toSet() != hand.cards.toSet())
            return LibrarySubmission(0, 1) // Stale presentation is not permission to return another Hand.
        val captured = SharedLibraryActions.mulligan(initial.board, libraryId, handId, displayedHand, seed(), count, minusOne)
        val invocation = UUID.randomUUID().toString()
        val prefix = send(ownerMoves(initial.board, displayedHand, MoveDestination(libraryId, MoveInsertion.TOP)) +
            PlayAction.ShuffleLibrary(libraryId, captured.seed), invocation)
        // submit synchronously updates the existing session's provisional board, not an accepted result/ack.
        // No await, extra engine, retry loop, local authoritative state or substitution of in-flight draws.
        val predicted = current()?.takeIf { available() } ?: return prefix
        val draws = send(SharedLibraryActions.draw(predicted.board, libraryId, handId, captured.drawCount), invocation)
        return LibrarySubmission(prefix.submitted + draws.submitted, prefix.refused + draws.refused)
    }

}

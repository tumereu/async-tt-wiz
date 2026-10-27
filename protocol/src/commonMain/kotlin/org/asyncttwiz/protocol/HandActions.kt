package org.asyncttwiz.protocol

import kotlinx.serialization.Serializable

@Serializable enum class HandRandomResult { DISCARD, REVEAL, EXILE, LIBRARY_BOTTOM }

object HandActions {
    fun random(board: BoardState, handId: String, selectedIds: List<String>, seed: String,
        result: HandRandomResult): PlayAction.RandomHand {
        val hand = board.containers.single { it.id == handId && it.zone == PlayZone.HAND }
        require(selectedIds.distinct().size == selectedIds.size && selectedIds.all { it in hand.cards })
        val picked = RandomV1.handPick(selectedIds.ifEmpty { hand.cards }, seed)
        return PlayAction.RandomHand(handId, selectedIds, seed, result, picked)
    }
}

internal object HandRules {
    fun change(state: PlayState, actor: String, command: GameCommand, action: PlayAction,
        rulesVersion: Int = 20): GameplayChange {
        val board = state.board
        val handId = when (action) {
            is PlayAction.RevealHand -> action.handId
            is PlayAction.RandomHand -> action.handId
            else -> error("Not a Hand action")
        }
        val hand = board.containers.singleOrNull { it.id == handId && it.zone == PlayZone.HAND }
            ?: return GameplayChange(reason = "missingReference")
        if (hand.accountId != actor) return GameplayChange(reason = "accessDenied")
        if (action is PlayAction.RandomHand) {
            if (action.selectedIds.any { it !in hand.cards } || hand.cards.isEmpty())
                return GameplayChange(reason = "movementConflict")
            if (RandomV1.handPick(action.selectedIds.ifEmpty { hand.cards }, action.seed) != action.cardId)
                return GameplayChange(reason = "invalidInput")
            val lowered = if (action.result == HandRandomResult.REVEAL) PlayAction.RevealHand(hand.id, listOf(action.cardId)) else {
                val zone = when (action.result) {
                    HandRandomResult.DISCARD -> PlayZone.GRAVEYARD
                    HandRandomResult.EXILE -> PlayZone.EXILE
                    HandRandomResult.LIBRARY_BOTTOM -> PlayZone.LIBRARY
                    HandRandomResult.REVEAL -> error("unreachable")
                }
                val owner = if (rulesVersion >= 20) board.cards.single { it.id == action.cardId }.owner else actor
                val target = board.containers.singleOrNull { it.accountId == owner && it.zone == zone }
                    ?: return GameplayChange(reason = "missingReference")
                PlayAction.MoveCard(action.cardId, MoveSource(hand.id), MoveDestination(target.id,
                    if (zone == PlayZone.LIBRARY) MoveInsertion.BOTTOM else MoveInsertion.UNORDERED))
            }
            val changed = GameplayRules.change(state, actor, command.copy(action = GameAction.Play(lowered)))
            val transition = changed.mutation?.transition ?: return changed
            return GameplayRules.record(state, actor, command, changed.state!!.board,
                transition.copy(delta = null, concerns = null), rulesVersion = rulesVersion)
        }
        action as PlayAction.RevealHand
        if (action.cardIds.any { it !in hand.cards }) return GameplayChange(reason = "movementConflict")
        if (board.contentsDisplays.isNotEmpty()) return GameplayChange(reason = "panelAlreadyOpen")
        val display = ContentsDisplay(CommandIds.uuidV5(command.requestId, "contents/0"), actor, hand.id,
            ContentsSelection.ALL, action.cardIds.size, true, hand.cards.filter { it in action.cardIds }, panelVersion = 4)
        return GameplayRules.record(state, actor, command, board.copy(contentsDisplays = listOf(display),
            cards = board.cards.map { if (it.id in action.cardIds) it.copy(knownToAll = false) else it }),
            GameplayTransition(contents = ContentsDisplayChange(to = display), disclosures = display.cardIds.map { id ->
                GameplayDisclosure(id, board.cards.single { it.id == id }.faceId, hand.id, board.players.map { it.accountId }.sorted())
            }), rulesVersion = rulesVersion)
    }
}

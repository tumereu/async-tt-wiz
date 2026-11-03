package org.asyncttwiz.server.game

import org.asyncttwiz.protocol.BoardState
import org.asyncttwiz.protocol.GameAction
import org.asyncttwiz.protocol.GameCommand
import org.asyncttwiz.protocol.MtgFacts
import org.asyncttwiz.protocol.MtgTag
import org.asyncttwiz.protocol.PlayAction
import org.asyncttwiz.server.accounts.AccountService

/**
 * I-D52: the server takes board changes only as Automatum batches (`AutomatumOnly`). Tests of the server's storage,
 * history and sync machinery play their events as automated ones, with this tag, the way the engine's batches arrive;
 * tests of what a player may send outside Automatum build their commands untagged. Automatum's form names the board the
 * event was planned on (pivot review #4): [boarded] adds it before a tagged command is sent.
 */
internal val TEST_TAG = MtgTag(listOf("test"))

/** [action] as an automated event (its board added by [boarded]). */
internal fun automatedPlay(action: PlayAction): GameAction.Play = GameAction.Play(action, TEST_TAG)

/** [actions] as one automated batch (its first member tagged; its board added by [boarded]). */
internal fun automatedBatch(actions: List<PlayAction>): GameAction.PlayBatch =
    GameAction.PlayBatch(actions, if (actions.isEmpty()) emptyList() else listOf(TEST_TAG))

/** [action] with its test tag naming [board], the board it applies to (Automatum's form, pivot review #4). */
internal fun boarded(action: GameAction, board: BoardState): GameAction {
    val tag = TEST_TAG.copy(board = MtgFacts.digest(board))
    return when (action) {
        is GameAction.Play -> if (action.tag == TEST_TAG) action.copy(tag = tag) else action
        is GameAction.PlayBatch -> if (action.tags.firstOrNull() == TEST_TAG) action.copy(tags = listOf(tag) + action.tags.drop(1)) else action
        else -> action
    }
}

/** [command] with its test tag naming the board it applies to: the current one of the game as [actor] reads it. */
internal fun GameService.boarded(actor: AccountService.Principal, command: GameCommand): GameCommand {
    val action = command.action
    if (action !is GameAction.Play && action !is GameAction.PlayBatch) return command
    val board = head(actor, command.scope.gameId).play?.board ?: return command
    return command.copy(action = boarded(action, board))
}

package org.asyncttwiz.server.game

import org.asyncttwiz.protocol.GameAction
import org.asyncttwiz.protocol.MtgTag
import org.asyncttwiz.protocol.PlayAction

/**
 * I-D52: the server takes board changes only as Automatum batches (`AutomatumOnly`). Tests of the server's storage,
 * history and sync machinery play their events as automated ones, with this tag, the way the engine's batches arrive;
 * tests of what a player may send outside Automatum build their commands untagged.
 */
internal val TEST_TAG = MtgTag(listOf("test"))

/** [action] as an automated event. */
internal fun automatedPlay(action: PlayAction): GameAction.Play = GameAction.Play(action, TEST_TAG)

/** [actions] as one automated batch (its first member tagged). */
internal fun automatedBatch(actions: List<PlayAction>): GameAction.PlayBatch =
    GameAction.PlayBatch(actions, if (actions.isEmpty()) emptyList() else listOf(TEST_TAG))

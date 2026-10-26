package org.asyncttwiz.android

import org.asyncttwiz.protocol.*
import java.util.UUID

/** Short-lived input lowering only. Discarded before the single session request is enqueued. */
internal class PlayBatchDraft(val initial: PlayState, private val actor: String,
    val requestId: String = UUID.randomUUID().toString()) {
    var state = initial
        private set
    val actions = mutableListOf<PlayAction>()
    var overflow = false
        private set
    private val envelope = GameCommand(requestId, GameScope(initial.gameId, playId = initial.id),
        GameAction.PlayBatch(emptyList()), initial.id, "0")

    fun add(action: PlayAction): Boolean {
        if (actions.size >= 100) { overflow = true; return false }
        val change = GameplayRules.change(state, actor, PlayBatches.member(envelope, actions.size, action))
        if (change.reason != null) return false
        if (change.mutation != null) {
            actions += action
            state = requireNotNull(change.state)
        }
        return true
    }
}

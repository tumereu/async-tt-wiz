package org.asyncttwiz.protocol

val GameAction.isPlay: Boolean get() = this is GameAction.Play || this is GameAction.PlayBatch

/** A batch is transport/atomicity only: each member still uses the ordinary gameplay reducer. */
object PlayBatches {
    fun member(command: GameCommand, index: Int, action: PlayAction): GameCommand = command.copy(
        requestId = CommandIds.uuidV5(command.requestId, "member/$index"),
        action = GameAction.Play(action), invocationId = command.requestId,
        continuation = command.continuation.takeIf { index == 0 })

    fun change(tree: GameplayTree, actor: String, command: GameCommand): GameplayTreeChange {
        val batch = command.action as? GameAction.PlayBatch
            ?: return GameplayTreeChange(reason = "invalidInput")
        if (!GameWire.valid(command)) return GameplayTreeChange(reason = "invalidInput")
        var candidate = tree
        val keys = linkedSetOf<GameplayTreeKey>()
        var target: String? = null
        for ((index, action) in batch.actions.withIndex()) {
            // A no-op first member must not lose the requested historical boundary.
            val member = member(command, index, action).let {
                if (target == null) it.copy(continuation = command.continuation) else it
            }
            val change = candidate.play(actor, member)
            if (change.reason != null) return GameplayTreeChange(reason = change.reason)
            change.tree?.let { next ->
                candidate = next
                keys += next.editKeys
                target = change.targetId
            }
        }
        return if (target == null) GameplayTreeChange()
        else GameplayTreeChange(candidate.changedFrom(tree, keys), targetId = target)
    }

    /** Distinguishes new atomic invocations from legacy, possibly interleaved ones. */
    internal fun isMember(node: GameplayMutation): Boolean = node.invocationId?.let { invocation ->
        (0 until 100).any { CommandIds.uuidV5(invocation, "member/$it") == node.requestId }
    } == true
}

package org.asyncttwiz.android

import org.asyncttwiz.protocol.*

/** Inspect individual history inputs without confusing them with transport requests. */
internal fun GameCommand.playMembers(): List<GameCommand> = when (val batch = action) {
    is GameAction.PlayBatch -> batch.actions.mapIndexed { index, action -> PlayBatches.member(this, index, action) }
    else -> listOf(this)
}
internal fun List<GameCommand>.playMembers(): List<GameCommand> = flatMap { it.playMembers() }
internal fun GameCommand.playActions(): List<PlayAction> = playMembers().mapNotNull { (it.action as? GameAction.Play)?.action }

package org.asyncttwiz.android

import org.asyncttwiz.protocol.*

/** Opaque intent data, retained only until the current account can authorize the target. */
data class ActivityRoute(val accountId: String, val binding: String, val kind: ActivityKind,
    val gameId: String, val identity: String) {
    companion object {
        fun parse(account: String?, binding: String?, kind: String?, game: String?, identity: String?): ActivityRoute? {
            val type = ActivityKind.entries.find { it.name == kind } ?: return null
            if (listOf(account, binding, game, identity).any { it == null || !DeckValidation.uuid(it) }) return null
            return ActivityRoute(account!!, binding!!, type, game!!, identity!!)
        }
    }
}

/** Shared normal-route/lifecycle policy; historical review is still displaying the same game. */
object ActivityDisplay {
    fun game(inGames: Boolean, selected: String?, detail: GameSummary?): String? =
        detail?.takeIf { inGames && selected == it.id && it.lifecycle == GameLifecycle.ACTIVE }?.id
}

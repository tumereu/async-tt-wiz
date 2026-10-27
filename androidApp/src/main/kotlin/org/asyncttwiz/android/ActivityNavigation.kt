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

internal fun ActivityKind.isPodActivity(): Boolean = name.startsWith("POD_")
internal fun ActivityHint.notificationKey(): String = when {
    kind == ActivityKind.GAME -> "game:$gameId"
    kind == ActivityKind.INVITATION -> "invite:$identity"
    kind == ActivityKind.POD_INVITATION -> "pod-invite:$gameId"
    else -> "pod:$gameId"
}

internal fun podActivityText(kind: ActivityKind, name: String? = null): String = when (kind) {
    ActivityKind.POD_INVITATION -> "New pod invitation"
    ActivityKind.POD_YOUR_PICK -> name?.let { "Your pick — $it" } ?: "Your pick"
    ActivityKind.POD_DECKS_READY -> name?.let { "Decks ready — $it" } ?: "Decks ready"
    ActivityKind.POD_DRAFT_COMPLETE -> "Draft complete: build your deck"
    ActivityKind.POD_DRAFT_CANCELLED -> "Draft cancelled"
    ActivityKind.POD_DESTROYED -> "Pod destroyed"
    else -> "Game activity"
}

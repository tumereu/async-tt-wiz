package org.asyncttwiz.android

import org.asyncttwiz.protocol.*

/** Opaque intent data, retained only until the current account can authorize the target. [heldInPod]: a pod challenge
 * that waits for the recipient's deck, opened in that pod's Games tab (DRF-137). */
data class ActivityRoute(val accountId: String, val binding: String, val kind: ActivityKind,
    val gameId: String, val identity: String, val heldInPod: String? = null) {
    companion object {
        fun parse(account: String?, binding: String?, kind: String?, game: String?, identity: String?, pod: String? = null): ActivityRoute? {
            val type = ActivityKind.entries.find { it.name == kind } ?: return null
            if (account == null || binding == null || game == null || identity == null) return null
            if (listOf(account, binding, game, identity).any { !DeckValidation.uuid(it) }) return null
            if (pod != null && (type != ActivityKind.INVITATION || !DeckValidation.uuid(pod))) return null
            return ActivityRoute(account, binding, type, game, identity, pod)
        }
    }
}

/** Shared normal-route/lifecycle policy; historical review is still displaying the same game. */
object ActivityDisplay {
    fun game(inGames: Boolean, selected: String?, detail: GameSummary?): String? =
        detail?.takeIf { inGames && selected == it.id && it.lifecycle == GameLifecycle.ACTIVE }?.id
}

/** What the resumed app shows. Its notifications are cleared on open and not posted while shown. */
data class ActivityScreen(val game: String? = null, val pod: String? = null, val podList: Boolean = false) {
    fun shows(key: String): Boolean = game != null && key == "game:$game" ||
        pod != null && (key == "pod:$pod" || key == "pod-chat:$pod") || podList && key.startsWith("pod-invite:")

    companion object {
        val NONE = ActivityScreen()
        fun of(destination: ShellDestination, selectedGame: String?, pods: PodState) = when (destination) {
            ShellDestination.GAMES -> ActivityScreen(game = selectedGame)
            ShellDestination.PODS -> if (pods.selected != null) ActivityScreen(pod = pods.selected)
                else ActivityScreen(podList = pods.creating == null)
            else -> NONE
        }
    }
}

fun ActivityKind.isPodActivity(): Boolean = name.startsWith("POD_")
fun ActivityHint.notificationKey(): String = when {
    kind == ActivityKind.GAME || kind == ActivityKind.GAME_REMOVAL -> "game:$gameId"
    kind == ActivityKind.INVITATION -> "invite:$identity"
    kind == ActivityKind.POD_INVITATION -> "pod-invite:$gameId"
    else -> "pod:$gameId"
}

/**
 * DRF-137, LOB-072: an invitation's notification: "<name> invited you · <game>"; a pod challenge "<pod>: <name> challenged
 * you", while it waits for your deck with " · submit your deck to accept"; a pod invitation "<name> invited you to <pod>"
 * ([podName]: the pod's name when the hint has none). Without the inviter's name, "New invitation" or "New pod invitation".
 */
fun invitationText(hint: ActivityHint, podName: String? = null): String {
    val name = hint.opponentName
    return when {
        hint.kind == ActivityKind.POD_INVITATION -> name?.let { who -> (hint.resourceName ?: podName)?.let { "$who invited you to $it" } }
            ?: podActivityText(ActivityKind.POD_INVITATION)
        name == null -> "New invitation"
        hint.podId != null -> "${hint.resourceName ?: "Pod"}: $name challenged you" + if (hint.held) " · submit your deck to accept" else ""
        else -> "$name invited you" + hint.resourceName?.let { " · $it" }.orEmpty()
    }
}

fun podActivityText(kind: ActivityKind, name: String? = null): String = when (kind) {
    ActivityKind.POD_INVITATION -> "New pod invitation"
    ActivityKind.POD_YOUR_PICK -> name?.let { "Your pick — $it" } ?: "Your pick"
    ActivityKind.POD_DECKS_READY -> name?.let { "Decks ready — $it" } ?: "Decks ready"
    ActivityKind.POD_DRAFT_COMPLETE -> "Draft complete: build your deck"
    ActivityKind.POD_DRAFT_CANCELLED -> "Draft cancelled"
    ActivityKind.POD_DESTROYED -> "Pod destroyed"
    ActivityKind.POD_AUTO_START_OFF -> name?.let { "Auto-start off — $it" } ?: "Auto-start off"
    // Game history §4.5: the cleanup's warning 3 days before a stalled pod is deleted
    ActivityKind.POD_REMOVAL -> "${name ?: "Pod"}: Removed in 3 days if nobody picks or plays"
    else -> "Game activity"
}

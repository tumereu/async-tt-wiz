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

/** DRF-137, LOB-072: an invitation's notification ([ActivityTexts.invitation]). */
fun invitationText(hint: ActivityHint, podName: String? = null): String = ActivityTexts.invitation(hint, podName)

/** A pod notification's title ([ActivityTexts.podActivity]). */
fun podActivityText(kind: ActivityKind, name: String? = null): String = ActivityTexts.podActivity(kind, name)

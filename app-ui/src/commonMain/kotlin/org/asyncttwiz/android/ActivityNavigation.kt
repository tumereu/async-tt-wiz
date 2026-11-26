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

/** What the resumed app shows. Its notifications are cleared on open and not posted while shown (LOB-277: a game's chat's
 * with the game, a pod's chat's with the pod). */
data class ActivityScreen(val game: String? = null, val pod: String? = null, val podList: Boolean = false) {
    fun shows(key: String): Boolean = key in keys() || podList && key.startsWith("pod-invite:")
    /** The notification keys of the game or pod shown. */
    fun keys(): List<String> = listOfNotNull(game?.let { "game:$it" }, game?.let { "chat:$it" }, pod?.let { "pod:$it" },
        pod?.let { "pod-chat:$it" })

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
/** The notification a hint shows in: one per game, game chat, invitation, pod invitation, pod and pod chat; a pod challenge is its pod's one
 * notification (DRF-135, DRF-122), so it and the pod's other notices replace each other, the newer showing. */
fun ActivityHint.notificationKey(): String = when {
    kind == ActivityKind.GAME || kind == ActivityKind.GAME_REMOVAL -> "game:$gameId"
    // LOB-277: one per chat, a game's or a pod's
    kind == ActivityKind.CHAT -> "chat:$gameId"
    kind == ActivityKind.POD_CHAT -> "pod-chat:$gameId"
    kind == ActivityKind.INVITATION && podId != null -> "pod:$podId"
    kind == ActivityKind.INVITATION -> "invite:$identity"
    kind == ActivityKind.POD_INVITATION -> "pod-invite:$gameId"
    else -> "pod:$gameId"
}

/** DRF-137, LOB-072: an invitation's notification ([ActivityTexts.invitation]). */
fun invitationText(hint: ActivityHint, podName: String? = null): String = ActivityTexts.invitation(hint, podName)

/** A pod notification's title ([ActivityTexts.podActivity]). */
fun podActivityText(kind: ActivityKind, name: String? = null): String = ActivityTexts.podActivity(kind, name)

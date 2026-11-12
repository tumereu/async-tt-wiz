package org.asyncttwiz.android

import org.asyncttwiz.appui.ExecutorService
import org.asyncttwiz.appui.randomUuid
import org.asyncttwiz.appui.runBlocking
import org.asyncttwiz.protocol.*

/**
 * DRF-105, DRF-166, LOB-047 (owner, 2026-11-11): a pod game's own deck renamed or given another cover from Prepare deck in
 * the game's lobby, also after submitting. They are the pod's own requests: the server changes the pod deck (its draft deck
 * too) and with it this member's games of the pod still in their lobby, the cover reaching this one as its revision; the
 * new name shows at once ([GameSession.podDeckNamed]). Main thread; requests run on [worker]. [saving] while one is on its
 * way; a failure sets the lobby's error.
 */
class LobbyPodDeck(private val scope: LobbyScope, private val pods: Pods?, private val credential: () -> String?,
    private val worker: ExecutorService, private val post: (() -> Unit) -> Unit, private val session: GameSession?) {
    private var state: LobbyState
        get() = scope.state
        set(value) { scope.state = value }

    /** A rename or cover change is on its way. */
    var saving = false
        private set

    /** Whether the open game's own deck can be renamed or given another cover here: a pod game in its lobby. */
    fun available(): Boolean = pods != null && state.detail?.let { it.podId != null && it.lifecycle == GameLifecycle.LOBBY } == true

    /** Renames the open pod game's deck to [name] (trimmed; 1–120 characters as in Build). */
    fun rename(name: String) {
        val trimmed = name.trim().take(120)
        if (trimmed.isEmpty()) return
        send("name \"$trimmed\"", done = { game -> session?.podDeckNamed(game, trimmed) }) { api, token, pod, attempt ->
            api.deckName(token, pod, SetPodDeckName(randomUuid(), attempt, trimmed))
        }
    }

    /** Makes the card [definition] (a digest among the deck's Main and Side cards) the open pod game's deck cover. */
    fun cover(definition: String) = send("cover $definition") { api, token, pod, attempt ->
        api.deckCover(token, pod, SetPodDeckCover(randomUuid(), attempt, definition))
    }

    private fun send(what: String, done: (gameId: String) -> Unit = {},
        request: suspend (Pods, String, String, String) -> PodDetail) {
        val game = state.detail ?: return
        val pod = game.podId ?: return
        val api = pods ?: return
        val token = credential() ?: return
        if (saving || game.lifecycle != GameLifecycle.LOBBY) return
        saving = true
        val generation = scope.epoch
        AppLog.info("lobby", "Pod deck $what from game ${game.id.take(8)}'s lobby (pod ${pod.take(8)})")
        worker.execute {
            val result = runCatching { runBlocking {
                // The pod's current draft attempt, which its requests name
                val attempt = api.detail(token, pod).draftAttemptId ?: throw PodFailure("not_drafting")
                request(api, token, pod, attempt)
            } }
            post {
                saving = false
                if (scope.closed || generation != scope.epoch) return@post
                result.onSuccess {
                    AppLog.info("lobby", "Pod deck $what saved")
                    done(game.id)
                    scope.connectionState()
                }.onFailure { failure ->
                    AppLog.warn("lobby", "Pod deck $what not saved: $failure")
                    state = state.copy(error = if (what.startsWith("name")) "Couldn't save the deck name. Try again."
                        else "Couldn't save the deck cover. Try again.")
                }
            }
        }
    }
}

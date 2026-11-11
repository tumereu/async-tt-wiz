package org.asyncttwiz.android

import org.asyncttwiz.protocol.GamePresence
import androidx.compose.runtime.Composable
import org.asyncttwiz.appui.AppPlatform
import org.asyncttwiz.appui.postToMainDelayed
import org.asyncttwiz.appui.AppBuild
import org.asyncttwiz.appui.randomUuid
import org.asyncttwiz.appui.runBlocking
import org.asyncttwiz.appui.newDiscardOldestExecutor

/** Dialogs have their own Window and bypass the activity's input: what is done there counts just like the table
 * (the platform's [AppPlatform.ObserveDialogInput]). */
@Composable
fun TrackGameDialogInput() { AppPlatform.current.ObserveDialogInput() }

/** Any input (not just commands) sets one bit, consumed once per heartbeat. */
class GameHeartbeatTracker(private val presenceId: String = randomUuid()) {
    private var game: String? = null
    private var didSomething = false
    fun input() { if (game != null) didSomething = true }
    fun display(next: String?): List<GamePresence> {
        if (next == game) return emptyList()
        val left = game?.let { GamePresence(it, presenceId, didSomething, left = true) }
        game = next; didSomething = false
        return listOfNotNull(left, heartbeat())
    }
    fun heartbeat(): GamePresence? = game?.let { GamePresence(it, presenceId, didSomething).also { didSomething = false } }
}

/** Main-thread lifecycle owner; socket first, bounded authenticated HTTP fallback off the UI thread.
 * Leaving always goes over HTTP too: a socket send only queues, and on a dead or soon frozen socket the
 * "left" mark is lost while the app sits in the background. */
class GameHeartbeatController(
    private val http: (String, GamePresence) -> Unit = { token, presence -> runBlocking {
        ActivityApi(AppBuild.ACCOUNT_ORIGIN).use { it.heartbeat(token, presence) } } },
    private val socket: (String, GamePresence) -> Boolean,
) : AutoCloseable {
    private val tracker = GameHeartbeatTracker()
    private val worker = newDiscardOldestExecutor("game-presence", 2)
    private var token: String? = null
    private var game: String? = null
    private var cancelPulse: () -> Unit = {}
    private fun pulse() {
        tracker.heartbeat()?.let { send(token, it) }
        if (game != null) cancelPulse = postToMainDelayed(5_000, ::pulse)
    }
    fun display(game: String?, token: String?) {
        val next = game?.takeIf { token != null }
        if (this.game == next && this.token == token) return
        if (this.token != token) tracker.display(null).forEach { send(this.token, it) }
        this.token = token; this.game = next
        tracker.display(next).forEach { send(token, it) }
        cancelPulse()
        if (next != null) cancelPulse = postToMainDelayed(5_000, ::pulse)
    }
    fun input() = tracker.input()
    private fun send(token: String?, presence: GamePresence) {
        if (token == null || socket(token, presence) && !presence.left) return
        worker.execute { runCatching { http(token, presence) } }
    }
    override fun close() { display(null, token); cancelPulse(); worker.shutdown() }
}

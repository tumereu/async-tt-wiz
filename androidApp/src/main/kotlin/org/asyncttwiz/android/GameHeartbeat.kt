package org.asyncttwiz.android

import org.asyncttwiz.protocol.GamePresence
import java.util.UUID
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.DialogWindowProvider

/** Dialogs have their own Window and bypass Activity.dispatchTouchEvent. Observe,
 * never consume, so scrolling/holds/typing there count just like the table. */
@Composable
internal fun TrackGameDialogInput() {
    var context = LocalContext.current
    while (context is android.content.ContextWrapper && context !is MainActivity) context = context.baseContext
    val host = context as? MainActivity ?: return
    val window = (LocalView.current.parent as? DialogWindowProvider)?.window ?: return
    DisposableEffect(host, window) {
        val original = window.callback
        val observer = object : android.view.Window.Callback by original {
            override fun dispatchTouchEvent(event: android.view.MotionEvent): Boolean {
                host.gameInput(); return original.dispatchTouchEvent(event)
            }
            override fun dispatchGenericMotionEvent(event: android.view.MotionEvent): Boolean {
                host.gameInput(); return original.dispatchGenericMotionEvent(event)
            }
            override fun dispatchKeyEvent(event: android.view.KeyEvent): Boolean {
                host.gameInput(); return original.dispatchKeyEvent(event)
            }
        }
        window.callback = observer
        onDispose { if (window.callback === observer) window.callback = original }
    }
}

/** Any input (not just commands) sets one bit, consumed once per heartbeat. */
internal class GameHeartbeatTracker(private val presenceId: String = UUID.randomUUID().toString()) {
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
internal class GameHeartbeatController(
    private val http: (String, GamePresence) -> Unit = { token, presence -> kotlinx.coroutines.runBlocking {
        ActivityApi(BuildConfig.ACCOUNT_ORIGIN).use { it.heartbeat(token, presence) } } },
    private val socket: (String, GamePresence) -> Boolean,
) : AutoCloseable {
    private val tracker = GameHeartbeatTracker()
    private val main = android.os.Handler(android.os.Looper.getMainLooper())
    private val worker = java.util.concurrent.ThreadPoolExecutor(1, 1, 0, java.util.concurrent.TimeUnit.SECONDS,
        java.util.concurrent.ArrayBlockingQueue<Runnable>(2), { Thread(it, "game-presence") },
        java.util.concurrent.ThreadPoolExecutor.DiscardOldestPolicy())
    private var token: String? = null
    private var game: String? = null
    private val pulse = object : Runnable {
        override fun run() {
            tracker.heartbeat()?.let { send(token, it) }
            if (game != null) main.postDelayed(this, 5_000)
        }
    }
    fun display(game: String?, token: String?) {
        val next = game?.takeIf { token != null }
        if (this.game == next && this.token == token) return
        if (this.token != token) tracker.display(null).forEach { send(this.token, it) }
        this.token = token; this.game = next
        tracker.display(next).forEach { send(token, it) }
        main.removeCallbacks(pulse)
        if (next != null) main.postDelayed(pulse, 5_000)
    }
    fun input() = tracker.input()
    private fun send(token: String?, presence: GamePresence) {
        if (token == null || socket(token, presence) && !presence.left) return
        worker.execute { runCatching { http(token, presence) } }
    }
    override fun close() { display(null, token); main.removeCallbacks(pulse); worker.shutdown() }
}

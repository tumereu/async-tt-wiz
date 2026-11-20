package org.asyncttwiz.android

import org.asyncttwiz.appui.AppBuild
import org.asyncttwiz.appui.executeSuspending
import org.asyncttwiz.appui.newDiscardOldestExecutor
import org.asyncttwiz.appui.postToMain
import org.asyncttwiz.protocol.PodSeen

/** What of the pods [screen] shows, for [PodSeenReporter]: the open pod's last change, or the Pods list's pods; null while
 * it hasn't loaded. */
fun podSeenVersion(screen: ActivityScreen, pods: PodState): String? = when {
    screen.pod != null -> pods.detail?.takeIf { it.id == screen.pod }?.updatedAt
    screen.podList && pods.loaded -> pods.pods.joinToString(",") { it.id }
    else -> null
}

/**
 * LOB-243 (owner, 2026-11-20): tells the server that a pod is open in front on this device (or the Pods list, which a
 * destroyed pod's notification opens), so the pod's news ("Decks ready", "Draft cancelled" …) clears on every device of the
 * player's. Sent once for each pod and [version] of it shown in front, so news that comes while the pod is open is seen
 * too; one that couldn't be sent is sent again at the next [display].
 */
class PodSeenReporter(
    private val http: suspend (String, PodSeen) -> Unit = { token, seen ->
        ActivityApi(AppBuild.ACCOUNT_ORIGIN).use { it.seen(token, seen) } },
    private val post: (() -> Unit) -> Unit = { postToMain(it) },
    /** Runs a request off the main thread (tests run them themselves). */
    run: ((suspend () -> Unit) -> Unit)? = null,
) : AutoCloseable {
    private val worker = if (run == null) newDiscardOldestExecutor("pod-seen", 2) else null
    private val launch: (suspend () -> Unit) -> Unit = run ?: worker.let { pool -> { block -> pool?.executeSuspending { block() } } }
    private var sent: String? = null

    /** Main thread: [screen] is shown, in front when [foreground], at [version] ([podSeenVersion]). */
    fun display(screen: ActivityScreen, version: String?, token: String?, foreground: Boolean) {
        val seen = when {
            token == null || !foreground || version == null -> null
            screen.pod != null -> PodSeen(screen.pod)
            screen.podList -> PodSeen()
            else -> null
        }
        val key = seen?.let { "${token.hashCode()}:${it.podId ?: "list"}:$version" }
        if (key == sent) return
        sent = key
        if (seen == null || token == null) return
        val what = seen.podId?.let { "pod ${it.take(8)}" } ?: "the Pods list"
        launch {
            try {
                http(token, seen)
                AppLog.info("activity", "Told the server $what is open in front: its news is seen on every device")
            } catch (failure: Exception) {
                if (failure is kotlinx.coroutines.CancellationException) throw failure
                AppLog.warn("activity", "Couldn't tell the server $what is open in front (sent again at the next change)", failure)
                post { if (sent == key) sent = null }
            }
        }
    }

    override fun close() { worker?.shutdown() }
}

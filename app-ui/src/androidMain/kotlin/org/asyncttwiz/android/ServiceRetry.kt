package org.asyncttwiz.android

import kotlinx.coroutines.delay

internal val quickRetryDelays = listOf(500L, 1_500L)
/** A first load keeps its loading indicator through a short outage (~10 s) before showing Retry. */
internal val firstLoadRetryDelays = listOf(500L, 1_500L, 2_000L, 3_000L, 3_000L)

/** Retry only safe reads or idempotent originals, always with the same captured request identity. */
internal suspend fun <T> serviceRetry(wait: (suspend (Long) -> Unit)? = null,
    current: () -> Boolean = { true }, delays: List<Long> = quickRetryDelays, action: suspend () -> T): T {
    for (attempt in 0..delays.size) {
        if (!current()) throw kotlinx.coroutines.CancellationException("Request owner changed")
        try { return action() } catch (failure: Exception) {
            if (!transientServiceFailure(failure) || attempt == delays.size) throw failure
            AppLog.info("http", "Retrying after ${failure.javaClass.simpleName}")
            if (wait == null) delay(delays[attempt]) else wait(delays[attempt])
        }
    }
    error("Unreachable")
}

internal fun transientServiceFailure(failure: Throwable): Boolean = when (failure) {
    is DeckFailure -> failure.code in setOf("network", "unavailable", "storage_unavailable")
    is LobbyFailure -> failure.code in setOf("network", "unavailable", "storage_unavailable")
    is PodFailure -> failure.code in setOf("network", "unavailable", "storage_unavailable", "internal_error")
    is AccountFailure -> failure.code in setOf("network", "unavailable")
    else -> failure is java.io.IOException
}

/** Automatic refreshes of data already on screen stay silent through short outages; the player
 * is told only once they have kept failing for [quietMillis]. Requests the player made report at once. */
internal class QuietRefreshFailures(private val now: () -> Long, private val quietMillis: Long = 30_000) {
    private var since: Long? = null
    fun succeeded() { since = null }
    /** Records a transient failure of a quiet refresh; true once such failures have lasted [quietMillis]. */
    fun failed(): Boolean { val start = since ?: now().also { since = it }; return now() - start >= quietMillis }
}
internal fun monotonicMillis() = System.nanoTime() / 1_000_000

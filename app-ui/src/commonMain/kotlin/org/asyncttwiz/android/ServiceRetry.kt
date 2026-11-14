package org.asyncttwiz.android

import kotlinx.coroutines.delay
import org.asyncttwiz.appui.nanoTime
import org.asyncttwiz.appui.simpleClassName

val quickRetryDelays = listOf(500L, 1_500L)
/** A first load keeps its loading indicator through a short outage (~10 s) before showing Retry. */
val firstLoadRetryDelays = listOf(500L, 1_500L, 2_000L, 3_000L, 3_000L)

/** Retry only safe reads or idempotent originals, always with the same captured request identity. */
suspend fun <T> serviceRetry(wait: (suspend (Long) -> Unit)? = null,
    current: () -> Boolean = { true }, delays: List<Long> = quickRetryDelays, action: suspend () -> T): T {
    for (attempt in 0..delays.size) {
        if (!current()) throw kotlinx.coroutines.CancellationException("Request owner changed")
        try { return action() } catch (failure: Exception) {
            if (!transientServiceFailure(failure) || attempt == delays.size) throw failure
            AppLog.info("http", "Retrying in ${delays[attempt]} ms after ${failureKind(failure)}")
            if (wait == null) delay(delays[attempt]) else wait(delays[attempt])
        }
    }
    error("Unreachable")
}

fun transientServiceFailure(failure: Throwable): Boolean = when (failure) {
    is DeckFailure -> failure.code in setOf("network", "unavailable", "storage_unavailable")
    is LobbyFailure -> failure.code in setOf("network", "unavailable", "storage_unavailable")
    is PodFailure -> failure.code in setOf("network", "unavailable", "storage_unavailable", "internal_error")
    is AccountFailure -> failure.code in setOf("network", "unavailable")
    else -> failure is kotlinx.io.IOException
}

/** A request failure in a few words for the app log: its code (network, unavailable, ...), else its class. */
fun failureKind(failure: Throwable): String = when (failure) {
    is DeckFailure -> "DeckFailure(${failure.code})"
    is LobbyFailure -> "LobbyFailure(${failure.code})"
    is PodFailure -> "PodFailure(${failure.code})"
    is AccountFailure -> "AccountFailure(${failure.code})"
    else -> simpleClassName(failure)
}

/** Automatic refreshes of data already on screen stay silent through short outages; the player
 * is told only once they have kept failing for [quietMillis]. Requests the player made report at once.
 * With a [tag], a stretch of failures is in the app log as it starts, as the note shows and as it ends (not per poll). */
class QuietRefreshFailures(private val now: () -> Long, private val quietMillis: Long = 30_000, private val tag: String? = null) {
    private var since: Long? = null
    private var failures = 0
    private var noted = false
    fun succeeded() {
        val start = since
        if (start != null && tag != null) AppLog.info(tag, "Refreshes work again after $failures quiet failures in " +
            "${(now() - start) / 1000} s${if (noted) "; the can't-reach note hidden" else ""}")
        since = null; failures = 0; noted = false
    }
    /** Records a transient failure of a quiet refresh; true once such failures have lasted [quietMillis]. */
    fun failed(): Boolean {
        val start = since ?: now().also { since = it }
        failures++
        val note = now() - start >= quietMillis
        if (tag != null) {
            if (failures == 1) AppLog.info(tag, "Refresh failed: kept quiet, the next refresh retries; the can't-reach note after ${quietMillis / 1000} s")
            else if (note && !noted) AppLog.info(tag, "Refreshes failing for ${(now() - start) / 1000} s ($failures failures): the can't-reach note shown")
        }
        noted = noted || note
        return note
    }
}
fun monotonicMillis() = nanoTime() / 1_000_000

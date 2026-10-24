package org.asyncttwiz.android

import kotlinx.coroutines.delay

/** Retry only safe reads or idempotent originals, always with the same captured request identity. */
internal suspend fun <T> serviceRetry(wait: suspend (Long) -> Unit = { delay(it) },
    current: () -> Boolean = { true }, action: suspend () -> T): T {
    val delays = listOf(500L, 1_500L)
    for (attempt in 0..delays.size) {
        if (!current()) throw kotlinx.coroutines.CancellationException("Request owner changed")
        try { return action() } catch (failure: Exception) {
            if (!transientServiceFailure(failure) || attempt == delays.size) throw failure
            wait(delays[attempt])
        }
    }
    error("Unreachable")
}

internal fun transientServiceFailure(failure: Throwable): Boolean = when (failure) {
    is DeckFailure -> failure.code in setOf("network", "unavailable", "storage_unavailable")
    is LobbyFailure -> failure.code in setOf("network", "unavailable", "storage_unavailable")
    is AccountFailure -> failure.code in setOf("network", "unavailable")
    else -> failure is java.io.IOException
}

package org.asyncttwiz.storage

/** A failed image never exhausts its attempts. Connectivity wakes a pending wait, not a burst. */
class ArtworkRetrySchedule(
    private val now: () -> Long = System::currentTimeMillis,
    private val connectivityGeneration: () -> Long = { 0L },
) {
    private var generation = connectivityGeneration()
    private var failures = 0
    private var due = 0L

    fun failed() {
        failures = (failures + 1).coerceAtMost(6)
        due = now() + backoff(failures)
    }

    fun remainingMillis(): Long {
        val current = connectivityGeneration()
        if (current != generation) {
            generation = current
            due = now()
        }
        return (due - now()).coerceAtLeast(0)
    }

    companion object {
        fun backoff(failures: Int): Long = minOf(30_000L, 1_000L shl (failures - 1).coerceIn(0, 5))
    }
}

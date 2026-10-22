package org.asyncttwiz.server.accounts

import java.security.MessageDigest

/** Bounded in-process fixed windows; no permanent account lockout, raw email keys or trusted XFF. */
class AccountRateLimits(private val now: () -> Long = { System.nanoTime() / 1_000_000_000 }) {
    private data class Window(val until: Long, var count: Int)
    private val windows = HashMap<String, Window>()

    @Synchronized fun allow(scope: String, value: String, limit: Int, seconds: Long): Boolean {
        val time = now()
        val key = scope + ":" + MessageDigest.getInstance("SHA-256").digest(value.toByteArray()).joinToString("") { "%02x".format(it) }
        var entry = windows[key]
        if (entry == null || time >= entry.until) {
            if (windows.size >= 10_000) windows.entries.removeIf { time >= it.value.until }
            if (windows.size >= 10_000) return false
            entry = Window(time + seconds, 0)
            windows[key] = entry
        }
        if (entry.count >= limit) return false
        entry.count++
        return true
    }
}

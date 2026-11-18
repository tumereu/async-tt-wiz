package org.asyncttwiz.server.accounts

import java.security.MessageDigest

/**
 * Bounded in-process fixed windows; no permanent account lockout, raw email keys or trusted XFF.
 *
 * What it logs ([log]): the first refusal of a window, and, when a window ends (its key's next request), how many requests
 * it allowed and refused if that was at least half its limit: a client asking too often explains itself in the server log.
 * A key is named by [allow]'s `shown` (an account id), else by the start of its hash (never an email or address).
 */
class AccountRateLimits(private val log: (String) -> Unit = { LOG.info(it) },
    private val now: () -> Long = { System.nanoTime() / 1_000_000_000 }) {
    private class Window(val until: Long, val limit: Int, val seconds: Long, val shown: String) {
        var count = 0
        var refused = 0
    }
    private val windows = HashMap<String, Window>()

    @Synchronized fun allow(scope: String, value: String, limit: Int, seconds: Long, shown: String? = null): Boolean {
        val time = now()
        val hash = MessageDigest.getInstance("SHA-256").digest(value.toByteArray()).joinToString("") { "%02x".format(it) }
        val key = "$scope:$hash"
        var entry = windows[key]
        if (entry == null || time >= entry.until) {
            entry?.let { ended(scope, it) }
            if (windows.size >= 10_000) windows.entries.removeIf { time >= it.value.until }
            if (windows.size >= 10_000) return false
            entry = Window(time + seconds, limit, seconds, shown ?: "key ${hash.take(8)}")
            windows[key] = entry
        }
        if (entry.count >= limit) {
            if (entry.refused++ == 0) log("Rate limit $scope of ${entry.shown}: $limit requests in $seconds s reached; " +
                "refusing for the window's last ${entry.until - time} s")
            return false
        }
        entry.count++
        return true
    }

    private fun ended(scope: String, window: Window) {
        if (window.refused > 0 || window.count * 2 >= window.limit) log("Rate window $scope of ${window.shown} ended: " +
            "${window.count} of ${window.limit} requests allowed in ${window.seconds} s, ${window.refused} refused")
    }

    private companion object {
        val LOG: org.slf4j.Logger = org.slf4j.LoggerFactory.getLogger(AccountRateLimits::class.java)
    }
}

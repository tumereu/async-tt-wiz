package org.asyncttwiz.android

/**
 * The game connection's lines in the app log (problem report VSVLSK: a page that opened its socket again every second
 * logged only "Connected", never why): each kind of line at most [BURST] times a minute; the ones beyond that are counted
 * and their number is said with the next line of the kind that is logged, so a reconnect loop explains itself without
 * pushing everything else out of the report.
 */
class ConnectionLog(private val now: () -> Long, private val write: (warn: Boolean, line: String) -> Unit = { warn, line ->
    if (warn) AppLog.warn("game", line) else AppLog.info("game", line)
}) {
    private class Window(var start: Long, var logged: Int = 0, var dropped: Int = 0)
    private val windows = mutableMapOf<String, Window>()

    fun info(kind: String, message: String) = add(kind, message, false)
    fun warn(kind: String, message: String) = add(kind, message, true)

    private fun add(kind: String, message: String, warn: Boolean) {
        val at = now()
        val window = windows.getOrPut(kind) { Window(at) }
        var dropped = 0
        if (at - window.start >= WINDOW_MILLIS) { dropped = window.dropped; window.start = at; window.logged = 0; window.dropped = 0 }
        if (window.logged >= BURST) { window.dropped++; return }
        window.logged++
        write(warn, message + (if (dropped > 0) " ($dropped more like it before this, not logged)" else "") +
            (if (window.logged == BURST) " (more like it this minute are only counted)" else ""))
    }

    companion object {
        const val BURST = 3
        const val WINDOW_MILLIS = 60_000L
    }
}

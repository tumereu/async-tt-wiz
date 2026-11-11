package org.asyncttwiz.protocol

import kotlin.concurrent.Volatile

/**
 * Client faults: a state the code didn't expect (a value missing that "can't" be missing), met where the client keeps
 * going with a plain fallback instead of crashing. [report] passes each to [sink]: the app's logs it at error level and
 * sends it as an automatic problem report ([ProblemReport.FAULT]) with the app log and the game's Automatum bundle
 * (app-ui's FaultReports), once per [Fault.key] per run. Without a sink (the server, plain tests, the web client for now)
 * a report does nothing.
 */
object Faults {
    /** Set once by the app at start. A failing sink never reaches the caller. */
    @Volatile var sink: ((Fault) -> Unit)? = null

    /** [area]: the code it happened in (a class or screen); [what]: a fixed description of the unexpected state, the same
     * every time (with [area] it is the deduplication key, so ids belong in [details]); [details]: the ids and state
     * needed to debug it. */
    fun report(area: String, what: String, details: Map<String, String> = emptyMap()) {
        val target = sink ?: return
        try { target(Fault(area, what, details)) } catch (_: Throwable) {}
    }
}

/** One [Faults.report]. */
data class Fault(val area: String, val what: String, val details: Map<String, String>) {
    /** What deduplicates it: the same area and description are reported once per run whatever their details. */
    val key: String get() = "$area: $what"

    /** One log line: the key and every detail. */
    fun line(): String = key + details.entries.joinToString("") { (name, value) -> "; $name=$value" }
}

package org.asyncttwiz.android

import org.asyncttwiz.protocol.ReconnectEpisode

// The diagnostics that record crashes, stutters and slow actions while reports are on ([Diagnostics]; SYS-030, LOB-090).
// Their queues, files and upload loops are the platform's (Android: the JVM's crash handler, files and IO dispatcher);
// the app's code reports to them through these.

/** A screen's frame tracking (Android: JankStats on an activity's window): [stop] ends it, [state] labels its frames. */
class JankTracking(val stop: () -> Unit, val state: (String, String) -> Unit)

/** Stutter reports: what the app was doing when frames were slow (Android: JankTelemetry.kt). */
expect object DebugJank {
    fun catchUp(game: String, running: Boolean)
    fun catchingUp(): Boolean
    fun treeInFlight(): Boolean
    fun animation(owner: String, running: Boolean)
    fun state(vararg values: Pair<String, String>)
    fun action(name: String)
    fun beginWork(kind: String): Long
    fun endWork(kind: String, start: Long)
    /** A screen ([owner]) starts its frame tracking: an earlier one of it stops; the labels so far are set on it. */
    fun track(owner: Any, tracking: JankTracking)
    /** [owner]'s frame tracking stops. */
    fun untrack(owner: Any)
    /** A frame the platform drew (only while reports are on), with the labels ([state]) it was drawn under. */
    fun frame(startNanos: Long, durationNanos: Long, isJank: Boolean, refreshRate: Float, states: Map<String, String>)
    fun authenticated(value: String?)
    /** Reports turned off: queued stutters are dropped and the saved batch deleted. */
    fun discard()
}

/** The engine games' own-action probe ([EngineDiagnostics], app-engine's) reports to [DebugLatency]: installed by the
 * app's diagnostics and its game sessions, so every game the app opens has it. */
fun installEngineDiagnostics() { EngineDiagnostics.latency = DebugLatency::begin }

/** Slow-action, reconnect and tap reports (Android: LatencyTelemetry.kt's DebugLatency). */
expect object DebugLatency {
    fun begin(gameId: String, action: String, nodes: Int): LatencyRecorder.Trace?
    fun result(requestId: String)
    fun drawn()
    fun tap(gameId: String, event: String, popup: Boolean = false, cardId: String? = null)
    fun tapAnimation(gameId: String, rotatingCardIds: Set<String>)
    fun tapFrame(gameId: String, progress: Float, rate: Float)
    fun reconnect(value: ReconnectEpisode)
    /** The app went to the background (not a rotation): what waits is sent now. */
    fun appBackgrounded()
    /** Reports turned off: the queues are emptied and the saved batch deleted. */
    fun discard()
    fun authenticated(value: String?)
}

/** Crash reports, kept on the device until sent (Android: CrashReports.kt). */
expect object DebugCrashes {
    /** The signed-in session's token as the app last said ([authenticated]), for a problem report sent from a screen
     * that doesn't hold the account (the table's Send problem report). */
    fun sessionToken(): String?
    fun install(hooks: ReportHooks)
    /** Reports turned off: queued reports are deleted on disk and in memory; nothing more is recorded or sent. */
    fun discard()
    fun authenticated(token: String?)
}

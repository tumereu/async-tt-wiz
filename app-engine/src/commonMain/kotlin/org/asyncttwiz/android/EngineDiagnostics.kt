package org.asyncttwiz.android

import kotlin.concurrent.Volatile
import org.asyncttwiz.appui.AppBuild

/**
 * What an [EngineGame]'s own-action probe (SYS-025, SYS-030) reports to the app's diagnostics, which are app-ui's
 * (Diagnostics, DebugLatency): the app installs [latency] (its slow-action recorder) before its games open; the engine's
 * Web Worker has none.
 */
object EngineDiagnostics {
    /** Test and debug builds (app-ui's Diagnostics.alwaysOn): every own action is logged, not only the slow ones. */
    val alwaysOn: Boolean get() = AppBuild.DIAGNOSTICS

    /** The app's slow-action recorder (app-ui's DebugLatency.begin); null: none. */
    @Volatile var latency: ((gameId: String, action: String, nodes: Int) -> LatencyTrace?)? = null

    /** A new trace of [action] in [gameId] from the app's recorder, or null (none installed, or reports off). */
    fun begin(gameId: String, action: String, nodes: Int): LatencyTrace? = latency?.invoke(gameId, action, nodes)
}

/** One own action's trace in the app's slow-action recorder (app-ui's LatencyRecorder.Trace). */
interface LatencyTrace {
    /** The action was sent ([requestId]) or, [predicted], applied locally. */
    fun submitted(requestId: String?, predicted: Boolean)
    /** Its local result is shown. */
    fun finish()
    /** Its parts (SYS-030), in milliseconds by their labels, once they are in. */
    fun parts(parts: Map<String, Double>)
}

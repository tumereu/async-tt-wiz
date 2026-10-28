package org.asyncttwiz.protocol

import kotlinx.serialization.Serializable

/** Debug diagnostics only; never part of game state or history. */
@Serializable
data class CrashReport(val reportedAt: String, val appVersion: String, val device: String, val body: String,
    val kind: String = "crash")

/** Milliseconds are monotonic elapsed times, not wall-clock/network latency estimates. */
@Serializable
data class ActionLatency(val reportedAt: String, val gameId: String, val actionType: String,
    val historyNodeCount: Int, val predicted: Boolean, val localApplyMs: Double? = null,
    val firstFrameMs: Double? = null, val serverResultMs: Double? = null, val submitted: Boolean = true)

@Serializable
data class LatencyBatch(val actions: List<ActionLatency>, val reconnects: List<ReconnectEpisode> = emptyList(),
    val taps: List<CardTapDiagnostic> = emptyList())

/** Bounded debug input/render observations. Never includes card IDs or card content. */
@Serializable
data class CardTapDiagnostic(val reportedAt: String, val gameId: String, val outcome: String,
    val reason: String, val firstAnimationFrameMs: Double? = null, val droppedFrames: Int = 0,
    val popupOpen: Boolean = false, val popupClosing: Boolean = false,
    val catchUpInFlight: Boolean = false, val treeWorkInFlight: Boolean = false)

@Serializable
data class JankBatch(val episodes: List<JankEpisode>)

/** UI labels only; never card identities, chat contents, credentials or intent payloads. */
@Serializable
data class JankEpisode(val startedAt: String, val durationMs: Double, val frameCount: Int,
    val jankyCount: Int, val worstFrameMs: Double, val p50FrameMs: Double, val p95FrameMs: Double,
    val refreshRate: Float, val states: Map<String, String>, val recentActions: List<String>,
    val localApplyInFlight: Boolean, val treeWorkInFlight: Boolean, val frozen: Boolean)

@Serializable
data class ReconnectStep(val stage: String, val elapsedMs: Long, val durationMs: Long)

/** No credentials, message bodies or exception messages. Server handling times live in server logs. */
@Serializable
data class ReconnectEpisode(val id: String, val gameId: String, val startedAt: String, val cause: String,
    val steps: List<ReconnectStep> = emptyList(), val durationMs: Long = 0, val complete: Boolean = false)

/** Stable diagnostic labels without action payloads (chat text, card data, etc.). */
fun GameAction.latencyType(): String = if (this is GameAction.Play) "Play.${action::class.simpleName}"
    else this::class.simpleName ?: "Unknown"

/** Player-sent diagnostics; accepted without an account so it also works from the update gate. */
@Serializable
data class ProblemReport(val reportedAt: String, val appVersion: String, val versionCode: Int, val buildType: String,
    val device: String, val android: String, val description: String = "", val problem: String = "",
    val log: String = "", val logcat: String = "") {
    companion object {
        const val MAX_BYTES = 524_288
        const val MAX_DESCRIPTION = 2_000
        const val MAX_PROBLEM = 1_000
    }
}

@Serializable
data class ProblemReportReceipt(val code: String)

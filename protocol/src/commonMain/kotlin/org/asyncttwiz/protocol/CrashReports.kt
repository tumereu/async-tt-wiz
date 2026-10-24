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
data class LatencyBatch(val actions: List<ActionLatency>, val reconnects: List<ReconnectEpisode> = emptyList())

@Serializable
data class ReconnectStep(val stage: String, val elapsedMs: Long, val durationMs: Long)

/** No credentials, message bodies or exception messages. Server handling times live in server logs. */
@Serializable
data class ReconnectEpisode(val id: String, val gameId: String, val startedAt: String, val cause: String,
    val steps: List<ReconnectStep> = emptyList(), val durationMs: Long = 0, val complete: Boolean = false)

/** Stable diagnostic labels without action payloads (chat text, card data, etc.). */
fun GameAction.latencyType(): String = if (this is GameAction.Play) "Play.${action::class.simpleName}"
    else this::class.simpleName ?: "Unknown"

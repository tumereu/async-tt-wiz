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
data class LatencyBatch(val actions: List<ActionLatency>)

/** Stable diagnostic labels without action payloads (chat text, card data, etc.). */
fun GameAction.latencyType(): String = if (this is GameAction.Play) "Play.${action::class.simpleName}"
    else this::class.simpleName ?: "Unknown"

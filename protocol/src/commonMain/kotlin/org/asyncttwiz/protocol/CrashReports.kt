package org.asyncttwiz.protocol

import kotlinx.serialization.Serializable

/** Debug diagnostics only; never part of game state or history. */
@Serializable
data class CrashReport(val reportedAt: String, val appVersion: String, val device: String, val body: String)

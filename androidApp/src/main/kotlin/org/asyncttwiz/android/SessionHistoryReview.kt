package org.asyncttwiz.android

/** Adapter for an ALREADY authorized ActivityTarget.GROUP (never a raw push hint). Group metadata
 * labels the captured target; the exact boundary alone selects review, including parent fallback. */
data class CapturedHistoryTarget(val accountId: String, val gameId: String, val playId: String,
    val groupId: String?, val boundaryNodeId: String)
data class CapturedHistoryReview(val target: CapturedHistoryTarget, val view: DownloadedHistoryState)

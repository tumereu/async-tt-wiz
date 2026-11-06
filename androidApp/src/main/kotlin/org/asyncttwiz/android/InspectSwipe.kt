package org.asyncttwiz.android

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlin.math.abs

/**
 * Pinned Inspect (TBL-104, owner 2026-11-06): while a finger holds a card's Inspect, a drag to the left ([moved]) slides
 * it into its pinned look by [progress] (0 to 1); lifting the finger past half way ([released]) keeps it open, [pinned],
 * until [close]. Once the finger has scrolled the view up or down, sideways movement never starts the swipe, and a swipe
 * once started takes every movement (nothing scrolls). Distances are in the caller's units (dp).
 */
@Stable
internal class InspectSwipe {
    /** What closing the pinned Inspect does (its host's close). */
    var onClose: () -> Unit = {}
    var progress by mutableFloatStateOf(0f); private set
    var pinned by mutableStateOf(false); private set
    private var scrolled = false
    private var swiping = false

    /** A new hold begins: nothing swiped, not pinned. */
    fun begin() {
        progress = 0f; pinned = false; scrolled = false; swiping = false
    }

    /**
     * The holding finger is [dx], [dy] from where the hold began. True when the movement is the swipe's (the caller doesn't
     * scroll with it); false when it is the scroll's.
     */
    fun moved(dx: Float, dy: Float): Boolean {
        if (pinned) return true
        if (!swiping && !scrolled) when {
            abs(dy) > SLOP && abs(dy) >= abs(dx) -> scrolled = true
            -dx > SLOP && abs(dx) > abs(dy) -> swiping = true
        }
        if (!swiping) return false
        progress = ((-dx - SLOP) / DISTANCE).coerceIn(0f, 1f)
        return true
    }

    /** The finger lifted: true when the Inspect stays open pinned (swiped past half way), else it closes. */
    fun released(): Boolean {
        if (pinned) return true
        if (swiping && progress >= .5f) { pinned = true; progress = 1f; return true }
        progress = 0f; swiping = false
        return false
    }

    /** Closes a pinned Inspect. */
    fun close() {
        begin()
        onClose()
    }

    companion object {
        /** How far the finger moves before the swipe or the scroll takes it. */
        const val SLOP = 12f
        /** How far left the finger drags the swipe from start to end. */
        const val DISTANCE = 120f
    }
}

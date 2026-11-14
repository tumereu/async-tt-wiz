package org.asyncttwiz.android

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameMillis
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** How finely [ViewPause.wait] counts the time left. */
private const val VIEW_PAUSE_SLICE_MILLIS = 20L

/**
 * HIS-021, HIS-094, HIS-112, HIS-113 (owner, 2026-11-08): a finger held on the table, or an Inspect open, pauses whatever
 * the view is waiting on: a play shown, a combat's hold, playback between events. It goes on with the time left. Only the
 * view waits: nothing of it is sent, and what waits holds the device's own steps as it always does. Main thread.
 */
@Stable
class ViewPause {
    /** A finger held still on the table (not a pan or a drag), or on a shown play. */
    var finger by mutableStateOf(false)
    /** WEB-024 (owner, decision 31): the desktop's finger key (Alt) held, anywhere: the desktop's finger down. */
    var key by mutableStateOf(false)
    /** Whether an Inspect is open, held or pinned (TBL-104, TBL-131); read as snapshot state. */
    var inspecting: () -> Boolean = { false }

    val paused: Boolean get() = finger || key || inspecting()

    private var showTap: Job? = null

    /** HIS-112: a tap on a shown play, in [scope] (which outlives it): whether it is a double tap's second. */
    fun tapOnShow(scope: CoroutineScope): Boolean {
        val second = showTap?.isActive == true
        showTap?.cancel()
        showTap = scope.launch { delay(DOUBLE_TAP_MILLIS) }
        return second
    }

    /** HIS-112: whether a tap on a shown play was made just now: a tap on the table then belongs to it (the play may have
     * gone from under the finger) and does nothing there. */
    val justTappedShow: Boolean get() = showTap?.isActive == true

    /** Returns once nothing pauses the view. */
    suspend fun lifted() { if (paused) snapshotFlow { paused }.first { !it } }

    /**
     * Waits [millis] of time the view isn't paused, ending only when it isn't. The time is the frames' clock (owner
     * playtest 2026-11-11: counting the slices waited for made a play shown last longer than its time while the main
     * thread was busy, each slice resuming late), so a busy main thread doesn't lengthen it.
     */
    suspend fun wait(millis: Long) {
        if (millis <= 0) { lifted(); return }
        var left = millis
        var last = withFrameMillis { it }
        while (true) {
            if (paused) { lifted(); last = withFrameMillis { it } }
            if (left <= 0) return
            delay(minOf(left, VIEW_PAUSE_SLICE_MILLIS))
            val now = withFrameMillis { it }
            left -= now - last
            last = now
        }
    }
}

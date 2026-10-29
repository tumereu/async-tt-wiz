package org.asyncttwiz.android

import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.delay
import org.asyncttwiz.protocol.OpponentPresence
import org.asyncttwiz.protocol.PresenceState
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/** The opponent's presence as last heard from the server, with the local monotonic time it arrived.
 *  Ages keep counting locally from [receivedAt]; the server's clock never matters. */
data class ReceivedPresence(val presence: OpponentPresence, val receivedAt: Long)

enum class PresenceDot(val color: Color) {
    ONLINE(Color(0xff43c463)), IDLE(Color(0xfff2c94c)), OFFLINE(Color(0xffaab3b6))
}

/** Dot colour and info line for the opponent's life square. Personal display only. */
object PresenceText {
    const val IDLE_MILLIS = 120_000L

    private fun elapsed(received: ReceivedPresence, now: Long) = (now - received.receivedAt).coerceAtLeast(0)
    private fun idleMillis(received: ReceivedPresence, now: Long) =
        received.presence.idleSeconds?.let { it * 1000L + elapsed(received, now) }

    /** Online turns idle here once two minutes pass without input; offline only ever comes from the server. */
    fun dot(received: ReceivedPresence, now: Long): PresenceDot = when (received.presence.state) {
        PresenceState.OFFLINE -> PresenceDot.OFFLINE
        PresenceState.IDLE -> PresenceDot.IDLE
        PresenceState.ONLINE -> if ((idleMillis(received, now) ?: 0) >= IDLE_MILLIS) PresenceDot.IDLE else PresenceDot.ONLINE
    }

    /** Milliseconds until [dot] changes by itself, or null when it only changes with news from the server. */
    fun untilDotChange(received: ReceivedPresence, now: Long): Long? {
        if (received.presence.state != PresenceState.ONLINE) return null
        val idle = idleMillis(received, now) ?: return null
        return (IDLE_MILLIS - idle).takeIf { it > 0 }
    }

    /** "Online", "Idle 7 min", "Last seen 12 min ago", "Last seen 3 h ago", "Last seen Oct 27" or "Not seen yet". */
    fun line(received: ReceivedPresence, now: Long, wallNow: ZonedDateTime): String {
        val presence = received.presence
        return when (dot(received, now)) {
            PresenceDot.ONLINE -> "Online"
            PresenceDot.IDLE -> "Idle ${span(((idleMillis(received, now) ?: 0) / 1000).coerceAtLeast(60))}"
            PresenceDot.OFFLINE -> {
                val seen = presence.lastSeenSeconds ?: return "Not seen yet"
                val seconds = seen + elapsed(received, now) / 1000
                when {
                    seconds < 60 -> "Last seen just now"
                    seconds < 86_400 -> "Last seen ${span(seconds)} ago"
                    else -> {
                        val day = wallNow.minusSeconds(seconds)
                        val pattern = if (day.year == wallNow.year) "MMM d" else "MMM d, yyyy"
                        "Last seen ${day.format(DateTimeFormatter.ofPattern(pattern, Locale.ENGLISH))}"
                    }
                }
            }
        }
    }

    /** True when [next] tells nothing [old] does not already predict (same state, ages within a few seconds),
     *  so routine heads every 30 s do not redraw the table. */
    fun same(old: ReceivedPresence, next: ReceivedPresence): Boolean {
        if (old.presence.state != next.presence.state) return false
        val drift = elapsed(old, next.receivedAt) / 1000
        fun close(before: Int?, after: Int?) = before == null && after == null ||
            before != null && after != null && kotlin.math.abs(before + drift - after) <= 5
        // Last seen only shows while offline; an open game keeps it near zero.
        return if (next.presence.state == PresenceState.OFFLINE) close(old.presence.lastSeenSeconds, next.presence.lastSeenSeconds)
            else close(old.presence.idleSeconds, next.presence.idleSeconds)
    }

    private fun span(seconds: Long) = if (seconds < 3600) "${seconds / 60} min" else "${seconds / 3600} h"
}

/** The dot for the current moment; recomposes once when an online opponent turns idle. */
@Composable
internal fun rememberPresenceDot(received: ReceivedPresence?): PresenceDot? {
    if (received == null) return null
    // Reading tick (as the effect key) recomposes this once the wait is over.
    var tick by remember(received) { mutableIntStateOf(0) }
    val now = monotonicMillis()
    val wait = PresenceText.untilDotChange(received, now)
    LaunchedEffect(received, tick) { if (wait != null) { delay(wait); tick++ } }
    return PresenceText.dot(received, now)
}

/** The info line, refreshed every 20 s while shown. */
@Composable
internal fun rememberPresenceLine(received: ReceivedPresence): String {
    var now by remember { mutableLongStateOf(monotonicMillis()) }
    LaunchedEffect(received) { while (true) { now = monotonicMillis(); delay(20_000) } }
    return PresenceText.line(received, maxOf(now, received.receivedAt), ZonedDateTime.now())
}

/** The opponent's presence for the table's player panels; null hides the line (unknown or old server). */
internal val LocalOpponentPresence = compositionLocalOf<ReceivedPresence?> { null }

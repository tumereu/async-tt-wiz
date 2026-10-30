package org.asyncttwiz.android

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FastRewind
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

internal val TimelineFaintInk = Color(0xff253d44).copy(alpha = .38f)
internal val OpponentBranchedColor = Color(0xffee8a2e)
internal const val PLAYBACK_FLASH_HOLD_MILLIS = 300L

internal fun TimelinePlaybackStatus.icon(): ImageVector = when (this) {
    TimelinePlaybackStatus.PLAYING -> Icons.Outlined.PlayArrow
    TimelinePlaybackStatus.PAUSED -> Icons.Outlined.Pause
    TimelinePlaybackStatus.REWINDING -> Icons.Outlined.FastRewind
}
internal fun TimelinePlaybackStatus.label() = when (this) {
    TimelinePlaybackStatus.PLAYING -> "Playing"
    TimelinePlaybackStatus.PAUSED -> "Paused"
    TimelinePlaybackStatus.REWINDING -> "Rewinding"
}

/** The board centre while in timeline mode. Purely visual: no pointer handlers. */
@Composable
internal fun TimelineCentreText(opponentBranched: Boolean, status: TimelinePlaybackStatus) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (opponentBranched) Row(Modifier.background(OpponentBranchedColor, RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            CompositionLocalProvider(LocalContentColor provides Color(0xff2b1700)) {
                TableChromeIcon("history")
                Text("Opponent branched", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium,
                    color = LocalContentColor.current)
            }
        } else Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            CompositionLocalProvider(LocalContentColor provides TimelineFaintInk) {
                TableChromeIcon("history")
                Text("Timeline mode", style = MaterialTheme.typography.titleMedium, color = LocalContentColor.current)
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Icon(status.icon(), null, Modifier.size(20.dp), tint = TimelineFaintInk)
            Text(status.label(), style = MaterialTheme.typography.titleSmall, color = TimelineFaintInk)
        }
    }
}

/** What the corner shows for a change of [TimelinePlaybackStatus] (null: not in timeline mode).
 * A rewind stays shown; play/pause only flash; entering or leaving while paused shows nothing. */
internal fun playbackFlashFor(before: TimelinePlaybackStatus?, now: TimelinePlaybackStatus?): TimelinePlaybackStatus? = when (now) {
    before -> null
    TimelinePlaybackStatus.REWINDING, TimelinePlaybackStatus.PLAYING -> now
    TimelinePlaybackStatus.PAUSED, null ->
        if (before == TimelinePlaybackStatus.PLAYING || before == TimelinePlaybackStatus.REWINDING) TimelinePlaybackStatus.PAUSED else null
}

/** A large play/pause icon flashed in the board's top-right corner whenever playback starts or stops
 * (manual, auto-pause, end of playback); a running rewind shows constantly. Purely visual. */
@Composable
internal fun PlaybackStatusFlash(status: TimelinePlaybackStatus?, modifier: Modifier = Modifier) {
    var before by remember { mutableStateOf(status) }
    var shown by remember { mutableStateOf<TimelinePlaybackStatus?>(null) }
    LaunchedEffect(status) {
        val flash = playbackFlashFor(before, status)
        before = status
        shown = flash
        if (flash != null && flash != TimelinePlaybackStatus.REWINDING) {
            delay(PLAYBACK_FLASH_HOLD_MILLIS)
            shown = null
        }
    }
    Box(modifier, contentAlignment = Alignment.TopEnd) {
        InstantShowFadeOut(shown, Modifier.padding(8.dp)) { flash ->
            Icon(flash.icon(), null, Modifier.size(56.dp).testTag("playback-flash")
                .semantics { contentDescription = flash.label() }, tint = Color(0xff253d44).copy(alpha = .85f))
        }
    }
}

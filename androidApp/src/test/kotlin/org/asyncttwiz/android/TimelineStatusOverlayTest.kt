package org.asyncttwiz.android

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import org.asyncttwiz.android.TimelinePlaybackStatus.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.*

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w412dp-h892dp-port-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class TimelineStatusOverlayTest {
    @get:Rule val compose = createComposeRule()

    // HIS-009
    @Test fun cornerFlashesOnlyActualPlayPauseChangesAndHoldsARewind() {
        assertNull(playbackFlashFor(PAUSED, PAUSED))
        assertEquals(PLAYING, playbackFlashFor(PAUSED, PLAYING))
        assertEquals(PAUSED, playbackFlashFor(PLAYING, PAUSED)) // Manual or auto-pause.
        assertEquals(PAUSED, playbackFlashFor(PLAYING, null)) // Playback reached the live end.
        assertEquals(REWINDING, playbackFlashFor(PAUSED, REWINDING))
        assertEquals(PLAYING, playbackFlashFor(REWINDING, PLAYING))
        assertNull(playbackFlashFor(null, PAUSED)) // Entering timeline mode paused.
        assertNull(playbackFlashFor(PAUSED, null)) // Leaving while paused.
    }

    // HIS-009
    @Test fun flashShowsAtOnceThenFadesWhileARewindStaysUntilItEnds() {
        var status by mutableStateOf<TimelinePlaybackStatus?>(PAUSED)
        compose.mainClock.autoAdvance = false
        compose.setContent { MaterialTheme { PlaybackStatusFlash(status, Modifier.fillMaxSize()) } }
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithTag("playback-flash").assertDoesNotExist()
        compose.runOnIdle { status = PLAYING; androidx.compose.runtime.snapshots.Snapshot.sendApplyNotifications() }
        compose.mainClock.advanceTimeByFrame(); compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithTag("playback-flash").assertContentDescriptionEquals("Playing")
        compose.mainClock.advanceTimeBy(PLAYBACK_FLASH_HOLD_MILLIS + EVENT_TEXT_FADE_OUT_MILLIS + 100)
        compose.onNodeWithTag("playback-flash").assertDoesNotExist()
        compose.runOnIdle { status = PAUSED; androidx.compose.runtime.snapshots.Snapshot.sendApplyNotifications() }
        compose.mainClock.advanceTimeByFrame(); compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithTag("playback-flash").assertContentDescriptionEquals("Paused")
        compose.runOnIdle { status = REWINDING; androidx.compose.runtime.snapshots.Snapshot.sendApplyNotifications() }
        compose.mainClock.advanceTimeByFrame(); compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithTag("playback-flash").assertContentDescriptionEquals("Rewinding")
        compose.mainClock.advanceTimeBy(5_000)
        compose.onNodeWithTag("playback-flash").assertContentDescriptionEquals("Rewinding")
        compose.runOnIdle { status = PLAYING; androidx.compose.runtime.snapshots.Snapshot.sendApplyNotifications() }
        compose.mainClock.advanceTimeByFrame(); compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithTag("playback-flash").assertContentDescriptionEquals("Playing")
        compose.runOnIdle { status = null; androidx.compose.runtime.snapshots.Snapshot.sendApplyNotifications() } // Playback ended back at live.
        compose.mainClock.advanceTimeByFrame(); compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithTag("playback-flash").assertContentDescriptionEquals("Paused")
        compose.mainClock.advanceTimeBy(PLAYBACK_FLASH_HOLD_MILLIS + EVENT_TEXT_FADE_OUT_MILLIS + 100)
        compose.onNodeWithTag("playback-flash").assertDoesNotExist()
    }

    // HIS-040
    @Test fun oldBranchShowsANeutralGreyChipAboveTheUsualStatus() {
        var opponent by mutableStateOf(false)
        compose.setContent { MaterialTheme { TimelineCentreText(opponent, PAUSED, oldBranch = true) } }
        compose.onNodeWithTag("timeline-branch-chip").assertIsDisplayed()
        compose.onNodeWithText("Old branch").assertIsDisplayed()
        compose.onNodeWithText("Timeline mode").assertDoesNotExist()
        compose.onNodeWithText("Paused").assertIsDisplayed()
        // Same chip as the orange "Opponent branched", in a neutral grey.
        val old = assertNotNull(timelineBranchChip(opponentBranched = false, oldBranch = true))
        assertEquals("Old branch", old.label)
        assertEquals(OldBranchColor, old.background)
        assertEquals(old.background.red, old.background.green, .05f); assertEquals(old.background.green, old.background.blue, .05f)
        assertEquals(OpponentBranchedColor, timelineBranchChip(opponentBranched = true, oldBranch = true)?.background)
        assertNull(timelineBranchChip(opponentBranched = false, oldBranch = false))
        compose.runOnIdle { opponent = true } // A pending opponent branch wins.
        compose.onNodeWithText("Opponent branched").assertIsDisplayed()
        compose.onNodeWithText("Old branch").assertDoesNotExist()
    }
}

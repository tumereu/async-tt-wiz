package org.asyncttwiz.android

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import org.asyncttwiz.protocol.PlayAction
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.*

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w412dp-h892dp-port-mdpi")
class TimelineNavigationRegressionTest {
    @get:Rule val compose = createComposeRule()

    private fun drain(f: BasicTableFixture) {
        repeat(4) { compose.runOnIdle { f.worker.drain() }; compose.waitForIdle() }
    }

    private fun review(f: BasicTableFixture): String {
        f.connect()
        assertTrue(f.lobby.gameplay(PlayAction.AdjustLife(f.actor, "1")))
        HistoryPlaybackPreferences(ApplicationProvider.getApplicationContext(), f.actor).shown(f.game.id, f.play.currentNodeId)
        compose.setContent { MaterialTheme { TableScreen(f.lobby, f.actor, {}, false) } }
        drain(f)
        val root = f.root.currentNodeId
        compose.runOnIdle { f.lobby.history.select(root) }
        drain(f)
        return root
    }

    // HIS-019, HIS-013 (I-D56 §8): the timeline is view-only: holding Auto at a past point arms nothing and sends nothing
    @Test fun holdingAutoAtAPastPointDoesNotBranch() {
        val f = BasicTableFixture()
        try {
            val root = review(f)
            val sent = f.commands().size
            compose.onNodeWithContentDescription("Continue playing from here").assertDoesNotExist()
            compose.onNodeWithText("Hold to branch", useUnmergedTree = true).assertDoesNotExist()
            compose.onNodeWithContentDescription("Auto advance").performTouchInput { longClick() }
            drain(f)
            compose.runOnIdle {
                assertEquals(null, f.lobby.history.armed)
                assertEquals(sent, f.commands().size)
            }
            compose.onNodeWithContentDescription("Cancel continuation").assertDoesNotExist()
            assertNotEquals(null, root)
        } finally { f.lobby.close(); f.worker.drain() }
    }

    // HIS-018, HIS-019
    @Test fun returnToCurrentStartsCatchupWithoutArmingOrOfferingContinuation() {
        val f = BasicTableFixture()
        try {
            review(f)
            val sent = f.commands().size
            compose.onNodeWithContentDescription("Return to current").performClick()
            compose.onNode(isDialog()).assertDoesNotExist()
            compose.runOnIdle {
                assertNull(f.lobby.history.armed)
                val position = f.lobby.history.playback!!.position
                assertTrue(position.playing || position.mode == HistoryMode.LIVE)
                assertEquals(sent, f.commands().size)
            }
        } finally { f.lobby.close(); f.worker.drain() }
    }
}

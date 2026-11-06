package org.asyncttwiz.android

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.*

/** Timeline mode on a game played on engine state (R4b: the engine table, [EngineLobbyFixture]). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w412dp-h892dp-port-mdpi")
class TimelineNavigationRegressionTest {
    @get:Rule val compose = createComposeRule()

    private fun drain(f: EngineLobbyFixture) {
        repeat(4) { compose.runOnIdle { f.worker.drain() }; compose.waitForIdle() }
    }

    /** Opens the table and enters timeline mode from Table actions → Timeline (HIS-004). */
    private fun review(f: EngineLobbyFixture) {
        f.connect()
        f.keep()
        compose.setContent { MaterialTheme { TableScreen(f.lobby, f.me, {}, false) } }
        drain(f)
        compose.onNodeWithContentDescription("Table actions").performClick()
        compose.onNodeWithText("Timeline").assertIsEnabled().performClick()
        drain(f)
        compose.onNodeWithContentDescription("Return to current").assertExists()
    }

    // HIS-019, HIS-013 (I-D56 §8): the timeline is view-only: holding Auto arms nothing and sends nothing
    @Test fun holdingAutoAtAPastPointDoesNotBranch() {
        val f = EngineLobbyFixture()
        try {
            review(f)
            val sent = f.commands().size
            compose.onNodeWithContentDescription("Continue playing from here").assertDoesNotExist()
            compose.onNodeWithText("Hold to branch", useUnmergedTree = true).assertDoesNotExist()
            compose.onNodeWithContentDescription("Auto advance").performTouchInput { longClick() }
            drain(f)
            compose.runOnIdle { assertEquals(sent, f.commands().size) }
            compose.onNodeWithContentDescription("Cancel continuation").assertDoesNotExist()
            compose.onNode(isDialog()).assertDoesNotExist()
        } finally { f.close() }
    }

    // HIS-018, HIS-019: Return to current leaves timeline mode for the live game, asking nothing and sending nothing
    @Test fun returnToCurrentLeavesTimelineModeWithoutAskingOrSending() {
        val f = EngineLobbyFixture()
        try {
            review(f)
            val sent = f.commands().size
            compose.onNodeWithContentDescription("Return to current").performClick()
            drain(f)
            compose.onNode(isDialog()).assertDoesNotExist()
            compose.onNodeWithContentDescription("Return to current").assertDoesNotExist()
            compose.runOnIdle { assertEquals(sent, f.commands().size) }
        } finally { f.close() }
    }
}

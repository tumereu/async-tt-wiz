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

    @Test fun leftForkButtonArmsSelectedPastWithoutMutatingAndKeepsStripMounted() {
        val f = BasicTableFixture()
        try {
            val root = review(f)
            val player = f.lobby.history.playback!!
            val description = HistoryTimelineLayout.events(player.tree, player.tree.sharedHeadId).first().description
            val event = hasContentDescription(description) and isSelected()
            val before = compose.onNode(event, useUnmergedTree = true).assertIsDisplayed().fetchSemanticsNode().boundsInRoot
            val sent = f.commands().size
            val fork = compose.onNodeWithContentDescription("Continue playing from here")
            val current = compose.onNodeWithContentDescription("Return to current")
            assertTrue(fork.fetchSemanticsNode().boundsInRoot.left < current.fetchSemanticsNode().boundsInRoot.left)
            fork.performClick()
            drain(f)
            compose.runOnIdle {
                assertEquals(root, f.lobby.history.armed)
                assertEquals(root, player.position.cursorId)
                assertNotEquals(root, player.tree.sharedHeadId)
                assertEquals(sent, f.commands().size)
            }
            compose.onNodeWithContentDescription("Cancel continuation").assertIsDisplayed()
            compose.onNodeWithContentDescription("Auto advance").assertDoesNotExist()
            assertEquals(before, compose.onNode(event, useUnmergedTree = true).assertIsDisplayed().fetchSemanticsNode().boundsInRoot)
            // The first predicted fork lands before the worker can publish its history index.
            compose.runOnIdle { assertTrue(f.lobby.gameplay(PlayAction.AdjustLife(f.actor, "2"))) }
            compose.waitForIdle()
            compose.onNode(event, useUnmergedTree = true).assertIsDisplayed()
            drain(f)
            compose.runOnIdle {
                assertEquals(f.play.currentNodeId, f.lobby.history.armed)
                assertTrue(player.tree.contains(f.play.currentNodeId))
            }
            compose.onNode(isSelected(), useUnmergedTree = true).assertIsDisplayed()
            compose.onNodeWithContentDescription("Cancel continuation").performClick()
            drain(f)
            compose.onNodeWithContentDescription("Timeline mode").assertIsDisplayed()
        } finally { f.lobby.close(); f.worker.drain() }
    }

    @Test fun returnToCurrentStartsCatchupWithoutArmingOrOfferingContinuation() {
        val f = BasicTableFixture()
        try {
            review(f)
            val sent = f.commands().size
            compose.onNodeWithContentDescription("Return to current").performClick()
            compose.onNode(isDialog()).assertDoesNotExist()
            compose.runOnIdle {
                assertNull(f.lobby.history.armed)
                assertNull(f.lobby.history.continuationTarget)
                val position = f.lobby.history.playback!!.position
                assertTrue(position.playing || position.mode == HistoryMode.LIVE)
                assertEquals(sent, f.commands().size)
            }
        } finally { f.lobby.close(); f.worker.drain() }
    }
}

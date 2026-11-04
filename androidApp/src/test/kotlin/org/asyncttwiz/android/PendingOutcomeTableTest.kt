package org.asyncttwiz.android

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import arcanum.engine.setup.GameResult
import arcanum.engine.state.PlayerId
import org.asyncttwiz.protocol.*
import org.asyncttwiz.protocol.game.ResultSuggestion
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.*

/** A pending result suggestion on the table of a game played on engine state (R4b: [EngineLobbyFixture]). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w412dp-h892dp-port-mdpi")
class PendingOutcomeTableTest {
    @get:Rule val compose = createComposeRule()

    private fun drain(f: EngineLobbyFixture) {
        repeat(4) { compose.runOnIdle { f.worker.drain() }; compose.waitForIdle() }
    }

    private fun pendingTable(opponent: Boolean) {
        val f = EngineLobbyFixture()
        try {
            val by = if (opponent) f.other else f.me
            f.connect(f.head { it.copy(suggestion = ResultSuggestion(PlayerId(by), GameResult.Draw)) })
            compose.setContent { MaterialTheme { TableScreen(f.lobby, f.me, {}, false) } }
            drain(f)
            val resolve = if (opponent) "Decline result" else "Withdraw result"
            compose.onNodeWithContentDescription(resolve).assertIsDisplayed().assertIsEnabled()
            compose.onAllNodes(isDialog()).assertCountEquals(0)
            // TRN-049: every other game action is blocked while the suggestion waits (at the opening the step arrow's place
            // is the opening's quick actions)
            val play = assertNotNull(f.lobby.state.board)
            for (label in listOf("Table actions", "Undo")) {
                compose.onNodeWithContentDescription(label).assertIsNotEnabled().performClick()
            }
            compose.onNodeWithContentDescription("Choose phase/step", substring = true).assertIsNotEnabled().performClick()
            val scene = TablePresentation.scene(f.game, play, f.me, 412f, 892f, false)
            val hand = scene.items.first { it.kind == TableItemKind.CARD && it.zone == PlayZone.HAND && it.owner == f.me }.visible.first().center
            // The viewer's half of the battlefield, below the middle of the screen
            val field = TablePoint(206f, 560f)
            compose.onRoot().performTouchInput {
                doubleClick(Offset(hand.x, hand.y))
                swipe(Offset(hand.x, hand.y), Offset(field.x, field.y))
            }
            drain(f)
            compose.onAllNodes(isDialog()).assertCountEquals(0)
            assertTrue(f.commands().isEmpty())
            // Chat still works
            compose.onNodeWithContentDescription("Chat, 0 unread messages").assertIsEnabled()
                .performSemanticsAction(SemanticsActions.OnClick) { assertTrue(it()) }
            compose.runOnIdle { assertTrue(f.lobby.state.chatOpen); f.lobby.closeChat() }
            drain(f)
            compose.onNodeWithContentDescription(resolve).performSemanticsAction(SemanticsActions.OnClick) { assertTrue(it()) }
            drain(f)
            compose.onNodeWithContentDescription(resolve).assertDoesNotExist()
            assertEquals(1, f.commands().size, "declining or withdrawing goes out as one action")
            assertNull(f.lobby.state.board?.board?.resultSuggestion)
        } finally { f.close() }
    }

    // HIS-020: a pending suggestion never blocks the timeline (the other player's keep is an event to look at)
    @Test fun aPendingSuggestionNeverBlocksTheTimeline() {
        val f = EngineLobbyFixture()
        try {
            f.connect(f.head(otherKept = true) { it.copy(suggestion = ResultSuggestion(PlayerId(f.other), GameResult.Draw)) })
            assertNotNull(f.lobby.engineGame()).playToStop(); f.lobby.engineSent(); f.worker.drain()
            compose.setContent { MaterialTheme { TableScreen(f.lobby, f.me, {}, false) } }
            drain(f)
            assertNotNull(f.lobby.state.board?.board?.resultSuggestion)
            compose.onAllNodesWithContentDescription("Timeline mode").onFirst().assertIsEnabled()
                .performSemanticsAction(SemanticsActions.OnClick) { assertTrue(it()) }
            drain(f)
            compose.onNodeWithContentDescription("Return to current").assertExists().performClick()
            drain(f)
            compose.onNodeWithContentDescription("Return to current").assertDoesNotExist()
            assertTrue(f.commands().isEmpty())
        } finally { f.close() }
    }

    // HIS-020, LOB-071, TRN-047, TRN-049 (on engine state, R4b)
    @Test fun authorCanWithdrawOnTableWhileOtherActionsAreLockedAndTimelineAndChatWork() = pendingTable(false)
    @Test fun opponentCanDeclineOnTableWhileOtherActionsAreLockedAndTimelineAndChatWork() = pendingTable(true)
}

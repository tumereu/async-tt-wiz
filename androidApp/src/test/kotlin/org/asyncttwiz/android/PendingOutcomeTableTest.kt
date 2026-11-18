package org.asyncttwiz.android

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
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
            compose.onNodeWithContentDescription("Undo").assertIsNotEnabled().performClick()
            // HIS-020, TRN-010, TRN-064 (owner, 2026-11-17: "concede and suggest game end should be always available to either
            // player"): Table actions still opens, its Timeline (aPendingSuggestionNeverBlocksTheTimeline enters it) and every
            // other entry usable
            compose.onNodeWithContentDescription("Table actions").assertIsEnabled().performClick()
            compose.onNodeWithText("Timeline").assertExists()
            for (entry in listOf("Suggest game end", "Concede", "Send problem report")) compose.onNodeWithText(entry).assertIsEnabled()
            androidx.test.espresso.Espresso.pressBack()
            drain(f)
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
            compose.onNodeWithContentDescription("Table actions").performClick()
            compose.onNodeWithText("Timeline").assertIsEnabled().performClick()
            drain(f)
            compose.onNodeWithContentDescription("Return to current").assertExists().performClick()
            drain(f)
            compose.onNodeWithContentDescription("Return to current").assertDoesNotExist()
            assertTrue(f.commands().isEmpty())
        } finally { f.close() }
    }

    /** Table actions' [entry], then its confirm dialog's [confirm] (a suggestion's chooser [choice] first, when given). */
    private fun tableAction(f: EngineLobbyFixture, entry: String, confirm: String, choice: String? = null) {
        compose.onNodeWithContentDescription("Table actions").performClick()
        compose.onNodeWithText(entry).assertIsEnabled().performClick()
        choice?.let { compose.onNodeWithText(it).performClick() }
        compose.onAllNodesWithText(confirm).filterToOne(hasClickAction() and hasAnyAncestor(isDialog())).performClick()
        drain(f)
    }

    // TRN-064, HIS-020, SYS-170, CR 104.3a (owner, 2026-11-17: "concede and suggest game end should be always available to either
    // player"): with the other player's suggestion waiting, Concede in Table actions ends the game as the viewer's loss
    @Test fun theOpponentConcedesWhileTheSuggestionWaits() {
        val f = EngineLobbyFixture()
        try {
            f.connect(f.head { it.copy(suggestion = ResultSuggestion(PlayerId(f.other), GameResult.Draw)) })
            compose.setContent { MaterialTheme { TableScreen(f.lobby, f.me, {}, false) } }
            drain(f)
            tableAction(f, "Concede", "Concede")
            assertEquals(1, f.commands().size, "the concession goes out: ${f.commands()}")
            assertEquals(GameResult.Won(PlayerId(f.other)), arcanum.engine.Engine.result(assertNotNull(f.lobby.engineGame()?.shown).engine))
        } finally { f.close() }
    }

    /** TRN-162: with a draw suggestion waiting (the other player's when [opponent], else the viewer's own), the viewer suggests their win from Table actions: it replaces it. */
    private fun suggestingAgain(opponent: Boolean) {
        val f = EngineLobbyFixture()
        try {
            f.connect(f.head { it.copy(suggestion = ResultSuggestion(PlayerId(if (opponent) f.other else f.me), GameResult.Draw)) })
            compose.setContent { MaterialTheme { TableScreen(f.lobby, f.me, {}, false) } }
            drain(f)
            tableAction(f, "Suggest game end", "Confirm", choice = "My win")
            assertEquals(1, f.commands().size, "the suggestion goes out: ${f.commands()}")
            val waiting = assertNotNull(f.lobby.engineGame()?.shown?.board?.engine).table.suggestion
            assertEquals(ResultSuggestion(PlayerId(f.me), GameResult.Won(PlayerId(f.me))), waiting)
        } finally { f.close() }
    }

    // TRN-162, TRN-010, HIS-020 (owner, 2026-11-17: "concede and suggest game end should be always available to either player"):
    // while a suggestion waits, a new one from Table actions replaces it, the author's own ...
    @Test fun theAuthorsNewSuggestionReplacesTheirWaitingOne() = suggestingAgain(opponent = false)
    // ... and the other player's
    @Test fun theOpponentsSuggestionReplacesTheWaitingOne() = suggestingAgain(opponent = true)

    // HIS-020, TRN-049, TRN-047 (on engine state, R4b)
    @Test fun authorCanWithdrawOnTableWhileOtherActionsAreLockedAndTimelineAndChatWork() = pendingTable(false)
    @Test fun opponentCanDeclineOnTableWhileOtherActionsAreLockedAndTimelineAndChatWork() = pendingTable(true)
}

package org.asyncttwiz.android

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import org.asyncttwiz.protocol.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.*

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w412dp-h892dp-port-mdpi")
class PendingOutcomeTableTest {
    @get:Rule val compose = createComposeRule()

    private fun drain(f: BasicTableFixture) {
        repeat(4) { compose.runOnIdle { f.worker.drain() }; compose.waitForIdle() }
    }

    private fun pendingTable(opponent: Boolean, continueBefore: Boolean = false) {
        val f = BasicTableFixture()
        val root = f.root.copy(board = f.root.board.copy(
            cards = f.root.board.cards.map { card -> when (card.id) {
                f.id(40) -> card.copy(containerId = f.container(PlayZone.BATTLEFIELD), covered = false,
                    placement = PlayPlacement(BoardPosition(0, 0), f.id(600)))
                f.id(41) -> card.copy(containerId = f.container(PlayZone.HAND), covered = false)
                else -> card
            } },
            containers = f.root.board.containers.map { container -> container.copy(cards = when (container.id) {
                f.container(PlayZone.BATTLEFIELD) -> listOf(f.id(40))
                f.container(PlayZone.HAND) -> listOf(f.id(41))
                f.container(PlayZone.LIBRARY) -> listOf(f.id(42), f.id(43))
                else -> emptyList()
            }) }))
        try {
            f.connect(root)
            val command = f.data.command(PlayAction.SuggestResult(SuggestedResult.DRAW))
            f.message(f.update(root, command, 1, if (opponent) f.other else f.actor))
            val head = f.play.currentNodeId
            HistoryPlaybackPreferences(ApplicationProvider.getApplicationContext(), f.actor).shown(f.game.id, head)
            compose.setContent { MaterialTheme { TableScreen(f.lobby, f.actor, {}, false) } }
            drain(f)
            val resolve = if (opponent) "Decline result" else "Withdraw result"
            compose.runOnIdle { assertTrue(f.lobby.history.playback!!.livePanelReveal) }
            compose.onNodeWithContentDescription(resolve).assertDoesNotExist()
            compose.mainClock.advanceTimeBy(1_000)
            drain(f)
            compose.onNodeWithContentDescription(resolve).assertIsDisplayed().assertIsEnabled()
            compose.onAllNodes(isDialog()).assertCountEquals(0)
            for (label in listOf("Table actions", "Interaction mode", "Undo", nextStepLabel(f.play.board.turn))) {
                compose.onNodeWithContentDescription(label).assertIsNotEnabled().performClick()
            }
            compose.onNodeWithContentDescription("Choose phase/step", substring = true).assertIsNotEnabled().performClick()
            val scene = TablePresentation.scene(f.game, f.play, f.actor, 412f, 892f, false)
            val field = scene.items.single { it.cardId == f.id(40) }.visible.first().center
            val hand = scene.items.single { it.cardId == f.id(41) }.visible.first().center
            compose.onRoot().performTouchInput {
                doubleClick(Offset(field.x, field.y))
                swipe(Offset(hand.x, hand.y), Offset(field.x, field.y))
            }
            compose.onAllNodes(isDialog()).assertCountEquals(0)
            assertTrue(f.commands().isEmpty())
            compose.onNodeWithContentDescription("Timeline mode").assertIsEnabled()
                .performSemanticsAction(SemanticsActions.OnClick) { assertTrue(it()) }
            drain(f)
            assertEquals(HistoryMode.PLAYBACK, f.lobby.history.playback!!.position.mode)
            compose.onNodeWithContentDescription(resolve).assertIsDisplayed().assertIsEnabled()
            compose.onNodeWithContentDescription("Previous").assertIsEnabled()
                .performSemanticsAction(SemanticsActions.OnClick) { assertTrue(it()) }
            drain(f)
            compose.onNodeWithContentDescription(resolve).assertDoesNotExist()
            // A point before the suggestion is not locked: the player may continue from it and play on.
            compose.onNodeWithContentDescription("Continue playing from here").assertIsEnabled()
            if (continueBefore) {
                val before = f.lobby.history.playback!!.position.cursorId
                compose.onNodeWithContentDescription("Continue playing from here")
                    .performSemanticsAction(SemanticsActions.OnClick) { assertTrue(it()) }
                drain(f)
                compose.runOnIdle { assertEquals(before, f.lobby.history.armed); assertFalse(f.lobby.resultLocked()) }
                compose.onNodeWithContentDescription("Table actions").assertIsEnabled()
                compose.onRoot().performTouchInput { swipe(Offset(hand.x, hand.y), Offset(field.x, field.y)) }
                drain(f)
                val sent = f.commands().single()
                assertEquals(Continuation(before, true), sent.continuation)
                assertIs<GameAction.Play>(sent.action)
                compose.runOnIdle { assertNull(f.lobby.state.play!!.board.resultSuggestion) }
                return
            }
            compose.onNodeWithContentDescription("Next").assertIsEnabled()
                .performSemanticsAction(SemanticsActions.OnClick) { assertTrue(it()) }
            drain(f)
            compose.onNodeWithContentDescription(resolve).assertIsDisplayed().assertIsEnabled()
            compose.onNodeWithContentDescription("Chat, 0 unread messages").assertIsEnabled()
                .performSemanticsAction(SemanticsActions.OnClick) { assertTrue(it()) }
            compose.runOnIdle { assertTrue(f.lobby.state.chatOpen); f.lobby.closeChat() }
            drain(f)
            compose.onNodeWithContentDescription(resolve).performSemanticsAction(SemanticsActions.OnClick) { assertTrue(it()) }
            drain(f)
            compose.onNodeWithContentDescription(resolve).assertDoesNotExist()
            val action = f.commands().single().action
            if (opponent) assertIs<PlayAction.DeclineResult>(assertIs<GameAction.Play>(action).action)
            else assertIs<PlayAction.WithdrawResult>(assertIs<GameAction.Play>(action).action)
        } finally { f.lobby.close(); f.worker.drain() }
    }

    @Test fun authorCanWithdrawOnTableWhileOtherActionsAreLockedAndTimelineAndChatWork() = pendingTable(false)
    @Test fun opponentCanDeclineOnTableWhileOtherActionsAreLockedAndTimelineAndChatWork() = pendingTable(true)
    @Test fun continuingFromBeforeTheSuggestionUnlocksTheTableOnTheNewBranch() = pendingTable(true, continueBefore = true)
}

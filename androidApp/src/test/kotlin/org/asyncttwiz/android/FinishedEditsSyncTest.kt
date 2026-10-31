package org.asyncttwiz.android

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import org.asyncttwiz.protocol.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.*

/** The production table (TableScreen -> LobbyController -> GameSession) with a recording connection. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w412dp-h892dp-port-mdpi")
class FinishedEditsSyncTest {
    @get:Rule val compose = createComposeRule()

    private fun drain(f: BasicTableFixture) {
        repeat(4) { compose.runOnIdle { f.worker.drain() }; compose.waitForIdle() }
    }

    private fun assertNoSendStepOrConfirmation() {
        compose.onAllNodes(isDialog()).assertCountEquals(0)
        compose.onAllNodes(hasText("Send", ignoreCase = true) or hasContentDescription("Send", ignoreCase = true)).assertCountEquals(0)
    }

    // SYS-003
    @Test fun finishedNoteValueAndMoveEachSendOneRequestAtOnceWithoutSendButtonOrConfirmation() {
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
        fun sent() = f.commands().map { (it.action as GameAction.Play).action }
        fun center(id: Int): Offset {
            val scene = TablePresentation.scene(f.game, f.play, f.actor, 412f, 892f, false)
            return scene.items.single { it.cardId == f.id(id) }.visible.first().center.let { Offset(it.x, it.y) }
        }
        try {
            f.connect(root)
            compose.setContent { MaterialTheme { TableScreen(f.lobby, f.actor, {}, false) } }
            drain(f)
            assertNoSendStepOrConfirmation()

            // A note: typing sends nothing; finishing the edit sends it, once.
            compose.onRoot().performTouchInput { click(center(40)) }
            compose.mainClock.advanceTimeBy(500)
            compose.ringClick("Edit card note")
            compose.onNode(hasSetTextAction()).performTextReplacement("remember me")
            drain(f)
            assertTrue(sent().isEmpty(), "Typing alone sends nothing")
            compose.onNodeWithText("Set", useUnmergedTree = true).performClick()
            drain(f)
            assertEquals(listOf<PlayAction>(PlayAction.SetNote(f.id(40), "remember me")), sent())
            assertEquals("remember me", f.card(40).note)
            assertNoSendStepOrConfirmation()

            // A value: the corner, finished the same way (the card menu may still be open).
            if (compose.onAllNodesWithContentDescription("Set corner").fetchSemanticsNodes().isEmpty()) {
                compose.onRoot().performTouchInput { click(center(40)) }
                compose.mainClock.advanceTimeBy(500)
            }
            compose.ringClick("Set corner")
            compose.onNode(hasSetTextAction()).performTextReplacement("4/5")
            compose.onNodeWithText("Set", useUnmergedTree = true).performClick()
            drain(f)
            assertEquals(PlayAction.SetCorner(f.id(40), "4/5"), sent().drop(1).single())
            assertEquals("4/5", f.card(40).corner)
            assertNoSendStepOrConfirmation()

            // A move: dropping the hand card on the battlefield is the whole action.
            compose.onRoot().performTouchInput { swipe(center(41), center(40) + Offset(120f, 0f), 400) }
            drain(f)
            val move = assertIs<PlayAction.MoveCard>(sent().drop(2).single())
            assertEquals(f.id(41), move.cardId)
            assertEquals(f.container(PlayZone.BATTLEFIELD), f.card(41).containerId)
            assertNoSendStepOrConfirmation()
        } finally { f.lobby.close(); f.worker.drain() }
    }
}

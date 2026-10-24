package org.asyncttwiz.android

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import org.asyncttwiz.protocol.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.*

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w412dp-h892dp-port-mdpi")
class LibraryActionsUiTest {
    @get:Rule val compose = createComposeRule()
    private val play = LiveTableFixture.play(5)
    private val library = play.board.containers.single { it.id == "a/LIBRARY" }
    private val hand = play.board.containers.single { it.id == "a/HAND" }
    private val sent = mutableListOf<PlayAction>()
    private val moves = mutableListOf<TableIntent.Move>()
    private var completed = 0

    private fun render(connected: Boolean = true) {
        val controller = LibraryController(play.id, library.id, hand.id, { play }, { connected },
            { sent += it; true }, seed = { RandomV1.encodeSeed(ByteArray(32)) },
            moveRequest = { moves += it; LibrarySubmission(it.scope.cardIds.size, 0) })
        compose.setContent { MaterialTheme {
            TableMenuPanel({}, title = "Library") {
                LibraryActions(controller, play.board, hand.cards, onComplete = { completed++ })
            }
        } }
    }

    private fun click(label: String) = compose.onNodeWithContentDescription(label).performClick()

    @Test fun allControlsFitPortraitAndOneTapButtonsDispatchWithoutChangingN() {
        render()
        for (label in listOf("Draw 1", "Search", "Reveal all", "Shuffle", "Decrease N", "Set N", "Increase N",
            "Top", "Bottom", "Draw N", "Look at N", "Reveal N", "Graveyard", "Exile", "Hand", "Battlefield", "Draw 7", "Draw one fewer")) {
            compose.onNodeWithContentDescription(label).assertIsDisplayed().assertHeightIsAtLeast(48.dp)
        }
        click("Draw 1")
        assertEquals(library.cards.take(1), sent.filterIsInstance<PlayAction.MoveCard>().map { it.cardId })
        assertTrue(sent.filterIsInstance<PlayAction.MoveCard>().all { it.destination.containerId == hand.id })
        sent.clear()
        click("Search")
        assertEquals(listOf<PlayAction>(PlayAction.ShowContents(library.id, ContentsSelection.ALL, 1, false)), sent)
        sent.clear()
        click("Reveal all")
        assertEquals(listOf<PlayAction>(PlayAction.ShowContents(library.id, ContentsSelection.ALL, 1, true)), sent)
        sent.clear()
        click("Shuffle")
        assertEquals(library.id, sent.filterIsInstance<PlayAction.ShuffleLibrary>().single().containerId)
        assertEquals(4, completed)
    }

    @Test fun quantityToggleAndEverySliceButtonDispatchTheSelectedBottomSlice() {
        render()
        click("Increase N"); click("Bottom")
        click("Draw N")
        assertEquals(library.cards.takeLast(2), sent.filterIsInstance<PlayAction.MoveCard>().map { it.cardId })
        assertTrue(sent.filterIsInstance<PlayAction.MoveCard>().all { it.destination.containerId == hand.id })
        sent.clear()
        click("Look at N")
        assertEquals(listOf<PlayAction>(PlayAction.ShowContents(library.id, ContentsSelection.BOTTOM, 2, false)), sent)
        sent.clear()
        click("Reveal N")
        assertEquals(listOf<PlayAction>(PlayAction.ShowContents(library.id, ContentsSelection.BOTTOM, 2, true)), sent)
        for (zone in listOf(PlayZone.GRAVEYARD, PlayZone.EXILE, PlayZone.HAND)) {
            sent.clear()
            click(moveZoneLabel(zone))
            val actions = sent.filterIsInstance<PlayAction.MoveCard>()
            assertEquals(library.cards.takeLast(2), actions.map { it.cardId })
            assertTrue(actions.all { it.destination.containerId == "a/$zone" })
        }
        click("Battlefield")
        val move = moves.single()
        assertEquals(library.cards.takeLast(2), move.scope.cardIds)
        val destination = assertIs<TableDestination.Grid>(move.destination)
        assertEquals("a/BATTLEFIELD", destination.containerId)
        val lastColumn = play.board.cards.filter { it.containerId == destination.containerId }.mapNotNull { it.placement?.position?.x }.maxOrNull()
        assertEquals(lastColumn?.plus(1) ?: 0, destination.column)
        assertTrue(destination.ownerOnly)
    }

    @Test fun valueTapEditsNAndMulligansKeepSevenAndHandMinusOneIndependentOfN() {
        render()
        // Keep the text field's cursor animation on a controlled clock while editing.
        compose.mainClock.autoAdvance = false
        click("Set N")
        compose.mainClock.advanceTimeBy(160)
        compose.onNode(hasSetTextAction()).performTextReplacement("3")
        compose.onNodeWithText("Set").performClick()
        compose.mainClock.autoAdvance = true
        click("Draw N")
        assertEquals(3, sent.filterIsInstance<PlayAction.MoveCard>().size)
        for ((label, count) in listOf("Draw 7" to 7, "Draw one fewer" to hand.cards.size - 1)) {
            sent.clear()
            click(label)
            assertEquals(hand.cards, sent.filterIsInstance<PlayAction.MoveCard>().take(hand.cards.size).map { it.cardId })
            assertEquals(1, sent.filterIsInstance<PlayAction.ShuffleLibrary>().size)
            assertEquals(hand.cards.size + count, sent.filterIsInstance<PlayAction.MoveCard>().size)
        }
    }

    @Test fun disconnectedActionsCannotDispatch() {
        render(connected = false)
        for (label in listOf("Draw 1", "Search", "Reveal all", "Shuffle", "Draw N", "Look at N", "Reveal N",
            "Graveyard", "Exile", "Hand", "Battlefield", "Draw 7", "Draw one fewer"))
            compose.onNodeWithContentDescription(label).assertIsNotEnabled()
        assertTrue(sent.isEmpty()); assertTrue(moves.isEmpty())
    }
}

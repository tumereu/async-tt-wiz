package org.asyncttwiz.android

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import android.content.Context
import org.asyncttwiz.protocol.*
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
class LibraryActionsUiTest {
    @get:Rule val compose = createComposeRule()
    private val play = LiveTableFixture.play(5)
    private val library = play.board.containers.single { it.id == "a/LIBRARY" }
    private val hand = play.board.containers.single { it.id == "a/HAND" }
    private val sent = mutableListOf<PlayAction>()
    private var completed = 0
    private val labels = listOf("Draw", "Reveal", "Look", "Mill", "Exile")

    private fun render(connected: Boolean = true) {
        ApplicationProvider.getApplicationContext<Context>().getSharedPreferences("library-quantities", 0).edit().clear().commit()
        val controller = LibraryController(play.id, library.id, hand.id, { play }, { connected },
            { sent += it; true }, seed = { RandomV1.encodeSeed(ByteArray(32)) })
        compose.setContent { MaterialTheme {
            InlinePopupPanel({}, "Library", width = 264.dp, height = 304.dp) {
                LibraryActions(controller, play.board, hand.cards, onComplete = { completed++ })
            }
        } }
        compose.waitForIdle()
    }
    private fun click(label: String) {
        compose.mainClock.advanceTimeBy(350)
        compose.onNodeWithContentDescription(label).performTouchInput { advanceEventTime(350); click() }
        compose.waitForIdle()
    }
    private fun quantity(action: String, n: Int) {
        click("$action N…")
        val input = compose.onNode(hasSetTextAction()).assertIsDisplayed()
        compose.mainClock.autoAdvance = false
        input.performTextReplacement(n.toString())
        compose.mainClock.advanceTimeByFrame()
        click(action)
        compose.mainClock.autoAdvance = true
    }

    @Test fun inlineRowsFitAndOneTapActionsUseTopCard() {
        render()
        for (label in labels.flatMap { listOf("$it 1", "$it N…") } + listOf("Search", "Shuffle", "Mulligan 7", "Mulligan −1"))
            compose.onNodeWithContentDescription(label).assertIsDisplayed().assertHeightIsAtLeast(48.dp)
        for ((action, zone) in listOf("Draw" to PlayZone.HAND, "Mill" to PlayZone.GRAVEYARD, "Exile" to PlayZone.EXILE)) {
            sent.clear(); click("$action 1")
            val move = sent.filterIsInstance<PlayAction.MoveCard>().single()
            assertEquals(library.cards.first(), move.cardId)
            assertEquals("a/$zone", move.destination.containerId)
        }
        for ((action, reveal) in listOf("Look" to false, "Reveal" to true)) {
            sent.clear(); click("$action 1")
            assertEquals(listOf<PlayAction>(PlayAction.ShowContents(library.id, ContentsSelection.TOP, 1, reveal)), sent)
        }
        sent.clear(); click("Search")
        assertEquals(listOf<PlayAction>(PlayAction.ShowContents(library.id, ContentsSelection.ALL, 1, false)), sent)
        sent.clear(); click("Shuffle")
        assertEquals(library.id, sent.filterIsInstance<PlayAction.ShuffleLibrary>().single().containerId)
        compose.onNodeWithContentDescription("Reveal all").assertDoesNotExist()
        compose.onNodeWithContentDescription("Bottom").assertDoesNotExist()
        compose.onNodeWithContentDescription("Close").assertDoesNotExist()
        compose.onNodeWithText("Library").assertDoesNotExist()
        compose.onNodeWithText("Mulligan (hand → shuffle → draw)").assertDoesNotExist()
        compose.onNodeWithContentDescription("Context action grid").assertHeightIsEqualTo(204.dp).assertWidthIsEqualTo(248.dp)
        compose.onAllNodes(isDialog()).assertCountEquals(0)
    }

    @Test fun quantitiesAreIndependentAndRememberedPerActionAndAccount() {
        render()
        quantity("Draw", 3)
        assertEquals(library.cards.take(3), sent.filterIsInstance<PlayAction.MoveCard>().map { it.cardId })
        sent.clear(); quantity("Mill", 2)
        assertEquals(library.cards.take(2), sent.filterIsInstance<PlayAction.MoveCard>().map { it.cardId })
        assertTrue(sent.filterIsInstance<PlayAction.MoveCard>().all { it.destination.containerId == "a/GRAVEYARD" })
        click("Draw N…")
        compose.onNode(hasSetTextAction()).assertTextEquals("3")
        val context = ApplicationProvider.getApplicationContext<Context>()
        assertEquals(3, LibraryQuantityStore(context, "a").read("Draw"))
        assertEquals(2, LibraryQuantityStore(context, "a").read("Mill"))
        assertEquals(1, LibraryQuantityStore(context, "a").read("Reveal"))
        assertEquals(1, LibraryQuantityStore(context, "b").read("Draw"))
    }

    @Test fun mulligansAreIndependentOfQuantities() {
        render()
        for ((label, count) in listOf("Mulligan 7" to 7, "Mulligan −1" to hand.cards.size - 1)) {
            sent.clear(); click(label)
            assertEquals(hand.cards, sent.filterIsInstance<PlayAction.MoveCard>().take(hand.cards.size).map { it.cardId })
            assertEquals(1, sent.filterIsInstance<PlayAction.ShuffleLibrary>().size)
            assertEquals(hand.cards.size + count, sent.filterIsInstance<PlayAction.MoveCard>().size)
        }
    }

    @Test fun quantityFloatsInOneRowAndSelectsAllOnFocusAndEveryTap() {
        render()
        quantity("Draw", 12)
        sent.clear()
        click("Draw N…")
        val input = compose.onNode(hasSetTextAction()).assertTextEquals("12")
        compose.onNodeWithContentDescription("Context action grid").assertHeightIsEqualTo(48.dp).assertWidthIsEqualTo(148.dp)
        compose.onAllNodes(isDialog()).assertCountEquals(0)
        input.performClick()
        input.assert(SemanticsMatcher.expectValue(androidx.compose.ui.semantics.SemanticsProperties.TextSelectionRange,
            androidx.compose.ui.text.TextRange(0, 2)))
        input.performTextInput("3")
        input.assertTextEquals("3")
        // Re-selecting on a later touch tap is verified on device: Robolectric's text magnifier crashes on touch input here.
        click("Decrease Draw quantity")
        input.assertTextEquals("2")
        click("Decrease Draw quantity")
        click("Decrease Draw quantity")
        input.assertTextEquals("0")
        click("Increase Draw quantity")
        input.assertTextEquals("1")
        click("Draw")
        assertEquals(1, sent.filterIsInstance<PlayAction.MoveCard>().size)
    }

    @Test fun disconnectedActionsCannotDispatch() {
        render(false)
        for (label in labels.flatMap { listOf("$it 1", "$it N…") } + listOf("Search", "Shuffle", "Mulligan 7", "Mulligan −1"))
            compose.onNodeWithContentDescription(label).assertIsNotEnabled()
        assertTrue(sent.isEmpty())
    }
}

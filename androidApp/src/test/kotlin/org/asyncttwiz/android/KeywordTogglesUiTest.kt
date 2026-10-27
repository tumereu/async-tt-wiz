package org.asyncttwiz.android

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import org.asyncttwiz.protocol.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.*

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w412dp-h892dp-port-mdpi")
class KeywordTogglesUiTest {
    @get:Rule val compose = createComposeRule()
    private val f = TableStatesFixture
    private val batches = mutableListOf<List<PlayAction>>()

    private fun render(notes: Map<String, String>, ids: List<String>): Pair<TableToolkitController, androidx.compose.runtime.MutableState<PlayState>> {
        val base = f.combat()
        val play = mutableStateOf(base.copy(board = base.board.copy(cards = base.board.cards.map { card ->
            notes[card.id]?.let { card.copy(note = it) } ?: card })))
        val toolkit = TableToolkitController(f.a, { play.value }, { true }, { action, _ -> batches += listOf(action); true },
            playBatch = { actions, _ -> batches += actions.toList(); true })
        toolkit.openCard(TableScope(ids.first(), ids))
        compose.setContent {
            MaterialTheme { TableSurface(f.game, play.value, f.a, false, Modifier.fillMaxSize(), connected = true,
                toolkit = toolkit, onIntent = toolkit::handle) }
        }
        compose.waitForIdle()
        return toolkit to play
    }
    private fun press(description: String) {
        compose.mainClock.advanceTimeBy(350)
        compose.onNodeWithContentDescription(description).performTouchInput { advanceEventTime(350); click() }
    }

    @Test fun moreOpensATogglePopupThatSendsEachToggleAndStaysOpen() {
        val first = f.source(f.a, "1/3").id
        val second = f.source(f.a, "1/2").id
        val (toolkit, play) = render(mapOf(first to "Trample"), listOf(first, second))
        compose.ringPage(3)
        compose.onNodeWithContentDescription("Edit keywords").assertIsEnabled()
        compose.onNodeWithContentDescription("Edit EOT keywords").assertIsEnabled()
        press("Edit keywords")
        compose.onNodeWithContentDescription("Card action ring").assertDoesNotExist()
        compose.onNodeWithContentDescription("Trample").assertIsNotSelected()
        press("Trample")
        assertEquals(listOf<PlayAction>(PlayAction.SetNote(second, "Trample")), batches.single())
        // The popup stays and reflects the (here simulated) applied state.
        play.value = play.value.copy(board = play.value.board.copy(cards = play.value.board.cards.map {
            if (it.id == second) it.copy(note = "Trample") else it }))
        compose.onNodeWithContentDescription("Trample").assertIsSelected()
        press("Flying")
        assertEquals(listOf<PlayAction>(PlayAction.SetNote(first, "Flying, Trample"), PlayAction.SetNote(second, "Flying, Trample")),
            batches.last())
        assertNotNull(toolkit.manual.keywords)
    }

    @Test fun customNoteTextDisablesOnlyThatButton() {
        val first = f.source(f.a, "1/3").id
        render(mapOf(first to "Flying, X=3"), listOf(first))
        compose.ringPage(3)
        compose.onNodeWithContentDescription("Edit keywords").assertIsNotEnabled()
        compose.onNodeWithContentDescription("Edit EOT keywords").assertIsEnabled()
    }
}

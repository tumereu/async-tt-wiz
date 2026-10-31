package org.asyncttwiz.android

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import org.asyncttwiz.protocol.CounterColor
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
class PortraitCounterGridUiTest {
    @get:Rule val compose = createComposeRule()

    private fun emptyGridOpensPlayerPopup(owner: String) {
        val initial = TableFixture.play(library = 0)
        val play = initial.copy(board = initial.board.copy(players = initial.board.players.map {
            it.copy(counters = mapOf(CounterColor.BLUE to "0"))
        }))
        val scene = TablePresentation.scene(TableFixture.game, play, "a", 412f, 892f, false)
        val toolkit = TableToolkitController("a", { play }, { true }, { _, _ -> true })
        compose.setContent { MaterialTheme {
            TableSurface(TableFixture.game, play, "a", false, Modifier.fillMaxSize(), connected = true,
                onIntent = toolkit::handle, toolkit = toolkit)
        } }
        for (counter in scene.items.filter { it.kind == TableItemKind.COUNTER })
            compose.onNodeWithContentDescription(counter.label).assertIsDisplayed().assertHasClickAction()
        val slot = scene.items.single { it.id == "counter/$owner/GRAY" }
        // Hit the corner of an empty slot, outside the drawn circle.
        compose.onNodeWithContentDescription(slot.label).performTouchInput { click(Offset(width - 1f, height - 1f)) }
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Counters").assertIsDisplayed()
        compose.onNodeWithContentDescription("Set life").assertDoesNotExist()
        for (color in CounterColor.entries)
            compose.onNodeWithContentDescription("Set ${color.name.lowercase()} counter").assertIsDisplayed()
        compose.runOnIdle {
            assertEquals(owner, toolkit.manual.scope!!.contextId)
            assertTrue(toolkit.manual.scope!!.playerCounters)
        }
    }

    // TRN-034
    @Test fun ownEmptyGridOpensOnlyCounters() = emptyGridOpensPlayerPopup("a")
    @Test fun opponentEmptyGridOpensOnlyCounters() = emptyGridOpensPlayerPopup("b")
}

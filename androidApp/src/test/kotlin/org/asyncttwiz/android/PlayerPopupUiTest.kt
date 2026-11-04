package org.asyncttwiz.android

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
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
class PlayerPopupUiTest {
    @get:Rule val compose = createComposeRule()

    private fun render(landscape: Boolean, empty: Boolean = false):
        Triple<PlayState, TableToolkitController, List<PlayAction>> {
        val initial = TableFixture.play(library = 0)
        val play = initial.copy(board = initial.board.copy(players = initial.board.players.map {
            it.copy(counters = if (empty) mapOf(CounterColor.BLUE to "0", CounterColor.GRAY to "", CounterColor.GREEN to "-00")
                else mapOf(CounterColor.BLUE to "3"))
        }))
        val sent = mutableListOf<PlayAction>()
        val toolkit = TableToolkitController("a", { tableBoard(play) }, { true }, { action, _ -> sent += action; true })
        compose.setContent { MaterialTheme {
            TableSurface(TableFixture.game, tableBoard(play), "a", landscape, Modifier.fillMaxSize(), connected = true,
                onIntent = toolkit::handle, toolkit = toolkit)
        } }
        return Triple(play, toolkit, sent)
    }

    private fun life(owner: String, landscape: Boolean = false, empty: Boolean = false) {
        val (play, toolkit, sent) = render(landscape, empty)
        val scene = TablePresentation.scene(TableFixture.game, tableBoard(play), "a", if (landscape) 892f else 412f,
            if (landscape) 412f else 892f, landscape)
        compose.onNodeWithContentDescription(scene.items.single { it.id == "life/$owner" }.label).performClick()
        compose.mainClock.advanceTimeBy(350)
        compose.onNodeWithContentDescription("Life", substring = false).assertIsDisplayed()
        val life = play.board.players.single { it.accountId == owner }.life
        compose.onNodeWithContentDescription("Life $life").assertIsDisplayed()
        // I-D52: life changes only by the rules: no steppers, value editor or counters
        for (name in listOf("Set life", "Increase life", "Decrease life", "Counters", "Edit status"))
            compose.onNodeWithContentDescription(name).assertDoesNotExist()
        for (color in CounterColor.entries)
            compose.onNodeWithContentDescription("Set ${color.name.lowercase()} counter").assertDoesNotExist()
        compose.runOnIdle { assertEquals(owner, toolkit.player) }
        assertTrue(sent.isEmpty())
    }

    // TRN-030
    @Test fun ownPortraitLife() = life("a")
    @Test fun opponentPortraitLife() = life("b")
    @Test @Config(qualifiers = "w892dp-h412dp-land-mdpi") fun ownLandscapeLife() = life("a", true)
    @Test @Config(qualifiers = "w892dp-h412dp-land-mdpi") fun opponentLandscapeLife() = life("b", true)
    // TRN-030, TRN-035: no Counters button in the Life panel, even with every counter empty.
    @Test @Config(qualifiers = "w892dp-h412dp-land-mdpi") fun ownEmptyLandscapeLife() = life("a", true, empty = true)
    @Test @Config(qualifiers = "w892dp-h412dp-land-mdpi") fun opponentEmptyLandscapeLife() = life("b", true, empty = true)

}

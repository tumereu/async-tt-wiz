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

    private fun render(landscape: Boolean, empty: Boolean = false, toolkitHost: Boolean = true):
        Triple<PlayState, TableToolkitController, List<PlayAction>> {
        val initial = TableFixture.play(library = 0)
        val play = initial.copy(board = initial.board.copy(players = initial.board.players.map {
            it.copy(counters = if (empty) mapOf(CounterColor.BLUE to "0", CounterColor.GRAY to "", CounterColor.GREEN to "-00")
                else mapOf(CounterColor.BLUE to "3"))
        }))
        val sent = mutableListOf<PlayAction>()
        val toolkit = TableToolkitController("a", { play }, { true }, { action, _ -> sent += action; true })
        compose.setContent { MaterialTheme {
            TableSurface(TableFixture.game, play, "a", landscape, Modifier.fillMaxSize(), connected = true,
                onIntent = toolkit::handle, toolkit = toolkit.takeIf { toolkitHost })
        } }
        return Triple(play, toolkit, sent)
    }

    private fun life(owner: String, landscape: Boolean = false, toolkitHost: Boolean = true) {
        val (play, toolkit, sent) = render(landscape, toolkitHost = toolkitHost)
        val scene = TablePresentation.scene(TableFixture.game, play, "a", if (landscape) 892f else 412f,
            if (landscape) 412f else 892f, landscape)
        compose.onNodeWithContentDescription(scene.items.single { it.id == "life/$owner" }.label).performClick()
        compose.mainClock.advanceTimeBy(350)
        compose.onNodeWithContentDescription("Life", substring = false).assertIsDisplayed()
        compose.onNodeWithContentDescription("Set life").assertIsDisplayed()
        compose.onNodeWithContentDescription("Counters").assertDoesNotExist()
        for (color in CounterColor.entries)
            compose.onNodeWithContentDescription("Set ${color.name.lowercase()} counter").assertDoesNotExist()
        assertTrue(sent.isEmpty())
        compose.onNodeWithContentDescription("Increase life").performClick()
        compose.onNodeWithContentDescription("Decrease life").performClick()
        assertEquals(listOf(PlayAction.AdjustLife(owner, "1"), PlayAction.AdjustLife(owner, "-1")), sent)
        compose.onNodeWithContentDescription("Set life").performClick()
        if (toolkitHost) compose.runOnIdle {
            assertEquals(ManualValue(ManualValueKind.LIFE), toolkit.manual.editor!!.field)
            assertEquals(owner, toolkit.manual.editor!!.scope.contextId)
        }
    }

    // TRN-030, TRN-031
    @Test fun ownPortraitLife() = life("a")
    @Test fun opponentPortraitLife() = life("b")
    @Test fun basicHostLife() = life("a", toolkitHost = false)
    @Test @Config(qualifiers = "w892dp-h412dp-land-mdpi") fun ownLandscapeLife() = life("a", true)
    @Test @Config(qualifiers = "w892dp-h412dp-land-mdpi") fun opponentLandscapeLife() = life("b", true)

    private fun counters(owner: String, fallback: Boolean = false, toolkitHost: Boolean = true) {
        val (play, toolkit, sent) = render(true, empty = fallback, toolkitHost = toolkitHost)
        val scene = TablePresentation.scene(TableFixture.game, play, "a", 892f, 412f, true)
        if (fallback) {
            assertTrue(scene.items.none { it.owner == owner && it.kind == TableItemKind.COUNTER })
            compose.onNodeWithContentDescription(scene.items.single { it.id == "life/$owner" }.label).performClick()
            compose.mainClock.advanceTimeBy(350)
            compose.onNodeWithContentDescription("Set life").assertIsDisplayed()
            compose.onNodeWithContentDescription("Set blue counter").assertDoesNotExist()
            compose.onNodeWithContentDescription("Counters").performClick()
        } else {
            compose.onNodeWithContentDescription(scene.items.single { it.id == "counter/$owner/BLUE" }.label).performClick()
        }
        compose.mainClock.advanceTimeBy(350)
        compose.onNodeWithContentDescription("Counters").assertIsDisplayed()
        compose.onNodeWithContentDescription("Set life").assertDoesNotExist()
        for (color in CounterColor.entries) {
            val name = color.name.lowercase()
            compose.onNodeWithContentDescription("Set $name counter").assertIsDisplayed()
            compose.onNodeWithContentDescription("Decrease $name counter").assertIsDisplayed()
            compose.onNodeWithContentDescription("Increase $name counter").assertIsDisplayed()
        }
        assertTrue(sent.isEmpty())
        compose.onNodeWithContentDescription("Increase red counter").performClick()
        compose.onNodeWithContentDescription("Decrease blue counter").performClick()
        assertEquals(listOf(PlayAction.AdjustCounter(CounterTarget.PLAYER, owner, CounterColor.RED, "1"),
            PlayAction.AdjustCounter(CounterTarget.PLAYER, owner, CounterColor.BLUE, "-1")), sent)
        compose.onNodeWithContentDescription("Set blue counter").performClick()
        if (toolkitHost) compose.runOnIdle {
            assertEquals(ManualValue(ManualValueKind.COUNTER, CounterColor.BLUE), toolkit.manual.editor!!.field)
            assertEquals(owner, toolkit.manual.editor!!.scope.contextId)
        }
    }

    // TRN-031, TRN-033, TRN-035
    @Test @Config(qualifiers = "w892dp-h412dp-land-mdpi") fun ownLandscapeCounters() = counters("a")
    @Test @Config(qualifiers = "w892dp-h412dp-land-mdpi") fun opponentLandscapeCounters() = counters("b")
    @Test @Config(qualifiers = "w892dp-h412dp-land-mdpi") fun ownEmptyLandscapeCounters() = counters("a", fallback = true)
    @Test @Config(qualifiers = "w892dp-h412dp-land-mdpi") fun opponentEmptyLandscapeCounters() = counters("b", fallback = true)
    @Test @Config(qualifiers = "w892dp-h412dp-land-mdpi") fun basicHostCounters() = counters("a", toolkitHost = false)
    @Test @Config(qualifiers = "w892dp-h412dp-land-mdpi") fun basicHostEmptyCounters() = counters("a", fallback = true, toolkitHost = false)

    @Test fun lifeValueStillDrags() {
        val (play, _, sent) = render(false)
        val scene = TablePresentation.scene(TableFixture.game, play, "a", 412f, 892f, false)
        compose.onNodeWithContentDescription(scene.items.single { it.id == "life/a" }.label).performClick()
        compose.mainClock.advanceTimeBy(350)
        compose.onNodeWithContentDescription("Set life").performTouchInput {
            advanceEventTime(350)
            down(center)
            moveBy(Offset(60f, 0f))
            up()
        }
        assertEquals(listOf(PlayAction.AdjustLife("a", "1")), sent)
        compose.onNodeWithContentDescription("Set life").assertIsDisplayed()
    }
}

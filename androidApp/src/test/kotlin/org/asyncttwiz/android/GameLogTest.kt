package org.asyncttwiz.android

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The game log (HIS-239–HIS-242): the timeline's events as lines of text, from Table actions. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w412dp-h892dp-port-mdpi")
class GameLogTest {
    @get:Rule val compose = createComposeRule()

    @After fun forget() = AutomatumBoards.reset()

    private val names = mapOf(TableStatesFixture.a to "Alice", TableStatesFixture.b to "Bob")

    private fun drain(f: EngineLobbyFixture) {
        repeat(4) { compose.runOnIdle { f.worker.drain() }; compose.waitForIdle() }
    }

    // HIS-239, HIS-241, HIS-004, TRN-010: Table actions → Game log lists the timeline's events, oldest at the top; tapping
    // Bob's keep closes the log and opens timeline mode there (not at the newest event: Auto, not Exit timeline, HIS-015)
    @Test fun theLogListsTheTimelineAndALineOpensTimelineModeThere() {
        val f = EngineLobbyFixture()
        try {
            f.connect(f.head(otherKept = true))
            f.keep()
            compose.setContent { MaterialTheme { TableScreen(f.lobby, f.me, {}, false) } }
            drain(f)
            compose.onNodeWithContentDescription("Table actions").performClick()
            compose.onNodeWithText("Game log").assertIsEnabled().performClick()
            drain(f)
            compose.onNodeWithTag("game-log").assertIsDisplayed()
            compose.onNodeWithText("Table actions").assertDoesNotExist()
            // Top to bottom as drawn (the list lays the newest out first, at the bottom)
            val lines = compose.onAllNodesWithTag("game-log-line").fetchSemanticsNodes().sortedBy { it.boundsInRoot.top }
            val texts = lines.map { it.config[androidx.compose.ui.semantics.SemanticsProperties.ContentDescription].single() }
            val timeline = compose.runOnIdle { f.lobby.engineGame()!!.let { it.timeline.events(it, names) }.map { it.description } }
            assertEquals(timeline, texts, "the log's lines are the timeline's events, oldest first")
            assertTrue("Bob kept" in texts && "You kept" in texts, "both keeps: $texts")
            val bob = lines[texts.indexOf("Bob kept")].boundsInRoot
            val mine = lines[texts.indexOf("You kept")].boundsInRoot
            assertTrue(bob.bottom <= mine.top + 1f, "the newer line below the older: $bob, $mine")
            compose.onNodeWithContentDescription("Bob kept").performClick()
            drain(f)
            compose.onNodeWithTag("game-log").assertDoesNotExist()
            compose.onNodeWithContentDescription("Return to current").assertExists()
            compose.onNodeWithContentDescription("Auto advance").assertExists()
            compose.onNodeWithContentDescription("Exit timeline").assertDoesNotExist()
        } finally { f.close() }
    }

    // HIS-240, HIS-242, HIS-239: a few turns of Mountains and Ember Cubs: each name and card in its player's colour, the turn
    // and step labels between the lines in the colour of whose turn it is
    @Test fun theLogInThePlayersColours() {
        val scenario = EngineCombatScenario()
        scenario.until(scenario::bothHaveCubs)
        val alice = scenario.alice
        val lines = alice.timeline.log(alice, names)
        val texts = lines.map { it.pieces.plain() }
        assertTrue(texts.any { it == "Bob played Mountain" } && texts.any { it == "You cast Ember Cub" }, "$texts")
        val bobs = lines.first { it.pieces.plain() == "Bob played Mountain" }.pieces
        assertEquals(listOf(StackTextPiece("Bob", scenario.f.b), StackTextPiece(" played "), StackTextPiece("Mountain", scenario.f.b)), bobs)
        val mine = lines.first { it.pieces.plain() == "You cast Ember Cub" }.pieces
        assertEquals(listOf(StackTextPiece("You", scenario.f.a), StackTextPiece(" cast "), StackTextPiece("Ember Cub", scenario.f.a)), mine)
        compose.setContent { MaterialTheme { NoRipples { GameLogPanel(lines, scenario.f.a, {}, {}) } } }
        compose.waitForIdle()
        compose.onAllNodesWithTag("game-log-separator").onFirst().assertExists()
        compose.onAllNodesWithTag("game-log-line").onLast().assertIsDisplayed()
        captureShot("$SCREENSHOTS/history/game-log.png",
            "Alice opened Table actions → Game log after a few turns of Mountains and Ember Cubs: one line per event with its icon, the newest at the bottom, her own name and cards in green and Bob's in red, and the turn and step labels between the lines.")
    }
}

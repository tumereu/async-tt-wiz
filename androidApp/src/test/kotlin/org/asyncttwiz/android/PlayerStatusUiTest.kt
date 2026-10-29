package org.asyncttwiz.android

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.TextRange
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
class PlayerStatusUiTest {
    @get:Rule val compose = createComposeRule()

    private fun open(owner: String, own: String = "", other: String = "", landscape: Boolean = false): MutableList<PlayAction> {
        val initial = TableFixture.play(library = 0)
        val play = initial.copy(board = initial.board.copy(players = initial.board.players.map {
            it.copy(status = if (it.accountId == "a") own else other)
        }))
        val sent = mutableListOf<PlayAction>()
        val toolkit = TableToolkitController("a", { play }, { true }, { action, _ -> sent += action; true })
        compose.setContent { MaterialTheme {
            TableSurface(TableFixture.game, play, "a", landscape, Modifier.fillMaxSize(), connected = true,
                onIntent = toolkit::handle, toolkit = toolkit)
        } }
        val scene = TablePresentation.scene(TableFixture.game, play, "a", if (landscape) 892f else 412f,
            if (landscape) 412f else 892f, landscape)
        val life = scene.items.single { it.id == "life/$owner" }
        assertEquals(if (owner == "a") own else other, life.status)
        lifeLabel = life.label
        reopen()
        return sent
    }

    private var lifeLabel = ""
    private fun reopen() {
        compose.onNodeWithContentDescription(lifeLabel).performClick()
        compose.mainClock.advanceTimeBy(350)
        compose.onNodeWithContentDescription("Set life").assertIsDisplayed()
    }
    private fun choose(action: String) {
        compose.onNodeWithContentDescription(action).performScrollTo().performClick()
        compose.mainClock.advanceTimeBy(350)
        compose.onNodeWithContentDescription("Set life").assertDoesNotExist()
    }

    @Test fun opponentPanelHasHideShowAndMuteUnmuteTogglesThatStayLocal() {
        val sent = open("b", other = "Got a response")
        compose.onNodeWithContentDescription("Show status bubble").assertDoesNotExist()
        compose.onNodeWithContentDescription("Unmute status bubble").assertDoesNotExist()
        choose("Hide status bubble")
        reopen()
        compose.onNodeWithContentDescription("Hide status bubble").assertDoesNotExist()
        choose("Show status bubble")
        reopen()
        choose("Mute status bubble")
        reopen()
        compose.onNodeWithContentDescription("Hide status bubble").assertExists()
        compose.onNodeWithContentDescription("Unmute status bubble").assertExists()
        choose("Unmute status bubble")
        reopen()
        compose.onNodeWithContentDescription("Mute status bubble").assertExists()
        // Full text remains in the panel; nothing is sent to the game.
        compose.onNodeWithText("Status: “Got a response”").assertIsDisplayed()
        assertTrue(sent.isEmpty())
    }

    @Test fun ownPanelAlsoHasTheToggles() {
        val sent = open("a", own = "BRB")
        compose.onNodeWithContentDescription("Hide status bubble").assertExists()
        choose("Mute status bubble")
        assertTrue(sent.isEmpty())
        compose.onNodeWithContentDescription(lifeLabel).assertExists()
    }

    @Test fun noStatusOffersMuteAndADisabledHide() {
        open("b")
        compose.onNodeWithContentDescription("Hide status bubble").assertIsNotEnabled()
        compose.onNodeWithContentDescription("Show status bubble").assertDoesNotExist()
        compose.onNodeWithContentDescription("Mute status bubble").assertExists()
    }

    @Test fun ownPanelOffersPresetsAndAPresetSetsTheStatusAndClosesThePanel() {
        val sent = open("a", own = PlayerStatus.OPENING)
        compose.onNodeWithText("Status: “Mulliganing”").assertIsDisplayed()
        for (preset in PlayerStatusInput.presets) compose.onNodeWithContentDescription("Set status $preset").assertExists()
        assertEquals(listOf("OK?", "Yes", "No", "Wait", "Thinking…", "Go ahead", "GG"), PlayerStatusInput.presets)
        compose.onNodeWithContentDescription("Edit status").assertExists()
        compose.onNodeWithContentDescription("Clear status").assertIsEnabled()
        compose.onNodeWithContentDescription("Set status Wait").performScrollTo().performClick()
        assertEquals(listOf<PlayAction>(PlayAction.SetStatus("Wait")), sent)
        compose.onNodeWithContentDescription("Set life").assertDoesNotExist()
    }

    @Test fun clearIsOfferedOnlyWhileAStatusIsSet() {
        val sent = open("a", own = "Go ahead")
        compose.onNodeWithContentDescription("Set status Go ahead").assertIsSelected()
        compose.onNodeWithContentDescription("Clear status").performScrollTo().performClick()
        assertEquals(listOf<PlayAction>(PlayAction.SetStatus("")), sent)
    }

    @Test fun noStatusHasADisabledClearButton() {
        open("a")
        compose.onNodeWithText("No status").assertIsDisplayed()
        compose.onNodeWithContentDescription("Clear status").assertIsNotEnabled()
        compose.onNodeWithContentDescription("Hide status bubble").assertIsNotEnabled()
    }

    private fun top(description: String) = compose.onNodeWithContentDescription(description).fetchSemanticsNode().boundsInRoot.top
    private fun lifeSquareCenterY() = compose.onNodeWithContentDescription(lifeLabel).fetchSemanticsNode().boundsInRoot.center.y

    @Test fun lifeRowSitsOnThePanelEdgeNearestTheLifeSquare() {
        open("a", own = "Wait")
        assertTrue(top("Set life") > top("Mute status bubble"), "own panel opens above the square: life row last")
    }

    @Test fun opponentLifeRowComesFirst() {
        open("b", other = "Wait")
        assertTrue(top("Set life") < top("Mute status bubble"), "opponent panel opens below the square: life row first")
    }

    private fun assertLifeRowLevelWithSquare(owner: String) {
        open(owner, own = "Wait", other = "Wait", landscape = true)
        val row = compose.onNodeWithContentDescription("Set life").fetchSemanticsNode().boundsInRoot
        assertTrue(lifeSquareCenterY() in row.top..row.bottom, "${lifeSquareCenterY()} not in ${row.top}..${row.bottom}")
    }

    @Test @Config(qualifiers = "w892dp-h412dp-land-mdpi") fun landscapeOwnLifeRowLinesUpWithTheLifeSquare() =
        assertLifeRowLevelWithSquare("a")

    @Test @Config(qualifiers = "w892dp-h412dp-land-mdpi") fun landscapeOpponentLifeRowLinesUpWithTheLifeSquare() =
        assertLifeRowLevelWithSquare("b")

    @Test fun penOpensAPrefilledFullySelectedFieldAndDoneSaves() {
        val sent = open("a", own = "Wait")
        compose.onNodeWithContentDescription("Edit status").performScrollTo().performClick()
        compose.waitForIdle()
        val field = compose.onNodeWithTag("statusText")
        field.assertTextContains("Wait")
        assertEquals(TextRange(0, 4), field.fetchSemanticsNode().config[SemanticsProperties.TextSelectionRange])
        // Focus and keyboard come from the shared rememberDialogField helper (see DialogInputTest).
        field.performTextReplacement("  Back in five minutes, sorry! " + "x".repeat(40) + "  ")
        assertEquals(PlayerStatus.MAX_LENGTH, field.fetchSemanticsNode().config[SemanticsProperties.EditableText].text.length)
        field.performImeAction()
        assertEquals(listOf<PlayAction>(PlayAction.SetStatus("Back in five minutes, sorry! " + "x".repeat(33))), sent)
        compose.onAllNodes(isDialog()).assertCountEquals(0)
    }

    @Test fun penCancelSendsNothingAndSaveWithBlankTextClears() {
        val sent = open("a", own = "Wait")
        compose.onNodeWithContentDescription("Edit status").performScrollTo().performClick()
        compose.onNodeWithText("Cancel").performClick()
        compose.onAllNodes(isDialog()).assertCountEquals(0)
        assertTrue(sent.isEmpty())
        compose.onNodeWithContentDescription("Edit status").performScrollTo().performClick()
        compose.onNodeWithTag("statusText").performTextReplacement("   ")
        compose.onNodeWithText("Save").performClick()
        assertEquals(listOf<PlayAction>(PlayAction.SetStatus("")), sent)
    }

    @Test fun opponentPanelShowsTheirFullStatusReadOnly() {
        val sent = open("b", other = "Got a response")
        compose.onNodeWithText("Status: “Got a response”").assertIsDisplayed()
        compose.onNodeWithContentDescription("Set status Wait").assertDoesNotExist()
        compose.onNodeWithContentDescription("Edit status").assertDoesNotExist()
        compose.onNodeWithContentDescription("Clear status").assertDoesNotExist()
        assertTrue(sent.isEmpty())
    }

    @Test @Config(qualifiers = "w892dp-h412dp-land-mdpi") fun landscapeOwnPanelAlsoSetsStatus() {
        val sent = open("a", own = PlayerStatus.OPENING, landscape = true)
        compose.onNodeWithContentDescription("Set status GG").performScrollTo().performClick()
        assertEquals(listOf<PlayAction>(PlayAction.SetStatus("GG")), sent)
    }
}

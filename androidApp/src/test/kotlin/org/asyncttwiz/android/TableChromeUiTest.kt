package org.asyncttwiz.android

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.github.takahirom.roborazzi.captureRoboImage
import java.io.File
import javax.imageio.ImageIO
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
class TableChromeUiTest {
    @get:Rule val compose = createComposeRule()

    private fun chrome(landscape: Boolean) {
        val initial = TableFixture.play(hand = 4, board = 3)
        var play by mutableStateOf(initial)
        var viewer by mutableStateOf("a")
        var review by mutableStateOf(false)
        val events = LiveTableFixture.timeline()
        compose.setContent { MaterialTheme {
            val toolkit = remember(viewer) { TableToolkitController(viewer, { tableBoard(play) }, { true }, { _, _ -> true }) }
            TableSurface(TableFixture.game, tableBoard(play), viewer, landscape, Modifier.fillMaxSize(),
                connected = true, onIntent = toolkit::handle, toolkit = toolkit,
                runtime = TableRuntimeHooks(navigation = {}, chat = {}, undo = { true }, reviewing = review,
                    history = { review = true }, returnCurrent = { review = false },
                    liveTimeline = { EngineStrip(events, viewer, landscape) { review = true } },
                    bottomPanel = if (review) ({ _ -> LiveTableFixture.ReviewPanel(viewer, landscape, events) }) else null))
        } }
        var arrow: androidx.compose.ui.geometry.Rect? = null
        for (who in listOf("a", "b")) for (turn in listOf("a", "b")) for (move in listOf("a", "b")) {
            compose.runOnIdle {
                viewer = who; review = false
                val position = if (who != turn && who == move) TurnPosition.DECLARE_BLOCKERS else TurnPosition.UNTAP
                play = initial.copy(board = initial.board.copy(attentionHolder = move,
                    turn = initial.board.turn.copy(accountId = turn, phase = position.phase, step = position.step)))
            }
            val prefix = "chrome-${if (landscape) "landscape" else "portrait"}-$who-turn-$turn-move-$move"
            // TRN-015, TRN-059, TRN-063: the toggles show on either player's turn, the step arrow only while you must act (the
            // move is yours: your blockers in the other player's turn), in your own turn too
            val shown = who == move
            assertEquals(shown, stepArrowShown(tableBoard(play), who))
            val next = compose.onNodeWithContentDescription(AutomatumStep.of(tableBoard(play)).label)
            if (shown) next.assertIsDisplayed() else next.assertDoesNotExist()
            compose.onNodeWithText("Opponent's turn").assertDoesNotExist()
            compose.onNodeWithContentDescription("Untap all").assertDoesNotExist()
            compose.onNodeWithContentDescription("History", substring = false).assertDoesNotExist()
            val output = File("$SCREENSHOTS/$prefix.png").also { it.parentFile!!.mkdirs() }
            val situation = "${if (landscape) "Landscape" else "Portrait"} table seen by player ${who.uppercase()}: " +
                "player ${turn.uppercase()}'s turn, player ${move.uppercase()} to move, " +
                "${if (who != turn && who == move) "declare blockers" else "untap"} step"
            compose.onRoot().captureShot(output.path, "$situation, live play.")
            val pixels = ImageIO.read(output)
            val tint = tableBarColor(play.board, who).toArgb()
            assertEquals(tint, pixels.getRGB(2, 35))
            assertEquals(tint, pixels.getRGB(pixels.width - 2, 35), "Tint continues to the screen edge")
            assertEquals(tint, pixels.getRGB(2, pixels.height - 2))
            run {
                // Hidden, the arrow's room stays empty: the toggles don't move
                val b = if (shown) next.fetchSemanticsNode().boundsInRoot.also { arrow = it } else requireNotNull(arrow)
                assertTrue(b.width in 68f..72f); assertEquals(48f, b.height)
                assertEquals(pixels.width - 8f, b.right, "Step button belongs in the right corner")
                // R4b: the toggles are the engine game's own (EngineToggleCells); their row is checked on an engine game
                // (EngineTableScreenshotTest), this table has none
            }
            val handLabel = TablePresentation.scene(TableFixture.game, tableBoard(play), who, pixels.width.toFloat(), pixels.height.toFloat(), landscape)
                .items.single { it.cardId == "$who/HAND/0" }.label
            val handBefore = compose.onAllNodesWithContentDescription(handLabel).fetchSemanticsNodes().map { it.boundsInRoot }
            // Only the strip's toggle enters timeline mode; event taps describe without scrubbing.
            val latest = compose.onNodeWithContentDescription(events.last().description, useUnmergedTree = true)
            latest.assertIsSelected()
            val latestBefore = latest.fetchSemanticsNode().boundsInRoot
            compose.onNodeWithContentDescription("Timeline mode").performClick()
            compose.onNodeWithContentDescription("Auto advance").assertIsDisplayed()
            val handAfter = compose.onAllNodesWithContentDescription(handLabel).fetchSemanticsNodes().map { it.boundsInRoot }
            assertEquals(handBefore, handAfter, "HUD must not move on mode change")
            assertEquals(latestBefore, compose.onNode(hasContentDescription(events.last().description) and isSelected(), useUnmergedTree = true)
                .assertIsSelected().fetchSemanticsNode().boundsInRoot, "The strip must not jump on mode change")
            val transport = compose.onNodeWithContentDescription("Auto advance").fetchSemanticsNode().boundsInRoot
            assertEquals(pixels.height - 4f, transport.bottom)
            val reviewOutput = "$SCREENSHOTS/$prefix-review.png"
            compose.onRoot().captureShot(reviewOutput, "$situation, after tapping Timeline mode: history at the latest event.")
            val reviewPixels = ImageIO.read(File(reviewOutput))
            assertEquals(TimelineBarColor.toArgb(), reviewPixels.getRGB(2, 35))
            assertEquals(TimelineBarColor.toArgb(), reviewPixels.getRGB(reviewPixels.width - 2, 35))
            assertEquals(TimelineBarColor.toArgb(), reviewPixels.getRGB(2, reviewPixels.height - 2))
            compose.onNodeWithContentDescription("Return to current").assertExists()
        }
        compose.runOnIdle { review = false }
        compose.onNodeWithContentDescription("Table actions").performClick()
        compose.onNodeWithText("Timeline").assertIsDisplayed().performClick()
        compose.onNodeWithContentDescription("Auto advance").assertIsDisplayed()
    }

    // HIS-001, HIS-004, HIS-007, HIS-008, TRN-013, TRN-015, TRN-016
    @Test fun portraitBothPlayersTurnAndMoveMatrix() = chrome(false)
    @Test @Config(qualifiers = "w892dp-h412dp-land-mdpi") fun landscapeBothPlayersTurnAndMoveMatrix() = chrome(true)

    private fun maxLengthOpponentNameDoesNotCoverControls(landscape: Boolean) {
        // AccountFields permits 64 Unicode scalars; use wide characters to stress both labels.
        val username = "W".repeat(64)
        val game = TableFixture.game.copy(recipient = AccountIdentity("b", username))
        val initial = TableFixture.play(hand = 4)
        val play = initial.copy(board = initial.board.copy(attentionHolder = "b",
            turn = initial.board.turn.copy(accountId = "b")))
        val toolkit = TableToolkitController("a", { tableBoard(play) }, { true }, { _, _ -> true })
        compose.setContent { MaterialTheme {
            TableSurface(game, tableBoard(play), "a", landscape, Modifier.fillMaxSize(), connected = true,
                onIntent = toolkit::handle, toolkit = toolkit)
        } }
        val root = compose.onRoot().fetchSemanticsNode().boundsInRoot
        val scene = TablePresentation.scene(game, tableBoard(play), "a", root.width, root.height, landscape)
        val hand = scene.items.single { it.id == "b/HAND" }.bounds.let { Rect(it.x, it.y, it.right, it.bottom) }
        // Nonempty hands expose their cards, not a separate Hand semantics node.
        val cardLabel = scene.items.single { it.cardId == "b/HAND/0" }.label
        val handCards = compose.onAllNodesWithContentDescription(cardLabel)
        handCards.assertCountEquals(4)
        for (index in 0 until 4) handCards[index].assertIsDisplayed()
        val step = compose.onNodeWithContentDescription("Choose phase/step", substring = true)
        val stepBounds = step.assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        for (node in listOf(compose.onNodeWithContentDescription(game.gameName),
            compose.onNodeWithContentDescription("Waiting · $username"))) {
            val bounds = node.assertIsDisplayed().fetchSemanticsNode().boundsInRoot
            assertFalse(bounds.overlaps(hand), "Title/waiting label must not overlap opponent Hand: $bounds vs $hand")
            assertFalse(bounds.overlaps(stepBounds), "Title/waiting label must not overlap step button")
            assertTrue(bounds.right <= stepBounds.left)
            if (landscape) assertTrue(bounds.right <= hand.left)
        }
        val textLayouts = mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
        compose.onNodeWithText("Waiting · $username", useUnmergedTree = true)
            .performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.GetTextLayoutResult) { it(textLayouts) }
        assertTrue(textLayouts.single().isLineEllipsized(0), "Maximum-length name must ellipsize")
        step.performTouchInput { click() }
        compose.onNodeWithText("Beginning").assertIsDisplayed()
    }

    @Test fun portraitMaxLengthOpponentNameDoesNotCoverHandOrStep() = maxLengthOpponentNameDoesNotCoverControls(false)
    @Test @Config(qualifiers = "w892dp-h412dp-land-mdpi")
    fun landscapeMaxLengthOpponentNameDoesNotCoverHandOrStep() = maxLengthOpponentNameDoesNotCoverControls(true)

    // SYS-017
    @Test fun loadingHasSpinnerAndOnlyFailureOffersReconnect() {
        var state by mutableStateOf(LobbyState())
        compose.setContent { MaterialTheme {
            TableUnavailable("Table", false, loading = tableIsLoading(state.tableView(), "game", null), retry = {})
        } }
        compose.onNodeWithText("Loading table…").assertIsDisplayed()
        compose.onNode(hasProgressBarRangeInfo(androidx.compose.ui.semantics.ProgressBarRangeInfo.Indeterminate)).assertIsDisplayed()
        compose.onNodeWithText("Reconnect").assertDoesNotExist()
        compose.runOnIdle { state = state.copy(connectionMessage = "Connection failed") }
        compose.onNodeWithText("Table unavailable").assertIsDisplayed()
        compose.onNodeWithText("Reconnect").assertIsDisplayed()
    }

    @Test fun holdingTimelineEventShowsDescriptionWithoutSelectingOrEntering() {
        val events = listOf(HistoryTimelineEvent("root", HistoryIcon.FLAG, "The game started", "a", null))
        var selected = 0
        compose.setContent { MaterialTheme {
            HistoryTimeline(events, "root", "a", {}, { selected++ }, readOnly = true)
        } }
        val event = compose.onNodeWithContentDescription("The game started", useUnmergedTree = true)
        event.performTouchInput { down(center) }
        compose.mainClock.advanceTimeBy(650)
        compose.onNodeWithText("The game started").assertIsDisplayed()
        event.performTouchInput { up() }
        assertEquals(0, selected)
        event.performTouchInput { click() }
        compose.onNodeWithText("The game started").assertIsDisplayed()
        assertEquals(0, selected)
    }



}

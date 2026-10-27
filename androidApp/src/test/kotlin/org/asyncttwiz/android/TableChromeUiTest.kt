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
        val playback = LiveTableFixture.playback()
        val events = HistoryTimelineLayout.events(playback.tree, playback.tree.sharedHeadId)
        compose.setContent { MaterialTheme {
            val toolkit = remember(viewer) { TableToolkitController(viewer, { play }, { true }, { _, _ -> true }) }
            TableSurface(TableFixture.game, play, viewer, landscape, Modifier.fillMaxSize(),
                connected = true, onIntent = toolkit::handle, toolkit = toolkit,
                runtime = TableRuntimeHooks(navigation = {}, chat = {}, undo = { true }, reviewing = review,
                    history = { playback.scrub(playback.tree.sharedHeadId); review = true }, returnCurrent = { review = false }, continueHere = {},
                    bottomPanel = { playControls -> HistoryPlaybackPanel(playback, playback.tree.sharedHeadId, viewer, true, 60_000, {},
                        overlay = true, landscape = landscape, background = tableBarColor(play.board, viewer, review),
                        readOnly = !review, onEnter = { playback.scrub(playback.tree.sharedHeadId); review = true }, playControls = playControls,
                         onSettings = {}, onTree = {}) }))
        } }
        for (who in listOf("a", "b")) for (turn in listOf("a", "b")) for (move in listOf("a", "b")) {
            compose.runOnIdle {
                viewer = who; review = false
                val position = if (who != turn && who == move) TurnPosition.DECLARE_BLOCKERS else TurnPosition.UNTAP
                play = initial.copy(board = initial.board.copy(attentionHolder = move,
                    turn = initial.board.turn.copy(accountId = turn, phase = position.phase, step = position.step)))
            }
            val prefix = "chrome-${if (landscape) "landscape" else "portrait"}-$who-turn-$turn-move-$move"
            val visible = who == turn || who == move
            val next = compose.onNodeWithContentDescription(nextStepLabel(play.board.turn))
            if (visible) next.assertIsDisplayed() else {
                next.assertDoesNotExist()
                compose.onNodeWithText("Opponent's turn").assertIsDisplayed()
                compose.onNodeWithContentDescription("Untap all").assertDoesNotExist()
            }
            if (who != turn && who == move) compose.onNodeWithContentDescription("No blocks + next").assertIsDisplayed()
            compose.onNodeWithContentDescription("History", substring = false).assertDoesNotExist()
            val output = File("build/outputs/roborazzi/$prefix.png").also { it.parentFile!!.mkdirs() }
            compose.onRoot().captureRoboImage(output.path)
            val pixels = ImageIO.read(output)
            val tint = tableBarColor(play.board, who).toArgb()
            assertEquals(tint, pixels.getRGB(2, 35))
            assertEquals(tint, pixels.getRGB(pixels.width - 2, 35), "Tint continues to the screen edge")
            assertEquals(tint, pixels.getRGB(2, pixels.height - 2))
            if (visible) {
                val b = next.fetchSemanticsNode().boundsInRoot
                assertTrue(b.width in 68f..72f); assertEquals(48f, b.height)
                assertEquals(pixels.width - 8f, b.right, "Step button belongs in the right corner")
                assertEquals(advanceButtonStyle(play.board, who).fill.toArgb(), pixels.getRGB(b.left.toInt() + 4, b.top.toInt() + 4))
                val controller = TableActionController({ play }, { true }, { true }, RecordingRoutes())
                val actions = QuickActions.contextual(play, QuickContext(viewer = who), controller)
                val cells = actions.map { compose.onNodeWithContentDescription(it.name).fetchSemanticsNode().boundsInRoot }
                assertTrue(cells.isNotEmpty())
                for (cell in cells) assertEquals(cells.first().width, cell.width, 1f, "Equal quick action widths")
                assertEquals((if (landscape) pixels.width / 2f else 0f) + 60f, cells.first().left)
                assertEquals(b.left - 4f, cells.last().right)
            }
            val handLabel = TablePresentation.scene(TableFixture.game, play, who, pixels.width.toFloat(), pixels.height.toFloat(), landscape)
                .items.single { it.cardId == "$who/HAND/0" }.label
            val handBefore = compose.onAllNodesWithContentDescription(handLabel).fetchSemanticsNodes().map { it.boundsInRoot }
            // The read-only strip enters at the latest event; it cannot scrub live play.
            val latest = compose.onNodeWithContentDescription(events.last().description, useUnmergedTree = true)
            latest.assertIsSelected()
            val latestBefore = latest.fetchSemanticsNode().boundsInRoot
            latest.performClick()
            compose.onNodeWithContentDescription("Auto advance").assertIsDisplayed()
            val handAfter = compose.onAllNodesWithContentDescription(handLabel).fetchSemanticsNodes().map { it.boundsInRoot }
            assertEquals(handBefore, handAfter, "HUD must not move on mode change")
            assertEquals(latestBefore, compose.onNode(hasContentDescription(events.last().description) and isSelected(), useUnmergedTree = true)
                .assertIsSelected().fetchSemanticsNode().boundsInRoot, "The strip must not jump on mode change")
            val transport = compose.onNodeWithContentDescription("Auto advance").fetchSemanticsNode().boundsInRoot
            if (visible) assertEquals(pixels.height - 4f, transport.bottom)
            val reviewOutput = "build/outputs/roborazzi/$prefix-review.png"
            compose.onRoot().captureRoboImage(reviewOutput)
            val reviewPixels = ImageIO.read(File(reviewOutput))
            assertEquals(TimelineBarColor.toArgb(), reviewPixels.getRGB(2, 35))
            assertEquals(TimelineBarColor.toArgb(), reviewPixels.getRGB(reviewPixels.width - 2, 35))
            assertEquals(TimelineBarColor.toArgb(), reviewPixels.getRGB(2, reviewPixels.height - 2))
            assertEquals(HistoryMode.PLAYBACK, playback.position.mode, "Opening at the latest event stays in timeline")
        }
        compose.runOnIdle { review = false }
        compose.onNodeWithContentDescription("Table actions").performClick()
        compose.onNodeWithText("Timeline").assertIsDisplayed().performClick()
        compose.onNodeWithContentDescription("Auto advance").assertIsDisplayed()
    }

    @Test fun portraitBothPlayersTurnAndMoveMatrix() = chrome(false)
    @Test @Config(qualifiers = "w892dp-h412dp-land-mdpi") fun landscapeBothPlayersTurnAndMoveMatrix() = chrome(true)

    private fun maxLengthOpponentNameDoesNotCoverControls(landscape: Boolean) {
        // AccountFields permits 64 Unicode scalars; use wide characters to stress both labels.
        val username = "W".repeat(64)
        val game = TableFixture.game.copy(recipient = AccountIdentity("b", username))
        val initial = TableFixture.play(hand = 4)
        val play = initial.copy(board = initial.board.copy(attentionHolder = "b",
            turn = initial.board.turn.copy(accountId = "b")))
        val toolkit = TableToolkitController("a", { play }, { true }, { _, _ -> true })
        compose.setContent { MaterialTheme {
            TableSurface(game, play, "a", landscape, Modifier.fillMaxSize(), connected = true,
                onIntent = toolkit::handle, toolkit = toolkit)
        } }
        val root = compose.onRoot().fetchSemanticsNode().boundsInRoot
        val scene = TablePresentation.scene(game, play, "a", root.width, root.height, landscape)
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

    @Test fun loadingHasSpinnerAndOnlyFailureOffersReconnect() {
        var state by mutableStateOf(LobbyState())
        compose.setContent { MaterialTheme {
            TableUnavailable("Table", false, loading = tableIsLoading(state, "game", null), retry = {})
        } }
        compose.onNodeWithText("Loading table…").assertIsDisplayed()
        compose.onNode(hasProgressBarRangeInfo(androidx.compose.ui.semantics.ProgressBarRangeInfo.Indeterminate)).assertIsDisplayed()
        compose.onNodeWithText("Reconnect").assertDoesNotExist()
        compose.runOnIdle { state = state.copy(connectionMessage = "Connection failed") }
        compose.onNodeWithText("Table unavailable").assertIsDisplayed()
        compose.onNodeWithText("Reconnect").assertIsDisplayed()
    }

    @Test fun holdingTimelineEventShowsDescriptionWithoutSelectingOrEntering() {
        val events = listOf(HistoryTimelineEvent("root", "flag", "The game started", "a", null))
        var entered = 0
        var selected = 0
        compose.setContent { MaterialTheme {
            HistoryTimeline(events, "root", "a", {}, { selected++ }, readOnly = true, onEnter = { entered++ })
        } }
        val event = compose.onNodeWithContentDescription("The game started", useUnmergedTree = true)
        event.performTouchInput { down(center) }
        compose.mainClock.advanceTimeBy(650)
        compose.onNodeWithText("The game started").assertIsDisplayed()
        event.performTouchInput { up() }
        assertEquals(0, entered); assertEquals(0, selected)
        event.performTouchInput { click() }
        assertEquals(1, entered); assertEquals(0, selected)
    }

    @Test fun readOnlyStripShowsPressedTintAcrossEmptySpace() {
        val playback = LiveTableFixture.playback()
        var entered = 0
        compose.setContent { MaterialTheme {
            HistoryPlaybackPanel(playback, playback.tree.sharedHeadId, "a", true, 60_000, {},
                readOnly = true, onEnter = { entered++ }, onSettings = {}, onTree = {})
        } }
        compose.onNodeWithContentDescription("Playback settings").assertDoesNotExist()
        compose.onNodeWithContentDescription("Open game tree").assertDoesNotExist()
        compose.onNodeWithContentDescription("History options").assertDoesNotExist()
        val output = File("build/outputs/roborazzi/timeline-strip-pressed.png").also { it.parentFile!!.mkdirs() }
        compose.onRoot().captureRoboImage(output.path)
        val before = ImageIO.read(output)
        compose.mainClock.autoAdvance = false
        compose.onRoot().performTouchInput { down(androidx.compose.ui.geometry.Offset(350f, 24f)) }
        compose.mainClock.advanceTimeBy(300)
        compose.onRoot().captureRoboImage(output.path)
        val pressed = ImageIO.read(output)
        assertNotEquals(before.getRGB(350, 24), pressed.getRGB(350, 24))
        assertNotEquals(before.getRGB(50, 24), pressed.getRGB(50, 24), "Empty strip space shares press feedback")
        compose.onRoot().performTouchInput { up() }
        compose.mainClock.autoAdvance = true
        assertEquals(1, entered)
    }

    @Test fun controlsCrossfadeWithoutMovingTheStripOrChangingPanelHeight() {
        val playback = LiveTableFixture.playback().apply { scrub(tree.sharedHeadId) }
        var live by mutableStateOf(true)
        compose.setContent { MaterialTheme {
            HistoryPlaybackPanel(playback, playback.tree.sharedHeadId, "a", true, 60_000, {},
                readOnly = live, playControls = { androidx.compose.material3.Text("Quick actions") },
                onSettings = {}, onTree = {})
        } }
        val selected = hasContentDescription(HistoryTimelineLayout.events(playback.tree, playback.tree.sharedHeadId).last().description) and isSelected()
        val before = compose.onNode(selected, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val panelBefore = compose.onRoot().fetchSemanticsNode().boundsInRoot
        assertEquals(panelBefore.center.x, before.center.x, "Selection is centered on the whole row, not the space before controls")
        compose.onNodeWithContentDescription("History options").assertDoesNotExist()
        compose.mainClock.autoAdvance = false
        compose.runOnIdle { live = false }
        // Commit the target-state recomposition before measuring animation time.
        compose.mainClock.advanceTimeByFrame()
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(100)
        compose.waitForIdle()
        compose.onNodeWithText("Quick actions").assertExists()
        compose.onNodeWithContentDescription("Auto advance").assertExists()
        compose.onNodeWithContentDescription("History options").assertDoesNotExist()
        compose.onNodeWithText("Event details").assertDoesNotExist()
        compose.onNodeWithText("Download history").assertDoesNotExist()
        assertEquals(before, compose.onNode(selected, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot)
        compose.mainClock.advanceTimeBy(150)
        compose.onNodeWithText("Quick actions").assertDoesNotExist()
        assertEquals(panelBefore, compose.onRoot().fetchSemanticsNode().boundsInRoot)
        compose.mainClock.autoAdvance = true
    }
}

package org.asyncttwiz.android

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureScreenRoboImage
import org.asyncttwiz.protocol.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.util.concurrent.Executor
import javax.imageio.ImageIO
import kotlin.test.*

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w412dp-h892dp-port-mdpi")
class TableStatesScreenshotTest {
    @get:Rule val compose = createComposeRule()
    private val f = TableStatesFixture

    @OptIn(ExperimentalRoborazziApi::class)
    private fun render(kind: String, landscape: Boolean): List<PlayAction> {
        val tree = f.history()
        val sent = mutableListOf<PlayAction>()
        val activations = mutableListOf<GameAction>()
        val history = TableHistoryController(Executor { it.run() }, { it() }, { node, complete ->
            val cursor = node ?: tree.currentNodeId
            complete(DownloadedHistoryState(1L, cursor, state = tree.stateAt(cursor))); true
        }, { action, _, _ -> sent += action; true }, { activations += it; true })
        history.update(tree, "fixture", f.game, f.a)
        val playback = history.playback!!
        val path = playback.tree.path(tree.currentNodeId)
        val review = kind.startsWith("history") || kind in listOf("continue", "continue-confirmation", "tree-viewer")
        if (review) history.select(when (kind) { "history-start" -> path.first(); "history-end" -> path.last(); else -> path[3] })
        history.displayPosition()
        if (kind == "history-playing") playback.play()
        val current = { if (review) requireNotNull(tree.stateAt(playback.position.cursorId))
            else if (kind == "graveyard-contents") f.initial else f.combat() }
        val toolkit = TableToolkitController(f.a, current, { !review || history.armed != null || playback.position.mode == HistoryMode.LIVE },
            { action, _ -> sent += action; true })
        if (kind == "combat") toolkit.selectionState.value = TableSelection(setOf(f.source(f.a, "1/3").id, f.source(f.a, "1/2").id), mode = TableMode.SELECT)
        val explorer = HistoryExplorerController(Executor { it.run() }, { it() }, { true }, { _, action -> activations += action; true })
        explorer.update(tree, openingNodeId = tree.nodes.values.first { it.requestId == f.id("request5") }.id)
        compose.setContent {
            MaterialTheme {
                if (kind in listOf("placeholder", "unavailable")) TableUnavailable(f.game.gameName, landscape,
                    loading = kind == "placeholder", navigation = {}, retry = {})
                else {
                    val reviewing = review && playback.position.mode == HistoryMode.PLAYBACK && history.armed == null
                    LaunchedEffect(playback.position) { history.displayPosition() }
                    Box(Modifier.fillMaxSize()) {
                        TableSurface(f.game, current(), f.a, landscape, Modifier.fillMaxSize(), connected = !reviewing,
                            onIntent = toolkit::handle, toolkit = toolkit,
                            runtime = TableRuntimeHooks(navigation = {}, chat = {}, history = { history.select(playback.position.cursorId) },
                                tree = {}, undo = { true }, redo = { true },
                                controlsConnected = true, reviewing = reviewing, continuationArmed = history.armed != null,
                                continueHere = if (reviewing) ({ history.requestContinuation(playback.position.cursorId) }) else null,
                                returnCurrent = if (review) history::current else null,
                                reviewPanel = if (reviewing) ({ HistoryPlaybackPanel(playback, tree.currentNodeId, f.a, true, 60_000L,
                                    { history.requestContinuation(it) }, overlay = true, landscape = landscape) }) else null))
                        history.armed?.let { boundary -> HistoryArmedIndicator(playback.tree.texts.getValue(boundary).label,
                            landscape, history::cancelContinuation) }
                    }
                    if (history.continuationTarget != null) HistoryContinueConfirmation(true,
                        history::dismissContinuation, { history.confirmContinuation() })
                    if (kind == "tree-viewer") HistoryExplorer(explorer, true, mapOf(f.a to OwnTableColor, f.b to OpponentTableColor),
                        false, {}, {}, { playback.scrub(it.currentNodeId) }, texts = playback.tree.texts)
                }
            }
        }
        compose.waitForIdle()
        when (kind) {
            "continue", "continue-confirmation" -> {
                compose.onNodeWithContentDescription("Continue playing from here").performClick()
                if (kind == "continue") {
                    compose.onNodeWithText("Continue", substring = false).performClick()
                    compose.onNodeWithContentDescription("Interaction mode").assertExists()
                    compose.onNodeWithContentDescription("Play").assertDoesNotExist()
                    compose.onNodeWithContentDescription("History · continuation armed").assertIsDisplayed()
                    compose.onNodeWithText("Continuing from Life · next action starts a new path").assertIsDisplayed()
                    compose.onNodeWithText("Cancel", substring = false).assertIsDisplayed()
                }
            }
            "stack-contents" -> {
                compose.onNodeWithContentDescription("Stack, 2 entries").performClick()
                compose.onNodeWithText("Stack · 2 entries").assertExists()
            }
            "graveyard-contents" -> {
                compose.onNodeWithContentDescription("Alice graveyard, 2 cards").performClick()
                compose.onNodeWithText("Graveyard · 2").assertIsDisplayed()
                compose.onNodeWithText("Search by name").assertIsDisplayed()
                compose.onNodeWithText("Creature", substring = false).assertIsDisplayed()
                compose.onNodeWithText("Meadow Sentinel", substring = false).assertIsDisplayed()
            }
            "history-start" -> compose.onNodeWithContentDescription("Previous").assertIsNotEnabled()
            "history-end" -> compose.onNodeWithContentDescription("Next").assertIsNotEnabled()
        }
        compose.waitForIdle()
        assertTrue(sent.isEmpty()); assertTrue(activations.isEmpty())
        val file = File("build/outputs/roborazzi/gameplay-$kind-${if (landscape) "landscape" else "portrait"}.png")
        file.parentFile!!.mkdirs()
        captureScreenRoboImage(file.path)
        val image = ImageIO.read(file)
        assertEquals(if (landscape) 892 else 412, image.width)
        assertEquals(if (landscape) 412 else 892, image.height)
        explorer.close()
        return sent
    }

    @Test fun combatPortrait() { render("combat", false) }
    @Test @Config(qualifiers = "w892dp-h412dp-land-mdpi") fun combatLandscape() { render("combat", true) }
    @Test fun stackPortrait() { render("stack-contents", false) }
    @Test fun graveyardBrowserPortrait() { render("graveyard-contents", false) }
    @Test fun graveyardSearchAndTapUseExistingCardActionMenu() {
        val sent = render("graveyard-contents", false)
        compose.onNodeWithText("Search by name").performTextInput("Sentinel")
        compose.onNodeWithText("Meadow Sentinel", substring = false).performClick()
        compose.mainClock.advanceTimeBy(400) // Normal single tap waits for the double-tap window.
        compose.onNodeWithContentDescription("Graveyard · Alice").assertExists()
        compose.onNodeWithContentDescription("Library top · Alice").assertExists()
        compose.onNodeWithContentDescription("Library bottom · Alice").assertExists()
        compose.onNodeWithContentDescription("Exile · Alice").assertExists()
        assertTrue(sent.isEmpty())
    }
    @Test fun graveyardHoldInspectsWithoutOpeningActionsOrLogging() {
        val sent = render("graveyard-contents", false)
        compose.onNodeWithText("Meadow Sentinel", substring = false).performTouchInput { longClick() }
        compose.onNodeWithContentDescription("Graveyard · Alice").assertDoesNotExist()
        compose.onNodeWithText("Creature — Soldier", substring = false).assertExists()
        assertTrue(sent.isEmpty())
    }
    @Test fun graveyardDoubleTapOnlyRotatesTheAddressedCard() {
        val sent = render("graveyard-contents", false)
        compose.onNodeWithText("Meadow Sentinel", substring = false).performTouchInput { doubleClick() }
        compose.mainClock.advanceTimeBy(400)
        val card = f.initial.board.cards.single { it.containerId == f.id("a/GRAVEYARD") && it.corner == "1/3" }
        assertEquals(listOf<PlayAction>(PlayAction.SetTapped(card.id, true)), sent)
        compose.onNodeWithContentDescription("Graveyard · Alice").assertDoesNotExist()
        compose.onNodeWithText("Graveyard · 2").assertIsDisplayed()
    }
    @Test @Config(qualifiers = "w892dp-h412dp-land-mdpi") fun stackLandscape() { render("stack-contents", true) }
    @Test fun historyStartPortrait() { render("history-start", false) }
    @Test @Config(qualifiers = "w892dp-h412dp-land-mdpi") fun historyStartLandscape() { render("history-start", true) }
    @Test fun historyPlayingPortrait() { render("history-playing", false) }
    @Test @Config(qualifiers = "w892dp-h412dp-land-mdpi") fun historyPlayingLandscape() { render("history-playing", true) }
    @Test fun historyPausedPortrait() { render("history-paused", false) }
    @Test @Config(qualifiers = "w892dp-h412dp-land-mdpi") fun historyPausedLandscape() { render("history-paused", true) }
    @Test fun historyEndPortrait() { render("history-end", false) }
    @Test @Config(qualifiers = "w892dp-h412dp-land-mdpi") fun historyEndLandscape() { render("history-end", true) }
    @Test fun continuePortrait() { render("continue", false) }
    @Test @Config(qualifiers = "w892dp-h412dp-land-mdpi") fun continueLandscape() { render("continue", true) }
    @Test fun cancelContinuationReturnsToLiveWithoutMutationPortrait() { cancelContinuation(false) }
    @Test @Config(qualifiers = "w892dp-h412dp-land-mdpi") fun cancelContinuationReturnsToLiveWithoutMutationLandscape() { cancelContinuation(true) }
    @Test fun confirmationPortrait() { render("continue-confirmation", false) }
    @Test @Config(qualifiers = "w892dp-h412dp-land-mdpi") fun confirmationLandscape() { render("continue-confirmation", true) }
    @Test fun treePortrait() { render("tree-viewer", false) }
    @Test @Config(qualifiers = "w892dp-h412dp-land-mdpi") fun treeLandscape() { render("tree-viewer", true) }
    @Test fun loadingPortrait() { render("placeholder", false) }
    @Test @Config(qualifiers = "w892dp-h412dp-land-mdpi") fun loadingLandscape() { render("placeholder", true) }
    @Test fun unavailablePortrait() { render("unavailable", false) }
    @Test @Config(qualifiers = "w892dp-h412dp-land-mdpi") fun unavailableLandscape() { render("unavailable", true) }

    private fun cancelContinuation(landscape: Boolean) {
        val sent = render("continue", landscape)
        compose.onNodeWithText("Cancel", substring = false).performClick()
        compose.onNodeWithText("Continuing from", substring = true).assertDoesNotExist()
        compose.onNodeWithContentDescription("History · continuation armed").assertDoesNotExist()
        compose.onNodeWithContentDescription("History", substring = false).assertIsDisplayed()
        compose.onNodeWithContentDescription("Play", substring = false).assertDoesNotExist()
        compose.onNodeWithContentDescription("Continue playing from here").assertDoesNotExist()
        // The latest live position has a hand card moved onto the battlefield, unlike the armed boundary.
        val tree = f.history()
        val live = tree.stateAt(tree.currentNodeId)!!
        val battlefield = live.board.containers.single { it.zone == PlayZone.BATTLEFIELD && it.accountId == f.a }
        compose.onNodeWithContentDescription("Alice Battlefield, ${battlefield.cards.size} cards").assertExists()
        compose.runOnIdle { assertTrue(sent.isEmpty()) }
    }

    @Test fun blankOpponentCounterOpensPlayerActionsWithoutMutation() {
        render("combat", false)
        compose.onNodeWithContentDescription("Bob, red, no value").performClick()
        compose.onNodeWithText("Life / counters").assertExists()
    }

    @Test fun pausedPlayHoldConsumesTapAndRequestsContinuation() {
        render("history-paused", false)
        compose.onNodeWithContentDescription("Play").performTouchInput { down(center) }
        compose.mainClock.advanceTimeBy(700)
        compose.onNodeWithContentDescription("Play").performTouchInput { up() }
        compose.onNodeWithText("Continue playing from here?").assertExists()
        compose.onNodeWithText("Cancel").performClick()
        compose.onNodeWithText("Paused", substring = true).assertExists()
    }

    @Test fun treeSelectionReviewsGroupEndAndOnlyMakeCurrentSubmits() {
        val tree = f.history()
        val submissions = mutableListOf<GameAction>()
        val explorer = HistoryExplorerController(Executor { it.run() }, { it() }, { true }, { _, action -> submissions += action; true })
        explorer.update(tree)
        val range = explorer.state.graph!!.ranges.single { it.nodeIds.any { id -> tree.nodes[id]?.requestId == f.id("request5") } }
        assertNotEquals(range.representativeId, range.targetId)
        val texts = f.playback(tree).tree.texts
        var open by mutableStateOf(true)
        compose.setContent { MaterialTheme {
            if (open) HistoryExplorer(explorer, true, mapOf(f.a to OwnTableColor, f.b to OpponentTableColor),
                false, {}, { open = false }, {}, texts = texts)
        } }
        val group = compose.onNodeWithContentDescription("${historyRangeText(range, texts).description}, ${range.nodeIds.size} events, review group end")
        val before = group.fetchSemanticsNode().positionInRoot
        compose.onNodeWithContentDescription("Game history group tree").performTouchInput {
            swipe(center, center + androidx.compose.ui.geometry.Offset(-30f, 20f), 200)
        }
        compose.runOnIdle {
            assertEquals(tree.currentNodeId, explorer.state.selectedNodeId)
            assertTrue(submissions.isEmpty())
        }
        assertNotEquals(before, group.fetchSemanticsNode().positionInRoot)
        group.performClick()
        compose.runOnIdle {
            assertEquals(range.targetId, explorer.state.selectedNodeId)
            assertTrue(submissions.isEmpty())
        }
        compose.onNodeWithContentDescription("Make current").performClick()
        compose.runOnIdle { assertEquals(listOf<GameAction>(GameAction.MakeCurrent(range.targetId)), submissions) }
        compose.onNodeWithContentDescription("Close game tree").performClick()
        compose.onNodeWithText("Game tree ▾").assertDoesNotExist()
        explorer.close()
    }
}

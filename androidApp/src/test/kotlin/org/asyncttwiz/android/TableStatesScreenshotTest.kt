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
        val playback = f.playback(tree)
        val path = playback.tree.path(tree.currentNodeId)
        val review = kind.startsWith("history") || kind in listOf("continue", "continue-confirmation", "tree-viewer")
        playback.scrub(when (kind) { "history-start" -> path.first(); "history-end" -> path.last(); else -> path[3] })
        if (kind == "history-playing") playback.play()
        var armed by mutableStateOf(false)
        var confirmation by mutableStateOf(false)
        val sent = mutableListOf<PlayAction>()
        val current = { if (review) requireNotNull(tree.stateAt(playback.position.cursorId)) else f.combat() }
        val toolkit = TableToolkitController(f.a, current, { !review || armed }, { action, _ -> sent += action; true })
        if (kind == "combat") toolkit.selectionState.value = TableSelection(setOf(f.source(f.a, "1/3").id, f.source(f.a, "1/2").id), mode = TableMode.SELECT)
        val activations = mutableListOf<GameAction>()
        val explorer = HistoryExplorerController(Executor { it.run() }, { it() }, { true }, { _, action -> activations += action; true })
        explorer.update(tree, openingNodeId = tree.nodes.values.first { it.requestId == f.id("request5") }.id)
        compose.setContent {
            MaterialTheme {
                if (kind in listOf("placeholder", "unavailable")) TableUnavailable(f.game.gameName, landscape,
                    loading = kind == "placeholder", navigation = {}, retry = {})
                else {
                    TableSurface(f.game, current(), f.a, landscape, Modifier.fillMaxSize(), connected = !review || armed,
                        onIntent = toolkit::handle, toolkit = toolkit,
                        runtime = TableRuntimeHooks(navigation = {}, chat = {}, history = {}, tree = {}, undo = { true }, redo = { true },
                            controlsConnected = true, reviewing = review && !armed,
                            continueHere = if (review && !armed) ({ playback.pause(); confirmation = true }) else null,
                            returnCurrent = if (review) ({ playback.play() }) else null,
                            reviewPanel = if (review && !armed) ({ HistoryPlaybackPanel(playback, tree.currentNodeId, f.a, true, 60_000L,
                                { confirmation = true }, overlay = true, landscape = landscape) }) else null))
                    if (confirmation) HistoryContinueConfirmation(true, { confirmation = false }, { armed = true; confirmation = false })
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
                }
            }
            "stack-contents" -> {
                compose.onNodeWithContentDescription("Stack, 2 entries").performClick()
                compose.onNodeWithText("Stack · 2 entries").assertExists()
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
    @Test fun confirmationPortrait() { render("continue-confirmation", false) }
    @Test @Config(qualifiers = "w892dp-h412dp-land-mdpi") fun confirmationLandscape() { render("continue-confirmation", true) }
    @Test fun treePortrait() { render("tree-viewer", false) }
    @Test @Config(qualifiers = "w892dp-h412dp-land-mdpi") fun treeLandscape() { render("tree-viewer", true) }
    @Test fun loadingPortrait() { render("placeholder", false) }
    @Test @Config(qualifiers = "w892dp-h412dp-land-mdpi") fun loadingLandscape() { render("placeholder", true) }
    @Test fun unavailablePortrait() { render("unavailable", false) }
    @Test @Config(qualifiers = "w892dp-h412dp-land-mdpi") fun unavailableLandscape() { render("unavailable", true) }

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

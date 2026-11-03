package org.asyncttwiz.android

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
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
    private val combatTargetsState = f.combat().let { play ->
        val target = f.source(f.b, "2/3")
        val original = play.definitions.single { it.digest == target.definition }
        val definition = original.definition.copy(faces = original.definition.faces.map {
            it.copy(name = "Jace Beleren", typeLine = "Planeswalker — Jace") })
        val submitted = SubmittedDefinition(DeckJson.digest(definition), definition)
        play.copy(definitions = play.definitions + submitted, board = play.board.copy(
            cards = play.board.cards.map { if (it.id == target.id) it.copy(definition = submitted.digest) else it },
            combat = play.board.combat!!.let { area -> area.copy(pairs = area.pairs.mapIndexed { index, pair ->
                if (index == 0) pair.copy(attackTarget = target.id) else pair }) }))
    }
    private val graveyardState = f.initial.let { play ->
        val graveyard = f.id("a/GRAVEYARD")
        val river = play.board.cards.single { it.containerId == f.id("a/HAND") && it.corner == "1/2" }
        play.copy(board = play.board.copy(
            cards = play.board.cards.map { if (it.id == river.id) it.copy(containerId = graveyard, covered = false) else it },
            containers = play.board.containers.map { container -> when (container.id) {
                graveyard -> container.copy(cards = listOf(river.id) + container.cards)
                river.containerId -> container.copy(cards = container.cards - river.id)
                else -> container
            } }))
    }

    @OptIn(ExperimentalRoborazziApi::class)
    private fun render(kind: String, landscape: Boolean): List<PlayAction> {
        val tree = f.history()
        val sent = mutableListOf<PlayAction>()
        val history = TableHistoryController(Executor { it.run() }, { it() }, { node, complete ->
            val cursor = node ?: tree.currentNodeId
            complete(DownloadedHistoryState(1L, cursor, state = tree.stateAt(cursor))); true
        }, { action, _, _ -> sent += action; true })
        history.update(tree, "fixture", f.game, f.a)
        val playback = history.playback!!
        val path = playback.tree.path(tree.currentNodeId)
        val review = kind.startsWith("history") || kind in listOf("continue", "tree-viewer")
        if (review) history.select(when (kind) { "history-start" -> path.first(); "history-end" -> path.last(); else -> path[3] })
        history.displayPosition()
        if (kind == "history-playing") playback.play()
        val tokenState = f.combat().let { state ->
            val actual = state.board.containers.single { it.zone == PlayZone.STACK }.cards.first { id -> state.board.cards.any { it.id == id } }
            val tokenIds = listOf(f.source(f.a, "1/3").id, f.source(f.b, "2/3").id,
                state.board.containers.single { it.accountId == f.a && it.zone == PlayZone.HAND }.cards.first()) +
                if (kind == "tokens-stack-card") listOf(actual) else emptyList()
            state.copy(board = state.board.copy(cards = state.board.cards.map { it.copy(token = it.id in tokenIds) },
                containers = state.board.containers.map { if (kind == "tokens-stack-card" && it.zone == PlayZone.STACK)
                    it.copy(cards = it.cards.reversed()) else it },
                stackEffects = state.board.stackEffects.map { it.copy(sourceId = f.source(f.a, "1/3").id) }))
        }
        val current = { if (kind == "combat-targets") combatTargetsState
            else if (kind.startsWith("tokens")) tokenState
            else if (review) requireNotNull(tree.stateAt(playback.position.cursorId))
            else if (kind == "graveyard-contents") graveyardState else f.combat() }
        val toolkit = TableToolkitController(f.a, current, { !review || history.armed != null || playback.position.mode == HistoryMode.LIVE },
            { action, _ -> sent += action; true })
        if (kind == "tokens-small") toolkit.layoutState.value = TableLayoutState(TableCamera(zoom = TablePresentation.MIN_ZOOM))
        val explorer = HistoryExplorerController(Executor { it.run() }, { it() })
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
                                undo = { true },
                                controlsConnected = true, reviewing = reviewing, continuationArmed = history.armed != null,
                                 returnCurrent = if (review) history::current else null,
                                 cancelContinuation = history::cancelContinuation,
                                reviewPanel = if (reviewing) ({ HistoryPlaybackPanel(playback, tree.currentNodeId, f.a, true, 60_000L,
                                    { history.requestContinuation(it) }, overlay = true, landscape = landscape) }) else null))
                    }
                    if (kind == "tree-viewer") HistoryExplorer(explorer, mapOf(f.a to OwnTableColor, f.b to OpponentTableColor),
                        {}, { playback.scrub(it.currentNodeId) }, texts = playback.tree.texts)
                }
            }
        }
        compose.waitForIdle()
        when (kind) {
            "tokens-contents" -> compose.onNodeWithContentDescription("Stack, 2 entries").performClick()
            "tokens-menu" -> compose.runOnIdle { toolkit.openCard(TableScope(f.source(f.a, "1/3").id, listOf(f.source(f.a, "1/3").id))) }
            "tokens-inspector" -> compose.runOnIdle { toolkit.inspect(f.source(f.a, "1/3").id) }
            "continue" -> {
                // HIS-036: holding Auto is the only way to branch.
                compose.onNodeWithContentDescription("Continue playing from here").assertDoesNotExist()
                compose.onNodeWithContentDescription("Auto advance").performTouchInput { longClick() }
                compose.onNodeWithContentDescription("Undo").assertExists()
                compose.onNodeWithContentDescription("Auto advance").assertDoesNotExist()
                compose.onNodeWithContentDescription("Cancel continuation").assertIsDisplayed()
                compose.onNodeWithText("Continuing from", substring = true).assertDoesNotExist()
                compose.onNode(isDialog()).assertDoesNotExist()
            }
            "stack-contents" -> {
                compose.onNodeWithContentDescription("Stack, 2 entries").performTouchInput { doubleClick() }
                compose.runOnIdle { assertEquals("manageStack", toolkit.panel) }
                compose.onNodeWithText("Stack · 2").assertExists()
            }
            "graveyard-contents" -> {
                // A pile tap opens its menu; the double-tap opens the search panel.
                compose.onNodeWithContentDescription("Alice graveyard, 3 cards").performTouchInput { doubleClick() }
                compose.mainClock.advanceTimeBy(500)
                compose.onNodeWithContentDescription("Your Graveyard panel, 3 cards").assertIsDisplayed()
                compose.onNode(isDialog()).assertDoesNotExist()
                compose.onNodeWithContentDescription("Card action ring").assertDoesNotExist()
                val scene = TablePresentation.scene(f.game, current(), f.a, 412f, 892f, landscape, toolkit.layoutState.value)
                val panel = scene.cardPanels.single()
                assertEquals(PlayZone.GRAVEYARD, panel.localZone)
                assertEquals(0f, panel.bounds.x)
                // Headers and names are canvas-rendered; verify the geometry used to draw them.
                assertEquals(listOf("Creature (2)", "Land (1)"), panel.typeHeaders.map { it.title })
                assertEquals(listOf("Meadow Sentinel", "River Adept", "Forest"), panel.rows.map { it.entry.name })
                for (name in listOf("Meadow Sentinel", "River Adept", "Forest"))
                    compose.onNodeWithContentDescription("Graveyard panel: $name").assertIsDisplayed()
            }
            "history-start" -> compose.onNodeWithContentDescription("Previous").assertIsNotEnabled()
            "history-end" -> compose.onNodeWithContentDescription("Next").assertIsNotEnabled()
        }
        if (kind.startsWith("history")) {
            compose.onNodeWithText("Timeline mode").assertIsDisplayed()
            compose.onNodeWithText(if (kind == "history-playing") "Auto: on" else "Auto: off").assertIsDisplayed()
            compose.onNodeWithText("Hold to branch").assertIsDisplayed() // HIS-013
        }
        if (kind == "tree-viewer") {
            compose.onNodeWithText("Make current").assertDoesNotExist()
            compose.onNodeWithText("Continue from here").assertDoesNotExist()
            compose.onNodeWithText("Branch details…").assertDoesNotExist()
        }
        compose.waitForIdle()
        assertTrue(sent.isEmpty())
        val file = File("$SCREENSHOTS/states-$kind-${if (landscape) "landscape" else "portrait"}.png")
        file.parentFile!!.mkdirs()
        val combat = "in combat at declare attackers"
        val shown = when (kind) {
            "combat" -> "$combat, one attacker blocked; the Stack holds an effect and a card"
            "combat-targets" -> "$combat, the first attack aimed at the opponent's Jace Beleren planeswalker, in its own lane"
            "tokens" -> "$combat, where your attacker, the opponent's blocker and one hand card are tokens"
            "tokens-small" -> "$combat with several tokens, zoomed out as far as possible"
            "tokens-contents" -> "$combat with several tokens, after tapping the two-entry Stack"
            "tokens-menu" -> "$combat with several tokens, card menu open on your token attacker"
            "tokens-inspector" -> "$combat with several tokens, Inspect view open for your token attacker"
            "tokens-stack-card" -> "$combat with several tokens, a token card on top of the Stack"
            "stack-contents" -> "$combat, after double-tapping the Stack: Manage Stack lists its two entries"
            "graveyard-contents" -> "after double-tapping your three-card graveyard: its panel lists the cards grouped by type"
            "history-start" -> "reviewing history at the game's start, auto-advance off"
            "history-playing" -> "reviewing history from the third event with auto-advance playing"
            "history-paused" -> "reviewing history paused at the third event"
            "history-end" -> "reviewing history at the latest event, auto-advance off"
            "continue" -> "reviewing history at the third event, after holding Auto to continue playing from there"
            "tree-viewer" -> "reviewing history at the third event with the history tree viewer open"
            "placeholder" -> "placeholder showing Loading table… with a spinner"
            "unavailable" -> "placeholder showing Table unavailable with a Reconnect button"
            else -> error("No caption for $kind")
        }
        captureShot(file.path, "${if (landscape) "Landscape" else "Portrait"} table $shown.")
        val image = ImageIO.read(file)
        assertEquals(if (landscape) 892 else 412, image.width)
        assertEquals(if (landscape) 412 else 892, image.height)
        if (kind.startsWith("combat")) {
            compose.onNodeWithContentDescription("Table actions").assertIsDisplayed().assertIsEnabled()
            val scene = TablePresentation.scene(f.game, current(), f.a, image.width.toFloat(), image.height.toFloat(), landscape,
                landscapeHome = true)
            if (kind == "combat") assertRenderedLandsBands(image, scene, f.a, turnPlayer = current().board.turn.accountId)
            // the title size of the area's cards, never smaller than COMBAT_MIN_CARD (combat review #12)
            assertTrue(scene.combatLabels.all { it.fontSize in 10.5f..11.5f })
            val cards = scene.items.filter { it.projection && it.viewport == "combat" }
            assertTrue(cards.isNotEmpty())
            assertTrue(scene.combatLabels.all { label -> cards.all { label.bounds.bottom < it.bounds.y } })
            if (kind == "combat-targets") assertEquals(1, scene.combatLaneDividers.size)
            // Card-slot dividers start below the lane headers; lane dividers cross the header row too.
            assertTrue(scene.combatDividers.all { (from, _) -> scene.combatLabels.all { from.y >= it.bounds.bottom - .01f } })
            assertTrue(scene.combatLaneDividers.all { (from, _) -> scene.combatLabels.all { from.y <= it.bounds.y + .01f } })
        }
        explorer.close()
        return sent
    }

    // CMB-008, CMB-013, CMB-014, STK-008, STK-018, STK-021
    @Test fun combatPortrait() { render("combat", false) }
    @Test fun combatCardTargetPortrait() { render("combat-targets", false) }
    @Test @Config(qualifiers = "w892dp-h412dp-land-mdpi") fun combatCardTargetLandscape() { render("combat-targets", true) }
    @Test fun manageStackReflectsLiveMembershipAndClosesWhenEmpty() {
        var state by mutableStateOf(f.combat())
        val ui = TableToolkitController(f.a, { state }, { true }, { _, _ -> error("Browsing is local") })
        ui.stackContents()
        compose.setContent { MaterialTheme {
            TableToolkitScreen(ui, state, f.game, false)
        } }
        compose.onNodeWithText("Stack · 2").assertIsDisplayed()
        compose.runOnIdle {
            state = state.copy(board = state.board.copy(stackEffects = emptyList(), containers = state.board.containers.map {
                if (it.zone == PlayZone.STACK) it.copy(cards = it.cards - f.id("effect")) else it }))
        }
        compose.onNodeWithText("Stack · 1").assertIsDisplayed()
        compose.runOnIdle { state = f.initial }
        compose.onNodeWithText("Stack · 1").assertDoesNotExist()
        compose.runOnIdle { assertNull(ui.panel) }
    }
    // STK-021
    @OptIn(ExperimentalRoborazziApi::class)
    @Test fun manageStackPeekAttachesAboveTheManageDialogAndReleasesBackToIt() {
        val state = f.combat()
        compose.setContent { MaterialTheme {
            ManageStackDialog(state, f.a, {})
        } }
        compose.onNodeWithText("Stack · 2").assertIsDisplayed()
        val dialog = android.view.inspector.WindowInspector.getGlobalWindowViews().last()
        val row = compose.onNodeWithText("Top ·", substring = true)
        compose.mainClock.autoAdvance = false
        compose.mainClock.advanceTimeBy(350)
        row.performTouchInput { advanceEventTime(350); down(center) }
        compose.mainClock.advanceTimeBy(600)
        compose.onNodeWithContentDescription("Card inspection content").assertIsDisplayed()
        val popup = android.view.inspector.WindowInspector.getGlobalWindowViews().last()
        assertEquals(dialog.windowToken, (popup.layoutParams as android.view.WindowManager.LayoutParams).token,
            "The inspection popup must belong to the manage dialog, not the activity underneath it")
        val file = File("$SCREENSHOTS/manage-stack-inspection-portrait.png")
        file.parentFile!!.mkdirs()
        captureShot(file.path, "Manage Stack dialog in combat with two entries, holding the top row: " +
            "its card peek opens above the dialog.")
        row.performTouchInput { up() }
        compose.mainClock.autoAdvance = true
        compose.onNodeWithContentDescription("Card inspection content").assertDoesNotExist()
        compose.onNodeWithText("Stack · 2").assertIsDisplayed()
    }
    @OptIn(ExperimentalRoborazziApi::class)
    @Test fun tokensOnBattlefieldHandStackAndCombatPortrait() { render("tokens", false) }
    @Test fun tokensAtSmallestCardSizePortrait() { render("tokens-small", false) }
    @Test fun tokenContentsPreviewPortrait() { render("tokens-contents", false) }
    @Test fun tokenMenuPreviewPortrait() { render("tokens-menu", false) }
    @Test fun tokenInspectorPreviewPortrait() { render("tokens-inspector", false) }
    @Test fun realTokenOnStackPortrait() { render("tokens-stack-card", false) }
    // CMB-008, STK-018, TBL-046, ZON-058
    @Test @Config(qualifiers = "w892dp-h412dp-land-mdpi") fun combatLandscape() { render("combat", true) }
    @Test fun stackPortrait() { render("stack-contents", false) }
    @Test fun graveyardBrowserPortrait() { render("graveyard-contents", false) }
    // ZON-057
    @Test fun graveyardHoldInspectsWithoutOpeningActionsOrLogging() {
        val sent = render("graveyard-contents", false)
        compose.mainClock.autoAdvance = false
        val row = compose.onNodeWithContentDescription("Graveyard panel: Meadow Sentinel")
        compose.mainClock.advanceTimeBy(350)
        row.performTouchInput { advanceEventTime(350); down(center) }
        compose.waitForIdle(); compose.mainClock.advanceTimeBy(600)
        compose.onNodeWithContentDescription("Card action ring").assertDoesNotExist()
        compose.onNodeWithContentDescription("Card action ring").assertDoesNotExist()
        compose.onNodeWithContentDescription("Card inspection content").assertIsDisplayed()
        compose.onNodeWithText("Creature — Soldier", substring = false).assertExists()
        assertTrue(sent.isEmpty())
        row.performTouchInput { up() }
        compose.mainClock.autoAdvance = true
        compose.onNodeWithContentDescription("Card inspection content").assertDoesNotExist()
        compose.onNodeWithContentDescription("Card action ring").assertDoesNotExist()
        compose.onNodeWithText("Creature — Soldier", substring = false).assertDoesNotExist()
        compose.onNodeWithContentDescription("Your Graveyard panel, 3 cards").assertIsDisplayed()
        assertTrue(sent.isEmpty())
    }
    @Test fun graveyardDoubleTapDoesNotRotateOrSubmit() {
        val sent = render("graveyard-contents", false)
        compose.mainClock.autoAdvance = false
        compose.mainClock.advanceTimeBy(350)
        // Real frames compose the immediately opened card menu before the second tap; that tap is
        // then relayed back to the table's coordinator, which recognizes the double tap.
        // Keep idle synchronization from auto-advancing past the recognition window between taps.
        val row = compose.onNodeWithContentDescription("Graveyard panel: Meadow Sentinel")
        val center = row.fetchSemanticsNode().boundsInRoot.center
        row.performTouchInput { advanceEventTime(350); click() }
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(120)
        compose.onNodeWithContentDescription("Card action ring").assertIsDisplayed()
        compose.onRoot().performTouchInput { click(center) }
        compose.mainClock.advanceTimeBy(400)
        compose.mainClock.autoAdvance = true
        assertTrue(sent.isEmpty())
        compose.onNodeWithContentDescription("Card action ring").assertDoesNotExist()
        compose.onNodeWithContentDescription("Your Graveyard panel, 3 cards").assertIsDisplayed()
    }
    // HIS-005, HIS-036, HIS-037, HIS-044, STK-018
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
    @Test fun treePortrait() { render("tree-viewer", false) }
    @Test @Config(qualifiers = "w892dp-h412dp-land-mdpi") fun treeLandscape() { render("tree-viewer", true) }
    @Test fun loadingPortrait() { render("placeholder", false) }
    @Test @Config(qualifiers = "w892dp-h412dp-land-mdpi") fun loadingLandscape() { render("placeholder", true) }
    @Test fun unavailablePortrait() { render("unavailable", false) }
    @Test @Config(qualifiers = "w892dp-h412dp-land-mdpi") fun unavailableLandscape() { render("unavailable", true) }

    private fun cancelContinuation(landscape: Boolean) {
        val sent = render("continue", landscape)
         val fork = compose.onNodeWithContentDescription("Cancel continuation")
         fork.assertWidthIsAtLeast(androidx.compose.ui.unit.Dp(48f)).assertHeightIsAtLeast(androidx.compose.ui.unit.Dp(48f))
         assertTrue(fork.fetchSemanticsNode().boundsInRoot.bottom <= 48f)
         fork.performTouchInput { click() }
        compose.onNodeWithText("Continuing from", substring = true).assertDoesNotExist()
         compose.onNodeWithContentDescription("Cancel continuation").assertDoesNotExist()
        compose.onNodeWithContentDescription("Table actions").assertIsDisplayed()
        compose.onNodeWithContentDescription("Auto advance", substring = false).assertDoesNotExist()
        compose.onNodeWithContentDescription("Continue playing from here").assertDoesNotExist()
        // The latest live position has a hand card moved onto the battlefield, unlike the armed boundary.
        val tree = f.history()
        val live = tree.stateAt(tree.currentNodeId)!!
        val battlefield = live.board.containers.single { it.zone == PlayZone.BATTLEFIELD && it.accountId == f.a }
        compose.onNodeWithContentDescription("Alice Battlefield, ${battlefield.cards.size} cards").assertExists()
        compose.runOnIdle { assertTrue(sent.isEmpty()) }
    }

    @Test @Config(qualifiers = "w892dp-h412dp-land-mdpi") fun opponentLifeOpensOnlyLifeInLandscape() {
        val sent = render("combat", true)
        compose.onNodeWithContentDescription("Bob, life", substring = true).performClick()
        compose.onNodeWithContentDescription("Life", substring = false).assertExists()
        compose.onNodeWithContentDescription("Set life").assertDoesNotExist()
        compose.onNodeWithContentDescription("Set red counter").assertDoesNotExist()
        assertTrue(sent.isEmpty())
    }


      // HIS-036
      @Test fun pausedAutoHoldImmediatelyArmsWithoutDialogAndConsumesTap() {
         render("history-paused", false)
          compose.onNodeWithContentDescription("Auto advance").performTouchInput { longClick() }
         compose.onNode(isDialog()).assertDoesNotExist()
         compose.onNodeWithContentDescription("Cancel continuation").assertIsDisplayed()
          compose.onNodeWithContentDescription("Auto advance").assertDoesNotExist()
         compose.onNodeWithContentDescription("Undo").assertIsDisplayed()
     }

     @Test fun returnToCurrentAtHeadLeavesTimelineWithoutDialog() {
         render("history-end", false)
         compose.onNodeWithContentDescription("Return to current").performClick()
         compose.onNode(isDialog()).assertDoesNotExist()
          compose.onNodeWithContentDescription("Auto advance").assertDoesNotExist()
          compose.onNodeWithContentDescription("Table actions").assertIsDisplayed()
     }

    // HIS-043, HIS-045, HIS-046
    @Test fun treeCentersReviewGroupAndTapSelectsLastEventThenCloses() {
        val tree = f.history()
        val explorer = HistoryExplorerController(Executor { it.run() }, { it() })
        val range = HistoryGraphLayout.build(tree).ranges.single { it.nodeIds.any { id -> tree.nodes[id]?.requestId == f.id("request5") } }
        val reviewCursor = range.nodeIds.first()
        assertNotEquals(reviewCursor, range.targetId)
        assertEquals(range.representativeId, range.targetId)
        explorer.update(tree, openingNodeId = reviewCursor)
        val texts = f.playback(tree).tree.texts
        var open by mutableStateOf(true)
        var cursor = tree.currentNodeId
        compose.setContent { MaterialTheme {
            if (open) HistoryExplorer(explorer, mapOf(f.a to OwnTableColor, f.b to OpponentTableColor),
                { open = false }, { cursor = it.currentNodeId }, texts = texts)
        } }
        val group = compose.onNodeWithContentDescription(texts.getValue(range.targetId).description)
        val viewport = compose.onNodeWithContentDescription("Game history group tree").fetchSemanticsNode().boundsInRoot
        val chip = group.fetchSemanticsNode().boundsInRoot
        assertEquals(viewport.center.x, chip.center.x, .5f)
        assertEquals(viewport.center.y, chip.center.y, .5f)
        compose.onNodeWithContentDescription(texts.getValue(reviewCursor).description).assertDoesNotExist()
        compose.onNodeWithText("Make current").assertDoesNotExist()
        compose.onNodeWithText("Continue from here").assertDoesNotExist()
        compose.onNodeWithText("Branch details…").assertDoesNotExist()
        val before = group.fetchSemanticsNode().positionInRoot
        compose.onNodeWithContentDescription("Game history group tree").performTouchInput {
            swipe(center, center + androidx.compose.ui.geometry.Offset(-30f, 20f), 200)
        }
        compose.runOnIdle {
            assertEquals(reviewCursor, explorer.state.selectedNodeId)
        }
        assertNotEquals(before, group.fetchSemanticsNode().positionInRoot)
        group.performTouchInput { longClick() }
        compose.runOnIdle {
            assertEquals(range.targetId, explorer.state.selectedNodeId)
        }
        group.performClick()
        compose.runOnIdle {
            assertEquals(range.targetId, cursor)
            assertFalse(open)
            assertEquals(tree.currentNodeId, explorer.state.graph!!.currentNodeId)
        }
        compose.onNodeWithContentDescription("Close game tree").assertDoesNotExist()
        explorer.close()
    }
}

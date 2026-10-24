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
        val history = TableHistoryController(Executor { it.run() }, { it() }, { node, complete ->
            val cursor = node ?: tree.currentNodeId
            complete(DownloadedHistoryState(1L, cursor, state = tree.stateAt(cursor))); true
        }, { action, _, _ -> sent += action; true })
        history.update(tree, "fixture", f.game, f.a)
        val playback = history.playback!!
        val path = playback.tree.path(tree.currentNodeId)
        val review = kind.startsWith("history") || kind in listOf("continue", "continue-confirmation", "tree-viewer")
        if (review) history.select(when (kind) { "history-start" -> path.first(); "history-end" -> path.last(); else -> path[3] })
        history.displayPosition()
        if (kind == "history-playing") playback.play()
        val stackSample = f.combat().let { play ->
            val card = CombatStackRules.inspection(play.board, f.id("effect"))!!
            val old = play.definitions.single { it.digest == card.definition }
            val definition = old.definition.copy(faces = old.definition.faces.map { it.copy(
                rulesText = "Draw a card, then discard a card.\nReturn target permanent to its owner's hand.\nA literal third paragraph.") })
            val replacement = SubmittedDefinition(DeckJson.digest(definition), definition)
            play.copy(definitions = play.definitions.map { if (it == old) replacement else it }, board = play.board.copy(
                cards = play.board.cards.map { if (it.definition == old.digest) it.copy(definition = replacement.digest) else it },
                stackTextMarks = mapOf(f.id("effect") to setOf(1))))
        }
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
        val current = { if (kind in listOf("stack-bubbles", "stack-preview")) stackSample
            else if (kind.startsWith("tokens")) tokenState
            else if (review) requireNotNull(tree.stateAt(playback.position.cursorId))
            else if (kind == "graveyard-contents") f.initial else f.combat() }
        val toolkit = TableToolkitController(f.a, current, { !review || history.armed != null || playback.position.mode == HistoryMode.LIVE },
            { action, _ -> sent += action; true })
        if (kind == "tokens-small") toolkit.layoutState.value = TableLayoutState(TableCamera(zoom = TablePresentation.MIN_ZOOM))
        if (kind.startsWith("stack-menu")) toolkit.selectionState.value = TableSelection(mode = TableMode.SELECT)
        if (kind == "combat") toolkit.selectionState.value = TableSelection(setOf(f.source(f.a, "1/3").id, f.source(f.a, "1/2").id), mode = TableMode.SELECT)
        if (kind in listOf("stack-bubbles", "stack-preview")) toolkit.showStackBubbles(f.id("effect"))
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
                                undo = { true }, redo = { true },
                                controlsConnected = true, reviewing = reviewing, continuationArmed = history.armed != null,
                                continueHere = if (reviewing) ({ history.requestContinuation(playback.position.cursorId) }) else null,
                                 returnCurrent = if (review) ({ history.requestExitReview() }) else null,
                                 cancelContinuation = history::cancelContinuation,
                                reviewPanel = if (reviewing) ({ HistoryPlaybackPanel(playback, tree.currentNodeId, f.a, true, 60_000L,
                                    { history.requestContinuation(it) }, overlay = true, landscape = landscape) }) else null))
                    }
                    if (history.continuationTarget != null) HistoryContinueConfirmation(true,
                         history::dismissContinuation, { history.confirmContinuation() }, history::current)
                    if (kind == "tree-viewer") HistoryExplorer(explorer, mapOf(f.a to OwnTableColor, f.b to OpponentTableColor),
                        false, {}, {}, { playback.scrub(it.currentNodeId) }, texts = playback.tree.texts)
                }
            }
        }
        compose.waitForIdle()
        when (kind) {
            "stack-bubbles", "stack-preview" -> {
                compose.onNodeWithContentDescription("Put to battlefield").assertIsDisplayed()
                compose.onNodeWithContentDescription("Put to graveyard").assertIsDisplayed()
                compose.onNodeWithContentDescription("Stack text mark 2").assertIsDisplayed()
                if (kind == "stack-preview") {
                    compose.mainClock.autoAdvance = false
                    compose.onNodeWithContentDescription("Mark text · drag up or down").performTouchInput {
                        down(center); moveBy(androidx.compose.ui.geometry.Offset(0f, 28f))
                    }
                    compose.waitForIdle() // Flush Android pointer delivery before advancing frozen composition frames.
                    compose.mainClock.advanceTimeByFrame()
                    compose.mainClock.advanceTimeByFrame()
                }
            }
            "tokens-contents" -> compose.onNodeWithContentDescription("Stack, 2 entries").performClick()
            "tokens-menu" -> compose.runOnIdle { toolkit.openCard(TableScope(f.source(f.a, "1/3").id, listOf(f.source(f.a, "1/3").id))) }
            "tokens-inspector" -> compose.runOnIdle { toolkit.inspect(f.source(f.a, "1/3").id) }
            "stack-menu-card", "stack-menu-effect" -> compose.runOnIdle {
                val id = CombatStackRules.stack(current().board).cards[if (kind == "stack-menu-card") 1 else 0]
                toolkit.openCard(TableScope(id, listOf(id)))
            }
             "continue", "continue-confirmation" -> {
                 compose.onNodeWithContentDescription(if (kind == "continue") "Continue playing from here" else "Return to current").performClick()
                 if (kind == "continue") {
                    compose.onNodeWithContentDescription("Interaction mode").assertExists()
                    compose.onNodeWithContentDescription("Auto advance").assertDoesNotExist()
                     compose.onNodeWithContentDescription("Cancel continuation").assertIsDisplayed()
                     compose.onNodeWithText("Continuing from", substring = true).assertDoesNotExist()
                     compose.onNode(isDialog()).assertDoesNotExist()
                 } else {
                     compose.onNodeWithText("Leave history?").assertIsDisplayed()
                     compose.onNodeWithText("Continue from here").assertIsDisplayed()
                     compose.onNodeWithText("Back to latest").assertIsDisplayed()
                }
            }
            "stack-contents" -> {
                compose.onNodeWithContentDescription("Stack, 2 entries").performTouchInput { doubleClick() }
                compose.runOnIdle { assertEquals("manageStack", toolkit.panel) }
                compose.onNodeWithText("Manage Stack · 2").assertExists()
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
        if (kind.startsWith("history")) {
            compose.onNodeWithText("Timeline mode").assertIsDisplayed()
            compose.onNodeWithText(if (kind == "history-playing") "Auto: on" else "Auto: off").assertIsDisplayed()
            compose.onNodeWithText("Tap: toggle · Hold: branch").assertIsDisplayed()
        }
        if (kind == "tree-viewer") {
            compose.onNodeWithText("Make current").assertDoesNotExist()
            compose.onNodeWithText("Continue from here").assertDoesNotExist()
            compose.onNodeWithText("Branch details…").assertDoesNotExist()
        }
        compose.waitForIdle()
        assertTrue(sent.isEmpty())
        val file = File("build/outputs/roborazzi/gameplay-$kind-${if (landscape) "landscape" else "portrait"}.png")
        file.parentFile!!.mkdirs()
        captureScreenRoboImage(file.path)
        if (kind == "stack-preview") {
            compose.onNodeWithContentDescription("Stack text drag preview").assertIsDisplayed()
            compose.onNodeWithText("A literal third paragraph.").assertIsDisplayed()
        }
        val image = ImageIO.read(file)
        assertEquals(if (landscape) 892 else 412, image.width)
        assertEquals(if (landscape) 412 else 892, image.height)
        if (kind == "combat") {
            compose.onNodeWithContentDescription("History", substring = false).assertIsDisplayed().assertIsEnabled()
            assertRenderedLandsBands(image, TablePresentation.scene(f.game, current(), f.a,
                image.width.toFloat(), image.height.toFloat(), landscape), f.a)
        }
        if (kind == "stack-preview") {
            compose.onRoot().performTouchInput { cancel() }
            compose.mainClock.autoAdvance = true
        }
        explorer.close()
        return sent
    }

    @Test fun combatPortrait() { render("combat", false) }
    @Test fun stackBubblesPortrait() { render("stack-bubbles", false) }
    @Test fun stackDoubleTapWithBubblesOpenShowsManageStackWithoutTappingSource() {
        val sent = render("stack-bubbles", false)
        val scene = TablePresentation.scene(f.game, f.combat(), f.a, 412f, 892f, false)
        val card = scene.items.single { it.cardId == f.id("effect") }
        compose.onRoot().performTouchInput {
            doubleClick(androidx.compose.ui.geometry.Offset(card.bounds.center.x, card.bounds.bottom - 6))
        }
        compose.onNodeWithText("Manage Stack · 2").assertIsDisplayed()
        compose.onNodeWithContentDescription("Card action ring").assertDoesNotExist()
        compose.onNodeWithText("Put to battlefield").assertIsDisplayed()
        compose.onAllNodesWithText("Card actions").assertCountEquals(2)
        compose.onNodeWithContentDescription("Tap selected scope").assertDoesNotExist()
        compose.onNodeWithContentDescription("Untap selected scope").assertDoesNotExist()
        compose.onAllNodesWithText("Card actions").onFirst().performClick()
        compose.onNodeWithContentDescription("Card action ring").assertIsDisplayed()
        compose.onNodeWithText("Manage Stack · 2").assertDoesNotExist()
        assertTrue(sent.isEmpty())
    }
    @Test fun manageStackReflectsLiveMembershipAndClosesWhenEmpty() {
        var state by mutableStateOf(f.combat())
        val ui = TableToolkitController(f.a, { state }, { true }, { _, _ -> error("Browsing is local") })
        ui.stackContents()
        compose.setContent { MaterialTheme {
            TableToolkitScreen(ui, state, f.game, false, TableSelection(), {})
        } }
        compose.onNodeWithText("Manage Stack · 2").assertIsDisplayed()
        compose.runOnIdle {
            state = state.copy(board = state.board.copy(stackEffects = emptyList(), containers = state.board.containers.map {
                if (it.zone == PlayZone.STACK) it.copy(cards = it.cards - f.id("effect")) else it }))
        }
        compose.onNodeWithText("Manage Stack · 1").assertIsDisplayed()
        compose.runOnIdle { state = f.initial }
        compose.onNodeWithText("Manage Stack · 1").assertDoesNotExist()
        compose.runOnIdle { assertNull(ui.panel) }
    }
    @OptIn(ExperimentalRoborazziApi::class)
    @Test fun manageStackPeekAttachesAboveTheManageDialogAndReleasesBackToIt() {
        val state = f.combat()
        compose.setContent { MaterialTheme {
            ManageStackDialog(state, f.a, true, {}, { error("Inspection must stay local") }, {})
        } }
        compose.onNodeWithText("Manage Stack · 2").assertIsDisplayed()
        val dialog = android.view.inspector.WindowInspector.getGlobalWindowViews().last()
        val row = compose.onNodeWithText("Top ·", substring = true)
        compose.mainClock.autoAdvance = false
        row.performTouchInput { down(center) }
        compose.mainClock.advanceTimeBy(600)
        compose.onNodeWithContentDescription("Card inspection content").assertIsDisplayed()
        val popup = android.view.inspector.WindowInspector.getGlobalWindowViews().last()
        assertEquals(dialog.windowToken, (popup.layoutParams as android.view.WindowManager.LayoutParams).token,
            "The inspection popup must belong to the manage dialog, not the activity underneath it")
        val file = File("build/outputs/roborazzi/manage-stack-inspection-portrait.png")
        file.parentFile!!.mkdirs()
        captureScreenRoboImage(file.path)
        row.performTouchInput { up() }
        compose.mainClock.autoAdvance = true
        compose.onNodeWithContentDescription("Card inspection content").assertDoesNotExist()
        compose.onNodeWithText("Manage Stack · 2").assertIsDisplayed()
    }
    @Test fun stackHoldHighlightsTheMarkedParagraphWithoutSubmittingAnything() {
        val sent = render("stack-bubbles", false)
        val play = f.combat()
        val scene = TablePresentation.scene(f.game, play, f.a, 412f, 892f, false)
        val card = scene.items.single { it.cardId == f.id("effect") }
        compose.mainClock.autoAdvance = false
        compose.onNodeWithContentDescription("Shared table").performTouchInput {
            down(androidx.compose.ui.geometry.Offset(card.bounds.center.x, card.bounds.bottom - 6))
        }
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(650)
        compose.onNodeWithText("Return target permanent to its owner's hand.").assertExists()
        compose.onNode(hasStateDescription("Stack text mark 2")).assertExists()
        compose.onNodeWithContentDescription("Shared table").performTouchInput { up() }
        compose.mainClock.autoAdvance = true
        compose.onNodeWithText("Return target permanent to its owner's hand.").assertDoesNotExist()
        assertTrue(sent.isEmpty())
    }
    @OptIn(ExperimentalRoborazziApi::class)
    @Test fun explicitStackInspectStaysOpenAndKeepsItsTextMarker() {
        val sent = render("stack-bubbles", false)
        val scene = TablePresentation.scene(f.game, f.combat(), f.a, 412f, 892f, false)
        val card = scene.items.single { it.cardId == f.id("effect") }
        compose.onNodeWithContentDescription("Shared table").performTouchInput {
            doubleClick(androidx.compose.ui.geometry.Offset(card.bounds.center.x, card.bounds.bottom - 6))
        }
        val inspect = compose.onAllNodesWithContentDescription("Your Stack entry").onFirst().fetchSemanticsNode()
            .config[androidx.compose.ui.semantics.SemanticsActions.CustomActions].single { it.label == "Inspect" }
        compose.runOnIdle { assertTrue(inspect.action()) }
        captureScreenRoboImage("build/outputs/roborazzi/ring-stack-inspection.png")
        compose.onNodeWithContentDescription("Close card inspection").assertIsDisplayed()
        compose.onNode(hasText("Return target permanent to its owner's hand.") and
            hasAnyAncestor(hasContentDescription("Card inspection content"))).performScrollTo().assertIsDisplayed()
        compose.onNode(hasStateDescription("Stack text mark 2")).assertExists()
        compose.onNodeWithContentDescription("Close card inspection").performClick()
        compose.onNode(hasText("Return target permanent to its owner's hand.") and
            hasAnyAncestor(hasContentDescription("Card inspection content"))).assertDoesNotExist()
        compose.onNodeWithText("Manage Stack · 2").assertIsDisplayed()
        compose.onNodeWithText("Return target permanent to its owner's hand.").assertExists() // The mark chip remains.
        assertTrue(sent.isEmpty())
    }
    @Test @Config(qualifiers = "w892dp-h412dp-land-mdpi") fun stackBubblesLandscape() { render("stack-bubbles", true) }
    @Test fun stackPreviewPortrait() { render("stack-preview", false) }
    @Test @Config(qualifiers = "w892dp-h412dp-land-mdpi") fun stackPreviewLandscape() { render("stack-preview", true) }
    @Test fun tokensOnBattlefieldHandStackAndCombatPortrait() { render("tokens", false) }
    @Test fun tokensAtSmallestCardSizePortrait() { render("tokens-small", false) }
    @Test fun tokenContentsPreviewPortrait() { render("tokens-contents", false) }
    @Test fun tokenMenuPreviewPortrait() { render("tokens-menu", false) }
    @Test fun tokenInspectorPreviewPortrait() { render("tokens-inspector", false) }
    @Test fun realTokenOnStackPortrait() { render("tokens-stack-card", false) }
    @Test fun ownLibraryDoubleTapSubmitsOnlyOneDrawNotView() {
        val sent = render("combat", false)
        compose.onNodeWithContentDescription("Alice library, 50 cards").performTouchInput { doubleClick() }
        compose.mainClock.advanceTimeBy(400)
        val library = f.combat().board.containers.single { it.id == f.id("a/LIBRARY") }
        assertEquals(listOf(PlayAction.MoveCard(library.cards.first(), MoveSource(library.id, edge = MoveSourceEdge.TOP),
            MoveDestination(f.id("a/HAND"), MoveInsertion.UNORDERED))), sent)
        compose.onNodeWithText("Library contents").assertDoesNotExist()
    }
    @Test fun opponentLibraryDoubleTapDoesNothing() {
        val sent = render("combat", false)
        compose.onNodeWithContentDescription("Bob library, 50 cards").performTouchInput { doubleClick() }
        compose.mainClock.advanceTimeBy(400)
        assertTrue(sent.isEmpty())
        compose.onNodeWithText("Library contents").assertDoesNotExist()
    }
    @Test fun stackCardOffersNoTapControlsInSelectMode() { assertNoStackTapControls("stack-menu-card") }
    @Test fun stackEffectOffersNoTapControlsInSelectMode() { assertNoStackTapControls("stack-menu-effect") }
    private fun assertNoStackTapControls(kind: String) {
        val sent = render(kind, false)
        compose.ringPage(3)
        compose.onNodeWithContentDescription("Tap selected scope").assertDoesNotExist()
        compose.onNodeWithContentDescription("Untap selected scope").assertDoesNotExist()
        assertTrue(sent.isEmpty())
    }
    @Test @Config(qualifiers = "w892dp-h412dp-land-mdpi") fun combatLandscape() { render("combat", true) }
    @Test fun stackPortrait() { render("stack-contents", false) }
    @Test fun graveyardBrowserPortrait() { render("graveyard-contents", false) }
    @Test fun graveyardSearchAndTapUseExistingCardActionMenu() {
        val sent = render("graveyard-contents", false)
        compose.onNodeWithText("Search by name").performTextInput("Sentinel")
        compose.onNodeWithText("Meadow Sentinel", substring = false).performClick()
        compose.ringPage(4)
        compose.onNodeWithContentDescription("Graveyard").assertExists()
        compose.onNodeWithContentDescription("Library top").assertExists()
        compose.onNodeWithContentDescription("Library bottom").assertExists()
        compose.onNodeWithContentDescription("Exile").assertExists()
        assertTrue(sent.isEmpty())
    }
    @Test fun graveyardHoldInspectsWithoutOpeningActionsOrLogging() {
        val sent = render("graveyard-contents", false)
        compose.mainClock.autoAdvance = false
        val row = compose.onNodeWithText("Meadow Sentinel", substring = false)
        row.performTouchInput { down(center) }
        compose.waitForIdle(); compose.mainClock.advanceTimeBy(600)
        compose.onNodeWithContentDescription("Graveyard").assertDoesNotExist()
        compose.onNodeWithText("Creature — Soldier", substring = false).assertExists()
        compose.onNodeWithText("Search by name").performTouchInput { up() }
        compose.mainClock.autoAdvance = true
        compose.onNodeWithText("Creature — Soldier", substring = false).assertDoesNotExist()
        assertTrue(sent.isEmpty())
    }
    @Test fun graveyardDoubleTapOnlyRotatesTheAddressedCard() {
        val sent = render("graveyard-contents", false)
        compose.onNodeWithText("Meadow Sentinel", substring = false).performTouchInput { doubleClick() }
        compose.mainClock.advanceTimeBy(400)
        val card = f.initial.board.cards.single { it.containerId == f.id("a/GRAVEYARD") && it.corner == "1/3" }
        assertEquals(listOf<PlayAction>(PlayAction.SetTapped(card.id, true)), sent)
        compose.onNodeWithContentDescription("Graveyard").assertDoesNotExist()
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
         val fork = compose.onNodeWithContentDescription("Cancel continuation")
         fork.assertWidthIsAtLeast(androidx.compose.ui.unit.Dp(48f)).assertHeightIsAtLeast(androidx.compose.ui.unit.Dp(48f))
         assertTrue(fork.fetchSemanticsNode().boundsInRoot.bottom <= 48f)
         fork.performTouchInput { click() }
        compose.onNodeWithText("Continuing from", substring = true).assertDoesNotExist()
         compose.onNodeWithContentDescription("Cancel continuation").assertDoesNotExist()
        compose.onNodeWithContentDescription("History", substring = false).assertIsDisplayed()
        compose.onNodeWithContentDescription("Auto advance", substring = false).assertDoesNotExist()
        compose.onNodeWithContentDescription("Continue playing from here").assertDoesNotExist()
        // The latest live position has a hand card moved onto the battlefield, unlike the armed boundary.
        val tree = f.history()
        val live = tree.stateAt(tree.currentNodeId)!!
        val battlefield = live.board.containers.single { it.zone == PlayZone.BATTLEFIELD && it.accountId == f.a }
        compose.onNodeWithContentDescription("Alice Battlefield, ${battlefield.cards.size} cards").assertExists()
        compose.runOnIdle { assertTrue(sent.isEmpty()) }
    }

    @Test @Config(qualifiers = "w892dp-h412dp-land-mdpi") fun blankOpponentCounterOpensPlayerActionsWithoutMutationInLandscape() {
        render("combat", true)
        compose.onNodeWithContentDescription("Bob, red, no value").performClick()
        compose.onNodeWithText("Life / counters").assertExists()
    }

    @Test fun hiddenPortraitCountersRemainReachableThroughLifeWithoutMutation() {
        render("combat", false)
        compose.onNodeWithContentDescription("Bob, red, no value").assertDoesNotExist()
        compose.onNodeWithContentDescription("Bob, life", substring = true).performClick()
        compose.onNodeWithText("Life / counters").assertExists()
        compose.onNodeWithContentDescription("Set red counter, no value").assertExists()
    }

      @Test fun pausedAutoHoldImmediatelyArmsWithoutDialogAndConsumesTap() {
         render("history-paused", false)
          compose.onNodeWithContentDescription("Auto advance").performTouchInput { longClick() }
         compose.onNode(isDialog()).assertDoesNotExist()
         compose.onNodeWithContentDescription("Cancel continuation").assertIsDisplayed()
          compose.onNodeWithContentDescription("Auto advance").assertDoesNotExist()
         compose.onNodeWithContentDescription("Interaction mode").assertIsDisplayed()
     }

     @Test fun toggleOffAwayFromHeadCanContinueFromCursor() {
         render("continue-confirmation", false)
         compose.onNodeWithText("Continue from here").performClick()
         compose.onNode(isDialog()).assertDoesNotExist()
         compose.onNodeWithContentDescription("Cancel continuation").assertIsDisplayed()
          compose.onNodeWithContentDescription("Auto advance").assertDoesNotExist()
         compose.onNodeWithContentDescription("Interaction mode").assertIsDisplayed()
     }

     @Test fun toggleOffAwayFromHeadCanReturnToLatest() {
         render("continue-confirmation", false)
         compose.onNodeWithText("Back to latest").performClick()
         compose.onNode(isDialog()).assertDoesNotExist()
         compose.onNodeWithContentDescription("Cancel continuation").assertDoesNotExist()
         compose.onNodeWithText("Playing groups", substring = true).assertIsDisplayed()
     }

     @Test fun dismissToggleOffDialogOutsideStaysInHistory() {
         render("continue-confirmation", false)
         compose.onNode(isDialog()).performTouchInput { click(androidx.compose.ui.geometry.Offset(4f, 4f)) }
         compose.onNode(isDialog()).assertDoesNotExist()
          compose.onNodeWithContentDescription("Auto advance").assertIsDisplayed()
         compose.onNodeWithContentDescription("Cancel continuation").assertDoesNotExist()
         compose.onNodeWithContentDescription("Return to current").assertIsDisplayed()
     }

     @Test fun dismissToggleOffDialogWithBackStaysInHistory() {
         render("continue-confirmation", false)
         androidx.test.espresso.Espresso.pressBack()
         compose.onNode(isDialog()).assertDoesNotExist()
          compose.onNodeWithContentDescription("Auto advance").assertIsDisplayed()
         compose.onNodeWithContentDescription("Return to current").assertIsDisplayed()
     }

     @Test fun toggleOffAtHeadHasNoDialog() {
         render("history-end", false)
         compose.onNodeWithContentDescription("Return to current").performClick()
         compose.onNode(isDialog()).assertDoesNotExist()
          compose.onNodeWithContentDescription("Auto advance").assertDoesNotExist()
         compose.onNodeWithContentDescription("History", substring = false).assertIsDisplayed()
     }

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
                false, {}, { open = false }, { cursor = it.currentNodeId }, texts = texts)
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
        compose.onNodeWithText("Game tree ▾").assertDoesNotExist()
        explorer.close()
    }
}

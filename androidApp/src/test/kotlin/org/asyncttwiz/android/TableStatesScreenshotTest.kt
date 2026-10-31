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
            else if (kind == "combat-targets") combatTargetsState
            else if (kind.startsWith("tokens")) tokenState
            else if (review) requireNotNull(tree.stateAt(playback.position.cursorId))
            else if (kind == "graveyard-contents") graveyardState else f.combat() }
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
                                 returnCurrent = if (review) history::current else null,
                                 cancelContinuation = history::cancelContinuation,
                                reviewPanel = if (reviewing) ({ HistoryPlaybackPanel(playback, tree.currentNodeId, f.a, true, 60_000L,
                                    { history.requestContinuation(it) }, overlay = true, landscape = landscape) }) else null))
                    }
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
            "continue" -> {
                compose.onNodeWithContentDescription("Continue playing from here").performClick()
                compose.onNodeWithContentDescription("Interaction mode").assertExists()
                compose.onNodeWithContentDescription("Auto advance").assertDoesNotExist()
                compose.onNodeWithContentDescription("Cancel continuation").assertIsDisplayed()
                compose.onNodeWithText("Continuing from", substring = true).assertDoesNotExist()
                compose.onNode(isDialog()).assertDoesNotExist()
            }
            "stack-contents" -> {
                compose.onNodeWithContentDescription("Stack, 2 entries").performTouchInput { doubleClick() }
                compose.runOnIdle { assertEquals("manageStack", toolkit.panel) }
                compose.onNodeWithText("Manage Stack · 2").assertExists()
            }
            "graveyard-contents" -> {
                // A pile tap opens its menu; the double-tap opens the search panel.
                compose.onNodeWithContentDescription("Alice graveyard, 3 cards").performTouchInput { doubleClick() }
                compose.mainClock.advanceTimeBy(500)
                compose.onNodeWithContentDescription("Your Graveyard panel, 3 cards").assertIsDisplayed()
                compose.onNode(isDialog()).assertDoesNotExist()
                compose.onNodeWithContentDescription("Panel card actions").assertDoesNotExist()
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
            compose.onNodeWithText("Tap: toggle · Hold: branch").assertDoesNotExist()
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
        captureScreenRoboImage(file.path)
        if (kind == "stack-preview") {
            compose.onNodeWithContentDescription("Stack text drag preview").assertIsDisplayed()
            compose.onNodeWithText("A literal third paragraph.").assertIsDisplayed()
        }
        val image = ImageIO.read(file)
        assertEquals(if (landscape) 892 else 412, image.width)
        assertEquals(if (landscape) 412 else 892, image.height)
        if (kind.startsWith("combat")) {
            compose.onNodeWithContentDescription("Table actions").assertIsDisplayed().assertIsEnabled()
            val scene = TablePresentation.scene(f.game, current(), f.a, image.width.toFloat(), image.height.toFloat(), landscape)
            if (kind == "combat") assertRenderedLandsBands(image, scene, f.a, turnPlayer = current().board.turn.accountId)
            assertTrue(scene.combatLabels.all { it.fontSize in 6.5f..7.5f }) // the card title size
            val cards = scene.items.filter { it.projection && it.viewport == "combat" }
            assertTrue(cards.isNotEmpty())
            assertTrue(scene.combatLabels.all { label -> cards.all { label.bounds.bottom < it.bounds.y } })
            if (kind == "combat-targets") assertEquals(1, scene.combatLaneDividers.size)
            // Card-slot dividers start below the lane headers; lane dividers cross the header row too.
            assertTrue(scene.combatDividers.all { (from, _) -> scene.combatLabels.all { from.y >= it.bounds.bottom - .01f } })
            assertTrue(scene.combatLaneDividers.all { (from, _) -> scene.combatLabels.all { from.y <= it.bounds.y + .01f } })
        }
        if (kind == "stack-preview") {
            compose.onRoot().performTouchInput { cancel() }
            compose.mainClock.autoAdvance = true
        }
        explorer.close()
        return sent
    }

    // CMB-008, CMB-013, CMB-014, STK-008, STK-013, STK-017, STK-018, STK-021
    @Test fun combatPortrait() { render("combat", false) }
    @Test fun combatCardTargetPortrait() { render("combat-targets", false) }
    @Test @Config(qualifiers = "w892dp-h412dp-land-mdpi") fun combatCardTargetLandscape() { render("combat-targets", true) }
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
        compose.onAllNodesWithContentDescription("Put to battlefield").onFirst().assertIsDisplayed()
        compose.onAllNodesWithContentDescription("Card actions").assertCountEquals(0)
        compose.onAllNodesWithContentDescription("Copy").assertCountEquals(2)
        compose.onAllNodesWithContentDescription("Change controller").assertCountEquals(2)
        compose.onAllNodesWithContentDescription("Change controller").onFirst().assertIsEnabled()
        compose.onAllNodesWithContentDescription("Change controller").onLast().assertIsNotEnabled()
        compose.onNodeWithContentDescription("Move up").assertDoesNotExist()
        compose.onNodeWithContentDescription("Move down").assertDoesNotExist()
        compose.onNodeWithContentDescription("Tap selected scope").assertDoesNotExist()
        compose.onNodeWithContentDescription("Untap selected scope").assertDoesNotExist()
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
    // STK-021
    @OptIn(ExperimentalRoborazziApi::class)
    @Test fun manageStackPeekAttachesAboveTheManageDialogAndReleasesBackToIt() {
        val state = f.combat()
        compose.setContent { MaterialTheme {
            ManageStackDialog(state, f.a, true, {}, { error("Inspection must stay local") })
        } }
        compose.onNodeWithText("Manage Stack · 2").assertIsDisplayed()
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
    // STK-017
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
        captureScreenRoboImage("$SCREENSHOTS/ring-stack-inspection.png")
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
    // STK-013, STK-016, TBL-018, ZON-013
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
    // ZON-013
    @Test fun opponentLibraryDoubleTapDrawsIntoOwnersHandWithoutView() {
        val sent = render("combat", false)
        compose.onNodeWithContentDescription("Bob library, 50 cards").performTouchInput { doubleClick() }
        compose.mainClock.advanceTimeBy(400)
        val library = f.combat().board.containers.single { it.id == f.id("b/LIBRARY") }
        assertEquals(listOf(PlayAction.MoveCard(library.cards.first(), MoveSource(library.id, edge = MoveSourceEdge.TOP),
            MoveDestination(f.id("b/HAND"), MoveInsertion.UNORDERED))), sent)
        compose.onNodeWithText("Library contents").assertDoesNotExist()
    }
    // STK-003
    @Test fun stackCardOffersNoTapControlsInSelectMode() { assertNoStackTapControls("stack-menu-card") }
    @Test fun stackEffectOffersNoTapControlsInSelectMode() { assertNoStackTapControls("stack-menu-effect") }
    private fun assertNoStackTapControls(kind: String) {
        val sent = render(kind, false)
        // A Stack card has the three battlefield pages; an effect marker has one page without a header.
        if (kind == "stack-menu-card") compose.ringPage(3)
        compose.onNodeWithContentDescription("Tap selected scope").assertDoesNotExist()
        compose.onNodeWithContentDescription("Untap selected scope").assertDoesNotExist()
        assertTrue(sent.isEmpty())
    }
    // CMB-008, STK-018, TBL-046, ZON-058
    @Test @Config(qualifiers = "w892dp-h412dp-land-mdpi") fun combatLandscape() { render("combat", true) }
    @Test fun stackPortrait() { render("stack-contents", false) }
    @Test fun graveyardBrowserPortrait() { render("graveyard-contents", false) }
    @Test fun graveyardTapOpensFixedPanelCardMenuWithoutSending() {
        val sent = render("graveyard-contents", false)
        compose.mainClock.advanceTimeBy(350)
        compose.onNodeWithContentDescription("Graveyard panel: Meadow Sentinel").performTouchInput { advanceEventTime(350); click() }
        compose.onNodeWithContentDescription("Panel card actions").assertIsDisplayed()
        compose.onNodeWithContentDescription("Card action ring").assertDoesNotExist()
        compose.onNodeWithContentDescription("Graveyard panel: Meadow Sentinel").assertIsSelected()
        val labels = panelCardActions.map { it.name }
        val first = compose.onNodeWithContentDescription("Inspect").fetchSemanticsNode().boundsInRoot
        for ((index, label) in labels.withIndex()) {
            val cell = compose.onNodeWithContentDescription(label).assertIsDisplayed()
            if (label in listOf("Graveyard", "Reveal+Hand", "Reveal+Top")) cell.assertIsNotEnabled()
            else if (label != "Ping / Unping" && label != "Inspect other side") cell.assertIsEnabled()
            val bounds = cell.fetchSemanticsNode().boundsInRoot
            assertEquals(CardRingGeometry.cell, bounds.width)
            assertEquals(CardRingGeometry.cell, bounds.height)
            assertEquals(first.left + index % 5 * (first.width + 2f), bounds.left, .1f)
            assertEquals(first.top + index / 5 * (first.height + 2f), bounds.top, .1f)
        }
        assertTrue(sent.isEmpty())
    }
    // ZON-057
    @Test fun graveyardHoldInspectsWithoutOpeningActionsOrLogging() {
        val sent = render("graveyard-contents", false)
        compose.mainClock.autoAdvance = false
        val row = compose.onNodeWithContentDescription("Graveyard panel: Meadow Sentinel")
        compose.mainClock.advanceTimeBy(350)
        row.performTouchInput { advanceEventTime(350); down(center) }
        compose.waitForIdle(); compose.mainClock.advanceTimeBy(600)
        compose.onNodeWithContentDescription("Panel card actions").assertDoesNotExist()
        compose.onNodeWithContentDescription("Card action ring").assertDoesNotExist()
        compose.onNodeWithContentDescription("Card inspection content").assertIsDisplayed()
        compose.onNodeWithText("Creature — Soldier", substring = false).assertExists()
        assertTrue(sent.isEmpty())
        row.performTouchInput { up() }
        compose.mainClock.autoAdvance = true
        compose.onNodeWithContentDescription("Card inspection content").assertDoesNotExist()
        compose.onNodeWithContentDescription("Panel card actions").assertDoesNotExist()
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
        compose.onNodeWithContentDescription("Panel card actions").assertIsDisplayed()
        compose.onRoot().performTouchInput { click(center) }
        compose.mainClock.advanceTimeBy(400)
        compose.mainClock.autoAdvance = true
        assertTrue(sent.isEmpty())
        compose.onNodeWithContentDescription("Panel card actions").assertDoesNotExist()
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
        compose.onNodeWithContentDescription("Set life").assertExists()
        compose.onNodeWithContentDescription("Set red counter").assertDoesNotExist()
        assertTrue(sent.isEmpty())
    }

    // TRN-034
    @Test fun emptyPortraitCounterOpensOnlyCountersWithoutMutation() {
        val sent = render("combat", false)
        compose.onNodeWithContentDescription("Bob, red, no value").performClick()
        compose.onNodeWithContentDescription("Counters").assertExists()
        compose.onNodeWithContentDescription("Set red counter").assertExists()
        compose.onNodeWithContentDescription("Set life").assertDoesNotExist()
        assertTrue(sent.isEmpty())
    }

      // HIS-036
      @Test fun pausedAutoHoldImmediatelyArmsWithoutDialogAndConsumesTap() {
         render("history-paused", false)
          compose.onNodeWithContentDescription("Auto advance").performTouchInput { longClick() }
         compose.onNode(isDialog()).assertDoesNotExist()
         compose.onNodeWithContentDescription("Cancel continuation").assertIsDisplayed()
          compose.onNodeWithContentDescription("Auto advance").assertDoesNotExist()
         compose.onNodeWithContentDescription("Interaction mode").assertIsDisplayed()
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

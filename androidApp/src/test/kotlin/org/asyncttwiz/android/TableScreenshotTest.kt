package org.asyncttwiz.android

import androidx.compose.ui.test.click

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.toArgb
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.asyncttwiz.protocol.*
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** Native Compose/Canvas screenshots at the mock's logical viewport (one pixel per dp). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w412dp-h892dp-port-mdpi")
class TableScreenshotTest {
    @get:Rule val compose = createComposeRule()

    @OptIn(ExperimentalRoborazziApi::class)
    private fun capture(name: String, landscape: Boolean, paused: Boolean = false, hand: Int = 5, controls: Boolean = false) {
        val initial = LiveTableFixture.play(hand)
        val library = initial.board.containers.single { it.id == "a/LIBRARY" }
        val play = initial.copy(board = initial.board.copy(contentsDisplays = listOf(
            ContentsDisplay("retained-view", "a", library.id, ContentsSelection.ALL, library.cards.size, false, library.cards))))
        // The engine table's board and routes (R4b follow-up): what the app draws of this board
        val board = engineTableBoard(play, "a")
        val toolkit = TableToolkitController("a", { board }, { !paused }, { _, _ -> true })
        // LOB-135: in a game the table offers Game settings
        toolkit.assumeReveals = {}
        compose.setContent {
            MaterialTheme {
                EngineFixtureTable(play, "a") {
                    Column(Modifier.fillMaxSize()) {
                        TableSurface(LiveTableFixture.game, board, "a", landscape, Modifier.weight(1f),
                            connected = !paused, onIntent = toolkit::handle, toolkit = toolkit,
                            runtime = TableRuntimeHooks(chat = {}, history = {}, navigation = {}, chatUnread = 1,
                                undo = { true }, reviewing = paused, controlsConnected = true,
                                returnCurrent = if (paused) ({}) else null,
                                bottomPanel = if (paused) ({ _ -> LiveTableFixture.ReviewPanel("a", landscape, at = 5) }) else null))
                    }
                }
            }
        }
        compose.waitForIdle()
        compose.onNodeWithText("View · 50").assertDoesNotExist()
        if (paused) {
            compose.onNodeWithContentDescription("Interaction mode").assertDoesNotExist()
            compose.onNodeWithContentDescription("Continue playing from here").assertDoesNotExist() // old-model timeline branching
        } else {
            compose.onNodeWithContentDescription("History", substring = false).assertDoesNotExist()
            // ("Next" is also the step arrow's word, TRN-063)
            for (label in listOf("Auto advance", "Previous", "Continue playing from here", "Return to current"))
                compose.onNodeWithContentDescription(label).assertDoesNotExist()
            for (label in listOf("Live", "Download history", "Event details")) compose.onNodeWithText(label).assertDoesNotExist()
        }
        val output = File("$SCREENSHOTS/$name.png")
        requireNotNull(output.parentFile).mkdirs()
        val caption = (if (name.startsWith("device-")) "Small phone screen, " else "") +
            "${if (landscape) "landscape" else "portrait"} table mid-game on your turn, " +
            (if (paused) "reviewing history with the timeline panel open" else "live play") +
            (if (hand > 5) ", $hand cards in each hand" else "") + (if (controls) ", Table actions menu open" else "") + "."
        if (controls) {
            compose.onNodeWithContentDescription("Table actions").performClick()
            compose.onNodeWithText("Table actions").assertExists()
            compose.onNodeWithText("View · 50").assertDoesNotExist()
            compose.onNodeWithText("Redo").assertDoesNotExist()
            compose.onNodeWithContentDescription("Game controls").assertDoesNotExist()
            compose.waitForIdle()
            captureShot(output.path, caption.replaceFirstChar { it.uppercase() })
        } else compose.onRoot().captureShot(output.path, caption.replaceFirstChar { it.uppercase() })
        val image = ImageIO.read(output)
        if (name.startsWith("device-")) {
            assertEquals(if (landscape) 1280 else 720, image.width)
            assertEquals(if (landscape) 720 else 1280, image.height)
        } else {
            assertEquals(if (landscape) 892 else 412, image.width)
            assertEquals(if (landscape) 412 else 892, image.height)
            if (!controls) {
                val normal = TablePresentation.scene(LiveTableFixture.game, board, "a",
                    image.width.toFloat(), image.height.toFloat(), landscape, reviewing = paused, landscapeHome = true, timeline = paused)
                val scene = normal
                assertRenderedLandsBands(image, scene, "a",
                    overlayTop = image.height - tableBottomHeight(landscape, paused).toInt(), reviewing = paused,
                    turnPlayer = play.board.turn.accountId)
            }
        }
    }

    // STK-008, SYS-024, TRN-007
    @Test fun livePortrait() = capture("live-portrait", false)
    @Test fun floatingErrorAboveTableControls() {
        val play = LiveTableFixture.play(5)
        compose.setContent {
            MaterialTheme {
                Column(Modifier.fillMaxSize()) {
                TableSurface(LiveTableFixture.game, tableBoard(play), "a", false, Modifier.fillMaxSize(), connected = true,
                    runtime = TableRuntimeHooks(errorMessage = "Can't undo: a later event refers to that action. Use History to review it, or keep playing.", dismissError = {}))
                }
            }
        }
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Dismiss message").assertIsDisplayed()
        val output = File("$SCREENSHOTS/table-floating-error.png")
        requireNotNull(output.parentFile).mkdirs()
        compose.onRoot().captureShot(output.path,
            "Portrait table mid-game with a floating error message about a refused undo, shown above the own hand row so the life square, hand and table controls stay visible.")
        val image = ImageIO.read(output)
        assertEquals(412, image.width)
        assertEquals(892, image.height)
    }
    // SYS-024
    @Test fun floatingErrorCanBeDismissedAndExpiresWithoutChangingTheTable() {
        val message = mutableStateOf<String?>("This action could not be used.")
        val play = LiveTableFixture.play(5)
        compose.setContent { MaterialTheme {
            TableSurface(LiveTableFixture.game, tableBoard(play), "a", false, Modifier.fillMaxSize(), connected = true,
                runtime = TableRuntimeHooks(errorMessage = message.value, dismissError = { message.value = null }))
        } }
        compose.onNodeWithContentDescription("Dismiss message").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("This action could not be used.").assertDoesNotExist()
        compose.runOnIdle { message.value = "Another error" }
        compose.onNodeWithText("Another error").assertIsDisplayed()
        compose.mainClock.autoAdvance = false
        compose.mainClock.advanceTimeBy(7_100)
        compose.waitForIdle()
        compose.onNodeWithText("Another error").assertDoesNotExist()
    }
    // TRN-007
    @Test @Config(qualifiers = "w892dp-h412dp-land-mdpi")
    fun liveLandscape() = capture("live-landscape", true)
    @Test fun pausedPortrait() = capture("paused-portrait", false, paused = true)
    @Test @Config(qualifiers = "w892dp-h412dp-land-mdpi")
    fun pausedLandscape() = capture("paused-landscape", true, paused = true)
    @Test fun overflowPortrait() = capture("overflow-portrait", false, hand = 24)
    @Test @Config(qualifiers = "w892dp-h412dp-land-mdpi")
    fun overflowLandscape() = capture("overflow-landscape", true, hand = 24)

    @Test @Config(qualifiers = "w360dp-h640dp-port-xhdpi")
    fun deviceLivePortrait() = capture("device-live-portrait", false)
    @Test @Config(qualifiers = "w640dp-h360dp-land-xhdpi")
    fun deviceLiveLandscape() = capture("device-live-landscape", true)
    @Test @Config(qualifiers = "w360dp-h640dp-port-xhdpi")
    fun devicePausedPortrait() = capture("device-paused-portrait", false, paused = true)
    @Test @Config(qualifiers = "w640dp-h360dp-land-xhdpi")
    fun devicePausedLandscape() = capture("device-paused-landscape", true, paused = true)

    // TRN-010
    @Test fun controlsPortrait() = capture("controls-portrait", false, controls = true)
    @Test @Config(qualifiers = "w892dp-h412dp-land-mdpi")
    fun controlsLandscape() = capture("controls-landscape", true, controls = true)

    /** All panels run through the production toolkit host, not look-alike test composables. */
    @OptIn(ExperimentalRoborazziApi::class)
    private fun menu(kind: String, landscape: Boolean, submitOk: Boolean = true,
        turn: TurnState? = null): Pair<TableToolkitController, List<PlayAction>> {
        val initial = LiveTableFixture.menuPlay(kind == "token-ping-reveal").let { play ->
            if (turn == null) play else play.copy(board = play.board.copy(turn = turn)) }
        val sentinel = initial.board.cards.first { it.containerId == "a/BATTLEFIELD" && it.corner == "1/3" }
        // TBL-116: a card's menu holds what Automatum offers for it; the Forest offers its mana ability
        val forest = initial.board.cards.first { card -> card.containerId == "a/BATTLEFIELD" &&
            initial.definitions.single { it.digest == card.definition }.definition.name == "Forest" }
        val occurrenceId = "00000000-0000-4000-8000-000000000777"
        val play = when (kind) {
            "stack-contents" -> initial.copy(board = initial.board.copy(
                stackEffects = listOf(StackEffect(occurrenceId, sentinel.id)),
                containers = initial.board.containers.map { if (it.zone == PlayZone.STACK) it.copy(cards = listOf(occurrenceId)) else it }))
            "phase-picker" -> initial.copy(board = initial.board.copy(turn = TurnState("b", "4", "BEGINNING", "DRAW")))
            else -> initial
        }
        val sent = mutableListOf<PlayAction>()
        val board = engineTableBoard(play, "a")
        val toolkit = TableToolkitController("a", { board }, { true }, { action, _ -> sent += action; submitOk })
        when (kind) {
            "card-actions", "action-tooltip" -> toolkit.openCard(TableScope(forest.id, listOf(forest.id)))
            "player-actions" -> toolkit.open(TableIntent.Actions("life/b", null),
                TableItem("life/b", TableBounds(0f, 0f, 48f, 48f), "Bob, life 20", "20", "b", kind = TableItemKind.LIFE))
            // LOB-135: in a game the table offers Game settings
            "table-actions" -> { toolkit.assumeReveals = {}; toolkit.tableMenu() }
            "phase-picker" -> toolkit.phasePicker()
            "library-actions" -> toolkit.openLibrary("a/LIBRARY")
            "pile-contents" -> toolkit.open(TableIntent.Actions("a/GRAVEYARD", null),
                TableItem("a/GRAVEYARD", TableBounds(0f, 0f, 48f, 48f), "Graveyard", "2", "a", PlayZone.GRAVEYARD))
            "stack-contents" -> toolkit.stackContents()
        }
        compose.setContent {
            MaterialTheme {
                EngineFixtureTable(play, "a") {
                    TableSurface(LiveTableFixture.game, board, "a", landscape, Modifier.fillMaxSize(),
                        connected = true, onIntent = toolkit::handle, toolkit = toolkit,
                        runtime = TableRuntimeHooks(chat = {}, history = {}, navigation = {},
                            undo = { true }, controlsConnected = true))
                }
            }
        }
        compose.waitForIdle()
        if (kind == "action-tooltip") {
            compose.mainClock.advanceTimeBy(350)
            menuRow().performTouchInput {
                advanceEventTime(350); down(center); advanceEventTime(700); moveBy(Offset.Zero)
            }
            compose.mainClock.advanceTimeBy(700)
        }
        compose.waitForIdle()
        val output = File("$SCREENSHOTS/gameplay-$kind${if (turn != null) "-custom-turn" else ""}-${if (landscape) "landscape" else "portrait"}.png")
        requireNotNull(output.parentFile).mkdirs()
        val shown = when (kind) {
            "card-actions" -> "card menu open on your Forest, its mana ability as its one row"
            "player-actions" -> "the Life panel open on Bob's life square"
            "table-actions" -> "Table actions menu open"
            "phase-picker" -> "phase and step picker open"
            "action-tooltip" -> "card menu open on your Forest, holding its mana ability row to show its name tooltip"
            "library-actions" -> "your library menu open, with its View deck cell (ZON-093)"
            "pile-contents" -> "the search view open after tapping your two-card graveyard"
            "stack-contents" -> "Stack contents open, holding one Meadow Sentinel effect"
            else -> error("No caption for $kind")
        }
        val moment = when {
            kind == "phase-picker" -> "the opponent's turn 4, draw step"
            turn != null -> "your turn's cleanup step"
            else -> "your turn 4, main phase"
        }
        captureShot(output.path, "${if (landscape) "Landscape" else "Portrait"} table on $moment: $shown.")
        val image = ImageIO.read(output)
        assertEquals(if (landscape) 892 else 412, image.width)
        assertEquals(if (landscape) 412 else 892, image.height)
        if (kind == "action-tooltip") assertTrue(sent.isEmpty())
        return toolkit to sent
    }

    // SYS-045, TRN-008, TRN-010
    @Test fun cardActionsPortrait() { menu("card-actions", false) }
    @Test @Config(qualifiers = "w892dp-h412dp-land-mdpi") fun cardActionsLandscape() { menu("card-actions", true) }
    @Test fun playerActionsPortrait() { menu("player-actions", false) }
    @Test @Config(qualifiers = "w892dp-h412dp-land-mdpi") fun playerActionsLandscape() { menu("player-actions", true) }
    @Test fun tableActionsPortrait() { menu("table-actions", false) }
    @Test @Config(qualifiers = "w892dp-h412dp-land-mdpi") fun tableActionsLandscape() { menu("table-actions", true) }
    @Test fun phasePickerPortrait() { menu("phase-picker", false) }
    @Test @Config(qualifiers = "w892dp-h412dp-land-mdpi") fun phasePickerLandscape() { menu("phase-picker", true) }
    @Test fun actionTooltipPortrait() { menu("action-tooltip", false) }
    @Test @Config(qualifiers = "w892dp-h412dp-land-mdpi") fun actionTooltipLandscape() { menu("action-tooltip", true) }
    @Test fun libraryActionsPortrait() { menu("library-actions", false) }
    @Test @Config(qualifiers = "w892dp-h412dp-land-mdpi") fun libraryActionsLandscape() { menu("library-actions", true) }
    @Test fun pileContentsPortrait() { menu("pile-contents", false) }
    @Test @Config(qualifiers = "w892dp-h412dp-land-mdpi") fun pileContentsLandscape() { menu("pile-contents", true) }
    @Test fun stackContentsPortrait() { menu("stack-contents", false) }
    @Test @Config(qualifiers = "w892dp-h412dp-land-mdpi") fun stackContentsLandscape() { menu("stack-contents", true) }



    @Test fun tooltipReleaseDoesNotActivate() {
        val (toolkit, sent) = menu("action-tooltip", false)
        menuRow().performTouchInput { up() }
        compose.runOnIdle {
            assertEquals(null, toolkit.peek)
            assertTrue(sent.isEmpty())
        }
    }

    /** The first row of the open card menu (TBL-115). */
    private fun menuRow() = compose.onAllNodes(hasClickAction() and hasAnyAncestor(hasContentDescription("Card action ring"))).onFirst()




    // TBL-071, TBL-116 (I-D52): one page, only what Automatum offers for the card; no Inspect or Other side (holding
    // inspects, TBL-104), no "Attack →" (the lanes pick targets, CMB-048); nothing changes the card by hand
    @Test fun battlefieldMenuHasOnlyAutomatumsRows() {
        val (_, sent) = menu("card-actions", false)
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Card action ring").assertExists()
        for (name in listOf("Inspect", "Inspect other side", "Other side", "Attack →", "Choose action page", "Graveyard", "Edit card note", "Clone", "Ping / Unping", "Move to Stack"))
            compose.onNodeWithContentDescription(name).assertDoesNotExist()
        assertTrue(sent.isEmpty())
    }
}

/** Sample an unobstructed column through both fields, including the band/main boundary.
 * Review dims the board and covers its lower edge without changing the underlying viewport. */
internal fun assertRenderedLandsBands(image: java.awt.image.BufferedImage, scene: TableScene, viewer: String,
    overlayTop: Int = image.height, reviewing: Boolean = false, turnPlayer: String = "a") {
    for (field in scene.fields) {
        val lands = field.landsBounds(field.owner == viewer)
        // Card-driven rows can exceed the viewport; geometry tests verify the pan bounds.
        // Check the exact background colors of every currently visible part of either field.
        // Portrait Stack is a screen-fixed overlay on the world's right edge.
        // Its soft shadow reaches a few dp beyond it (the taller combat band can put that just inside a field).
        val overlay = scene.stackRegion?.takeIf { it.copy(y = it.y - 6f, height = it.height + 12f).intersection(field.bounds) != null }
        // Also avoid the fixed quick-action square to the left of the narrowed Stack area.
        val start = minOf(field.bounds.right - 8, overlay?.x?.minus(36) ?: Float.MAX_VALUE).toInt()
        // The combat area floats over the battlefields (CMB-007): the column passes beside it
        val x = (start downTo field.bounds.x.toInt() + 2).first { x -> scene.items.none { item ->
            item.kind != TableItemKind.HAND && (item.visible + listOfNotNull(item.countBadge)).any { part ->
                x >= part.x - 4 && x <= part.right + 4 && part.intersection(field.bounds) != null }
        } && scene.combatPanel?.let { x >= it.x - 8 && x <= it.right + 40 } != true }
        for (y in kotlin.math.ceil(field.bounds.y.toDouble()).toInt() until minOf(field.bounds.bottom.toInt(), overlayTop)) {
            // Avoid fractional, anti-aliased Canvas edges.
            if (y - field.bounds.y < 1 || field.bounds.bottom - y <= 1) continue
            if (kotlin.math.abs(y - lands.y) < 1 || kotlin.math.abs(y - lands.bottom) < 1) continue
            val inBand = lands.contains(TablePoint(x.toFloat(), y.toFloat()))
            val base = if (field.owner != turnPlayer) {
                if (inBand) 0xffdfe2e2.toInt() else 0xffeceeee.toInt()
            } else if (field.owner == viewer) {
                if (inBand) 0xffc4dcd0.toInt() else 0xffe2efe6.toInt()
            } else if (inBand) 0xffe9d1d3.toInt() else 0xfff4e8ea.toInt()
            val expected = if (reviewing) Color.Black.copy(alpha = .12f).compositeOver(Color(base)).toArgb() else base
            val actual = image.getRGB(x, y)
            val message = "${field.owner} battlefield at ($x, $y)"
            if (reviewing) {
                // Native Canvas uses premultiplied 8-bit alpha, unlike Color's float blend.
                // Allow its single-channel rounding, but still verify every tinted pixel.
                for (shift in listOf(0, 8, 16)) assertTrue(kotlin.math.abs(
                    ((expected shr shift) and 255) - ((actual shr shift) and 255)) <= 1, message)
                assertEquals(255, actual ushr 24, message)
            } else assertEquals(expected, actual, message)
        }
    }
}


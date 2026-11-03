package org.asyncttwiz.android

import androidx.compose.ui.test.click

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
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
        val toolkit = TableToolkitController("a", { play }, { !paused }, { _, _ -> true })
        val playback = LiveTableFixture.playback()
        compose.setContent {
            MaterialTheme {
                Column(Modifier.fillMaxSize()) {
                    TableSurface(LiveTableFixture.game, play, "a", landscape, Modifier.weight(1f),
                        connected = !paused, onIntent = toolkit::handle, toolkit = toolkit,
                        runtime = TableRuntimeHooks(chat = {}, history = {}, navigation = {}, chatUnread = 1,
                            undo = { true }, reviewing = paused, controlsConnected = true,
                            returnCurrent = if (paused) ({}) else null,
                            reviewPanel = if (paused) ({ HistoryPlaybackPanel(playback, playback.tree.sharedHeadId, "a", true, 240L, {},
                                overlay = true, landscape = landscape) }) else null))
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
                val normal = TablePresentation.scene(LiveTableFixture.game, play, "a",
                    image.width.toFloat(), image.height.toFloat(), landscape, reviewing = paused, landscapeHome = true)
                val scene = normal
                assertRenderedLandsBands(image, scene, "a",
                    overlayTop = image.height - tableBottomHeight(landscape).toInt(), reviewing = paused,
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
                TableSurface(LiveTableFixture.game, play, "a", false, Modifier.fillMaxSize(), connected = true,
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
            TableSurface(LiveTableFixture.game, play, "a", false, Modifier.fillMaxSize(), connected = true,
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
        val occurrenceId = "00000000-0000-4000-8000-000000000777"
        val play = when (kind) {
            "stack-contents" -> initial.copy(board = initial.board.copy(
                stackEffects = listOf(StackEffect(occurrenceId, sentinel.id)),
                containers = initial.board.containers.map { if (it.zone == PlayZone.STACK) it.copy(cards = listOf(occurrenceId)) else it }))
            "phase-picker" -> initial.copy(board = initial.board.copy(turn = TurnState("b", "4", "BEGINNING", "DRAW")))
            else -> initial
        }
        val sent = mutableListOf<PlayAction>()
        val toolkit = TableToolkitController("a", { play }, { true }, { action, _ -> sent += action; submitOk })
        when (kind) {
            "card-actions", "action-tooltip" -> toolkit.openCard(TableScope(sentinel.id, listOf(sentinel.id)))
            "player-actions" -> toolkit.open(TableIntent.Actions("life/b", null),
                TableItem("life/b", TableBounds(0f, 0f, 48f, 48f), "Bob, life 20", "20", "b", kind = TableItemKind.LIFE))
            "table-actions" -> toolkit.tableMenu()
            "phase-picker" -> toolkit.phasePicker()
            "library-actions" -> toolkit.openLibrary("a/LIBRARY")
            "pile-contents" -> toolkit.open(TableIntent.Actions("a/GRAVEYARD", null),
                TableItem("a/GRAVEYARD", TableBounds(0f, 0f, 48f, 48f), "Graveyard", "2", "a", PlayZone.GRAVEYARD))
            "stack-contents" -> toolkit.stackContents()
            "card-inspection" -> toolkit.inspect(sentinel.id)
        }
        compose.setContent {
            MaterialTheme {
                TableSurface(LiveTableFixture.game, play, "a", landscape, Modifier.fillMaxSize(),
                    connected = true, onIntent = toolkit::handle, toolkit = toolkit,
                    runtime = TableRuntimeHooks(chat = {}, history = {}, navigation = {},
                        undo = { true }, controlsConnected = true))
            }
        }
        compose.waitForIdle()
        if (kind == "action-tooltip") {
            compose.mainClock.advanceTimeBy(350)
            compose.onNodeWithContentDescription("Inspect", useUnmergedTree = false).performTouchInput {
                advanceEventTime(350); down(center); advanceEventTime(700); moveBy(Offset.Zero)
            }
            compose.mainClock.advanceTimeBy(700)
            compose.onNodeWithText("Inspect").assertExists()
        }
        compose.waitForIdle()
        val output = File("$SCREENSHOTS/gameplay-$kind${if (turn != null) "-custom-turn" else ""}-${if (landscape) "landscape" else "portrait"}.png")
        requireNotNull(output.parentFile).mkdirs()
        val shown = when (kind) {
            "card-actions" -> "card menu open on your Meadow Sentinel"
            "player-actions" -> "the Life panel open on Bob's life square"
            "table-actions" -> "Table actions menu open"
            "phase-picker" -> "phase and step picker open"
            "action-tooltip" -> "card menu open on your Meadow Sentinel, holding Inspect to show its name tooltip"
            "library-actions" -> "your library menu open"
            "pile-contents" -> "actions open for your two-card graveyard"
            "stack-contents" -> "Stack contents open, holding one Meadow Sentinel effect"
            "card-inspection" -> "Inspect view open for your Meadow Sentinel"
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
    @Test fun cardInspectionPortrait() { menu("card-inspection", false) }
    @Test @Config(qualifiers = "w892dp-h412dp-land-mdpi") fun cardInspectionLandscape() { menu("card-inspection", true) }



    @Test fun tooltipReleaseDoesNotInspect() {
        val (toolkit, sent) = menu("action-tooltip", false)
        compose.onNodeWithText("Inspect").assertExists()
        compose.onNodeWithContentDescription("Inspect").performTouchInput { up() }
        compose.runOnIdle {
            assertEquals(null, toolkit.inspection)
            assertTrue(sent.isEmpty())
        }
    }




    // TBL-071, TBL-116 (I-D52): one page, Inspect, Other side and Attack →; nothing changes the card by hand
    @Test fun battlefieldMenuHasOnlyInspectOtherSideAndAttack() {
        val (_, sent) = menu("card-actions", false)
        compose.waitForIdle()
        for (name in listOf("Inspect", "Inspect other side", "Attack →")) compose.onNodeWithContentDescription(name).assertExists()
        for (name in listOf("Choose action page", "Graveyard", "Edit card note", "Clone", "Ping / Unping", "Move to Stack"))
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

internal object LiveTableFixture {
    val game = TableFixture.game.copy(gameName = "Crackling Mist 4")
    private val definitions = listOf(
        DefinitionFace("land", "Forest", typeLine = "Basic Land — Forest", scryfallId = placeholderPrinting("Forest")),
        DefinitionFace("sentinel", "Meadow Sentinel", typeLine = "Creature — Soldier", power = "1", toughness = "3", corner = "1/3",
            scryfallId = placeholderPrinting("Meadow Sentinel")),
        DefinitionFace("river", "River Adept", typeLine = "Creature — Wizard", power = "1", toughness = "2", corner = "1/2",
            scryfallId = placeholderPrinting("River Adept")),
        DefinitionFace("guide", "Lantern Guide", typeLine = "Creature — Scout", power = "2", toughness = "3", corner = "2/3",
            scryfallId = placeholderPrinting("Lantern Guide")),
        DefinitionFace("aegis", "Aegis Ward", typeLine = "Enchantment", scryfallId = placeholderPrinting("Aegis Ward"))
    ).map { face -> CardDefinition(face.name, listOf(face)).let { SubmittedDefinition(DeckJson.digest(it), it) } }

    fun play(hand: Int = 5): PlayState {
        val cards = mutableListOf<PlayCard>()
        val containers = mutableListOf<PlayContainer>()
        for (owner in listOf("a", "b")) for (zone in PlayZone.entries.filter { it != PlayZone.STACK }) {
            val count = when (zone) {
                PlayZone.HAND -> if (owner == "a" || hand > 5) hand else 3
                PlayZone.BATTLEFIELD -> 5
                PlayZone.LIBRARY -> 50
                PlayZone.GRAVEYARD -> 2
                else -> 0
            }
            val id = "$owner/$zone"
            val members = (0 until count).map { "$id/$it" }
            members.forEachIndexed { n, cardId ->
                val definition = definitions[when (zone) {
                    PlayZone.BATTLEFIELD -> if (n < 2) 0 else n - 1
                    else -> n % definitions.size
                }]
                val face = definition.definition.faces.single()
                cards += PlayCard(cardId, definition.digest, owner, id, face.faceId,
                    covered = zone == PlayZone.LIBRARY || zone == PlayZone.HAND,
                    tapped = zone == PlayZone.BATTLEFIELD && n == 1, corner = face.corner,
                    placement = if (zone == PlayZone.BATTLEFIELD) PlayPlacement(
                        BoardPosition(if (n < 2) n else n - 2, if (n < 2) 0 else 2), cardId) else null)
            }
            containers += PlayContainer(id, zone, owner, members)
        }
        containers += PlayContainer("stack", PlayZone.STACK)
        return TableFixture.play().copy(definitions = definitions, board = BoardState(
            listOf(PlayPlayer("a", "20", mapOf(CounterColor.BLUE to "3", CounterColor.GRAY to "0")), PlayPlayer("b", "17")),
            cards, containers, TurnState("a", "4", "PRECOMBAT_MAIN", "MAIN")))
    }

    fun menuPlay(showcase: Boolean = false): PlayState {
        val play = play()
        val ids = play.board.cards.associate { it.id to java.util.UUID.nameUUIDFromBytes(it.id.toByteArray()).toString() }
        val cards = play.board.cards.map { it.copy(id = ids.getValue(it.id),
            placement = it.placement?.copy(pileId = ids.getValue(it.id))) }
        val shown = cards.filter { it.containerId == "a/LIBRARY" }.take(2).map { it.id }
        val normalized = play.copy(board = play.board.copy(cards = cards,
            containers = play.board.containers.map { it.copy(cards = it.cards.map(ids::getValue)) }))
        if (!showcase) return normalized
        val definition = CardDefinition("Soldier", listOf(DefinitionFace("soldier", "Soldier", typeLine = "Token Creature — Soldier", corner = "1/1",
            scryfallId = placeholderPrinting("Soldier"))))
        val submitted = SubmittedDefinition(DeckJson.digest(definition), definition)
        val token = PlayCard(java.util.UUID.nameUUIDFromBytes("soldier".toByteArray()).toString(), submitted.digest,
            "a", "a/BATTLEFIELD", "soldier", covered = false, corner = "1/1", token = true,
            placement = PlayPlacement(BoardPosition(3, 2), "soldier-pile"))
        val group = "ping-group"
        val reference = ReferencedEventContext(EventReference(play.id, play.currentNodeId), "fixture")
        val pings = listOf(cards.first { it.containerId == "a/BATTLEFIELD" && it.corner == "1/3" }.id, token.id).mapIndexed { n, id ->
            val target = PingTarget(PingTargetKind.CARD, id)
            RecordedPingChange(AnnotationRecording(play.id, "branch", play.currentNodeId, "ping-$n", group, "request-$n", "a"),
                reference, target, null, PingPresence(target, (n + 1).toString(), "ping-$n"))
        }
        return normalized.copy(definitions = normalized.definitions + submitted,
            currentGroup = GameplayGroup(group, "a", play.currentNodeId, play.currentNodeId, "ping", null, "2"),
            annotations = EventAnnotationState(pings = pings), board = normalized.board.copy(cards = cards + token,
                containers = normalized.board.containers.map { if (it.id == token.containerId) it.copy(cards = it.cards + token.id) else it },
                contentsDisplays = listOf(ContentsDisplay("reveal", "a", "a/LIBRARY", ContentsSelection.TOP, 2, true, shown))))
    }

    fun playback(): HistoryPlayback {
        fun id(n: Int) = "00000000-0000-4000-8000-${n.toString().padStart(12, '0')}"
        val nodes = (1..12).map { n -> GameplayMutation(id(n), id(n - 1),
            id(100), id(200 + n), if (n % 3 == 0) "b" else "a", null,
            PlayAction.SetLife("a", (20 - n).toString()), GameplayTransition(),
            grouping = HistorySemantic(HistorySignificance.LIFE_PLAYER_COUNTER_EDIT, HistoryIcon.FAVORITE, null)) }
        return HistoryPlayback(HistoryTree(id(0), nodes, id(12), "fixture")).apply { scrub(id(6)) }
    }
}

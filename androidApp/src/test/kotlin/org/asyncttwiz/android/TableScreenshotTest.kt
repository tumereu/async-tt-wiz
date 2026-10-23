package org.asyncttwiz.android

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.github.takahirom.roborazzi.captureRoboImage
import com.github.takahirom.roborazzi.captureScreenRoboImage
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
                        runtime = TableRuntimeHooks(chat = {}, history = {}, tree = {}, navigation = {}, chatUnread = 1,
                            undo = { true }, redo = { true }, reviewing = paused, controlsConnected = true,
                            continueHere = if (paused) ({}) else null, returnCurrent = if (paused) ({}) else null,
                            reviewPanel = if (paused) ({ HistoryPlaybackPanel(playback, playback.tree.sharedHeadId, "a", true, 240L, {},
                                overlay = true, landscape = landscape) }) else null))
                }
            }
        }
        compose.waitForIdle()
        compose.onNodeWithText("View · 50").assertDoesNotExist()
        if (paused) {
            compose.onNodeWithContentDescription("Interaction mode").assertDoesNotExist()
            compose.onNodeWithContentDescription("Continue playing from here").assertExists()
        } else {
            for (label in listOf("Play", "Previous", "Next", "Continue playing from here", "Return to current"))
                compose.onNodeWithContentDescription(label).assertDoesNotExist()
            for (label in listOf("Live", "Download history", "Event details")) compose.onNodeWithText(label).assertDoesNotExist()
        }
        val output = File("build/outputs/roborazzi/$name.png")
        requireNotNull(output.parentFile).mkdirs()
        if (controls) {
            compose.onNodeWithContentDescription("Game controls").performClick()
            compose.onNodeWithText("Table actions").assertExists()
            compose.onNodeWithText("View · 50").assertExists()
            compose.waitForIdle()
            captureScreenRoboImage(output.path)
        } else compose.onRoot().captureRoboImage(output.path)
        val image = ImageIO.read(output)
        if (name.startsWith("device-")) {
            assertEquals(if (landscape) 1280 else 720, image.width)
            assertEquals(if (landscape) 720 else 1280, image.height)
        } else {
            assertEquals(if (landscape) 892 else 412, image.width)
            assertEquals(if (landscape) 412 else 892, image.height)
        }
    }

    @Test fun livePortrait() = capture("live-portrait", false)
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

    @Test fun controlsPortrait() = capture("controls-portrait", false, controls = true)
    @Test @Config(qualifiers = "w892dp-h412dp-land-mdpi")
    fun controlsLandscape() = capture("controls-landscape", true, controls = true)
}

internal object LiveTableFixture {
    val game = TableFixture.game.copy(gameName = "Crackling Mist 4")
    private val definitions = listOf(
        DefinitionFace("land", "Forest", typeLine = "Basic Land — Forest"),
        DefinitionFace("sentinel", "Meadow Sentinel", typeLine = "Creature — Soldier", power = "1", toughness = "3", corner = "1/3"),
        DefinitionFace("river", "River Adept", typeLine = "Creature — Wizard", power = "1", toughness = "2", corner = "1/2"),
        DefinitionFace("guide", "Lantern Guide", typeLine = "Creature — Scout", power = "2", toughness = "3", corner = "2/3"),
        DefinitionFace("aegis", "Aegis Ward", typeLine = "Enchantment")
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

    fun playback(): HistoryPlayback {
        fun id(n: Int) = "00000000-0000-4000-8000-${n.toString().padStart(12, '0')}"
        val nodes = (1..12).map { n -> GameplayMutation(id(n), id(n - 1),
            id(100), id(200 + n), if (n % 3 == 0) "b" else "a", null,
            PlayAction.SetLife("a", (20 - n).toString()), GameplayTransition(),
            grouping = HistorySemantic(HistorySignificance.LIFE_PLAYER_COUNTER_EDIT, "favorite", null)) }
        return HistoryPlayback(HistoryTree(id(0), nodes, id(12), "fixture")).apply { scrub(id(6)) }
    }
}

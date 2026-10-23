package org.asyncttwiz.android

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.asyncttwiz.protocol.*
import java.io.File

/** Native Compose/Canvas screenshots, resized to the mock's 1px/dp for visual review. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w412dp-h892dp-port-mdpi")
class TableScreenshotTest {
    @get:Rule val compose = createComposeRule()

    private fun capture(name: String, landscape: Boolean, paused: Boolean = false, hand: Int = 5) {
        val play = LiveTableFixture.play(hand)
        val playback = HistoryPlayback(HistoryTree("root", emptyList(), "root", "fixture")).apply { scrub("root") }
        compose.setContent {
            MaterialTheme {
                Column(Modifier.fillMaxSize()) {
                    TableSurface(LiveTableFixture.game, play, "a", landscape, Modifier.weight(1f),
                        runtime = TableRuntimeHooks(reviewing = paused))
                    if (paused) HistoryPlaybackPanel(playback, "root", "a", true, 240L, {})
                }
            }
        }
        compose.waitForIdle()
        val output = File("build/outputs/roborazzi/$name.png")
        output.parentFile.mkdirs()
        compose.onRoot().captureRoboImage(output.path)
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
}

package org.asyncttwiz.android

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.github.takahirom.roborazzi.captureRoboImage
import org.asyncttwiz.protocol.*
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import kotlin.test.*

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-port-420dpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PanelRevisionUiTest {
    @get:Rule val compose = createComposeRule()
    private val f = BasicTableFixture()
    @After fun close() { f.lobby.close(); f.worker.drain() }

    private fun panels(landscape: Boolean, selection: ContentsSelection = ContentsSelection.TOP, reveal: Boolean = false,
        zone: PlayZone = PlayZone.GRAVEYARD) {
        f.connect()
        assertTrue(f.move(40, TableDestination.Container(f.container(zone), zone)))
        assertTrue(f.lobby.gameplay(PlayAction.ShowContents(f.container(PlayZone.LIBRARY), selection, 2, reveal)))
        val ui = TableToolkitController(f.actor, { f.play }, { true }, f.lobby::gameplay)
        ui.openPublicPile(f.container(zone))
        compose.setContent { MaterialTheme { TableSurface(f.game, f.play, f.actor, landscape,
            connected = true, toolkit = ui, onIntent = ui::handle) } }
        compose.waitForIdle()
        val file = File("build/outputs/roborazzi/r13-panels-${if (landscape) "landscape" else "portrait"}-${if (reveal) "reveal" else selection.name}.png")
        file.parentFile!!.mkdirs()
        compose.onRoot().captureRoboImage(file.path)
        val count = f.commands().size
        compose.onNodeWithContentDescription("Shared table").performTouchInput {
            // A clear point on the battlefield, outside both left panels and the Stack.
            click(Offset(width * .65f, height * .65f))
        }
        compose.runOnIdle {
            assertNull(ui.layoutState.value.localPileId)
            assertEquals(1, f.play.board.contentsDisplays.size)
            assertEquals(count, f.commands().size, "Outside dismissal is local, never DismissContents")
        }
    }

    @Test fun lookAndLocalPortrait() = panels(false)
    @Test fun revealAndLocalPortrait() = panels(false, reveal = true)
    @Test fun searchAndLocalPortrait() = panels(false, ContentsSelection.ALL)
    @Test fun exileOutsideDismissal() = panels(false, zone = PlayZone.EXILE)
    @Test @Config(qualifiers = "w891dp-h411dp-land-420dpi") fun lookAndLocalLandscape() = panels(true)

    private fun stackAndValues(landscape: Boolean) {
        f.connect()
        assertTrue(f.move(40, TableDestination.Grid(f.container(PlayZone.BATTLEFIELD), 0, 1)))
        val ui = TableToolkitController(f.actor, { f.play }, { true }, f.lobby::gameplay)
        repeat(2) { assertTrue(ui.addEffects(listOf(f.id(40)))) }
        compose.setContent { MaterialTheme { TableSurface(f.game, f.play, f.actor, landscape,
            connected = true, toolkit = ui, onIntent = ui::handle) } }
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Put to exile").assertIsDisplayed()
        val orientation = if (landscape) "landscape" else "portrait"
        val output = File("build/outputs/roborazzi/r13-stack-$orientation.png")
        output.parentFile!!.mkdirs()
        compose.onRoot().captureRoboImage(output.path)
        compose.runOnIdle { ui.showStackBubbles(null); ui.manual.player(f.play, f.actor) }
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Set life").assertIsDisplayed()
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/r13-values-$orientation.png")
    }

    @Test fun stackAndValuesPortrait() = stackAndValues(false)
    @Test @Config(qualifiers = "w891dp-h411dp-land-420dpi") fun stackAndValuesLandscape() = stackAndValues(true)
}

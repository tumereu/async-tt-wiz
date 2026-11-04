package org.asyncttwiz.android

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import org.asyncttwiz.protocol.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import kotlin.test.*

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w412dp-h892dp-port-mdpi")
class TableInteractionsScreenshotTest {
    @get:Rule val compose = createComposeRule()
    private val f = TableStatesFixture
    private val sent = mutableListOf<PlayAction>()
    private fun render(width: Float = 412f, height: Float = 892f, font: Float = 1f): Pair<TableToolkitController, TableScene> {
        val play = f.combat()
        val toolkit = TableToolkitController(f.a, { TableBoard.of(play) }, { true }, { action, _ -> sent += action; true })
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f, font)) {
                MaterialTheme { TableSurface(f.game, TableBoard.of(play), f.a, width > height, Modifier.fillMaxSize(),
                    connected = true, toolkit = toolkit, onIntent = toolkit::handle) }
            }
        }
        compose.waitForIdle()
        return toolkit to TablePresentation.scene(f.game, TableBoard.of(play), f.a, width, height, width > height)
    }

    @OptIn(ExperimentalRoborazziApi::class)
    private fun capture(name: String, caption: String) {
        val file = File("$SCREENSHOTS/interactions-$name.png")
        file.parentFile!!.mkdirs()
        captureShot(file.path, caption)
    }

    private fun menu(width: Float, height: Float, font: Float) {
        val (toolkit, _) = render(width, height, font)
        compose.runOnIdle {
            val id = f.source(f.a, "1/3").id
            toolkit.openCard(TableScope(id, listOf(id)))
        }
        compose.onNodeWithContentDescription("Inspect").assertIsDisplayed()
        val screen = "${width.toInt()}x${height.toInt()} ${if (width > height) "landscape" else "portrait"} table in combat, " +
            "text size ${font}x: card menu on your attacking Meadow Sentinel"
        capture("menu-${width.toInt()}x${height.toInt()}-font$font", "$screen.")
        for (label in listOf("Inspect", "Inspect other side", "Attack →")) {
            val bounds = compose.onNodeWithContentDescription(label).fetchSemanticsNode().boundsInRoot
            assertTrue(bounds.width >= 48f && bounds.height >= 48f, "$label keeps its touch target")
        }
        assertTrue(sent.isEmpty())
    }

    // TBL-063
    @Test fun contextMenuNormal() { menu(412f, 892f, 1f) }
    @Test @Config(qualifiers = "w360dp-h640dp-port-mdpi") fun contextMenuSmallLargeFont() { menu(360f, 640f, 1.3f) }
    @Test @Config(qualifiers = "w640dp-h360dp-land-mdpi") fun contextMenuLandscapeLargeFont() { menu(640f, 360f, 1.3f) }


    private fun drag() {
        val (_, scene) = render()
        val source = scene.items.first { it.zone == PlayZone.HAND && it.kind == TableItemKind.CARD && it.owner == f.a && it.visible.isNotEmpty() }
        val start = source.visible.first().center
        val end = scene.fields.last().let { TablePoint(it.bounds.right - 70, it.landsBounds(true, scene.camera).center.y) }
        compose.mainClock.autoAdvance = false
        compose.onNodeWithContentDescription("Shared table").performTouchInput {
            // ZON-081: a hand card is dragged by first moving up (sideways pans the hand)
            down(Offset(start.x, start.y)); moveTo(Offset(start.x, start.y - 30f), 40); moveTo(Offset(end.x, end.y), 80)
        }
        compose.waitForIdle(); compose.mainClock.advanceTimeByFrame(); compose.mainClock.advanceTimeByFrame()
        try {
            capture("landing-lands", "Portrait table in combat, dragging a hand card onto your battlefield's lands row: " +
                "landing placeholders show where it would go.")
            assertTrue(sent.isEmpty(), "A preview never submits a move")
        } finally {
            compose.onNodeWithContentDescription("Shared table").performTouchInput { cancel() }
            compose.mainClock.autoAdvance = true
        }
    }

    // TBL-052
    @Test fun dragShowsLandingPlaceholders() { drag() }
}

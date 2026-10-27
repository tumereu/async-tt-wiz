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
import com.github.takahirom.roborazzi.captureScreenRoboImage
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
        val toolkit = TableToolkitController(f.a, { play }, { true }, { action, _ -> sent += action; true })
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f, font)) {
                MaterialTheme { TableSurface(f.game, play, f.a, width > height, Modifier.fillMaxSize(),
                    connected = true, toolkit = toolkit, onIntent = toolkit::handle) }
            }
        }
        compose.waitForIdle()
        return toolkit to TablePresentation.scene(f.game, play, f.a, width, height, width > height)
    }

    @OptIn(ExperimentalRoborazziApi::class)
    private fun capture(name: String) {
        val file = File("build/outputs/roborazzi/interactions-$name.png")
        file.parentFile!!.mkdirs()
        captureScreenRoboImage(file.path)
    }

    private fun menu(width: Float, height: Float, font: Float) {
        val (toolkit, _) = render(width, height, font)
        compose.runOnIdle {
            val id = f.source(f.a, "1/3").id
            toolkit.openCard(TableScope(id, listOf(id)))
        }
        compose.onNodeWithContentDescription("Inspect").assertIsDisplayed()
        capture("menu-${width.toInt()}x${height.toInt()}-font$font")
        for ((page, label) in listOf(1 to "Set corner", 2 to "Set gray counter", 2 to "Decrease gray counter", 4 to "Move to Stack")) {
            compose.ringPage(page)
            compose.onNodeWithContentDescription(label).assertIsDisplayed()
            val bounds = compose.onNodeWithContentDescription(label).fetchSemanticsNode().boundsInRoot
            assertTrue(bounds.width >= (if (label.startsWith("Decrease")) 24f else 48f) && bounds.height >= 48f, "$label retains its widget target")
        }
        capture("menu-${width.toInt()}x${height.toInt()}-font$font-move")
        assertTrue(sent.isEmpty())
    }

    @Test fun contextMenuNormal() { menu(412f, 892f, 1f) }
    @Test @Config(qualifiers = "w360dp-h640dp-port-mdpi") fun contextMenuSmallLargeFont() { menu(360f, 640f, 1.3f) }
    @Test @Config(qualifiers = "w640dp-h360dp-land-mdpi") fun contextMenuLandscapeLargeFont() { menu(640f, 360f, 1.3f) }

    @Test fun physicalSecondTapThroughJustOpenedMenuRotatesOnceAndDismissesIt() {
        val (toolkit, scene) = render()
        val item = scene.items.single { it.cardId == f.source(f.a, "1/3").id }
        val p = item.visible.first().center
        compose.mainClock.autoAdvance = false
        compose.onNodeWithContentDescription("Shared table").performTouchInput { click(Offset(p.x, p.y)) }
        compose.waitForIdle(); compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithContentDescription("Inspect").assertIsDisplayed()
        // The transparent ring relays only this same-card replacement; it owns other outside taps.
        compose.onNodeWithContentDescription("Card action ring").performTouchInput { advanceEventTime(80); click(Offset(p.x, p.y)) }
        compose.mainClock.autoAdvance = true
        compose.onNodeWithContentDescription("Card action ring").assertDoesNotExist()
        compose.runOnIdle { assertNull(toolkit.manual.scope) }
        assertEquals(listOf<PlayAction>(PlayAction.SetTapped(item.cardId!!, !item.tapped)), sent)
    }

    private fun drag(hand: Boolean) {
        val (_, scene) = render()
        val source = scene.items.first { it.zone == PlayZone.HAND && it.kind == TableItemKind.CARD && it.owner == f.a && it.visible.isNotEmpty() }
        val start = source.visible.first().center
        val end = if (hand) scene.items.single { it.kind == TableItemKind.HAND && it.owner == f.a }.bounds.let { TablePoint(it.right - 4, it.center.y) }
            else scene.fields.last().let { TablePoint(it.bounds.right - 70, it.landsBounds(true, scene.camera).center.y) }
        compose.mainClock.autoAdvance = false
        compose.onNodeWithContentDescription("Shared table").performTouchInput {
            down(Offset(start.x, start.y)); moveTo(Offset(end.x, end.y), 80)
        }
        compose.waitForIdle(); compose.mainClock.advanceTimeByFrame(); compose.mainClock.advanceTimeByFrame()
        try {
            capture(if (hand) "hand-insertion" else "landing-lands")
            assertTrue(sent.isEmpty(), "A preview never submits a move")
        } finally {
            compose.onNodeWithContentDescription("Shared table").performTouchInput { cancel() }
            compose.mainClock.autoAdvance = true
        }
    }

    @Test fun dragShowsLandingPlaceholders() { drag(false) }
    @Test fun handDragShowsInsertionLine() { drag(true) }
}

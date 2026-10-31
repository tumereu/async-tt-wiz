package org.asyncttwiz.android

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureScreenRoboImage
import android.graphics.Paint
import javax.imageio.ImageIO
import org.asyncttwiz.protocol.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import kotlin.test.*

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w412dp-h892dp-port-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CardSettingsUiTest {
    @get:Rule val compose = createComposeRule()
    private val context get() = ApplicationProvider.getApplicationContext<Context>()
    @Before fun clear() { context.getSharedPreferences("card-appearance", Context.MODE_PRIVATE).edit().clear().commit() }

    // LOB-081, TBL-034, TBL-036
    @Test fun typedValuesValidateLimitsAndLiveSampleTracksSliderAndCustomization() {
        val preferences = CardAppearancePreferences(context)
        compose.setContent { MaterialTheme { Column(Modifier.verticalScroll(rememberScrollState())) { CardSettings(preferences) } } }
        compose.onNodeWithTag("card-size-value").performClick()
        compose.onNodeWithTag("card-scale-input").performTextReplacement("9")
        compose.onNodeWithTag("card-scale-save").assertIsEnabled().performClick()
        compose.runOnIdle { assertEquals(3f, preferences.read().cardSize) }
        compose.onNodeWithTag("card-size-value").performClick()
        compose.onNodeWithTag("card-scale-input").performTextReplacement("NaN")
        compose.onNodeWithTag("card-scale-save").assertIsNotEnabled()
        compose.onNodeWithTag("card-scale-input").performTextReplacement("0.1")
        compose.onNodeWithTag("card-scale-save").assertIsEnabled().performClick()
        compose.onNodeWithTag("card-size-sample").assertWidthIsEqualTo(14.dp).assertHeightIsEqualTo(20.dp)
        compose.onNodeWithTag("card-size").performTouchInput { swipeRight() }
        compose.runOnIdle { assertTrue(preferences.read().cardSize > .5f) }
        compose.onNodeWithText("Customize per area").performClick()
        compose.onNodeWithTag("card-size-HAND").assertExists()
        compose.onNodeWithTag("card-size-HAND-value").performScrollTo().performClick()
        compose.onNodeWithTag("card-scale-input").performTextReplacement("3")
        compose.onNodeWithTag("card-scale-save").performClick()
        compose.onNodeWithTag("card-size-HAND-sample").assertWidthIsEqualTo(84.dp).assertHeightIsEqualTo(120.dp)
        compose.runOnIdle { assertEquals(3f, preferences.read().size(CardArea.HAND)) }
    }

    @OptIn(ExperimentalRoborazziApi::class)
    private fun screenshots(landscape: Boolean, annotations: Boolean = false) {
        val f = TableStatesFixture
        val initial = f.combat()
        val library = initial.board.containers.first { it.zone == PlayZone.LIBRARY && it.accountId == f.a }
        val play = initial.copy(board = initial.board.copy(contentsDisplays = listOf(
            ContentsDisplay("sample-panel", f.a, library.id, ContentsSelection.TOP, library.cards.size, false, library.cards)),
            cards = initial.board.cards.map { it.copy(note = "Flying", counters = mapOf(CounterColor.BLUE to "1", CounterColor.RED to "2")) }))
        val preferences = CardAppearancePreferences(context)
        val toolkit = TableToolkitController(f.a, { play }, { true }, { _, _ -> true })
        compose.setContent { MaterialTheme { TableSurface(f.game, play, f.a, landscape, Modifier.fillMaxSize(), toolkit = toolkit) } }
        for (scale in listOf(.5f, 3f)) for (zoom in listOf(.65f, 1f, 2f)) {
            compose.runOnIdle {
                preferences.setSize(if (annotations) 1f else scale); preferences.setText(scale)
                toolkit.layoutState.value = TableLayoutState(camera = TableCamera(zoom = zoom))
            }
            compose.waitForIdle()
            compose.mainClock.advanceTimeBy(1000)
            compose.waitForIdle()
            val name = if (annotations) "card-annotations" else "card-sizes"
            val file = File("$SCREENSHOTS/$name-${if (landscape) "landscape" else "portrait"}-$scale-$zoom.png")
            file.parentFile!!.mkdirs()
            captureScreenRoboImage(file.path)
            if (!annotations && scale == 3f && zoom == 1f) {
                val home = TablePresentation.scene(f.game, play, f.a, if (landscape) 892f else 412f,
                    if (landscape) 412f else 892f, landscape, appearance = preferences.read())
                val world = home.world!!
                for (field in home.fields) {
                    val card = home.items.first { it.viewport == field.containerId }
                    val camera = world.clamp(TableCamera(
                        x = card.bounds.center.x - world.viewport.center.x,
                        y = card.bounds.center.y - world.viewport.center.y))
                    compose.runOnIdle { toolkit.layoutState.value = TableLayoutState(camera = camera) }
                    compose.waitForIdle()
                    captureScreenRoboImage("${file.path.removeSuffix(".png")}-${if (field.owner == f.a) "own" else "opponent"}-panned.png")
                }
            }
            if (annotations) {
                val image = ImageIO.read(file)
                val scene = TablePresentation.scene(f.game, play, f.a, image.width.toFloat(), image.height.toFloat(), landscape,
                    toolkit.layoutState.value, appearance = preferences.read())
                // Piles must retain their top card's counters too, not just its note.
                assertTrue(scene.items.any { it.kind == TableItemKind.PILE && !it.covered && it.counters.size == 2 })
                assertTrue(scene.items.any { it.zone == PlayZone.HAND && !it.covered && it.counters.size == 2 })
                assertTrue(scene.items.any { it.zone == PlayZone.STACK && !it.covered && it.counters.size == 2 })
                assertTrue(scene.items.any { it.viewport.startsWith("cardPanel/") && it.counters.size == 2 })
                assertCounterPixels(image, scene, scale)
            }
        }
    }

    @Test fun portraitSizeExtremes() { screenshots(false) }
    @Test @Config(qualifiers = "w892dp-h412dp-land-mdpi")
    fun landscapeSizeExtremes() { screenshots(true) }

    // TBL-026
    @Test fun portraitAnnotationTextAndZoomExtremes() { screenshots(false, annotations = true) }
    @Test @Config(qualifiers = "w892dp-h412dp-land-mdpi")
    fun landscapeAnnotationTextAndZoomExtremes() { screenshots(true, annotations = true) }

    private fun assertCounterPixels(image: java.awt.image.BufferedImage, scene: TableScene, textScale: Float) {
        val metrics = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 7f * textScale }.fontMetrics
        val lineHeight = metrics.bottom - metrics.top + 2f
        val layout = cardAnnotationLayout(maxOf(11f, 7f * textScale + 3f), 40f - maxOf(10f, 7f * textScale + 3f),
            lineHeight, maxOf(10f * textScale, lineHeight), true, 2)
        // Check unobstructed upright faces in every HUD/world region that is on screen.
        val checked = mutableSetOf<String>()
        for (item in scene.items.filter { !it.covered && !it.tapped && it.counters.size == 2 &&
            (it.kind == TableItemKind.CARD || it.kind == TableItemKind.PILE) }) {
            if (item.visible.none { it == item.bounds } || item.bounds.bottom >= image.height || item.bounds.x < 0) continue
            if (scene.items.any { other -> other !== item && other.kind != TableItemKind.HAND &&
                scene.items.indexOf(other) > scene.items.indexOf(item) && other.visible.any { it.intersection(item.bounds) != null } }) continue
            val z = item.bounds.width / 28f
            for ((index, color) in listOf(CounterColor.RED, CounterColor.BLUE).withIndex()) {
                val slot = layout.counters[index]
                val x0 = (item.bounds.x + slot.x * z).toInt().coerceAtLeast(0)
                val x1 = (item.bounds.x + slot.right * z).toInt().coerceAtMost(image.width - 1)
                val y0 = (item.bounds.y + slot.y * z).toInt().coerceAtLeast(0)
                val y1 = (item.bounds.y + slot.bottom * z).toInt().coerceAtMost(image.height - 1)
                val expected = counterColor(color).toArgb()
                assertTrue((x0..x1).any { x -> (y0..y1).any { y -> image.getRGB(x, y) == expected } },
                    "Missing $color counter on ${item.id}, text $textScale, zoom ${scene.camera.zoom}")
            }
            checked += item.viewport
        }
        assertTrue(checked.isNotEmpty(), "No unobstructed cards checked")
    }
}

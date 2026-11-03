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
    private fun screenshots(landscape: Boolean) {
        val f = TableStatesFixture
        val play = f.combat()
        val preferences = CardAppearancePreferences(context)
        val toolkit = TableToolkitController(f.a, { play }, { true }, { _, _ -> true })
        compose.setContent { MaterialTheme { TableSurface(f.game, play, f.a, landscape, Modifier.fillMaxSize(), toolkit = toolkit) } }
        for (scale in listOf(.5f, 3f)) for (zoom in listOf(.65f, 1f, 2f)) {
            compose.runOnIdle {
                preferences.setSize(scale); preferences.setText(scale)
                toolkit.layoutState.value = TableLayoutState(camera = TableCamera(zoom = zoom))
            }
            compose.waitForIdle()
            compose.mainClock.advanceTimeBy(1000)
            compose.waitForIdle()
            val name = "card-sizes"
            val file = File("$SCREENSHOTS/$name-${if (landscape) "landscape" else "portrait"}-$scale-$zoom.png")
            file.parentFile!!.mkdirs()
            val situation = "${if (landscape) "Landscape" else "Portrait"} table in combat; card size ${scale}x, text ${scale}x, zoom ${zoom}x"
            captureShot(file.path, "$situation.")
            if (scale == 3f && zoom == 1f) {
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
                    captureShot("${file.path.removeSuffix(".png")}-${if (field.owner == f.a) "own" else "opponent"}-panned.png",
                        "$situation, panned to ${if (field.owner == f.a) "your" else "the opponent's"} battlefield.")
                }
            }
        }
    }

    @Test fun portraitSizeExtremes() { screenshots(false) }
    @Test @Config(qualifiers = "w892dp-h412dp-land-mdpi")
    fun landscapeSizeExtremes() { screenshots(true) }


}

package org.asyncttwiz.android

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureScreenRoboImage
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

    @Test fun typedValuesValidateLimitsAndLiveSampleTracksSliderAndCustomization() {
        val preferences = CardAppearancePreferences(context)
        compose.setContent { MaterialTheme { Column(Modifier.verticalScroll(rememberScrollState())) { CardSettings(preferences) } } }
        compose.onNodeWithTag("card-size-value").performClick()
        compose.onNodeWithTag("card-scale-input").performTextReplacement("2.01")
        compose.onNodeWithTag("card-scale-save").assertIsNotEnabled()
        compose.onNodeWithTag("card-scale-input").performTextReplacement("NaN")
        compose.onNodeWithTag("card-scale-save").assertIsNotEnabled()
        compose.onNodeWithTag("card-scale-input").performTextReplacement("0.75")
        compose.onNodeWithTag("card-scale-save").assertIsEnabled().performClick()
        compose.onNodeWithTag("card-size-sample").assertWidthIsEqualTo(21.dp).assertHeightIsEqualTo(30.dp)
        compose.onNodeWithTag("card-size").performTouchInput { swipeRight() }
        compose.runOnIdle { assertTrue(preferences.read().cardSize > .75f) }
        compose.onNodeWithText("Customize per area").performClick()
        compose.onNodeWithTag("card-size-HAND").assertExists()
        compose.onNodeWithTag("card-size-HAND-value").performScrollTo().performClick()
        compose.onNodeWithTag("card-scale-input").performTextReplacement("2")
        compose.onNodeWithTag("card-scale-save").performClick()
        compose.onNodeWithTag("card-size-HAND-sample").assertWidthIsEqualTo(56.dp).assertHeightIsEqualTo(80.dp)
        compose.runOnIdle { assertEquals(2f, preferences.read().size(CardArea.HAND)) }
    }

    @OptIn(ExperimentalRoborazziApi::class)
    private fun screenshots(landscape: Boolean) {
        val f = TableStatesFixture
        val initial = f.combat()
        val library = initial.board.containers.first { it.zone == PlayZone.LIBRARY && it.accountId == f.a }
        val play = initial.copy(board = initial.board.copy(contentsDisplays = listOf(
            ContentsDisplay("sample-panel", f.a, library.id, ContentsSelection.TOP, library.cards.size, false, library.cards)),
            cards = initial.board.cards.map { it.copy(note = "Flying", counters = mapOf(CounterColor.BLUE to "1")) }))
        val preferences = CardAppearancePreferences(context)
        compose.setContent { MaterialTheme { TableSurface(f.game, play, f.a, landscape, Modifier.fillMaxSize()) } }
        for (scale in listOf(.75f, 2f)) {
            compose.runOnIdle { preferences.setSize(scale); preferences.setText(scale) }
            compose.waitForIdle()
            compose.mainClock.advanceTimeBy(1000)
            compose.waitForIdle()
            val file = File("build/outputs/roborazzi/card-sizes-${if (landscape) "landscape" else "portrait"}-$scale.png")
            file.parentFile!!.mkdirs()
            captureScreenRoboImage(file.path)
        }
    }

    @Test fun portraitSizeExtremes() { screenshots(false) }
    @Test @Config(qualifiers = "w892dp-h412dp-land-mdpi")
    fun landscapeSizeExtremes() { screenshots(true) }
}

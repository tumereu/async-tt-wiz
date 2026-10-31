package org.asyncttwiz.android

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import org.asyncttwiz.protocol.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The pod settings form's mode button and its popup list of modes. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w412dp-h892dp-port-mdpi")
class PodModeScreenshotTest {
    @get:Rule val compose = createComposeRule()

    // DRF-020
    @Test fun portrait() = capture(false)
    @Test @Config(qualifiers = "w892dp-h412dp-land-mdpi") fun landscape() = capture(true)

    private fun capture(landscape: Boolean) {
        compose.setContent { ScreenTheme { NoRipples { Surface(Modifier.fillMaxSize(), color = ScreenBackground) {
            Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                PodSettingsForm("Cube night", DraftSettings.winston(), PodSource("Runes", 540), 2, true, {}, {}, {})
            }
        } } } }
        compose.onNodeWithTag("pod-mode").performClick()
        compose.onNodeWithText("Winston · 2–4 players").assertIsDisplayed()
        captureShot("$SCREENSHOTS/non-gameplay/pod-mode-${if (landscape) "landscape" else "portrait"}.png",
            "${if (landscape) "Landscape" else "Portrait"} New pod settings for a Winston pod from the Runes cube with the full-width " +
                "mode button tapped: its popup lists the seven modes with their player ranges, Winston checked.")
    }
}

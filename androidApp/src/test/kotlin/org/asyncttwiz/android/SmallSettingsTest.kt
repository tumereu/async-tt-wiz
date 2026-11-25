package org.asyncttwiz.android

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.*

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w412dp-h892dp-port-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SmallSettingsTest {
    @get:Rule val compose = createComposeRule()

    // LOB-083
    @Test fun versionTextIncludesBuildAndDirtyCommitVerbatim() {
        assertEquals("Version 1.2.3 (45) debug · commit abcdef123456-dirty",
            appVersionText("1.2.3", 45, "debug", "abcdef123456-dirty"))
        assertEquals("Version 1.2.3 (45) release · commit abcdef123456",
            appVersionText("1.2.3", 45, "release", "abcdef123456"))
        assertTrue(appVersionText().contains(BuildConfig.GIT_COMMIT))
    }

    // LOB-083
    @Test fun settingsDisplaysVersionAtBottomAndDebugSpeedControl() {
        compose.setContent { MaterialTheme { SettingsContent(null, logout = {}) } }
        compose.onNodeWithTag("card-move-speed").assertExists().performTouchInput { swipeRight() }
        compose.onNodeWithTag("app-version").performScrollTo().assertTextEquals(appVersionText())
    }

    // LOB-275: About & account credits 17Lands for the draft bots' card ratings, above the version line
    @Test fun aboutCreditsTheCardRatings() {
        compose.setContent { MaterialTheme { SettingsContent(null, logout = {}) } }
        val credit = compose.onNodeWithTag("card-ratings-credit").performScrollTo()
        credit.assertTextEquals("Draft bots' card ratings: 17Lands public data (17lands.com), CC BY 4.0")
        assertTrue(credit.getUnclippedBoundsInRoot().top < compose.onNodeWithTag("app-version").getUnclippedBoundsInRoot().top)
    }
}

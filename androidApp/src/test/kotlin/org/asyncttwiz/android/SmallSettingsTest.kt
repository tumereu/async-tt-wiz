package org.asyncttwiz.android

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
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

    @Test fun versionTextIncludesBuildAndDirtyCommitVerbatim() {
        assertEquals("Version 1.2.3 (45) debug · commit abcdef123456-dirty",
            appVersionText("1.2.3", 45, "debug", "abcdef123456-dirty"))
        assertEquals("Version 1.2.3 (45) release · commit abcdef123456",
            appVersionText("1.2.3", 45, "release", "abcdef123456"))
        assertTrue(appVersionText().contains(BuildConfig.GIT_COMMIT))
    }

    @Test fun settingsDisplaysVersionAtBottomAndDebugSpeedControl() {
        compose.setContent { MaterialTheme { SettingsContent(null, logout = {}) } }
        compose.onNodeWithTag("card-move-speed").assertExists().performTouchInput { swipeRight() }
        compose.onNodeWithTag("app-version").performScrollTo().assertTextEquals(appVersionText())
    }
}

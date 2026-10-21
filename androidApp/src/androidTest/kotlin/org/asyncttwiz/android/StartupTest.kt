package org.asyncttwiz.android

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import org.junit.Rule
import org.junit.Test

/** Uses the normal launcher activity, not a test-only screen or alternate APK. */
class StartupTest {
    @get:Rule
    val activity = createAndroidComposeRule<MainActivity>()

    @Test
    fun normalAppShowsItsTitle() {
        activity.onNodeWithText("Async Tabletop Wizards").assertIsDisplayed()
    }
}

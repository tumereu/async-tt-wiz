package org.asyncttwiz.android

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import org.junit.Rule
import org.junit.Test
import androidx.lifecycle.ViewModelProvider
import org.junit.Assert.assertSame
import androidx.compose.ui.test.onNodeWithTag

/** Uses the normal launcher activity, not a test-only screen or alternate APK. */
class StartupTest {
    @get:Rule
    val activity = createAndroidComposeRule<MainActivity>()

    @Test
    fun normalAppShowsItsTitle() {
        activity.onNodeWithText("Async Tabletop Wizards").assertIsDisplayed()
        lateinit var owner: StorageViewModel
        lateinit var auth: AuthViewModel
        activity.runOnUiThread {
            owner = ViewModelProvider(activity.activity)[StorageViewModel::class.java]
            auth = ViewModelProvider(activity.activity)[AuthViewModel::class.java]
        }
        activity.waitUntil(timeoutMillis = 15_000) { owner.state == StorageState.Ready }
        activity.waitUntil(timeoutMillis = 15_000) {
            auth.controller.state.restoring.not()
        }
        activity.onNodeWithTag("email").assertIsDisplayed()
    }

    @Test
    fun normalLauncherRetainsTheStorageOwnerAcrossRecreation() {
        lateinit var owner: StorageViewModel
        activity.runOnUiThread { owner = ViewModelProvider(activity.activity)[StorageViewModel::class.java] }
        activity.waitUntil(timeoutMillis = 15_000) { owner.state == StorageState.Ready }
        activity.activityRule.scenario.recreate()
        activity.runOnUiThread {
            assertSame(owner, ViewModelProvider(activity.activity)[StorageViewModel::class.java])
        }
        activity.onNodeWithText("Async Tabletop Wizards").assertIsDisplayed()
    }
}

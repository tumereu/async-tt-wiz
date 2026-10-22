package org.asyncttwiz.android

import android.content.pm.ActivityInfo
import android.content.res.Configuration
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.ViewModelProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.util.UUID

/** Affected immersive/rotation/Back checks on the normal launcher and real synthetic account. */
class ShellPresentationTest {
    @get:Rule val activity = createAndroidComposeRule<MainActivity>()

    @Test fun rotationAndKeyboardReturnRestoreFullscreenWithReachableNavigation() = runBlocking {
        val email = "presentation-${UUID.randomUUID().toString().take(8)}@example.test"
        AccountApi(BuildConfig.ACCOUNT_ORIGIN).use { it.register("Presentation ${UUID.randomUUID().toString().take(8)}", email, "wizard password") }
        lateinit var controller: AuthController
        activity.runOnUiThread { controller = ViewModelProvider(activity.activity)[AuthViewModel::class.java].controller }
        activity.waitUntil(15_000) { !controller.state.restoring }
        activity.runOnUiThread { controller.edit(email = email, password = "wizard password"); controller.submit() }
        activity.waitUntil(15_000) { controller.state.account != null }
        for ((orientation, expected) in listOf(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE to Configuration.ORIENTATION_LANDSCAPE,
            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT to Configuration.ORIENTATION_PORTRAIT)) {
            activity.runOnUiThread { activity.activity.requestedOrientation = orientation }
            activity.waitUntil(15_000) { activity.activity.resources.configuration.orientation == expected }
            var last = ""
            activity.waitUntil(10_000) {
                var hidden = false
                activity.runOnUiThread {
                    val decor = activity.activity.window.decorView
                    hidden = ViewCompat.getRootWindowInsets(decor)?.let {
                        val observed = "orientation=$expected status=${it.isVisible(WindowInsetsCompat.Type.statusBars())} nav=${it.isVisible(WindowInsetsCompat.Type.navigationBars())} ime=${it.isVisible(WindowInsetsCompat.Type.ime())} focus=${decor.hasWindowFocus()} size=${decor.width}x${decor.height} flags=${decor.systemUiVisibility} windowFlags=${activity.activity.window.attributes.flags}"
                        if (observed != last) { println(observed); last = observed }
                        !it.isVisible(WindowInsetsCompat.Type.statusBars()) && !it.isVisible(WindowInsetsCompat.Type.navigationBars())
                    } == true
                }
                hidden
            }
            if (expected == Configuration.ORIENTATION_PORTRAIT) {
                activity.onNodeWithContentDescription("Open navigation").performClick()
                activity.onNodeWithText("Logout").assertIsDisplayed()
                activity.runOnUiThread { activity.activity.onBackPressedDispatcher.onBackPressed() }
                activity.onNodeWithText("ATW").assertIsDisplayed()
                activity.onNodeWithText("Logout").assertDoesNotExist()
            } else activity.onNodeWithText("Logout").assertIsDisplayed()
            println("PASS actual normal-app orientation=$expected status/navigation bars hidden; navigation reachable")
        }
        activity.runOnUiThread { controller.logout() }
        activity.waitUntil(15_000) { !controller.state.busy }
        assertFalse(controller.state.logoutFailed)
        activity.onNodeWithTag("password").performScrollTo().performClick()
        activity.waitUntil(10_000) {
            var shown = false
            activity.runOnUiThread { shown = ViewCompat.getRootWindowInsets(activity.activity.window.decorView)
                ?.isVisible(WindowInsetsCompat.Type.ime()) == true }
            shown
        }
        activity.onNodeWithText("Forgot password?").performScrollTo().assertIsDisplayed()
        activity.runOnUiThread {
            androidx.core.view.WindowInsetsControllerCompat(activity.activity.window, activity.activity.window.decorView)
                .hide(WindowInsetsCompat.Type.ime())
        }
        activity.waitUntil(10_000) {
            var hidden = false
            activity.runOnUiThread { hidden = ViewCompat.getRootWindowInsets(activity.activity.window.decorView)?.let {
                !it.isVisible(WindowInsetsCompat.Type.statusBars()) && !it.isVisible(WindowInsetsCompat.Type.navigationBars()) &&
                    !it.isVisible(WindowInsetsCompat.Type.ime())
            } == true }
            hidden
        }
        println("PASS native keyboard opens, scrolled account action remains reachable and keyboard return restores fullscreen")
    }
}

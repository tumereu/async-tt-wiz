package org.asyncttwiz.android

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Parcel
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Opt-in checkpoint with an actual observed synthetic email link, never a fabricated reset token. */
class ResetIntentTest {
    @get:Rule val activity = createAndroidComposeRule<MainActivity>()

    @Test fun warmIntentCallbackExactInputAndSavedStateKeepResetVolatile() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val link = requireNotNull(InstrumentationRegistry.getArguments().getString("resetLink"))
        val token = requireNotNull(ResetLinks.token(link, BuildConfig.RESET_ORIGIN))
        lateinit var reset: ResetController
        lateinit var scenarioIntent: Intent
        activity.runOnUiThread {
            reset = ViewModelProvider(activity.activity)[AuthViewModel::class.java].reset
            scenarioIntent = Intent(activity.activity.intent)
            assertNull(scenarioIntent.data)
        }
        fun open() {
            // Actual manifest/task routing and rotation are exercised by Appium. Sending NEW_TASK
            // through Context here interferes with ActivityScenario's tracked singleTask lifecycle.
            activity.runOnUiThread { instrumentation.callActivityOnNewIntent(activity.activity, Intent(Intent.ACTION_VIEW, Uri.parse(link))) }
            activity.waitUntil(15_000) { reset.state?.let { it.page == ResetPage.PASSWORD && !it.checking } == true }
        }
        open()
        val exact = " \tnew Cafe\u0301 wizard 🧙\n "
        activity.onNodeWithTag("password").performTextReplacement(exact)
        open() // singleTask warm delivery does not clear an unfinished password or dispatch confirmation.
        activity.runOnUiThread { assertEquals(exact, reset.state!!.password); assertNull(activity.activity.intent.data) }
        val bundle = Bundle()
        activity.runOnUiThread { instrumentation.callActivityOnSaveInstanceState(activity.activity, bundle) }
        val parcel = Parcel.obtain()
        try {
            parcel.writeBundle(bundle)
            val bytes = parcel.marshall()
            for (encoding in listOf(Charsets.UTF_8, Charsets.UTF_16LE)) {
                assertFalse("Reset password must not enter saved instance state", bytes.toString(encoding).contains(exact))
                assertFalse("Reset token must not enter saved instance state", bytes.toString(encoding).contains(token))
            }
        } finally { parcel.recycle() }
        // ActivityScenario filters lifecycle events by equality with its original launch
        // intent (including categories). The app deliberately replaces VIEW with a clean
        // MAIN intent. Restore only Scenario's original *non-secret* launcher identity
        // after the secrecy assertions so its ordinary finish/destroy checks still run.
        activity.runOnUiThread { reset.dismiss(); activity.activity.intent = scenarioIntent }
    }
}

package org.asyncttwiz.android

import android.os.Bundle
import android.os.Parcel
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTextReplacement
import androidx.lifecycle.ViewModelProvider
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class AuthSavedStateTest {
    @get:Rule val activity = createAndroidComposeRule<MainActivity>()

    @Test fun nativeFieldDoesNotPutThePasswordInAndroidSavedState() {
        lateinit var controller: AuthController
        activity.runOnUiThread { controller = ViewModelProvider(activity.activity)[AuthViewModel::class.java].controller }
        activity.waitUntil(timeoutMillis = 15_000) { !controller.state.restoring }
        val password = " Never-save-🧙-Cafe\u0301 \n "
        activity.onNodeWithTag("password").performTextReplacement(password)
        val bundle = Bundle()
        activity.runOnUiThread {
            assertEquals(password, controller.state.password)
            InstrumentationRegistry.getInstrumentation().callActivityOnSaveInstanceState(activity.activity, bundle)
        }
        val parcel = Parcel.obtain()
        try {
            parcel.writeBundle(bundle)
            val bytes = parcel.marshall()
            for (encoding in listOf(Charsets.UTF_8, Charsets.UTF_16LE)) {
                assertFalse("Password must not enter an Android saved-state parcel", bytes.toString(encoding).contains(password))
            }
        } finally { parcel.recycle() }
        // A manually saved lifecycle can dispose the Compose hierarchy. Clear the
        // volatile controller directly; do not issue another UI action after save.
        activity.runOnUiThread { controller.edit(password = "") }
    }
}

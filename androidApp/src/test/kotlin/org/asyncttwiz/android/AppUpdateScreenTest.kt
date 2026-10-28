package org.asyncttwiz.android

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w412dp-h892dp-port-mdpi")
class AppUpdateScreenTest {
    @get:Rule val compose = createComposeRule()
    private val application get() = RuntimeEnvironment.getApplication()
    private val preferences get() = application.getSharedPreferences("app-update", 0)
    private val store = ViewModelStore()
    private fun model() = ViewModelProvider(store,
        ViewModelProvider.AndroidViewModelFactory(application))[AppUpdateViewModel::class.java]

    @Before fun reset() { preferences.edit().clear().commit() }
    @After fun close() { store.clear(); preferences.edit().clear().commit() }

    @Test fun `cached update blocks main UI immediately without a network check`() {
        preferences.edit().putString("latest", """{"versionCode":${BuildConfig.VERSION_CODE + 1},"versionName":"1.2.3","sha256":"${"a".repeat(64)}","size":100}""").commit()
        val updates = model()
        assertTrue(updates.required)
        assertFalse(updates.initialCheck)
        compose.setContent { MaterialTheme {
            if (updates.required) AppUpdateScreen(updates, install = { error("No user update request") })
            else Text("Main UI")
        } }
        compose.onNodeWithText("Update required").assertIsDisplayed()
        compose.onNodeWithText("1.2.3").assertIsDisplayed()
        compose.onNodeWithText("Update").assertIsEnabled()
        compose.onNodeWithText("Main UI").assertDoesNotExist()
    }

    @Test fun `rules incompatibility survives recreation but not an APK version change`() {
        model().requireForRules()
        store.clear()
        assertTrue(model().required)
        store.clear()
        preferences.edit().putInt("incompatible-version", BuildConfig.VERSION_CODE - 1).commit()
        assertFalse(model().required)
    }

    @Test fun `a failed update stays explained and offers the browser download`() {
        preferences.edit().putString("latest", """{"versionCode":${BuildConfig.VERSION_CODE + 1},"versionName":"1.2.3","sha256":"${"a".repeat(64)}","size":100}""").commit()
        val updates = model()
        compose.setContent { MaterialTheme { AppUpdateScreen(updates, install = { error("No user update request") }) } }
        compose.onNodeWithText("Download in browser").assertDoesNotExist()
        updates.installerReturned()
        compose.onNodeWithText("The update wasn't installed. Tap Update to try again.").assertIsDisplayed()
        compose.onNodeWithText("Download in browser").assertIsDisplayed()
    }
}

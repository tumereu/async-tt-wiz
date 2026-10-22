package org.asyncttwiz.android

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.util.UUID

/** Exact input sequences and requests against the real configured synthetic backend. */
class AccountEntryTest {
    @get:Rule val activity = createAndroidComposeRule<MainActivity>()

    @Test fun exactWhitespaceUnicodePasswordSurvivesNativeInputRotationAndRealRequests() = runBlocking {
        assertTrue("Configure the normal debug API origin for this opt-in device test", BuildConfig.ACCOUNT_ORIGIN.startsWith("http://127.0.0.1:"))
        lateinit var controller: AuthController
        activity.runOnUiThread { controller = ViewModelProvider(activity.activity)[AuthViewModel::class.java].controller }
        activity.waitUntil(timeoutMillis = 15_000) { !controller.state.restoring }
        val secret = " \tCafe\u0301 wizard 🧙\n "
        activity.onNodeWithTag("password").performTextReplacement(secret)
        activity.runOnUiThread { assertEquals(secret, controller.state.password) }
        activity.activityRule.scenario.recreate()
        activity.runOnUiThread {
            assertSame(controller, ViewModelProvider(activity.activity)[AuthViewModel::class.java].controller)
            assertEquals(secret, controller.state.password)
        }
        // The actual Ktor Android engine/server, not a stub, verifies the same bytes.
        AccountApi(BuildConfig.ACCOUNT_ORIGIN).use { api ->
            val id = UUID.randomUUID().toString().take(8)
            val email = "unicode-$id@example.test"
            val registered = api.register("Unicode $id", email, secret)
            for (wrong in listOf(secret.trim(), secret.replace("e\u0301", "é"))) {
                try { api.login(email, wrong); fail("Normalized secret authenticated") }
                catch (failure: AccountFailure) { assertEquals("invalid_credentials", failure.code) }
            }
            val session = api.login(email, controller.state.password)
            assertEquals(registered, api.identity(session.token))
            api.revoke(session.token)
            try { api.identity(session.token); fail("Revoked credential authenticated") }
            catch (failure: AccountFailure) { assertEquals("authentication_required", failure.code) }
        }
        activity.onNodeWithTag("password").performTextReplacement("")
    }
}

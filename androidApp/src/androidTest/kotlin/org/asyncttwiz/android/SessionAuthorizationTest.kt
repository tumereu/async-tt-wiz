package org.asyncttwiz.android

import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.File

/** Read-only synthetic E2E observer. No login injection, revocation, reset or product test endpoint. */
class SessionAuthorizationTest {
    @Test fun observesOrdinaryAuthorizationOfTheUiCreatedSession() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val arguments = InstrumentationRegistry.getArguments()
        // Instrumentation runs as the target UID; it cannot write the companion APK's private directory.
        // This isolated synthetic observer fixture is never read or written by product code.
        val saved = File(instrumentation.targetContext.noBackupFilesDir, "e2e-observed-synthetic-session")
        val token = if (arguments.getString("captureSession") == "true") {
            val session = ProtectedSessionStore(instrumentation.targetContext, BuildConfig.ACCOUNT_ORIGIN).load()
            assertTrue("UI must have created a real protected session", session is StoredSession.Present)
            (session as StoredSession.Present).token.also { saved.writeText(it) }
        } else saved.readText()
        val expected = arguments.getString("expectedAuthorization") != "false"
        AccountApi(BuildConfig.ACCOUNT_ORIGIN).use { api ->
            try {
                api.identity(token)
                assertTrue("Old session must reject a new request after successful reset", expected)
            } catch (failure: AccountFailure) {
                assertEquals("authentication_required", failure.code)
                assertFalse("Requesting/opening/invalid link must not revoke the session", expected)
            }
        }
        if (!expected) assertTrue(saved.delete())
    }
}

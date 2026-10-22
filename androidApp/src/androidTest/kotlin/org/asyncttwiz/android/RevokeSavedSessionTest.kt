package org.asyncttwiz.android

import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test

/** Explicit opt-in checkpoint in the retained normal-UI journey, never production code. */
class RevokeSavedSessionTest {
    @Test fun revokeOnlyThisSyntheticDevicesActualProtectedToken() = runBlocking {
        assertTrue(BuildConfig.ACCOUNT_ORIGIN.startsWith("http://127.0.0.1:"))
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val saved = ProtectedSessionStore(context, BuildConfig.ACCOUNT_ORIGIN).load()
        if (InstrumentationRegistry.getArguments().getString("requireSavedSession") == "true")
            assertTrue("The retained UI checkpoint must have a real saved session", saved is StoredSession.Present)
        AccountApi(BuildConfig.ACCOUNT_ORIGIN).use {
            // Outside the retained checkpoint, a fresh-suite invocation checks the
            // public idempotent unknown-token path, without inventing a login.
            it.revoke(if (saved is StoredSession.Present) saved.token else "A".repeat(43))
        }
        // Leave ciphertext intact: normal app restart must discover server revocation.
    }
}

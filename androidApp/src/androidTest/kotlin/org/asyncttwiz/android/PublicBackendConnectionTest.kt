package org.asyncttwiz.android

import androidx.test.platform.app.InstrumentationRegistry
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Assume.assumeTrue
import org.junit.Test

/** Opt-in deployment check using the installed APK's real TLS policy and Android HTTP engine. */
class PublicBackendConnectionTest {
    @Test fun configuredPublicBackendReturnsAnAccountResponse() = runBlocking {
        assumeTrue(InstrumentationRegistry.getArguments().getString("atwPublicConnection") == "true")
        assertTrue("Build with the public HTTPS origin", BuildConfig.ACCOUNT_ORIGIN.startsWith("https://"))
        AccountApi(BuildConfig.ACCOUNT_ORIGIN).use { api ->
            // No registration or persistent data: a unique nonexistent account must
            // produce the backend's credentials error, not a DNS/TLS/network failure.
            try {
                api.login("deployment-${UUID.randomUUID()}@example.invalid", "connection-check-only")
                fail("A nonexistent account authenticated")
            } catch (failure: AccountFailure) {
                assertEquals("invalid_credentials", failure.code)
            }
        }
    }
}

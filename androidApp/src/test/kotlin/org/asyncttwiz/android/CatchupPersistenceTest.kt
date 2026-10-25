package org.asyncttwiz.android

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.*

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class CatchupPersistenceTest {
    @Test fun debugBookmarksSurviveRealLogoutAndAccountActivationWithoutCrossingAccountOrGameScopes() {
        assertTrue(BuildConfig.DEBUG)
        val context = ApplicationProvider.getApplicationContext<Context>()
        val worker = Executors.newSingleThreadExecutor()
        val local = AndroidAccountLocalData(context)
        val store = object : SessionStore {
            var token: String? = "session"
            override fun load() = token?.let { StoredSession.Present(it) } ?: StoredSession.Empty
            override fun save(token: String) { this.token = token }
            override fun clear() { token = null }
        }
        try {
            worker.submit { local.activate("alice") }.get(5, TimeUnit.SECONDS)
            HistoryPlaybackPreferences(context, "alice").shown("game", "seen-a")
            worker.submit { local.logout(store); local.activate("bob") }.get(5, TimeUnit.SECONDS)
            assertEquals(StoredSession.Empty, store.load())
            val bob = HistoryPlaybackPreferences(context, "bob")
            assertNull(bob.lastSeen("game"))
            bob.shown("game", "seen-b")
            worker.submit { local.activate("alice") }.get(5, TimeUnit.SECONDS)
            val aliceAgain = HistoryPlaybackPreferences(context, "alice")
            assertEquals("seen-a", aliceAgain.lastSeen("game"))
            assertNull(aliceAgain.lastSeen("other-game"))
            assertEquals("seen-b", HistoryPlaybackPreferences(context, "bob").lastSeen("game"))
        } finally {
            worker.submit { local.close() }.get(5, TimeUnit.SECONDS)
            worker.shutdown()
        }
    }
}

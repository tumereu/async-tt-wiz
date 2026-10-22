package org.asyncttwiz.android

import android.app.Application
import android.content.Context
import androidx.test.platform.app.InstrumentationRegistry
import org.asyncttwiz.storage.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.UUID

class AccountLocalDataTest {
    private fun isolated(block: (Context, String) -> Unit) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val id = UUID.randomUUID().toString()
        val directory = File(context.noBackupFilesDir, "profile-test-$id").apply { check(mkdirs()) }
        val application = object : Application() {
            override fun getApplicationContext(): Context = this
            override fun getNoBackupFilesDir() = directory
        }
        try { block(application, "profile-test-$id") }
        finally { directory.walkBottomUp().forEach { check(it.delete()) } }
    }

    private fun <T> storage(context: Context, block: (ClientStorage) -> T): T =
        (AndroidClientStorage.open(AndroidClientStorage.databaseFile(context)) as StorageOpenResult.Opened).storage.use(block)

    @Test fun actualProfileContextAndPrivateReplicaAreRetainedOnlyForSameAccount() = isolated { context, alias ->
        val session = ProtectedSessionStore(context, "https://synthetic.example", alias)
        try {
            session.save("A".repeat(43))
            val local = AndroidAccountLocalData(context)
            assertEquals(ShellDestination.MENU, local.activate("a"))
            local.destination("a", ShellDestination.GAMES)
            storage(context) {
                it.commitAccepted("private-game", null, 1, listOf(AcceptedRecord(RecordKey("game", "a"), StoredPayload(1, "{}"))))
                it.savePersonal("private-draft", "text", StoredPayload(1, "\"unfinished\""))
                it.cachePublic("catalog", StoredPayload(1, "{}"))
            }
            assertEquals(ShellDestination.GAMES, AndroidAccountLocalData(context).activate("a"))
            storage(context) { assertNotNull(it.accepted("private-game")) }
            assertEquals(ShellDestination.MENU, local.activate("b"))
            storage(context) {
                assertNull(it.accepted("private-game")); assertNull(it.personal("private-draft", "text"))
                assertNotNull(it.publicCache("catalog"))
            }
            local.destination("b", ShellDestination.CONTACTS)
            local.logout(session)
            assertEquals(StoredSession.Empty, session.load())
            assertFalse(File(context.noBackupFilesDir, "auth/session.bin").exists())
            storage(context) { assertNull(it.personal("installation", "account-owner")) }
            assertEquals(ShellDestination.MENU, AndroidAccountLocalData(context).activate("b"))
        } finally { session.clear() }
    }

    @Test fun interruptedLocalLogoutFinishesBeforeStartupCanRestoreCredentials() = isolated { context, alias ->
        val real = ProtectedSessionStore(context, "https://synthetic.example", alias)
        var failClear = true
        val session = object : SessionStore by real {
            override fun clear() { if (failClear) error("Synthetic local I/O refusal"); real.clear() }
        }
        try {
            session.save("B".repeat(43))
            val local = AndroidAccountLocalData(context)
            local.activate("a"); local.destination("a", ShellDestination.DECKS)
            try { local.logout(session); fail("Expected local cleanup refusal") } catch (_: IllegalStateException) { }
            assertTrue(File(context.noBackupFilesDir, "account/logout.pending").isFile)
            failClear = false
            AndroidAccountLocalData(context).resumeLogout(session)
            assertEquals(StoredSession.Empty, real.load())
            assertFalse(File(context.noBackupFilesDir, "account/logout.pending").exists())
            assertEquals(ShellDestination.MENU, local.activate("a"))
        } finally { real.clear() }
    }
}

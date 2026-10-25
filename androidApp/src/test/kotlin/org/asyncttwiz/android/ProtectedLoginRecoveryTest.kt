package org.asyncttwiz.android

import android.content.Context
import android.security.keystore.KeyPermanentlyInvalidatedException
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import org.asyncttwiz.protocol.AccountIdentity
import org.asyncttwiz.protocol.SessionCreated
import org.asyncttwiz.storage.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.security.KeyStoreException
import java.security.UnrecoverableKeyException
import java.util.concurrent.AbstractExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import kotlin.test.*

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w412dp-h892dp-port-mdpi")
class ProtectedLoginRecoveryTest {
    @get:Rule val compose = createComposeRule()
    @get:Rule val files = TemporaryFolder()
    private val alias = "test.session"
    private val token = "A".repeat(43)
    private val identity = AccountIdentity("alice", "Alice")
    private val rememberedAccount get() = RememberedSession(token, RememberedAccount(identity, "alice@example.test"))
    private val file get() = File(files.root, "auth/session.bin")
    private val keys = FakeKeys()
    private fun store(origin: String = "https://accounts.example") = ProtectedSessionStore(origin, alias, file, keys)

    @Test fun missingKeyOffersSignInInsteadOfEndlessRetryAndClearsRealPrivateData() {
        val store = store()
        store.save(token)
        store.remember(listOf(rememberedAccount))
        keys.values.clear() // Simulate deletion of both app aliases by the OS or a same-uid test.
        assertEquals(StoredSession.Unrecoverable, store.load())
        val bytes = file.readBytes()
        val context = ApplicationProvider.getApplicationContext<Context>()
        val local = AndroidAccountLocalData(context)
        val worker = Executors.newSingleThreadExecutor()
        val database = AndroidClientStorage.databaseFile(context)
        fun <T> storage(action: (ClientStorage) -> T): T =
            (AndroidClientStorage.open(database) as StorageOpenResult.Opened).storage.use(action)
        worker.submit {
            local.activate(identity.accountId)
            local.destination(identity.accountId, ShellDestination.CONTACTS)
            storage {
                it.savePersonal("private-draft", "text", StoredPayload(1, "\"secret\""))
                it.commitAccepted("private-game", null, 1,
                    listOf(AcceptedRecord(RecordKey("game", "a"), StoredPayload(1, "{}"))))
                it.cachePublic("catalog", StoredPayload(1, "{}"))
            }
        }.get(5, TimeUnit.SECONDS)
        val auth = AuthController(Api(), store, worker, local, accountSwitcher = true, post = { it() })
        val resetWorker = Worker()
        val reset = ResetController(AccountApi(""), "", resetWorker, {}) { it() }
        try {
            worker.submit {}.get(5, TimeUnit.SECONDS)
            assertTrue(auth.state.savedLoginUnrecoverable)
            assertContentEquals(bytes, file.readBytes()) // Nothing deleted just by detecting key loss.
            compose.setContent { AccountEntry(auth, reset) }
            compose.onNodeWithText("Your saved sign-in could not be restored. Please sign in again.").assertIsDisplayed()
            compose.onNodeWithText("Retry").assertDoesNotExist()
            compose.onNodeWithText("Sign in").performClick()
            worker.submit {}.get(5, TimeUnit.SECONDS)
            compose.onNodeWithTag("email").assertIsDisplayed()
            compose.onNodeWithTag("password").assertIsDisplayed()
            assertEquals(StoredSession.Empty, store.load())
            assertFalse(file.exists())
            assertFalse(File(file.parentFile, "remembered.bin").exists())
            assertEquals(listOf(alias, "$alias.remembered"), keys.deleted)
            assertFalse(auth.state.logoutFailed)
            worker.submit {
                storage {
                    assertNull(it.personal("installation", "account-owner"))
                    assertNull(it.personal("private-draft", "text"))
                    assertNull(it.accepted("private-game"))
                    assertNotNull(it.publicCache("catalog"))
                }
                assertEquals(ShellDestination.MENU, local.activate(identity.accountId))
            }.get(5, TimeUnit.SECONDS)
        } finally {
            reset.close(); resetWorker.drain()
            worker.submit { local.close() }.get(5, TimeUnit.SECONDS)
            auth.close(); worker.awaitTermination(5, TimeUnit.SECONDS)
        }
    }

    @Test fun transientKeystoreFailureKeepsRetryAndDoesNotDeleteAnyData() {
        val store = store()
        store.save(token)
        val bytes = file.readBytes()
        keys.failure = KeyStoreException("Keystore temporarily unavailable")
        val worker = Worker()
        var clears = 0
        val local = object : AccountLocalData {
            override fun logout(store: SessionStore) { clears++; store.clear() }
        }
        val auth = AuthController(Api(), store, worker, local, accountSwitcher = true, post = { it() })
        val resetWorker = Worker()
        val reset = ResetController(AccountApi(""), "", resetWorker, {}) { it() }
        try {
            worker.drain()
            assertEquals(StoredSession.Unavailable, store.load())
            assertFalse(auth.state.savedLoginUnrecoverable)
            compose.setContent { AccountEntry(auth, reset) }
            compose.onNodeWithText("Retry").assertIsDisplayed().performClick()
            compose.runOnIdle { worker.drain() }
            compose.onNodeWithText("Retry").assertIsDisplayed()
            compose.onNodeWithTag("email").assertDoesNotExist()
            assertEquals(0, clears)
            assertContentEquals(bytes, file.readBytes())
            assertTrue(keys.deleted.isEmpty())
            compose.runOnIdle { keys.failure = null }
            compose.onNodeWithText("Retry").performClick()
            compose.runOnIdle { worker.drain() }
            assertEquals(identity, auth.state.account)
            assertEquals(token, assertIs<StoredSession.Present>(store.load()).token)
            assertTrue(keys.deleted.isEmpty())
        } finally { auth.close(); worker.drain(); reset.close(); resetWorker.drain() }
    }

    @Test fun damagedCiphertextAndReplacementKeyRecoverWithoutWeakeningSubsequentLogin() {
        for (replacementKey in listOf(false, true)) {
            val store = store()
            store.save(token)
            store.remember(listOf(rememberedAccount))
            if (replacementKey) keys.values[alias] = keys.create(alias)
            else file.writeBytes(file.readBytes().also { it[it.lastIndex] = (it.last().toInt() xor 1).toByte() })
            assertEquals(StoredSession.Unrecoverable, store.load())
            val worker = Worker()
            val auth = AuthController(Api(), store, worker, accountSwitcher = true, post = { it() })
            try {
                worker.drain()
                assertTrue(auth.state.savedLoginUnrecoverable)
                auth.logout(); worker.drain()
                assertFalse(auth.state.restoring)
                assertFalse(auth.state.logoutFailed)
                assertEquals(StoredSession.Empty, store.load())
                assertEquals(token, store.remembered().single().token) // Healthy other envelope survives.
                auth.edit(email = "alice@example.test", password = "password")
                auth.submit(); worker.drain()
                assertEquals(identity, auth.state.account)
                assertEquals(token, assertIs<StoredSession.Present>(store.load()).token)
                assertFalse(file.readBytes().toString(Charsets.ISO_8859_1).contains(token))
            } finally { auth.close(); worker.drain() }
        }
    }

    @Test fun permanentlyBrokenRememberedStoreCannotTrapLogoutAndRemoteFailureIsIgnored() {
        val store = store()
        store.save(token)
        store.remember(listOf(rememberedAccount))
        keys.values.remove("$alias.remembered")
        val worker = Worker()
        var revoked: String? = null
        val auth = AuthController(Api(), store, worker, revoke = { revoked = it; error("offline") },
            accountSwitcher = true, post = { it() })
        try {
            worker.drain()
            assertTrue(auth.state.savedLoginUnrecoverable)
            auth.logout(); worker.drain()
            assertEquals(token, revoked)
            assertFalse(auth.state.logoutFailed)
            assertFalse(auth.state.restoring)
            assertEquals(StoredSession.Empty, store.load())
            assertTrue(store.remembered().isEmpty())
        } finally { auth.close(); worker.drain() }
    }

    @Test fun wrappedInvalidationAndUnrecoverableKeyArePermanentButUnknownProviderErrorsAreNot() {
        val store = store()
        for (failure in listOf(KeyPermanentlyInvalidatedException(), UnrecoverableKeyException("lost"))) {
            store.save(token)
            keys.failure = KeyStoreException("provider wrapper", failure)
            assertEquals(StoredSession.Unrecoverable, store.load())
            keys.failure = null
            store.resetUnrecoverable()
            assertFalse(keys.values.containsKey(alias))
            assertFalse(file.exists())
        }
        store.save(token)
        val bytes = file.readBytes()
        keys.failure = KeyStoreException("temporary")
        assertEquals(StoredSession.Unavailable, store.load())
        assertFailsWith<KeyStoreException> { store.clear() }
        assertContentEquals(bytes, file.readBytes())
    }

    private class FakeKeys : SessionKeys {
        val values = mutableMapOf<String, SecretKey>()
        val deleted = mutableListOf<String>()
        var failure: Exception? = null
        override fun get(alias: String): SecretKey? { failure?.let { throw it }; return values[alias] }
        override fun create(alias: String): SecretKey = KeyGenerator.getInstance("AES").apply { init(256) }
            .generateKey().also { values[alias] = it }
        override fun delete(alias: String) {
            failure?.let { throw it }
            deleted += alias; values.remove(alias)
        }
    }

    private inner class Api : Accounts {
        override suspend fun register(username: String, email: String, password: String) = identity
        override suspend fun login(email: String, password: String) = SessionCreated(token, identity)
        override suspend fun identity(token: String) = identity
        override suspend fun revoke(token: String) = Unit
        override fun close() = Unit
    }

    private class Worker : AbstractExecutorService() {
        private val jobs = ArrayDeque<Runnable>()
        private var stopped = false
        override fun execute(command: Runnable) { jobs.add(command) }
        fun drain() { while (jobs.isNotEmpty()) jobs.removeFirst().run() }
        override fun shutdown() { stopped = true }
        override fun shutdownNow(): MutableList<Runnable> = mutableListOf()
        override fun isShutdown() = stopped
        override fun isTerminated() = stopped && jobs.isEmpty()
        override fun awaitTermination(timeout: Long, unit: TimeUnit) = isTerminated
    }
}

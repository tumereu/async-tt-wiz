package org.asyncttwiz.android

import org.asyncttwiz.protocol.AccountIdentity
import org.asyncttwiz.protocol.SessionCreated
import org.junit.Test
import java.util.concurrent.AbstractExecutorService
import java.util.concurrent.TimeUnit
import kotlin.test.*

class AccountSwitcherTest {
    private val alex = RememberedAccount(AccountIdentity("a", "alex"), "alex@example.test")
    private val riley = RememberedAccount(AccountIdentity("b", "riley"), "riley@example.test")
    private val tokenA = "A".repeat(43)
    private val tokenB = "B".repeat(43)

    private class Worker : AbstractExecutorService() {
        private val jobs = ArrayDeque<Runnable>()
        private var stopped = false
        override fun execute(command: Runnable) { jobs.add(command) }
        fun drain() { while (jobs.isNotEmpty()) jobs.removeFirst().run() }
        override fun shutdown() { stopped = true }
        override fun shutdownNow() = mutableListOf<Runnable>()
        override fun isShutdown() = stopped
        override fun isTerminated() = stopped && jobs.isEmpty()
        override fun awaitTermination(timeout: Long, unit: TimeUnit) = isTerminated
    }

    private class Store : SessionStore {
        var current: String? = null
        var saved = emptyList<RememberedSession>()
        var rememberedReads = 0
        var rememberedWrites = 0
        var failRemember = false
        override fun load() = current?.let { StoredSession.Present(it) } ?: StoredSession.Empty
        override fun save(token: String) { current = token }
        override fun clear() { current = null }
        override fun remembered(): List<RememberedSession> { rememberedReads++; return saved }
        override fun remember(sessions: List<RememberedSession>) {
            if (failRemember) error("Protected storage unavailable")
            rememberedWrites++; saved = sessions
        }
    }

    private inner class Fixture(enabled: Boolean = true, delayedPosts: Boolean = false, privateWork: () -> Unit = {}) {
        val worker = Worker()
        val store = Store()
        val posts = ArrayDeque<() -> Unit>()
        val revoked = mutableListOf<String>()
        val rejected = mutableSetOf<String>()
        var offline = false
        var privateClears = 0
        var localClears = 0
        var privateValue: String? = null
        var owner: String? = null
        var destinationWrites = 0
        var failCleanup = false
        val accounts = object : Accounts {
            override suspend fun register(username: String, email: String, password: String) = error("unused")
            override suspend fun login(email: String, password: String): SessionCreated =
                if (email == alex.email) SessionCreated(tokenA, alex.identity) else SessionCreated(tokenB, riley.identity)
            override suspend fun identity(token: String): AccountIdentity {
                if (token in rejected) throw AccountFailure("authentication_required")
                if (offline) throw AccountFailure("network")
                return if (token == tokenA) alex.identity else riley.identity
            }
            override suspend fun revoke(token: String) { revoked += token }
            override fun close() = Unit
        }
        val auth = AuthController(accounts, store, worker, object : AccountLocalData {
            override fun logout(store: SessionStore) {
                if (failCleanup) error("Private storage unavailable")
                store.clear(); privateValue = null; owner = null; localClears++
            }
            override fun activate(accountId: String): ShellDestination {
                owner = accountId
                return ShellDestination.MENU
            }
            override fun destination(accountId: String, destination: ShellDestination) { destinationWrites++ }
        }, revoke = { revoked += it }, clearPrivateWork = { privateClears++; privateWork() }, accountSwitcher = enabled,
            post = { if (delayedPosts) posts.add(it) else it() })

        fun flush() { worker.drain(); while (posts.isNotEmpty()) posts.removeFirst()() }
        fun login(account: RememberedAccount) {
            auth.edit(email = account.email, password = "never stored password")
            auth.submit(); flush()
            assertEquals(account.identity, auth.state.account)
        }
        fun twoAccounts() {
            flush(); login(alex); auth.addAccount(); flush(); login(riley)
        }
    }

    @Test fun rememberAddSwitchAndLogoutKeepExactlyOneActiveAccount() {
        val f = Fixture(); f.flush(); f.login(alex)
        assertEquals(listOf(alex), f.auth.state.rememberedAccounts)
        f.privateValue = "Alex's private data"
        f.auth.addAccount()
        assertNull(f.auth.sessionToken()); assertNull(f.auth.state.account)
        f.flush()
        assertNull(f.store.current); assertNull(f.privateValue)
        assertEquals(1, f.localClears); assertEquals(1, f.privateClears)
        assertEquals(listOf(tokenA), f.store.saved.map { it.token }); assertTrue(f.revoked.isEmpty())
        f.login(riley)
        assertEquals(listOf(alex, riley), f.auth.state.rememberedAccounts)
        assertEquals(tokenB, f.auth.sessionToken())
        f.privateValue = "Riley's private data"
        f.auth.navigate(ShellDestination.DECKS) // This queued write belongs only to Riley.
        f.auth.switchAccount("a")
        assertNull(f.auth.sessionToken()); assertNull(f.auth.state.account)
        f.flush()
        assertEquals(alex.identity, f.auth.state.account); assertEquals(tokenA, f.auth.sessionToken())
        assertNull(f.privateValue); assertEquals("a", f.owner)
        assertEquals(0, f.destinationWrites); assertEquals(2, f.localClears)
        assertEquals(ShellDestination.MENU, f.auth.state.destination); assertTrue(f.auth.state.backStack.isEmpty())
        assertTrue(f.revoked.isEmpty())
        f.auth.switchAccount("a"); f.flush(); assertEquals(2, f.localClears) // Current row is a no-op.
        f.auth.logout(); f.flush()
        assertEquals(listOf(tokenA), f.revoked); assertEquals(listOf(riley), f.auth.state.rememberedAccounts)
        assertEquals(listOf(tokenB), f.store.saved.map { it.token }); assertNull(f.store.current)
        assertNull(f.auth.state.account); assertEquals(3, f.localClears)
    }

    @Test fun staleAuthAndPrivateCallbacksCannotReenterAfterSwitch() {
        val f = Fixture(delayedPosts = true); f.twoAccounts()
        f.auth.authenticationRequired(tokenB) // Old transport rejection is queued on the UI thread.
        f.auth.navigate(ShellDestination.GAMES)
        f.auth.switchAccount("a")
        f.flush()
        assertEquals(alex.identity, f.auth.state.account)
        assertEquals(tokenA, f.auth.sessionToken()); assertEquals(0, f.destinationWrites)
        assertEquals(2, f.store.saved.size); assertTrue(f.revoked.isEmpty())
    }

    @Test fun switchFencesRealQueuedPrivateLookupAndDiscardsDrafts() {
        val lookup = Worker(); val callbacks = ArrayDeque<() -> Unit>()
        val importer = DeckImportController({ text -> org.asyncttwiz.storage.DeckImportResult(text,
            emptyList(), emptyList(), null) }, lookup, { callbacks.add(it) })
        val f = Fixture(privateWork = importer::exit); f.twoAccounts()
        importer.open(); importer.edit("private deck draft"); importer.lookup(); lookup.drain()
        f.auth.switchAccount("a"); f.flush()
        while (callbacks.isNotEmpty()) callbacks.removeFirst()()
        assertEquals(alex.identity, f.auth.state.account)
        assertFalse(importer.state.active); assertEquals("", importer.state.text); assertNull(importer.state.result)
        assertEquals(2, f.localClears)
        assertTrue(f.revoked.isEmpty())
        importer.close(); f.auth.close(); lookup.drain(); f.worker.drain()
    }

    @Test fun invalidRememberedTokenIsDroppedAndEmailPrefilled() {
        val f = Fixture(); f.twoAccounts(); f.rejected += tokenA
        f.auth.switchAccount("a"); f.flush()
        assertNull(f.auth.state.account); assertNull(f.auth.sessionToken()); assertNull(f.store.current)
        assertEquals(AuthPage.LOGIN, f.auth.state.page); assertEquals(alex.email, f.auth.state.email)
        assertEquals(listOf(riley), f.auth.state.rememberedAccounts)
        assertTrue(f.auth.state.error); assertFalse(f.auth.state.restoring); assertTrue(f.revoked.isEmpty())
    }

    @Test fun activeTokenRejectionAlsoForgetsOnlyThatAccount() {
        val f = Fixture(); f.twoAccounts()
        f.auth.authenticationRequired(tokenB); f.flush()
        assertNull(f.auth.state.account); assertEquals(riley.email, f.auth.state.email)
        assertEquals(listOf(alex), f.auth.state.rememberedAccounts); assertTrue(f.revoked.isEmpty())
    }

    @Test fun networkFailureKeepsSelectedCredentialForNormalStartupRetry() {
        val f = Fixture(); f.twoAccounts(); f.offline = true
        f.auth.switchAccount("a"); f.flush()
        assertTrue(f.auth.state.restoreFailed); assertNull(f.auth.state.account)
        assertEquals(tokenA, f.store.current); assertEquals(2, f.store.saved.size)
        f.offline = false; f.auth.restore(); f.flush()
        assertEquals(alex.identity, f.auth.state.account); assertTrue(f.revoked.isEmpty())
    }

    @Test fun failedCleanupCannotActivateTargetOrPermitSignIn() {
        val f = Fixture(); f.twoAccounts(); f.failCleanup = true
        f.auth.switchAccount("a"); f.flush()
        assertTrue(f.auth.state.logoutFailed); assertNull(f.auth.state.account)
        assertNull(f.auth.sessionToken()); assertEquals(tokenB, f.store.current)
        f.auth.submit(); f.flush(); assertNull(f.auth.state.account)
        assertEquals(2, f.store.saved.size); assertTrue(f.revoked.isEmpty())
        f.failCleanup = false
        f.auth.logout(); f.flush()
        assertFalse(f.auth.state.logoutFailed)
        assertEquals(listOf(tokenB), f.revoked)
        assertEquals(listOf(alex), f.auth.state.rememberedAccounts)
    }

    @Test fun logoutRetryStillRemovesCredentialAfterRememberedStorageRecoversWithoutRevokingTwice() {
        val f = Fixture(); f.twoAccounts(); f.store.failRemember = true
        f.auth.logout(); f.flush()
        assertTrue(f.auth.state.logoutFailed); assertEquals(listOf(tokenB), f.revoked)
        assertEquals(2, f.store.saved.size)
        f.store.failRemember = false
        f.auth.logout(); f.flush()
        assertFalse(f.auth.state.logoutFailed); assertNull(f.store.current)
        assertEquals(listOf(tokenB), f.revoked)
        assertEquals(listOf(alex), f.auth.state.rememberedAccounts)
    }

    @Test fun restoredCurrentSessionCanBeSwitchedBackToIncludingLegacySingleSession() {
        val f = Fixture()
        f.store.current = tokenA; f.flush()
        assertEquals(alex.identity, f.auth.state.account)
        f.auth.addAccount(); f.flush(); f.login(riley)
        f.auth.switchAccount("a"); f.flush()
        assertEquals(alex.identity, f.auth.state.account); assertEquals(tokenA, f.auth.sessionToken())
        assertTrue(f.revoked.isEmpty())
    }

    @Test fun flagOffNeverReadsOrWritesRememberedCredentials() {
        val f = Fixture(enabled = false)
        f.store.saved = listOf(RememberedSession(tokenB, riley))
        f.flush(); f.login(alex)
        f.auth.switchAccount("b"); f.auth.addAccount(); f.flush()
        assertEquals(alex.identity, f.auth.state.account); assertEquals(0, f.localClears)
        f.auth.authenticationRequired(tokenA); f.flush(); f.login(alex)
        f.auth.logout(); f.flush()
        assertEquals(0, f.store.rememberedReads); assertEquals(0, f.store.rememberedWrites)
        assertTrue(f.auth.state.rememberedAccounts.isEmpty())
        assertEquals(listOf(tokenA), f.revoked); assertEquals(listOf(tokenB), f.store.saved.map { it.token })
    }
}

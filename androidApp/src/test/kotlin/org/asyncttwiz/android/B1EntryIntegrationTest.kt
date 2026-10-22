package org.asyncttwiz.android

import org.asyncttwiz.protocol.AccountIdentity
import org.asyncttwiz.protocol.SessionCreated
import org.junit.Test
import java.util.concurrent.AbstractExecutorService
import java.util.concurrent.TimeUnit
import kotlin.test.*

/** Independent B1 review: couple the production controllers, not just their isolated callbacks. */
class B1EntryIntegrationTest {
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

    private class Store : SessionStore {
        var saved: StoredSession = StoredSession.Present("A".repeat(43))
        override fun load() = saved
        override fun save(token: String) { saved = StoredSession.Present(token) }
        override fun clear() { saved = StoredSession.Empty }
    }

    private class Local : AccountLocalData {
        var owner: String? = "a"
        var destination = ShellDestination.CONTACTS
        var pendingLogout = false
        var deletions = 0
        override fun resumeLogout(store: SessionStore) { check(!pendingLogout) }
        override fun activate(accountId: String): ShellDestination {
            if (owner != accountId) { owner = accountId; destination = ShellDestination.MENU }
            return destination
        }
        override fun logout(store: SessionStore) {
            pendingLogout = true
            store.clear(); owner = null; destination = ShellDestination.MENU; deletions++
            pendingLogout = false
        }
    }

    private class Api : Accounts, PasswordResets {
        var account = AccountIdentity("a", "Wizard A")
        var resetFailure: AccountFailure? = null
        var confirmations = 0
        var logins = 0
        var password = ""
        override suspend fun register(username: String, email: String, password: String) = account
        override suspend fun login(email: String, password: String): SessionCreated {
            logins++; return SessionCreated("B".repeat(43), account)
        }
        override suspend fun identity(token: String) = account
        override suspend fun revoke(token: String) = Unit
        override suspend fun requestReset(email: String) { resetFailure?.let { throw it } }
        override suspend fun checkResetLink(token: String) { resetFailure?.let { throw it } }
        override suspend fun resetPassword(token: String, password: String) {
            resetFailure?.let { throw it }; confirmations++; this.password = password
        }
        override fun close() = Unit
    }

    private val origin = "https://reset.example"
    private val link = "$origin/reset-password?token=${"A".repeat(43)}"

    @Test fun requestInvalidLinkAndExplicitResetPreserveTheRightAccountBoundaries() {
        val api = Api(); val store = Store(); val local = Local()
        val authWorker = Worker(); val resetWorker = Worker()
        val auth = AuthController(api, store, authWorker, local) { it() }
        val reset = ResetController(api, origin, resetWorker, auth::passwordResetCompleted) { it() }
        authWorker.drain()
        assertEquals(ShellDestination.CONTACTS, auth.state.destination)

        reset.request("a@example.invalid"); reset.submit(); resetWorker.drain()
        assertFalse(reset.state!!.busy)
        assertFalse(reset.state!!.error)
        assertTrue(reset.state!!.message!!.startsWith("If that email is registered"))
        assertEquals(api.account, auth.state.account)
        assertIs<StoredSession.Present>(store.saved)
        reset.dismiss()
        api.resetFailure = AccountFailure("invalid_reset_link")
        reset.open(link); resetWorker.drain()
        assertEquals(ResetPage.INVALID, reset.state!!.page)
        assertEquals(api.account, auth.state.account)
        assertIs<StoredSession.Present>(store.saved)
        assertEquals(0, api.confirmations)

        api.resetFailure = null
        reset.dismiss(); reset.open(link); resetWorker.drain()
        assertEquals(0, api.confirmations) // Merely opening a valid link is not confirmation.
        val exact = " \tNew e\u0301 wizard 🧙\n "
        reset.edit(password = exact); reset.submit(); reset.submit(); resetWorker.drain()
        assertEquals(1, api.confirmations); assertEquals(exact, api.password)
        assertNull(reset.state); assertNull(auth.state.account)
        authWorker.drain()
        assertIs<StoredSession.Empty>(store.saved)
        assertEquals("Password changed. Sign in with your new password.", auth.state.message)
        assertEquals(0, api.logins); assertEquals(0, local.deletions)
        auth.edit(email = "a@example.invalid", password = exact); auth.submit(); authWorker.drain()
        assertEquals(ShellDestination.CONTACTS, auth.state.destination)
        assertEquals("", auth.state.password)

        auth.logout()
        assertNull(auth.state.account); assertEquals(emptyList(), auth.state.backStack)
        authWorker.drain(); assertIs<StoredSession.Empty>(store.saved)
        api.account = AccountIdentity("b", "Wizard B")
        auth.edit(email = "b@example.invalid", password = "other password")
        auth.submit(); authWorker.drain()
        assertEquals("b", auth.state.account!!.accountId)
        assertEquals(ShellDestination.MENU, auth.state.destination)
        assertEquals(1, local.deletions)
        reset.close(); resetWorker.drain(); auth.close(); authWorker.drain()
    }

    @Test fun resetCannotExposeAQueuedOldLoginOrBypassInterruptedLogout() {
        val api = Api(); val store = Store(); val local = Local()
        store.clear()
        val authWorker = Worker(); val resetWorker = Worker(); val posts = ArrayDeque<() -> Unit>()
        val auth = AuthController(api, store, authWorker, local) { posts.add(it) }
        val reset = ResetController(api, origin, resetWorker, auth::passwordResetCompleted) { it() }
        authWorker.drain(); posts.removeFirst()()
        auth.edit(email = "a@example.invalid", password = "old password")
        auth.submit(); authWorker.drain() // Authorized login's UI callback is still queued.
        reset.open(link); resetWorker.drain(); reset.edit(password = "new password")
        local.pendingLogout = true
        reset.submit(); resetWorker.drain()
        posts.removeFirst()(); assertNull(auth.state.account)
        authWorker.drain(); posts.removeFirst()()
        assertTrue(auth.state.restoreFailed); assertTrue(auth.state.restoring)
        auth.submit(); authWorker.drain(); assertEquals(1, api.logins)
        local.pendingLogout = false
        store.clear() // Model successful completion of the interrupted local deletion.
        auth.restore(); authWorker.drain(); posts.removeFirst()()
        assertFalse(auth.state.restoring); assertNull(auth.state.account)
        assertEquals("", auth.state.password); assertNull(reset.state)
        reset.close(); resetWorker.drain(); auth.close(); authWorker.drain()
    }
}

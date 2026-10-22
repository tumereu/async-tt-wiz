package org.asyncttwiz.android

import org.asyncttwiz.protocol.AccountIdentity
import org.asyncttwiz.protocol.SessionCreated
import org.junit.Test
import java.util.concurrent.AbstractExecutorService
import java.util.concurrent.TimeUnit
import kotlin.test.*

class AuthControllerTest {
    private class Worker : AbstractExecutorService() {
        val jobs = ArrayDeque<Runnable>()
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
        var value: StoredSession = StoredSession.Empty
        var failSave = false
        override fun load() = value
        override fun save(token: String) { if (failSave) error("private failure"); value = StoredSession.Present(token) }
        override fun clear() { value = StoredSession.Empty }
    }
    private class Api : Accounts {
        val identity = AccountIdentity("id", "Wizard")
        var calls = 0
        var failure: AccountFailure? = null
        var revoked = false
        var exact = ""
        override suspend fun register(username: String, email: String, password: String): AccountIdentity {
            calls++; exact = password; failure?.let { throw it }; return identity
        }
        override suspend fun login(email: String, password: String): SessionCreated {
            calls++; exact = password; failure?.let { throw it }; return SessionCreated("A".repeat(43), identity)
        }
        override suspend fun identity(token: String): AccountIdentity { failure?.let { throw it }; return identity }
        override suspend fun revoke(token: String) { revoked = true }
        override fun close() = Unit
    }

    @Test fun registrationReturnsToSignInAndSuccessClearsVolatilePassword() {
        val api = Api(); val store = Store(); val worker = Worker()
        val controller = AuthController(api, store, worker) { it() }
        worker.drain()
        controller.page(AuthPage.REGISTER)
        val secret = " \tCafe\u0301 🧙\n "
        controller.edit(username = "Wizard", email = "x@example.com", password = secret)
        controller.submit(); controller.submit()
        assertTrue(controller.state.busy)
        worker.drain()
        assertEquals(1, api.calls)
        assertEquals(secret, api.exact)
        assertEquals(AuthPage.LOGIN, controller.state.page)
        assertEquals("", controller.state.password)
        assertEquals("x@example.com", controller.state.email)
        assertNull(controller.state.account) // No guest/automatic login after registration.
        controller.edit(password = secret)
        controller.submit(); worker.drain()
        assertEquals(api.identity, controller.state.account)
        assertEquals("", controller.state.password)
        assertIs<StoredSession.Present>(store.value)
        assertFalse(controller.state.toString().contains(secret))
    }

    @Test fun failuresKeepCorrectionUsableButUnsavedTokenDoesNotEnter() {
        val api = Api(); val store = Store(); val worker = Worker()
        val controller = AuthController(api, store, worker) { it() }; worker.drain()
        api.failure = AccountFailure("invalid_credentials")
        controller.edit(email = "x@example.com", password = " goodpass ")
        controller.submit(); worker.drain()
        assertEquals(" goodpass ", controller.state.password)
        assertNull(controller.state.account)
        assertFalse(controller.state.busy)
        api.failure = AccountFailure("network")
        controller.submit(); worker.drain()
        assertEquals(" goodpass ", controller.state.password)
        assertEquals("x@example.com", controller.state.email)
        assertTrue(controller.state.message!!.contains("connection"))
        assertFalse(controller.state.busy)
        api.failure = null; store.failSave = true
        controller.submit(); worker.drain()
        assertTrue(api.revoked)
        assertNull(controller.state.account)
        assertEquals("", controller.state.password)
        assertIs<StoredSession.Empty>(store.value)
    }

    @Test fun persistedSessionRestoreDistinguishesNetworkFailureRevocationAndKeyLoss() {
        val api = Api(); val store = Store(); val worker = Worker()
        store.value = StoredSession.Present("A".repeat(43))
        api.failure = AccountFailure("network")
        val controller = AuthController(api, store, worker) { it() }; worker.drain()
        assertTrue(controller.state.restoreFailed)
        assertIs<StoredSession.Present>(store.value)
        controller.submit(); assertEquals(0, api.calls)
        api.failure = null; controller.restore(); worker.drain()
        assertEquals(api.identity, controller.state.account)
        api.failure = AccountFailure("authentication_required"); controller.restore(); worker.drain()
        assertIs<StoredSession.Empty>(store.value)
        assertFalse(controller.state.restoring)
        assertNull(controller.state.account)
        store.value = StoredSession.Unavailable; controller.restore(); worker.drain()
        assertIs<StoredSession.Empty>(store.value)
        assertTrue(controller.state.message!!.contains("unavailable"))
    }

    @Test fun finishingOwnerFencesQueuedCallbacksAndDropsPassword() {
        val posts = ArrayDeque<() -> Unit>(); val worker = Worker()
        val controller = AuthController(Api(), Store(), worker) { posts.add(it) }
        worker.drain(); posts.removeFirst()()
        controller.edit(password = "password"); controller.submit(); worker.drain()
        controller.close(); worker.drain(); posts.removeFirst()()
        assertNull(controller.state.account)
        assertEquals("", controller.state.password)
        assertTrue(worker.isShutdown)
    }
}

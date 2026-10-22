package org.asyncttwiz.android

import org.junit.Test
import java.util.concurrent.AbstractExecutorService
import java.util.concurrent.TimeUnit
import kotlin.test.*

class ResetControllerTest {
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
    private class Api : PasswordResets {
        val calls = mutableListOf<String>()
        var password = ""
        var failure: AccountFailure? = null
        override suspend fun requestReset(email: String) { calls += "request"; failure?.let { throw it } }
        override suspend fun checkResetLink(token: String) { calls += "check"; failure?.let { throw it } }
        override suspend fun resetPassword(token: String, password: String) {
            calls += "confirm"; this.password = password; failure?.let { throw it }
        }
        override fun close() = Unit
    }
    private val origin = "https://reset.example"
    private val link = "$origin/reset-password?token=${"A".repeat(43)}"

    @Test fun exactOriginPathAndCanonicalTokenOnly() {
        assertEquals("A".repeat(43), ResetLinks.token(link, origin))
        assertEquals("A".repeat(43), ResetLinks.token(link.replace("reset.example", "RESET.EXAMPLE:443"), origin))
        for (invalid in listOf(null, "", link.replace("https:", "http:"), link.replace("reset.example", "evil.example"),
            link.replace("reset.example", "reset.example.evil"), link.replace("reset.example", "user@reset.example"),
            link.replace("reset.example", "reset.example:444"), "$link#fragment", "$link&token=${"A".repeat(43)}",
            "$link&other=value", link.replace("reset-password", "%72eset-password"), link.replace("?token=", "?TOKEN="),
            link.replace("?token=A", "?token=%41"), link.dropLast(1) + "B", link + "=", link.replace("reset-password", "reset-password/"))) {
            assertNull(ResetLinks.token(invalid, origin), "Invalid link must not yield a token")
        }
        assertNull(ResetLinks.token(link, ""))
        assertNull(ResetLinks.token(link, "http://reset.example"))
    }

    @Test fun openChecksWithoutMutationDuplicateDeliveryPreservesInputAndExplicitSuccessClearsSecrets() {
        val api = Api(); val worker = Worker(); var success = 0
        val controller = ResetController(api, origin, worker, { success++ }) { it() }
        controller.open(link); assertTrue(controller.state!!.checking); worker.drain()
        assertEquals(listOf("check"), api.calls); assertEquals(0, success)
        val exact = " \tCafe\u0301 wizard 🧙\n "
        controller.edit(password = exact); controller.open(link); worker.drain()
        assertEquals(exact, controller.state!!.password); assertEquals(listOf("check"), api.calls)
        assertFalse(controller.state.toString().contains(exact))
        controller.submit(); controller.submit(); worker.drain()
        assertEquals(exact, api.password); assertEquals(listOf("check", "confirm"), api.calls)
        assertEquals(1, success); assertNull(controller.state)
    }

    @Test fun networkFailureIsRetryableButInvalidExpiredUsedLinksHaveNoPasswordAction() {
        val api = Api(); val worker = Worker()
        val controller = ResetController(api, origin, worker, { fail("No automatic success") }) { it() }
        api.failure = AccountFailure("network"); controller.open(link); worker.drain()
        assertTrue(controller.state!!.checkFailed); assertTrue(controller.state!!.checking)
        controller.submit(); worker.drain(); assertEquals(listOf("check"), api.calls)
        api.failure = null; controller.checkLink(); worker.drain()
        controller.edit(password = "exact password "); api.failure = AccountFailure("network")
        controller.submit(); worker.drain(); assertEquals("exact password ", controller.state!!.password)
        api.failure = AccountFailure("invalid_reset_link"); controller.submit(); worker.drain()
        assertEquals(ResetPage.INVALID, controller.state!!.page); assertEquals("", controller.state!!.password)
        val calls = api.calls.size; controller.submit(); worker.drain(); assertEquals(calls, api.calls.size)
        controller.request(); assertEquals(ResetPage.REQUEST, controller.state!!.page)
        controller.edit(email = "player@example.test"); api.failure = AccountFailure("network")
        controller.submit(); worker.drain(); assertEquals("player@example.test", controller.state!!.email)
        api.failure = null; controller.submit(); worker.drain()
        assertTrue(controller.state!!.message!!.startsWith("If that email is registered"))
        assertFalse(controller.state!!.error)
        controller.open("https://evil.example/reset-password?token=${"A".repeat(43)}")
        assertEquals(ResetPage.INVALID, controller.state!!.page)
    }

    @Test fun dismissalReplacementAndCloseFenceQueuedCallbacksAndSecrets() {
        val api = Api(); val worker = Worker(); val posts = ArrayDeque<() -> Unit>()
        val controller = ResetController(api, origin, worker, { fail("Old callback must be fenced") }) { posts.add(it) }
        controller.open(link); worker.drain()
        controller.open(link.dropLast(1) + "E") // New link while old callback is pending.
        posts.removeFirst()(); assertTrue(controller.state!!.checking)
        worker.drain(); posts.removeFirst()()
        controller.edit(password = "old account secret"); controller.submit(); worker.drain()
        controller.dismiss(); posts.removeFirst()(); assertNull(controller.state)
        controller.open(link); controller.close(); worker.drain()
        assertNull(controller.state); assertTrue(worker.isShutdown)
        controller.open(link); assertNull(controller.state)
    }
}

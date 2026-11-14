package org.asyncttwiz.e2e

import java.net.URI
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.function.Predicate
import kotlin.test.assertTrue
import org.junit.Test

/**
 * The web client when the connection drops requests (found 2026-11-14 in WebOptimizedCodeTest: a connection reset right
 * after sign-in gave "Uncaught exception in pods: Fail to fetch", a crash report). In the browser a failed fetch reached
 * the app as a `kotlin.Error`, which the API clients (catching `Exception`) let through as a crash; now it is a network
 * failure like Android's, retried and kept quiet.
 */
class WebDroppedRequestsTest {
    // SYS-020, SYS-021, DRF-078: the first read of each list Alice's browser makes right after sign-in (pods, games,
    // invitations, contacts, decks, activity) ends in a connection reset; the reads are retried quietly, no crash is
    // reported, and the Draft and Games lists show their pod and game
    @Test fun requestsDroppedRightAfterSignInAreRetriedQuietly() {
        val (alice, bob) = ServerSetup.fresh()
        ServerSetup.invitation(alice, bob, "Dropped request game")
        ServerSetup.setupPod(alice, "Dropped request pod")
        E2eBrowser().use { web ->
            val dropped = CopyOnWriteArrayList<String>()
            val asked = ConcurrentHashMap.newKeySet<String>()
            fun endpoint(url: String): String? = URI(url).path.takeIf { it in DROPPED || it.startsWith("/activity") }
            web.page.route(Predicate { url -> endpoint(url) != null }) { route ->
                val path = endpoint(route.request().url())!!
                if (route.request().method() == "GET" && asked.add(path)) {
                    dropped += path
                    route.abort("connectionreset")
                } else route.resume()
            }
            try {
                web.signIn(alice)
                web.waitFor("#menu-PODS")
                web.tap("#menu-PODS")
                web.waitForText("Dropped request pod")
                web.page.goBack()
                web.waitFor("#menu-GAMES", 30_000.0)
                web.tap("#menu-GAMES")
                web.waitForText("Dropped request game")
                assertTrue("/v1/pods" in dropped, "the pod list read was dropped; dropped: $dropped")
                assertTrue("/v1/games" in dropped, "the game list read was dropped; dropped: $dropped")
                val faults = web.faults()
                assertTrue(faults.isEmpty(), "The page reported faults (dropped: $dropped):\n${faults.joinToString("\n")}")
                // Each dropped read is in the app log as a network failure of its endpoint
                assertTrue(web.console.any { "/v1/pods" in it && "failed" in it }, "the dropped pod list read is logged")
            } catch (failure: Throwable) {
                web.evidence("WebDroppedRequestsTest")
                throw failure
            }
        }
    }

    private companion object {
        val DROPPED = setOf("/v1/pods", "/v1/games", "/v1/invitations", "/v1/contacts", "/v1/decks", "/v1/cubes")
    }
}

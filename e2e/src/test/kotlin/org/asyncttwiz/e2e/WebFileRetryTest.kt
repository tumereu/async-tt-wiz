package org.asyncttwiz.e2e

import java.net.URI
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import java.util.function.Predicate
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.Test

/**
 * The web app's built-in files on a shaky connection (owner, 2026-11-20): a file that fails to load is tried again a couple of
 * times before "ATW couldn't load" shows.
 */
class WebFileRetryTest {
    // WEB-005: the app's starting script fails once and a bundled font twice (connection resets); each is tried again, the
    // app starts and signs in, and the failed tries are in the page's log
    @Test fun builtInFilesThatFailToLoadAreTriedAgain() {
        val (alice, _) = ServerSetup.fresh()
        E2eBrowser().use { web ->
            val failures = mapOf("atw.js" to 1, "keyrune.ttf" to 2)
            val asked = ConcurrentHashMap<String, AtomicInteger>()
            fun file(url: String): String? = URI(url).path.substringAfterLast('/').takeIf { it in failures }
            web.page.route(Predicate { url -> file(url) != null }) { route ->
                val name = file(route.request().url())!!
                if (asked.computeIfAbsent(name) { AtomicInteger() }.incrementAndGet() <= failures.getValue(name))
                    route.abort("connectionreset")
                else route.resume()
            }
            try {
                web.signIn(alice)
                assertEquals(2, asked.getValue("atw.js").get(), "the script tried twice")
                assertEquals(3, asked.getValue("keyrune.ttf").get(), "the font tried three times")
                assertTrue(web.console.any { "atw.js didn't load (try 1 of 3)" in it }, "the script's failed try is logged")
                assertTrue(web.console.any { "Bundled file fonts/keyrune.ttf didn't load (try 2 of 3)" in it },
                    "the font's failed tries are logged")
                assertTrue(web.console.none { "ATW couldn't load" in it })
            } catch (failure: Throwable) {
                web.evidence("WebFileRetryTest")
                throw failure
            }
        }
    }
}

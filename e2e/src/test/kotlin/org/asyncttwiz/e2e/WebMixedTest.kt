package org.asyncttwiz.e2e

import arcanum.api.TurnStep
import arcanum.engine.state.PlayerId
import org.junit.Test
import kotlin.test.assertTrue

/**
 * One game, two clients (docs/specs/web-client.md §11 step 4): Alice plays in a real browser on the web build
 * ([E2eBrowser]: the shared UI and engine compiled for the web, the engine's Web Worker working out her plays), Bob on
 * the Android app's code ([E2ePlayer]). Each sees the other's moves through the server as two phones would.
 */
class WebMixedTest : E2e() {
    // WEB-007, SYS-017: Alice opens the game by its address in her browser and keeps; Bob's phone gets her keep and Bob
    // keeps; her browser goes on to her Main 1, where her "To combat" from the browser reaches Bob's phone
    @Test fun aBrowserAndAPhonePlayTheOpeningTogether() {
        startGame(alicePhone = false)
        bob.openGame(game, this)
        E2eBrowser().use { web ->
            try {
                web.signIn(aliceAccount, "/games/${game.id}")
                // TRN-062: the opening's Keep is a quick action, beside the greyed main button
                waitUntil("Keep among Alice's quick actions in her browser", timeoutSeconds = 180) { web.showsControl("Keep") }
                web.tap(web.control("Keep"))
                waitUntil("Bob's phone holding Alice's keep from her browser") {
                    bob.shown.held.any { it.author == aliceAccount.id && it.assumed == null }
                }
                assertTrue(bob.showsControl("Keep"), "Bob decides on his own opening")
                bob.tap("Keep")
                waitUntil("Alice's browser in her Main 1 after Bob's keep from his phone") { web.mainWords()?.contains("To combat") == true }
                web.tap("#main-button")
                waitUntil("Bob's phone seeing Alice leave her Main 1 from her browser") {
                    bob.state.let { s -> s.turn.active == PlayerId(aliceAccount.id) && s.turn.step != TurnStep.PrecombatMain }
                }
                val faults = web.faults()
                assertTrue(faults.isEmpty(), "The page reported faults:\n${faults.joinToString("\n")}")
            } catch (failure: Throwable) {
                web.evidence("WebMixedTest")
                throw failure
            }
        }
    }
}

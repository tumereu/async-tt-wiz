package org.asyncttwiz.e2e

import arcanum.api.TurnStep
import arcanum.engine.state.PlayerId
import org.junit.Test
import kotlin.test.assertTrue

/**
 * Problem report VSVLSK (owner, 2026-11-15, Firefox against the Android app): after a play the web table said
 * "Reconnecting" and the plays never reached the other player's phone. The page's game socket opened again every second,
 * with nothing sent. The cause: the tabs of one browser are one installation to the server, and another ATW tab opening its
 * game streams took them over; the table's next action was fenced, and from then on every reconnect asked to resume the
 * stream the other tab now held, was fenced again and started over, silently, forever.
 */
class WebTwoTabsTest : E2e() {
    // WEB-060, SYS-013, SYS-014: Alice plays in one tab while a second ATW tab of the same browser opens on the menu (taking
    // the game's stream over); her next action in the first tab still reaches Bob's phone, and the first tab's socket doesn't
    // go into a reconnect loop
    @Test fun aSecondTabDoesNotStopTheFirstTabsActions() {
        startGame(alicePhone = false)
        bob.openGame(game, this)
        E2eBrowser().use { web ->
            try {
                web.signIn(aliceAccount, "/games/${game.id}")
                waitUntil("Keep among Alice's quick actions in her browser", timeoutSeconds = 180) { web.showsControl("Keep") }
                web.tap(web.control("Keep"))
                waitUntil("Bob's phone holding Alice's keep from her browser") {
                    bob.shown.held.any { it.author == aliceAccount.id && it.assumed == null }
                }
                bob.tap("Keep")
                waitUntil("Alice's browser in her Main 1 after Bob's keep") { web.mainWords()?.contains("To combat") == true }
                val connectsBefore = web.console.count { "Connected" in it }
                // A second tab of the same browser, on the menu: signed in from the shared storage, it opens the streams of
                // the account's games
                web.openTab("/")
                // (Playwright hands over the tab's console lines only while it is called: a short wait each round)
                waitUntil("the second tab connected", timeoutSeconds = 120) {
                    web.page.waitForTimeout(100.0); web.tabConsole.any { "Connected" in it }
                }
                // Its stream opens right after its welcome
                web.page.waitForTimeout(3_000.0)
                web.tap("#main-button")
                waitUntil("Bob's phone seeing Alice leave her Main 1 from her first tab", timeoutSeconds = 30) {
                    bob.state.let { s -> s.turn.active == PlayerId(aliceAccount.id) && s.turn.step != TurnStep.PrecombatMain }
                }
                // The first tab's socket keeps working: at most one connection more (none is needed)
                web.page.waitForTimeout(3_000.0)
                val connects = web.console.count { "Connected" in it } - connectsBefore
                assertTrue(connects <= 1, "Alice's first tab connected $connects more times:\n" +
                    web.console.filter { "game:" in it }.takeLast(40).joinToString("\n"))
                val faults = web.faults()
                assertTrue(faults.isEmpty(), "The page reported faults:\n${faults.joinToString("\n")}")
            } catch (failure: Throwable) {
                web.evidence("WebTwoTabsTest")
                throw failure
            }
        }
    }
}

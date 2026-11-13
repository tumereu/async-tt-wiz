package org.asyncttwiz.e2e

import com.microsoft.playwright.Page
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import org.junit.Test

/**
 * The web build in a real browser (docs/specs/web-client.md §10, §11 steps 3 and 4): the server serves the built web
 * client (webApp's webDist, WEB_APP_DIR) beside the API, and Chrome (the installed one, through Playwright: [E2eBrowser])
 * loads it, signs in, lists the games, moves between screens by address and browser history, and opens a started game's
 * table and makes a move there. Not a Robolectric test: the app runs in Chrome.
 */
class WebSmokeTest {
    // WEB-001, WEB-002, WEB-005, WEB-007, WEB-009, WEB-011: load, sign in, list games; the address and the browser's Back
    // follow the screens, a refresh keeps the screen and the sign-in, and the card records are kept in the browser; a
    // started game opened by its address shows its table, where Alice keeps her opening hand (the server takes it), with
    // the browser's gestures off; its Back leaves for Games
    @Test fun loadSignInListGamesAndMakeAMove() {
        val (alice, bob) = ServerSetup.fresh()
        ServerSetup.invitation(alice, bob, "Web smoke game")
        val started = ServerSetup.prepare(alice, bob, TestDeck.thirds(), TestDeck.thirds(), name = "Web started game").ready()
        val origin = E2eStack.origin
        E2eBrowser().use { web ->
            val page = web.page
            try {
                web.signIn(alice)
                web.waitFor("#menu-GAMES")
                assertEquals("/", web.path())
                web.tap("#menu-GAMES")
                web.waitForText("Web smoke game")
                assertEquals("/games", web.path())
                web.waitForText("Web started game")
                // A refresh opens the same screen, still signed in (the session is kept in the browser)
                page.reload()
                web.waitForText("Web smoke game", 120_000.0)
                assertEquals("/games", web.path())
                // The browser's Back returns to the menu
                page.goBack()
                web.waitFor("#menu-GAMES", 30_000.0)
                assertEquals("/", web.path())
                // WEB-009: the card records were installed into the browser's storage
                page.waitForFunction("""async () => {
                    const db = await new Promise((resolve, reject) => { const r = indexedDB.open('atw'); r.onsuccess = () => resolve(r.result); r.onerror = reject; });
                    const count = await new Promise((resolve) => { const r = db.transaction('catalog').objectStore('catalog').count(); r.onsuccess = () => resolve(r.result); });
                    db.close();
                    return count > 0;
                }""", null, Page.WaitForFunctionOptions().setTimeout(60_000.0))
                // WEB-007, TRN-062: a started game, opened by its address, shows its table: the opening's Keep among the quick
                // actions (the main button greyed beside them, TRN-063)
                val before = ServerSetup.head(alice, started.id).engine?.localHeads?.get(alice.id)
                page.navigate("$origin/games/${started.id}")
                web.waitFor("#main-button", 120_000.0)
                assertEquals("/games/${started.id}", web.path())
                // WEB-011: the browser's own gestures are off on the table
                assertTrue(page.evaluate("document.documentElement.classList.contains('atw-table')") as Boolean, "table page marked")
                val deadline = System.nanoTime() + 60_000_000_000L
                while (!web.showsControl("Keep")) {
                    check(System.nanoTime() < deadline) { "no Keep among the quick actions; the main button: ${web.mainWords()}" }
                    Thread.sleep(200)
                }
                web.tap(web.control("Keep"))
                // The move reaches the server: Alice's own keep moves her head there
                while (ServerSetup.head(alice, started.id).engine?.localHeads?.get(alice.id) == before) {
                    check(System.nanoTime() < deadline) { "Alice's keep never reached the server" }
                    Thread.sleep(300)
                }
                assertNotEquals(before, ServerSetup.head(alice, started.id).engine?.localHeads?.get(alice.id))
                // WEB-002, WEB-004: Esc with nothing open leaves the table at once, for Games
                page.keyboard().press("Escape")
                web.waitForText("Web smoke game")
                assertEquals("/games", web.path())
                assertTrue(!(page.evaluate("document.documentElement.classList.contains('atw-table')") as Boolean), "screen page again")
                val faults = web.faults()
                assertTrue(faults.isEmpty(), "The page reported faults:\n${faults.joinToString("\n")}")
            } catch (failure: Throwable) {
                web.evidence("WebSmokeTest")
                throw failure
            }
        }
    }
}

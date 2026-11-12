package org.asyncttwiz.e2e

import com.microsoft.playwright.Browser
import com.microsoft.playwright.BrowserType
import com.microsoft.playwright.Page
import com.microsoft.playwright.Playwright
import com.microsoft.playwright.options.WaitForSelectorState
import java.io.File
import java.nio.file.Paths
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.Test

/**
 * The web build in a real browser (docs/specs/web-client.md §10, §11 step 3): the server serves the built web client
 * (webApp's webDist, WEB_APP_DIR) beside the API, and Chrome (the installed one, through Playwright) loads it, signs in,
 * lists the games and moves between screens by address and browser history. Controls are found by the screens' semantics,
 * which the page mirrors into its document (test tags are element ids). Not a Robolectric test: the app runs in Chrome.
 */
class WebSmokeTest {
    // WEB-001, WEB-002, WEB-005, WEB-007, WEB-009: load, sign in, list games; a started game shows the placeholder; the
    // address and the browser's Back follow the screens, a refresh keeps the screen and the sign-in, and the card records
    // are kept in the browser
    @Test fun loadSignInAndListGames() {
        val (alice, bob) = ServerSetup.fresh()
        ServerSetup.invitation(alice, bob, "Web smoke game")
        val started = ServerSetup.prepare(alice, bob, TestDeck.thirds(), TestDeck.thirds(), name = "Web started game").ready()
        val origin = E2eStack.origin
        Playwright.create().use { playwright ->
            val browser = playwright.chromium().launch(BrowserType.LaunchOptions().setChannel("chrome").setHeadless(true))
            browser.use {
                val context = browser.newContext(Browser.NewContextOptions().setViewportSize(412, 860))
                val page = context.newPage()
                val console = CopyOnWriteArrayList<String>()
                page.onConsoleMessage { console += "${it.type()}: ${it.text()}" }
                page.onPageError { console += "pageerror: $it" }
                try {
                    page.navigate("$origin/")
                    // WEB-005: the loading screen until the app draws
                    page.waitForSelector("#email", Page.WaitForSelectorOptions().setTimeout(120_000.0).setState(WaitForSelectorState.ATTACHED))
                    tap(page, "#email")
                    page.keyboard().type(alice.email)
                    tap(page, "#password")
                    page.keyboard().type(ServerSetup.PASSWORD)
                    page.keyboard().press("Enter")
                    // A first sign-in offers an avatar: skipped
                    page.waitForSelector("#menu-GAMES, div[role=button]:text-is('Skip')",
                        Page.WaitForSelectorOptions().setTimeout(60_000.0).setState(WaitForSelectorState.ATTACHED))
                    if (page.locator("div[role=button]:text-is('Skip')").count() > 0) tap(page, "div[role=button]:text-is('Skip')")
                    page.waitForSelector("#menu-GAMES", Page.WaitForSelectorOptions().setTimeout(60_000.0).setState(WaitForSelectorState.ATTACHED))
                    assertEquals("/", path(page))
                    tap(page, "#menu-GAMES")
                    waitForText(page, "Web smoke game")
                    assertEquals("/games", path(page))
                    waitForText(page, "Web started game")
                    // A refresh opens the same screen, still signed in (the session is kept in the browser)
                    page.reload()
                    waitForText(page, "Web smoke game", 120_000.0)
                    assertEquals("/games", path(page))
                    // The browser's Back returns to the menu
                    page.goBack()
                    page.waitForSelector("#menu-GAMES", Page.WaitForSelectorOptions().setTimeout(30_000.0).setState(WaitForSelectorState.ATTACHED))
                    assertEquals("/", path(page))
                    // WEB-009: the card records were installed into the browser's storage
                    page.waitForFunction("""async () => {
                        const db = await new Promise((resolve, reject) => { const r = indexedDB.open('atw'); r.onsuccess = () => resolve(r.result); r.onerror = reject; });
                        const count = await new Promise((resolve) => { const r = db.transaction('catalog').objectStore('catalog').count(); r.onsuccess = () => resolve(r.result); });
                        db.close();
                        return count > 0;
                    }""", null, Page.WaitForFunctionOptions().setTimeout(60_000.0))
                    // WEB-007: a started game, opened by its address, says it opens in the app for now; its Back leaves
                    page.navigate("$origin/games/${started.id}")
                    page.waitForSelector("#game-elsewhere", Page.WaitForSelectorOptions().setTimeout(60_000.0).setState(WaitForSelectorState.ATTACHED))
                    assertEquals("/games/${started.id}", path(page))
                    tap(page, "div[role=button]:text-is('Back to Games')")
                    waitForText(page, "Web smoke game")
                    assertEquals("/games", path(page))
                    val faults = console.filter { it.contains("pageerror") || it.contains("ATW/crash") || it.contains("NotImplementedError") }
                    assertTrue(faults.isEmpty(), "The page reported faults:\n${faults.joinToString("\n")}")
                } catch (failure: Throwable) {
                    val dir = File(E2eStack.output, "failures/WebSmokeTest").apply { mkdirs() }
                    runCatching { page.screenshot(Page.ScreenshotOptions().setPath(Paths.get(dir.path, "page.png"))) }
                    File(dir, "console.log").writeText(console.joinToString("\n"))
                    // The semantics mirror the controls were looked for in: each element's tag, id, role, label, text, place
                    runCatching { File(dir, "semantics.txt").writeText(page.evaluate(SEMANTICS).toString()) }
                    throw failure
                }
            }
        }
    }

    private fun path(page: Page): String = java.net.URI(page.url()).path

    private companion object {
        /** The page's semantics mirror (inside the app's shadow root) as lines, for a failure's evidence. */
        const val SEMANTICS = """() => {
            const out = [];
            const walk = (root) => { for (const e of root.querySelectorAll('*')) {
                if (e.shadowRoot) walk(e.shadowRoot);
                if (!e.id && !e.getAttribute('role')) continue;
                const r = e.getBoundingClientRect();
                out.push(e.tagName + '#' + e.id + ' role=' + e.getAttribute('role') + ' label=' + (e.getAttribute('aria-label') || '') +
                    ' text=' + (e.textContent || '').slice(0, 60) + ' @' + [r.x, r.y, r.width, r.height].map(Math.round).join(','));
            } };
            walk(document);
            return out.join('\n');
        }"""
    }

    /** A tap in the middle of the control [selector] finds: on the page's canvas, as a player's (the semantics mirror
     * only says where controls are). */
    private fun tap(page: Page, selector: String) {
        val box = checkNotNull(page.locator(selector).first().boundingBox()) { "$selector has no place on the page" }
        page.mouse().click(box.x + box.width / 2, box.y + box.height / 2)
    }

    /** Waits until the page's semantics show [text] (the a11y mirror's text). */
    private fun waitForText(page: Page, text: String, timeout: Double = 60_000.0) {
        page.waitForSelector("text=$text", Page.WaitForSelectorOptions().setTimeout(timeout).setState(WaitForSelectorState.ATTACHED))
    }
}

package org.asyncttwiz.e2e

import com.microsoft.playwright.Browser
import com.microsoft.playwright.BrowserType
import com.microsoft.playwright.Page
import com.microsoft.playwright.Playwright
import com.microsoft.playwright.options.WaitForSelectorState
import java.io.File
import java.nio.file.Paths
import java.util.concurrent.CopyOnWriteArrayList

/**
 * A player in a real browser (docs/specs/web-client.md §10): the installed Chrome, headless through Playwright, on the
 * web build the server serves beside its API ([E2eStack.origin], WEB_APP_DIR), in a phone-sized window. Controls are found
 * by the screens' semantics, which the page mirrors into its document (test tags are element ids, labels and texts are
 * the elements'), and tapped on the page's canvas where they are, as a player's finger would.
 */
internal class E2eBrowser : AutoCloseable {
    private val playwright = Playwright.create()
    private val browser = playwright.chromium().launch(BrowserType.LaunchOptions().setChannel("chrome").setHeadless(true))
    private val context = browser.newContext(Browser.NewContextOptions().setViewportSize(412, 860))
    val page: Page = context.newPage()
    /** The page's console lines and errors. */
    val console = CopyOnWriteArrayList<String>()

    init {
        page.onConsoleMessage { console += "${it.type()}: ${it.text()}" }
        page.onPageError { console += "pageerror: $it" }
    }

    /** Opens the web app at [path] and signs in as [account] (a first sign-in's avatar offer skipped): on the menu or
     * at [path]'s screen. */
    fun signIn(account: TestAccount, path: String = "/") {
        page.navigate("${E2eStack.origin}$path")
        // WEB-005: the loading screen until the app draws
        waitFor("#email", 120_000.0)
        tap("#email")
        page.keyboard().type(account.email)
        tap("#password")
        page.keyboard().type(ServerSetup.PASSWORD)
        page.keyboard().press("Enter")
        waitFor("#menu-GAMES, #main-button, div[role=button]:text-is('Skip')", 60_000.0)
        if (page.locator("div[role=button]:text-is('Skip')").count() > 0) tap("div[role=button]:text-is('Skip')")
    }

    /** The page's address path. */
    fun path(): String = java.net.URI(page.url()).path

    /** Waits until the semantics mirror has something [selector] finds. */
    fun waitFor(selector: String, timeout: Double = 60_000.0) {
        page.waitForSelector(selector, Page.WaitForSelectorOptions().setTimeout(timeout).setState(WaitForSelectorState.ATTACHED))
    }

    /** Waits until the page's semantics show [text]. */
    fun waitForText(text: String, timeout: Double = 60_000.0) = waitFor("text=$text", timeout)

    /** Whether the semantics mirror has something [selector] finds now. */
    fun shows(selector: String): Boolean = page.locator(selector).count() > 0

    /** A tap in the middle of the control [selector] finds, on the page's canvas (the mirror only says where it is). */
    fun tap(selector: String) {
        val box = checkNotNull(page.locator(selector).first().boundingBox()) { "$selector has no place on the page" }
        page.mouse().click(box.x + box.width / 2, box.y + box.height / 2)
    }

    /** The control (a button) whose label is [label] (alone, or merged with its own words: "Keep, Keep"), as the Android
     * tests' `showsControl` finds it by its description: a quick-action cell such as the opening's Keep (TRN-062). */
    fun control(label: String): String = "div[role=button][aria-label='$label'], div[role=button][aria-label^='$label, ']"

    /** Whether the control [label] ([control]) shows now. */
    fun showsControl(label: String): Boolean = shows(control(label))

    /** The main button's words (its semantics label), null while it isn't shown. */
    fun mainWords(): String? = page.locator("#main-button").takeIf { it.count() > 0 }?.first()?.let { button ->
        button.getAttribute("aria-label") ?: button.textContent()
    }

    /** Lines of the page's console that tell of a fault (an uncaught error, a crash report, code missing on the web). */
    fun faults(): List<String> = console.filter { it.contains("pageerror") || it.contains("ATW/crash") || it.contains("NotImplementedError") }

    /** A failure's evidence in e2e/build/e2e/failures/[name]: the page's picture, its console and its semantics. */
    fun evidence(name: String) {
        val dir = File(E2eStack.output, "failures/$name").apply { mkdirs() }
        runCatching { page.screenshot(Page.ScreenshotOptions().setPath(Paths.get(dir.path, "page.png"))) }
        File(dir, "console.log").writeText(console.joinToString("\n"))
        runCatching { File(dir, "semantics.txt").writeText(page.evaluate(SEMANTICS).toString()) }
    }

    override fun close() {
        runCatching { browser.close() }
        playwright.close()
    }

    private companion object {
        /** The page's semantics mirror (inside the app's shadow root) as lines: each element's tag, id, role, label, text,
         * place. */
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
}

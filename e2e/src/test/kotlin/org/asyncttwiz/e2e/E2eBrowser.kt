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
 * A player in a real browser: the installed Chrome, headless through Playwright, on the
 * web build the server serves beside its API ([E2eStack.origin], WEB_APP_DIR), in a phone-sized window. Controls are found
 * by the screens' semantics, which the page mirrors into its document (test tags are element ids, labels and texts are
 * the elements'), and tapped on the page's canvas where they are, as a player's finger would. [width] × [height] is the
 * window (CSS px): a phone's by default; 1024 × 600 or more draws the desktop layout (WEB-013). [chromeArgs]: more of
 * Chrome's command-line switches.
 */
internal class E2eBrowser(width: Int = 412, height: Int = 860, chromeArgs: List<String> = emptyList()) : AutoCloseable {
    private val playwright = Playwright.create()
    private val browser = playwright.chromium().launch(BrowserType.LaunchOptions().setChannel("chrome").setHeadless(true)
        .setArgs(chromeArgs))
    private val context = browser.newContext(Browser.NewContextOptions().setViewportSize(width, height))
    val page: Page = context.newPage()
    /** The page's console lines and errors. */
    val console = CopyOnWriteArrayList<String>()
    private val signInRefusals = java.util.concurrent.atomic.AtomicInteger()

    init {
        page.onConsoleMessage { console += "${it.type()}: ${it.text()}" }
        page.onPageError { console += "pageerror: $it" }
        // WEB-056: Chrome ended the page (its "Aw, Snap!": a crash or out of memory)
        page.onResponse { if (it.status() == 429 && java.net.URI(it.url()).path == "/sessions") signInRefusals.incrementAndGet() }
        page.onCrash { console += "pageerror: the page crashed (Chrome ended it)" }
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
        // The server's sign-in limits (AccountHttp: 5 a minute per email, 10 per address) are shared by the whole run's
        // browsers and phones on 127.0.0.1: refused, the page shows "Too many attempts…" and Sign in is pressed again
        // after the minute, as a player would
        val signedIn = "#menu-GAMES, #main-button, div[role=button]:text-is('Skip')"
        for (attempt in 1..3) {
            val refused = signInRefusals.get()
            val end = System.currentTimeMillis() + 60_000
            while (System.currentTimeMillis() < end && !shows(signedIn) && signInRefusals.get() == refused) page.waitForTimeout(250.0)
            if (signInRefusals.get() == refused) break
            page.waitForTimeout(61_000.0)
            tap("div[role=button]:text-is('Sign in')")
        }
        waitFor(signedIn, 60_000.0)
        if (page.locator("div[role=button]:text-is('Skip')").count() > 0) tap("div[role=button]:text-is('Skip')")
    }

    /** The page's console lines of another tab ([openTab]). */
    val tabConsole = CopyOnWriteArrayList<String>()
    private val tabs = CopyOnWriteArrayList<Page>()

    /** Another tab of the same browser opened at [path]: the same site storage, so the same sign-in and the same browser
     * installation; its console goes to [tabConsole]. */
    fun openTab(path: String): Page = context.newPage().also { tab ->
        tabs += tab
        tab.onConsoleMessage { tabConsole += "${it.type()}: ${it.text()}" }
        tab.onPageError { tabConsole += "pageerror: $it" }
        tab.navigate("${E2eStack.origin}$path")
    }

    /** The site may show notifications (as if the player had allowed them in the browser's prompt). */
    fun allowNotifications() {
        context.grantPermissions(listOf("notifications"), com.microsoft.playwright.BrowserContext.GrantPermissionsOptions()
            .setOrigin(E2eStack.origin))
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

    /** Where the semantics mirror places what [selector] finds first (CSS px of the page), waiting up to 5 s for an element
     * just attached to be given its place. */
    fun box(selector: String): com.microsoft.playwright.options.BoundingBox {
        val deadline = System.nanoTime() + 5_000_000_000L
        while (true) {
            page.locator(selector).first().takeIf { page.locator(selector).count() > 0 }?.boundingBox()?.let { return it }
            check(System.nanoTime() < deadline) { "$selector has no place on the page" }
            Thread.sleep(100)
        }
    }

    /** Where the semantics mirror places what [selector] finds once that place has held for 1.5 s (waiting up to 20 s):
     * Compose copies the screen into the mirror only once the screen has been still for 100 ms, and at least once a second
     * while it keeps changing, so two reads alike may still be an earlier frame's place (WEB-058, found 2026-11-15). */
    fun settledBox(selector: String): com.microsoft.playwright.options.BoundingBox {
        val deadline = System.nanoTime() + 20_000_000_000L
        var last = box(selector)
        var since = System.nanoTime()
        while (System.nanoTime() - since < 1_500_000_000L) {
            check(System.nanoTime() < deadline) { "$selector kept moving in the page's semantics mirror: last at ${place(last)}" }
            Thread.sleep(100)
            val now = box(selector)
            if (place(now) != place(last)) { last = now; since = System.nanoTime() }
        }
        return last
    }

    private fun place(box: com.microsoft.playwright.options.BoundingBox) = listOf(box.x, box.y, box.width, box.height)

    /** A tap in the middle of the control [selector] finds, on the page's canvas (the mirror only says where it is). */
    fun tap(selector: String) {
        val box = box(selector)
        page.mouse().click(box.x + box.width / 2, box.y + box.height / 2)
    }

    /** The mouse moved to the middle of what [selector] finds (no button pressed), then the cursor the page shows there
     * ([cursorAt]). */
    fun cursorOver(selector: String): String {
        val box = box(selector)
        return cursorAt(box.x + box.width / 2, box.y + box.height / 2)
    }

    /** The mouse moved to ([x], [y]) (no button pressed); the cursor the page shows there once the app has had a frame to
     * set it, as CSS names it ("pointer", "text", "default"): the computed cursor of the element under the mouse. */
    fun cursorAt(x: Double, y: Double): String {
        page.mouse().move(x - 3, y - 3)
        page.mouse().move(x, y, com.microsoft.playwright.Mouse.MoveOptions().setSteps(3))
        page.evaluate("() => new Promise(done => requestAnimationFrame(() => requestAnimationFrame(done)))")
        return page.evaluate(CURSOR, listOf(x, y)).toString()
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

    /** The text in the text field [selector] finds (its value as the page's semantics mirror holds it), "" when empty. */
    fun fieldText(selector: String): String = page.locator(selector).first().evaluate(
        "e => (e.value !== undefined ? e.value : (e.textContent || ''))").toString()

    /** Lines of the page's console that tell of a fault (an uncaught error, a crash report, code missing on the web). */
    fun faults(): List<String> = console.filter { it.contains("pageerror") || it.contains("ATW/crash") || it.contains("NotImplementedError") }

    /** The page's semantics mirror as lines: each element's tag, id, role, label, text and place. */
    fun semantics(): String = page.evaluate(SEMANTICS).toString()

    /** A failure's evidence in e2e/build/e2e/failures/[name]: the page's picture, its console and its semantics. */
    fun evidence(name: String) {
        val dir = File(E2eStack.output, "failures/$name").apply { mkdirs() }
        runCatching { page.screenshot(Page.ScreenshotOptions().setPath(Paths.get(dir.path, "page.png"))) }
        File(dir, "console.log").writeText(console.joinToString("\n"))
        if (tabs.isNotEmpty()) File(dir, "tab-console.log").writeText(tabConsole.joinToString("\n"))
        tabs.forEachIndexed { index, tab -> runCatching { tab.screenshot(Page.ScreenshotOptions().setPath(Paths.get(dir.path, "tab${index + 2}.png"))) } }
        runCatching { File(dir, "semantics.txt").writeText(semantics()) }
    }

    override fun close() {
        runCatching { browser.close() }
        playwright.close()
    }

    private companion object {
        /** The computed cursor of the element at the point ([x, y]), looking into shadow roots. */
        const val CURSOR = """([x, y]) => {
            let e = document.elementFromPoint(x, y);
            while (e && e.shadowRoot) { const inner = e.shadowRoot.elementFromPoint(x, y); if (!inner || inner === e) break; e = inner; }
            return e ? getComputedStyle(e).cursor : 'none';
        }"""
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

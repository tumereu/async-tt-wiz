package org.asyncttwiz.e2e

import com.microsoft.playwright.Page
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.Test

/**
 * The web client's notifications, install and tab title in a real browser (docs/specs/web-client.md §9.1, §9.2, step 7):
 * the manifest and its icon load, the service worker registers, the tab's title counts the games waiting for Alice, and
 * Browser notifications turns on from the Games list's hint (the browser allowing it), registers this browser with the
 * server, changes a kind and turns off in Settings. Headless Chrome has no push service, so the browser's subscription is
 * a stand-in with a push service's address; the server keeps it as it would a real one.
 */
class WebNotificationsTest {
    private fun subscriptions(account: TestAccount): List<String> =
        E2eStack.query("SELECT coalesce(web_push, '') FROM activity_installation WHERE account_id = '${account.id}'")

    private fun waitFor(what: String, condition: () -> Boolean) {
        val deadline = System.nanoTime() + 60_000_000_000L
        while (!condition()) {
            check(System.nanoTime() < deadline) { "Timed out waiting for $what" }
            Thread.sleep(200)
        }
    }

    // WEB-054, WEB-055, WEB-050, WEB-051, WEB-053: installable (manifest, icons, service worker); "(N) ATW"; the hint's Turn
    // on asks the browser (allowed) and the server keeps this browser's subscription with every kind; Settings switches a kind
    // (the server told at once) and turns notifications off (the server forgets this browser); the hint stays away
    @Test fun installableTitledAndNotificationsTurnOnAndOff() {
        val (alice, bob) = ServerSetup.fresh()
        val started = ServerSetup.prepare(alice, bob, TestDeck.thirds(), TestDeck.thirds(), name = "Web notified game").ready()
        val origin = E2eStack.origin
        E2eBrowser().use { web ->
            val page = web.page
            try {
                web.allowNotifications()
                page.addInitScript(PUSH_STAND_IN)
                web.signIn(alice)
                web.waitFor("#menu-GAMES")

                // WEB-054: the manifest and its icon, and the service worker for the whole site
                assertEquals("/manifest.webmanifest", page.evaluate("document.querySelector('link[rel=manifest]').getAttribute('href')"))
                val manifest = page.evaluate("""async () => {
                    const manifest = await (await fetch('/manifest.webmanifest')).json();
                    const icon = await fetch(manifest.icons[0].src);
                    return [manifest.short_name, manifest.display, manifest.start_url, String(icon.status), icon.headers.get('content-type')].join('|');
                }""")
                assertEquals("ATW|standalone|/|200|image/png", manifest)
                page.waitForFunction("""async () => {
                    const registration = await navigator.serviceWorker.getRegistration('/');
                    return !!registration && !!registration.active && registration.active.scriptURL.endsWith('/sw.js') && registration.scope.endsWith('/');
                }""", null, Page.WaitForFunctionOptions().setTimeout(60_000.0))

                // WEB-055: the tab's title counts the games waiting for Alice's move
                web.tap("#menu-GAMES")
                web.waitForText("Web notified game")
                val waiting = ServerSetup.head(alice, started.id).summary.attentionHolder == alice.id
                val title = if (waiting) "(1) ATW" else "ATW"
                waitFor("the title $title (it is ${page.title()})") { page.title() == title }

                // WEB-053, WEB-050: the hint, and its Turn on: the browser allows, the server keeps the subscription
                web.waitForText("Get notified when it's your move")
                assertTrue(subscriptions(alice).isEmpty(), "nothing registered before turning on")
                web.tap("#notifications-hint-on")
                waitFor("Alice's browser registered") { subscriptions(alice).any { it.contains("fcm.googleapis.com") } }
                val registered = subscriptions(alice).single()
                assertTrue(registered.contains("\"invitations\":true") && registered.contains("\"games\":true") &&
                    registered.contains("\"drafts\":true"), registered)
                waitFor("the hint gone") { !web.shows("#notifications-hint") }

                // WEB-050, WEB-051: Settings shows the switch on with its kinds; Draft activity off is told to the server
                page.navigate("$origin/settings")
                web.waitFor("#notification-kind-drafts", 120_000.0)
                // Scrolled into view with the mouse wheel (the page is the app's canvas)
                page.mouse().move(200.0, 500.0)
                waitFor("Draft activity on screen") {
                    val box = page.locator("#notification-kind-drafts").first().boundingBox()
                    (box != null && box.height > 0 && box.y > 0 && box.y + box.height < 800).also { if (!it) page.mouse().wheel(0.0, 300.0) }
                }
                web.tap("#notification-kind-drafts")
                waitFor("Draft activity off on the server") { subscriptions(alice).singleOrNull()?.contains("\"drafts\":false") == true }
                // Off: the server forgets this browser
                waitFor("Browser notifications on screen") {
                    val box = page.locator("#browser-notifications").first().boundingBox()
                    (box != null && box.height > 0 && box.y > 60).also { if (!it) page.mouse().wheel(0.0, -200.0) }
                }
                web.tap("#browser-notifications")
                waitFor("Alice's browser forgotten") { subscriptions(alice).isEmpty() }
                waitFor("the kinds hidden") { !web.shows("#notification-kind-drafts") }

                // WEB-053: turning on put the hint away for good
                page.navigate("$origin/games")
                web.waitForText("Web notified game", 120_000.0)
                assertTrue(!web.shows("#notifications-hint"), "the hint stays away")
                val faults = web.faults()
                assertTrue(faults.isEmpty(), "The page reported faults:\n${faults.joinToString("\n")}")
            } catch (failure: Throwable) {
                web.evidence("WebNotificationsTest")
                throw failure
            }
        }
    }

    private companion object {
        /** A push subscription in place of the browser's push service (headless Chrome has none): the address of one, with
         * well-formed keys. */
        const val PUSH_STAND_IN = """(() => {
            if (typeof PushManager === 'undefined') return;
            const bytes = (count, first) => { const array = new Uint8Array(count); for (let i = 0; i < count; i++) array[i] = i; array[0] = first; return array.buffer; };
            let current = null;
            PushManager.prototype.subscribe = async function (options) {
                current = { endpoint: 'https://fcm.googleapis.com/fcm/send/e2e-stand-in', options: { applicationServerKey: options.applicationServerKey },
                    getKey: (name) => name === 'p256dh' ? bytes(65, 4) : bytes(16, 1),
                    unsubscribe: async () => { current = null; return true; } };
                return current;
            };
            PushManager.prototype.getSubscription = async function () { return current; };
        })()"""
    }
}

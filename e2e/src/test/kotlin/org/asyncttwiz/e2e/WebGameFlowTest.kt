package org.asyncttwiz.e2e

import arcanum.engine.state.PlayerId
import com.microsoft.playwright.options.BoundingBox
import org.asyncttwiz.protocol.PresenceState
import org.asyncttwiz.protocol.game.AssumeResolves
import org.asyncttwiz.protocol.game.PlaySettings
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A game's turns end to end in a real browser against a phone (the core loop of CoreLoopTest, on the web build): Alice plays
 * in Chrome ([E2eBrowser], the desktop layout at 1440 × 900, her plays worked out by the engine's Web Worker), Bob on the
 * Android app's code ([E2ePlayer]). Everything Alice does goes through the page's canvas with the mouse and the keyboard.
 */
class WebGameFlowTest : E2e() {
    private val aliceName get() = aliceAccount.identity.username

    // WEB-007, WEB-013, WEB-023, WEB-031, HIS-084, HIS-094, TRN-057, CMB-028: Alice keeps in her browser, plays a Mountain and casts
    // an Ember Cub by clicks while Bob is away, takes the cast back with Undo (nothing after it is Bob's) and casts it again;
    // Bob comes back and lets it resolve; Shift+Space ends her turn. She hides the page while Bob plays his Mountain and ends
    // his turn; shown again, her table waits with "Bob has made moves · Tap to watch" and her click plays his turn back. In
    // her next turn she goes to combat, a click stages her Cub, "Attack (1)" declares it, and its 2 damage reaches Bob's phone
    @Test fun aGameInTheBrowserAgainstAPhone() {
        startGame(alicePhone = false, bobSettings = PlaySettings(assume = AssumeResolves.Always),
            deal = { a, b -> "Mountain" in hand(a) && "Ember Cub" in hand(a) && "Mountain" in hand(b) })
        bob.openGame(game, this)
        val alice = PlayerId(aliceAccount.id)
        E2eBrowser(width = 1440, height = 900).use { web ->
            try {
                web.signIn(aliceAccount, "/games/${game.id}")
                // The opening: both keep (TRN-062), Alice in her browser
                waitUntil("Keep among Alice's quick actions in her browser", timeoutSeconds = 180) { web.showsControl("Keep") }
                assertTrue(web.shows("#phase-track"), "Alice's table in the desktop layout")
                web.tap(web.control("Keep"))
                waitUntil("Bob's phone holding Alice's keep from her browser") {
                    bob.shown.held.any { it.author == aliceAccount.id && it.assumed == null }
                }
                bob.tap("Keep")
                waitUntil("Alice's Main 1 in her browser") { web.mainWords()?.contains("To combat") == true }
                // Bob leaves: what Alice casts waits for him, so taking it back is hers alone
                bob.away()
                bob.link.cut()
                // WEB-023: a click raises a hand card, a click on the raised card plays it
                playFromHand(web, "Mountain")
                waitUntil("the Mountain on Alice's battlefield in her browser") { web.onBattlefield("Mountain", aliceName).isNotEmpty() }
                playFromHand(web, "Ember Cub")
                // HIS-084: Undo takes the cast back: the Cub back in her hand, the Mountain Autotap tapped for it untapped
                waitUntil("Alice's Undo") { web.shows("#desktop-undo") }
                web.page.waitForTimeout(500.0)
                web.tap("#desktop-undo")
                waitUntil("the Cub back in Alice's hand in her browser") {
                    web.inHand("Ember Cub", aliceName).isNotEmpty() && web.shows("div[aria-label^='Mountain, owner $aliceName, untapped']")
                }
                // The same cast again
                playFromHand(web, "Ember Cub")
                // Bob comes back and lets it resolve
                bob.back()
                waitUntil("the Cub on Alice's battlefield on Bob's phone", along = listOf(bob)) {
                    bob.field("Ember Cub", alice).isNotEmpty() && bob.field("Mountain", alice).isNotEmpty()
                }
                assertEquals(1, bob.field("Ember Cub", alice).size, "one Cub: the cast taken back never reached Bob")
                waitUntil("the Cub on Alice's battlefield in her browser and her Main 1 again") {
                    web.onBattlefield("Ember Cub", aliceName).isNotEmpty() && web.mainWords()?.contains("To combat") == true
                }
                // WEB-031: Shift+Space is the main button's hold: End turn
                web.page.waitForTimeout(500.0)
                web.page.keyboard().press("Shift+Space")
                waitUntil("Bob's turn on his phone") { bob.state.turn.active == bob.me }
                // Alice hides the page (another tab in front): she is away while Bob plays his turn
                web.visible(false)
                waitUntil("Bob seeing Alice away") { bob.lobby.state.opponentPresence?.presence?.state != PresenceState.ONLINE }
                waitUntil("Bob's Main 1") { bob.inOwnMain() }
                bob.playFromHand("Mountain", this)
                waitUntil("Bob's Mountain on his battlefield") { bob.field("Mountain").isNotEmpty() && bob.inOwnMain() }
                bob.hold("To combat")
                waitUntil("Bob's turn over, all of it on the server") {
                    bob.state.turn.active == alice && bob.engine { device.session.pending.isEmpty() }
                }
                // HIS-094: shown again, her table waits for her click on the centre text, then plays Bob's turn back
                web.visible(true)
                val centre = "#centre-action"
                waitUntil("Alice's centre text", timeoutSeconds = 60) { web.shows(centre) }
                val label = web.page.locator(centre).first().getAttribute("aria-label").orEmpty()
                assertTrue(label.startsWith("${bob.name} has made moves · Tap to watch"), "the centre text says what waits: $label")
                web.tap(centre)
                var clicked = System.nanoTime()
                waitUntil("Bob's turn played back in Alice's browser, her Main 1", timeoutSeconds = 90) {
                    // A stop in the playback waits for her click on the centre text again
                    if (web.shows(centre) && System.nanoTime() - clicked > 2_000_000_000L) { web.tap(centre); clicked = System.nanoTime() }
                    !web.shows(centre) && web.mainWords()?.contains("To combat") == true && web.onBattlefield("Mountain", bob.name).isNotEmpty()
                }
                // TRN-057, CMB-028: to combat, a click on her Cub stages it, "Attack (1)" declares it
                web.page.waitForTimeout(500.0)
                tapMain(web)
                waitUntil("Alice declaring attackers in her browser") { web.mainWords()?.contains("No attack") == true }
                var staged = 0L
                waitUntil("the Cub staged in Alice's browser", timeoutSeconds = 60) {
                    // Until her table has worked the combat out a click only says so: clicked again after a while
                    if (System.nanoTime() - staged > 10_000_000_000L && web.mainWords()?.contains("Attack (1)") != true) {
                        web.click(web.onBattlefield("Ember Cub", aliceName).first())
                        staged = System.nanoTime()
                    }
                    web.mainWords()?.contains("Attack (1)") == true
                }
                val life = bob.state.player(bob.me).life
                web.page.waitForTimeout(500.0)
                tapMain(web)
                var pressed = System.nanoTime()
                waitUntil("the Cub's damage on Bob's phone", timeoutSeconds = 90, along = listOf(bob)) {
                    // Alice passes on with the main button where she holds priority until the damage
                    val s = bob.state
                    // (the main button goes while the combat's hold lasts: tapped only where the page places it)
                    if (s.priority.holder == alice && s.player(bob.me).life == life && System.nanoTime() - pressed > 2_000_000_000L &&
                        web.mainWords()?.let { it.startsWith("Next") || it.startsWith("Pass") || it.startsWith("To ") } == true &&
                        web.placed("#main-button")) {
                        tapMain(web)
                        pressed = System.nanoTime()
                    }
                    s.player(bob.me).life < life
                }
                assertEquals(life - 2, bob.state.player(bob.me).life, "the Cub's damage")
                val faults = web.faults()
                assertTrue(faults.isEmpty(), "The page reported faults:\n${faults.joinToString("\n")}")
            } catch (failure: Throwable) {
                web.evidence("WebGameFlowTest")
                throw failure
            }
        }
    }

    /**
     * WEB-023, ZON-079, ZON-102: Alice's hand card [name] played by clicks in her browser: a click raises it, a click on the
     * raised card plays it; done once it has left her hand (one card fewer of the name there).
     */
    private fun playFromHand(web: E2eBrowser, name: String) {
        val before = web.inHand(name, aliceName).size
        check(before > 0) { "no $name in Alice's hand in her browser" }
        var clicked = 0L
        waitUntil("$name played from Alice's hand in her browser", timeoutSeconds = 60) {
            val cards = web.inHand(name, aliceName)
            if (cards.size < before) return@waitUntil true
            // A click a moment after the last (the card's raise has been drawn), on where the card is now
            if (System.nanoTime() - clicked > 1_500_000_000L) { web.click(cards.first()); clicked = System.nanoTime() }
            false
        }
    }

    /** A click on the main button, then the mouse off it, over the empty battlefield: resting on it opens its tooltip, a layer
     * the page's mirror shows alone while it is open (WEB-059), which would hide the main button from the test. */
    private fun tapMain(web: E2eBrowser) {
        web.tap("#main-button")
        web.cursorAt(560.0, 300.0)
    }

    /** The page hidden or ([shown]) shown again, as the browser tells it when another tab comes to the front and back. */
    private fun E2eBrowser.visible(shown: Boolean) {
        page.evaluate("""shown => {
            Object.defineProperty(document, 'visibilityState', { configurable: true, get: () => shown ? 'visible' : 'hidden' });
            Object.defineProperty(document, 'hidden', { configurable: true, get: () => !shown });
            document.dispatchEvent(new Event('visibilitychange'));
        }""", shown)
    }

    /** Where the page's semantics mirror places each card [name] of [owner]'s (hand, battlefield, Stack). */
    private fun E2eBrowser.cards(name: String, owner: String): List<BoundingBox> =
        page.locator("div[aria-label^='$name, owner $owner, ']").all().mapNotNull { it.boundingBox() }

    /** [owner]'s battlefield on the page. */
    private fun E2eBrowser.battlefield(owner: String): BoundingBox = box("div[aria-label^='$owner Battlefield, ']")

    /** [owner]'s cards [name] on their battlefield on the page. */
    private fun E2eBrowser.onBattlefield(name: String, owner: String): List<BoundingBox> {
        val field = battlefield(owner)
        return cards(name, owner).filter { box ->
            val x = box.x + box.width / 2
            val y = box.y + box.height / 2
            x >= field.x && x <= field.x + field.width && y >= field.y && y <= field.y + field.height
        }.sortedBy { it.x }
    }

    /** Alice's cards [name] in her hand on the page: below her battlefield, left to right. */
    private fun E2eBrowser.inHand(name: String, owner: String): List<BoundingBox> {
        val field = battlefield(owner)
        return cards(name, owner).filter { it.y + it.height / 2 > field.y + field.height }.sortedBy { it.x }
    }

    /** Whether the page's semantics mirror places what [selector] finds now (attached and given a size). */
    private fun E2eBrowser.placed(selector: String): Boolean =
        page.locator(selector).let { it.count() > 0 && it.first().boundingBox()?.let { box -> box.width > 0 } == true }

    /** A click in the middle of [box] on the page's canvas. */
    private fun E2eBrowser.click(box: BoundingBox) = page.mouse().click(box.x + box.width / 2, box.y + box.height / 2)
}

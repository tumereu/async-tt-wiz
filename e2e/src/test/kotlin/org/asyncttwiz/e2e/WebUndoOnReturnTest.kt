package org.asyncttwiz.e2e

import arcanum.engine.input.Input
import arcanum.engine.state.PlayerId
import com.microsoft.playwright.WebSocketRoute
import org.asyncttwiz.protocol.PresenceState
import org.asyncttwiz.protocol.game.AssumeResolves
import org.asyncttwiz.protocol.game.PlaySettings
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Owner, prod 0.2.1, 2026-11-17: "if an opponent undoes while I'm away the app rewinds instantly instead of telling me
 * this happened", on the desktop web client: Alice plays in a real browser ([E2eBrowser], the desktop layout), Bob on the
 * Android app's code. In the browser "away" is the page hidden (another tab in front, the window minimised): the
 * browser's visibility, which the app follows as Android's app leaving and coming back (HIS-035).
 */
class WebUndoOnReturnTest : E2e() {
    /** The page hidden or ([shown]) shown again, as the browser tells it when another tab comes to the front and back. */
    private fun E2eBrowser.visible(shown: Boolean) {
        page.evaluate("""shown => {
            Object.defineProperty(document, 'visibilityState', { configurable: true, get: () => shown ? 'visible' : 'hidden' });
            Object.defineProperty(document, 'hidden', { configurable: true, get: () => !shown });
            document.dispatchEvent(new Event('visibilitychange'));
        }""", shown)
    }

    /**
     * The page's game socket, as the server's messages reach it: while [asleep] they are lost (a computer asleep, its
     * connection gone without a word); [wake] ends the socket, and on the page's next one the game's revisions wait until a
     * head with engine state has been handed on, then follow it (a head is read as soon as asked for, a revision published
     * up to a moment after its commit; after more revisions than a resubscription replays, the head comes alone).
     */
    private class PageSocket {
        @Volatile var asleep = false
        @Volatile var overtaken = false
        private var holding = false
        private val held = mutableListOf<String>()
        private var current: WebSocketRoute? = null

        fun route(ws: WebSocketRoute) {
            val server = ws.connectToServer()
            synchronized(this) { current = ws }
            ws.onMessage { frame -> frame.text()?.let(server::send) ?: frame.binary()?.let(server::send) }
            server.onMessage { message ->
                val text = message.text() ?: run { message.binary()?.let(ws::send); return@onMessage }
                synchronized(this) {
                    if (asleep || current !== ws) return@synchronized
                    if (holding && REVISION.containsMatchIn(text)) { held += text; return@synchronized }
                    ws.send(text)
                    if (holding && HEAD.containsMatchIn(text) && "\"engine\":{" in text) {
                        holding = false
                        held.forEach(ws::send)
                        held.clear()
                        overtaken = true
                    }
                }
            }
        }

        fun wake() = synchronized(this) {
            asleep = false
            holding = true
            current?.close()
        }

        companion object {
            val REVISION = Regex("^\\{\"type\":\"revision\"")
            val HEAD = Regex("^\\{\"type\":\"head\"")
        }
    }

    // HIS-205, HIS-158, HIS-035: Alice's browser stops at Bob's Ember Herald (she could respond) and she hides the page; her
    // computer sleeps, Bob undoes the cast she had seen and ends his turn; she shows the page again and its new connection
    // gets the game's head ahead of the revisions: her table is as she left it, the Herald on the Stack, with "Bob undid
    // moves · Tap to watch", and nothing moves until her click on it, which plays the undo
    @Test fun anUndoWhileThePageWasHiddenWaitsForAClick() {
        startGame(TestDeck.glimmers(), TestDeck.thirds(TestCards.herald), bobStarts = true, alicePhone = false,
            bobSettings = PlaySettings(assume = AssumeResolves.Always),
            deal = { a, b -> "Idle Glimmer" in hand(a) && "Mountain" in hand(b) && "Ember Herald" in hand(b) })
        bob.openGame(game, this)
        E2eBrowser(width = 1440, height = 900).use { web ->
            try {
                val socket = PageSocket()
                web.page.routeWebSocket(java.util.regex.Pattern.compile(".*"), socket::route)
                // Playwright hands the page's socket traffic on only while the test talks to the browser
                everyRun += { web.page.waitForTimeout(1.0) }
                web.signIn(aliceAccount, "/games/${game.id}")
                waitUntil("Keep among Alice's quick actions in her browser", timeoutSeconds = 180) { web.showsControl("Keep") }
                web.tap(web.control("Keep"))
                bob.tap("Keep")
                waitUntil("Bob's Main 1") { bob.inOwnMain() }
                bob.playFromHand("Mountain", this)
                waitUntil("Bob's Mountain on the battlefield") { bob.field("Mountain").isNotEmpty() && bob.inOwnMain() }
                val herald = atw(bob.hand("Ember Herald").first())
                bob.playFromHand("Ember Herald", this)
                waitUntil("the Herald on Bob's battlefield") { herald in bob.field("Ember Herald").map(::atw) && bob.inOwnMain() }
                waitUntil("all of Bob's on the server") { bob.engine { device.session.pending.isEmpty() } }
                val cast = assertNotNull(bob.engine { device.session.events }.firstOrNull { e ->
                    e.engine?.steps?.any { (it.input as? Input.Cast)?.card?.let(::atw) == herald } == true })
                // Her browser stops at his spell (she holds an Idle Glimmer: she could respond) and tells the server she saw it
                waitUntil("Alice's browser seeing the cast", timeoutSeconds = 60) {
                    ServerSetup.head(bob.account, game.id).engine?.localHeads?.get(aliceAccount.id) == cast.id
                }
                web.visible(false)
                waitUntil("Bob seeing Alice away") { bob.lobby.state.opponentPresence?.presence?.state != PresenceState.ONLINE }
                socket.asleep = true
                val target = assertNotNull(bob.shown.undoTarget)
                assertEquals(cast.parentId, target, "Undo's point: just before the cast")
                bob.tap("Undo")
                waitUntil("the Herald back in Bob's hand") { where(bob.shown.board, herald) == "HAND" }
                bob.hold("To combat")
                waitUntil("Bob's turn over") { bob.state.turn.active == PlayerId(aliceAccount.id) }
                waitUntil("all of Bob's on the server") { bob.engine { device.session.pending.isEmpty() } }
                socket.wake()
                web.visible(true)
                waitUntil("the head having overtaken the revisions on Alice's new connection", timeoutSeconds = 60) { socket.overtaken }
                val centre = "#centre-action"
                waitUntil("Alice's centre text", timeoutSeconds = 30) { web.shows(centre) }
                val label = web.page.locator(centre).first().getAttribute("aria-label").orEmpty()
                assertTrue(label.startsWith("${bob.name} undid moves · Tap to watch"), "the centre text says what waits: $label")
                // Nothing moves while she waits: the undo isn't played (her app log would say the shown board was taken back)
                web.page.waitForTimeout(2_000.0)
                assertTrue(web.shows(centre), "the centre text stays until her click")
                val takenBack = "Shown board taken back: rewind to $target"
                assertFalse(web.console.any { takenBack in it }, "nothing taken back before her click")
                web.tap(centre)
                waitUntil("the undo played on Alice's table after her click", timeoutSeconds = 30) { web.console.any { takenBack in it } }
                val faults = web.faults()
                assertTrue(faults.isEmpty(), "The page reported faults:\n${faults.joinToString("\n")}")
            } catch (failure: Throwable) {
                web.evidence("WebUndoOnReturnTest")
                throw failure
            }
        }
    }
}

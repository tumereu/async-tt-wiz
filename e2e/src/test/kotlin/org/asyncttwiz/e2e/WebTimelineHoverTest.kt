package org.asyncttwiz.e2e

import org.junit.Test
import kotlin.test.assertTrue

/**
 * The desktop timeline with a mouse in a real browser ([E2eBrowser], the desktop layout at 1440 × 900): resting the pointer
 * on an event shows its text, as holding it does on the phone.
 */
class WebTimelineHoverTest : E2e() {
    // HIS-011, WEB-089, WEB-028: after both keep, T opens Alice's timeline in her browser at the newest event; the pointer
    // resting on the game's first event shows "The game began" in a tooltip (the layer the page's mirror shows alone while it
    // is open, WEB-059) without moving the review cursor, and the tooltip goes once the pointer leaves the event
    @Test fun restingOnATimelineEventShowsItsText() {
        startGame(alicePhone = false)
        bob.openGame(game, this)
        E2eBrowser(width = 1440, height = 900).use { web ->
            try {
                web.signIn(aliceAccount, "/games/${game.id}")
                waitUntil("Keep among Alice's quick actions in her browser", timeoutSeconds = 180) { web.showsControl("Keep") }
                web.tap(web.control("Keep"))
                waitUntil("Bob's phone holding Alice's keep from her browser") {
                    bob.shown.held.any { it.author == aliceAccount.id && it.assumed == null }
                }
                bob.tap("Keep")
                waitUntil("Alice's Main 1 in her browser") { web.mainWords()?.contains("To combat") == true }
                web.cursorAt(560.0, 300.0)
                web.page.keyboard().press("t")
                waitUntil("Alice's timeline mode at the newest event") { web.showsControl("Exit timeline") }
                val first = "div[aria-label='The game began']"
                waitUntil("the game's first event on Alice's strip, the table mirrored") { web.shows(first) && web.shows("#phase-track") }
                web.cursorOver(first)
                waitUntil("the first event's text in a tooltip, alone in the page's mirror") {
                    !web.shows("#phase-track") && web.shows("text=The game began")
                }
                web.cursorAt(560.0, 300.0)
                waitUntil("the tooltip gone once the pointer left the event") { web.shows("#phase-track") }
                assertTrue(web.showsControl("Exit timeline"), "resting on an event never moved the review cursor from the newest")
                val faults = web.faults()
                assertTrue(faults.isEmpty(), "The page reported faults:\n${faults.joinToString("\n")}")
            } catch (failure: Throwable) {
                web.evidence("WebTimelineHoverTest")
                throw failure
            }
        }
    }
}

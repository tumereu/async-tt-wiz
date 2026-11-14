package org.asyncttwiz.e2e

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * The web client's mouse cursor in a real browser ([E2eBrowser], the desktop layout at 1440 × 900), as web pages show it:
 * the hand over what can be clicked, the text cursor over text fields.
 */
class WebHoverPointerTest : E2e() {
    // WEB-058 (owner, desktop playtest 2026-11-14): on Alice's desktop table in her browser the mouse shows the hand over a
    // control (the opening's Keep, the dock button) and over a card of her hand drawn on the table's canvas, the text cursor
    // over the docked chat's field, and no hand over an empty battlefield
    @Test fun theHandOverControlsAndCardsAndTheTextCursorOverFields() {
        startGame(alicePhone = false, deal = { a, _ -> "Mountain" in hand(a) })
        bob.openGame(game, this)
        E2eBrowser(width = 1440, height = 900).use { web ->
            try {
                web.signIn(aliceAccount, "/games/${game.id}")
                waitUntil("Keep among Alice's quick actions in her browser", timeoutSeconds = 180) { web.showsControl("Keep") }
                assertTrue(web.shows("#phase-track"), "Alice's table in the desktop layout")
                assertEquals("pointer", web.cursorOver(web.control("Keep")), "over Keep")
                assertEquals("pointer", web.cursorOver("#dock-button"), "over the dock button")
                assertEquals("pointer", web.cursorOver("div[role=button][aria-label^='Mountain, owner ']"), "over her hand's Mountain")
                assertEquals("text", web.cursorOver("div[role=textbox][aria-label='Chat message draft']"), "over the chat's field")
                assertNotEquals("pointer", web.cursorOver("div[aria-label*=' Battlefield, 0 cards']"),
                    "over an empty battlefield")
                val faults = web.faults()
                assertTrue(faults.isEmpty(), "The page reported faults:\n${faults.joinToString("\n")}")
            } catch (failure: Throwable) {
                web.evidence("WebHoverPointerTest")
                throw failure
            }
        }
    }
}

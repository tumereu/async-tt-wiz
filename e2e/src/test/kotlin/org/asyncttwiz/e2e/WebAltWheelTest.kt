package org.asyncttwiz.e2e

import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.Test

/**
 * The owner's report (desktop web, Firefox, 2026-11-15): holding Alt over the table and turning the mouse wheel to scroll an
 * Inspect went back a page, Firefox's own Alt+wheel. Alice, in the desktop layout of a real browser ([E2eBrowser],
 * 1440 × 900), has her game's table open: the page cancels the browser's own handling of the wheel turned with Alt or Shift
 * held (the app still gets it, to scroll Inspect), and turning it with Alt held leaves her on her game. Not a Robolectric
 * test: what the browser does with the wheel is the page's to stop.
 */
class WebAltWheelTest {
    // WEB-024: the wheel with Alt or Shift held never moves the browser through its history or scrolls the page
    @Test fun theWheelWithAltHeldNeverLeavesTheTable() {
        val (alice, bob) = ServerSetup.fresh()
        val started = ServerSetup.prepare(alice, bob, TestDeck.thirds(), TestDeck.thirds(), name = "Alt wheel game").ready()
        E2eBrowser(1440, 900).use { web ->
            val page = web.page
            try {
                web.signIn(alice)
                page.navigate("${E2eStack.origin}/games/${started.id}")
                web.waitFor("#main-button", 120_000.0)
                web.waitFor("#phase-track")
                val path = web.path()
                fun cancelled(alt: Boolean, shift: Boolean) = page.evaluate(WHEEL, listOf(alt, shift)) == true
                assertTrue(cancelled(alt = true, shift = false), "the browser's own Alt+wheel (Firefox: history) is cancelled")
                assertTrue(cancelled(alt = false, shift = true), "the browser's own Shift+wheel is cancelled")
                // Alt held with the wheel turned over the table, as the owner did
                page.mouse().move(560.0, 400.0)
                page.keyboard().down("Alt")
                page.mouse().wheel(0.0, 360.0)
                page.waitForTimeout(300.0)
                page.keyboard().up("Alt")
                page.waitForTimeout(500.0)
                assertEquals(path, web.path(), "still on her game")
                assertTrue(web.shows("#phase-track"), "her table still shown")
                val faults = web.faults()
                assertTrue(faults.isEmpty(), "The page reported faults:\n${faults.joinToString("\n")}")
            } catch (failure: Throwable) {
                web.evidence("WebAltWheelTest")
                throw failure
            }
        }
    }

    private companion object {
        /** A wheel turn over the middle of the table with Alt ([alt]) or Shift ([shift]) held: whether the page cancelled the
         * browser's own handling of it. */
        const val WHEEL = """([alt, shift]) => {
            const target = document.elementFromPoint(560, 400) || document.body;
            const e = new WheelEvent('wheel', { deltaY: 120, altKey: alt, shiftKey: shift, clientX: 560, clientY: 400,
                cancelable: true, bubbles: true, composed: true });
            target.dispatchEvent(e);
            return e.defaultPrevented;
        }"""
    }
}

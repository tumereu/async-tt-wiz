package org.asyncttwiz.e2e

import kotlin.test.assertTrue
import org.junit.Test

/**
 * The web table's frame times in the app log (owner, desktop playtest 2026-11-14: "Laggy FPS" in Firefox): Alice, in the
 * desktop layout of a real browser ([E2eBrowser], 1440 × 900), opens a started game's table, sends a problem report from it
 * and leaves it; the app log (the page's console carries its lines) names the table, the window and its graphics as it
 * opened, the frames drawn so far as the report went (owner, 2026-11-15, "Web FPS is still stuttery": the pictures the page
 * drew, with how long its drawing of them took, not every refresh of an unchanged page), and the whole visit's as she left.
 * Frame rates themselves are never asserted (no timing tests): only that the frames were timed and summarised.
 */
class WebFrameTimesTest {
    // WEB-057, WEB-068, WEB-004: the table's frame times as it opens, as a problem report is sent from it and as Esc leaves it
    @Test fun theTablesFrameTimesReachTheAppLog() {
        val (alice, bob) = ServerSetup.fresh()
        val started = ServerSetup.prepare(alice, bob, TestDeck.thirds(), TestDeck.thirds(), name = "Frame times game").ready()
        E2eBrowser(1440, 900).use { web ->
            val page = web.page
            fun frameLines() = web.console.filter { "ATW/frames: Frame times: table of game ${started.id}" in it }
            fun waitForLine(what: String, test: (String) -> Boolean): String {
                val deadline = System.nanoTime() + 60_000_000_000L
                while (true) {
                    frameLines().firstOrNull(test)?.let { return it }
                    check(System.nanoTime() < deadline) { "no frame times line $what; frame lines: ${frameLines()}" }
                    page.waitForTimeout(200.0) // Playwright delivers console events only while it is called
                }
            }
            try {
                web.signIn(alice)
                page.navigate("${E2eStack.origin}/games/${started.id}")
                web.waitFor("#main-button", 120_000.0)
                val opened = waitForLine("as the table opened") { " opened; window 1440x900 at " in it }
                assertTrue("; graphics " in opened, "the graphics the browser draws with: $opened")
                // A few seconds of the table drawing (the opening's Keep waits), the mouse moving over it meanwhile
                for (step in 0 until 60) { page.mouse().move(300.0 + step * 12.0, 450.0); page.waitForTimeout(50.0) }
                // WEB-057, WEB-068: a problem report sent from the table carries the frames so far (Table actions → Send problem report)
                web.tap("[aria-label='Table actions']")
                web.waitForText("Send problem report")
                web.tap("div[role=button]:has-text('Send problem report')")
                web.waitFor("#report-description")
                web.tap("#report-description")
                page.keyboard().type("frame times")
                web.tap("#send-report")
                val soFar = waitForLine("as the problem report was sent") { " so far: " in it }
                assertTrue(Regex(" so far: [0-9.]+ s drawn: [1-9][0-9]* frames \\(").containsMatchIn(soFar), "frames counted: $soFar")
                assertTrue(Regex("p50 [0-9.]+ p95 [0-9.]+ p99 [0-9.]+ max [0-9.]+ ms; ").containsMatchIn(soFar), soFar)
                // Owner, 2026-11-15 ("Web FPS is still stuttery"): only the pictures the page drew are frames, each with the
                // time the page's own drawing of it took
                assertTrue(Regex("max [0-9.]+ ms; drawing [0-9.]+ ms a frame, at most [0-9.]+ ms; slow [0-9]+ ").containsMatchIn(soFar), soFar)
                // WEB-004: Esc closes what is open, then leaves the table; its summary goes to the log
                val deadline = System.nanoTime() + 60_000_000_000L
                while (frameLines().none { " closed (left): " in it }) {
                    check(System.nanoTime() < deadline) { "the table never closed; path ${web.path()}, frame lines: ${frameLines()}" }
                    page.keyboard().press("Escape")
                    page.waitForTimeout(700.0)
                }
                val closed = frameLines().first { " closed (left): " in it }
                assertTrue(Regex(" closed \\(left\\): [0-9.]+ s drawn: [1-9][0-9]* frames \\(").containsMatchIn(closed), closed)
                val faults = web.faults()
                assertTrue(faults.isEmpty(), "The page reported faults:\n${faults.joinToString("\n")}")
            } catch (failure: Throwable) {
                web.evidence("WebFrameTimesTest")
                throw failure
            }
        }
    }
}

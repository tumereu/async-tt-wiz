package org.asyncttwiz.e2e

import org.junit.Test
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * The page's hidden accessibility structure (the semantics mirror screen readers, the e2e tests and the hover pointer read,
 * WEB-058) follows what shows: a menu or dialog closed leaves it at once, not only after a reload (found 2026-11-14 by the
 * frame-rate worker), and the screen under it is mirrored again. Alice in a real browser ([E2eBrowser], the phone layout).
 * Not a Robolectric test: the mirror is the browser page's.
 */
class WebSemanticsMirrorTest {
    // WEB-059, WEB-058: on Alice's table Table actions opens and Esc closes it: its entries leave the mirror, the pointer over
    // where one was isn't the hand, and the table's controls are mirrored again; the same for its problem report dialog,
    // which takes Table actions' place and closes with it
    @Test fun aClosedMenuOrDialogLeavesTheMirror() {
        val (alice, bob) = ServerSetup.fresh()
        val started = ServerSetup.prepare(alice, bob, TestDeck.thirds(), TestDeck.thirds(), name = "Mirror game").ready()
        E2eBrowser().use { web ->
            val page = web.page
            try {
                web.signIn(alice, "/games/${started.id}")
                web.waitFor("#main-button", 120_000.0)
                val deadline = System.nanoTime() + 60_000_000_000L
                while (!web.showsControl("Keep")) {
                    check(System.nanoTime() < deadline) { "no Keep among the quick actions; the main button: ${web.mainWords()}" }
                    Thread.sleep(200)
                }
                // Table actions (a dialog of its own): its entries in the mirror while it shows
                val report = web.control("Send problem report")
                web.tap(web.control("Table actions"))
                web.waitFor(report, 30_000.0)
                val box = checkNotNull(page.locator(report).first().boundingBox())
                page.keyboard().press("Escape")
                gone(web, report, "the closed Table actions' entry")
                back(web)
                assertNotEquals("pointer", web.cursorAt(box.x + box.width / 2, box.y + box.height / 2),
                    "the hand over where the closed menu's entry was")
                // Its problem report, a dialog in its place: closed with Esc, the table is mirrored again
                web.tap(web.control("Table actions"))
                web.waitFor(report, 30_000.0)
                web.tap(report)
                web.waitFor("#report-description", 30_000.0)
                page.keyboard().press("Escape")
                gone(web, "#report-description", "the closed problem report")
                gone(web, report, "Table actions, closed with its problem report")
                back(web)
                val faults = web.faults()
                assertTrue(faults.isEmpty(), "The page reported faults:\n${faults.joinToString("\n")}")
            } catch (failure: Throwable) {
                web.evidence("WebSemanticsMirrorTest")
                throw failure
            }
        }
    }

    /** Waits up to 5 s for the mirror to drop what [selector] finds ([what] for the message), as the screen did at once. */
    private fun gone(web: E2eBrowser, selector: String, what: String) {
        val deadline = System.nanoTime() + 5_000_000_000L
        while (web.shows(selector)) {
            check(System.nanoTime() < deadline) { "$what still in the page's semantics mirror:\n${web.semantics()}" }
            Thread.sleep(100)
        }
    }

    /** Waits up to 5 s for the table's own controls (the opening's Keep) to be mirrored again. */
    private fun back(web: E2eBrowser) {
        val deadline = System.nanoTime() + 5_000_000_000L
        while (!web.showsControl("Keep")) {
            check(System.nanoTime() < deadline) { "the table's controls not in the page's semantics mirror again:\n${web.semantics()}" }
            Thread.sleep(100)
        }
    }
}

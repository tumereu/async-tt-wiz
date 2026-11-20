package org.asyncttwiz.e2e

import com.microsoft.playwright.Page
import org.asyncttwiz.protocol.DraftMode
import org.asyncttwiz.protocol.DraftSettings
import org.junit.Test
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Owner playtest 2026-11-19 (N10, on 0.2.1): the Rotisserie draft's search field "displays horiz scrollbar when empty and
 * focused, ugly". Not reproduced in Chrome (phone and desktop layouts: no element of the page, Compose's hidden text input
 * included, draws a scrollbar); kept as the guard of it on the web. The field is now in the pool's filter popup (DRF-250).
 */
class WebRotisserieSearchTest {
    // DRF-251, WEB-120: Alice opens her Rotisserie draft in the browser, clicks the filter button in the pool's corner and taps the
    // popup's empty field: nothing on the page shows a scrollbar there (the browser's own text box behind the field included); Esc
    // closes the popup and the button stays
    @Test fun theEmptyFocusedSearchFieldShowsNoScrollbar() {
        val (alice, bob) = ServerSetup.fresh()
        val pod = ServerSetup.draftingPod(alice, bob, "E2E web rotisserie ${UUID.randomUUID().toString().take(6)}",
            settings = DraftSettings(mode = DraftMode.ROTISSERIE, packsPerPlayer = 3, cardsPerPack = 2, cardsPerPlayer = 5, poolPerPlayer = 6))
        for ((width, height) in listOf(412 to 860, 1440 to 900)) E2eBrowser(width, height).use { web ->
            try {
                web.signIn(alice, "/pods/$pod")
                web.waitFor("#pod-rotisserie-filter", 120_000.0)
                web.tap("#pod-rotisserie-filter")
                web.waitFor("#pod-rotisserie-filter-field")
                web.tap("#pod-rotisserie-filter-field")
                web.page.waitForFunction(FOCUSED, null, Page.WaitForFunctionOptions().setTimeout(30_000.0))
                val scrollbars = web.page.evaluate(SCROLLBARS).toString()
                assertEquals("[]", scrollbars, "elements with a scrollbar on the focused field's page at $width × $height")
                web.page.keyboard().press("Escape")
                web.page.locator("#pod-rotisserie-filter-field").waitFor(com.microsoft.playwright.Locator.WaitForOptions()
                    .setState(com.microsoft.playwright.options.WaitForSelectorState.DETACHED).setTimeout(30_000.0))
                assertTrue(web.shows("#pod-rotisserie-filter"), "the button stays at $width × $height")
            } catch (failure: Throwable) {
                web.evidence("WebRotisserieSearchTest-$width")
                throw failure
            }
        }
    }

    private companion object {
        /** The element with the keyboard's focus, looking into shadow roots. */
        const val ACTIVE_JS = "let a = document.activeElement; while (a && a.shadowRoot && a.shadowRoot.activeElement) a = a.shadowRoot.activeElement;"
        const val FOCUSED = "() => { $ACTIVE_JS return a && a.tagName !== 'BODY' && a.tagName !== 'CANVAS'; }"

        /** Every element of the page (shadow roots included) that draws a scrollbar: one that takes room (a classic scrollbar),
         * or a box that scrolls with its content overflowing it. */
        const val SCROLLBARS = """() => {
            const all = [];
            const walk = (root) => root.querySelectorAll('*').forEach(e => { all.push(e); if (e.shadowRoot) walk(e.shadowRoot); });
            walk(document);
            return all.map(e => {
                const s = getComputedStyle(e);
                const bar = (e.offsetWidth - e.clientWidth - parseFloat(s.borderLeftWidth) - parseFloat(s.borderRightWidth)) +
                    (e.offsetHeight - e.clientHeight - parseFloat(s.borderTopWidth) - parseFloat(s.borderBottomWidth));
                const scrolls = (v) => v === 'auto' || v === 'scroll';
                const over = (scrolls(s.overflowX) && e.scrollWidth > e.clientWidth) || (scrolls(s.overflowY) && e.scrollHeight > e.clientHeight)
                    || s.overflowX === 'scroll' || s.overflowY === 'scroll';
                return (bar > 0 || over) && s.display !== 'none' && s.visibility !== 'hidden'
                    ? e.tagName + '.' + e.className + ' ' + e.offsetWidth + 'x' + e.offsetHeight + ' ' + s.overflowX + '/' + s.overflowY : null;
            }).filter(x => x);
        }"""
    }
}

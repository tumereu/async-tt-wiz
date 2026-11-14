package org.asyncttwiz.e2e

import com.microsoft.playwright.Keyboard
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.asyncttwiz.android.HOTKEY_SHEET_TAG
import org.junit.Test

/**
 * Typing never acts as a hotkey (owner's report, desktop web, 2026-11-14: "?" showed and hid the list of keys while he wrote
 * a problem report): Alice, in the desktop layout of a real browser ([E2eBrowser], 1440 × 900), types "?" and the table's
 * hotkey letters and Space into the problem report and the table's chat. Every key goes only to the field: the text is
 * there, the list of keys never opens, and nothing happens in the game. Not a Robolectric test: whether a text field has
 * the keyboard is the browser's to say.
 */
class WebTypingKeysTest {
    // WEB-031, WEB-034: "?", Space (the main button: the opening's Keep) and T F S G X M C P (Shift+G, Shift+X too) typed
    // into the problem report and the chat are only text
    @Test fun keysTypedIntoAFieldAreOnlyText() {
        val (alice, bob) = ServerSetup.fresh()
        val started = ServerSetup.prepare(alice, bob, TestDeck.thirds(), TestDeck.thirds(), name = "Typing game").ready()
        E2eBrowser(1440, 900).use { web ->
            val page = web.page
            try {
                web.signIn(alice)
                // The problem report in Settings
                web.waitFor("#menu-SETTINGS")
                web.tap("#menu-SETTINGS")
                web.waitFor("#report-problem")
                web.tap("#report-problem")
                web.waitFor("#report-description")
                web.tap("#report-description")
                page.waitForTimeout(500.0)
                typeInto(web, "#report-description", "? tfsgxmcp GX ?", "her problem report")
                // The table's chat, opened ready to type by C
                val before = ServerSetup.head(alice, started.id).engine?.localHeads?.get(alice.id)
                page.navigate("${E2eStack.origin}/games/${started.id}")
                web.waitFor("#main-button", 120_000.0)
                val deadline = System.nanoTime() + 60_000_000_000L
                while (!web.showsControl("Keep")) {
                    check(System.nanoTime() < deadline) { "no Keep among the quick actions; the main button: ${web.mainWords()}" }
                    Thread.sleep(200)
                }
                val chat = "[aria-label='Chat message draft']"
                page.keyboard().press("c")
                web.waitFor(chat)
                web.tap(chat)
                page.waitForTimeout(500.0)
                typeInto(web, chat, "? good game tfsgxmcp GX ?", "her chat draft")
                assertEquals(before, ServerSetup.head(alice, started.id).engine?.localHeads?.get(alice.id),
                    "Space typed into the chat didn't keep Alice's opening hand")
                assertTrue(web.showsControl("Keep"), "the opening's Keep still waits")
                // The app log says why no key acted (the typed characters themselves never named)
                assertTrue(web.console.any { it.contains("ATW/keys: Typing: a typed character went to the text field") },
                    "the log notes the held-back keys")
                val faults = web.faults()
                assertTrue(faults.isEmpty(), "The page reported faults:\n${faults.joinToString("\n")}")
            } catch (failure: Throwable) {
                web.evidence("WebTypingKeysTest")
                throw failure
            }
        }
    }

    /** Types [text] into the focused field [field] ([where] for the messages): its "?" first, the list of keys checked after
     * it (it opened at once before the fix), then the rest; the field holds it all and no hotkey acted. */
    private fun typeInto(web: E2eBrowser, field: String, text: String, where: String) {
        val page = web.page
        val hotkeys = web.console.count(::hotkeyLine)
        page.keyboard().type(text.take(1))
        page.waitForTimeout(500.0)
        assertTrue(!web.shows("#$HOTKEY_SHEET_TAG"), "the list of keys opened at a \"?\" typed into $where")
        page.keyboard().type(text.drop(1), Keyboard.TypeOptions().setDelay(30.0))
        page.waitForTimeout(1_000.0)
        assertTrue(!web.shows("#$HOTKEY_SHEET_TAG"), "the list of keys opened while Alice typed into $where")
        assertEquals(hotkeys, web.console.count(::hotkeyLine), "no hotkey acted while she typed into $where: " +
            web.console.filter(::hotkeyLine))
        assertEquals(text, web.fieldText(field), "$where holds what she typed")
    }

    /** A console line of the app log saying a hotkey acted (the "?" sheet opening or a table, pod or editor hotkey). */
    private fun hotkeyLine(line: String) = line.contains("ATW/keys") && (line.contains("Hotkey") || line.contains("list of keys"))
}

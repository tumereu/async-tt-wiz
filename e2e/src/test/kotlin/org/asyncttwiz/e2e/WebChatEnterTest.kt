package org.asyncttwiz.e2e

import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.Test

/**
 * Enter sends a chat message in the desktop layout (owner's report, desktop web, 2026-11-16: Enter in the table's chat only
 * started a new line): Alice, in the desktop layout of a real browser ([E2eBrowser], 1440 × 900), writes in the table's chat
 * dock and in the games list's chat. Shift+Enter and Ctrl+Enter start a new line, Enter sends. Not a Robolectric test: the
 * keys are the browser's.
 */
class WebChatEnterTest {
    // WEB-017, LOB-068, LOB-148: in the dock Shift+Enter and Ctrl+Enter break the line and Enter sends the three lines; the same
    // in the games list's chat (opened by a right-click on the game's row)
    @Test fun enterSendsAndShiftOrCtrlEnterBreaksTheLine() {
        val (alice, bob) = ServerSetup.fresh()
        val started = ServerSetup.prepare(alice, bob, TestDeck.thirds(), TestDeck.thirds(), name = "Enter game").ready()
        E2eBrowser(1440, 900).use { web ->
            val page = web.page
            try {
                web.signIn(alice, "/games/${started.id}")
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
                writeAndSend(web, chat, "Dock", "the dock")
                assertTrue(web.showsControl("Keep"), "Enter in the chat didn't keep Alice's opening hand")

                // The games list's chat: Esc closes the dock's field, Esc again leaves the table (WEB-004)
                page.keyboard().press("Escape")
                page.waitForTimeout(300.0)
                if (web.path() != "/games") page.keyboard().press("Escape")
                web.waitForText("Enter game")
                val row = web.box("text=Enter game")
                page.mouse().click(row.x + row.width / 2, row.y + row.height / 2,
                    com.microsoft.playwright.Mouse.ClickOptions().setButton(com.microsoft.playwright.options.MouseButton.RIGHT))
                web.waitFor("div[role=button]:text-is('Chat')", 30_000.0)
                web.tap("div[role=button]:text-is('Chat')")
                web.waitFor(chat, 30_000.0)
                web.tap(chat)
                page.waitForTimeout(500.0)
                writeAndSend(web, chat, "List", "the games list's chat")
                val faults = web.faults()
                assertTrue(faults.isEmpty(), "The page reported faults:\n${faults.joinToString("\n")}")
            } catch (failure: Throwable) {
                web.evidence("WebChatEnterTest")
                throw failure
            }
        }
    }

    /** In the focused chat field [field] ([where] for the messages): three lines, the first two ended by Shift+Enter and
     * Ctrl+Enter, stay in the draft; Enter sends them: the draft empties and the message shows. The page's semantics mirror
     * leaves a field's line breaks out, so the app log tells the lines (their count, never the text). */
    private fun writeAndSend(web: E2eBrowser, field: String, word: String, where: String) {
        val page = web.page
        val before = web.console.size
        fun logged(line: String) = web.console.drop(before).any { it.contains("ATW/chat: $line") }
        page.keyboard().type("$word one")
        page.keyboard().press("Shift+Enter")
        page.keyboard().type("$word two")
        page.keyboard().press("Control+Enter")
        page.keyboard().type("$word three")
        page.waitForTimeout(500.0)
        assertTrue(logged("Shift+Enter broke the line of the draft (2 lines)"), "Shift+Enter broke the line in $where")
        assertTrue(logged("Ctrl+Enter broke the line of the draft (3 lines)"), "Ctrl+Enter broke the line in $where")
        assertEquals("$word one$word two$word three", web.fieldText(field), "the draft in $where holds all she typed")
        page.keyboard().press("Enter")
        val deadline = System.nanoTime() + 15_000_000_000L
        while (web.fieldText(field).isNotEmpty() || !web.shows("text=$word three")) {
            check(System.nanoTime() < deadline) { "Enter didn't send the message in $where: the draft holds \"${web.fieldText(field)}\"" }
            Thread.sleep(200)
        }
        assertTrue(logged("Enter sent the message (3 lines)"), "Enter sent the three lines in $where")
    }
}

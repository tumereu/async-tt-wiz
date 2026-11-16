package org.asyncttwiz.e2e

import com.microsoft.playwright.Page
import com.microsoft.playwright.options.Clip
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.Test

/**
 * Emojis in the web client's chat (owner, desktop playtest 2026-11-16: "Emojis sent in chat don't seem to work on
 * desktop?"): the browser's Compose draws its own text with only the fonts the page gives it, and the colour emoji font it
 * had held only the pick mark's 🔥, so every other emoji drew as an empty box. In a real browser ([E2eBrowser], the desktop
 * layout at 1440 × 900) Alice reads Bob's message with emojis in colour, and writes one back that reaches Bob's phone whole.
 */
class WebEmojiTest : E2e() {
    // WEB-062, LOB-136: Alice's typed "Thanks 😀👍" is in her draft whole, goes with Send and reaches Bob's phone as typed;
    // Bob's "Good luck 😀🎉" and her own message show their emojis in colour in her chat (yellow faces where a plain message
    // of his has none)
    @Test fun emojisGoWholeAndShowInColour() {
        startGame(alicePhone = false)
        bob.openGame(game, this)
        ServerSetup.gameChat(bob.account, game, "Plain words")
        ServerSetup.gameChat(bob.account, game, "Good luck 😀🎉")
        E2eBrowser(width = 1440, height = 900).use { web ->
            val page = web.page
            try {
                web.signIn(aliceAccount, "/games/${game.id}")
                waitUntil("Keep among Alice's quick actions in her browser", timeoutSeconds = 180) { web.showsControl("Keep") }
                val chat = "[aria-label='Chat message draft']"
                page.keyboard().press("c")
                web.waitFor(chat)
                web.waitForText("Good luck")
                // Her own message, typed as the browser hands an emoji to the page (an inserted text, as the emoji picker does)
                web.tap(chat)
                page.waitForTimeout(300.0)
                page.keyboard().type("Thanks 😀👍")
                page.waitForTimeout(500.0)
                assertEquals("Thanks 😀👍", web.fieldText(chat), "her draft holds the emojis whole")
                web.tap(web.control("Send"))
                waitUntil("Alice's message on Bob's phone as typed", along = listOf(bob)) {
                    bob.lobby.state.chat.messages.any { it.text == "Thanks 😀👍" }
                }
                web.waitForText("Thanks")
                val plain = yellow(page, web.box("text=Plain words"))
                waitUntil("Bob's emojis in colour in Alice's chat (yellow pixels: plain message $plain)", timeoutSeconds = 30) {
                    yellow(page, web.box("text=Good luck")) >= plain + YELLOW_FACE
                }
                waitUntil("Alice's emojis in colour in her chat", timeoutSeconds = 30) {
                    yellow(page, web.box("text=Thanks")) >= plain + YELLOW_FACE
                }
                // The app log says when the emoji font came and that the page's text was laid out again with it
                assertTrue(web.console.any { it.contains("Emoji font registered") }, "the log notes the emoji font")
                val faults = web.faults()
                assertTrue(faults.isEmpty(), "The page reported faults:\n${faults.joinToString("\n")}")
            } catch (failure: Throwable) {
                web.evidence("WebEmojiTest")
                throw failure
            }
        }
    }

    /** How many of the page's pixels in [box] are an emoji face's yellow (bright red and green, little blue): none in
     * the app's own text and panels, hundreds in a colour 😀 at chat size. */
    private fun yellow(page: Page, box: com.microsoft.playwright.options.BoundingBox): Int {
        val shot = page.screenshot(Page.ScreenshotOptions().setClip(Clip(box.x, box.y, box.width, box.height)))
        val picture = javax.imageio.ImageIO.read(java.io.ByteArrayInputStream(shot))
        var count = 0
        for (y in 0 until picture.height) for (x in 0 until picture.width) {
            val pixel = picture.getRGB(x, y)
            val red = (pixel shr 16) and 255; val green = (pixel shr 8) and 255; val blue = pixel and 255
            if (red > 200 && green > 150 && blue < 110 && red - blue > 120) count++
        }
        return count
    }

    private companion object {
        /** More yellow pixels than a plain message's: a fraction of one colour face at chat size. */
        const val YELLOW_FACE = 20
    }
}

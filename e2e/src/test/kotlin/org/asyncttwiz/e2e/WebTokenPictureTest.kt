package org.asyncttwiz.e2e

import com.microsoft.playwright.Page
import com.microsoft.playwright.Route
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.util.concurrent.CopyOnWriteArrayList
import java.util.function.Predicate
import javax.imageio.ImageIO
import org.junit.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Owner, desktop playtest 2026-11-15 (problem report VSVLSK): "Web UI token arts don't seem to load correctly, they're just
 * blank art." On the phone a token Automatum makes shows the best matching token picture of the card records (SYS-072); in
 * the browser the same token was drawn with the plain frame.
 */
class WebTokenPictureTest : E2e() {
    // SYS-072, WEB-026: Alice casts an Ember Herald on her phone, which makes a 1/1 red Elemental token. She opens the game in
    // her browser (desktop layout), whose card records find the test cards' Elemental token picture for it: the browser asks
    // for that picture, draws it on the token on the table, and resting the pointer on the token shows it large in the quick
    // look.
    // Scryfall's answers are the test's own: the token's picture is a plain magenta one, every other card has none
    @Test fun aTokensPictureLoadsOnTheWebTable() {
        startGame(TestDeck.thirds(TestCards.herald), deal = { a, _ -> "Mountain" in hand(a) && "Ember Herald" in hand(a) })
        bothOpen()
        bothKeep(bobAway = false)
        alice.playFromHand("Mountain", this)
        waitUntil("the Mountain on the battlefield", along = listOf(bob)) { alice.field("Mountain").isNotEmpty() && alice.inOwnMain() }
        alice.playFromHand("Ember Herald", this)
        fun token() = alice.shown.board.let { play -> play.board.cards.firstOrNull { it.token }?.let { card ->
            play.definitions.single { it.digest == card.definition }.definition
        } }
        waitUntil("the Herald's token on Alice's battlefield", along = listOf(bob)) { token() != null && alice.inOwnMain() && alice.settled(this) }
        // The phone here has no card records of its own (the test phones' token pictures stay plain): it only makes the token
        val made = assertNotNull(token())
        E2eBrowser(width = 1440, height = 900).use { web ->
            try {
                val asked = CopyOnWriteArrayList<String>()
                web.page.route(Predicate { it.startsWith("https://api.scryfall.com/") || it.startsWith("https://cards.scryfall.io/") }) {
                    route -> answer(route, asked)
                }
                web.signIn(aliceAccount, "/games/${game.id}")
                web.waitFor("#main-button", 180_000.0)
                val label = "div[aria-label^='${made.name}, owner ${alice.name}, ']"
                waitUntil("the token on Alice's web table", timeoutSeconds = 120) { web.shows(label) }
                waitUntil("the browser asking for the token's picture (asked: $asked)", timeoutSeconds = 60) {
                    asked.any { ELEMENTAL in it && it.startsWith("https://cards.scryfall.io/") }
                }
                val box = web.box(label)
                waitUntil("the token drawn with its picture", timeoutSeconds = 30) { magenta(web.page, box) > .25 }
                // WEB-026: the quick look shows the token's picture large beside it
                val before = magentaOutside(web.page, box)
                web.cursorAt(box.x + box.width / 2, box.y + box.height / 2)
                waitUntil("the quick look showing the token's picture", timeoutSeconds = 30) {
                    magentaOutside(web.page, box) > before + box.width * box.height
                }
                val faults = web.faults()
                assertTrue(faults.isEmpty(), "The page reported faults:\n${faults.joinToString("\n")}")
            } catch (failure: Throwable) {
                web.evidence("WebTokenPictureTest")
                throw failure
            }
        }
    }

    /** The test's Scryfall: card data and a magenta picture for the Elemental token, nothing for any other card. */
    private fun answer(route: Route, asked: MutableList<String>) {
        val url = route.request().url()
        asked += url
        val cors = mapOf("Access-Control-Allow-Origin" to "*")
        when {
            url == "https://api.scryfall.com/cards/$ELEMENTAL" -> route.fulfill(Route.FulfillOptions().setStatus(200)
                .setHeaders(cors + ("Content-Type" to "application/json")).setBody(
                    """{"object":"card","id":"$ELEMENTAL","name":"Elemental","layout":"token","image_uris":{""" +
                        listOf("small", "normal", "large", "png", "art_crop").joinToString(",") {
                            "\"$it\":\"https://cards.scryfall.io/$it/front/6/3/$ELEMENTAL.png\""
                        } + "}}"))
            url.startsWith("https://cards.scryfall.io/") && ELEMENTAL in url -> route.fulfill(Route.FulfillOptions().setStatus(200)
                .setHeaders(cors + ("Content-Type" to "image/png")).setBodyBytes(PICTURE))
            else -> route.fulfill(Route.FulfillOptions().setStatus(404).setHeaders(cors + ("Content-Type" to "application/json"))
                .setBody("""{"object":"error","status":404}"""))
        }
    }

    private fun screenshot(page: Page): BufferedImage = ImageIO.read(page.screenshot().inputStream())

    private fun isMagenta(rgb: Int): Boolean {
        val red = (rgb shr 16) and 255; val green = (rgb shr 8) and 255; val blue = rgb and 255
        return red > 200 && blue > 200 && green < 60
    }

    /** The share of the page's pixels inside [box] that are the token picture's magenta. */
    private fun magenta(page: Page, box: com.microsoft.playwright.options.BoundingBox): Double {
        val image = screenshot(page)
        var all = 0; var hits = 0
        for (x in box.x.toInt() until (box.x + box.width).toInt()) for (y in box.y.toInt() until (box.y + box.height).toInt()) {
            if (x !in 0 until image.width || y !in 0 until image.height) continue
            all++
            if (isMagenta(image.getRGB(x, y))) hits++
        }
        return if (all == 0) 0.0 else hits.toDouble() / all
    }

    /** How many of the page's pixels outside [box] are the token picture's magenta. */
    private fun magentaOutside(page: Page, box: com.microsoft.playwright.options.BoundingBox): Int {
        val image = screenshot(page)
        var hits = 0
        for (x in 0 until image.width) for (y in 0 until image.height) {
            if (x >= box.x && x < box.x + box.width && y >= box.y && y < box.y + box.height) continue
            if (isMagenta(image.getRGB(x, y))) hits++
        }
        return hits
    }

    private companion object {
        /** The test cards' Elemental token (TST T1, tests/fixtures/card-catalog), the best match for Ember Herald's token. */
        const val ELEMENTAL = "6312ffc2-54f2-5922-b5ac-a64cd065503c"
        /** A plain magenta card picture. */
        val PICTURE: ByteArray = BufferedImage(488, 680, BufferedImage.TYPE_INT_RGB).let { image ->
            val graphics = image.createGraphics()
            graphics.color = java.awt.Color(255, 0, 255)
            graphics.fillRect(0, 0, image.width, image.height)
            graphics.dispose()
            ByteArrayOutputStream().also { ImageIO.write(image, "png", it) }.toByteArray()
        }
    }
}

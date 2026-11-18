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
                // Resting on it opens its tooltip, a layer the page's mirror shows alone while it is open (WEB-059): the mouse
                // moves off it, over the empty battlefield, so the tooltip closes and the table is mirrored again
                web.cursorAt(560.0, 300.0)
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

    // WEB-018, WEB-019, WEB-080, WEB-082, WEB-020, WEB-058 (found 2026-11-14: the mirror put the 1.3× top row, action area and panels where they
    // would be at 1×, Table actions at x ≈ 1052 instead of about 1399): on Alice's desktop table in her browser the mirror
    // places a top-row control, an action-area button and a panel's card where they are drawn; the hand shows over them and
    // taps through the mirror hit them: the dock folds away and back, two mulligans open the Keep / Bottom overlay, a card
    // tapped there goes to Bottom and the main button's OK closes it
    @Test fun theMirrorPlacesTheLargerControlsWhereTheyAreDrawn() {
        startGame(alicePhone = false)
        bob.openGame(game, this)
        bob.tap("Keep")
        E2eBrowser(width = 1440, height = 900).use { web ->
            try {
                web.signIn(aliceAccount, "/games/${game.id}")
                waitUntil("Keep among Alice's quick actions in her browser", timeoutSeconds = 180) { web.showsControl("Keep") }
                // The top row (WEB-020), 1.3× its 56 tall: Table actions at its right end
                val actions = web.box(web.control("Table actions"))
                assertTrue(actions.x + actions.width >= 1440 - 16 && actions.x + actions.width <= 1440.5, "Table actions at the right end: ${show(actions)}")
                assertEquals(48 * 1.3, actions.height, 2.0, "its 48 square 1.3× as large: ${show(actions)}")
                assertEquals(56 * 1.3 / 2, actions.y + actions.height / 2, 2.0, "in the middle of the 1.3× top row: ${show(actions)}")
                assertEquals("pointer", web.cursorOver("#dock-button"), "the hand over the dock button")
                web.tap("#dock-button")
                waitUntil("the dock folded away") { !web.shows("#desktop-dock") }
                web.tap("#dock-button")
                waitUntil("the dock back") { web.shows("#desktop-dock") }
                // The action area (WEB-018): its quick actions inside its 320 wide corner
                val mulligan = web.box(web.control("Mulligan"))
                assertTrue(mulligan.x >= 1440 - 320 && mulligan.x + mulligan.width <= 1440.5 && mulligan.y >= 900 - 200 &&
                    mulligan.y + mulligan.height <= 900.5, "Mulligan in the action area: ${show(mulligan)}")
                assertEquals("pointer", web.cursorOver(web.control("Mulligan")), "the hand over Mulligan")
                val panel = "div[aria-label='Keep or bottom']"
                for (round in 1..2) {
                    val before = ServerSetup.head(aliceAccount, game.id).engine?.localHeads?.get(aliceAccount.id)
                    web.tap(web.control("Mulligan"))
                    waitUntil("Alice's mulligan $round on the server", timeoutSeconds = 60) {
                        ServerSetup.head(aliceAccount, game.id).engine?.localHeads?.get(aliceAccount.id) != before
                    }
                }
                waitUntil("the Keep / Bottom overlay", timeoutSeconds = 60) { web.shows(panel) }
                // A panel (WEB-019, WEB-080, WEB-082): the centred overlay, 560 wide, over the battlefields left of the dock; measured
                // once its opening animation has ended and the mirror has copied its last frame ([E2eBrowser.settledBox])
                val overlay = web.settledBox(panel)
                assertEquals((1440 - 320) / 2.0, overlay.x + overlay.width / 2, 4.0, "the overlay centred on the battlefields: ${show(overlay)}")
                // 560 wide at 1.3×, or narrower where it is drawn smaller to fit the height (not below 1×; the hand dealt decides)
                assertTrue(overlay.width <= 563 && overlay.width >= 560 / 1.3 - 3, "the overlay's width: ${show(overlay)}")
                // A card of her hand (the test deck's Mountains, Storm Sparks and Ember Cubs) in the overlay's Keep area
                val named = ":is([aria-label='Mountain'], [aria-label='Storm Spark'], [aria-label='Ember Cub'])"
                val card = "div[aria-label='Keep area'] $named"
                val cardBox = web.box(card)
                assertTrue(cardBox.x >= overlay.x && cardBox.x + cardBox.width <= overlay.x + overlay.width + .5 &&
                    cardBox.y >= overlay.y && cardBox.y + cardBox.height <= overlay.y + overlay.height + .5, "the card in the overlay: ${show(cardBox)}, ${show(overlay)}")
                assertEquals("pointer", web.cursorOver(card), "the hand over the panel's card")
                web.tap(card)
                waitUntil("the card in Bottom") { web.shows("div[aria-label='Bottom area'] $named") }
                web.tap("#main-button")
                waitUntil("the overlay closed by OK") { !web.shows(panel) }
                val faults = web.faults()
                assertTrue(faults.isEmpty(), "The page reported faults:\n${faults.joinToString("\n")}")
            } catch (failure: Throwable) {
                web.evidence("WebHoverPointerTest")
                throw failure
            }
        }
    }

    // WEB-019, WEB-058 (found 2026-11-15: the mirror had the Keep / Bottom overlay drawn at 1.2× with its 1.25× layout, 11 px
    // left of the middle, its last card on a second row, in about one run of three): in a window 1440 × 860 the overlay after
    // two mulligans is too tall at 1.3× (her seven cards on two rows) and is drawn a step smaller each frame until it fits.
    // Once the mirror has kept its place for longer than it waits to copy the screen, it has the overlay where the page's
    // picture shows the white panel: in the middle of the battlefields, its room's width wide and shorter than at the size it
    // last filled, a card of the Keep area inside it
    @Test fun aFittedOverlayIsMirroredWhereItIsDrawn() {
        startGame(alicePhone = false)
        bob.openGame(game, this)
        bob.tap("Keep")
        E2eBrowser(width = 1440, height = 860).use { web ->
            try {
                web.signIn(aliceAccount, "/games/${game.id}")
                waitUntil("Keep among Alice's quick actions in her browser", timeoutSeconds = 180) { web.showsControl("Keep") }
                val panel = "div[aria-label='Keep or bottom']"
                // The mouse rests on Mulligan meanwhile, as in the run where it was found
                assertEquals("pointer", web.cursorOver(web.control("Mulligan")), "the hand over Mulligan")
                for (round in 1..2) {
                    val before = ServerSetup.head(aliceAccount, game.id).engine?.localHeads?.get(aliceAccount.id)
                    web.tap(web.control("Mulligan"))
                    waitUntil("Alice's mulligan $round on the server", timeoutSeconds = 60) {
                        ServerSetup.head(aliceAccount, game.id).engine?.localHeads?.get(aliceAccount.id) != before
                    }
                }
                waitUntil("the Keep / Bottom overlay", timeoutSeconds = 60) { web.shows(panel) }
                // Drawn smaller than 1.3× to fit: "Desktop panel: 396 px fills its 396 at 1.3×, drawn at 1.25×", and so on
                val fills = Regex("""Desktop panel: \d+ px fills its (\d+) at ([\d.]+)×, drawn at ([\d.]+)×""")
                waitUntil("the overlay drawn smaller to fit") { web.shows(panel) && web.console.any { fills.containsMatchIn(it) } }
                val overlay = web.settledBox(panel)
                val filled = web.console.mapNotNull { fills.find(it) }.last().groupValues.let { it[1].toDouble() * it[2].toDouble() }
                assertEquals((1440 - 320) / 2.0, overlay.x + overlay.width / 2, 2.0, "the overlay centred on the battlefields: ${show(overlay)}")
                assertEquals(560.0, overlay.width, 3.0, "the overlay its room's width: ${show(overlay)}")
                assertTrue(overlay.height < filled - 2, "the overlay at the size it fits, shorter than the $filled it filled: ${show(overlay)}")
                val drawn = drawnPanel(web)
                assertTrue(kotlin.math.abs(drawn.x - overlay.x) <= 2 && kotlin.math.abs(drawn.width - overlay.width) <= 3 &&
                    kotlin.math.abs(drawn.y - overlay.y) <= 4 && kotlin.math.abs(drawn.height - overlay.height) <= 5,
                    "the overlay where it is drawn: mirror ${show(overlay)}, drawn ${show(drawn)}")
                val card = "div[aria-label='Keep area'] :is([aria-label='Mountain'], [aria-label='Storm Spark'], [aria-label='Ember Cub'])"
                val cardBox = web.box(card)
                assertTrue(cardBox.x >= drawn.x && cardBox.x + cardBox.width <= drawn.x + drawn.width + .5 &&
                    cardBox.y >= drawn.y && cardBox.y + cardBox.height <= drawn.y + drawn.height + .5,
                    "the card in the drawn overlay: ${show(cardBox)}, ${show(drawn)}")
                val faults = web.faults()
                assertTrue(faults.isEmpty(), "The page reported faults:\n${faults.joinToString("\n")}")
            } catch (failure: Throwable) {
                web.evidence("WebHoverPointerTest")
                throw failure
            }
        }
    }

    /** The white panel of a centred overlay as the page's picture shows it: the box of the pure white runs at least 12 px
     * long over the dimmed battlefields (left of the 320 wide dock, below the top row), which nothing else draws there. */
    private fun drawnPanel(web: E2eBrowser): com.microsoft.playwright.options.BoundingBox {
        val shot = javax.imageio.ImageIO.read(java.io.ByteArrayInputStream(web.page.screenshot()))
        var left = Int.MAX_VALUE; var right = -1; var top = Int.MAX_VALUE; var bottom = -1
        for (y in 74 until shot.height) {
            var run = 0
            for (x in 0 until minOf(shot.width - 320, shot.width)) {
                run = if (shot.getRGB(x, y) and 0xffffff == 0xffffff) run + 1 else 0
                if (run >= 12) { left = minOf(left, x - 11); right = maxOf(right, x); top = minOf(top, y); bottom = maxOf(bottom, y) }
            }
        }
        check(right >= 0) { "no white panel in the page's picture" }
        return com.microsoft.playwright.options.BoundingBox().apply {
            x = left.toDouble(); y = top.toDouble(); width = (right - left + 1).toDouble(); height = (bottom - top + 1).toDouble()
        }
    }

    private fun show(box: com.microsoft.playwright.options.BoundingBox) = "(${box.x}, ${box.y}) ${box.width} × ${box.height}"
}

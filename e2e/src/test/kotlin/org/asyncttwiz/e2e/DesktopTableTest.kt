package org.asyncttwiz.e2e

import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.performScrollTo
import arcanum.api.TurnStep
import kotlinx.coroutines.runBlocking
import org.asyncttwiz.android.DESKTOP_UNDO_TAG
import org.asyncttwiz.android.DOCK_STACK_TAG
import org.asyncttwiz.android.HOTKEY_SHEET_TAG
import org.asyncttwiz.android.HotkeyAction
import org.asyncttwiz.android.hotkeyRowTag
import org.asyncttwiz.protocol.game.AssumeResolves
import org.asyncttwiz.protocol.game.PlaySettings
import org.asyncttwiz.protocol.game.ResponseMode
import org.junit.Test
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The desktop table (docs/specs/web-client.md §5–§7, step 5): Alice plays in the desktop layout at 1440 × 900 with the mouse
 * and the keyboard, Bob on his phone's layout beside her.
 */
@Config(qualifiers = "w1440dp-h900dp-land-mdpi")
class DesktopTableTest : E2e() {
    /** Alice (desktop layout) holds a Mountain and an Ember Cub; Bob a Mountain. Both have opened the game. [bobWaits]: Bob
     * plays in Control and Alice assumes none of his passes, so what she casts waits on the Stack until he passes. */
    private fun start(bobWaits: Boolean = false) {
        startGame(aliceDesktop = true, aliceSettings = PlaySettings().assuming(AssumeResolves.Never).takeIf { bobWaits },
            bobSettings = PlaySettings(mode = ResponseMode.FullControl).takeIf { bobWaits },
            deal = { a, b -> "Mountain" in hand(a) && "Ember Cub" in hand(a) && "Mountain" in hand(b) })
        bothOpen()
        assertTrue(alice.shows(hasTestTag("phase-track")), "Alice's table in the desktop layout: ${alice.screen()}")
    }

    /** WEB-023, ZON-079: a click raises Alice's hand card [name], a click on the raised card plays it. */
    private fun clickFromHand(name: String) {
        waitUntil("Alice's game settled") { alice.settled(this) }
        alice.handCard(name).performMouseInput { click() }
        waitUntil("$name raised in Alice's hand") { alice.raisedInHand() == name && alice.settled(this) }
        alice.handCard(name).performMouseInput { click() }
    }

    /** TRN-062: Alice keeps with a click on the Keep quick action; both go on to her Main 1. */
    private fun keep() {
        alice.control("Keep").performMouseInput { click() }
        waitUntil("Alice's Main 1", along = listOf(bob)) { alice.inOwnMain() }
    }

    private fun sheetShown() = alice.shows(hasTestTag(HOTKEY_SHEET_TAG))

    // WEB-034, WEB-023, WEB-024, WEB-031, WEB-018: "?" lists the keys and closes again; Alice keeps by a click, plays a Mountain and
    // casts an Ember Cub by clicks, holds Alt over the Cub to inspect it, Space goes on from her Main 1 and Shift+Space ends
    // her turn
    @Test fun aTurnWithTheMouseAndTheKeys() {
        start()
        alice.pressKey("?", shift = true)
        waitUntil("the list of keys over Alice's table") { sheetShown() }
        assertTrue(alice.showsText("Ctrl+Z") && alice.showsText("Shift+Space"), "her keys listed: ${alice.screen()}")
        alice.pressKey("?", shift = true)
        waitUntil("the list closed") { !sheetShown() }
        keep()
        // WEB-018 (owner, desktop playtest 2026-11-14): under the quick actions Undo is a third of the row, the main button the
        // other two thirds, lined up with no room between them
        waitUntil("Alice's main button") { alice.settled(this) && alice.shows(hasTestTag("main-button")) }
        val undo = alice.tagged(DESKTOP_UNDO_TAG).fetchSemanticsNode().boundsInRoot
        val main = alice.tagged("main-button").fetchSemanticsNode().boundsInRoot
        val row = main.right - undo.left
        assertEquals(row / 3, undo.width, 2f, "Undo a third of the row: $undo, $main")
        assertEquals(row * 2 / 3, main.width, 2f, "the main button two thirds: $undo, $main")
        assertTrue(main.left - undo.right in 0f..4f && kotlin.math.abs(main.top - undo.top) <= 1f, "lined up, no room between: $undo, $main")
        clickFromHand("Mountain")
        waitUntil("the Mountain on Alice's battlefield", along = listOf(bob)) {
            alice.field("Mountain").isNotEmpty() && alice.showsOnBattlefield("Mountain") && alice.inOwnMain()
        }
        val cub = atw(alice.hand("Ember Cub").first())
        clickFromHand("Ember Cub")
        waitUntil("the Cub on Alice's battlefield", along = listOf(bob)) {
            cub in alice.field("Ember Cub").map(::atw) && alice.showsOnBattlefield("Ember Cub") && alice.inOwnMain() && alice.settled(this)
        }
        assertTrue(!alice.refusalSeen, "nothing refused")
        // WEB-024: Alt held over the Cub inspects it; released after the tap's time, the Inspect closes
        alice.battlefieldCard("Ember Cub").performMouseInput { moveTo(center) }
        run()
        assertTrue(alice.key("Alt", true, at = 100_000), "the finger key taken")
        waitUntil("the Cub inspected") { alice.shows(hasTestTag("card-inspection")) }
        alice.key("Alt", false, at = 101_000)
        waitUntil("the Inspect closed") { !alice.shows(hasTestTag("card-inspection")) }
        // WEB-031: Space is a tap on the main button
        val words = alice.mainWords()
        alice.pastMainGuard()
        alice.pressKey(" ")
        waitUntil("Alice past her Main 1 after Space (the main button said $words)") {
            alice.state.turn.step != TurnStep.PrecombatMain || alice.state.turn.active != alice.me
        }
        // Shift+Space is its hold: End turn
        waitUntil("Alice's main button") { alice.settled(this) && alice.stepArrow() != null || alice.state.turn.active != alice.me }
        if (alice.state.turn.active == alice.me) {
            alice.pastMainGuard()
            alice.pressKey(" ", shift = true)
        }
        waitUntil("Bob's turn after Alice's Shift+Space", along = listOf(bob)) { alice.state.turn.number >= 2 }
        assertTrue(alice.leaks.leaks.isEmpty() && bob.leaks.leaks.isEmpty())
    }

    // WEB-017, STK-007 (owner, desktop playtest 2026-11-14: "a green half-circle in the chat pane was the Stack, almost
    // completely hidden by the open chat panel"): Alice casts her Ember Cub and waits for Bob: the dock lists it whole above
    // the chat, and the table's own Stack, hidden under the open dock, shows nothing of itself at the dock's edge (its count
    // bubble peeked out there)
    @Test fun theDockListsTheStackAboveTheChatAndNothingOfItPeeksOut() {
        start(bobWaits = true)
        keep()
        clickFromHand("Mountain")
        waitUntil("the Mountain on Alice's battlefield", along = listOf(bob)) { alice.field("Mountain").isNotEmpty() && alice.inOwnMain() }
        clickFromHand("Ember Cub")
        waitUntil("the Cub on Alice's Stack, waiting for Bob") {
            alice.state.stack.isNotEmpty() && alice.shows(hasTestTag(DOCK_STACK_TAG)) && alice.showsText("Top · Ember Cub")
        }
        run()
        val dock = alice.tagged("desktop-dock").fetchSemanticsNode().boundsInRoot
        val list = alice.tagged(DOCK_STACK_TAG).fetchSemanticsNode().boundsInRoot
        val entry = alice.text("Top · Ember Cub").fetchSemanticsNode().boundsInRoot
        val chat = alice.text("Game chat").fetchSemanticsNode().boundsInRoot
        assertTrue(entry.top >= list.top && entry.bottom <= list.bottom && entry.height > 0f, "the entry whole in the dock's list: $entry in $list")
        assertTrue(list.bottom <= chat.top, "the list above the chat: $list, chat at $chat")
        assertTrue(dock.contains(list.topLeft) && dock.contains(list.bottomRight), "in the dock: $list in $dock")
        val stack = alice.node(hasContentDescription("Stack, 1 entries")).fetchSemanticsNode().boundsInRoot
        assertTrue(stack.left >= dock.left, "the table's Stack wholly under the dock, its count bubble too: $stack, dock $dock")
        assertEquals(emptyList(), alice.leaks.leaks)
    }

    /** How cyan the glow is at [x], [y] of [picture]: how far its red falls below its green and blue (0 on white or grey). */
    private fun cyanAt(picture: android.graphics.Bitmap, x: Float, y: Float): Int {
        val pixel = picture.getPixel(x.toInt(), y.toInt())
        val red = (pixel shr 16) and 255; val green = (pixel shr 8) and 255; val blue = pixel and 255
        return minOf(green, blue) - red
    }

    /** How cyan [picture] is over the middle of the card [bounds]' top edge, a tenth of its width above it: the phone's glow
     * (4 dp around its 28 dp wide cards) reaches that far from a card of any size. */
    private fun glowAbove(picture: android.graphics.Bitmap, bounds: androidx.compose.ui.geometry.Rect): Int =
        cyanAt(picture, bounds.center.x, bounds.top - bounds.width * .1f)

    // TBL-117 (owner, desktop playtest 2026-11-14: "No 'playable card' highlight for hand cards like in mobile or all cards
    // are highlight?"): at Alice's Main 1 with no land out, her Mountain may be played and glows around it as on the phone,
    // as far from the card for its size; her Ember Cub, which she can't pay for, doesn't
    @Test fun onlyThePlayableHandCardGlowsAsOnThePhone() {
        start()
        keep()
        waitUntil("Alice's game settled") { alice.settled(this) }
        repeat(3) { run(skip = java.time.Duration.ofMillis(300)) }
        val mountain = alice.handCard("Mountain").fetchSemanticsNode().boundsInWindow
        val cub = alice.handCard("Ember Cub").fetchSemanticsNode().boundsInWindow
        assertEquals(28f * 2.6f, mountain.width, 1f, "the desktop's hand card at 2.6×: $mountain")
        val picture = alice.picture()
        // The band's own colour at the same height, away from the cards
        val band = cyanAt(picture, 20f, mountain.top - mountain.width * .1f)
        assertTrue(glowAbove(picture, mountain) - band >= 10, "the Mountain glows: ${glowAbove(picture, mountain)} over $band at $mountain")
        assertTrue(glowAbove(picture, cub) - band <= 3, "the Cub doesn't glow: ${glowAbove(picture, cub)} over $band at $cub")
    }

    // WEB-035, WEB-033, WEB-034: from the list of keys Alice opens Settings and gives the main button N; the server keeps it
    // for her account; back at the table Space no longer goes on and N does
    @Test fun aReboundKeyActsOnTheTable() {
        start()
        alice.pressKey("?", shift = true)
        waitUntil("the list of keys") { sheetShown() }
        alice.tagged("hotkey-sheet-settings").performClick()
        waitUntil("Alice's keyboard settings") { alice.shows(hasTestTag("keyboard-shortcuts")) && !sheetShown() }
        alice.tagged(hotkeyRowTag(HotkeyAction.Main)).performScrollTo().performClick()
        waitUntil("the row waiting for a key") { alice.showsText("Press a key… Esc cancels") }
        alice.pressKey("n")
        waitUntil("N on the main button's row") { alice.shows(hasContentDescription("Main button: N")) }
        waitUntil("the server keeping Alice's key") {
            runBlocking { org.asyncttwiz.android.AccountApi(E2eStack.origin).use { it.accountSettings(alice.account.token) } }.keyBindings ==
                mapOf("main" to "N")
        }
        alice.activity.onBackPressedDispatcher.onBackPressed()
        run()
        if (!alice.shows(hasTestTag("phase-track"))) alice.openGame(game, this)
        waitUntil("Alice's table") { alice.shows(hasTestTag("phase-track")) && alice.game?.shown != null }
        keep()
        waitUntil("Alice's game settled") { alice.settled(this) && alice.stepArrow() != null }
        alice.pastMainGuard()
        alice.pressKey(" ")
        repeat(3) { run(skip = java.time.Duration.ofMillis(400)) }
        assertTrue(alice.inOwnMain(), "Space does nothing now")
        alice.pastMainGuard()
        alice.pressKey("n")
        waitUntil("Alice past her Main 1 after N") { alice.state.turn.step != TurnStep.PrecombatMain || alice.state.turn.active != alice.me }
        assertEquals(emptyList(), alice.leaks.leaks)
    }
}

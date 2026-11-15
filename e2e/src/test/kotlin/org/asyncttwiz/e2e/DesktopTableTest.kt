package org.asyncttwiz.e2e

import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import arcanum.api.TurnStep
import kotlinx.coroutines.runBlocking
import org.asyncttwiz.android.DESKTOP_UNDO_TAG
import org.asyncttwiz.android.HOTKEY_SHEET_TAG
import org.asyncttwiz.android.HotkeyAction
import org.asyncttwiz.android.INSPECT_LIST_TAG
import org.asyncttwiz.android.INSPECT_SEPARATOR_TAG
import org.asyncttwiz.android.PANEL_GO_ON_TAG
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
        // a third of the row and two thirds, with the quick actions' gap between them
        val gap = org.asyncttwiz.android.DesktopTable.ACTION_GAP * org.asyncttwiz.android.DesktopTable.UI_SCALE
        assertEquals((row - 2 * gap) / 3, undo.width, 1.5f, "Undo a third of the row: $undo, $main")
        assertEquals(row - (row - 2 * gap) / 3 - gap, main.width, 1.5f, "the main button two thirds: $undo, $main")
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
        // WEB-024: Alt held over the Cub inspects it; released, the Inspect closes
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

    // WEB-017, STK-007 (owner playtest 2026-11-15: "web chat pane now completely hides the stack? It should completely not hide
    // the stack, i.e. push it to the side. Stack is better as a thing on the field, not in the chat pane at all"): Alice casts
    // her Ember Cub and waits for Bob: with the chat dock open the Cub is on the Stack at the battlefields' right edge, wholly
    // left of the dock, which holds only the chat; a click on the Stack opens the Stack list; folded away by the chat button,
    // the Stack moves right with the battlefields' edge
    @Test fun theStackStaysOnTheFieldBesideTheChatDock() {
        start(bobWaits = true)
        keep()
        clickFromHand("Mountain")
        waitUntil("the Mountain on Alice's battlefield", along = listOf(bob)) { alice.field("Mountain").isNotEmpty() && alice.inOwnMain() }
        clickFromHand("Ember Cub")
        waitUntil("the Cub on Alice's Stack, waiting for Bob") {
            alice.state.stack.isNotEmpty() && alice.shows(hasContentDescription("Stack, 1 entries")) && alice.settled(this)
        }
        run()
        val dock = alice.tagged("desktop-dock").fetchSemanticsNode().boundsInRoot
        val stack = alice.node(hasContentDescription("Stack, 1 entries")).fetchSemanticsNode().boundsInRoot
        assertTrue(stack.right <= dock.left && stack.width > 0f, "the Stack on the field, wholly left of the chat dock: $stack, dock $dock")
        assertTrue(!alice.showsText("Top · Ember Cub"), "nothing of the Stack in the dock: ${alice.screen()}")
        alice.node(hasContentDescription("Stack, 1 entries")).performMouseInput { click() }
        waitUntil("the Stack list open") { alice.showsText("Top · Ember Cub") }
        alice.control("Close").performMouseInput { click() }
        waitUntil("the Stack list closed") { !alice.showsText("Top · Ember Cub") }
        alice.tagged("dock-button").performMouseInput { click() }
        waitUntil("Alice's chat dock folded away") { !alice.shows(hasTestTag("desktop-dock")) && alice.settled(this) }
        run()
        val folded = alice.node(hasContentDescription("Stack, 1 entries")).fetchSemanticsNode().boundsInRoot
        assertEquals(stack.right + (dock.right - dock.left), folded.right, 2f, "the Stack moved right with the battlefields' edge: $folded")
        assertEquals(emptyList(), alice.leaks.leaks)
    }

    // ZON-001, WEB-016 (owner, desktop playtest 2026-11-14: "Opponent hand should be a fan like in mobile?"): on Alice's desktop
    // table Bob's hand is the phone's small fan of card backs in his seat header, after his piles and level with his life
    // square, its cards overlapping (a row of small cards side by side before)
    @Test fun theOtherPlayersHandIsAFanInTheirSeatHeader() {
        start()
        val life = alice.described("${bob.name}, life 20", substring = true).fetchSemanticsNode().boundsInRoot
        val exile = alice.described("${bob.name} exile,", substring = true).fetchSemanticsNode().boundsInRoot
        val cards = alice.nodes(hasContentDescription("Covered card, owner ${bob.name}, ", substring = true)).fetchSemanticsNodes()
            .map { it.boundsInRoot }.sortedBy { it.left }
        assertEquals(bob.state.player(bob.me).hand.size, cards.size, "his whole hand as card backs: ${alice.screen()}")
        assertTrue(cards.all { it.left > exile.right && it.center.y > life.top && it.center.y < life.bottom },
            "in his seat header after his piles: $cards, exile $exile, life $life")
        assertTrue(cards.zipWithNext().all { (a, b) -> b.left < a.right }, "fanned, each card over the one before: $cards")
        assertEquals(emptyList(), alice.leaks.leaks)
    }

    // WEB-017, LOB-066, WEB-031 (owner, desktop playtest 2026-11-14: "Two different ways of showing chat, do we need those?"):
    // Alice folds the dock away; Bob's message lights the top row's chat button, whose click unfolds the dock to its chat,
    // ready to type, with no chat panel over the table (before: the side panel), and the message read there clears the dot.
    // Folded away again, C does the same
    @Test fun theChatButtonOpensTheDocksChat() {
        start()
        val draft = hasContentDescription("Chat message draft")
        alice.tagged("dock-button").performMouseInput { click() }
        waitUntil("Alice's dock folded away") { !alice.shows(hasTestTag("desktop-dock")) }
        bob.described("Chat, ", substring = true).performClick()
        waitUntil("Bob's chat open") { bob.shows(draft) }
        bob.described("Chat message draft").performTextInput("Ready?")
        waitUntil("Bob's draft") { bob.lobby.state.chat.draft?.text == "Ready?" }
        bob.described("Send").performClick()
        waitUntil("Bob's message accepted") { bob.lobby.state.chat.messages.size == 1 }
        bob.described("Close chat").performClick()
        waitUntil("Alice's chat button lit", along = listOf(bob)) { alice.shows(hasContentDescription("Chat, 1 unread messages")) }
        alice.described("Chat, 1 unread messages").performMouseInput { click() }
        waitUntil("the dock's chat unfolded, ready to type: ${alice.screen()}") {
            alice.shows(hasTestTag("desktop-dock")) && alice.shows(draft and isFocused())
        }
        assertTrue(!alice.shows(hasContentDescription("Close chat")), "no chat panel over the table: ${alice.screen()}")
        assertTrue(alice.showsText("Ready?"), "Bob's message in the dock")
        waitUntil("Bob's message read") { alice.lobby.state.chat.unread == 0 }
        // Folded away again, the chat key unfolds it ready to type too
        alice.tagged("dock-button").performMouseInput { click() }
        waitUntil("Alice's dock folded away") { !alice.shows(hasTestTag("desktop-dock")) }
        alice.pressKey("c")
        waitUntil("the dock's chat unfolded by C, ready to type") { alice.shows(hasTestTag("desktop-dock")) && alice.shows(draft and isFocused()) }
        assertTrue(!alice.shows(hasContentDescription("Close chat")), "no chat panel over the table: ${alice.screen()}")
        assertEquals(emptyList(), alice.leaks.leaks)
    }

    /** How cyan the glow is at [x], [y] of [picture]: how far its red falls below its green and blue (0 on white or grey). */
    private fun cyanAt(picture: android.graphics.Bitmap, x: Float, y: Float): Int {
        val pixel = picture.getPixel(x.toInt(), y.toInt())
        val red = (pixel shr 16) and 255; val green = (pixel shr 8) and 255; val blue = pixel and 255
        return minOf(green, blue) - red
    }

    /** WEB-015: the mouse on Alice's hand card [name]: it rises whole into view out of the fan, upright; its bounds then. */
    private fun hoverInHand(name: String): androidx.compose.ui.geometry.Rect {
        waitUntil("Alice's game settled") { alice.settled(this) }
        alice.handCard(name).performMouseInput { moveTo(center) }
        waitUntil("$name risen out of Alice's fan") {
            alice.settled(this) && alice.handCard(name).fetchSemanticsNode().boundsInRoot.let { it.bottom <= 900f && it.height > it.width }
        }
        repeat(3) { run(skip = java.time.Duration.ofMillis(300)) }
        return alice.handCard(name).fetchSemanticsNode().boundsInWindow
    }

    // TBL-117 (owner, desktop playtest 2026-11-14: "No 'playable card' highlight for hand cards like in mobile or all cards
    // are highlight?"), WEB-022, WEB-015: at Alice's Main 1 with no land out, her Mountain may be played and glows around it as
    // on the phone, as far from the card for its size; her Ember Cub, which she can't pay for, doesn't. Each is looked at risen
    // out of the fan with the mouse on it (owner playtest 2026-11-15), upright at the desktop's 5.2x
    @Test fun onlyThePlayableHandCardGlowsAsOnThePhone() {
        start()
        keep()
        fun glow(name: String): Pair<Int, Int> {
            val card = hoverInHand(name)
            assertEquals(28f * 5.2f, card.width, 1f, "the desktop's hand card at 5.2x, upright: $card")
            val picture = alice.picture()
            val y = card.top - card.width * .1f
            // The battlefield's own colour at the same height at the window's left, away from the cards
            return cyanAt(picture, card.left + card.width * .1f, y) to cyanAt(picture, 20f, y)
        }
        val (mountain, field) = glow("Mountain")
        assertTrue(mountain - field >= 10, "the Mountain glows: $mountain over $field")
        val (cub, cubField) = glow("Ember Cub")
        assertTrue(cub - cubField <= 3, "the Cub doesn't glow: $cub over $cubField")
    }

    // WEB-024, TBL-132, TBL-133 (owner 2026-11-15): on the desktop table nothing clicks a change, so Inspect by Alt held shows a
    // change line's explanation at once with its source: Alice casts her Ash Pup, then Ember Beckoning has the Ring tempt her;
    // Alt held over the Pup shows its Ring-bearer line, "From The Ring" and what a Ring-bearer is, without any click
    @Test fun altInspectExplainsAChangeWithoutAClick() {
        startGame(TestDeck("Ring", listOf(TestCards.mountain to 14, TestCards.pup to 13, TestCards.beckoning to 13)), aliceDesktop = true,
            aliceSettings = PlaySettings(assume = AssumeResolves.Always),
            deal = { a, _ -> listOf("Ash Pup", "Ember Beckoning").all { it in hand(a) } })
        bothOpen()
        keep()
        clickFromHand("Ash Pup")
        waitUntil("the Pup on Alice's battlefield", along = listOf(bob)) { alice.field("Ash Pup").isNotEmpty() && alice.inOwnMain() }
        val pup = alice.field("Ash Pup").single()
        // The fan's next Ash Pup comes to lie under the mouse, and its quick look would cover the fan: the mouse goes up first
        alice.tagged("phase-track").performMouseInput { moveTo(center) }
        run()
        clickFromHand("Ember Beckoning")
        waitUntil("the Ring's temptation", along = listOf(bob)) {
            if (alice.stepArrow() == "Resolve") alice.once { alice.tap("Resolve") }
            arcanum.api.ObjectMark.RingBearer in alice.state.obj(pup)?.marks.orEmpty() && alice.state.stack.isEmpty() && alice.inOwnMain()
        }
        waitUntil("the Ring-bearer's word on Alice's table") {
            alice.settled(this) && alice.shows(hasContentDescription(", Ring-bearer", substring = true))
        }
        alice.battlefieldCard("Ash Pup").performMouseInput { moveTo(center) }
        run()
        assertTrue(alice.key("Alt", true), "the finger key taken")
        waitUntil("the Pup inspected") { alice.shows(hasTestTag("card-inspection")) }
        val bearer = arcanum.api.RulesGlossary.state("ring-bearer")!!.text
        assertTrue(alice.showsText("Ring-bearer: legendary, can't be blocked by creatures with greater power") &&
            alice.showsText("From The Ring") && alice.shows(hasContentDescription("Rule explanation")) && alice.showsText(bearer),
            "the line with its source and explanation, no click: ${alice.screen()}")
        alice.key("Alt", false)
        waitUntil("the Inspect closed with Alt let go") { !alice.shows(hasTestTag("card-inspection")) }
        assertTrue(!alice.refusalSeen, "nothing refused")
    }

    // WEB-015, WEB-016, WEB-018, WEB-019, WEB-024, WEB-031 (owner playtest 2026-11-15): Alice's hand is a fan at the bottom of her
    // desktop table, its cards partly below the window's bottom: the mouse on a Mountain raises it whole into view, a click
    // keeps it up and a second plays it from the fan. Her Ember Burst's cast panel has its Cast in its bottom right corner as on
    // the main button, which casts it. Held, Ctrl keeps her in Control mode and let go puts back her mode; with another key it
    // is that key's chord. In her next turn her second Mountain stacks on the first: Alt over them lists both one under the
    // other, and the wheel scrolls the list while Alt is held
    @Test fun theHandFanPanelCornerAndHeldKeys() {
        startGame(TestDeck.bursts(), aliceDesktop = true,
            deal = { a, _ -> hand(a).count { it == "Mountain" } >= 2 && "Ember Burst" in hand(a) })
        bothOpen()
        keep()
        waitUntil("Alice's game settled") { alice.settled(this) }
        val resting = alice.handCard("Mountain").fetchSemanticsNode().boundsInRoot
        // The card's part in view (its semantics end at the window's bottom) is shorter than a card
        assertTrue(resting.bottom >= 899f && resting.height < resting.width * 1.3f, "partly below the window's bottom: $resting")
        val risen = hoverInHand("Mountain")
        assertTrue(risen.top < resting.top - 40f, "risen whole into view: $resting -> $risen")
        clickFromHand("Mountain")
        waitUntil("the Mountain played from the fan", along = listOf(bob)) { alice.field("Mountain").isNotEmpty() && alice.inOwnMain() }
        // WEB-019: the cast panel's go-on in its corner, as the main button says it
        clickFromHand("Ember Burst")
        waitUntil("the Burst's cast panel with Cast in its corner") {
            alice.shows(hasContentDescription("Cast panel")) &&
                alice.shows(hasTestTag(PANEL_GO_ON_TAG) and hasContentDescription("Cast", substring = true))
        }
        val panel = alice.described("Cast panel").fetchSemanticsNode().boundsInRoot
        val corner = alice.tagged(PANEL_GO_ON_TAG).fetchSemanticsNode().boundsInRoot
        assertTrue(kotlin.math.abs(corner.right - panel.right) < 24f && corner.top >= panel.bottom - 1f,
            "at the panel's bottom right: $corner, $panel")
        alice.described("One more").performMouseInput { click() }
        waitUntil("X at 1") { alice.shows(hasContentDescription("Value 1")) && alice.settled(this) }
        alice.pastMainGuard()
        alice.tagged(PANEL_GO_ON_TAG).performMouseInput { click() }
        waitUntil("Alice's target bar") { alice.showsText("Choose any target") }
        alice.described("${bob.name}, life 20", substring = true).performMouseInput { click() }
        waitUntil("the Burst dealing 1 to Bob", along = listOf(bob)) {
            alice.state.player(bob.me).life == 19 && alice.inOwnMain() && alice.settled(this)
        }
        // WEB-031: Ctrl held is Control mode until it is let go; Ctrl with another key is that key's chord
        val mode = { alice.game?.shown?.settings?.mode }
        val own = mode()
        assertTrue(own != null && own != ResponseMode.FullControl, "Alice not in Control to begin with: $own")
        alice.key("Control", true, ctrl = true)
        repeat(2) { run(skip = java.time.Duration.ofMillis(300)) }
        waitUntil("Control while Ctrl is held") {
            mode() == ResponseMode.FullControl && alice.shows(hasContentDescription("Response mode") and
                androidx.compose.ui.test.SemanticsMatcher.expectValue(androidx.compose.ui.semantics.SemanticsProperties.StateDescription, "Control"))
        }
        alice.key("Control", false)
        waitUntil("back to her mode once let go") { mode() == own }
        alice.key("Control", true, ctrl = true)
        alice.key("k", true, ctrl = true)
        alice.key("k", false, ctrl = true)
        repeat(2) { run(skip = java.time.Duration.ofMillis(300)) }
        alice.key("Control", false)
        run()
        assertEquals(own, mode(), "Ctrl+K is a chord, never Control mode")
        // WEB-024: the next turn her second Mountain stacks on the first; Alt held over them lists both, the wheel scrolls them
        alice.pastMainGuard()
        alice.pressKey(" ", shift = true)
        waitUntil("Alice's next Main 1", along = listOf(bob)) { alice.state.turn.number >= 3 && alice.inOwnMain() }
        clickFromHand("Mountain")
        waitUntil("two Mountains on Alice's battlefield", along = listOf(bob)) {
            alice.field("Mountain").size == 2 && alice.inOwnMain() && alice.settled(this)
        }
        alice.battlefieldCard("Mountain").performMouseInput { moveTo(center) }
        run()
        assertTrue(alice.key("Alt", true), "the finger key taken")
        waitUntil("both Mountains inspected, one under the other") {
            alice.shows(hasTestTag(INSPECT_LIST_TAG)) && alice.shows(hasTestTag(INSPECT_SEPARATOR_TAG))
        }
        val line = alice.tagged(INSPECT_SEPARATOR_TAG).fetchSemanticsNode().boundsInRoot
        assertTrue(line.width > line.height, "a line across between them: $line")
        alice.tagged(INSPECT_LIST_TAG).performMouseInput { scroll(3f) }
        run()
        val scrolled = alice.tagged(INSPECT_SEPARATOR_TAG).fetchSemanticsNode().boundsInRoot
        assertTrue(scrolled.top < line.top - 50f, "the wheel scrolled the list while Alt is held: $line -> $scrolled")
        alice.key("Alt", false)
        waitUntil("the Inspect closed with Alt let go") { !alice.shows(hasTestTag("card-inspection")) }
        assertTrue(!alice.refusalSeen, "nothing refused")
        assertEquals(emptyList(), alice.leaks.leaks)
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

package org.asyncttwiz.e2e

import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.longClick
import arcanum.engine.decision.Answer
import arcanum.engine.input.Input
import org.junit.Test
import java.time.Duration
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Owner problem report 5DDSKK (2026-11-09): after a second mulligan the bottom card was asked at once in the generic
 * choice panel, and Keep then opened the Keep / Bottom screen again, saying a card was still owed though the hand was at
 * six. Owner's choice: the bottom asked right after a mulligan is the Keep / Bottom screen, and Keep only keeps.
 */
class OpeningBottomTest : E2e() {
    private val bottomScreen = hasContentDescription("Keep or bottom")

    // TRN-062, STK-043, CR 103.5 (problem report 5DDSKK): Bob keeps; Alice mulligans twice (the first one free), puts one
    // card on the bottom in the Keep / Bottom screen that opens after the second, with Mulligan and Keep greyed meanwhile;
    // then Keep goes straight on to her first turn with six cards, no second bottom screen
    @Test fun keepAfterTheBottomOfASecondMulliganOnlyKeeps() {
        startGame()
        bothOpen()
        bob.tap("Keep")
        waitUntil("Bob's keep on Alice's table") { alice.shown.held.any { it.author == bob.id } }
        repeat(2) { round ->
            val before = alice.state.player(alice.me).hand.toList()
            alice.tap("Mulligan")
            waitUntil("Alice's hand after mulligan ${round + 1}") { alice.state.player(alice.me).hand.toList() != before }
        }
        waitUntil("the bottom of Alice's second mulligan asked") { alice.shows(bottomScreen) || alice.shows(hasContentDescription("Automatum choice")) }
        val usedScreen = alice.shows(bottomScreen)
        val hand = alice.state.player(alice.me).hand.toList()
        assertEquals(7, hand.size, "seven drawn")
        if (usedScreen) for (action in listOf("Keep", "Mulligan")) {
            // Greyed: a tap changes nothing, holding it says why
            val control = hasContentDescription(action) and hasClickAction() and !hasAnyAncestor(bottomScreen)
            alice.node(control).performClick()
            run(skip = Duration.ofMillis(300))
            assertEquals(hand, alice.state.player(alice.me).hand.toList(), "$action changed nothing")
            assertTrue(alice.shows(bottomScreen), "the bottom screen stays open")
            alice.node(control).performTouchInput { longClick() }
            waitUntil("why $action is refused") { alice.showsText("Put the card on the bottom first.") }
            run(skip = Duration.ofMillis(2500))
        }
        val bottom = cardName(alice.state, hand.first())!!
        val panels = bottomScreen or hasContentDescription("Automatum choice")
        alice.nodes(hasContentDescription(bottom) and hasAnyAncestor(panels))[0].performClick()
        waitUntil("the card picked for the bottom") {
            alice.state.player(alice.me).hand.size == 6 || alice.shows(androidx.compose.ui.test.hasText("Bottom · 1") and hasAnyAncestor(panels))
        }
        if (alice.state.player(alice.me).hand.size == 7) alice.node(androidx.compose.ui.test.hasText("OK") and hasAnyAncestor(panels)).performClick()
        waitUntil("six cards in Alice's hand") { alice.state.player(alice.me).hand.size == 6 && !alice.shows(panels) }
        alice.tap("Keep")
        // A moment for a panel that would open
        run(skip = Duration.ofMillis(500))
        assertFalse(alice.shows(bottomScreen), "no second bottom screen: ${alice.screen()}")
        waitUntil("Alice's Main 1 on her keep", along = listOf(bob)) { alice.inOwnMain() }
        assertEquals(6, alice.state.player(alice.me).hand.size, "she keeps six")
        assertTrue(alice.engine { device.session.events + device.session.pending }.any { e -> e.author == alice.id &&
            e.engine?.steps.orEmpty().any { (it.input as? Input.Answer)?.answer == Answer.Option("Keep") ||
                (it.input as? Input.SetupAnswer)?.answer == Answer.Option("Keep") } }, "her own keep on the line")
        assertTrue(usedScreen, "the bottom was asked in the Keep / Bottom screen")
        assertFalse(alice.refusalSeen, "no refusal: ${alice.shownTexts}")
    }
}

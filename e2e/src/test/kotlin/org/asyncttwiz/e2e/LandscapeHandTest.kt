package org.asyncttwiz.e2e

import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.doubleClick
import androidx.compose.ui.test.click
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import org.asyncttwiz.android.CardAppearancePreferences
import org.asyncttwiz.android.CardArea
import org.asyncttwiz.appui.AppPlatform
import org.junit.Test
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Owner notes after beta5 (2026-11-08): in landscape the hand was a full-width strip above the bottom bar; it moves into
 * the bottom bar, between your life square and piles and the controls at its right end, and the battlefields take the
 * strip's height. Everything the hand does stays.
 */
class LandscapeHandTest : E2e() {
    // TBL-009, TBL-144, ZON-079, ZON-102, ZON-004, ZON-104, ZON-138 (owner, 2026-11-08, 2026-11-25): in landscape Alice's hand is a
    // fan in the bottom bar, after her piles and before Undo and the quick actions (its cards' part in view in the bar, the rest
    // below the screen), the battlefield reaching down to the bar; a tap raises a card there and a second plays it, a double tap
    // plays a land
    @Config(qualifiers = "w892dp-h412dp-land-mdpi")
    @Test fun theHandInTheBottomBarPlaysAsInPortrait() {
        // The deal follows each run's random account ids (per-player random streams since 336be95d5): Bob's Mountain is asked for,
        // as playing along through his turn he plays one if he holds it, else his cleanup's discard waits for him
        startGame(deal = { a, b -> hand(a).count { it == "Mountain" } >= 2 && "Mountain" in hand(b) })
        bothOpen()
        alice.tap("Keep")
        bob.tap("Keep")
        waitUntil("Alice's Main 1", along = listOf(bob)) { alice.inOwnMain() }
        val bar = 412f - 56f
        fun handBounds() = alice.nodes(hasContentDescription(", owner ${alice.name}, ", substring = true)).fetchSemanticsNodes()
            .map { it.boundsInRoot }.filter { it.center.y > bar }
        val cards = handBounds()
        assertEquals(alice.state.player(alice.me).hand.size, cards.size, "her whole hand in the bottom bar: ${alice.screen()}")
        val undo = alice.control("Undo").fetchSemanticsNode().boundsInRoot
        val library = alice.node(hasContentDescription("${alice.name} library,", substring = true)).fetchSemanticsNode().boundsInRoot
        assertTrue(cards.all { it.top >= bar && it.bottom <= 412f }, "their part in view inside the bar: $cards")
        assertTrue(cards.all { it.left > library.right && it.right < undo.left }, "between her piles and the controls: $cards")
        assertTrue(alice.battlefieldBounds().bottom > bar - 12f, "the battlefield reaches the bar: ${alice.battlefieldBounds()}")
        fun centre(row: List<androidx.compose.ui.geometry.Rect>) = (row.minOf { it.left } + row.maxOf { it.right }) / 2
        val handCentre = centre(cards)

        // A tap raises a Mountain in the bar; a second plays it
        alice.playFromHand("Mountain", this)
        waitUntil("the Mountain on the battlefield") { alice.field("Mountain").isNotEmpty() && alice.inOwnMain() }
        // The hand's fan stays where it was (the cards left centred in it)
        assertEquals(handCentre, centre(handBounds()), .5f)
        // In her next turn a double tap plays the other Mountain
        alice.hold("To combat")
        waitUntil("Alice's next Main 1", along = listOf(bob)) {
            // Her playback stops in Bob's turn: she goes on with the main button
            if (alice.status is org.asyncttwiz.protocol.game.GameDevice.Status.Stopped ||
                alice.status == org.asyncttwiz.protocol.game.GameDevice.Status.YourMove && alice.state.turn.active != alice.me)
                alice.once { alice.stepArrow()?.let(alice::tap) }
            alice.inOwnMain() && alice.state.turn.number >= 3
        }
        alice.handCard("Mountain").performTouchInput { doubleClick() }
        waitUntil("the second Mountain on the battlefield") { alice.field("Mountain").size == 2 }
        assertTrue(alice.leaks.leaks.isEmpty() && bob.leaks.leaks.isEmpty())
    }

    // TBL-238, TBL-144, ZON-138 (owner 2026-11-24, 2026-11-25): with large hand cards (2.5x) in landscape Alice's battlefield is seen
    // down to the bottom bar, under her hand's fan, whose cards stand above the bar over it (their bottom fifth below the screen); a
    // tap on a card's part above the bar raises it, a second plays it
    @Config(qualifiers = "w892dp-h412dp-land-mdpi")
    @Test fun largeHandCardsStandOverTheBattlefield() {
        val sizes = CardAppearancePreferences(AppPlatform.current)
        sizes.customizeAreas(true)
        sizes.setSize(2.5f, CardArea.HAND)
        try {
            // At 2.5x her fan holds her whole opening hand (ZON-139): a Mountain among her first three cards, as before (a deal with
            // both further right timed out when her hand was a row)
            startGame(deal = { a, _ -> "Mountain" in hand(a).take(3) })
            bothOpen()
            alice.tap("Keep")
            bob.tap("Keep")
            waitUntil("Alice's Main 1", along = listOf(bob)) { alice.inOwnMain() }
            val bar = 412f - 56f
            // Her leftmost Mountain in her hand (in the bar's height), once laid out at the 2.5x hand size (100 dp tall, 80 of it in view)
            fun mountain(): Rect? = alice.nodes(hasContentDescription("Mountain, owner ${alice.name}, ", substring = true))
                .fetchSemanticsNodes().map { it.boundsInRoot }.filter { it.bottom > bar && it.height > 75f }.minByOrNull { it.left }
            fun field() = runCatching { alice.battlefieldBounds() }.getOrNull()
            // Her battlefield is laid out down to her fan's top (its land row's colour going on under the cards to the bar, TBL-238).
            // (Before, it stopped 4 dp above a row of hand cards standing higher; now the fan stands lower, ZON-138, and with the other
            // player's hand a small fan in the top bar, ZON-126, the empty rows fit their room instead of reaching under the cards)
            waitUntil("Alice's 2.5x hand laid out, her battlefield seen down to it") {
                val card = mountain(); val field = field()
                card != null && field != null && card.top < bar - 20f && field.bottom > card.top - 8f && field.bottom <= bar + .5f &&
                    alice.settled(this)
            }
            val card = mountain()!!
            // (on the card's own part of the fan, its left part: the card beside it overlaps its right part)
            fun tapAbove(c: Rect) {
                val field = alice.battlefieldBounds()
                alice.table().performTouchInput { click(Offset(c.left + 12f - field.left, c.top + 8f - field.top)) }
            }
            tapAbove(card)
            waitUntil("the Mountain raised") { mountain()?.let { it.top < card.top - 10f } == true && alice.settled(this) }
            tapAbove(mountain()!!)
            waitUntil("the Mountain on the battlefield") { alice.field("Mountain").isNotEmpty() }
            assertTrue(alice.leaks.leaks.isEmpty() && bob.leaks.leaks.isEmpty())
        } finally { sizes.setSize(1f, CardArea.HAND); sizes.customizeAreas(false) }
    }
}

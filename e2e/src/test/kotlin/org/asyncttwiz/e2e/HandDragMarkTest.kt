package org.asyncttwiz.e2e

import androidx.compose.ui.geometry.Offset
import org.junit.Test
import kotlin.test.assertEquals

/**
 * Owner playtest 2026-11-19 (N16, on 0.2.1): "Some hand reordering drag target visual code clearly remains, should not." A hand
 * card dragged back over the hand tinted the hand and drew an insertion line, as when hands could be reordered by hand.
 */
class HandDragMarkTest : E2e() {
    // ZON-081, ZON-002: Alice lifts the first card of her hand (up, then back down over her hand, held there): her other hand
    // cards look exactly as before the drag, no tint or line over them
    @Test fun aCardDraggedOverTheHandMarksNothingThere() {
        startGame(deal = { a, _ -> hand(a).size == 7 })
        bothOpen()
        bothKeep(bobAway = false)
        waitUntil("Alice's Main 1", along = listOf(bob)) { alice.inOwnMain() }
        waitUntil("Alice's game settled") { alice.settled(this) }
        val field = alice.battlefieldBounds()
        val cards = alice.nodes(androidx.compose.ui.test.hasContentDescription(", owner ${alice.name}, ", substring = true))
            .fetchSemanticsNodes().filter { it.boundsInRoot.center.y > field.bottom }.sortedBy { it.boundsInRoot.left }
        val first = cards.first()
        val last = cards.last().boundsInRoot
        fun lastCardPixels(picture: android.graphics.Bitmap) = (0 until 5).flatMap { i -> (0 until 5).map { j ->
            picture.getPixel((last.left + last.width * (.2f + .15f * i)).toInt(), (last.top + 4f + (picture.height - last.top - 8f).coerceAtMost(last.height) * (.1f + .15f * j)).toInt())
        } }
        val before = lastCardPixels(alice.picture())
        val lift = Offset(0f, -first.boundsInRoot.height * .8f)
        var during: List<Int> = emptyList()
        holding(alice.node(androidx.compose.ui.test.SemanticsMatcher("first hand card") { it.id == first.id }), millis = 0, moveFirst = lift) {
            moveHeldFinger(alice.node(androidx.compose.ui.test.SemanticsMatcher("first hand card") { it.id == first.id }), Offset(0f, -lift.y * .9f))
            passWhileHeld(200)
            during = lastCardPixels(alice.picture())
        }
        assertEquals(before, during, "the hand's last card looks as before while a card is dragged over the hand")
    }
}

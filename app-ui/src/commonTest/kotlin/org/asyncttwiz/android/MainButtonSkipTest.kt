package org.asyncttwiz.android

import org.asyncttwiz.appui.BigInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The main button's skip (owner 2026-11-19): what a press waiting for its skip's board does, and what a skip leaves on the table. */
class MainButtonSkipTest {
    // TRN-166: the press is made after the skip only when the main button still says what was pressed there and isn't greyed;
    // otherwise only the skip was made (a reveal's panel, the other player's move arrived meanwhile)
    @Test fun thePressIsMadeAfterItsSkipOnlyWhereTheButtonStillSaysTheSame() {
        var made = 0
        val resolve = MainPress("Resolve", true, { made++ }, null)
        assertTrue(madeAfterSkip("Resolve", resolve))
        assertFalse(madeAfterSkip("Resolve", MainPress("End turn", true, {}, null)), "the button says something else there")
        assertFalse(madeAfterSkip("Resolve", MainPress("Resolve", false, {}, null)), "greyed there")
        assertFalse(madeAfterSkip("Resolve", null), "nothing on the button there")
        assertEquals(0, made, "deciding makes nothing")
    }

    // TBL-201, TBL-208, TRN-165: the skip draws the table where it is without moving (the next scene doesn't animate), while the
    // change tags and flashes showing stay as they are
    @Test fun aSkipStopsTheMotionAndLeavesTheFlashes() {
        var time = 0L
        val changes = TableChanges { time }
        val life = TableChange(TableChangeKey("life/bob", TableChangeKind.LIFE), listOf(BigInteger("-3")))
        changes.applyEngine("e1", jump = false, listOf(life), emptySet())
        assertTrue(changes.animates("e1"), "the board's own events animate")
        time = 200
        changes.stopMotion()
        assertFalse(changes.animates("e1"), "after the skip the scene is drawn at once")
        assertEquals(setOf(life.key), changes.active.keys, "the flash stays")
    }
}

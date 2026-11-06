package org.asyncttwiz.e2e

import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isHeading
import arcanum.engine.input.Input
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Owner playtest 2026-11-05 evening: the undo point of an interaction is its start. Alice plays a Mountain, then begins
 * casting Storm Spark (its target bar open, nothing sent) and taps Undo: the cast is gone and she is just before she began
 * it, exactly where an Undo after the Spark would take her: the Spark in her hand, her Mountain still on the battlefield
 * and untapped, nothing of the cast sent and nothing else taken back.
 */
class UndoMidCastTest : E2e() {
    // STK-025, HIS-084: Undo with the target bar open drops the cast; the Mountain played before it stays
    @Test fun undoWithACastOpenGoesBackToJustBeforeIt() {
        startGame(TestDeck.sparks(), deal = { a, _ -> "Mountain" in hand(a) && "Storm Spark" in hand(a) })
        bothOpen()
        bothKeep(bobAway = true)
        alice.playFromHand("Mountain", this)
        waitUntil("the Mountain on the battlefield, on the server") {
            alice.field("Mountain").isNotEmpty() && alice.inOwnMain() && alice.engine { device.session.pending.isEmpty() }
        }
        val before = alice.engine { device.session.head }
        val sparks = alice.hand("Storm Spark").size
        alice.playFromHand("Storm Spark", this)
        waitUntil("Alice's target bar") { alice.shows(hasText("Choose any target") and isHeading()) }
        alice.tap("Undo")
        waitUntil("the cast dropped") { !alice.shows(hasText("Choose any target") and isHeading()) }
        // A few rounds for anything that would still go out
        repeat(10) { run(java.time.Duration.ofMillis(100)) }
        assertEquals(before, alice.engine { device.session.head }, "nothing taken back")
        assertTrue(alice.engine { device.session.pending.isEmpty() }, "nothing sent")
        assertEquals(before, serverGame()?.headId, "the server's game as before the cast")
        assertTrue(alice.engine { device.session.events }.none { e -> e.engine?.steps.orEmpty().any { it.input is Input.Cast } },
            "no cast on her line")
        assertEquals(sparks, alice.hand("Storm Spark").size, "the Spark back in her hand")
        assertTrue(alice.field("Mountain").isNotEmpty(), "her Mountain still on the battlefield")
        assertTrue(alice.field("Mountain").none { alice.state.obj(it)?.tapped == true }, "her Mountain untapped")
        assertTrue(alice.inOwnMain())
        assertFalse(alice.refusalSeen, "no note: ${alice.shownTexts}")
        // She can begin the cast again at once
        alice.playFromHand("Storm Spark", this)
        waitUntil("Alice's target bar again") { alice.shows(hasText("Choose any target") and isHeading()) }
    }
}

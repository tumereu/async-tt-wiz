package org.asyncttwiz.e2e

import org.asyncttwiz.protocol.game.GameDevice
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Owner, 2026-11-08: timeline mode opens at the newest event, and there the Auto button reads "Exit timeline": one tap
 * leaves it, to where the player was. Moving back makes the button Auto again.
 */
class TimelineExitTest : E2e() {
    // HIS-004, HIS-015, HIS-018: live: opened at the newest event the button says Exit timeline; Previous makes it Auto, Next
    // brings Exit timeline back, and its tap is back in live play at once
    @Test fun exitTimelineLeavesToLivePlay() {
        startGame(deal = { a, _ -> "Mountain" in hand(a) })
        bothOpen()
        bothKeep(bobAway = false)
        alice.playFromHand("Mountain", this)
        waitUntil("the Mountain on the battlefield", along = listOf(bob)) { alice.field("Mountain").isNotEmpty() && alice.inOwnMain() }
        alice.openTimeline(this)
        waitUntil("Alice in timeline mode at the newest event") { alice.showsTimelineLook() && alice.showsControl("Exit timeline") }
        assertFalse(alice.showsControl("Auto advance"), alice.screen())
        alice.tap("Previous")
        waitUntil("the button Auto again") { alice.showsControl("Auto advance") && !alice.showsControl("Exit timeline") }
        alice.tap("Next")
        waitUntil("Exit timeline again at the last event") { alice.showsControl("Exit timeline") }
        assertTrue(alice.showsTimelineLook(), "Next to the last event stays in timeline mode")
        alice.tap("Exit timeline")
        waitUntil("Alice live again") { !alice.showsTimelineLook() && alice.stepArrow() == "To combat" }
        assertEquals(GameDevice.Status.YourMove, alice.status)
    }

    // HIS-015, HIS-018, HIS-006: from a paused catch-up: Exit timeline is back at the catch-up with its centre text
    @Test fun exitTimelineLeavesToThePausedCatchUp() {
        aliceCastsAHeraldWhileBobIsAway(endTurn = false)
        bob.back()
        waitUntil("Bob's centre text") { bob.showsWatch() }
        bob.openTimeline(this)
        waitUntil("Bob in timeline mode") { bob.showsTimelineLook() && bob.showsControl("Exit timeline") }
        bob.tap("Exit timeline")
        waitUntil("Bob back at the catch-up, paused") { !bob.showsTimelineLook() && bob.showsWatch() }
        assertEquals(GameDevice.Status.CatchUp, bob.status)
    }
}

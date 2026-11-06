package org.asyncttwiz.e2e

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.performClick
import org.asyncttwiz.android.CardMovePreferences
import org.asyncttwiz.protocol.game.GameDevice
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Owner, 2026-11-05 (built on the engine path): holding a card to inspect it pauses playback, the catch-up's as a tap on
 * the table does and timeline mode's Auto; releasing the card doesn't resume it.
 */
class HoldPausesPlaybackTest : E2e() {
    private fun E2ePlayer.auto(): String? = described("Auto advance").fetchSemanticsNode().config
        .getOrNull(SemanticsProperties.StateDescription)

    // HIS-017, TBL-105, HIS-095: Alice casts an Ember Herald while Bob is away; Bob comes back and watches. Holding a card
    // of his hand while the catch-up plays pauses it (it goes on to the next point where he gets priority and stops there
    // with the centre text). Then in timeline mode, with Auto on, holding a card stops Auto, and it stays off after
    // the release.
    @Test fun holdingACardPausesTheCatchUpAndTimelinePlayback() {
        aliceCastsAHeraldWhileBobIsAway(endTurn = false)
        // Bob's app starts again and opens the game; while he holds the card his table shows nothing new (a board arriving
        // under a held finger isn't drawn in this test's still clock, [HeldWorker])
        val engine = HeldWorker()
        bob = restart(bob, engine)
        // Card move speed 0.25×: each event Bob sees waits 1.32 s in his playback (HIS-021), time enough to hold a card
        CardMovePreferences(bob.activity, bob.id).multiplier = .25f
        var pausedAfterHold = false
        var held = false
        everyRun += {
            val s = bob.game?.shown
            if (held && (s?.pausing == true || (s?.status as? GameDevice.Status.Stopped)?.paused == true)) pausedAfterHold = true
        }
        bob.openGame(game, this)
        waitUntil("Bob's catch-up") { bob.status == GameDevice.Status.CatchUp && bob.showsWatch() }
        bob.tapToWatch()
        waitUntil("Bob's playback running") { bob.status is GameDevice.Status.Playing }
        held = true
        engine.holding = true
        holding(bob.handCard("Idle Glimmer")) {
            assertTrue(bob.shows(hasContentDescription("Card inspection content")), "the held card inspected: ${bob.screen()}")
        }
        engine.release()
        waitUntil("the hold pausing the playback") { pausedAfterHold }
        waitUntil("Bob's playback stopped") { bob.status is GameDevice.Status.Stopped }
        val stop = bob.status as GameDevice.Status.Stopped
        // It stopped where his pause made it stop, or at the stop it would have made anyway (his Glimmer: he could respond)
        assertTrue(stop.paused || bob.state.stack.isNotEmpty(), "stopped: $stop")
        if (stop.paused) assertTrue(bob.showsWatch(), "the centre text back at a stop the pause made")

        // Timeline mode: back a few events, Auto on, then a hold stops it
        bob.described("Timeline mode").performClick()
        waitUntil("Bob in timeline mode") { bob.showsTimelineLook() && bob.showsControl("Previous") }
        repeat(4) { bob.tap("Previous"); run() }
        bob.tap("Auto advance")
        run()
        assertEquals("Auto: on", bob.auto())
        holding(bob.handCard("Idle Glimmer")) {
            assertTrue(bob.shows(hasContentDescription("Card inspection content")), "inspected in timeline mode: ${bob.screen()}")
        }
        assertEquals("Auto: off", bob.auto(), "the hold stopped Auto")
        // Released: Auto stays off and the cursor stays where the hold left it
        repeat(3) { run(skip = java.time.Duration.ofSeconds(2)) }
        assertEquals("Auto: off", bob.auto(), "the release didn't resume Auto")
        assertTrue(bob.showsTimelineLook(), "still in timeline mode")
    }
}

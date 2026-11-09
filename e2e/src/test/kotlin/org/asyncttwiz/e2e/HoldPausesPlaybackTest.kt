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
 * Owner, 2026-11-08: a finger held on the table pauses catch-up playback between events, and it goes on when lifted. Owner,
 * 2026-11-05: holding a card stops timeline mode's own Auto, and releasing the card doesn't resume that.
 */
class HoldPausesPlaybackTest : E2e() {
    private fun E2ePlayer.auto(): String? = described("Auto advance").fetchSemanticsNode().config
        .getOrNull(SemanticsProperties.StateDescription)

    // HIS-021, HIS-094, HIS-017: Alice casts an Ember Herald while Bob is away (he has nothing to respond with); Bob comes
    // back and watches. Holding a card of his hand while the catch-up plays inspects it and pauses the playback however long
    // he holds it; lifted, the playback goes on by itself to the newest event. Then in timeline mode, with Auto on, holding a
    // card stops Auto, and it stays off after the release.
    @Test fun holdingACardPausesTheCatchUpUntilLiftedAndStopsTimelineAuto() {
        aliceCastsAHeraldWhileBobIsAway(endTurn = false, bobCanRespond = false)
        // Bob's app starts again and opens the game; while he holds the card his table shows nothing new (a board arriving
        // under a held finger isn't drawn in this test's still clock, [HeldWorker]); his device is read for what it did
        val engine = HeldWorker()
        bob = restart(bob, engine)
        // Card move speed 0.25×: each event Bob sees waits 1.32 s in his playback (HIS-021), time enough to hold a card
        CardMovePreferences(bob.activity, bob.id).multiplier = .25f
        var paused = false
        everyRun += { if ((bob.game?.shown?.status as? GameDevice.Status.Stopped)?.paused == true) paused = true }
        bob.openGame(game, this)
        waitUntil("Bob's catch-up") { bob.status == GameDevice.Status.CatchUp && bob.showsWatch() }
        bob.tapToWatch()
        waitUntil("Bob's playback running") { bob.status is GameDevice.Status.Playing }
        val card = listOf("Mountain", "Ember Cub", "Storm Spark").first { bob.hand(it).isNotEmpty() }
        engine.holding = true
        var heldAt: String? = null
        holding(bob.handCard(card)) {
            assertTrue(bob.shows(hasContentDescription("Card inspection content")), "the held card inspected: ${bob.screen()}")
            heldAt = bob.engine { device.session.localHead }
            // Far longer than a playback wait
            passWhileHeld(5_000)
            assertEquals(heldAt, bob.engine { device.session.localHead }, "his playback waits while he holds")
            assertTrue(bob.engine { device.session.behind }, "with more to play")
            assertTrue(bob.engine { !device.pausing }, "only held, not paused as a tap pauses it")
        }
        engine.release()
        waitUntil("the playback going on once lifted") { bob.engine { device.session.localHead } != heldAt }
        waitUntil("Bob's playback at the newest event") { !bob.shown.behind }
        assertTrue(!paused, "the hold made no pause stop")

        // Timeline mode: back a few events, Auto on, then a hold stops it
        bob.openTimeline(this)
        waitUntil("Bob in timeline mode") { bob.showsTimelineLook() && bob.showsControl("Previous") }
        repeat(4) { bob.tap("Previous"); run() }
        bob.tap("Auto advance")
        run()
        assertEquals("Auto: on", bob.auto())
        holding(bob.handCard(card)) {
            assertTrue(bob.shows(hasContentDescription("Card inspection content")), "inspected in timeline mode: ${bob.screen()}")
        }
        assertEquals("Auto: off", bob.auto(), "the hold stopped Auto")
        // Released: Auto stays off and the cursor stays where the hold left it
        repeat(3) { run(skip = java.time.Duration.ofSeconds(2)) }
        assertEquals("Auto: off", bob.auto(), "the release didn't resume Auto")
        assertTrue(bob.showsTimelineLook(), "still in timeline mode")
    }
}

package org.asyncttwiz.e2e

import org.asyncttwiz.protocol.game.GameDevice
import org.asyncttwiz.protocol.game.PlaySettings
import org.junit.Test
import java.time.Duration
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Owner playtest 2026-11-05: coming back to a game still open in memory (the app back from the background) with the
 * other player's events played them at once; only a fresh load paused. Returning starts the catch-up paused, and
 * leaving the app pauses playback at once.
 */
class ReturnPausesTest : E2e() {
    // HIS-035, HIS-006, HIS-094: Bob leaves the app with the game open; Alice plays a Mountain and casts an Ember Herald
    // and ends her turn (her phone assuming his passes, "Always assume resolves"); Bob's app comes back (nothing
    // reloaded): his table waits paused with the centre text. He taps it, leaves in the middle of the playback: it pauses
    // at once; back again, still paused until the centre text
    @Test fun returningToAnOpenGameStartsTheCatchUpPausedAndLeavingPauses() {
        startGame(TestDeck.thirds(TestCards.herald), TestDeck.thirds(TestCards.herald), aliceSettings = PlaySettings(alwaysAssumeResolves = true),
            deal = { a, _ -> "Mountain" in hand(a) && "Ember Herald" in hand(a) })
        bothOpen()
        bothKeep(bobAway = false)
        val open = bob.game!!
        away(bob, alice)
        alice.playFromHand("Mountain", this)
        waitUntil("the Mountain on the battlefield") { alice.field("Mountain").isNotEmpty() && alice.inOwnMain() }
        alice.playFromHand("Ember Herald", this)
        waitUntil("the Herald on Alice's battlefield") { alice.field("Ember Herald").isNotEmpty() && alice.inOwnMain() }
        alice.hold("To combat")
        waitUntil("Alice's turn over, all of it on the server") {
            alice.state.turn.active == bob.me && alice.engine { device.session.pending.isEmpty() }
        }

        bob.back()
        waitUntil("Bob's table with Alice's events") { bob.shown.behind }
        assertTrue(bob.game === open, "the game still open in memory, not loaded again")
        val head = bob.shown.localHead
        repeat(3) { run(skip = Duration.ofSeconds(2)) }
        assertEquals(GameDevice.Status.CatchUp, bob.status, "paused until the centre text")
        assertEquals("${alice.name} has made moves · Tap to watch", bob.watchText())
        assertEquals(head, bob.shown.localHead, "nothing played back")

        // The centre text, then away in the middle of the playback: it stops at once
        bob.tapToWatch()
        waitUntil("the playback going") { bob.shown.localHead != head }
        bob.away()
        run()
        val left = bob.shown.localHead
        repeat(3) { run(skip = Duration.ofSeconds(2)) }
        assertEquals(left, bob.shown.localHead, "nothing more played while away")
        bob.back()
        repeat(3) { run(skip = Duration.ofSeconds(2)) }
        assertEquals(left, bob.shown.localHead, "still paused after coming back")
        assertEquals(GameDevice.Status.CatchUp, bob.status)
        assertTrue(bob.showsWatch())
        bob.tapToWatch()
        waitUntil("Bob caught up") {
            if (bob.status is GameDevice.Status.Stopped) bob.playAlong()
            !bob.shown.behind && bob.status !is GameDevice.Status.Playing
        }
        // HIS-006: away and back with nothing new: live again, nothing waits for Play
        bob.away()
        run()
        bob.back()
        waitUntil("Bob's game told he is back") { !bob.game!!.awayPaused }
        assertEquals(false, bob.engine { device.catchUp }, "live")
    }
}

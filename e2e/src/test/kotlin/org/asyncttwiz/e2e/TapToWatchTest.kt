package org.asyncttwiz.e2e

import androidx.compose.ui.test.performClick
import org.asyncttwiz.protocol.game.GameDevice
import org.asyncttwiz.protocol.game.PlaySettings
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Owner playtest 2026-11-06: with "<name> has made moves · Tap to watch" showing, any tap on the table starts the
 * playback, not only a tap on the text: the battlefields, the combat band and the cards on them. The hand keeps its own
 * taps.
 */
class TapToWatchTest : E2e() {
    /** Alice plays a Mountain with Bob along, then Bob leaves and she casts an Ember Herald (assuming his pass, "Always
     * assume resolves"); Bob comes back to the catch-up's centre text. */
    private fun bobComesBackToTheCatchUp() {
        startGame(TestDeck.thirds(TestCards.herald), TestDeck.glimmers(), aliceSettings = PlaySettings(alwaysAssumeResolves = true),
            deal = { a, b -> "Mountain" in hand(a) && "Ember Herald" in hand(a) && "Idle Glimmer" in hand(b) })
        bothOpen()
        bothKeep(bobAway = false)
        alice.playFromHand("Mountain", this)
        waitUntil("the Mountain on both tables", along = listOf(bob)) {
            alice.field("Mountain").isNotEmpty() && alice.inOwnMain() && bob.showsOnBattlefield("Mountain", alice)
        }
        away(bob, alice)
        alice.playFromHand("Ember Herald", this)
        waitUntil("the Herald on Alice's battlefield, all of it on the server") {
            alice.field("Ember Herald").isNotEmpty() && alice.inOwnMain() && alice.engine { device.session.pending.isEmpty() }
        }
        bob.back()
        waitUntil("Bob's catch-up with its centre text") { bob.status == GameDevice.Status.CatchUp && bob.showsWatch() }
    }

    private fun bobWatches() = waitUntil("Bob's playback started") {
        bob.status.let { it is GameDevice.Status.Playing || it is GameDevice.Status.Stopped } && !bob.showsWatch()
    }

    // HIS-094 (owner playtest 2026-11-06): a tap on the empty battlefield, away from the centre text, starts the playback
    @Test fun aTapOnTheTableStartsThePlayback() {
        bobComesBackToTheCatchUp()
        val head = bob.shown.localHead
        bob.tapTable()
        bobWatches()
        waitUntil("Alice's cast played back") { bob.shown.localHead != head }
    }

    // HIS-094 (owner playtest 2026-11-06): a tap on a battlefield card (Alice's Mountain) starts the playback; a tap on a
    // hand card before it doesn't (the hand keeps its own taps: it raises the card, ZON-079)
    @Test fun aTapOnACardStartsThePlaybackButTheHandKeepsItsTaps() {
        bobComesBackToTheCatchUp()
        bob.handCard("Idle Glimmer").performClick()
        repeat(3) { run() }
        assertEquals(GameDevice.Status.CatchUp, bob.status, "a hand tap doesn't start the playback")
        assertTrue(bob.showsWatch())
        bob.battlefieldCard("Mountain", owner = alice).performClick()
        bobWatches()
    }
}

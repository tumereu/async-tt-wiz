package org.asyncttwiz.e2e

import androidx.compose.ui.test.performClick
import org.asyncttwiz.protocol.game.GameDevice
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * Owner playtest 2026-11-05: tapping Play on a catch-up waited a long time before anything happened, even before the
 * button turned to Pause. The button turns at once, whatever the engine thread is doing, and the first event is shown
 * with the first board after the tap: no playback delay before it (HIS-023: the first step right away).
 */
class PlayStartsAtOnceTest : E2e() {
    private val deck = TestDeck("Mountains", listOf(TestCards.mountain to 28, TestCards.spark to 12))

    // HIS-013, HIS-094, HIS-103, HIS-023: Bob comes back to Alice's first turn played while he was away: paused; one tap
    // on Play reads Pause in the next frame, before his engine thread has answered, and the first board his engine
    // publishes after it already shows Alice's first event
    @Test fun playTurnsToPauseAtOnceAndTheFirstEventShowsWithTheFirstBoard() {
        startGame(deck, deck, deal = { a, _ -> "Mountain" in hand(a) })
        bothOpen()
        bob.tap("Keep")
        waitUntil("Bob's keep on Alice's table") { alice.shown.held.any { it.author == bob.id } }
        away(bob, alice)
        alice.tap("Keep")
        waitUntil("Alice's Main 1") { alice.inOwnMain() }
        alice.playFromHand("Mountain", this)
        waitUntil("the Mountain on the battlefield, all of it on the server") {
            alice.field("Mountain").isNotEmpty() && alice.inOwnMain() && alice.engine { device.session.pending.isEmpty() }
        }

        val held = HeldWorker()
        bob = restart(bob, held)
        bob.openGame(game, this)
        waitUntil("Bob's catch-up") { bob.status == GameDevice.Status.CatchUp }
        assertEquals("Play", bob.playbackLabel())
        val head = bob.shown.localHead
        // Every board Bob's engine publishes from here on, as it reaches his screen
        val boards = mutableListOf<Pair<GameDevice.Status, String?>>()
        held.observe = { bob.game?.shown?.let { boards += it.status to it.localHead } }

        // His engine thread is busy: nothing it publishes reaches the screen yet
        held.holding = true
        bob.playbackButton().performClick()
        run()
        assertEquals("Pause", bob.playbackLabel(), "the button turns at once")
        held.release()
        waitUntil("the first board after Play") { boards.isNotEmpty() }
        val (status, shown) = boards.first()
        assertTrue(status != GameDevice.Status.CatchUp, "playing: $status")
        assertNotEquals(head, shown, "the first board after Play already shows Alice's first event")
        waitUntil("Bob caught up") {
            if (bob.status is GameDevice.Status.Stopped) bob.playAlong()
            !bob.shown.behind && bob.status !is GameDevice.Status.Playing
        }
    }
}

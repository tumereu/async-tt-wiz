package org.asyncttwiz.e2e

import org.asyncttwiz.android.AppLog
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Found by the merge gate (2026-11-21, CatchUpPlaybackTest.anEmptyCombatIsSkipped, its first failure): Bob kept and Alice,
 * whose phone hadn't heard of his keep yet, took a mulligan on the game's start. The server had his keep first, so her phone
 * made her mulligan again on it, quietly (TRN-027: each player's mulligans are their own), with the same new hand. Her Keep,
 * tapped on the board of her own mulligan just as the board with her mulligan made again came, said "That wasn't taken: the
 * game moved on first" and left her deciding on her opening again. Alice's slow downlink holds Bob's keep back
 * ([NetworkLink.holdIncoming]), and her phone holds what its engine thread shows ([HeldWorker]) while it makes her mulligan
 * again.
 */
class OpeningRaceTest : E2e() {
    private val held = HeldWorker()

    // TRN-027, SYS-135, SYS-077: Alice's Keep tapped on the board of her own mulligan while her phone made that mulligan again on
    // Bob's keep (her same hand, her same opening decision waiting there) is taken there, with no note
    @Test fun aKeepTappedAsTheMulliganIsMadeAgainOnTheOtherPlayersKeepIsTaken() {
        startGame(aliceWorker = held)
        bothOpen()
        val start = alice.engine { device.session.tip }
        // Bob's keep reaches the server; Alice's phone hears nothing of it yet
        alice.link.holdIncoming()
        bob.tap("Keep")
        waitUntil("Bob's keep on the server") { bob.engine { device.session.pending.isEmpty() && device.session.events.any { it.author == bob.id && keeps(it) } } }
        val bobsKeep = bob.engine { device.session.events.last { it.author == bob.id && keeps(it) }.id }
        val first = alice.state.player(alice.me).hand.toList()
        alice.tap("Mulligan")
        waitUntil("Alice's new hand") { alice.state.player(alice.me).hand.toList() != first && alice.showsControl("Keep") }
        assertEquals(start, alice.engine { device.session.pending.first { it.author == alice.id }.parentId },
            "her mulligan made on the game's start: Bob's keep hasn't reached her")
        val hand = alice.state.player(alice.me).hand.toList()
        // Bob's keep and the server's refusal of her mulligan reach her phone, which makes her mulligan again on his keep; her
        // table still shows the board of her own mulligan
        held.holding = true
        alice.link.releaseIncoming()
        waitUntil("her mulligan made again on Bob's keep") {
            alice.engine { (device.session.events + device.session.pending).any { it.author == alice.id && it.parentId == bobsKeep } }
        }
        assertEquals(hand, alice.state.player(alice.me).hand.toList(), "her table still shows the board of her own mulligan")
        alice.tap("Keep")
        held.release()
        waitUntil("Alice's keep on the server") {
            alice.engine { device.session.pending.isEmpty() && device.session.events.any { it.author == alice.id && keeps(it) } }
        }
        assertFalse(alice.showsText("That wasn't taken: the game moved on first. The table shows where it is now."), "no note: ${alice.screen()}")
        val since = AppLog.text().lines()
        assertTrue(since.none { "Own keep not taken" in it }, "her keep not refused: ${since.filter { "keep" in it }}")
        assertTrue(since.any { "Own keep made on board" in it }, "made on the newer board: ${since.filter { "keep" in it }}")
        assertEquals(hand, alice.engine { device.model.engine.player(alice.me).hand.toList() }, "she kept the hand she saw")
    }
}

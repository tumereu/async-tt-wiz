package org.asyncttwiz.e2e

import arcanum.engine.input.Input
import org.asyncttwiz.protocol.game.GameDevice
import org.junit.Test
import kotlin.test.assertTrue

/**
 * Owner, 2026-11-05 (from the Analysis): a later table note no longer replaces an earlier one at once; the notes show
 * one after another (SYS-078).
 */
class NotesInTurnTest : E2e() {
    private val branched = "responded — your later actions were taken back"
    private val notTaken = "That wasn't taken"

    // SYS-078, HIS-097, SYS-012: Alice plays on while Bob is away; Bob comes back and answers her Herald with an Idle
    // Glimmer (a branch) while her pass on the line he discarded is on its way to the server, which refuses it: her table
    // tells the branch first and keeps it shown, then "That wasn't taken" (before, the second replaced the first at once)
    @Test fun aBranchNoteThenARefusalShowOneAfterTheOther() {
        aliceCastsAHeraldWhileBobIsAway(endTurn = false)
        alice.link.holdIncoming()
        bob.back()
        waitUntil("Bob's playback stopping at Alice's Herald") {
            if (bob.status == GameDevice.Status.CatchUp) bob.once { bob.tapToWatch() }
            bob.status is GameDevice.Status.Stopped && bob.state.stack.isNotEmpty()
        }
        val cast = bob.game!!.send(listOf(Input.Cast(bob.me, bob.hand("Idle Glimmer").first())))
        assertTrue(cast is GameDevice.Acted.Sent, "Bob's Glimmer: $cast")
        waitUntil("Bob's branch on the server") { bob.engine { device.session.pending.isEmpty() } }
        // Alice, hearing nothing of it, goes on on the line Bob discarded
        alice.tap("To combat")
        waitUntil("Alice's pass on its way") { alice.engine { device.session.pending.isNotEmpty() } }
        alice.link.releaseIncoming()
        waitUntil("the branch told on Alice's table, the refusal in") {
            alice.told(branched) && alice.engine { device.repro(codec).log }.any { it.startsWith("refused Submit") }
        }
        // Shown for its while: the refusal waits for it
        repeat(3) { run(skip = java.time.Duration.ofMillis(800)) }
        assertTrue(alice.showsText(branched, substring = true), "the branch note still shows: ${alice.screen()}")
        assertTrue(!alice.told(notTaken), "the refusal waits for the branch note")
        waitUntil("the refusal told after it") { alice.told(notTaken) }
        assertTrue(alice.shownTexts.indexOfFirst { branched in it } < alice.shownTexts.indexOfFirst { notTaken in it })
    }
}

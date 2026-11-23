package org.asyncttwiz.e2e

import arcanum.engine.input.Input
import org.asyncttwiz.protocol.game.GameDevice
import org.junit.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Owner, 2026-11-05 (from the Analysis): a later table note no longer replaces an earlier one at once; the notes show
 * one after another (SYS-078; the queue itself: TableNotesTest). Owner, 2026-11-22: a branch shows no note (HIS-185),
 * while the refusal's "That wasn't taken" still does (SYS-012).
 */
class NotesInTurnTest : E2e() {
    private val notTaken = "That wasn't taken"

    // SYS-078, HIS-185, HIS-097, SYS-012, SYS-138: Alice plays on while Bob is away; Bob comes back and answers her Herald with an
    // Idle Glimmer (a branch) while her pass on the line he discarded is on its way to the server, which refuses it: her
    // table shows no branch note, only "That wasn't taken"
    @Test fun aBranchShowsNoNoteWhileTheRefusalStillDoes() {
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
        waitUntil("the branch on Alice's device, the refusal in") {
            alice.branchLogged() && alice.engine { device.repro(codec).log }.any { it.startsWith("refused Submit") }
        }
        waitUntil("the refusal told") { alice.told(notTaken) }
        assertFalse(alice.toldATakeBack(bob), "no branch note: ${alice.shownTexts}")
    }
}

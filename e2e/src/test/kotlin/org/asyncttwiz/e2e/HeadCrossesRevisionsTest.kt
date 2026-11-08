package org.asyncttwiz.e2e

import arcanum.api.TurnStep
import org.asyncttwiz.protocol.game.GameDevice
import org.junit.Test
import kotlin.test.assertEquals

/**
 * A full e2e run, 2026-11-08 (MainPhaseUndoTest timed out, "Bob having seen it"): a phone's own events were on the server
 * and the other phone had them, but the phone itself kept them as pending for as long as nobody made another event,
 * reporting what it had seen again and again, each report refused. A heartbeat's summary past the revisions it had received
 * made it ask for the game's head; that head, read before the last revisions were published, ended on the phone's own
 * pending event, so it loaded nothing and yet fenced off the revisions on their way, which were the confirmations.
 *
 * The race staged: Alice's socket hands her the revisions only after a head she asked for ([HeadFirstConnection]).
 */
class HeadCrossesRevisionsTest : E2e() {
    /** What the server refused of this phone's reports more than once, as its record has them. */
    private fun repeatedRefusals(player: E2ePlayer): Map<String, Int> =
        player.game!!.repro()!!.log.filter { it.startsWith("refused EngineSeen") }.groupingBy { it }.eachCount().filterValues { it > 1 }

    // SYS-014, HIS-032, SYS-009: a head that overtakes the revisions of Alice's own events loses none of them: they confirm her
    // events, she and Bob end on the same head, and no report goes out again to be refused
    @Test fun aHeadAheadOfTheRevisionsOfTheOwnEventsLosesNone() {
        startGame(bobStarts = true, deal = { _, b -> "Mountain" in hand(b) })
        bothOpen()
        alice.tap("Keep")
        bob.tap("Keep")
        waitUntil("Bob's Main 1", along = listOf(alice)) { bob.inOwnMain() }
        bob.hold("To combat")
        waitUntil("Alice's Main 1 after her draw", along = listOf(bob)) { alice.inOwnMain() && alice.state.turn.active == alice.me }
        waitUntil("both settled") { alice.settled(this) && bob.settled(this) }

        val turn = alice.state.turn.number
        alice.connection.holdRevisions()
        alice.tap("To combat")
        waitUntil("Alice past her Main 1") { alice.state.turn.number == turn && alice.state.turn.step != TurnStep.PrecombatMain && alice.status == GameDevice.Status.YourMove }
        val tip = alice.engine { device.session.tip }
        waitUntil("Bob holding all her events") { bob.engine { device.session.head } == tip && bob.engine { device.session.pending.isEmpty() } }
        alice.askHead()
        waitUntil("the head having overtaken them") { alice.connection.overtaken }

        waitUntil("both on the same head, her events confirmed") {
            alice.settled(this) && alice.engine { device.session.head } == tip && alice.engine { device.localHeads[bob.me] } == tip
        }
        assertEquals(emptyMap(), repeatedRefusals(alice))
    }
}

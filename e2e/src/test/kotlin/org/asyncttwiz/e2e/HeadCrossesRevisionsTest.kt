package org.asyncttwiz.e2e

import arcanum.api.TurnStep
import org.asyncttwiz.protocol.game.GameDevice
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

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

    // HIS-006 (full e2e run 2026-11-13, PassesAndUndoTest in the opening: a head past her revisions loaded Alice's game again
    // and her live table paused for the centre text): a head that overtakes Bob's events on their way to Alice's live table
    // leaves it live: his Mountain shows as it would have, with no catch-up pause
    @Test fun aHeadOvertakingRevisionsLeavesTheLiveTableLive() {
        startGame(bobStarts = true, deal = { _, b -> "Mountain" in hand(b) })
        bothOpen()
        alice.tap("Keep")
        bob.tap("Keep")
        waitUntil("Bob's Main 1", along = listOf(alice)) { bob.inOwnMain() }
        waitUntil("both settled") { alice.settled(this) && bob.settled(this) }

        var paused = false
        everyRun += { if (alice.game?.shown?.status == GameDevice.Status.CatchUp) paused = true }
        alice.connection.holdRevisions()
        bob.playFromHand("Mountain", this)
        waitUntil("Bob's Mountain on the server") { bob.field("Mountain").isNotEmpty() && bob.engine { device.session.pending.isEmpty() } }
        alice.askHead()
        waitUntil("the head having overtaken it") { alice.connection.overtaken }
        waitUntil("Bob's Mountain on Alice's live table") { alice.field("Mountain", bob.me).isNotEmpty() }
        assertFalse(paused, "Alice's live table never paused for the centre text")
    }

    // SYS-135, SYS-138, HIS-006 (merge gate 2026-11-24, CoreLoopTest.theStepArrowGoesToCombatAndDeclares: Alice's own To combat
    // at the start of her turn refused with "That wasn't taken: the game moved on first."): the head her phone asked for was read
    // after a revision committed past the replay, whose publication came after it. Her phone loaded the game again from that
    // head, which took back the board she had just been shown and judged her press on it stale. Staged here: Bob's Mountain
    // reaches Alice's phone after the head she asks for. Her game isn't loaded again and no shown board is taken back: the
    // revisions are asked for again and play live; then, in her own turn, her To combat is taken with no refusal
    @Test fun aHeadOvertakingARevisionLoadsNothingAgainAndTakesNothingBack() {
        startGame(bobStarts = true, deal = { _, b -> "Mountain" in hand(b) })
        bothOpen()
        alice.tap("Keep")
        bob.tap("Keep")
        waitUntil("Bob's Main 1", along = listOf(alice)) { bob.inOwnMain() }
        waitUntil("both settled") { alice.settled(this) && bob.settled(this) }

        val before = alice.engine { device }
        val takeBacks = alice.engine { this.takeBacks }
        alice.connection.holdRevisions()
        bob.playFromHand("Mountain", this)
        waitUntil("Bob's Mountain on the server") { bob.field("Mountain").isNotEmpty() && bob.engine { device.session.pending.isEmpty() } }
        alice.askHead()
        waitUntil("the head having overtaken it") { alice.connection.overtaken }
        waitUntil("Bob's Mountain on Alice's live table") { alice.field("Mountain", bob.me).isNotEmpty() && alice.settled(this) }
        assertTrue(alice.engine { device } === before, "Alice's game not loaded again from the head")
        assertEquals(takeBacks, alice.engine { this.takeBacks }, "no board of hers taken back: ${alice.engine { this.lastTakeBack }}")

        bob.hold("To combat")
        waitUntil("Alice's Main 1", along = listOf(bob)) { alice.inOwnMain() && alice.state.turn.active == alice.me }
        assertEquals("To combat", alice.stepArrow())
        alice.tap("To combat")
        waitUntil("Alice past her Main 1", along = listOf(bob)) { alice.state.turn.step != TurnStep.PrecombatMain }
        assertFalse(alice.refusalSeen, "no refusal: ${alice.shownTexts}")
    }
}

package org.asyncttwiz.e2e

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.performClick
import arcanum.api.TurnStep
import org.asyncttwiz.android.AppLog
import org.asyncttwiz.protocol.CombatStackRules
import org.asyncttwiz.protocol.game.GameDevice
import org.junit.Test
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Fix (problem reports WCRF3Q, R8XVPA, 2026-11-11): Bob staged a block with his token while Alice already waited for his
 * blocks; she assumed he had none, so his phone stopped at her assumption and he declared his block there. That declaration
 * couldn't also end the shared block staging, so the staging stayed on the table; the token died in the combat. In Alice's
 * next attack with the same creature the table showed the old staging as Bob's blocks again, with the token that no longer
 * existed, and both phones crashed on every opening of the game.
 */
class BlockAtStopTest : E2e() {
    private fun goOn(player: E2ePlayer) {
        if (!player.settled(this)) return
        player.once {
            if (player.status == GameDevice.Status.YourMove || player.status is GameDevice.Status.Stopped)
                player.stepArrow()?.takeIf { it in listOf("Pass", "Next", "Resolve") }?.let(player::tap)
        }
    }

    /** Alice declares an attack with her Cinder Brute and both go on until Bob declares blockers. */
    private fun aliceAttacks() {
        alice.tap("To combat")
        waitUntil("Alice declaring attackers", along = listOf(bob)) { alice.state.turn.step == TurnStep.DeclareAttackers }
        alice.stageAttacker("Cinder Brute", this)
        waitUntil("the Brute staged") { alice.stepArrow() == "Attack (1)" }
        alice.declare("Attack (1)", this)
        waitUntil("Bob declaring blockers") {
            goOn(alice); goOn(bob)
            bob.status == GameDevice.Status.YourMove && bob.state.turn.step == TurnStep.DeclareBlockers &&
                bob.stepArrow()?.let { it == "No blocks" || it.startsWith("Block (") } == true
        }
    }

    private fun bobsToken() = bob.state.battlefield.any { bob.state.obj(it)?.token == true }

    /** Bob taps his Herald's token on his battlefield (picking it as the blocker). */
    private fun tapBobsToken() {
        val token = bob.nodes(hasContentDescription(", owner ${bob.name}, ", substring = true)).fetchSemanticsNodes().first { n ->
            val what = n.config.getOrNull(SemanticsProperties.ContentDescription)?.joinToString().orEmpty()
            !what.startsWith("Ember Herald") && !what.startsWith("Mountain") && !what.startsWith("Projection") &&
                bob.battlefieldBounds().contains(n.boundsInRoot.center)
        }
        bob.node(SemanticsMatcher("node #${token.id}") { it.id == token.id }).performClick()
    }

    // CMB-034, CMB-040
    @Test fun aBlockDeclaredAtAnAssumptionLeavesNoStagingForTheNextCombat() {
        startGame(TestDeck("Brutes", listOf(TestCards.mountain to 20, TestCards.brute to 20)), TestDeck.thirds(TestCards.herald),
            deal = { a, b -> "Cinder Brute" in hand(a) && "Mountain" in hand(b) && "Ember Herald" in hand(b) })
        bothOpen()
        bothKeep(bobAway = false)
        // Turn 1: Alice's Brute (2/2, trample); turn 2: Bob's Herald and its 1/1 token
        alice.playFromHand("Cinder Brute", this)
        waitUntil("the Brute on the battlefield", along = listOf(bob)) { alice.field("Cinder Brute").isNotEmpty() && alice.inOwnMain() }
        alice.hold("To combat")
        waitUntil("Bob's Main 1", along = listOf(alice)) { bob.inOwnMain() }
        bob.playFromHand("Mountain", this)
        waitUntil("Bob's Mountain", along = listOf(alice)) { bob.field("Mountain").isNotEmpty() && bob.inOwnMain() }
        bob.playFromHand("Ember Herald", this)
        waitUntil("Bob's Herald and its token", along = listOf(alice)) {
            bob.field("Ember Herald").isNotEmpty() && bobsToken() && bob.inOwnMain()
        }
        bob.hold("To combat")
        // Turn 3: Alice attacks; Bob stages his token as the Brute's blocker, which reaches the table
        waitUntil("Alice's second Main 1", along = listOf(bob)) { alice.inOwnMain() && alice.state.turn.number >= 3 }
        aliceAttacks()
        bob.combatWorkedOut(this)
        tapBobsToken()
        run(skip = java.time.Duration.ofSeconds(1))
        bob.bandCard("Cinder Brute", owner = alice).performClick()
        waitUntil("Bob's block staged on the table, on Alice's phone too") {
            bob.stepArrow() == "Block (1)" && bob.settled(this) && !alice.shown.board.engine!!.table.blockDraft.isNullOrEmpty()
        }
        val staged = bob.shown.board.engine!!.table.blockDraft.orEmpty()
        assertTrue(staged.size == 1 && bob.state.obj(staged.single().blocker)?.token == true, "the token blocks: $staged")
        // Alice assumes he has no blockers; his phone stops at her assumption, and he declares his block there
        waitUntil("Alice's Assume no blockers") { alice.settled(this) && alice.showsControl("Assume no blockers") }
        alice.tap("Assume no blockers")
        waitUntil("Bob stopped at Alice's assumption with his block") {
            bob.status is GameDevice.Status.Stopped && bob.stepArrow() == "Block (1)" && bob.settled(this)
        }
        bob.tap("Block (1)")
        // The combat damage (Alice's division of the Brute's trample damage, when it's asked): the token dies, the Brute lives
        waitUntil("the token dead in the combat", along = listOf(bob)) {
            goOn(alice); goOn(bob)
            if (alice.settled(this) && alice.showsControl("OK") && alice.shows(hasContentDescription("Division of combat damage")))
                alice.tap("OK")
            !bobsToken() && alice.field("Cinder Brute").isNotEmpty()
        }
        // Turn 4: Bob's; turn 5: Alice attacks with the same Brute and Bob declares blockers again
        waitUntil("Bob's second Main 1", along = listOf(alice)) { goOn(alice); bob.inOwnMain() && bob.state.turn.number >= 4 }
        bob.hold("To combat")
        waitUntil("Alice's third Main 1", along = listOf(bob)) { alice.inOwnMain() && alice.state.turn.number >= 5 }
        aliceAttacks()
        // No staged block from the earlier combat on either phone, every combat copy a card the board has; neither crashed
        waitUntil("both phones settled") { alice.settled(this) && bob.settled(this) }
        run(skip = java.time.Duration.ofSeconds(1))
        for (player in listOf(alice, bob)) {
            val board = player.shown.board.board
            val pairs = board.combat?.pairs.orEmpty()
            assertTrue(pairs.isNotEmpty() && pairs.all { it.blockers.isEmpty() }, "no blocker in ${player.name}'s band: $pairs")
            for (occurrence in CombatStackRules.occurrences(board.combat))
                assertNotNull(CombatStackRules.inspection(board, occurrence.id), "${player.name}'s combat copy ${occurrence.id}")
        }
        assertTrue(bob.stepArrow() == "No blocks", "Bob declares his blocks afresh: ${bob.stepArrow()}")
        assertTrue(AppLog.text().lines().none { "E/crash" in it }, "no crash")
        assertFalse(alice.refusalSeen || bob.refusalSeen, "no refusal")
    }
}

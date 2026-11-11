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
 * existed, and both phones crashed on every opening of the game. Follow-up: a declaration at such a stop now ends the
 * shared block staging, so no old block shows in a later combat, also when its blocker still lives.
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

    /** [player]'s combat copy of Bob's token, tapped once (a first tap selects it, the next removes it, CMB-032). */
    private fun tapTokensCopy(player: E2ePlayer) {
        val token = player.nodes(hasContentDescription("Projection, ", substring = true)).fetchSemanticsNodes().first { n ->
            val what = n.config.getOrNull(SemanticsProperties.ContentDescription)?.joinToString().orEmpty()
            "owner ${bob.name}, " in what && !what.startsWith("Projection, Ember Herald") && !what.startsWith("Projection, Cinder Brute")
        }
        run(skip = java.time.Duration.ofSeconds(1))
        player.node(SemanticsMatcher("node #${token.id}") { it.id == token.id }).performClick()
        run(skip = java.time.Duration.ofSeconds(1))
    }

    /**
     * Turns 1 to 3: Alice's Brute, Bob's Herald and its token; Alice attacks and Bob stages his token as the Brute's blocker,
     * which reaches the table; Alice assumes he has no blockers, and his phone stops at her assumption with his block.
     */
    private fun toBobsStopWithHisStagedBlock() {
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
        // Alice assumes he has no blockers; his phone stops at her assumption, with his block
        waitUntil("Alice's Assume no blockers") { alice.settled(this) && alice.showsControl("Assume no blockers") }
        alice.tap("Assume no blockers")
        waitUntil("Bob stopped at Alice's assumption with his block") {
            bob.status is GameDevice.Status.Stopped && bob.stepArrow() == "Block (1)" && bob.settled(this)
        }
    }

    /** The combat goes on to its damage ([done] once it is dealt), Alice dividing the Brute's trample damage when asked. */
    private fun throughTheDamage(what: String, done: () -> Boolean) {
        waitUntil(what, along = listOf(bob)) {
            goOn(alice); goOn(bob)
            if (alice.settled(this) && alice.showsControl("OK") && alice.shows(hasContentDescription("Division of combat damage")))
                alice.tap("OK")
            done()
        }
    }

    /** Turns 4 and 5: Alice attacks with the same Brute again; no block from the earlier combat shows on either phone (no
     * staging left on the table), every combat copy is a card the board has, and neither phone crashed. */
    private fun theNextAttackShowsNoOldBlock() {
        waitUntil("no block staging left on either table", along = listOf(alice, bob)) {
            goOn(alice)
            alice.shown.board.engine!!.table.blockDraft == null && bob.shown.board.engine!!.table.blockDraft == null
        }
        waitUntil("Bob's second Main 1", along = listOf(alice)) { goOn(alice); bob.inOwnMain() && bob.state.turn.number >= 4 }
        bob.hold("To combat")
        waitUntil("Alice's third Main 1", along = listOf(bob)) { alice.inOwnMain() && alice.state.turn.number >= 5 }
        aliceAttacks()
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

    // CMB-034, CMB-040, CMB-043: Bob declares his staged block at the stop (a branch); the token dies in the combat
    @Test fun aBlockDeclaredAtAnAssumptionLeavesNoStagingForTheNextCombat() {
        toBobsStopWithHisStagedBlock()
        bob.tap("Block (1)")
        throughTheDamage("the token dead in the combat") { !bobsToken() && alice.field("Cinder Brute").isNotEmpty() }
        theNextAttackShowsNoOldBlock()
    }

    // CMB-034, CMB-040, CMB-043 (follow-up to WCRF3Q): at the stop Bob removes his staged block (on his phone only) and
    // declares no blockers, confirming Alice's assumption; his token lives, and its old staged block, were it left on the
    // table, would show as a block when the same Brute attacks again
    @Test fun noBlockersDeclaredAtAnAssumptionLeavesNoPhantomBlockOfALivingBlocker() {
        toBobsStopWithHisStagedBlock()
        tapTokensCopy(bob)
        assertTrue(bob.stepArrow() == "Block (1)", "still blocking after the first tap: ${bob.stepArrow()}")
        tapTokensCopy(bob)
        waitUntil("Bob's block removed on his phone") { bob.stepArrow() == "No blocks" && bob.settled(this) }
        bob.tap("No blocks")
        throughTheDamage("the Brute's damage dealt to Bob") { bob.state.player(arcanum.engine.state.PlayerId(bob.id)).life < 20 }
        assertTrue(bobsToken(), "the token lives")
        theNextAttackShowsNoOldBlock()
    }
}

package org.asyncttwiz.e2e

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.performTouchInput
import arcanum.api.TurnStep
import arcanum.engine.Engine
import org.asyncttwiz.android.AppLog
import org.asyncttwiz.protocol.game.GameDevice
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Fix (owner, 2026-11-10): as the defender, Bob could drag his creatures into the band as blockers during Alice's declare
 * attackers step, and that caused an Automatum error. Before his declare blockers step his own creatures can't be dragged as
 * blockers (a drag on one pans the table) and nothing of it reaches Automatum; once he declares blockers the same drag
 * blocks.
 */
class EarlyBlockerDragTest : E2e() {
    private fun readyCubs() = alice.state.let { s ->
        val view = Engine.view(s)
        alice.field("Ember Cub").filter { s.obj(it)?.tapped == false && view[it]?.summoningSick == false }
    }

    private fun goOn(player: E2ePlayer) = player.once {
        if (player.status == GameDevice.Status.YourMove || player.status is GameDevice.Status.Stopped) player.stepArrow()?.takeIf { it in listOf("Pass", "Next", "Resolve") }?.let(player::tap)
    }

    private fun castCub() {
        val before = alice.field("Ember Cub").size
        alice.playFromHand("Ember Cub", this)
        waitUntil("the Cub on Alice's battlefield", along = listOf(bob)) { alice.field("Ember Cub").size == before + 1 && alice.inOwnMain() }
    }

    /** Alice has two ready Ember Cubs and Bob an Ember Herald and its token; Alice is declaring attackers with none staged. */
    private fun aliceDeclaring() {
        startGame(TestDeck.thirds(TestCards.cub), TestDeck.thirds(TestCards.herald),
            deal = { a, b -> "Mountain" in hand(a) && hand(a).count { it == "Ember Cub" } >= 2 && "Mountain" in hand(b) &&
                "Ember Herald" in hand(b) })
        bothOpen()
        bothKeep(bobAway = false)
        alice.playFromHand("Mountain", this)
        waitUntil("the Mountain on the battlefield", along = listOf(bob)) { alice.field("Mountain").isNotEmpty() && alice.inOwnMain() }
        castCub()
        alice.hold("To combat")
        waitUntil("Bob's Main 1", along = listOf(alice)) { bob.inOwnMain() }
        bob.playFromHand("Mountain", this)
        waitUntil("Bob's Mountain", along = listOf(alice)) { bob.field("Mountain").isNotEmpty() && bob.inOwnMain() }
        bob.playFromHand("Ember Herald", this)
        waitUntil("Bob's Herald and its token", along = listOf(alice)) {
            bob.field("Ember Herald").isNotEmpty() && bob.state.battlefield.any { bob.state.obj(it)?.token == true } && bob.inOwnMain()
        }
        bob.hold("To combat")
        waitUntil("Alice's second Main 1", along = listOf(bob)) { alice.inOwnMain() && alice.state.turn.number >= 3 }
        castCub()
        alice.hold("To combat")
        waitUntil("Alice's Main 1 with two ready Cubs", along = listOf(bob)) { alice.inOwnMain() && readyCubs().size == 2 }
        alice.tap("To combat")
        waitUntil("Alice declaring attackers", along = listOf(bob)) {
            alice.state.turn.step == TurnStep.DeclareAttackers && alice.stepArrow() == "No attack"
        }
    }

    /** A finger drags [from] by [by] (px) and lifts. */
    private fun drag(from: SemanticsNodeInteraction, by: Offset) {
        from.performTouchInput {
            down(center)
            repeat(10) { moveBy(by / 10f) }
            up()
        }
    }

    /** The offset from Bob's Ember Herald on his battlefield to the blockers' row under the copy of Alice's [index]th Cub in
     * his (mirrored) band: where a blocker of it goes (CMB-007). */
    private fun toBlockerRow(index: Int = 0): Offset {
        val herald = bob.battlefieldCard("Ember Herald").fetchSemanticsNode().boundsInRoot.center
        val cub = bob.bandCard("Ember Cub", owner = alice, index = index).fetchSemanticsNode().boundsInRoot
        return Offset(cub.center.x, cub.center.y + cub.height * 1.6f) - herald
    }

    /** Bob's finger goes from his Herald by [by] (px): it isn't picked up (the finger pans the table, as far as it pans). */
    private fun pansWithHerald(by: Offset) {
        drag(bob.battlefieldCard("Ember Herald"), by)
        run(skip = java.time.Duration.ofSeconds(1))
    }

    /** The drags of Bob's [card] the app log tells of (every drag says where it went and what came of it). */
    private fun drags(card: String) = AppLog.text().lines().count { "Drag of [$card]" in it }

    /** Bob's drags so far didn't reach the combat: no drag at all (the table panned), no refusal said or shaken, no blocker
     * of his anywhere, and Alice's [attackers] as they were. */
    private fun nothingOfBobsInCombat(herald: String, attackers: Int) {
        assertEquals(0, drags(herald), "the Herald wasn't dragged: the table panned")
        assertFalse(bob.refusalSeen, "nothing refused: ${bob.shownTexts.takeLast(4)}")
        for (player in listOf(alice, bob)) {
            val table = player.shown.board.engine!!.table
            assertTrue(table.blockDraft.isNullOrEmpty(), "no blocker drafted on ${player.name}'s phone: ${table.blockDraft}")
            val pairs = player.shown.board.board.combat?.pairs.orEmpty()
            assertTrue(pairs.all { it.blockers.isEmpty() }, "no blocker in ${player.name}'s band: $pairs")
            assertEquals(attackers, pairs.flatMap { it.attackers }.size, "Alice's attackers in ${player.name}'s band: $pairs")
        }
    }

    // CMB-034, CMB-040 (owner, 2026-11-10): Bob's creatures can't be dragged as blockers before his declare blockers step: not
    // while Alice stages her attack (onto her staged Cub's copy, or into the blockers' row under it), nor in the declare
    // attackers step after her attack; nothing of it reaches Automatum. In his declare blockers step the same drag blocks
    @Test fun theDefendersCreaturesWaitForTheirDeclareBlockersStep() {
        aliceDeclaring()
        val herald = atw(bob.field("Ember Herald").single())
        alice.stageAttacker("Ember Cub", this)
        waitUntil("the Cub staged, on Bob's band too") {
            alice.shown.board.engine!!.table.attackDraft.orEmpty().size == 1 && bob.bandOpen() && bob.settled(this)
        }
        bob.combatWorkedOut(this)
        pansWithHerald(toBlockerRow())
        val at = bob.battlefieldCard("Ember Herald").fetchSemanticsNode().boundsInRoot.center
        pansWithHerald(bob.bandCard("Ember Cub", owner = alice).fetchSemanticsNode().boundsInRoot.center - at)
        waitUntil("both phones settled") { alice.settled(this) && bob.settled(this) }
        nothingOfBobsInCombat(herald, attackers = 1)
        assertEquals(1, alice.shown.board.engine!!.table.attackDraft.orEmpty().size, "Alice's staging as it was")
        assertEquals("Attack (1)", alice.stepArrow())
        // After the attack, still in the declare attackers step: Bob may respond (his phone stops at Alice's assumption)
        alice.declare("Attack (1)", this)
        waitUntil("Bob's move in the declare attackers step") {
            goOn(alice)
            (bob.status == GameDevice.Status.YourMove || bob.status is GameDevice.Status.Stopped) && bob.current()
        }
        assertEquals(TurnStep.DeclareAttackers, bob.state.turn.step)
        bob.combatWorkedOut(this)
        pansWithHerald(toBlockerRow())
        nothingOfBobsInCombat(herald, attackers = 1)
        // His own declare blockers step: the same drag blocks
        waitUntil("Bob declaring blockers") {
            goOn(alice); goOn(bob)
            bob.status == GameDevice.Status.YourMove && bob.stepArrow() == "No blocks"
        }
        bob.combatWorkedOut(this)
        drag(bob.battlefieldCard("Ember Herald"), toBlockerRow())
        waitUntil("Bob's block staged by the drag") { bob.stepArrow() == "Block (1)" }
        assertEquals(1, drags(herald), "the one drag of his blockers step")
        assertTrue(AppLog.text().lines().none { "couldn't work" in it || "couldn't go on" in it }, "no Automatum failure")
        assertFalse(alice.refusalSeen || bob.refusalSeen, "nothing refused")
    }
}

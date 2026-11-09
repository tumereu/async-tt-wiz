package org.asyncttwiz.e2e

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import arcanum.api.TurnStep
import arcanum.engine.Engine
import org.asyncttwiz.android.CHOOSE_BLOCKERS
import org.asyncttwiz.android.COMBAT_SLOT_TAG
import org.asyncttwiz.protocol.game.GameDevice
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Owner, 2026-11-08: attackers and blockers have a fairly symmetric UX. Alice's active lane has a dashed slot for the next
 * attacker, a drop target; for the whole declare blockers step each attacker has a dashed blocker slot, brighter on the
 * attackers the creature selected may block, taking a tap and a drop; "Choose blockers" sits on the band until the first
 * block. Assume mode has the same look.
 */
class CombatSlotsTest : E2e() {
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

    private fun E2ePlayer.slots(): List<SemanticsNode> = nodes(hasTestTag(COMBAT_SLOT_TAG)).fetchSemanticsNodes()
    private fun SemanticsNode.bright() = config.getOrNull(SemanticsProperties.StateDescription) == "may go here"
    private fun same(node: SemanticsNode) = SemanticsMatcher("node #${node.id}") { it.id == node.id }
    private fun E2ePlayer.slot(index: Int = 0): SemanticsNodeInteraction = node(same(slots()[index]))

    /** A finger drags the card [from] onto the combat slot [to]. */
    private fun drag(from: SemanticsNodeInteraction, to: SemanticsNode) {
        val start = from.fetchSemanticsNode().boundsInRoot.center
        val by: Offset = to.boundsInRoot.center - start
        from.performTouchInput {
            down(center)
            repeat(10) { moveBy(by / 10f) }
            up()
        }
    }

    /** Alice stages one Cub by a tap and drags the other onto the slot of her active lane, and declares; Bob reaches his blocks. */
    private fun aliceAttacksWithBothCubs(declare: () -> Unit = {}) {
        // CMB-001: collapsed, no slot; CMB-028: the band open, the active lane has one
        assertTrue(alice.slots().isEmpty(), "no slot in the collapsed band")
        alice.stageAttacker("Ember Cub", this)
        waitUntil("the first Cub staged") { alice.shown.board.engine!!.table.attackDraft.orEmpty().size == 1 && alice.slots().size == 1 }
        assertTrue(alice.shows(hasContentDescription("Attacker slot")), "the attacker slot: ${alice.screen()}")
        run(skip = java.time.Duration.ofSeconds(1))
        alice.combatWorkedOut(this)
        drag(alice.battlefieldCard("Ember Cub", index = 0), alice.slots().single())
        waitUntil("the second Cub staged by its drop on the slot") { alice.shown.board.engine!!.table.attackDraft.orEmpty().size == 2 }
        assertFalse(alice.refusalSeen, "the drop was taken")
        declare()
        alice.declare("Attack (2)", this)
    }

    // CMB-028, CMB-034, CMB-007, CMB-012: the attacker's slot takes a drop; each attacker has a blocker's slot for the whole
    // step with the words on the band; the creature picked brightens them; a tap on a slot blocks, a drop on one blocks, each
    // slot going as it fills and the words with the first block; the blocks are declared and the combat goes on
    @Test fun attackersAndBlockersGoIntoTheSlots() {
        aliceDeclaring()
        aliceAttacksWithBothCubs()
        waitUntil("Bob declaring blockers") {
            goOn(alice); goOn(bob)
            bob.status == GameDevice.Status.YourMove && bob.stepArrow() == "No blocks"
        }
        bob.combatWorkedOut(this)
        waitUntil("a slot over each attacker") { bob.slots().size == 2 }
        assertTrue(bob.showsText(CHOOSE_BLOCKERS), "the words on the band: ${bob.screen()}")
        assertTrue(bob.slots().none { it.bright() }, "dim with nothing picked")
        bob.pickBlocker("Ember Herald", this)
        waitUntil("the slots brighter for the creature picked") { bob.slots().all { it.bright() } }
        run(skip = java.time.Duration.ofSeconds(1)) // past the double-tap window
        bob.slot(0).performClick()
        waitUntil("Bob's first block staged") { bob.stepArrow() == "Block (1)" }
        waitUntil("the filled slot gone, the words too") { bob.slots().size == 1 && !bob.showsText(CHOOSE_BLOCKERS) }
        assertFalse(bob.refusalSeen, "the tap was taken")
        bob.combatWorkedOut(this)
        // His Herald's token, the other creature on his battlefield
        val token = bob.nodes(hasContentDescription(", owner ${bob.name}, ", substring = true)).fetchSemanticsNodes().first { n ->
            val what = n.config.getOrNull(SemanticsProperties.ContentDescription)?.joinToString().orEmpty()
            !what.startsWith("Ember Herald") && !what.startsWith("Mountain") && bob.battlefieldBounds().contains(n.boundsInRoot.center)
        }
        drag(bob.node(same(token)), bob.slots().single())
        waitUntil("Bob's second block staged by its drop on the slot") { bob.stepArrow() == "Block (2)" }
        assertTrue(bob.slots().isEmpty(), "no slot left")
        assertFalse(bob.refusalSeen, "the drop was taken")
        bob.declare("Block (2)", this)
        waitUntil("the blocks declared: no slot, no words", along = listOf(alice)) {
            goOn(alice)
            bob.state.turn.step != TurnStep.DeclareBlockers || bob.stepArrow().let { it != "Block (2)" }
        }
        assertTrue(bob.slots().isEmpty())
        assertTrue(alice.leaks.leaks.isEmpty() && bob.leaks.leaks.isEmpty(), "${alice.leaks.leaks} ${bob.leaks.leaks}")
    }

    // CMB-040: waiting for Bob's blocks Alice has the same look on her lanes: the slots, the words, a tap on a slot placing the
    // creature she picked; only her Assume button assumes
    @Test fun assumeModeHasTheSameLook() {
        aliceDeclaring()
        aliceAttacksWithBothCubs { away(bob, alice) }
        waitUntil("Alice in assume mode for Bob's blockers") { alice.showsControl("Assume no blockers") }
        alice.combatWorkedOut(this)
        waitUntil("a slot over each attacker") { alice.slots().size == 2 }
        assertTrue(alice.showsText(CHOOSE_BLOCKERS), "the words on the band: ${alice.screen()}")
        alice.battlefieldCard("Ember Herald", owner = bob).performClick()
        waitUntil("the slots brighter for the creature picked") { alice.slots().all { it.bright() } }
        run(skip = java.time.Duration.ofSeconds(1))
        alice.slot(0).performClick()
        waitUntil("the block placed") { alice.showsControl("Assume blockers") }
        waitUntil("the filled slot gone, the words too") { alice.slots().size == 1 && !alice.showsText(CHOOSE_BLOCKERS) }
        alice.tap("Assume blockers")
        waitUntil("the blocks assumed: no slot") { alice.slots().isEmpty() }
    }
}

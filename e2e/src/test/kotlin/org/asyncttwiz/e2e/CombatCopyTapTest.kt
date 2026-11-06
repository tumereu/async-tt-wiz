package org.asyncttwiz.e2e

import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.doubleClick
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import java.time.Duration
import kotlin.test.assertEquals
import arcanum.api.TurnStep
import arcanum.engine.Engine
import org.asyncttwiz.protocol.game.GameDevice
import org.junit.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Owner playtest 2026-11-06: tapping a combat copy showed "Rotation not queued. Reconnect and try again.": the old
 * by-hand rotation still answered a quick second tap on it. Alice stages her Ember Cub as an attacker and taps its copy in
 * the combat band twice in a row: the taps only select it and the double tap does nothing, with no message.
 *
 * Owner, 2026-11-06: a first tap on a combat copy only selects it; a tap on the selected copy unstages the attacker or
 * removes the blocker (also a blocker placed to assume), with no Combat menu in between.
 */
class CombatCopyTapTest : E2e() {
    // CMB-003, CMB-032, TBL-043: a double tap on a combat copy does nothing and says nothing
    @Test fun doubleTappingACombatCopySaysNothing() {
        startGame(TestDeck.thirds(TestCards.cub), deal = { a, _ -> "Mountain" in hand(a) && "Ember Cub" in hand(a) })
        bothOpen()
        bothKeep(bobAway = false)
        alice.playFromHand("Mountain", this)
        waitUntil("the Mountain on the battlefield", along = listOf(bob)) { alice.field("Mountain").isNotEmpty() && alice.inOwnMain() }
        alice.playFromHand("Ember Cub", this)
        waitUntil("the Cub on Alice's battlefield", along = listOf(bob)) { alice.field("Ember Cub").isNotEmpty() && alice.inOwnMain() }
        alice.hold("To combat")
        waitUntil("Alice's next Main 1 with a ready Cub", along = listOf(bob)) {
            alice.inOwnMain() && alice.state.turn.number >= 3 && alice.state.let { s ->
                alice.field("Ember Cub").any { s.obj(it)?.tapped == false && Engine.view(s)[it]?.summoningSick == false }
            }
        }
        alice.tap("To combat")
        waitUntil("Alice declaring attackers", along = listOf(bob)) {
            alice.state.turn.step == TurnStep.DeclareAttackers && alice.stepArrow() == "No attack"
        }
        alice.stageAttacker("Ember Cub", this)
        waitUntil("the Cub staged") { alice.shown.board.engine!!.table.attackDraft.orEmpty().size == 1 }
        run(skip = java.time.Duration.ofSeconds(1)) // past the double-tap window of the staging tap
        val copy = alice.node(hasContentDescription("Projection, Ember Cub, owner ${alice.name}, ", substring = true))
        copy.performTouchInput { doubleClick() }
        run(skip = java.time.Duration.ofSeconds(1))
        assertFalse(alice.told("Rotation not queued"), "no rotation message: ${alice.shownTexts}")
        assertFalse(alice.refusalSeen, "no refusal: ${alice.shownTexts}")
    }

    /** The combat copy of [owner]'s [name] on [player]'s screen (its own or the other player's). */
    private fun copyOf(player: E2ePlayer, name: String, owner: E2ePlayer) =
        player.node(hasContentDescription("Projection, $name, owner ${owner.name}, ", substring = true))

    /** A single tap on [player]'s [node], clear of the double-tap window of the tap before. */
    private fun singleTap(node: androidx.compose.ui.test.SemanticsNodeInteraction) {
        run(skip = Duration.ofSeconds(1))
        node.performClick()
        run(skip = Duration.ofSeconds(1))
    }

    // CMB-003, CMB-032, CMB-028, CMB-034, CMB-040: Alice's staged attacker's copy is selected by a tap and unstaged by the
    // next; staged again, she attacks; in assume mode a blocker she placed for Bob is selected, then removed; Bob's own
    // blocker's copy likewise; nobody sees a Combat menu
    @Test fun aCopysFirstTapSelectsItTheNextRemovesIt() {
        startGame(TestDeck.thirds(TestCards.cub), TestDeck.thirds(TestCards.cub),
            deal = { a, b -> "Mountain" in hand(a) && "Ember Cub" in hand(a) && "Mountain" in hand(b) && "Ember Cub" in hand(b) })
        bothOpen()
        bothKeep(bobAway = false)
        alice.playFromHand("Mountain", this)
        waitUntil("Alice's Mountain", along = listOf(bob)) { alice.field("Mountain").isNotEmpty() && alice.inOwnMain() }
        alice.playFromHand("Ember Cub", this)
        waitUntil("Alice's Cub", along = listOf(bob)) { alice.field("Ember Cub").isNotEmpty() && alice.inOwnMain() }
        alice.hold("To combat")
        waitUntil("Bob's Main 1", along = listOf(alice)) { bob.inOwnMain() }
        bob.playFromHand("Mountain", this)
        waitUntil("Bob's Mountain", along = listOf(alice)) { bob.field("Mountain").isNotEmpty() && bob.inOwnMain() }
        bob.playFromHand("Ember Cub", this)
        waitUntil("Bob's Cub", along = listOf(alice)) { bob.field("Ember Cub").isNotEmpty() && bob.inOwnMain() }
        bob.hold("To combat")
        waitUntil("Alice's next Main 1 with a ready Cub", along = listOf(bob)) {
            alice.inOwnMain() && alice.state.turn.number >= 3 && alice.state.let { s ->
                alice.field("Ember Cub").any { s.obj(it)?.tapped == false && Engine.view(s)[it]?.summoningSick == false }
            }
        }
        alice.tap("To combat")
        waitUntil("Alice declaring attackers", along = listOf(bob)) {
            alice.state.turn.step == TurnStep.DeclareAttackers && alice.stepArrow() == "No attack"
        }
        fun staged() = alice.shown.board.engine!!.table.attackDraft.orEmpty().size
        alice.stageAttacker("Ember Cub", this)
        waitUntil("the Cub staged") { staged() == 1 }
        // The copy's first tap selects it; the attacker stays staged; no menu
        singleTap(copyOf(alice, "Ember Cub", alice))
        assertEquals(1, staged(), "still staged after the first tap")
        assertFalse(alice.shows(hasContentDescription("Card action ring")), "no Combat menu")
        // The tap on the selected copy unstages it
        singleTap(copyOf(alice, "Ember Cub", alice))
        waitUntil("the Cub unstaged") { staged() == 0 && alice.stepArrow() == "No attack" }
        assertFalse(alice.shows(hasContentDescription("Card action ring")), "no Combat menu")
        // Staged again, the attack goes out; Alice's table is in assume mode while Bob declares blockers
        run(skip = Duration.ofSeconds(1))
        alice.stageAttacker("Ember Cub", this)
        waitUntil("the Cub staged again") { alice.stepArrow() == "Attack (1)" }
        alice.declare("Attack (1)", this)
        waitUntil("Bob declaring blockers, Alice assuming") {
            // Bob passes where he may respond to the attack, as his main button says
            bob.once {
                if (bob.status == GameDevice.Status.YourMove || bob.status is GameDevice.Status.Stopped)
                    bob.stepArrow()?.takeIf { it in listOf("Pass", "Next", "Resolve") }?.let(bob::tap)
            }
            alice.showsControl("Assume no blockers") && bob.status == GameDevice.Status.YourMove && bob.stepArrow() == "No blocks"
        }
        // CMB-040: Alice places Bob's Cub as a blocker to assume, then removes it by its copy: selected first, then gone
        alice.combatWorkedOut(this)
        singleTap(alice.battlefieldCard("Ember Cub", owner = bob))
        singleTap(copyOf(alice, "Ember Cub", alice))
        waitUntil("the block placed to assume") { alice.showsControl("Assume blockers") }
        singleTap(copyOf(alice, "Ember Cub", bob))
        assertTrue(alice.showsControl("Assume blockers"), "still placed after the first tap")
        singleTap(copyOf(alice, "Ember Cub", bob))
        waitUntil("the assumed block removed") { alice.showsControl("Assume no blockers") }
        assertFalse(alice.shows(hasContentDescription("Card action ring")), "no Combat menu")
        // CMB-034: Bob blocks with his Cub, then removes it by its copy the same way
        run(skip = Duration.ofSeconds(1))
        bob.pickBlocker("Ember Cub", this)
        run(skip = Duration.ofSeconds(1))
        singleTap(bob.bandCard("Ember Cub", owner = alice))
        waitUntil("Bob's block staged") { bob.stepArrow() == "Block (1)" }
        singleTap(copyOf(bob, "Ember Cub", bob))
        assertEquals("Block (1)", bob.stepArrow(), "still blocking after the first tap")
        singleTap(copyOf(bob, "Ember Cub", bob))
        waitUntil("Bob's blocker removed") { bob.stepArrow() == "No blocks" }
        assertFalse(bob.shows(hasContentDescription("Card action ring")), "no Combat menu")
        assertFalse(alice.refusalSeen || bob.refusalSeen, "no refusal: ${alice.shownTexts} ${bob.shownTexts}")
    }
}

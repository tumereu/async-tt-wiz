package org.asyncttwiz.e2e

import androidx.compose.ui.test.performClick
import arcanum.api.TurnStep
import arcanum.engine.Engine
import org.asyncttwiz.protocol.game.GameDevice
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The combat band (owner, 2026-11-05): while Alice declares attackers with none staged the band between the battlefields
 * is a thin strip; she taps it open, stages two attackers by tapping her creatures (nothing covers them), declares with
 * the main button; Bob blocks one of them on the band's lanes; the combat damage is dealt.
 */
class CombatBandTest : E2e() {
    /** Alice's Ember Cubs that can attack: untapped, not summoning sick. */
    private fun readyCubs() = alice.state.let { s ->
        val view = Engine.view(s)
        alice.field("Ember Cub").filter { s.obj(it)?.tapped == false && view[it]?.summoningSick == false }
    }

    /** Taps [player]'s main button while it passes or goes on (not a declaration). */
    private fun goOn(player: E2ePlayer) = player.once {
        if (player.status == GameDevice.Status.YourMove) player.stepArrow()?.takeIf { it in listOf("Pass", "Next", "Resolve") }?.let(player::tap)
    }

    /** Alice casts an Ember Cub from her hand (Autotap) and is back in her Main 1 with it on the battlefield. */
    private fun castCub() {
        val before = alice.field("Ember Cub").size
        alice.playFromHand("Ember Cub", this)
        waitUntil("the Cub on Alice's battlefield", along = listOf(bob)) { alice.field("Ember Cub").size == before + 1 && alice.inOwnMain() }
    }

    // CMB-001, CMB-007, CMB-028, CMB-031, CMB-034, TRN-063
    @Test fun attackersAreStagedInTheBandAndBlockedOnItsLanes() {
        startGame(TestDeck.thirds(TestCards.cub), TestDeck.thirds(TestCards.herald),
            deal = { a, b -> "Mountain" in hand(a) && hand(a).count { it == "Ember Cub" } >= 2 && "Mountain" in hand(b) &&
                "Ember Herald" in hand(b) })
        bothOpen()
        bothKeep(bobAway = false)
        // Turn 1: a Mountain and a Cub
        alice.playFromHand("Mountain", this)
        waitUntil("the Mountain on the battlefield", along = listOf(bob)) { alice.field("Mountain").isNotEmpty() && alice.inOwnMain() }
        castCub()
        alice.hold("To combat")
        // Bob's turn: a Mountain and an Ember Herald, so he has creatures to block with
        waitUntil("Bob's Main 1", along = listOf(alice)) { bob.inOwnMain() }
        bob.playFromHand("Mountain", this)
        waitUntil("Bob's Mountain", along = listOf(alice)) { bob.field("Mountain").isNotEmpty() && bob.inOwnMain() }
        bob.playFromHand("Ember Herald", this)
        waitUntil("Bob's Herald and its token", along = listOf(alice)) {
            bob.field("Ember Herald").isNotEmpty() && bob.state.battlefield.any { bob.state.obj(it)?.token == true } && bob.inOwnMain()
        }
        bob.hold("To combat")
        // Turn 3: Alice's second Cub; then on to her turn 5 with both ready
        waitUntil("Alice's second Main 1", along = listOf(bob)) { alice.inOwnMain() && alice.state.turn.number >= 3 }
        castCub()
        alice.hold("To combat")
        waitUntil("Alice's Main 1 with two ready Cubs", along = listOf(bob)) { alice.inOwnMain() && readyCubs().size == 2 }
        alice.tap("To combat")
        waitUntil("Alice declaring attackers", along = listOf(bob)) {
            alice.state.turn.step == TurnStep.DeclareAttackers && alice.stepArrow() == "No attack"
        }
        // CMB-001: the thin strip, opened by a tap; opening it stages nothing
        waitUntil("the thin strip") { alice.showsCombatStrip() }
        alice.tagged("combat-strip").performClick()
        waitUntil("the band open") { !alice.showsCombatStrip() }
        assertEquals("No attack", alice.stepArrow())
        // CMB-028: a tap on each ready Cub stages it in the band's lane; nothing covers them
        for (n in 0..1) {
            alice.stageAttacker("Ember Cub", this, index = n)
            waitUntil("Cub ${n + 1} staged") { alice.shown.board.engine!!.table.attackDraft.orEmpty().size == n + 1 }
            assertFalse(alice.refusalSeen, "the Cub is staged")
        }
        assertEquals("Attack (2)", alice.stepArrow())
        val life = alice.state.player(bob.me).life
        alice.tap("Attack (2)")
        // Bob reaches his blocks (passing where he may respond to the attack)
        waitUntil("Bob declaring blockers") {
            goOn(alice); goOn(bob)
            bob.status == GameDevice.Status.YourMove && bob.stepArrow() == "No blocks"
        }
        // CMB-034: his Herald picked as the blocker, then Alice's first attacking Cub's copy in the band blocks it
        bob.battlefieldCard("Ember Herald").performClick()
        run(skip = java.time.Duration.ofSeconds(1)) // past the double-tap window: the tap is a single tap
        bob.bandCard("Ember Cub", owner = alice).performClick()
        waitUntil("Bob's block staged") { bob.stepArrow() == "Block (1)" }
        bob.tap("Block (1)")
        waitUntil("the combat damage", along = listOf(bob)) {
            goOn(alice)
            alice.state.player(bob.me).life < life
        }
        assertEquals(life - 2, alice.state.player(bob.me).life, "one Cub unblocked")
        assertFalse(alice.refusalSeen || bob.refusalSeen, "no refusal")
        assertTrue(alice.leaks.leaks.isEmpty() && bob.leaks.leaks.isEmpty(), "${alice.leaks.leaks} ${bob.leaks.leaks}")
    }
}

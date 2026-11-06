package org.asyncttwiz.e2e

import androidx.compose.ui.test.performClick
import arcanum.api.TurnStep
import org.asyncttwiz.protocol.game.GameDevice
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The division of combat damage with trample (owner playtest 2026-11-06): Alice's Cinder Brute (2/2, trample) attacks and
 * Bob blocks it with three Ash Pups (1/1); Alice divides its 2 damage in the panel attached to the combat band: a row per
 * Pup with its damage chip, then Bob's own last row; OK deals it.
 */
class DamageDivisionTest : E2e() {
    /** Taps [player]'s main button while it passes or goes on (also at a stop at an assumption about them, HIS-095). */
    private fun goOn(player: E2ePlayer) = player.once {
        if (player.status == GameDevice.Status.YourMove || player.status is GameDevice.Status.Stopped)
            player.stepArrow()?.takeIf { it in listOf("Pass", "Next", "Resolve") }?.let(player::tap)
    }

    // CMB-033, STK-033, CR 702.19b
    @Test fun aTrampleDivisionIsMadeInThePanelWithTheDefendersRowLast() {
        startGame(TestDeck("Brutes", listOf(TestCards.mountain to 20, TestCards.brute to 20)),
            TestDeck("Pups", listOf(TestCards.mountain to 10, TestCards.pup to 30)),
            deal = { a, b -> "Cinder Brute" in hand(a) && hand(b).count { it == "Ash Pup" } >= 3 })
        bothOpen()
        bothKeep(bobAway = false)
        // Turn 1: Alice's Brute
        alice.playFromHand("Cinder Brute", this)
        waitUntil("the Brute on the battlefield", along = listOf(bob)) { alice.field("Cinder Brute").isNotEmpty() && alice.inOwnMain() }
        alice.hold("To combat")
        // Turn 2: Bob's three Pups
        waitUntil("Bob's Main 1", along = listOf(alice)) { bob.inOwnMain() }
        for (n in 1..3) {
            bob.playFromHand("Ash Pup", this)
            waitUntil("Pup $n on the battlefield", along = listOf(alice)) { bob.field("Ash Pup").size == n && bob.inOwnMain() }
        }
        bob.hold("To combat")
        // Turn 3: Alice attacks with the Brute
        waitUntil("Alice's second Main 1", along = listOf(bob)) { alice.inOwnMain() && alice.state.turn.number >= 3 }
        alice.tap("To combat")
        waitUntil("Alice declaring attackers", along = listOf(bob)) { alice.state.turn.step == TurnStep.DeclareAttackers }
        alice.stageAttacker("Cinder Brute", this)
        waitUntil("the Brute staged") { alice.stepArrow() == "Attack (1)" }
        alice.tap("Attack (1)")
        waitUntil("Bob declaring blockers") {
            goOn(alice); goOn(bob)
            bob.status == GameDevice.Status.YourMove && bob.stepArrow() == "No blocks"
        }
        // CMB-034: each Pup picked as the blocker, then the Brute's copy in the band
        for (n in 0..2) {
            bob.battlefieldCard("Ash Pup", index = n).performClick()
            run(skip = java.time.Duration.ofSeconds(1))
            bob.bandCard("Cinder Brute", owner = alice).performClick()
            waitUntil("Pup ${n + 1} blocking") { bob.stepArrow() == "Block (${n + 1})" }
        }
        bob.tap("Block (3)")
        // CMB-033: Alice's division opens attached to the band: a row per Pup (lethal 1 each in turn: two red, one grey)
        // and Bob's own row last
        waitUntil("Alice's division", along = listOf(bob)) {
            goOn(alice)
            alice.shows(androidx.compose.ui.test.hasContentDescription("Division of combat damage"))
        }
        assertTrue(alice.showsText("Divide 2 damage"), alice.screen())
        assertEquals(2, alice.nodes(androidx.compose.ui.test.hasContentDescription("Ash Pup: 1")).fetchSemanticsNodes().size)
        assertEquals(1, alice.nodes(androidx.compose.ui.test.hasContentDescription("Ash Pup: 0")).fetchSemanticsNodes().size)
        assertTrue(alice.shows(androidx.compose.ui.test.hasContentDescription("${bob.name}: 0")), "Bob's trample row: ${alice.screen()}")
        assertTrue(alice.shows(androidx.compose.ui.test.hasContentDescription("Power 2")), "the Brute's power at the top left")
        val life = alice.state.player(bob.me).life
        alice.tap("OK")
        waitUntil("the combat damage dealt", along = listOf(bob)) {
            goOn(alice); goOn(bob)
            bob.field("Ash Pup").size == 1
        }
        assertEquals(life, alice.state.player(bob.me).life, "nothing trampled over")
        assertFalse(alice.refusalSeen || bob.refusalSeen, "no refusal")
        assertTrue(alice.leaks.leaks.isEmpty() && bob.leaks.leaks.isEmpty())
    }
}

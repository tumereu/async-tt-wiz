package org.asyncttwiz.e2e

import androidx.compose.ui.test.hasContentDescription
import arcanum.api.TurnStep
import org.asyncttwiz.protocol.game.GameDevice
import org.junit.Test
import kotlin.test.assertFalse

/**
 * Owner-reported 2026-11-11 (CMB-044): a cost to attack whose X the board defines ("pays {X} for each creature they control
 * that's attacking you, where X is the number of enchantments you control") showed the letter X on its chip. It shows its
 * amount, on the attacker's phone and the defender's alike.
 */
class AttackCostXTest : E2e() {
    private val pups = TestDeck("Pups", listOf(TestCards.mountain to 20, TestCards.pup to 20))
    private val wards = TestDeck("Wards", listOf(TestCards.mountain to 14, TestCards.bulwark to 13, TestCards.chime to 13))

    private fun E2ePlayer.showsCost(label: String) = shows(hasContentDescription("Pays $label", substring = true))

    // CMB-044 (CR 508.1h): Bob has an Ember Bulwark and an Ember Chime, two enchantments; Alice, with two Mountains, stages her Pup and its chip
    // reads {2} on both phones, never {X}
    @Test fun aCostToAttackWithXShowsItsAmountOnBothPhones() {
        startGame(pups, wards, bobSettings = org.asyncttwiz.protocol.game.PlaySettings(),
            deal = { a, b -> "Ash Pup" in hand(a) && hand(a).count { it == "Mountain" } >= 2 && "Ember Bulwark" in hand(b) && "Ember Chime" in hand(b) })
        bothOpen()
        bothKeep(bobAway = false)
        alice.playFromHand("Ash Pup", this)
        waitUntil("the Pup", along = listOf(bob)) { alice.field("Ash Pup").isNotEmpty() && alice.inOwnMain() }
        alice.playFromHand("Mountain", this)
        waitUntil("Alice's first Mountain", along = listOf(bob)) { alice.field("Mountain").size == 1 && alice.inOwnMain() }
        alice.hold("To combat")
        waitUntil("Bob's Main 1") { bob.inOwnMain() }
        for (card in listOf("Ember Bulwark", "Ember Chime")) {
            bob.playFromHand(card, this)
            waitUntil("Bob's $card", along = listOf(alice)) { bob.field(card).isNotEmpty() && bob.inOwnMain() }
        }
        bob.hold(requireNotNull(bob.stepArrow()))
        waitUntil("Alice's next Main 1", along = listOf(bob)) {
            if (alice.status is GameDevice.Status.Stopped) alice.once { alice.stepArrow()?.let(alice::tap) }
            alice.inOwnMain() && alice.state.turn.number >= 3
        }
        // Two Mountains to pay {2} with: a cost that can't be paid isn't offered (CR 508.1d)
        alice.playFromHand("Mountain", this)
        waitUntil("Alice's second Mountain", along = listOf(bob)) { alice.field("Mountain").size == 2 && alice.inOwnMain() }
        alice.tap("To combat")
        waitUntil("Alice declaring attackers") { alice.state.turn.step == TurnStep.DeclareAttackers && alice.stepArrow() == "No attack" }
        alice.stageAttacker("Ash Pup", this)
        waitUntil("the Pup's cost {2} on Alice's phone") { alice.showsCost("{2}") }
        waitUntil("the Pup's cost {2} on Bob's phone") { bob.showsCost("{2}") }
        assertFalse(alice.showsCost("{X}") || bob.showsCost("{X}"), "never the letter X")
    }
}

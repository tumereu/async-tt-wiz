package org.asyncttwiz.e2e

import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.performClick
import arcanum.api.TurnStep
import org.asyncttwiz.android.AppLog
import org.junit.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Problem report WGEG3N (owner, prod 0.2.1, 2026-11-20): "creature did not gain double strike". His Omenport Vigilante has
 * double strike as long as he has committed a crime this turn; he committed one by sacrificing a Voracious Varmint to destroy
 * the other player's Mystical Tether. The engine read the crime off the destroyed enchantment as it was then, gone from the
 * battlefield: no longer the other player's, so no crime, and the Vigilante dealt its damage once.
 *
 * Bob casts a Mountain Hymn (an enchantment) in his turn; in hers Alice casts a Harborgate Vigilante (haste, double strike
 * after a crime) and a Hungry Gnawer, sacrifices the Gnawer to destroy the Hymn, and attacks Bob with the Vigilante.
 */
class CrimeDoubleStrikeTest : E2e() {
    // CR 700.13, 702.4b, 510.4: the crime stays committed after its target is destroyed: the Vigilante deals 2 in the first-strike
    // damage step and 2 more in the regular one; the app log lists the crime
    @Test fun aCrimeStaysCommittedAfterItsTargetIsDestroyed() {
        startGame(TestDeck("Vigilantes", listOf(TestCards.mountain to 10, TestCards.vigilante to 15, TestCards.gnawer to 15)),
            TestDeck("Hymns", listOf(TestCards.mountain to 20, TestCards.boost to 20)), bobStarts = true,
            deal = { a, b -> "Harborgate Vigilante" in hand(a) && "Hungry Gnawer" in hand(a) && "Mountain Hymn" in hand(b) })
        bothOpen()
        alice.tap("Keep")
        bob.tap("Keep")
        waitUntil("Bob's Main 1", along = listOf(alice)) { bob.inOwnMain() }
        bob.playFromHand("Mountain Hymn", this)
        waitUntil("Bob's Hymn", along = listOf(alice)) { bob.field("Mountain Hymn").isNotEmpty() && bob.inOwnMain() }
        bob.hold("To combat")
        waitUntil("Alice's Main 1", along = listOf(bob)) { alice.inOwnMain() }
        for (name in listOf("Harborgate Vigilante", "Hungry Gnawer")) {
            waitUntil("Alice's game settled") { alice.settled(this) }
            alice.playFromHand(name, this)
            waitUntil("Alice's $name", along = listOf(bob)) { alice.field(name).isNotEmpty() && alice.inOwnMain() }
        }
        waitUntil("Alice's game settled") { alice.settled(this) }
        alice.battlefieldCard("Hungry Gnawer").performClick()
        waitUntil("Alice's target bar") { alice.shows(hasContentDescription("Choose targets")) || alice.shows(hasContentDescription("Choose target", substring = true)) }
        alice.battlefieldCard("Mountain Hymn", owner = bob).performClick()
        waitUntil("the Hymn destroyed", along = listOf(bob)) {
            if (alice.stepArrow() == "Resolve") alice.once { alice.tap("Resolve") }
            alice.field("Mountain Hymn", owner = bob.me).isEmpty() && alice.field("Hungry Gnawer").isEmpty() && alice.inOwnMain()
        }
        assertTrue(alice.state.player(bob.me).graveyard.any { cardName(alice.state, it) == "Mountain Hymn" }, "the Hymn in Bob's graveyard")

        waitUntil("Alice's game settled") { alice.settled(this) }
        alice.tap("To combat")
        waitUntil("Alice declaring attackers", along = listOf(bob)) { alice.state.turn.step == TurnStep.DeclareAttackers && alice.stepArrow() == "No attack" }
        alice.stageAttacker("Harborgate Vigilante", this)
        alice.declare("Attack (1)", this)
        waitUntil("the combat over", along = listOf(bob)) {
            if (alice.stepArrow() == "To damage") alice.once { alice.tap("To damage") }
            alice.state.turn.active != alice.me ||
                alice.state.turn.step in listOf(TurnStep.EndOfCombat, TurnStep.PostcombatMain, TurnStep.End, TurnStep.Cleanup)
        }
        assertEquals(16, alice.state.player(bob.me).life, "the Vigilante dealt its 2 twice, with double strike")
        assertContains(AppLog.text(), "crimes this turn +yours 1")
        assertFalse(alice.refusalSeen || bob.refusalSeen, "no refusal")
    }
}

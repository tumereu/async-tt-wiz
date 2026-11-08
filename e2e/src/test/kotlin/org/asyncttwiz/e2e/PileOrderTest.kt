package org.asyncttwiz.e2e

import androidx.compose.ui.test.performClick
import arcanum.api.TurnStep
import org.asyncttwiz.android.BattlefieldLayout
import org.asyncttwiz.android.BattlefieldRow
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Owner, 2026-11-08 (TBL-053): the tapped members of a stack lie on top, also where a new member joins or stacks merge, and
 * entered this turn splits only creatures. Alice taps her Mountain and plays another: it joins the stack at once, the
 * tapped one on top. Her Pup attacks (tapped) and a new Pup, entered this turn, lies apart until the turn ends; the two
 * then merge with the tapped one on top.
 */
class PileOrderTest : E2e() {
    private val deck = TestDeck("Pups", listOf(TestCards.mountain to 20, TestCards.pup to 20))

    private fun E2ePlayer.stacks(name: String) = BattlefieldLayout.groups(shown.board, id).filter { group ->
        group.members.all { m -> state.battlefield.any { atw(it) == m && cardName(state, it) == name && state.obj(it)?.controller == me } }
    }
    private fun E2ePlayer.tapped(card: String) = state.battlefield.first { atw(it) == card }.let { state.obj(it)?.tapped == true }

    // TBL-053, TBL-027, TBL-130, TBL-054
    @Test fun tappedMembersLieOnTopAndOnlyCreaturesStayApart() {
        startGame(deck, deck, deal = { a, _ -> hand(a).count { it == "Mountain" } >= 2 && hand(a).count { it == "Ash Pup" } >= 2 })
        bothOpen()
        bothKeep(bobAway = false)
        alice.playFromHand("Mountain", this)
        waitUntil("the first Mountain", along = listOf(bob)) { alice.field("Mountain").size == 1 && alice.inOwnMain() }
        alice.playFromHand("Ash Pup", this)
        waitUntil("the first Pup", along = listOf(bob)) { alice.field("Ash Pup").size == 1 && alice.inOwnMain() }
        alice.hold("To combat")
        waitUntil("Alice's next Main 1", along = listOf(bob)) {
            if (alice.status is org.asyncttwiz.protocol.game.GameDevice.Status.Stopped) alice.once { alice.stepArrow()?.let(alice::tap) }
            alice.inOwnMain() && alice.state.turn.number >= 3
        }

        // Her Mountain tapped for mana, then a new Mountain: one stack at once, the tapped one on top
        val first = atw(alice.field("Mountain").single())
        waitUntil("her game settled") { alice.settled(this) }
        alice.battlefieldCard("Mountain").performClick()
        waitUntil("the Mountain tapped") { alice.tapped(first) }
        alice.playFromHand("Mountain", this)
        waitUntil("the second Mountain", along = listOf(bob)) { alice.field("Mountain").size == 2 && alice.inOwnMain() }
        val lands = alice.stacks("Mountain")
        assertEquals(1, lands.size, "a land entered this turn joins its stack at once: ${lands.map { it.members }}")
        assertEquals(first, lands.single().members.last(), "the tapped one on top")

        // The Pup attacks and stays tapped; a new one after combat lies apart, entered this turn
        val attacker = atw(alice.field("Ash Pup").single())
        alice.tap("To combat")
        waitUntil("Alice declaring attackers", along = listOf(bob)) { alice.state.turn.step == TurnStep.DeclareAttackers && alice.stepArrow() == "No attack" }
        alice.stageAttacker("Ash Pup", this)
        alice.declare("Attack (1)", this)
        waitUntil("Alice's second Main", along = listOf(bob)) {
            if (alice.status is org.asyncttwiz.protocol.game.GameDevice.Status.Stopped) alice.once { alice.stepArrow()?.let(alice::tap) }
            else if (alice.state.turn.step != TurnStep.PostcombatMain && alice.status == org.asyncttwiz.protocol.game.GameDevice.Status.YourMove)
                alice.once { alice.stepArrow()?.takeIf { it == "Next" || it == "Pass" }?.let(alice::tap) }
            alice.inOwnMain(TurnStep.PostcombatMain)
        }
        assertTrue(alice.tapped(attacker), "the attacker stays tapped")
        alice.playFromHand("Ash Pup", this)
        waitUntil("the second Pup", along = listOf(bob)) { alice.field("Ash Pup").size == 2 && alice.inOwnMain(TurnStep.PostcombatMain) }
        assertEquals(2, alice.stacks("Ash Pup").size, "a creature entered this turn lies apart")

        // The turn ends: the two merge, the tapped one on top, on both tables
        alice.hold("End turn")
        waitUntil("Bob's turn, the Pups merged on both tables", along = listOf(bob)) {
            alice.state.turn.active == bob.me && listOf(alice, bob).all { p ->
                BattlefieldLayout.groups(p.shown.board, p.id).any { attacker in it.members && it.members.size == 2 } }
        }
        for (p in listOf(alice, bob)) {
            val pile = BattlefieldLayout.groups(p.shown.board, p.id).single { attacker in it.members }
            assertEquals(attacker, pile.members.last(), "${p.name}: the tapped one on top, never in the middle")
            assertEquals(BattlefieldRow.Creatures, pile.row)
        }
    }


}

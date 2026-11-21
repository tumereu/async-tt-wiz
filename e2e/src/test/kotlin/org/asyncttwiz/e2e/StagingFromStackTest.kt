package org.asyncttwiz.e2e

import arcanum.api.TurnStep
import arcanum.engine.Engine
import org.asyncttwiz.protocol.PlayZone
import org.asyncttwiz.protocol.game.GameDevice
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Owner playtest of 0.2.0-beta4 (2026-11-07): putting several creatures of one stack into the attack was hard, because the
 * stack regrouped as each was staged. Now, while attackers are declared, a stack keeps its place: each tap on it stages its
 * topmost unstaged creature, which moves to the stack's right end, and the others stay where they were (CMB-047, CMB-071, CMB-070).
 */
class StagingFromStackTest : E2e() {
    /** Alice's Ash Pups that can attack: untapped, not summoning sick. */
    private fun readyPups() = alice.state.let { s ->
        val view = Engine.view(s)
        alice.field("Ash Pup").filter { s.obj(it)?.tapped == false && view[it]?.summoningSick == false }
    }

    /** Alice's Pups in the order her battlefield lays them out (arrival order: the last one on top of their stack). */
    private fun pupsInOrder(): List<String> {
        val pups = alice.field("Ash Pup").map(::atw).toSet()
        return alice.shown.board.board.containers.single { it.zone == PlayZone.BATTLEFIELD && it.accountId == alice.id }
            .cards.filter { it in pups }
    }

    /** The attackers Alice has staged, in the order she staged them. */
    private fun staged(): List<String> = alice.shown.board.engine!!.table.attackDraft.orEmpty().map { atw(it.attacker) }

    /** The left edge of Alice's leftmost Pup on her screen (the stack's bottom card). */
    private fun bottomLeft(): Float = alice.battlefieldCard("Ash Pup").fetchSemanticsNode().boundsInRoot.left

    /** Alice's three ready Ash Pups in one stack on her screen, her table declaring attackers. */
    private fun toThreePupsDeclaring() {
        startGame(TestDeck("Pups", listOf(TestCards.mountain to 10, TestCards.pup to 30)), TestDeck.thirds(TestCards.cub),
            // Bob holds a Storm Spark: with his Mountain he may act after Alice's attackers are declared, so her phone waits for
            // him there (Waiting, "Bob may act") instead of going on through the combat by itself
            deal = { a, b -> hand(a).count { it == "Ash Pup" } >= 3 && "Mountain" in hand(b) && "Storm Spark" in hand(b) })
        bothOpen()
        bothKeep(bobAway = false)
        for (n in 1..3) {
            alice.playFromHand("Ash Pup", this)
            waitUntil("Pup $n on Alice's battlefield", along = listOf(bob)) { alice.field("Ash Pup").size == n && alice.inOwnMain() }
        }
        alice.hold("To combat")
        // Bob's turn: a Mountain (so he has no discard at his cleanup), then on
        waitUntil("Bob's Main 1", along = listOf(alice)) { bob.inOwnMain() }
        bob.playFromHand("Mountain", this)
        waitUntil("Bob's Mountain", along = listOf(alice)) { bob.field("Mountain").isNotEmpty() && bob.inOwnMain() }
        bob.hold("To combat")
        waitUntil("Alice's Main 1 with three ready Pups", along = listOf(bob)) { alice.inOwnMain() && readyPups().size == 3 }
        waitUntil("the Pups in one stack on Alice's screen") {
            alice.shows(androidx.compose.ui.test.hasContentDescription("Ash Pup, owner ${alice.name},", substring = true) and
                androidx.compose.ui.test.hasContentDescription("stack of 3", substring = true))
        }
        alice.tap("To combat")
        waitUntil("Alice declaring attackers", along = listOf(bob)) {
            alice.state.turn.step == TurnStep.DeclareAttackers && alice.stepArrow() == "No attack"
        }
    }

    /** Whether Alice's screen shows a pile of [n] Ash Pups of hers ("stack of [n]" on its top card). */
    private fun pupPile(n: Int) = alice.shows(androidx.compose.ui.test.hasContentDescription("Ash Pup, owner ${alice.name},", substring = true) and
        androidx.compose.ui.test.hasContentDescription("stack of $n", substring = true))

    // CMB-047, CMB-071, CMB-028, TBL-130, TBL-163, TBL-053, TBL-178: Alice's three identical Ash Pups lie in one stack; while she declares attackers
    // two taps on the stack stage its two topmost Pups one after the other, the bottom Pup staying where it was
    @Test fun twoOfThreeIdenticalCreaturesAreStagedByTappingTheirStack() {
        toThreePupsDeclaring()
        val pups = pupsInOrder()
        val left = bottomLeft()
        // The first tap on the stack (its leftmost card, the bottom one) stages the topmost Pup
        alice.stageAttacker("Ash Pup", this, index = 0)
        waitUntil("one Pup staged") { staged().size == 1 }
        assertEquals(listOf(pups[2]), staged(), "the topmost Pup")
        waitUntil("the band open") { alice.bandOpen() }
        assertEquals(left, bottomLeft(), "the bottom Pup stays where it was")
        // The second tap on the same place stages the next one down
        alice.stageAttacker("Ash Pup", this, index = 0)
        waitUntil("two Pups staged") { staged().size == 2 }
        assertEquals(listOf(pups[2], pups[1]), staged(), "the next topmost Pup")
        assertEquals(left, bottomLeft(), "the bottom Pup still where it was")
        assertEquals("Attack (2)", alice.stepArrow())
        assertFalse(alice.refusalSeen, "no refusal")
        assertTrue(alice.leaks.leaks.isEmpty() && bob.leaks.leaks.isEmpty(), "${alice.leaks.leaks} ${bob.leaks.leaks}")
    }

    // CMB-070, TBL-230, TBL-053 (owner playtest 2026-11-20: "attackers declared from a stack should stack together"): Alice stages two of
    // her three Pups from their stack: they lie together in one pile right of the third, not each in a pile of its own; once
    // declared they still lie in one pile of two right of the Pup left at home
    @Test fun attackersDeclaredFromAStackLieTogetherInOnePile() {
        toThreePupsDeclaring()
        val left = bottomLeft()
        alice.stageAttacker("Ash Pup", this, index = 0)
        waitUntil("one Pup staged") { staged().size == 1 }
        alice.stageAttacker("Ash Pup", this, index = 0)
        waitUntil("two Pups staged") { staged().size == 2 }
        waitUntil("the staged Pups in one pile of two: ${alice.screen()}") { pupPile(2) }
        assertEquals(left, bottomLeft(), "the Pup left at home stays where it was")
        val staged = staged()
        alice.declare("Attack (2)", this)
        // Bob doesn't play along here: his tap at his stop after the attack moved the combat on, and the declared attackers
        // showed only for a moment (a full run, 2026-11-21: the wait for them, settled, saw Alice in her second main phase).
        // Alice's phone waits for him with the attackers declared
        waitUntil("the two Pups declared as attackers, Alice's phone waiting for Bob") {
            alice.state.combat?.attackers?.map { atw(it.id) }?.toSet() == staged.toSet() && alice.settled(this) &&
                alice.status is GameDevice.Status.Waiting
        }
        waitUntil("the attacking Pups in one pile of two: ${alice.screen()}") { pupPile(2) }
        assertEquals(left, bottomLeft(), "the Pup at home still where it was, the attackers right of it")
        val home = alice.battlefieldCard("Ash Pup", index = 0).fetchSemanticsNode().boundsInRoot
        val pile = (0 until 3).map { alice.battlefieldCard("Ash Pup", index = it).fetchSemanticsNode().boundsInRoot }
        assertTrue(pile.drop(1).all { it.left > home.left }, "the attackers' pile right of the Pup at home: $pile")
        assertFalse(alice.refusalSeen, "no refusal")
    }
}

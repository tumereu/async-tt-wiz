package org.asyncttwiz.e2e

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.performClick
import arcanum.api.TurnStep
import org.junit.Test
import java.time.Duration
import kotlin.test.assertEquals

/**
 * Owner's review answer (2026-11-17, "Shift should not happen even for 2+ lanes"): with attackers in two lanes, the attackers
 * shifted sideways when the attack was declared and the dashed slots went (fixed only for one lane on 2026-11-10).
 */
class AttackersStayPutTest : E2e() {
    // CMB-058, CMB-028: Bob makes a Jace token with his free Ember Lesson; Alice stages one hasty Ember Dasher at the token and one at
    // Bob, then declares: neither copy on the band moves
    @Test fun attackersInTwoLanesStayPutWhenTheAttackIsDeclared() {
        startGame(TestDeck("Dashers", listOf(TestCards.mountain to 20, TestCards.dasher to 20)),
            TestDeck("Lessons", listOf(TestCards.mountain to 20, TestCards.lesson to 20)), bobStarts = true,
            deal = { a, b -> hand(a).count { it == "Ember Dasher" } >= 2 && "Ember Lesson" in hand(b) })
        bothOpen()
        bothKeep(bobAway = false)
        waitUntil("Bob's Main 1", along = listOf(alice)) { bob.inOwnMain() }
        bob.playFromHand("Ember Lesson", this)
        waitUntil("Bob's Jace token", along = listOf(alice)) { bob.field("Jace Token").size == 1 && bob.inOwnMain() }
        bob.hold("To combat")
        waitUntil("Alice's Main 1", along = listOf(bob)) { alice.inOwnMain() && alice.field("Jace Token", bob.me).size == 1 }
        alice.playFromHand("Ember Dasher", this)
        waitUntil("the first Dasher on the battlefield", along = listOf(bob)) { alice.field("Ember Dasher").size == 1 && alice.inOwnMain() }
        alice.playFromHand("Ember Dasher", this)
        waitUntil("both Dashers on the battlefield", along = listOf(bob)) { alice.field("Ember Dasher").size == 2 && alice.inOwnMain() }
        waitUntil("Alice's game settled") { alice.settled(this) }
        alice.tap("To combat")
        waitUntil("Alice declaring attackers", along = listOf(bob)) {
            alice.state.turn.step == TurnStep.DeclareAttackers && alice.stepArrow() == "No attack"
        }
        alice.combatWorkedOut(this)
        alice.node(hasContentDescription("Attack Jace Token")).performClick()
        run(skip = Duration.ofSeconds(1))
        alice.stageAttacker("Ember Dasher", this)
        waitUntil("the first Dasher staged") { alice.stepArrow() == "Attack (1)" }
        alice.combatWorkedOut(this)
        alice.tap("Attack ${bob.name}")
        run(skip = Duration.ofSeconds(1))
        alice.stageAttacker("Ember Dasher", this)
        waitUntil("the second Dasher staged") { alice.stepArrow() == "Attack (2)" && alice.settled(this) }
        run(skip = Duration.ofSeconds(1))
        fun copies(): List<Rect> = alice.nodes(hasContentDescription("Projection, Ember Dasher, owner ${alice.name}, ", substring = true))
            .fetchSemanticsNodes().map { it.boundsInRoot }.sortedBy { it.left }
        val staged = copies()
        assertEquals(2, staged.size, alice.screen())
        alice.declare("Attack (2)", this)
        waitUntil("the attack declared and the slots gone") {
            alice.settled(this) && alice.stepArrow() != "Attack (2)" && !alice.shows(hasContentDescription("Attacker slot")) &&
                copies().size == 2
        }
        run(skip = Duration.ofSeconds(1))
        val declared = copies()
        for ((before, after) in staged.zip(declared)) {
            assertEquals(before.left, after.left, 0.5f, "a Dasher stays put: $staged → $declared")
            assertEquals(before.top, after.top, 0.5f, "a Dasher stays put: $staged → $declared")
        }
    }
}

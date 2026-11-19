package org.asyncttwiz.e2e

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.performClick
import arcanum.api.TurnStep
import org.asyncttwiz.android.CardAppearancePreferences
import org.asyncttwiz.appui.AppPlatform
import org.asyncttwiz.protocol.game.ResponseMode
import org.junit.Test
import java.time.Duration
import kotlin.test.assertEquals

/**
 * Owner's review answer (2026-11-17, "Shift should not happen even for 2+ lanes"): with attackers in two lanes, the attackers
 * shifted sideways when the attack was declared and the dashed slots went (fixed only for one lane on 2026-11-10).
 */
class AttackersStayPutTest : E2e() {
    // CMB-058, CMB-028: Bob makes a Jace token with his free Ember Lesson; Alice (large cards) stages one hasty Ember Dasher at the
    // token and one at Bob, then declares: neither copy on the band moves
    @Test fun attackersInTwoLanesStayPutWhenTheAttackIsDeclared() {
        // Cards twice the usual size, so the lanes are as wide as their attackers and their room, not their chips
        val sizes = CardAppearancePreferences(AppPlatform.current)
        sizes.setSize(2f)
        try {
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
            // Bob on Full control: the combat waits for him, so Alice's table holds the declared attackers (as in GoneTargetNameTest)
            bob.game!!.settings(bob.shown.settings.copy(mode = ResponseMode.FullControl))
            waitUntil("Bob's Full control known on both phones", along = listOf(bob)) {
                alice.engine { device.opponentMode } == ResponseMode.FullControl && bob.shown.settings.mode == ResponseMode.FullControl
            }
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
            // Bob's blocks are awaited (his Full control): the band stays with the declared attackers, the slots gone
            waitUntil("the declared attackers on Alice's band, the slots gone", along = listOf(bob)) {
                alice.state.turn.step == TurnStep.DeclareBlockers && !alice.shows(hasContentDescription("Attacker slot")) &&
                    copies().let { it.size == 2 && it.all { copy -> copy.width > 0f } }
            }
            run(skip = Duration.ofSeconds(1))
            val declared = copies()
            for ((before, after) in staged.zip(declared)) {
                assertEquals(before.left, after.left, 0.5f, "a Dasher stays put: $staged → $declared")
                assertEquals(before.top, after.top, 0.5f, "a Dasher stays put: $staged → $declared")
            }
        } finally { sizes.setSize(1f) }
    }
}

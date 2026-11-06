package org.asyncttwiz.e2e

import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.doubleClick
import androidx.compose.ui.test.performTouchInput
import arcanum.api.TurnStep
import arcanum.engine.Engine
import org.junit.Test
import kotlin.test.assertFalse

/**
 * Owner playtest 2026-11-06: tapping a combat copy showed "Rotation not queued. Reconnect and try again.": the old
 * by-hand rotation still answered a quick second tap on it. Alice stages her Ember Cub as an attacker and taps its copy in
 * the combat band twice in a row: the taps only select it and the double tap does nothing, with no message.
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
}

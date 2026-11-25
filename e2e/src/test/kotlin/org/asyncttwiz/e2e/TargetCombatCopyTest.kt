package org.asyncttwiz.e2e

import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performMouseInput
import arcanum.api.TurnStep
import arcanum.engine.Engine
import arcanum.engine.state.TargetRef
import org.asyncttwiz.protocol.game.AssumeResolves
import org.asyncttwiz.protocol.game.GameDevice
import org.asyncttwiz.protocol.game.PlaySettings
import org.junit.Test
import org.robolectric.annotation.Config
import java.time.Duration
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Owner playtest 2026-11-21 (N2): while a target was picked, the combat copies of creatures in combat glowed as legal
 * targets, but a tap on one did the copy's own thing (selected it, or took it out of combat) instead of targeting its
 * creature. A tap on a glowing copy picks its creature, exactly as a tap on the real creature does: Bob's Storm Spark cast
 * at Alice's attacking Cub, and Alice's Ember Raider's attack trigger aimed at the Raider itself. It failed with a spell whose
 * cast panel opens on its way (kicker) before the target: the legal targets already glow then, but a tap wasn't taken as a
 * pick, and the copy's tap selected it (aCombatCopyTappedWhileTheCastPanelChoosesItsWayIsTheTarget, failing before the fix).
 */
abstract class TargetCombatCopyFlow(private val desktop: Boolean) : E2e() {
    private val picking = hasText("Choose any target") and isHeading()

    /** A tap on [node] (a click on the desktop table). */
    private fun press(node: SemanticsNodeInteraction) { if (desktop) node.performMouseInput { click() } else node.performClick() }

    // STK-047, CMB-003, CMB-032 (owner playtest 2026-11-21): Alice attacks with her Ember Cub; Bob, where he may respond to the
    // attack, casts Storm Spark and taps the Cub's copy in the combat band: the Cub is the Spark's target
    @Test fun aCombatCopyTappedWhileTargetsArePickedIsTheTarget() = bobCastsAtTheAttacker(TestCards.spark)

    // STK-047, ZON-113, CMB-003 (owner playtest 2026-11-21): the same with a spell whose panel opens on its way (kicker) before the
    // target: its legal targets glow, and a tap on the Cub's copy picks the Cub
    @Test fun aCombatCopyTappedWhileTheCastPanelChoosesItsWayIsTheTarget() = bobCastsAtTheAttacker(TestCards.jab)

    private fun bobCastsAtTheAttacker(spell: TestCard) {
        // Bob's phone casts: once, in the phone class
        org.junit.Assume.assumeFalse(desktop)
        // Alice's phone never assumes Bob's pass, so he holds priority over her attack as his own move
        startGame(TestDeck.thirds(TestCards.cub), TestDeck("Spells", listOf(TestCards.mountain to 20, spell to 20)),
            aliceSettings = PlaySettings(assume = AssumeResolves.Never),
            deal = { a, b -> "Mountain" in hand(a) && "Ember Cub" in hand(a) && "Mountain" in hand(b) && spell.name in hand(b) })
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
        alice.stageAttacker("Ember Cub", this)
        waitUntil("the Cub staged") { alice.stepArrow() == "Attack (1)" }
        alice.declare("Attack (1)", this)
        waitUntil("Bob holding priority over the attack") {
            (bob.status == GameDevice.Status.YourMove || bob.status is GameDevice.Status.Stopped) &&
                bob.state.turn.step == TurnStep.DeclareAttackers && bob.state.priority.holder == bob.me && bob.settled(this)
        }
        val cub = alice.field("Ember Cub").single()
        bob.playFromHand(spell.name, this)
        if (spell == TestCards.jab) waitUntil("Bob's cast panel") { bob.showsText("Cast ${spell.name}") }
        else waitUntil("Bob's target bar") { bob.shows(picking) }
        run(skip = Duration.ofSeconds(1))
        press(bob.bandCard("Ember Cub", owner = alice))
        run(skip = Duration.ofSeconds(1))
        // The Spark went on the Stack with the Cub as its target (or has already resolved and the Cub died)
        fun targeted() = bob.state.let { s ->
            s.stack.any { id -> s.stackItems[id]?.targets?.values?.flatten()?.contains(TargetRef.Object(cub)) == true } ||
                (cub !in s.battlefield && s.stack.isEmpty())
        }
        waitUntil("the spell cast at the Cub", timeoutSeconds = 20) { !bob.shows(picking) && targeted() }
        assertFalse(bob.refusalSeen, "no refusal: ${bob.shownTexts}")
        assertTrue(targeted(), "the Spark cast at the Cub: ${bob.screen()}")
    }

    // STK-047, CMB-003, CMB-032 (owner playtest 2026-11-21): Alice attacks with her Ember Raider; its attack trigger's target is
    // picked on the table, and a tap on the Raider's own glowing combat copy picks the Raider
    @Test fun anAttackTriggersTargetIsPickedOnACombatCopy() {
        startGame(TestDeck("Raiders", listOf(TestCards.mountain to 20, TestCards.raider to 20)), TestDeck.thirds(TestCards.cub),
            aliceDesktop = desktop, deal = { a, _ -> "Ember Raider" in hand(a) })
        bothOpen()
        bothKeep(bobAway = false)
        alice.playFromHand("Ember Raider", this)
        waitUntil("the Raider", along = listOf(bob)) { alice.field("Ember Raider").isNotEmpty() && alice.inOwnMain() }
        val raider = alice.field("Ember Raider").single()
        alice.tap("To combat")
        waitUntil("Alice declaring attackers", along = listOf(bob)) {
            alice.state.turn.step == TurnStep.DeclareAttackers && alice.stepArrow() == "No attack"
        }
        alice.stageAttacker("Ember Raider", this)
        waitUntil("the Raider staged") { alice.stepArrow() == "Attack (1)" }
        val copy = { alice.node(hasContentDescription("Projection, Ember Raider, owner ${alice.name}, ", substring = true)) }
        alice.declare("Attack (1)", this)
        waitUntil("Alice's target bar for the attack trigger") { alice.shows(picking) }
        run(skip = Duration.ofSeconds(1))
        press(copy())
        run(skip = Duration.ofSeconds(1))
        fun targeted() = alice.state.let { s ->
            s.stack.any { id -> s.stackItems[id]?.targets?.values?.flatten()?.contains(TargetRef.Object(raider)) == true } ||
                (s.stack.isEmpty() && (s.obj(raider)?.damage ?: 0) >= 1)
        }
        waitUntil("the trigger aimed at the Raider", timeoutSeconds = 20) { !alice.shows(picking) && targeted() }
        assertFalse(alice.refusalSeen, "no refusal: ${alice.shownTexts}")
    }
}

class TargetCombatCopyTest : TargetCombatCopyFlow(desktop = false)

/** On the desktop table (the player tapping the copy clicks it there). */
@Config(qualifiers = "w1440dp-h900dp-land-mdpi")
class TargetCombatCopyDesktopTest : TargetCombatCopyFlow(desktop = true)

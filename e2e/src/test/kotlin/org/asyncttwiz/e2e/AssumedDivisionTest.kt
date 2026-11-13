package org.asyncttwiz.e2e

import androidx.compose.ui.test.performClick
import arcanum.api.TurnStep
import arcanum.engine.decision.Answer
import arcanum.engine.input.Input
import org.asyncttwiz.android.AppLog
import org.asyncttwiz.protocol.game.GameDevice
import org.asyncttwiz.protocol.game.ResponseMode
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Owner problem report JVPJ8Y ("assume no response won't continue"): Alice attacks with her Zinc Hauler (4/3) and Bob
 * blocks it with two Ash Pups, then leaves. Alice passes after the blocks ("To damage") and her table waits for Bob (his
 * Normal stops at the blocks) with "Assume no response". Her press assumes his pass; in the combat damage step that
 * follows her Hauler's 4 damage divides among the Pups by itself (enough for lethal damage to each, STK-033), in the
 * same event. The server refused that event until the fix (it took her automatic division for a real input of hers in
 * an assumed event), so the damage was shown and taken back at every press.
 */
class AssumedDivisionTest : E2e() {
    /** Taps [player]'s main button while it passes or goes on, once their game has settled (DamageDivisionTest's). */
    private fun goOn(player: E2ePlayer) {
        if (!player.settled(this)) return
        player.once {
            if (player.status == GameDevice.Status.YourMove || player.status is GameDevice.Status.Stopped)
                player.stepArrow()?.takeIf { it in listOf("Pass", "Next", "Resolve") }?.let(player::tap)
        }
    }

    /** The Ash Pups on Bob's battlefield as [viewer]'s table shows it. */
    private fun pups(viewer: E2ePlayer) = viewer.state.let { s ->
        s.battlefield.count { s.obj(it)?.controller == bob.me && s.obj(it)?.definition?.faces?.first()?.name == "Ash Pup" }
    }

    // JVPJ8Y, STK-033, CR 510.1c
    @Test fun assumingNoResponseAfterADoubleBlockDealsTheDamage() {
        startGame(TestDeck("Haulers", listOf(TestCards.mountain to 20, TestCards.hauler to 20)),
            TestDeck("Pups", listOf(TestCards.mountain to 10, TestCards.pup to 30)),
            deal = { a, b -> "Zinc Hauler" in hand(a) && hand(b).count { it == "Ash Pup" } >= 2 })
        bothOpen()
        bothKeep(bobAway = false)
        // Turn 1: Alice's Hauler
        alice.playFromHand("Zinc Hauler", this)
        waitUntil("the Hauler on the battlefield", along = listOf(bob)) { alice.field("Zinc Hauler").isNotEmpty() && alice.inOwnMain() }
        alice.hold("To combat")
        // Turn 2: Bob's two Pups
        waitUntil("Bob's Main 1", along = listOf(alice)) { bob.inOwnMain() }
        for (n in 1..2) {
            bob.playFromHand("Ash Pup", this)
            waitUntil("Pup $n on the battlefield", along = listOf(alice)) { bob.field("Ash Pup").size == n && bob.inOwnMain() }
        }
        bob.hold("To combat")
        waitUntil("Alice's second Main 1", along = listOf(bob)) { alice.inOwnMain() && alice.state.turn.number >= 3 }
        // Both in Normal: the blocks are a pause for both (STK-036), so Alice's phone waits for her own pass there (in Quick
        // it would pass for her and Bob's phone would assume it, playing the combat out on his side), and with Bob away
        // her table then waits for his pass with Assume
        alice.game!!.settings(alice.shown.settings.copy(mode = ResponseMode.Normal))
        bob.game!!.settings(bob.shown.settings.copy(mode = ResponseMode.Normal))
        waitUntil("each phone knowing the other's Normal", along = listOf(bob)) {
            alice.engine { device.opponentMode } == ResponseMode.Normal && bob.engine { device.opponentMode } == ResponseMode.Normal
        }
        // Turn 3: Alice attacks with the Hauler, Bob blocks with both Pups
        alice.tap("To combat")
        waitUntil("Alice declaring attackers", along = listOf(bob)) { alice.state.turn.step == TurnStep.DeclareAttackers }
        alice.stageAttacker("Zinc Hauler", this)
        waitUntil("the Hauler staged") { alice.stepArrow() == "Attack (1)" }
        alice.declare("Attack (1)", this)
        waitUntil("Bob declaring blockers") {
            goOn(alice); goOn(bob)
            bob.status == GameDevice.Status.YourMove && bob.stepArrow() == "No blocks"
        }
        for (n in 0..1) {
            bob.pickBlocker("Ash Pup", this, index = n)
            run(skip = java.time.Duration.ofSeconds(1))
            bob.bandCard("Zinc Hauler", owner = alice).performClick()
            waitUntil("Pup ${n + 1} blocking") { bob.stepArrow() == "Block (${n + 1})" }
        }
        bob.declare("Block (2)", this)
        waitUntil("Alice's priority after the blocks, the game settled", along = listOf(bob)) {
            alice.settled(this) && bob.settled(this) && alice.status == GameDevice.Status.YourMove &&
                alice.state.turn.step == TurnStep.DeclareBlockers && alice.state.combat?.blockers?.size == 2
        }
        away(bob, alice)
        // Her pass; then Bob's priority, which her table waits for with Assume
        alice.tap("To damage")
        waitUntil("Alice waiting for Bob with Assume") {
            alice.settled(this) && alice.state.turn.step == TurnStep.DeclareBlockers && alice.showsControl("Assume no response")
        }
        val takenBack = AppLog.text().lines().count { "Shown board taken back" in it }
        alice.tap("Assume no response")
        waitUntil("the combat damage dealt and on the server") {
            alice.engine { device.session.pending.isEmpty() } && pups(alice) == 0 &&
                alice.engine { device.session.events.any { it.assumed?.assumedBy == alice.me && it.assumed?.input == Input.PassPriority(bob.me) &&
                    it.engine?.steps.orEmpty().any { s -> (s.input as? Input.Answer)?.answer is Answer.Division } } }
        }
        repeat(5) { run(skip = java.time.Duration.ofMillis(200)) }
        assertEquals(0, pups(alice), "both Pups died and stay dead")
        assertTrue(alice.field("Zinc Hauler").isNotEmpty(), "the Hauler lives (1 + 1 damage)")
        assertEquals(takenBack, AppLog.text().lines().count { "Shown board taken back" in it }, "no board taken back")
        assertFalse(alice.refusalSeen, "nothing refused")
        assertTrue(alice.leaks.leaks.isEmpty())
    }
}

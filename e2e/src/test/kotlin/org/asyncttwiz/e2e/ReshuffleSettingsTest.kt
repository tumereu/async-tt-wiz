package org.asyncttwiz.e2e

import androidx.compose.ui.test.performClick
import arcanum.api.TurnStep
import arcanum.engine.input.Input
import org.asyncttwiz.protocol.AssumeReveals
import org.asyncttwiz.protocol.game.GameDevice
import org.asyncttwiz.protocol.game.GameEvent
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Owner 2026-11-26 (playtest F): the game settings "Reshuffle libraries on undo" and "Reshuffle libraries on branch" (both on
 * by default). Off, that kind of new line of play keeps the libraries' order: the same cards come again.
 */
class ReshuffleSettingsTest : E2e() {
    // HIS-042, ZON-024, HIS-236, LOB-282: with "Reshuffle libraries on undo" off Bob ends his first turn and Alice's phone passes
    // on into her turn, drawing her card; Bob's free Undo takes his End turn back with her draw (his device logs no reshuffle);
    // he ends his turn again and Alice draws the very same card
    @Test fun withReshuffleOnUndoOffTheSameCardIsDrawnAgain() {
        startGame(bobStarts = true, gameSettings = AssumeReveals(reshuffleOnUndo = false))
        bothOpen()
        alice.tap("Keep")
        bob.tap("Keep")
        waitUntil("Bob's Main 1", along = listOf(alice)) { bob.inOwnMain() }
        bob.hold("To combat")
        waitUntil("Alice's Main 1 after her draw", along = listOf(bob)) { alice.inOwnMain() && alice.state.turn.active == alice.me }
        waitUntil("both settled") { alice.settled(this) && bob.settled(this) }
        val hand = alice.state.player(alice.me).hand.toList()
        bob.tap("Undo")
        waitUntil("Bob back in his turn, Alice's draw taken back on both", along = listOf(alice)) {
            bob.state.turn.active == bob.me && alice.state.turn.active == bob.me
        }
        waitUntil("Bob's undo logged") { ownUndoLogged("Undid") }
        assertFalse(ownUndoLogged("reshuffled"), "HIS-236: no reshuffle to tell of")
        waitUntil("Bob in his Main 1 again", along = listOf(alice)) { bob.inOwnMain() && bob.settled(this) }
        bob.hold("To combat")
        waitUntil("Alice's Main 1 after her draw again", along = listOf(bob)) { alice.inOwnMain() && alice.state.turn.active == alice.me }
        waitUntil("both settled again") { alice.settled(this) && bob.settled(this) }
        assertEquals(hand, alice.state.player(alice.me).hand.toList(), "the same card drawn again")
        assertTrue(alice.leaks.leaks.isEmpty() && bob.leaks.leaks.isEmpty())
    }

    // HIS-042, HIS-097, LOB-282: with "Reshuffle libraries on branch" off Alice attacks with her Ember Dasher, her phone assuming
    // Bob's pass right after the attack; Bob pumps his Ash Hound there instead: a branch. Its new line keeps both libraries'
    // order (no reshuffle step in Bob's event), on both phones and the server alike
    @Test fun withReshuffleOnBranchOffABranchKeepsTheLibraries() {
        startGame(TestDeck("Dashers", listOf(TestCards.mountain to 20, TestCards.dasher to 20)),
            TestDeck("Hounds", listOf(TestCards.mountain to 20, TestCards.ashHound to 20)), bobStarts = true,
            gameSettings = AssumeReveals(reshuffleOnBranch = false),
            deal = { a, b -> "Mountain" in hand(a) && "Ember Dasher" in hand(a) && "Mountain" in hand(b) && "Ash Hound" in hand(b) })
        bothOpen()
        alice.tap("Keep")
        bob.tap("Keep")
        waitUntil("Bob's Main 1", along = listOf(alice)) { bob.inOwnMain() }
        bob.playFromHand("Mountain", this)
        waitUntil("Bob's Mountain", along = listOf(alice)) { bob.field("Mountain").isNotEmpty() && bob.inOwnMain() }
        bob.playFromHand("Ash Hound", this)
        waitUntil("Bob's Hound", along = listOf(alice)) { bob.field("Ash Hound").isNotEmpty() && bob.inOwnMain() }
        bob.hold("To combat")
        waitUntil("Alice's Main 1", along = listOf(bob)) { alice.inOwnMain() }
        alice.playFromHand("Mountain", this)
        waitUntil("Alice's Mountain", along = listOf(bob)) { alice.field("Mountain").isNotEmpty() && alice.inOwnMain() }
        alice.playFromHand("Ember Dasher", this)
        waitUntil("Alice's Dasher", along = listOf(bob)) { alice.field("Ember Dasher").isNotEmpty() && alice.inOwnMain() }
        alice.tap("To combat")
        waitUntil("Alice declaring attackers") { alice.state.turn.step == TurnStep.DeclareAttackers }
        alice.stageAttacker("Ember Dasher", this)
        waitUntil("the Dasher staged") { alice.stepArrow() == "Attack (1)" }
        alice.tap("Attack (1)")
        waitUntil("Alice waiting for Bob's blocks") { alice.showsControl("Assume no blockers") }
        val libraries = alice.state.let { s -> s.players.map { s.player(it.id).library.toList() } }

        waitUntil("Bob able to act after Alice's attack") {
            bob.settled(this) && bob.state.turn.step == TurnStep.DeclareAttackers && bob.state.priority.holder == bob.me &&
                (bob.status == GameDevice.Status.YourMove || bob.status is GameDevice.Status.Stopped)
        }
        val hound = bob.field("Ash Hound").single()
        bob.battlefieldCard("Ash Hound").performClick()
        fun pump(e: GameEvent) = e.author == bob.id && e.engine?.steps.orEmpty().any { (it.input as? Input.Activate)?.source == hound }
        waitUntil("Bob's pump on the server's main line") {
            bob.engine { device.session.pending.isEmpty() && device.session.events.any(::pump) }
        }
        val branch = bob.engine { device.session.events.first(::pump) }
        assertTrue(branch.startsLine != null, "Bob's pump starts the branch's line")
        assertTrue(branch.engine!!.steps.none { it.input == null }, "no reshuffle step")
        waitUntil("the branch on Alice's device") { alice.branchLogged() }
        alice.continuesPastBranchStop(this)
        waitUntil("Bob's pump shown on Alice's table") { alice.shown.held.any(::pump) && !alice.shown.behind }
        assertEquals(libraries, alice.state.let { s -> s.players.map { s.player(it.id).library.toList() } }, "the libraries keep their order")
        assertEquals(libraries, bob.state.let { s -> s.players.map { s.player(it.id).library.toList() } })
        assertTrue(alice.leaks.leaks.isEmpty() && bob.leaks.leaks.isEmpty())
    }
}

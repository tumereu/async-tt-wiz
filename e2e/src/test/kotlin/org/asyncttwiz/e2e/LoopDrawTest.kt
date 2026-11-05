package org.asyncttwiz.e2e

import arcanum.engine.decision.Decision
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Owner, 2026-11-05 (TRN-060): the loop-draw choice is offered when the loop can't be stopped by what the players can
 * actually do; a hand of only lands doesn't count as able to act.
 */
class LoopDrawTest : E2e() {
    private val question = "The game is in a loop of actions nobody can stop, which makes it a draw (CR 104.4b). End it as a draw, or continue?"

    // TRN-060 (CR 104.4b): Bob empties his hand in his first turn; in hers Alice, holding nothing but Mountains, casts a
    // Restless Wisp, which enters, is sacrificed, dies and comes back with only passes in between that neither could avoid
    // (her Mountains can't be played while something is on the Stack): her table asks her the loop choice
    @Test fun aLoopWithAHandOfOnlyLandsAsksTheLoopChoice() {
        startGame(TestDeck("Wisp", listOf(TestCards.wisp to 1, TestCards.mountain to 39)),
            TestDeck("Thoughts", listOf(TestCards.thoughts to 1, TestCards.mountain to 39)), bobStarts = true,
            // HIS-096: Alice's phone goes on past Bob's pauses while he is away
            aliceSettings = org.asyncttwiz.protocol.game.PlaySettings(alwaysAssumeResolves = true),
            deal = { a, b -> "Restless Wisp" in hand(a) && "Empty Thoughts" in hand(b) })
        bothOpen()
        alice.tap("Keep")
        bob.tap("Keep")
        waitUntil("Bob's Main 1", along = listOf(alice)) { bob.inOwnMain() }
        bob.playFromHand("Empty Thoughts", this)
        waitUntil("Bob's hand empty", along = listOf(alice)) { bob.state.player(bob.me).hand.isEmpty() && bob.inOwnMain() }
        bob.hold("To combat")
        waitUntil("Alice's Main 1", along = listOf(bob)) { alice.inOwnMain() }
        away(bob, alice)
        assertTrue(alice.hand("Mountain").size == alice.state.player(alice.me).hand.size - 1, "Alice holds the Wisp and Mountains only")
        alice.playFromHand("Restless Wisp", this)
        waitUntil("the loop choice on Alice's table") { alice.state.pending?.decision is Decision.ChooseOption }
        val choice = assertIs<Decision.ChooseOption>(alice.state.pending?.decision)
        assertEquals(alice.me, choice.player, "asked of Alice, who holds priority")
        assertEquals(question, choice.text)
        assertEquals(listOf("Continue", "Draw"), choice.options)
        waitUntil("the question on Alice's screen") { alice.showsText(question, substring = true) }
    }
}

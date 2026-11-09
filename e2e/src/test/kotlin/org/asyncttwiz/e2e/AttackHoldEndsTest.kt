package org.asyncttwiz.e2e

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import arcanum.api.TurnStep
import org.asyncttwiz.android.CardMovePreferences
import org.asyncttwiz.android.EngineGame
import org.asyncttwiz.android.EngineShownProbe
import org.asyncttwiz.android.HistoryPlaybackPreferences
import org.junit.After
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Owner problem report QVERHX (2026-11-09): "On my opponent's end step it says that it is my move but I have no button in
 * the bottom right hand corner." The defender, with no cards in hand, could do nothing during the other player's attack,
 * so that one event brought the attack, its damage and the step after combat. Their phone showed it with its combat hold
 * (HIS-113) and, while its steps still played, the server's next board came (its confirmation of the phone's own local
 * head): that ended the steps, but the hold waiting on them never heard of it and never ended. The other player's next
 * moves (their end step, then the untap and draw of the defender's turn) then waited behind it for ever: "Your move", no
 * main button.
 */
class AttackHoldEndsTest : E2e() {
    private val pups = TestDeck("Pups", listOf(TestCards.mountain to 20, TestCards.pup to 20))
    private val thoughts = TestDeck("Thoughts", listOf(TestCards.mountain to 20, TestCards.thoughts to 20))

    @After fun noProbe() { EngineShownProbe.listener = null }

    // HIS-113, HIS-021, TRN-063, problem report QVERHX: Alice empties her hand (Empty Thoughts), so Bob's attack, its damage
    // and his Main 2 come in one event; her phone holds on it, at her card move speed 0.25×, and the server's next board
    // arrives before the attack's first step has played (as on a phone over the internet at 1×). The holds end by their
    // time, and when Bob ends his turn her phone plays on into her own turn by itself and stops in her Main 1 with the main
    // button
    @Test fun anAttackHoldEndsAndTheDefendersTurnComes() {
        startGame(thoughts, pups, bobStarts = true, deal = { a, b -> "Empty Thoughts" in hand(a) && "Ash Pup" in hand(b) })
        // Alice's phone shows plays and combat as a new account's does; Bob's doesn't (his own hold isn't this test's)
        HistoryPlaybackPreferences(ApplicationProvider.getApplicationContext<Context>(), alice.id).showPlaysMillis =
            HistoryPlaybackPreferences.DEFAULT_SHOW_PLAYS_MILLIS
        CardMovePreferences(alice.activity, alice.id).multiplier = .25f
        bothOpen()
        alice.tap("Keep")
        bob.tap("Keep")
        waitUntil("Bob's Main 1", along = listOf(alice)) { bob.inOwnMain() }
        bob.playFromHand("Ash Pup", this)
        waitUntil("Bob's Pup", along = listOf(alice)) { bob.field("Ash Pup").isNotEmpty() && bob.inOwnMain() }
        bob.hold("To combat")
        waitUntil("Alice's Main 1", along = listOf(bob)) { alice.inOwnMain() }
        alice.playFromHand("Empty Thoughts", this)
        waitUntil("Alice's hand empty", along = listOf(bob)) { alice.state.player(alice.me).hand.isEmpty() && alice.inOwnMain() }
        alice.hold("To combat")
        waitUntil("Bob's next Main 1") { bob.inOwnMain() && bob.state.turn.number >= 3 }
        bob.tap("To combat")
        waitUntil("Bob declaring attackers") { bob.state.turn.step == TurnStep.DeclareAttackers && bob.stepArrow() == "No attack" }
        bob.stageAttacker("Ash Pup", this)
        val life = alice.state.player(alice.me).life
        // Every board Alice's phone publishes (two may come between frames)
        val boards = java.util.concurrent.CopyOnWriteArrayList<EngineGame.Shown>()
        EngineShownProbe.listener = { me, shown -> if (me == alice.id) boards += shown }
        bob.declare("Attack (1)", this)
        frameByFrame {
            fun held(shown: EngineGame.Shown) = shown.combatHold != null && shown.engine.player(alice.me).life == life - 1
            // Bob's attack reaches Alice's phone and plays back: one board with the attack, its damage and his Main 2, held
            messages("the attack's board on Alice's phone, held", frames = true) { alice.game?.shown?.let(::held) == true }
            // Drawn: its steps start (at 0.25× the first lasts far longer than these frames), the hold waits on them
            repeat(2) { frame() }
            val attack = boards.first(::held).version
            // The server's next board (its confirmation of her local head) arrives before the first step settles (or has
            // already come with it)
            messages("the server's next board on Alice's phone", frames = false) { boards.any { it.version > attack } }
            frame()
        }
        EngineShownProbe.listener = null
        assertEquals(TurnStep.PostcombatMain, alice.state.turn.step, "the attack's one event goes on to Bob's Main 2")
        waitUntil("Bob's Main 2 settled") { bob.inOwnMain(TurnStep.PostcombatMain) && bob.settled(this) }
        bob.hold(requireNotNull(bob.stepArrow()))
        // Alice doesn't play along: her phone alone takes her into her turn, where she gets the main button
        waitUntil("Alice in her own Main 1 with the main button", along = listOf(bob)) {
            alice.inOwnMain() && alice.state.turn.number >= 4 && alice.stepArrow() != null
        }
        assertNull(alice.shown.combatHold, "the holds over")
    }
}

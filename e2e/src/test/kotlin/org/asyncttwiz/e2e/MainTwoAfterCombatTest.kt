package org.asyncttwiz.e2e

import androidx.compose.ui.test.performClick
import arcanum.api.TurnStep
import arcanum.engine.input.Attack
import arcanum.engine.input.Input
import arcanum.engine.state.TargetRef
import org.asyncttwiz.android.AppLog
import org.asyncttwiz.android.HistoryPlaybackPreferences
import org.asyncttwiz.android.NOT_TAKEN
import org.asyncttwiz.appui.AppPlatform
import org.asyncttwiz.protocol.game.GameDevice
import org.asyncttwiz.protocol.game.PlaySettings
import org.junit.Test
import kotlin.test.assertTrue

/**
 * Problem report B7UZB5 (prod 0.2.1, 2026-11-18): "my second main phase got skipped after combat". The other player had no cards
 * in hand and nothing untapped, so all of their passes went by themselves and her attack's event reached her Main 2 at once,
 * while her table still played the attack's holds (HIS-113). The main button, drawn from those steps, said "To blockers", and
 * her tap on it was made where the game was: a pass of her Main 2. Now (owner 2026-11-19, TRN-163, TRN-165) the main button
 * says what her Main 2 offers from the moment her game is there, and a press skips the rest of the combat and is made there.
 */
class MainTwoAfterCombatTest : E2e() {
    private val leeches = TestDeck("Leeches", listOf(TestCards.mountain to 20, TestCards.leech to 20))
    private val allLeeches = TestDeck("All Leeches", listOf(TestCards.leech to 40))
    private val main2Words = setOf("End turn", "To end step")

    private fun logged(text: String) = AppLog.text().lines().filter { text in it }

    // TRN-163, TRN-165, HIS-113, TBL-205 (problem report B7UZB5, owner 2026-11-19): while Alice's table still holds on her attack, her
    // phone at her Main 2 already shows Main 2's words on the main button; her press skips the combat's holds and is made at Main 2
    @Test fun theMainButtonOffersMainTwoWhileTheCombatStillShowsAndItsPressSkipsAndActs() {
        startGame(leeches, allLeeches, bobStarts = true, bobSettings = PlaySettings(), deal = { a, _ -> "Ember Leech" in hand(a) })
        for (player in listOf(alice, bob)) HistoryPlaybackPreferences(AppPlatform.current, player.id).showPlaysMillis =
            HistoryPlaybackPreferences.MAX_SHOW_PLAYS_MILLIS
        bothOpen()
        alice.tap("Keep")
        bob.tap("Keep")
        waitUntil("Bob's Main 1", along = listOf(alice)) { bob.inOwnMain() }
        // Bob empties his hand: seven Leeches (free, haste), and attacks with them all, so they are tapped through Alice's turn
        repeat(7) { n ->
            if (n < 6) bob.playFromHand("Ember Leech", this)
            else {
                // His last card: alone in the hand row, it can't be told raised from the row, so two taps a moment apart
                waitUntil("Bob's game settled") { bob.settled(this) }
                bob.handCard("Ember Leech").performClick()
                run(skip = java.time.Duration.ofMillis(300))
                bob.handCard("Ember Leech").performClick()
            }
            waitUntil("Bob's Leech ${n + 1}", along = listOf(alice)) { bob.field("Ember Leech").size == n + 1 && bob.inOwnMain() }
        }
        bob.tap("To combat")
        waitUntil("Bob declaring attackers", along = listOf(alice)) {
            bob.state.turn.step == TurnStep.DeclareAttackers && bob.stepArrow() == "No attack" && bob.settled(this)
        }
        val attack = Input.DeclareAttackers(bob.me, bob.field("Ember Leech").map { Attack(it, TargetRef.Player(alice.me)) })
        assertTrue(bob.game!!.send(listOf(attack)) !is GameDevice.Acted.Refused)
        waitUntil("Bob's Main 2", along = listOf(alice)) {
            if (bob.status is GameDevice.Status.Stopped) bob.once { bob.stepArrow()?.let(bob::tap) }
            bob.inOwnMain(TurnStep.PostcombatMain) && bob.settled(this)
        }
        bob.hold(requireNotNull(bob.stepArrow()))
        waitUntil("Alice's Main 1", along = listOf(bob)) {
            if (alice.status is GameDevice.Status.Stopped) alice.once { alice.stepArrow()?.let(alice::tap) }
            alice.inOwnMain() && alice.state.turn.active == alice.me
        }
        assertTrue(alice.state.player(bob.me).hand.isEmpty(), "Bob's hand empty")
        alice.playFromHand("Ember Leech", this)
        waitUntil("Alice's Leech", along = listOf(bob)) { alice.field("Ember Leech").isNotEmpty() && alice.inOwnMain() }
        alice.tap("To combat")
        waitUntil("Alice declaring attackers", along = listOf(bob)) { alice.state.turn.step == TurnStep.DeclareAttackers && alice.stepArrow() == "No attack" }
        alice.stageAttacker("Ember Leech", this)
        val turn = alice.state.turn.number
        // The frames' clock in step with the real one: a hold can't end between two looks (E2e.clockInStep)
        clockInStep {
            alice.declare("Attack (1)", this)
            // TRN-163: her phone at her Main 2 while her table still holds on the attack: the main button already says Main 2's words
            var words: String? = null
            val seen = linkedSetOf<String>()
            try {
                waitUntil("Alice's phone at her Main 2 while her table still shows the combat", timeoutSeconds = 30) {
                    words = alice.mainWords()
                    seen += "${alice.state.turn.step} ${alice.game?.shown?.status} hold ${alice.shown.combatHold?.kind} \"$words\""
                    alice.state.turn.number == turn && alice.state.turn.step == TurnStep.PostcombatMain &&
                        alice.game?.shown?.status == GameDevice.Status.YourMove && alice.shown.combatHold != null && words in main2Words
                }
            } catch (e: AssertionError) { throw AssertionError("What Alice's phone showed meanwhile : $seen", e) }
            val label = words!!
            // TRN-165: her press skips the rest of the combat and is made at her Main 2: her turn goes on past it
            alice.tap(label)
            waitUntil("the combat skipped and Alice's Main 2 passed", along = listOf(bob)) {
                alice.shown.combatHold == null && (alice.state.turn.step != TurnStep.PostcombatMain || alice.state.turn.number != turn)
            }
            assertTrue(logged("Main button tap \"$label\" while the view still plays").isNotEmpty(), "the skip in the app log")
            assertTrue(logged("Main button tap \"$label\" made after its skip").isNotEmpty(), "the press after it in the app log")
            assertTrue(!alice.told(NOT_TAKEN), "nothing refused: ${alice.screen()}")
        }
    }
}

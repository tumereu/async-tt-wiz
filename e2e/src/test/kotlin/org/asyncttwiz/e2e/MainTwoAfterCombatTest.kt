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
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Problem report B7UZB5 (prod 0.2.1, 2026-11-18): "my second main phase got skipped after combat". The other player had no cards
 * in hand and nothing untapped, so all of their passes went by themselves and her attack's event reached her Main 2 at once,
 * while her table still played the attack's steps, the attackers' hold first (HIS-113). The main button, drawn from those
 * steps, said "To blockers", and her tap on it was made where the game was: a pass of her Main 2, and the turn went to the other
 * player. Such a press is now not taken, with the note (SYS-137, SYS-138).
 */
class MainTwoAfterCombatTest : E2e() {
    private val leeches = TestDeck("Leeches", listOf(TestCards.mountain to 20, TestCards.leech to 20))
    private val allLeeches = TestDeck("All Leeches", listOf(TestCards.leech to 40))
    private val main2Words = setOf("End turn", "To end step")

    private fun logged(text: String) = AppLog.text().lines().filter { text in it }

    // SYS-011, SYS-137, SYS-138, HIS-113, TRN-063 (problem report B7UZB5): a tap on the main button while the attack's steps still
    // play leaves the Main 2 the game reached as it is, and says it wasn't taken; her next press there acts on Main 2
    @Test fun aPressOnTheAttacksStepsDoesntPassTheMainTwoTheGameReached() {
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
            // Her phone at her Main 2 while her table still plays the attack's steps: the main button says what it said there
            var words: String? = null
            val seen = linkedSetOf<String>()
            try {
                waitUntil("Alice's phone at her Main 2 while her table still shows the combat", timeoutSeconds = 30) {
                    words = alice.mainWords()
                    seen += "${alice.state.turn.step} ${alice.game?.shown?.status} hold ${alice.shown.combatHold?.kind} \"$words\""
                    alice.state.turn.number == turn && alice.state.turn.step == TurnStep.PostcombatMain &&
                        alice.game?.shown?.status == GameDevice.Status.YourMove && words?.startsWith("To ") == true && words !in main2Words
                }
            } catch (e: AssertionError) { throw AssertionError("What Alice's phone showed meanwhile: $seen", e) }
            val label = words!!
            // Her press, as the report's press line has it (a tap the button doesn't take yet, while it is still disabled by a
            // board on its way in, is tapped again while it says the same)
            val pressLine = "main button tap \"$label\" (the device is at turn $turn PostcombatMain)"
            var taps = 0
            var lastTap = 0L
            waitUntil("Alice's press on \"$label\" while her phone is at her Main 2", timeoutSeconds = 20) {
                val now = System.nanoTime()
                if (logged(pressLine).isEmpty() && taps < 5 && now - lastTap > 300_000_000L && alice.mainWords() == label) {
                    taps++; lastTap = now; alice.tap(label)
                }
                logged(pressLine).isNotEmpty()
            }
            assertEquals(1, logged(pressLine).size, "her one press on the attack's steps")
            waitUntil("the attack's steps played and the hold over", along = listOf(bob)) { alice.shown.combatHold == null && alice.settled(this) }
            assertTrue(alice.inOwnMain(TurnStep.PostcombatMain), "still Alice's Main 2: ${alice.state.turn}")
            assertEquals(turn, alice.state.turn.number, "her turn wasn't passed")
            assertTrue(alice.told(NOT_TAKEN), "the note says the press wasn't taken: ${alice.screen()}")
            assertTrue(logged("Main button press \"$label\" not taken").isNotEmpty(), "in the app log")
        }
        // Once her table has played the attack's steps, her press on Main 2 acts there
        waitUntil("Alice's Main 2 on her table", along = listOf(bob)) {
            alice.inOwnMain(TurnStep.PostcombatMain) && alice.settled(this) && alice.mainWords() in main2Words
        }
        val label = requireNotNull(alice.mainWords())
        alice.tap(label)
        waitUntil("past Alice's Main 2", along = listOf(bob)) { alice.state.turn.step != TurnStep.PostcombatMain || alice.state.turn.number != turn }
    }
}

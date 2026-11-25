package org.asyncttwiz.e2e

import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.performClick
import arcanum.api.TurnStep
import org.asyncttwiz.android.AppLog
import org.asyncttwiz.android.HistoryPlaybackPreferences
import org.asyncttwiz.appui.AppPlatform
import org.asyncttwiz.protocol.game.GameDevice
import org.asyncttwiz.protocol.game.ResponseMode
import org.junit.Test
import java.time.Duration
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Automatic fault report GL2H6A (prod 0.2.1, 2026-11-18): "CombatStackPresentation: attack target without its card's name". A Jace
 * planeswalker token that the combat damage killed ceased to exist (CR 704.5d, 111.7): its lane, still shown while the damage
 * held the view, found no card to name it by and read "Card (gone)".
 */
class GoneTargetNameTest : E2e() {
    private val faultLine = "attack target without its card's name"

    // CMB-046: Bob (Full control) makes a Jace token with his free Ember Lesson (Empower Jace 1); Alice's hasty Ember Dasher attacks it
    // and deals it 1 damage: the token dies and, while the band stays, its lane reads "Jace Token (gone)"; no fault is reported
    @Test fun aPlaneswalkerTokenKilledInCombatKeepsItsNameOnItsLane() {
        startGame(TestDeck("Dashers", listOf(TestCards.mountain to 20, TestCards.dasher to 20)),
            TestDeck("Lessons", listOf(TestCards.mountain to 20, TestCards.lesson to 20)), bobStarts = true,
            deal = { a, b -> "Ember Dasher" in hand(a) && "Ember Lesson" in hand(b) })
        for (player in listOf(alice, bob)) HistoryPlaybackPreferences(AppPlatform.current, player.id).showPlaysMillis =
            HistoryPlaybackPreferences.MAX_SHOW_PLAYS_MILLIS
        bothOpen()
        bothKeep(bobAway = false)
        waitUntil("Bob's Main 1", along = listOf(alice)) { bob.inOwnMain() }
        bob.playFromHand("Ember Lesson", this)
        waitUntil("Bob's Jace token", along = listOf(alice)) { bob.field("Jace Token").size == 1 && bob.inOwnMain() }
        bob.hold("To combat")
        waitUntil("Alice's Main 1", along = listOf(bob)) { alice.inOwnMain() && alice.field("Jace Token", bob.me).size == 1 }
        alice.playFromHand("Ember Dasher", this)
        waitUntil("the Dasher on the battlefield", along = listOf(bob)) { alice.field("Ember Dasher").size == 1 && alice.inOwnMain() }
        // As on the report's phone: the token's controller gets priority in the damage step, its table drawn with the token gone
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
        // CMB-001: a tap on the token's chip picks it as the target, then the Dasher is staged attacking it
        alice.node(hasContentDescription("Attack Jace Token")).performClick()
        run(skip = Duration.ofSeconds(1))
        alice.stageAttacker("Ember Dasher", this)
        val faultsBefore = AppLog.text().lines().count { faultLine in it }
        // The frames' clock in step with the real one: a hold can't end between two looks (E2e.clockInStep)
        clockInStep {
            alice.declare("Attack (1)", this)
            // No hold on the attackers (HIS-113); the damage's hold ends by its time (HIS-148: no tap skips it)
            waitUntil("the damage holds Alice's view", along = listOf(bob)) {
                if (alice.status is GameDevice.Status.Waiting && (alice.status as GameDevice.Status.Waiting).offer != null)
                    alice.once { alice.game?.assume(emptyList()); alice.lobby.engineSent() }
                if (alice.status is GameDevice.Status.Stopped) alice.once { alice.stepArrow()?.let(alice::tap) }
                alice.shown.combatHold?.kind == GameDevice.CombatMoment.Damage
            }
            assertEquals(emptyList(), alice.field("Jace Token", bob.me), "the token died")
            // The hold shows the damage dealt; with the token gone from the table and the band still up (CMB-050, on either
            // phone, as their playback reaches it) its lane is "(gone)"
            val gone = hasContentDescription("Jace Token (gone)")
            var seen = false
            var standIn = false
            waitUntil("the gone lane on a band, or the combat over on both phones", along = listOf(bob)) {
                for (player in listOf(alice, bob)) {
                    if (player.shows(gone)) seen = true
                    if (player.shows(hasContentDescription("Card (gone)"))) standIn = true
                }
                // Each phone's playback past the combat: the board it draws is in the main phase after it
                seen || listOf(alice, bob).all { it.shown.combatHold == null && it.shown.board.engine?.state?.turn?.step == TurnStep.PostcombatMain }
            }
            assertTrue(seen, "the token's lane by its name once it was gone: ${alice.screen()} / ${bob.screen()}")
            assertFalse(standIn, "no stand-in name")
        }
        assertEquals(faultsBefore, AppLog.text().lines().count { faultLine in it }, "no fault reported")
    }
}

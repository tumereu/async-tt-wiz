package org.asyncttwiz.e2e

import androidx.compose.ui.test.hasContentDescription
import arcanum.api.TurnStep
import org.asyncttwiz.android.HistoryPlaybackPreferences
import org.asyncttwiz.appui.AppPlatform
import org.asyncttwiz.protocol.game.GameDevice
import org.junit.Test
import java.time.Duration
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Owner playtest, 2026-11-11: combat damage on the band shows each target's total only, and the attacker's lifelink gain shows
 * on the band too, with an icon of its own. Alice casts an Ember Leech (2/2, lifelink, haste) in her first turn and attacks
 * Bob, who has no creatures; the damage's hold shows Bob's total on his chip and Alice's gain as the life-gain heart on the
 * band, on both phones, and nothing per lane.
 */
class CombatLifeGainTest : E2e() {
    private val leeches = TestDeck("Leeches", listOf(TestCards.mountain to 20, TestCards.leech to 20))
    private val heart = hasContentDescription("Gained 2 life with the combat damage, on the band")
    private val perLane = hasContentDescription("in this lane", substring = true)

    // CMB-050 (owner playtest 2026-11-11; CR 510.1, CR 702.15b), HIS-113, CMB-001
    @Test fun anAttackersLifelinkGainShowsOnTheBand() {
        startGame(leeches, leeches, bobSettings = org.asyncttwiz.protocol.game.PlaySettings(),
            deal = { a, _ -> "Ember Leech" in hand(a) })
        for (player in listOf(alice, bob)) HistoryPlaybackPreferences(AppPlatform.current, player.id).showPlaysMillis =
            HistoryPlaybackPreferences.MAX_SHOW_PLAYS_MILLIS
        bothOpen()
        bothKeep(bobAway = false)
        alice.playFromHand("Ember Leech", this)
        waitUntil("the Leech", along = listOf(bob)) { alice.field("Ember Leech").isNotEmpty() && alice.inOwnMain() }
        alice.tap("To combat")
        waitUntil("Alice declaring attackers", along = listOf(bob)) { alice.state.turn.step == TurnStep.DeclareAttackers && alice.stepArrow() == "No attack" }
        alice.stageAttacker("Ember Leech", this)
        // The frames' clock in step with the real one: a hold can't end between two looks (E2e.clockInStep)
        clockInStep {
            alice.declare("Attack (1)", this)
            waitUntil("the attackers hold Alice's view") { alice.shown.combatHold?.kind == GameDevice.CombatMoment.Attackers }
            run(skip = Duration.ofMillis(500)) // past the double-tap window
            alice.doubleTapTable()
            waitUntil("the damage holds Alice's view", along = listOf(bob)) {
                if (alice.status is GameDevice.Status.Waiting && (alice.status as GameDevice.Status.Waiting).offer != null)
                    alice.once { alice.game?.assume(emptyList()); alice.lobby.engineSent() }
                if (alice.status is GameDevice.Status.Stopped) alice.once { alice.stepArrow()?.let(alice::tap) }
                alice.shown.combatHold?.kind == GameDevice.CombatMoment.Damage
            }
            assertEquals(18, alice.state.player(bob.me).life, "Bob took 2")
            assertEquals(22, alice.state.player(alice.me).life, "Alice gained 2 with lifelink")
            assertTrue(alice.shows(heart), "the heart on Alice's band: ${alice.screen()}")
            assertTrue(alice.shows(hasContentDescription("${bob.name} took 2 combat damage")), "Bob's total on his chip")
            assertTrue(alice.shows(hasContentDescription("Gained 2 life with the combat damage")), "the +2 at her life square")
            assertFalse(alice.shows(perLane), "nothing per lane")
            // Bob's phone shows the same from his side
            waitUntil("the damage holds Bob's view", along = listOf(alice)) { bob.shown.combatHold?.kind == GameDevice.CombatMoment.Damage }
            assertTrue(bob.shows(heart), "the heart on Bob's band: ${bob.screen()}")
            assertFalse(bob.shows(perLane), "nothing per lane")
            // The combat over, the band closed: the heart is gone with it (CMB-001: it fades out where it was)
            waitUntil("the combat over on Alice's phone", along = listOf(bob)) {
                alice.shown.combatHold == null && alice.state.turn.step != TurnStep.CombatDamage
            }
            waitUntil("the heart gone") { !alice.shows(heart) }
        }
        assertFalse(alice.refusalSeen || bob.refusalSeen, "no refusal")
    }
}

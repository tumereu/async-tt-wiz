package org.asyncttwiz.e2e

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import arcanum.api.TurnStep
import org.asyncttwiz.android.HistoryPlaybackPreferences
import org.asyncttwiz.protocol.game.GameDevice
import org.junit.Test
import java.time.Duration
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Owner, 2026-11-08: a combat that goes by itself went by too fast to read. It now holds the view, never the game, once when
 * the attackers show and once on the damage, for the "Show plays and combat" time; a tap on the table skips it; at Off
 * nothing holds. Bob has no creatures: Alice's attack goes by itself once she assumes his blocks.
 */
class CombatHoldTest : E2e() {
    private val pups = TestDeck("Pups", listOf(TestCards.mountain to 20, TestCards.pup to 20))

    /** Alice has a Pup ready and is on her second turn's declaration, with [millis] as both phones' time. */
    private fun aliceDeclaring(millis: Long) {
        startGame(pups, pups, bobSettings = org.asyncttwiz.protocol.game.PlaySettings(),
            deal = { a, _ -> "Ash Pup" in hand(a) && "Mountain" in hand(a) })
        val context = ApplicationProvider.getApplicationContext<Context>()
        for (player in listOf(alice, bob)) HistoryPlaybackPreferences(context, player.id).showPlaysMillis = millis
        bothOpen()
        bothKeep(bobAway = false)
        alice.playFromHand("Ash Pup", this)
        waitUntil("the Pup", along = listOf(bob)) { alice.field("Ash Pup").isNotEmpty() && alice.inOwnMain() }
        alice.hold("To combat")
        waitUntil("Alice's next Main 1", along = listOf(bob)) {
            if (alice.status is GameDevice.Status.Stopped) alice.once { alice.stepArrow()?.let(alice::tap) }
            alice.inOwnMain() && alice.state.turn.number >= 3
        }
        alice.tap("To combat")
        waitUntil("Alice declaring attackers", along = listOf(bob)) { alice.state.turn.step == TurnStep.DeclareAttackers && alice.stepArrow() == "No attack" }
        alice.stageAttacker("Ash Pup", this)
    }

    // HIS-113, LOB-082, HIS-112: both phones hold on the attackers, the game going on past Alice's hold on Bob's phone only as
    // far as he is shown; a tap skips Alice's; then the damage holds both, each ended by its time
    @Test fun aCombatHoldsBothViewsOnTheAttackersAndTheDamage() {
        aliceDeclaring(HistoryPlaybackPreferences.MAX_SHOW_PLAYS_MILLIS)
        val life = alice.state.player(bob.me).life
        alice.declare("Attack (1)", this)
        waitUntil("the attackers hold Alice's view") { alice.shown.combatHold?.kind == GameDevice.CombatMoment.Attackers }
        val at = alice.shown.tip
        run(skip = Duration.ofMillis(200))
        assertEquals(at, alice.shown.tip, "her phone makes nothing of its own meanwhile")
        // Her hold never holds the game: Bob's phone hears the attack and holds too
        waitUntil("the attackers hold Bob's view") { bob.shown.combatHold?.kind == GameDevice.CombatMoment.Attackers }
        // A tap on the table skips it, doing nothing else
        alice.tapTable()
        waitUntil("Alice's hold skipped") { alice.shown.combatHold == null }
        assertTrue(!alice.refusalSeen)
        // Bob's hold ends by its time; he answers as he plays along, no blocks possible with no creatures
        waitUntil("the damage holds Alice's view", along = listOf(bob)) {
            if (alice.status is GameDevice.Status.Waiting && (alice.status as GameDevice.Status.Waiting).offer != null) alice.once { alice.game!!.assume(emptyList()); alice.lobby.engineSent() }
            if (alice.status is GameDevice.Status.Stopped) alice.once { alice.stepArrow()?.let(alice::tap) }
            alice.shown.combatHold?.kind == GameDevice.CombatMoment.Damage
        }
        assertEquals(life - 1, alice.state.player(bob.me).life, "the board shows the damage dealt while it holds")
        waitUntil("the hold over by itself, Alice's phone on past it", along = listOf(bob)) { alice.shown.combatHold == null }
        assertNull(alice.shown.show)
    }

    // HIS-113, LOB-082: at Off there is no hold, though the combat is the same
    @Test fun atOffNothingHolds() {
        aliceDeclaring(0)
        var held = false
        everyRun += { if (alice.game?.shown?.combatHold != null || bob.game?.shown?.combatHold != null) held = true }
        val life = alice.state.player(bob.me).life
        alice.declare("Attack (1)", this)
        waitUntil("the damage dealt", along = listOf(bob)) {
            val status = alice.status
            if (status is GameDevice.Status.Waiting && status.offer != null) alice.once { alice.game!!.assume(emptyList()); alice.lobby.engineSent() }
            if (status is GameDevice.Status.Stopped) alice.once { alice.stepArrow()?.let(alice::tap) }
            alice.state.player(bob.me).life == life - 1
        }
        everyRun.clear()
        assertTrue(!held, "no hold at Off")
        assertNotNull(alice.game)
    }
}

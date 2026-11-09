package org.asyncttwiz.e2e

import android.content.Context
import androidx.compose.ui.test.hasTestTag
import androidx.test.core.app.ApplicationProvider
import arcanum.api.TurnStep
import org.asyncttwiz.android.HistoryPlaybackPreferences
import org.asyncttwiz.android.VIEW_PAUSED_TAG
import org.asyncttwiz.protocol.game.GameDevice
import org.junit.Test
import java.time.Duration
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Owner, 2026-11-08: a combat that goes by itself went by too fast to read. It now holds the view, never the game, once when
 * the attackers show and once on the damage, for the "Show plays and combat" time; a double tap on the table skips it, a
 * single tap does nothing (owner, 2026-11-08), a held finger pauses it; at Off nothing holds. Bob has no creatures: Alice's attack goes by itself once she assumes his blocks.
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
    // far as he is shown; a single tap does nothing, a double tap skips Alice's; then the damage holds both, each ended by
    // its time
    @Test fun aCombatHoldsBothViewsOnTheAttackersAndTheDamage() {
        aliceDeclaring(HistoryPlaybackPreferences.MAX_SHOW_PLAYS_MILLIS)
        val life = alice.state.player(bob.me).life
        alice.declare("Attack (1)", this)
        waitUntil("the attackers hold Alice's view") { alice.shown.combatHold?.kind == GameDevice.CombatMoment.Attackers }
        val at = alice.shown.tip
        run(skip = Duration.ofMillis(200))
        assertEquals(at, alice.shown.tip, "her phone makes nothing of its own meanwhile")
        // A single tap on the table does nothing at all
        alice.tapTable()
        run()
        assertEquals(GameDevice.CombatMoment.Attackers, alice.shown.combatHold?.kind, "still held after a single tap")
        // Her hold never holds the game: Bob's phone hears the attack and holds too
        waitUntil("the attackers hold Bob's view") { bob.shown.combatHold?.kind == GameDevice.CombatMoment.Attackers }
        // A double tap on the table skips it, doing nothing else: it doesn't zoom the table (TBL-012)
        val field = alice.battlefieldBounds()
        run(skip = Duration.ofMillis(500)) // past the double-tap window: that tap was a single tap
        alice.doubleTapTable()
        waitUntil("Alice's hold skipped") { alice.shown.combatHold == null }
        assertEquals(field, alice.battlefieldBounds(), "the double tap didn't zoom the table")
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

    // HIS-113, HIS-021 (owner, 2026-11-08): a finger held on the table pauses the attackers' hold however long it stays,
    // with the pause icon in the corner and nothing of Alice's own made meanwhile; lifted, the hold goes on and ends by itself.
    // Bob's phone is never held by it
    @Test fun aHeldFingerPausesTheCombatHold() {
        aliceDeclaring(HistoryPlaybackPreferences.MAX_SHOW_PLAYS_MILLIS)
        alice.declare("Attack (1)", this)
        waitUntil("the attackers hold Alice's view") { alice.shown.combatHold?.kind == GameDevice.CombatMoment.Attackers }
        val at = alice.engine { device.session.tip }
        holding(alice.table()) {
            assertTrue(alice.shows(hasTestTag(VIEW_PAUSED_TAG)), "the pause icon: ${alice.screen()}")
            // Twice the longest time
            passWhileHeld(2 * HistoryPlaybackPreferences.MAX_SHOW_PLAYS_MILLIS)
            assertEquals(GameDevice.CombatMoment.Attackers, alice.engine { device.combatHold?.kind }, "paused while held")
            assertEquals(at, alice.engine { device.session.tip }, "her phone makes nothing of its own meanwhile")
        }
        waitUntil("the hold over by itself once lifted", along = listOf(bob)) { alice.shown.combatHold?.kind != GameDevice.CombatMoment.Attackers }
        waitUntil("the pause icon gone") { !alice.shows(hasTestTag(VIEW_PAUSED_TAG)) }
    }
}

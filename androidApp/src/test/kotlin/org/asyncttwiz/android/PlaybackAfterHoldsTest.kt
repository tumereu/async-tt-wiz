package org.asyncttwiz.android

import androidx.compose.ui.test.junit4.v2.createComposeRule
import arcanum.api.TurnStep
import org.asyncttwiz.android.EngineGameFixture.Choices
import org.asyncttwiz.protocol.game.GameDevice
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Owner, 2026-11-22 (after declaring an attack, a delay before the attack triggers happened): the table went on after a play
 * shown, its target arrows or a combat hold only once something unrelated composed it again (a message from the server, the
 * other player's presence), as each board published was told to nothing the table watched. Alice's whole app ([LiveTableKit],
 * [TableScreen]) with the server answering everything as it is sent and nothing arriving afterwards: the table goes on by
 * itself.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w412dp-h892dp-port-mdpi")
class PlaybackAfterHoldsTest {
    @get:Rule val compose = createComposeRule()
    private var screen: LiveTableScreen? = null

    @After fun close() { screen?.close() }

    private fun failure(what: String) = "$what; the table's moments:\n" + screen!!.moments.takeLast(60).joinToString("\n")

    // HIS-021, HIS-112, HIS-230, TBL-150, TBL-206: Alice casts a Spark Lancer; its enters trigger shows, its arrow to Bob's Ash
    // Pup grows and is read, and then the trigger resolves and the Pup dies, nothing else arriving meanwhile
    @Test fun aTriggerResolvesAfterItsArrowsWithNothingElseArriving() {
        val s = LiveTableScreen(compose, TriggerTimingGames.lancerAtPup()).also { screen = it }
        s.open()
        s.tapInHand(Choices.LANCER)
        s.run(1_000)
        s.tapInHand(Choices.LANCER)
        assertTrue(s.runUntil(10_000) { s.playShown() }, failure("the Lancer's trigger shown"))
        assertTrue(s.runUntil(10_000) { !s.playShown() }, failure("the trigger landed on the Stack"))
        // At most 20 s of the app's time: never, before the fix
        assertTrue(s.runUntil(20_000) { !s.onField(Choices.PUP, s.kit.f.b) && s.kit.alice.shown?.status == GameDevice.Status.YourMove },
            failure("the trigger resolved after its arrows, the Pup dead and Alice's move"))
    }

    // HIS-113, HIS-112, TBL-205 (owner 2026-11-25: no hold on the attackers): Alice attacks with an Ember Raider and a Spark Harrier,
    // each with an attack trigger: their triggers show at once, and once the damage's hold ends the combat ends, nothing else arriving
    @Test fun anAttacksTriggersShowAfterItsHoldAndTheCombatEndsAfterTheDamage() {
        val s = LiveTableScreen(compose, TriggerTimingGames.raiderAndHarrier()).also { screen = it }
        s.open()
        s.tapOnField(Choices.RAIDER)
        s.run(600)
        s.tapOnField(Choices.HARRIER)
        s.run(1_000)
        s.tapMain("Attack (2)")
        // STK-030: the two triggers' order, asked as the attack is declared
        assertTrue(s.runUntil(5_000) { s.mainButton("OK") }, failure("the triggers' order asked"))
        // TRN-063: a press within MainButtonGuard.MILLIS of the last is taken for a double tap's second
        s.run(MainButtonGuard.MILLIS + 100)
        s.tapMain("OK")
        assertTrue(s.runUntil(5_000) { s.playShown() }, failure("the attack triggers shown"))
        assertEquals(null, s.kit.alice.shown?.combatHold, "no hold on the attackers")
        assertTrue(s.runUntil(20_000) { s.kit.alice.shown?.combatHold?.kind == GameDevice.CombatMoment.Damage },
            failure("the damage's hold"))
        assertTrue(s.runUntil(20_000) { s.kit.alice.shown?.let { it.status == GameDevice.Status.YourMove &&
            it.engine.turn.step == TurnStep.PostcombatMain } == true }, failure("the combat over after the damage's hold"))
    }
}

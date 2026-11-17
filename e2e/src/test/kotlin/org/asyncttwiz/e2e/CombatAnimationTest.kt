package org.asyncttwiz.e2e

import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import arcanum.api.TurnStep
import org.asyncttwiz.android.DEFAULT_CARD_MOVE_SPEED
import org.asyncttwiz.android.HistoryPlaybackPreferences
import org.asyncttwiz.android.TableProbes
import org.asyncttwiz.appui.AppPlatform
import org.asyncttwiz.protocol.game.GameDevice
import org.junit.After
import org.junit.Test
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Owner playtest 2026-11-16: "Combat animations have been borked." The target chips faded in again during the damage, and
 * from the attacker's view the end of combat was instant. Alice attacks with an Ash Pup; Bob has no creatures, so the attack
 * goes by itself once she assumes his blocks; the damage holds both views (HIS-113) and the combat ends on its own.
 */
class CombatAnimationTest : E2e() {
    private val pups = TestDeck("Pups", listOf(TestCards.mountain to 20, TestCards.pup to 20))

    /** What a player's screen showed in one round: when (the phones' frames' clock, [E2e.framesMillis]), where the other
     * player's Mountain on their battlefield was drawn (it moves with that battlefield as the band opens and closes), whether
     * any target chip was drawn and whether the band was open. */
    private class Sample(val at: Long, val otherTop: Float, val chips: Boolean, val open: Boolean)

    private fun E2ePlayer.sample(other: E2ePlayer) = Sample(framesMillis(),
        nodes(hasContentDescription("Mountain, owner ${other.name}, ", substring = true)).fetchSemanticsNodes()
            .first().boundsInRoot.top,
        shows(hasTestTag("combat-target-chip")), bandOpen())

    /**
     * CMB-001, CMB-055, CMB-054 on [viewer]'s [samples] (the combat to its end, [open] the other player's Mountain's top with the band open): the band
     * collapses with its animation, the other battlefield taking places between open and closed, and what sat on it (the
     * target chips) fades out where it was, still drawn for a while after the collapse began.
     */
    private fun assertAnimatedClose(viewer: String, samples: List<Sample>, open: Float) {
        val closed = samples.last().otherTop
        assertTrue(kotlin.math.abs(closed - open) > 2f, "$viewer: the other battlefield back after the combat ($open → $closed)")
        val start = samples.indexOfLast { it.open && kotlin.math.abs(it.otherTop - open) < .5f }
        val after = samples.drop(start + 1)
        val between = after.filter { it.otherTop > minOf(open, closed) + .5f && it.otherTop < maxOf(open, closed) - .5f }
        // At the card move speed (1.2 dp a millisecond) a battlefield moving only a few dp (the desktop table's band) gets
        // there within a frame: places between show only for a move that takes several frames
        if (kotlin.math.abs(closed - open) > 4 * 17 * DEFAULT_CARD_MOVE_SPEED) assertTrue(between.isNotEmpty(),
            "$viewer: the band collapsed with its animation, not at once: " + after.take(20).joinToString { "${it.otherTop}" })
        val fading = after.filter { it.chips }
        val fadeMillis = if (fading.isEmpty()) 0 else fading.last().at - fading.first().at
        assertTrue(fadeMillis >= 100, "$viewer: the chips faded out where they were as the band closed ($fadeMillis ms drawn)")
    }

    // CMB-001, CMB-052, CMB-055, CMB-050, HIS-113, TBL-125 (owner playtest 2026-11-16): the target chips fade in once in a combat, for the attacker
    // as she declares and for the defender as the attackers show, and never again (not at the damage); when the combat ends the
    // band collapses with its animation and its chips fade out where they were, on both phones, the attacker's too (the end
    // of combat came in the same board as her own step after the damage's hold)
    @Test fun theChipsFadeInOnceAndTheBandClosesAnimatedOnBothPhones() = combat(desktop = false)

    // CMB-001, HIS-113, WEB-015 (owner prod report 3UQ9DY, desktop Firefox: "Heavy stutters/rebuilds on combat steps"): the same
    // with Alice at the desktop table, whose table was made anew as each combat hold ended
    @Config(qualifiers = "w1440dp-h900dp-land-mdpi")
    @Test fun theDesktopTableStaysTheSameThroughTheCombat() = combat(desktop = true)

    private fun combat(desktop: Boolean) {
        startGame(pups, pups, bobSettings = org.asyncttwiz.protocol.game.PlaySettings(), aliceDesktop = desktop,
            deal = { a, b -> "Ash Pup" in hand(a) && "Mountain" in hand(a) && "Mountain" in hand(b) })
        for (player in listOf(alice, bob)) HistoryPlaybackPreferences(AppPlatform.current, player.id).showPlaysMillis = 600
        bothOpen()
        bothKeep(bobAway = false)
        alice.playFromHand("Mountain", this)
        waitUntil("Alice's Mountain", along = listOf(bob)) { alice.field("Mountain").isNotEmpty() && alice.inOwnMain() }
        alice.playFromHand("Ash Pup", this)
        waitUntil("the Pup", along = listOf(bob)) { alice.field("Ash Pup").isNotEmpty() && alice.inOwnMain() }
        alice.hold("To combat")
        // Bob's Mountain, which shows where his battlefield is drawn
        waitUntil("Bob's Main 1") { bob.inOwnMain() }
        bob.playFromHand("Mountain", this)
        waitUntil("Bob's Mountain", along = listOf(alice)) { bob.field("Mountain").isNotEmpty() && bob.inOwnMain() }
        bob.hold(requireNotNull(bob.stepArrow()))
        waitUntil("Alice's next Main 1", along = listOf(bob)) {
            if (alice.status is GameDevice.Status.Stopped) alice.once { alice.stepArrow()?.let(alice::tap) }
            alice.inOwnMain() && alice.state.turn.number >= 3
        }
        val fades = mutableListOf<Pair<String, String>>()
        TableProbes.chipsFadeIn = { me, combat -> synchronized(fades) { fades += me to combat } }
        // The tables stay the same ones throughout (HIS-113): a table made anew starts without the band it showed, and closes
        // nothing
        val made = mutableListOf<String>()
        TableProbes.made = { me -> synchronized(made) { made += me } }
        alice.tap("To combat")
        waitUntil("Alice declaring attackers", along = listOf(bob)) { alice.state.turn.step == TurnStep.DeclareAttackers && alice.stepArrow() == "No attack" }
        waitUntil("Alice's chips") { alice.shows(hasTestTag("combat-target-chip")) }
        alice.stageAttacker("Ash Pup", this)
        val life = alice.state.player(bob.me).life
        // The frames' clock in step with the real one: each round sees the band as it is drawn then (E2e.clockInStep)
        clockInStep {
            val aliceSees = mutableListOf<Sample>()
            val bobSees = mutableListOf<Sample>()
            var aliceOpen: Float? = null
            var bobOpen: Float? = null
            everyRun += {
                runCatching {
                    aliceSees += alice.sample(bob)
                    bobSees += bob.sample(alice)
                    if (alice.game?.shown?.combatHold?.kind == GameDevice.CombatMoment.Damage && alice.bandOpen()) aliceOpen = aliceSees.last().otherTop
                    if (bob.game?.shown?.combatHold?.kind == GameDevice.CombatMoment.Damage && bob.bandOpen()) bobOpen = bobSees.last().otherTop
                }
            }
            alice.declare("Attack (1)", this)
            waitUntil("the combat over on both phones, Alice in her second main phase", along = listOf(bob)) {
                val status = alice.status
                if (status is GameDevice.Status.Waiting && status.offer != null) alice.once { alice.game!!.assume(emptyList()); alice.lobby.engineSent() }
                if (status is GameDevice.Status.Stopped) alice.once { alice.stepArrow()?.let(alice::tap) }
                alice.state.turn.step == TurnStep.PostcombatMain && alice.shown.combatHold == null && bob.shown.combatHold == null &&
                    bob.state.turn.step == TurnStep.PostcombatMain && alice.settled(this) && bob.settled(this)
            }
            // What the close animates has time to play out, by the frames' clock (the defender's band closes in the very board
            // that ends the wait above; the main thread's clock runs far ahead of the frames here, E2e.framesMillis)
            val end = framesMillis() + 1500
            while (framesMillis() < end) { run(); java.util.concurrent.locks.LockSupport.parkNanos(2_000_000) }
            everyRun.clear()
            assertEquals(life - 1, alice.state.player(bob.me).life, "the damage dealt")
            assertEquals(emptyList(), synchronized(made) { made.toList() }, "no table made anew during the combat")
            val fadesNow = synchronized(fades) { fades.toList() }
            assertEquals(1, fadesNow.count { it.first == alice.id }, "Alice's chips faded in once in the combat: $fadesNow")
            assertEquals(1, fadesNow.count { it.first == bob.id }, "Bob's chips faded in once in the combat: $fadesNow")
            assertAnimatedClose("Alice", aliceSees, requireNotNull(aliceOpen) { "Alice's band open at the damage" })
            assertAnimatedClose("Bob", bobSees, requireNotNull(bobOpen) { "Bob's band open at the damage" })
        }
    }

    @After fun noProbe() { TableProbes.chipsFadeIn = null; TableProbes.made = null }
}

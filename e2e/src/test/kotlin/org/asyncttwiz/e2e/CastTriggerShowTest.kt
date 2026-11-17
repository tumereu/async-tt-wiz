package org.asyncttwiz.e2e

import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performMouseInput
import org.asyncttwiz.android.HistoryPlaybackPreferences
import org.asyncttwiz.android.StackAnimationProbe
import org.asyncttwiz.appui.AppPlatform
import org.asyncttwiz.protocol.game.AssumeResolves
import org.asyncttwiz.protocol.game.GameDevice
import org.asyncttwiz.protocol.game.PlaySettings
import org.asyncttwiz.protocol.game.ResponseMode
import org.junit.After
import org.junit.Test
import org.robolectric.annotation.Config
import java.util.Collections
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Owner playtest 2026-11-15 (Burst Lightning with a Firebrand Archer, report VSVLSK): "On web, the caster side, the
 * targeting arrow is drawn multiple times. On android, the watching side, both the spell and the trigger it results in
 * animate to the center of the screen, then they fly to the stack and then the stack is just empty and their effects are
 * just applied. No arrows were ever drawn."
 *
 * Alice (desktop layout, the web client's table) has an Ember Archer and casts an Ember Flick at Bob, her phone assuming
 * his passes, so the Flick and the Archer's trigger above it both resolve at once, nobody stopping. Bob watches on his
 * phone: both plays fly in, show and land on his Stack, the Flick's arrow grows once the trigger above it has resolved, and
 * only then does the Flick resolve. On Alice's table her own trigger shows and lands (her cast makes its one move), nothing
 * leaves her Stack before it is there, and the Flick's arrow grows once. With Bob in Control, his phone stops over each.
 */
@Config(qualifiers = "w1440dp-h900dp-land-mdpi")
class CastTriggerShowTest : E2e() {
    private val aliceDeck = TestDeck("Archers", listOf(TestCards.mountain to 14, TestCards.archer to 13, TestCards.flick to 13))

    @After fun forgetProbes() {
        StackAnimationProbe.targeting = null
        StackAnimationProbe.resolving = null
        StackAnimationProbe.landed = null
    }

    /** WEB-023: a click raises Alice's hand card [name], a click on the raised card plays it. */
    private fun clickFromHand(name: String) {
        waitUntil("Alice's game settled") { alice.settled(this) }
        alice.handCard(name).performMouseInput { click() }
        waitUntil("$name raised in Alice's hand") { alice.raisedInHand() == name && alice.settled(this) }
        alice.handCard(name).performMouseInput { click() }
    }

    /** What each table's Stack animation does, in order: the plays landing, the arrows (step 4), the resolutions (step 5). */
    private val steps = Collections.synchronizedList(ArrayList<String>())

    /**
     * Alice (desktop) and Bob with play defaults [aliceSettings] and [bobSettings], plays shown on both tables: Alice casts an
     * Ember Archer and both tables catch up; then she casts an Ember Flick and her target bar asks for its target.
     */
    private fun toTheFlicksTarget(aliceSettings: PlaySettings, bobSettings: PlaySettings? = null) {
        startGame(aliceDeck, TestDeck.thirds(), aliceDesktop = true, aliceSettings = aliceSettings, bobSettings = bobSettings,
            deal = { a, _ -> "Ember Archer" in hand(a) && "Ember Flick" in hand(a) })
        for (player in listOf(alice, bob)) HistoryPlaybackPreferences(AppPlatform.current, player.id).showPlaysMillis = 1_500
        bothOpen()
        bothKeep(bobAway = false)
        clickFromHand("Ember Archer")
        waitUntil("the Archer on Alice's battlefield, both tables caught up", along = listOf(bob)) {
            alice.field("Ember Archer").isNotEmpty() && alice.inOwnMain() && alice.state.stack.isEmpty() &&
                bob.field("Ember Archer", owner = alice.me).isNotEmpty() && !bob.shown.behind && bob.shown.show == null && quiet()
        }
        StackAnimationProbe.landed = { me, entry -> steps += "${who(me)} lands $entry" }
        StackAnimationProbe.targeting = { me, entry, targets, _ -> steps += "${who(me)} arrows $entry ($targets)" }
        StackAnimationProbe.resolving = { me, entries -> for (e in entries) steps += "${who(me)} resolves $e" }
        // Raised and cast on the frames' own clock (the desktop hand's click waits out a double click); the plays and arrows
        // come only once the target is picked, and are watched in real time
        // The mouse off the hand first: resting where the Archer was, its quick look (WEB-026) covers the cards beside it
        alice.table().performMouseInput { moveTo(center) }
        clickFromHand("Ember Flick")
        waitUntil("Alice's target bar") { alice.shows(hasText("Choose any target") and isHeading()) }
    }

    /** Alice picks Bob as the Flick's target. */
    private fun targetBob() = alice.node(hasContentDescription("${bob.name}, life", substring = true)).performClick()

    /** Waits until the Flick and the trigger have resolved on both tables ([along]: who plays along meanwhile). */
    private fun bothResolved(along: List<E2ePlayer> = emptyList()) {
        waitUntil("the Flick and the trigger resolved on both tables", timeoutSeconds = 90, along = along) {
            alice.state.stack.isEmpty() && bob.state.stack.isEmpty() && bob.state.player(bob.me).life == 18 &&
                !bob.shown.behind && bob.shown.show == null && steps.count { it.startsWith("${bob.name} resolves") } >= 2 &&
                steps.count { it.startsWith("${alice.name} resolves") } >= 2
        }
        // The last resolution's own animation
        run(skip = java.time.Duration.ofMillis(800))
    }

    /** The order on both tables: the plays shown land first (Bob's both, Alice's own trigger), nothing leaves a Stack before
     * it landed there, the trigger resolves first, and the Flick's arrow grows once, between the two resolutions. */
    private fun assertTheOrder() {
        val log = steps.toList()
        for (name in listOf(alice.name, bob.name)) {
            val own = log.filter { it.startsWith("$name ") }.map { it.removePrefix("$name ") }
            val arrows = own.filter { it.startsWith("arrows ") }
            assertEquals(1, arrows.size, "one step 4 on $name's table, the Flick's: $log")
            assertTrue(arrows.single().endsWith("(1)"), "one target, Bob: $log")
            val flick = arrows.single().removePrefix("arrows ").substringBefore(" (")
            val resolves = own.filter { it.startsWith("resolves ") }.map { it.removePrefix("resolves ") }
            assertEquals(2, resolves.size, "each entry leaves $name's Stack once: $log")
            assertEquals(flick, resolves.last(), "the trigger above the Flick resolves first on $name's table: $log")
            assertTrue(own.indexOf("arrows $flick (1)") in own.indexOf("resolves ${resolves.first()}") until own.indexOf("resolves $flick"),
                "on $name's table the Flick's arrow grows once the trigger above it has gone, before the Flick resolves: $log")
            // Nothing leaves the Stack before its play shown landed there
            for (entry in own.filter { it.startsWith("lands ") }.map { it.removePrefix("lands ") })
                assertTrue(own.indexOf("lands $entry") < own.indexOf("resolves $entry"), "$entry lands before it resolves on $name's table: $log")
        }
        val bobs = log.filter { it.startsWith("${bob.name} ") }
        assertEquals(2, bobs.count { " lands " in it }, "both plays shown land on Bob's Stack: $log")
        assertTrue(bobs.indexOfLast { " lands " in it } < bobs.indexOfFirst { " arrows " in it || " resolves " in it },
            "both land on Bob's Stack before anything of theirs plays: $log")
        val alices = log.filter { it.startsWith("${alice.name} ") }
        assertEquals(1, alices.count { " lands " in it }, "Alice's own trigger shows on her table (never her own cast): $log")
        assertTrue(alice.leaks.leaks.isEmpty() && bob.leaks.leaks.isEmpty(), "${alice.leaks.leaks} ${bob.leaks.leaks}")
    }

    // HIS-112, HIS-134, HIS-137, STK-058, STK-127, HIS-230, TBL-125 (owner playtest 2026-11-15)
    @Test fun aSpellAndTheTriggerItsCastMakesLandOnTheStackAndTheSpellsArrowGrowsOnceBeforeItResolves() {
        toTheFlicksTarget(PlaySettings(assume = AssumeResolves.Always))
        clockInStep {
            targetBob()
            bothResolved()
        }
        assertTheOrder()
    }

    // HIS-112, HIS-137, STK-058, STK-127, HIS-230, TBL-125, STK-027 (owner 2026-11-15): Bob plays in Control and Alice assumes none of his passes. On his
    // table both plays show and land, and his phone stops with the trigger on top and the Flick under it, his main button
    // offering his response (the trigger has no target, so no arrows yet). He passes: the trigger resolves, the Flick becomes
    // the top entry and its arrow grows, once, and stays drawn while his phone stops again; he passes again and the Flick
    // resolves after its arrow
    @Test fun inControlTheWatcherStopsOverTheTriggerAndThenOverTheSpellWithItsArrow() {
        toTheFlicksTarget(PlaySettings(assume = AssumeResolves.Never), PlaySettings(mode = ResponseMode.FullControl))
        fun bobs(what: String) = steps.count { it.startsWith("${bob.name} $what") }
        clockInStep {
            targetBob()
            waitUntil("Bob's phone stopped over the trigger, the Flick under it") {
                bob.status == GameDevice.Status.YourMove && bob.state.stack.size == 2 && bobs("lands") == 2 &&
                    bob.shown.show == null && bob.stepArrow() != null && bob.shows(hasContentDescription("Stack, 2 entries"))
            }
            run(skip = java.time.Duration.ofMillis(500))
            assertEquals(0, bobs("arrows"), "the trigger on top has no target: no arrows yet: $steps")
            assertEquals(0, bobs("resolves"), "nothing resolved on Bob's table while he may respond: $steps")
            bob.tap(requireNotNull(bob.stepArrow()))
            waitUntil("Bob's phone stopped over the Flick, its arrow grown", along = listOf(alice)) {
                bob.status == GameDevice.Status.YourMove && bob.state.stack.size == 1 && bobs("resolves") == 1 &&
                    bobs("arrows") == 1 && bob.stepArrow() != null && bob.settled(this)
            }
            run(skip = java.time.Duration.ofMillis(500))
            assertTrue(bob.shows(hasTestTag("stack-target-arrows")), "the Flick's arrow stays drawn while Bob may respond: ${bob.screen()}")
            assertEquals(1, bobs("arrows"), "its arrow grew once: $steps")
            bob.tap(requireNotNull(bob.stepArrow()))
            bothResolved(along = listOf(alice))
        }
        assertTheOrder()
    }

    private fun who(account: String) = if (account == alice.id) alice.name else bob.name
}

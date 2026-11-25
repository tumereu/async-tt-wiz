package org.asyncttwiz.e2e

import android.content.Context
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.test.core.app.ApplicationProvider
import arcanum.api.TurnStep
import org.asyncttwiz.android.HistoryPlaybackPreferences
import org.asyncttwiz.android.TableProbes
import org.asyncttwiz.android.VIEW_PAUSED_TAG
import org.asyncttwiz.protocol.game.GameDevice
import org.junit.After
import org.junit.Test
import java.time.Duration
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.asyncttwiz.appui.AppPlatform

/**
 * Owner, 2026-11-08: a combat that goes by itself went by too fast to read. It now holds the view, never the game, on the
 * damage, for the "Show plays and combat" time (owner, 2026-11-25: no longer when the attackers show); no tap on the table ends
 * it (owner, 2026-11-19: the main button skips it), a held finger pauses it; at Off nothing holds. Bob has no creatures: Alice's attack goes by itself once she assumes his blocks.
 */
class CombatHoldTest : E2e() {
    private val pups = TestDeck("Pups", listOf(TestCards.mountain to 20, TestCards.pup to 20))

    /** Alice has a Pup ready and is on her second turn's declaration, with [millis] as both phones' time. */
    private fun aliceDeclaring(millis: Long) {
        // The deal follows each run's random account ids (per-player random streams since 336be95d5): Bob's Mountain is asked for,
        // as playing along through his turn he plays one if he holds it, else his cleanup's discard waits for him
        startGame(pups, pups, bobSettings = org.asyncttwiz.protocol.game.PlaySettings(),
            deal = { a, b -> "Ash Pup" in hand(a) && "Mountain" in hand(a) && "Mountain" in hand(b) })
        val context = ApplicationProvider.getApplicationContext<Context>()
        for (player in listOf(alice, bob)) HistoryPlaybackPreferences(AppPlatform.current, player.id).showPlaysMillis = millis
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

    /**
     * Alice declares her staged attack and her phone goes on (Bob's blocks assumed, her stops passed, Bob playing along) until the
     * damage holds her view; returns whether either phone held before the damage was dealt (a hold on the attackers).
     */
    // HIS-096, HIS-180: Bob's passes right after her attack are assumed, and he has no blockers
    private fun aliceAttacksUpToTheDamageHold(): Boolean {
        val life = alice.state.player(bob.me).life
        var early = false
        everyRun += {
            if (alice.game?.shown?.combatHold != null && alice.state.player(bob.me).life == life ||
                bob.game?.shown?.combatHold != null && bob.state.player(bob.me).life == life) early = true
        }
        alice.declare("Attack (1)", this)
        waitUntil("the damage holds Alice's view", along = listOf(bob)) {
            val status = alice.status
            if (status is GameDevice.Status.Waiting && status.offer != null) alice.once { alice.game!!.assume(emptyList()); alice.lobby.engineSent() }
            if (status is GameDevice.Status.Stopped) alice.once { alice.stepArrow()?.let(alice::tap) }
            alice.shown.combatHold?.kind == GameDevice.CombatMoment.Damage
        }
        everyRun.clear()
        assertEquals(life - 1, alice.state.player(bob.me).life, "the board shows the damage dealt while it holds")
        return early
    }

    // HIS-113, HIS-146, HIS-148, LOB-082, HIS-112 (owner 2026-11-25): declaring the attack holds neither phone; the damage holds Alice's
    // view, her phone making nothing of its own meanwhile; neither a tap nor a double tap on the table ends it (owner 2026-11-19); it
    // ends by its time
    @Test fun aCombatHoldsTheViewOnTheDamageOnly() {
        aliceDeclaring(HistoryPlaybackPreferences.MAX_SHOW_PLAYS_MILLIS)
        // The frames' clock in step with the real one: a hold can't end between two looks (E2e.clockInStep)
        clockInStep {
            assertTrue(!aliceAttacksUpToTheDamageHold(), "no hold on the attackers, on either phone")
            val at = alice.shown.tip
            run(skip = Duration.ofMillis(200))
            assertEquals(at, alice.shown.tip, "her phone makes nothing of its own meanwhile")
            // A tap on the table doesn't end it
            alice.tapTable()
            run()
            assertEquals(GameDevice.CombatMoment.Damage, alice.shown.combatHold?.kind, "still held after a tap")
            // Nor does a double tap, which is the table's own as at any time (HIS-148, TBL-012: only the main button skips it, TRN-165)
            run(skip = Duration.ofMillis(500)) // past the double-tap window: that tap was a single tap
            alice.doubleTapTable()
            run()
            assertEquals(GameDevice.CombatMoment.Damage, alice.shown.combatHold?.kind, "still held after a double tap")
            assertTrue(!alice.refusalSeen)
            waitUntil("the hold over by itself, Alice's phone on past it", along = listOf(bob)) { alice.shown.combatHold == null }
            assertNull(alice.shown.show)
        }
    }

    /** This player's screen shows [owner]'s [name] on their battlefield with [damage] damage marked (TBL-024). */
    private fun E2ePlayer.showsDamaged(name: String, owner: E2ePlayer, damage: Int): Boolean = runCatching {
        val field = battlefieldBounds(owner)
        nodes(hasContentDescription("$name, owner ${owner.name}, ", substring = true) and hasContentDescription(", $damage damage", substring = true))
            .fetchSemanticsNodes().any { field.contains(it.boundsInRoot.center) }
    }.getOrDefault(false)

    // HIS-113, HIS-147, TBL-024 (owner playtest 2026-11-09: the view paused before the damage showed, and nothing paused on creatures
    // about to die): Alice attacks with two Pups and Bob blocks one with his Pup. Alice's view holds on the damage right after
    // it is dealt: Bob's life down by one and the two blocked Pups still on the battlefields, each with its damage marked,
    // though the game already put them into the graveyards; once the hold ends they die on her screen. Bob's phone holds
    // on the damage the same way
    @Test fun theDamageHoldsWithTheDamageShownBeforeTheCreaturesDie() {
        startGame(pups, pups, bobSettings = org.asyncttwiz.protocol.game.PlaySettings(),
            deal = { a, b -> hand(a).count { it == "Ash Pup" } >= 2 && "Ash Pup" in hand(b) })
        val context = ApplicationProvider.getApplicationContext<Context>()
        for (player in listOf(alice, bob)) HistoryPlaybackPreferences(AppPlatform.current, player.id).showPlaysMillis = HistoryPlaybackPreferences.MAX_SHOW_PLAYS_MILLIS
        bothOpen()
        bothKeep(bobAway = false)
        repeat(2) { n ->
            alice.playFromHand("Ash Pup", this)
            waitUntil("Alice's Pup ${n + 1}", along = listOf(bob)) { alice.field("Ash Pup").size == n + 1 && alice.inOwnMain() }
        }
        alice.hold("To combat")
        waitUntil("Bob's Main 1") { bob.inOwnMain() }
        bob.playFromHand("Ash Pup", this)
        waitUntil("Bob's Pup", along = listOf(alice)) { bob.field("Ash Pup").isNotEmpty() && bob.inOwnMain() }
        bob.hold(requireNotNull(bob.stepArrow()))
        waitUntil("Alice's next Main 1", along = listOf(bob)) {
            if (alice.status is GameDevice.Status.Stopped) alice.once { alice.stepArrow()?.let(alice::tap) }
            alice.inOwnMain() && alice.state.turn.number >= 3
        }
        alice.tap("To combat")
        waitUntil("Alice declaring attackers") { alice.state.turn.step == TurnStep.DeclareAttackers && alice.stepArrow() == "No attack" && alice.settled(this) }
        val attack = arcanum.engine.input.Input.DeclareAttackers(alice.me, alice.field("Ash Pup").map {
            arcanum.engine.input.Attack(it, arcanum.engine.state.TargetRef.Player(bob.me)) })
        assertTrue(alice.game!!.send(listOf(attack)) !is GameDevice.Acted.Refused)
        waitUntil("Bob declaring blockers") { bob.state.turn.step == TurnStep.DeclareBlockers && bob.stepArrow() == "No blocks" && bob.settled(this) }
        val blocker = bob.field("Ash Pup").single()
        val blocked = attack.attacks.first().attacker
        val block = arcanum.engine.input.Input.DeclareBlockers(bob.me, listOf(arcanum.engine.input.Block(blocker, blocked)))
        // The frames' clock in step with the real one: a hold can't end between two looks (E2e.clockInStep)
        clockInStep {
            // Bob's phone too, whoever made the damage's event: his own Pup shown with its damage while his view holds
            var bobSaw = false
            everyRun += { if (bob.game?.shown?.combatHold?.kind == GameDevice.CombatMoment.Damage && bob.showsDamaged("Ash Pup", bob, 1)) bobSaw = true }
            assertTrue(bob.game!!.send(listOf(block)) !is GameDevice.Acted.Refused)
            val life = alice.state.player(bob.me).life
            waitUntil("the damage holding Alice's view with the damage on her screen", along = listOf(bob)) {
                val status = alice.status
                if (status is GameDevice.Status.Waiting && status.offer != null) alice.once { alice.game!!.assume(emptyList()); alice.lobby.engineSent() }
                if (status is GameDevice.Status.Stopped) alice.once { alice.stepArrow()?.let(alice::tap) }
                alice.shown.combatHold?.kind == GameDevice.CombatMoment.Damage && alice.showsDamaged("Ash Pup", bob, 1)
            }
            val s = alice.state
            assertTrue(s.player(bob.me).graveyard.any { cardName(s, it) == "Ash Pup" }, "the game already put Bob's Pup into his graveyard")
            assertTrue(alice.showsDamaged("Ash Pup", alice, 1), "her blocked Pup still shown with its damage: ${alice.screen()}")
            assertTrue(alice.shows(hasContentDescription("${bob.name}, life ${life - 1}", substring = true)), "Bob's life down by one: ${alice.screen()}")
            assertEquals(GameDevice.CombatMoment.Damage, alice.shown.combatHold?.kind, "still held")
            waitUntil("the hold over, the blocked Pups dead on her screen", along = listOf(bob)) {
                alice.shown.combatHold == null && !alice.showsOnBattlefield("Ash Pup", bob) && alice.field("Ash Pup").size == 1
            }
            assertEquals(1, alice.field("Ash Pup").size, "her unblocked Pup lives")
            waitUntil("Bob's hold over too") { bob.shown.combatHold == null && !bob.showsOnBattlefield("Ash Pup", bob) }
            everyRun.clear()
            assertTrue(bobSaw, "Bob's view held on the damage with his dying Pup shown")
        }
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

    // HIS-113, HIS-148, HIS-021, HIS-126, HIS-127, HIS-128 (owner, 2026-11-08): a finger held on the table pauses the damage's hold however long it stays,
    // with the pause icon in the corner and nothing of Alice's own made meanwhile; lifted, the hold goes on and ends by itself.
    // Bob's phone is never held by it
    @Test fun aHeldFingerPausesTheCombatHold() {
        aliceDeclaring(HistoryPlaybackPreferences.MAX_SHOW_PLAYS_MILLIS)
        // The frames' clock in step with the real one: a hold can't end between two looks (E2e.clockInStep)
        clockInStep {
            aliceAttacksUpToTheDamageHold()
            val at = alice.engine { device.session.tip }
            holding(alice.table()) {
                assertTrue(alice.shows(hasTestTag(VIEW_PAUSED_TAG)), "the pause icon: ${alice.screen()}")
                // Twice the longest time
                passWhileHeld(2 * HistoryPlaybackPreferences.MAX_SHOW_PLAYS_MILLIS)
                assertEquals(GameDevice.CombatMoment.Damage, alice.engine { device.combatHold?.kind }, "paused while held")
                assertEquals(at, alice.engine { device.session.tip }, "her phone makes nothing of its own meanwhile")
            }
            waitUntil("the hold over by itself once lifted", along = listOf(bob)) { alice.shown.combatHold == null }
            waitUntil("the pause icon gone") { !alice.shows(hasTestTag(VIEW_PAUSED_TAG)) }
        }
    }

    // HIS-113, HIS-021, HIS-126 (full e2e run 2026-11-15): Alice's table stays the one she has as the damage's hold begins and ends.
    // It was made anew as the hold began, and a finger already down on it lost its hold and the pause (whether the finger
    // came down before or after that was timing: aHeldFingerPausesTheCombatHold failed only now and then)
    @Test fun theTableStaysTheSameThroughTheCombatHold() {
        aliceDeclaring(HistoryPlaybackPreferences.MAX_SHOW_PLAYS_MILLIS)
        var made = 0
        TableProbes.made = { me -> if (me == alice.id) made++ }
        clockInStep {
            aliceAttacksUpToTheDamageHold()
            waitUntil("the hold over by itself", along = listOf(bob)) { alice.shown.combatHold == null }
        }
        assertEquals(0, made, "Alice's table made anew during the attack and its hold")
    }

    @After fun noProbe() { TableProbes.made = null }
}

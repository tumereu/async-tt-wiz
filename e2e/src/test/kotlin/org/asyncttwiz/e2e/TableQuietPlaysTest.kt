package org.asyncttwiz.e2e

import arcanum.api.TurnStep
import org.asyncttwiz.android.HistoryPlaybackPreferences
import org.asyncttwiz.android.TableProbes
import org.asyncttwiz.appui.AppPlatform
import org.asyncttwiz.protocol.game.GameDevice
import org.asyncttwiz.protocol.game.PlaySettings
import org.junit.After
import org.junit.Test
import org.robolectric.annotation.Config
import java.time.Duration
import kotlin.test.assertTrue

/**
 * The desktop table composing only for what changed while plays and combat show (owner, 2026-11-20, desktop browser: "Desktop
 * still stutters, but less than before … mostly when plays happen"): Alice, in the desktop layout at 1440 × 900, watches Bob
 * cast his Ash Pup (shown large in the middle, TBL-203) and then attacks with hers (the combat band and its holds, TBL-205).
 * Her table's own scope composes only when what it is given changes (the board, the hooks, the layout); before, every new
 * shown state of the device composed it again for nothing: the hooks were new objects (equal ones too), and the table read
 * the device's shown state directly. Nothing here measures time (no timing tests): only what composed.
 */
@Config(qualifiers = "w1440dp-h900dp-land-mdpi")
class TableQuietPlaysTest : E2e() {
    private val pups = TestDeck("Pups", listOf(TestCards.mountain to 20, TestCards.pup to 20))

    @After fun noProbe() { TableProbes.composedWith = null }

    /** Counts Alice's table's compositions while [block] runs: all of them, and those given the same objects as the one before. */
    private fun counted(block: () -> Unit): Pair<Int, Int> {
        var all = 0
        var idle = 0
        var last: List<Any?>? = null
        TableProbes.composedWith = { me, inputs ->
            if (me == alice.id) {
                all++
                val before = last
                if (before != null && before.size == inputs.size && before.indices.all { before[it] === inputs[it] }) idle++
                last = inputs
            }
        }
        try { block() } finally { TableProbes.composedWith = null }
        return all to idle
    }

    // TBL-203, TBL-205, HIS-112, HIS-113: Bob's cast shown on Alice's desktop table and her attack's holds compose her table only
    // for a new board, new hooks or layout, never again with all of them unchanged
    @Test fun aPlayShownAndACombatComposeTheTableOnlyForWhatChanged() {
        startGame(pups, pups, bobSettings = PlaySettings(), aliceDesktop = true,
            deal = { a, b -> "Ash Pup" in hand(a) && "Mountain" in hand(a) && "Ash Pup" in hand(b) && "Mountain" in hand(b) })
        HistoryPlaybackPreferences(AppPlatform.current, alice.id).showPlaysMillis = HistoryPlaybackPreferences.DEFAULT_SHOW_PLAYS_MILLIS
        bothOpen()
        bothKeep(bobAway = false)
        alice.playFromHand("Ash Pup", this)
        waitUntil("Alice's Pup", along = listOf(bob)) { alice.field("Ash Pup").isNotEmpty() && alice.inOwnMain() }
        alice.hold("To combat")
        waitUntil("Bob's Main 1") { bob.inOwnMain() }
        // The frames' clock in step with the real one: the play's and the holds' times pass as frames (E2e.clockInStep)
        val (showComposed, showIdle) = clockInStep {
            counted {
                var shown = false
                everyRun += { if (alice.game?.shown?.show != null) shown = true }
                bob.playFromHand("Ash Pup", this)
                waitUntil("Bob's Pup shown on Alice's table and landed", along = listOf(bob)) {
                    shown && alice.game?.shown?.show == null && alice.field("Ash Pup").size == 1 && bob.field("Ash Pup").isNotEmpty()
                }
                run(skip = Duration.ofMillis(500))
            }
        }
        assertTrue(showComposed > 0, "Alice's table composed for Bob's cast")
        assertTrue(showIdle <= 2, "Alice's table composed $showIdle of $showComposed times with nothing it is given changed (Bob's cast)")
        waitUntil("Bob's Main 1 again", along = listOf(alice)) { bob.inOwnMain() && bob.stepArrow() != null }
        bob.hold(requireNotNull(bob.stepArrow()))
        waitUntil("Alice's next Main 1", along = listOf(bob)) {
            if (alice.status is GameDevice.Status.Stopped) alice.once { alice.stepArrow()?.let(alice::tap) }
            alice.inOwnMain() && alice.state.turn.number >= 3
        }
        alice.tap("To combat")
        waitUntil("Alice declaring attackers") { alice.state.turn.step == TurnStep.DeclareAttackers && alice.stepArrow() == "No attack" && alice.settled(this) }
        val attack = arcanum.engine.input.Input.DeclareAttackers(alice.me, alice.field("Ash Pup").map {
            arcanum.engine.input.Attack(it, arcanum.engine.state.TargetRef.Player(bob.me)) })
        val (combatComposed, combatIdle) = clockInStep {
            counted {
                val life = alice.state.player(bob.me).life
                check(alice.game!!.send(listOf(attack)) !is GameDevice.Acted.Refused)
                waitUntil("Bob declaring blockers", along = listOf(alice)) { bob.state.turn.step == TurnStep.DeclareBlockers && bob.stepArrow() == "No blocks" }
                bob.tap("No blocks")
                waitUntil("the damage dealt and Alice's holds over", along = listOf(bob)) {
                    if ((alice.status as? GameDevice.Status.Waiting)?.offer != null) alice.once { alice.game!!.assume(emptyList()); alice.lobby.engineSent() }
                    if (alice.status is GameDevice.Status.Stopped) alice.once { alice.stepArrow()?.let(alice::tap) }
                    alice.state.player(bob.me).life < life && alice.shown.combatHold == null && alice.status == GameDevice.Status.YourMove
                }
                run(skip = Duration.ofMillis(500))
            }
        }
        assertTrue(combatComposed > 0, "Alice's table composed for her combat")
        assertTrue(combatIdle <= 2, "Alice's table composed $combatIdle of $combatComposed times with nothing it is given changed (her combat)")
    }
}

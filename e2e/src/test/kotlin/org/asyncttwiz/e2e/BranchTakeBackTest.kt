package org.asyncttwiz.e2e

import arcanum.engine.input.Input
import org.asyncttwiz.android.EngineGame
import org.asyncttwiz.protocol.game.GameDevice
import org.asyncttwiz.protocol.game.PlaySettings
import org.junit.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Owner playtest 2026-11-05: when the other player's real answer differed from an assumed one, the assumer's table
 * jumped straight to the branch point. It must show the branch: the rewind animation back to the branch point (paced by
 * the Rewind pause), with HIS-097's note, which the player can't stop, skip or leave. Owner 2026-11-08: the rewind then
 * stops at the branch point, as playback stops, with "<name> responded differently from here · Tap to continue"; the tap
 * plays the other player's new events back as catch-up playback.
 */
class BranchTakeBackTest : E2e() {
    // HIS-097, HIS-022, HIS-106: Alice casts an Ember Herald while Bob is away and plays on ("Always assume resolves");
    // Bob comes back and, at his playback's stop at her spell (he could respond), casts an Idle Glimmer instead of
    // letting it resolve: Alice's table, open and live, rewinds to the branch point with timeline mode's look (a backward
    // transition with its steps, the note), meanwhile a tap on the table doesn't pause it and Back does nothing; it stops
    // there with "Bob responded differently from here · Tap to continue", Bob's Glimmer not played; her tap plays it back
    // as playback that looks like live play; then her table is live at the new head
    @Test fun aBranchRewindsStopsAtTheBranchPointThenPlaysTheNewEventsBack() {
        startGame(TestDeck.thirds(TestCards.herald), TestDeck.glimmers(), aliceSettings = PlaySettings(assume = org.asyncttwiz.protocol.game.AssumeResolves.Always),
            deal = { a, b -> "Mountain" in hand(a) && "Ember Herald" in hand(a) && "Idle Glimmer" in hand(b) })
        bothOpen()
        bothKeep(bobAway = true)
        alice.playFromHand("Mountain", this)
        waitUntil("the Mountain on the battlefield") { alice.field("Mountain").isNotEmpty() && alice.inOwnMain() }
        alice.playFromHand("Ember Herald", this)
        waitUntil("the Herald on Alice's battlefield") { alice.field("Ember Herald").isNotEmpty() && alice.inOwnMain() }
        waitUntil("all of Alice's on the server") { alice.engine { device.session.pending.isEmpty() } }

        // Every board Alice's table shows from here on
        val boards = mutableListOf<EngineGame.Shown>()
        everyRun += { alice.game?.shown?.let { s -> if (boards.lastOrNull()?.version != s.version) boards += s } }
        // HIS-106: going back has timeline mode's look
        var timelineLook = false
        everyRun += { if (alice.game?.shown?.takingBack == true && alice.showsTimelineLook()) timelineLook = true }
        bob.back()
        waitUntil("Bob's playback stopping at Alice's Herald") {
            if (bob.status == GameDevice.Status.CatchUp) bob.once { bob.tapToWatch() }
            bob.status is GameDevice.Status.Stopped && bob.state.stack.isNotEmpty()
        }
        val glimmer = bob.hand("Idle Glimmer").first()
        val cast = bob.game!!.send(listOf(Input.Cast(bob.me, glimmer)))
        assertTrue(cast is GameDevice.Acted.Sent, "Bob's Glimmer: $cast")

        fun takeBack(): Int = boards.indexOfFirst { it.transition?.undone != null && it.transition!!.backward.isNotEmpty() }
        fun bobsCast(): Int = boards.indexOfFirst { s -> s.held.any { e ->
            e.author == bob.id && e.engine?.steps.orEmpty().any { (it.input as? Input.Cast)?.card == glimmer } } }
        waitUntil("Alice's table going back to the branch point") { takeBack() >= 0 }
        val back = boards[takeBack()]
        assertTrue(back.transition!!.steps.isNotEmpty(), "the rewind animates back step by step")
        assertTrue(alice.told("${bob.name} responded") && !alice.told("taken back"), "HIS-097's note: only her phone's passes were taken back")
        // While it goes back: no pause, no leaving
        assertTrue(alice.shown.behind, "Bob's new events still to play back")
        assertTrue(alice.showsTimelineLook() && !alice.showsWatch(), "still going back")
        alice.tapTable()
        alice.activity.onBackPressedDispatcher.onBackPressed()
        run()
        assertNotNull(alice.game, "still at the table")
        assertTrue(!alice.shown.pausing, "a tap on the table doesn't pause the take-back")
        // HIS-106 (owner 2026-11-08): it stops at the branch point until her tap
        alice.waitsAtBranchStop(this, bob) { bobsCast() >= 0 }
        assertTrue(timelineLook, "the take-back went back with timeline mode's look")
        val stopped = boards.size
        alice.tapToWatch()
        waitUntil("Bob's Glimmer played back on Alice's table") { bobsCast() >= 0 }
        assertTrue(bobsCast() >= stopped, "the rewind and its stop first, then Bob's new events")
        waitUntil("Alice's table live at the new head") { !alice.shown.behind && alice.status !is GameDevice.Status.Playing }
        assertTrue(boards.drop(stopped).none { it.status == GameDevice.Status.CatchUp || it.branchStop }, "no second stop")
        assertTrue(!alice.showsTimelineLook(), "live again: no timeline look")
        assertTrue(alice.leaks.leaks.isEmpty() && bob.leaks.leaks.isEmpty())
    }
}

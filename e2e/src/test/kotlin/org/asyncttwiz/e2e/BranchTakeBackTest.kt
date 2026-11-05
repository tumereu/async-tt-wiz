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
 * the Rewind pause), with HIS-097's note, then the playback of the other player's new events, which the player can't
 * stop, skip or leave.
 */
class BranchTakeBackTest : E2e() {
    // HIS-097, HIS-022, HIS-106: Alice casts an Ember Herald while Bob is away and plays on ("Always assume resolves");
    // Bob comes back and, at his playback's stop at her spell (he could respond), casts an Idle Glimmer instead of
    // letting it resolve: Alice's table rewinds to the branch point with timeline mode's look (a backward transition with
    // its steps, the note), then plays Bob's Glimmer back as playback that looks like live play; meanwhile a tap on the
    // table doesn't pause it and Back does nothing; then her table is live at the new head
    @Test fun aBranchRewindsThenPlaysTheNewEventsBackAndCantBeLeft() {
        startGame(TestDeck.thirds(TestCards.herald), TestDeck.glimmers(), aliceSettings = PlaySettings(alwaysAssumeResolves = true),
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
        assertTrue(alice.showsText("responded — your later actions were taken back", substring = true), "HIS-097's note")
        // While it plays: no pause, no leaving
        assertTrue(alice.shown.behind, "Bob's new events still to play back")
        alice.tapTable()
        alice.activity.onBackPressedDispatcher.onBackPressed()
        run()
        assertNotNull(alice.game, "still at the table")
        assertTrue(!alice.shown.pausing, "a tap on the table doesn't pause the take-back")
        assertTrue(boards.drop(takeBack()).none { it.status is GameDevice.Status.Stopped && it.behind }, "nothing stopped it")
        waitUntil("Bob's Glimmer played back on Alice's table") { bobsCast() >= 0 }
        assertTrue(bobsCast() > takeBack(), "the rewind first, then Bob's new events")
        waitUntil("Alice's table live at the new head") { !alice.shown.behind && alice.status !is GameDevice.Status.Playing }
        assertTrue(timelineLook, "the take-back went back with timeline mode's look")
        assertTrue(!alice.showsTimelineLook(), "live again: no timeline look")
        assertTrue(alice.leaks.leaks.isEmpty() && bob.leaks.leaks.isEmpty())
    }
}

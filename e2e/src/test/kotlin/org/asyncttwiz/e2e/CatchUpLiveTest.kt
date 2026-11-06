package org.asyncttwiz.e2e

import arcanum.engine.input.Input
import org.asyncttwiz.protocol.PlayZone
import org.asyncttwiz.protocol.game.GameDevice
import org.asyncttwiz.protocol.game.PlaySettings
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Owner, 2026-11-05 (live / catch-up playback / timeline): coming back after the other player played, the table looks
 * and behaves like live play while their moves play out. It starts with the centre text "<name> has made moves · Tap to
 * watch"; playback has no Play/Pause button, no grey bars, no dimming or label; it stops where the player could respond
 * (Auto-skip) and is live there: Resolve on the main button confirms and playback goes on to live play, another answer
 * branches; a tap on the table pauses it at the next point where the player gets priority, and the centre text resumes it.
 */
class CatchUpLiveTest : E2e() {
    /** Bob catches up: the centre text, then playback until it stops for him. */
    private fun bobWatchesToAStop(what: String) = waitUntil(what) {
        if (bob.status == GameDevice.Status.CatchUp) bob.once { bob.tapToWatch() }
        bob.status is GameDevice.Status.Stopped
    }

    // HIS-094, HIS-095, HIS-105, TRN-059, STK-029 (owner 2026-11-05: resolving at a playback stop didn't work): Bob comes
    // back to Alice's Ember Herald played while he was away; his table looks like live play with the centre text and the
    // playback's own toggles; a tap starts playback, which stops at her spell (he holds an Idle Glimmer, so he could
    // respond); Resolve on the main button there confirms and playback goes on (stopping again at her trigger) until his
    // table is live at the newest event, with live play's toggles back
    @Test fun bobWatchesStopsWhereHeCanRespondResolvesAndGoesLive() {
        val cast = aliceCastsAHeraldWhileBobIsAway()
        bob.back()
        waitUntil("Bob's catch-up") { bob.status == GameDevice.Status.CatchUp }
        // Looks like live play: the centre text, no Play/Pause, no timeline look; playback's toggles
        assertEquals("${alice.name} has made moves · Tap to watch", bob.watchText())
        assertTrue(!bob.showsTimelineLook(), "no timeline look")
        assertTrue(!bob.showsText("Catching up"), "no catching-up label")
        assertTrue(bob.showsControl("Control") && bob.showsControl("Autotap mana"), bob.screen())
        assertTrue(!bob.showsControl("Assume resolves setting") && !bob.showsControl("Response mode"), bob.screen())

        bobWatchesToAStop("Bob's playback stopping at Alice's spell")
        assertTrue(!bob.showsTimelineLook(), "playback has no timeline look")
        val stop = assertIs<GameDevice.Status.Stopped>(bob.status)
        assertEquals(cast.next.id, stop.event.id, "it stops before the pass assumed for him")
        assertEquals("STACK", where(bob.shown.board, cast.herald))
        assertTrue(!bob.showsWatch(), "a stop of his playback is live: no centre text")
        // Resolve is the main button
        assertEquals("Resolve", bob.stepArrow())
        bob.tap("Resolve")
        waitUntil("the server taking Bob's confirmation") {
            val head = serverGame()?.localHeads?.get(bob.id)
            head != null && cast.line.indexOfFirst { it.id == head } >= cast.line.indexOf(cast.next)
        }
        assertTrue(alice.engine { device.session.events }.any { it.id == cast.next.id }, "confirmed, not branched")
        waitUntil("the Herald on Bob's battlefield") { where(bob.shown.board, cast.herald) == PlayZone.BATTLEFIELD.name }
        // On to live play: each further stop answered as the main button says
        waitUntil("Bob live at the newest event") {
            if (bob.status is GameDevice.Status.Stopped) bob.once { bob.stepArrow()?.let(bob::tap) }
            !bob.shown.behind && bob.status !is GameDevice.Status.Playing && bob.status !is GameDevice.Status.Stopped
        }
        assertTrue(bob.showsControl("Assume resolves setting") && bob.showsControl("Response mode"), "live play's toggles: ${bob.screen()}")
        assertTrue(bob.leaks.leaks.isEmpty() && alice.leaks.leaks.isEmpty())
    }

    // HIS-105, HIS-097: at his playback's stop at Alice's spell Bob casts his Idle Glimmer from his hand instead of letting
    // it resolve: the game branches (her later actions are taken back, with the note on her table) and goes on from his
    // answer
    @Test fun anotherAnswerAtAStopBranches() {
        val cast = aliceCastsAHeraldWhileBobIsAway()
        bob.back()
        bobWatchesToAStop("Bob's playback stopping at Alice's spell")
        assertEquals("STACK", where(bob.shown.board, cast.herald))
        val glimmer = atw(bob.hand("Idle Glimmer").first())
        bob.playFromHand("Idle Glimmer", this)
        waitUntil("Bob's Glimmer on the server's main line") {
            serverGame() != null && alice.engine { device.session.events }.any { e -> e.author == bob.id &&
                e.engine?.steps.orEmpty().any { (it.input as? Input.Cast)?.card?.let(::atw) == glimmer } }
        }
        // HIS-097's note on Alice's table (shown for a few seconds)
        waitUntil("Alice told") { alice.told("responded — your later actions were taken back") }
        assertTrue(alice.engine { device.session.events }.none { it.id == cast.next.id }, "the assumed line is discarded")
        // HIS-097 (analysis 2026-11-05): her End turn was on the discarded line, so it is taken back with it: the game goes
        // on from Bob's answer to her own main phase and waits for her there (it went on to Bob's turn by itself, the
        // long run of animations hiding the note)
        waitUntil("Alice in her own main phase on the new line") { alice.inOwnMain() && alice.status == GameDevice.Status.YourMove }
        assertEquals(1, alice.state.turn.number, "still her first turn")
        assertTrue(bob.leaks.leaks.isEmpty() && alice.leaks.leaks.isEmpty())
    }

    // HIS-095: Bob can't respond to anything of Alice's turn (no Glimmer), so his playback would run to the newest event;
    // a tap on the table while it runs pauses it at the next point where he gets priority (where Full control would stop),
    // live there, with the centre text back; the centre text resumes it
    @Test fun aTapOnTheTablePausesAtTheNextPriorityPoint() {
        startGame(TestDeck.thirds(TestCards.herald), TestDeck.thirds(TestCards.herald), aliceSettings = PlaySettings(alwaysAssumeResolves = true),
            deal = { a, _ -> "Mountain" in hand(a) && "Ember Herald" in hand(a) })
        bothOpen()
        bothKeep(bobAway = true)
        alice.playFromHand("Mountain", this)
        waitUntil("the Mountain on the battlefield") { alice.field("Mountain").isNotEmpty() && alice.inOwnMain() }
        alice.playFromHand("Ember Herald", this)
        waitUntil("the Herald on Alice's battlefield") { alice.field("Ember Herald").isNotEmpty() && alice.inOwnMain() }
        alice.hold("To combat")
        waitUntil("Alice's turn over, all of it on the server") {
            alice.state.turn.active == bob.me && alice.engine { device.session.pending.isEmpty() }
        }
        bob.back()
        waitUntil("Bob's catch-up") { bob.status == GameDevice.Status.CatchUp }
        bob.tapToWatch()
        waitUntil("playback running") { bob.status is GameDevice.Status.Playing }
        bob.tapTable()
        waitUntil("paused at a point where Bob gets priority") {
            (bob.status as? GameDevice.Status.Stopped)?.paused == true || !bob.shown.behind
        }
        val stop = assertIs<GameDevice.Status.Stopped>(bob.status, "a pause stop before the newest event")
        assertTrue(stop.paused)
        assertIs<Input.PassPriority>(stop.assumed)
        assertTrue(bob.showsWatch(), "the centre text resumes it")
        assertTrue(bob.stepArrow() != null, "live there: the main button")
        assertTrue(!bob.showsTimelineLook())
        val at = bob.shown.localHead
        repeat(3) { run(skip = java.time.Duration.ofSeconds(2)) }
        assertEquals(at, bob.shown.localHead, "it stays paused")
        bob.tapToWatch()
        waitUntil("Bob live at the newest event") {
            if (bob.status is GameDevice.Status.Stopped && !(bob.status as GameDevice.Status.Stopped).paused) bob.once { bob.stepArrow()?.let(bob::tap) }
            !bob.shown.behind && bob.status !is GameDevice.Status.Playing && bob.status !is GameDevice.Status.Stopped
        }
        assertTrue(bob.leaks.leaks.isEmpty() && alice.leaks.leaks.isEmpty())
    }
}

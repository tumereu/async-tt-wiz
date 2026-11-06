package org.asyncttwiz.e2e

import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import arcanum.engine.input.Input
import org.asyncttwiz.protocol.PlayZone
import org.asyncttwiz.protocol.game.GameDevice
import org.asyncttwiz.protocol.game.GameEvent
import org.junit.Test
import java.time.Duration
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Owner playtest 2026-11-06: watching the other player's playback, stopped at a decision of his own, Bob put the app in
 * the background and brought it back: his table said "Alice has made moves · Tap to watch", though there was nothing to
 * watch: his playback stood at the decision he had to make. Coming back to a stop (the app back from the background, or
 * the game opened again from the Games list) shows that same stop, live, with no centre text, with or without Alice's
 * events after it.
 */
class PlaybackStopReturnTest : E2e() {
    /**
     * Bob holds an Idle Glimmer (he could respond, HIS-095) and is away; Alice ("Assume resolves" Never) plays a Mountain,
     * casts an Ember Cub and assumes Bob lets it resolve with the main button's Assume, so the game waits for her in her
     * main phase. Bob comes back and watches to his stop before her assumed pass: the event before it is her cast, and
     * nothing of hers follows the assumed pass. The Cub's id and the assumed pass.
     */
    private fun bobStopsAtAliceSpell(): Pair<String, GameEvent> {
        startGame(TestDeck.thirds(TestCards.cub), TestDeck.glimmers(),
            deal = { a, b -> "Mountain" in hand(a) && "Ember Cub" in hand(a) && "Idle Glimmer" in hand(b) })
        bothOpen()
        bothKeep(bobAway = true)
        alice.playFromHand("Mountain", this)
        waitUntil("the Mountain on the battlefield") { alice.field("Mountain").isNotEmpty() && alice.inOwnMain() }
        val cub = atw(alice.hand("Ember Cub").first())
        alice.playFromHand("Ember Cub", this)
        waitUntil("Alice waiting for Bob at her spell") { (alice.status as? GameDevice.Status.Waiting)?.offer == GameDevice.Offer.AssumeResolves }
        alice.tap("Assume resolves")
        waitUntil("the Cub on Alice's battlefield, all of it on the server") {
            where(alice.shown.board, cub) == PlayZone.BATTLEFIELD.name && alice.inOwnMain() && alice.engine { device.session.pending.isEmpty() }
        }
        val assumed = alice.engine { device.session.events }.last()
        assertEquals(Input.PassPriority(bob.me), assumed.assumed?.input, "the line ends with her assumed pass for Bob")
        bob.back()
        waitUntil("Bob's playback stopping at Alice's spell") {
            if (bob.status == GameDevice.Status.CatchUp) bob.once { bob.tapToWatch() }
            bob.status is GameDevice.Status.Stopped
        }
        assertBobLiveAtTheStop(cub, assumed, "watched to it")
        return cub to assumed
    }

    /** Bob's table at his stop before [assumed], live as at the head (HIS-105): no centre text, Resolve on the main
     * button, the Cub still on the Stack. */
    private fun assertBobLiveAtTheStop(spell: String, assumed: GameEvent, how: String) {
        val stop = assertIs<GameDevice.Status.Stopped>(bob.status, "$how: ${bob.screen()}")
        assertEquals(assumed.id, stop.event.id, "$how: the same stop")
        assertFalse(stop.paused, "$how: a stop for his decision, not a pause")
        assertEquals(assumed.parentId, bob.shown.localHead, "$how: nothing past the stop shown")
        assertEquals("STACK", where(bob.shown.board, spell), "$how: the spell waits on the Stack")
        assertFalse(bob.showsWatch(), "$how: no \"has made moves\" (${bob.watchText()})")
        assertEquals("Resolve", bob.stepArrow(), "$how: live, Resolve on the main button")
    }

    /** From now on, whether Bob's table ever showed the centre text (HIS-094's "has made moves · Tap to watch"). */
    private fun watchShown(): () -> Boolean {
        var seen = false
        everyRun += { if (!seen && bob.game != null && bob.showsWatch()) seen = true }
        return { seen }
    }

    /** Bob leaves the table for the Games list (Back twice) and opens the game again from it: the
     * device loads it anew from the server. */
    private fun bobReopensFromTheList() {
        val open = bob.game!!
        bob.activity.onBackPressedDispatcher.onBackPressed()
        bob.activity.onBackPressedDispatcher.onBackPressed()
        waitUntil("Bob back on the Games list") { bob.lobby.state.selected == null && bob.showsText(game.gameName) }
        // The phone lets go of a game no longer open at its next connection tick (once a second, switch-review #11); opened
        // again before it, the game still in memory would show on
        run(skip = Duration.ofSeconds(1))
        bob.text(game.gameName).performScrollTo().performClick()
        waitUntil("Bob's table") { bob.game?.shown != null }
        assertTrue(bob.game !== open, "loaded anew")
    }

    /** Lets the phones' clock run several seconds (anything that would start or move playback by itself would). */
    private fun settle() = repeat(3) { run(skip = Duration.ofSeconds(2)) }

    // HIS-094, HIS-095, HIS-105, HIS-035, HIS-033 (owner playtest 2026-11-06): nothing of Alice's after Bob's stop: away
    // and back, then the game opened again from the Games list, his table is at the same stop, live, never with the
    // centre text; Resolve there confirms
    @Test fun aStopStaysLiveAfterLeavingAndReopeningWithNothingAfterIt() {
        val (cub, assumed) = bobStopsAtAliceSpell()
        val shown = watchShown()
        bob.away()
        settle()
        bob.back()
        waitUntil("Bob's game told he is back") { !bob.game!!.awayPaused }
        settle()
        assertBobLiveAtTheStop(cub, assumed, "back from the background")
        bobReopensFromTheList()
        settle()
        assertBobLiveAtTheStop(cub, assumed, "opened again from the Games list")
        assertFalse(shown(), "the centre text never showed")
        bob.tap("Resolve")
        waitUntil("the Cub on Bob's battlefield") { where(bob.shown.board, cub) == PlayZone.BATTLEFIELD.name }
        assertTrue(bob.leaks.leaks.isEmpty() && alice.leaks.leaks.isEmpty())
    }

    // HIS-094, HIS-095, HIS-105, HIS-035, HIS-033 (owner playtest 2026-11-06): Bob leaves the app at his stop and Alice
    // plays on past it (she ends her turn); back, and then with the app started
    // again and the game opened from the Games list, his table is at the same stop, live, never with the centre text;
    // Resolve there confirms and playback goes on by itself
    @Test fun aStopStaysLiveAfterLeavingAndReopeningWithTheirEventsAfterIt() {
        val (cub, assumed) = bobStopsAtAliceSpell()
        val shown = watchShown()
        away(bob, alice)
        alice.hold("To combat")
        waitUntil("Alice's turn over, all of it on the server") {
            alice.state.turn.active == bob.me && alice.engine { device.session.pending.isEmpty() }
        }
        val after = alice.engine { device.session.after(assumed.id) }.orEmpty()
        assertTrue(after.any { it.author == alice.id }, "Alice's events after Bob's stop")
        bob.back()
        waitUntil("Bob's game told he is back, with Alice's events") {
            !bob.game!!.awayPaused && bob.engine { device.session.head } == alice.engine { device.session.head }
        }
        settle()
        assertBobLiveAtTheStop(cub, assumed, "back from the background")
        bob = restart(bob)
        bob.openGame(game, this)
        settle()
        assertBobLiveAtTheStop(cub, assumed, "the app started again, the game opened from the Games list")
        assertFalse(shown(), "the centre text never showed")
        bob.tap("Resolve")
        waitUntil("the Cub on Bob's battlefield, playback going on") { where(bob.shown.board, cub) == PlayZone.BATTLEFIELD.name }
        assertTrue(bob.leaks.leaks.isEmpty() && alice.leaks.leaks.isEmpty())
    }
}

package org.asyncttwiz.e2e

import org.asyncttwiz.protocol.game.GameDevice
import org.junit.Test
import java.time.Duration
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Found by the e2e framework (owner, 2026-11-05: drop the assumed opening keep): a phone that opened the game before the
 * other player arrived assumed their opening keep at once, and went on to turn 1 without them, even after they came.
 * The starting player's phone never assumes the other player's keep: it waits for their keep (or mulligans).
 */
class OpeningKeepTest : E2e() {
    // TRN-027, TRN-028, TRN-070, HIS-096: Alice (the starting player) opens the game first and keeps while Bob hasn't
    // opened it; her phone waits for his keep, offers no Assume keep, and her turn 1 begins only on his own keep
    @Test fun theStartingPlayerWaitsForTheOtherPlayersKeepWhenOpeningFirst() {
        startGame()
        alice.openGame(game, this)
        alice.tap("Keep")
        waitUntil("Alice's keep on the server") { alice.engine { device.session.pending.isEmpty() && device.session.events.any { it.author == alice.id } } }
        // Long past any grace for a live wait: still waiting for Bob's keep
        repeat(3) { run(skip = Duration.ofSeconds(90)) }
        val waiting = assertIs<GameDevice.Status.Waiting>(alice.status, "Alice waits for Bob's keep")
        assertEquals(GameDevice.Wait.Keep, waiting.reason)
        assertEquals(null, waiting.offer, "no Assume keep")
        assertFalse(alice.showsControl("Assume keep"))
        assertEquals(0, alice.state.turn.number, "turn 1 hasn't begun")
        assertTrue(alice.engine { device.session.events + device.session.pending }.none { e -> e.engine?.steps.orEmpty().any { it.assumedBy != null } },
            "nothing assumed for Bob")

        // Bob arrives: still waiting until he keeps
        bob.openGame(game, this)
        waitUntil("both seeing the other online") {
            listOf(alice, bob).all { it.lobby.state.opponentPresence?.presence?.state == org.asyncttwiz.protocol.PresenceState.ONLINE }
        }
        run(skip = Duration.ofMillis(1500))
        assertEquals(0, alice.state.turn.number, "turn 1 waits for Bob's keep after he came")
        assertTrue(bob.showsControl("Keep"), "Bob decides on his own opening")
        bob.tap("Keep")
        waitUntil("Alice's Main 1 on Bob's own keep") { alice.inOwnMain() }
        assertTrue(alice.shown.held.any { it.author == bob.id && it.assumed == null }, "Bob's own keep on Alice's line")
        assertTrue(alice.shown.held.all { e -> e.assumed?.input.let { it == null || it is arcanum.engine.input.Input.PassPriority } },
            "nothing but passes assumed: no assumed keep on the line")
    }
}

package org.asyncttwiz.e2e

import org.asyncttwiz.protocol.game.GameDevice
import androidx.compose.ui.test.performClick
import org.junit.Test
import java.time.Duration
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Found by the e2e framework (owner, 2026-11-05): a phone that opened the game before the other player arrived assumed
 * their opening keep at once, and went on to turn 1 without them, even after they came. The starting player's phone
 * never assumes the other player's keep by itself (owner, 2026-11-15: only a press of Assume keep does, HIS-096, HIS-183).
 */
class OpeningKeepTest : E2e() {
    // TRN-027, TRN-124, TRN-070, TRN-161, HIS-096, HIS-183: Alice (the starting player) opens the game first and keeps while Bob hasn't
    // opened it; long past any grace her phone still waits for his keep with Assume keep on the main button, assumes
    // nothing by itself, and her turn 1 begins on his own keep
    @Test fun theStartingPlayerWaitsForTheOtherPlayersKeepWhenOpeningFirst() {
        startGame()
        alice.openGame(game, this)
        alice.tap("Keep")
        waitUntil("Alice's keep on the server") { alice.engine { device.session.pending.isEmpty() && device.session.events.any { it.author == alice.id } } }
        // Long past any grace for a live wait: still waiting for Bob's keep
        repeat(3) { run(skip = Duration.ofSeconds(90)) }
        val waiting = assertIs<GameDevice.Status.Waiting>(alice.status, "Alice waits for Bob's keep")
        assertEquals(GameDevice.Wait.Keep, waiting.reason)
        assertEquals(GameDevice.Offer.AssumeKeep, waiting.offer, "Assume keep offered")
        assertTrue(alice.showsControl("Assume keep"))
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

    // TRN-062, ZON-095 (owner playtest 2026-11-20: "Dont fade out cards when mulliganing"): while Alice decides on her mulligan her
    // opening hand is lit, not dimmed as under an open choice (a dimmed hand takes no taps): a tap on one of its cards is taken,
    // the card she can't play yet saying why in its bubble
    @Test fun theOpeningHandIsLitWhileDecidingOnAMulligan() {
        startGame()
        alice.openGame(game, this)
        waitUntil("Alice's Mulligan and Keep") { alice.showsControl("Mulligan") && alice.showsControl("Keep") }
        val first = alice.state.player(alice.me).hand.mapNotNull { cardName(alice.state, it) }.first()
        val told = alice.shownTexts.size
        alice.handCard(first).performClick()
        waitUntil("the tap on her opening hand taken, its bubble saying why: ${alice.screen()}") { alice.shownTexts.size > told }
        assertTrue(alice.showsControl("Keep"), "still deciding on her opening")
    }
}

package org.asyncttwiz.e2e

import arcanum.api.TurnStep
import org.asyncttwiz.android.AppLog
import org.asyncttwiz.protocol.game.AssumeResolves
import org.asyncttwiz.protocol.game.PlaySettings
import org.junit.Test
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Reports P2XUER and 3WWPA8 (prod 0.2.0-beta7): in Quick, Alice's phone passes over Bob's spell by itself as Bob ends his
 * turn, the two racing on the server; thirty seconds into her first main phase her phone sent a pass, and she says she
 * pressed nothing. Here the same race (her uplink slow, so his events reach the server first) and then more than one round
 * of the server's 30 s heads with both phones connected: only a press leaves her main phase. Every press goes into the app
 * log, so the next report can tell a press from automation.
 */
class MainPhaseStaysTest : E2e() {
    private val herald = TestDeck.thirds(TestCards.herald)

    /** The real time passing, the phones running, both connected: one more round of the server's heads and presence than
     * the report's 30 s. Only a wait: nothing is asserted about how long anything takes. */
    private fun sitAtMain() {
        val end = System.nanoTime() + TimeUnit.SECONDS.toNanos(35)
        waitUntil("the wait", timeoutSeconds = 60) { System.nanoTime() > end }
    }

    private fun logged(text: String) = AppLog.text().lines().filter { "seat ${alice.id.take(8)}" in it && text in it }

    // TRN-057, STK-036, STK-053: the race with Bob's End turn, a long sit in her Main 1, and she is still there with
    // nothing sent; LOB-089: her tap and her hold of the main button are in the app log with what it said and the step, and
    // her phone's own passes on the way never read as a press or as a pass of her main phase
    @Test fun herMainPhaseWaitsForHerPressAfterTheRaceWithHisEndTurn() {
        startGame(TestDeck.thirds(), herald, bobStarts = true, aliceSettings = PlaySettings(),
            bobSettings = PlaySettings().assuming(AssumeResolves.WhenNoMana),
            deal = { _, b -> "Mountain" in hand(b) && "Ember Herald" in hand(b) })
        bothOpen()
        alice.tap("Keep")
        bob.tap("Keep")
        waitUntil("Bob's Main 1") { bob.inOwnMain() }
        bob.playFromHand("Mountain", this)
        waitUntil("the Mountain on both phones") { bob.inOwnMain() && bob.field("Mountain").isNotEmpty() && alice.field("Mountain", bob.me).isNotEmpty() && quiet() }

        // Her uplink slow: what her phone makes for her on his spell reaches the server after his End turn
        alice.link.holdOutgoing()
        bob.playFromHand("Ember Herald", this)
        waitUntil("the Herald and its token on Bob's battlefield") {
            bob.inOwnMain() && bob.field("Ember Herald").isNotEmpty() && bob.state.battlefield.any { bob.state.obj(it)?.token == true }
        }
        val logBefore = AppLog.text().lines().size
        bob.hold("To combat")
        waitUntil("Alice's Main 1 on her phone") { alice.inOwnMain() }
        alice.link.releaseOutgoing()
        waitUntil("the race over") { alice.settled(this) && bob.settled(this) }
        assertTrue(alice.inOwnMain(), "Alice's Main 1 after the race")
        // As in the report: some of her passes taken as his same events, the rest dropped when his End turn came first
        val record = alice.engine { device.recentRecord(200) }
        assertTrue(record.any { it.startsWith("adopted") }, "her own passes raced his events: $record")
        assertTrue(AppLog.text().lines().drop(logBefore).any { "own pending events dropped: another event came first" in it }, AppLog.text())

        val turn = alice.state.turn.number
        val head = alice.engine { device.session.head }
        sitAtMain()
        assertTrue(alice.inOwnMain(), "still Alice's Main 1, her phone at ${alice.state.turn}")
        assertEquals(turn, alice.state.turn.number)
        assertEquals(head, alice.engine { device.session.head }, "nothing made on her phone")
        waitUntil("the server's game read") { serverGame() != null }
        assertEquals(head, serverGame()!!.headId, "nothing made on the server")
        assertEquals(emptyList(), logged("main button"), "she pressed nothing")
        assertEquals(emptyList(), logged("passed the player's own main phase"))

        // Her press moves on, in the log with what the button said and the step
        alice.tap("To combat")
        waitUntil("Alice past her Main 1", along = listOf(bob)) { alice.state.turn.step != TurnStep.PrecombatMain }
        assertEquals(1, logged("""turn $turn PrecombatMain: main button tap "To combat"""").size, AppLog.text())
        waitUntil("Alice's Main 2", along = listOf(bob)) { alice.inOwnMain(TurnStep.PostcombatMain) && alice.settled(this) }
        val label = assertLabel()
        alice.hold(label)
        waitUntil("Alice past her Main 2") { alice.state.turn.step != TurnStep.PostcombatMain }
        assertEquals(1, logged("""turn $turn PostcombatMain: main button hold "$label"""").size, AppLog.text())
        assertEquals(emptyList(), logged("passed the player's own main phase"))
    }

    /** What the main button says now, which it does. */
    private fun assertLabel(): String {
        val label = alice.stepArrow()
        assertFalse(label == null, "the main button on Alice's table")
        return label!!
    }
}

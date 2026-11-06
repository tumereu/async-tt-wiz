package org.asyncttwiz.e2e

import org.junit.Test
import java.time.Duration
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Owner, 2026-11-05 (built on the engine path): losing the connection shows nothing by itself; only a game action that
 * has waited 3 s for the server brings "Reconnecting…", and the action goes out once the connection is back.
 */
class ReconnectingTest : E2e() {
    // SYS-016, SYS-013: Alice's connection drops in her Main 1: nothing shows, however long; she plays a Mountain (shown
    // on her table at once), and once it has waited for the server the dialog says "Reconnecting…" with "Back to games
    // list" and her table refuses actions; the connection comes back, the dialog goes and the Mountain reaches the server
    @Test fun aGameActionWaitingForTheServerShowsReconnecting() {
        startGame(deal = { a, _ -> hand(a).count { it == "Mountain" } >= 2 })
        bothOpen()
        bothKeep(bobAway = false)
        alice.link.cut()
        waitUntil("Alice's connection gone") { game.id !in alice.lobby.state.connectedGames }
        repeat(3) { run(skip = Duration.ofSeconds(2)) }
        assertFalse(alice.showsText("Reconnecting…"), "losing the connection alone shows nothing")
        assertTrue(game.id !in alice.lobby.state.reconnectingGames)

        alice.playFromHand("Mountain", this)
        waitUntil("the Mountain on Alice's table") { alice.field("Mountain").isNotEmpty() }
        waitUntil("Reconnecting… after the action waited") { alice.showsText("Reconnecting…") }
        assertTrue(alice.showsText("Game actions unavailable."))
        assertTrue(alice.showsText("Back to games list"))
        assertTrue(game.id in alice.lobby.state.reconnectingGames, "her table refuses actions meanwhile")

        alice.link.restore()
        waitUntil("the connection back, the dialog gone", timeoutSeconds = 90) {
            game.id in alice.lobby.state.connectedGames && !alice.showsText("Reconnecting…")
        }
        waitUntil("the Mountain on the server and on Bob's table") {
            alice.engine { device.session.pending.isEmpty() } && bob.field("Mountain", alice.me).isNotEmpty()
        }
    }
}

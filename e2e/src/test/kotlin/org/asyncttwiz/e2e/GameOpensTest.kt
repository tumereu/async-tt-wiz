package org.asyncttwiz.e2e

import org.junit.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Owner playtest 2026-11-04 (build e25f6be9, fixed in cd2755c6): a game started while the players' apps were open
 * didn't open on the second phone: the start's revision reached a device with no engine game for it, went to the old
 * catch-up download, which failed and dropped the socket, again every second (the reconnect loop).
 */
class GameOpensTest : E2e() {
    // SYS-017: both apps watch the game in their Games lists while it starts; each opens it, its table loads
    // and the connection stays up
    @Test fun aGameStartedWhileBothAppsAreOpenOpensOnBothPhones() {
        startGame(ready = false)
        for (player in listOf(alice, bob)) {
            waitUntil("${player.name} watching the game before it starts") { game.id in player.lobby.state.connectedGames }
        }
        ready()
        for (player in listOf(alice, bob)) {
            player.openGame(game, this)
            assertNotNull(player.game?.shown, "${player.name}'s table")
        }
        // A few seconds of the apps' own maintenance (the loop reconnected every second)
        repeat(10) {
            run(skip = java.time.Duration.ofMillis(500))
            for (player in listOf(alice, bob)) {
                assertTrue(game.id in player.lobby.state.connectedGames, "${player.name} still connected")
                assertTrue(game.id !in player.lobby.state.reconnectingGames, "${player.name} not reconnecting")
                assertTrue(player.game?.loadFailed == false)
            }
        }
    }
}

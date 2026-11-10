package org.asyncttwiz.e2e

import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import org.asyncttwiz.protocol.GameLifecycle
import org.junit.Test
import kotlin.test.assertFalse

/**
 * Found 2026-11-09 building the game ending: after the server refused Bob's Ready, his phone still showed him Ready (its
 * local prediction never taken back). A refused lobby action takes back what it showed.
 */
class RefusedLobbyActionTest : E2e() {
    /** Opens the game's lobby from the main menu's Games list. */
    private fun E2ePlayer.openLobby() {
        waitUntil("$name's menu") { shows(hasTestTag("menu-GAMES")) }
        tagged("menu-GAMES").performClick()
        waitUntil("the game in $name's Games list") { showsText(this@RefusedLobbyActionTest.game.gameName) }
        text(this@RefusedLobbyActionTest.game.gameName).performScrollTo().performClick()
    }

    private fun E2ePlayer.shownReady() = lobby.state.preparation?.players?.single { it.accountId == id }?.ready == true

    // LOB-046, TRN-065: Bob concedes and his app starts again in the lobby before Alice's OK; his Ready goes to the server,
    // which refuses it (no Ready before both OKs): his phone then shows him not Ready, as the server has him
    @Test fun aRefusedReadyIsTakenBack() {
        startGame()
        bothOpen()
        bothKeep(bobAway = false)
        bob.game!!.concede()
        bob.lobby.engineSent()
        waitUntil("the game over on the server") { bob.lobby.state.detail?.lifecycle == GameLifecycle.LOBBY }
        bob = restart(bob)
        bob.openLobby()
        waitUntil("Bob's lobby") { bob.lobby.state.detail?.lifecycle == GameLifecycle.LOBBY && bob.lobby.state.preparation != null }
        val gameId = game.id
        bob.lobby.ready()
        waitUntil("Bob's Ready shown and sent") { gameId in bob.lobby.state.pendingGames || bob.shownReady() }
        waitUntil("the server's answer") { gameId !in bob.lobby.state.pendingGames && gameId !in bob.lobby.state.queuedGames }
        run(skip = java.time.Duration.ofSeconds(1))
        assertFalse(ServerSetup.head(bob.account, gameId).preparation!!.players.single { it.accountId == bob.id }.ready, "refused by the server")
        assertFalse(bob.shownReady(), "Bob's phone shows him not Ready: ${bob.lobby.state.preparation?.players}")
    }
}

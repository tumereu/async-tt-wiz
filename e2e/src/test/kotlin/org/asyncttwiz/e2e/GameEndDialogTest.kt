package org.asyncttwiz.e2e

import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.isEnabled
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import org.asyncttwiz.android.GAME_END_DIALOG_TAG
import org.asyncttwiz.android.GAME_END_OK_TAG
import org.asyncttwiz.protocol.GameLifecycle
import org.asyncttwiz.protocol.PresenceState
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * TRN-065, TRN-136, TRN-137, TRN-140, TRN-141, TRN-064, LOB-046 (owner playtest 2026-11-07: the result was "auto-dismissed" when the other player pressed OK
 * first; owner 2026-11-09: "I could not press OK because it was saying waiting for X to catch up ... I should not be taken
 * back until I acknowledge it -- one player acknowledging is insufficient"): OK works whenever the dialog shows, after its
 * first greyed moments; a result the rules gave takes both OKs, each can be taken back until then; after a concession or an
 * agreed result the player whose own action ended it has given theirs, and (owner 2026-11-09) nobody goes to the lobby
 * until both OKs stand, whatever the ending.
 */
class GameEndDialogTest : E2e() {
    private val dialog = hasTestTag(GAME_END_DIALOG_TAG)
    private fun E2ePlayer.showsDialog(vararg texts: String) =
        shows(dialog) && texts.all { shows(hasText(it) and hasAnyAncestor(dialog)) }
    private fun E2ePlayer.inLobby() = lobby.state.detail?.lifecycle == GameLifecycle.LOBBY && shows(hasTestTag("lobby-history-tab"))
    private fun E2ePlayer.atTable() = !shows(hasTestTag("lobby-history-tab")) && shows(dialog)
    /** What [atTable] reads of this player's screen now, for a failure's message. */
    private fun E2ePlayer.where() = "$name: lobby ${shows(hasTestTag("lobby-history-tab"))}, dialog ${shows(dialog)}, " +
        "status ${game?.shown?.status}, lifecycle ${lobby.state.detail?.lifecycle}"

    /** Presses OK once it isn't greyed any more (its first moments, TRN-065, TRN-136). */
    private fun E2ePlayer.pressOk(vararg along: E2ePlayer) {
        val armed = hasTestTag(GAME_END_OK_TAG) and isEnabled()
        waitUntil("$name's OK no longer greyed", along = along.toList()) { shows(armed) }
        node(armed).performClick()
    }

    /** Opens [game]'s lobby from the main menu's Games list. */
    private fun E2ePlayer.openLobby() {
        waitUntil("$name's menu") { shows(hasTestTag("menu-GAMES")) }
        tagged("menu-GAMES").performClick()
        waitUntil("the game in $name's Games list") { showsText(this@GameEndDialogTest.game.gameName) }
        text(this@GameEndDialogTest.game.gameName).performScrollTo().performClick()
    }

    /** Alice's Ember Ruin takes Bob's life to 0: the result on both tables. */
    private fun aliceWinsByTheRules() {
        startGame(TestDeck("Ruin", listOf(TestCards.ruin to 1, TestCards.mountain to 39)), deal = { a, _ -> "Ember Ruin" in hand(a) })
        bothOpen()
        bothKeep(bobAway = false)
        alice.playFromHand("Ember Ruin", this)
        waitUntil("the result on both tables", along = listOf(bob)) {
            alice.showsDialog("You won", "${bob.name}'s life reached 0") && bob.showsDialog("You lost", "Your life reached 0")
        }
    }

    private val waitingForBob get() = "Waiting for ${bob.name} to acknowledge"
    private val waitingForAlice get() = "Waiting for ${alice.name} to acknowledge"

    // TRN-065, TRN-137, TRN-138 (owner, 2026-11-09, the report's two bugs): Bob is away and hasn't seen Alice's win: her OK works all the same,
    // and she stays at the table waiting for him; Bob comes back to the result, his dialog not dismissed by her OK, and only
    // his OK takes them both to the lobby, where nobody owes an OK any more
    @Test fun okWorksBeforeTheOtherPlayerHasSeenTheResultAndOnlyBothOksEndIt() {
        startGame(TestDeck("Ruin", listOf(TestCards.ruin to 1, TestCards.mountain to 39)), deal = { a, _ -> "Ember Ruin" in hand(a) })
        bothOpen()
        bothKeep(bobAway = false)
        away(bob, alice)
        waitUntil("Alice seeing Bob away") { alice.lobby.state.opponentPresence?.presence?.state == PresenceState.OFFLINE }
        alice.playFromHand("Ember Ruin", this)
        waitUntil("Alice's result") { alice.showsDialog("You won", "${bob.name}'s life reached 0") }
        alice.pressOk()
        waitUntil("Alice waiting for Bob") { alice.showsDialog("You won", waitingForBob) && alice.showsText("Take back OK") }
        bob.back()
        waitUntil("Bob's result, Alice's OK taking nothing from him", along = listOf(bob)) {
            if (bob.shows(hasTestTag("centre-action"))) bob.tapToWatch()
            bob.showsDialog("You lost", "Your life reached 0")
        }
        assertFalse(bob.shows(hasText(waitingForAlice)), "Bob hasn't pressed his")
        assertTrue(alice.atTable(), "one OK isn't enough")
        bob.pressOk(alice)
        waitUntil("both in the lobby", along = listOf(bob)) { alice.inLobby() && bob.inLobby() }
        assertFalse(alice.shows(dialog) || bob.shows(dialog), "both have pressed OK")
        assertFalse(alice.shows(hasTestTag("result-unseen")) || bob.shows(hasTestTag("result-unseen")), "nobody owes one")
    }

    // TRN-065, TRN-137, TRN-139: Alice takes her OK back before Bob's: Bob's OK then doesn't end it; hers again does. Reopening shows where
    // the OK stands: Bob's app starts again and he sees his own OK waiting for hers, on the table
    @Test fun anOkIsTakenBackUntilTheOtherPlayerPressesTheirs() {
        aliceWinsByTheRules()
        alice.pressOk(bob)
        waitUntil("Alice waiting for Bob", along = listOf(bob)) { alice.showsDialog(waitingForBob) }
        alice.text("Take back OK").performClick()
        waitUntil("Alice's OK taken back", along = listOf(bob)) { !alice.showsText(waitingForBob) && !alice.showsText("Take back OK") }
        bob.pressOk(alice)
        waitUntil("Bob waiting for Alice", along = listOf(alice)) { bob.showsDialog(waitingForAlice) }
        assertTrue(alice.atTable() && bob.atTable(), "her OK is gone, so nothing is final")
        bob = restart(bob)
        bob.openLobby()
        waitUntil("Bob's OK still standing on opening the game again", along = listOf(alice)) { bob.showsDialog(waitingForAlice) }
        alice.pressOk(bob)
        waitUntil("both in the lobby", along = listOf(bob)) { alice.inLobby() && bob.inLobby() && !alice.shows(dialog) && !bob.shows(dialog) }
    }

    // TRN-065, TRN-137, HIS-084, HIS-200: with Bob's OK on the result, Alice's Undo asks him as usual; on his yes the result and every OK go,
    // and the game goes on from before her Ruin
    @Test fun undoBeforeTheResultIsFinalTakesTheOksWithIt() {
        aliceWinsByTheRules()
        bob.pressOk(alice)
        waitUntil("Bob waiting for Alice", along = listOf(alice)) { bob.showsDialog(waitingForAlice) }
        alice.tap("Undo")
        val confirm = hasTestTag("rewind-confirm") and isEnabled()
        waitUntil("Alice's undo request dialog", along = listOf(bob)) { alice.shows(confirm) }
        alice.node(confirm).performClick()
        waitUntil("Bob asked to accept the undo", along = listOf(alice)) { bob.shows(hasTestTag("request-accept")) }
        bob.node(hasTestTag("request-accept")).performClick()
        waitUntil("the result gone from both tables", along = listOf(bob)) { !alice.shows(dialog) && !bob.shows(dialog) }
        assertTrue(alice.hand("Ember Ruin").isNotEmpty(), "back in her hand")
        assertEquals(GameLifecycle.ACTIVE, alice.lobby.state.detail?.lifecycle)
        assertTrue(alice.engine { device.model.table.resultOks.isEmpty() }, "no OK left on the board")
    }


    /** Bob concedes on his device while it is Alice's turn (TRN-064: his concession needs nobody's agreement), as his
     * table's Concede does. */
    private fun bobConcedes() {
        val game = bob.game!!
        game.concede()
        bob.lobby.engineSent()
    }

    /** [player] concedes from Table actions (TRN-064). */
    private fun concede(player: E2ePlayer) {
        player.tap("Table actions")
        waitUntil("${player.name}'s table actions") { player.showsText("Concede") }
        player.text("Concede").performClick()
        waitUntil("the Concede dialog") { player.showsText("You lose this game at once.") }
        player.text("Concede").performClick()
    }

    /** In the lobby with nothing over it: both OKs stand. */
    private fun E2ePlayer.inFreeLobby() = inLobby() && !shows(dialog) && !shows(hasTestTag("lobby-blocked"))

    /** Whether [player] is Ready in the open game's lobby. */
    private fun ready(player: E2ePlayer) = player.lobby.state.preparation?.players?.any { it.accountId == player.id && it.ready } == true

    // TRN-064, TRN-065, TRN-140, TRN-141, TRN-142, TRN-143, LOB-046 (owner, 2026-11-09: nobody goes to the lobby until both have pressed OK): Alice
    // concedes: the game is over and recorded at once, but she stays at the table with "Waiting for Bob to acknowledge" and no
    // button (her concession was her OK); Bob sees "You won" with "Alice conceded". Alice leaves for the Games list; opening
    // the game again (owner, 2026-11-22) opens its table with the same dialog over its final board, not the lobby, and her
    // Ready is refused. Bob's OK takes them both to the lobby, where the next game waits for both Readies
    @Test fun aConcederWaitsAtTheTableForTheOtherPlayersOk() {
        startGame()
        bothOpen()
        bothKeep(bobAway = false)
        concede(alice)
        waitUntil("both results", along = listOf(bob)) {
            bob.showsDialog("You won", "${alice.name} conceded") && alice.showsDialog("You lost", "You conceded", waitingForBob)
        }
        waitUntil("the game over on the server") { alice.lobby.state.detail?.lifecycle == GameLifecycle.LOBBY }
        run(skip = java.time.Duration.ofSeconds(3))
        assertTrue(alice.atTable() && bob.atTable(), "both stay at the table: ${alice.where()}; ${bob.where()}")
        assertFalse(alice.shows(hasTestTag(GAME_END_OK_TAG)) || alice.showsText("Take back OK"), "nothing for the conceder to press")
        assertTrue(bob.shows(hasTestTag(GAME_END_OK_TAG)), "Bob's OK")
        // Alice leaves for the Games list and comes back: TRN-142 (owner, 2026-11-22): the game's table again, the dialog over
        // the ended play's final board, as when it ended live
        alice.lobby.back()
        waitUntil("Alice off the table") { !alice.shows(dialog) }
        waitUntil("Alice's Games list or menu") { alice.showsText(game.gameName) || alice.shows(hasTestTag("menu-GAMES")) }
        if (!alice.showsText(game.gameName)) alice.tagged("menu-GAMES").performClick()
        waitUntil("the game in Alice's Games list") { alice.showsText(game.gameName) }
        alice.nodes(hasText(game.gameName))[0].performScrollTo().performClick()
        waitUntil("Alice's table with the dialog: ${alice.where()}") { alice.atTable() && alice.showsDialog("You lost", "You conceded", waitingForBob) }
        assertFalse(alice.shows(hasTestTag(GAME_END_OK_TAG)) || alice.showsText("Take back OK"), "still nothing for the conceder to press")
        assertTrue(arcanum.engine.Engine.result(alice.state) != null, "the ended play's final board behind the dialog")
        assertFalse(alice.showsControl("Cancel game"), "no Cancel game while an OK is owed")
        // TRN-143: her Ready, asked for as from a lobby that didn't know, is refused until both OKs stand
        alice.lobby.ready()
        repeat(3) { run(skip = java.time.Duration.ofSeconds(1)) }
        assertFalse(ServerSetup.head(aliceAccount, game.id).preparation!!.players.single { it.accountId == alice.id }.ready,
            "no Ready before both OKs")
        waitUntil("her Ready shown as the server has it") { !ready(alice) }
        assertTrue(alice.atTable(), "Alice still at the table: ${alice.where()}")
        assertTrue(bob.atTable(), "Bob still at the table")
        bob.pressOk(alice)
        waitUntil("both in the lobby once both OKs stand", along = listOf(bob)) { alice.inFreeLobby() && bob.inFreeLobby() }
        assertFalse(alice.shows(hasTestTag("result-unseen")) || bob.shows(hasTestTag("result-unseen")), "nobody owes one")
    }

    // TRN-064, TRN-065, TRN-141, LOB-046 (owner, prod 0.2.0: a player was taken to the lobby right after a concession; the full e2e run
    // of 2026-11-16 caught it in aConcederWaitsAtTheTableForTheOtherPlayersOk): the game's head, read after Alice's concession,
    // reaches both phones before the revision that ended the play (a head is read as soon as asked for, a revision published
    // up to a moment after its commit): both still stay at the table with the dialog, and Bob's OK takes them both to the lobby
    @Test fun aHeadAheadOfTheConcessionTakesNobodyToTheLobby() {
        startGame()
        bothOpen()
        bothKeep(bobAway = false)
        alice.connection.holdRevisions(anyHead = true)
        bob.connection.holdRevisions(anyHead = true)
        concede(alice)
        waitUntil("the game over on the server") { ServerSetup.head(aliceAccount, game.id).summary.lifecycle == GameLifecycle.LOBBY }
        alice.askHead()
        bob.askHead()
        waitUntil("the heads having overtaken the concession") { alice.connection.overtaken && bob.connection.overtaken }
        waitUntil("both results", along = listOf(bob)) {
            bob.showsDialog("You won", "${alice.name} conceded") && alice.showsDialog("You lost", "You conceded", waitingForBob)
        }
        run(skip = java.time.Duration.ofSeconds(3))
        assertTrue(alice.atTable() && bob.atTable(), "both stay at the table: ${alice.where()}; ${bob.where()}")
        assertTrue(bob.shows(hasTestTag(GAME_END_OK_TAG)), "Bob's OK")
        bob.pressOk(alice)
        waitUntil("both in the lobby once both OKs stand", along = listOf(bob)) { alice.inFreeLobby() && bob.inFreeLobby() }
    }

    // TRN-065, TRN-142, LOB-046 (the race behind aConcederWaitsAtTheTableForTheOtherPlayersOk's failures, 2026-11-10): the lobby's
    // background sync read the lists while Alice was at the table, and its answer came only after she had left the game and
    // opened it again: the game is still there, and she sees its table with the dialog again (TRN-142; it said "no longer
    // available" and left her on the Games list)
    @Test fun aListReadFromTheTableDoesNotCloseTheGameOpenedAgain() {
        startGame()
        bothOpen()
        bothKeep(bobAway = false)
        concede(alice)
        waitUntil("both results", along = listOf(bob)) {
            bob.showsDialog("You won", "${alice.name} conceded") && alice.showsDialog("You lost", "You conceded", waitingForBob)
        }
        waitUntil("the game over on the server") { alice.lobby.state.detail?.lifecycle == GameLifecycle.LOBBY }
        run(skip = java.time.Duration.ofSeconds(3))
        waitUntil("Alice's lists read") { !alice.lobby.state.busy }
        // The sync's read of the lists' changes goes out with the table open; its answer waits in the network, as do the
        // game socket's messages (a slow downlink)
        val lists = arrayOf("/v1/games?", "/v1/invitations?")
        alice.link.holdAnswers(*lists, "/v1/socket")
        alice.lobby.sync()
        assertTrue(alice.lobby.state.busy, "the sync's read on its way")
        alice.lobby.back()
        waitUntil("Alice off the table") { !alice.shows(dialog) }
        waitUntil("the game in Alice's Games list") { alice.showsText(game.gameName) }
        alice.nodes(hasText(game.gameName))[0].performScrollTo().performClick()
        waitUntil("Alice opening the game again") { alice.lobby.state.selected == game.id }
        // The read's answer comes first
        alice.link.releaseAnswers(*lists)
        waitUntil("Alice's lists read") { !alice.lobby.state.busy }
        alice.link.releaseAnswers()
        waitUntil("Alice's table with the dialog: ${alice.where()}") { alice.atTable() && alice.showsDialog("You lost", waitingForBob) }
        assertEquals(game.id, alice.lobby.state.selected)
        bob.pressOk(alice)
        waitUntil("both in the lobby once both OKs stand", along = listOf(bob)) { alice.inFreeLobby() && bob.inFreeLobby() }
    }

    // TRN-064, TRN-010, HIS-020, SYS-170 (owner, 2026-11-17: "concede and suggest game end should be always available to either
    // player"; Concede was greyed while a suggestion waited): Alice suggests her win; with it waiting on Bob's table, his Table
    // actions' Concede ends the game as his concession: he waits for her OK, she sees "You won" with "Bob conceded"
    @Test fun theOtherPlayerConcedesWhileASuggestionWaits() {
        startGame()
        bothOpen()
        bothKeep(bobAway = false)
        alice.tap("Table actions")
        waitUntil("Alice's table actions") { alice.showsText("Suggest game end") }
        alice.text("Suggest game end").performClick()
        waitUntil("the suggestion chooser") { alice.showsText("My win") }
        alice.text("My win").performClick()
        waitUntil("the confirm dialog") { alice.shows(hasTestTag("resultNote")) }
        alice.text("Confirm").performClick()
        waitUntil("the suggestion on Bob's table") { bob.shows(hasContentDescription("Accept result")) }
        concede(bob)
        waitUntil("both results", along = listOf(alice)) {
            alice.showsDialog("You won", "${bob.name} conceded") && bob.showsDialog("You lost", "You conceded", waitingForAlice)
        }
        waitUntil("the game over on the server") { bob.lobby.state.detail?.lifecycle == GameLifecycle.LOBBY }
        alice.pressOk(bob)
        waitUntil("both in the lobby", along = listOf(bob)) { alice.inFreeLobby() && bob.inFreeLobby() }
    }

    // TRN-162, TRN-010, HIS-020, TRN-047 (owner, 2026-11-17: "concede and suggest game end should be always available to either player"):
    // Alice suggests her win; with it waiting on Bob's table, his Table actions' Suggest game end makes a draw suggestion instead:
    // both phones show Bob's draw waiting (Accept on Alice's, Withdraw on his) and Alice's win suggestion is gone
    @Test fun theOtherPlayersSuggestionReplacesTheWaitingOne() {
        startGame()
        bothOpen()
        bothKeep(bobAway = false)
        alice.tap("Table actions")
        waitUntil("Alice's table actions") { alice.showsText("Suggest game end") }
        alice.text("Suggest game end").performClick()
        waitUntil("the suggestion chooser") { alice.showsText("My win") }
        alice.text("My win").performClick()
        waitUntil("the confirm dialog") { alice.shows(hasTestTag("resultNote")) }
        alice.text("Confirm").performClick()
        waitUntil("Alice's suggestion on Bob's table") { bob.showsText("${alice.name} proposed their win.") }
        bob.tap("Table actions")
        waitUntil("Bob's table actions") { bob.showsText("Suggest game end") }
        bob.text("Suggest game end").performClick()
        waitUntil("Bob's suggestion chooser") { bob.showsText("Draw game") }
        bob.text("Draw game").performClick()
        waitUntil("Bob's confirm dialog") { bob.shows(hasTestTag("resultNote")) }
        bob.text("Confirm").performClick()
        waitUntil("Bob's draw waiting on both phones", along = listOf(alice)) {
            alice.showsText("${bob.name} proposed a draw.") && bob.showsText("You proposed a draw.") &&
                alice.shows(hasContentDescription("Accept result"))
        }
        assertFalse(alice.showsText("You proposed your win."), "Alice's suggestion is gone from her table")
        assertFalse(bob.showsText("${alice.name} proposed their win."), "and from Bob's")
        assertEquals(GameLifecycle.ACTIVE, bob.lobby.state.detail?.lifecycle, "the game goes on: ${alice.where()}; ${bob.where()}")
    }

    // TRN-047, TRN-134, TRN-065, TRN-135, TRN-136, TRN-141, TRN-142, LOB-046: Alice suggests her win ("Infinite combo"); Bob's Accept, greyed at first, ends the game: he
    // stays at the table with "Waiting for Alice to acknowledge", and Alice sees "You won" with "Agreed: Infinite combo"; on
    // another device (her app started again) the game opens at its table with the same dialog (TRN-142, owner 2026-11-22),
    // with OK: it takes them both to the lobby
    @Test fun theAcceptorWaitsAtTheTableForTheSuggestersOk() {
        startGame()
        bothOpen()
        bothKeep(bobAway = false)
        alice.tap("Table actions")
        waitUntil("Alice's table actions") { alice.showsText("Suggest game end") }
        alice.text("Suggest game end").performClick()
        waitUntil("the suggestion chooser") { alice.showsText("My win") }
        alice.text("My win").performClick()
        waitUntil("the confirm dialog") { alice.shows(hasTestTag("resultNote")) }
        alice.tagged("resultNote").performTextInput("Infinite combo")
        alice.text("Confirm").performClick()
        waitUntil("the suggestion on Bob's table") { bob.shows(hasContentDescription("Accept result")) }
        // The icon names the button around it, greyed for its first moments
        val accept = hasTestTag("result-accept") and isEnabled()
        waitUntil("Bob's Accept no longer greyed") { bob.shows(accept) }
        bob.node(accept).performClick()
        waitUntil("both results") {
            alice.showsDialog("You won", "Agreed: Infinite combo") && bob.showsDialog("You lost", "Agreed: Infinite combo", waitingForAlice)
        }
        run(skip = java.time.Duration.ofSeconds(3))
        assertTrue(alice.atTable() && bob.atTable(), "both stay at the table: ${alice.where()}; ${bob.where()}")
        assertFalse(bob.shows(hasTestTag(GAME_END_OK_TAG)), "the player who accepted has given his OK")
        alice = restart(alice)
        alice.openLobby()
        waitUntil("Alice's table with the dialog on reopening: ${alice.where()}") { alice.atTable() && alice.showsDialog("You won", "Agreed: Infinite combo") }
        assertTrue(bob.atTable(), "Bob still waits")
        alice.pressOk(bob)
        waitUntil("both in the lobby", along = listOf(bob)) { alice.inFreeLobby() && bob.inFreeLobby() }
    }

    // TRN-064, TRN-065, TRN-141 (owner, 2026-11-09): Alice's Mountain is still on its way (her uplink slow) when Bob's concession lands:
    // the concession stands, her move is refused without trouble, and she sees "You won" with OK; Bob waits at the table
    // until it, then both go to the lobby
    @Test fun aMoveInFlightWhenTheOtherPlayerConcedes() {
        // The deal follows each run's random account ids (per-player random streams since 336be95d5): her Mountain is asked for
        startGame(deal = { a, _ -> "Mountain" in hand(a) })
        bothOpen()
        bothKeep(bobAway = false)
        alice.link.holdOutgoing()
        alice.playFromHand("Mountain", this)
        waitUntil("Alice's Mountain shown on her table") { alice.field("Mountain").isNotEmpty() }
        bobConcedes()
        waitUntil("Bob waiting for Alice's OK") { bob.showsDialog("You lost", "You conceded", waitingForAlice) }
        waitUntil("the game over on the server") { bob.lobby.state.detail?.lifecycle == GameLifecycle.LOBBY }
        alice.link.releaseOutgoing()
        waitUntil("Alice's result") { alice.showsDialog("You won", "${bob.name} conceded") }
        run(skip = java.time.Duration.ofSeconds(3))
        assertTrue(alice.atTable() && bob.atTable(), "both stay at the table: ${alice.where()}; ${bob.where()}")
        alice.pressOk(bob)
        waitUntil("both in the lobby") { alice.inFreeLobby() && bob.inFreeLobby() }
        assertEquals(GameLifecycle.LOBBY, alice.lobby.state.detail?.lifecycle)
        assertTrue(alice.lobby.state.preparation?.players?.none { it.ready } == true, "both Ready cleared")
    }

    // TRN-065, TRN-142, TRN-143, LOB-046 (owner, 2026-11-09): every way into the next game waits for both OKs: after Bob's concession neither
    // can get Ready (his Ready request refused if it is sent), and Bob's second device (his app started again) opens the game
    // at its table with the same waiting dialog (TRN-142, owner 2026-11-22). Alice's OK clears it on every device; then both
    // Readies start the next game
    @Test fun theNextGameWaitsForBothOksOnEveryDevice() {
        startGame()
        bothOpen()
        bothKeep(bobAway = false)
        bobConcedes()
        waitUntil("Alice's result") { alice.showsDialog("You won", "${bob.name} conceded") }
        bob = restart(bob)
        bob.openLobby()
        waitUntil("Bob's table with the waiting dialog on his other device: ${bob.where()}") { bob.atTable() && bob.showsDialog("You lost", waitingForAlice) }
        // Bob's Ready, asked for as from a lobby that didn't know (the server refuses it, ResultNoticeTest): he stays not Ready
        bob.lobby.ready()
        run(skip = java.time.Duration.ofSeconds(2)); run(skip = java.time.Duration.ofSeconds(2))
        run(skip = java.time.Duration.ofSeconds(1))
        assertFalse(ServerSetup.head(bob.account, game.id).preparation!!.players.single { it.accountId == bob.id }.ready, "no Ready before both OKs")
        alice.pressOk(bob)
        waitUntil("both in the lobby on every device") { alice.inFreeLobby() && bob.inFreeLobby() }
        alice.lobby.ready(); bob.lobby.ready()
        waitUntil("the next game started") { alice.lobby.state.detail?.lifecycle == GameLifecycle.ACTIVE }
    }
}

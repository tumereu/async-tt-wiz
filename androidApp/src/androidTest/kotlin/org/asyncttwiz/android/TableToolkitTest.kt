package org.asyncttwiz.android

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import org.asyncttwiz.protocol.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Normal MainActivity/controllers with a UI-created active game. Compile != device execution.
 * Execute only inside the existing whole native resource lifecycle, never while nativeI12 owns it. */
class TableToolkitTest {
    @get:Rule val app = createAndroidComposeRule<MainActivity>()
    private fun open(): AuthViewModel {
        lateinit var model: AuthViewModel
        app.runOnUiThread { model = ViewModelProvider(app.activity)[AuthViewModel::class.java] }
        app.waitUntil(30_000) { model.controller.state.account != null && !model.controller.state.restoring }
        if (model.lobby.state.play == null) {
            app.waitUntil(30_000) { model.lobby.state.games.any { it.lifecycle == GameLifecycle.ACTIVE } }
            app.onNodeWithText(model.lobby.state.games.first { it.lifecycle == GameLifecycle.ACTIVE }.gameName).performClick()
        }
        app.waitUntil(30_000) { model.lobby.state.play != null && model.lobby.state.detail?.id in model.lobby.state.connectedGames }
        app.onNodeWithContentDescription("Shared table").assertExists()
        return model
    }

    @Test fun mountedTokenManualEditorAndSourceEffectUseRealNormalTable() {
        val model = open(); val lobby = model.lobby
        val actor = model.controller.state.account!!.accountId
        app.onNodeWithContentDescription("Table actions").performClick()
        app.onNodeWithText("Create token / Blank").performScrollTo().performClick()
        app.onNodeWithText("Blank").performClick()
        app.waitUntil(30_000) { lobby.state.play!!.board.cards.any { it.definition == TokenCatalog.blank.digest } }
        val token = lobby.state.play!!.board.cards.last { it.definition == TokenCatalog.blank.digest && it.owner == actor }
        val game = lobby.state.detail!!
        val name = if (game.inviter.accountId == actor) game.inviter.username else game.recipient.username
        val card = app.onNodeWithContentDescription("Blank, owner $name, untapped")
        val before = lobby.state.play!!.board
        card.performTouchInput { longClick() }
        app.waitForIdle(); assertEquals(before, lobby.state.play!!.board)
        app.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        card.performTouchInput { click() }
        app.mainClock.advanceTimeBy(350); app.waitForIdle()
        app.onNodeWithContentDescription("Edit card note").performScrollTo().performClick()
        app.onNode(hasSetTextAction()).performTextReplacement("toolkit\nretained note")
        app.onNodeWithText("Set", useUnmergedTree = true).performClick()
        app.waitUntil(30_000) { lobby.state.play!!.board.cards.single { it.id == token.id }.note == "toolkit\nretained note" }
        app.onNodeWithContentDescription("Increase blue counter").performScrollTo().performClick()
        app.onNodeWithContentDescription("Increase blue counter").performClick()
        app.waitUntil(30_000) { lobby.state.play!!.board.cards.single { it.id == token.id }.counters[CounterColor.BLUE] == "2" }
        app.onNodeWithContentDescription("Add Stack effect").performScrollTo().performClick()
        app.waitUntil(30_000) { lobby.state.play!!.board.stackEffects.any { it.sourceId == token.id } }
        app.onNodeWithContentDescription("Declare without tapping").performScrollTo().performClick()
        app.waitUntil(30_000) { CombatStackRules.occurrences(lobby.state.play!!.board.combat).any { it.sourceId == token.id } }
        assertFalse(lobby.state.play!!.board.cards.single { it.id == token.id }.tapped)
    }

    @Test fun actualProgressionHoldConsumesTapAndPlayerDragCancellationDoesNotSubmit() {
        val model = open(); val lobby = model.lobby
        val before = lobby.state.play!!
        app.onNodeWithContentDescription("Next step").performTouchInput { longClick(durationMillis = 650) }
        app.waitUntil(30_000) { lobby.state.play!!.board.turn.accountId != before.board.turn.accountId }
        assertEquals("UNTAP", lobby.state.play!!.board.turn.step)
        val game = lobby.state.detail!!
        val player = lobby.state.play!!.board.players.single { it.accountId == game.inviter.accountId }
        val board = lobby.state.play!!.board
        app.onNodeWithContentDescription("${game.inviter.username}, life ${player.life}").performTouchInput {
            down(center); moveBy(Offset(0f, -48f)); cancel()
        }
        app.waitForIdle(); assertEquals(board, lobby.state.play!!.board)
        // A name-only hold is personal; opening the picker/menu itself cannot advance tracking.
        app.onNodeWithContentDescription("Table actions").performTouchInput { longClick() }
        app.waitForIdle(); assertEquals(board.turn, lobby.state.play!!.board.turn)
    }
}

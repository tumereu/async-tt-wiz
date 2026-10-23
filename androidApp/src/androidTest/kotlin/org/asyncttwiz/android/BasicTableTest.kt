package org.asyncttwiz.android

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import org.asyncttwiz.protocol.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Normal MainActivity and production controllers. Requires the UI-created two-player active game.
 * Authored/compiled in I16a; execution belongs to the complete leased I16b native lifecycle.
 */
class BasicTableTest {
    @get:Rule val app = createAndroidComposeRule<MainActivity>()
    private fun open(): AuthViewModel {
        lateinit var model: AuthViewModel
        app.runOnUiThread { model = ViewModelProvider(app.activity)[AuthViewModel::class.java] }
        app.waitUntil(30_000) { !model.controller.state.restoring && model.controller.state.account != null }
        if (model.lobby.state.play == null) {
            app.waitUntil(30_000) { model.lobby.state.games.any { it.lifecycle == GameLifecycle.ACTIVE } }
            app.onNodeWithText(model.lobby.state.games.first { it.lifecycle == GameLifecycle.ACTIVE }.gameName).performClick()
        }
        app.waitUntil(30_000) { model.lobby.state.detail?.id in model.lobby.state.connectedGames && model.lobby.state.play != null }
        app.onNodeWithContentDescription("Shared table").assertExists()
        return model
    }

    @Test fun normalActionsDrawAndLifeHaveRealPredictionsWhileHoldSelectionAndCancellationDoNotEdit() {
        val model = open()
        val lobby = model.lobby
        val owner = model.controller.state.account!!.accountId
        val game = lobby.state.detail!!
        val name = if (game.inviter.accountId == owner) game.inviter.username else game.recipient.username
        val player = lobby.state.play!!.board.players.single { it.accountId == owner }
        val life = app.onNodeWithContentDescription("$name, life ${player.life}")
        val before = lobby.state.play!!.board
        life.performTouchInput { down(center); moveBy(Offset(0f, -48f)); cancel() }
        app.waitForIdle(); assertEquals(before, lobby.state.play!!.board)
        life.performClick()
        app.onNodeWithContentDescription("Increase life").performClick()
        app.onNodeWithContentDescription("Increase life").performClick()
        app.onNodeWithContentDescription("Set life").assertExists()
        val expected = java.math.BigInteger(player.life).add(java.math.BigInteger.TWO).toString()
        app.waitUntil(30_000) { lobby.state.play!!.board.players.single { it.accountId == owner }.life == expected }
        app.onNodeWithContentDescription("Close player actions").performClick()
        app.onNodeWithContentDescription("$name, life $expected").assertExists()
    }

    @Test fun actualCanvasLibraryActionMovesCapturedTopAndOwnHoldIsNotLoggedInspection() {
        val model = open()
        val lobby = model.lobby
        val owner = model.controller.state.account!!.accountId
        val game = lobby.state.detail!!
        val name = if (game.inviter.accountId == owner) game.inviter.username else game.recipient.username
        val initial = lobby.state.play!!
        val library = initial.board.containers.single { it.zone == PlayZone.LIBRARY && it.accountId == owner }
        val top = library.cards.first()
        val hand = initial.board.containers.single { it.zone == PlayZone.HAND && it.accountId == owner }
        app.onNodeWithContentDescription("$name Library, ${library.cards.size} cards").performTouchInput { click() }
        app.onNodeWithContentDescription("Draw one").performClick()
        app.waitUntil(30_000) { lobby.state.play!!.board.cards.single { it.id == top }.containerId == hand.id }
        assertEquals(top, lobby.state.play!!.board.cards.single { it.id == top }.id)
        val card = app.onNode(hasContentDescription("Covered card, owner $name, untapped"))
        val before = lobby.state.play!!.board
        card.performTouchInput { longClick() }
        app.waitForIdle(); assertEquals(before, lobby.state.play!!.board)
        app.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        val actions = card.fetchSemanticsNode().config[SemanticsActions.CustomActions]
        app.runOnUiThread { actions.first { it.label == "Select or deselect" }.action() }
        app.waitForIdle(); assertEquals(before, lobby.state.play!!.board)
        app.onNodeWithContentDescription("Shared table").assert(SemanticsMatcher.expectValue(
            androidx.compose.ui.semantics.SemanticsProperties.StateDescription, "1 selected, normal mode"))
    }
}

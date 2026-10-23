package org.asyncttwiz.android

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.platform.app.InstrumentationRegistry
import org.asyncttwiz.protocol.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Called inside the I31 native owner's retained journey on the same-account third installation.
 * No account/game/history fixture or direct shared mutation. UI Logout must exercise the actual
 * AuthViewModel clearPrivateWork and activity/ViewModel lifetime, including recreation and close.
 */
class IntegratedLifecycleTest {
    @get:Rule val app = createAndroidComposeRule<MainActivity>()

    @Test fun fullRecoveredTreeRetainsExactUiCreatedOffshootAndCurrentPath() {
        val args = InstrumentationRegistry.getArguments()
        fun id(key: String) = requireNotNull(args.getString("i31$key"))
        lateinit var model: AuthViewModel
        app.runOnUiThread { model = ViewModelProvider(app.activity)[AuthViewModel::class.java] }
        app.waitUntil(30_000) { model.controller.state.account?.username == "Start First" && !model.controller.state.restoring }
        if (model.lobby.state.selected != id("GameId")) {
            app.waitUntil(30_000) { model.lobby.state.games.any { it.id == id("GameId") } }
            app.onNodeWithText("Native Initial Spark").performClick()
        }
        app.waitUntil(30_000) {
            model.lobby.retainedTree()?.let { tree -> tree.initial.id == id("PlayId") &&
                tree.nodes.keys.containsAll(listOf(id("life"), id("draw"), id("turn"))) } == true &&
                model.lobby.history.playback != null
        }
        app.onNodeWithText("Return to current").performClick()
        app.waitUntil(10_000) { model.lobby.history.view?.cursor == id("turn") }
        // Observe the actual recovered normal session, never install a fixture tree or submit controls.
        app.runOnIdle {
            val tree = requireNotNull(model.lobby.retainedTree())
            assertEquals(id("GameId"), tree.initial.gameId)
            assertEquals(id("PlayId"), tree.initial.id)
            assertEquals(id("RootId"), tree.initial.root.id)
            assertEquals(setOf(id("life"), id("draw"), id("turn")), tree.nodes.keys)
            assertEquals(id("turn"), tree.currentNodeId)
            assertEquals(id("RootId"), tree.nodes.getValue(id("life")).parentId)
            assertEquals(id("RootId"), tree.nodes.getValue(id("draw")).parentId)
            assertEquals(id("draw"), tree.nodes.getValue(id("turn")).parentId)
            assertNotEquals(tree.nodes.getValue(id("life")).branchId, tree.nodes.getValue(id("draw")).branchId)
            for (node in tree.nodes.values) {
                assertEquals(CommandIds.uuidV5(node.requestId, "board-node"), node.id)
                assertNotNull(node.grouping)
            }
            assertEquals(3, tree.nodes.values.map { it.requestId }.distinct().size)
            val viewer = requireNotNull(model.controller.state.account).accountId
            assertEquals(viewer, tree.nodes.getValue(id("life")).author)
            assertEquals(viewer, tree.nodes.getValue(id("turn")).author)
            assertNotEquals(viewer, tree.nodes.getValue(id("draw")).author)
            assertEquals("21", tree.stateAt(id("life"))!!.board.players.single { it.accountId == viewer }.life)
            assertEquals("20", tree.stateAt(id("draw"))!!.board.players.single { it.accountId == viewer }.life)
            assertEquals(tree.stateAt(id("turn")), model.lobby.state.acceptedPlay)
            assertTrue(model.lobby.history.view!!.hasCompleteGeneration)
            assertFalse(model.lobby.history.view!!.downloadIncomplete)
            assertNull(model.lobby.history.view!!.error)
        }
        app.onNodeWithText("Event details").performClick()
        app.onNodeWithText("Boundary ${id("turn")}").assertExists()
        app.onNodeWithText("Close event details").performClick()
    }

    @Test fun realAuthViewModelLogoutClearsPrivateReviewAndDraftAcrossRotationAndClose() {
        val arguments = InstrumentationRegistry.getArguments()
        val gameId = requireNotNull(arguments.getString("i31GameId"))
        val playId = requireNotNull(arguments.getString("i31PlayId"))
        val rootId = requireNotNull(arguments.getString("i31RootId"))
        lateinit var model: AuthViewModel
        app.runOnUiThread { model = ViewModelProvider(app.activity)[AuthViewModel::class.java] }
        app.waitUntil(30_000) { model.controller.state.account?.username == "Start First" && !model.controller.state.restoring }
        if (model.lobby.state.selected != gameId) {
            app.waitUntil(30_000) { model.lobby.state.games.any { it.id == gameId && it.lifecycle == GameLifecycle.ACTIVE } }
            app.onNodeWithText("Native Initial Spark").performClick()
        }
        app.waitUntil(30_000) { model.lobby.state.acceptedPlay?.id == playId && model.lobby.history.playback != null }
        val history = model.lobby.history
        app.onNodeWithText("Return to current").performClick()
        app.waitUntil(10_000) { history.playback?.position?.cursorId == rootId && history.view?.cursor == rootId }
        app.onNodeWithText("Event details").performClick()
        app.onNodeWithText("Boundary $rootId").assertExists()
        app.onNodeWithText("Close event details").performClick()
        app.onNodeWithText("Chat").performClick()
        app.onNodeWithContentDescription("Chat message draft").performTextInput("I31 private unsent draft")
        app.onNodeWithContentDescription("Close chat").performClick()
        app.runOnIdle {
            assertEquals("I31 private unsent draft", model.lobby.state.chat.draft?.text)
            assertEquals(playId, model.lobby.state.acceptedPlay?.id)
            assertEquals(rootId, history.playback?.position?.cursorId)
        }
        // Native journey also rotates real devices. Recreation here checks the actual retained VM.
        app.activityRule.scenario.recreate()
        app.runOnUiThread { assertSame(model, ViewModelProvider(app.activity)[AuthViewModel::class.java]) }
        app.onNodeWithContentDescription("Open navigation").performClick()
        app.onNodeWithText("Logout").performClick()
        app.waitUntil(10_000) { model.controller.state.account == null && !model.controller.state.busy }
        app.onNodeWithText("Forgot password?").assertExists()
        fun cleared() {
            assertNull(model.controller.state.account)
            assertNull(model.controller.sessionToken())
            assertEquals(LobbyState(), model.lobby.state)
            assertNull(history.playback); assertNull(history.view); assertNull(history.armed); assertNull(history.gate)
        }
        app.runOnIdle { cleared() }
        app.activityRule.scenario.recreate()
        app.onNodeWithText("Forgot password?").assertExists()
        app.runOnIdle { cleared() }
        // Exercise real onCleared/close. The surrounding native journey then restarts, logs in as B,
        // checks absence of A's unsent draft, and logs A back in without clearing app/backend data.
        app.activityRule.scenario.close()
        InstrumentationRegistry.getInstrumentation().runOnMainSync { cleared() }
    }
}

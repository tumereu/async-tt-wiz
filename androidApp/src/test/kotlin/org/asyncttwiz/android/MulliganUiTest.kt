package org.asyncttwiz.android

import android.content.Context
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import org.asyncttwiz.protocol.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.*

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w412dp-h892dp-port-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MulliganUiTest {
    @get:Rule val compose = createComposeRule()

    // ZON-028
    @Test fun quantityIsRememberedOnlyForTheSameGameAndPlayer() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.getSharedPreferences("mulligan-quantities", 0).edit().clear().commit()
        val store = MulliganQuantityStore(context, "a", "game")
        assertEquals(7, store.read())
        store.write(5)
        assertEquals(5, MulliganQuantityStore(context, "a", "game").read())
        assertEquals(7, MulliganQuantityStore(context, "b", "game").read())
        assertEquals(7, MulliganQuantityStore(context, "a", "other-game").read())
    }

    // ZON-029
    @Test fun nonTurnPlayersQuickMulliganOpensOnlyNumberDialogAndSharesLibraryPreference() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.getSharedPreferences("mulligan-quantities", 0).edit().clear().commit()
        val f = BasicTableFixture()
        f.connect(f.root.copy(board = f.root.board.copy(turn = TurnState(f.other),
            players = f.root.board.players.map { it.copy(status = PlayerStatus.OPENING) })))
        val ui = TableToolkitController(f.actor, { f.play }, { true }, f.lobby::gameplay, playBatch = f.lobby::gameplayBatch)
        fun click(name: String) {
            compose.mainClock.advanceTimeBy(350)
            compose.onNodeWithContentDescription(name).performClick()
            compose.waitForIdle()
        }
        try {
            compose.setContent { MaterialTheme {
                TableSurface(f.game, f.lobby.state.play!!, f.actor, false, connected = true, toolkit = ui, onIntent = ui::handle)
            } }
            compose.onNodeWithContentDescription(nextStepLabel(f.play.board.turn)).assertDoesNotExist()
            click("Mull N")
            assertTrue(f.commands().isEmpty())
            compose.onNodeWithContentDescription("Search").assertDoesNotExist()
            compose.onNode(hasSetTextAction()).assertTextEquals("7").performTextReplacement("3")
            click("Mulligan")
            val action = assertIs<PlayAction.Mulligan>((f.commands().single().action as GameAction.Play).action)
            assertEquals(3, action.drawCount)
            assertEquals(f.container(PlayZone.LIBRARY), action.libraryId)
            assertEquals(3, f.play.board.containers.single { it.id == action.handId }.cards.size)
            compose.onAllNodes(isDialog()).assertCountEquals(0)
            compose.runOnIdle { ui.openLibrary(f.container(PlayZone.LIBRARY)) }
            click("Mulligan N…")
            compose.onNode(hasSetTextAction()).assertTextEquals("3")
            compose.onNodeWithContentDescription("Cancel mulligan").assertDoesNotExist()
            compose.runOnIdle { ui.dismiss() }
            click("Mull −1")
            assertEquals(2, assertIs<PlayAction.Mulligan>((f.commands().last().action as GameAction.Play).action).drawCount)
            assertNull(ui.panel)
            compose.onAllNodes(isDialog()).assertCountEquals(0)
        } finally { f.lobby.close(); f.worker.drain() }
    }
}

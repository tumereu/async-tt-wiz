package org.asyncttwiz.android

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import arcanum.api.TurnStep
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * TRN-163, TRN-165 (owner 2026-11-19; problem report B7UZB5): the main button holds what the player is offered where the game
 * really is, never what the board the table still draws (a step behind, while the board's steps play) would offer, and its
 * press is made there. Games played through the simulated server; time on the test clock.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w412dp-h892dp-port-mdpi")
class MainButtonRealStateTest {
    @get:Rule val compose = createComposeRule()

    @After fun forget() { AutomatumBoards.reset() }

    /** Alice's table drawing [drawn] (an older board of her game) while her game is where it is. */
    private fun render(kit: EngineTableKit, drawn: TableBoard) {
        compose.setContent {
            MaterialTheme {
                NoRipples {
                    CompositionLocalProvider(LocalEngineRoutes provides kit.routes) {
                        TableSurface(kit.f.game, drawn, kit.f.a, false, Modifier.fillMaxSize(), connected = true, onIntent = kit.toolkit::handle,
                            toolkit = kit.toolkit, runtime = TableRuntimeHooks(navigation = {}, chat = {}, undo = { kit.routes.undo() },
                                controlsConnected = true))
                    }
                }
            }
        }
        compose.waitForIdle()
    }

    // TRN-163, TRN-165, TBL-200, TBL-202 (problem report B7UZB5): the table still draws Alice's Main 1 (the board's steps a step behind) while her
    // game is at her declaration of attackers: the main button already says "No attack", never the drawn board's "To combat", and
    // its press declares no attackers there (it doesn't go to combat again from a Main 1 the game has left)
    @Test fun theMainButtonFollowsTheGameNotTheBoardStillDrawn() {
        val kit = EngineTableKit()
        kit.toReadyCub()
        val drawn = kit.alice.play()
        assertEquals(TurnStep.PrecombatMain, drawn.engine?.state?.turn?.step)
        kit.toAttacks()
        assertEquals(TurnStep.DeclareAttackers, kit.state().turn.step, "her game at her declaration")
        render(kit, drawn)
        compose.onNodeWithTag("main-button").assert(hasContentDescription("No attack"))
        compose.onNodeWithTag("main-button").performClick()
        compose.waitForIdle()
        kit.playOn("past Alice's declaration") { kit.state().turn.step != TurnStep.DeclareAttackers }
        assertTrue(kit.state().combat?.attackers.isNullOrEmpty(), "nothing attacked")
        assertNotEquals(TurnStep.PrecombatMain, kit.state().turn.step, "not back in a Main 1")
    }
}

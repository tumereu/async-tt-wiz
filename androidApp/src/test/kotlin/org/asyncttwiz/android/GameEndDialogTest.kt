package org.asyncttwiz.android

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import org.asyncttwiz.protocol.LossCause
import org.asyncttwiz.protocol.MatchEnding
import org.asyncttwiz.protocol.game.GameDevice
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertEquals
import kotlin.test.assertIs

/**
 * TRN-065 (owner 2026-11-07): every ending shows a dialog in the middle of the screen: "You won", "You lost" or "Draw",
 * how it ended in plain words, the eye and OK.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w412dp-h892dp-port-mdpi")
class GameEndDialogTest {
    @get:Rule val compose = createComposeRule()
    private val names = mapOf("a" to "Alice", "b" to "Bob")

    @After fun forget() = AutomatumBoards.reset()

    private fun text(winner: String?, ending: MatchEnding, causes: Map<String, LossCause> = emptyMap(), viewer: String = "a") =
        GameEndText.of(winner, ending, causes, viewer, names)

    // TRN-065, LOB-126: the title from the viewer's side and the reason for each way a game ends
    @Test fun eachEndingSaysWhoWonAndWhy() {
        assertEquals(GameEndText("You won", "Bob's life reached 0"), text("a", MatchEnding.ByRules, mapOf("b" to LossCause.ZeroLife)))
        assertEquals(GameEndText("You lost", "Your life reached 0"), text("a", MatchEnding.ByRules, mapOf("b" to LossCause.ZeroLife), "b"))
        assertEquals("You drew from an empty library", text("b", MatchEnding.ByRules, mapOf("a" to LossCause.EmptyLibrary)).reason)
        assertEquals("Bob got 10 poison counters", text("a", MatchEnding.ByRules, mapOf("b" to LossCause.Poison)).reason)
        assertEquals("Doom Sigil's effect", text("a", MatchEnding.ByRules, mapOf("b" to LossCause.CardEffect("Doom Sigil"))).reason)
        assertEquals(GameEndText("You won", "Bob conceded"), text("a", MatchEnding.Conceded("b")))
        assertEquals(GameEndText("You lost", "You conceded"), text("b", MatchEnding.Conceded("a")))
        assertEquals("Agreed: Infinite combo", text("a", MatchEnding.Agreed("Infinite combo")).reason)
        assertEquals("Agreed", text("a", MatchEnding.Agreed()).reason)
        assertEquals(GameEndText("Draw", "Both players lost at once"), text(null, MatchEnding.Draw(loop = false)))
        assertEquals(GameEndText("Draw", "An endless loop"), text(null, MatchEnding.Draw(loop = true)))
    }

    // TRN-065 (owner, 2026-11-09): with the player's OK standing, the dialog says who it waits for, and Take back OK takes OK's place
    @Test fun anOkThatStandsShowsWhoItWaitsForAndCanBeTakenBack() {
        var taken = 0
        compose.setContent { MaterialTheme { NoRipples { Box(Modifier.fillMaxSize()) {
            GameEndDialog(GameEndText("You won", "Bob's life reached 0"), "Waiting for Bob to acknowledge", takeBack = { taken++ }, ok = {})
        } } } }
        compose.waitForIdle()
        val dialog = hasTestTag(GAME_END_DIALOG_TAG)
        compose.onNode(hasText("Waiting for Bob to acknowledge") and hasAnyAncestor(dialog), useUnmergedTree = true).assertIsDisplayed()
        compose.onNode(hasText("OK") and hasAnyAncestor(dialog), useUnmergedTree = true).assertDoesNotExist()
        captureShot("$SCREENSHOTS/engine/game-end-waiting.png", "Alice pressed OK on her win by the rules: the dialog stays with " +
            "\"Waiting for Bob to acknowledge\" under the reason, the eye at its bottom left and Take back OK at its right.")
        compose.onNode(hasText("Take back OK") and hasClickAction()).performClick()
        assertEquals(1, taken)
    }

    // TRN-065, TRN-064: Bob concedes in Alice's first turn: her table shows "You won" and "Bob conceded" in a dialog in the
    // middle of the screen with the eye and OK; the eye hides it (only the eye stays) and brings it back
    @Test fun theOtherPlayersConcessionShowsTheDialogOverTheBoard() {
        val kit = EngineTableKit()
        kit.opening()
        kit.bob.concede()
        kit.playOn("Bob's concession on Alice's table", bobActs = false) { kit.alice.shown?.status is GameDevice.Status.Over }
        val over = assertIs<GameDevice.Status.Over>(kit.alice.shown?.status)
        assertEquals(MatchEnding.Conceded(kit.f.b), over.ending)
        compose.setContent { MaterialTheme { NoRipples { CompositionLocalProvider(LocalEngineRoutes provides kit.routes) {
            Box(Modifier.fillMaxSize()) {
                val play = kit.alice.play()
                TableSurface(kit.f.game, play, kit.f.a, false, Modifier.fillMaxSize(), connected = true,
                    onIntent = kit.toolkit::handle, toolkit = kit.toolkit,
                    runtime = TableRuntimeHooks(navigation = {}, chat = {}, undo = { kit.routes.undo() }, controlsConnected = true,
                        waitingLine = kit.routes.waitingLine(play)))
            }
        } } } }
        compose.waitForIdle()
        val dialog = hasTestTag(GAME_END_DIALOG_TAG)
        compose.onNode(hasText("You won") and hasAnyAncestor(dialog), useUnmergedTree = true).assertIsDisplayed()
        val reason = compose.onAllNodes(hasText("conceded", substring = true) and hasAnyAncestor(dialog), useUnmergedTree = true)
        reason.assertCountEquals(1)
        // TRN-065: OK is greyed for the dialog's first moments, so a misplaced tap can't accept it
        val ok = compose.onNodeWithTag(GAME_END_OK_TAG, useUnmergedTree = true)
        ok.assertIsNotEnabled()
        compose.mainClock.advanceTimeBy(OK_GREY_MILLIS)
        compose.waitForIdle()
        ok.assertIsEnabled()
        captureShot("$SCREENSHOTS/engine/game-end-dialog.png", "Bob conceded in Alice's first turn: her table shows a dialog " +
            "in the middle of the screen with \"You won\" and \"Bob conceded\" under it, the eye at its bottom left and OK at its right.")
        compose.onNodeWithContentDescription("Hide result").performClick()
        compose.waitForIdle()
        compose.onNode(hasText("OK") and hasAnyAncestor(dialog), useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithContentDescription("Show result").performClick()
        compose.waitForIdle()
        compose.onNode(hasText("OK") and hasAnyAncestor(dialog), useUnmergedTree = true).assertExists()
    }

    // TRN-065, TRN-064, LOB-046 (owner, 2026-11-09: nobody goes to the lobby until both have pressed OK): Alice concedes in
    // her first turn: her own table shows "You lost" and "You conceded" with "Waiting for Bob to acknowledge" and no button:
    // her concession was her OK, final, nothing to take back
    @Test fun theConcederWaitsAtTheTableForTheOtherPlayersOk() {
        val kit = EngineTableKit()
        kit.opening()
        kit.alice.concede()
        kit.playOn("Alice's concession on her table", bobActs = false) { kit.alice.shown?.status is GameDevice.Status.Over }
        val over = assertIs<GameDevice.Status.Over>(kit.alice.shown?.status)
        assertEquals(MatchEnding.Conceded(kit.f.a), over.ending)
        compose.setContent { MaterialTheme { NoRipples { CompositionLocalProvider(LocalEngineRoutes provides kit.routes) {
            Box(Modifier.fillMaxSize()) {
                val play = kit.alice.play()
                TableSurface(kit.f.game, play, kit.f.a, false, Modifier.fillMaxSize(), connected = true,
                    onIntent = kit.toolkit::handle, toolkit = kit.toolkit,
                    runtime = TableRuntimeHooks(navigation = {}, chat = {}, undo = { kit.routes.undo() }, controlsConnected = true,
                        waitingLine = kit.routes.waitingLine(play)))
            }
        } } } }
        compose.mainClock.advanceTimeBy(OK_GREY_MILLIS)
        compose.waitForIdle()
        val dialog = hasTestTag(GAME_END_DIALOG_TAG)
        compose.onNode(hasText("You lost") and hasAnyAncestor(dialog), useUnmergedTree = true).assertIsDisplayed()
        compose.onNode(hasText("You conceded") and hasAnyAncestor(dialog), useUnmergedTree = true).assertIsDisplayed()
        compose.onNode(hasText("Waiting for Bob to acknowledge") and hasAnyAncestor(dialog), useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithTag(GAME_END_OK_TAG, useUnmergedTree = true).assertDoesNotExist()
        compose.onNode(hasText("Take back OK"), useUnmergedTree = true).assertDoesNotExist()
        captureShot("$SCREENSHOTS/engine/game-end-conceder.png", "Alice conceded in her first turn: her own table shows a " +
            "dialog with \"You lost\", \"You conceded\" and \"Waiting for Bob to acknowledge\", the eye at its bottom left and no button.")
    }
}

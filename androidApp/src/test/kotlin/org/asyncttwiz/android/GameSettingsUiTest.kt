package org.asyncttwiz.android

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.dp
import org.asyncttwiz.protocol.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w412dp-h892dp-port-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class GameSettingsUiTest {
    @get:Rule val compose = createComposeRule()

    /** The lobby's Starting player and Game settings cards as the lobby lays them out, for [viewer]. */
    private fun lobbyCards(viewer: (GameSummary) -> String, sent: MutableList<AssumeReveals>,
        lobby: State<Boolean> = mutableStateOf(true)): MutableState<GameSummary> {
        val game = mutableStateOf(ScreenFixtures().lobby.state.games.last())
        compose.setContent {
            ScreenTheme { Surface(Modifier.fillMaxSize(), color = ScreenBackground) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    StartingPlayerControl(game.value, StarterChoice(), true) {}
                    GameSettingsCard(game.value, viewer(game.value), lobby = lobby.value) { sent += it; game.value = game.value.copy(assumeReveals = it) }
                }
            } }
        }
        return game
    }

    // LOB-135, LOB-035, HIS-232: the host sees the game's settings at their defaults (the opponent's library allowed, hands not,
    // undo past the other player's passes free) and each switch sends the whole new value
    @Test fun theHostChangesTheGameSettings() {
        val sent = mutableListOf<AssumeReveals>()
        lobbyCards({ it.inviter.accountId }, sent)
        val library = compose.onNodeWithTag("assume-library")
        val hands = compose.onNodeWithTag("assume-hands")
        val passes = compose.onNodeWithTag("undo-past-passes")
        library.assertIsOn().assertIsEnabled()
        hands.assertIsOff().assertIsEnabled()
        passes.assertIsOff().assertIsEnabled()
        compose.onNodeWithText(GameSettingsText.LIBRARY).assertIsDisplayed()
        compose.onNodeWithText(GameSettingsText.HANDS).assertIsDisplayed()
        compose.onNodeWithText(GameSettingsText.UNDO_PASSES).assertIsDisplayed()
        compose.onAllNodesWithText("can change these", substring = true).assertCountEquals(0)
        val file = File("$SCREENSHOTS/settings/game-settings-host.png")
        file.parentFile!!.mkdirs()
        captureShot(file.path, "Portrait game lobby cards for the game's host: Starting player, then Game settings with " +
            "\"Allow assumes that reveal the opponent's library\" on, \"Allow assumes that reveal hands\" off and \"Undo past the " +
            "other player's passes needs a request\" off, then \"Reshuffle libraries on undo\" and \"Reshuffle libraries on branch\" " +
            "on, the defaults.")
        hands.performClick().assertIsOn()
        library.performClick().assertIsOff()
        passes.performClick().assertIsOn()
        assertEquals(listOf(AssumeReveals(library = true, hands = true), AssumeReveals(library = false, hands = true),
            AssumeReveals(library = false, hands = true, undoPastPasses = true)), sent)
    }

    // LOB-135, LOB-154: the other player sees the current values, greyed, under who can change them; a change of the host's shows
    @Test fun theOtherPlayerSeesTheSettingsGreyed() {
        val sent = mutableListOf<AssumeReveals>()
        val game = lobbyCards({ it.recipient.accountId }, sent)
        val hands = compose.onNodeWithTag("assume-hands")
        hands.assertIsOff().assertIsNotEnabled()
        compose.onNodeWithTag("assume-library").assertIsOn().assertIsNotEnabled()
        compose.onNodeWithText(GameSettingsText.hostOnly(game.value.inviter.username)).assertIsDisplayed()
        hands.performClick()
        compose.runOnIdle { game.value = game.value.copy(assumeReveals = AssumeReveals(library = true, hands = true)) }
        hands.assertIsOn().assertIsNotEnabled()
        assertEquals(emptyList(), sent)
    }

    // LOB-282, HIS-042 (owner 2026-11-26): "Reshuffle libraries on undo" and "on branch", both on by default, each with its line;
    // the game's creator changes them in the lobby (each switch sends the whole value), greyed while a match is on; the other
    // player sees them greyed
    @Test fun theCreatorChangesTheReshuffleSwitchesInTheLobbyOnly() {
        val sent = mutableListOf<AssumeReveals>()
        val lobby = mutableStateOf(true)
        lobbyCards({ it.inviter.accountId }, sent, lobby)
        val undo = compose.onNodeWithTag("reshuffle-undo")
        val branch = compose.onNodeWithTag("reshuffle-branch")
        undo.assertIsOn().assertIsEnabled()
        branch.assertIsOn().assertIsEnabled()
        compose.onNodeWithText(GameSettingsText.RESHUFFLE_UNDO_MEANING).assertExists()
        compose.onNodeWithText(GameSettingsText.RESHUFFLE_BRANCH_MEANING).assertExists()
        undo.performClick().assertIsOff()
        assertEquals(listOf(AssumeReveals(reshuffleOnUndo = false)), sent)
        val file = File("$SCREENSHOTS/settings/game-settings-reshuffle.png")
        file.parentFile!!.mkdirs()
        captureShot(file.path, "Portrait game lobby cards for the game's creator, who turned \"Reshuffle libraries on undo\" off: the Game " +
            "settings card's last two switches, each with its line (choice: these lines), \"Reshuffle libraries on undo\" off and \"Reshuffle " +
            "libraries on branch\" on.")
        compose.runOnIdle { lobby.value = false }
        undo.assertIsOff().assertIsNotEnabled()
        branch.assertIsNotEnabled().performClick()
        compose.onNodeWithTag("assume-hands").assertIsEnabled()
        assertEquals(1, sent.size, "nothing sent while a match is on")
    }

    // LOB-282: the other player sees the creator's reshuffle switches greyed
    @Test fun theOtherPlayerSeesTheReshuffleSwitchesGreyed() {
        val sent = mutableListOf<AssumeReveals>()
        val game = lobbyCards({ it.recipient.accountId }, sent)
        compose.runOnIdle { game.value = game.value.copy(assumeReveals = AssumeReveals(reshuffleOnBranch = false)) }
        compose.onNodeWithTag("reshuffle-undo").assertIsOn().assertIsNotEnabled()
        compose.onNodeWithTag("reshuffle-branch").assertIsOff().assertIsNotEnabled().performClick()
        assertEquals(emptyList(), sent)
    }

    // LOB-152, LOB-282 (owner 2026-11-27): while "Reshuffle libraries on branch" is off, "Allow assumes that reveal the opponent's
    // library" shows off and greyed, its line saying why; turning the branch reshuffle on again brings back its stored value (on)
    @Test fun theLibrarySwitchIsGreyedOffWhileBranchesDontReshuffle() {
        val sent = mutableListOf<AssumeReveals>()
        lobbyCards({ it.inviter.accountId }, sent)
        val library = compose.onNodeWithTag("assume-library")
        val branch = compose.onNodeWithTag("reshuffle-branch")
        library.assertIsOn().assertIsEnabled()
        compose.onNodeWithText(GameSettingsText.LIBRARY_MEANING).assertExists()
        branch.performClick().assertIsOff()
        assertEquals(listOf(AssumeReveals(reshuffleOnBranch = false)), sent, "the library setting itself stays as it was")
        library.assertIsOff().assertIsNotEnabled()
        compose.onNodeWithText(GameSettingsText.LIBRARY_NO_RESHUFFLE).assertIsDisplayed()
        compose.onNodeWithText(GameSettingsText.LIBRARY_MEANING).assertDoesNotExist()
        library.performClick().assertIsOff()
        assertEquals(1, sent.size, "the greyed switch sends nothing")
        val file = File("$SCREENSHOTS/settings/game-settings-library-greyed.png")
        file.parentFile!!.mkdirs()
        captureShot(file.path, "Portrait game lobby cards for the game's creator, who turned \"Reshuffle libraries on branch\" off: \"Allow " +
            "assumes that reveal the opponent's library\" shows off and greyed, its line saying it is off while the branch reshuffle is " +
            "(choice: this line's words).")
        branch.performClick().assertIsOn()
        library.assertIsOn().assertIsEnabled()
        compose.onNodeWithText(GameSettingsText.LIBRARY_MEANING).assertExists()
        assertEquals(listOf(AssumeReveals(reshuffleOnBranch = false), AssumeReveals()), sent)
    }
}

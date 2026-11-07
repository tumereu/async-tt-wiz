package org.asyncttwiz.android

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
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
    private fun lobbyCards(viewer: (GameSummary) -> String, sent: MutableList<AssumeReveals>): MutableState<GameSummary> {
        val game = mutableStateOf(ScreenFixtures().lobby.state.games.last())
        compose.setContent {
            ScreenTheme { Surface(Modifier.fillMaxSize(), color = ScreenBackground) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    StartingPlayerControl(game.value, StarterChoice(), true) {}
                    GameSettingsCard(game.value, viewer(game.value)) { sent += it; game.value = game.value.copy(assumeReveals = it) }
                }
            } }
        }
        return game
    }

    // LOB-135, LOB-035: the host sees the game's settings at their defaults (the opponent's library allowed, hands not)
    // and each switch sends the whole new value
    @Test fun theHostChangesTheGameSettings() {
        val sent = mutableListOf<AssumeReveals>()
        lobbyCards({ it.inviter.accountId }, sent)
        val library = compose.onNodeWithTag("assume-library")
        val hands = compose.onNodeWithTag("assume-hands")
        library.assertIsOn().assertIsEnabled()
        hands.assertIsOff().assertIsEnabled()
        compose.onNodeWithText(GameSettingsText.LIBRARY).assertIsDisplayed()
        compose.onNodeWithText(GameSettingsText.HANDS).assertIsDisplayed()
        compose.onAllNodesWithText("can change these", substring = true).assertCountEquals(0)
        val file = File("$SCREENSHOTS/settings/game-settings-host.png")
        file.parentFile!!.mkdirs()
        captureShot(file.path, "Portrait game lobby cards for the game's host: Starting player, then Game settings with " +
            "\"Allow assumes that reveal the opponent's library\" on and \"Allow assumes that reveal hands\" off, the defaults.")
        hands.performClick().assertIsOn()
        library.performClick().assertIsOff()
        assertEquals(listOf(AssumeReveals(library = true, hands = true), AssumeReveals(library = false, hands = true)), sent)
    }

    // LOB-135: the other player sees the current values, greyed, under who can change them; a change of the host's shows
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
}

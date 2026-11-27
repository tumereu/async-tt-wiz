package org.asyncttwiz.android

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import kotlin.test.assertEquals

/** DRF-319 (owner 2026-11-26, playtest F): the pod's Game settings card at the end of New pod and pod setup. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w412dp-h892dp-port-mdpi")
class PodGameSettingsTest {
    @get:Rule val compose = createComposeRule()

    private fun form(editable: Boolean, settings: MutableState<DraftSettings>) {
        compose.setContent { ScreenTheme { NoRipples { Surface(Modifier.fillMaxSize(), color = ScreenBackground) {
            Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                PodSettingsForm("Cube night", settings.value, PodSource("Runes", 540), 2, editable, {}, {}, { settings.value = it(settings.value) })
                // As New pod and pod setup show it: after the form, at the screen's end
                PodGameSettingsCard(settings.value.gameSettings, editable) { value -> settings.value = settings.value.copy(gameSettings = value) }
            }
        } } } }
    }

    // DRF-319, LOB-135, LOB-282: the creator's New pod ends with the Game settings card: the lobby's switches that apply to pod games
    // (no Decks group) at the lobby's defaults, with its note; each switch changes the pod's game settings alone, and choosing
    // another mode keeps them
    @Test fun theCreatorSetsThePodsGameSettings() {
        val settings = mutableStateOf(DraftSettings())
        form(true, settings)
        compose.onNodeWithTag("pod-game-settings").performScrollTo()
        compose.onNodeWithText(GameSettingsText.POD_NOTE).assertExists()
        compose.onNodeWithTag("deck-settings").assertDoesNotExist()
        compose.onNodeWithTag("assume-library").assertIsOn().assertIsEnabled()
        compose.onNodeWithTag("assume-hands").assertIsOff().assertIsEnabled()
        compose.onNodeWithTag("undo-past-passes").assertIsOff().assertIsEnabled()
        compose.onNodeWithTag("reshuffle-undo").assertIsOn().assertIsEnabled()
        compose.onNodeWithTag("reshuffle-branch").assertIsOn().assertIsEnabled().performScrollTo().performClick().assertIsOff()
        compose.onNodeWithTag("assume-hands").performScrollTo().performClick().assertIsOn()
        compose.runOnIdle { assertEquals(DraftSettings(gameSettings = AssumeReveals(hands = true, reshuffleOnBranch = false)), settings.value) }
        compose.onNodeWithTag("pod-game-settings").performScrollTo()
        captureShot("$SCREENSHOTS/non-gameplay/pod-game-settings.png",
            "Portrait New pod form for a Booster pod from the Runes cube, scrolled to its end: the Game settings card (choice: a card " +
                "at the screen's end, after the members in setup, with this note) with the lobby's switches that apply to pod games, \"Allow assumes that reveal " +
                "hands\" turned on and \"Reshuffle libraries on branch\" turned off by the creator.")
        compose.runOnIdle { settings.value = podModeSettings(DraftMode.SEALED, settings.value) }
        compose.runOnIdle { assertEquals(AssumeReveals(hands = true, reshuffleOnBranch = false), settings.value.gameSettings) }
    }

    // DRF-319: a member sees the pod's game settings greyed
    @Test fun aMemberSeesThePodsGameSettingsGreyed() {
        val settings = mutableStateOf(DraftSettings(gameSettings = AssumeReveals(reshuffleOnUndo = false)))
        form(false, settings)
        compose.onNodeWithTag("reshuffle-undo").performScrollTo().assertIsOff().assertIsNotEnabled().performClick()
        compose.onNodeWithTag("assume-library").assertIsOn().assertIsNotEnabled()
        compose.runOnIdle { assertEquals(AssumeReveals(reshuffleOnUndo = false), settings.value.gameSettings) }
    }
}

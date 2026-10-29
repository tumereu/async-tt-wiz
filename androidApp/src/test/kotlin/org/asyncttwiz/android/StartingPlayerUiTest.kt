package org.asyncttwiz.android

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import org.asyncttwiz.protocol.*
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w412dp-h892dp-port-mdpi")
class StartingPlayerUiTest {
    @get:Rule val compose = createComposeRule()
    @Test fun randomIsSelectedAndRepeatedClicksAndAccountRecompositionNeverDeselect() {
        val fixture = ScreenFixtures()
        val game = fixture.lobby.state.games.last()
        var preparation by mutableStateOf(PreparationRules.empty(game))
        var currentGame by mutableStateOf(game)
        compose.setContent {
            ScreenTheme { Surface(Modifier.fillMaxSize()) {
                StartingPlayerControl(currentGame, preparation.startingChoice, true) {
                    preparation = preparation.copy(starter = it)
                }
            } }
        }
        val random = compose.onNodeWithContentDescription("Starting player: Random")
        random.assertIsSelected().performClick().assertIsSelected()
        compose.onNodeWithContentDescription("Starting player: alex").performClick().assertIsSelected()
        random.assertIsNotSelected().performClick().assertIsSelected()
        compose.runOnIdle {
            preparation = preparation.copy(starter = null) // Legacy server snapshot after an account change.
            currentGame = game.copy(inviter = game.recipient, recipient = game.inviter)
        }
        random.assertIsSelected().performClick().assertIsSelected()
        compose.onAllNodes(isSelected()).assertCountEquals(1)
    }

    @Test fun alternateToggleSitsInTheCardHeaderOnByDefaultAndSendsItsNewState() {
        val fixture = ScreenFixtures()
        val game = fixture.lobby.state.games.last()
        var preparation by mutableStateOf(PreparationRules.empty(game))
        val sent = mutableListOf<Boolean>()
        compose.setContent {
            ScreenTheme { Surface(Modifier.fillMaxSize()) {
                StartingPlayerControl(game, preparation.startingChoice, true, preparation.alternateStarter,
                    { sent += it; preparation = preparation.copy(alternateStarter = it) }) { preparation = preparation.copy(starter = it) }
            } }
        }
        val toggle = compose.onNodeWithContentDescription("Alternate starting player")
        toggle.assertIsSelected()
        val title = compose.onNodeWithText("Starting player").getBoundsInRoot()
        val chip = toggle.getBoundsInRoot()
        assertTrue(chip.left > title.left && chip.top < title.bottom, "Top-right corner of the card")
        toggle.performClick().assertIsNotSelected()
        toggle.performClick().assertIsSelected()
        assertEquals(listOf(false, true), sent)
        // Choosing a player by hand stays possible while alternating is on.
        compose.onNodeWithContentDescription("Starting player: alex").performClick().assertIsSelected()
        toggle.assertIsSelected()
    }
}

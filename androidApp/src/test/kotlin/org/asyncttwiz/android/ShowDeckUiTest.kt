package org.asyncttwiz.android

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import java.util.UUID
import org.asyncttwiz.protocol.*
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w412dp-h892dp-port-mdpi")
class ShowDeckUiTest {
    @get:Rule val compose = createComposeRule()
    private fun id() = UUID.randomUUID().toString()

    @Test fun opponentSeesTheDeckNameOnlyAfterTheOwnerSwitchesShowOnAndAgainHiddenAfterOff() {
        val owner = id(); val viewer = id()
        val deck = PreparedDeck(id(), id(), "1", "Pod name", null, id(), emptyList())
        var preparation by mutableStateOf(LobbyPreparation(id(), id(), listOf(PlayerPreparation(owner, deck = deck))))
        val sent = mutableListOf<Boolean>()
        compose.setContent {
            ScreenTheme { Surface(Modifier.fillMaxSize()) { Column {
                val shown = preparation.players.single().deckShown
                ShowDeckChip(shown, enabled = true) { value ->
                    sent += value
                    // What the server answers: the private pod name arrives only with the shown flag.
                    preparation = preparation.copy(players = preparation.players.map {
                        it.copy(deckShown = value, shownPodDeckName = "Secret plan".takeIf { value }) })
                }
                PreparedSummary(preparation, owner, viewer)
            } } }
        }
        compose.onNodeWithText("Deck").assertExists()
        compose.onNodeWithText("Secret plan").assertDoesNotExist()
        val chip = compose.onNodeWithContentDescription("Show deck name and art to opponent")
        chip.assertIsNotSelected().performClick().assertIsSelected()
        compose.onNodeWithText("Secret plan").assertExists()
        chip.performClick().assertIsNotSelected()
        compose.onNodeWithText("Secret plan").assertDoesNotExist()
        compose.onNodeWithText("Deck").assertExists()
        assertEquals(listOf(true, false), sent)
    }
}

package org.asyncttwiz.android

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import org.asyncttwiz.protocol.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class DeckNamePresentationUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun preparedSummariesNeverRenderOpponentsTitleAndSwitchWithViewer() {
        val preparation = LobbyPreparation("game", "round", listOf(
            PlayerPreparation("a", deck = PreparedDeck("deck-a", "source-a", "1", "Own strategy", null, "select-a", emptyList())),
            PlayerPreparation("b", deck = PreparedDeck("deck-b", "source-b", "1", "Secret sideboard", null, "select-b", emptyList()))), emptyList())
        var viewer by mutableStateOf("a")
        compose.setContent { MaterialTheme { Column {
            PreparedSummary(preparation, "a", viewer)
            PreparedSummary(preparation, "b", viewer)
        } } }
        compose.onNodeWithText("Own strategy").assertIsDisplayed()
        compose.onNodeWithText("Secret sideboard").assertDoesNotExist()
        compose.onNodeWithText("Deck").assertIsDisplayed()
        compose.runOnIdle { viewer = "b" }
        compose.onNodeWithText("Own strategy").assertDoesNotExist()
        compose.onNodeWithText("Secret sideboard").assertIsDisplayed()
    }
}

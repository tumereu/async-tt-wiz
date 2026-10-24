package org.asyncttwiz.android

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w412dp-h892dp-port-mdpi")
class DeckEditorUiTest {
    @get:Rule val compose = createComposeRule()
    @Test fun editReplacesActionAndPrefillsListAndCloseConfirmsDiscardAndCoverIsDraftOnly() {
        val fixture = ScreenFixtures().apply { configure("deck-detail") }
        fixture.importer.exit()
        val detail = fixture.library.state.detail!!
        compose.setContent {
            ScreenTheme { Surface(Modifier.fillMaxSize()) {
                if (fixture.importer.state.active) DeckImportScreen(fixture.importer, false, null, null)
                else SavedDeckScreen(fixture.library, null) {
                    fixture.importer.openEditor(it)
                    fixture.importer.seed(fixture.importer.state.copy(busy = false,
                        result = DeckEditorSource(it).resolve(fixture.importer.state.text) { error("No new cards") }))
                }
            } }
        }
        compose.onNodeWithText("Edit").performClick()
        compose.onNodeWithText("Edit").assertDoesNotExist()
        compose.onNodeWithText("Save").assertIsDisplayed()
        compose.onNodeWithTag("deck-list").assertTextContains("Deck\n4 Meadow Sentinel", substring = true)
        compose.onNodeWithContentDescription("Choose deck cover").performClick()
        compose.onNodeWithText("Aegis Ward").performClick()
        assertEquals(detail.definitions.entries.first { it.value.name == "Aegis Ward" }.key, fixture.importer.state.cover)
        assertEquals(detail, fixture.library.state.detail)
        compose.onNodeWithTag("deck-name").performTextReplacement("Edited title")
        compose.onNodeWithContentDescription("Exit without saving").performClick()
        compose.onNodeWithText("Discard unsaved changes?").assertIsDisplayed()
        compose.onNodeWithText("Keep editing").performClick()
        compose.onNodeWithTag("deck-name").assertTextEquals("Edited title")
        compose.onNodeWithContentDescription("Exit without saving").performClick()
        compose.onNodeWithText("Discard").performClick()
        compose.onNodeWithText("Edit").assertIsDisplayed()
        assertEquals(detail, fixture.library.state.detail)
    }
}

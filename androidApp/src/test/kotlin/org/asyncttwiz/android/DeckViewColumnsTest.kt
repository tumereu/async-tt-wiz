package org.asyncttwiz.android

import android.content.Context
import android.content.res.Configuration
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.*

/** Saved decks open in the game preparation's visual view, read-only, and both views keep their own column count. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w412dp-h892dp-port-mdpi")
class DeckViewColumnsTest {
    @get:Rule val compose = createComposeRule()
    private val preferences = ApplicationProvider.getApplicationContext<Context>().getSharedPreferences("deck-view", 0)
    private val savedKey = deckViewColumnsKey(DeckViewScreen.SAVED, Configuration.ORIENTATION_PORTRAIT)
    private val preparationKey = deckViewColumnsKey(DeckViewScreen.PREPARATION, Configuration.ORIENTATION_PORTRAIT)

    @Before fun clear() { preferences.edit().clear().commit() }

    private fun tileWidth(description: String) = compose.onNodeWithContentDescription(description).fetchSemanticsNode().boundsInRoot.width
    /** 412dp less the 16dp gutters, split into [columns] with 4dp gaps. */
    private fun expected(columns: Int) = (412f - 32f - 4f * (columns - 1)) / columns

    @Test fun savedDeckShowsBothSectionsAsTypeGroupedTilesThatOnlyInspect() {
        val fixture = ScreenFixtures().also { it.configure("deck-detail") }
        try {
            compose.setContent { ScreenTheme { SavedDeckScreen(fixture.library, null, importDeck = {}) } }
            compose.onNodeWithText("Mainboard · 60").assertIsDisplayed()
            compose.onNodeWithText("Creatures · 60").assertIsDisplayed()
            val tile = compose.onNodeWithContentDescription("Meadow Sentinel, 4 copies, Mainboard").assertIsDisplayed()
            // Read-only: no move actions, no prep menu, no undo.
            assertFalse(SemanticsActions.CustomActions in tile.fetchSemanticsNode().config)
            tile.performTouchInput { click() }
            compose.onNodeWithText("Move one to Sideboard").assertDoesNotExist()
            compose.onNodeWithTag("preparation-undo").assertDoesNotExist()
            assertEquals("Meadow Sentinel", fixture.library.inspection.state?.card?.definition?.name)
            fixture.library.inspection.close()
            compose.onNodeWithTag("deck-view").performScrollToNode(hasText("Sideboard · 15"))
            compose.onNodeWithContentDescription("Quiet Grove, 15 copies, Sideboard").assertExists()
            compose.onNodeWithTag("deck-view").performScrollToNode(hasText("Delete saved deck"))
            compose.onNodeWithText("Delete saved deck").assertIsDisplayed()
        } finally { fixture.library.close() }
    }

    @Test fun deckViewDefaultsToThreeColumnsAndKeepsTheChosenCountApartFromPreparation() {
        val fixture = ScreenFixtures().also { it.configure("deck-detail") }
        try {
            compose.setContent { ScreenTheme { SavedDeckScreen(fixture.library, null, importDeck = {}) } }
            val tile = "Aegis Ward, 4 copies, Mainboard" // First by name, so on screen even one per row.
            compose.onNodeWithContentDescription("Columns").assert(hasText("3"))
            assertEquals(expected(3), tileWidth(tile), 1f)
            compose.onNodeWithContentDescription("More columns").performClick()
            compose.onNodeWithContentDescription("Columns").assert(hasText("4"))
            assertEquals(expected(4), tileWidth(tile), 1f)
            assertEquals(4, preferences.getInt(savedKey, -1))
            assertFalse(preferences.contains(preparationKey))
            compose.onNodeWithContentDescription("Columns").performSemanticsAction(SemanticsActions.SetProgress) { it(1f) }
            compose.onNodeWithContentDescription("Fewer columns").assertIsNotEnabled()
            assertEquals(expected(1), tileWidth(tile), 1f)
        } finally { fixture.library.close() }
    }

    @Test fun preparationReadsItsOwnColumnCountAndOffersTheSameControl() {
        preferences.edit().putInt(preparationKey, 5).putInt(savedKey, 2).commit()
        val fixture = ScreenFixtures().also { it.configure("prepare-deck-faded") }
        try {
            compose.setContent { ScreenTheme { PreparationScreen(fixture.lobby, "a", false) } }
            val tile = "Meadow Sentinel, 3 copies, Mainboard"
            compose.onNodeWithContentDescription("Columns").assert(hasText("5"))
            assertEquals(expected(5), tileWidth(tile), 1f)
            compose.onNodeWithContentDescription("Fewer columns").performClick()
            assertEquals(expected(4), tileWidth(tile), 1f)
            assertEquals(4, preferences.getInt(preparationKey, -1))
            assertEquals(2, preferences.getInt(savedKey, -1))
        } finally { fixture.lobby.close() }
    }
}

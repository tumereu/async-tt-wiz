package org.asyncttwiz.android

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.semantics.SemanticsProperties
import org.asyncttwiz.storage.CatalogTokenChoice
import org.asyncttwiz.protocol.*
import org.asyncttwiz.storage.CatalogTokenCursor
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.*

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w412dp-h892dp-port-mdpi")
class TokenMenuUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun gameEndRequiresOutcomeAndConfirmationAndCancelDoesNotSubmit() {
        val play = LiveTableFixture.menuPlay()
        val controller = TableToolkitController("a", { play }, { true }, { _, _ -> error("No gameplay submission") }).table
        val sent = mutableListOf<TableActionIntent>()
        var dismissed = false
        compose.setContent { ScreenTheme {
            TableActionMenu(play, "a", QuickContext(), controller, Color.Blue, { dismissed = true },
                perform = { sent += it; true })
        } }
        for (removed in listOf("Untap all", "Untap all · turn player", "Choose phase / step", "Next step", "End turn", "Draw one · turn player", "Create Blank token"))
            compose.onNodeWithContentDescription(removed).assertDoesNotExist()
        compose.onNodeWithContentDescription("Suggest game end").performClick()
        for (outcome in listOf("My win", "My loss", "Draw game")) compose.onNodeWithContentDescription(outcome).assertIsDisplayed()
        compose.onNodeWithContentDescription("My loss").performClick()
        compose.onNodeWithText("Suggest my loss?").assertIsDisplayed()
        compose.onNodeWithText("Cancel").performClick()
        assertTrue(sent.isEmpty()); assertFalse(dismissed)
        compose.onNodeWithContentDescription("Draw game").performClick()
        compose.onNodeWithText("Suggest draw?").assertIsDisplayed()
        compose.onNodeWithText("Confirm").performClick()
        assertEquals(EndingTool.SUGGEST_DRAW, (sent.single() as TableActionIntent.Ending).tool)
        assertEquals("", (sent.single() as TableActionIntent.Ending).note)
        assertTrue(dismissed)
    }

    @Test fun suggestionDialogHasOptionalReasonAndKeyboardDoneConfirmsWithTheTrimmedReason() {
        val play = LiveTableFixture.menuPlay()
        val controller = TableToolkitController("a", { play }, { true }, { _, _ -> error("No gameplay submission") }).table
        val sent = mutableListOf<TableActionIntent>()
        var dismissed = false
        compose.setContent { ScreenTheme {
            TableActionMenu(play, "a", QuickContext(), controller, Color.Blue, { dismissed = true },
                perform = { sent += it; true })
        } }
        compose.onNodeWithContentDescription("Suggest game end").performClick()
        compose.onNodeWithContentDescription("My loss").performClick()
        compose.onNodeWithText("Reason (optional)").assertIsDisplayed()
        compose.onNodeWithTag("resultNote").performClick()
        compose.onNodeWithText("Infinite combo").assertIsDisplayed()
        compose.onNodeWithTag("resultNote").performTextInput("  Infinite combo\twith Twin  ")
        compose.onNodeWithTag("resultNote").performTextInput("x".repeat(300))
        compose.runOnIdle { assertTrue(sent.isEmpty()) }
        compose.onNodeWithTag("resultNote").performImeAction()
        val ending = sent.single() as TableActionIntent.Ending
        assertEquals(EndingTool.SUGGEST_LOSS, ending.tool)
        assertEquals(("  Infinite combo with Twin  " + "x".repeat(300)).take(ResultNotes.MAX_LENGTH).trim(), ending.note)
        assertTrue(ResultNotes.valid(ending.note))
        assertTrue(dismissed)
    }

    @Test fun tokenRowsSupportInspectFavoriteFilteredDefaultsAndOneTapCreate() {
        val play = LiveTableFixture.menuPlay()
        fun token(name: String) = TokenCatalog.blank.definition.copy(name = name,
            faces = TokenCatalog.blank.definition.faces.map { it.copy(name = name, corner = "1/1", rulesText = "Flying") })
            .let { SubmittedDefinition(DeckJson.digest(it), it) }
        val food = token("Food"); val treasure = token("Treasure")
        val sent = mutableListOf<PlayAction>()
        val controller = TokenLinkController({ play }) { sent += it; true }
        controller.catalog()
        val search = object : TokenSearchSource {
            val page = TokenSearchPage("fixture", listOf(food, treasure), null, listOf(food, treasure).map(CatalogTokenChoice::from))
            override fun cached() = page
            override suspend fun search(query: String, cursor: CatalogTokenCursor?): TokenSearchPage = error("Cached dialog must not search")
            override suspend fun isCurrent(generation: String) = true
        }
        var saved: TokenFavorites? = null
        val favorites = object : TokenFavoritesSource {
            override suspend fun cached() = TokenFavorites()
            override suspend fun refresh() = TokenFavorites()
            override suspend fun set(favorites: TokenFavorites) { saved = favorites }
        }
        compose.setContent { ScreenTheme { TokenCatalogDialog(controller, emptyList(), searchSource = search, favoritesSource = favorites) } }
        compose.onNodeWithContentDescription("Create Food token").assertIsDisplayed()
        compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.ProgressBarRangeInfo)).assertCountEquals(0)
        compose.onNodeWithContentDescription("Create Food token").performTouchInput { longClick() }
        compose.onNodeWithContentDescription("Close card inspection").assertIsDisplayed().performClick()
        assertTrue(sent.isEmpty())
        compose.onNodeWithText("Search tokens").performTextInput("Food")
        compose.onNodeWithContentDescription("Remove favorite Food").performClick()
        compose.runOnIdle {
            assertEquals(listOf(TokenIdentity.key(treasure.definition)), saved!!.keys)
        }
        compose.onNodeWithContentDescription("Create Food token").performClick()
        compose.runOnIdle { assertEquals(listOf<PlayAction>(PlayAction.CreateToken(food)), sent) }
    }
}

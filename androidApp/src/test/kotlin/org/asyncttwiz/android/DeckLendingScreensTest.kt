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
import kotlin.test.assertEquals

/** LOB-244..LOB-258 (owner 2026-11-19): deck categories in Manage decks, and lent decks in a game's lobby. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w412dp-h892dp-port-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DeckLendingScreensTest {
    @get:Rule val compose = createComposeRule()
    private val box = DeckCategory("c-box", "Battle box")
    private val red = DeckCategory("c-red", "Red", box.id)
    private val green = DeckCategory("c-green", "Green", box.id)
    private val casual = DeckCategory("c-casual", "Casual")
    private fun deck(id: String, name: String, category: String? = null) =
        DeckSummary(id, name, "1", "2026-11-19T12:00:00Z", 60, 15, category = category, automatum = CardListSupport(21))
    private val lentDecks = listOf(deck("d-burn", "Burn", red.id), deck("d-goblins", "Goblins", red.id), deck("d-elves", "Elves", green.id),
        deck("d-ramp", "Ramp", green.id))

    // LOB-244, LOB-245: the Decks tab lists the categories as a tree: an open category shows its subcategories, then its
    // decks; decks without a category come last; New category sits at the top
    @Test fun manageDecksShowsTheCategoryTree() {
        val fixtures = ScreenFixtures()
        fixtures.library.seed(DeckLibraryState(loaded = true, categories = listOf(box, red, green, casual), expanded = setOf(box.id, red.id),
            decks = lentDecks + listOf(deck("d-pauper", "Pauper night", casual.id), deck("d-training", "Training deck"), deck("d-starter", "Starter list"))))
        compose.setContent { ScreenTheme { Surface(Modifier.fillMaxSize(), color = ScreenBackground) {
            ShellContent(ShellDestination.DECKS, true, {}, {}, null, fixtures.importer, fixtures.library, fixtures.lobby, "a") } } }
        compose.onNodeWithTag("new-category").assertIsDisplayed()
        compose.onNodeWithText("Burn").assertIsDisplayed()
        compose.onNodeWithText("Pauper night").assertDoesNotExist()
        // LOB-245 (owner playtest 2026-11-19): every category's ⋮ sits at the row's right edge, whatever its depth, where the
        // deck rows end
        val menuEdges = listOf(box, green, red, casual).map {
            compose.onNodeWithContentDescription("${it.name} options").fetchSemanticsNode().boundsInRoot.right }
        assertEquals(1, menuEdges.distinct().size, "$menuEdges")
        assertEquals(compose.onNodeWithTag("category-Battle box").fetchSemanticsNode().boundsInRoot.right, menuEdges.first(), .5f)
        captureShot("$SCREENSHOTS/lending/manage-decks-categories.png", "Portrait Manage decks, Decks tab: New category at the top, " +
            "the categories as a tree with Battle box and its Red subcategory open (Red's decks, then Battle box's Green shut), Casual shut, " +
            "and the decks without a category last.")
        compose.onNodeWithContentDescription("Casual options").performClick()
        compose.onNodeWithText("Delete").performClick()
        compose.onNodeWithText("Delete category?").assertIsDisplayed()
        compose.onNodeWithText("Its decks and subcategories move up to the top level. No deck is deleted.").assertIsDisplayed()
    }

    // LOB-247, LOB-248, LOB-249, LOB-250, LOB-251, LOB-254: the creator's Decks group, lending a category, Random on
    @Test fun theCreatorSetsTheDeckSettings() {
        val fixtures = ScreenFixtures()
        val game = mutableStateOf(fixtures.lobby.state.games.last().copy(
            deckSettings = DeckSettings(DeckLending.Categories(listOf(box.id)), onlyLent = true, sameDeck = false, random = true)))
        val sent = mutableListOf<DeckSettings>()
        compose.setContent { ScreenTheme { Surface(Modifier.fillMaxSize(), color = ScreenBackground) {
            Column(Modifier.padding(16.dp)) {
                GameSettingsCard(game.value, game.value.inviter.accountId, DeckSettingsAccess(listOf(box, red, green, casual), {}) {
                    sent += it; game.value = game.value.copy(deckSettings = it) }) {}
            }
        } } }
        compose.onNodeWithTag("lend-decks").assertTextContains("Battle box", substring = true)
        compose.onNodeWithTag("only-lent").assertIsOn().assertIsEnabled()
        compose.onNodeWithTag("redeal").assertIsOff().assertIsEnabled()
        captureShot("$SCREENSHOTS/lending/game-settings-decks.png", "Portrait lobby Game settings card for the game's creator: the assume " +
            "switches, then the Decks group lending the Battle box category, Only lent decks on, Same deck allowed off, Deck choice Random " +
            "and New random decks on rematch off.")
        compose.onNodeWithTag("redeal").performClick()
        compose.onNodeWithTag("lend-decks").performClick()
        compose.onNodeWithText("All my decks").performClick()
        compose.onNodeWithTag("lend-done").performClick()
        assertEquals(listOf(DeckSettings(DeckLending.Categories(listOf(box.id)), onlyLent = true, sameDeck = false, random = true, redeal = true),
            DeckSettings(DeckLending.All, onlyLent = true, sameDeck = false, random = true, redeal = true)), sent)
    }

    // LOB-247, LOB-154: the other player sees the Decks values greyed, the categories counted
    @Test fun theOtherPlayerSeesTheDeckSettingsGreyed() {
        val fixtures = ScreenFixtures()
        val game = fixtures.lobby.state.games.last().copy(deckSettings = DeckSettings(DeckLending.Categories(listOf(box.id, casual.id))))
        compose.setContent { ScreenTheme { GameSettingsCard(game, game.recipient.accountId, DeckSettingsAccess(null, {}) { error("not the creator") }) {} } }
        compose.onNodeWithTag("lend-decks").assertContentDescriptionEquals("alex lends decks: 2 categories").assertIsNotEnabled()
        compose.onNodeWithTag("same-deck").assertIsOn().assertIsNotEnabled()
        compose.onNodeWithText(GameSettingsText.hostOnly("alex")).assertIsDisplayed()
    }

    // LOB-255, LOB-249: the other player's Choose deck in a battle box: the creator's lent decks first by category, their own greyed
    @Test fun chooseDeckListsTheLentDecksFirst() {
        val fixtures = ScreenFixtures()
        val game = fixtures.lobby.state.games.last().copy(invitationState = InvitationState.ACCEPTED,
            deckSettings = DeckSettings(DeckLending.Categories(listOf(box.id)), onlyLent = true))
        val lent = LentDecks(ScreenFixtures.alex, lentDecks, listOf(box, red, green))
        fixtures.lobby.seed(fixtures.lobby.state.copy(selected = game.id, detail = game, connectedGames = setOf(game.id),
            preparation = PreparationRules.empty(game),
            picker = DeckPickerState(game, listOf(deck("d-mine", "My first deck")), lentDecks.first(), loading = false, loaded = true, lent = lent)))
        compose.setContent { ScreenTheme { Surface(Modifier.fillMaxSize(), color = ScreenBackground) {
            WaitingLobby(fixtures.lobby, "b", {}, false, importDeck = {}) } } }
        compose.onNodeWithText("ALEX'S DECKS").assertIsDisplayed()
        compose.onNodeWithTag("only-lent-note").assertIsDisplayed()
        captureShot("$SCREENSHOTS/lending/choose-lent-deck.png", "Portrait Choose deck for the invited player of a battle box: alex's lent " +
            "decks first under their categories (Battle box › Green, Battle box › Red) with Burn highlighted, then the player's own deck greyed " +
            "under \"Only alex's lent decks can be used in this game.\"")
    }

    // LOB-250, LOB-256: a Random game's lobby for the invited player: no Choose deck, the deck dealt as the game starts, Ready possible
    @Test fun aRandomGameDealsTheDecks() {
        val fixtures = ScreenFixtures()
        val game = fixtures.lobby.state.games.last().copy(invitationState = InvitationState.ACCEPTED,
            deckSettings = DeckSettings(DeckLending.All, onlyLent = true, random = true, redeal = true))
        fixtures.lobby.seed(fixtures.lobby.state.copy(selected = game.id, detail = game, connectedGames = setOf(game.id),
            preparation = PreparationRules.empty(game)))
        compose.setContent { ScreenTheme { Surface(Modifier.fillMaxSize(), color = ScreenBackground) {
            WaitingLobby(fixtures.lobby, "b", {}, false, importDeck = {}) } } }
        compose.onAllNodesWithText(DEALT_AT_RANDOM).assertCountEquals(2)
        compose.onNodeWithText("Choose deck").assertDoesNotExist()
        compose.onNode(hasText("Ready") and hasClickAction()).assertIsEnabled()
        captureShot("$SCREENSHOTS/lending/lobby-random.png", "Portrait lobby of a Random battle box for the invited player: both decks read " +
            "\"Dealt at random when the game starts\", there is no Choose deck, and Ready can be pressed.")
    }

    // LOB-252: an invitation whose creator lends decks says so on its row
    @Test fun anInvitationSaysDecksAreLent() {
        val fixtures = ScreenFixtures()
        val invitation = fixtures.lobby.state.games.last().copy(id = "g3", inviter = ScreenFixtures.riley, recipient = ScreenFixtures.alex,
            invitationState = InvitationState.PENDING, deckSettings = DeckSettings(DeckLending.All, onlyLent = true, random = true))
        fixtures.lobby.seed(fixtures.lobby.state.copy(invitations = listOf(invitation), connectedGames = setOf("g1", "g2", "g3")))
        compose.setContent { ScreenTheme { Surface(Modifier.fillMaxSize(), color = ScreenBackground) {
            ShellContent(ShellDestination.GAMES, true, {}, {}, null, null, null, fixtures.lobby, "a") } } }
        compose.onNodeWithText("riley lends you decks").assertIsDisplayed()
        captureShot("$SCREENSHOTS/lending/invitation-lends-decks.png", "Portrait Games list with riley's invitation, its row saying " +
            "\"riley lends you decks\" under its status, Accept and Decline at its right.")
    }
}

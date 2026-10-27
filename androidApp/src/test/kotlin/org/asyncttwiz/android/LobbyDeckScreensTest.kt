package org.asyncttwiz.android

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.ViewModelStore
import androidx.test.core.app.ApplicationProvider
import org.asyncttwiz.protocol.*
import org.asyncttwiz.storage.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.UUID
import java.util.concurrent.AbstractExecutorService
import java.util.concurrent.TimeUnit
import kotlin.test.*

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w412dp-h892dp-port-mdpi")
class LobbyDeckScreensTest {
    @get:Rule val compose = createComposeRule()
    private fun id() = UUID.randomUUID().toString()
    private class Worker : AbstractExecutorService() {
        val queue = ArrayDeque<Runnable>()
        override fun execute(command: Runnable) { queue.add(command) }
        fun drain() { while (queue.isNotEmpty()) queue.removeFirst().run() }
        override fun shutdown() = Unit
        override fun shutdownNow() = mutableListOf<Runnable>()
        override fun isShutdown() = false
        override fun isTerminated() = false
        override fun awaitTermination(timeout: Long, unit: TimeUnit) = true
    }
    private val riley = AccountIdentity(id(), "Riley")
    private val morgan = AccountIdentity(id(), "Morgan")
    private inner class ContactsApi : Lobbies {
        var searches = 0
        var adds = 0
        var removes = 0
        override suspend fun contacts(token: String, after: String?) = ContactList(listOf(riley, morgan))
        override suspend fun search(token: String, query: String): UserSearch { searches++; return UserSearch(listOf(AccountIdentity("search-id", "Sam"))) }
        override suspend fun add(token: String, input: AddContact): AccountIdentity { adds++; return AccountIdentity(input.accountId, "Sam") }
        override suspend fun remove(token: String, accountId: String) { removes++ }
        override suspend fun games(token: String, invitations: Boolean, after: String?) = GameList(emptyList())
        override suspend fun create(token: String, input: CreateInvitation): GameSummary = error("Not creating in this test")
        override suspend fun head(token: String, id: String): GameSummary = error("No game selected")
        override fun close() = Unit
    }

    @Test fun newGameAndContactsShareDefaultTabsAndLocalFilteringAndConfirmRemoval() {
        val worker = Worker(); val api = ContactsApi()
        val controller = LobbyController(api, { "token" }, worker, { it() })
        controller.refresh(); worker.drain()
        var destination by mutableStateOf(ShellDestination.GAMES)
        try {
            compose.setContent { ScreenTheme { ShellContent(destination, true, {}, { destination = it }, null, null, null, controller, "owner") } }
            compose.onNodeWithText("New game").performClick()
            compose.onNodeWithTag("destination-title").assertTextEquals("Choose opponent")
            compose.onNode(hasText("Contacts") and hasClickAction()).assertIsSelected()
            compose.onNodeWithContentDescription("Filter contacts").performTextInput("ILE")
            compose.onNodeWithText("Riley").assertIsDisplayed()
            compose.onNodeWithText("Morgan").assertDoesNotExist()
            assertEquals(0, api.searches)
            compose.onNodeWithText("Riley").performClick()
            compose.onNodeWithText("New game").assertIsDisplayed()
            compose.runOnIdle { controller.dismiss(); controller.contacts(false) }
            compose.onNodeWithTag("destination-title").assertTextEquals("Contacts")
            compose.onAllNodesWithContentDescription("Remove contact")[0].performClick()
            assertEquals(riley, controller.state.removingContact)
            compose.onNodeWithText("Remove Riley?").assertIsDisplayed()
            compose.onNodeWithText("Cancel").performClick()
            assertEquals(0, api.removes)
            compose.onAllNodesWithContentDescription("Remove contact")[0].performClick()
            compose.onNodeWithText("Remove").performClick()
            compose.runOnIdle { worker.drain() }
            compose.onNodeWithText("Riley").assertDoesNotExist()
            assertEquals(1, api.removes)
        } finally { controller.close(); worker.drain() }
    }

    @Test fun searchInviteAddsContactAndAddedStateKeepsActionGeometry() {
        val worker = Worker(); val api = ContactsApi()
        val controller = LobbyController(api, { "token" }, worker, { it() })
        controller.refresh(); worker.drain(); controller.contacts(true)
        try {
            compose.setContent { ScreenTheme { ContactsContent(controller) } }
            compose.onNodeWithText("Search", useUnmergedTree = true).performClick()
            compose.onNodeWithContentDescription("Search usernames").performTextInput("Sam")
            compose.onNodeWithContentDescription("Search players").performClick()
            compose.runOnIdle { worker.drain() }
            val before = compose.onNodeWithContentDescription("Add contact").fetchSemanticsNode().boundsInRoot
            compose.onNodeWithContentDescription("Invite").performClick()
            assertNull(controller.state.draft)
            compose.runOnIdle { worker.drain() }
            compose.onNodeWithText("Invite Sam").assertIsDisplayed()
            assertEquals(1, api.adds)
            compose.onNodeWithText("Cancel").performClick()
            assertEquals(before, compose.onNodeWithContentDescription("Added contact").fetchSemanticsNode().boundsInRoot)
            compose.onNodeWithContentDescription("Invite").performClick()
            assertEquals(1, api.adds)
        } finally { controller.close(); worker.drain() }
    }

    @Test fun menuWaitsForFirstSnapshotAndRefreshAndMoveBadgeKeepGeometry() {
        val fixture = ScreenFixtures()
        val game = fixture.lobby.state.games.first().copy(attentionHolder = "a")
        fixture.lobby.seed(LobbyState(busy = true))
        compose.setContent { ScreenTheme { ShellContent(ShellDestination.MENU, true, {}, {}, null, null, null, fixture.lobby, "a") } }
        compose.onNodeWithTag("menu-DECKS").assertDoesNotExist()
        compose.runOnIdle { fixture.lobby.seed(LobbyState(loaded = true, games = listOf(game))) }
        val menu = compose.onNodeWithTag("menu-DECKS").fetchSemanticsNode().boundsInRoot
        val row = compose.onNodeWithTag("quick-continue-${game.id}").fetchSemanticsNode().boundsInRoot
        compose.runOnIdle { fixture.lobby.seed(fixture.lobby.state.copy(busy = true, games = listOf(game.copy(attentionHolder = "b")))) }
        assertEquals(menu, compose.onNodeWithTag("menu-DECKS").fetchSemanticsNode().boundsInRoot)
        assertEquals(row, compose.onNodeWithTag("quick-continue-${game.id}").fetchSemanticsNode().boundsInRoot)
    }

    @Test fun drawerKeepsNavigationAndAccountActionsAvailableDuringInitialLoadAndFailure() {
        val fixture = ScreenFixtures(accountSwitcher = true)
        fixture.auth.seed(AuthState(account = ScreenFixtures.alex, restoring = false,
            rememberedAccounts = listOf(RememberedAccount(ScreenFixtures.alex, "alex@example.test"),
                RememberedAccount(ScreenFixtures.riley, "riley@example.test"))))
        fixture.lobby.seed(LobbyState(busy = true))
        compose.setContent { ScreenTheme {
            Surface(Modifier.fillMaxSize()) {
                if (fixture.auth.state.destination == ShellDestination.SETTINGS)
                    SettingsContent(null, "a", fixture.auth::logout)
                else Navigation(fixture.auth, true, fixture.lobby)
            }
        } }
        compose.onNodeWithText(ShellDestination.DECKS.title).assertIsDisplayed()
        compose.onNodeWithText("Settings").assertIsDisplayed()
        compose.onNodeWithTag("switch-account-b").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Add account").performScrollTo().assertIsDisplayed()
        compose.runOnIdle { fixture.lobby.seed(LobbyState(error = "Synthetic lobby failure")) }
        compose.onNodeWithText("Retry").performScrollTo().assertIsEnabled()
        compose.onNodeWithTag("switch-account-b").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Add account").assertIsDisplayed()
        compose.onNodeWithText(ShellDestination.DECKS.title).performScrollTo().performClick()
        assertEquals(ShellDestination.DECKS, fixture.auth.state.destination)
        compose.onNodeWithText("Settings").performClick()
        compose.onNodeWithTag("logout").performScrollTo().performClick()
        assertNull(fixture.auth.state.account)
    }

    @Test @Config(qualifiers = "w412dp-h640dp-port-mdpi")
    fun portraitCatalogInstallRelooksUpTheDraftWithoutCatalogControls() = assertCatalogRecovery(false)

    @Test @Config(qualifiers = "w892dp-h412dp-land-mdpi")
    fun landscapeCatalogInstallRelooksUpTheDraftWithoutCatalogControls() = assertCatalogRecovery(true)

    private fun assertCatalogRecovery(landscape: Boolean) {
        val models = ViewModelStore()
        val catalog = CatalogViewModel(ApplicationProvider.getApplicationContext()).also { models.put("catalog", it) }
        val metadata = CatalogMetadata("2026-10-26", "test")
        catalog.seed(CatalogUiState(loaded = true, available = metadata))
        val worker = Worker()
        var resolved = false
        val printing = CatalogPrinting(id(), "Unknown", "TST", "1", "English", "", null, false, emptyList(), "{}")
        val importer = DeckImportController({ text -> DeckImportResult(text,
            if (resolved) listOf(ResolvedDeckEntry(DeckSection.MAINBOARD, 1, listOf(printing), listOf(1))) else emptyList(),
            if (resolved) emptyList() else listOf(ImportIssue(1, "Card records are missing or outdated")),
            if (resolved) metadata else null) }, worker, { it() })
        importer.open(); importer.name("Keep my draft"); importer.edit("1 Unknown"); importer.lookup(); worker.drain()
        try {
            compose.setContent { ScreenTheme { DeckImportScreen(importer, landscape, null, catalog) } }
            // Card records are managed in Settings only; the problem list names what failed.
            compose.onNodeWithText("Download card records").assertDoesNotExist()
            compose.onNodeWithText("Check for updates").assertDoesNotExist()
            compose.onNodeWithText("Refresh full catalog").assertDoesNotExist()
            compose.onNodeWithText("Line 1: Card records are missing or outdated").assertIsDisplayed()
            val paste = compose.onNodeWithTag("deck-list").getUnclippedBoundsInRoot()
            assertEquals("Keep my draft", importer.state.name)
            assertEquals("1 Unknown", importer.state.text)
            compose.runOnIdle {
                resolved = true
                catalog.seed(CatalogUiState(loaded = true, installed = metadata))
            }
            compose.runOnIdle { worker.drain() }
            compose.onNodeWithTag("save-deck").assertIsEnabled()
            assertTrue(importer.state.active)
            assertEquals("Keep my draft", importer.state.name)
            assertEquals("1 Unknown", importer.state.text)
            assertEquals(paste, compose.onNodeWithTag("deck-list").getUnclippedBoundsInRoot())
        } finally { importer.close(); worker.drain(); models.clear() }
    }

    @Test @Config(qualifiers = "w892dp-h412dp-land-mdpi")
    fun failedLookupOffersRetryWithoutSwitchingLandscapeColumnsOrMovingPasteArea() {
        val worker = Worker(); var failing = true
        val importer = DeckImportController({ text ->
            if (failing) error("Synthetic storage failure")
            DeckImportResult(text, emptyList(), listOf(ImportIssue(1, "Unknown card")), null)
        }, worker, { it() })
        importer.openExisting(DeckSummary(id(), "Existing deck", "1", "date"))
        try {
            compose.setContent { ScreenTheme { DeckImportScreen(importer, true, null, null) } }
            compose.onNodeWithText("Overwrite").assertExists()
            compose.onNodeWithText("Merge").assertExists()
            compose.onNodeWithTag("deck-list").performTextInput("1 Unknown")
            val before = compose.onNodeWithTag("deck-list").fetchSemanticsNode().boundsInRoot
            compose.mainClock.advanceTimeBy(500)
            compose.runOnIdle { worker.drain() }
            assertEquals(before, compose.onNodeWithTag("deck-list").fetchSemanticsNode().boundsInRoot)
            compose.onNodeWithTag("retry-lookup").performScrollTo().assertExists()
            val scrolled = compose.onNodeWithTag("deck-list").fetchSemanticsNode().boundsInRoot
            compose.runOnIdle { failing = false; importer.lookup(); worker.drain() }
            compose.onNodeWithText("Line 1: Unknown card").assertExists()
            compose.onNodeWithTag("save-deck").assertIsNotEnabled()
            assertEquals(scrolled, compose.onNodeWithTag("deck-list").fetchSemanticsNode().boundsInRoot)
        } finally { importer.close(); worker.drain() }
    }

    @Test fun automaticLookupEnablesSingleStepSaveThenOpensSavedDeckForReview() {
        val worker = Worker()
        val printing = CatalogPrinting(id(), "Rune Bear", "TST", "1", "English", "", null, false, emptyList(), """{"name":"Rune Bear","type":"Creature"}""")
        val imported = DeckImportResult("2 Rune Bear", listOf(ResolvedDeckEntry(DeckSection.MAINBOARD, 2, listOf(printing), listOf(1))),
            emptyList(), CatalogMetadata("2026-10-26", "test"))
        var saved: SavedDeck? = null
        var definitions = emptyMap<String, CardDefinition>()
        val api = object : Decks {
            override suspend fun list(token: String, after: String?) = DeckList(listOfNotNull(saved?.deck))
            override suspend fun read(token: String, id: String) = checkNotNull(saved)
            override suspend fun definition(token: String, id: String, digest: String) = SubmittedDefinition(digest, definitions.getValue(digest))
            override suspend fun submit(token: String, input: SubmitDefinitions): DefinitionsAccepted = error("Use captured save action")
            override suspend fun create(token: String, input: CreateDeck): SavedDeck = error("Use captured save action")
            override suspend fun update(token: String, id: String, input: UpdateDeck): SavedDeck = error("No update")
            override suspend fun delete(token: String, id: String, input: DeleteDeck): DeckDeleted = error("No delete")
            override fun close() = Unit
        }
        val library = DeckLibraryController(api, { "token" }, worker, { it() })
        val importer = DeckImportController({ imported }, worker, { it() })
        importer.connectSave({ { plan ->
            definitions = plan.definitions.definitions.associate { it.digest to it.definition }
            SavedDeck(DeckSummary(id(), plan.deck.name, "1", "date", 2, 0,
                cover = DeckCover(checkNotNull(plan.cover), "Rune Bear")), plan.deck.entries).also { saved = it }
        } }, { library.open(it.deck.id) })
        importer.open()
        try {
            compose.setContent { ScreenTheme {
                if (importer.state.active) DeckImportScreen(importer, false, null, null)
                else SavedDeckScreen(library, null)
            } }
            compose.onNodeWithTag("save-deck").assertIsNotEnabled()
            compose.onNodeWithTag("retry-lookup").assertDoesNotExist()
            compose.onNodeWithTag("deck-name").performTextInput("Bear deck")
            compose.onNodeWithTag("deck-list").performTextInput(imported.text)
            val before = compose.onNodeWithTag("deck-list").fetchSemanticsNode().boundsInRoot
            compose.mainClock.advanceTimeBy(500)
            compose.runOnIdle { worker.drain() }
            compose.onNodeWithTag("save-deck").assertIsEnabled()
            compose.onNodeWithText("Review import").assertDoesNotExist()
            assertEquals(before, compose.onNodeWithTag("deck-list").fetchSemanticsNode().boundsInRoot)
            compose.onNodeWithContentDescription("Choose deck cover").assertExists()
            assertEquals(importer.coverCards.first().digest, importer.currentCover?.definition)
            compose.onNodeWithTag("save-deck").performClick()
            compose.runOnIdle { worker.drain() }
            compose.onNodeWithText("Saved deck").assertIsDisplayed()
            compose.onNodeWithText("Bear deck").assertIsDisplayed()
            compose.onNodeWithText("Mainboard").assertIsDisplayed()
            compose.onNodeWithText("Sideboard").assertIsDisplayed()
            compose.onNodeWithText("2 cards").assertIsDisplayed()
            compose.onNodeWithText("0 cards").assertIsDisplayed()
            compose.onNodeWithContentDescription("Exit without saving").assertDoesNotExist()
            assertEquals(saved?.deck?.id, library.state.selected)
        } finally { importer.close(); library.close(); worker.drain() }
    }
}

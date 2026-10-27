package org.asyncttwiz.android

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasProgressBarRangeInfo
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelStore
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureScreenRoboImage
import org.asyncttwiz.protocol.*
import org.asyncttwiz.storage.*
import org.junit.Rule
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner.Parameters
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.lang.reflect.Proxy
import java.util.concurrent.AbstractExecutorService
import java.util.concurrent.TimeUnit
import javax.imageio.ImageIO
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Production screen composables, synthetic accepted state, no HTTP/catalog/art dependencies. */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w412dp-h892dp-port-mdpi")
class NonGameplayScreenshotTest(private val screen: String) {
    @get:Rule val compose = createComposeRule()
    private val viewModels = ViewModelStore()
    @After fun clearViewModels() { viewModels.clear() }

    companion object {
        @JvmStatic @Parameters(name = "{0}") fun screens() = listOf(
            "sign-in", "register", "register-scrolled", "reset-email", "new-password", "home", "navigation-open", "navigation-accounts",
            "play", "games-loading", "catalog-startup", "catalog-update", "catalog-checking", "records-downloading", "records-downloading-unknown", "records-indexing", "contacts", "create-game", "waiting-invite", "game-context", "delete-game", "cancel-lobby",
            "lobby-select-deck", "choose-lobby-deck", "prepare-deck", "prepare-deck-faded", "ready-waiting",
             "decks", "deck-detail", "deck-edit", "deck-edit-cover", "deck-art", "choose-cover", "delete-deck", "import-edit", "import-errors",
            "import-save", "import-save-scrolled", "reconnecting", "gameplay-chat")
    }

    @Test fun portrait() = capture(false)
    @Test @Config(qualifiers = "w892dp-h412dp-land-mdpi") fun landscape() = capture(true)

    @OptIn(ExperimentalRoborazziApi::class)
    private fun capture(landscape: Boolean) {
        val fixtures = ScreenFixtures(accountSwitcher = screen == "navigation-accounts")
        fixtures.auth.seed(AuthState(restoring = false, page = if (screen.startsWith("register")) AuthPage.REGISTER else AuthPage.LOGIN,
            username = "player2_tactics", email = "player1@tabletop.local", password = "password123",
            message = if (screen == "sign-in") "Email or password not recognised." else null, error = screen == "sign-in"))
        if (screen == "reset-email") fixtures.reset.seed(ResetState(ResetPage.REQUEST, email = "alex@example.test",
            message = "If this email has an account, a reset link is on its way."))
        if (screen == "new-password") fixtures.reset.seed(ResetState(ResetPage.PASSWORD, password = "password123"))
        fixtures.configure(screen)
        val accountScreen = screen in listOf("sign-in", "register", "register-scrolled", "reset-email", "new-password")
        if (!accountScreen) fixtures.auth.seed(AuthState(restoring = false, account = ScreenFixtures.alex,
            rememberedAccounts = if (screen == "navigation-accounts") listOf(
                RememberedAccount(ScreenFixtures.alex, "alex@example.test"),
                RememberedAccount(ScreenFixtures.riley, "riley@example.test")) else emptyList()))
        val catalog = if (screen == "catalog-checking" || screen.startsWith("records-")) {
            fixtures.auth.seed(fixtures.auth.state.copy(destination = ShellDestination.GAMES))
            fixtures.lobby.seed(fixtures.lobby.state.copy(busy = true))
            val progress = when (screen) {
                "records-indexing" -> CatalogProgress(CatalogPhase.INDEXING, 160_000_000, 450_000_000, 45_123)
                "records-downloading" -> CatalogProgress(CatalogPhase.DOWNLOADING, 160_000_000, 450_000_000)
                "records-downloading-unknown" -> CatalogProgress(CatalogPhase.DOWNLOADING, 160_000_000, null)
                else -> null
            }
            CatalogViewModel(ApplicationProvider.getApplicationContext()).also {
                viewModels.put("catalog", it)
                it.seed(CatalogUiState(busy = true, progress = progress, status = progress?.label() ?: "Checking card records…"))
            }
        } else null
        if (screen == "prepare-deck") compose.mainClock.autoAdvance = false
        compose.setContent {
            ScreenTheme {
                Surface(Modifier.fillMaxSize(), color = ScreenBackground) {
                    if (accountScreen) {
                        AccountEntry(fixtures.auth, fixtures.reset)
                    } else if (screen in listOf("reconnecting", "gameplay-chat")) {
                        MaterialTheme {
                            TableSurface(LiveTableFixture.game, LiveTableFixture.play(), "a", landscape, Modifier.fillMaxSize(), connected = screen != "reconnecting")
                            if (screen == "reconnecting") ReconnectingDialog({})
                            else Box(Modifier.fillMaxSize(), contentAlignment = Alignment.CenterEnd) {
                                ChatPanel(fixtures.lobby, "a", Modifier.fillMaxWidth(if (landscape) .5f else 1f))
                            }
                        }
                    } else if (catalog != null) {
                        AuthenticatedShell(fixtures.auth, landscape, catalog, lobby = fixtures.lobby)
                    } else if (screen in listOf("navigation-open", "navigation-accounts") && !landscape) {
                        Box(Modifier.fillMaxSize()) {
                            ShellContent(ShellDestination.GAMES, true, {}, {}, null, null, null, fixtures.lobby, "a")
                            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = .2f)))
                            Surface(Modifier.width(280.dp).fillMaxHeight()) { Navigation(fixtures.auth, true, fixtures.lobby) }
                        }
                    } else {
                        Row(Modifier.fillMaxSize()) {
                            if (landscape) Surface(Modifier.width(220.dp).fillMaxHeight(),
                                color = Color.White) { Navigation(fixtures.auth, !landscape, fixtures.lobby) }
                            Box(Modifier.weight(1f).fillMaxHeight()) {
                                when (screen) {
                                    "waiting-invite", "lobby-select-deck", "ready-waiting", "choose-lobby-deck", "prepare-deck", "prepare-deck-faded", "cancel-lobby" ->
                                        WaitingLobby(fixtures.lobby, "a", if (landscape) null else ({}), landscape, importDeck = {})
                                    "deck-detail", "deck-art", "choose-cover", "delete-deck" ->
                                        SavedDeckScreen(fixtures.library, if (landscape) null else ({}), importDeck = {})
                                     "deck-edit", "deck-edit-cover", "import-edit", "import-errors", "import-save", "import-save-scrolled" ->
                                        DeckImportScreen(fixtures.importer, landscape, if (landscape) null else ({}), null)
                                    else -> ShellContent(when (screen) {
                                        "play", "games-loading", "game-context", "delete-game" -> ShellDestination.GAMES
                                        "contacts", "create-game" -> ShellDestination.CONTACTS
                                        "decks" -> ShellDestination.DECKS
                                        else -> ShellDestination.MENU
                                    }, !landscape, {}, {}, null, fixtures.importer, fixtures.library, fixtures.lobby, "a")
                                }
                            }
                        }
                        SetupCancellationDialogs(fixtures.lobby)
                        GameDeletionDialogs(fixtures.lobby)
                        if (screen in listOf("catalog-startup", "catalog-update")) CatalogStartupDialog(CatalogUiState(
                            installed = if (screen == "catalog-update") CatalogMetadata("2026-10-20", "5.2.2") else null,
                            available = CatalogMetadata("2026-10-24", "5.2.2"), size = 450_000_000, metered = true), {}, {})
                    }
                }
            }
        }
        compose.waitForIdle()
        if (screen == "prepare-deck") { compose.mainClock.advanceTimeByFrame(); compose.waitForIdle() }
        if (screen == "register-scrolled") compose.onNodeWithText("Sign in").performScrollTo()
        if (screen == "import-save-scrolled") compose.onNodeWithTag("deck-list").performScrollTo()
        // Assert the important geometry/semantics, not just that a PNG could be produced.
        fun paired(first: String, second: String) {
            val a = compose.onNode(hasText(first) and hasClickAction()).fetchSemanticsNode().boundsInRoot
            val b = compose.onNode(hasText(second) and hasClickAction()).fetchSemanticsNode().boundsInRoot
            assertEquals(a.width, b.width, .5f, "Paired actions must split the available row equally")
            assertEquals(a.top, b.top, .5f, "Paired actions must not stack")
        }
        when (screen) {
            "games-loading" -> {
                compose.onNodeWithText("Loading games…").assertDoesNotExist()
                compose.onAllNodesWithTag("floating-busy-pill").assertCountEquals(0)
            }
            "catalog-startup", "catalog-update" -> {
                compose.onNodeWithText("Download size: 450 MB.").assertIsDisplayed()
                compose.onNodeWithText("Download").assertIsDisplayed()
                compose.onNodeWithContentDescription("Not now").assertDoesNotExist()
                paired(if (screen == "catalog-startup") "Exit" else "Not now", "Download")
            }
            "catalog-checking" -> {
                compose.onNodeWithText("Checking card records…").assertDoesNotExist()
                compose.onNodeWithText("Loading games…").assertDoesNotExist()
            }
            "records-downloading", "records-downloading-unknown", "records-indexing" -> {
                compose.onNodeWithText(catalog!!.state.progress!!.label()).assertIsDisplayed()
                compose.onNodeWithText("Loading games…").assertDoesNotExist()
                compose.onAllNodes(hasProgressBarRangeInfo(ProgressBarRangeInfo.Indeterminate))
                    .assertCountEquals(if (screen == "records-downloading-unknown") 2 else 0)
            }
            "navigation-accounts" -> {
                compose.onNodeWithText("Add account").performScrollTo().assertIsDisplayed()
                compose.onNodeWithText("Switch account").assertIsDisplayed()
                compose.onNodeWithText("alex").assertIsDisplayed()
                compose.onNodeWithTag("switch-account-b").assertIsDisplayed()
                compose.onNodeWithContentDescription("Current account").assertIsDisplayed()
            }
            "waiting-invite", "ready-waiting" -> {
                paired("Change deck", "Prepare deck")
                compose.onNodeWithContentDescription("Cancel game").assertIsDisplayed()
            }
            "play" -> paired("Accept", "Decline")
            "choose-lobby-deck" -> paired("Import deck", "Use selected deck")
            "choose-cover" -> paired("Use cover", "Cancel")
              "import-save", "import-save-scrolled" -> {
                  compose.onNodeWithTag("save-deck").assertIsDisplayed().assertIsEnabled()
                  compose.onNodeWithTag("deck-list").assertExists()
                  compose.onNodeWithText("Edit pasted list").assertDoesNotExist()
              }
             "deck-detail" -> {
                 compose.onNodeWithText("Edit").assertIsDisplayed()
                 compose.onNodeWithText("Save").assertDoesNotExist()
                 val row = compose.onNodeWithText("Meadow Sentinel").fetchSemanticsNode().boundsInRoot
                 assertTrue(row.height <= 48f)
             }
              "deck-edit" -> {
                  compose.onNodeWithContentDescription("Save deck").assertIsDisplayed()
                 compose.onNodeWithText("Edit").assertDoesNotExist()
                 compose.onNodeWithTag("deck-list").assertIsDisplayed()
                 compose.onNodeWithContentDescription("Choose deck cover").assertIsDisplayed()
             }
             "deck-edit-cover" -> {
                 compose.onNodeWithText("Choose cover").assertIsDisplayed()
                 compose.onNodeWithText("Meadow Sentinel").assertIsDisplayed()
             }
            "create-game" -> paired("Cancel", "Create")
            "delete-game" -> paired("Keep game", "Delete game")
            "cancel-lobby" -> paired("Keep game", "Cancel game")
            "gameplay-chat" -> compose.onNodeWithContentDescription("Send").assertIsDisplayed()
            "reconnecting" -> compose.onNodeWithText("Back to games list").assertIsDisplayed()
            "deck-art" -> {
                val close = compose.onNodeWithContentDescription("Close card inspection").fetchSemanticsNode().boundsInRoot
                // Dialog semantics use their own window coordinates, not the activity's viewport.
                val dialog = compose.onNode(isDialog()).fetchSemanticsNode().boundsInRoot
                assertTrue(close.right >= dialog.right - 32f && close.top <= dialog.top + 32f,
                    "Inspection close belongs at the top right")
            }
        }
        compose.onNodeWithContentDescription("Back").assertDoesNotExist()
        val output = File("build/outputs/roborazzi/non-gameplay/$screen-${if (landscape) "landscape" else "portrait"}.png")
        output.parentFile!!.mkdirs()
        captureScreenRoboImage(output.path)
        val image = ImageIO.read(output)
        assertEquals(if (landscape) 892 else 412, image.width)
        assertEquals(if (landscape) 412 else 892, image.height)
    }
}

/** Seeding accepted presentation avoids turning screenshot capture into a transport integration test. */
internal fun Any.seed(state: Any) {
    val field = javaClass.getDeclaredField("state\$delegate").apply { isAccessible = true }
    @Suppress("UNCHECKED_CAST")
    (field.get(this) as MutableState<Any?>).value = state
}

internal class ScreenFixtures(accountSwitcher: Boolean = false) {
    companion object {
        val alex = AccountIdentity("a", "alex")
        val riley = AccountIdentity("b", "riley")
    }
    private val worker = object : AbstractExecutorService() {
        override fun execute(command: Runnable) = Unit // Captures never run requests or polling work.
        override fun shutdown() = Unit
        override fun shutdownNow() = mutableListOf<Runnable>()
        override fun isShutdown() = false
        override fun isTerminated() = false
        override fun awaitTermination(timeout: Long, unit: TimeUnit) = true
    }
    private inline fun <reified T> unused(): T = Proxy.newProxyInstance(T::class.java.classLoader, arrayOf(T::class.java)) { _, method, _ ->
        error("Screenshot attempted ${method.name}")
    } as T
    val auth = AuthController(unused<Accounts>(), unused<SessionStore>(), worker, accountSwitcher = accountSwitcher, post = { it() })
    val reset = ResetController(unused<PasswordResets>(), "https://example.test", worker, {}, { it() })
    val lobby = LobbyController(unused<Lobbies>(), { "fixture" }, worker, { it() }, accountId = { "a" })
    val library = DeckLibraryController(unused<Decks>(), { "fixture" }, worker, { it() })
    val importer = DeckImportController({ error("Screenshot attempted lookup") }, worker, { it() })
    val definitions = listOf("Meadow Sentinel", "Aegis Ward", "Training Land", "Ember Adept", "Quiet Grove").mapIndexed { n, name ->
        CardDefinition(name, listOf(DefinitionFace("face-$n", name, typeLine = "Creature", corner = "1/3"))).let { SubmittedDefinition(DeckJson.digest(it), it) }
    }
    val decks = listOf("Training deck", "Woodland practice", "Starter list").mapIndexed { n, name ->
        DeckSummary("deck-$n", name, "1", "2026-10-20T12:00:00Z", 60, 15)
    }
    fun configure(screen: String) {
        val game = lobby.state.games.last().copy(invitationState = if (screen == "waiting-invite") InvitationState.DECLINED else InvitationState.ACCEPTED)
        val prepared = PreparedDeck("prepared", "deck-0", "1", "Training deck", null, "00000000-0000-4000-8000-000000000001", listOf(
            PreparedEntry(definitions[0].digest, SavedDeckSection.MAINBOARD, "0", 3),
            PreparedEntry(definitions[1].digest, SavedDeckSection.MAINBOARD, "3", 4),
            PreparedEntry(definitions[2].digest, SavedDeckSection.MAINBOARD, "7", 52),
            PreparedEntry(definitions[0].digest, SavedDeckSection.SIDEBOARD, "59", 1),
            PreparedEntry(definitions[3].digest, SavedDeckSection.SIDEBOARD, "60", 3),
            PreparedEntry(definitions[4].digest, SavedDeckSection.SIDEBOARD, "63", 12)))
        val prep = LobbyPreparation(game.id, game.preparationRound, listOf(
            PlayerPreparation("a", ready = screen == "ready-waiting", deck = prepared.takeUnless { screen == "lobby-select-deck" }),
            PlayerPreparation("b", ready = screen == "lobby-select-deck")), definitions, StarterChoice())
        val inLobby = screen in listOf("waiting-invite", "lobby-select-deck", "ready-waiting", "choose-lobby-deck", "prepare-deck", "prepare-deck-faded", "cancel-lobby")
        lobby.seed(lobby.state.copy(busy = screen == "games-loading", contacts = listOf(riley, AccountIdentity("c", "morgan")), query = "sam", searched = true,
            results = listOf(AccountIdentity("d", "sam")), connectedGames = setOf("g1", "g2", "g3"),
            invitations = listOf(game.copy(id = "g3", inviter = riley, recipient = alex, invitationState = InvitationState.PENDING)),
            selected = if (inLobby) game.id else null, detail = if (inLobby) game else null,
            preparation = if (inLobby) prep else null, preparing = screen.startsWith("prepare-deck"),
            transferFeedback = if (screen == "prepare-deck") TransferFeedback(1, "1× Meadow Sentinel → Sideboard") else null,
            picker = if (screen == "choose-lobby-deck") DeckPickerState(game, decks, decks.first(), loading = false) else null,
            draft = if (screen == "create-game") GameNameDraft(riley, "Crackling Mist") else null))
        if (screen == "game-context") lobby.gameContext(lobby.state.games.first())
        if (screen == "delete-game") lobby.requestGameDeletion(lobby.state.games.first())
        if (screen == "cancel-lobby") lobby.requestCancelSetup(game)
        val entries = listOf(SavedDeckEntry(SavedDeckSection.MAINBOARD, definitions[0].digest, 4),
            SavedDeckEntry(SavedDeckSection.MAINBOARD, definitions[3].digest, 4),
            SavedDeckEntry(SavedDeckSection.MAINBOARD, definitions[1].digest, 4),
            SavedDeckEntry(SavedDeckSection.MAINBOARD, definitions[2].digest, 48),
            SavedDeckEntry(SavedDeckSection.SIDEBOARD, definitions[4].digest, 15))
        library.seed(DeckLibraryState(decks = decks, selected = decks.first().id, loaded = true,
            detail = DeckDetail(SavedDeck(decks.first(), entries), definitions.associate { it.digest to it.definition }),
             dialog = when (screen) { "choose-cover" -> DeckDialog.COVER; "delete-deck" -> DeckDialog.DELETE; else -> null },
            draftCover = definitions.first().digest))
        if (screen == "deck-art") library.inspect(definitions.first().digest)
        val text = "Deck\n4 Meadow Sentinel\n4 Ember Adept\n4 Aegis Ward\n48 Training Land\nSideboard\n15 Quiet Grove"
        val result = DeckImportResult(text, entries.map { entry ->
            val definition = definitions.first { it.digest == entry.definition }.definition
            val printing = CatalogPrinting(entry.definition, definition.name, "TST", "1", "English", "", null, false, emptyList(), "{}")
            ResolvedDeckEntry(if (entry.section == SavedDeckSection.MAINBOARD) DeckSection.MAINBOARD else DeckSection.SIDEBOARD,
                entry.quantity, listOf(printing), listOf(1))
        }, if (screen == "import-errors") listOf(ImportIssue(3, "Unknown Card could not be resolved.")) else emptyList(),
            CatalogMetadata("2026-10-20", "fixture", "fixture"))
         importer.seed(DeckImportState(active = true, text = text, name = "Training deck", result = result,
             preview = screen.startsWith("import-save"), target = if (screen == "import-edit") decks.first().id else null))
         if (screen.startsWith("deck-edit")) {
             importer.openEditor(library.state.detail!!)
             val resolved = DeckEditorSource(library.state.detail!!).resolve(importer.state.text) { error("No new cards") }
             importer.seed(importer.state.copy(result = resolved, busy = false))
             if (screen == "deck-edit-cover") importer.chooseCover()
         }
        if (screen == "gameplay-chat") {
            val active = LiveTableFixture.game
            lobby.seed(lobby.state.copy(selected = active.id, detail = active, chatOpen = true, chatLoaded = true, connectedGames = setOf(active.id),
                chat = ChatPresentation(messages = listOf(
                    ChatMessage("m1", active.id, active.playId!!, "b", "Ready when you are.", "1", "2026-10-20T12:00:00Z"),
                    ChatMessage("m2", active.id, active.playId!!, "a", "I'll play my next moves this evening.", "2", "2026-10-20T12:01:00Z"),
                    ChatMessage("m3", active.id, active.playId!!, "b", "Sounds good!", "3", "2026-10-20T12:02:00Z")))))
        }
    }
    init {
        lobby.seed(LobbyState(loaded = true, games = listOf(
            GameSummary("g1", "Azure Spark", "2026-10-21T12:00:00Z", alex, riley, "round", "invite1",
                InvitationState.ACCEPTED, GameLifecycle.ACTIVE, playId = "play"),
            GameSummary("g2", "Crackling Mist", "2026-10-20T12:00:00Z", alex, riley, "round", "invite2",
                InvitationState.ACCEPTED))))
    }
}

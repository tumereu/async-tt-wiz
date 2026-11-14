package org.asyncttwiz.android

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import org.asyncttwiz.android.MatchHistoryFixtures.records
import org.asyncttwiz.protocol.*
import org.asyncttwiz.storage.CatalogCard
import org.asyncttwiz.storage.CatalogCardSearch
import org.asyncttwiz.storage.CatalogMetadata
import org.asyncttwiz.storage.CatalogPrinting
import org.asyncttwiz.storage.DeckSection
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.lang.reflect.Proxy
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.concurrent.AbstractExecutorService
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The desktop layout's screens outside the table (docs/specs/web-client.md §7.1, §7.4, §7.5, §7.7; wireframes D7, D8, D10)
 * at 1440 × 900: the permanent side menu, the wide lists, the two-pane deck editor with its search shown and hidden, Stats
 * in columns and Settings in two columns.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w1440dp-h900dp-land-mdpi")
class DesktopScreensScreenshotTest {
    @get:Rule val compose = createComposeRule()
    private val fixtures = ScreenFixtures()
    private val pending = object : AbstractExecutorService() {
        override fun execute(command: Runnable) = Unit // Captures never run requests.
        override fun shutdown() = Unit
        override fun shutdownNow() = mutableListOf<Runnable>()
        override fun isShutdown() = false
        override fun isTerminated() = false
        override fun awaitTermination(timeout: Long, unit: TimeUnit) = true
    }
    private inline fun <reified T> unused(): T = Proxy.newProxyInstance(T::class.java.classLoader, arrayOf(T::class.java)) { _, method, _ ->
        error("Screenshot attempted ${method.name}")
    } as T
    private val pods = PodController(unused<Pods>(), unused<Cubes>(), { "token" }, pending, { it() })

    @Before fun pinClock() {
        AppClock.pinned = Clock.fixed(Instant.parse("2026-11-08T20:00:00Z"), ZoneOffset.UTC)
        ApplicationProvider.getApplicationContext<android.content.Context>().getSharedPreferences("deck-view", 0).edit().clear().commit()
    }
    @After fun close() { AppClock.pinned = null; pods.close() }

    private fun signedIn(destination: ShellDestination) =
        fixtures.auth.seed(AuthState(restoring = false, account = ScreenFixtures.alex, destination = destination))

    @Composable
    private fun Desktop(content: @Composable () -> Unit) = ScreenTheme {
        CompositionLocalProvider(LocalAppLayout provides AppLayout.Desktop) {
            Surface(Modifier.fillMaxSize(), color = ScreenBackground) { NoRipples { content() } }
        }
    }

    /** The side menu's width as drawn: WEB-037's 280. */
    private fun assertSideMenu() {
        val menu = compose.onNodeWithTag("navigation-Games").fetchSemanticsNode().boundsInRoot
        assertTrue(menu.right <= 280f + 1f, "the side menu's rows inside its 280: $menu")
    }

    // WEB-037, WEB-038, LOB-018, LOB-023, LOB-024 (D7): the Games screen with the side menu always open and the list at its
    // readable width, each row one line of columns
    @Test fun gamesList() {
        fixtures.configure("play")
        signedIn(ShellDestination.GAMES)
        compose.setContent { Desktop { AuthenticatedShell(fixtures.auth, true, lobby = fixtures.lobby, pods = pods) } }
        compose.waitForIdle()
        assertSideMenu()
        // The list's row, not the side menu's
        val row = compose.onAllNodesWithText("Azure Spark", useUnmergedTree = true).fetchSemanticsNodes()
            .map { it.boundsInRoot }.single { it.left > 280f }
        val line = compose.onNodeWithText("vs riley · In play", substring = true, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        assertEquals(row.left, line.left, 1f, "the vs line under the name, beside the avatar")
        val title = compose.onNodeWithTag("destination-title").fetchSemanticsNode().boundsInRoot
        assertTrue(title.right <= 280f + 980f + 1f, "the list keeps its readable width: $title")
        captureShot("$SCREENSHOTS/desktop/games.png",
            "Alex opened Games on the desktop layout: the side menu stays open at the left, and the games list keeps a readable width, each row the avatar, the name over its vs line, the move chip or Accept and Decline, and the chevron.")
    }

    // WEB-038, LOB-028 (D7): Contacts at the list's readable width
    @Test fun contacts() {
        fixtures.configure("contacts")
        signedIn(ShellDestination.CONTACTS)
        compose.setContent { Desktop { AuthenticatedShell(fixtures.auth, true, lobby = fixtures.lobby, pods = pods) } }
        compose.waitForIdle()
        assertSideMenu()
        captureShot("$SCREENSHOTS/desktop/contacts.png",
            "Alex opened Contacts on the desktop layout: the side menu at the left and riley and morgan listed at the list's readable width.")
    }

    // WEB-038, DRF-012 (D7): the Pods list at the list's readable width
    @Test fun podsList() {
        pods.seed(PodState(loaded = true, pods = listOf(
            PodSummary("p1", "Friday cube", PodPhase.DRAFTING, ScreenFixtures.alex, listOf(ScreenFixtures.alex, ScreenFixtures.riley),
                PodAttention.YOUR_PICK, 2, "2026-11-08T12:00:00Z"),
            PodSummary("p2", "Ravnica night", PodPhase.SETUP, ScreenFixtures.riley, listOf(ScreenFixtures.alex, ScreenFixtures.riley),
                PodAttention.NONE, 0, "2026-11-07T12:00:00Z"))))
        signedIn(ShellDestination.PODS)
        compose.setContent { Desktop { AuthenticatedShell(fixtures.auth, true, lobby = fixtures.lobby, pods = pods) } }
        compose.waitForIdle()
        assertSideMenu()
        captureShot("$SCREENSHOTS/desktop/pods.png",
            "Alex opened Pods on the desktop layout: the side menu at the left and his two pods listed at the list's readable width.")
    }

    // WEB-038, LOB-055: Manage decks' saved decks as tiles in columns
    @Test fun decksGrid() {
        fixtures.configure("decks")
        fixtures.library.seed(fixtures.library.state.copy(selected = null, detail = null,
            decks = fixtures.decks + fixtures.decks.mapIndexed { n, deck -> deck.copy(id = "more-$n", name = "${deck.name} II") }))
        signedIn(ShellDestination.DECKS)
        compose.setContent { Desktop { AuthenticatedShell(fixtures.auth, true, lobby = fixtures.lobby, decks = fixtures.library) } }
        compose.waitForIdle()
        val first = compose.onNodeWithText("Training deck").fetchSemanticsNode().boundsInRoot
        val second = compose.onNodeWithText("Woodland practice").fetchSemanticsNode().boundsInRoot
        assertEquals(first.top, second.top, 1f, "the decks lie side by side")
        captureShot("$SCREENSHOTS/desktop/decks.png",
            "Alex opened Manage decks on the desktop layout: his six saved decks fill the width as tiles in three columns.")
    }

    private val metadata = CatalogMetadata("2026-10-30", "fixture", "fixture")
    private fun printing(name: String) = CatalogPrinting("p-$name", name, "NEW", "9", "English", "", null, false, emptyList(),
        "{\"type\":\"Creature — Bear\"}")

    /** The saved Training deck open in the editor, its search reading [names] for any query. */
    private fun editor(vararg names: String): DeckImportController {
        fixtures.configure("deck-detail")
        val detail = fixtures.library.state.detail!!
        return DeckImportController({ error("No lookup expected") }, Immediate, { it() }).also { editor ->
            editor.cardRecords = testCardRecords(fixtures.definitions.map { card -> card.definition }, set = "")
            editor.openEditor(detail)
            editor.search.source = { _, _ ->
                CatalogCardSearch.Page("g", metadata, names.map { CatalogCard("o-$it", it, listOf(printing(it))) }, null, names.size)
            }
        }
    }

    // WEB-039, WEB-040, LOB-059, LOB-105, LOB-121 (D8): the two-pane editor: the list left, the search right; a click on a
    // result adds a copy to the chosen section; Hide search folds it into the rail, the list taking the width, and the rail
    // opens it again as it was left
    @Test fun deckEditorTwoPanes() {
        val editor = editor("Moss Stalker", "Canopy Drake", "Briar Lynx", "Verdant Scout", "Seedling Swarm")
        signedIn(ShellDestination.DECKS)
        compose.setContent { Desktop { AuthenticatedShell(fixtures.auth, true, deckImport = editor) } }
        compose.waitForIdle()
        compose.onNodeWithTag("card-search-pane").assertIsDisplayed()
        compose.onNodeWithTag("card-list-cards").assertIsDisplayed()
        compose.onNodeWithTag("card-search-field").performTextInput("t:creature")
        compose.mainClock.advanceTimeBy(600)
        compose.waitForIdle()
        compose.onNodeWithTag("search-result-Moss Stalker").performClick()
        compose.runOnIdle { assertEquals(1, CardListEdits.count(editor.entries!!, "Moss Stalker", DeckSection.MAINBOARD)) }
        compose.onNodeWithText("61 main · 15 side").assertIsDisplayed()
        val pane = compose.onNodeWithTag("card-search-pane").fetchSemanticsNode().boundsInRoot
        assertEquals(1440f, pane.right, 1f, "the search at the right edge")
        captureShot("$SCREENSHOTS/desktop/deck-editor.png",
            "Alex edits his Training deck on the desktop layout: the list on the left, and on the right the search for t:creature with Add to on Main, where a click on Moss Stalker added a copy (its green square shows 1, the counts read 61 main).")
        compose.onNodeWithTag("hide-search").performClick()
        compose.onNodeWithTag("card-search-pane").assertDoesNotExist()
        val rail = compose.onNodeWithTag("search-rail").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        assertTrue(rail.width < 60f && rail.right > 1439f, "a slim rail at the right edge: $rail")
        captureShot("$SCREENSHOTS/desktop/deck-editor-search-hidden.png",
            "After Hide search the editor's search is folded into the slim Search rail at the right edge and the deck list takes the full width.")
        compose.onNodeWithTag("search-rail").performClick()
        compose.onNodeWithTag("card-search-field").assertTextContains("t:creature")
        compose.onNodeWithTag("search-result-Moss Stalker").assertIsDisplayed()
    }

    // WEB-049, LOB-121: a search result dragged onto the Mainboard header goes into Main though Add to is on Side; dropped back on
    // the search pane it adds nothing
    @Test fun aSearchResultDraggedIntoASection() {
        val editor = editor("Moss Stalker")
        signedIn(ShellDestination.DECKS)
        compose.setContent { Desktop { AuthenticatedShell(fixtures.auth, true, deckImport = editor) } }
        compose.waitForIdle()
        compose.onNodeWithTag("card-search-field").performTextInput("t:creature")
        compose.mainClock.advanceTimeBy(600)
        compose.waitForIdle()
        compose.onNodeWithTag("destination-side").performClick()
        val result = compose.onNodeWithTag("search-result-Moss Stalker").fetchSemanticsNode().boundsInRoot
        val header = compose.onNodeWithText("Mainboard · 60").fetchSemanticsNode().boundsInRoot
        val onHeader = Offset(header.center.x - result.left, header.center.y - result.top)
        compose.onNodeWithTag("search-result-Moss Stalker").performMouseInput { moveTo(center); press(); moveTo(center + Offset(-40f, 0f)) }
        compose.onNodeWithTag("search-result-Moss Stalker").performMouseInput { moveTo(onHeader) }
        compose.onNodeWithContentDescription("Moss Stalker: add to Mainboard").assertIsDisplayed()
        compose.onNodeWithTag("search-result-Moss Stalker").performMouseInput { release() }
        compose.runOnIdle {
            assertEquals(1, CardListEdits.count(editor.entries!!, "Moss Stalker", DeckSection.MAINBOARD))
            assertEquals(0, CardListEdits.count(editor.entries!!, "Moss Stalker", DeckSection.SIDEBOARD))
        }
        compose.onNodeWithTag("search-drag").assertDoesNotExist()
        compose.onNodeWithTag("search-result-Moss Stalker").performMouseInput {
            moveTo(center); press(); moveTo(center + Offset(0f, 60f)); release()
        }
        compose.runOnIdle { assertEquals(1, DeckSection.entries.sumOf { CardListEdits.count(editor.entries!!, "Moss Stalker", it) }) }
    }

    // WEB-047, LOB-026 (D7): a right-click on a game row offers what holding it does, and never opens the game
    @Test fun aRightClickOnAGameRowOffersItsActions() {
        fixtures.configure("play")
        signedIn(ShellDestination.GAMES)
        compose.setContent { Desktop { AuthenticatedShell(fixtures.auth, true, lobby = fixtures.lobby, pods = pods) } }
        compose.waitForIdle()
        val game = fixtures.lobby.state.games.first { it.gameName == "Azure Spark" }
        val offered = fixtures.lobby.offersGameDeletion(game) || fixtures.lobby.offersSetupCancellation(game)
        compose.onNodeWithTag("quick-continue-${game.id}").performMouseInput {
            moveTo(center); press(androidx.compose.ui.test.MouseButton.Secondary); release(androidx.compose.ui.test.MouseButton.Secondary)
        }
        compose.waitForIdle()
        assertEquals(null, fixtures.lobby.state.selected, "the game not opened")
        assertEquals(offered, fixtures.lobby.state.gameContext != null || fixtures.lobby.setupCancellation.context != null,
            "the row's hold actions offered")
    }

    // WEB-041: Ctrl+S saves the edited deck as the Save button does; while a text field has the keyboard it does nothing
    @Test fun ctrlSSavesTheDeck() {
        val editor = editor()
        val plans = mutableListOf<DeckSave>()
        editor.connectSave(action = { { plan -> plans += plan; error("Not saved in this test") } }, saved = {})
        signedIn(ShellDestination.DECKS)
        compose.setContent { Desktop { AuthenticatedShell(fixtures.auth, true, deckImport = editor) } }
        compose.waitForIdle()
        compose.onNodeWithText("Ctrl+S").assertIsDisplayed()
        compose.runOnIdle {
            assertTrue(editor.canSave, "the opened deck can be saved")
            assertTrue(!DesktopKeyboard.dispatch(DesktopKey("s", true, 1, ctrl = true, textField = true)), "typing: not taken")
        }
        compose.waitForIdle()
        assertTrue(plans.isEmpty(), "nothing saved while typing")
        compose.runOnIdle { assertTrue(DesktopKeyboard.dispatch(DesktopKey("s", true, 2, ctrl = true)), "Ctrl+S taken") }
        compose.waitUntil(5_000) { plans.isNotEmpty() }
        assertEquals("Training deck", plans.single().name)
    }

    // WEB-042, LOB-130, LOB-132: the Stats screen's graphs in columns
    @Test fun statsGraphs() {
        fixtures.lobby.history.stats = HistoryList(records)
        fixtures.lobby.history.choice = StatsChoice(graphs = true)
        signedIn(ShellDestination.STATS)
        compose.setContent { Desktop { AuthenticatedShell(fixtures.auth, true, lobby = fixtures.lobby) } }
        compose.waitForIdle()
        val first = compose.onNodeWithTag("graph-Record over time").fetchSemanticsNode().boundsInRoot
        val second = compose.onNodeWithTag("graph-Win % on the play vs on the draw").fetchSemanticsNode().boundsInRoot
        assertEquals(first.top, second.top, 1f, "the graphs lie side by side")
        captureShot("$SCREENSHOTS/desktop/stats-graphs.png",
            "Alex opened Stats on the desktop layout and chose Graphs: the six tiles across the top and the graphs under them in two columns.")
    }

    private class FakeSettings : AccountSettingsSource {
        override suspend fun cached() = AccountSettings()
        override suspend fun refresh() = AccountSettings()
        override suspend fun set(settings: AccountSettings) = Unit
    }

    // WEB-043, WEB-035 (D10): Settings' groups as cards in two columns at Settings' readable width, beside the side menu
    @Test fun settingsRows() {
        signedIn(ShellDestination.SETTINGS)
        compose.setContent { Desktop {
            Row(Modifier.fillMaxSize()) {
                Surface(Modifier.width(DesktopScreens.SideMenuWidth).fillMaxHeight(), color = Color.White) {
                    Navigation(fixtures.auth, false, fixtures.lobby)
                }
                androidx.compose.foundation.layout.Box(Modifier.weight(1f)) {
                    Column(Modifier.desktopReadable(DesktopScreens.SettingsMaxWidth).fillMaxSize()) {
                        ScreenHeader("Settings", null)
                        SettingsContent(null, "a", {}, fixtures.auth, FakeSettings(),
                            versionText = "Version 0.2.0 (1) debug · commit fixture")
                    }
                }
            }
        } }
        compose.waitForIdle()
        compose.onNodeWithTag("desktop-settings").assertIsDisplayed()
        val table = compose.onNodeWithText("Timeline playback").fetchSemanticsNode().boundsInRoot
        val keyboard = compose.onNodeWithText("Keyboard").fetchSemanticsNode().boundsInRoot
        assertTrue(keyboard.left > table.right, "Keyboard right of Timeline playback: $table $keyboard")
        captureShot("$SCREENSHOTS/desktop/settings.png",
            "Alex opened Settings on the desktop layout: its groups as cards in two columns, Profile, Games, Table and the rest on the left, About & account and Keyboard on the right.")
    }
}

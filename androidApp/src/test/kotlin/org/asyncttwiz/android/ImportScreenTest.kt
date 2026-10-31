package org.asyncttwiz.android

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelStore
import androidx.test.core.app.ApplicationProvider
import java.util.concurrent.AbstractExecutorService
import java.util.concurrent.TimeUnit
import org.asyncttwiz.protocol.*
import org.asyncttwiz.storage.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.*

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w412dp-h892dp-port-mdpi")
class ImportScreenTest {
    @get:Rule val compose = createComposeRule()
    private class Worker : AbstractExecutorService() {
        val queue = ArrayDeque<Runnable>()
        override fun execute(command: Runnable) { queue += command }
        fun drain() { while (queue.isNotEmpty()) queue.removeFirst().run() }
        override fun shutdown() = Unit
        override fun shutdownNow() = mutableListOf<Runnable>()
        override fun isShutdown() = false
        override fun isTerminated() = false
        override fun awaitTermination(timeout: Long, unit: TimeUnit) = true
    }
    private fun clipboard(value: String) {
        ApplicationProvider.getApplicationContext<Context>().getSystemService(ClipboardManager::class.java)
            .setPrimaryClip(ClipData.newPlainText("Import", value))
    }
    private val printing = CatalogPrinting("rune-bear", "Rune Bear", "TST", "1", "English", "", null, false, emptyList(),
        """{"name":"Rune Bear","type":"Creature"}""")
    private fun resolved(text: String) = DeckImportResult(text, listOf(
        ResolvedDeckEntry(DeckSection.MAINBOARD, 2, listOf(printing), listOf(2)),
        ResolvedDeckEntry(DeckSection.SIDEBOARD, 1, listOf(printing), listOf(4))), emptyList(), null)

    // LOB-061
    @Test fun deckLinkDialogOpensFromTheTopBarAndAutopastesOnlyOnceWithoutFetching() {
        val worker = Worker()
        val controller = DeckImportController({ error("No lookup expected") }, worker) { it() }
        controller.connectUrl({ error("No resolution expected") }, { error("No automatic fetch") })
        controller.open()
        clipboard("https://archidekt.com/decks/123")
        try {
            compose.setContent { ScreenTheme { DeckImportScreen(controller, false, null, null) } }
            compose.onNodeWithText("Link").assertDoesNotExist()
            compose.onNodeWithText("Paste list").assertDoesNotExist()
            compose.onNodeWithTag("deck-name").assertExists()
            compose.onNodeWithContentDescription("Choose deck cover").assertDoesNotExist() // Nothing resolved yet.
            compose.onNodeWithTag("deck-list").assertExists()
            compose.onNodeWithTag("deck-url").assertDoesNotExist()
            compose.onNodeWithTag("open-import-link").performClick()
            compose.onNodeWithTag("deck-url").assertTextContains("https://archidekt.com/decks/123")
            compose.onNodeWithTag("deck-url").performTextReplacement("archidekt:456")
            clipboard("https://archidekt.com/decks/789")
            compose.onNodeWithText("Cancel").performClick()
            compose.onNodeWithTag("deck-url").assertDoesNotExist()
            compose.onNodeWithTag("open-import-link").performClick()
            compose.onNodeWithTag("deck-url").assertTextContains("archidekt:456")
            assertFalse(controller.state.busy)
            assertTrue(worker.queue.isEmpty())
        } finally { controller.close() }
    }

    // LOB-061
    @Test fun deckPasteIconImportsAtOnceTypedLinksWaitForImportAndTheResultIsAnEditablePasteList() {
        val worker = Worker(); val fetched = mutableListOf<String>()
        val controller = DeckImportController({ resolved(it) }, worker) { it() }
        controller.connectUrl({ resolved(it.text) }, { location ->
            if (location.id == "999") error("403")
            fetched += location.id
            ImportedDeck("Site deck ${location.id}", DeckTextParser.parse("Deck\n2 Rune Bear\nSideboard\n1 Rune Bear"))
        })
        controller.open()
        clipboard("not a link")
        try {
            compose.setContent { ScreenTheme { DeckImportScreen(controller, false, null, null) } }
            compose.onNodeWithTag("open-import-link").performClick()
            assertEquals("", controller.state.url) // Not a link: nothing is prefilled.
            compose.onNodeWithTag("import-link").assertIsNotEnabled()
            compose.onNodeWithTag("deck-url").performTextInput("archidekt:999")
            compose.onNodeWithTag("import-link").performClick()
            compose.runOnIdle { worker.drain() }
            compose.onNodeWithText("Archidekt could not be loaded. Paste the list instead.").assertIsDisplayed()
            compose.onNodeWithTag("deck-url").performTextReplacement("archidekt:456")
            compose.runOnIdle { worker.drain() }
            assertTrue(fetched.isEmpty())
            compose.onNodeWithTag("import-link").performClick()
            compose.runOnIdle { worker.drain() }
            assertEquals(listOf("456"), fetched)
            compose.onNodeWithTag("deck-url").assertDoesNotExist()
            compose.onNodeWithText("2 main · 1 side").assertIsDisplayed()
            compose.onNodeWithTag("deck-name").assertTextEquals("Site deck 456")
            compose.onNodeWithTag("deck-list").assertTextContains("2 Rune Bear", substring = true)
            compose.onNodeWithContentDescription("Choose deck cover").assertIsDisplayed()
            compose.onNodeWithTag("save-deck").assertIsEnabled()

            // A typed name stays; an existing list is replaced by the explicit import.
            compose.onNodeWithTag("deck-name").performTextReplacement("My bears")
            compose.onNodeWithTag("deck-list").performTextReplacement("1 Something else")
            clipboard("https://archidekt.com/decks/789")
            compose.onNodeWithTag("open-import-link").performClick()
            compose.onNodeWithContentDescription("Paste deck link").performClick()
            compose.runOnIdle { worker.drain() }
            assertEquals(listOf("456", "789"), fetched)
            compose.onNodeWithTag("deck-url").assertDoesNotExist()
            compose.onNodeWithTag("deck-name").assertTextEquals("My bears")
            compose.onNodeWithTag("deck-list").assertTextContains("2 Rune Bear", substring = true)
            assertEquals("https://archidekt.com/decks/789", controller.state.url)
        } finally { controller.close() }
    }

    // LOB-061
    @Test @Config(qualifiers = "w360dp-h740dp-port-mdpi")
    fun narrowPortraitShowsTheLinkActionAsAnIconWithADescription() {
        val controller = DeckImportController({ resolved(it) }, Worker()) { it() }
        controller.openExisting(DeckSummary("deck", "Bears", "r1", "2026-10-28T00:00:00Z", 60, 0))
        clipboard("")
        try {
            compose.setContent { ScreenTheme { DeckImportScreen(controller, false, {}, null) } }
            compose.onNodeWithText(IMPORT_FROM_LINK).assertDoesNotExist()
            compose.onNodeWithText("Import decklist").assertIsDisplayed()
            compose.onNodeWithContentDescription(IMPORT_FROM_LINK).performClick()
            compose.onNodeWithTag("deck-url").assertIsDisplayed()
        } finally { controller.close() }
    }

    @Test @Config(qualifiers = "w892dp-h412dp-land-mdpi")
    fun landscapeShowsALabelledLinkActionAndDialogButtonsSitBottomRight() {
        val controller = DeckImportController({ resolved(it) }, Worker()) { it() }
        controller.open()
        clipboard("")
        try {
            compose.setContent { ScreenTheme { DeckImportScreen(controller, true, null, null) } }
            compose.onNodeWithText(IMPORT_FROM_LINK).assertIsDisplayed().performClick()
            compose.onNodeWithTag("deck-url").assertIsDisplayed()
            val field = compose.onNodeWithTag("deck-url").getUnclippedBoundsInRoot()
            val cancel = compose.onNodeWithTag("cancel-link").getUnclippedBoundsInRoot()
            val import = compose.onNodeWithTag("import-link").getUnclippedBoundsInRoot()
            assertTrue(cancel.top >= field.bottom && import.top >= field.bottom)
            assertTrue(import.left >= cancel.right, "Import is the rightmost action")
            assertTrue(import.right >= field.right - 1.dp, "Actions align to the right edge")
            compose.onNodeWithTag("deck-list").assertExists()
            compose.onNodeWithTag("save-deck").assertExists()
        } finally { controller.close() }
    }

    // LOB-059, LOB-060
    @Test fun deckSaveIsEnabledOnlyForANamedResolvedListAndPasteIconOnlyFillsTheList() {
        val worker = Worker()
        val controller = DeckImportController({ text ->
            if (text.contains("Unknown")) DeckImportResult(text, emptyList(), listOf(ImportIssue(1, "Unknown could not be resolved.")), null)
            else resolved(text)
        }, worker) { it() }
        controller.open()
        clipboard("Deck\n2 Rune Bear\nSideboard\n1 Rune Bear")
        try {
            compose.setContent { ScreenTheme { DeckImportScreen(controller, false, null, null) } }
            compose.onNodeWithTag("save-deck").assertIsNotEnabled()
            compose.onNodeWithContentDescription("Paste from clipboard").performClick()
            compose.onNodeWithTag("deck-list").assertTextContains("2 Rune Bear", substring = true)
            assertTrue(worker.queue.isEmpty())
            compose.mainClock.advanceTimeBy(500)
            compose.runOnIdle { worker.drain() }
            compose.onNodeWithText("2 main · 1 side").assertIsDisplayed()
            compose.onNodeWithTag("save-deck").assertIsNotEnabled() // No name yet.
            compose.onNodeWithTag("deck-name").performTextInput("Bears")
            compose.onNodeWithTag("save-deck").assertIsEnabled()
            compose.onNodeWithTag("deck-list").performTextReplacement("1 Unknown")
            compose.mainClock.advanceTimeBy(500)
            compose.runOnIdle { worker.drain() }
            compose.onNodeWithText("Line 1: Unknown could not be resolved.").assertIsDisplayed()
            compose.onNodeWithTag("save-deck").assertIsNotEnabled()
        } finally { controller.close() }
    }

    @Test fun importScreenHasNoCatalogSectionAndOnlyTheLinkTopBarAction() {
        val models = ViewModelStore()
        val catalog = CatalogViewModel(ApplicationProvider.getApplicationContext()).also { models.put("catalog", it) }
        catalog.seed(CatalogUiState(loaded = true, installed = CatalogMetadata("2026-10-20", "test"),
            available = CatalogMetadata("2026-10-26", "test")))
        val controller = DeckImportController({ error("No lookup expected") }, Worker()) { it() }
        controller.open()
        try {
            compose.setContent { ScreenTheme { DeckImportScreen(controller, false, {}, catalog) } }
            compose.onNodeWithText("Card records").assertDoesNotExist()
            compose.onNodeWithText("Check for updates").assertDoesNotExist()
            compose.onNodeWithText("Refresh full catalog").assertDoesNotExist()
            compose.onNodeWithContentDescription("Save deck").assertDoesNotExist()
            compose.onAllNodesWithText("Save").assertCountEquals(1)
            // The only top-bar action is the link import.
            compose.onNodeWithText(IMPORT_FROM_LINK).assertIsDisplayed()
        } finally { controller.close(); models.clear() }
    }

    // DRF-003
    @Test fun cubeEditorIsThePasteListAndTheLinkDialogDoesNotImportClipboardAutomatically() {
        val worker = Worker()
        val controller = CubeController(DeckApi("https://invalid.example"), { null }, { _, _ -> error("No lookup expected") },
            { error("No resolution expected") }, worker, { it() }, { error("No automatic fetch") })
        controller.newCube()
        clipboard("https://cubecobra.com/cube/list/runes")
        try {
            compose.setContent { ScreenTheme { CubeScreen(controller, null) } }
            compose.onNodeWithText("Link").assertDoesNotExist()
            compose.onNodeWithTag("cube-name").assertExists()
            compose.onNodeWithTag("cube-list").assertExists()
            compose.onNodeWithTag("cube-url").assertDoesNotExist()
            compose.onNodeWithTag("open-import-link").performClick()
            compose.onNodeWithTag("cube-url").assertTextContains("https://cubecobra.com/cube/list/runes")
            clipboard("another-cube")
            compose.onNodeWithText("Cancel").performClick()
            compose.onNodeWithTag("cube-url").assertDoesNotExist()
            compose.onNodeWithTag("open-import-link").performClick()
            compose.onNodeWithTag("cube-url").assertTextContains("https://cubecobra.com/cube/list/runes")
            assertFalse(controller.state.busy)
            assertTrue(worker.queue.isEmpty())
        } finally { controller.close(); worker.drain() }
    }

    // DRF-008, DRF-009
    @Test fun cubePasteIconImportsAtOnceWhileTypedLinksWaitForImportAndSaveNeedsAName() {
        val worker = Worker(); val fetched = mutableListOf<String>()
        val result = DeckImportResult("1 Rune Bear", listOf(ResolvedDeckEntry(DeckSection.MAINBOARD, 1, listOf(printing), listOf(1))),
            emptyList(), null)
        val controller = CubeController(DeckApi("https://invalid.example"), { "token" }, { _, _ -> result },
            { result }, worker, { it() }, { id ->
                if (id == "missing") throw CubeCobraFailure("Cube Cobra cube missing was not found.")
                fetched += id; DeckTextParser.parse("1 Rune Bear")
            })
        controller.newCube()
        clipboard("nothing useful")
        try {
            compose.setContent { ScreenTheme { CubeScreen(controller, null) } }
            compose.onNodeWithTag("open-import-link").performClick()
            compose.onNodeWithTag("cube-url").performTextInput("missing")
            compose.onNodeWithTag("import-link").performClick()
            compose.runOnIdle { worker.drain() }
            compose.onNodeWithText("Cube Cobra cube missing was not found.").assertIsDisplayed()
            compose.onNodeWithTag("cube-url").performTextReplacement("typed")
            compose.runOnIdle { worker.drain() }
            assertTrue(fetched.isEmpty())
            compose.onNodeWithTag("import-link").performClick()
            compose.runOnIdle { worker.drain() }
            assertEquals(listOf("typed"), fetched)
            compose.onNodeWithTag("cube-url").assertDoesNotExist()
            compose.onNodeWithText("1 card").assertIsDisplayed()
            compose.onNodeWithTag("cube-name").assertTextEquals("typed")
            compose.onNodeWithTag("cube-list").assertTextContains("1 Rune Bear", substring = true)
            compose.onNodeWithTag("save-cube").assertIsEnabled()

            clipboard("Check out my cube https://cubecobra.com/cube/list/runes?view=table")
            compose.onNodeWithTag("open-import-link").performClick()
            compose.onNodeWithContentDescription("Paste Cube Cobra link").performClick()
            compose.runOnIdle { worker.drain() }
            assertEquals(listOf("typed", "runes"), fetched)
            compose.onNodeWithTag("cube-url").assertDoesNotExist()
            assertTrue(controller.state.cobraInput.contains("cubecobra.com/cube/list/runes"))
            compose.onNodeWithTag("cube-name").assertTextEquals("typed")
            compose.onNodeWithTag("cube-name").performTextReplacement("")
            compose.onNodeWithTag("save-cube").assertIsNotEnabled()
        } finally { controller.close(); worker.drain() }
    }
}

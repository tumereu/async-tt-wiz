package org.asyncttwiz.android

import android.content.Context
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import org.asyncttwiz.protocol.*
import org.asyncttwiz.storage.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.lang.reflect.Proxy
import java.util.concurrent.AbstractExecutorService
import java.util.concurrent.TimeUnit
import kotlin.test.*

internal object Immediate : AbstractExecutorService() {
    override fun execute(command: Runnable) = command.run()
    override fun shutdown() = Unit
    override fun shutdownNow() = mutableListOf<Runnable>()
    override fun isShutdown() = false
    override fun isTerminated() = false
    override fun awaitTermination(timeout: Long, unit: TimeUnit) = true
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w412dp-h892dp-port-mdpi")
class CardListEditorUiTest {
    @get:Rule val compose = createComposeRule()

    @Before fun clearPreferences() {
        ApplicationProvider.getApplicationContext<Context>().getSharedPreferences("deck-view", 0).edit().clear().commit()
    }

    private val fixture = ScreenFixtures().apply { configure("deck-detail") }
    private val detail get() = fixture.library.state.detail!!
    private fun digest(name: String) = detail.definitions.entries.first { it.value.name == name }.key
    private fun editor(): DeckImportController = DeckImportController({ error("No lookup expected") }, Immediate, { it() }).also {
        it.cardRecords = testCardRecords(fixture.definitions.map { card -> card.definition }, set = "")
        it.openEditor(detail)
    }
    private fun count(editor: CardListEditor, name: String, section: DeckSection) = CardListEdits.count(editor.entries!!, name, section)

    private val metadata = CatalogMetadata("2026-10-30", "fixture", "fixture")
    private fun printing(name: String) = CatalogPrinting("p-$name", name, "NEW", "9", "English", "", null, false, emptyList(),
        "{\"type\":\"Creature — Bear\"}")
    private val searched = mutableListOf<String>()
    private fun connectSearch(editor: DeckImportController, vararg names: String) {
        editor.search.source = { query, _ ->
            searched += query
            CatalogCardSearch.Page("g", metadata, names.map { CatalogCard("o-$it", it, listOf(printing(it))) }, null, names.size)
        }
    }

    // LOB-105, LOB-107, LOB-059, LOB-064, SYS-083
    @Test fun cardsTabSwipesBetweenSectionsAndTheMenuEditsAndMovesCopies() {
        val editor = editor()
        lateinit var back: () -> Unit
        compose.setContent {
            val dispatcher = LocalOnBackPressedDispatcherOwner.current!!.onBackPressedDispatcher
            SideEffect { back = dispatcher::onBackPressed }
            ScreenTheme { Surface(Modifier.fillMaxSize()) {
                if (editor.state.active) DeckImportScreen(editor, false, null, null)
            } }
        }
        compose.onNodeWithTag("card-list-cards").assertIsDisplayed()
        compose.onNodeWithText("60 main · 15 side").assertIsDisplayed()
        compose.onNodeWithText("Add cards").assertDoesNotExist()
        val sentinel = "editor-card-MAINBOARD/${digest("Meadow Sentinel")}"
        // Left one section down: Main to Side
        compose.onNodeWithTag(sentinel).performTouchInput { swipeLeft() }
        compose.runOnIdle {
            assertEquals(3, count(editor, "Meadow Sentinel", DeckSection.MAINBOARD))
            assertEquals(1, count(editor, "Meadow Sentinel", DeckSection.SIDEBOARD))
            assertTrue(editor.state.result!!.resolved, "A Cards edit stays resolved without a new lookup")
            assertTrue("Sideboard\n15 Quiet Grove\n1 Meadow Sentinel" in editor.state.text, editor.state.text)
        }
        compose.onNodeWithText("59 main · 16 side").assertIsDisplayed()
        // Left from Side down to Maybe; left from Maybe wraps to Main.
        compose.onNodeWithTag("card-list-cards").performScrollToNode(hasTestTag("editor-card-SIDEBOARD/${digest("Meadow Sentinel")}"))
        compose.onNodeWithTag("editor-card-SIDEBOARD/${digest("Meadow Sentinel")}").performTouchInput { swipeLeft() }
        compose.runOnIdle { assertEquals(1, count(editor, "Meadow Sentinel", DeckSection.MAYBEBOARD)) }
        compose.onNodeWithText("59 main · 15 side · 1 maybe").assertIsDisplayed()
        compose.onNodeWithTag("card-list-cards").performScrollToNode(hasText("Maybeboard · 1"))
        compose.onNodeWithTag("editor-card-MAYBEBOARD/${digest("Meadow Sentinel")}").performTouchInput { swipeLeft() }
        compose.runOnIdle { assertEquals(4, count(editor, "Meadow Sentinel", DeckSection.MAINBOARD)) }
        // Right one section up: Main wraps to Maybe, from Maybe up to Side, from Side up to Main
        compose.onNodeWithTag("card-list-cards").performScrollToIndex(0)
        compose.onNodeWithTag(sentinel).performTouchInput { swipeRight() }
        compose.runOnIdle { assertEquals(1, count(editor, "Meadow Sentinel", DeckSection.MAYBEBOARD)) }
        compose.onNodeWithTag("card-list-cards").performScrollToNode(hasText("Maybeboard · 1"))
        compose.onNodeWithTag("editor-card-MAYBEBOARD/${digest("Meadow Sentinel")}").performTouchInput { swipeRight() }
        compose.runOnIdle { assertEquals(1, count(editor, "Meadow Sentinel", DeckSection.SIDEBOARD)) }
        compose.onNodeWithTag("card-list-cards").performScrollToNode(hasTestTag("editor-card-SIDEBOARD/${digest("Meadow Sentinel")}"))
        compose.onNodeWithTag("editor-card-SIDEBOARD/${digest("Meadow Sentinel")}").performTouchInput { swipeRight() }
        compose.runOnIdle { assertEquals(4, count(editor, "Meadow Sentinel", DeckSection.MAINBOARD)) }

        compose.onNodeWithTag("card-list-cards").performScrollToIndex(0)
        compose.onNodeWithTag(sentinel).performTouchInput { click() }
        compose.onNodeWithText("+1").performClick()
        compose.runOnIdle { assertEquals(5, count(editor, "Meadow Sentinel", DeckSection.MAINBOARD)) }
        compose.onNodeWithTag(sentinel).performTouchInput { click() }
        compose.onNodeWithText("−1").performClick()
        compose.runOnIdle { assertEquals(4, count(editor, "Meadow Sentinel", DeckSection.MAINBOARD)) }
        compose.onNodeWithTag(sentinel).performTouchInput { click() }
        compose.onNodeWithText("Move to…").performClick()
        compose.onNodeWithText("All 4 to Sideboard").assertIsDisplayed()
        compose.onNodeWithText("All 4 to Maybeboard").performClick()
        compose.runOnIdle {
            assertEquals(0, count(editor, "Meadow Sentinel", DeckSection.MAINBOARD))
            assertEquals(4, count(editor, "Meadow Sentinel", DeckSection.MAYBEBOARD))
        }
        val ward = "editor-card-MAINBOARD/${digest("Aegis Ward")}"
        compose.onNodeWithTag(ward).performTouchInput { click() }
        // Holding a card inspects it; the menu has no Inspect
        compose.onNodeWithText("Inspect").assertDoesNotExist()
        compose.onNodeWithText("Remove all").performClick()
        compose.runOnIdle { assertEquals(0, count(editor, "Aegis Ward", DeckSection.MAINBOARD)) }

        // LOB-059: the Text tab shows the rewritten list; Save keeps both tabs' changes.
        compose.onNodeWithTag("card-list-tab-text").performClick()
        compose.onNodeWithTag("deck-list").assertTextContains("Maybeboard\n4 Meadow Sentinel", substring = true)
        compose.onNodeWithTag("save-deck").assertIsEnabled()
        // LOB-064
        compose.runOnIdle { back() }
        compose.onNodeWithText("Discard unsaved changes?").assertIsDisplayed()
    }

    // LOB-105, LOB-106, LOB-122
    @Test fun searchAddsToTheChosenSectionSquaresRemoveAndDoneKeepsTheChanges() {
        val editor = editor()
        connectSearch(editor, "Meadow Sentinel", "Rune Bear")
        compose.setContent { ScreenTheme { Surface(Modifier.fillMaxSize()) { DeckImportScreen(editor, false, null, null) } } }
        compose.onNodeWithContentDescription("Add cards to Mainboard").performClick()
        compose.mainClock.advanceTimeBy(500)
        compose.onNodeWithTag("card-search").assertIsDisplayed()
        compose.onNodeWithText("Type a card name or search terms, or choose filters.").assertIsDisplayed()
        assertTrue(searched.isEmpty(), "Nothing typed or chosen: no search")
        compose.onNodeWithTag("destination-main").assertIsSelected()
        compose.onNodeWithTag("destination-maybe").assertIsNotSelected()
        compose.onNodeWithTag("card-search-field").performTextInput("e")
        compose.onNodeWithContentDescription("Colour G").performClick()
        compose.mainClock.advanceTimeBy(500)
        compose.onNodeWithTag("card-search-results").assertIsDisplayed()
        assertEquals(listOf("e c:g"), searched, "Typing and chips search once, after a short pause")
        compose.onNodeWithText("Done").assertIsDisplayed()

        // Meadow Sentinel is already in Main ×4: its Main square counts on from there; a card with no copies has no square.
        compose.onNodeWithTag("search-count-Meadow Sentinel-main").assertTextEquals("4")
        compose.onNodeWithTag("search-count-Rune Bear-main").assertDoesNotExist()
        compose.onNodeWithTag("search-result-Rune Bear").performTouchInput { click() }
        compose.mainClock.advanceTimeBy(500)
        compose.onNodeWithTag("search-count-Rune Bear-main").assertTextEquals("1")
        compose.onNodeWithText("Done · +1 card").assertIsDisplayed()
        compose.onNodeWithTag("search-result-Meadow Sentinel").performTouchInput { click() }
        compose.mainClock.advanceTimeBy(500)
        compose.onNodeWithTag("search-count-Meadow Sentinel-main").assertTextEquals("5")
        assertEquals("p-Rune Bear", editor.entries!!.last().printing.id)
        assertEquals(digest("Meadow Sentinel"), editor.entries!!.first { it.printing.name == "Meadow Sentinel" }.printing.id,
            "Another printing of a listed card adds to it")

        // Switching the destination: taps add to Side, whose square appears beside Main's.
        compose.onNodeWithTag("destination-side").performClick()
        compose.onNodeWithTag("search-count-Rune Bear-main").assertTextEquals("1")
        compose.onNodeWithTag("search-count-Rune Bear-side").assertDoesNotExist()
        compose.onNodeWithTag("search-result-Rune Bear").performTouchInput { click() }
        compose.onNodeWithTag("search-result-Rune Bear").performTouchInput { click() }
        compose.mainClock.advanceTimeBy(500)
        compose.onNodeWithTag("search-count-Rune Bear-side").assertTextEquals("2").performClick()
        compose.mainClock.advanceTimeBy(500)
        compose.onNodeWithTag("search-count-Rune Bear-side").assertTextEquals("1")
        compose.onNodeWithText("Done · +3 cards").performClick()
        compose.mainClock.advanceTimeBy(500)
        compose.onNodeWithTag("card-list-cards").assertIsDisplayed()
        assertEquals(1, count(editor, "Rune Bear", DeckSection.MAINBOARD))
        assertEquals(1, count(editor, "Rune Bear", DeckSection.SIDEBOARD))
        assertEquals(5, count(editor, "Meadow Sentinel", DeckSection.MAINBOARD))
        assertTrue("1 Rune Bear (NEW) 9" in editor.state.text)

        // LOB-105: search starts at the section whose + was tapped, and the search is kept for the session.
        compose.onNodeWithTag("card-list-cards").performScrollToNode(hasTestTag("add-cards-maybe"))
        compose.onNodeWithTag("add-cards-maybe").performClick()
        compose.mainClock.advanceTimeBy(500)
        compose.onNodeWithTag("destination-maybe").assertIsSelected()
        compose.onNodeWithTag("destination-side").assertIsNotSelected()
        compose.onNodeWithText("Done").assertIsDisplayed()
        assertEquals(1, searched.size, "Coming back keeps the results")
        compose.runOnIdle { editor.search.close() }
        compose.onNodeWithTag("card-list-cards").performScrollToNode(hasTestTag("add-cards-main"))
        compose.onNodeWithTag("add-cards-main").performClick()
        compose.onNodeWithTag("destination-main").assertIsSelected()
    }

    // LOB-120, LOB-049: a round Undo at the bottom left, shown only when there is something to undo, takes back Cards tab and
    // search changes one at a time.
    @Test fun undoTakesBackCardsTabAndSearchChangesOneAtATime() {
        val editor = editor()
        connectSearch(editor, "Rune Bear")
        val original = editor.state.text
        compose.setContent { ScreenTheme { Surface(Modifier.fillMaxSize()) { DeckImportScreen(editor, false, null, null) } } }
        compose.onNodeWithTag("card-list-undo").assertDoesNotExist()
        val sentinel = "editor-card-MAINBOARD/${digest("Meadow Sentinel")}"
        compose.onNodeWithTag(sentinel).performTouchInput { swipeLeft() }
        compose.runOnIdle { assertEquals(1, count(editor, "Meadow Sentinel", DeckSection.SIDEBOARD)) }
        compose.onNodeWithTag("card-list-undo").assertIsDisplayed()
        compose.onNodeWithTag(sentinel).performTouchInput { click() }
        compose.onNodeWithText("Remove all").performClick()
        compose.onNodeWithTag("add-cards-main").performClick()
        compose.onNodeWithTag("card-search-field").performTextInput("rune")
        compose.mainClock.advanceTimeBy(500)
        compose.onNodeWithTag("search-result-Rune Bear").performTouchInput { click() }
        compose.onNodeWithTag("search-result-Rune Bear").performTouchInput { click() }
        compose.onNodeWithTag("card-search-done").performClick()
        compose.runOnIdle { assertEquals(2, count(editor, "Rune Bear", DeckSection.MAINBOARD)) }

        // Each tap takes back one change, newest first: two search adds, Remove all, then the swipe.
        compose.onNodeWithTag("card-list-undo").performClick()
        compose.runOnIdle { assertEquals(1, count(editor, "Rune Bear", DeckSection.MAINBOARD)) }
        compose.onNodeWithTag("card-list-undo").performClick()
        compose.runOnIdle { assertEquals(0, count(editor, "Rune Bear", DeckSection.MAINBOARD)); assertEquals(0, count(editor, "Meadow Sentinel", DeckSection.MAINBOARD)) }
        compose.onNodeWithTag("card-list-undo").performClick()
        compose.runOnIdle { assertEquals(3, count(editor, "Meadow Sentinel", DeckSection.MAINBOARD)) }
        compose.onNodeWithTag("card-list-undo").performClick()
        compose.runOnIdle {
            assertEquals(4, count(editor, "Meadow Sentinel", DeckSection.MAINBOARD))
            assertEquals(0, count(editor, "Meadow Sentinel", DeckSection.SIDEBOARD))
            assertEquals(original, editor.state.text, "Undoing every change gives back the text the editor opened with")
            assertTrue(editor.state.result!!.resolved)
        }
        compose.onNodeWithTag("card-list-undo").assertDoesNotExist()

        // A cover the change cleared comes back with it; a Text tab edit starts the undo over.
        compose.runOnIdle {
            val ward = editor.coverCards.first { it.definition.name == "Aegis Ward" }.definition.printingId!!
            editor.cover(ward)
            editor.applyCards(CardListEdits.removeAll(editor.entries!!, digest("Aegis Ward"), DeckSection.MAINBOARD), editor.listCatalog)
            assertNull(editor.state.cover)
            editor.undoCards()
            assertEquals(ward, editor.state.cover)
            editor.applyCards(CardListEdits.change(editor.entries!!, digest("Aegis Ward"), DeckSection.MAINBOARD, -1), editor.listCatalog)
            assertTrue(editor.canUndo)
            editor.edit(editor.state.text + "\n1 Rune Bear")
            assertFalse(editor.canUndo)
        }
        compose.onNodeWithTag("card-list-undo").assertDoesNotExist()
    }

    // LOB-121: Main, Side and Maybe squares in that order, each only while its section holds copies; a tap on a square removes
    // one copy from that section, whichever section is chosen.
    @Test fun searchSquaresShowEachSectionInOrderAndRemoveFromTheirOwnSection() {
        val editor = editor()
        connectSearch(editor, "Rune Bear")
        editor.search.open(CardListEdits.total(editor.entries!!))
        editor.search.text("rune")
        val bear = listOf(CatalogPrinting("p-Rune Bear", "Rune Bear", "NEW", "9", "English", "", null, false, emptyList(),
            "{\"type\":\"Creature — Bear\"}"))
        var entries = editor.entries!!
        for ((section, copies) in listOf(DeckSection.MAYBEBOARD to 3, DeckSection.MAINBOARD to 1, DeckSection.SIDEBOARD to 2))
            repeat(copies) { entries = CardListEdits.add(CardListKind.DECK, entries, bear, section).entries }
        editor.applyCards(entries, metadata)
        compose.setContent { ScreenTheme { Surface(Modifier.fillMaxSize()) { DeckImportScreen(editor, false, null, null) } } }
        compose.mainClock.advanceTimeBy(500)
        fun square(section: String) = compose.onNodeWithTag("search-count-Rune Bear-$section")
        fun left(section: String) = square(section).fetchSemanticsNode().boundsInRoot.left
        square("main").assertTextEquals("1"); square("side").assertTextEquals("2"); square("maybe").assertTextEquals("3")
        assertTrue(left("main") < left("side") && left("side") < left("maybe"), "Main, Side, Maybe from the left")
        val result = compose.onNodeWithTag("search-result-Rune Bear").fetchSemanticsNode().boundsInRoot
        assertTrue(square("main").fetchSemanticsNode().boundsInRoot.let { it.left - result.left < 8 && result.bottom - it.bottom < 8 },
            "Squares sit at the result's bottom left")

        // Main is chosen; tapping the Maybe square still removes from Maybe.
        square("maybe").performClick()
        compose.mainClock.advanceTimeBy(500)
        square("maybe").assertTextEquals("2")
        assertEquals(1, count(editor, "Rune Bear", DeckSection.MAINBOARD))
        square("main").performClick()
        compose.mainClock.advanceTimeBy(500)
        square("main").assertDoesNotExist()
        assertTrue(left("side") < left("maybe"), "Side moves to the front when Main is empty")
        square("side").performClick(); square("side").performClick()
        compose.mainClock.advanceTimeBy(500)
        square("side").assertDoesNotExist()
        square("maybe").assertTextEquals("2")
        assertEquals(0, count(editor, "Rune Bear", DeckSection.MAINBOARD))
        assertEquals(0, count(editor, "Rune Bear", DeckSection.SIDEBOARD))
        assertEquals(2, count(editor, "Rune Bear", DeckSection.MAYBEBOARD))
    }

    // LOB-121, DRF-003: a cube's results show only Main and Maybe squares; the chosen section's square counts up.
    @Test fun cubeSearchSquaresShowMainAndMaybe() {
        val bear = CardDefinition("Rune Bear", listOf(DefinitionFace("rune-bear", "Rune Bear", typeLine = "Creature — Bear")),
            printingId = "00000000-0000-4000-8000-000000000201")
        val digest = bear.printingId!!
        val saved = SavedCube(CubeSummary("cube", "Runes", "1", "now", "now", 2, maybeCopies = 1),
            listOf(SavedCubeEntry(digest, bear.name, 2), SavedCubeEntry(digest, bear.name, 1, SavedDeckSection.MAYBEBOARD)))
        val api = Proxy.newProxyInstance(Cubes::class.java.classLoader, arrayOf(Cubes::class.java)) { _, method, args ->
            when (method.name) {
                "readCube" -> saved
                "close" -> Unit
                else -> error("unused ${method.name}")
            }
        } as Cubes
        val cubes = CubeController(api, { "token" }, { error("No lookup expected") }, { error("unused") }, Immediate, { it() })
        cubes.cardRecords = testCardRecords(bear)
        cubes.open("cube"); cubes.edit()
        try {
            cubes.search.source = { _, _ -> CatalogCardSearch.Page("g", metadata,
                listOf(CatalogCard("o-Rune Bear", "Rune Bear", listOf(printing("Rune Bear")))), null, 1) }
            cubes.search.open(CardListEdits.total(cubes.entries!!))
            cubes.search.text("rune")
            compose.setContent { ScreenTheme { Surface(Modifier.fillMaxSize()) { CubeScreen(cubes, null) } } }
            compose.mainClock.advanceTimeBy(500)
            fun square(section: String) = compose.onNodeWithTag("search-count-Rune Bear-$section")
            square("main").assertTextEquals("2")
            square("side").assertDoesNotExist()
            square("maybe").assertTextEquals("1")
            assertTrue(square("main").fetchSemanticsNode().boundsInRoot.left < square("maybe").fetchSemanticsNode().boundsInRoot.left)
            compose.onNodeWithTag("destination-maybe").performClick()
            compose.onNodeWithTag("search-result-Rune Bear").performTouchInput { click() }
            compose.mainClock.advanceTimeBy(500)
            square("maybe").assertTextEquals("2")
            square("main").performClick()
            compose.mainClock.advanceTimeBy(500)
            square("main").assertTextEquals("1")
            compose.runOnIdle { assertEquals(listOf(DeckSection.MAINBOARD to 1, DeckSection.MAYBEBOARD to 2),
                cubes.entries!!.map { it.section to it.quantity }) }
        } finally { cubes.close() }
    }

    // LOB-119: colour and mana value chips are mana symbols; every chip is 32 dp tall and 4 dp from the next, rows too;
    // a chosen chip shows as selected.
    @Test fun searchChipsAreManaSymbols32DpTall4DpApart() {
        val editor = editor()
        connectSearch(editor)
        editor.search.open(CardListEdits.total(editor.entries!!))
        compose.setContent { ScreenTheme { Surface(Modifier.fillMaxSize()) { DeckImportScreen(editor, false, null, null) } } }
        compose.mainClock.advanceTimeBy(500)
        fun bounds(node: SemanticsNodeInteraction) = node.getUnclippedBoundsInRoot()
        val colours = SearchColor.entries.map { compose.onNodeWithTag("search-colour-${it.label}") }
        val values = SearchManaValue.entries.map { compose.onNodeWithTag("search-value-${it.label}") }
        val types = SearchType.entries.map { compose.onNodeWithText(it.label) }
        for (chip in colours + values + types) chip.assertHeightIsEqualTo(32.dp)
        for (row in listOf(colours, values, types)) row.zipWithNext { a, b -> assertEquals(4.dp, bounds(b).left - bounds(a).right) }
        assertEquals(4.dp, bounds(types.first()).top - bounds(colours.first()).bottom, "Rows 4 dp apart")
        compose.onNodeWithContentDescription("Colour G").assertIsNotSelected().performClick().assertIsSelected()
        compose.onNodeWithContentDescription("Mana value 6+").performScrollTo().performClick().assertIsSelected()
        assertEquals(setOf(SearchColor.G), editor.search.state.colors)
        assertEquals(setOf(SearchManaValue.SIX), editor.search.state.values)
    }

    // LOB-063, LOB-122: adding past the limit is refused with a reason.
    @Test fun searchRefusesCardsPastTheLimit() {
        val editor = editor()
        val full = editor.entries!!.map { if (it.printing.name == "Training Land") it.copy(quantity = 1000 - 60 - 15 + 48) else it }
        editor.applyCards(full, metadata)
        assertEquals(1000L, CardListEdits.total(editor.entries!!))
        connectSearch(editor, "Rune Bear")
        editor.search.open(1000)
        editor.search.text("rune")
        compose.setContent { ScreenTheme { Surface(Modifier.fillMaxSize()) { DeckImportScreen(editor, false, null, null) } } }
        compose.mainClock.advanceTimeBy(500)
        compose.onNodeWithTag("search-result-Rune Bear").performTouchInput { click() }
        compose.onNodeWithText("A deck holds at most 1000 cards.").assertIsDisplayed()
        assertEquals(0, count(editor, "Rune Bear", DeckSection.MAINBOARD))
    }

    // LOB-107: a list with problems cannot be edited as cards until the Text tab is fixed.
    @Test fun cardsTabWaitsForAResolvedList() {
        val editor = editor()
        editor.seed(editor.state.copy(result = DeckImportResult("x", emptyList(), listOf(ImportIssue(2, "Unknown Card could not be resolved.")), null)))
        compose.setContent { ScreenTheme { Surface(Modifier.fillMaxSize()) { DeckImportScreen(editor, false, null, null) } } }
        compose.onNodeWithText("The list has problems. Fix them in the Text tab.").assertIsDisplayed()
        compose.onNodeWithText("Line 2: Unknown Card could not be resolved.").assertIsDisplayed()
        // LOB-107: the + buttons are off: the sections don't show until the list resolves.
        compose.onNodeWithTag("add-cards-main").assertDoesNotExist()
    }

    // LOB-059: the Text tab's box is full width with no counts; problems show in a fixed area under it and as a red error
    // icon on the Text tab while they remain.
    @Test @Config(qualifiers = "w892dp-h412dp-land-mdpi") fun textTabIsFullWidthWithProblemsUnderItAndAnErrorIcon() {
        val editor = editor()
        val text = editor.state.text
        val clean = editor.state.result!!
        editor.seed(editor.state.copy(result = DeckImportResult(text, emptyList(),
            listOf(ImportIssue(2, "Meadow Sentinel is ambiguous. Include an exact (SET) collector-number.")), null)))
        compose.setContent { ScreenTheme { Surface(Modifier.fillMaxSize()) { DeckImportScreen(editor, true, null, null) } } }
        compose.onNodeWithTag("card-list-tab-text").assert(hasContentDescription("The list has problems"))
        compose.onNodeWithTag("text-tab-problems", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithTag("card-list-tab-text").performClick()
        val box = compose.onNodeWithTag("deck-list").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        assertEquals(892f - 32f, box.width, 1f, "The box is full width in landscape too")
        val message = compose.onNodeWithText("Line 2: Meadow Sentinel is ambiguous. Include an exact (SET) collector-number.")
            .assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        assertTrue(message.top >= box.bottom, "Messages show under the box")
        compose.onNodeWithText("60 main · 15 side").assertDoesNotExist()
        compose.runOnIdle { assertEquals(setOf(2), problemLines(editor.state.text, editor.state.result)) }
        // Once the list resolves, the icon goes and the Text tab still shows no counts.
        compose.runOnIdle { editor.seed(editor.state.copy(result = clean)) }
        compose.onNodeWithTag("text-tab-problems", useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithText("60 main · 15 side").assertDoesNotExist()
        compose.onNodeWithText("Line 2", substring = true).assertDoesNotExist()
    }

    // LOB-105: a new list shows every section, empty ones too, each header's + opening search with that section chosen.
    @Test fun everySectionHeaderHasAPlusThatOpensSearchWithThatSection() {
        val editor = DeckImportController({ error("No lookup expected") }, Immediate, { it() }).also { it.open() }
        connectSearch(editor, "Rune Bear")
        compose.setContent { ScreenTheme { Surface(Modifier.fillMaxSize()) { DeckImportScreen(editor, false, null, null) } } }
        compose.onNodeWithTag("card-list-tab-cards").performClick()
        for ((label, tag) in listOf("Mainboard" to "main", "Sideboard" to "side", "Maybeboard" to "maybe")) {
            compose.onNodeWithTag("card-list-cards").performScrollToNode(hasText("$label · 0"))
            compose.onNodeWithText("$label · 0").assertIsDisplayed()
            compose.onNodeWithTag("add-cards-$tag").assertIsDisplayed().performClick()
            compose.onNodeWithTag("card-search").assertIsDisplayed()
            compose.onNodeWithTag("destination-$tag").assertIsSelected()
            compose.runOnIdle { editor.search.close() }
        }
        compose.onAllNodesWithText("Empty").assertCountEquals(3)
        // Changing the section inside search still works.
        compose.onNodeWithTag("add-cards-side").performClick()
        compose.onNodeWithTag("destination-main").performClick()
        compose.runOnIdle { assertEquals(DeckSection.MAINBOARD, editor.destination) }
    }

    // DRF-003, LOB-064, LOB-105, LOB-106: the cube editor is the same editor with Main | Maybe, and asks before discarding.
    @Test fun cubeEditorUsesMainAndMaybeAndConfirmsDiscard() {
        val bear = CardDefinition("Rune Bear", listOf(DefinitionFace("rune-bear", "Rune Bear", typeLine = "Creature — Bear")),
            printingId = "00000000-0000-4000-8000-000000000201")
        val digest = bear.printingId!!
        val saved = SavedCube(CubeSummary("cube", "Runes", "1", "now", "now", 3), listOf(SavedCubeEntry(digest, bear.name, 3)))
        val api = object : Cubes {
            override suspend fun listCubes(token: String, after: String?) = CubeList(listOf(saved.cube))
            override suspend fun readCube(token: String, id: String) = saved
            override suspend fun create(token: String, value: CreateCube): SavedCube = error("unused")
            override suspend fun update(token: String, id: String, value: UpdateCube): SavedCube = error("unused")
            override suspend fun delete(token: String, id: String, value: DeleteCube): CubeDeleted = error("unused")
            override fun close() = Unit
        }
        val cubes = CubeController(api, { "token" }, { error("No lookup expected") }, { error("unused") }, Immediate, { it() })
        cubes.cardRecords = testCardRecords(bear)
        cubes.open("cube"); cubes.edit()
        try {
            compose.setContent { ScreenTheme { Surface(Modifier.fillMaxSize()) { CubeScreen(cubes, null) } } }
            compose.onNodeWithText("Edit cube").assertIsDisplayed()
            compose.onNodeWithText("3 main").assertIsDisplayed()
            compose.onNodeWithTag("editor-card-MAINBOARD/$digest").performTouchInput { swipeRight() }
            compose.runOnIdle { assertEquals(listOf(DeckSection.MAINBOARD to 2, DeckSection.MAYBEBOARD to 1),
                cubes.entries!!.map { it.section to it.quantity }) }
            compose.onNodeWithText("2 main · 1 maybe").assertIsDisplayed()
            // LOB-120: the cube editor's Undo takes the swipe back.
            compose.onNodeWithTag("card-list-undo").performClick()
            compose.onNodeWithText("3 main").assertIsDisplayed()
            compose.onNodeWithTag("card-list-undo").assertDoesNotExist()
            compose.onNodeWithTag("editor-card-MAINBOARD/$digest").performTouchInput { swipeRight() }
            compose.onNodeWithTag("add-cards-side").assertDoesNotExist()
            compose.onNodeWithTag("card-list-cards").performScrollToNode(hasTestTag("add-cards-maybe"))
            compose.onNodeWithTag("add-cards-maybe").performClick()
            compose.onNodeWithTag("destination-side").assertDoesNotExist()
            compose.onNodeWithTag("destination-maybe").assertIsSelected()
            assertEquals(DeckSection.MAYBEBOARD, cubes.destination)
            compose.runOnIdle { cubes.back() }
            compose.onNodeWithTag("card-list-cards").assertIsDisplayed()
            compose.runOnIdle { cubes.back() }
            compose.onNodeWithText("Discard unsaved changes?").assertIsDisplayed()
            compose.onNodeWithText("Keep editing").performClick()
            assertTrue(cubes.state.editing)
            compose.runOnIdle { cubes.back() }
            compose.onNodeWithText("Discard").performClick()
            compose.runOnIdle { assertFalse(cubes.state.editing) }
            compose.onNodeWithText("Saved cube").assertIsDisplayed()
        } finally { cubes.close() }
    }

    // LOB-056, LOB-057, SYS-054: a saved deck shows Maybe in its section colour when it has Maybe cards; Edit is its only button.
    @Test fun savedDeckShowsMaybeAndOnlyEdit() {
        val library = fixture.library
        library.seed(library.state.copy(detail = detail.copy(saved = detail.saved.copy(entries = detail.saved.entries +
            SavedDeckEntry(SavedDeckSection.MAYBEBOARD, digest("Ember Adept"), "Ember Adept", 2)))))
        compose.setContent { ScreenTheme { SavedDeckScreen(library, null, importDeck = {}) } }
        compose.onNodeWithTag("deck-view").performScrollToNode(hasText("Maybeboard · 2"))
        compose.onNodeWithContentDescription("Ember Adept, 2 copies, Maybeboard").assertExists()
        compose.onNodeWithText("Edit").assertIsDisplayed()
        compose.onNodeWithText("Rename").assertDoesNotExist()
        compose.onNodeWithText("Delete saved deck").assertDoesNotExist()
        compose.onNodeWithText("Delete draft deck").assertDoesNotExist()
    }

    // LOB-057: a draft deck shows Delete draft deck beside Save as deck at the top right; deleting is confirmed.
    @Test fun draftDeckShowsDeleteBesideSaveAsDeckAtTheTopRight() {
        val fixture = ScreenFixtures().apply { configure("draft-deck-detail") }
        compose.setContent { ScreenTheme { SavedDeckScreen(fixture.library, null, importDeck = {}) } }
        val title = compose.onNodeWithText("Draft deck").fetchSemanticsNode().boundsInRoot
        val delete = compose.onNodeWithText("Delete draft deck").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        val save = compose.onNodeWithText("Save as deck").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        assertTrue(delete.right <= save.left && delete.center.y in title.top..title.bottom && save.center.y in title.top..title.bottom,
            "Delete draft deck sits beside Save as deck in the top bar")
        val sections = compose.onNodeWithText("Mainboard · 60").fetchSemanticsNode().boundsInRoot
        assertTrue(save.bottom <= sections.top)
        compose.onNodeWithText("Edit").assertDoesNotExist()
        compose.onNodeWithText("Rename").assertDoesNotExist()
        compose.onNodeWithText("Delete draft deck").performClick()
        compose.onNodeWithText("Deletion cannot be undone.").assertIsDisplayed()
        assertEquals(DeckDialog.DELETE, fixture.library.state.dialog)
    }

    // LOB-057: the editor of a saved deck has a red Delete deck at the top right, confirmed with "Deletion cannot be undone.";
    // a new import has none.
    @Test fun savedDeckEditorDeletesTheDeckAfterConfirmation() {
        val editor = editor()
        val deleted = mutableListOf<String>(); val requests = mutableListOf<Pair<String, DeleteDeck>>()
        var fail = true
        editor.connectDelete({ { id, request -> requests += id to request; if (fail) throw DeckFailure("network") } }, { deleted += it })
        compose.setContent { ScreenTheme { Surface(Modifier.fillMaxSize()) {
            if (editor.state.active) DeckImportScreen(editor, false, null, null)
        } } }
        compose.onNodeWithTag("deck-name").performTextReplacement("Renamed")
        compose.onNodeWithTag("delete-list").assertTextEquals("Delete deck").performClick()
        compose.onNodeWithText("Delete deck?").assertIsDisplayed()
        compose.onNodeWithText(detail.saved.deck.name).assertIsDisplayed()
        compose.onNodeWithText("Deletion cannot be undone.").assertIsDisplayed()
        compose.onNodeWithText("Keep deck").performClick()
        compose.onNodeWithText("Delete deck?").assertDoesNotExist()
        assertTrue(requests.isEmpty(), "Delete needs confirmation")
        compose.onNodeWithTag("deck-name").assertTextEquals("Renamed")

        compose.onNodeWithTag("delete-list").performClick()
        compose.onNode(hasText("Delete deck") and hasAnyAncestor(isDialog())).performClick()
        compose.onNodeWithText("Could not reach the deck service. Your input is kept. Check your connection and retry.").assertIsDisplayed()
        assertTrue(editor.state.active && deleted.isEmpty())
        fail = false
        compose.runOnIdle { editor.dismissDeleteError() }
        compose.onNode(hasText("Delete deck") and hasAnyAncestor(isDialog())).performClick()
        compose.runOnIdle {
            assertEquals(listOf(detail.saved.deck.id, detail.saved.deck.id), requests.map { it.first })
            assertEquals(requests[0].second, requests[1].second, "A retried delete keeps its request id")
            assertEquals(listOf(detail.saved.deck.id), deleted)
            assertFalse(editor.state.active)
        }

        // A new import is not a saved deck: no Delete.
        compose.runOnIdle { editor.open() }
        compose.onNodeWithTag("open-import-link").assertIsDisplayed()
        compose.onNodeWithTag("delete-list").assertDoesNotExist()
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w412dp-h892dp-port-mdpi")
class PodCubePreparationTest {
    @get:Rule val compose = createComposeRule()
    private val bear = CardDefinition("Rune Bear", listOf(DefinitionFace("rune-bear", "Rune Bear", typeLine = "Creature — Bear")),
        printingId = "00000000-0000-4000-8000-000000000201")
    private val bolt = CardDefinition("Rune Bolt", listOf(DefinitionFace("rune-bolt", "Rune Bolt", typeLine = "Instant")),
        printingId = "00000000-0000-4000-8000-000000000202")
    private val bearDigest = DeckJson.digest(bear)
    private val boltDigest = DeckJson.digest(bolt)
    /** The pod keeps its cards by digest, each with its printing for the phone to show it from its records (owner 2026-11-10). */
    private var cube = PodCube("Runes", listOf(PodCubeEntry(bearDigest, bear.printingId, bear.name, 3),
        PodCubeEntry(boltDigest, bolt.printingId, bolt.name, 1, SavedDeckSection.MAYBEBOARD)))
    private val moves = mutableListOf<MovePodCubeCards>()
    private var refused = false
    private var offline = false
    private val api = Proxy.newProxyInstance(Pods::class.java.classLoader, arrayOf(Pods::class.java)) { _, method, args ->
        when (method.name) {
            "cube" -> cube
            "moveCube" -> {
                val input = args[2] as MovePodCubeCards
                if (refused) throw PodFailure("not_setup")
                moves += input
                cube = cube.moved(PodCubeMove(input.definition, input.from, input.quantity))!!
                cube
            }
            else -> error("unused ${method.name}")
        }
    } as Pods

    // DRF-141, DRF-142
    @Test fun prepareCubeMovesCardsBetweenMainAndMaybeAndUndoes() {
        var refreshed = 0
        val preparation = PodCubePreparation(api, { "token" }, Immediate, { it() }) { refreshed++ }.also { it.cardRecords = testCardRecords(bear, bolt) }
        preparation.open("00000000-0000-4000-8000-000000000001")
        compose.setContent { ScreenTheme { Surface(Modifier.fillMaxSize()) { if (preparation.state.open) PodCubePreparationScreen(preparation) } } }
        compose.onNodeWithText("Prepare cube · Runes").assertIsDisplayed()
        compose.onNodeWithText("Mainboard · 3").assertIsDisplayed()
        compose.onNodeWithText("Maybeboard · 1").assertIsDisplayed()
        compose.onNodeWithTag("prepare-cube-undo").assertDoesNotExist()
        compose.onNodeWithTag("prepare-cube-card-MAINBOARD/$bearDigest").performTouchInput { swipeLeft() }
        compose.runOnIdle {
            assertEquals(listOf(SavedDeckSection.MAINBOARD to 1), moves.map { it.from to it.quantity })
            assertEquals(2L, preparation.state.count(SavedDeckSection.MAINBOARD))
        }
        compose.onNodeWithText("1× Rune Bear → Maybeboard").assertExists()
        compose.onNodeWithTag("prepare-cube-card-MAYBEBOARD/$boltDigest").performTouchInput { click() }
        compose.onNodeWithText("Move to Mainboard").performClick()
        compose.runOnIdle { assertEquals(3L, preparation.state.count(SavedDeckSection.MAINBOARD)) }
        compose.onNodeWithTag("prepare-cube-undo").performClick()
        compose.runOnIdle {
            assertEquals(PodCubeMove(boltDigest, SavedDeckSection.MAINBOARD, 1), moves.last().let { PodCubeMove(it.definition, it.from, it.quantity) })
            assertEquals(2L, preparation.state.count(SavedDeckSection.MAINBOARD))
            assertEquals(1, preparation.state.undo.size)
        }
        // A refused move reloads the server's prepared cube and says so.
        refused = true
        compose.onNodeWithTag("prepare-cube-card-MAINBOARD/$bearDigest").performTouchInput { swipeRight() }
        compose.runOnIdle { assertEquals(2L, preparation.state.count(SavedDeckSection.MAINBOARD)) }
        compose.onNodeWithText("The move was not saved. The cube was reloaded.").assertIsDisplayed()
        compose.onNodeWithTag("prepare-cube-done").performClick()
        compose.runOnIdle { assertFalse(preparation.state.open); assertEquals(1, refreshed) }
    }

    /** Runs queued pod work only when the test says so. */
    private class Queued : AbstractExecutorService() {
        val tasks = ArrayDeque<Runnable>()
        override fun execute(command: Runnable) { tasks.addLast(command) }
        fun runAll() { while (tasks.isNotEmpty()) tasks.removeFirst().run() }
        fun runOne() { tasks.removeFirst().run() }
        override fun shutdown() = Unit
        override fun shutdownNow() = mutableListOf<Runnable>()
        override fun isShutdown() = false
        override fun isTerminated() = false
        override fun awaitTermination(timeout: Long, unit: TimeUnit) = true
    }

    /** [api] that can't reach the server while [offline] (a proxy would wrap the checked failure). */
    private val unreachable = object : Pods by api {
        override suspend fun cube(token: String, id: String): PodCube =
            if (offline) throw PodFailure("network") else api.cube(token, id)
        override suspend fun moveCube(token: String, id: String, input: MovePodCubeCards): PodCube =
            if (offline) throw PodFailure("network") else api.moveCube(token, id, input)
    }

    // DRF-142, LOB-049, LOB-050: a move that can't reach the server makes Prepare cube read-only until the cube reloads.
    @Test fun anUnreachableServerMakesPrepareCubeReadOnlyUntilItsCubeReloads() {
        val worker = Queued()
        val preparation = PodCubePreparation(unreachable, { "token" }, worker, { it() }, wait = {}) {}
        preparation.open("00000000-0000-4000-8000-000000000001")
        worker.runAll()
        compose.setContent { ScreenTheme { Surface(Modifier.fillMaxSize()) { if (preparation.state.open) PodCubePreparationScreen(preparation) } } }
        compose.onNodeWithText("Disconnected · preparation is read-only").assertDoesNotExist()
        compose.onNodeWithTag("prepare-cube-card-MAINBOARD/$bearDigest").performTouchInput { swipeLeft() }
        compose.runOnIdle { worker.runAll() }
        compose.onNodeWithTag("prepare-cube-undo").assertIsEnabled()
        offline = true
        compose.onNodeWithTag("prepare-cube-card-MAINBOARD/$bearDigest").performTouchInput { swipeLeft() }
        compose.runOnIdle { worker.runOne(); assertEquals(1L, preparation.state.count(SavedDeckSection.MAINBOARD)) }
        compose.onNodeWithText("Disconnected · preparation is read-only").assertIsDisplayed()
        // Read-only: nothing moves, the menu's moves are disabled and there is nothing left to undo.
        compose.onNodeWithTag("prepare-cube-undo").assertDoesNotExist()
        compose.runOnIdle { assertFalse(preparation.move(bearDigest, SavedDeckSection.MAINBOARD, false)) }
        compose.onNodeWithTag("prepare-cube-card-MAINBOARD/$bearDigest").performTouchInput { click() }
        compose.onNodeWithText("Move to Maybeboard").assertIsNotEnabled()
        compose.onNodeWithText("Inspect").assertDoesNotExist() // holding inspects (LOB-048)
        // Still unreachable: it keeps trying, one attempt per task.
        compose.runOnIdle { worker.runOne(); assertEquals(1, worker.tasks.size); assertFalse(preparation.state.connected) }
        offline = false
        compose.runOnIdle { worker.runAll() }
        compose.onNodeWithText("Disconnected · preparation is read-only").assertDoesNotExist()
        compose.runOnIdle {
            assertTrue(preparation.state.connected)
            // The server's cube is shown: the failed move never happened.
            assertEquals(2L, preparation.state.count(SavedDeckSection.MAINBOARD))
            assertEquals(1, moves.size)
        }
    }

    private object Dropping : AbstractExecutorService() {
        override fun execute(command: Runnable) = Unit
        override fun shutdown() = Unit
        override fun shutdownNow() = mutableListOf<Runnable>()
        override fun isShutdown() = false
        override fun isTerminated() = false
        override fun awaitTermination(timeout: Long, unit: TimeUnit) = true
    }

    // DRF-141, DRF-142: only the creator of a cube pod sees Prepare cube in setup, which opens over the pod screen.
    @Test fun setupOffersPrepareCubeToTheCreatorOfACubePod() {
        val alice = AccountIdentity("alice", "Alice")
        val cubes = Proxy.newProxyInstance(Cubes::class.java.classLoader, arrayOf(Cubes::class.java)) { _, method, _ ->
            error("unused ${method.name}") } as Cubes
        val pods = PodController(api, cubes, { "token" }, Dropping, { it() })
        val pod = "00000000-0000-4000-8000-000000000001"
        val detail = PodDetail(pod, "Runes night", PodPhase.SETUP, alice, DraftSettings(), PodSource("Runes", 360, maybeCopies = 12),
            DraftSupply(360, 360), listOf(PodMember(alice)), emptyList(), updatedAt = "2026-10-31T00:00:00Z")
        var viewer by androidx.compose.runtime.mutableStateOf("alice")
        pods.seed(pods.state.copy(selected = pod, detail = detail, loaded = true))
        compose.setContent { ScreenTheme { Surface(Modifier.fillMaxSize()) { PodsDestination(pods, viewer, null, null, {}) } } }
        compose.onNodeWithText("360 main · 12 maybe").performScrollTo().assertIsDisplayed()
        compose.runOnIdle { viewer = "bob" }
        compose.onNodeWithTag("prepare-cube").assertDoesNotExist()
        compose.runOnIdle { viewer = "alice"; pods.seed(pods.state.copy(detail = detail.copy(source = PodSource(kind = PodSourceKind.SET, setName = "Set")))) }
        compose.onNodeWithTag("prepare-cube").assertDoesNotExist()
        compose.runOnIdle { pods.seed(pods.state.copy(detail = detail)) }
        compose.onNodeWithTag("prepare-cube").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(pod, pods.cubePreparation.state.pod) }
        compose.onNodeWithText("Prepare cube").assertIsDisplayed()
        compose.onNodeWithTag("prepare-cube-done").performClick()
        compose.runOnIdle { assertFalse(pods.cubePreparation.state.open) }
        compose.onNodeWithText("360 main · 12 maybe").assertExists()
    }
}

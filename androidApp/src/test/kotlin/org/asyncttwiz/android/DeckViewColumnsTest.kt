package org.asyncttwiz.android

import android.content.Context
import android.content.res.Configuration
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import org.asyncttwiz.protocol.*
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

    // LOB-056
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
            // LOB-057: Edit is the only button above a saved deck.
            compose.onNodeWithText("Edit").assertIsDisplayed()
            compose.onNodeWithText("Delete saved deck").assertDoesNotExist()
            compose.onNodeWithText("Rename").assertDoesNotExist()
            compose.onNodeWithText("Maybeboard", substring = true).assertDoesNotExist()
        } finally { fixture.library.close() }
    }

    // LOB-051
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

    // LOB-051
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

    private fun cubeController(): CubeController {
        val cards = listOf(CardDefinition("Rune Bear", listOf(DefinitionFace("rune-bear", "Rune Bear", typeLine = "Creature — Bear"))),
            CardDefinition("Rune Bolt", listOf(DefinitionFace("rune-bolt", "Rune Bolt", typeLine = "Instant"))))
            .associateBy { org.asyncttwiz.protocol.DeckJson.digest(it) }
        val saved = SavedCube(CubeSummary("cube", "Runes", "1", "now", "now", 4),
            cards.keys.mapIndexed { index, digest -> SavedCubeEntry(digest, if (index == 0) 3 else 1) })
        val immediate = object : java.util.concurrent.AbstractExecutorService() {
            override fun execute(command: Runnable) = command.run()
            override fun shutdown() = Unit
            override fun shutdownNow() = mutableListOf<Runnable>()
            override fun isShutdown() = false
            override fun isTerminated() = false
            override fun awaitTermination(timeout: Long, unit: java.util.concurrent.TimeUnit) = true
        }
        val api = object : Cubes {
            override suspend fun listCubes(token: String, after: String?) = CubeList(listOf(saved.cube))
            override suspend fun readCube(token: String, id: String) = saved
            override suspend fun cubeDefinitions(token: String, id: String, value: ReadCubeDefinitions) =
                SubmittedDefinitions(value.digests.map { SubmittedDefinition(it, cards.getValue(it)) })
            override suspend fun submit(token: String, value: SubmitDefinitions): DefinitionsAccepted = error("unused")
            override suspend fun create(token: String, value: CreateCube): SavedCube = error("unused")
            override suspend fun cubeDefinition(token: String, id: String, digest: String): SubmittedDefinition = error("unused")
            override suspend fun update(token: String, id: String, value: UpdateCube): SavedCube = error("unused")
            override suspend fun delete(token: String, id: String, value: DeleteCube): CubeDeleted = error("unused")
            override fun close() = Unit
        }
        return CubeController(api, { "token" }, { _ -> error("unused") }, { error("unused") }, immediate, { it() })
            .also { it.open(saved.cube.id) }
    }

    // DRF-010, LOB-056
    @Test fun savedCubeShowsTypeGroupedPicturesFiveWideInPortraitWithItsOwnCountAndTapInspects() {
        val cubes = cubeController()
        try {
            compose.setContent { ScreenTheme { CubeScreen(cubes, null) } }
            val tile = "Rune Bear, 3 copies, Mainboard"
            compose.onNodeWithText("Creatures · 3").assertIsDisplayed()
            compose.onNodeWithText("Instants · 1").assertIsDisplayed()
            // A cube has a Main section, and Maybe only when it holds cards.
            compose.onNodeWithText("Mainboard · 4").assertIsDisplayed()
            compose.onNodeWithText("Maybeboard", substring = true).assertDoesNotExist()
            compose.onNodeWithContentDescription("Columns").assert(hasText("5"))
            assertEquals(expected(5), tileWidth(tile), 1f)
            compose.onNodeWithContentDescription("More columns").performClick()
            assertEquals(expected(6), tileWidth(tile), 1f)
            val cubeKey = deckViewColumnsKey(DeckViewScreen.CUBE, Configuration.ORIENTATION_PORTRAIT)
            assertEquals(6, preferences.getInt(cubeKey, -1))
            assertFalse(preferences.contains(savedKey))
            compose.onNodeWithContentDescription(tile).performTouchInput { click() }
            assertEquals("Rune Bear", cubes.inspection.state?.card?.definition?.name)
            compose.runOnIdle { cubes.back() }
            assertNull(cubes.inspection.state)
            assertEquals("cube", cubes.state.selected, "Back closes the inspection first")
        } finally { cubes.close() }
    }

    // DRF-010
    @Test @Config(qualifiers = "w892dp-h412dp-land-mdpi")
    fun savedCubeStartsEightWideInLandscape() {
        val cubes = cubeController()
        try {
            compose.setContent { ScreenTheme { CubeScreen(cubes, null) } }
            compose.onNodeWithContentDescription("Columns").assert(hasText("8"))
            assertEquals((892f - 32f - 4f * 7) / 8, tileWidth("Rune Bear, 3 copies, Mainboard"), 1f)
        } finally { cubes.close() }
    }
}

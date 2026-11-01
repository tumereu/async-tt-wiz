package org.asyncttwiz.android

import android.content.Context
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import org.asyncttwiz.protocol.*
import org.asyncttwiz.storage.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.lang.reflect.Proxy

/** The card list editor's search page, the cube editor's Cards tab and Prepare cube, with placeholder card pictures. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w412dp-h892dp-port-mdpi")
class CardListScreenshotTest {
    @get:Rule val compose = createComposeRule()

    @Before fun clearPreferences() {
        ApplicationProvider.getApplicationContext<Context>().getSharedPreferences("deck-view", 0).edit().clear().commit()
    }

    private fun path(name: String, landscape: Boolean) = "$SCREENSHOTS/non-gameplay/$name-${if (landscape) "landscape" else "portrait"}.png"
    private fun orientation(landscape: Boolean) = if (landscape) "Landscape" else "Portrait"

    private val types = mapOf("Meadow Sentinel" to "Creature — Elf", "Rune Bear" to "Creature — Bear", "Ember Adept" to "Creature — Human",
        "Quiet Grove" to "Land", "Rune Bolt" to "Instant", "Rune Wall" to "Creature — Wall", "Aegis Ward" to "Enchantment",
        "Training Land" to "Land", "Rune Charm" to "Instant", "Rune Golem" to "Artifact Creature — Golem")
    private fun definition(name: String) = CardDefinition(name, listOf(DefinitionFace("face-$name", name, typeLine = types.getValue(name))))
    private fun printing(name: String) = CatalogPrinting("p-$name", name, "NEW", "9", "English", "", null, false, emptyList(),
        "{\"type\":\"${types.getValue(name)}\"}")

    // LOB-106, LOB-107, SYS-054
    @Test fun searchPortrait() = search(false)
    @Test @Config(qualifiers = "w892dp-h412dp-land-mdpi") fun searchLandscape() = search(true)

    private fun search(landscape: Boolean) {
        val fixture = ScreenFixtures().apply { configure("deck-edit") }
        val editor = fixture.importer
        val metadata = CatalogMetadata("2026-10-30", "fixture", "fixture")
        val baseline = CardListEdits.total(editor.entries!!)
        var entries = editor.entries!!
        repeat(2) { entries = CardListEdits.add(CardListKind.DECK, entries, listOf(printing("Rune Bear")), DeckSection.MAINBOARD).entries }
        entries = CardListEdits.add(CardListKind.DECK, entries, listOf(printing("Rune Bear")), DeckSection.MAYBEBOARD).entries
        entries = CardListEdits.add(CardListKind.DECK, entries, listOf(printing("Rune Bolt")), DeckSection.SIDEBOARD).entries
        editor.applyCards(entries, metadata)
        val names = listOf("Meadow Sentinel", "Quiet Grove", "Rune Bear", "Rune Bolt", "Rune Charm", "Rune Golem", "Rune Wall")
        val colors = setOf(SearchColor.G)
        val values = setOf(SearchManaValue.TWO)
        editor.search.seed(CardListSearchState(open = true, text = "rune", colors = colors, types = setOf(SearchType.CREATURE),
            values = values, query = cardSearchQuery("rune", colors, setOf(SearchType.CREATURE), values),
            cards = names.map { CatalogCard("o-$it", it, listOf(printing(it))) }, metadata = metadata, baseline = baseline))
        compose.setContent { ScreenTheme { Surface(Modifier.fillMaxSize()) {
            DeckImportScreen(editor, landscape, if (landscape) null else ({}), null)
        } } }
        compose.mainClock.advanceTimeBy(500)
        compose.onNodeWithTag("search-count-Rune Bear-main").assertTextEquals("2")
        compose.onNodeWithTag("search-count-Rune Bear-maybe").assertTextEquals("1")
        compose.onNodeWithText("Done · +4 cards").assertIsDisplayed()
        captureShot(path("card-search", landscape), "${orientation(landscape)} Add cards search page of the Training deck editor: " +
            "rune with the G mana symbol, generic 2 and Creature chips chosen, picture results with green Main, blue Side and amber Maybe count squares at their bottom left " +
            "(Rune Bear 2 Main and 1 Maybe), the Main | Side | Maybe destination on Main and Done · +4 cards.")
    }

    // LOB-105, LOB-107, DRF-003, SYS-054
    @Test fun cubeEditPortrait() = cubeEdit(false)
    @Test @Config(qualifiers = "w892dp-h412dp-land-mdpi") fun cubeEditLandscape() = cubeEdit(true)

    // DRF-010, LOB-057: the saved cube view has Edit as its only top button, then the name and Draft this cube.
    @Test fun savedCubePortrait() = cubeEdit(false, editing = false)
    @Test @Config(qualifiers = "w892dp-h412dp-land-mdpi") fun savedCubeLandscape() = cubeEdit(true, editing = false)

    private fun cubeEdit(landscape: Boolean, editing: Boolean = true) {
        val main = listOf("Meadow Sentinel", "Rune Bear", "Ember Adept", "Rune Bolt", "Aegis Ward", "Rune Golem", "Quiet Grove")
        val maybe = listOf("Rune Wall", "Rune Charm")
        val definitions = (main + maybe).associate { definition(it).let { card -> DeckJson.digest(card) to card } }
        fun digest(name: String) = definitions.entries.first { it.value.name == name }.key
        val saved = SavedCube(CubeSummary("cube", "Runes", "1", "now", "now", main.size.toLong(), maybeCopies = 2),
            main.map { SavedCubeEntry(digest(it), 1) } + maybe.map { SavedCubeEntry(digest(it), 1, SavedDeckSection.MAYBEBOARD) })
        val api = object : Cubes {
            override suspend fun listCubes(token: String, after: String?) = CubeList(listOf(saved.cube))
            override suspend fun readCube(token: String, id: String) = saved
            override suspend fun cubeDefinitions(token: String, id: String, value: ReadCubeDefinitions) =
                SubmittedDefinitions(value.digests.map { SubmittedDefinition(it, definitions.getValue(it)) })
            override suspend fun submit(token: String, value: SubmitDefinitions): DefinitionsAccepted = error("unused")
            override suspend fun create(token: String, value: CreateCube): SavedCube = error("unused")
            override suspend fun cubeDefinition(token: String, id: String, digest: String): SubmittedDefinition = error("unused")
            override suspend fun update(token: String, id: String, value: UpdateCube): SavedCube = error("unused")
            override suspend fun delete(token: String, id: String, value: DeleteCube): CubeDeleted = error("unused")
            override fun close() = Unit
        }
        val cubes = CubeController(api, { "token" }, { error("No lookup expected") }, { error("unused") }, Immediate, { it() })
        cubes.open("cube")
        if (!editing) {
            compose.setContent { ScreenTheme { Surface(Modifier.fillMaxSize()) {
                CubeScreen(cubes, if (landscape) null else ({}), draftCube = {}, landscape = landscape)
            } } }
            compose.onNodeWithText("Draft this cube").assertIsDisplayed()
            captureShot(path("cube-detail", landscape), "${orientation(landscape)} saved Runes cube in its picture view: Edit at the " +
                "top right as its only top button, the name with 7 total copies and Draft this cube, then Mainboard and Maybeboard.")
            return
        }
        cubes.edit()
        compose.setContent { ScreenTheme { Surface(Modifier.fillMaxSize()) { CubeScreen(cubes, if (landscape) null else ({}), landscape = landscape) } } }
        compose.onNodeWithText("7 main · 2 maybe").assertIsDisplayed()
        compose.onNodeWithTag("delete-list").assertIsDisplayed()
        captureShot(path("cube-edit-cards", landscape), "${orientation(landscape)} Edit cube screen for the Runes cube on its Cards tab: " +
            "Delete cube at the top right, Mainboard and Maybeboard sections as pictures by type in 5 (landscape 8) columns, a + at the right of each section header, and Save.")
    }

    // DRF-141, DRF-142, SYS-054
    @Test fun prepareCubePortrait() = prepareCube(false)
    @Test @Config(qualifiers = "w892dp-h412dp-land-mdpi") fun prepareCubeLandscape() = prepareCube(true)

    private fun prepareCube(landscape: Boolean) {
        val main = listOf("Meadow Sentinel", "Rune Bear", "Ember Adept", "Rune Bolt", "Aegis Ward", "Rune Golem", "Quiet Grove", "Training Land")
        val maybe = listOf("Rune Wall", "Rune Charm")
        val definitions = (main + maybe).associate { definition(it).let { card -> DeckJson.digest(card) to card } }
        fun digest(name: String) = definitions.entries.first { it.value.name == name }.key
        val cube = PodCube("Runes", main.map { SavedCubeEntry(digest(it), if (it == "Training Land") 2 else 1) } +
            maybe.map { SavedCubeEntry(digest(it), 1, SavedDeckSection.MAYBEBOARD) })
        val api = Proxy.newProxyInstance(Pods::class.java.classLoader, arrayOf(Pods::class.java)) { _, method, args ->
            when (method.name) {
                "cube" -> cube
                "cubeDefinitions" -> SubmittedDefinitions((args[2] as ReadCubeDefinitions).digests.map { SubmittedDefinition(it, definitions.getValue(it)) })
                else -> error("unused ${method.name}")
            }
        } as Pods
        val preparation = PodCubePreparation(api, { "token" }, Immediate, { it() }) {}
        preparation.open("00000000-0000-4000-8000-000000000001")
        compose.setContent { ScreenTheme { Surface(Modifier.fillMaxSize()) { PodCubePreparationScreen(preparation) } } }
        compose.onNodeWithText("Mainboard · 9").assertIsDisplayed()
        captureShot(path("prepare-cube", landscape), "${orientation(landscape)} Prepare cube for a pod using the Runes cube: " +
            "Mainboard and Maybeboard sections as pictures by type, with Done at the top right, before any card is moved.")
    }
}

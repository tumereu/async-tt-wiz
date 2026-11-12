package org.asyncttwiz.android

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule

import arcanum.engine.decision.Decision
import org.asyncttwiz.android.EngineGameFixture.Choices
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Owner playtest 2026-11-05 evening (a fetch land): the library search panel shows its source at the right as other
 * choice panels do (STK-044), says what is searched, keeps its selection while hidden with the eye, and shows the
 * searched library's other cards in an unselectable "Other cards" section (STK-056). Alice's deck holds Cinder Trails
 * (a fetch-style land), Mountains and Ember Divinations.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w412dp-h892dp-port-mdpi")
class LibrarySearchPanelTest {
    @get:Rule val compose = createComposeRule()

    @After fun forget() = AutomatumBoards.reset()

    private fun render(kit: EngineTableKit) {
        compose.setContent {
            MaterialTheme {
                NoRipples {
                    CompositionLocalProvider(LocalEngineRoutes provides kit.routes) {
                        Box(Modifier.fillMaxSize()) {
                            TableSurface(kit.f.game, kit.alice.play(), kit.f.a, false, Modifier.fillMaxSize(), connected = true,
                                onIntent = kit.toolkit::handle, toolkit = kit.toolkit,
                                runtime = TableRuntimeHooks(navigation = {}, chat = {}, undo = { kit.routes.undo() }, controlsConnected = true))
                        }
                    }
                }
            }
        }
        compose.waitForIdle()
    }

    /** Alice plays a Cinder Trail and activates it: its ability resolves to her search. */
    private fun search(): EngineTableKit {
        val kit = EngineTableKit(EngineCombatScenario(EngineGameFixture(aliceCards = listOf(Choices.TRAIL, "Mountain", Choices.DIVINATION))))
        kit.opening()
        val trail = kit.atw(kit.hand(Choices.TRAIL).first())
        assertTrue(kit.toolkit.cast.start(trail), "the Trail is played: ${kit.toolkit.error}")
        kit.playOn("the Trail on the battlefield") { trail in kit.field(Choices.TRAIL).map(kit::atw) && kit.aliceMain() }
        kit.tap(kit.item(trail))
        kit.playOn("Alice's search") { kit.state().pending?.decision is Decision.ChooseObjects }
        assertIs<Decision.ChooseObjects>(kit.state().pending?.decision)
        return kit
    }

    private val inPanel = hasAnyAncestor(hasContentDescription("Automatum choice"))

    // STK-056: a search's title is its search sentence up to the first comma, an activated ability's cost left out
    @Test fun aSearchIsTitledByWhatIsSearched() {
        fun title(text: String) = ObjectsShape.of(Decision.ChooseObjects(arcanum.engine.decision.DecisionId("d"),
            arcanum.engine.state.PlayerId("alice"), null, emptyList(), 0, 1, text)).let { it.title to it.search }
        assertEquals("Search your library for a basic land card" to true,
            title("{T}, Pay 1 life, Sacrifice this land: Search your library for a basic land card, put it onto the battlefield, then shuffle."))
        assertEquals("Search target opponent's library for an artifact card" to true,
            title("Search target opponent's library for an artifact card, exile it, then that player shuffles."))
        // Owner, 2026-11-12 (Recruiter of the Guard): a "you may search" inside a trigger's sentence is a search too
        assertEquals("Search your library for a creature card with toughness 2 or less" to true,
            title("When this creature enters, you may search your library for a creature card with toughness 2 or less, reveal it, put it into your hand, then shuffle."))
        assertEquals("Search" to true, title("Search"))
        assertEquals(false, title("Whenever an opponent searches their library").second)
        assertEquals("Sacrifice a creature" to false, title("Sacrifice a creature"))
    }

    /** The panel's card named [name] (the first such), [chosen] or not. */
    private fun card(name: String, chosen: Boolean = false) =
        compose.onAllNodes(hasContentDescription(if (chosen) "$name, chosen" else name) and inPanel, useUnmergedTree = true)

    // STK-056, STK-044: the fetch land's search: titled by what is searched, the Trail's ability (sacrificed to pay for it)
    // at the panel's right as its token, the basic lands to pick, then the library's other cards, unselectable
    @Test fun aSearchShowsItsSourceWhatIsSearchedAndTheOtherCards() {
        val kit = search()
        render(kit)
        compose.onNode(hasText("Search your library for a basic land card") and isHeading()).assertExists()
        val source = compose.onNodeWithTag(CHOICE_SOURCE_TAG, useUnmergedTree = true)
        source.assert(hasContentDescription(Choices.TRAIL))
        compose.onNode(hasContentDescription("Activated ability") and hasAnyAncestor(hasTestTag(CHOICE_SOURCE_TAG)), useUnmergedTree = true).assertExists()
        assertEquals(5, card("Mountain").fetchSemanticsNodes().size, "the basic lands to pick")
        compose.onNode(hasText("Other cards") and isHeading(), useUnmergedTree = true).assertExists()
        val library = kit.state().player(kit.me).library.size
        compose.onNodeWithContentDescription("Other cards, ${library - 5}", useUnmergedTree = true).assertExists()
        // An other card can't be chosen
        card(Choices.DIVINATION).onFirst().performClick()
        compose.waitForIdle()
        assertTrue(card(Choices.DIVINATION, chosen = true).fetchSemanticsNodes().isEmpty(), "an other card isn't chosen")
        compose.onRoot().captureShot("$SCREENSHOTS/choice-source/library-search.png",
            "Alice activated her Cinder Trail (sacrificed to pay for it): the panel above her hand is titled \"Search your library for a basic land card\", the Trail's ability sits at its right as its token, the five Mountains are hers to pick and the library's other cards follow dimmed under \"Other cards\".")
    }

    // STK-056, STK-044: a picked card stays picked while the panel is hidden with the eye and shown again; OK finds it
    @Test fun aSearchKeepsItsPickWhileHidden() {
        val kit = search()
        render(kit)
        card("Mountain").onFirst().performClick()
        compose.waitForIdle()
        card("Mountain", chosen = true).assertCountEquals(1)
        compose.onNodeWithContentDescription("Hide Automatum panel").performClick()
        compose.waitForIdle()
        assertTrue(card("Mountain").fetchSemanticsNodes().isEmpty(), "hidden")
        compose.onNodeWithContentDescription("Show Automatum panel").performClick()
        compose.waitForIdle()
        card("Mountain", chosen = true).assertCountEquals(1)
        val before = kit.field("Mountain").size
        compose.onNodeWithText("OK").assertIsEnabled().performClick()
        kit.playOn("the Mountain found") { kit.field("Mountain").size == before + 1 }
    }
}

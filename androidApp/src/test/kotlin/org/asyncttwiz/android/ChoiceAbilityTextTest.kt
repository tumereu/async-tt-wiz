package org.asyncttwiz.android

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import arcanum.engine.decision.Decision
import org.asyncttwiz.android.EngineGameFixture.Choices
import org.asyncttwiz.protocol.game.GameDevice
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * STK-181 (owner gallery review 2026-11-25: every choice dialog shows its ability's whole text for context): a spell's choices show the
 * spell's whole text under their title, also those with words of their own (a search, a "you may") and the cast panel's X, never only
 * the sentence they ask about. Alice casts each spell on her own table.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w412dp-h892dp-port-mdpi")
class ChoiceAbilityTextTest {
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

    /** Alice casts her free [card] and plays on until her own choice of the [kind] is open. */
    private fun castUntil(card: String, kind: String, open: (Decision) -> Boolean): EngineTableKit {
        val kit = EngineTableKit(EngineCombatScenario(EngineGameFixture(aliceCards = listOf(card, "Mountain"))))
        kit.opening()
        assertTrue(kit.toolkit.cast.start(kit.atw(kit.hand(card).first())), "the cast begins: ${kit.toolkit.error}")
        // A target to pick on casting (Bob, the one opponent): his
        kit.toolkit.cast.pick?.let { pick -> assertTrue(kit.toolkit.cast.answer(pick.answer(listOf(pick.options.first()))), "the target picked") }
        kit.playOn("Alice's $kind", rounds = 40) {
            val d = kit.state().pending?.decision
            d != null && d.player == kit.me && open(d) && kit.alice.device.status == GameDevice.Status.YourMove
        }
        render(kit)
        return kit
    }

    // STK-181, STK-056: a spell's search, titled by what is searched, shows the spell's whole text under the title
    @Test fun aSpellsSearchShowsItsWholeText() {
        castUntil(Choices.SEEKING, "search") { it is Decision.ChooseObjects }
        compose.onNode(hasText("Search your library for a basic land card") and isHeading()).assertExists()
        compose.onNodeWithContentDescription("Question: $SEEKING_TEXT").assertExists()
        compose.onRoot().captureShot("$SCREENSHOTS/choice-text/spell-search.png",
            "Alice cast her Ember Seeking: the search panel titled \"Search your library for a basic land card\" has the Seeking's name and whole text under its title, then her Mountains to pick and the library's other cards.")
    }

    // STK-181, STK-182: a spell's "you may" shows the spell's whole text above it; its own words only where they say more
    @Test fun aSpellsYouMayShowsItsWholeText() {
        castUntil(Choices.MUSING, "you may") { it is Decision.May }
        compose.onNodeWithContentDescription("Question: $MUSING_TEXT").assertExists()
        compose.onNodeWithText("Yes").assertExists()
        compose.onRoot().captureShot("$SCREENSHOTS/choice-text/spell-may.png",
            "Alice cast her Ember Musing and gained 2 life: its \"you may\" panel has the Musing's name and whole text under its title, then No and Yes.")
    }

    // STK-136, STK-181, STK-023: the cast panel's X of a spell shows the spell's whole text, both its sentences naming X
    @Test fun theCastPanelsXShowsTheSpellsWholeText() {
        val kit = EngineTableKit(EngineCombatScenario(EngineGameFixture(aliceCards = listOf(Choices.SURGE, "Mountain"))))
        kit.opening()
        kit.playLand()
        assertTrue(kit.toolkit.cast.start(kit.atw(kit.hand(Choices.SURGE).first())), "the cast begins: ${kit.toolkit.error}")
        render(kit)
        compose.onNodeWithContentDescription("Question: $SURGE_TEXT").assertExists()
        compose.onRoot().captureShot("$SCREENSHOTS/choice-text/cast-x.png",
            "Alice played a Mountain and began casting her Ember Surge: the cast panel titled \"Cast Ember Surge\" has the Surge's whole text under it, both sentences naming X, over the X to choose.")
    }

    /** STK-230: the text highlighted in the open panel, or null when nothing is. */
    private fun highlighted(): String? = compose.onAllNodes(hasContentDescription("Highlighted: ", substring = true))
        .fetchSemanticsNodes().singleOrNull()?.config?.get(androidx.compose.ui.semantics.SemanticsProperties.ContentDescription)
        ?.single()?.removePrefix("Highlighted: ")

    // STK-230, STK-181, STK-182: a spell's "you may" shines in the spell's whole text as the target bar's phrase picked now does
    @Test fun aSpellsYouMayShinesInItsWholeText() {
        castUntil(Choices.MUSING, "you may") { it is Decision.May }
        compose.onNodeWithContentDescription("Question: $MUSING_TEXT").assertExists()
        assertEquals("You may draw a card.", highlighted())
        compose.onRoot().captureShot("$SCREENSHOTS/ability-highlight/spell-may.png",
            "Alice cast her Ember Musing and gained 2 life: its \"you may\" panel shows the Musing's whole text with \"You may draw a card.\" " +
                "in violet on a softly pulsing light violet, as the target bar marks the phrase picked now, then No and Yes.")
    }

    // STK-230, STK-056: a spell's search shines its search sentence in the spell's whole text
    @Test fun aSpellsSearchShinesItsSentence() {
        castUntil(Choices.SEEKING, "search") { it is Decision.ChooseObjects }
        assertEquals("Search your library for a basic land card, reveal it, put it into your hand, then shuffle.", highlighted())
        compose.onRoot().captureShot("$SCREENSHOTS/ability-highlight/spell-search.png",
            "Alice cast her Ember Seeking: the search panel shows the Seeking's whole text with its search sentence highlighted in violet, " +
                "\"You gain 2 life.\" plain after it, then her Mountains to pick and the library's other cards.")
    }

    // STK-230, STK-224: a look at the other player's hand: the choice from it shines its "you may choose" sentence
    @Test fun aLookAtAHandShinesItsChoice() {
        castUntil(Choices.PEEK, "choice from Bob's hand") { it is Decision.ChooseObjects }
        assertEquals("You may choose a nonland card from it.", highlighted())
        compose.onRoot().captureShot("$SCREENSHOTS/ability-highlight/look-at-hand.png",
            "Alice cast her Ember Peek at Bob: she looks at his hand, and the choice panel shows the Peek's whole text with " +
                "\"You may choose a nonland card from it.\" highlighted in violet, over Bob's cards to choose from.")
    }

    // STK-230: never a guess: a scry's question ("Put on the bottom") has no words of the spell's text, so nothing shines in it
    @Test fun aScryShinesNothing() {
        castUntil(Choices.PORTENT, "scry") { it is Decision.ChooseObjects }
        compose.onNodeWithContentDescription("Question: Scry 2. You gain 1 life.").assertExists()
        assertNull(highlighted())
        compose.onRoot().captureShot("$SCREENSHOTS/ability-highlight/scry-nothing.png",
            "Alice cast her Ember Portent: the Scry 2 panel shows the Portent's whole text with nothing highlighted, because the scry's " +
                "question isn't worded in the text, so which part it is can't be known exactly; then the two cards between Top and Bottom.")
    }

    private companion object {
        const val SEEKING_TEXT = "Search your library for a basic land card, reveal it, put it into your hand, then shuffle. You gain 2 life."
        const val MUSING_TEXT = "You gain 2 life. You may draw a card. If you do, discard a card."
        const val SURGE_TEXT = "Ember Surge deals X damage to any target. You gain X life."
    }
}

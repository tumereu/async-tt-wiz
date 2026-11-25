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

    private companion object {
        const val SEEKING_TEXT = "Search your library for a basic land card, reveal it, put it into your hand, then shuffle. You gain 2 life."
        const val MUSING_TEXT = "You gain 2 life. You may draw a card. If you do, discard a card."
        const val SURGE_TEXT = "Ember Surge deals X damage to any target. You gain X life."
    }
}

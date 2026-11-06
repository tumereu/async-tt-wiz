package org.asyncttwiz.android

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import arcanum.engine.state.PlayerId
import org.asyncttwiz.android.EngineGameFixture.Choices
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Owner playtest 2026-11-05 evening: the cast panel has no OK before Cast and no Cancel. Alice's deck is Ember Bursts ({X}:
 * X damage to any target) and Mountains; with one Mountain X goes from 0 to 1 and no further, and Cast answers X and goes
 * on to the target.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w412dp-h892dp-port-mdpi")
class CastPanelChoicesTest {
    @get:Rule val compose = createComposeRule()

    @After fun forget() = AutomatumBoards.reset()

    private val kit = EngineTableKit(EngineCombatScenario(EngineGameFixture(aliceCards = listOf(Choices.BURST, "Mountain"))))

    private fun render() {
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

    // STK-023, STK-052 (owner playtest 2026-11-05 evening): an X cast has no OK of its own and no Cancel; − / + stop at the
    // least X and the most one Mountain pays; Cast answers X and the cast goes on to its target, then resolves
    @Test fun anXCastHasNoOkAndItsXIsBounded() {
        kit.opening()
        kit.playLand()
        val burst = kit.atw(kit.hand(Choices.BURST).first())
        assertTrue(kit.toolkit.cast.start(burst), "the cast begins: ${kit.toolkit.error}")
        render()
        compose.onNodeWithContentDescription("Cast panel").assertExists()
        compose.onNodeWithContentDescription("Value 0").assertExists()
        compose.onNodeWithContentDescription("One less").assertIsNotEnabled()
        compose.onNodeWithContentDescription("One more").assertIsEnabled().performClick()
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Value 1").assertExists()
        compose.onNodeWithContentDescription("One more").assertIsNotEnabled()
        compose.onNodeWithText("OK").assertDoesNotExist()
        compose.onNodeWithText("Cancel").assertDoesNotExist()
        compose.onNodeWithText("Set the value, then Cast.").assertExists()
        compose.onNodeWithText("Cast").assertIsEnabled()
        compose.onRoot().captureShot("$SCREENSHOTS/cast-panel/x-cast.png",
            "Alice began casting Ember Burst ({X}) with one Mountain and tapped + once: the cast panel shows X at 1 with + greyed at the most her mana pays, and only the eye and Cast below, no OK and no Cancel.")
        compose.onNodeWithText("Cast").performClick()
        compose.waitForIdle()
        assertTrue(kit.toolkit.cast.targeting, "X answered, the target is next")
        compose.onNode(hasText("Choose any target") and isHeading()).assertExists()
        compose.onNodeWithText("Cancel").assertDoesNotExist()
        kit.tap(kit.scene().items.single { it.kind == TableItemKind.LIFE && it.owner == kit.f.b })
        kit.playOn("the Burst resolving") { kit.state().player(PlayerId(kit.f.b)).life == 19 }
        assertEquals(19, kit.state().player(PlayerId(kit.f.b)).life)
    }

    // STK-023: with no mana at all X can only be 0 (Ember Burst costs nothing then); + stays greyed
    @Test fun withNoManaXStaysAtItsLeast() {
        kit.opening()
        val burst = kit.atw(kit.hand(Choices.BURST).first())
        assertTrue(kit.toolkit.cast.start(burst), "the cast begins: ${kit.toolkit.error}")
        render()
        compose.onNodeWithContentDescription("Value 0").assertExists()
        compose.onNodeWithContentDescription("One more").assertIsNotEnabled()
        compose.onNodeWithText("Cast").assertIsEnabled()
    }
}

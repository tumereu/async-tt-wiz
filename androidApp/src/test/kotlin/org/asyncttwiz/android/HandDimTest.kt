package org.asyncttwiz.android

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createComposeRule
import org.asyncttwiz.android.EngineGameFixture.Choices
import org.asyncttwiz.protocol.PlayZone
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** ZON-095 (owner notes 2026-11-14): while a cast, a choice or another panel is open, your hand is dimmed and takes no taps,
 * unless the choice is about a card in your hand. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w412dp-h892dp-port-mdpi")
class HandDimTest {
    @get:Rule val compose = createComposeRule()
    private var version by mutableIntStateOf(0)

    @After fun forget() = AutomatumBoards.reset()

    private fun render(kit: EngineTableKit) {
        compose.setContent {
            MaterialTheme {
                NoRipples {
                    CompositionLocalProvider(LocalEngineRoutes provides kit.routes) {
                        Box(Modifier.fillMaxSize()) {
                            version
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

    // ZON-095 (owner notes 2026-11-14): Alice opens her Ember Kit's cast panel: her hand is dimmed, and a tap on another hand card
    // neither raises it nor shakes it; the panel closed, her hand is lit and taps raise again
    @Test fun anOpenCastPanelDimsTheHandAndTakesNoTaps() {
        val kit = EngineTableKit(EngineCombatScenario(EngineGameFixture(aliceCards = listOf(Choices.KIT, Choices.SHARD, "Mountain"))))
        val toolkit = kit.toolkit
        kit.opening()
        kit.playLand()
        assertFalse(toolkit.handDimmed(kit.alice.play()), "nothing open: the hand is lit")
        val card = kit.atw(kit.hand(Choices.KIT).first())
        render(kit)
        kit.tap(kit.item(card, PlayZone.HAND))
        kit.tap(kit.item(card, PlayZone.HAND))
        assertTrue(toolkit.cast.open, "the Kit's cast panel: ${toolkit.error}")
        assertTrue(toolkit.handDimmed(kit.alice.play()), "the cast panel dims the hand")
        val other = kit.atw(kit.hand(Choices.SHARD).first())
        val shakes = toolkit.shaking
        kit.tap(kit.item(other, PlayZone.HAND))
        assertNull(toolkit.hand.raised, "a dimmed hand card isn't raised")
        assertEquals(shakes, toolkit.shaking, "nor shaken")
        assertTrue(toolkit.cast.open, "the cast stays open")
        version++
        compose.waitForIdle()
        captureShot("$SCREENSHOTS/hand/dimmed-under-cast-panel.png",
            "Alice opened her Ember Kit's cast panel: her hand below it is dimmed and takes no taps until the panel closes.")
        toolkit.cast.cancel()
        assertFalse(toolkit.handDimmed(kit.alice.play()), "the panel closed: the hand is lit")
        kit.tap(kit.item(other, PlayZone.HAND))
        assertEquals(other, toolkit.hand.raised, "a tap raises again")
    }

    // ZON-095 (owner notes 2026-11-14): Alice's discard at her cleanup step is a choice about her hand cards: her hand stays lit
    @Test fun aChoiceAboutHandCardsLeavesTheHandLit() {
        val kit = EngineTableKit()
        kit.toAliceDiscard()
        render(kit)
        assertTrue(kit.toolkit.flows.answering != null, "Alice's discard open on her table")
        assertFalse(kit.toolkit.handDimmed(kit.alice.play()), "a discard is about the hand: it stays lit")
    }
}

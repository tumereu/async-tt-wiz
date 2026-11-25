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
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * STK-229 (owner-reported 2026-11-25): the player who chooses one of two separated piles sees both piles: Bob casts Ember Destiny,
 * separating his top three cards into a face-up pile of one and a face-down pile of two, and Alice chooses.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w412dp-h892dp-port-mdpi")
class PileChoiceTest {
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

    private fun area(label: String) = hasAnyAncestor(hasContentDescription("$label area"))

    // STK-229, STK-044: Alice's pile choice is titled "Choose a pile" with Bob's Ember Destiny as its source; the face-up pile's card is
    // drawn as a card, the face-down pile's two as card backs, each pile with its count and a Choose button; Choose answers
    @Test fun theChooserSeesBothPilesAndChoosesOne() {
        val kit = EngineTableKit(EngineCombatScenario(EngineGameFixture(bobCards = listOf(Choices.DESTINY, "Mountain"))))
        kit.opening()
        kit.bobCasts(Choices.DESTINY, free = true)
        kit.scenario.confirmStops[kit.f.a] = false
        assertTrue(kit.toolkit.automatumStep(TableActionIntent.End(kit.scope)), "End turn")
        kit.playOn("Alice choosing one of Bob's piles", rounds = 40) {
            val d = kit.state().pending?.decision
            d is Decision.ChooseOption && d.piles != null && d.player == kit.me && kit.alice.device.status == GameDevice.Status.YourMove
        }
        val decision = assertIs<Decision.ChooseOption>(kit.state().pending?.decision)
        val piles = assertNotNull(decision.piles)
        assertTrue(piles.secondFaceDown, "the second pile lies face down: $piles")
        assertEquals(1, piles.first.size, "Bob put one card in the face-up pile: $piles")
        render(kit)
        compose.onNode(hasText("Choose a pile") and isHeading()).assertExists()
        compose.onNodeWithTag(CHOICE_SOURCE_TAG, useUnmergedTree = true).assert(hasContentDescription(Choices.DESTINY))
        compose.onNodeWithText("Face-up pile · 1").assertExists()
        compose.onNodeWithText("Face-down pile · 2").assertExists()
        // The face-up card is drawn as itself; the face-down ones only as backs
        fun name(id: arcanum.engine.state.ObjectId) = kit.state().obj(id)?.definition?.faces?.first()?.name
        val faceUp = assertNotNull(name(piles.first.single()))
        compose.onNode(hasContentDescription(faceUp) and area("Face-up pile"), useUnmergedTree = true).assertExists()
        assertEquals(2, compose.onAllNodes(hasContentDescription("Hidden card") and area("Face-down pile"), useUnmergedTree = true)
            .fetchSemanticsNodes().size)
        compose.onNodeWithTag("pile-choose-first", useUnmergedTree = true).assertExists()
        compose.onRoot().captureShot("$SCREENSHOTS/choice-source/pile-choice.png",
            "Bob's Ember Destiny resolved: he put one of his top three cards face up and two face down, and Alice chooses: the panel is titled \"Choose a pile\" with the Destiny at its right, the Face-up pile shows its card, the Face-down pile two card backs, each with its count and a Choose button.")
        val handBefore = kit.state().player(kit.other).hand.size
        compose.onNodeWithTag("pile-choose-second", useUnmergedTree = true).performClick()
        compose.waitForIdle()
        kit.playOn("Alice's answer taken") { kit.state().pending?.decision?.id != decision.id }
        val bob = kit.state().player(kit.other)
        // The face-down pile went to Bob's hand, the face-up one to his graveyard
        assertEquals(handBefore + 2, bob.hand.size, "Bob's hand: ${bob.hand}")
        assertTrue(bob.graveyard.any { name(it) == faceUp },
            "the face-up pile went to Bob's graveyard")
    }
}

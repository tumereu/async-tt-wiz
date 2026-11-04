package org.asyncttwiz.android

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import org.asyncttwiz.protocol.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** A face-down card's front is its owner's alone; the owner sees it plainly marked as face down. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w412dp-h892dp-port-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class FaceDownInspectionTest {
    @get:Rule val compose = createComposeRule()
    private val f = TableStatesFixture
    private val front = DefinitionFace("front", "Secret morph", rulesText = "Morph {3}{U}")

    /** Card a's face down in [container] (a's Battlefield/Exile/Graveyard or the shared Stack). */
    private fun state(container: String): Pair<PlayState, PlayCard> {
        val original = f.source(f.a, "1/3")
        val card = original.copy(containerId = f.id(container), covered = true, explicitlyCovered = true, corner = "2/2", placement =
            original.placement.takeIf { container.endsWith("BATTLEFIELD") })
        val play = f.initial.copy(definitions = f.initial.definitions.map {
            if (it.digest == card.definition) it.copy(definition = CardDefinition("Secret morph", listOf(front.copy(faceId = card.faceId)))) else it
        }, board = f.initial.board.copy(cards = f.initial.board.cards.map { if (it.id == card.id) card else it },
            containers = f.initial.board.containers.map { c -> when {
                c.id == card.containerId -> c.copy(cards = (c.cards - card.id) + card.id)
                card.id in c.cards -> c.copy(cards = c.cards - card.id)
                else -> c
            } }))
        return play to play.board.cards.single { it.id == card.id }
    }

    // TBL-109, ZON-075
    @Test fun onlyTheOwnerMayInspectAFaceDownFrontWhereverItLies() {
        // A real card is never in the Stack itself (STK-003).
        for (container in listOf("a/BATTLEFIELD", "a/EXILE", "a/GRAVEYARD")) {
            val (play, card) = state(container)
            assertTrue(ManualDisclosure.permittedFaces(play, card, f.a, inspecting = true).isNotEmpty(), container)
            assertTrue(ManualDisclosure.permittedFaces(play, card, f.b, inspecting = true).isEmpty(), container)
            assertTrue(ManualDisclosure.permittedFaces(play, card, f.b, inspecting = false).isEmpty(), container)
            assertTrue(ManualDisclosure.faceDownFrontHidden(play, card, f.b), container)
            assertFalse(ManualDisclosure.faceDownFrontHidden(play, card, f.a), container)
            // The face-up card is not covered by the rule: an ordinary public front.
            assertFalse(ManualDisclosure.faceDownFrontHidden(play, card.copy(covered = false), f.b), container)
        }
    }

    // SYS-039, TBL-088, TBL-109, ZON-075
    @Test fun ownerSeesTheFrontUnderAFaceDownMarkAndOnlyADeliberateInspectShowsItToTheOpponent() {
        val viewer = mutableStateOf(f.a)
        val container = mutableStateOf("a/BATTLEFIELD")
        val explicit = mutableStateOf(false)
        compose.setContent {
            val (play, card) = state(container.value)
            MaterialTheme {
                // Only the deliberate, logged "Inspect other side" (explicit) shows the opponent the front.
                GameplayCardInspection(TableBoard.of(play), viewer.value, ManualInspection(play.gameId, play.id, card.id,
                    listOf(card.faceId), explicit = explicit.value), {})
            }
        }
        compose.onNodeWithText("Face down").assertIsDisplayed()
        compose.onAllNodesWithText("Secret morph").onFirst().assertExists()
        for (place in listOf("a/BATTLEFIELD", "a/EXILE", "a/GRAVEYARD")) {
            compose.runOnIdle { viewer.value = f.b; container.value = place; explicit.value = false }
            compose.waitForIdle()
            compose.onNodeWithText("Covered card").assertIsDisplayed()
            compose.onAllNodesWithText("Secret morph", substring = true).assertCountEquals(0)
            compose.onAllNodesWithText("Morph {3}{U}", substring = true).assertCountEquals(0)
            compose.runOnIdle { explicit.value = true }
            compose.waitForIdle()
            compose.onNodeWithText("Face down").assertIsDisplayed()
            compose.onNodeWithText("Morph {3}{U}").assertExists()
        }
        compose.runOnIdle { viewer.value = f.a; container.value = "a/GRAVEYARD"; explicit.value = false }
        compose.waitForIdle()
        compose.onNodeWithText("Face down").assertIsDisplayed()
        compose.onNodeWithText("Morph {3}{U}").assertExists()
    }

    // SYS-065, TBL-109, ZON-075: a face-down Stack entry's front is its hand card's holder's alone.
    @Test fun aFaceDownStackEntryShowsItsHolderTheMarkedFrontAndTheOpponentNothing() {
        val (base, held) = state("a/HAND")
        val card = held.copy(explicitlyCovered = false)
        val entry = f.id("face-down-entry")
        val play = base.copy(board = base.board.copy(cards = base.board.cards.map { if (it.id == card.id) card else it }, stackEffects = listOf(StackEffect(entry, card.id, faceDown = true)),
            containers = base.board.containers.map { if (it.zone == PlayZone.STACK) it.copy(cards = listOf(entry)) else it }))
        val inspected = CombatStackRules.inspection(play.board, entry)!!
        assertTrue(ManualDisclosure.permittedFaces(play, inspected, f.b, inspecting = true).isEmpty())
        val viewer = mutableStateOf(f.a)
        compose.setContent {
            MaterialTheme {
                GameplayCardInspection(TableBoard.of(play), viewer.value, ManualInspection(play.gameId, play.id, entry,
                    ManualDisclosure.permittedFaces(play, inspected, viewer.value, inspecting = true).map { it.faceId },
                    stackEntryId = entry), {})
            }
        }
        compose.onNodeWithText("Face down").assertIsDisplayed()
        compose.onNodeWithText("Morph {3}{U}").assertExists()
        compose.runOnIdle { viewer.value = f.b }
        compose.waitForIdle()
        compose.onNodeWithText("Covered card").assertIsDisplayed()
        compose.onAllNodesWithText("Secret morph", substring = true).assertCountEquals(0)
        compose.onAllNodesWithText("Morph {3}{U}", substring = true).assertCountEquals(0)
    }
}

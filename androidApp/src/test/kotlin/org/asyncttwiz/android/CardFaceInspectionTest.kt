package org.asyncttwiz.android

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureScreenRoboImage
import org.asyncttwiz.protocol.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Peek and Inspect open on the face a card currently shows; hidden faces stay hidden. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w412dp-h892dp-port-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CardFaceInspectionTest {
    @get:Rule val compose = createComposeRule()
    private val f = TableStatesFixture
    private val front = DefinitionFace(f.id("front"), "Daylight healer", typeLine = "Creature — Human Cleric",
        rulesText = "Front rules text.", power = "1", toughness = "1")

    /** Card a's transformed (showing its back face) in [container]. */
    private fun transformed(container: String = "a/BATTLEFIELD"): Pair<PlayState, PlayCard> {
        val original = f.source(f.a, "1/3")
        val back = DefinitionFace(original.faceId, "Nightfall horror", typeLine = "Creature — Horror",
            rulesText = "Back rules text.", power = "4", toughness = "4")
        val card = original.copy(containerId = f.id(container), placement = original.placement.takeIf { container.endsWith("BATTLEFIELD") },
            covered = !container.endsWith("BATTLEFIELD"))
        val play = f.initial.copy(definitions = f.initial.definitions.map {
            if (it.digest == card.definition) it.copy(definition = CardDefinition("Daylight healer // Nightfall horror",
                listOf(front, back), layout = "transform")) else it
        }, board = f.initial.board.copy(cards = f.initial.board.cards.map { if (it.id == card.id) card else it },
            containers = f.initial.board.containers.map { c -> when {
                c.id == card.containerId -> c.copy(cards = (c.cards - card.id) + card.id)
                card.id in c.cards -> c.copy(cards = c.cards - card.id)
                else -> c
            } }))
        return play to play.board.cards.single { it.id == card.id }
    }

    private data class Shown(val play: PlayState, val card: PlayCard, val viewer: String, val peek: Boolean)
    private val shown = mutableStateOf<Shown?>(null)

    /** Shows the hold peek or the Inspect dialog as [viewer] would open it in a game. */
    private fun show(play: PlayState, card: PlayCard, viewer: String, peek: Boolean) {
        val value = Shown(play, card, viewer, peek)
        if (shown.value == null) {
            shown.value = value
            compose.setContent {
                val (p, c, v, k) = shown.value ?: return@setContent
                MaterialTheme {
                    // A new inspection per case, as each hold or Inspect action makes one.
                    androidx.compose.runtime.key(shown.value) {
                        GameplayCardInspection(p, v, ManualInspection(p.gameId, p.id, c.id,
                            ManualDisclosure.permittedFaces(p, c, v, inspecting = true).map { it.faceId }), {}, peek = k)
                    }
                }
            }
        } else compose.runOnIdle { shown.value = value }
        compose.waitForIdle()
    }

    // TBL-106
    @Test fun peekShowsTheBackFaceOfATransformedCard() {
        val (play, card) = transformed()
        show(play, card, f.a, peek = true)
        compose.onNodeWithText("Back rules text.").assertExists()
        compose.onNodeWithText("Front rules text.").assertDoesNotExist()
    }

    // TBL-106, TBL-107
    @OptIn(ExperimentalRoborazziApi::class)
    @Test fun inspectOpensOnTheBackFaceAndStillSwitchesToTheFront() {
        val (play, card) = transformed()
        show(play, card, f.a, peek = false)
        compose.onNodeWithText("Back rules text.").assertExists()
        compose.onNodeWithText("Front rules text.").assertDoesNotExist()
        compose.onNodeWithText("2 / 2").assertIsDisplayed()
        compose.onNodeWithText("Daylight healer // Nightfall horror").assertIsDisplayed()
        compose.onNodeWithContentDescription("Next face").assertIsNotEnabled()
        File("$SCREENSHOTS/inspection").mkdirs()
        captureScreenRoboImage("$SCREENSHOTS/inspection/table-inspect-back.png")
        compose.onNodeWithContentDescription("Previous face").assertIsEnabled().performClick()
        compose.onNodeWithText("Front rules text.").assertExists()
        compose.onNodeWithText("1 / 2").assertIsDisplayed()
        compose.onNodeWithContentDescription("Previous face").assertIsNotEnabled()
    }

    // TBL-106
    @Test fun aFlippedFlipCardOpensTurnedOnItsFlippedHalf() {
        val (transformedPlay, card) = transformed()
        val play = transformedPlay.copy(definitions = transformedPlay.definitions.map {
            if (it.digest == card.definition) it.copy(definition = it.definition.copy(layout = "flip")) else it })
        val definition = play.definitions.single { it.digest == card.definition }.definition
        assertTrue(flippedOnTable(definition, card.faceId))
        assertFalse(flippedOnTable(definition, front.faceId))
        assertFalse(flippedOnTable(definition.copy(layout = "transform"), card.faceId))
        // Owner (both halves permitted) and opponent (only the shown half) alike.
        for (viewer in listOf(f.a, f.b)) {
            show(play, card, viewer, peek = false)
            compose.onNodeWithText("Back rules text.").assertExists()
            compose.onNodeWithContentDescription("Rotate back").assertExists().performClick()
            compose.onNodeWithContentDescription("Rotate card").assertExists()
        }
        // The controller: opening turned shows 180°; switching faces resets the personal turn.
        val controller = CardInspectionController()
        controller.open(InspectedCard(definition, listOf(null, null)), 1, turned = true)
        assertEquals(180, controller.state!!.degrees)
        controller.face(0)
        assertEquals(0, controller.state!!.degrees)
        // Cards without a rotate button never open turned.
        controller.open(InspectedCard(definition.copy(layout = "transform"), listOf(null, null)), 1, turned = true)
        assertEquals(0, controller.state!!.degrees)
    }

    // SYS-039, TBL-109, ZON-075
    @Test fun opponentStillSeesOnlyTheShownFaceAndNothingOfAHiddenCard() {
        val (play, card) = transformed()
        for (peek in listOf(true, false)) {
            show(play, card, f.b, peek)
            compose.onNodeWithText("Back rules text.").assertExists()
            compose.onAllNodesWithText("Front rules text.", substring = true).assertCountEquals(0)
            compose.onAllNodesWithText("Daylight healer", substring = true).assertCountEquals(0)
            compose.onNodeWithContentDescription("Next face").assertDoesNotExist()
            compose.onNodeWithText("1 / 1").assertDoesNotExist()
        }
        val (hidden, inHand) = transformed("a/HAND")
        for (peek in listOf(true, false)) {
            show(hidden, inHand, f.b, peek)
            compose.onNodeWithText("Covered card").assertIsDisplayed()
            compose.onAllNodesWithText("rules text", substring = true).assertCountEquals(0)
            compose.onAllNodesWithText("Nightfall horror", substring = true).assertCountEquals(0)
        }
    }

    @OptIn(ExperimentalRoborazziApi::class)
    @Test fun deckInspectionFaceSwitcher() {
        val (play, _) = transformed()
        val definition = play.definitions.single { it.definition.layout == "transform" }
        val controller = CardInspectionController().apply { open(InspectedCard.retained(definition.digest, definition.definition)) }
        compose.setContent { ScreenTheme { DeckCardInspection(controller) } }
        compose.onNodeWithText("1 / 2").assertIsDisplayed()
        compose.onNodeWithContentDescription("Previous face").assertIsNotEnabled()
        File("$SCREENSHOTS/inspection").mkdirs()
        captureScreenRoboImage("$SCREENSHOTS/inspection/deck-inspect-front.png")
        compose.onNodeWithContentDescription("Next face").performClick()
        compose.onNodeWithText("2 / 2").assertIsDisplayed()
        compose.onNodeWithText("Back rules text.").assertExists()
    }
}

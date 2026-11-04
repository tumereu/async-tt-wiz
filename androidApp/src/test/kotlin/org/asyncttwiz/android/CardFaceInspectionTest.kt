package org.asyncttwiz.android

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import org.asyncttwiz.protocol.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Player a's "1/3" battlefield card made a transformed double-faced card (showing its back) in [container]. */
internal fun transformedFixture(container: String = "a/BATTLEFIELD"): Pair<PlayState, PlayCard> {
    val f = TableStatesFixture
    val original = f.source(f.a, "1/3")
    val front = DefinitionFace(f.id("front"), "Daylight healer", typeLine = "Creature — Human Cleric",
        rulesText = "Front rules text.", power = "1", toughness = "1",
        scryfallId = placeholderPrinting("Daylight healer", "Nightfall horror"))
    val back = DefinitionFace(original.faceId, "Nightfall horror", typeLine = "Creature — Horror",
        rulesText = "Back rules text.", power = "4", toughness = "4",
        scryfallId = placeholderPrinting("Daylight healer", "Nightfall horror"))
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

/** Holding a card shows every face of it, only as pictures (TBL-104, TBL-106); hidden faces stay hidden. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w412dp-h892dp-port-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CardFaceInspectionTest {
    @get:Rule val compose = createComposeRule()
    private val f = TableStatesFixture

    private data class Shown(val play: PlayState, val card: PlayCard, val viewer: String)
    private val shown = mutableStateOf<Shown?>(null)

    /** Shows what holding [card] shows [viewer] in a game. */
    private fun show(play: PlayState, card: PlayCard, viewer: String) {
        val value = Shown(play, card, viewer)
        if (shown.value == null) {
            shown.value = value
            compose.setContent {
                val (p, c, v) = shown.value ?: return@setContent
                MaterialTheme {
                    // A new inspection per case, as each hold makes one.
                    androidx.compose.runtime.key(shown.value) {
                        GameplayCardInspection(tableBoard(p), v, ManualInspection(p.gameId, p.id, c.id,
                            ManualDisclosure.permittedFaces(p, c, v, inspecting = true).map { it.faceId }), {})
                    }
                }
            }
        } else compose.runOnIdle { shown.value = value }
        compose.waitForIdle()
    }

    private fun image(name: String) = compose.onNodeWithContentDescription("Card image: $name")

    /** No text block: no rules text, type line or printed values, and no Automatum lines. */
    private fun assertOnlyPictures() {
        for (text in listOf("Back rules text.", "Front rules text.", "Creature — Horror", "4/4", "1/1", "Owner", "Automatum"))
            compose.onAllNodesWithText(text, substring = true).assertCountEquals(0)
        compose.onNodeWithContentDescription("Close card inspection").assertDoesNotExist()
        compose.onNodeWithContentDescription("Next face").assertDoesNotExist()
    }

    // TBL-104, TBL-106
    @OptIn(ExperimentalRoborazziApi::class)
    @Test fun holdShowsEveryFaceOfADoubleFacedCardStackedWithItsShownFaceFirst() {
        val (play, card) = transformedFixture()
        show(play, card, f.a)
        val back = image("Nightfall horror").assertIsDisplayed().getBoundsInRoot()
        val front = image("Daylight healer").assertExists().getBoundsInRoot()
        assertTrue(back.bottom <= front.top, "Portrait stacks the faces, the face the card shows first")
        assertEquals(back.left, front.left)
        assertOnlyPictures()
        File("$SCREENSHOTS/inspection").mkdirs()
        captureShot("$SCREENSHOTS/inspection/table-inspect-dfc-portrait.png",
            "Owner holds their transformed double-faced battlefield card in portrait: Inspect shows only its card images, the back it shows on top and the front below, scrolled by the finger.")
    }

    // TBL-104, TBL-106
    @OptIn(ExperimentalRoborazziApi::class)
    @Test @Config(qualifiers = "w892dp-h412dp-land-mdpi")
    fun landscapeHoldShowsTheFacesSideBySide() {
        val (play, card) = transformedFixture()
        show(play, card, f.a)
        val back = image("Nightfall horror").assertIsDisplayed().getBoundsInRoot()
        val front = image("Daylight healer").assertIsDisplayed().getBoundsInRoot()
        assertTrue(back.right <= front.left, "Landscape puts the faces side by side, the face the card shows first")
        assertEquals(back.top, front.top)
        assertOnlyPictures()
        File("$SCREENSHOTS/inspection").mkdirs()
        captureShot("$SCREENSHOTS/inspection/table-inspect-dfc-landscape.png",
            "Owner holds their transformed double-faced battlefield card in landscape: Inspect shows only its card images side by side, the back it shows first.")
    }

    // TBL-106
    @Test fun aCardWhosePartsShareOnePictureShowsItOnceAFlippedFlipCardUpsideDown() {
        val (transformedPlay, card) = transformedFixture()
        val definition = transformedPlay.definitions.single { it.digest == card.definition }.definition
        val flip = definition.copy(layout = "flip")
        assertTrue(flippedOnTable(flip, card.faceId))
        val flipped = inspectFaces(InspectedCard(flip, listOf(null, null)), 1, flippedOnTable(flip, card.faceId))
        assertEquals(listOf(180), flipped.map { it.degrees })
        assertEquals(listOf(flip.name), flipped.map { it.name })
        assertEquals(listOf(0), inspectFaces(InspectedCard(flip, listOf(null, null)), 0, false).map { it.degrees })
        // TBL-108: a split card's one picture turns sideways
        assertEquals(listOf(90), inspectFaces(InspectedCard(definition.copy(layout = "split"), listOf(null, null))).map { it.degrees })
        assertEquals(1, inspectFaces(InspectedCard(definition.copy(layout = "adventure"), listOf(null, null))).size)
        // A double-faced card: every face, the shown one first
        assertEquals(listOf("Nightfall horror", "Daylight healer"), inspectFaces(InspectedCard(definition, listOf(null, null)), 1).map { it.name })
    }

    // SYS-039, TBL-109, ZON-075
    @Test fun opponentSeesEveryFaceOfAFaceUpCardAndNothingOfAHiddenOne() {
        val (play, card) = transformedFixture()
        show(play, card, f.b)
        image("Nightfall horror").assertExists()
        image("Daylight healer").assertExists()
        assertOnlyPictures()
        val (hidden, inHand) = transformedFixture("a/HAND")
        show(hidden, inHand, f.b)
        compose.onNodeWithContentDescription("Covered card").assertIsDisplayed()
        compose.onAllNodesWithContentDescription("Card image", substring = true).assertCountEquals(0)
        compose.onAllNodesWithText("Nightfall horror", substring = true).assertCountEquals(0)
    }
}

/** A real hold on the table: Inspect shows every face and closes on release (TBL-047, TBL-104, TBL-106). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w412dp-h892dp-port-mdpi")
class CardFaceHoldTest {
    @get:Rule val compose = createComposeRule()

    // TBL-047, TBL-104, TBL-106
    @Test fun holdingADoubleFacedTableCardShowsBothFacesUntilRelease() {
        val f = TableStatesFixture
        val (play, card) = transformedFixture()
        val toolkit = TableToolkitController(f.a, { tableBoard(play) }, { true }, { _, _ -> true })
        compose.setContent { MaterialTheme {
            TableSurface(f.game, tableBoard(play), f.a, false, Modifier.fillMaxSize(), connected = true,
                toolkit = toolkit, onIntent = toolkit::handle)
        } }
        val scene = TablePresentation.scene(f.game, tableBoard(play), f.a, 412f, 892f, false)
        val point = scene.items.first { it.cardId == card.id && it.visible.isNotEmpty() }.visible.first().center
        val table = compose.onNodeWithContentDescription("Shared table")
        compose.mainClock.autoAdvance = false
        table.performTouchInput { down(Offset(point.x, point.y)) }
        compose.waitForIdle(); compose.mainClock.advanceTimeBy(600)
        compose.onNodeWithContentDescription("Card image: Nightfall horror").assertExists()
        compose.onNodeWithContentDescription("Card image: Daylight healer").assertExists()
        compose.onNodeWithText("Back rules text.").assertDoesNotExist()
        table.performTouchInput { up() }
        compose.mainClock.autoAdvance = true
        compose.onNodeWithContentDescription("Card inspection content").assertDoesNotExist()
        compose.runOnIdle { assertNull(toolkit.peek); assertNull(toolkit.capture) }
    }
}

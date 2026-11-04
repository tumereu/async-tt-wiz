package org.asyncttwiz.android

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import org.asyncttwiz.protocol.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import kotlin.test.*

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w412dp-h892dp-port-mdpi")
class PilePeekTest {
    @get:Rule val compose = createComposeRule()
    private val f = TableStatesFixture

    /** Three of Alice's battlefield cards with different names made into one pile, top first; the middle one face down. */
    private fun pile(): Pair<PlayState, List<PlayCard>> {
        val play = f.initial
        val field = play.board.containers.single { it.id == f.id("a/BATTLEFIELD") }
        val names = play.definitions.associate { it.digest to it.definition.name }
        val members = field.cards.map { id -> play.board.cards.single { it.id == id } }
            .filter { !it.token }.distinctBy { names[it.definition] }.take(3)
        assertEquals(3, members.size)
        val placement = assertNotNull(members.first().placement)
        val middle = members[1].id
        val ids = members.map { it.id }
        val cards = play.board.cards.map { card -> when {
            card.id == middle -> card.copy(placement = placement, covered = true, explicitlyCovered = true)
            card.id in ids -> card.copy(placement = placement)
            else -> card
        } }
        val state = play.copy(board = play.board.copy(cards = cards, containers = play.board.containers.map {
            if (it.id == field.id) it.copy(cards = ids + (it.cards - ids.toSet())) else it }))
        return state to ids.map { id -> state.board.cards.single { it.id == id } }
    }

    /** The previewed title of [card]; the preview names a card only when it shows it. */
    private fun title(play: PlayState, card: PlayCard) = compose.onAllNodes(hasText(name(play, card), substring = false) and
        hasAnyAncestor(hasContentDescription("Card inspection content")), useUnmergedTree = true)

    private fun name(play: PlayState, card: PlayCard) = play.definitions.single { it.digest == card.definition }.definition.faces
        .single { it.faceId == card.faceId }.name

    // TBL-053, TBL-111
    @Test fun cardsUnderAPileCardFollowInPileOrder() {
        val (play, members) = pile()
        val (top, middle, bottom) = members
        assertEquals(listOf(middle.id, bottom.id), battlefieldPileBelow(play.board, top.id).map { it.id })
        assertEquals(listOf(bottom.id), battlefieldPileBelow(play.board, middle.id).map { it.id })
        assertEquals(emptyList(), battlefieldPileBelow(play.board, bottom.id))
        // A lone battlefield card, a combat copy and a card elsewhere have nothing under them.
        val lone = play.board.cards.first { it.containerId == f.id("a/BATTLEFIELD") && it.id !in members.map(PlayCard::id) }
        assertEquals(emptyList(), battlefieldPileBelow(play.board, lone.id))
        assertEquals(emptyList(), battlefieldPileBelow(f.combat().board, f.id("attacker1")))
        val hand = play.board.containers.single { it.id == f.id("a/HAND") }.cards.first()
        assertEquals(emptyList(), battlefieldPileBelow(play.board, hand))
    }

    private fun hold(play: PlayState, viewer: String, cardId: String): TableToolkitController {
        val toolkit = TableToolkitController(viewer, { engineTableBoard(play, viewer) }, { true }, { _, _ -> true })
        compose.setContent { MaterialTheme { NoRipples {
            EngineFixtureTable(play, viewer) {
                TableSurface(f.game, engineTableBoard(play, viewer), viewer, false, Modifier.fillMaxSize(), connected = true, toolkit = toolkit, onIntent = toolkit::handle)
            }
        } } }
        val scene = TablePresentation.scene(f.game, engineTableBoard(play, viewer), viewer, 412f, 892f, false)
        val item = scene.items.single { it.cardId == cardId && !it.projection }
        val point = item.visible.flatMap { b -> listOf(b.center, TablePoint(b.x + 2f, b.y + 2f)) }.first { scene.hit(it)?.id == item.id }
        compose.mainClock.autoAdvance = false
        compose.onNodeWithContentDescription("Shared table").performTouchInput { down(Offset(point.x, point.y)) }
        compose.waitForIdle(); compose.mainClock.advanceTimeBy(CardPeekHoldMillis + 250)
        compose.runOnIdle { assertEquals(cardId, toolkit.peek?.cardId) }
        return toolkit
    }

    private fun release(toolkit: TableToolkitController) {
        compose.onNodeWithContentDescription("Shared table").performTouchInput { up() }
        compose.mainClock.autoAdvance = true
        compose.runOnIdle { assertNull(toolkit.peek) }
        compose.onNodeWithContentDescription("Card inspection content").assertDoesNotExist()
    }

    // TBL-109, TBL-111
    @Test fun holdingThePileTopPreviewsEveryCardUnderItAndShowsTheOwnerTheirFaceDownCardMarked() {
        val (play, members) = pile()
        val toolkit = hold(play, f.a, members.first().id)
        compose.onNodeWithContentDescription("Card inspection content").assertExists()
        for (card in members) title(play, card).assertCountEquals(1)
        // The pile order, top first, one below the other.
        val tops = members.map { title(play, it)[0].fetchSemanticsNode().positionInRoot.y }
        assertEquals(tops.sorted(), tops)
        compose.onNodeWithText("Face down").assertExists()
        compose.onNodeWithText("Covered card").assertDoesNotExist()
        // Scroll down to where the top card's text ends and the face-down card under it begins.
        compose.onNodeWithContentDescription("Shared table").performTouchInput { moveBy(Offset(0f, -120f)) }
        compose.waitForIdle(); compose.mainClock.advanceTimeBy(100)
        val file = File("$SCREENSHOTS/pile-preview-portrait.png")
        file.parentFile!!.mkdirs()
        captureShot(file.path, "Portrait table: holding the top card of Alice's three-card battlefield pile and moving the finger " +
            "up a little scrolls the one preview from the top card's text down to the face-down card under it, shown to Alice " +
            "dimmed with a Face down label.")
        release(toolkit)
    }

    // TBL-111
    @Test fun holdingAMiddleCardPreviewsOnlyTheCardsUnderIt() {
        val (play, members) = pile()
        val toolkit = hold(play, f.a, members[1].id)
        title(play, members[0]).assertCountEquals(0)
        title(play, members[1]).assertCountEquals(1)
        title(play, members[2]).assertCountEquals(1)
        release(toolkit)
    }

    // TBL-111, ZON-075
    @Test fun theOpponentSeesAFaceDownPileCardOnlyAsCovered() {
        val (play, members) = pile()
        val toolkit = hold(play, f.b, members.first().id)
        title(play, members[0]).assertCountEquals(1)
        title(play, members[2]).assertCountEquals(1)
        compose.onNodeWithText("Covered card").assertExists()
        compose.onNodeWithText("Face down").assertDoesNotExist()
        compose.onAllNodes(hasText(name(play, members[1]), substring = true) and
            hasAnyAncestor(hasContentDescription("Card inspection content")), useUnmergedTree = true).assertCountEquals(0)
        release(toolkit)
    }
}

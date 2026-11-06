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

/** TBL-111: holding a battlefield group inspects all its members, its top card first. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w412dp-h892dp-port-mdpi")
class GroupPeekTest {
    @get:Rule val compose = createComposeRule()
    private val f = TableStatesFixture

    /** Three of Alice's battlefield cards with different names made into one group: the second and third attached to the
     * first, which is face down. */
    private fun group(): Pair<PlayState, List<PlayCard>> {
        val play = f.initial
        val field = play.board.containers.single { it.id == f.id("a/BATTLEFIELD") }
        val names = play.definitions.associate { it.digest to it.definition.name }
        val members = field.cards.map { id -> play.board.cards.single { it.id == id } }
            .filter { !it.token }.distinctBy { names[it.definition] }.take(3)
        assertEquals(3, members.size)
        val host = members.first()
        val ids = members.map { it.id }
        val cards = play.board.cards.map { card -> when {
            card.id == host.id -> card.copy(covered = true, explicitlyCovered = true)
            card.id in ids -> card.copy(attachedTo = MtgRef.Card(host.id, host.zoneChanges))
            else -> card
        } }
        val state = play.copy(board = play.board.copy(cards = cards))
        return state to ids.map { id -> state.board.cards.single { it.id == id } }
    }

    /** The inspected picture of [card]; Inspect shows a card only when the viewer may see it. */
    private fun title(play: PlayState, card: PlayCard) = compose.onAllNodes(hasContentDescription("Card image: ${name(play, card)}") and
        hasAnyAncestor(hasContentDescription("Card inspection content")), useUnmergedTree = true)

    private fun name(play: PlayState, card: PlayCard) = play.definitions.single { it.digest == card.definition }.definition.faces
        .single { it.faceId == card.faceId }.name

    /** Holds [viewer]'s finger on [cardId]'s drawing: Inspect opens on the group's top card [shown]. */
    private fun hold(play: PlayState, viewer: String, cardId: String, shown: String): TableToolkitController {
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
        compose.runOnIdle { assertEquals(shown, toolkit.peek?.cardId) }
        return toolkit
    }

    private fun release(toolkit: TableToolkitController) {
        compose.onNodeWithContentDescription("Shared table").performTouchInput { up() }
        compose.mainClock.autoAdvance = true
        compose.runOnIdle { assertNull(toolkit.peek) }
        compose.onNodeWithContentDescription("Card inspection content").assertDoesNotExist()
    }

    // TBL-109, TBL-111, TBL-053
    @Test fun holdingAGroupPreviewsEveryMemberAndShowsTheOwnerTheirFaceDownCardMarked() {
        val (play, members) = group()
        val toolkit = hold(play, f.a, members.first().id, members.first().id)
        compose.onNodeWithContentDescription("Card inspection content").assertExists()
        for (card in members) title(play, card).assertCountEquals(1)
        // The host first, then what is attached to it, one below the other.
        val tops = members.map { title(play, it)[0].fetchSemanticsNode().positionInRoot.y }
        assertEquals(tops.sorted(), tops)
        compose.onNodeWithText("Face down").assertExists()
        compose.onNodeWithText("Covered card").assertDoesNotExist()
        // Scroll down to where the face-down creature's picture ends and the first attached card begins.
        compose.onNodeWithContentDescription("Shared table").performTouchInput { moveBy(Offset(0f, -420f)) }
        compose.waitForIdle(); compose.mainClock.advanceTimeBy(100)
        val file = File("$SCREENSHOTS/group-preview-portrait.png")
        file.parentFile!!.mkdirs()
        captureShot(file.path, "Portrait table: holding Alice's face-down creature with two cards attached to it and moving " +
            "the finger up scrolls the one Inspect view of the whole group from the face-down creature (shown to Alice dimmed " +
            "with a Face down label) down to the first card attached to it.")
        release(toolkit)
    }

    // TBL-111, TBL-053: an attached card's strip is no target of its own: holding it inspects the whole group, host first
    @Test fun holdingAnAttachedCardInspectsTheWholeGroup() {
        val (play, members) = group()
        val toolkit = hold(play, f.a, members[2].id, members.first().id)
        for (card in members) title(play, card).assertCountEquals(1)
        release(toolkit)
    }

    // TBL-111, TBL-109
    @Test fun theOpponentSeesAFaceDownGroupCardOnlyAsCovered() {
        val (play, members) = group()
        val toolkit = hold(play, f.b, members.first().id, members.first().id)
        title(play, members[1]).assertCountEquals(1)
        title(play, members[2]).assertCountEquals(1)
        compose.onNodeWithText("Covered card").assertExists()
        compose.onNodeWithText("Face down").assertDoesNotExist()
        compose.onAllNodes((hasText(name(play, members[0]), substring = true) or hasContentDescription(name(play, members[0]), substring = true)) and
            hasAnyAncestor(hasContentDescription("Card inspection content")), useUnmergedTree = true).assertCountEquals(0)
        release(toolkit)
    }
}

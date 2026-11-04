package org.asyncttwiz.android

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onRoot
import java.io.File
import javax.imageio.ImageIO
import org.asyncttwiz.protocol.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.*
import kotlin.math.roundToInt

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w412dp-h892dp-port-mdpi")
class TableMotionRuntimeTest {
    @get:Rule val compose = createComposeRule()



    private fun draw(owner: String) {
        val initial = TableFixture.play(library = 1)
        val state = mutableStateOf(initial)
        compose.setContent { MaterialTheme {
            TableSurface(TableFixture.game, tableBoard(state.value), "a", false, Modifier.fillMaxSize())
        } }
        compose.waitForIdle()
        compose.mainClock.autoAdvance = false
        val id = "$owner/LIBRARY/0"
        val moved = initial.copy(currentNodeId = "draw", board = initial.board.copy(
            cards = initial.board.cards.map { if (it.id == id) it.copy(containerId = "$owner/HAND") else it },
            containers = initial.board.containers.map { c -> when (c.id) {
                "$owner/LIBRARY" -> c.copy(cards = emptyList())
                "$owner/HAND" -> c.copy(cards = listOf(id))
                else -> c
            } }))
        val target = TablePresentation.scene(TableFixture.game, tableBoard(moved), "a", 412f, 892f, false).items.single { it.cardId == id }
        val source = TablePresentation.scene(TableFixture.game, tableBoard(initial), "a", 412f, 892f, false).items.single { it.id == "$owner/LIBRARY" }
        compose.runOnIdle { state.value = moved }
        // BoxWithConstraints subcomposes on Android's layout traversal, not on the test clock.
        compose.mainClock.advanceTimeByFrame()
        compose.waitForIdle()
        val distance = kotlin.math.hypot(source.bounds.center.x - target.bounds.center.x, source.bounds.center.y - target.bounds.center.y)
        val travelMillis = cardMoveDurationMillis(distance)
        compose.mainClock.advanceTimeBy(travelMillis.toLong() + 32)
        compose.waitForIdle()
        val node = compose.onNodeWithContentDescription(target.label)
        node.assertIsDisplayed()
        val during = node.fetchSemanticsNode().boundsInRoot
        assertTrue(travelMillis < MAX_CARD_MOVE_DURATION_MILLIS, "This short draw must arrive before the scene ends")
        assertEquals(target.bounds.x, during.left, .01f)
        assertEquals(target.bounds.y, during.top, .01f)
        // Confirmation metadata must not restart a short move that has already arrived while
        // the shared scene clock is still running.
        compose.runOnIdle { state.value = moved.copy(board = moved.board.copy(cards = moved.board.cards.map {
            if (it.id == id) it.copy(note = "accepted metadata") else it
        })) }
        compose.mainClock.advanceTimeByFrame()
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(160)
        compose.waitForIdle()
        val after = node.fetchSemanticsNode().boundsInRoot
        assertEquals(target.bounds.x, after.left, .01f)
        assertEquals(target.bounds.y, after.top, .01f)
        compose.mainClock.advanceTimeBy(200)
        compose.waitForIdle()
        assertEquals(after, node.fetchSemanticsNode().boundsInRoot)
        compose.mainClock.autoAdvance = true
    }

    // TBL-040
    @Test fun ownLibraryDrawMovesAcrossTheTableAndConfirmationDoesNotRestartClock() = draw("a")
    @Test fun opponentLibraryDrawMovesToCoveredHand() = draw("b")

    // TBL-040, ZON-002
    @Test fun bothHandRailsSlideTheirSurvivorsInsteadOfJumping() = handReflow(false)
    @Test fun historyJumpSkipsHandReflowWithTheOtherSceneAnimations() = handReflow(true)

    private fun handReflow(jump: Boolean) {
        val initial = TableFixture.play(hand = 1, library = 1)
        val state = mutableStateOf(initial)
        val changes = if (jump) TableChanges() else null
        compose.setContent { MaterialTheme {
            TableSurface(TableFixture.game, tableBoard(state.value), "a", false, Modifier.fillMaxSize(),
                runtime = TableRuntimeHooks(changes = changes))
        } }
        compose.waitForIdle()
        compose.mainClock.autoAdvance = false
        val ids = listOf("a/LIBRARY/0", "b/LIBRARY/0")
        val target = initial.copy(currentNodeId = "draw", board = initial.board.copy(
            cards = initial.board.cards.map { if (it.id in ids) it.copy(containerId = "${it.owner}/HAND") else it },
            containers = initial.board.containers.map { c -> when (c.zone) {
                PlayZone.LIBRARY -> c.copy(cards = emptyList())
                PlayZone.HAND -> c.copy(cards = c.cards + "${c.accountId}/LIBRARY/0")
                else -> c
            } }))
        val before = TablePresentation.scene(TableFixture.game, tableBoard(initial), "a", 412f, 892f, false)
        val after = TablePresentation.scene(TableFixture.game, tableBoard(target), "a", 412f, 892f, false)
        compose.runOnIdle {
            changes?.apply(TableChangeBatch(jump = true))
            state.value = target
        }
        repeat(2) { compose.mainClock.advanceTimeByFrame(); compose.waitForIdle() }
        compose.mainClock.advanceTimeBy(80); compose.waitForIdle()
        for (owner in listOf("a", "b")) {
            val old = before.items.single { it.cardId == "$owner/HAND/0" }
            val next = after.items.single { it.cardId == old.cardId }
            val lefts = compose.onAllNodesWithContentDescription(next.label).fetchSemanticsNodes().map { it.boundsInRoot.left }
            if (jump) assertTrue(lefts.any { kotlin.math.abs(it - next.bounds.x) < .01f })
            else {
                val current = lefts.min()
                assertTrue(current > minOf(old.bounds.x, next.bounds.x) && current < maxOf(old.bounds.x, next.bounds.x),
                    "$owner hand must still be sliding: $current")
            }
        }
        compose.mainClock.advanceTimeBy(500); compose.waitForIdle()
        for (owner in listOf("a", "b")) {
            val cards = after.items.filter { it.zone == PlayZone.HAND && it.kind == TableItemKind.CARD && it.owner == owner }
            val lefts = compose.onAllNodesWithContentDescription(cards.first().label).fetchSemanticsNodes().map { it.boundsInRoot.left }.sorted()
            assertEquals(cards.map { it.bounds.x }.sorted(), lefts)
        }
        compose.mainClock.autoAdvance = true
    }

    // TBL-031, TBL-037
    @Test fun tapAndUntapAccessibilityBoundsTrackTheDrawnFaceAndFinishInNinetyMillis() {
        val initial = TableFixture.play(board = 1)
        val state = mutableStateOf(initial)
        val id = "a/BATTLEFIELD/0"
        val toolkit = TableToolkitController("a", { tableBoard(state.value) }, { true }, { _, _ -> true })
        compose.setContent { MaterialTheme {
            TableSurface(TableFixture.game, tableBoard(state.value), "a", false, Modifier.fillMaxSize(), toolkit = toolkit)
        } }
        compose.waitForIdle()
        compose.mainClock.autoAdvance = false
        for (tapped in listOf(true, false)) {
            val target = initial.copy(currentNodeId = "tap/$tapped", board = initial.board.copy(cards = initial.board.cards.map {
                if (it.id == id) it.copy(tapped = tapped) else it
            }))
            val item = TablePresentation.scene(TableFixture.game, tableBoard(target), "a", 412f, 892f, false).items.single { it.cardId == id }
            compose.runOnIdle { state.value = target }
            // Flush BoxWithConstraints' Android layout traversals separately before sampling
            // the animation clock; batched clock frames do not run those traversals themselves.
            repeat(3) { compose.mainClock.advanceTimeByFrame(); compose.waitForIdle() }
            compose.mainClock.advanceTimeBy(48); compose.waitForIdle()
            val node = compose.onNodeWithContentDescription(item.label)
            val during = node.fetchSemanticsNode().boundsInRoot
            assertTrue(during.width > 40f, "Mid-rotation AABB must include the whole diagonal face: $during")
            compose.mainClock.advanceTimeBy(64); compose.waitForIdle()
            val end = node.fetchSemanticsNode().boundsInRoot
            assertEquals(item.bounds.x.roundToInt().toFloat(), end.left)
            assertEquals(item.bounds.y.roundToInt().toFloat(), end.top)
            assertEquals(item.bounds.width, end.width)
            assertEquals(item.bounds.height, end.height)
        }
        compose.mainClock.autoAdvance = true
    }

    // CMB-007 (combat review #1): the combat area floats over the battlefields, so opening and closing it moves nothing
    @Test fun combatOpeningAndClosingMovesNoWorldGeometryOrHud() {
        val initial = TableFixture.play(board = 1, hand = 1).let { play ->
            val hand = CardDefinition("Hand card", listOf(DefinitionFace("face", "Hand face")))
            play.copy(definitions = play.definitions + SubmittedDefinition(DeckJson.digest(hand), hand), board = play.board.copy(
                cards = play.board.cards.map { if (it.containerId.endsWith("HAND"))
                    it.copy(tapped = true, definition = DeckJson.digest(hand)) else it })) }
        val state = mutableStateOf(initial)
        compose.setContent { MaterialTheme {
            TableSurface(TableFixture.game, tableBoard(state.value), "a", false, Modifier.fillMaxSize())
        } }
        compose.waitForIdle()
        val home = TablePresentation.scene(TableFixture.game, tableBoard(initial), "a", 412f, 892f, false)
        fun bounds(id: String) = compose.onNodeWithContentDescription(home.items.single { it.id == id }.label).fetchSemanticsNode().boundsInRoot
        val cardBefore = bounds("card/a/BATTLEFIELD/0")
        val handBefore = bounds("card/a/HAND/0")
        val stackBefore = bounds("stack")
        val opened = initial.copy(board = initial.board.copy(combat = CombatArea("combat")))
        compose.mainClock.autoAdvance = false
        compose.runOnIdle { state.value = opened }
        for (step in 0..3) {
            compose.mainClock.advanceTimeBy(80); compose.waitForIdle()
            assertEquals(cardBefore, bounds("card/a/BATTLEFIELD/0"))
            assertEquals(handBefore, bounds("card/a/HAND/0"))
            assertEquals(stackBefore, bounds("stack"))
        }
        compose.runOnIdle { state.value = initial }
        compose.mainClock.advanceTimeByFrame(); compose.waitForIdle()
        compose.mainClock.advanceTimeBy(80); compose.waitForIdle()
        assertEquals(cardBefore, bounds("card/a/BATTLEFIELD/0"))
        compose.mainClock.autoAdvance = true
    }
}

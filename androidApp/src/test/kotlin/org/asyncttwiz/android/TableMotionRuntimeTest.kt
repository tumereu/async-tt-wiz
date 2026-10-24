package org.asyncttwiz.android

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import org.asyncttwiz.protocol.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.*

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w412dp-h892dp-port-mdpi")
class TableMotionRuntimeTest {
    @get:Rule val compose = createComposeRule()

    private fun draw(owner: String) {
        val initial = TableFixture.play(library = 1)
        val state = mutableStateOf(initial)
        compose.setContent { MaterialTheme {
            TableSurface(TableFixture.game, state.value, "a", false, Modifier.fillMaxSize())
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
        val target = TablePresentation.scene(TableFixture.game, moved, "a", 412f, 892f, false).items.single { it.cardId == id }
        val source = TablePresentation.scene(TableFixture.game, initial, "a", 412f, 892f, false).items.single { it.id == "$owner/LIBRARY" }
        compose.runOnIdle { state.value = moved }
        // BoxWithConstraints subcomposes on Android's layout traversal, not on the test clock.
        compose.mainClock.advanceTimeByFrame()
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(80)
        compose.waitForIdle()
        val node = compose.onNodeWithContentDescription(target.label)
        node.assertIsDisplayed()
        val during = node.fetchSemanticsNode().boundsInRoot
        val sourceY = source.bounds.y + 8f
        assertTrue(during.top > minOf(sourceY, target.bounds.y) && during.top < maxOf(sourceY, target.bounds.y))
        // This changes the scene's metadata while the move is in flight, like an authoritative
        // confirmation. It must neither cancel the clock nor replay its remaining tail.
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

    @Test fun ownLibraryDrawMovesAcrossTheTableAndConfirmationDoesNotRestartClock() = draw("a")
    @Test fun opponentLibraryDrawMovesToCoveredHand() = draw("b")
}

package org.asyncttwiz.android

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
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
class CardRingInteractionTest {
    @get:Rule val compose = createComposeRule()
    private val f = TableStatesFixture
    private val sent = mutableListOf<PlayAction>()
    private val batches = mutableListOf<List<PlayAction>>()
    private fun render(multi: Boolean = false, play: PlayState = f.combat(),
        contextId: String = f.source(f.a, "1/3").id): TableToolkitController {
        val toolkit = TableToolkitController(f.a, { play }, { true }, { action, _ -> sent += action; true },
            playBatch = { actions, _ -> batches += actions.toList(); sent += actions; true })
        val source = f.source(f.a, "1/3").id
        toolkit.openCard(TableScope(contextId, if (multi) listOf(source, f.source(f.a, "1/2").id) else listOf(contextId)))
        compose.setContent {
            MaterialTheme {
                TableSurface(f.game, play, f.a, false, Modifier.fillMaxSize(),
                    connected = true, toolkit = toolkit, onIntent = toolkit::handle)
            }
        }
        compose.waitForIdle()
        return toolkit
    }

    // TBL-069
    @Test fun bubblePressDuringGuardCannotFireEvenWhenReleasedAfterTheWindow() {
        compose.mainClock.autoAdvance = false
        val toolkit = render()
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithContentDescription("Inspect").performTouchInput { down(center) }
        compose.mainClock.advanceTimeBy(350)
        compose.onNodeWithContentDescription("Inspect").performTouchInput { advanceEventTime(350); up() }
        compose.runOnIdle { assertNull(toolkit.inspection); assertNotNull(toolkit.capture); assertTrue(sent.isEmpty()) }
        compose.mainClock.autoAdvance = true
        compose.onNodeWithContentDescription("Inspect").performClick()
        compose.onNodeWithContentDescription("Close card inspection").assertIsDisplayed()
        compose.runOnIdle { assertNotNull(toolkit.inspection) }
    }

    // TBL-068
    @Test fun outsideTapDismissesWithoutActivatingTableChrome() {
        val toolkit = render()
        compose.mainClock.advanceTimeBy(350)
        compose.onNodeWithContentDescription("Card action ring").performTouchInput { click(Offset(width - 30f, height - 25f)) }
        compose.onNodeWithContentDescription("Card action ring").assertDoesNotExist()
        compose.runOnIdle { assertTrue(sent.isEmpty()); assertNull(toolkit.capture) }
    }

    private fun sourcePoint(corner: String = "1/3"): Offset {
        val scene = TablePresentation.scene(f.game, f.combat(), f.a, 412f, 892f, false)
        val point = scene.items.single { it.cardId == f.source(f.a, corner).id }.visible.first().center
        return Offset(point.x, point.y)
    }


    @Test fun sameCardSingleTapOnlyClosesRingAndAnotherCardTapOpensItsRing() {
        val toolkit = render()
        compose.mainClock.advanceTimeBy(600)
        compose.onNodeWithContentDescription("Shared table").performTouchInput { click(sourcePoint()) }
        compose.mainClock.advanceTimeBy(400)
        compose.onNodeWithContentDescription("Card action ring").assertDoesNotExist()
        compose.runOnIdle { assertNull(toolkit.capture); assertTrue(sent.isEmpty()) }
        compose.runOnIdle { val id = f.source(f.a, "1/3").id; toolkit.openCard(TableScope(id, listOf(id))) }
        compose.mainClock.advanceTimeBy(600)
        val scene = TablePresentation.scene(f.game, f.combat(), f.a, 412f, 892f, false)
        val square = compose.onNodeWithContentDescription("Card action grid").fetchSemanticsNode().boundsInRoot
        val item = scene.items.first { it.kind == TableItemKind.CARD && it.cardId != f.source(f.a, "1/3").id &&
            it.visible.isNotEmpty() && !square.contains(Offset(it.visible.first().center.x, it.visible.first().center.y)) }
        val another = item.cardId!!
        val point = item.visible.first().center
        assertFalse(square.contains(Offset(point.x, point.y)), "The second card must be outside the reserved square")
        compose.onNodeWithContentDescription("Shared table").performTouchInput { click(Offset(point.x, point.y)) }
        compose.runOnIdle {
            assertEquals(another, toolkit.capture?.contextViewId)
            assertTrue(sent.isEmpty())
        }
    }

    // SYS-042, TBL-068
    @Test fun outsideDragSurvivesRingDismissalAndMovesCard() {
        render()
        compose.mainClock.advanceTimeBy(600)
        val point = sourcePoint()
        val field = TablePresentation.scene(f.game, f.combat(), f.a, 412f, 892f, false).fields.last().bounds
        compose.onNodeWithContentDescription("Shared table").performTouchInput {
            swipe(point, Offset(field.right - 24f, field.center.y), 160)
        }
        compose.onNodeWithContentDescription("Card action ring").assertDoesNotExist()
        compose.runOnIdle { assertIs<PlayAction.MoveCard>(sent.single()) }
    }

}

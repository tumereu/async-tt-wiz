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
import kotlin.test.*

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w412dp-h892dp-port-mdpi")
class CardRingInteractionTest {
    @get:Rule val compose = createComposeRule()
    private val f = TableStatesFixture
    private val sent = mutableListOf<PlayAction>()
    private fun render(multi: Boolean = false): TableToolkitController {
        val play = f.combat()
        val toolkit = TableToolkitController(f.a, { play }, { true }, { action, _ -> sent += action; true })
        val source = f.source(f.a, "1/3").id
        toolkit.openCard(TableScope(source, if (multi) listOf(source, f.source(f.a, "1/2").id) else listOf(source)))
        compose.setContent {
            MaterialTheme { TableSurface(f.game, play, f.a, false, Modifier.fillMaxSize(),
                connected = true, toolkit = toolkit, onIntent = toolkit::handle) }
        }
        compose.waitForIdle()
        return toolkit
    }

    @Test fun bubblePressDuringGuardCannotFireEvenWhenReleasedAfterTheWindow() {
        compose.mainClock.autoAdvance = false
        val toolkit = render()
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithContentDescription("Inspect").performTouchInput { down(center) }
        compose.mainClock.advanceTimeBy(350)
        compose.onNodeWithContentDescription("Inspect").performTouchInput { advanceEventTime(350); up() }
        compose.runOnIdle { assertNull(toolkit.inspection); assertNotNull(toolkit.manual.scope); assertTrue(sent.isEmpty()) }
        compose.mainClock.autoAdvance = true
        compose.onNodeWithContentDescription("Inspect").performClick()
        compose.onNodeWithContentDescription("Close card inspection").assertIsDisplayed()
        compose.runOnIdle { assertNotNull(toolkit.inspection) }
    }

    @Test fun outsideTapDismissesWithoutActivatingTableChrome() {
        val toolkit = render()
        compose.ringPage(1)
        val selection = toolkit.selectionState.value
        // The underlying interaction-mode control must not open its dropdown.
        compose.onNodeWithContentDescription("Card action ring").performTouchInput { click(Offset(width - 30f, height - 25f)) }
        compose.onNodeWithContentDescription("Card action ring").assertDoesNotExist()
        compose.onNodeWithText("Normal").assertDoesNotExist()
        compose.runOnIdle { assertEquals(selection, toolkit.selectionState.value); assertTrue(sent.isEmpty()); assertNull(toolkit.manual.scope) }
    }

    private fun sourcePoint(corner: String = "1/3"): Offset {
        val scene = TablePresentation.scene(f.game, f.combat(), f.a, 412f, 892f, false)
        val point = scene.items.single { it.cardId == f.source(f.a, corner).id }.visible.first().center
        return Offset(point.x, point.y)
    }

    @Test fun delayedDoubleTapThroughOpenRingTapsCardOnceWithoutReopeningRing() {
        val toolkit = render()
        val point = sourcePoint()
        compose.runOnIdle { toolkit.dismiss() }
        compose.onNodeWithContentDescription("Shared table").performTouchInput { click(point) }
        compose.onNodeWithContentDescription("Card action ring").assertExists()
        compose.mainClock.advanceTimeBy(600)
        compose.onNodeWithContentDescription("Shared table").performTouchInput { doubleClick(point) }
        compose.onNodeWithContentDescription("Card action ring").assertDoesNotExist()
        compose.runOnIdle {
            assertNull(toolkit.manual.scope)
            assertEquals(listOf<PlayAction>(PlayAction.SetTapped(f.source(f.a, "1/3").id, true)), sent)
        }
    }

    @Test fun sameCardSingleTapOnlyClosesRingAndAnotherCardTapOpensItsRing() {
        val toolkit = render()
        compose.mainClock.advanceTimeBy(600)
        compose.onNodeWithContentDescription("Shared table").performTouchInput { click(sourcePoint()) }
        compose.mainClock.advanceTimeBy(400)
        compose.onNodeWithContentDescription("Card action ring").assertDoesNotExist()
        compose.runOnIdle { assertNull(toolkit.manual.scope); assertTrue(sent.isEmpty()) }
        compose.runOnIdle { val id = f.source(f.a, "1/3").id; toolkit.openCard(TableScope(id, listOf(id))) }
        compose.mainClock.advanceTimeBy(600)
        val another = f.source(f.b, "2/3").id
        val scene = TablePresentation.scene(f.game, f.combat(), f.a, 412f, 892f, false)
        val point = scene.items.single { it.cardId == another }.visible.first().center
        val square = compose.onNodeWithContentDescription("Card action grid").fetchSemanticsNode().boundsInRoot
        assertFalse(square.contains(Offset(point.x, point.y)), "The second card must be outside the reserved square")
        compose.onNodeWithContentDescription("Shared table").performTouchInput { click(Offset(point.x, point.y)) }
        compose.runOnIdle {
            assertEquals(another, toolkit.manual.scope?.contextId)
            assertTrue(sent.isEmpty())
        }
    }

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

    @Test fun outsideRectangleAndPinchRemainTableGestures() {
        val toolkit = render()
        compose.mainClock.advanceTimeBy(600)
        val point = sourcePoint()
        compose.onNodeWithContentDescription("Shared table").performTouchInput {
            swipe(point + Offset(-25f, -35f), point + Offset(5f, 5f), 160)
        }
        compose.onNodeWithContentDescription("Card action ring").assertDoesNotExist()
        compose.runOnIdle {
            assertTrue(f.source(f.a, "1/3").id in toolkit.selectionState.value.ids)
            val id = f.source(f.a, "1/3").id; toolkit.openCard(TableScope(id, listOf(id)))
        }
        compose.mainClock.advanceTimeBy(600)
        compose.onNodeWithContentDescription("Shared table").performTouchInput {
            down(0, point); down(1, point + Offset(20f, 0f))
            moveTo(0, point - Offset(10f, 0f)); moveTo(1, point + Offset(30f, 0f))
            up(0); up(1)
        }
        compose.onNodeWithContentDescription("Card action ring").assertDoesNotExist()
        compose.runOnIdle { assertTrue(toolkit.layoutState.value.camera.zoom > 1f); assertTrue(sent.isEmpty()) }
    }

    @Test fun cornerCellsAdjustBothPartsForEverySelectedSourceAndValueOpensEditor() {
        val toolkit = render(multi = true)
        compose.ringPage(1)
        compose.onNodeWithContentDescription("Increase corner").performClick()
        assertEquals(listOf<PlayAction>(PlayAction.AdjustCorner(f.source(f.a, "1/3").id, CornerPart.BOTH, 1),
            PlayAction.AdjustCorner(f.source(f.a, "1/2").id, CornerPart.BOTH, 1)), sent)
        compose.onNodeWithContentDescription("Set corner").performClick()
        compose.runOnIdle { assertEquals(ManualValueKind.CORNER, toolkit.manual.editor?.field?.kind) }
        compose.onNodeWithContentDescription("Card action ring").assertDoesNotExist()
    }

    @Test fun counterCellsKeepSetAndAdjustmentCallbacks() {
        val toolkit = render()
        compose.ringPage(2)
        compose.onNodeWithContentDescription("Decrease blue counter").performClick()
        compose.onNodeWithContentDescription("Increase blue counter").performClick()
        assertEquals(listOf<PlayAction>(PlayAction.AdjustCounter(CounterTarget.CARD, f.source(f.a, "1/3").id, CounterColor.BLUE, "-1"),
            PlayAction.AdjustCounter(CounterTarget.CARD, f.source(f.a, "1/3").id, CounterColor.BLUE, "1")), sent)
        compose.onNodeWithContentDescription("Set blue counter").performClick()
        compose.runOnIdle { assertEquals(ManualValue(ManualValueKind.COUNTER, CounterColor.BLUE), toolkit.manual.editor?.field) }
    }

    @Test fun swipeChangesPageAndAnotherCardRemembersItAfterClosingTheRing() {
        val toolkit = render()
        compose.ringPage(2)
        compose.onNodeWithContentDescription("Card action grid").performTouchInput { swipeLeft() }
        compose.onNode(hasContentDescription("Action page 3 of ", substring = true)).assertIsDisplayed()
        compose.ringPage(4)
        compose.runOnIdle { toolkit.dismiss() }
        compose.waitForIdle()
        compose.runOnIdle {
            val another = f.source(f.a, "1/2").id
            toolkit.openCard(TableScope(another, listOf(another)))
        }
        compose.onNode(hasContentDescription("Action page 4 of ", substring = true)).assertIsDisplayed()
        assertTrue(sent.isEmpty())
    }

    @Test fun verticalDragDoesNotSwitchPagesAndMovePageNeedsNoScrolling() {
        render()
        compose.ringPage(4)
        val grid = compose.onNodeWithContentDescription("Card action grid")
        grid.performTouchInput { swipeUp() }
        compose.onNode(hasContentDescription("Action page 4 of ", substring = true)).assertIsDisplayed()
        compose.onNodeWithContentDescription("Move to Stack").assertIsDisplayed()
        assertTrue(sent.isEmpty())
    }

    @Test fun tabsAndSwipesKeepTheSameSquareAndDisabledActionsStayDisabled() {
        render()
        compose.ringPage(1)
        val bounds = compose.onNodeWithContentDescription("Card action grid").fetchSemanticsNode().boundsInRoot
        compose.onNodeWithContentDescription("Inspect other side").assertDoesNotExist()
        for (page in 2..4) {
            compose.ringPage(page)
            assertEquals(bounds, compose.onNodeWithContentDescription("Card action grid").fetchSemanticsNode().boundsInRoot)
        }
        compose.ringPage(3)
        compose.onNodeWithContentDescription("Inspect other side").assertIsNotEnabled()
    }
}

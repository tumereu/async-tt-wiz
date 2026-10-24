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

    @Test fun outsideTapIsSwallowedAndCannotActivateTableChromeOrSelection() {
        val toolkit = render()
        compose.ringPage(1)
        val selection = toolkit.selectionState.value
        // The underlying interaction-mode control must not open its dropdown.
        compose.onNodeWithContentDescription("Card action ring").performTouchInput { click(Offset(width - 30f, height - 25f)) }
        compose.onNodeWithContentDescription("Card action ring").assertDoesNotExist()
        compose.onNodeWithText("Normal").assertDoesNotExist()
        compose.runOnIdle { assertEquals(selection, toolkit.selectionState.value); assertTrue(sent.isEmpty()); assertNull(toolkit.manual.scope) }
    }

    @Test fun wholeCornerPillAdjustsBothPartsForEverySelectedSourceAndValueOpensEditor() {
        val toolkit = render(multi = true)
        compose.ringPage(1)
        compose.onNodeWithContentDescription("Increase corner").performClick()
        assertEquals(listOf<PlayAction>(PlayAction.AdjustCorner(f.source(f.a, "1/3").id, CornerPart.BOTH, 1),
            PlayAction.AdjustCorner(f.source(f.a, "1/2").id, CornerPart.BOTH, 1)), sent)
        compose.onNodeWithContentDescription("Set corner").performClick()
        compose.runOnIdle { assertEquals(ManualValueKind.CORNER, toolkit.manual.editor?.field?.kind) }
        compose.onNodeWithContentDescription("Card action ring").assertDoesNotExist()
    }

    @Test fun counterPillKeepsAllThreeControlsOnThePage() {
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
        compose.onNodeWithContentDescription("Card action ring").performTouchInput { swipeLeft() }
        compose.onNode(hasContentDescription("Action page 3 of 3", substring = true)).assertIsDisplayed()
        compose.runOnIdle { toolkit.dismiss() }
        compose.waitForIdle()
        compose.runOnIdle {
            val another = f.source(f.a, "1/2").id
            toolkit.openCard(TableScope(another, listOf(another)))
        }
        compose.onNode(hasContentDescription("Action page 3 of 3", substring = true)).assertIsDisplayed()
        assertTrue(sent.isEmpty())
    }

    @Test fun verticalGridDragScrollsWithoutSwitchingPagesAndEveryMoveRemainsReachable() {
        render()
        compose.ringPage(3)
        val grid = compose.onNodeWithContentDescription("Card action grid")
        val before = grid.fetchSemanticsNode().config[androidx.compose.ui.semantics.SemanticsProperties.VerticalScrollAxisRange].value()
        grid.performTouchInput { swipeUp() }
        compose.onNode(hasContentDescription("Action page 3 of 3", substring = true)).assertIsDisplayed()
        val after = grid.fetchSemanticsNode().config[androidx.compose.ui.semantics.SemanticsProperties.VerticalScrollAxisRange].value()
        assertTrue(after > before)
        compose.onNodeWithContentDescription("Move to Stack").performScrollTo().assertIsDisplayed()
        assertTrue(sent.isEmpty())
    }
}

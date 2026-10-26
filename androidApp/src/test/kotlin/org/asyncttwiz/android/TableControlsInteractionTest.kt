package org.asyncttwiz.android

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.github.takahirom.roborazzi.captureRoboImage
import java.io.File
import javax.imageio.ImageIO
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
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
class TableControlsInteractionTest {
    @get:Rule val compose = createComposeRule()

    @Test fun playerValueDragCountsHorizontalDistanceFromDownAndCancelsWithoutSending() {
        val play = TableToolFixture.play()
        val sent = mutableListOf<PlayAction>()
        val controller = TableActionController({ play }, { true }, { sent += it; true }, RecordingRoutes())
        val target = PlayerValueTarget(controller.scope()!!, play.board.players.first().accountId)
        compose.setContent { MaterialTheme {
            PlayerDeltaControl(target, "20", OwnTableColor, true, controller, Modifier.padding(top = 60.dp))
        } }
        val value = compose.onNodeWithContentDescription("Life, 20")
        value.performTouchInput { down(center); moveBy(androidx.compose.ui.geometry.Offset(30f, 0f)) }
        compose.onNodeWithText("+2 → 22").assertExists()
        compose.runOnIdle { assertTrue(sent.isEmpty()) }
        value.performTouchInput { up() }
        compose.runOnIdle { assertEquals(listOf<PlayAction>(PlayAction.AdjustLife(target.accountId, "2")), sent) }
        value.performTouchInput { down(center); moveBy(androidx.compose.ui.geometry.Offset(0f, 30f)); cancel() }
        compose.runOnIdle { assertEquals(1, sent.size) }
    }

    @Test fun undoShowsPressFeedbackOnDownAndTapsOnReleaseWithoutWaitingForRedoHold() {
        var undo = 0
        var redo = 0
        compose.setContent { MaterialTheme {
            ActionSquare("Undo", "", true, fill = Color.White, ink = Color.Black,
                holdName = "Redo", onHold = { redo++ }, onClick = { undo++ })
        } }
        val button = compose.onNodeWithContentDescription("Undo")
        fun pixel(): Int {
            val output = File("build/outputs/roborazzi/undo-press.png")
            requireNotNull(output.parentFile).mkdirs()
            button.captureRoboImage(output.path)
            return ImageIO.read(output).getRGB(2, 2) and 0xff
        }
        val idle = pixel()
        compose.mainClock.autoAdvance = false
        button.performTouchInput { down(center) }
        compose.mainClock.advanceTimeByFrame()
        val pressed = pixel()
        assertTrue(pressed < idle, "Press must be visibly drawn before release")
        assertEquals(0, undo); assertEquals(0, redo)
        button.performTouchInput { advanceEventTime(50); up() }
        compose.runOnIdle { assertEquals(1, undo); assertEquals(0, redo) }
        compose.mainClock.advanceTimeByFrame()
        assertEquals(idle, pixel())
        compose.mainClock.autoAdvance = true
    }

    @Test fun modeButtonCyclesWithoutAMenuAndLivesAboveBottomControls() {
        val play = TableFixture.play(board = 2)
        val ui = TableToolkitController("a", { play }, { true }, { _, _ -> true })
        var pingAvailable by mutableStateOf(true)
        compose.setContent { MaterialTheme {
            TableSurface(TableFixture.game, play, "a", false, Modifier.fillMaxSize(), true, ui::handle, toolkit = ui,
                runtime = TableRuntimeHooks(controlsConnected = true, ping = if (pingAvailable) ({ true }) else null,
                    undo = { true }, redo = { true }))
        } }
        val mode = compose.onNodeWithContentDescription("Interaction mode")
        mode.assertWidthIsEqualTo(48.dp).assertHeightIsEqualTo(48.dp)
        val modeBounds = mode.fetchSemanticsNode().boundsInRoot
        val nextBounds = compose.onNodeWithContentDescription("Next step").fetchSemanticsNode().boundsInRoot
        val root = compose.onRoot().fetchSemanticsNode().boundsInRoot
        assertTrue(nextBounds.right < root.right)
        assertEquals(root.bottom - 4f, nextBounds.bottom)
        assertTrue(modeBounds.bottom < nextBounds.top)
        for (expected in listOf(TableMode.SELECT, TableMode.PING, TableMode.NORMAL)) {
            mode.performTouchInput { click() }
            compose.runOnIdle { assertEquals(expected, ui.selectionState.value.mode) }
        }
        compose.runOnIdle { pingAvailable = false }
        mode.performClick(); mode.performClick()
        compose.runOnIdle { assertEquals(TableMode.NORMAL, ui.selectionState.value.mode) }
        compose.onNodeWithText("Redo").assertDoesNotExist()
    }

    @Test fun singleContextualActionFillsSpaceBeforeRightCornerStepButton() {
        val play = TableToolFixture.play(TurnPosition.END_STEP)
        val controller = TableActionController({ play }, { true }, { true }, RecordingRoutes())
        compose.setContent { MaterialTheme {
            QuickActionStrip(play, QuickContext(viewer = play.board.turn.accountId), controller, OwnTableColor)
        } }
        val end = compose.onNodeWithContentDescription("End turn").fetchSemanticsNode().boundsInRoot
        val next = compose.onNodeWithContentDescription("Next step").fetchSemanticsNode().boundsInRoot
        assertEquals(0f, end.left)
        assertEquals(end.right + 4f, next.left)
        assertEquals(412f, next.right)
        assertEquals(48f, next.width)
    }

    @Test fun holdTooltipStaysOutsideButtonAndClampsAtScreenEdges() {
        val position = HoldTooltipPosition(8)
        val window = IntSize(412, 892)
        val label = IntSize(160, 40)
        assertEquals(IntOffset(0, 752), position.calculatePosition(IntRect(0, 800, 48, 848), window, LayoutDirection.Ltr, label))
        assertEquals(IntOffset(252, 56), position.calculatePosition(IntRect(364, 0, 412, 48), window, LayoutDirection.Rtl, label))
    }

    @Test fun nextStepTapHasNoHapticAndEndTurnHoldTicksOnceWithoutNextOnRelease() {
        val play = TableToolFixture.play()
        val sent = mutableListOf<PlayAction>()
        val ticks = mutableListOf<HapticFeedbackType>()
        val haptics = object : HapticFeedback {
            override fun performHapticFeedback(hapticFeedbackType: HapticFeedbackType) { ticks += hapticFeedbackType }
        }
        val controller = TableActionController({ play }, { true }, { action -> sent += action; true }, RecordingRoutes())
        compose.setContent { MaterialTheme {
            CompositionLocalProvider(LocalHapticFeedback provides haptics) {
                Column {
                    QuickActionStrip(play, QuickContext(viewer = play.board.turn.accountId), controller, OwnTableColor)
                    ActionSquare("Tooltip only", "", true, onClick = { error("Tooltip must not click") })
                    ActionSquare("Disabled hold", "", false, holdName = "Mutate", onHold = { error("Disabled hold") }, onClick = {})
                }
            }
        } }
        val next = compose.onNodeWithContentDescription("Next step")
        next.performTouchInput { click() }
        assertEquals(listOf<PlayAction>(PlayAction.NextStep), sent)
        assertTrue(ticks.isEmpty())
        next.performTouchInput { longClick() }
        assertEquals(listOf<PlayAction>(PlayAction.NextStep, PlayAction.EndTurn), sent)
        assertEquals(listOf(HapticFeedbackType.SegmentFrequentTick), ticks)
        compose.onNodeWithContentDescription("Tooltip only").performTouchInput { longClick() }
        compose.onNodeWithContentDescription("Disabled hold").performTouchInput { longClick() }
        assertEquals(1, ticks.size)
    }

    @Test fun contentsPeekStartsAt350MillisWithoutHapticsOrActions() {
        val peeks = mutableListOf<Boolean>()
        val haptics = object : HapticFeedback {
            override fun performHapticFeedback(hapticFeedbackType: HapticFeedbackType) { error("Peek must not vibrate") }
        }
        compose.setContent { MaterialTheme {
            CompositionLocalProvider(LocalHapticFeedback provides haptics) {
                Text("Card", Modifier.size(100.dp).contentsCardGestures("card", PlayZone.BATTLEFIELD,
                    actions = { error("Peek must not open actions") }, double = null,
                    inspect = { error("Peek must not inspect") }, peek = peeks::add))
            }
        } }
        compose.mainClock.autoAdvance = false
        val card = compose.onNodeWithText("Card")
        card.performTouchInput { down(center) }
        compose.mainClock.advanceTimeBy(349, ignoreFrameDuration = true)
        assertFalse(true in peeks)
        compose.mainClock.advanceTimeBy(1, ignoreFrameDuration = true)
        compose.waitForIdle()
        assertTrue(true in peeks)
        card.performTouchInput { advanceEventTime(350); up() }
        compose.mainClock.autoAdvance = true
        assertFalse(peeks.last())
    }

    @Test fun pickerIsDialogWithPhaseLanesAndCurrentStepAndPlayer() {
        val play = TableToolFixture.play(TurnPosition.DECLARE_ATTACKERS, "42")
        val controller = TableActionController({ play }, { true }, { true }, RecordingRoutes())
        compose.setContent { MaterialTheme {
            PhaseStepPicker(play, play.board.turn.accountId, OwnTableColor, controller, {},
                names = mapOf(play.board.turn.accountId to "Alice"))
        } }
        compose.onNodeWithText("Combat · Declare attackers").assertIsDisplayed()
        compose.onNodeWithText("Alice's turn").assertIsDisplayed()
        for (phase in TurnPosition.entries.map { it.phase }.distinct()) compose.onNodeWithText(phaseName(phase)).assertIsDisplayed()
        compose.onNodeWithContentDescription("Declare attackers").assertIsSelected()
        for (step in TurnPosition.entries) compose.onNodeWithContentDescription(step.label).assertIsDisplayed()
        compose.onNodeWithContentDescription("Close").assertIsDisplayed()
        compose.onAllNodes(isDialog()).assertCountEquals(1)
        compose.onNodeWithText("Turn 42").assertDoesNotExist()
    }

    @Test fun stepCodesFollowActualProtocolPositionsAndMainNamesDoNotRepeat() {
        assertEquals(listOf("UNT", "UPK", "DRW", "M1", "BOC", "ATK", "BLK", "DMG", "EOC", "M2", "END", "CLN"),
            TurnPosition.entries.map { it.shortCode })
        assertEquals("Precombat main", turnStepName(TableToolFixture.play(TurnPosition.PRECOMBAT_MAIN).board.turn))
        assertEquals("Ending · Cleanup", turnStepName(TableToolFixture.play(TurnPosition.CLEANUP).board.turn))
    }
}

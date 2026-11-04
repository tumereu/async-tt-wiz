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


    // SYS-044
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
            button.captureRoboImage(output.path, ScratchCapture)
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




    // SYS-045, TBL-064
    @Test fun holdTooltipStaysOutsideButtonAndClampsAtScreenEdges() {
        val position = HoldTooltipPosition(8)
        val window = IntSize(412, 892)
        val label = IntSize(160, 40)
        assertEquals(IntOffset(0, 752), position.calculatePosition(IntRect(0, 800, 48, 848), window, LayoutDirection.Ltr, label))
        assertEquals(IntOffset(252, 56), position.calculatePosition(IntRect(364, 0, 412, 48), window, LayoutDirection.Rtl, label))
    }


    // TBL-047
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

    // TRN-008, TRN-056: the twelve steps; the first-strike damage step only while the turn is in it (or its combat had it)
    @Test fun pickerIsDialogWithPhaseLanesAndCurrentStepAndPlayer() {
        var play by mutableStateOf(TableToolFixture.play(TurnPosition.DECLARE_ATTACKERS, "42"))
        val controller = TableActionController({ TableBoard.of(play) }, { true }, RecordingRoutes())
        compose.setContent { MaterialTheme {
            PhaseStepPicker(TableBoard.of(play), play.board.turn.accountId, OwnTableColor, {},
                names = mapOf(play.board.turn.accountId to "Alice"))
        } }
        compose.onNodeWithText("Combat · Declare attackers").assertIsDisplayed()
        compose.onNodeWithText("Alice's turn").assertIsDisplayed()
        for (phase in TurnPosition.entries.map { it.phase }.distinct()) compose.onNodeWithText(phaseName(phase)).assertIsDisplayed()
        compose.onNodeWithContentDescription("Declare attackers").assertIsSelected()
        for (step in TurnPosition.MANUAL) compose.onNodeWithContentDescription(step.label).assertIsDisplayed()
        compose.onNodeWithContentDescription(TurnPosition.FIRST_STRIKE_DAMAGE.label).assertDoesNotExist()
        compose.onNodeWithContentDescription("Close").assertIsDisplayed()
        compose.onAllNodes(isDialog()).assertCountEquals(1)
        compose.onNodeWithText("Turn 42").assertDoesNotExist()
        compose.runOnIdle { play = TableToolFixture.play(TurnPosition.FIRST_STRIKE_DAMAGE, "42") }
        compose.onNodeWithText("Combat · First-strike damage").assertIsDisplayed()
        compose.onNodeWithContentDescription(TurnPosition.FIRST_STRIKE_DAMAGE.label).assertIsSelected()
    }

    @Test fun stepCodesFollowActualProtocolPositionsAndMainNamesDoNotRepeat() {
        assertEquals(listOf("UNT", "UPK", "DRW", "M1", "BOC", "ATK", "BLK", "FS", "DMG", "EOC", "M2", "END", "CLN"),
            TurnPosition.entries.map { it.shortCode })
        assertEquals("Precombat main", turnStepName(TableToolFixture.play(TurnPosition.PRECOMBAT_MAIN).board.turn))
        assertEquals("Ending · Cleanup", turnStepName(TableToolFixture.play(TurnPosition.CLEANUP).board.turn))
    }

    // TRN-007, TRN-008: the step button and the phase dialog's squares show the same one-line short names.
    @Test fun indicatorAndPickerUseTheSameShortStepNamesForEveryStep() {
        val initial = TableToolFixture.play()
        var turn by mutableStateOf(initial.board.turn)
        var picker by mutableStateOf(false)
        val controller = TableActionController({ TableBoard.of(initial) }, { true }, RecordingRoutes())
        compose.setContent { MaterialTheme {
            TurnStepIcon(turn)
            if (picker) PhaseStepPicker(TableBoard.of(initial), initial.board.turn.accountId, OwnTableColor, {})
        } }
        for (step in TurnPosition.entries) {
            compose.runOnIdle { turn = turn.copy(phase = step.phase, step = step.step) }
            compose.onNodeWithText(stepLabel(step), useUnmergedTree = true).assertExists()
        }
        compose.runOnIdle { picker = true }
        for (step in TurnPosition.MANUAL) {
            val label = hasText(stepLabel(step)) and hasAnyAncestor(hasContentDescription(step.label))
            compose.onNode(label, useUnmergedTree = true).assertExists()
        }
    }
}

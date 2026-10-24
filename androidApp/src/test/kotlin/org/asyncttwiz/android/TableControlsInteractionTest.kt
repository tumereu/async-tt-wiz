package org.asyncttwiz.android

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import org.asyncttwiz.protocol.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.*

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w412dp-h892dp-port-mdpi")
class TableControlsInteractionTest {
    @get:Rule val compose = createComposeRule()

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
                    QuickActionStrip(play, QuickContext(), controller, OwnTableColor)
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

    @Test fun pickerShowsCurrentPhaseStepPlayerAndLabelsEveryLane() {
        val play = TableToolFixture.play(TurnPosition.DECLARE_ATTACKERS, "42")
        val controller = TableActionController({ play }, { true }, { true }, RecordingRoutes())
        compose.setContent { MaterialTheme {
            PhaseStepPicker(play, play.board.turn.accountId, OwnTableColor, controller, {},
                names = mapOf(play.board.turn.accountId to "Alice"))
        } }
        compose.onNodeWithText("Combat · Declare attackers").assertIsDisplayed()
        compose.onNodeWithText("Alice's turn").assertIsDisplayed()
        for (label in listOf("Beginning", "Precombat main", "Combat", "Postcombat main", "Ending")) {
            compose.onNodeWithText(label).assertExists()
        }
        compose.onNodeWithText("Turn 42").assertDoesNotExist()
    }

    @Test fun stepCodesFollowActualProtocolPositionsAndMainNamesDoNotRepeat() {
        assertEquals(listOf("UNT", "UPK", "DRW", "M1", "BOC", "ATK", "BLK", "DMG", "EOC", "M2", "END", "CLN"),
            TurnPosition.entries.map { it.shortCode })
        assertEquals("Precombat main", turnStepName(TableToolFixture.play(TurnPosition.PRECOMBAT_MAIN).board.turn))
        assertEquals("Ending · Cleanup", turnStepName(TableToolFixture.play(TurnPosition.CLEANUP).board.turn))
    }
}

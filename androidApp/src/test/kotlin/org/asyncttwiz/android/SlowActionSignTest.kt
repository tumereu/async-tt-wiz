package org.asyncttwiz.android

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** TRN-063 (owner, 2026-11-06): when the main button's working sign shows, on the controlled test clock. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SlowActionSignTest {
    @get:Rule val compose = createComposeRule()
    private val pending = mutableStateOf(false)

    private fun start() {
        compose.setContent { if (rememberSlowActionSign { pending.value }) Box(Modifier.size(10.dp).testTag("sign")) }
        compose.waitForIdle()
        compose.mainClock.autoAdvance = false
    }

    private fun sign(shown: Boolean) = compose.onNodeWithTag("sign").run { if (shown) assertExists() else assertDoesNotExist() }

    /** The action's result arrives ([value] false) or a new one is pressed: the change is applied before time goes on. */
    private fun pending(value: Boolean) { compose.runOnIdle { pending.value = value }; compose.waitForIdle() }

    // TRN-063: a result within 150 ms: nothing shows at any frame
    @Test fun aQuickResultShowsNothing() {
        start()
        pending(true)
        repeat(7) { compose.mainClock.advanceTimeBy(16); sign(false) }
        pending(false)
        repeat(30) { compose.mainClock.advanceTimeBy(16); sign(false) }
    }

    // TRN-063: still pending after 150 ms: the sign shows until the result arrives
    @Test fun aSlowResultShowsTheSignUntilItArrives() {
        start()
        pending(true)
        compose.mainClock.advanceTimeBy(120); sign(false)
        compose.mainClock.advanceTimeBy(64); sign(true)
        compose.mainClock.advanceTimeBy(1_000); sign(true)
        pending(false)
        compose.mainClock.advanceTimeBy(32); sign(false)
    }

    // TRN-063: once shown, at least 300 ms, however soon the result arrives
    @Test fun theSignStaysAtLeast300Ms() {
        start()
        pending(true)
        compose.mainClock.advanceTimeBy(200); sign(true)
        pending(false)
        compose.mainClock.advanceTimeBy(150); sign(true)
        compose.mainClock.advanceTimeBy(200); sign(false)
    }
}

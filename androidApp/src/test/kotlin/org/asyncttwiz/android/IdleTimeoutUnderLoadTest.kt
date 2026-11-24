package org.asyncttwiz.android

import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.espresso.AppNotIdleException
import androidx.test.espresso.IdlingPolicies
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** Compose's waits for idle fail on a screen that keeps working, not on a machine that stalls the test ([IdleTimeoutUnderLoad]). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class IdleTimeoutUnderLoadTest {
    @get:Rule val compose = createComposeRule()

    @Test fun theWallClockLimitIsOnlyAnHourLongBackstop() {
        val policy = IdlingPolicies.getMasterIdlingPolicy()
        assertEquals(TimeUnit.MINUTES.toMillis(60), policy.idleTimeoutUnit.toMillis(policy.idleTimeout))
        assertEquals(TimeUnit.MINUTES.toNanos(4), MainThreadCpuBudget.limitNanos)
    }

    @Test fun aScreenThatNeverGetsIdleFailsOnceItsMainThreadHasUsedTheCpuBudget() {
        MainThreadCpuBudget.limitNanos = MainThreadCpuBudget.usedNanos() + TimeUnit.SECONDS.toNanos(2)
        var running by mutableStateOf(true)
        val failure = assertFails {
            compose.setContent {
                if (running) {
                    // An effect that changes state on every frame (Compose's tests stop infinite transitions themselves)
                    var frames by remember { mutableIntStateOf(0) }
                    LaunchedEffect(Unit) { while (true) withFrameNanos { frames++ } }
                    Text("frame $frames")
                } else Text("stopped")
            }
        }
        val cause = generateSequence(failure) { it.cause }.firstOrNull { it is AppNotIdleException }
        assertNotNull(cause, "fails as Compose not getting idle: $failure")
        assertTrue("CPU time" in cause.message.orEmpty(), cause.message)
        // The screen stops, so the rule's own clean-up finds it idle.
        compose.runOnUiThread { running = false }
        compose.onNodeWithText("stopped").assertExists()
    }

    @Test fun timeTheTestSpendsStoppedDoesNotCount() {
        compose.setContent { Text("idle") }
        MainThreadCpuBudget.limitNanos = MainThreadCpuBudget.usedNanos() + TimeUnit.SECONDS.toNanos(1)
        // A frozen JVM uses no CPU, like this sleep: a wall clock would count it, the budget doesn't.
        Thread.sleep(3_000)
        compose.onNodeWithText("idle").assertExists()
    }
}

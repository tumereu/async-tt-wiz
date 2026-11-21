package org.asyncttwiz.android

import androidx.test.espresso.IdlingPolicies
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class IdleTimeoutUnderLoadTest {
    /** Every Robolectric test's Compose waits for idle get the longer limit ([IdleTimeoutUnderLoad]), not Espresso's 60 s. */
    @Test fun composeWaitsForIdleGetMinutesBeforeFailing() {
        val policy = IdlingPolicies.getMasterIdlingPolicy()
        assertEquals(TimeUnit.MINUTES.toMillis(4), policy.idleTimeoutUnit.toMillis(policy.idleTimeout))
    }
}

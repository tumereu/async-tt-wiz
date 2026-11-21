package org.asyncttwiz.android

import androidx.test.espresso.IdlingPolicies
import org.robolectric.pluginapi.TestEnvironmentLifecyclePlugin
import java.util.concurrent.TimeUnit

/**
 * Gives Compose's waits for an idle screen ([MINUTES] minutes) before a test fails with "Compose did not get idle"
 * (AppNotIdleException), set before every Robolectric test (registered in META-INF/services). The wait's limit is
 * Espresso's master idling policy, 60 s by default, measured by the wall clock: a machine stalled by a concurrent build at
 * its memory cap held one first composition of the desktop table, or one pod screen swap, past it, though the same wait
 * takes well under a second on a quiet machine (PodCubePreparationTest, DesktopTableScreenshotTest, ManaGlowTest,
 * HandDimTest in the merge gates of 2026-11-20; PodBuildViewTest in one of 2026-11-21). A screen that really never gets idle
 * still fails, a little later.
 */
class IdleTimeoutUnderLoad : TestEnvironmentLifecyclePlugin {
    override fun onSetupApplicationState() {
        IdlingPolicies.setMasterPolicyTimeout(MINUTES, TimeUnit.MINUTES)
    }

    private companion object {
        const val MINUTES = 4L
    }
}

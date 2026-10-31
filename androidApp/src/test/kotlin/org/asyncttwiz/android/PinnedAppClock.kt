package org.asyncttwiz.android

import org.robolectric.pluginapi.TestEnvironmentLifecyclePlugin
import java.time.Clock
import java.time.Instant
import java.time.ZoneId

/** Pins [AppClock] before every Robolectric test (registered in META-INF/services), so screenshots and shown
 *  dates don't depend on the day the tests run. The committed images were recorded on this day. */
class PinnedAppClock : TestEnvironmentLifecyclePlugin {
    override fun onSetupApplicationState() { AppClock.pinned = clock() }

    companion object {
        val INSTANT: Instant = Instant.parse("2026-10-31T12:00:00Z")
        fun clock(): Clock = Clock.fixed(INSTANT, ZoneId.systemDefault())
    }
}

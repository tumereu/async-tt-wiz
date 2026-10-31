package org.asyncttwiz.android

import java.time.Clock

/** The wall clock behind dates and times the app shows as text or visible state ("21 Oct", "Last seen Oct 27",
 *  which sets are released). Tests pin it so screenshots don't change with the date they run on. Timing,
 *  scheduling, telemetry and network code keep the real clock. */
internal object AppClock {
    /** Set only by tests; null means the device clock in its current time zone. */
    @Volatile var pinned: Clock? = null

    fun clock(): Clock = pinned ?: Clock.systemDefaultZone()
}

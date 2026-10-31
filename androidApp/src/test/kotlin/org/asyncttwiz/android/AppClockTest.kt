package org.asyncttwiz.android

import org.asyncttwiz.storage.DraftableSet
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import kotlin.test.assertEquals

/** Screenshots render shown dates through [AppClock]; Robolectric tests pin it, so the images don't change
 *  with the day the suite runs on. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AppClockTest {
    private val game = ScreenFixtures().lobby.state.games.first { it.id == "g1" }

    @Test fun robolectricTestsSeeThePinnedDayNotTheRealOne() {
        assertEquals(PinnedAppClock.INSTANT, AppClock.clock().instant())
        assertEquals("2026-10-21T12:00:00Z", game.updatedAt)
        assertEquals("21 Oct", gameLastActivity(game))
    }

    @Test fun shownDatesFollowTheAppClockSoA2027RunWouldChangeThemWithoutThePin() {
        val pinned = AppClock.pinned
        try {
            AppClock.pinned = Clock.fixed(Instant.parse("2027-01-01T12:00:00Z"), ZoneId.systemDefault())
            assertEquals("21 Oct 2026", gameLastActivity(game))
            val sets = listOf(PodSetRow(DraftableSet("NXT", "Next set", "2026-11-15", 200, "expansion"), false))
            assertEquals(listOf("NXT"), PodSetPickerState(rows = sets).filtered.map { it.set.code })
            AppClock.pinned = pinned
            assertEquals(emptyList(), PodSetPickerState(rows = sets).filtered.map { it.set.code })
        } finally { AppClock.pinned = pinned }
    }

    @Test fun withoutAPinTheAppClockIsTheDeviceClock() {
        val pinned = AppClock.pinned
        try {
            AppClock.pinned = null
            assertEquals(ZoneId.systemDefault(), AppClock.clock().zone)
            val before = Instant.now()
            val now = AppClock.clock().instant()
            assert(!now.isBefore(before)) { "$now is before $before" }
        } finally { AppClock.pinned = pinned }
    }
}

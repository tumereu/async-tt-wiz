package org.asyncttwiz.android

import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import kotlin.test.assertEquals

/** LOB-067 (owner, 2026-11-14): a chat message's small time and the date line between days, in the device's zone. */
class ChatTimesTest {
    private val helsinki = ZoneId.of("Europe/Helsinki")
    private val clock = Clock.fixed(Instant.parse("2026-11-14T20:30:00Z"), helsinki)

    // LOB-067
    @Test fun aMessagesTimeIsItsLocalHourAndMinute() {
        assertEquals("22:05", chatTime("2026-11-14T20:05:09.123Z", clock))
        assertEquals("09:00", chatTime("2026-03-01T07:00:00Z", clock))
        assertEquals("", chatTime("not a time", clock))
    }

    // LOB-067: Today and Yesterday by the device's day (Helsinki's midnight, not UTC's), then the weekday and date, with the
    // year only before this one
    @Test fun theDayLineNamesTodayYesterdayOrTheDate() {
        assertEquals("Today", chatDay("2026-11-13T22:30:00Z", clock))
        assertEquals("Yesterday", chatDay("2026-11-13T21:59:00Z", clock))
        assertEquals("Thu 12 Nov", chatDay("2026-11-12T10:00:00Z", clock))
        assertEquals("Wed 31 Dec 2025", chatDay("2025-12-31T10:00:00Z", clock))
        assertEquals("", chatDay("", clock))
    }
}

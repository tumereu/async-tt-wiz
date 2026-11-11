package org.asyncttwiz.android

import org.asyncttwiz.appui.Clock
import org.asyncttwiz.appui.Instant
import org.asyncttwiz.appui.LocalDate
import org.asyncttwiz.appui.ZonedDateTime
import org.asyncttwiz.protocol.GameSummary
import org.asyncttwiz.protocol.MatchRecord

// Dates and times as the app writes them (docs/specs/web-client.md §1 item 2). Android: java.time, as before; each
// function's Android side is the code that was here.

/** A game row's last activity: "14:05" today, "21 Oct" this year, "21 Oct 2025" before; "" when unreadable. */
expect fun gameLastActivity(game: GameSummary, clock: Clock = AppClock.clock()): String

/** A game's creation date, "7 Nov 2026" (the device's zone); "" when unreadable. */
expect fun gameDate(game: GameSummary): String

/** A record's date, "7 Nov 2026" (the device's zone). */
expect fun matchDate(record: MatchRecord): String

/** How long a match took, "2 d 3 h", "3 h 5 min" or "12 min". */
expect fun matchDuration(record: MatchRecord): String

/** The day [seconds] before [wallNow], "Oct 27" this year or "Oct 27, 2025" before. */
expect fun lastSeenDay(wallNow: ZonedDateTime, seconds: Long): String

/** Matches per week (weeks from Monday) for the 8 weeks up to [clock]'s, oldest first, by [clock]'s zone. */
expect fun matchesPerWeek(records: List<MatchRecord>, me: String, clock: Clock): List<Pair<LocalDate, Int>>

/** A week's first day, "3 Nov". */
expect fun weekLabel(week: LocalDate): String

/** Today by [clock], "2026-11-07". */
expect fun isoDateToday(clock: Clock): String

/** An ISO-8601 date and time with an offset as milliseconds since 1970; throws when unreadable. */
expect fun offsetDateTimeMillis(text: String): Long

/** The seconds from [now] until the HTTP date [value] (RFC 1123), at least 0; throws when unreadable. */
expect fun secondsUntilHttpDate(now: Instant, value: String?): Long

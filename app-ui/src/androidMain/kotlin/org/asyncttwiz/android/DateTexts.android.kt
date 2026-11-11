package org.asyncttwiz.android

import org.asyncttwiz.protocol.GameSummary
import org.asyncttwiz.protocol.MatchRecord
import java.time.Clock
import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import java.util.Locale

actual fun gameLastActivity(game: GameSummary, clock: Clock): String = runCatching {
    val activity = Instant.parse(game.updatedAt).atZone(clock.zone)
    val today = LocalDate.now(clock)
    val pattern = when {
        activity.toLocalDate() == today -> "HH:mm"
        activity.year == today.year -> "d MMM"
        else -> "d MMM uuuu"
    }
    activity.format(DateTimeFormatter.ofPattern(pattern, Locale.ENGLISH))
}.getOrDefault("")

actual fun gameDate(game: GameSummary): String = runCatching {
    DateTimeFormatter.ofPattern("d MMM uuuu").withZone(ZoneId.systemDefault()).format(Instant.parse(game.createdAt))
}.getOrDefault("")

actual fun matchDate(record: MatchRecord): String = runCatching {
    DateTimeFormatter.ofPattern("d MMM uuuu").withZone(AppClock.clock().zone).format(Instant.parse(record.endedAt))
}.getOrDefault("")

actual fun matchDuration(record: MatchRecord): String = runCatching {
    val minutes = Duration.between(Instant.parse(record.startedAt), Instant.parse(record.endedAt)).toMinutes().coerceAtLeast(0)
    when {
        minutes >= 24 * 60 -> "${minutes / (24 * 60)} d ${minutes % (24 * 60) / 60} h"
        minutes >= 60 -> "${minutes / 60} h ${minutes % 60} min"
        else -> "$minutes min"
    }
}.getOrDefault("")

actual fun lastSeenDay(wallNow: ZonedDateTime, seconds: Long): String {
    val day = wallNow.minusSeconds(seconds)
    val pattern = if (day.year == wallNow.year) "MMM d" else "MMM d, yyyy"
    return day.format(DateTimeFormatter.ofPattern(pattern, Locale.ENGLISH))
}

/** Matches per week (weeks from Monday, in [zone]) for the [weeks] weeks up to [now]'s, oldest first. */
fun MatchStats.perWeek(records: List<MatchRecord>, me: String, now: Instant, zone: ZoneId, weeks: Int = 8): List<Pair<LocalDate, Int>> {
    fun week(instant: Instant): LocalDate = instant.atZone(zone).toLocalDate().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    val current = week(now)
    val counts = records.finished(me).groupingBy { week(Instant.parse(it.endedAt)) }.eachCount()
    return (weeks - 1 downTo 0).map { back -> current.minusWeeks(back.toLong()).let { it to (counts[it] ?: 0) } }
}

actual fun matchesPerWeek(records: List<MatchRecord>, me: String, clock: Clock): List<Pair<LocalDate, Int>> =
    MatchStats.perWeek(records, me, clock.instant(), clock.zone)

actual fun weekLabel(week: LocalDate): String = DateTimeFormatter.ofPattern("d MMM").format(week)

actual fun isoDateToday(clock: Clock): String = LocalDate.now(clock).toString()

actual fun offsetDateTimeMillis(text: String): Long = java.time.OffsetDateTime.parse(text).toInstant().toEpochMilli()

actual fun secondsUntilHttpDate(now: Instant, value: String?): Long =
    java.time.Duration.between(now, java.time.ZonedDateTime.parse(value,
        java.time.format.DateTimeFormatter.RFC_1123_DATE_TIME).toInstant()).seconds.coerceAtLeast(0)

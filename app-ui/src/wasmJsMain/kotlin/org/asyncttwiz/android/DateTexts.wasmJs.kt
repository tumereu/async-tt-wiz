@file:OptIn(ExperimentalWasmJsInterop::class)

package org.asyncttwiz.android

import org.asyncttwiz.appui.Clock
import org.asyncttwiz.appui.Instant
import org.asyncttwiz.appui.LocalDate
import org.asyncttwiz.appui.ZonedDateTime
import org.asyncttwiz.appui.instantOfEpochMilli
import org.asyncttwiz.appui.nowMillis
import org.asyncttwiz.appui.parseInstant
import org.asyncttwiz.appui.zonedNow
import org.asyncttwiz.protocol.GameSummary
import org.asyncttwiz.protocol.MatchRecord

// The browser's dates and times (web client step 3b): Android's texts, worked out in the browser's time zone. Where
// Android writes month names in English, so does this; where it uses the device's language, the browser's (Intl).

private val englishMonths = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")

/** [month]'s (1-12) short name as a date writes it in the browser's language ("Nov"; Finnish "marrask."). */
private fun localMonth(month: Int): String = jsShortMonth(month).ifEmpty { englishMonths[month - 1] }

private fun jsShortMonth(month: Int): String = js("""(() => {
    try {
        const parts = new Intl.DateTimeFormat(undefined, { day: 'numeric', month: 'short' }).formatToParts(new Date(2001, month - 1, 15));
        const part = parts.find(p => p.type === 'month');
        return part ? part.value : '';
    } catch (e) { return ''; }
})()""")

private fun zoned(instant: Instant): ZonedDateTime = zonedNow(FixedClock(instant))

/** A clock stopped at [instant], to read an instant's local fields through [zonedNow]. */
private class FixedClock(private val instant: Instant) : Clock() {
    override fun millis(): Long = instant.epochMillis
}

private fun two(value: Int) = value.toString().padStart(2, '0')

actual fun gameLastActivity(game: GameSummary, clock: Clock): String = runCatching {
    val activity = zoned(parseInstant(game.updatedAt))
    val today = zonedNow(clock).date
    when {
        activity.date == today -> "${two(activity.hour)}:${two(activity.minute)}"
        activity.year == today.year -> "${activity.day} ${englishMonths[activity.month - 1]}"
        else -> "${activity.day} ${englishMonths[activity.month - 1]} ${activity.year}"
    }
}.getOrDefault("")

private fun localDate(text: String): String = zoned(parseInstant(text)).let { "${it.day} ${localMonth(it.month)} ${it.year}" }

actual fun gameDate(game: GameSummary): String = runCatching { localDate(game.createdAt) }.getOrDefault("")

actual fun matchDate(record: MatchRecord): String = runCatching { localDate(record.endedAt) }.getOrDefault("")

actual fun matchDuration(record: MatchRecord): String = runCatching {
    val minutes = ((parseInstant(record.endedAt).epochMillis - parseInstant(record.startedAt).epochMillis) / 60_000).coerceAtLeast(0)
    when {
        minutes >= 24 * 60 -> "${minutes / (24 * 60)} d ${minutes % (24 * 60) / 60} h"
        minutes >= 60 -> "${minutes / 60} h ${minutes % 60} min"
        else -> "$minutes min"
    }
}.getOrDefault("")

actual fun lastSeenDay(wallNow: ZonedDateTime, seconds: Long): String {
    val day = wallNow.minusSeconds(seconds)
    val text = "${englishMonths[day.month - 1]} ${day.day}"
    return if (day.year == wallNow.year) text else "$text, ${day.year}"
}

actual fun matchesPerWeek(records: List<MatchRecord>, me: String, clock: Clock): List<Pair<LocalDate, Int>> {
    fun week(instant: Instant): LocalDate = zoned(instant).date.let { it.plusDays(-(it.dayOfWeek - 1).toLong()) }
    val current = week(instantOfEpochMilli(clock.nowMillis()))
    val counts = with(MatchStats) { records.finished(me) }.groupingBy { week(parseInstant(it.endedAt)) }.eachCount()
    return (8 - 1 downTo 0).map { back -> current.plusDays(-7L * back).let { it to (counts[it] ?: 0) } }
}

actual fun weekLabel(week: LocalDate): String = "${week.day} ${localMonth(week.month)}"

actual fun isoDateToday(clock: Clock): String = zonedNow(clock).date.toString()

actual fun offsetDateTimeMillis(text: String): Long = parseInstant(text).epochMillis

private val httpDate = Regex("^(?:[A-Za-z]{3},\\s+)?(\\d{1,2})\\s+([A-Za-z]{3})\\s+(\\d{4})\\s+(\\d{2}):(\\d{2})(?::(\\d{2}))?\\s+(GMT|[+-]\\d{4})$")

actual fun secondsUntilHttpDate(now: Instant, value: String?): Long {
    val match = requireNotNull(httpDate.matchEntire(requireNotNull(value).trim())) { "Not an HTTP date: $value" }
    val (day, monthName, year, hour, minute, second, zone) = match.destructured
    val month = englishMonths.indexOfFirst { it.equals(monthName, ignoreCase = true) } + 1
    require(month > 0) { "Not an HTTP date: $value" }
    val offsetMinutes = if (zone == "GMT") 0 else (zone.substring(1, 3).toInt() * 60 + zone.substring(3).toInt()) * if (zone[0] == '-') -1 else 1
    val iso = "$year-${two(month)}-${two(day.toInt())}T$hour:$minute:${second.ifEmpty { "00" }}Z"
    val millis = parseInstant(iso).epochMillis - offsetMinutes * 60_000L
    return ((millis - now.epochMillis) / 1000).coerceAtLeast(0)
}

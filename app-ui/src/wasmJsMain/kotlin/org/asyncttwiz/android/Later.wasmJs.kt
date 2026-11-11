package org.asyncttwiz.android

import org.asyncttwiz.appui.Clock
import org.asyncttwiz.appui.Instant
import org.asyncttwiz.appui.LocalDate
import org.asyncttwiz.appui.ZonedDateTime
import org.asyncttwiz.protocol.GameSummary
import org.asyncttwiz.protocol.MatchRecord
import org.asyncttwiz.protocol.ReconnectEpisode
import org.asyncttwiz.storage.ArtworkReference
import org.asyncttwiz.storage.CatalogCard
import org.asyncttwiz.storage.CatalogNamedPart
import org.asyncttwiz.storage.CatalogPrinting
import org.asyncttwiz.storage.ParsedDeck

// The browser's sides of the app's platform pieces come with the web shell and the web table (docs/specs/web-client.md
// §11 steps 3 and 4).
private fun later(): Nothing = throw NotImplementedError("web client step 3/4")

actual fun ArtworkReference.Companion.from(record: CatalogPrinting, size: String): ArtworkReference? = later()
actual fun CatalogCard.namedParts(): List<CatalogNamedPart> = later()
actual fun cubeCobraLink(text: String?): String? = later()
actual fun cubeCobraId(value: String): String = later()
actual fun parseCubeCobra(json: String): ParsedDeck = later()

actual fun gameLastActivity(game: GameSummary, clock: Clock): String = later()
actual fun gameDate(game: GameSummary): String = later()
actual fun matchDate(record: MatchRecord): String = later()
actual fun matchDuration(record: MatchRecord): String = later()
actual fun lastSeenDay(wallNow: ZonedDateTime, seconds: Long): String = later()
actual fun matchesPerWeek(records: List<MatchRecord>, me: String, clock: Clock): List<Pair<LocalDate, Int>> = later()
actual fun weekLabel(week: LocalDate): String = later()
actual fun isoDateToday(clock: Clock): String = later()
actual fun offsetDateTimeMillis(text: String): Long = later()
actual fun secondsUntilHttpDate(now: Instant, value: String?): Long = later()

actual fun platformGameConnection(origin: String): GameConnection = later()

actual object BackgroundEngineWorker : EngineWorker {
    actual override fun run(work: () -> Unit): Unit = later()
    actual override fun <T> call(work: () -> T): T = later()
    actual override fun main(work: () -> Unit): Unit = later()
    actual override fun frame(work: () -> Unit): Unit = later()
    actual override fun early(work: (release: (() -> Unit)?) -> Unit): Unit = later()
}

/** Reports are off on the web until its diagnostics come (step 3/4): nothing is recorded. */
actual object DebugJank {
    actual fun catchUp(game: String, running: Boolean) {}
    actual fun catchingUp(): Boolean = false
    actual fun treeInFlight(): Boolean = false
    actual fun animation(owner: String, running: Boolean) {}
    actual fun state(vararg values: Pair<String, String>) {}
    actual fun action(name: String) {}
    actual fun beginWork(kind: String): Long = 0
    actual fun endWork(kind: String, start: Long) {}
    actual fun track(owner: Any, tracking: JankTracking) {}
    actual fun untrack(owner: Any) {}
    actual fun frame(startNanos: Long, durationNanos: Long, isJank: Boolean, refreshRate: Float, states: Map<String, String>) {}
    actual fun authenticated(value: String?) {}
    actual fun discard() {}
}

actual object DebugLatency {
    actual fun begin(gameId: String, action: String, nodes: Int): LatencyRecorder.Trace? = null
    actual fun result(requestId: String) {}
    actual fun drawn() {}
    actual fun tap(gameId: String, event: String, popup: Boolean, cardId: String?) {}
    actual fun tapAnimation(gameId: String, rotatingCardIds: Set<String>) {}
    actual fun tapFrame(gameId: String, progress: Float, rate: Float) {}
    actual fun reconnect(value: ReconnectEpisode) {}
    actual fun appBackgrounded() {}
    actual fun discard() {}
    actual fun authenticated(value: String?) {}
}

actual object DebugCrashes {
    actual fun sessionToken(): String? = null
    actual fun install(hooks: ReportHooks) {}
    actual fun discard() {}
    actual fun authenticated(token: String?) {}
}

actual object DeviceRandom {
    actual fun seeded(seed: Long?): Unit = later()
    actual fun uuid(): String = later()
    actual fun bytes(size: Int): ByteArray = later()
    actual fun seed(): String = later()
}

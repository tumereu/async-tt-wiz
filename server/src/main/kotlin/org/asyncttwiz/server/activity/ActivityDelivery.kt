package org.asyncttwiz.server.activity

import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Clock
import java.time.Duration
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlinx.serialization.json.*
import org.asyncttwiz.protocol.ActivityPushHint
import org.asyncttwiz.protocol.ActivityWire
import org.asyncttwiz.server.ServiceRuntime
import org.asyncttwiz.server.generated.tables.Account.ACCOUNT
import org.asyncttwiz.server.generated.tables.AccountSession.ACCOUNT_SESSION as SESSION
import org.asyncttwiz.server.generated.tables.ActivityInstallation.ACTIVITY_INSTALLATION as INSTALL
import org.asyncttwiz.server.generated.tables.ActivityPush.ACTIVITY_PUSH as PUSH
import org.asyncttwiz.server.generated.tables.ActivitySummary.ACTIVITY_SUMMARY as SUMMARY
import org.asyncttwiz.server.generated.tables.Game.GAME
import org.asyncttwiz.server.generated.tables.Invitation.INVITATION
import org.jooq.SQLDialect
import org.jooq.impl.DSL

enum class PushStatus { DELIVERED, TRANSIENT, INVALID_REGISTRATION, FAILED }
data class PushResult(val status: PushStatus, val retryAfterSeconds: Long = 0)
fun interface ActivityPushAdapter { fun send(token: String, hint: ActivityPushHint): PushResult }

/** Optional FCM HTTP-v1 adapter. No names or notification payload. */
class FcmActivityAdapter @JvmOverloads constructor(private val project: String, private val accessToken: () -> String,
    private val client: HttpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8))
        .followRedirects(HttpClient.Redirect.NEVER).build()) : ActivityPushAdapter {
    init { require(project.matches(Regex("[a-z][a-z0-9-]{4,62}"))) }
    override fun send(token: String, hint: ActivityPushHint): PushResult = try {
        val body = payload(token, hint)
        val response = client.send(HttpRequest.newBuilder(URI("https://fcm.googleapis.com/v1/projects/$project/messages:send"))
            .timeout(Duration.ofSeconds(20)).header("Authorization", "Bearer ${accessToken()}")
            .header("Content-Type", "application/json; charset=UTF-8")
            .POST(HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofInputStream())
        val bytes = response.body().use { it.readNBytes(16385) }
        val invalid = if (bytes.size <= 16384) runCatching {
            ActivityWire.json.parseToJsonElement(bytes.decodeToString()).jsonObject["error"]?.jsonObject
                ?.get("details")?.jsonArray?.any { it.jsonObject["errorCode"]?.jsonPrimitive?.content == "UNREGISTERED" } == true
        }.getOrDefault(false) else false
        val status = when {
            response.statusCode() in 200..299 -> PushStatus.DELIVERED
            invalid -> PushStatus.INVALID_REGISTRATION
            response.statusCode() == 429 || response.statusCode() >= 500 -> PushStatus.TRANSIENT
            else -> PushStatus.FAILED
        }
        PushResult(status, retryAfter(response.headers().firstValue("Retry-After").orElse(null)))
    } catch (_: Exception) { PushResult(PushStatus.TRANSIENT) }

    companion object {
        fun payload(token: String, hint: ActivityPushHint): String = buildJsonObject {
            putJsonObject("message") {
                put("token", token)
                putJsonObject("data") { put("generation", hint.generation); put("gameId", hint.gameId)
                    put("activityId", hint.activityId); put("identity", hint.identity) }
                putJsonObject("android") { put("ttl", "86400s"); put("collapse_key", hint.gameId); put("priority", "high") }
            }
        }.toString()
        fun retryAfter(value: String?, now: java.time.Instant = java.time.Instant.now()): Long =
            value?.toLongOrNull()?.coerceAtLeast(0) ?: runCatching {
                Duration.between(now, java.time.ZonedDateTime.parse(value, java.time.format.DateTimeFormatter.RFC_1123_DATE_TIME).toInstant())
                    .seconds.coerceAtLeast(0)
            }.getOrDefault(0)
    }
}

/** Independently scheduled optional hint delivery. Network I/O never owns a game/DB transaction. */
class ActivityDelivery(private val runtime: ServiceRuntime, private val adapter: ActivityPushAdapter? = null,
    private val clock: Clock = Clock.systemUTC()) : AutoCloseable {
    private val worker = Executors.newSingleThreadScheduledExecutor { Thread(it, "activity-hints").apply { isDaemon = true } }
    fun start() { worker.scheduleWithFixedDelay({ if (runtime.ready()) runCatching { deliverDue() } }, 0, 1, TimeUnit.SECONDS) }
    fun deliverDue() {
        ActivityService(runtime, clock).pruneExpired()
        runtime.transaction { connection ->
            ActivityService.remindDue(DSL.using(connection, SQLDialect.POSTGRES), OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC))
        }
        runtime.transaction { connection ->
            val sql = DSL.using(connection, SQLDialect.POSTGRES)
            val time = OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC)
            sql.deleteFrom(PUSH).where(PUSH.EXPIRES_AT.le(time)).execute()
            val pending = sql.select(PUSH.fields().toList() + INSTALL.ACCOUNT_ID).from(PUSH)
                .join(INSTALL).on(INSTALL.GENERATION.eq(PUSH.GENERATION)).where(PUSH.KIND.eq("GAME")).fetch()
            for (row in pending) {
                val owner = row[INSTALL.ACCOUNT_ID]!!; val game = row[PUSH.GAME_ID]!!
                val exact = PUSH.GENERATION.eq(row[PUSH.GENERATION]).and(PUSH.GAME_ID.eq(game)).and(PUSH.KIND.eq("GAME"))
                val summary = sql.selectFrom(SUMMARY).where(SUMMARY.ACCOUNT_ID.eq(owner)).and(SUMMARY.GAME_ID.eq(game))
                    .and(SUMMARY.KIND.eq("GAME")).fetchOne() ?: continue
                val recipient = ActivityTiming.leases(sql, game, owner)
                val activity = row[PUSH.ACTIVITY_ID]!!
                if (activity == summary.reminderActivityId) {
                    // The waiting reminder ignores read progress: opening the game is not an answer.
                    if (ActivityTiming.present(recipient, time)) sql.deleteFrom(PUSH).where(exact).execute()
                } else if (activity > summary.activityId!!) {
                    sql.deleteFrom(PUSH).where(exact).execute() // A reminder whose stretch the recipient answered.
                } else if (summary.seenThrough!! >= summary.activityId!! || ActivityTiming.present(recipient, time)) {
                    sql.deleteFrom(PUSH).where(exact).execute()
                } else if (summary.authorId != null && !ActivityTiming.due(ActivityTiming.leases(sql, game, summary.authorId!!),
                        recipient, summary.lastEventAt!!, summary.yourMove!!, time)) {
                    sql.update(PUSH).set(PUSH.DUE_AT, maxOf(row[PUSH.DUE_AT]!!, time.plusSeconds(1))).where(exact).execute()
                }
            }
        }
        val adapter = adapter ?: return // Non-FCM maintenance is a normal supported service configuration.
        repeat(32) {
            val time = OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC)
            val work = runtime.transaction { connection ->
                val sql = DSL.using(connection, SQLDialect.POSTGRES)
                // Recheck expiry on every iteration: earlier network calls may
                // have consumed the remaining TTL of another queued hint.
                sql.deleteFrom(PUSH).where(PUSH.EXPIRES_AT.le(time)).execute()
                val row = sql.select(PUSH.fields().toList() + INSTALL.PUSH_TOKEN + INSTALL.ACCOUNT_ID).from(PUSH)
                    .join(INSTALL).on(INSTALL.GENERATION.eq(PUSH.GENERATION)).join(SESSION).on(SESSION.ID.eq(INSTALL.SESSION_ID))
                    .join(ACCOUNT).on(ACCOUNT.ID.eq(INSTALL.ACCOUNT_ID)).leftJoin(GAME).on(GAME.ID.eq(PUSH.GAME_ID))
                    .leftJoin(INVITATION).on(INVITATION.ID.eq(GAME.CURRENT_INVITATION))
                    .where(PUSH.DUE_AT.le(time)).and(INSTALL.PUSH_TOKEN.isNotNull).and(SESSION.REVOKED_AT.isNull)
                    .and(SESSION.AUTH_EPOCH.eq(ACCOUNT.AUTH_EPOCH))
                    .and(GAME.LIFECYCLE.`in`("LOBBY", "ACTIVE").and(PUSH.KIND.`in`("INVITATION", "GAME"))
                        .and(PUSH.KIND.ne("INVITATION").or(INVITATION.STATE.eq("PENDING").and(GAME.CURRENT_INVITATION.eq(PUSH.IDENTITY))))
                        .or(ActivityService.podVisible(INSTALL.ACCOUNT_ID, PUSH.KIND, PUSH.GAME_ID, PUSH.IDENTITY)))
                    .orderBy(PUSH.DUE_AT).limit(1).forUpdate().of(PUSH).skipLocked().fetchOne() ?: return@transaction null
                val attempt = row[PUSH.ATTEMPTS]!!
                val seconds = retrySeconds(attempt)
                sql.update(PUSH).set(PUSH.ATTEMPTS, attempt + 1).set(PUSH.DUE_AT, time.plusSeconds(seconds))
                    .where(PUSH.GENERATION.eq(row[PUSH.GENERATION])).and(PUSH.GAME_ID.eq(row[PUSH.GAME_ID])).and(PUSH.KIND.eq(row[PUSH.KIND])).execute()
                // The pushed activity was due now; the woken poll must show it even if the actor's next
                // (late-reported) input arrives before that poll and makes it look not-yet-due again.
                if (row[PUSH.KIND] == "GAME") sql.update(SUMMARY)
                    .set(SUMMARY.PUSHED_THROUGH, DSL.greatest(SUMMARY.PUSHED_THROUGH, DSL.`val`(row[PUSH.ACTIVITY_ID])))
                    .where(SUMMARY.ACCOUNT_ID.eq(row[INSTALL.ACCOUNT_ID])).and(SUMMARY.GAME_ID.eq(row[PUSH.GAME_ID]))
                    .and(SUMMARY.KIND.eq("GAME")).execute()
                Triple(row, ActivityPushHint(row[PUSH.GENERATION].toString(), row[PUSH.GAME_ID].toString(),
                    row[PUSH.ACTIVITY_ID].toString(), row[PUSH.IDENTITY].toString()), row[INSTALL.PUSH_TOKEN]!!)
            } ?: return
            val result = adapter.send(work.third, work.second)
            runtime.transaction { connection ->
                val sql = DSL.using(connection, SQLDialect.POSTGRES)
                val row = work.first
                val exact = PUSH.GENERATION.eq(row[PUSH.GENERATION]).and(PUSH.GAME_ID.eq(row[PUSH.GAME_ID]))
                    .and(PUSH.KIND.eq(row[PUSH.KIND])).and(PUSH.ACTIVITY_ID.eq(row[PUSH.ACTIVITY_ID]))
                when (result.status) {
                    PushStatus.TRANSIENT -> if (result.retryAfterSeconds > 0) sql.update(PUSH)
                        .set(PUSH.DUE_AT, minOf(row[PUSH.EXPIRES_AT]!!, time.plusSeconds(maxOf(
                            retrySeconds(row[PUSH.ATTEMPTS]!!), result.retryAfterSeconds.coerceAtMost(86400)))))
                        .where(exact).execute()
                    PushStatus.INVALID_REGISTRATION -> {
                        sql.update(INSTALL).set(INSTALL.PUSH_TOKEN, null as String?)
                            .set(INSTALL.INVALID_TOKEN_HASH, ActivityService.tokenHash(work.third)).where(INSTALL.GENERATION.eq(row[PUSH.GENERATION]))
                            .and(INSTALL.PUSH_TOKEN.eq(work.third)).execute()
                        sql.deleteFrom(PUSH).where(exact).execute()
                    }
                    else -> sql.deleteFrom(PUSH).where(exact).execute()
                }
            }
        }
    }
    override fun close() { worker.shutdownNow(); check(worker.awaitTermination(25, TimeUnit.SECONDS)) }
    companion object { fun retrySeconds(attempt: Int) = when (attempt) { 0 -> 60L; 1 -> 300L; 2 -> 900L; else -> 3600L } }
}

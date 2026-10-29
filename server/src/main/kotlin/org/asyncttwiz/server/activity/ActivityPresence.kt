package org.asyncttwiz.server.activity

import java.time.OffsetDateTime
import java.util.UUID
import org.asyncttwiz.protocol.OpponentPresence
import org.asyncttwiz.protocol.PresenceState
import org.asyncttwiz.server.generated.tables.ActivityPresence.ACTIVITY_PRESENCE as PRESENCE
import org.asyncttwiz.server.generated.tables.AccountSession.ACCOUNT_SESSION as SESSION
import org.asyncttwiz.server.generated.tables.Account.ACCOUNT
import org.jooq.DSLContext

internal data class PresenceLease(val heartbeat: OffsetDateTime, val input: OffsetDateTime)

/** Delivery policy only: no shared game state, history, or read-progress writes. A lease proves presence only
 * up to its heartbeat, so [present] may delay a notice but never marks anything seen; only a later heartbeat does. */
internal object ActivityTiming {
    fun present(leases: List<PresenceLease>, now: OffsetDateTime) = leases.any { it.heartbeat.plusSeconds(15).isAfter(now) }

    fun due(actor: List<PresenceLease>, recipient: List<PresenceLease>, event: OffsetDateTime,
        yourMove: Boolean, now: OffsetDateTime): Boolean {
        if (present(recipient, now)) return false
        val live = actor.filter { it.heartbeat.plusSeconds(15).isAfter(now) }
        if (live.isEmpty()) return true
        val lastInput = maxOf(event.toInstant(), live.maxOf { it.input.toInstant() })
        return !lastInput.plusSeconds(if (yourMove) 10 else 60).isAfter(now.toInstant())
    }

    fun leases(sql: DSLContext, game: UUID, account: UUID): List<PresenceLease> =
        sql.select(PRESENCE.HEARTBEAT_AT, PRESENCE.INPUT_AT).from(PRESENCE)
            .join(SESSION).on(SESSION.ID.eq(PRESENCE.SESSION_ID)).join(ACCOUNT).on(ACCOUNT.ID.eq(PRESENCE.ACCOUNT_ID))
            .where(PRESENCE.GAME_ID.eq(game)).and(PRESENCE.ACCOUNT_ID.eq(account)).and(PRESENCE.LEFT_GAME.isFalse)
            .and(SESSION.REVOKED_AT.isNull).and(SESSION.AUTH_EPOCH.eq(ACCOUNT.AUTH_EPOCH))
            .fetch { PresenceLease(it[PRESENCE.HEARTBEAT_AT]!!, it[PRESENCE.INPUT_AT]!!) }
}

internal data class PresenceRow(val presenceId: UUID, val heartbeat: OffsetDateTime, val input: OffsetDateTime, val left: Boolean)

/** A player's presence change that the opponent's open table should hear about now. */
data class PresenceChange(val gameId: String, val accountId: String, val presence: OpponentPresence)
fun interface PresenceListener { fun changed(change: PresenceChange) }

/** What the opponent sees of a player: computed from the same rows as delivery, never stored separately.
 *  The newest row per game and player outlives pruning, so "last seen" survives days. */
object OpponentPresences {
    const val LIVE_SECONDS = 15L
    const val IDLE_SECONDS = 120L
    /** Clients turn the dot idle by themselves once ages pass [IDLE_SECONDS]. Input after this age is
     *  sent at once, so a client never runs ahead of input it has not heard about (heads come every 30 s). */
    const val LATE_SECONDS = 90L

    internal fun rows(sql: DSLContext, game: UUID, account: UUID): List<PresenceRow> =
        sql.select(PRESENCE.PRESENCE_ID, PRESENCE.HEARTBEAT_AT, PRESENCE.INPUT_AT, PRESENCE.LEFT_GAME).from(PRESENCE)
            .where(PRESENCE.GAME_ID.eq(game)).and(PRESENCE.ACCOUNT_ID.eq(account))
            .fetch { PresenceRow(it[PRESENCE.PRESENCE_ID]!!, it[PRESENCE.HEARTBEAT_AT]!!, it[PRESENCE.INPUT_AT]!!, it[PRESENCE.LEFT_GAME]!!) }

    /** A player's presence as their opponent sees it; one indexed read. */
    fun read(sql: DSLContext, game: UUID, account: UUID, now: OffsetDateTime): OpponentPresence = of(rows(sql, game, account), now)

    private fun age(time: OffsetDateTime, now: OffsetDateTime): Int =
        java.time.Duration.between(time.toInstant(), now.toInstant()).seconds.coerceIn(0, Int.MAX_VALUE.toLong()).toInt()

    internal fun of(rows: List<PresenceRow>, now: OffsetDateTime): OpponentPresence {
        val lastSeen = rows.maxByOrNull { it.heartbeat.toInstant() }?.let { age(it.heartbeat, now) }
        val live = rows.filter { !it.left && it.heartbeat.plusSeconds(LIVE_SECONDS).isAfter(now) }
        if (live.isEmpty()) return OpponentPresence(PresenceState.OFFLINE, lastSeenSeconds = lastSeen)
        val idle = live.minOf { age(it.input, now) }
        return OpponentPresence(if (idle < IDLE_SECONDS) PresenceState.ONLINE else PresenceState.IDLE, idle, lastSeen)
    }

    /** Coarse buckets; a heartbeat that moves a player between them is pushed to the opponent. */
    internal fun bucket(rows: List<PresenceRow>, now: OffsetDateTime): Int {
        val presence = of(rows, now)
        return when (presence.state) {
            PresenceState.OFFLINE -> 0
            PresenceState.IDLE -> 1
            PresenceState.ONLINE -> if (presence.idleSeconds!! >= LATE_SECONDS) 2 else 3
        }
    }
}

package org.asyncttwiz.server.activity

import java.time.OffsetDateTime
import java.util.UUID
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

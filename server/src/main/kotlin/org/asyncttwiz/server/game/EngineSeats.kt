package org.asyncttwiz.server.game

import java.util.UUID
import org.asyncttwiz.server.generated.tables.EngineSeat.ENGINE_SEAT
import org.jooq.DSLContext

/** Whose move a play whose state is the engine's is for one viewer (I-D56 §7), as stored in `engine_seat`. */
internal object EngineSeats {
    /** [holder]: the viewer when they have a next stop, else the other player when they have one, else nobody. */
    class Move(val holder: String?)

    /** Whose move [play] is for [viewer]; null when [play] has no engine seats (today's model). One indexed read. */
    fun attention(sql: DSLContext, play: UUID, viewer: UUID): Move? {
        val rows = sql.select(ENGINE_SEAT.ACCOUNT_ID, ENGINE_SEAT.NEXT_STOP).from(ENGINE_SEAT).where(ENGINE_SEAT.PLAY_ID.eq(play))
            .orderBy(ENGINE_SEAT.ACCOUNT_ID).fetch()
        if (rows.isEmpty()) return null
        val stops = rows.filter { it.value2() != null }.map { it.value1()!! }
        return Move((stops.firstOrNull { it == viewer } ?: stops.firstOrNull())?.toString())
    }
}

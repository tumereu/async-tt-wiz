package org.asyncttwiz.server.activity

import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID
import org.asyncttwiz.protocol.*
import org.jooq.SQLDialect
import org.jooq.impl.DSL
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

/** A batch's events (a 1000-member Automatum batch, I-D14) are recorded together: the same activity rows, summary and
 * pending pushes as recording them one by one. */
class ActivityRecordAllTest {
    private fun id() = ActivityFixture.id()

    @Test fun `recording a batch's events together leaves what recording them one by one leaves`() {
        ActivityFixture().use { f ->
            ActivityService(f.runtime).register(f.b, RegisterInstallation(id(), id(), "synthetic-push"))
            val owner = UUID.fromString(f.b.account.accountId)
            val author = UUID.fromString(f.a.account.accountId)
            val time = OffsetDateTime.now(ZoneOffset.UTC).withNano(0)
            val x = UUID.randomUUID(); val y = UUID.randomUUID()
            for (pattern in listOf(listOf(x, x, y), listOf(x, x, x), listOf(y, x, y, y), listOf(y))) for (retrying in listOf(false, true)) {
                // Two games in the same state: a pending push for identity x (retrying or not) and a summary
                val games = List(2) { UUID.fromString(f.create().id) }
                for (game in games) {
                    f.runtime.transaction { c -> ActivityService.record(DSL.using(c, SQLDialect.POSTGRES), owner, ActivityKind.GAME, game, x, author,
                        time.minusMinutes(5), yourMove = true, move = true) }
                    if (retrying) f.sql("UPDATE activity_push SET attempts = 2, due_at = '${time.plusMinutes(3)}' WHERE game_id = '$game'")
                }
                // ... then one by one in the first, all together in the second
                f.runtime.transaction { c -> for (identity in pattern) ActivityService.record(DSL.using(c, SQLDialect.POSTGRES), owner,
                    ActivityKind.GAME, games[0], identity, author, time, yourMove = false, move = true) }
                f.runtime.transaction { c -> ActivityService.recordAll(DSL.using(c, SQLDialect.POSTGRES), owner, ActivityKind.GAME,
                    games[1], pattern.map { it to author }, time, yourMove = false, move = true) }
                fun rows(query: String, game: UUID) = f.db.connection().use { c -> c.createStatement().executeQuery(query.replace(":game", "'$game'")).use { r ->
                    buildList { while (r.next()) add((1..r.metaData.columnCount).map { r.getString(it) }) } } }
                // Activity ids are per account (both games' interleave): the batch's own, relative to its first
                fun first(game: UUID) = rows("SELECT min(activity_id) FROM account_activity WHERE game_id = :game AND created_at = '$time'", game)
                    .single().single()!!.toLong()
                fun events(game: UUID) = rows("SELECT activity_id, kind, identity, author_id, created_at FROM account_activity " +
                    "WHERE game_id = :game AND created_at = '$time' ORDER BY activity_id", game).map { listOf((it[0].toLong() - first(game)).toString()) + it.drop(1) }
                fun summary(game: UUID) = rows("SELECT activity_id, identity, author_id, last_event_at, your_move, new_message, reminder_move_at, " +
                    "reminder_activity_id IS NULL FROM activity_summary WHERE game_id = :game AND kind = 'GAME'", game).single()
                    .let { listOf((it[0].toLong() - first(game)).toString()) + it.drop(1) }
                fun pushes(game: UUID) = rows("SELECT activity_id, identity, first_at, due_at, expires_at, attempts FROM activity_push WHERE game_id = :game AND kind = 'GAME'", game)
                    .map { listOf((it[0].toLong() - first(game)).toString()) + it.drop(1) }
                assertEquals(pattern.indices.map { it.toString() }, events(games[0]).map { it[0] })
                assertEquals(events(games[0]), events(games[1]), "$pattern $retrying")
                assertEquals(summary(games[0]), summary(games[1]), "$pattern $retrying")
                assertEquals(1, pushes(games[0]).size)
                assertEquals(pushes(games[0]), pushes(games[1]), "$pattern $retrying")
            }
        }
    }
}

package org.asyncttwiz.server.activity

import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit
import java.util.UUID
import org.asyncttwiz.protocol.*
import org.asyncttwiz.server.accounts.AccountService
import org.jooq.SQLDialect
import org.jooq.impl.DSL
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

/** The app's waiting-game fallback lists a game only while it waits for the account by the push rule. */
class ActivityWaitingTest {
    private class Scenario(val f: ActivityFixture) {
        val game = f.accept(f.create())
        val clock = ActivityClock(Instant.now().truncatedTo(ChronoUnit.MICROS))
        val start: Instant = clock.time
        val service = ActivityService(f.runtime, clock)
        val opponent = GamePresence(game.id, UUID.randomUUID().toString())
        init { f.sql("UPDATE game SET lifecycle = 'ACTIVE' WHERE id = '${game.id}'") }
        fun at(seconds: Long) { clock.time = start.plusSeconds(seconds) }
        fun event(by: AccountService.Principal = f.a, yourMove: Boolean = true, group: UUID = UUID.randomUUID()) = group.also {
            val other = if (by == f.a) f.b else f.a
            f.runtime.transaction { connection ->
                ActivityService.record(DSL.using(connection, SQLDialect.POSTGRES), UUID.fromString(other.account.accountId),
                    ActivityKind.GAME, UUID.fromString(game.id), group, UUID.fromString(by.account.accountId),
                    OffsetDateTime.ofInstant(clock.time, ZoneOffset.UTC), yourMove = yourMove, move = true)
            }
        }
        fun waiting(who: AccountService.Principal = f.b) = service.waiting(who).games
    }

    @Test fun `a game waits for the account when the idle opponent left the move with it`() {
        ActivityFixture().use { f ->
            val s = Scenario(f)
            assertTrue(s.waiting().isEmpty(), "No event yet")
            s.service.heartbeat(f.a, s.opponent.copy(didSomething = true))
            val group = s.event()
            assertTrue(s.waiting().isEmpty(), "The opponent is still at the table")
            s.at(9); assertTrue(s.waiting().isEmpty())
            s.at(10)
            val hint = s.waiting().single()
            assertEquals(s.game.id, hint.gameId); assertEquals(group.toString(), hint.identity)
            assertEquals(ActivityKind.GAME, hint.kind); assertEquals(f.a.account.accountId, hint.authorId)
            val notice = hint.notice!!
            assertTrue(notice.reminder && notice.yourMove); assertEquals("Inviter", notice.opponentName)
            assertEquals("Private table", notice.gameName)
            assertEquals(s.start, OffsetDateTime.parse(notice.lastEventAt).toInstant())
            assertTrue(s.waiting(f.a).isEmpty(), "Never the opponent's own list")
            // The opponent's later input at the table postpones it by the same rule as a push.
            s.service.heartbeat(f.a, s.opponent.copy(didSomething = true))
            assertTrue(s.waiting().isEmpty())
            s.at(20); assertEquals(1, s.waiting().size)
            // The recipient at the table is not waited for.
            val own = GamePresence(s.game.id, UUID.randomUUID().toString())
            s.service.heartbeat(f.b, own); assertTrue(s.waiting().isEmpty())
            s.service.heartbeat(f.b, own.copy(left = true)); assertEquals(1, s.waiting().size)
        }
    }

    @Test fun `the account's own later event, the opponent's move and a game out of play are not waiting`() {
        ActivityFixture().use { f ->
            val s = Scenario(f)
            s.event(yourMove = false)
            assertTrue(s.waiting().isEmpty(), "The opponent still has the move")
            s.at(1); s.event()
            assertEquals(1, s.waiting().size)
            s.at(2); s.event(by = f.b, yourMove = false)
            assertTrue(s.waiting().isEmpty(), "The account made the latest event")
            s.at(3); val next = s.event()
            assertEquals(next.toString(), s.waiting().single().identity, "A new opponent event waits again")
            f.sql("UPDATE game SET lifecycle = 'LOBBY' WHERE id = '${s.game.id}'")
            assertTrue(s.waiting().isEmpty())
        }
    }
}

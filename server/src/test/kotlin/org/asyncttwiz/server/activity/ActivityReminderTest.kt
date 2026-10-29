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

class ActivityReminderTest {
    private fun id() = ActivityFixture.id()

    private class Scenario(val f: ActivityFixture) {
        val game = f.accept(f.create())
        val clock = ActivityClock(Instant.now().truncatedTo(ChronoUnit.MICROS))
        val start: Instant = clock.time
        val service = ActivityService(f.runtime, clock)
        val sent = mutableListOf<ActivityPushHint>()
        val delivery = ActivityDelivery(f.runtime, ActivityPushAdapter { _, hint -> sent += hint; PushResult(PushStatus.DELIVERED) }, clock)
        val actor = GamePresence(game.id, UUID.randomUUID().toString())
        val recipient = GamePresence(game.id, UUID.randomUUID().toString())
        init {
            service.register(f.b, RegisterInstallation(UUID.randomUUID().toString(), UUID.randomUUID().toString(), "push-b"))
            f.sql("DELETE FROM activity_push")
            f.sql("UPDATE game SET lifecycle = 'ACTIVE' WHERE id = '${game.id}'") // Reminders concern a game in play.
        }
        fun at(seconds: Long) { clock.time = start.plusSeconds(seconds) }
        fun move(by: AccountService.Principal = f.a, chat: Boolean = false) = f.runtime.transaction { connection ->
            val other = if (by == f.a) f.b else f.a
            ActivityService.record(DSL.using(connection, SQLDialect.POSTGRES), UUID.fromString(other.account.accountId),
                ActivityKind.GAME, UUID.fromString(game.id), UUID.randomUUID(), UUID.fromString(by.account.accountId),
                OffsetDateTime.ofInstant(clock.time, ZoneOffset.UTC), newMessage = chat, move = !chat)
        }
        /** Runs the scheduler every second up to [seconds]. */
        fun runUntil(seconds: Long, each: (Long) -> Unit = {}) {
            var now = java.time.Duration.between(start, clock.time).seconds
            while (now < seconds) { now++; at(now); each(now); delivery.deliverDue() }
        }
        fun reminders() = service.page(f.b, null).readyNotices!!.filter { it.notice?.reminder == true }
    }

    @Test fun `one reminder five minutes after the opponent's last move and opening the game is not an answer`() {
        ActivityFixture().use { f ->
            val s = Scenario(f)
            s.service.heartbeat(f.a, s.actor.copy(didSomething = true))
            s.move()
            s.at(10); s.service.heartbeat(f.a, s.actor.copy(left = true))
            s.delivery.deliverDue()
            assertEquals(1, s.sent.size, "The ordinary notice is pushed when the opponent leaves")
            // Opening at 3 minutes and leaving without moving still gets the reminder at 5 minutes.
            s.runUntil(299) { now ->
                if (now == 180L) s.service.heartbeat(f.b, s.recipient)
                if (now == 190L) s.service.heartbeat(f.b, s.recipient.copy(left = true))
            }
            assertEquals(1, s.sent.size)
            assertTrue(s.reminders().isEmpty())
            s.runUntil(300)
            assertEquals(2, s.sent.size)
            val reminder = s.reminders().single()
            assertEquals(s.sent.last().activityId, reminder.activityId)
            assertTrue(reminder.activityId.toLong() > s.sent.first().activityId.toLong())
            assertEquals("Inviter", reminder.notice!!.opponentName)
            assertEquals(s.start, OffsetDateTime.parse(reminder.notice!!.lastEventAt).toInstant())
            // A further opponent move in the same unanswered stretch never reminds again.
            s.move()
            s.runUntil(1000)
            assertEquals(1L, f.number("SELECT count(*) FROM activity_summary WHERE reminder_activity_id IS NOT NULL"))
            assertEquals(3, s.sent.size, "Only the ordinary notice of the later move")
        }
    }

    @Test fun `reminder time is exact and the opponent's continued input postpones it`() {
        ActivityFixture().use { f ->
            val s = Scenario(f)
            s.service.heartbeat(f.a, s.actor.copy(didSomething = true))
            s.move()
            // The opponent keeps touching the table for two minutes, then stays idle but open.
            s.runUntil(299) { now -> if (now % 5 == 0L) s.service.heartbeat(f.a, s.actor.copy(didSomething = now <= 120)) }
            assertTrue(s.reminders().isEmpty())
            s.runUntil(419) { now -> if (now % 5 == 0L) s.service.heartbeat(f.a, s.actor) }
            assertTrue(s.reminders().isEmpty(), "Five minutes after the last input, not the move")
            s.runUntil(420) { s.service.heartbeat(f.a, s.actor) }
            assertEquals(1, s.reminders().size)
        }
    }

    @Test fun `a recipient in the game at the mark delays the reminder until five minutes after leaving`() {
        ActivityFixture().use { f ->
            val s = Scenario(f)
            s.move()
            s.runUntil(400) { now -> if (now >= 250 && now % 5 == 0L) s.service.heartbeat(f.b, s.recipient.copy(left = now == 400L)) }
            assertTrue(s.reminders().isEmpty())
            s.runUntil(699)
            assertTrue(s.reminders().isEmpty())
            s.runUntil(700)
            assertEquals(1, s.reminders().size)
        }
    }

    @Test fun `the recipient's move answers the stretch and the opponent's next move starts a new one`() {
        ActivityFixture().use { f ->
            val s = Scenario(f)
            s.move()
            s.at(100); s.move(by = f.b)
            s.runUntil(600)
            assertTrue(s.reminders().isEmpty(), "Answered before five minutes")
            s.move(by = f.b, chat = true)
            s.at(700); s.move(chat = true)
            s.runUntil(1100)
            assertTrue(s.reminders().isEmpty(), "Chat is not a move")
            s.move()
            s.runUntil(1400)
            assertEquals(1, s.reminders().size)
            s.move(by = f.b)
            assertTrue(s.reminders().isEmpty(), "Moving withdraws the sent reminder")
            s.move()
            s.runUntil(2000)
            assertEquals(1, s.reminders().size, "A new stretch reminds once more")
        }
    }

    @Test fun `a pushed notice stays shown when the opponent's late input reaches the server before the poll`() {
        ActivityFixture().use { f ->
            val s = Scenario(f)
            s.service.heartbeat(f.a, s.actor.copy(didSomething = true))
            s.move(); val cursor = s.service.page(f.b, "0").nextCursor
            s.runUntil(60) { now -> if (now % 5 == 0L) s.service.heartbeat(f.a, s.actor) }
            assertEquals(1, s.sent.size, "Due after sixty idle seconds")
            // Input made just before the push, reported by the next heartbeat, before the woken app polls.
            s.at(61); s.service.heartbeat(f.a, s.actor.copy(didSomething = true))
            val notice = s.service.page(f.b, cursor).readyNotices!!.single()
            assertEquals(s.sent.single().activityId, notice.activityId)
            assertFalse(notice.notice!!.reminder)
        }
    }
}
